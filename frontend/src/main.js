import { createApp } from 'vue'
import { createPinia } from 'pinia'
// 按需引入图标：@element-plus/icons-vue 整包约 3MB，全量注册会让产物显著膨胀，
// 这里只导入项目实际使用的图标
import {
  Cpu,
  ArrowDown,
  ArrowRight,
  SwitchButton,
  MagicStick,
  Tickets,
  Search,
  Warning,
  Timer,
  Plus,
  Check,
  Monitor,
  OfficeBuilding,
  Document,
  Lock,
  Avatar,
  User,
  Download,
  Calendar,
  DataAnalysis,
  Tools,
  Sunny,
  BellFilled,
  WarnTriangleFilled,
  EditPen,
  Finished,
  Stamp,
  Bell,
  Moon,
  Promotion,
  Odometer,
  Fold,
  Expand,
  Refresh,
  View,
  Delete,
  Clock,
  CircleCheck,
  UserFilled,
  Setting,
  Message,
  Grid,
  Close,
  Iphone,
  Key,
  ArrowLeft,
  ChatDotRound
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/index.scss'
// 在入口处加载主题模块：其副作用会在文档加载时立即把已保存/系统主题写到 <html>，
// 从而保证登录、注册、找回密码这类不套用后台布局的整页也遵循深色模式
// （若只在 layout 里引入，直接在 /login 打开页面时主题不会被应用）
import '@/utils/theme'

// 菜单、统计卡、待办面板通过字符串名称动态引用图标（<component :is="icon" />），
// 无法被构建工具静态识别，因此按名注册
const icons = {
  Cpu,
  ArrowDown,
  ArrowRight,
  SwitchButton,
  MagicStick,
  Tickets,
  Search,
  Warning,
  Timer,
  Plus,
  Check,
  Monitor,
  OfficeBuilding,
  Document,
  Lock,
  Avatar,
  User,
  Download,
  Calendar,
  DataAnalysis,
  Tools,
  Sunny,
  BellFilled,
  WarnTriangleFilled,
  EditPen,
  Finished,
  Stamp,
  Bell,
  Moon,
  Promotion,
  Odometer,
  Fold,
  Expand,
  Refresh,
  View,
  Delete,
  Clock,
  CircleCheck,
  UserFilled,
  Setting,
  Message,
  Grid,
  Close,
  Iphone,
  Key,
  ArrowLeft,
  ChatDotRound
}

const app = createApp(App)

for (const [name, component] of Object.entries(icons)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)

app.mount('#app')
