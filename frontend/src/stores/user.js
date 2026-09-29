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
    // 首登强制改密(种子账号 admin/ops):为 true 时必须先改密才能使用其它功能
    mustChangePassword: (state) => !!state.userInfo?.mustChangePassword,
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
    async changePassword(payload) {
      // 修改密码:后端重签令牌并清除强制改密标记,整体替换本地凭证
      const data = await request.put('/profile/password', payload)
      this.setToken(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('userInfo', JSON.stringify(data.user))
      return data
    },
    setToken(token, refreshToken) {
      this.token = token
      // 首登强制改密时后端不签发刷新令牌(refreshToken 为 null),归一为空串避免存成 "null"
      this.refreshToken = refreshToken || ''
      localStorage.setItem('token', token || '')
      localStorage.setItem('refreshToken', refreshToken || '')
    },
    bumpBgMusic() {
      this.bgMusicVersion++
    },
    logout() {
      // 通知后端登出(access 与本次会话的 refresh token 一并吊销),再清空本地登录态
      if (this.token || this.refreshToken) request.post('/auth/logout', { refreshToken: this.refreshToken }).catch(() => {})
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('userInfo')
      // 清掉 Service Worker 缓存过的接口响应,避免登出后仍可离线读到上一账号的数据
      if (typeof caches !== 'undefined') caches.delete('api-cache').catch(() => {})
    },
  },
})

// 多标签共享登录态:refresh token 每次续期即轮换(旧值作废),另一标签轮换后必须同步到本标签,
// 否则本标签仍拿旧令牌续期会被判失效、把自己(乃至整个浏览器)登出。storage 事件只在其他标签发写时触发。
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key !== 'token' && e.key !== 'refreshToken' && e.key !== 'userInfo') return
    const store = useUserStore()
    store.token = localStorage.getItem('token') || ''
    store.refreshToken = localStorage.getItem('refreshToken') || ''
    try { store.userInfo = JSON.parse(localStorage.getItem('userInfo') || 'null') } catch { store.userInfo = null }
  })
}
