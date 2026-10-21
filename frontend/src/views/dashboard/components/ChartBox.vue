<template>
  <div class="chart-wrap glass-card">
    <div class="chart-head">
      <div class="chart-title">
        <span class="title-dot" :style="{ background: color }"></span>
        {{ title }}
      </div>
      <slot name="extra"></slot>
    </div>
    <div ref="chartRef" class="chart-body" :style="{ height: height }"></div>
    <!-- 图例等附属信息：由父级以普通 HTML 排版，避免在窄屏下覆盖图表本体 -->
    <slot name="footer"></slot>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import echarts from '@/utils/echarts'

const props = defineProps({
  title: String,
  option: { type: Object, required: true },
  height: { type: String, default: '300px' },
  color: { type: String, default: '#5b8cff' }
})

const chartRef = ref(null)
let chart = null
let ro = null

// 容器尚无尺寸时初始化，ECharts 会告警 "Can't get DOM width or height"，
// 故等容器具备宽高后再 init；未就绪时由 ResizeObserver 触发重试
function ensureChart() {
  if (!chartRef.value) return null
  if (!chart) {
    const { clientWidth, clientHeight } = chartRef.value
    if (!clientWidth || !clientHeight) return null
    chart = echarts.init(chartRef.value)
  }
  return chart
}

function render() {
  const instance = ensureChart()
  if (!instance) return
  instance.setOption(props.option, true)
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  // 延后一帧，等待父级网格完成布局
  requestAnimationFrame(render)
  ro = new ResizeObserver(() => {
    if (chart) {
      onResize()
    } else {
      render()
    }
  })
  ro.observe(chartRef.value)
  window.addEventListener('resize', onResize)
})

watch(() => props.option, render, { deep: true })
// 高度 prop 变化时重绘，窄屏下自适应高度生效
watch(() => props.height, () => { onResize(); render() })

onBeforeUnmount(() => {
  ro?.disconnect()
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped lang="scss">
.chart-wrap {
  padding: 20px 22px;
  min-width: 0;
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.chart-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-1);
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  box-shadow: 0 0 8px currentColor;
}

.chart-body {
  width: 100%;
  min-width: 0;
  max-width: 100%;
}
</style>