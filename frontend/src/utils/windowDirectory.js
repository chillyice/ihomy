import { buildNavItems, groupNavItems } from './navModules'

export const DEFAULT_WINDOW_FAVORITES = ['music', 'item', 'assistant', 'chat']
export const WINDOW_PREF = 'window.favorites.v1'

const aliases = {
  music: ['听歌', '曲库', '歌曲', 'listen', 'songs'],
  item: ['寻物', '找东西', '找物品', 'find belongings', 'find items'],
  assistant: ['问助手', 'AI助手', '家庭助手', 'ask assistant', 'ai'],
  chat: ['聊天', '发消息', '联系人', 'messages', 'contacts'],
  album: ['照片', '相片', 'photos'],
  book: ['账本', '记一笔', '记账', 'expenses'],
  familyTree: ['家谱', '族谱', 'family tree'],
  anniversaryView: ['纪念日', '生日', 'anniversary', 'birthdays'],
}

export function windowEntries(modules, { hasOps, child, loggedIn, allows, label }) {
  const entries = []
  for (const original of buildNavItems(modules, { hasOps, child })) {
    if (!allows(original)) continue
    const item = { ...original, label: label(original.code, original.title) }
    if (item.code === 'item') item.path = '/item?focus=search'
    entries.push(item)
    if (item.code === 'chat')
      entries.push({ ...item, code: 'assistant', path: '/chat?target=assistant', label: label('assistant'), icon: 'assistant' })
    if (item.code === 'member')
      entries.push({ ...item, code: 'familyTree', path: '/member?tab=tree', label: label('familyTree'), searchOnly: true, icon: 'tree' })
    if (item.code === 'plan')
      entries.push({
        ...item,
        code: 'anniversaryView',
        path: '/plan?tab=anniversary',
        label: label('anniversaryView'),
        searchOnly: true,
        icon: 'anniversary',
      })
  }
  if (!child && loggedIn)
    entries.push({ code: 'notifications', path: '/notification', label: label('notifications'), category: 'system', icon: 'announcement' })
  if (!loggedIn) entries.push({ code: 'login', path: '/login', label: label('login'), category: 'system', icon: 'member' })
  entries.push({ code: 'appDownload', path: '/app', label: label('appDownload'), category: 'system', icon: 'tools' })
  if (loggedIn && !child) entries.push({ code: 'kiosk', path: '/kiosk', label: label('kiosk'), category: 'system', icon: 'ops' })
  return entries
}

const normalize = (s) =>
  String(s || '')
    .normalize('NFKC')
    .toLowerCase()
    .replace(/\s+/gu, '')

export function windowGroups(entries, query = '') {
  const term = normalize(query)
  return groupNavItems(
    entries.filter((item) =>
      term
        ? [
            item.label,
            item.title && !item.searchOnly && item.code !== 'assistant' ? item.title : '',
            item.code,
            ...(aliases[item.code] || []),
          ].some((word) => normalize(word).includes(term))
        : !item.searchOnly,
    ),
  )
}

export function parseWindowFavorites(value) {
  try {
    const codes = JSON.parse(value)
    if (Array.isArray(codes)) return [...new Set(codes.filter((c) => typeof c === 'string'))]
  } catch {
    // A previous or unreadable local preference falls back to the four initial shortcuts.
  }
  return [...DEFAULT_WINDOW_FAVORITES]
}
