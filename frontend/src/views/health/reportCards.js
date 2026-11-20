// 健康报表卡片目录(前端唯一来源):与后端 common/HealthReportCard 同口径。
// 新增卡片 = 后端加一行 + HealthReportService 加一段 + 这里加一行 + i18n 加标题;
// 卡片 id 就是接口里的 id,改名必须两边一起改,否则前端会渲染成空卡。
// group = 报表分组(睡眠/活动/体征/身体/提醒);detail = 「查看详情」要跳的 tab 与预选指标。

export const HEALTH_REPORT_CARDS = [
  { id: 'sleep_last', titleKey: 'health.report.cards.sleepLast', sensitive: false, group: 'sleep', detail: { tab: 'sleep' } },
  {
    id: 'steps_7d',
    titleKey: 'health.report.cards.steps7d',
    sensitive: false,
    group: 'activity',
    detail: { tab: 'activity', metric: 'STEPS' },
  },
  {
    id: 'energy_7d',
    titleKey: 'health.report.cards.energy7d',
    sensitive: false,
    group: 'activity',
    detail: { tab: 'activity', metric: 'ACTIVE_ENERGY' },
  },
  {
    id: 'hr_day',
    titleKey: 'health.report.cards.hrDay',
    sensitive: false,
    group: 'vitals',
    detail: { tab: 'vitals', metric: 'HEART_RATE' },
  },
  { id: 'alerts', titleKey: 'health.report.cards.alerts', sensitive: false, group: 'alerts', detail: null },
  { id: 'sleep_7d', titleKey: 'health.report.cards.sleep7d', sensitive: false, group: 'sleep', detail: { tab: 'sleep' } },
  {
    id: 'exercise_7d',
    titleKey: 'health.report.cards.exercise7d',
    sensitive: false,
    group: 'activity',
    detail: { tab: 'activity', metric: 'EXERCISE_MIN' },
  },
  {
    id: 'distance_7d',
    titleKey: 'health.report.cards.distance7d',
    sensitive: false,
    group: 'activity',
    detail: { tab: 'activity', metric: 'DISTANCE' },
  },
  {
    id: 'resting_hr_7d',
    titleKey: 'health.report.cards.restingHr7d',
    sensitive: false,
    group: 'vitals',
    detail: { tab: 'vitals', metric: 'RESTING_HR' },
  },
  { id: 'hrv_7d', titleKey: 'health.report.cards.hrv7d', sensitive: false, group: 'vitals', detail: { tab: 'vitals', metric: 'HRV' } },
  { id: 'spo2_day', titleKey: 'health.report.cards.spo2Day', sensitive: false, group: 'vitals', detail: { tab: 'vitals', metric: 'SPO2' } },
  {
    id: 'weight_trend',
    titleKey: 'health.report.cards.weightTrend',
    sensitive: false,
    group: 'body',
    detail: { tab: 'body', metric: 'WEIGHT' },
  },
  { id: 'menstrual_7d', titleKey: 'health.report.cards.menstrual7d', sensitive: true, group: 'body', detail: null },
]

/** 分组顺序(报表页按这个顺序出小节标题) */
export const CARD_GROUPS = ['sleep', 'activity', 'vitals', 'body', 'alerts']

/** 默认报表 = 最近一晚睡眠 + 近 7 天步数/活动消耗 + 最近一天心率 + 异常提醒(与后端 DEFAULTS 一致) */
export const DEFAULT_CARDS = ['sleep_last', 'steps_7d', 'energy_7d', 'hr_day', 'alerts']

/** 单张报表最多卡片数(与后端 MAX_CARDS 一致) */
export const MAX_CARDS = 13

export const cardMeta = (id) => HEALTH_REPORT_CARDS.find((c) => c.id === String(id || '')) || null

export const isSensitiveCard = (id) => !!cardMeta(id)?.sensitive

export const cardTitle = (t, id) => {
  const meta = cardMeta(id)
  return meta ? t(meta.titleKey) : String(id || '')
}

/** 按分组聚合(cards 保持接口给的顺序);空组不出现 */
export const groupCards = (cards) => {
  const map = new Map()
  ;(Array.isArray(cards) ? cards : []).forEach((card) => {
    const meta = cardMeta(card?.id)
    if (!meta) return
    if (!map.has(meta.group)) map.set(meta.group, [])
    map.get(meta.group).push({ ...card, meta })
  })
  return CARD_GROUPS.filter((g) => map.has(g)).map((g) => ({ key: g, cards: map.get(g) }))
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
