// 日期/时间格式化共享逻辑:按当前 i18n 语言输出本地化日期,并提供统一相对时间。
// 各页面不要再自行 new Date().toLocaleString('zh-CN')(切英文后仍显示中文格式)。
import i18n from '../i18n'

const curLocale = () => i18n.global.locale.value

export const formatDate = (d) => (d ? new Date(d).toLocaleDateString(curLocale()) : '')

export const formatDateTime = (d, opts) => (d ? new Date(d).toLocaleString(curLocale(), opts) : '')

// 相对时间:1 分钟内「刚才」,1 小时内按分钟,1 天内按小时,更早按天。
export const formatRelativeTime = (t, d) => {
  if (!d) return ''
  const diff = (Date.now() - new Date(d).getTime()) / 1000
  if (diff < 60) return t('time.justNow')
  if (diff < 3600) return t('time.minutesAgo', { n: Math.floor(diff / 60) })
  if (diff < 86400) return t('time.hoursAgo', { n: Math.floor(diff / 3600) })
  return t('time.daysAgo', { n: Math.floor(diff / 86400) })
}
