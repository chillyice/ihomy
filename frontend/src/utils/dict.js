// V3.9 数据字典前端映射: 与 sys_dict_item 对应,文案走 i18n(dict.<group>.<value>)。
// 调用方传入 vue-i18n 的 t 函数,未知值回退原值,避免页面空白。
// ponytail: 用 t(key) === key 判定缺失(vue-i18n 缺键时返回 key 本身),无需 te
export const dictText = (t, group, value) => {
  const key = `dict.${group}.${value}`
  const msg = t(key)
  return msg === key ? (value || '') : msg
}

// 气象预警级别(和风天气国内预警): 升序 + 颜色;未知级别按橙色处理
export const WARN_LEVEL_ORDER = ['白色', '蓝色', '黄色', '橙色', '红色']
export const warnLevelColor = (level) => ({
  白色: '#9a9a9a', 蓝色: '#4a90d9', 黄色: '#d4a13f', 橙色: '#e0862f', 红色: '#d94a3f',
}[level] || '#e0862f')
/** 取预警列表里级别最高的一条(颜色用) */
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
  1: '#6b9b6b', 2: '#c9a227', 3: '#e0862f', 4: '#d94a3f', 5: '#9b5a9e', 6: '#7d3c4a',
}
export const aqiColor = (level) => AQI_LEVEL_COLOR[Number(level)] || '#c9a227'

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
