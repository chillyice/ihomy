// 健康报表卡片目录(前端唯一来源):与后端 common/HealthReportCard 同口径。
// 新增卡片 = 后端加一行 + HealthReportService 加一段 + 这里加一行 + i18n 加标题;
// 卡片 id 就是接口里的 id,改名必须两边一起改,否则前端会渲染成空卡。

export const HEALTH_REPORT_CARDS = [
  { id: 'sleep_last', titleKey: 'health.report.cards.sleepLast', sensitive: false },
  { id: 'steps_7d', titleKey: 'health.report.cards.steps7d', sensitive: false },
  { id: 'energy_7d', titleKey: 'health.report.cards.energy7d', sensitive: false },
  { id: 'hr_day', titleKey: 'health.report.cards.hrDay', sensitive: false },
  { id: 'alerts', titleKey: 'health.report.cards.alerts', sensitive: false },
  { id: 'sleep_7d', titleKey: 'health.report.cards.sleep7d', sensitive: false },
  { id: 'exercise_7d', titleKey: 'health.report.cards.exercise7d', sensitive: false },
  { id: 'resting_hr_7d', titleKey: 'health.report.cards.restingHr7d', sensitive: false },
  { id: 'spo2_day', titleKey: 'health.report.cards.spo2Day', sensitive: false },
  { id: 'weight_trend', titleKey: 'health.report.cards.weightTrend', sensitive: false },
  { id: 'menstrual_7d', titleKey: 'health.report.cards.menstrual7d', sensitive: true },
]

/** 默认报表 = 最近一晚睡眠 + 近 7 天步数/活动消耗 + 最近一天心率 + 异常提醒(与后端 DEFAULTS 一致) */
export const DEFAULT_CARDS = ['sleep_last', 'steps_7d', 'energy_7d', 'hr_day', 'alerts']

/** 单张报表最多卡片数(与后端 MAX_CARDS 一致) */
export const MAX_CARDS = 12

export const cardMeta = (id) => HEALTH_REPORT_CARDS.find((c) => c.id === String(id || '')) || null

export const isSensitiveCard = (id) => !!cardMeta(id)?.sensitive

export const cardTitle = (t, id) => {
  const meta = cardMeta(id)
  return meta ? t(meta.titleKey) : String(id || '')
}

/** 收口:未知忽略、去重、空回落默认(顺序保持调用方给的顺序,即界面上的展示顺序) */
export const sanitizeCards = (raw) => {
  const list = Array.isArray(raw) ? raw : []
  const out = []
  list.forEach((id) => {
    if (cardMeta(id) && !out.includes(id) && out.length < MAX_CARDS) out.push(id)
  })
  return out.length ? out : [...DEFAULT_CARDS]
}

/** 读了偏好里存的 JSON 卡片清单:解析失败/形状不对都回落默认,不让脏数据卡死报表 */
export const parseCardsPref = (raw) => {
  if (!raw) return [...DEFAULT_CARDS]
  try {
    return sanitizeCards(JSON.parse(raw))
  } catch (e) {
    return [...DEFAULT_CARDS]
  }
}
