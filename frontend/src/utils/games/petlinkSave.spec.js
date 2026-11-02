import { describe, expect, it } from 'vitest'
import { PETLINK_CELLS, PETLINK_LEVEL_TIME, parsePetLinkState, serializePetLinkState, typeCountOf } from './petlinkSave'

/** 造一份合法棋盘:70 对牌,类型在 1..maxType 之间 */
const makeBoard = (level = 3) => {
  const maxType = typeCountOf(level)
  const board = []
  for (let pair = 0; pair < PETLINK_CELLS / 2; pair++) {
    const type = (pair % maxType) + 1
    board.push({ id: pair * 2 + 1, type }, { id: pair * 2 + 2, type })
  }
  return board
}

const baseState = (overrides = {}) => ({
  board: makeBoard(),
  score: 120,
  level: 3,
  shufflesLeft: 8,
  timeLeft: 90,
  paused: false,
  ...overrides,
})

describe('typeCountOf', () => {
  it('第 1 关 30 种、第 2 关 35 种、第 3 关起 40 种', () => {
    expect(typeCountOf(1)).toBe(30)
    expect(typeCountOf(2)).toBe(35)
    expect(typeCountOf(3)).toBe(40)
    expect(typeCountOf(9)).toBe(40)
  })
})

describe('serializePetLinkState', () => {
  it('只保留存档字段并把 paused 归一为布尔', () => {
    const saved = serializePetLinkState({ ...baseState(), paused: undefined, extra: 'x' })
    expect(Object.keys(saved).sort()).toEqual(['board', 'level', 'paused', 'score', 'shufflesLeft', 'timeLeft'])
    expect(saved.paused).toBe(false)
    expect(saved.extra).toBeUndefined()
  })

  it('棋盘逐格浅拷贝,后续原位移动不会改到已写出的对象', () => {
    const board = makeBoard(3)
    const saved = serializePetLinkState(baseState({ board }))
    board[0].type = 40
    expect(saved.board[0].type).not.toBe(40)
  })
})

describe('parsePetLinkState', () => {
  it('合法存档恢复成功并带出 maxId', () => {
    const parsed = parsePetLinkState(baseState())
    expect(parsed).not.toBeNull()
    expect(parsed.board).toHaveLength(PETLINK_CELLS)
    expect(parsed.maxId).toBe(PETLINK_CELLS)
    expect(parsed.score).toBe(120)
    expect(parsed.level).toBe(3)
    expect(parsed.shufflesLeft).toBe(8)
    expect(parsed.timeLeft).toBe(90)
    expect(parsed.paused).toBe(false)
  })

  it('paused 缺省按 false,非布尔真值按 true', () => {
    expect(parsePetLinkState(baseState({ paused: undefined })).paused).toBe(false)
    expect(parsePetLinkState(baseState({ paused: 1 })).paused).toBe(true)
  })

  it('棋盘长度不对直接当坏档', () => {
    expect(parsePetLinkState(baseState({ board: makeBoard().slice(1) }))).toBeNull()
  })

  it('空盘不续档(通常刚要过关)', () => {
    expect(parsePetLinkState(baseState({ board: new Array(PETLINK_CELLS).fill(null) }))).toBeNull()
  })

  it('牌数为奇数不续档', () => {
    const board = makeBoard()
    board[0] = null
    expect(parsePetLinkState(baseState({ board }))).toBeNull()
  })

  it('宠物不成对不续档', () => {
    const board = makeBoard()
    board[0] = { id: 1, type: 40 }
    expect(parsePetLinkState(baseState({ board }))).toBeNull()
  })

  it('宠物类型超过当前关卡上限不续档', () => {
    const board = makeBoard(1)
    board[0] = { id: 1, type: 31 }
    board[1] = { id: 2, type: 31 }
    expect(parsePetLinkState(baseState({ board, level: 1 }))).toBeNull()
  })

  it('时间必须落在 1~150 秒', () => {
    expect(parsePetLinkState(baseState({ timeLeft: 0 }))).toBeNull()
    expect(parsePetLinkState(baseState({ timeLeft: PETLINK_LEVEL_TIME + 1 }))).toBeNull()
    expect(parsePetLinkState(baseState({ timeLeft: 1.5 }))).toBeNull()
    expect(parsePetLinkState(baseState({ timeLeft: PETLINK_LEVEL_TIME }))).not.toBeNull()
  })

  it('关卡 / 分数 / 重排数非法都不续档', () => {
    expect(parsePetLinkState(baseState({ level: 0 }))).toBeNull()
    expect(parsePetLinkState(baseState({ level: 10 }))).toBeNull()
    expect(parsePetLinkState(baseState({ score: -1 }))).toBeNull()
    expect(parsePetLinkState(baseState({ score: 1.5 }))).toBeNull()
    expect(parsePetLinkState(baseState({ shufflesLeft: -1 }))).toBeNull()
  })

  it('牌对象缺字段/类型非法不续档', () => {
    const board = makeBoard()
    board[2] = { id: 3 }
    expect(parsePetLinkState(baseState({ board }))).toBeNull()

    const board2 = makeBoard()
    board2[2] = { id: 3, type: 0 }
    expect(parsePetLinkState(baseState({ board: board2 }))).toBeNull()
  })

  it('非对象输入不抛错', () => {
    expect(parsePetLinkState(null)).toBeNull()
    expect(parsePetLinkState([])).toBeNull()
    expect(parsePetLinkState('x')).toBeNull()
  })
})
