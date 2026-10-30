// 清单模式的数据整形纯函数:/schedule/events 的 days 键 → 按日期分组的行。
// 与月历共用同一份事件数据;排序口径与后端一致(时刻升序、全天在后,同时刻按类型与 id)。

import { matchFilter, typeOfKind } from './scheduleType'

/** 同一时刻的顺序:纪念日 → 提醒 → 每日任务 → 事项 → 计划子任务 */
export const KIND_RANK = { ANNIVERSARY: 0, REMINDER: 1, TASK: 2, PLAN: 3, PLAN_TASK: 4 }

const rankOf = (kind) => KIND_RANK[kind] ?? 9

/** 复制并排序,不改入参 */
export function sortEvents(rows = []) {
  return [...rows].sort((a, b) => {
    const at = a.time || ''
    const bt = b.time || ''
    if (at !== bt) {
      if (!at) return 1
      if (!bt) return -1
      return at < bt ? -1 : 1
    }
    const ra = rankOf(a.kind)
    const rb = rankOf(b.kind)
    if (ra !== rb) return ra - rb
    return (a.id || 0) - (b.id || 0)
  })
}

/** 摊平成带 date 的行(日期升序,同日按 sortEvents) */
export function flattenEvents(days = {}) {
  const rows = []
  Object.keys(days)
    .sort()
    .forEach((date) => {
      sortEvents((days[date] || []).map((it) => ({ ...it, date: it.date || date }))).forEach((it) => rows.push(it))
    })
  return rows
}

/**
 * 清单模式:[{date, items}] 分组;filter 取 TYPE_FILTERS 的值。
 * 空日期组不输出(筛选后没有事项的日期不占位)。
 */
export function buildList(days = {}, filter = 'ALL') {
  const groups = []
  Object.keys(days)
    .sort()
    .forEach((date) => {
      const items = sortEvents(
        (days[date] || []).map((it) => ({ ...it, date: it.date || date })).filter((it) => matchFilter(it.kind, filter)),
      )
      if (items.length) groups.push({ date, items })
    })
  return groups
}

/** 某日的用户类型集合(月历格子只关心「有没有安排」,这里给带类型的去重结果) */
export function dayTypes(items = []) {
  const seen = []
  items.forEach((it) => {
    const type = typeOfKind(it.kind)
    if (!seen.includes(type)) seen.push(type)
  })
  return seen
}
