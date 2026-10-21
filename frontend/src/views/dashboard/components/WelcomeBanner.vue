<template>
  <div class="welcome glass-card">
    <div class="welcome-glow"></div>
    <div class="welcome-left">
      <div class="hi-tag">{{ greetingText }}</div>
      <div class="hi-title">
        {{ timePartText }}，<span class="hi-name">{{ realName }}</span>
      </div>
      <div class="hi-sub">
        <span>{{ roleText }}</span>
        <span class="dot">·</span>
        <span>{{ permissionCount }} 项权限</span>
        <span class="dot">·</span>
        <span>{{ today }}</span>
      </div>
    </div>

    <div class="welcome-actions">
      <div v-for="a in actions" :key="a.label" class="action-item glass-hover" @click="a.click">
        <div class="action-icon" :style="{ background: a.bg }">
          <el-icon :size="20"><component :is="a.icon" /></el-icon>
        </div>
        <div class="action-text">{{ a.label }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

const realName = computed(() => userStore.userInfo?.realName || '同学')
const roleText = computed(() => {
  const map = {
    SUPER_ADMIN: '超级管理员', ADMIN: '超级管理员',
    LAB_ADMIN: '实验室管理员', LABADMIN: '实验室管理员',
    TEACHER: '教师', STUDENT: '学生'
  }
  const r = userStore.roles?.[0]
  return (r && map[r]) || r || '管理员'
})
const permissionCount = computed(() => userStore.permissions.length)

const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
})

function greeting() {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
}
const greetingText = greeting()

// 欢迎语按时间
function timePart() {
  const h = new Date().getHours()
  if (h < 6) return '注意休息'
  if (h < 12) return '一日之计在于晨'
  return '欢迎回来'
}
const timePartText = timePart()

const actions = [
  { label: '发起预约', icon: 'Calendar', bg: 'linear-gradient(135deg,#5b8cff,#7a5bff)', click: () => router.push('/reservation/apply') },
  { label: '实时看板', icon: 'Odometer', bg: 'linear-gradient(135deg,#5b8cff,#35b3e0)', click: () => router.push('/board') },
  { label: '我的签到', icon: 'Stamp', bg: 'linear-gradient(135deg,#22c1c3,#35e0a1)', click: () => router.push('/checkin/mine') },
  { label: '消息中心', icon: 'Bell', bg: 'linear-gradient(135deg,#8f6bff,#c46bff)', click: () => router.push('/notice') }
]
</script>

<style scoped lang="scss">
.welcome {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 28px;
  overflow: hidden;
}

.welcome-glow {
  position: absolute;
  top: -60%;
  right: 20%;
  width: 380px;
  height: 380px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(91, 140, 255, 0.22), transparent 65%);
  pointer-events: none;
}

.hi-tag {
  display: inline-block;
  font-size: 12px;
  color: var(--accent);
  background: rgba(91, 140, 255, 0.12);
  border: 1px solid rgba(91, 140, 255, 0.2);
  padding: 2px 10px;
  border-radius: 999px;
  margin-bottom: 8px;
}

.hi-title {
  font-size: 24px;
  font-weight: 800;
  color: var(--ink-1);

  .hi-name {
    background: var(--accent-gradient);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}

.hi-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--ink-3);
  letter-spacing: 0.3px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 6px;

  // 每段整体换行，避免日期被拆成「…星期 / 三」
  > span {
    white-space: nowrap;
  }

  .dot {
    opacity: 0.6;
  }
}

.welcome-actions {
  display: flex;
  gap: 16px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 16px;
  cursor: pointer;
  background: var(--card-bg-soft);
  border: 1px solid var(--hairline);
  min-width: 84px;

  .action-icon {
    width: 44px;
    height: 44px;
    border-radius: 14px;
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 6px 14px rgba(91, 140, 255, 0.25);
  }

  .action-text {
    font-size: 13px;
    font-weight: 600;
    color: var(--ink-2);
  }
}

@media (max-width: 1100px) {
  .welcome {
    flex-direction: column;
    align-items: stretch;
  }

  .welcome-actions {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 12px;
  }
}

@media (max-width: 640px) {
  .welcome-actions {
    grid-template-columns: repeat(2, 1fr);
  }

  .hi-title {
    font-size: 20px;
  }

  // 窄屏去掉分隔点：内容自然分成「角色 + 权限」「日期」两行
  .hi-sub .dot {
    display: none;
  }

  .action-item {
    min-width: 0;
  }
}
</style>