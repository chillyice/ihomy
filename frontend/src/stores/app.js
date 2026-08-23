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
    homeEditMode: false,
  }),
  getters: {
    familyName: (s) => s.family?.name || '',
  },
  actions: {
    toggleHomeEditMode() { this.homeEditMode = !this.homeEditMode },
    // 初始化首页数据:公开数据 + 登录后私有仪表盘并行拉取(原串行两个 RTT)
    async init(force = false) {
      if (this.loaded && !force) return
      if (this.loading) return
      this.loading = true
      try {
        const [pub, dash] = await Promise.all([
          publicApi.getHome().catch(() => null),
          homeApi.getDashboard().catch(() => null),
        ])
        if (pub) {
          this.family = pub.family || null
          this.modules = pub.modules || []
          this.photos = pub.photos || []
          this.stats = pub.stats || { memberCount: 0, upcomingEvents: [] }
        }
        // 登录后私有模块覆盖公开模块列表
        if (dash?.modules) this.modules = dash.modules
        this.loaded = true
      } finally {
        this.loading = false
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