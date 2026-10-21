<template>
  <div class="stat-card glass-card glass-hover">
    <!-- 霓虹图标 -->
    <div class="stat-icon" :style="{ background: bg }">
      <el-icon :size="26"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-info">
      <div class="stat-value">
        <span ref="numRef" class="num">0</span>
        <span v-if="suffix" class="suffix">{{ suffix }}</span>
      </div>
      <div class="stat-label">{{ label }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'

const props = defineProps({
  label: String,
  icon: String,
  value: { type: Number, default: 0 },
  suffix: { type: String, default: '' },
  bg: { type: String, default: 'linear-gradient(135deg,#5b8cff,#7a5bff)' }
})

const numRef = ref(null)

// 数字滚动动画
function animateTo(target) {
  const el = numRef.value
  if (!el) return
  const duration = 900
  const start = performance.now()
  const from = 0
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
  if (reduceMotion) {
    el.textContent = target
    return
  }
  function step(now) {
    const p = Math.min((now - start) / duration, 1)
    // easeOutCubic
    const eased = 1 - Math.pow(1 - p, 3)
    el.textContent = Math.round(from + (target - from) * eased)
    if (p < 1) requestAnimationFrame(step)
  }
  el.textContent = 0
  requestAnimationFrame(step)
}

onMounted(() => animateTo(props.value))
watch(() => props.value, (v) => animateTo(v))
</script>

<style scoped lang="scss">
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  position: relative;
  overflow: hidden;
  min-width: 0;
}

.stat-icon {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 18px rgba(91, 140, 255, 0.25);
  flex-shrink: 0;
  overflow: hidden;
  // 建立层叠上下文，让高光只压在底色上、不盖住图标本身
  isolation: isolate;

  &::after {
    content: '';
    position: absolute;
    inset: 2px;
    border-radius: 16px;
    background: linear-gradient(160deg, rgba(255, 255, 255, 0.45), transparent 55%);
    pointer-events: none;
    z-index: -1;
  }
}

.stat-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 30px;
  font-weight: 800;
  color: var(--ink-1);
  line-height: 1.1;
  display: flex;
  align-items: baseline;
  gap: 2px;

  .num {
    font-variant-numeric: tabular-nums;
    background: linear-gradient(135deg, #3a55c9, #6b46d1);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }

  .suffix {
    font-size: 14px;
    font-weight: 600;
    color: var(--ink-3);
  }
}

.stat-label {
  font-size: 13px;
  color: var(--ink-3);
  margin-top: 4px;
  letter-spacing: 0.3px;
}

@media (max-width: 640px) {
  .stat-card {
    padding: 14px 16px;
    gap: 12px;
  }

  .stat-icon {
    width: 44px;
    height: 44px;
    border-radius: 14px;

    &::after { border-radius: 12px; }
  }

  .stat-value {
    font-size: 24px;
  }

  .stat-label {
    font-size: 12px;
  }
}
</style>