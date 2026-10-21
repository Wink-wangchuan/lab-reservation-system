<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Tickets /></el-icon>
          预约管理
        </div>
        <div class="toolbar-right">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="load">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="搜索单号/用途" clearable style="width: 200px" @keyup.enter="load" @clear="load" />
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button v-if="canExport" type="success" :loading="exporting" @click="doExport"><el-icon><Download /></el-icon> 导出 Excel</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="单号" prop="reservationNo" width="210" />
        <el-table-column label="实验室" prop="labName" width="170" />
        <el-table-column label="申请人" prop="studentName" width="90" />
        <el-table-column label="审批人" prop="teacherName" width="110" />
        <el-table-column label="日期" prop="reserveDate" width="110" />
        <el-table-column label="时段" width="130">
          <template #default="{ row }">{{ hhmm(row.startTime) }} - {{ hhmm(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="人数" prop="peopleCount" width="70" align="center" />
        <el-table-column label="用途" prop="purpose" min-width="200" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="row.canApprove">
              <el-button link type="success" @click="doApprove(row, 'APPROVE')">通过</el-button>
              <el-button link type="danger" @click="doApprove(row, 'REJECT')">驳回</el-button>
            </template>
            <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

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
    </div>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" title="预约详情" width="480px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="单号" :span="2">{{ current.reservationNo }}</el-descriptions-item>
        <el-descriptions-item label="实验室">{{ current.labName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ current.reserveDate }}</el-descriptions-item>
        <el-descriptions-item label="时段" :span="2">{{ hhmm(current.startTime) }} - {{ hhmm(current.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ current.studentName }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ current.teacherName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人数">{{ current.peopleCount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(current.status)">{{ current.statusDesc }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用途" :span="2">{{ current.purpose }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ current.createTime }}</el-descriptions-item>
        <el-descriptions-item v-if="current.rejectReason" label="驳回原因" :span="2">{{ current.rejectReason }}</el-descriptions-item>
        <el-descriptions-item v-if="current.cancelReason" label="取消原因" :span="2">{{ current.cancelReason }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { reservationPage, reservationApprove, reservationExport } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
// 导出接口需要 reservation:export 权限，教师没有该权限 —— 无权限时不渲染该按钮
const canExport = computed(() => userStore.hasPerm('reservation:export'))

// 后端时间字段带秒（HH:mm:ss），页面统一按 HH:mm 展示
function hhmm(t) { return t ? String(t).slice(0, 5) : '' }

const loading = ref(false)
const exporting = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, status: '', keyword: '' })

const statusMap = {
  PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回',
  CANCELLED: '已取消', COMPLETED: '已完成'
}
const statusTypeMap = {
  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger',
  CANCELLED: 'info', COMPLETED: 'primary'
}
function statusType(s) { return statusTypeMap[s] || 'info' }

const detailVisible = ref(false)
const current = ref(null)

async function load() {
  loading.value = true
  try {
    const data = await reservationPage({ ...query.value })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function doExport() {
  exporting.value = true
  try {
    const res = await reservationExport({
      status: query.value.status || undefined,
      keyword: query.value.keyword || undefined
    })
    const blob = res.data
    // 业务异常时后端返回的是 JSON（HTTP 200 + code≠200），这里转成提示，避免下载出一个坏的 xlsx
    if (blob.type && blob.type.includes('json')) {
      const body = JSON.parse(await blob.text())
      ElMessage.error(body.message || '导出失败')
      return
    }
    const fileName = parseFileName(res.headers['content-disposition'])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    // 网络/权限错误已由响应拦截器提示
  } finally {
    exporting.value = false
  }
}

// 后端以 ISO-8859-1 字节承载中文文件名，需要按字节还原为 UTF-8 才能得到正确中文名
function parseFileName(disposition) {
  const fallback = '预约记录导出.xlsx'
  if (!disposition) return fallback
  const m = /filename\*?=(?:UTF-8'')?["']?([^"';]+)/i.exec(disposition)
  if (!m) return fallback
  try {
    const bytes = Uint8Array.from(m[1], (c) => c.charCodeAt(0))
    return new TextDecoder('utf-8').decode(bytes)
  } catch {
    return fallback
  }
}

// 审批按钮按后端返回的 canApprove 显隐：不能批自己提交的单、教师提交的单仅管理员可批。
// 后端 approve 接口有同样的双重校验，前端只是提前隐藏入口，避免「点了才 40300」。
function doApprove(row, action) {
  const isPass = action === 'APPROVE'
  const text = isPass ? '通过' : '驳回'
  const promptFn = isPass ? Promise.resolve('') : ElMessageBox.prompt('请输入驳回原因', `驳回预约`, { confirmButtonText: '确认驳回', cancelButtonText: '取消' }).then(({ value }) => value)
  promptFn.then(async (comment) => {
    ElMessageBox.confirm(`确认${text}该预约吗？`, '审批确认', { type: 'warning' }).then(async () => {
      await reservationApprove({ reservationId: row.id, action, comment: comment || '' })
      ElMessage.success(`已${text}`)
      load()
    }).catch(() => {})
  }).catch(() => {})
}

function viewDetail(row) {
  current.value = row
  detailVisible.value = true
}

onMounted(load)
</script>