import { describe, expect, it } from 'vitest'
import { canSwitchRole, canTakePlayerSeat, roomPlayerCount } from './gameRoom'

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
