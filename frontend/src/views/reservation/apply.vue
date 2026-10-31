<template>
  <div class="page-container">
    <!-- 顶部工具栏 -->
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Calendar /></el-icon>
          我的预约
        </div>
        <div class="toolbar-right">
          <el-button type="primary" @click="openApply">
            <el-icon><Plus /></el-icon> 发起预约
          </el-button>
        </div>
      </div>

      <el-table v-if="!isMobile" v-loading="loading" :data="records" stripe border>
        <el-table-column label="预约单号" prop="reservationNo" width="210" />
        <el-table-column label="实验室" prop="labName" width="170" />
        <el-table-column label="日期" width="110">
          <template #default="{ row }">{{ row.reserveDate }}</template>
        </el-table-column>
        <el-table-column label="时段" width="130">
          <template #default="{ row }">{{ hhmm(row.startTime) }} - {{ hhmm(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="人数" prop="peopleCount" width="70" align="center" />
        <el-table-column label="用途" prop="purpose" min-width="200" />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="canCancel(row)"
              link type="danger" @click="doCancel(row)"
            >取消</el-button>
            <el-button link type="primary" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 窄屏改用卡片列表：单号与用途在表格里会被固定操作列挤出可视区 -->
      <div v-else v-loading="loading" class="mine-cards">
        <div v-for="row in records" :key="row.id" class="mine-card">
          <div class="mine-card__head">
            <span class="mine-card__no">{{ row.reservationNo }}</span>
            <el-tag :type="statusType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
          </div>
          <div class="mine-card__title">{{ row.labName || '-' }}</div>
          <div class="mine-card__grid">
            <div class="mine-card__item">
              <span class="mine-card__label">日期</span>
              <span class="mine-card__value">{{ row.reserveDate }}</span>
            </div>
            <div class="mine-card__item">
              <span class="mine-card__label">时段</span>
              <span class="mine-card__value">{{ hhmm(row.startTime) }} - {{ hhmm(row.endTime) }}</span>
            </div>
            <div class="mine-card__item">
              <span class="mine-card__label">人数</span>
              <span class="mine-card__value">{{ row.peopleCount }}</span>
            </div>
          </div>
          <div class="mine-card__item mine-card__purpose">
            <span class="mine-card__label">用途</span>
            <span class="mine-card__value">{{ row.purpose || '-' }}</span>
          </div>
          <div class="mine-card__actions">
            <el-button v-if="canCancel(row)" size="small" type="danger" plain @click="doCancel(row)">取消预约</el-button>
            <el-button size="small" type="primary" plain @click="viewDetail(row)">详情</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && !records.length" description="暂无预约" :image-size="80" />
      </div>

      <div class="table-pagination">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 发起预约对话框 -->
    <el-dialog v-model="applyVisible" title="发起实验室预约" width="560px" :close-on-click-modal="false">
      <el-form ref="applyFormRef" class="form-responsive" :model="applyForm" :rules="applyRules" label-width="88px">
        <el-form-item label="实验室" prop="labId">
          <el-select v-model="applyForm.labId" placeholder="请选择实验室" style="width: 100%" @change="onLabChange">
            <el-option v-for="l in labs" :key="l.id" :label="`${l.labName}（${l.location}）`" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期" prop="reserveDate">
          <el-date-picker
            v-model="applyForm.reserveDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :disabled-date="(d) => d < new Date()"
            @change="onDateChange"
          />
        </el-form-item>
        <el-form-item label="开放时段" prop="startTime">
          <el-select v-model="applyForm.startTime" placeholder="选择可预约时段" style="width: 100%" @change="onSlotChange">
            <el-option
              v-for="s in availableSlots"
              :key="s.id"
              :label="slotLabel(s)"
              :value="s.startTime"
              :disabled="s.occupied"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-show="applyForm.startTime" label="结束时间">
          <el-input :model-value="hhmm(applyForm.endTime)" disabled />
        </el-form-item>
        <el-form-item v-if="applyForm.reserveDate && !availableSlots.length" label=" ">
          <span class="slot-empty-tip">该实验室当日无可预约时段，请更换实验室或日期</span>
        </el-form-item>
        <el-form-item label="预约人数" prop="peopleCount">
          <div class="people-block">
            <el-input-number v-model="applyForm.peopleCount" :min="1" :max="peopleMax" />
            <span v-if="peopleTip" class="people-tip">{{ peopleTip }}</span>
          </div>
        </el-form-item>
        <el-form-item label="实验用途" prop="purpose">
          <el-input v-model="applyForm.purpose" type="textarea" :rows="3" maxlength="512" show-word-limit placeholder="请输入实验/课程名称与用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doSubmit">提交预约</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="预约详情" width="480px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="预约单号" :span="2">{{ current.reservationNo }}</el-descriptions-item>
        <el-descriptions-item label="实验室">{{ current.labName }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ current.reserveDate }}</el-descriptions-item>
        <el-descriptions-item label="时段" :span="2">{{ hhmm(current.startTime) }} - {{ hhmm(current.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="人数">{{ current.peopleCount }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(current.status)">{{ current.statusDesc }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="用途" :span="2">{{ current.purpose }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ current.teacherName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ current.createTime }}</el-descriptions-item>
        <el-descriptions-item v-if="current.rejectReason" label="驳回原因" :span="2">{{ current.rejectReason }}</el-descriptions-item>
        <el-descriptions-item v-if="current.cancelReason" label="取消原因" :span="2">{{ current.cancelReason }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { reservationMine, reservationSubmit, reservationCancel, labList, slotAvailable } from '@/api'

// 窄屏（<=640px）用卡片列表替代宽表格，避免固定操作列把单号/用途挤出可视区
const isMobile = ref(window.innerWidth <= 640)
function trackWidth() {
  isMobile.value = window.innerWidth <= 640
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = ref({ current: 1, size: 10 })

// 申请
const applyVisible = ref(false)
const applyFormRef = ref()
const submitting = ref(false)
const labs = ref([])
const availableSlots = ref([])
const applyForm = ref({ labId: null, reserveDate: null, startTime: '', endTime: '', peopleCount: 1, purpose: '' })

const applyRules = {
  labId: [{ required: true, message: '请选择实验室', trigger: 'change' }],
  reserveDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择时段', trigger: 'change' }],
  peopleCount: [{ required: true, message: '请输入人数', trigger: 'blur' }],
  purpose: [{ required: true, message: '请输入用途', trigger: 'blur' }]
}

// 详情
const detailVisible = ref(false)
const current = ref(null)

const statusTypeMap = {
  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger',
  CANCELLED: 'info', COMPLETED: 'primary', DRAFT: 'info'
}
function statusType(s) { return statusTypeMap[s] || 'info' }

// 后端时间字段带秒（HH:mm:ss），页面统一按 HH:mm 展示
function hhmm(t) { return t ? String(t).slice(0, 5) : '' }

// 取消按钮的展示条件（与后端校验保持一致）：
//  - 待审核：可随时撤回（包括已过期的僵尸申请）
//  - 已通过：只能在预约开始前取消，已开始/已结束则不再提供按钮
function canCancel(row) {
  if (row.status === 'PENDING') return true
  if (row.status !== 'APPROVED') return false
  const startAt = new Date(`${String(row.reserveDate).slice(0, 10)}T${hhmm(row.startTime)}:00`)
  return !Number.isNaN(startAt.getTime()) && Date.now() < startAt.getTime()
}

async function load() {
  loading.value = true
  try {
    const data = await reservationMine({ current: query.value.current, size: query.value.size })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function openApply() {
  const data = await labList()
  labs.value = data
  resetForm()
  applyVisible.value = true
}

async function onLabChange() {
  applyForm.value.reserveDate = null
  applyForm.value.startTime = ''
  applyForm.value.endTime = ''
  availableSlots.value = []
}

async function onDateChange() {
  applyForm.value.startTime = ''
  applyForm.value.endTime = ''
  availableSlots.value = []
  if (!applyForm.value.labId || !applyForm.value.reserveDate) return
  try {
    availableSlots.value = await slotAvailable(applyForm.value.labId, applyForm.value.reserveDate)
  } catch {
    // 该实验室当日未配置开放时段：保持空列表，由表单内的空状态提示引导用户换日期/实验室
    availableSlots.value = []
  }
}

// 选中时段后同步带出结束时间，保证提交参数完整（原实现此处为空函数，导致 endTime 永远为空）
function onSlotChange(startTime) {
  const slot = availableSlots.value.find((s) => s.startTime === startTime)
  applyForm.value.endTime = slot ? slot.endTime : ''
  // 同一时段可多人共享，人数不能超过该时段剩余可约人数
  if (slot && slot.remainingPeople != null) {
    applyForm.value.peopleCount = Math.min(applyForm.value.peopleCount,
      Math.max(1, slot.remainingPeople))
  }
}

const selectedSlot = computed(
  () => availableSlots.value.find((s) => s.startTime === applyForm.value.startTime)
)

// 人数输入框上限：remainingPeople 为 null 表示不限
const peopleMax = computed(() => {
  const remaining = selectedSlot.value?.remainingPeople
  return remaining == null ? 200 : Math.max(1, Math.min(200, remaining))
})

const peopleTip = computed(() => {
  const slot = selectedSlot.value
  if (!slot) return ''
  if (slot.exclusive) {
    return slot.occupied ? '该时段已被独占预约' : '该实验室为整间独占，该时段可整间预约'
  }
  if (slot.remainingPeople == null) return `该时段人数不限，已约 ${slot.bookedPeople || 0} 人`
  if (slot.remainingPeople === 0) return '该时段已约满，请更换时段'
  return `该时段剩余可约 ${slot.remainingPeople} 人（已约 ${slot.bookedPeople || 0} 人）`
})

// 时段下拉文案：带上剩余可约人数，让用户选时段时就能看到还能不能挤进去
function slotLabel(s) {
  const base = `${s.slotName}  ${hhmm(s.startTime)}-${hhmm(s.endTime)}`
  if (s.exclusive) return `${base}  ${s.occupied ? '已占用' : '整间独占'}`
  if (s.remainingPeople == null) return base
  return `${base}  ${s.remainingPeople > 0 ? `余 ${s.remainingPeople} 人` : '已满'}`
}

function doSubmit() {
  applyFormRef.value && applyFormRef.value.validate(async (valid) => {
    if (!valid) return
    const slot = availableSlots.value.find((s) => s.startTime === applyForm.value.startTime)
    if (!slot) return
    submitting.value = true
    try {
      await reservationSubmit({
        labId: applyForm.value.labId,
        reserveDate: applyForm.value.reserveDate,
        startTime: applyForm.value.startTime,
        // 结束时间以所选时段为准，避免表单字段与时段不一致
        endTime: slot.endTime,
        peopleCount: applyForm.value.peopleCount,
        purpose: applyForm.value.purpose
      })
      ElMessage.success('预约已提交，等待审批')
      applyVisible.value = false
      resetForm()
      load()
    } catch {
      // 拦截器提示（如冲突/锁定）
    } finally {
      submitting.value = false
    }
  })
}

function doCancel(row) {
  ElMessageBox.prompt('请输入取消原因', '取消预约', {
    confirmButtonText: '确认取消',
    cancelButtonText: '再想想',
  }).then(async ({ value }) => {
    await reservationCancel(row.id, value)
    ElMessage.success('预约已取消')
    load()
  }).catch(() => {})
}

function viewDetail(row) {
  current.value = row
  detailVisible.value = true
}

// 关闭/提交后清空表单，避免下次打开残留上一次的选择
function resetForm() {
  applyForm.value = { labId: null, reserveDate: null, startTime: '', endTime: '', peopleCount: 1, purpose: '' }
  availableSlots.value = []
  applyFormRef.value?.clearValidate()
}

onMounted(() => {
  load()
  window.addEventListener('resize', trackWidth)
})

onUnmounted(() => {
  window.removeEventListener('resize', trackWidth)
})
</script>

<style scoped>
.slot-empty-tip {
  font-size: 12px;
  color: var(--ink-3);
}

/* 人数输入 + 剩余可约提示：共享预约时提示该时段还能进几个人 */
.people-block {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 12px;
}

.people-tip {
  font-size: 12px;
  color: var(--ink-3);
}

/* ===== 窄屏卡片列表 ===== */
.mine-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 80px;
  margin-top: 4px;
}

.mine-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--card-bg-soft);
}

.mine-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.mine-card__no {
  font-size: 12px;
  color: var(--ink-3);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mine-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mine-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px 12px;
}

.mine-card__item,
.mine-card__purpose {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
}

.mine-card__label {
  flex: none;
  font-size: 12px;
  color: var(--ink-3);
}

.mine-card__value {
  min-width: 0;
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.mine-card__purpose {
  align-items: flex-start;
}

.mine-card__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 2px;
}

.mine-card__actions .el-button {
  width: 100%;
  margin-left: 0 !important;
}

/* 仅「详情」一个按钮时占满整行 */
.mine-card__actions .el-button:only-child {
  grid-column: 1 / -1;
}

@media (max-width: 640px) {
  .slot-empty-tip {
    line-height: 1.6;
  }

  .mine-card__title {
    font-size: 14px;
  }
}
</style>