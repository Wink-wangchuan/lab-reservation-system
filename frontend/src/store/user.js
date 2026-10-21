import { defineStore } from 'pinia'
import { login as loginApi, loginByCode as loginByCodeApi, logout as logoutApi, getInfo } from '@/api'
import { getToken, setToken, removeToken, setUser, getUser } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: getUser() || null,
    permissions: getUser()?.permissions || []
  }),

  getters: {
    isLoggedIn: (s) => !!s.token,
    roles: (s) => s.userInfo?.roles || [],
    hasPerm: (s) => (perm) => {
      if (!s.permissions || s.permissions.length === 0) return false
      if (s.permissions.includes('*')) return true
      return s.permissions.includes(perm)
    }
  },

  actions: {
    async doLogin(username, password) {
      const data = await loginApi({ username, password })
      return this.acceptLogin(data)
    },

    /**
     * 验证码登录：账号可为用户名或手机号
     */
    async doLoginByCode(account, code) {
      const data = await loginByCodeApi({ account, code })
      return this.acceptLogin(data)
    },

    /**
     * 两种登录方式共用的落地流程：写入令牌 → 拉取当前用户信息
     */
    async acceptLogin(data) {
      this.token = data.token
      setToken(data.token)
      await this.fetchInfo()
      return data
    },

    async fetchInfo() {
      const info = await getInfo()
      this.userInfo = info
      this.permissions = info.permissions || []
      setUser(info)
      return info
    },

    async doLogout() {
      try {
        await logoutApi()
      } catch {
        // 忽略登出接口异常
      } finally {
        this.reset()
      }
    },

    reset() {
      this.token = ''
      this.userInfo = null
      this.permissions = []
      removeToken()
    }
  }
})