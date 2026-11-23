// 家庭动态流共享逻辑:类型标签 / 摘要 / 相对时间(首页·移动端首页·暖居首页同口径)。
// t 为 vue-i18n 的 t 函数;sliceLen 为日记摘要截断长度(历史口径不同,默认 40)。
export const FEED_TYPES = ['blog', 'diary', 'photo', 'video', 'wish', 'task', 'recipe', 'book']

const feedRoutes = {
  diary: '/diary',
  photo: '/album',
  video: '/cinema',
  wish: '/wish',
  task: '/task',
  recipe: '/kitchen',
  book: '/library',
  blog: '/blog',
}

export function feedPath(f) {
  if (f.type === 'blog' && f.id != null) return `/blog/${f.id}`
  if (f.type === 'recipe' && f.id != null) return `/kitchen/recipe/${f.id}`
  return feedRoutes[f.type]
}

export const feedTypeLabel = (t, type) => (FEED_TYPES.includes(type) ? t('feed.type.' + type) : '')

export const feedSummary = (t, f, sliceLen = 40) => {
  if (!f) return ''
  switch (f.type) {
    case 'blog':
      return f.title || ''
    case 'diary':
      return (f.content || '').slice(0, sliceLen)
    case 'photo':
      return t('feed.photoCount', { n: f.count || 0 })
    case 'video':
      return t('feed.uploadedVideo', { title: f.title || '' })
    case 'wish':
      return t(f.status === 'ACHIEVED' ? 'feed.wishAchieved' : 'feed.wishMade', { title: f.title || '' })
    case 'task':
      return t('feed.taskPublished', { title: f.title || '' })
    case 'recipe':
      return t('feed.recipeShared', { title: f.title || '' })
    case 'book':
      return t('feed.bookAdded', { title: f.title || '' })
    default:
      return ''
  }
}

// 动态类型 → 图标 / 色相锚点(--blob-N):首页列表芯片与横滑卡片共用,避免两套映射
const FEED_ICONS = { blog: '📝', diary: '📖', photo: '📷', video: '🎬', wish: '🎁', task: '🎯', recipe: '🍳', book: '📚' }
const FEED_CHIPS = { blog: 1, diary: 5, photo: 2, video: 2, wish: 3, task: 5, recipe: 1, book: 3 }
export const feedIcon = (type) => FEED_ICONS[type] || '✨'
export const feedChip = (type) => FEED_CHIPS[type] || 1

// 动态封面:博客/视频/菜谱/图书直接带 coverImage;日记是 images(数组或 JSON 文本),照片是 urls 前几张
const firstImage = (value) => {
  if (!value) return ''
  if (Array.isArray(value)) return value[0] || ''
  if (typeof value !== 'string') return ''
  const raw = value.trim()
  if (!raw) return ''
  if (raw.startsWith('[') || raw.startsWith('{')) {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) return parsed[0] || ''
      return parsed?.url || ''
    } catch (e) {
      return ''
    }
  }
  return raw
}
export const feedCover = (f) => f?.coverImage || firstImage(f?.images) || firstImage(f?.urls) || ''
