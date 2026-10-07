// 家庭动态流共享逻辑:类型标签 / 摘要 / 相对时间(首页·移动端首页·暖居首页同口径)。
// t 为 vue-i18n 的 t 函数;sliceLen 为日记摘要截断长度(历史口径不同,默认 40)。
export const FEED_TYPES = ['blog', 'diary', 'photo', 'video', 'wish', 'task', 'recipe', 'book']

export const feedTypeLabel = (t, type) => (FEED_TYPES.includes(type) ? t('feed.type.' + type) : '')

export const feedSummary = (t, f, sliceLen = 40) => {
  if (!f) return ''
  switch (f.type) {
    case 'blog': return f.title || ''
    case 'diary': return (f.content || '').slice(0, sliceLen)
    case 'photo': return t('feed.photoCount', { n: f.count || 0 })
    case 'video': return t('feed.uploadedVideo', { title: f.title || '' })
    case 'wish': return t(f.status === 'ACHIEVED' ? 'feed.wishAchieved' : 'feed.wishMade', { title: f.title || '' })
    case 'task': return t('feed.taskPublished', { title: f.title || '' })
    case 'recipe': return t('feed.recipeShared', { title: f.title || '' })
    case 'book': return t('feed.bookAdded', { title: f.title || '' })
    default: return ''
  }
}

