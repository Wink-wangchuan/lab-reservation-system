<template>
  <div class="page-container">
    <div class="glass-card board-main">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Odometer /></el-icon>
          实验室实时占用看板
        </div>
        <div class="toolbar-right">
          <el-select v-model="filterStatus" placeholder="占用状态" clearable style="width: 140px">
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
          <el-input v-model="keyword" placeholder="搜索实验室名称/位置" clearable style="width: 220px" />
          <el-button type="primary" :loading="loading" @click="load">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </div>

      <!-- 实时汇总指标 -->
      <div class="sum-grid">
        <div v-for="c in summaryCards" :key="c.key" class="sum-card">
          <div class="sum-value">{{ c.value }}<span v-if="c.suffix" class="sum-suffix">{{ c.suffix }}</span></div>
          <div class="sum-label">{{ c.label }}</div>
        </div>
      </div>

      <!-- 看板卡片：加载中显示骨架屏 -->
      <div v-if="loading && !labs.length" class="lab-grid">
        <div v-for="i in 6" :key="i" class="lab-card">
          <div class="skeleton" style="height: 20px; width: 60%"></div>
          <div class="skeleton" style="height: 14px; width: 40%; margin-top: 12px"></div>
          <div class="skeleton" style="height: 14px; width: 80%; margin-top: 8px"></div>
        </div>
      </div>

      <div v-else-if="!filteredLabs.length">
        <el-empty description="暂无符合条件的实验室" />
      </div>

      <div v-else class="lab-grid">
        <div v-for="lab in filteredLabs" :key="lab.labId" class="lab-card" :data-status="lab.status">
          <div class="lab-head">
            <div class="lab-name">{{ lab.labName }}</div>
            <span class="lab-badge">{{ lab.statusDesc }}</span>
          </div>
          <div v-if="lab.futureCount" class="upcoming-flag">
            <el-icon><Calendar /></el-icon>
            近期已被预约 · 未来共 {{ lab.futureCount }} 场
          </div>
          <div class="lab-meta">
            <span><el-icon><OfficeBuilding /></el-icon> {{ lab.labCode }}</span>
            <span>{{ lab.location || '位置待定' }}</span>
          </div>
          <div class="lab-meta">
            <span>容量 {{ lab.capacity }} 人</span>
            <span>{{ lab.labType }}</span>
          </div>

          <div v-if="lab.status === 'IN_USE' || lab.status === 'RESERVED'" class="lab-current">
            <div class="cur-line">
              <el-icon><Clock /></el-icon>
              {{ dateText(lab.currentReserveDate) }} {{ hhmm(lab.currentStartTime) }} - {{ hhmm(lab.currentEndTime) }}
            </div>
            <div class="cur-line ellipsis">用途：{{ lab.purpose || '-' }}</div>
          </div>
          <div v-else class="lab-current idle">
            <div v-if="lab.nextReserveDate" class="cur-line next">
              <el-icon><Calendar /></el-icon>
              下次预约 {{ dateText(lab.nextReserveDate) }} {{ hhmm(lab.nextStartTime) }} - {{ hhmm(lab.nextEndTime) }}
            </div>
            <div class="cur-line">今日有效预约 {{ lab.todayCount }} 场</div>
            <div class="cur-line">今日后续待开始 {{ lab.upcomingCount }} 场</div>
          </div>

          <div class="lab-foot">
            <span>今日预约 {{ lab.todayCount }} 场</span>
            <span>未来 {{ lab.futureCount || 0 }} 场</span>
          </div>
        </div>
      </div>

      <div class="board-tip">
        占用状态按「今天」的时段实时判定；未来日期的已通过预约只在空闲卡片上以「下次预约」提示，不计入今天的占用与空闲统计。数据每 20 秒由服务端缓存刷新，页面每 30 秒自动拉取一次，点击「刷新」可立即更新。
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { boardRealtime, boardSummary } from '@/api'

const loading = ref(false)
const labs = ref([])
const summary = ref({})
const filterStatus = ref('')
const keyword = ref('')
let timer = null

const statusOptions = [
  { value: 'IN_USE', label: '使用中' },
  { value: 'RESERVED', label: '已预约' },
  { value: 'IDLE', label: '空闲' },
  { value: 'LOCKED', label: '故障锁定' },
  { value: 'CLOSED', label: '未开放' }
]

const summaryCards = computed(() => {
  const s = summary.value
  return [
    { key: 'total', label: '实验室总数', value: s.labTotal ?? 0 },
    { key: 'inuse', label: '使用中', value: s.inUse ?? 0 },
    { key: 'reserved', label: '已预约', value: s.reserved ?? 0 },
    { key: 'idle', label: '空闲', value: s.idle ?? 0 },
    { key: 'locked', label: '故障锁定', value: s.locked ?? 0 },
    { key: 'util', label: '实时使用率', value: s.utilization ?? 0, suffix: '%' }
  ]
})

const filteredLabs = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return labs.value.filter((l) => {
    if (filterStatus.value && l.status !== filterStatus.value) return false
    if (!kw) return true
    return (l.labName || '').toLowerCase().includes(kw)
      || (l.labCode || '').toLowerCase().includes(kw)
      || (l.location || '').toLowerCase().includes(kw)
  })
})

// 后端 LocalTime 序列化为 HH:mm:ss，这里截到分钟避免多余秒数
function hhmm(t) {
  return t ? String(t).slice(0, 5) : ''
}

// 预约日期文案：当天显示「今日」，其余显示「x月x日」
function dateText(d) {
  if (!d) return ''
  const pad = (n) => String(n).padStart(2, '0')
  const now = new Date()
  const todayStr = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
  if (String(d) === todayStr) return '今日'
  const [, m, day] = String(d).split('-')
  return `${Number(m)}月${Number(day)}日`
}

async function load() {
  loading.value = true
  try {
    const [list, sum] = await Promise.all([boardRealtime(), boardSummary()])
    labs.value = list || []
    summary.value = sum || {}
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  timer = setInterval(load, 30000)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.board-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sum-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.sum-card {
  position: relative;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: var(--surface-btn);
  overflow: hidden;
}

.sum-value {
  font-size: 24px;
  font-weight: 800;
  color: var(--ink-1);
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.sum-suffix {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-3);
  margin-left: 2px;
}

.sum-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--ink-3);
}

.lab-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.lab-card {
  position: relative;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
  background: var(--card-bg);
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-soft);
  }

  &[data-status='IN_USE'] {
    border-color: rgba(255, 107, 129, 0.4);
    background: rgba(255, 107, 129, 0.08);
  }

  &[data-status='RESERVED'] {
    border-color: rgba(247, 163, 92, 0.4);
    background: rgba(247, 163, 92, 0.08);
  }

  &[data-status='LOCKED'] {
    border-color: rgba(143, 107, 255, 0.4);
    background: rgba(143, 107, 255, 0.08);
  }

  &[data-status='CLOSED'] {
    opacity: 0.72;
  }
}

.lab-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.lab-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lab-badge {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 999px;
  color: #fff;
  background: var(--accent-gradient);
}

.lab-card[data-status='IN_USE'] .lab-badge { background: linear-gradient(135deg, #ff6b81, #ff8a5c); }
.lab-card[data-status='RESERVED'] .lab-badge { background: linear-gradient(135deg, #f7a35c, #ffb84d); }
.lab-card[data-status='IDLE'] .lab-badge { background: linear-gradient(135deg, #22c1c3, #35e0a1); }
.lab-card[data-status='LOCKED'] .lab-badge { background: linear-gradient(135deg, #8f6bff, #c46bff); }
.lab-card[data-status='CLOSED'] .lab-badge { background: linear-gradient(135deg, #8a94b8, #a7b0cc); }

// 未来预约提示：空闲卡片但近期有预约时的高亮条，避免被误认为「完全无预约」
.upcoming-flag {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 9px;
  border-radius: 9px;
  font-size: 12px;
  font-weight: 600;
  color: #b3660f;
  background: rgba(247, 163, 92, 0.16);
  border: 1px solid rgba(247, 163, 92, 0.4);
}

.cur-line.next {
  color: #c97716;
  font-weight: 600;
}

html.dark .upcoming-flag {
  color: #ffc078;
  background: rgba(247, 163, 92, 0.12);
}

html.dark .cur-line.next {
  color: #ffc078;
}

.lab-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--ink-3);
  flex-wrap: wrap;
  span {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
}

.lab-current {
  margin-top: 2px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--card-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 4px;

  &.idle {
    color: var(--ink-3);
  }
}

.cur-line {
  font-size: 12px;
  color: var(--ink-2);
  display: flex;
  align-items: center;
  gap: 5px;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lab-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  font-size: 11px;
  color: var(--ink-3);
  border-top: 1px dashed var(--glass-border);
  padding-top: 8px;
}

.board-tip {
  font-size: 11px;
  color: var(--ink-3);
  text-align: center;
}

@media (max-width: 1180px) {
  .sum-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .lab-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .sum-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lab-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
