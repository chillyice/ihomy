// V3.9 数据字典前端映射: 与 sys_dict_item 对应,文案走 i18n(dict.<group>.<value>)。
// 调用方传入 vue-i18n 的 t 函数,未知值回退原值,避免页面空白。
// ponytail: 用 t(key) === key 判定缺失(vue-i18n 缺键时返回 key 本身),无需 te
export const dictText = (t, group, value) => {
  const key = `dict.${group}.${value}`
  const msg = t(key)
  return msg === key ? value || '' : msg
}

// 气象预警级别(和风天气国内预警): 升序 + 颜色;未知级别按橙色处理
export const WARN_LEVEL_ORDER = ['白色', '蓝色', '黄色', '橙色', '红色']
export const warnLevelColor = (level) =>
  ({
    白色: '#9a9a9a',
    蓝色: '#4a90d9',
    黄色: '#d4a13f',
    橙色: '#e0862f',
    红色: '#d94a3f',
  })[level] || '#e0862f'
/** 预警类型 → 和风图标库编号:国内预警类型码 1001-1089 与图标编号同号,
 *  国际预警 2001-2084 同样有对应图标;缺码或未知类型回退通用预警图标 9999(三角叹号)。 */
export const warnIconCode = (w) => {
  const code = String(typeof w === 'object' && w ? (w.code ?? '') : (w ?? '')).trim()
  return /^(10\d{2}|20\d{2})$/.test(code) ? code : '9999'
}
/** 预警信号底色上的字色:黄色 / 白色底太浅,用深色字保证可读;其余用白字。 */
export const warnInkColor = (level) => (level === '黄色' || level === '白色' ? '#3a2e22' : '#ffffff')
/** 取预警列表里级别最高的一条(颜色用)。
 *  未知级别经 indexOf 得 -1,按最低级别对待:只有全部未知时才会返回列表首个元素。 */
export const topWarning = (warnings) => {
  const list = warnings || []
  let best = null
  for (const w of list) {
    const lv = WARN_LEVEL_ORDER.indexOf(w.level)
    if (best === null || lv > WARN_LEVEL_ORDER.indexOf(best.level)) best = w
  }
  return best
}

// 空气质量等级(和风 AQI level 1-6,对应优/良/轻度/中度/重度/严重污染):
// 沿用国标色系顺序(绿→黄→橙→红→紫→褐),按项目暖色底做了轻微降饱和;未知等级按良处理
export const AQI_LEVEL_COLOR = {
  1: '#6b9b6b',
  2: '#c9a227',
  3: '#e0862f',
  4: '#d94a3f',
  5: '#9b5a9e',
  6: '#7d3c4a',
}
export const aqiColor = (level) => AQI_LEVEL_COLOR[Number(level)] || '#c9a227'

// 生活指数类别(和风 category 文案)→ 「舒适 ↔ 不适宜」四档:
// good 牛油果绿 / fair 橄榄金 / poor 陶土橙 / bad 主题红(色值由页面 CSS 变量给,这里只分档)。
// 用精确匹配而非包含:和风文案里「较不宜」含「不宜」、「很不舒适」含「舒适」,子串判定会串档;
// 化妆/空调/太阳镜(保湿/防晒/开启/需要…)不是舒适度量表,返回空串 → 保持品牌色。
export const INDEX_CAT_TONE = {
  good: ['适宜', '较适宜', '舒适', '较舒适', '不易发', '少发', '最弱', '弱', '优', '良', '良好'],
  fair: ['一般', '中等', '中', '较冷', '较热'],
  poor: ['较不宜', '较不适宜', '较不舒适', '较易发', '较差', '热', '冷'],
  bad: ['不宜', '不适宜', '很不舒适', '极易发', '易发', '很差', '差', '强', '很强', '炎热', '寒冷'],
}
export const indexCatTone = (category) => {
  const c = (category || '').trim()
  if (!c) return ''
  for (const [tone, list] of Object.entries(INDEX_CAT_TONE)) if (list.includes(c)) return tone
  return ''
}

// 月相(和风 v1 astro.moonPhase 代码)→ emoji 图标;名称走 i18n weatherPage.moon.<code>
export const MOON_PHASE_ICON = {
  'new-moon': '🌑',
  'waxing-crescent': '🌒',
  'first-quarter': '🌓',
  'waxing-gibbous': '🌔',
  'full-moon': '🌕',
  'waning-gibbous': '🌖',
  'last-quarter': '🌗',
  'waning-crescent': '🌘',
}
export const moonIcon = (code) => MOON_PHASE_ICON[code] || '🌙'
/** 月相名称(走 i18n weatherPage.moon.<code>,缺键回退空串) */
export const moonPhaseText = (t, code) => {
  const key = 'weatherPage.moon.' + code
  const msg = t(key)
  return msg === key ? '' : msg
}
