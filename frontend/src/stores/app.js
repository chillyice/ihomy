// 首页聚合 store:家庭信息/模块/照片/统计集中拉取,避免各组件各自重复请求
import { defineStore } from 'pinia'
import { publicApi, homeApi } from '@/api'
import { parseShareQuery } from '@/utils/shareLanding'

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
    toggleHomeEditMode() {
      this.homeEditMode = !this.homeEditMode
    },
    // 初始化首页数据:公开数据 + 登录后私有仪表盘并行拉取(原串行两个 RTT)
    async init(force = false) {
      if (this.loaded && !force) return
      if (this.loading) return
      this.loading = true
      try {
        // 分享落地(/?hid=xxx 或 ?home_id=):游客打开分享链接时按链接里的家庭取数,
        // 否则暖居/移动端会一律回落到「默认演示家庭」,看到的是别人家。
        const { hid, homeId } = parseShareQuery(window.location.search)
        const [pub, dash] = await Promise.all([
          publicApi.getHome(homeId ?? undefined, hid || undefined).catch(() => null),
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
