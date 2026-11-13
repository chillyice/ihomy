/**
 * 可见范围四档(顺序固定:公开 > 亲友圈 > 家庭 > 私密),发布/编辑弹窗共用。
 * CIRCLE 只是「对外分享」的开关:真正分享给哪几个家庭由 AudiencePicker 选择,
 * 文案口径见需求 §6.4 与 friends-circle 计划。
 */
export const VISIBILITY = {
  PUBLIC: 'PUBLIC',
  CIRCLE: 'CIRCLE',
  FAMILY: 'FAMILY',
  PRIVATE: 'PRIVATE',
}

/** 选项顺序即界面顺序;调用方只传 t,不各自改文案 */
export function visibilityOptions(t) {
  return [
    { value: VISIBILITY.PUBLIC, label: t('visibility.public') },
    { value: VISIBILITY.CIRCLE, label: t('visibility.circle') },
    { value: VISIBILITY.FAMILY, label: t('visibility.family') },
    { value: VISIBILITY.PRIVATE, label: t('visibility.private') },
  ]
}

/** 只有选「亲友圈」时才需要选具体家庭 */
export function needsAudience(visibility) {
  return visibility === VISIBILITY.CIRCLE
}

const LABEL_KEYS = {
  [VISIBILITY.PUBLIC]: 'visibility.public',
  [VISIBILITY.CIRCLE]: 'visibility.circle',
  [VISIBILITY.FAMILY]: 'visibility.family',
  [VISIBILITY.PRIVATE]: 'visibility.private',
}

/** 列表角标/筛选等处的档位文案;未知值按「家庭」兜底(与后端默认一致) */
export function visibilityLabel(t, value) {
  return t(LABEL_KEYS[value] || 'visibility.family')
}
