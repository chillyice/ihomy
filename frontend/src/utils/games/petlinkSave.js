// 宠物连连看进度存档:只做结构校验与序列化,localStorage 读写仍留在页面。
// 棋盘 = 140 格(10×14),每格 null 或 { id, type };同一宠物必须成对,type 上限随关卡变化。
// 坏档一律返回 null(当没存档处理),不阻断开新局。

export const PETLINK_STORAGE_KEY = 'ihomy:games:petlink:v1'
export const PETLINK_ROWS = 10
export const PETLINK_COLS = 14
export const PETLINK_CELLS = PETLINK_ROWS * PETLINK_COLS
export const PETLINK_MAX_LEVEL = 9
export const PETLINK_LEVEL_TIME = 150

/** 每关用到的宠物种类数:第 1 关 30、第 2 关 35、第 3 关起 40 */
export const typeCountOf = (level) => (level === 1 ? 30 : level === 2 ? 35 : 40)

/** 收成存档对象(棋盘逐格浅拷贝,避免后续原位移动改动已写出的对象) */
export function serializePetLinkState({ board, score, level, shufflesLeft, timeLeft, paused }) {
  return {
    board: board.map((tile) => (tile ? { id: tile.id, type: tile.type } : null)),
    score,
    level,
    shufflesLeft,
    timeLeft,
    paused: !!paused,
  }
}

/** 校验并归一化存档;返回对象带 maxId,供页面恢复 tileSeq 计数 */
export function parsePetLinkState(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  const { board, score, level, shufflesLeft, timeLeft } = raw
  if (!Array.isArray(board) || board.length !== PETLINK_CELLS) return null
  if (!Number.isInteger(level) || level < 1 || level > PETLINK_MAX_LEVEL) return null
  if (!Number.isInteger(score) || score < 0) return null
  if (!Number.isInteger(shufflesLeft) || shufflesLeft < 0) return null
  if (!Number.isInteger(timeLeft) || timeLeft <= 0 || timeLeft > PETLINK_LEVEL_TIME) return null

  const maxType = typeCountOf(level)
  const typeCounts = new Map()
  let tiles = 0
  let maxId = 0
  for (const tile of board) {
    if (tile === null) continue
    if (typeof tile !== 'object' || Array.isArray(tile)) return null
    if (!Number.isInteger(tile.id) || tile.id <= 0) return null
    if (!Number.isInteger(tile.type) || tile.type < 1 || tile.type > maxType) return null
    tiles += 1
    maxId = Math.max(maxId, tile.id)
    typeCounts.set(tile.type, (typeCounts.get(tile.type) || 0) + 1)
  }
  // 空盘(刚要过关)与奇数张都不算可续档
  if (!tiles || tiles % 2 !== 0) return null
  for (const count of typeCounts.values()) {
    if (count % 2 !== 0) return null
  }

  return {
    board: board.map((tile) => (tile ? { id: tile.id, type: tile.type } : null)),
    score,
    level,
    shufflesLeft,
    timeLeft,
    paused: !!raw.paused,
    maxId,
  }
}
