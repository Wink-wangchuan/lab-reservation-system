import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

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
        meta: { title: '数据驾驶舱', icon: 'DataAnalysis' }
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
        meta: { title: '预约申请', icon: 'Calendar' }
      },
      {
        path: 'list',
        name: 'ReservationList',
        component: () => import('@/views/reservation/list.vue'),
        meta: { title: '预约管理', icon: 'Tickets' }
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
        meta: { title: '实验室管理', icon: 'OfficeBuilding' }
      },
      {
        path: 'slot',
        name: 'LabSlot',
        component: () => import('@/views/lab/slot.vue'),
        meta: { title: '开放时段配置', icon: 'Timer' }
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
        meta: { title: '设备台账', icon: 'Monitor' }
      },
      {
        path: 'repair',
        name: 'DeviceRepair',
        component: () => import('@/views/device/repair.vue'),
        meta: { title: '报修闭环', icon: 'Tools' }
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
        meta: { title: '实时占用看板', icon: 'Odometer' }
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
        meta: { title: '我的签到', icon: 'Stamp' }
      },
      {
        path: 'verify',
        name: 'CheckinVerify',
        component: () => import('@/views/checkin/verify.vue'),
        meta: { title: '签到核销', icon: 'CircleCheck' }
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
        meta: { title: '消息中心', icon: 'Bell' }
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
        meta: { title: '用户管理', icon: 'User' }
      },
      {
        path: 'role',
        name: 'SysRole',
        component: () => import('@/views/system/role.vue'),
        meta: { title: '角色管理', icon: 'Avatar' }
      },
      {
        path: 'permission',
        name: 'SysPermission',
        component: () => import('@/views/system/permission.vue'),
        meta: { title: '权限管理', icon: 'Lock' }
      },
      {
        path: 'operlog',
        name: 'OperLog',
        component: () => import('@/views/system/operlog.vue'),
        meta: { title: '操作日志', icon: 'Document' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 无需登录即可访问的页面（注册、找回密码与登录同级，独立整页）
const WHITE_LIST = ['/login', '/register', '/forgot']

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
  next()
})

export default router