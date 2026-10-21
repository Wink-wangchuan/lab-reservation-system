<template>
  <div class="page-container" v-loading="loading">
    <!-- 欢迎横幅 + 快捷入口 -->
    <WelcomeBanner />

    <!-- 概览指标卡 -->
    <div class="stat-grid">
      <StatCard
        v-for="c in cards" :key="c.key"
        :label="c.label" :value="c.value" :suffix="c.suffix"
        :icon="c.icon" :bg="c.bg"
      />
    </div>

    <!-- 左侧待办 + 右侧图表组合（待办属运维视角，非管理角色不渲染） -->
    <div class="mid-grid" :class="{ 'no-todo': !isOperator }">
      <TodoPanel
        v-if="isOperator"
        :pending-approval="overview.pendingApproval ?? 0"
        :pending-repair="overview.pendingRepair ?? 0"
        :fault-device="overview.faultDevice ?? 0"
        :today-reservation="overview.todayReservation ?? 0"
      />

      <div class="chart-stack">
        <ChartBox title="近 7 日预约趋势" :option="trendOption" :height="chartH" color="#5b8cff">
          <template #extra>
            <span class="chart-chip">单位：场次</span>
          </template>
        </ChartBox>

        <!-- 状态分布含待审核/已驳回等管理态，仅管理角色展示 -->
        <ChartBox
          v-if="isOperator"
          title="预约状态分布" :option="statusOption" :height="chartH" color="#35e0a1"
        >
          <template #extra>
            <span class="chart-chip">共 {{ totalReservation }} 笔</span>
          </template>
        </ChartBox>
      </div>
    </div>

    <!-- 底部图表行 -->
    <div class="chart-grid">
      <ChartBox title="热门实验室 TOP10" :option="hotOption" :height="chartH2" color="#f7a35c" />
      <ChartBox title="实验室类型分布" :option="typeOption" :height="chartH2" color="#c46bff" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import echarts from '@/utils/echarts'
import { isDark } from '@/utils/theme'
import { useUserStore } from '@/store/user'
import StatCard from './components/StatCard.vue'
import WelcomeBanner from './components/WelcomeBanner.vue'
import TodoPanel from './components/TodoPanel.vue'
import ChartBox from './components/ChartBox.vue'
import {
  statsOverview, statsHotLabs, statsReservationStatus,
  statsReservationTrend, statsLabType
} from '@/api'

const loading = ref(true)
const overview = ref({})
const userStore = useUserStore()

// 随视口宽度自适应图表高度，保证窄屏下图表仍可读、不畸变
const winW = ref(window.innerWidth)
function trackWidth() { winW.value = window.innerWidth }
onMounted(() => window.addEventListener('resize', trackWidth))
onBeforeUnmount(() => window.removeEventListener('resize', trackWidth))

const chartH = computed(() => winW.value < 640 ? '240px' : winW.value < 1180 ? '270px' : '300px')
const chartH2 = computed(() => winW.value < 640 ? '280px' : '320px')

// 窄屏标记：图表内部布局（边距/标签/柱宽）随之收敛，保证跨屏观感一致
const compact = computed(() => winW.value < 640)

// 图表配色随主题切换：轴/网格/标签/提示框与扇区描边都需要明暗两套取值
const palette = computed(() => {
  const dark = isDark.value
  return {
    axisLine: dark ? 'rgba(150,165,210,0.35)' : 'rgba(140,150,200,0.35)',
    splitLine: dark ? 'rgba(150,165,210,0.16)' : 'rgba(140,150,200,0.14)',
    label: dark ? '#9aa6c8' : '#8a94b8',
    sectorBorder: dark ? 'rgba(21,26,42,0.9)' : 'rgba(255,255,255,0.85)',
    symbolBorder: dark ? '#151a2a' : '#fff'
  }
})

const axisStyle = computed(() => {
  const p = palette.value
  return {
    axisLine: { lineStyle: { color: p.axisLine } },
    axisTick: { show: false },
    axisLabel: { color: p.label },
    splitLine: { lineStyle: { color: p.splitLine } }
  }
})

const tooltipStyle = computed(() => {
  const dark = isDark.value
  return {
    backgroundColor: dark ? 'rgba(26,31,48,0.94)' : 'rgba(255,255,255,0.92)',
    borderColor: dark ? 'rgba(91,140,255,0.4)' : 'rgba(91,140,255,0.25)',
    textStyle: { color: dark ? '#e9efff' : '#1c2540' },
    extraCssText: 'box-shadow:0 8px 24px rgba(60,80,180,0.12);backdrop-filter:blur(10px);border-radius:12px;'
  }
})

// 是否为运维/管理视角：决定驾驶舱里的待办面板、状态分布图与部分指标卡是否展示。
// 驾驶舱面向全体登录用户开放，但「用户总数 / 待审核预约 / 待处理报修 / 故障设备」
// 属于管理侧数据（账号规模、审批与运维待办），学生看到既无意义也会暴露系统内部信息。
const isOperator = computed(() =>
  userStore.hasPerm('reservation:approve') || userStore.hasPerm('system:user:list'))

const cards = computed(() => {
  const o = overview.value
  const all = [
    { key: 'lab', label: '实验室总数', value: o.labTotal ?? 0, icon: 'OfficeBuilding', bg: 'linear-gradient(135deg,#5b8cff,#7a5bff)' },
    { key: 'device', label: '设备总数', value: o.deviceTotal ?? 0, icon: 'Monitor', bg: 'linear-gradient(135deg,#22c1c3,#35e0a1)' },
    { key: 'user', label: '用户总数', value: o.userTotal ?? 0, icon: 'User', bg: 'linear-gradient(135deg,#f77b6f,#f7a35c)', adminOnly: true },
    { key: 'resv', label: '预约总数', value: o.reservationTotal ?? 0, icon: 'Calendar', bg: 'linear-gradient(135deg,#8f6bff,#c46bff)' },
    { key: 'today', label: '今日预约', value: o.todayReservation ?? 0, icon: 'Sunny', bg: 'linear-gradient(135deg,#ffb84d,#ff8a5c)' },
    { key: 'pending', label: '待审核预约', value: o.pendingApproval ?? 0, icon: 'BellFilled', bg: 'linear-gradient(135deg,#5b8cff,#35b3e0)', adminOnly: true },
    { key: 'repair', label: '待处理报修', value: o.pendingRepair ?? 0, icon: 'Tools', bg: 'linear-gradient(135deg,#ff6b81,#ff8a5c)', adminOnly: true },
    { key: 'fault', label: '故障设备', value: o.faultDevice ?? 0, icon: 'WarnTriangleFilled', bg: 'linear-gradient(135deg,#ff4d6d,#f77b6f)', adminOnly: true }
  ]
  return all.filter((c) => !c.adminOnly || isOperator.value)
})

const totalReservation = computed(() => overview.value.reservationTotal ?? 0)

const trendOption = ref({})
const statusOption = ref({})
const hotOption = ref({})
const typeOption = ref({})

// 保留原始数据，跨断点时按新尺寸重建图表选项
const rawTrend = ref([])
const rawStatus = ref([])
const rawHot = ref([])
const rawType = ref([])

function gradient(colors) {
  return new echarts.graphic.LinearGradient(0, 0, 1, 0, colors)
}

function rebuildCharts() {
  buildTrend(rawTrend.value)
  buildStatus(rawStatus.value)
  buildHot(rawHot.value)
  buildType(rawType.value)
}

watch([compact, isDark], rebuildCharts)

async function loadAll() {
  loading.value = true
  try {
    const [ov, trend, status, hot, types] = await Promise.all([
      statsOverview(),
      statsReservationTrend(7),
      statsReservationStatus(),
      statsHotLabs(10),
      statsLabType()
    ])
    overview.value = ov
    rawTrend.value = trend
    rawStatus.value = status
    rawHot.value = hot
    rawType.value = types
    rebuildCharts()
  } finally {
    loading.value = false
  }
}

function buildTrend(trend) {
  const dates = trend.map((t) => t.date.slice(5))
  const counts = trend.map((t) => t.count)
  const c = compact.value
  const p = palette.value
  trendOption.value = {
    tooltip: { trigger: 'axis', ...tooltipStyle.value },
    grid: c
      ? { left: 30, right: 12, top: 26, bottom: 26 }
      : { left: 44, right: 20, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: dates, boundaryGap: false, ...axisStyle.value },
    yAxis: { type: 'value', minInterval: 1, ...axisStyle.value },
    series: [{
      name: '预约数', type: 'line', smooth: true, data: counts,
      symbol: 'circle', symbolSize: c ? 7 : 9,
      lineStyle: { width: 3, color: '#5b8cff' },
      itemStyle: { color: '#5b8cff', borderColor: p.symbolBorder, borderWidth: 2 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(91,140,255,0.32)' },
          { offset: 1, color: 'rgba(91,140,255,0.02)' }
        ])
      }
    }]
  }
}

function buildStatus(status) {
  const pieColors = ['#5b8cff', '#35e0a1', '#ff8a5c', '#ff4d6d', '#8f6bff', '#22c1c3']
  const c = compact.value
  const p = palette.value
  statusOption.value = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 笔（{d}%）', ...tooltipStyle.value },
    legend: { bottom: 0, icon: 'circle', itemWidth: 9, textStyle: { color: p.label } },
    color: pieColors,
    series: [{
      name: '状态', type: 'pie',
      radius: c ? ['40%', '62%'] : ['46%', '68%'],
      center: c ? ['50%', '44%'] : ['50%', '46%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 8, borderColor: p.sectorBorder, borderWidth: 2 },
      // 窄屏改用图例承载名称，避免扇区标签重叠
      label: c
        ? { show: false }
        : { show: true, formatter: '{b}\n{d}%', color: p.label, fontSize: 11 },
      labelLine: { show: !c },
      data: status.map((s) => ({ name: s.name, value: s.value }))
    }]
  }
}

function buildHot(hot) {
  const data = hot.slice().reverse()
  const c = compact.value
  const p = palette.value
  hotOption.value = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, ...tooltipStyle.value },
    grid: c
      ? { left: 74, right: 14, top: 12, bottom: 28 }
      : { left: 110, right: 30, top: 16, bottom: 34 },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: p.splitLine } }, axisLabel: { color: p.label } },
    yAxis: { type: 'category', data: data.map((h) => h.labName || '未知'), ...axisStyle.value },
    series: [{
      type: 'bar', data: data.map((h) => h.bookingCount),
      barWidth: c ? 12 : 16,
      itemStyle: {
        borderRadius: [0, 8, 8, 0],
        color: gradient([{ offset: 0, color: '#5b8cff' }, { offset: 1, color: '#8f6bff' }])
      },
      label: { show: true, position: 'right', color: p.label, fontSize: c ? 10 : 12, padding: [0, 0, 0, 6] }
    }]
  }
}

function buildType(types) {
  const colors = ['#5b8cff', '#22c1c3', '#f7a35c', '#8f6bff', '#ff6b81', '#35e0a1', '#ffb84d']
  const c = compact.value
  const p = palette.value
  typeOption.value = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 间（{d}%）', ...tooltipStyle.value },
    legend: { bottom: 0, icon: 'circle', itemWidth: 9, textStyle: { color: p.label } },
    color: colors,
    series: [{
      name: '类型', type: 'pie',
      radius: c ? ['38%', '62%'] : ['40%', '66%'],
      center: c ? ['50%', '44%'] : ['50%', '46%'],
      itemStyle: { borderRadius: 8, borderColor: p.sectorBorder, borderWidth: 2 },
      label: c
        ? { show: false }
        : { show: true, formatter: '{b}\n{d}%', color: p.label, fontSize: 11 },
      labelLine: { show: !c },
      data: types.map((t) => ({ name: t.name, value: t.value }))
    }]
  }
}

onMounted(loadAll)
</script>

<style scoped lang="scss">
.page-container {
  padding: 4px 2px 30px;
  display: flex;
  flex-direction: column;
  gap: 16px;

  > * { min-width: 0; }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;

  > * { min-width: 0; }
}

.mid-grid {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;

  > * { min-width: 0; }

  // 无待办面板时（学生端）图表独占整行，避免被 300px 列位挤窄
  &.no-todo {
    grid-template-columns: minmax(0, 1fr);
  }
}

.chart-stack {
  display: grid;
  // 自适应列数：两张图时并排，只剩趋势图时自动铺满一行
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;

  > * { min-width: 0; }
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;

  > * { min-width: 0; }
}

.chart-chip {
  font-size: 11px;
  color: var(--ink-3);
  background: rgba(91, 140, 255, 0.1);
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
}

// 中屏：指标卡回落 2 列（8 张卡每行填满）；待办与图表、图表同行改单列堆叠
@media (max-width: 1180px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .mid-grid { grid-template-columns: 1fr; }
  .chart-stack, .chart-grid { grid-template-columns: 1fr; }
}

// 窄屏：收紧间距，保持模块形态一致
@media (max-width: 640px) {
  .page-container { gap: 12px; }
  .stat-grid { gap: 12px; }
}
</style>