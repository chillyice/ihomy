// 小游戏分类:与后端 common/GameCategory、sys_dict_item.game_category 同值同序。
// 展示文案走 i18n dict.game_category.<value>;未识别/空值统一落 OTHER。
export const GAME_CATEGORIES = ['ACTION', 'PUZZLE', 'CASUAL', 'SIMULATION', 'ADVENTURE', 'RPG', 'STRATEGY', 'ARCADE', 'OTHER']

export const OTHER_CATEGORY = 'OTHER'

/** 归一:去空白转大写,白名单外的值落 OTHER(老数据没有分类时也走这里) */
export const normalizeGameCategory = (value) => {
  const upper = String(value ?? '')
    .trim()
    .toUpperCase()
  return GAME_CATEGORIES.includes(upper) ? upper : OTHER_CATEGORY
}

/** 统计各分类数量,返回的键恒在 GAME_CATEGORIES 内(筛选条按它决定显示哪些分类) */
export const countGameCategories = (items) => {
  const counts = Object.fromEntries(GAME_CATEGORIES.map((c) => [c, 0]))
  for (const item of items || []) {
    counts[normalizeGameCategory(item && item.category)] += 1
  }
  return counts
}

/** 是否属于选中的分类(ALL 表示不筛) */
export const inGameCategory = (item, active) => !active || active === 'ALL' || normalizeGameCategory(item && item.category) === active
