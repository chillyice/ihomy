// 功能开关的响应式状态(模块级单例):导航、路由守卫、首页卡片、小游戏页共用同一份。
// 与显示模式一样按账号存在本机;家长替孩子设置时也只改这台设备上的记录。
import { ref } from 'vue'
import { getDisplayMode } from './useDisplayMode'
import {
  clearUserFeatureGates,
  gateOf,
  isGateVisible,
  isPathHidden,
  normalizeGateState,
  readUserFeatureGates,
  setUserFeatureGate,
} from '@/utils/featureGates'

const gates = ref({})
// 当前内存状态属于哪个账号;null = 未登录(游客记录)
let boundUser = null

const sameUser = (userId) => userId != null && String(userId) === String(boundUser)

/** 绑定登录账号并贴回该账号的功能开关;登录/登出/切账号时调 */
export function initFeatureGates(userId = null) {
  boundUser = userId ?? null
  gates.value = readUserFeatureGates(boundUser)
}

/** 非组件场景(导航构建/路由守卫)读当前账号的开关表 */
export function getFeatureGates() {
  return gates.value
}

/** 当前账号 + 当前显示模式:这个功能是否可见 */
export function isFeatureVisibleNow(featureId) {
  return isGateVisible(gateOf(gates.value, featureId), getDisplayMode())
}

/** 当前账号 + 当前显示模式:这个路径是否被开关隐藏(直达链接用) */
export function isPathHiddenNow(path) {
  return isPathHidden(path, gates.value, getDisplayMode())
}

/** 查某个账号在这台设备上的开关表(设置页选择目标成员时用) */
export function featureGatesFor(userId) {
  if (sameUser(userId)) return gates.value
  if (userId == null) return gates.value
  return readUserFeatureGates(userId)
}

/** 设置某个账号的一项开关;目标是当前账号时立即生效 */
export function setFeatureGateFor(userId, featureId, state) {
  if (userId == null || !featureId) return false
  const next = setUserFeatureGate(userId, featureId, normalizeGateState(state))
  if (sameUser(userId)) gates.value = next
  return true
}

/** 恢复默认(全部启用);目标是当前账号时立即生效 */
export function resetFeatureGatesFor(userId) {
  if (userId == null) return false
  clearUserFeatureGates(userId)
  if (sameUser(userId)) gates.value = {}
  return true
}

export function useFeatureGates() {
  return {
    gates,
    isVisible: isFeatureVisibleNow,
    isPathHidden: isPathHiddenNow,
    gatesFor: featureGatesFor,
    setGateFor: setFeatureGateFor,
    resetFor: resetFeatureGatesFor,
  }
}
