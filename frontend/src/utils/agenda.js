// 今日列表:把 /plan/today 的 plans + reminders 合并成一行行日程(首页卡、暖居卡、今日页签共用)。
// 排序口径:提醒时刻升序(没有时刻的排最后),同一时刻计划在前,再按 id。
// 纯函数:不做请求、不读时间,便于单测。

const timeOf = (t) => String(t || '').slice(0, 5)

const compareRows = (a, b) => {
  if (a.time !== b.time) {
    if (!a.time) return 1
    if (!b.time) return -1
    return a.time < b.time ? -1 : 1
  }
  if (a.kind !== b.kind) return a.kind === 'plan' ? -1 : 1
  return a.id - b.id
}

/**
 * @param {{plans?: Array, reminders?: Array}} today GET /plan/today 的返回
 * @param {number} limit 取前 N 条(默认不限)
 * @returns {Array<{key,id,kind,title,time,overdueDays,doneCount,totalCount,fromPlan}>}
 */
export function buildAgenda(today, limit = Infinity) {
  const plans = Array.isArray(today?.plans) ? today.plans : []
  const reminders = Array.isArray(today?.reminders) ? today.reminders : []
  const rows = [
    ...plans.map((p) => ({
      key: 'p' + p.id,
      id: p.id,
      kind: 'plan',
      title: p.title,
      time: timeOf(p.remindTime),
      overdueDays: p.overdue ? p.overdueDays || 0 : 0,
      doneCount: p.doneCount ?? 0,
      totalCount: p.totalCount ?? 0,
      fromPlan: false,
    })),
    ...reminders.map((r) => ({
      key: 'r' + r.id,
      id: r.id,
      kind: 'reminder',
      title: r.title,
      time: timeOf(r.remindTime),
      overdueDays: 0,
      doneCount: 0,
      totalCount: 0,
      fromPlan: !!r.fromPlan,
    })),
  ]
  rows.sort(compareRows)
  return rows.slice(0, limit)
}

export { timeOf }
