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
        <div class="table-scroll">
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
import { ref, computed, onMounted } from 'vue'
import { checkinMine, checkinCode, checkinCheckout } from '@/api'

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
const formattedCode = computed(() => {
  const code = current.value?.checkinCode || ''
  return code ? code.split('').join(' ') : ''
})

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

onMounted(load)
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

@media (max-width: 640px) {
  .code-digits {
    font-size: 26px;
    letter-spacing: 4px;
    padding: 14px 16px;
  }
}
</style>
