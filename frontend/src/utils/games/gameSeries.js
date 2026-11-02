// 小游戏系列:自由文本(如「宝可梦」),归一口径与后端 common/GameSeries.normalize 一致。
// 自动归类由后端按名称完成(导入留空时 + 「自动归类」按钮),前端只负责展示、去重与筛选。
export const UNSERIES = '__UNSERIES__'

export const normalizeGameSeries = (value) => String(value ?? '').trim()

/** 当前家庭已出现的系列,去重后按中文排序;空值(未归类)不列入 */
export const seriesOptionsOf = (items) => {
  const set = new Set()
  for (const item of items || []) {
    const s = normalizeGameSeries(item && item.series)
    if (s) set.add(s)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'zh-CN'))
}

/** active 为空=全部系列,UNSERIES=未归系列,其余按系列名精确匹配 */
export const inGameSeries = (item, active) => {
  if (!active) return true
  const s = normalizeGameSeries(item && item.series)
  return active === UNSERIES ? !s : s === active
}
