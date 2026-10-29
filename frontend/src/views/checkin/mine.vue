<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Stamp /></el-icon>
          我的签到
        </div>
        <div class="toolbar-right">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
        </div>
      </div>

      <el-skeleton v-if="loading && !records.length" :rows="6" animated />

      <template v-else>
        <div v-if="!isMobile" class="table-scroll">
          <el-table v-loading="loading" :data="records" stripe border>
            <el-table-column label="预约单号" prop="reservationNo" width="210" />
            <el-table-column label="实验室" prop="labName" min-width="170" />
            <el-table-column label="日期" prop="reserveDate" width="110" />
            <el-table-column label="时段" width="130">
              <template #default="{ row }">{{ hhmm(row.startTime) }} - {{ hhmm(row.endTime) }}</template>
            </el-table-column>
            <el-table-column label="签到时间" width="180">
              <template #default="{ row }">{{ row.checkinTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="签退时间" width="180">
              <template #default="{ row }">{{ row.checkoutTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ row.statusDesc }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'SIGN_IN_PENDING' || row.status === 'CHECKED_IN'"
                  link type="primary" @click="showCode(row)"
                >核销码</el-button>
                <el-button v-if="row.checkoutable" link type="success" @click="doCheckout(row)">签退</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 窄屏改用卡片列表：核销码、状态与操作在表格里会被挤到横向滚动区外 -->
        <div v-else v-loading="loading" class="checkin-cards">
          <div v-for="row in records" :key="row.id" class="checkin-card">
            <div class="checkin-card__head">
              <span class="checkin-card__lab">{{ row.labName }}</span>
              <el-tag :type="statusType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
            </div>
            <!-- 核销码按位拆开展示，窄屏下便于逐位读给管理员核销 -->
            <div v-if="row.checkinCode" class="checkin-card__code">{{ formatCode(row.checkinCode) }}</div>
            <div class="checkin-card__meta">
              <span>{{ row.reserveDate }}</span>
              <span>{{ hhmm(row.startTime) }} - {{ hhmm(row.endTime) }}</span>
            </div>
            <div class="checkin-card__meta checkin-card__meta--muted">
              <span>单号 {{ row.reservationNo }}</span>
              <span>签到 {{ row.checkinTime || '-' }}</span>
              <span v-if="row.checkoutTime">签退 {{ row.checkoutTime }}</span>
            </div>
            <div
              v-if="row.status === 'SIGN_IN_PENDING' || row.status === 'CHECKED_IN' || row.checkoutable"
              class="checkin-card__actions"
            >
              <el-button
                v-if="row.status === 'SIGN_IN_PENDING' || row.status === 'CHECKED_IN'"
                size="small" type="primary" plain @click="showCode(row)"
              >核销码</el-button>
              <el-button v-if="row.checkoutable" size="small" type="success" plain @click="doCheckout(row)">签退</el-button>
            </div>
          </div>
        </div>

        <el-empty v-if="!loading && !records.length" description="暂无签到记录，审批通过的预约会自动生成签到凭证" />

        <div class="table-pagination">
          <el-pagination
            v-model:current-page="query.current"
            v-model:page-size="query.size"
            :total="total"
            layout="total, sizes, prev, pager, next"
            :page-sizes="[10, 20, 50]"
            @size-change="load"
            @current-change="load"
          />
        </div>
      </template>
    </div>

    <!-- 核销码展示 -->
    <el-dialog v-model="codeVisible" title="签到核销码" width="420px" align-center>
      <div v-if="current" class="code-panel">
        <div class="code-lab">{{ current.labName }}</div>
        <div class="code-range">{{ current.reserveDate }} {{ hhmm(current.startTime) }} - {{ hhmm(current.endTime) }}</div>
        <div class="code-digits">{{ formattedCode }}</div>
        <div class="code-tip">
          请在开放时段内（可提前 30 分钟）到实验室，由管理员/教师输入该码完成核销签到。
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { checkinMine, checkinCode, checkinCheckout } from '@/api'

// 窄屏（<=640px）用卡片列表替代宽表格，避免固定操作列把状态与核销码挤出可视区
const isMobile = ref(window.innerWidth <= 640)
function trackWidth() {
  isMobile.value = window.innerWidth <= 640
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, status: '' })

// 后端时间字段带秒（HH:mm:ss），页面统一按 HH:mm 展示
function hhmm(t) { return t ? String(t).slice(0, 5) : '' }

const statusMap = {
  SIGN_IN_PENDING: '待签到',
  CHECKED_IN: '已签到',
  COMPLETED: '已完成',
  EXPIRED: '已过期',
  CANCELLED: '已取消'
}
const statusTypeMap = {
  SIGN_IN_PENDING: 'warning', CHECKED_IN: 'primary', COMPLETED: 'success',
  EXPIRED: 'info', CANCELLED: 'info'
}
function statusType(s) {
  return statusTypeMap[s] || 'info'
}

const codeVisible = ref(false)
const current = ref(null)
// 核销码按位拆开，避免在窄屏上连成一串不易逐位核对
function formatCode(code) {
  return code ? String(code).split('').join(' ') : ''
}

const formattedCode = computed(() => formatCode(current.value?.checkinCode))

async function load() {
  loading.value = true
  try {
    const data = await checkinMine({ ...query.value })
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function showCode(row) {
  const data = await checkinCode(row.reservationId)
  current.value = data
  codeVisible.value = true
}

function doCheckout(row) {
  ElMessageBox.confirm('确认已离场并签退吗？签退后本场预约将标记为已完成。', '签退确认', {
    confirmButtonText: '确认签退',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await checkinCheckout(row.id)
    ElMessage.success('签退成功')
    load()
  }).catch(() => {})
}

onMounted(() => {
  load()
  window.addEventListener('resize', trackWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', trackWidth)
})
</script>

<style scoped lang="scss">
.code-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 8px 0 4px;
  text-align: center;
}

.code-lab {
  font-size: 16px;
  font-weight: 700;
  color: var(--ink-1);
}

.code-range {
  font-size: 12px;
  color: var(--ink-3);
}

.code-digits {
  margin: 10px 0 4px;
  padding: 16px 22px;
  border-radius: var(--radius-md);
  background: var(--accent-gradient);
  color: #fff;
  font-size: 34px;
  font-weight: 800;
  letter-spacing: 6px;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 10px 26px rgba(91, 140, 255, 0.35);
}

.code-tip {
  font-size: 12px;
  color: var(--ink-3);
  line-height: 1.7;
  max-width: 320px;
}

/* ===== 窄屏卡片列表 ===== */
.checkin-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
  margin-top: 4px;
}

.checkin-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--card-bg-soft);
}

.checkin-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.checkin-card__lab {
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
  overflow-wrap: anywhere;
}

.checkin-card__code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 2px;
  color: var(--ink-1);
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}

.checkin-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
  font-size: 12px;
  color: var(--ink-2);
}

.checkin-card__meta > span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.checkin-card__meta--muted {
  color: var(--ink-3);
}

.checkin-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 2px;
}

.checkin-card__actions .el-button {
  width: 100%;
  margin-left: 0 !important;
}

/* 只有「签退」或「核销码」一个按钮时占满整行 */
.checkin-card__actions .el-button:only-child {
  grid-column: span 2;
}

@media (max-width: 640px) {
  .code-digits {
    font-size: 26px;
    letter-spacing: 4px;
    padding: 14px 16px;
  }
}
</style>
