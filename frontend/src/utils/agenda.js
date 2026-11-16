// 今日列表:把 /plan/today 的 plans + reminders + undated 合并成一行行日程
// (首页卡、暖居卡、早安页、今日页签共用)。
// 排序口径:提醒时刻升序(没有时刻的排最后),同一时刻计划在前,再按 id。
// undated 是无日期待办(计划本身或计划下无截止日的子任务),没有时刻,自然排在最后。
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
 * @param {{plans?: Array, reminders?: Array, undated?: Array}} today GET /plan/today 的返回
 * @param {number} limit 取前 N 条(默认不限)
 * @returns {Array<{key,id,kind,title,time,overdueDays,doneCount,totalCount,fromPlan,undated,done,repeatType,planId,planTitle}>}
 */
export function buildAgenda(today, limit = Infinity) {
  const plans = Array.isArray(today?.plans) ? today.plans : []
  const reminders = Array.isArray(today?.reminders) ? today.reminders : []
  const undated = Array.isArray(today?.undated) ? today.undated : []
  const rows = [
    ...plans.map((p) => ({
      key: 'p' + p.id,
      id: p.id,
      kind: 'plan',
      sourceKind: 'PLAN',
      title: p.title,
      time: timeOf(p.remindTime),
      overdueDays: p.overdue ? p.overdueDays || 0 : 0,
      doneCount: p.doneCount ?? 0,
      totalCount: p.totalCount ?? 0,
      fromPlan: false,
      undated: false,
      done: false,
      repeatType: '',
      planId: null,
      planTitle: '',
    })),
    ...reminders.map((r) => ({
      key: 'r' + r.id,
      id: r.id,
      kind: 'reminder',
      sourceKind: 'REMINDER',
      title: r.title,
      time: timeOf(r.remindTime),
      overdueDays: 0,
      doneCount: 0,
      totalCount: 0,
      fromPlan: !!r.fromPlan,
      undated: false,
      done: false,
      repeatType: r.repeatType || 'ONCE',
      planId: r.planId ?? null,
      planTitle: '',
    })),
    ...undated.map((u) => {
      const task = u.kind === 'PLAN_TASK'
      return {
        key: (task ? 'ut' : 'up') + u.id,
        id: u.id,
        kind: task ? 'task' : 'plan',
        sourceKind: task ? 'PLAN_TASK' : 'PLAN',
        title: u.title,
        time: '',
        overdueDays: 0,
        doneCount: 0,
        totalCount: 0,
        fromPlan: task,
        undated: true,
        done: !!u.done,
        repeatType: '',
        planId: u.planId ?? null,
        planTitle: u.planTitle || '',
      }
    }),
  ]
  rows.sort(compareRows)
  return rows.slice(0, limit)
}

export { timeOf }
