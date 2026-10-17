// 工具箱卡片顺序:偏好只存本机(localStorage),不落库
// mergeToolOrder 负责把「已存顺序」与「当前默认清单」合并:新工具自动补到末尾,旧工具保留原位
export const TOOL_ORDER_STORAGE_KEY = 'ihomy:tools:order:v1'

export function mergeToolOrder(saved, defaultIds) {
  const known = new Set(defaultIds)
  const merged = []
  for (const id of Array.isArray(saved) ? saved : []) {
    if (known.has(id) && !merged.includes(id)) merged.push(id)
  }
  for (const id of defaultIds) {
    if (!merged.includes(id)) merged.push(id)
  }
  return merged
}

export function loadToolOrder(defaultIds, storage = globalThis.localStorage) {
  try {
    const raw = storage?.getItem(TOOL_ORDER_STORAGE_KEY)
    return mergeToolOrder(raw ? JSON.parse(raw) : null, defaultIds)
  } catch (e) {
    return mergeToolOrder(null, defaultIds)
  }
}

export function saveToolOrder(order, storage = globalThis.localStorage) {
  try {
    storage?.setItem(TOOL_ORDER_STORAGE_KEY, JSON.stringify(order))
  } catch (e) {
    // 隐私模式或配额满时不落盘,本次会话内排序仍然生效
  }
}

export function moveToolTo(order, id, targetId) {
  const from = order.indexOf(id)
  const to = order.indexOf(targetId)
  if (from < 0 || to < 0 || from === to) return order
  const next = order.slice()
  next.splice(from, 1)
  next.splice(to, 0, id)
  return next
}

export function moveToolBy(order, id, delta) {
  const from = order.indexOf(id)
  if (from < 0 || !delta) return order
  const to = Math.max(0, Math.min(order.length - 1, from + delta))
  if (to === from) return order
  return moveToolTo(order, id, order[to])
}
