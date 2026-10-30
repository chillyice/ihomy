// 日程只读来源(贷款还款/理财到期/食材到期/计划子任务):纯展示开关,只影响前端展示,
// 不改后端聚合口径、不发额外请求、不落库。
// 口径见 docs/设计想法/功能/家庭日程/日程提醒重构-计划.md §8。

/** 只读来源:日程页只能看与跳转,不可勾选/编辑/删除 */
export const READ_ONLY_SOURCES = ['LOAN', 'FINANCE', 'ITEM_EXPIRY']

/** 来源开关顺序(与后端 Provider 顺序一致:贷款 → 理财 → 食材,计划子任务在前) */
export const SOURCE_OPTIONS = ['PLAN_TASK', 'LOAN', 'FINANCE', 'ITEM_EXPIRY']

/** 前端偏好键:只存本机展示开关,默认全开 */
export const SOURCE_STORAGE_KEY = 'ihomy:schedule:sources:v1'

/** 全开的一份新对象(调用方直接改不会被共享) */
export function defaultSourcePrefs() {
  return { PLAN_TASK: true, LOAN: true, FINANCE: true, ITEM_EXPIRY: true }
}

/** 读偏好:缺键/坏 JSON/存储不可用都回落全开,不抛错 */
export function loadSourcePrefs(storage = globalThis.localStorage) {
  const prefs = defaultSourcePrefs()
  try {
    const raw = storage?.getItem(SOURCE_STORAGE_KEY)
    if (!raw) return prefs
    const saved = JSON.parse(raw)
    SOURCE_OPTIONS.forEach((s) => {
      if (typeof saved?.[s] === 'boolean') prefs[s] = saved[s]
    })
  } catch (e) {
    // 存储被禁用或内容损坏时静默回落默认值
  }
  return prefs
}

/** 写偏好:存储不可用(隐私模式)时静默失败,不影响页面 */
export function saveSourcePrefs(prefs, storage = globalThis.localStorage) {
  try {
    storage?.setItem(SOURCE_STORAGE_KEY, JSON.stringify(prefs))
  } catch (e) {
    // 忽略写入失败
  }
}

/** 事项来源:后端只读来源带 source;老响应/计划子任务按 kind 兜底;其余日程无来源 */
export function sourceOf(item) {
  if (!item) return ''
  if (item.source) return item.source
  return SOURCE_OPTIONS.includes(item.kind) ? item.kind : ''
}

/** 是否只读来源(带跳转、不可在日程页改删) */
export const isReadOnlySource = (item) => READ_ONLY_SOURCES.includes(sourceOf(item))

/** 按开关过滤一份事项:无来源的日程始终保留,关闭的来源整类隐藏 */
export function filterSourceItems(items = [], prefs = {}) {
  return items.filter((it) => prefs[sourceOf(it)] !== false)
}

/** 按开关过滤 { 日期: [事项] }:保留全部日期键(空格子仍显示为空数组,圆点自然消失) */
export function filterEventDays(days = {}, prefs = {}) {
  const out = {}
  Object.keys(days).forEach((date) => {
    out[date] = filterSourceItems(days[date] || [], prefs)
  })
  return out
}
