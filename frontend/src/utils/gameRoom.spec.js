import { describe, expect, it } from 'vitest'
import { canSwitchRole, canTakePlayerSeat, roomHasAi, roomPlayerCount, seatLayout } from './gameRoom'

const member = (userId, role) => ({ userId, role, name: `u${userId}`, ready: false, score: null, finishedAt: null })

/** 麻将 4 席:房主 + 2 玩家 + 1 观战 */
const room = (overrides = {}) => ({
  id: 1,
  gameCode: 'game_mahjong',
  status: 'WAITING',
  hostId: 1,
  maxSeatPlayers: 4,
  members: [member(1, 'HOST'), member(2, 'PLAYER'), member(3, 'PLAYER'), member(4, 'SPECTATOR')],
  ...overrides,
})

describe('roomPlayerCount', () => {
  it('只数 HOST/PLAYER,观战不算', () => {
    expect(roomPlayerCount(room())).toBe(3)
  })

  it('空房间/缺 members 返回 0', () => {
    expect(roomPlayerCount({ members: [] })).toBe(0)
    expect(roomPlayerCount(null)).toBe(0)
  })
})

describe('canTakePlayerSeat', () => {
  it('席位未满可直切参战', () => {
    expect(canTakePlayerSeat(room())).toBe(true)
  })

  it('参战满员不能再切(观战者只能申请换位)', () => {
    const full = room({
      members: [member(1, 'HOST'), member(2, 'PLAYER'), member(3, 'PLAYER'), member(4, 'PLAYER'), member(5, 'SPECTATOR')],
    })
    expect(canTakePlayerSeat(full)).toBe(false)
  })

  it('缺 maxSeatPlayers 时按 gameCode 兜底(麻将 4 席)', () => {
    const noCap = room({ members: [member(1, 'HOST'), member(2, 'PLAYER'), member(3, 'PLAYER'), member(4, 'PLAYER')] })
    delete noCap.maxSeatPlayers
    expect(canTakePlayerSeat(noCap)).toBe(false)
  })

  it('没有房间返回 false', () => {
    expect(canTakePlayerSeat(null)).toBe(false)
  })
})

describe('canSwitchRole', () => {
  it('等待中/已结算的非房主成员可调', () => {
    expect(canSwitchRole(room(), 2)).toBe(true)
    expect(canSwitchRole(room({ status: 'FINISHED' }), 2)).toBe(true)
    expect(canSwitchRole(room({ status: 'FINISHED' }), 4)).toBe(true)
  })

  it('对局进行中/已取消锁定', () => {
    expect(canSwitchRole(room({ status: 'PLAYING' }), 2)).toBe(false)
    expect(canSwitchRole(room({ status: 'CANCELLED' }), 2)).toBe(false)
  })

  it('房主不可调(恒为玩家)', () => {
    expect(canSwitchRole(room(), 1)).toBe(false)
  })

  it('不在房间内不可调', () => {
    expect(canSwitchRole(room(), 99)).toBe(false)
    expect(canSwitchRole(null, 2)).toBe(false)
  })
})

describe('roomHasAi', () => {
  it('只有麻将/飞行棋有电脑玩家', () => {
    expect(roomHasAi('game_mahjong')).toBe(true)
    expect(roomHasAi('game_ludo')).toBe(true)
    expect(roomHasAi('game_petlink')).toBe(false)
    expect(roomHasAi('game_unknown')).toBe(false)
  })
})

describe('seatLayout', () => {
  it('席位跟游戏走:玩家之外先补空参战席,再排观战', () => {
    const layout = seatLayout(room())
    expect(layout.players).toHaveLength(3)
    expect(layout.spectators).toHaveLength(1)
    expect(layout.emptyPlayerSeats).toBe(1) // 4 席 - 3 玩家
    expect(layout.emptySpectatorSeats).toBe(3) // 容量 8 - 5 人
  })

  it('超员时空参战席不为负(麻将 4 席坐满 5 人)', () => {
    const full = room({
      members: [
        member(1, 'HOST'),
        member(2, 'PLAYER'),
        member(3, 'PLAYER'),
        member(4, 'PLAYER'),
        member(5, 'SPECTATOR'),
        member(6, 'SPECTATOR'),
      ],
    })
    const layout = seatLayout(full)
    expect(layout.emptyPlayerSeats).toBe(0)
    expect(layout.emptySpectatorSeats).toBe(2)
  })

  it('空参战席优先占空位,容量不足时观战席为 0', () => {
    const tight = room({ members: [member(1, 'HOST'), member(2, 'PLAYER')], maxPlayers: 3 })
    const layout = seatLayout(tight)
    expect(layout.emptyPlayerSeats).toBe(1)
    expect(layout.emptySpectatorSeats).toBe(0)
  })

  it('空房间/缺字段不抛错', () => {
    expect(seatLayout(null).emptyPlayerSeats).toBeGreaterThan(0)
    expect(seatLayout({ members: [] }).players).toHaveLength(0)
  })
})
