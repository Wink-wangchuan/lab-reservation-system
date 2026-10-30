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
        <!-- 当前日期时间（窄屏只留时间，日期在页面内已有展示） -->
        <div class="clock" :title="clockDate + ' ' + clockTime">
          <span class="clock-date">{{ clockDate }}</span>
          <span class="clock-time">{{ clockTime }}</span>
        </div>

        <!-- 当前天气（默认广州，取到定位则按实际位置） -->
        <div v-if="weather" class="weather" :title="weatherTip">
          <el-icon :size="17"><component :is="weatherIcon" /></el-icon>
          <span class="weather-temp">{{ weather.temp }}°</span>
          <span class="weather-city">{{ weather.city || '广州' }}</span>
        </div>

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
import { noticeMine, noticeUnreadCount, weatherNow } from '@/api'
import SideMenu from './SideMenu.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isDark, toggleTheme } = useTheme()

const drawerVisible = ref(false)
const unread = ref(0)
let timer = null

// —— 顶栏时钟 ——
const now = ref(new Date())
let clockTimer = null
const pad2 = (n) => String(n).padStart(2, '0')
const WEEK_NAMES = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const clockDate = computed(() => {
  const d = now.value
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${WEEK_NAMES[d.getDay()]}`
})
const clockTime = computed(() => {
  const d = now.value
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
})

// —— 顶栏天气 ——
// 定位优先用浏览器坐标；拿不到（拒绝授权 / 非 HTTPS 源）就让后端回退默认位置广州。
// 天气每 30 分钟刷新一次，后端对实况做了 10 分钟缓存，不会过度消耗接口配额。
const weather = ref(null)
let weatherTimer = null
let lastCoords = null

const weatherTip = computed(() => {
  const w = weather.value
  if (!w) return ''
  const parts = [w.text, `体感 ${w.feelsLike}°`, `湿度 ${w.humidity}%`]
  if (w.windDir) parts.push(`${w.windDir}${w.windScale}级`)
  return parts.filter(Boolean).join(' · ')
})

// 和风天气图标编码 → Element Plus 图标（图标库无雪天图标，雪与雾霾统一用 Cloudy）
function weatherIconOf(code) {
  const n = Number(code) || 0
  if (n === 100) return 'Sunny'
  if (n >= 150 && n <= 153) return 'MoonNight'
  if (n === 104 || (n >= 500 && n <= 515)) return 'Cloudy'
  if (n >= 101 && n <= 103) return 'PartlyCloudy'
  if (n === 302 || (n >= 303 && n <= 312)) return 'Lightning'
  if (n >= 300 && n <= 301) return 'Drizzling'
  if (n >= 313 && n <= 399) return 'Pouring'
  if (n >= 400 && n <= 499) return 'Cloudy'
  if (n >= 900) return 'WindPower'
  return 'Sunny'
}
const weatherIcon = computed(() => weatherIconOf(weather.value?.icon))

async function loadWeather(lat, lon) {
  try {
    const data = await weatherNow(lat == null || lon == null ? undefined : { lat, lon })
    weather.value = data || null
  } catch {
    // 天气属于弱功能：失败时保持静默，不打扰用户
  }
}

function refreshWeather() {
  if (lastCoords) {
    loadWeather(lastCoords.lat, lastCoords.lon)
    return
  }
  if (!navigator.geolocation) {
    loadWeather()
    return
  }
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      lastCoords = { lat: pos.coords.latitude, lon: pos.coords.longitude }
      loadWeather(lastCoords.lat, lastCoords.lon)
    },
    () => loadWeather(), // 拒绝授权或超时 → 默认广州
    { timeout: 5000, maximumAge: 10 * 60 * 1000, enableHighAccuracy: false }
  )
}

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
  await pushNewNotices()
}

// —— 新消息推送 ——
// 轮询未读数时顺带拉取最新消息，发现比上次记录更新的消息后：
//  1) 已授权系统通知 → 弹浏览器系统通知（点击聚焦页面并跳转消息中心）
//  2) 未授权/不支持 → 页面内弹 ElNotification 兜底
// 已见消息的最大 ID 按用户存 sessionStorage，刷新页面后不会把旧消息重新弹一遍
function noticeStorageKey() {
  return `labwise_last_notice_${userStore.userInfo?.userId ?? 'anon'}`
}
let lastNoticeId = 0

function saveLastNoticeId(id) {
  lastNoticeId = id
  try {
    sessionStorage.setItem(noticeStorageKey(), String(id))
  } catch { /* 隐私模式等场景写不进就仅存内存 */ }
}

async function pushNewNotices() {
  try {
    const page = await noticeMine({ current: 1, size: 5 })
    const records = page?.records || []
    if (!records.length) return
    if (!lastNoticeId) {
      lastNoticeId = Number(sessionStorage.getItem(noticeStorageKey()) || 0)
    }
    const newest = records[0].id
    if (!lastNoticeId) {
      // 首次加载只记录基线，不弹历史消息
      saveLastNoticeId(newest)
      return
    }
    const fresh = records.filter((n) => n.id > lastNoticeId)
    if (!fresh.length) return
    saveLastNoticeId(newest)
    notifyNewNotices(fresh)
  } catch {
    // 拉取失败不影响主流程
  }
}

/** 浏览器系统通知需要用户手势才能申请授权：监听首次点击后静默申请一次 */
function askNotifyPermission() {
  if (!('Notification' in window)) return
  if (Notification.permission === 'default') {
    const p = Notification.requestPermission()
    if (p && typeof p.catch === 'function') p.catch(() => {})
  }
}

function notifyNewNotices(list) {
  const canSystemNotify = 'Notification' in window && Notification.permission === 'granted'

  // 系统通知：同 tag 去重，避免同一消息重复弹；点击聚焦并进入消息中心
  if (canSystemNotify) {
    list.slice(0, 3).forEach((n) => {
      const notification = new Notification(n.title || '新消息', {
        body: n.content || '',
        tag: `labwise-notice-${n.id}`,
        icon: '/favicon.ico'
      })
      notification.onclick = () => {
        window.focus()
        router.push('/notice')
        notification.close()
      }
    })
    if (list.length > 3) {
      const extra = new Notification('消息中心', {
        body: `还有 ${list.length - 3} 条新消息，点击查看`,
        tag: 'labwise-notice-more',
        icon: '/favicon.ico'
      })
      extra.onclick = () => { window.focus(); router.push('/notice'); extra.close() }
    }
    return
  }

  // 页内弹窗兜底（未授权系统通知时）
  list.slice(0, 3).forEach((n) => {
    ElNotification({
      title: n.title || '新消息',
      message: n.content || '',
      duration: 8000,
      onClick: () => router.push('/notice')
    })
  })
  if (list.length > 3) {
    ElNotification({
      title: '消息中心',
      message: `还有 ${list.length - 3} 条新消息，点击查看`,
      duration: 8000,
      onClick: () => router.push('/notice')
    })
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
  // 轮询未读消息与新消息推送（60s），保证顶栏红点与系统通知及时
  timer = setInterval(refreshUnread, 60000)
  // 系统通知授权必须由用户手势触发：页面首次被点击时静默申请一次
  window.addEventListener('pointerdown', askNotifyPermission, { once: true })
  // 顶栏时钟每秒刷新
  clockTimer = setInterval(() => { now.value = new Date() }, 1000)
  // 天气：进入应用先取一次，之后每 30 分钟刷新
  refreshWeather()
  weatherTimer = setInterval(refreshWeather, 30 * 60 * 1000)
  window.addEventListener('resize', trackWidth)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (clockTimer) clearInterval(clockTimer)
  if (weatherTimer) clearInterval(weatherTimer)
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

// 顶栏日期时间：日期在上、时间在下，右对齐贴近主题按钮
.clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  line-height: 1.25;
  white-space: nowrap;
  margin-right: 2px;

  .clock-date {
    font-size: 11px;
    color: var(--ink-3);
  }

  .clock-time {
    font-size: 14px;
    font-weight: 600;
    color: var(--ink-1);
    // 等宽数字：秒数跳动时宽度不抖动
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.3px;
  }
}

// 天气胶囊：与时钟并排，弱强调，不抢视觉重心
.weather {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(91, 140, 255, 0.08);
  color: var(--ink-2);
  white-space: nowrap;
  cursor: default;

  .weather-temp {
    font-size: 13px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .weather-city {
    font-size: 11px;
    color: var(--ink-3);
  }
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

  // 窄屏顶栏空间紧张：日期时间与天气改为紧凑形态而不是直接隐藏
  // 左上角只保留抽屉开关，品牌图标让位给时钟与天气
  .brand {
    flex-shrink: 0;
  }

  .brand .brand-logo {
    display: none;
  }

  .clock {
    display: flex;
    flex-direction: row;
    align-items: baseline;
    gap: 6px;
    margin-right: 0;

    // 窄屏只保留时间，日期在页面内已有展示
    .clock-date {
      display: none;
    }

    .clock-time {
      font-size: 13px;
      letter-spacing: 0;
    }
  }

  .weather {
    display: flex;
    padding: 4px 8px;
    gap: 4px;

    // 城市名在窄屏舍弃，靠 title 无法查看，保留温度更实用
    .weather-city {
      display: none;
    }

    .weather-temp {
      font-size: 12px;
    }
  }

  .topbar-right {
    gap: 6px;
  }

  .icon-btn {
    width: 34px;
    height: 34px;
    border-radius: 10px;
  }

  .user-block {
    gap: 6px;
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
