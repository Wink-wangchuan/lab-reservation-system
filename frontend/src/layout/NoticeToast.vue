/**
 * 新消息浮层（页面内兜底通知）
 *
 * 替代默认的 ElNotification：
 *   1. 单卡信息分层（类型图标 / 标题 / 正文两行截断 / 类型标签 / 查看入口），长消息不会把弹窗撑爆；
 *   2. 按消息类型着色（预约蓝、报修橙、系统紫），与消息中心的图标配色保持一致；
 *   3. 固定在右下角，最多同时叠 3 张，鼠标悬停暂停自动关闭，避免一次性刷屏；
 *   4. 移动端改为贴底通栏，不再遮挡顶栏的时钟与图标按钮。
 */
<template>
  <transition-group name="toast" tag="div" class="notice-stack" appear>
    <div
      v-for="item in items"
      :key="item.id"
      class="notice-toast"
      :class="'type-' + typeOf(item)"
      role="status"
      @mouseenter="pause(item.id)"
      @mouseleave="resume(item.id)"
      @click="open(item)"
    >
      <span class="notice-toast__accent"></span>
      <div class="notice-toast__icon">
        <el-icon :size="17"><component :is="iconOf(item)" /></el-icon>
      </div>
      <div class="notice-toast__body">
        <div class="notice-toast__head">
          <span class="notice-toast__title">{{ item.title || '新消息' }}</span>
          <button
            class="notice-toast__close"
            type="button"
            aria-label="关闭这条提示"
            @click.stop="$emit('close', item.id)"
          >
            <el-icon :size="12"><Close /></el-icon>
          </button>
        </div>
        <p class="notice-toast__content">{{ item.content || '点击查看详情' }}</p>
        <div class="notice-toast__foot">
          <span class="notice-toast__tag">{{ item.noticeTypeDesc || '系统通知' }}</span>
          <span class="notice-toast__action">{{ item.summary ? '进入消息中心 ›' : '查看详情 ›' }}</span>
        </div>
      </div>
    </div>
  </transition-group>
</template>

<script setup>
import { onBeforeUnmount, watch } from 'vue'

const props = defineProps({
  /** 待展示的消息（最多 3 条，由父组件裁剪） */
  items: { type: Array, default: () => [] },
  /** 单条自动关闭时长（毫秒） */
  duration: { type: Number, default: 8000 }
})

const emit = defineEmits(['open', 'close'])

// 与消息中心完全一致的图标 / 配色映射，避免同一类消息在两处长得不一样
const TYPE_ICON = { SYSTEM: 'Message', RESERVATION: 'Tickets', REPAIR: 'Tools' }
const TYPE_OF_SUMMARY = 'SYSTEM'

function typeOf(item) {
  return item.summary ? TYPE_OF_SUMMARY : item.noticeType || TYPE_OF_SUMMARY
}

function iconOf(item) {
  return item.summary ? 'Bell' : TYPE_ICON[typeOf(item)] || 'Bell'
}

/** 每条消息一个定时器，悬停时暂停，移出后重新计时 */
const timers = new Map()

function schedule(id) {
  clear(id)
  timers.set(id, setTimeout(() => {
    timers.delete(id)
    emit('close', id)
  }, props.duration))
}

function clear(id) {
  const timer = timers.get(id)
  if (timer) {
    clearTimeout(timer)
    timers.delete(id)
  }
}

function pause(id) {
  clear(id)
}

function resume(id) {
  schedule(id)
}

function open(item) {
  emit('open', item)
}

// 新增即开始倒计时；被移除的条目要立刻释放定时器
watch(
  () => props.items.map((item) => item.id),
  (ids) => {
    ids.forEach((id) => {
      if (!timers.has(id)) schedule(id)
    })
    Array.from(timers.keys()).forEach((id) => {
      if (!ids.includes(id)) clear(id)
    })
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  timers.forEach((timer) => clearTimeout(timer))
  timers.clear()
})
</script>

<style scoped lang="scss">
.notice-stack {
  position: fixed;
  right: 18px;
  bottom: 22px;
  z-index: 2600;
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.notice-toast {
  position: relative;
  display: flex;
  gap: 10px;
  width: min(360px, calc(100vw - 36px));
  padding: 11px 12px 10px 14px;
  border-radius: 14px;
  border: 1px solid var(--glass-border);
  background: var(--card-bg);
  backdrop-filter: blur(18px) saturate(1.25);
  box-shadow: 0 14px 34px rgba(60, 80, 180, 0.18);
  cursor: pointer;
  pointer-events: auto;
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 18px 38px rgba(60, 80, 180, 0.26);
  }
}

/* 左侧色条：比整块染色的弹窗克制，也不会影响深色模式下的可读性 */
.notice-toast__accent {
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: linear-gradient(180deg, #8f6bff, #5b8cff);
}

.type-RESERVATION .notice-toast__accent {
  background: linear-gradient(180deg, #5b8cff, #35b3e0);
}

.type-REPAIR .notice-toast__accent {
  background: linear-gradient(180deg, #ff8a5c, #ff4d6d);
}

.notice-toast__icon {
  flex: none;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: #fff;
  background: linear-gradient(135deg, #8f6bff, #5b8cff);
  box-shadow: 0 6px 14px rgba(91, 140, 255, 0.28);
}

.type-RESERVATION .notice-toast__icon {
  background: linear-gradient(135deg, #5b8cff, #35b3e0);
}

.type-REPAIR .notice-toast__icon {
  background: linear-gradient(135deg, #ff8a5c, #ff4d6d);
  box-shadow: 0 6px 14px rgba(255, 110, 110, 0.26);
}

.notice-toast__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.notice-toast__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.notice-toast__title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--ink-1);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
}

.notice-toast__close {
  flex: none;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;

  &:hover {
    background: var(--card-bg-hover);
    color: var(--ink-1);
  }
}

/* 正文两行截断：预约提醒等消息较长，不截断会把浮层撑得很高 */
.notice-toast__content {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ink-2);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow-wrap: anywhere;
}

.notice-toast__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}

.notice-toast__tag {
  font-size: 11px;
  padding: 1px 7px;
  border-radius: 999px;
  color: var(--ink-3);
  background: var(--card-bg-soft);
  white-space: nowrap;
}

.notice-toast__action {
  font-size: 11px;
  font-weight: 600;
  color: #5b8cff;
  white-space: nowrap;
}

/* 进场从下方浮起、退场向右淡出；离场绝对定位 + move 过渡避免剩余卡片跳位 */
.toast-enter-from {
  opacity: 0;
  transform: translateY(14px) scale(0.97);
}

.toast-enter-active {
  transition: opacity 0.26s ease, transform 0.26s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.toast-leave-to {
  opacity: 0;
  transform: translateX(16px) scale(0.97);
}

.toast-leave-active {
  position: absolute;
  right: 0;
  bottom: 0;
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.toast-move {
  transition: transform 0.24s ease;
}

@media (max-width: 640px) {
  .notice-stack {
    left: 10px;
    right: 10px;
    bottom: 10px;
  }

  .notice-toast {
    width: auto;
  }
}

/* 尊重系统「减少动效」设置：只保留淡入淡出，不做位移 */
@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active,
  .toast-move {
    transition: opacity 0.15s ease !important;
  }

  .toast-enter-from,
  .toast-leave-to {
    transform: none;
  }
}
</style>
