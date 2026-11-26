// 成就墙展示用的纯逻辑:等级换算、进度百分比、按分类分组。
// 等级档位与后端 common/AchievementCatalog.LEVELS 同口径(后端是权威,这里只做展示兜底,
// 页面优先用接口返回的 summary.level / summary.nextLevelScore)
export const LEVELS = [
  { level: 1, minScore: 0 },
  { level: 2, minScore: 100 },
  { level: 3, minScore: 250 },
  { level: 4, minScore: 450 },
  { level: 5, minScore: 650 },
  { level: 6, minScore: 800 },
]

/** 分类展示顺序(与目录里出现的顺序一致:签到→积分→内容→任务→游戏) */
export const CATEGORY_ORDER = ['CHECKIN', 'POINTS', 'CONTENT', 'TASK', 'GAME']

/** 累计成就分 → 等级序号(低于第一档也返回 1) */
export function levelOf(score) {
  let level = 1
  for (const l of LEVELS) {
    if (Number(score) >= l.minScore) level = l.level
  }
  return level
}

/** 累计成就分 → 下一档起始分;已满级返回 -1 */
export function nextLevelScore(score) {
  for (const l of LEVELS) {
    if (Number(score) < l.minScore) return l.minScore
  }
  return -1
}

/** 等级序号 → 进入该档的起始分(未知等级按 1 档) */
export function levelFloor(level) {
  return LEVELS.find((l) => l.level === Number(level))?.minScore ?? 0
}

/** 单条成就进度百分比(0–100,达标封顶;阈值为 0 视为满进度) */
export function progressPercent(item) {
  const threshold = Number(item?.threshold) || 0
  const progress = Number(item?.progress) || 0
  if (threshold <= 0) return 100
  return Math.max(0, Math.min(100, Math.round((progress / threshold) * 100)))
}

/** 当前分到下一档之间的百分比(满级返回 100) */
export function levelProgress(score, level) {
  const next = nextLevelScore(score)
  if (next < 0) return 100
  const floor = levelFloor(level)
  const span = next - floor
  if (span <= 0) return 100
  return Math.max(0, Math.min(100, Math.round(((Number(score) - floor) / span) * 100)))
}

/**
 * 按分类分组:只返回有内容的分类,顺序固定为 CATEGORY_ORDER,
 * 未登记的分类排在最后(目录扩展时不丢条目)。
 */
export function groupByCategory(items) {
  const groups = new Map()
  for (const item of items || []) {
    const key = item?.category || 'OTHER'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(item)
  }
  const ordered = CATEGORY_ORDER.filter((c) => groups.has(c))
  const rest = [...groups.keys()].filter((c) => !CATEGORY_ORDER.includes(c))
  return [...ordered, ...rest].map((category) => ({ category, items: groups.get(category) }))
}

/** 分类 i18n 键(页面用 $t 包一层) */
export function categoryKey(category) {
  return `achievements.categories.${category}`
}

/** 单条成就名称/说明的 i18n 键 */
export function catalogKeys(code) {
  return {
    name: `achievements.catalog.${code}.name`,
    desc: `achievements.catalog.${code}.desc`,
  }
}

/** 解锁时间显示:后端返回 ISO 串,只留日期部分 */
export function unlockDate(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 10)
}
