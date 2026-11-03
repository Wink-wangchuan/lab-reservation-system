<template>
  <div class="page-container">
    <div class="glass-card">
      <div class="toolbar">
        <div class="page-title">
          <el-icon><Calendar /></el-icon>
          实验室周日程
        </div>
        <div class="toolbar-right">
          <el-select v-model="labId" placeholder="选择实验室" filterable style="width: 220px" @change="load">
            <el-option v-for="l in labs" :key="l.id" :label="`${l.labName}（${l.location || '-'}）`" :value="l.id" />
          </el-select>
          <el-button-group>
            <el-button @click="shiftWeek(-1)"><el-icon><ArrowLeft /></el-icon> 上一周</el-button>
            <el-button @click="goToday">本周</el-button>
            <el-button @click="shiftWeek(1)">下一周 <el-icon><ArrowRight /></el-icon></el-button>
          </el-button-group>
        </div>
      </div>

      <div v-if="schedule" class="week-range">
        {{ schedule.startDate }} ~ {{ schedule.endDate }} · {{ schedule.labName }}
      </div>

      <div v-loading="loading" class="week-grid">
        <div v-for="day in schedule?.days || []" :key="day.date" class="day-col" :class="{ today: day.date === todayStr }">
          <div class="day-head">
            <span class="day-name">{{ weekdayName(day.weekday) }}</span>
            <span class="day-date">{{ day.date?.slice(5) }}</span>
          </div>

          <div class="slot-list">
            <div v-for="slot in day.slots" :key="slot.id" class="slot-chip">
              {{ hhmm(slot.startTime) }}-{{ hhmm(slot.endTime) }}
              <span v-if="slot.maxPeople" class="slot-limit">上限 {{ slot.maxPeople }} 人</span>
            </div>
            <div v-if="!day.slots.length" class="slot-none">当日未开放</div>
          </div>

          <div class="resv-list">
            <div
              v-for="item in day.reservations"
              :key="item.id"
              class="resv-block"
              :class="[`resv-block--${item.status?.toLowerCase()}`, { mine: item.mine }]"
            >
              <div class="resv-block__time">{{ hhmm(item.startTime) }}-{{ hhmm(item.endTime) }}</div>
              <div class="resv-block__title">{{ item.purpose || '未填写用途' }}</div>
              <div class="resv-block__meta">
                <span>{{ item.studentName }}</span>
                <span>{{ item.peopleCount }} 人</span>
                <el-tag size="small" :type="statusType(item.status)">{{ item.statusDesc }}</el-tag>
              </div>
            </div>
            <div v-if="!day.reservations.length" class="resv-none">暂无预约</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { labList, labWeekSchedule } from '@/api'

const labs = ref([])
const labId = ref(null)
const loading = ref(false)
const schedule = ref(null)

const WEEKDAYS = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
function weekdayName(w) { return WEEKDAYS[w] || '' }
function hhmm(t) { return t ? String(t).slice(0, 5) : '' }

function statusType(s) {
  return { PENDING: 'warning', APPROVED: 'success', COMPLETED: 'primary' }[s] || 'info'
}

const todayStr = (() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})()

/** 当前查看周的周一起始日期 */
const anchor = ref(mondayOf(new Date()))

function mondayOf(date) {
  const d = new Date(date)
  const day = d.getDay() === 0 ? 7 : d.getDay()
  d.setDate(d.getDate() - day + 1)
  d.setHours(0, 0, 0, 0)
  return d
}

function formatDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

async function load() {
  if (!labId.value) return
  loading.value = true
  try {
    schedule.value = await labWeekSchedule({ labId: labId.value, startDate: formatDate(anchor.value) })
  } finally {
    loading.value = false
  }
}

function shiftWeek(delta) {
  const d = new Date(anchor.value)
  d.setDate(d.getDate() + delta * 7)
  anchor.value = d
  load()
}

function goToday() {
  anchor.value = mondayOf(new Date())
  load()
}

onMounted(async () => {
  labs.value = (await labList()) || []
  if (labs.value.length) {
    labId.value = labs.value[0].id
    load()
  }
})
</script>

<style scoped lang="scss">
.week-range {
  margin: 4px 0 10px;
  font-size: 13px;
  color: var(--ink-3);
}

.week-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(150px, 1fr));
  gap: 10px;
  min-height: 200px;
  overflow-x: auto;
}

.day-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--hairline);
  border-radius: 12px;
  background: var(--card-bg-soft);
  min-width: 150px;
}

.day-col.today {
  border-color: var(--brand, #409eff);
  box-shadow: inset 0 0 0 1px var(--brand, #409eff);
}

.day-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.day-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-1);
}

.day-date {
  font-size: 12px;
  color: var(--ink-3);
}

.slot-list,
.resv-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.slot-chip {
  padding: 4px 6px;
  border-radius: 6px;
  font-size: 12px;
  color: var(--ink-2);
  background: rgba(64, 158, 255, 0.1);
}

.slot-limit {
  margin-left: 4px;
  color: var(--ink-3);
}

.slot-none,
.resv-none {
  font-size: 12px;
  color: var(--ink-3);
}

.resv-block {
  padding: 6px 8px;
  border-radius: 8px;
  border-left: 3px solid #909399;
  background: rgba(144, 147, 153, 0.12);
  font-size: 12px;
}

.resv-block--pending {
  border-left-color: #e6a23c;
  background: rgba(230, 162, 60, 0.12);
}

.resv-block--approved {
  border-left-color: #67c23a;
  background: rgba(103, 194, 58, 0.12);
}

.resv-block--completed {
  border-left-color: #409eff;
  background: rgba(64, 158, 255, 0.1);
}

.resv-block.mine {
  outline: 1px dashed var(--brand, #409eff);
}

.resv-block__time {
  font-weight: 600;
  color: var(--ink-1);
}

.resv-block__title {
  margin-top: 1px;
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.resv-block__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 3px;
  color: var(--ink-3);
}

@media (max-width: 900px) {
  .week-grid {
    grid-template-columns: repeat(7, minmax(130px, 1fr));
  }
}
</style>
