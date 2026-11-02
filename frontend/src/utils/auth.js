// Token 存取管理
//
// 登录态策略：
//   - 默认「会话级」：token/user 存 sessionStorage，关闭浏览器（标签页结束）即退出；
//   - 勾选「7 天免登录」：改存 localStorage，浏览器重启后仍保持登录；
//   - 读取时两种存储都查，保证从「记住我」切换后不会读到旧值。
const TOKEN_KEY = 'labwise_token'
const USER_KEY = 'labwise_user'
const REMEMBER_KEY = 'labwise_remember'

function stores() {
  return [sessionStorage, localStorage]
}

export function getToken() {
  for (const store of stores()) {
    const token = store.getItem(TOKEN_KEY)
    if (token) return token
  }
  return null
}

export function setToken(token, remember = false) {
  removeToken()
  const store = remember ? localStorage : sessionStorage
  store.setItem(TOKEN_KEY, token)
  // 记录本次登录是否勾选「记住我」，供页面提示与后续写入用户信息时复用
  store.setItem(REMEMBER_KEY, remember ? '1' : '0')
}

export function isRemembered() {
  return sessionStorage.getItem(REMEMBER_KEY) === '1' || localStorage.getItem(REMEMBER_KEY) === '1'
}

export function removeToken() {
  for (const store of stores()) {
    store.removeItem(TOKEN_KEY)
    store.removeItem(USER_KEY)
    store.removeItem(REMEMBER_KEY)
  }
}

export function getUser() {
  for (const store of stores()) {
    const raw = store.getItem(USER_KEY)
    if (!raw) continue
    try {
      return JSON.parse(raw)
    } catch {
      // 缓存损坏时继续查另一个存储
    }
  }
  return null
}

export function setUser(user) {
  const store = isRemembered() ? localStorage : sessionStorage
  store.setItem(USER_KEY, JSON.stringify(user))
}
