// 日程分类判定(唯一来源):用户只回答「日期 / 循环 / 提醒」三件事,分类由这里推导。
// 口径见 docs/设计想法/功能/家庭日程/日程提醒重构-计划.md §3:
//   无日期                        → PLAN(计划,落 family_plan,target_date 为空)
//   有日期 + 不循环 + 无提醒       → ITEM(事项)
//   有日期 + 不循环 + 当天提醒     → REMINDER(提醒,一次性)
//   有日期 + 循环 + 无提醒/提前提醒 → ANNIVERSARY(纪念日)
//   有日期 + 循环 + 当天提醒       → TASK(每日任务,循环提醒)
// 弹窗徽标、保存路由、清单筛选共用本文件,禁止在组件里另写一套判断。

/** 循环周期(与后端 repeat_type 同名) */
export const REPEAT = {
  NONE: 'NONE',
  DAILY: 'DAILY',
  WEEKLY: 'WEEKLY',
  MONTHLY: 'MONTHLY',
  YEARLY: 'YEARLY',
}

/** 提醒方式 */
export const REMIND = {
  NONE: 'NONE',
  ON_TIME: 'ON_TIME',
  ADVANCE: 'ADVANCE',
}

/** 用户可选的循环周期(弹窗顺序) */
export const REPEAT_OPTIONS = [REPEAT.NONE, REPEAT.DAILY, REPEAT.WEEKLY, REPEAT.MONTHLY, REPEAT.YEARLY]

/** 清单筛选顺序:全部 → 纪念日 → 提醒 → 每日任务 → 事项 → 计划 */
export const TYPE_FILTERS = ['ALL', 'ANNIVERSARY', 'REMINDER', 'TASK', 'ITEM', 'PLAN']

/**
 * 按三个开关推导分类。
 * @param {{hasDate?: boolean, repeat?: string, remind?: string}} input
 * @returns {'PLAN'|'ITEM'|'REMINDER'|'ANNIVERSARY'|'TASK'}
 */
export function classify({ hasDate = false, repeat = REPEAT.NONE, remind = REMIND.NONE } = {}) {
  if (!hasDate) return 'PLAN'
  const recurring = !!repeat && repeat !== REPEAT.NONE
  if (recurring) return remind === REMIND.ON_TIME ? 'TASK' : 'ANNIVERSARY'
  return remind === REMIND.ON_TIME ? 'REMINDER' : 'ITEM'
}

/**
 * 后端聚合接口的 kind → 用户叫法。
 * PLAN(有目标日)按「事项」展示;PLAN_TASK 是计划子任务,归到「计划」;
 * TASK 是循环提醒(每日任务),REMINDER 是一次性提醒。
 */
export const KIND_TYPE = {
  ANNIVERSARY: 'ANNIVERSARY',
  REMINDER: 'REMINDER',
  TASK: 'TASK',
  PLAN: 'ITEM',
  PLAN_TASK: 'PLAN',
}

/** kind 未知时按「事项」兜底,避免徽标空白 */
export const typeOfKind = (kind) => KIND_TYPE[kind] || 'ITEM'

export const typeLabelKey = (type) => `schedule.type.${type}`

/** 清单筛选:filter=ALL 不过滤,其余按 kind 映射后的类型匹配 */
export const matchFilter = (kind, filter) => filter === 'ALL' || typeOfKind(kind) === filter
