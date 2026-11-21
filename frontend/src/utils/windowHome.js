import { feedPath } from './feed'
import { shareId } from './shareId'

const feedModules = {
  blog: 'blog',
  diary: 'diary',
  photo: 'album',
  recipe: 'kitchen',
  video: 'cinema',
  book: 'library',
  task: 'task',
  wish: 'wish',
}
export function homeFeedModule(entry) {
  return feedModules[entry.type]
}

export function storyImage(row) {
  const image = row.coverImage || row.urls?.[0] || row.poster
  if (image || !row.images) return image || ''
  try {
    const images = typeof row.images === 'string' ? JSON.parse(row.images) : row.images
    return Array.isArray(images) && typeof images[0] === 'string' ? images[0] : ''
  } catch {
    return ''
  }
}

export function storyPath(entry, photo = null) {
  if (entry.type === 'photo' && photo?.albumId) return '/album/' + photo.albumId + (photo.id == null ? '' : '?p=' + shareId(photo.id))
  if (entry.type === 'diary' && entry.authorId && entry.id) return '/diary/book/' + entry.authorId + '?entry=' + entry.id
  if (entry.type === 'video' && entry.id) return '/cinema/' + entry.id
  return feedPath(entry)
}

export function dayEventPath(event, date) {
  if (typeof event.link === 'string' && /^\/(?!\/)/.test(event.link)) return event.link
  const kind = { TASK: 'REMINDER', PLAN_TASK: 'PLAN' }[event.kind] || event.kind
  const id = event.kind === 'PLAN_TASK' ? event.planId : event.id
  const path = '/plan?date=' + encodeURIComponent(date)
  return ['ANNIVERSARY', 'PLAN', 'REMINDER'].includes(kind) && /^\d+$/.test(String(id)) ? path + '&kind=' + kind + '&id=' + id : path
}

export function remainingDayEvents(items, now) {
  const clock = String(now.getHours()).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0')
  return (items || [])
    .filter((it) => !it.done && (!it.time || String(it.time).slice(0, 5) >= clock))
    .sort((a, b) => (a.time || '99').localeCompare(b.time || '99'))
}
