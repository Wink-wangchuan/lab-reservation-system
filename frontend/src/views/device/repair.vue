<template>
  <div class="page-container">
    <!-- 闭环步骤可视化 -->
    <div class="glass-card flow-card">
      <div class="page-title" style="margin-bottom:14px">
        <el-icon><MagicStick /></el-icon>
        报修闭环流程
      </div>
      <el-steps :active="flowActive" align-center finish-status="success" process-status="process">
        <el-step title="提交报修" description="锁定设备" icon="EditPen" />
        <el-step title="开始维修" description="维修中" icon="Tools" />
        <el-step title="完成维修" description="自动解锁" icon="Finished" />
      </el-steps>
    </div>

    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Tickets /></el-icon>
          {{ canListAll ? '报修工单' : '我的报修' }}
        </div>
        <div class="toolbar-right">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="load">
            <el-option v-for="(v, k) in statusMap" :key="k" :label="v" :value="k" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="搜索单号/设备" clearable style="width: 180px" @keyup.enter="load" @clear="load" />
          <el-button type="primary" @click="load"><el-icon><Search /></el-icon> 查询</el-button>
          <el-button v-if="canSubmit" type="warning" @click="openSubmit"><el-icon><Warning /></el-icon> 提交报修</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" stripe border>
        <el-table-column label="报修单号" prop="repairNo" width="210" />
        <el-table-column label="设备" prop="deviceName" min-width="180" />
        <el-table-column label="报修人" prop="reporterName" width="90" />
        <el-table-column label="故障描述" prop="faultDesc" min-width="220" />
        <el-table-column label="状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="维修人" prop="handlerName" width="90">
          <template #default="{ row }">{{ row.handlerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="维修备注" prop="handleRemark" min-width="170">
          <template #default="{ row }">{{ row.handleRemark || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 维修动作属处理人权限；取消报修后端校验「仅报修人本人」，故同时判断归属 -->
            <template v-if="row.status === 'PENDING'">
              <el-button v-if="canHandle" link type="warning" @click="doStart(row)">开始维修</el-button>
              <el-button v-if="canSubmit && isMine(row)" link type="danger" @click="doCancel(row)">取消</el-button>
            </template>
            <template v-else-if="row.status === 'REPAIRING' && canHandle">
              <el-button link type="success" @click="doComplete(row)">完成维修</el-button>
            </template>
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

    <!-- 提交报修 -->
    <el-dialog v-model="submitVisible" title="提交设备报修" width="520px" :close-on-click-modal="false">
      <el-form ref="submitFormRef" class="form-responsive" :model="submitForm" :rules="submitRules" label-width="90px">
        <el-form-item label="设备" prop="deviceId">
          <el-select v-model="submitForm.deviceId" placeholder="选择故障设备" filterable style="width: 100%">
            <el-option v-for="d in devices" :key="d.id" :label="`${d.deviceName}（${d.deviceCode}）`" :value="d.id" :disabled="d.faultStatus === 1" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障描述" prop="faultDesc">
          <el-input v-model="submitForm.faultDesc" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请详细描述故障现象，提交后设备将锁定，直至维修完成解锁" />
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon title="提交后该设备将被锁定，所属实验室暂时无法预约" />
      </el-form>
      <template #footer>
        <el-button @click="submitVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="doSubmit">提交报修</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import {
  repairPage, repairMine, repairSubmit, repairStart, repairComplete,
  repairCancel, repairDeviceOptions
} from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

// 页面按权限自适应：
//  - 有「报修列表」权限（管理/处理人）→ 看全部工单，可执行维修动作
//  - 只有「提交报修」权限（普通用户）→ 只看自己提交的工单，仅能提交与取消
// 这样普通用户进入本页不再因调用管理端接口而报「无权限访问该资源」
const canListAll = computed(() => userStore.hasPerm('device:repair:list'))
const canHandle = computed(() => userStore.hasPerm('device:repair:handle'))
const canSubmit = computed(() => userStore.hasPerm('device:repair:submit'))

// 后端「取消报修」只允许报修人本人操作（越权会被 40300 拒绝）。
// 有列表权限的角色能看到他人工单，因此按钮还需判断归属，避免「看得见点不了」。
const myUserId = computed(() => String(userStore.userInfo?.userId ?? ''))
function isMine(row) {
  return String(row.reporterId ?? '') === myUserId.value
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10, status: '', keyword: '' })

const statusMap = { PENDING: '待处理', REPAIRING: '维修中', COMPLETED: '已完成', CANCELLED: '已取消' }
const statusTypeMap = { PENDING: 'warning', REPAIRING: 'primary', COMPLETED: 'success', CANCELLED: 'info' }
function statusType(s) { return statusTypeMap[s] || 'info' }

// 闭环演示：完成维修后停留 10 秒，再回退到「提交报修」，便于反复演示整条链路
const FLOW_RESET_DELAY = 10000
const flowReset = ref(false)
let flowResetTimer = null

// 步骤跟随最新一条工单（列表按创建时间倒序），保证新提报修时从第一步重新开始
const flowActive = computed(() => {
  if (flowReset.value) return 0
  return { PENDING: 0, REPAIRING: 1, COMPLETED: 2, CANCELLED: 0 }[records.value[0]?.status] ?? 0
})

watch(() => records.value[0]?.status, (status) => {
  clearTimeout(flowResetTimer)
  flowReset.value = false
  if (status === 'COMPLETED') {
    flowResetTimer = setTimeout(() => { flowReset.value = true }, FLOW_RESET_DELAY)
  }
}, { immediate: true })

onBeforeUnmount(() => clearTimeout(flowResetTimer))

const devices = ref([])
const submitVisible = ref(false)
const submitFormRef = ref()
const submitting = ref(false)
const submitForm = ref({ deviceId: null, faultDesc: '' })
const submitRules = {
  deviceId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  faultDesc: [{ required: true, message: '请输入故障描述', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    // 无「报修列表」权限时改查「我的报修」，避免调用管理端接口返回 403
    const data = canListAll.value
      ? await repairPage({ ...query.value })
      : await repairMine({ ...query.value })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function loadDevices() {
  // 用报修专用的精简清单：普通用户无需设备台账权限即可选设备
  devices.value = await repairDeviceOptions()
}

async function openSubmit() {
  if (!devices.value.length) await loadDevices()
  submitVisible.value = true
}

function doSubmit() {
  submitFormRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await repairSubmit({ ...submitForm.value })
      ElMessage.success('报修已提交，设备已锁定')
      submitVisible.value = false
      load()
    } catch {
    } finally {
      submitting.value = false
    }
  })
}

function doStart(row) {
  ElMessageBox.prompt('请输入维修计划/措施', '开始维修', {
    confirmButtonText: '开始维修', cancelButtonText: '取消'
  }).then(async ({ value }) => {
    await repairStart(row.id, value)
    ElMessage.success('已开始维修，设备保持锁定')
    load()
  }).catch(() => {})
}

function doComplete(row) {
  ElMessageBox.prompt('请输入维修结果与验收说明', '完成维修', {
    confirmButtonText: '维修完成', cancelButtonText: '取消'
  }).then(async ({ value }) => {
    await repairComplete(row.id, value)
    ElMessage.success('维修完成，设备已自动解锁')
    load()
  }).catch(() => {})
}

function doCancel(row) {
  ElMessageBox.prompt('请输入取消原因', '取消报修', {
    confirmButtonText: '确认取消', cancelButtonText: '再想想'
  }).then(async ({ value }) => {
    await repairCancel(row.id, value)
    ElMessage.success('报修已取消，设备已解锁')
    load()
  }).catch(() => {})
}

onMounted(load)
</script>