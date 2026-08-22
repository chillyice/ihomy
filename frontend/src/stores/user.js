// 用户状态 store:token/refreshToken/userInfo 持久化到 localStorage,统一处理登录、续期、切换家庭与登出
import { defineStore } from 'pinia'
import request from '@/api/request'

export const useUserStore = defineStore('user', {
  state: () => ({
    // 初始化时从 localStorage 恢复登录态,保证刷新页面后保持登录
    token: localStorage.getItem('token') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null'),
    bgMusicVersion: 0,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    // 当前家庭角色是否为家长(OWNER 权限豁免的依据)
    isOwner: (state) => state.userInfo?.role === 'OWNER',
    // 是否为运维管理员(V3.8:仅能访问 /ops 运维页;与家庭角色正交,可同时是某家庭 OWNER + 系统级 OPS)
    isOps: (state) => !!state.userInfo?.isOps,
    // 是否为纯 OPS 账号(只有 OPS 角色,没有家庭角色,不显示侧边栏)
    isPureOps: (state) => state.userInfo?.role === 'OPS',
    // 检查用户是否拥有某权限码(基于登录时返回的 permissions 列表)
    hasPerm: (state) => (code) => {
      const perms = state.userInfo?.permissions
      if (!perms || !Array.isArray(perms)) return false
      return perms.includes(code)
    },
    isGuest: (state) => !state.token,
  },
  actions: {
    async login(payload) {
      // 登录成功后保存 token 与用户信息,并同步 localStorage
      const data = await request.post('/auth/login', payload)
      this.setToken(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('userInfo', JSON.stringify(data.user))
      return data
    },
    // 检查 userInfo 是否完整(含 permissions 字段),不完整则静默刷新一次
    async ensureUserInfo() {
      if (this.token && this.userInfo && this.userInfo.permissions === undefined) {
        try { await this.refresh() } catch (e) { /* 忽略,下次 401 会处理 */ }
      }
    },
    async register(payload) {
      // 注册仅返回结果,不保存 token:注册成功后由页面跳转登录页
      return await request.post('/auth/register', payload)
    },
    async refresh() {
      if (!this.refreshToken) throw new Error('no refresh token')
      // 用 refresh token 换取新 access token(由请求拦截器 401 流程调用)
      const data = await request.post('/auth/refresh', { refreshToken: this.refreshToken })
      this.setToken(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('userInfo', JSON.stringify(data.user))
    },
    async switchFamily(familyId, setDefault = false) {
      // 切换当前家庭:后端按新家庭重签 token,前端整体替换本地凭证
      const data = await request.post('/auth/family/switch', { familyId, setDefault })
      this.setToken(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('userInfo', JSON.stringify(data.user))
      return data
    },
    setToken(token, refreshToken) {
      this.token = token
      this.refreshToken = refreshToken
      localStorage.setItem('token', token)
      localStorage.setItem('refreshToken', refreshToken)
    },
    bumpBgMusic() {
      this.bgMusicVersion++
    },
    logout() {
      // 通知后端登出(使其 refresh token 进入黑名单),再清空本地登录态
      if (this.token) request.post('/auth/logout').catch(() => {})
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
    },
  },
})
