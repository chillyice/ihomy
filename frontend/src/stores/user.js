import { defineStore } from 'pinia'
import request from '@/api/request'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null'),
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isOwner: (state) => state.userInfo?.role === 'OWNER',
  },
  actions: {
    async login(payload) {
      const data = await request.post('/auth/login', payload)
      this.setToken(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('userInfo', JSON.stringify(data.user))
      return data
    },
    async register(payload) {
      const data = await request.post('/auth/register', payload)
      this.setToken(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('userInfo', JSON.stringify(data.user))
      return data
    },
    async refresh() {
      if (!this.refreshToken) throw new Error('no refresh token')
      const data = await request.post('/auth/refresh', { refreshToken: this.refreshToken })
      this.setToken(data.accessToken, data.refreshToken)
      this.userInfo = data.user
      localStorage.setItem('userInfo', JSON.stringify(data.user))
    },
    async fetchMe() {
      const data = await request.get('/auth/me')
      this.userInfo = data
      localStorage.setItem('userInfo', JSON.stringify(data))
    },
    setToken(token, refreshToken) {
      this.token = token
      this.refreshToken = refreshToken
      localStorage.setItem('token', token)
      localStorage.setItem('refreshToken', refreshToken)
    },
    logout() {
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
