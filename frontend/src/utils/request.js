import axios from 'axios'
import router from '@/router'
import { getToken, removeToken } from './auth'

// 统一 HTTP 客户端
// baseURL 优先取构建期注入的 VITE_API_BASE（生产：前端 8092、后端 8091 分离部署时直连后端）；
// 未配置时回退 /api，走开发环境 Vite 代理
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 20000
})

// —— 请求拦截：附加 JWT ——
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// —— 响应拦截：统一解包 + 错误提示 + 401 跳登录 ——
// 同一页面往往并发多个请求，令牌失效时会同时返回 401；
// 用该标记保证只提示并跳转一次，避免连续弹出多条「登录已失效」
let handlingUnauthorized = false

service.interceptors.response.use(
  (response) => {
    // 文件下载（二进制）直接返回
    if (response.config.responseType === 'blob') {
      return response
    }
    const res = response.data
    // code=200 为成功，取出业务数据
    if (res.code === 200) {
      return res.data
    }
    // 业务错误统一提示
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(res)
  },
  (error) => {
    // HTTP 层错误
    const status = error.response?.status
    if (status === 401) {
      if (!handlingUnauthorized) {
        handlingUnauthorized = true
        ElMessage.error('登录已失效，请重新登录')
        removeToken()
        router.push('/login').finally(() => {
          // 留出跳转时间后复位，保证新会话再次过期时仍能正常提示
          setTimeout(() => { handlingUnauthorized = false }, 1000)
        })
      }
    } else if (status === 403) {
      ElMessage.error('无权限访问该资源')
    } else {
      const msg = error.response?.data?.message || error.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default service