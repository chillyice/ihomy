// 首页聚合 store:家庭信息/模块/照片/统计集中拉取,避免各组件各自重复请求
import { defineStore } from 'pinia'
import { publicApi, homeApi } from '@/api'
import { parseShareQuery } from '@/utils/shareLanding'
import { useUserStore } from './user'

export const useAppStore = defineStore('app', {
  state: () => ({
    family: null,
    modules: [],
    photos: [],
    stats: { memberCount: 0, upcomingEvents: [] },
    familyLoaded: false,
    dashboardLoaded: false,
    loadFailed: false,
    generation: 0,
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
      if (this.loading) return
      const { hid, homeId } = parseShareQuery(window.location.search)
      const needsDashboard = useUserStore().isLoggedIn && !hid && !homeId
      const fetchFamily = force || !this.familyLoaded
      const fetchDashboard = needsDashboard && (force || !this.dashboardLoaded)
      if (!fetchFamily && !fetchDashboard) return
      const generation = this.generation
      this.loading = true
      const loadFamily = async () => {
        if (!fetchFamily) return
        const pub = await publicApi.getHome(homeId ?? undefined, hid || undefined).catch(() => null)
        if (generation !== this.generation) return
        this.familyLoaded = !!pub
        if (!pub) return
        this.family = pub.family || null
        if (!this.dashboardLoaded) this.modules = pub.modules || []
        this.photos = pub.photos || []
        this.stats = pub.stats || { memberCount: 0, upcomingEvents: [] }
      }
      const loadDashboard = async () => {
        if (!fetchDashboard) return
        const dash = await homeApi.getDashboard().catch(() => null)
        if (generation !== this.generation) return
        this.dashboardLoaded = Array.isArray(dash?.modules)
        if (this.dashboardLoaded) this.modules = dash.modules
      }
      try {
        await Promise.all([loadFamily(), loadDashboard()])
        if (generation !== this.generation) return
        this.loadFailed = !this.familyLoaded || (needsDashboard && !this.dashboardLoaded)
      } finally {
        if (generation === this.generation) this.loading = false
      }
    },
    // 登出/切换家庭时清空缓存,下次 init 强制重新拉取
    reset() {
      this.generation++
      this.family = null
      this.modules = []
      this.photos = []
      this.stats = { memberCount: 0, upcomingEvents: [] }
      this.familyLoaded = false
      this.dashboardLoaded = false
      this.loading = false
      this.loadFailed = false
    },
  },
})
