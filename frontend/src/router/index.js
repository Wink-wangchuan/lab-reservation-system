import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: { title: '注册账号' }
  },
  {
    path: '/forgot',
    name: 'Forgot',
    component: () => import('@/views/forgot/index.vue'),
    meta: { title: '找回密码' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据驾驶舱', icon: 'DataAnalysis', perms: ['statistics:view'] }
      }
    ]
  },
  // 预约相关也放入布局内，但保留在 /index.vue 等业务页
  {
    path: '/reservation',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: 'apply',
        name: 'ReservationApply',
        component: () => import('@/views/reservation/apply.vue'),
        meta: { title: '预约申请', icon: 'Calendar', perms: ['reservation:submit'] }
      },
      {
        path: 'list',
        name: 'ReservationList',
        component: () => import('@/views/reservation/list.vue'),
        meta: { title: '预约管理', icon: 'Tickets', perms: ['reservation:list'] }
      }
    ]
  },
  {
    path: '/lab',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: 'info',
        name: 'LabInfo',
        component: () => import('@/views/lab/info.vue'),
        meta: { title: '实验室管理', icon: 'OfficeBuilding', perms: ['system:lab:list'] }
      },
      {
        path: 'slot',
        name: 'LabSlot',
        component: () => import('@/views/lab/slot.vue'),
        meta: { title: '开放时段配置', icon: 'Timer', perms: ['system:lab:slotConfig'] }
      },
      {
        path: 'schedule',
        name: 'LabSchedule',
        component: () => import('@/views/lab/schedule.vue'),
        meta: { title: '实验室周日程', icon: 'Calendar' }
      }
    ]
  },
  {
    path: '/device',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: 'list',
        name: 'DeviceList',
        component: () => import('@/views/device/list.vue'),
        meta: { title: '设备台账', icon: 'Monitor', perms: ['system:device:list'] }
      },
      {
        path: 'repair',
        name: 'DeviceRepair',
        component: () => import('@/views/device/repair.vue'),
        meta: { title: '报修闭环', icon: 'Tools', perms: ['device:repair:list', 'device:repair:submit'] }
      }
    ]
  },
  {
    path: '/board',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: '',
        name: 'LabBoard',
        component: () => import('@/views/board/index.vue'),
        meta: { title: '实时占用看板', icon: 'Odometer', perms: ['lab:board:view'] }
      }
    ]
  },
  {
    path: '/checkin',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: 'mine',
        name: 'CheckinMine',
        component: () => import('@/views/checkin/mine.vue'),
        meta: { title: '我的签到', icon: 'Stamp', perms: ['checkin:mine'] }
      },
      {
        path: 'verify',
        name: 'CheckinVerify',
        component: () => import('@/views/checkin/verify.vue'),
        meta: { title: '签到核销', icon: 'CircleCheck', perms: ['checkin:verify'] }
      }
    ]
  },
  {
    path: '/notice',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: '',
        name: 'NoticeCenter',
        component: () => import('@/views/notice/index.vue'),
        meta: { title: '消息中心', icon: 'Bell', perms: ['notice:mine', 'notice:list'] }
      }
    ]
  },
  {
    path: '/ai',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: '',
        name: 'AiAssistant',
        component: () => import('@/views/ai/index.vue'),
        meta: { title: 'AI 助手', icon: 'ChatDotRound' }
      }
    ]
  },
  {
    path: '/profile',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: '',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'UserFilled' }
      }
    ]
  },
  {
    path: '/system',
    component: () => import('@/layout/index.vue'),
    children: [
      {
        path: 'user',
        name: 'SysUser',
        component: () => import('@/views/system/user.vue'),
        meta: { title: '用户管理', icon: 'User', perms: ['system:user:list'] }
      },
      {
        path: 'role',
        name: 'SysRole',
        component: () => import('@/views/system/role.vue'),
        meta: { title: '角色管理', icon: 'Avatar', perms: ['system:role:list'] }
      },
      {
        path: 'permission',
        name: 'SysPermission',
        component: () => import('@/views/system/permission.vue'),
        meta: { title: '权限管理', icon: 'Lock', perms: ['system:role:list'] }
      },
      {
        path: 'operlog',
        name: 'OperLog',
        component: () => import('@/views/system/operlog.vue'),
        meta: { title: '操作日志', icon: 'Document', perms: ['system:operlog:list'] }
      },
      {
        path: 'knowledge',
        name: 'SysKnowledge',
        component: () => import('@/views/system/knowledge.vue'),
        meta: { title: 'AI 知识库', icon: 'AiKnowledgeIcon', perms: ['system:knowledge:manage'] }
      },
      {
        path: 'setting',
        name: 'SysSetting',
        component: () => import('@/views/system/setting.vue'),
        meta: { title: '系统设置', icon: 'Setting', perms: ['system:config:manage'] }
      },
      {
        path: 'announcement',
        name: 'SysAnnouncement',
        component: () => import('@/views/system/announcement.vue'),
        meta: { title: '系统公告', icon: 'BellFilled', perms: ['system:announcement:manage'] }
      },
      {
        path: 'data-scope',
        name: 'DataScope',
        component: () => import('@/views/system/data-scope.vue'),
        meta: { title: '数据权限', icon: 'Lock', perms: ['system:user:edit'] }
      }
    ]
  },
  {
    path: '/403',
    name: 'NoPermission',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无访问权限' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 无需登录即可访问的页面（注册、找回密码与登录同级，独立整页）
const WHITE_LIST = ['/login', '/register', '/forgot']

/** 当前登录用户是否具备访问该路由的任一权限（* 为全部权限） */
function hasRoutePermission(required) {
  if (!required || required.length === 0) return true
  const permissions = getUser()?.permissions || []
  return permissions.includes('*') || required.some((perm) => permissions.includes(perm))
}

// 全局前置守卫
router.beforeEach((to, from, next) => {
  document.title = to.meta?.title ? `${to.meta.title} · 智慧实验室预约管理系统` : '智慧实验室预约管理系统'
  if (WHITE_LIST.includes(to.path)) {
    next()
    return
  }
  if (!getToken()) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  // 路由级权限：菜单之外直接输入 URL 也不进入无权页面（后端仍有接口权限兜底）
  if (!hasRoutePermission(to.meta?.perms)) {
    next({ path: '/403', query: { from: to.fullPath } })
    return
  }
  next()
})

export default router