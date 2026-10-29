<template>
  <div class="todo-panel glass-card">
    <div class="card-head">
      <div class="card-title">
        <el-icon><BellFilled /></el-icon>
        待办工作台
      </div>
    </div>

    <div class="todo-list">
      <div
        v-for="t in todos"
        :key="t.key"
        class="todo-row"
        :class="{ idle: t.value === 0 }"
        @click="t.route && router.push(t.route)"
      >
        <!-- 环形进度（值>0 时染色） -->
        <div class="todo-ring" :style="ringStyle(t)">
          <svg viewBox="0 0 44 44" class="ring-svg">
            <circle class="ring-bg" cx="22" cy="22" r="18" />
            <circle
              class="ring-fg"
              cx="22" cy="22" r="18"
              :style="{ stroke: t.color, strokeDashoffset: ringOffset(t) }"
            />
          </svg>
          <span class="ring-num">{{ t.value }}</span>
        </div>

        <div class="todo-info">
          <div class="todo-label">{{ t.label }}</div>
          <div class="todo-desc">{{ t.desc }}</div>
        </div>
        <el-icon class="todo-arrow" v-if="t.route"><ArrowRight /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'

const props = defineProps({
  pendingApproval: { type: Number, default: 0 },
  pendingRepair: { type: Number, default: 0 },
  faultDevice: { type: Number, default: 0 },
  todayReservation: { type: Number, default: 0 }
})

const router = useRouter()
const userStore = useUserStore()

// 用一个假想总量计算环形占比（视觉参考），超出/为0时处理
//
// 注意：驾驶舱对所有登录用户可见，但各条目指向的页面是有权限门槛的。
// 学生看不到「预约管理 / 设备台账」，因此这里按权限过滤条目，
// 并把「今日预约」的落地页按可访问性回退到「我的签到」，避免点出 403 空页。
const todos = computed(() => {
  const canListReservation = userStore.hasPerm('reservation:list')
  const all = [
    {
      key: 'approval', label: '待审核预约', value: props.pendingApproval, color: '#ff8a5c',
      desc: '需教师审批的预约申请', route: '/reservation/list',
      perms: ['reservation:list', 'reservation:approve']
    },
    {
      key: 'repair', label: '未完成报修', value: props.pendingRepair, color: '#ff4d6d',
      desc: '待处理与维修中的工单', route: '/device/repair',
      perms: ['device:repair:list', 'device:repair:handle', 'device:repair:submit']
    },
    {
      key: 'fault', label: '故障设备', value: props.faultDevice, color: '#8f6bff',
      desc: '处于故障锁定状态的设备', route: '/device/list',
      perms: ['system:device:list']
    },
    {
      key: 'today', label: '今日预约', value: props.todayReservation, color: '#35a4f0',
      desc: canListReservation ? '今天的实验室预约场次' : '我今天要参加的实验室预约',
      route: canListReservation ? '/reservation/list' : '/checkin/mine'
    }
  ]
  return all.filter((t) => !t.perms || t.perms.some((p) => userStore.hasPerm(p)))
})

const MAX = 20 // 环形满圈参考值
function ringOffset(t) {
  const ratio = Math.min(t.value / MAX, 1)
  const len = 2 * Math.PI * 18 // r=18 周长
  return len * (1 - ratio)
}

function ringStyle(t) {
  return t.value > 0 ? { '--_c': t.color } : {}
}
</script>

<style scoped lang="scss">
.todo-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-head {
  margin-bottom: 12px;
}

.card-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-1);
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.todo-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid var(--hairline);
  background: var(--card-bg);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    background: var(--card-bg-hover);
    transform: translateX(3px);
  }

  &.idle {
    opacity: 0.55;
    cursor: default;
    &:hover {
      transform: none;
    }
  }
}

.todo-ring {
  position: relative;
  width: 44px;
  height: 44px;
  flex-shrink: 0;

  .ring-svg {
    width: 44px;
    height: 44px;
    transform: rotate(-90deg);
  }

  .ring-bg {
    fill: none;
    stroke: rgba(140, 150, 200, 0.18);
    stroke-width: 5;
  }

  .ring-fg {
    fill: none;
    stroke-width: 5;
    stroke-linecap: round;
    transition: stroke-dashoffset 0.6s ease;
    stroke-dasharray: 113.1; // 2π*18
  }

  .ring-num {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    font-weight: 800;
    color: var(--ink-1);
  }
}

.todo-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.todo-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-1);
}

.todo-desc {
  font-size: 12px;
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-arrow {
  color: var(--ink-3);
}

@media (max-width: 640px) {
  .todo-row {
    gap: 10px;
    padding: 8px 10px;
  }

  .todo-ring {
    width: 38px;
    height: 38px;

    .ring-svg {
      width: 38px;
      height: 38px;
    }

    .ring-num {
      font-size: 12px;
    }
  }
}
</style>