// 愿望单共享口径(V10.92):分类候选项 + 来源类型 → 站内跳转。
// 愿望单页与「加入愿望单」弹窗共用,避免两处各写一份。

// 分类候选项(允许自由输入;值即落库文本)
export const WISH_CATEGORIES = ['生日礼物', '家庭用品', '旅行', '数码设备', '美食', '影视', '其他']

// 各入口的默认分类:放映厅许愿默认归到「影视」
export const WISH_SOURCE_DEFAULT_CATEGORY = { CINEMA: '影视' }

// 来源类型回跳:放映厅详情带条目ID时回该作品,其余回来源页
const SOURCE_ROUTE = {
  CINEMA: '/cinema',
  ANNIVERSARY: '/plan?tab=anniversary',
  GAME: '/games',
  PLAN: '/plan',
}

export const wishSourceRoute = (sourceType, sourceId) => {
  if (sourceType === 'CINEMA' && sourceId) return `/cinema/${sourceId}`
  return SOURCE_ROUTE[sourceType] || ''
}
