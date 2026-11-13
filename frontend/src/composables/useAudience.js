import { reactive } from 'vue'
import { audienceApi, circleApi } from '@/api'
import { useUserStore } from '@/stores/user'

/**
 * 「给谁看」面板状态:可选亲友圈 + 当前内容已选中的圈。
 * 圈列表只随家庭变化,同一页面实例内缓存一次;内容 id 为空(新建)时只用缓存。
 * 不选任何圈 = 只给自家人;保存失败由请求层统一提示。
 */
export function useAudience(type) {
  const userStore = useUserStore()
  const state = reactive({ loaded: false, canShare: true, selected: [], circles: [] })
  let circleCache = null

  /** 打开面板前调用:重置并载入圈列表(传内容 id 时再读已选圈与可分享性) */
  async function load(contentId) {
    state.selected = []
    state.canShare = true
    state.circles = []
    if (!userStore.isLoggedIn) {
      state.loaded = true
      return
    }
    try {
      if (!circleCache) circleCache = (await circleApi.list()) || []
      state.circles = circleCache
      if (contentId) {
        const data = await audienceApi.get(type, contentId)
        state.circles = data.circles || circleCache
        state.selected = data.selectedCircleIds || []
        state.canShare = !!data.canShare
      }
    } catch {
      // 面板加载失败按「不能分享」处理,不阻断内容本身的保存
      state.canShare = false
    } finally {
      state.loaded = true
    }
  }

  async function save(contentId, circleIds) {
    return audienceApi.save(type, contentId, [...circleIds])
  }

  return { state, load, save }
}
