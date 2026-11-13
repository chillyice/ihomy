import { reactive } from 'vue'
import { audienceApi, familyApi } from '@/api'
import { useUserStore } from '@/stores/user'

/**
 * 「给谁看」面板状态(四档可见性里的「亲友圈」档):
 * - families:当前家庭可选分享的圈内家庭(家庭名/圈内称呼/所属圈),来自家庭设置里的可选清单
 * - defaults:家庭级「默认分享」预选的家庭,新建内容选亲友圈时直接勾上
 * - selected:本条内容当前勾选的家庭 id
 *
 * load(contentId) 传了内容 id 时以服务端已选家庭为准;新建时用 applyDefaults=true 套默认值。
 * 不勾任何家庭 = 仍然只有自家人能看到,保存时不写受众行。
 */
export function useAudience(defaultType) {
  const userStore = useUserStore()
  const state = reactive({ loaded: false, canShare: true, selected: [], families: [], defaults: [], hasCircles: false })
  let familyCache = null

  async function loadFamilies() {
    if (!familyCache) {
      familyCache = (await familyApi.shareDefaults()) || {}
    }
    state.families = familyCache.families || []
    state.defaults = familyCache.targetFamilyIds || []
    state.hasCircles = state.families.length > 0
    return familyCache
  }

  /** 打开面板前调用:重置并载入可选家庭(传内容 id 时再读已选家庭与可分享性) */
  async function load(contentId, type = defaultType, { applyDefaults = false } = {}) {
    state.selected = []
    state.canShare = true
    state.families = []
    state.defaults = []
    state.hasCircles = false
    if (!userStore.isLoggedIn) {
      state.loaded = true
      return
    }
    try {
      await loadFamilies()
      if (contentId) {
        const data = await audienceApi.get(type, contentId)
        state.selected = data.selectedFamilyIds || []
        state.canShare = !!data.canShare
      } else if (applyDefaults) {
        state.selected = [...state.defaults]
      }
    } catch {
      // 面板加载失败按「不能分享」处理,不阻断内容本身的保存
      state.canShare = false
    } finally {
      state.loaded = true
    }
  }

  /** 换家庭后圈关系会变,设置页保存后也用它刷新 */
  function invalidateFamilies() {
    familyCache = null
  }

  async function save(contentId, familyIds, type = defaultType) {
    return audienceApi.save(type, contentId, [...familyIds])
  }

  /** 保存后把默认值/可选清单同步到本地缓存(家庭设置页改完后调用) */
  function setFamilies(data) {
    familyCache = data || null
    state.families = (data && data.families) || []
    state.defaults = (data && data.targetFamilyIds) || []
    state.hasCircles = state.families.length > 0
  }

  return { state, load, save, invalidateFamilies, setFamilies }
}
