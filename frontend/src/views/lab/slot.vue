<template>
  <div class="page-container">
    <!-- 顶部：新增 / 编辑表单 -->
    <div class="glass-card form-card">
      <div class="card-head">
        <div class="page-title">
          <el-icon><Timer /></el-icon>
          开放时段配置
        </div>
        <div class="head-right">
          <el-select v-model="currentLabId" placeholder="请选择实验室" style="width: 240px" @change="loadSlots">
            <el-option v-for="l in labs" :key="l.id" :label="`${l.labName}（${l.location}）`" :value="l.id" />
          </el-select>
        </div>
      </div>

      <el-empty v-if="!currentLabId" description="请先选择实验室查看其开放时段" />

      <template v-else>
        <div class="form-hint">
          系统按<b>自然周</b>配置：此处设置的是每周固定的开放时段，学生按<b>具体日期</b>预约时会自动匹配该日期所属的星期。
          括号内为该星期最近一次对应的日期。
        </div>

        <div class="slot-form">
          <div class="field">
            <span class="field-label">星期</span>
            <el-select v-model="form.weekDay" style="width: 100%">
              <el-option v-for="o in weekOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </div>
          <div class="field">
            <span class="field-label">时段名称</span>
            <el-input v-model="form.slotName" placeholder="如 上午第一节" />
          </div>
          <div class="field">
            <span class="field-label">开始时间</span>
            <el-time-picker
              v-model="form.startTime"
              format="HH:mm"
              value-format="HH:mm"
              placeholder="开始时间"
              style="width: 100%"
            />
          </div>
          <div class="field">
            <span class="field-label">结束时间</span>
            <el-time-picker
              v-model="form.endTime"
              format="HH:mm"
              value-format="HH:mm"
              placeholder="结束时间"
              style="width: 100%"
            />
          </div>
          <div class="field">
            <span class="field-label">最大人数</span>
            <el-input-number v-model="form.maxPeople" :min="0" :max="999" controls-position="right" style="width: 100%" />
          </div>
          <div class="field">
            <span class="field-label">状态</span>
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </div>
          <div class="field field-actions">
            <el-button v-if="editingIndex === -1" type="primary" @click="applyForm">
              <el-icon><Plus /></el-icon> 添加到列表
            </el-button>
            <template v-else>
              <el-button type="primary" @click="applyForm">
                <el-icon><Check /></el-icon> 更新列表项
              </el-button>
              <el-button @click="cancelEdit">取消编辑</el-button>
            </template>
          </div>
        </div>

        <div class="slot-meta">
          已配置 <b>{{ slots.length }}</b> 个时段
          <el-tag v-if="editingIndex !== -1" size="small" type="warning" effect="plain" class="editing-tag">
            正在编辑列表第 {{ editingIndex + 1 }} 项
          </el-tag>
        </div>

        <!-- 列表展示 -->
        <el-table v-loading="loading" :data="slots" stripe border class="slot-table">
          <el-table-column label="星期" width="170">
            <template #default="{ row }">
              <el-tag size="small">{{ weekLabel(row.weekDay) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时段名称" prop="slotName" min-width="160" />
          <el-table-column label="开始时间" prop="startTime" width="100" align="center" />
          <el-table-column label="结束时间" prop="endTime" width="100" align="center" />
          <el-table-column label="最大人数" prop="maxPeople" width="100" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" align="center">
            <template #default="{ $index }">
              <el-button link type="primary" @click="editRow($index)">编辑</el-button>
              <el-button link type="danger" @click="removeRow($index)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="该实验室还没有配置任何开放时段，请在上方表单添加" :image-size="70" />
          </template>
        </el-table>

        <div class="btn-row">
          <el-button :loading="saving" type="success" @click="saveAll">
            <el-icon><Check /></el-icon> 保存全部配置
          </el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { labList, slotList, slotSave } from '@/api'

const labs = ref([])
const currentLabId = ref(null)
const slots = ref([])
const loading = ref(false)
const saving = ref(false)
const editingIndex = ref(-1)

const weekNames = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }

// 当前星期（周一到周日按 1-7 记）
function todayWeek() {
  return new Date().getDay() === 0 ? 7 : new Date().getDay()
}

// 该星期最近一次对应的日期（含今天），用于把「星期配置」映射到学生端的「日期申请」
function dateOfWeekday(w) {
  const now = new Date()
  const diff = (w - todayWeek() + 7) % 7
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() + diff)
}

function mmdd(d) {
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 选项/列表统一展示「周X（最近日期）」，让星期与日期的对应关系一目了然
function weekLabel(w) {
  const d = dateOfWeekday(w)
  return w === todayWeek() ? `${weekNames[w]}（今天 ${mmdd(d)}）` : `${weekNames[w]}（${mmdd(d)}）`
}

const weekOptions = computed(() =>
  [1, 2, 3, 4, 5, 6, 7].map((w) => ({ value: w, label: weekLabel(w) }))
)

// 新增时的默认值：时间预填为常见的「上午第一节」区间，减少填写成本
const DEFAULT_FORM = { weekDay: 1, slotName: '', startTime: '08:00', endTime: '09:40', maxPeople: 40, status: 1 }

const form = ref({ ...DEFAULT_FORM })

function resetForm() {
  form.value = { ...DEFAULT_FORM }
  editingIndex.value = -1
}

// 新增时的默认星期：优先落在该实验室还没配置过的星期，避免一进页面就与已有配置冲突
function firstFreeWeekday() {
  const used = new Set(slots.value.map((s) => s.weekDay))
  for (const w of [1, 2, 3, 4, 5, 6, 7]) {
    if (!used.has(w)) return w
  }
  return 1
}

async function loadLabs() {
  const data = await labList()
  labs.value = data
  if (data.length) {
    currentLabId.value = data[0].id
    loadSlots()
  }
}

async function loadSlots() {
  if (!currentLabId.value) return
  loading.value = true
  resetForm()
  try {
    const data = await slotList(currentLabId.value)
    slots.value = (data || []).map((s) => ({
      weekDay: s.weekDay,
      slotName: s.slotName,
      startTime: hhmm(s.startTime),
      endTime: hhmm(s.endTime),
      maxPeople: s.maxPeople,
      status: s.status
    }))
    sortSlots()
    // 默认星期跟随该实验室的配置情况，避免新增时立刻与已有时段冲突
    form.value.weekDay = firstFreeWeekday()
  } finally {
    loading.value = false
  }
}

// 后端返回 HH:mm:ss，页面统一按 HH:mm 展示与提交（ISO_LOCAL_TIME 可直接解析）
function hhmm(t) {
  return t ? String(t).slice(0, 5) : ''
}

function sortSlots() {
  slots.value.sort((a, b) => (a.weekDay - b.weekDay) || String(a.startTime).localeCompare(String(b.startTime)))
}

function editRow(index) {
  const row = slots.value[index]
  form.value = { ...row }
  editingIndex.value = index
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function cancelEdit() {
  resetForm()
}

function removeRow(index) {
  slots.value.splice(index, 1)
  // 删除项在编辑项之前时，编辑下标需要同步左移
  if (editingIndex.value === index) {
    resetForm()
  } else if (editingIndex.value > index) {
    editingIndex.value -= 1
  }
}

/**
 * 把表单内容写入列表（新增或覆盖被编辑项），本地校验后再入列
 */
function applyForm() {
  const f = form.value
  if (!f.slotName || !f.slotName.trim()) {
    ElMessage.warning('请填写时段名称')
    return
  }
  if (!f.startTime || !f.endTime) {
    ElMessage.warning('请选择开始时间与结束时间')
    return
  }
  if (f.startTime >= f.endTime) {
    ElMessage.warning('结束时间必须晚于开始时间')
    return
  }
  const row = {
    weekDay: f.weekDay,
    slotName: f.slotName.trim(),
    startTime: f.startTime,
    endTime: f.endTime,
    maxPeople: f.maxPeople,
    status: f.status
  }
  // 同一星期内时段不允许重叠（避免出现两个完全一样的时段）
  const overlapped = slots.value.some((s, i) =>
    i !== editingIndex.value
    && s.weekDay === row.weekDay
    && row.startTime < s.endTime
    && row.endTime > s.startTime
  )
  if (overlapped) {
    ElMessage.warning(`${weekNames[row.weekDay]}已存在与该时段重叠的配置，请调整时间`)
    return
  }
  if (editingIndex.value === -1) {
    slots.value.push(row)
  } else {
    slots.value[editingIndex.value] = row
  }
  sortSlots()
  resetForm()
  ElMessage.success('已写入列表，点击「保存全部配置」后生效')
}

async function saveAll() {
  if (!currentLabId.value) return
  if (!slots.value.length) {
    ElMessage.warning('请至少添加一个时段')
    return
  }
  saving.value = true
  try {
    await slotSave(currentLabId.value, slots.value)
    ElMessage.success('开放时段已保存')
    await loadSlots()
  } finally {
    saving.value = false
  }
}

onMounted(loadLabs)
</script>

<style scoped>
.form-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-hint {
  font-size: 12px;
  line-height: 1.7;
  color: var(--ink-3);
  background: var(--card-bg-soft);
  border: 1px solid var(--hairline);
  border-radius: 10px;
  padding: 8px 12px;
}

.form-hint b {
  color: var(--ink-2);
  font-weight: 600;
}

.slot-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px 16px;
  align-items: end;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.field-label {
  font-size: 12px;
  color: var(--ink-3);
}

.field-actions {
  flex-direction: row;
  align-items: center;
  gap: 10px;
}

.slot-meta {
  font-size: 13px;
  color: var(--ink-2);
  display: flex;
  align-items: center;
  gap: 10px;
}

.editing-tag {
  font-weight: 500;
}

.slot-table {
  width: 100%;
}

.btn-row {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .slot-form {
    grid-template-columns: 1fr;
  }

  .field-actions {
    justify-content: flex-start;
  }
}
</style>
