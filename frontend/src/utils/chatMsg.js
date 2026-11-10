// 聊天消息类型与富内容负载:与后端 common/ChatMsgType、ChatService.validateRichPayload 同一口径。
// 负载一律走结构化对象渲染(禁 v-html);解析失败由 renderFallback 降级为文本占位。
export const MSG_TYPE = {
  TEXT: 'TEXT',
  IMAGE: 'IMAGE',
  LINK: 'LINK',
  CARD: 'CARD',
  GAME_INVITE: 'GAME_INVITE',
  CALL: 'CALL',
}

/** 站内对象卡可引用的对象类型 */
export const CARD_OBJ_TYPES = ['ALBUM', 'PHOTO', 'VIDEO', 'BLOG', 'DIARY', 'BOOK', 'WISH', 'MATCH']

/** 八个 H5 小游戏:code 与后端 GameConst.H5_GAME_CODES 一致,route 为邀请卡跳转页 */
export const H5_GAMES = [
  { code: 'game_2048', nameKey: 'games.g2048.title', route: '/games/2048' },
  { code: 'game_sudoku', nameKey: 'games.sudoku.title', route: '/games/sudoku' },
  { code: 'game_minesweeper', nameKey: 'games.minesweeper.title', route: '/games/minesweeper' },
  { code: 'game_petlink', nameKey: 'games.petlink.title', route: '/games/petlink' },
  { code: 'game_tetris', nameKey: 'games.tetris.title', route: '/games/tetris' },
  { code: 'game_snake', nameKey: 'games.snake.title', route: '/games/snake' },
  { code: 'game_solitaire', nameKey: 'games.solitaire.title', route: '/games/cards/klondike' },
  { code: 'game_spider', nameKey: 'games.spider.title', route: '/games/cards/spider' },
]

export const H5_GAME_CODES = new Set(H5_GAMES.map((g) => g.code))

export const gameByCode = (code) => H5_GAMES.find((g) => g.code === code) || null

/** 负载长度上限(与后端 PAYLOAD_MAX_BYTES 同口径) */
export const PAYLOAD_MAX_LENGTH = 4096

/**
 * 解析消息负载:非 JSON / 非对象一律返回 null(渲染层降级),绝不抛错打断页面。
 */
export function parsePayload(raw) {
  if (raw == null || raw === '') return null
  try {
    const v = JSON.parse(raw)
    if (!v || typeof v !== 'object' || Array.isArray(v)) return null
    return v
  } catch {
    return null
  }
}

/** 站内地址(以 / 开头且非 //)→ 发 CARD,否则发 LINK */
export function isSiteInternal(url) {
  return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//')
}

/** 文本截断(选择器标题快照等展示用) */
export function clipText(v, max) {
  if (typeof v !== 'string') return ''
  const s = v.replace(/\s+/g, ' ').trim()
  return s.length > max ? `${s.slice(0, max)}…` : s
}

/** 限长截断 */
const clip = (v, max) => (typeof v === 'string' ? v.slice(0, max) : v)

/**
 * 构造各类型 payload(发送侧校验,非法返回 null 由调用方拦下):
 * IMAGE  {url 必填 /files/ 开头, width?, height?}
 * LINK   {title?, description?, image?, siteName?}(全空返回 null = 纯链接)
 * CARD   {objType, objId, url / 开头, sub?, cover?}
 * GAME_INVITE {gameCode 必须属于 H5 小游戏全集}
 */
export function buildImagePayload(url, width, height) {
  if (!url || !url.startsWith('/files/')) return null
  const p = { url }
  if (Number.isFinite(width) && width > 0) p.width = Math.round(width)
  if (Number.isFinite(height) && height > 0) p.height = Math.round(height)
  return p
}

export function buildLinkPayload(preview) {
  if (!preview || typeof preview !== 'object') return null
  const p = {}
  if (preview.title) p.title = clip(preview.title, 200)
  if (preview.description) p.description = clip(preview.description, 500)
  if (preview.image) p.image = clip(preview.image, 1000)
  if (preview.siteName) p.siteName = clip(preview.siteName, 100)
  return Object.keys(p).length ? p : null
}

export function buildCardPayload({ objType, objId, url, sub, cover }) {
  if (!CARD_OBJ_TYPES.includes(objType)) return null
  // objId 允许数字或短字符串(放映厅条目是 Jellyfin 十六进制 id),与后端口径一致
  const idOk =
    (Number.isFinite(Number(objId)) && objId !== '' && objId != null) ||
    (typeof objId === 'string' && objId.trim() !== '' && objId.length <= 64)
  if (!idOk) return null
  if (!url || !url.startsWith('/') || url.length > 200) return null
  const p = { objType, objId: typeof objId === 'number' ? objId : String(objId).trim(), url }
  if (sub) p.sub = clip(sub, 200)
  if (cover) p.cover = clip(cover, 1000)
  return p
}

export function buildGameInvitePayload(gameCode) {
  if (!H5_GAME_CODES.has(gameCode)) return null
  return { gameCode }
}

/**
 * 历史消息与本地缓存合并:按 id 去重、升序排列,只留最近 keep 条。
 * 拉历史期间 WS 可能已把新消息推进缓存,直接整体覆盖会把这些消息吞掉;
 * 历史接口也可能只回最近一页,更早的缓存要保留在前面。
 */
export function mergeMessages(cached, incoming, keep = 200) {
  const byId = new Map()
  for (const m of cached || []) {
    if (m && m.id != null) byId.set(m.id, m)
  }
  for (const m of incoming || []) {
    if (m && m.id != null) byId.set(m.id, m)
  }
  const list = [...byId.values()].sort((a, b) => Number(a.id) - Number(b.id))
  return list.length > keep ? list.slice(list.length - keep) : list
}

/** 序列化为待发送的负载字符串:超限或异常返回 null */
export function serializePayload(obj) {
  if (!obj) return null
  try {
    const s = JSON.stringify(obj)
    if (s.length > PAYLOAD_MAX_LENGTH) return null
    return s
  } catch {
    return null
  }
}
