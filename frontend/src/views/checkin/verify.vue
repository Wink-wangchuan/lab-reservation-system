<template>
  <div class="page-container">
    <!-- 核销台 -->
    <div class="glass-card verify-card">
      <div class="page-title">
        <el-icon><CircleCheck /></el-icon>
        签到核销台
      </div>
      <div class="verify-body">
        <el-input
          v-model="code"
          class="code-input"
          size="large"
          maxlength="6"
          placeholder="请输入 6 位核销码"
          @keyup.enter="doVerify"
        >
          <template #prefix><el-icon><Stamp /></el-icon></template>
        </el-input>
        <el-button type="primary" size="large" :loading="verifying" @click="doVerify">
          核销签到
        </el-button>
      </div>
      <el-input v-model="remark" placeholder="核销备注（可选，如：已核验学生证件）" clearable />

      <transition name="fade-slide">
        <div v-if="lastResult" class="verify-result" :class="{ ok: lastResult.ok }">
          <el-icon :size="18"><component :is="lastResult.ok ? 'CircleCheck' : 'Warning'" /></el-icon>
          <div class="vr-text">
            <div class="vr-title">{{ lastResult.title }}</div>
            <div class="vr-sub">{{ lastResult.sub }}</div>
          </div>
        </div>
      </transition>
    </div>

    <!-- 签到记录 -->
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Tickets /></el-icon>
          签到记录
        </div>
        <div class="toolbar-right">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="load">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
          <el-input
            v-model="query.keyword"
            :placeholder="isAdmin ? '搜索单号/学生/实验室/核销码' : '搜索单号/学生/实验室'"
            clearable
            style="width: 240px"
            @keyup.enter="load"
            @clear="load"
          />
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
        </div>
      </div>

      <el-skeleton v-if="loading && !records.length" :rows="6" animated />

      <template v-else>
        <div class="table-scroll">
          <el-table v-loading="loading" :data="records" stripe border>
            <!-- 核销码仅超级管理员可见；教师只能现场输入核销，不提前获知 -->
            <el-table-column v-if="isAdmin" label="核销码" width="100" align="center">
              <template #default="{ row }"><span class="mono">{{ row.checkinCode }}</span></template>
            </el-table-column>
            <el-table-column label="预约单号" prop="reservationNo" width="210" />
            <el-table-column label="实验室" prop="labName" min-width="170" />
            <el-table-column label="使用人" prop="userName" width="100" />
            <el-table-column label="日期" prop="reserveDate" width="110" />
            <el-table-column label="时段" width="130">
              <template #default="{ row }">{{ hhmm(row.startTime) }} - {{ hhmm(row.endTime) }}</template>
            </el-table-column>
            <el-table-column label="签到时间" width="180">
              <template #default="{ row }">{{ row.checkinTime || '-' }}</template>
            </el-table-column>
            <el-table-column label="核销人" prop="checkinByName" width="100" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ row.statusDesc }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right" align="center">
              <template #default="{ row }">
                <el-button v-if="row.checkoutable" link type="success" @click="doCheckout(row)">签退</el-button>
                <span v-else class="dash">-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-empty v-if="!loading && !records.length" description="暂无签到记录" />

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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { checkinVerify, checkinPage, checkinCheckout } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
// 超级管理员可查看核销码；教师只有核销权，列表不显示核销码
const isAdmin = computed(() => (userStore.roles || []).includes('SUPER_ADMIN'))

const code = ref('')
const remark = ref('')
const verifying = ref(false)
const lastResult = ref(null)

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, status: '', keyword: '' })

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

async function load() {
  loading.value = true
  try {
    const data = await checkinPage({ ...query.value })
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function doVerify() {
  const value = code.value.trim()
  if (!value) {
    ElMessage.warning('请输入核销码')
    return
  }
  verifying.value = true
  try {
    const data = await checkinVerify({ checkinCode: value, remark: remark.value })
    lastResult.value = {
      ok: true,
      title: '签到成功',
      sub: `${data.userName} · ${data.labName} · ${data.reserveDate} ${data.startTime}-${data.endTime}`
    }
    ElMessage.success('核销签到成功')
    code.value = ''
    remark.value = ''
    load()
  } catch (e) {
    lastResult.value = {
      ok: false,
      title: '签到失败',
      sub: e?.message || '核销码无效或不在可签到时段'
    }
  } finally {
    verifying.value = false
  }
}

function doCheckout(row) {
  ElMessageBox.confirm(`确认为「${row.userName}」签退吗？`, '签退确认', {
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
.verify-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.verify-body {
  display: flex;
  gap: 12px;
  align-items: center;
}

.code-input {
  flex: 1;
  min-width: 0;

  :deep(.el-input__inner) {
    letter-spacing: 6px;
    font-weight: 700;
    font-size: 18px;
  }
}

.verify-result {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 107, 129, 0.35);
  background: rgba(255, 107, 129, 0.1);
  color: #d3455e;

  &.ok {
    border-color: rgba(53, 224, 161, 0.4);
    background: rgba(53, 224, 161, 0.12);
    color: #1c9c72;
  }
}

.vr-title {
  font-size: 14px;
  font-weight: 700;
}

.vr-sub {
  font-size: 12px;
  opacity: 0.85;
}

.mono {
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-weight: 700;
  letter-spacing: 1px;
  color: var(--accent);
}

.dash {
  color: var(--ink-3);
}

@media (max-width: 640px) {
  .verify-body {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
