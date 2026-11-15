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
 * 可开房游戏:modes 为允许玩法,result 为结算口径,maxSeats 为单局玩家席位数
 * (房间人数超出时按进房顺序分派为观战;与后端 GameConst.ROOM_GAMES 同口径),
 * ai 为是否有电脑玩家(空参战席补电脑、1 名真人可开局),
 * path 为游戏页路由(纸牌是二级路由,房间页按此直达牌桌)。
 */
export const ROOM_GAMES = {
  game_petlink: {
    code: 'game_petlink',
    nameKey: 'games.petlink.title',
    path: '/games/petlink',
    modes: [ROOM_MODE.PK, ROOM_MODE.COOP],
    result: ROOM_RESULT.FIRST,
    maxSeats: 6,
    ai: false,
  },
  game_minesweeper: {
    code: 'game_minesweeper',
    nameKey: 'games.minesweeper.title',
    path: '/games/minesweeper',
    modes: [ROOM_MODE.COOP],
    result: ROOM_RESULT.COOP,
    maxSeats: 4,
    ai: false,
  },
  game_sudoku: {
    code: 'game_sudoku',
    nameKey: 'games.sudoku.title',
    path: '/games/sudoku',
    modes: [ROOM_MODE.COOP],
    result: ROOM_RESULT.COOP,
    maxSeats: 4,
    ai: false,
  },
  game_2048: {
    code: 'game_2048',
    nameKey: 'games.g2048.title',
    path: '/games/2048',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
    maxSeats: 6,
    ai: false,
  },
  game_snake: {
    code: 'game_snake',
    nameKey: 'games.snake.title',
    path: '/games/snake',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
    maxSeats: 6,
    ai: false,
  },
  game_tetris: {
    code: 'game_tetris',
    nameKey: 'games.tetris.title',
    path: '/games/tetris',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
    maxSeats: 6,
    ai: false,
  },
  game_solitaire: {
    code: 'game_solitaire',
    nameKey: 'games.solitaire.title',
    path: '/games/cards/klondike',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
    maxSeats: 4,
    ai: false,
  },
  game_spider: {
    code: 'game_spider',
    nameKey: 'games.spider.title',
    path: '/games/cards/spider',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
    maxSeats: 4,
    ai: false,
  },
  game_freecell: {
    code: 'game_freecell',
    nameKey: 'games.freecell.title',
    path: '/games/cards/freecell',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
    maxSeats: 4,
    ai: false,
  },
  game_mahjong: {
    code: 'game_mahjong',
    nameKey: 'games.mahjong.title',
    path: '/games/mahjong',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.SCORE,
    maxSeats: 4,
    ai: true,
  },
  game_ludo: {
    code: 'game_ludo',
    nameKey: 'games.ludo.title',
    path: '/games/ludo',
    modes: [ROOM_MODE.PK],
    result: ROOM_RESULT.FIRST,
    maxSeats: 4,
    ai: true,
  },
}

export const roomGame = (code) => ROOM_GAMES[code] || null

export const roomModes = (code) => roomGame(code)?.modes || []

/** 单局玩家席位数(超出人数按进房顺序转观战) */
export const roomMaxSeats = (code) => roomGame(code)?.maxSeats || 8

/** 该游戏是否有电脑玩家(空参战席补电脑、1 名真人可开局) */
export const roomHasAi = (code) => !!roomGame(code)?.ai

/** 直进房默认游戏:上次玩的游戏,没有玩过用连连看(与「点击一起玩直接进房」配套) */
export const DEFAULT_ROOM_GAME = 'game_petlink'
export const LAST_ROOM_GAME_KEY = 'ihomy:games:lastRoomGame'
export const lastRoomGame = () => {
  try {
    const code = localStorage.getItem(LAST_ROOM_GAME_KEY)
    return ROOM_GAMES[code] ? code : DEFAULT_ROOM_GAME
  } catch {
    return DEFAULT_ROOM_GAME
  }
}
export const rememberRoomGame = (code) => {
  if (!ROOM_GAMES[code]) return
  try {
    localStorage.setItem(LAST_ROOM_GAME_KEY, code)
  } catch {}
}

/**
 * 房间席位视图:玩家 → 空参战席(有 AI 的游戏由电脑补位)→ 观战 → 空观战席。
 * 席位数跟游戏走(maxSeatPlayers),房间容量(maxPlayers)只限制总人数,
 * 空出的容量都是观战席。空参战席数量夹在 [0, 空位总数] 内,不为负也不虚标。
 */
export function seatLayout(room) {
  const members = room?.members || []
  const players = members.filter((m) => !isSpectatorRole(m.role))
  const spectators = members.filter((m) => isSpectatorRole(m.role))
  const maxSeats = room?.maxSeatPlayers || roomMaxSeats(room?.gameCode)
  const capacity = Math.min(Number(room?.maxPlayers) || 8, 8)
  const emptyTotal = Math.max(0, capacity - members.length)
  const emptyPlayerSeats = Math.max(0, Math.min(maxSeats - players.length, emptyTotal))
  const emptySpectatorSeats = Math.max(0, emptyTotal - emptyPlayerSeats)
  return { players, spectators, emptyPlayerSeats, emptySpectatorSeats, maxSeats, capacity }
}

/** 成员席位角色(与后端 GameConst.ROOM_ROLE_* 同口径) */
export const ROOM_ROLE = {
  HOST: 'HOST',
  PLAYER: 'PLAYER',
  SPECTATOR: 'SPECTATOR',
}

export const isSpectatorRole = (role) => role === ROOM_ROLE.SPECTATOR

/** 房间参战人数(HOST/PLAYER,观战不算;与后端 views.playersCount 同口径) */
export const roomPlayerCount = (room) => (room?.members || []).filter((m) => !isSpectatorRole(m.role)).length

/**
 * 参战席是否还有空位:满员时观战者不能直切参战,只能申请与参战玩家换位。
 * 与后端 switchRole 同口径:非观战成员数 < 游戏席位数(maxSeatPlayers 缺失时按 gameCode 兜底)。
 */
export const canTakePlayerSeat = (room) => !!room && roomPlayerCount(room) < (room.maxSeatPlayers || roomMaxSeats(room.gameCode))

/** 自己能否自助调整席位:在房间、非房主(房主恒为玩家)、未开局/已结算(对局进行中锁定) */
export const canSwitchRole = (room, myId) =>
  !!room &&
  !!myId &&
  room.hostId !== myId &&
  (room.status === ROOM_STATUS.WAITING || room.status === ROOM_STATUS.FINISHED) &&
  (room.members || []).some((m) => m.userId === myId)

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
