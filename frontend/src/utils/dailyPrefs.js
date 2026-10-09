// 每日内容偏好:与设置页 DailySettings 共用 localStorage ihomy-daily(纯前端展示偏好,不落库)
// 缺省:两个开关默认开,知识分类默认历史+生活
export const DEFAULT_DAILY_TYPES = ['history', 'life']

export function readDailyPrefs() {
  let p = {}
  try {
    p = JSON.parse(localStorage.getItem('ihomy-daily') || '{}')
  } catch {}
  return {
    imageOn: p.imageOn ?? true,
    knowledgeOn: p.knowledgeOn ?? true,
    types: Array.isArray(p.types) && p.types.length ? p.types : [...DEFAULT_DAILY_TYPES],
  }
}
