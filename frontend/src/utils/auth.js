// Token 与「记住我」存取管理
//
// 登录态策略：
//   - 令牌只存 sessionStorage，关闭浏览器即退出；页面连续 1 小时无操作也会自动退出。
//   - 「记住我」只记住用户名（localStorage），下次打开登录页自动回填账号，密码仍需手动输入；
//     不保存密码，避免把凭据长期留在浏览器本地。
const TOKEN_KEY = 'labwise_token'
const USER_KEY = 'labwise_user'
const REMEMBER_KEY = 'labwise_remember_account'

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

/** 保存「记住我」的用户名（不保存密码） */
export function setRememberedAccount(username) {
  localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username }))
}

/** 读取记住的用户名；未保存时返回 null。兼容清理旧版本存过密码的数据 */
export function getRememberedAccount() {
  try {
    const raw = JSON.parse(localStorage.getItem(REMEMBER_KEY) || 'null')
    if (!raw || !raw.username) return null
    const username = raw.username
    // 旧版本曾保存 password，读取时顺手重写为只含用户名
    if (Object.prototype.hasOwnProperty.call(raw, 'password')) {
      localStorage.setItem(REMEMBER_KEY, JSON.stringify({ username }))
    }
    return { username }
  } catch {
    return null
  }
}

export function clearRememberedAccount() {
  localStorage.removeItem(REMEMBER_KEY)
}
