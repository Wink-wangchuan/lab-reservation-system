// Token 与「记住我」存取管理
//
// 登录态策略：
//   - 令牌只存 sessionStorage，关闭浏览器即退出；页面连续 1 小时无操作也会自动退出。
//   - 「记住我」只记住账号密码（localStorage），下次打开登录页自动回填，不等于保持登录态。
const TOKEN_KEY = 'labwise_token'
const USER_KEY = 'labwise_user'
const REMEMBER_KEY = 'labwise_remember_account'

// 账号密码做一层 Base64 编码，避免明文直读；注意这不是加密，仅用于本地便捷回填
function encode(text) {
  return btoa(encodeURIComponent(text))
}

function decode(text) {
  try {
    return decodeURIComponent(atob(text))
  } catch {
    return ''
  }
}

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  removeToken()
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}

export function getUser() {
  try {
    return JSON.parse(sessionStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function setUser(user) {
  sessionStorage.setItem(USER_KEY, JSON.stringify(user))
}

/** 保存「记住我」的账号密码 */
export function setRememberedAccount(username, password) {
  localStorage.setItem(REMEMBER_KEY, JSON.stringify({
    username: encode(username),
    password: encode(password)
  }))
}

/** 读取「记住我」的账号密码；未保存时返回 null */
export function getRememberedAccount() {
  try {
    const raw = JSON.parse(localStorage.getItem(REMEMBER_KEY) || 'null')
    if (!raw || !raw.username) return null
    return { username: decode(raw.username), password: decode(raw.password) }
  } catch {
    return null
  }
}

export function clearRememberedAccount() {
  localStorage.removeItem(REMEMBER_KEY)
}
