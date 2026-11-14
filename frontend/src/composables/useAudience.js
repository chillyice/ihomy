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
 *
 * 可选家庭清单是模块级缓存(跨组件实例共用):记录生成时的家庭 id,切换家庭自动重拉;
 * 入圈/退圈等圈关系变化由家庭设置页调用 invalidateFamilies() 清缓存,面板下次 load 时重新取数。
 */
let familyCache = null
let familyCacheFamilyId = null

/** 清空可选家庭缓存(圈关系或默认分享变化后调用,所有面板实例下次取数时重新拉) */
export function invalidateFamilies() {
  familyCache = null
  familyCacheFamilyId = null
}

export function useAudience(defaultType) {
  const userStore = useUserStore()
  const state = reactive({ loaded: false, canShare: true, selected: [], families: [], defaults: [], hasCircles: false })

  async function loadFamilies() {
    const familyId = userStore.userInfo?.familyId ?? null
    if (!familyCache || familyCacheFamilyId !== familyId) {
      familyCache = (await familyApi.shareDefaults()) || {}
      familyCacheFamilyId = familyId
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

  async function save(contentId, familyIds, type = defaultType) {
    return audienceApi.save(type, contentId, [...familyIds])
  }

  /** 保存后把默认值/可选清单同步到本地缓存(家庭设置页改完后调用) */
  function setFamilies(data) {
    familyCache = data || null
    familyCacheFamilyId = userStore.userInfo?.familyId ?? null
    state.families = (data && data.families) || []
    state.defaults = (data && data.targetFamilyIds) || []
    state.hasCircles = state.families.length > 0
  }

  return { state, load, save, setFamilies }
}
