<template>
  <div class="layout-root">
    <!-- 顶栏 -->
    <header class="topbar glass-card">
      <div class="brand">
        <!-- 窄屏：呼出抽屉导航 -->
        <button class="nav-toggle" type="button" aria-label="打开导航" @click="drawerVisible = true">
          <el-icon :size="18"><Expand /></el-icon>
        </button>
        <div class="brand-logo">
          <el-icon :size="22"><Cpu /></el-icon>
        </div>
        <div class="brand-text">
          <div class="brand-title">智慧实验室预约管理系统</div>
          <div class="brand-sub">LabWise Reservation</div>
        </div>
      </div>

      <div class="topbar-right">
        <!-- 主题切换 -->
        <button class="icon-btn" type="button" :title="isDark ? '切换到浅色' : '切换到深色'" @click="toggleTheme">
          <el-icon :size="18"><component :is="isDark ? 'Sunny' : 'Moon'" /></el-icon>
        </button>

        <!-- 消息中心入口（带未读红点） -->
        <button class="icon-btn" type="button" title="消息中心" @click="router.push('/notice')">
          <el-icon :size="18"><Bell /></el-icon>
          <span v-if="unread > 0" class="dot">{{ unread > 99 ? '99+' : unread }}</span>
        </button>

        <div class="user-block">
          <div class="avatar">
            <el-avatar :size="36" :src="avatarSrc">
              {{ avatarText }}
            </el-avatar>
          </div>
          <div class="user-meta">
            <div class="user-name">{{ userStore.userInfo?.realName || '管理员' }}</div>
            <div class="user-role">{{ roleText }}</div>
          </div>
          <el-dropdown trigger="click" @command="onCommand">
            <el-icon class="more-icon"><ArrowDown /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><UserFilled /></el-icon> 个人中心
                </el-dropdown-item>
                <el-dropdown-item command="notice">
                  <el-icon><Bell /></el-icon> 消息中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <div class="layout-body">
      <!-- 桌面侧边导航 -->
      <aside class="sidebar glass-card">
        <SideMenu :menus="menus" :active="activeMenu" :collapse="collapseNav" />
      </aside>

      <!-- 内容区 -->
      <main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>

    <!-- 移动端抽屉导航 -->
    <el-drawer
      v-model="drawerVisible"
      direction="ltr"
      size="248px"
      :with-header="false"
      class="nav-drawer"
    >
      <div class="drawer-head">
        <div class="brand-logo">
          <el-icon :size="20"><Cpu /></el-icon>
        </div>
        <div class="brand-title">智慧实验室预约</div>
      </div>
      <SideMenu :menus="menus" :active="activeMenu" @click="drawerVisible = false" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useTheme } from '@/utils/theme'
import { noticeUnreadCount } from '@/api'
import SideMenu from './SideMenu.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isDark, toggleTheme } = useTheme()

const drawerVisible = ref(false)
const unread = ref(0)
let timer = null

// 中屏（769-1024px）折叠为图标导航；窄屏隐藏侧栏走抽屉
const winW = ref(window.innerWidth)
const collapseNav = computed(() => winW.value <= 1024)
function trackWidth() {
  winW.value = window.innerWidth
}

// 菜单定义（perms 命中任意一项即可见，* 代表全部权限）
const menuDefs = [
  { path: '/dashboard', title: '数据驾驶舱', icon: 'DataAnalysis', perms: ['statistics:view'] },
  { path: '/board', title: '实时占用看板', icon: 'Odometer', perms: ['lab:board:view'] },
  { path: '/reservation/apply', title: '预约申请', icon: 'Calendar', perms: ['reservation:submit'] },
  { path: '/reservation/list', title: '预约管理', icon: 'Tickets', perms: ['reservation:list'] },
  { path: '/checkin/mine', title: '我的签到', icon: 'Stamp', perms: ['checkin:mine'] },
  { path: '/checkin/verify', title: '签到核销', icon: 'CircleCheck', perms: ['checkin:verify'] },
  { path: '/lab/info', title: '实验室管理', icon: 'OfficeBuilding', perms: ['system:lab:list'] },
  { path: '/lab/slot', title: '开放时段配置', icon: 'Timer', perms: ['system:lab:slotConfig'] },
  { path: '/device/list', title: '设备台账', icon: 'Monitor', perms: ['system:device:list'] },
  { path: '/device/repair', title: '报修闭环', icon: 'Tools', perms: ['device:repair:list', 'device:repair:submit'] },
  { path: '/notice', title: '消息中心', icon: 'Bell', perms: ['notice:mine', 'notice:list'] },
  { path: '/system/user', title: '用户管理', icon: 'User', perms: ['system:user:list'] },
  { path: '/system/role', title: '角色管理', icon: 'Avatar', perms: ['system:role:list'] },
  { path: '/system/permission', title: '权限管理', icon: 'Lock', perms: ['system:role:list'] },
  { path: '/system/operlog', title: '操作日志', icon: 'Document', perms: ['system:operlog:list'] },
  { path: '/ai', title: 'AI 助手', icon: 'ChatDotRound', always: true },
  { path: '/profile', title: '个人中心', icon: 'UserFilled', always: true }
]

// 按权限过滤菜单
const menus = computed(() => {
  const perms = userStore.permissions || []
  return menuDefs.filter((m) => m.always || perms.includes('*')
    || m.perms.some((p) => perms.includes(p)))
})

const activeMenu = computed(() => route.path || '/board')
const avatarSrc = computed(() => userStore.userInfo?.avatar || '')
const avatarText = computed(() => (userStore.userInfo?.realName || '管').slice(-1))

const roleText = computed(() => {
  const map = {
    SUPER_ADMIN: '超级管理员', ADMIN: '超级管理员',
    LAB_ADMIN: '实验室管理员', LABADMIN: '实验室管理员',
    TEACHER: '教师', STUDENT: '学生'
  }
  const r = userStore.roles?.[0]
  return (r && map[r]) || r || '用户'
})

async function refreshUnread() {
  try {
    unread.value = (await noticeUnreadCount()) || 0
  } catch {
    // 未读数获取失败不影响主流程
  }
}

function onCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/profile')
    return
  }
  if (cmd === 'notice') {
    router.push('/notice')
    return
  }
  if (cmd === 'logout') {
    ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(async () => {
      await userStore.doLogout()
      ElMessage.success('已退出登录')
      router.push('/login')
    }).catch(() => {})
  }
}

// 进入消息中心后未读清零，返回时刷新红点
watch(() => route.path, () => {
  drawerVisible.value = false
  refreshUnread()
})

onMounted(() => {
  // 每次进入应用都刷新一次用户信息：本地缓存的权限可能已过期
  // （例如管理员调整了角色权限），无条件刷新可避免"必须重新登录菜单才会更新"
  userStore.fetchInfo().catch(() => {})
  refreshUnread()
  // 轮询未读消息（60s），保证顶栏红点及时
  timer = setInterval(refreshUnread, 60000)
  window.addEventListener('resize', trackWidth)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', trackWidth)
})
</script>

<style scoped lang="scss">
.layout-root {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 14px 16px;
  gap: 14px;
  overflow: hidden;
}

.topbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.nav-toggle {
  display: none;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--glass-border);
  background: var(--surface-btn);
  color: var(--ink-2);
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}

.brand-logo {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: var(--accent-gradient);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 18px rgba(91, 140, 255, 0.4);
  flex-shrink: 0;
}

.brand-text {
  min-width: 0;
}

.brand-title {
  font-size: 17px;
  font-weight: 700;
  color: var(--ink-1);
  letter-spacing: 0.3px;
  white-space: nowrap;
}

.brand-sub {
  font-size: 11px;
  color: var(--ink-3);
  letter-spacing: 1px;
  text-transform: uppercase;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.icon-btn {
  position: relative;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  border: 1px solid var(--glass-border);
  background: var(--surface-btn);
  color: var(--ink-2);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: var(--accent);
    border-color: rgba(91, 140, 255, 0.4);
    transform: translateY(-1px);
  }

  .dot {
    position: absolute;
    top: -4px;
    right: -4px;
    min-width: 17px;
    height: 17px;
    padding: 0 4px;
    border-radius: 999px;
    background: linear-gradient(135deg, #ff5c7a, #ff8a5c);
    color: #fff;
    font-size: 10px;
    line-height: 17px;
    text-align: center;
    font-weight: 700;
    box-shadow: 0 2px 6px rgba(255, 92, 122, 0.5);
  }
}

.user-block {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}

.user-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--ink-1);
}

.user-role {
  font-size: 11px;
  color: var(--ink-3);
}

.more-icon {
  cursor: pointer;
  color: var(--ink-3);
  margin-left: 4px;
}

.layout-body {
  flex: 1;
  display: flex;
  gap: 14px;
  min-height: 0;

  > * { min-width: 0; }
}

.sidebar {
  width: 220px;
  flex-shrink: 0;
  padding: 12px 10px;
  overflow-y: auto;
  // 侧栏贴边铺满，去掉玻璃卡默认投影：否则底边一条灰影会露在页面留白里
  box-shadow: none;
  // 隐藏滚动条但保留滚轮滚动能力
  scrollbar-width: none;      // Firefox
  -ms-overflow-style: none;   // 旧版 Edge/IE
  &::-webkit-scrollbar {      // Chromium
    width: 0;
    height: 0;
  }
}

.main-content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  overflow-x: hidden;
  border-radius: var(--radius-lg);
  padding: 6px 2px;
}

.drawer-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 16px;
}

// 页面切换过渡
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

// —— 响应式 ——
// 中屏：折叠为纯图标导航，给主内容让出空间
@media (max-width: 1024px) {
  .layout-root {
    padding: 8px 10px;
    gap: 10px;
  }

  .brand-text {
    display: none;
  }

  .sidebar {
    width: 60px;
    padding: 12px 6px;
  }
}

// 窄屏：侧栏整体收起，改用抽屉导航
@media (max-width: 768px) {
  .nav-toggle {
    display: flex;
  }

  .sidebar {
    display: none;
  }

  .topbar {
    padding: 8px 12px;
  }

  .brand-logo {
    width: 38px;
    height: 38px;
    border-radius: 12px;
  }

  .main-content {
    padding: 4px 0;
  }
}

@media (max-width: 480px) {
  .user-meta {
    display: none;
  }
}
</style>
