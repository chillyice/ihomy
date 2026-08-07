// 首页聚合 store:家庭信息/模块/照片/统计集中拉取,避免各组件各自重复请求
import { defineStore } from 'pinia'
import { publicApi, homeApi } from '@/api'

export const useAppStore = defineStore('app', {
  state: () => ({
    family: null,
    modules: [],
    photos: [],
    stats: { memberCount: 0, upcomingEvents: [] },
    loaded: false,
    loading: false,
  }),
  getters: {
    familyName: (s) => s.family?.name || '',
  },
  actions: {
    // 初始化首页数据:先拉公开数据(访客/成员通用),登录后补充私有仪表盘模块
    async init(force = false) {
      if (this.loaded && !force) return
      if (this.loading) return
      this.loading = true
      try {
        const pub = await publicApi.getHome()
        this.family = pub.family || null
        this.modules = pub.modules || []
        this.photos = pub.photos || []
        this.stats = pub.stats || { memberCount: 0, upcomingEvents: [] }
        this.loaded = true
      } catch (e) {
        // 忽略
      } finally {
        this.loading = false
      }
      try {
        const dash = await homeApi.getDashboard()
        if (dash?.modules) this.modules = dash.modules
      } catch (e) {
        // 忽略
      }
    },
    // 登出/切换家庭时清空缓存,下次 init 强制重新拉取
    reset() {
      this.family = null
      this.modules = []
      this.photos = []
      this.stats = { memberCount: 0, upcomingEvents: [] }
      this.loaded = false
    },
  },
})