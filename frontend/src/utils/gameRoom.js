// 「一起玩」房间口径:游戏 ↔ 玩法/结算/路由的唯一前端来源(与后端 GameConst.ROOM_GAMES 同口径)。
// PK 各端用同一 seed 独立跑,先完成/高分者胜;COOP 由房主权威广播共享盘面,任意成员操作都生效。
export const ROOM_MODE = {
  PK: 'PK',
  COOP: 'COOP',
}

export const ROOM_STATUS = {
  WAITING: 'WAITING',
  PLAYING: 'PLAYING',
  FINISHED: 'FINISHED',
  CANCELLED: 'CANCELLED',
}

export const ROOM_RESULT = {
  FIRST: 'FIRST_FINISH',
  SCORE: 'HIGH_SCORE',
  COOP: 'COOP',
}

/**
 * 可开房游戏:modes 为允许玩法,result 为结算口径,
 * path 为游戏页路由(纸牌是二级路由,房间页按此直达牌桌)。
 */
export const ROOM_GAMES = {
  game_petlink: {
    code: 'game_petlink',
    nameKey: 'games.petlink.title',
    path: '/games/petlink',
    modes: [ROOM_MODE.PK, ROOM_MODE.COOP],
    result: ROOM_RESULT.FIRST,
  },
  game_minesweeper: {
    code: 'game_minesweeper',
    nameKey: 'games.minesweeper.title',
    path: '/games/minesweeper',
    modes: [ROOM_MODE.COOP],
    result: ROOM_RESULT.COOP,
  },
  game_sudoku: {
    code: 'game_sudoku',
    nameKey: 'games.sudoku.title',
    path: '/games/sudoku',
    modes: [ROOM_MODE.COOP],
    result: ROOM_RESULT.COOP,
  },
  game_2048: { code: 'game_2048', nameKey: 'games.g2048.title', path: '/games/2048', modes: [ROOM_MODE.PK], result: ROOM_RESULT.SCORE },
  game_snake: { code: 'game_snake', nameKey: 'games.snake.title', path: '/games/snake', modes: [ROOM_MODE.PK], result: ROOM_RESULT.SCORE },
  game_tetris: {
    code: 'game_tetris',
    nameKey: 'games.tetris.title',
    path: '/games/tetris',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
  },
  game_solitaire: {
    code: 'game_solitaire',
    nameKey: 'games.solitaire.title',
    path: '/games/cards/klondike',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
  },
  game_spider: {
    code: 'game_spider',
    nameKey: 'games.spider.title',
    path: '/games/cards/spider',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
  },
  game_freecell: {
    code: 'game_freecell',
    nameKey: 'games.freecell.title',
    path: '/games/cards/freecell',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
  },
}

export const roomGame = (code) => ROOM_GAMES[code] || null

export const roomModes = (code) => roomGame(code)?.modes || []

export const defaultRoomMode = (code) => {
  const modes = roomModes(code)
  return modes.includes(ROOM_MODE.PK) ? ROOM_MODE.PK : ROOM_MODE.COOP
}

export const isCoop = (mode) => mode === ROOM_MODE.COOP

/** 结算口径文案(房间页/游戏内横幅用) */
export const resultKey = (code) => {
  const result = roomGame(code)?.result
  if (result === ROOM_RESULT.FIRST) return 'games.room.resultFirst'
  if (result === ROOM_RESULT.COOP) return 'games.room.resultCoop'
  return 'games.room.resultScore'
}

/** 游戏页地址:带上房间 id,游戏内读取 ?room= 进入房间模式 */
export const roomGamePath = (code, roomId) => {
  const spec = roomGame(code)
  if (!spec) return '/games'
  return { path: spec.path, query: { room: String(roomId) } }
}

/** 纸牌按玩法码区分路由 */
export const roomPathForGame = (code) => roomGame(code)?.path || '/games'

/** 阅读型比分:纸牌按「用步数/用时换分」统一成高分为好(与 PK 高分制一致) */
export const cardRoomScore = (moves, seconds) =>
  Math.max(1, 20000 - Math.max(0, Math.round(moves || 0)) * 20 - Math.max(0, Math.round(seconds || 0)))
