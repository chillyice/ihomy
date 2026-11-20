import { describe, it, expect, vi } from 'vitest'
import { effectScope } from 'vue'
import { useCardRoomPlay } from './useCardRoomPlay'

// 牌桌房间层:座位推导、公共广播/私牌下发、托管与超时交电脑、观战态。
// 这四个玩法共用这一层,所以口径守在这里;页面只提供 5 个回调。
function makePlay({ active = false, myId = 1, hostId = 1, members = [] } = {}) {
  return {
    active,
    myId,
    isHost: String(myId) === String(hostId),
    room: active ? { id: 7, hostId, members } : null,
    members,
    send: vi.fn(() => true),
  }
}

function mountRoom(play, hands = {}) {
  const scope = effectScope()
  const opts = {
    seatCount: 3,
    prefix: 'dz',
    getHand: (seat) => hands[seat] || [],
    snapshot: () => ({ turn: 0 }),
    applyPublic: vi.fn(),
    handPayload: (seat) => ({ tiles: hands[seat] || [] }),
    applyHand: vi.fn(),
  }
  const room = scope.run(() => useCardRoomPlay(play, opts))
  return { room, opts, stop: () => scope.stop() }
}

describe('useCardRoomPlay', () => {
  it('单机局也能建出座位表,自己是 0 号位(页面不必为没房间另写分支)', () => {
    const play = makePlay({ active: false })
    const { room, stop } = mountRoom(play)
    expect(room.mySeat).toBe(0)
    expect(room.seated).toBe(true)
    expect(room.watching).toBe(false)
    expect(room.authority).toBe(true)
    expect(room.counts).toEqual([0, 0, 0])
    stop()
  })

  it('房间局:房主排 0 号位,空位补电脑;观测不到广播时张数为 0', () => {
    const play = makePlay({
      active: true,
      myId: 3,
      hostId: 1,
      members: [
        { userId: 3, role: 'PLAYER', name: '小B' },
        { userId: 1, role: 'HOST', name: '房主' },
        { userId: 9, role: 'SPECTATOR', name: '看客' },
      ],
    })
    const { room, stop } = mountRoom(play)
    expect(room.seats.map((s) => s.userId)).toEqual([1, 3, null])
    expect(room.seats[2].ai).toBe(true)
    expect(room.mySeat).toBe(1)
    expect(room.isGuest).toBe(true)
    expect(room.authority).toBe(false)
    expect(room.watching).toBe(false)
    stop()
  })

  it('自己不在席位里(观战/串房):watching 为真,页面据此隐藏操作区', () => {
    const play = makePlay({
      active: true,
      myId: 42,
      hostId: 1,
      members: [{ userId: 1, role: 'HOST', name: '房主' }],
    })
    const { room, stop } = mountRoom(play)
    expect(room.mySeat).toBe(-1)
    expect(room.seated).toBe(false)
    expect(room.watching).toBe(true)
    stop()
  })

  it('房主广播:公共盘面只带张数,手牌单独定向发给真人席位,内容没变不重复推', () => {
    const play = makePlay({
      active: true,
      myId: 1,
      hostId: 1,
      members: [
        { userId: 1, role: 'HOST', name: '房主' },
        { userId: 3, role: 'PLAYER', name: '小B' },
      ],
    })
    const { room, stop } = mountRoom(play, { 0: [{ id: 'a' }, { id: 'b' }], 1: [{ id: 'c' }] })
    room.publish()
    expect(room.counts).toEqual([2, 1, 0])
    const events = play.send.mock.calls.map((c) => c[0])
    expect(events).toEqual(['dz:public', 'dz:hand'])
    const hand = play.send.mock.calls[1][1]
    expect(hand).toMatchObject({ to: 3, seat: 1, tiles: [{ id: 'c' }] })
    // 同一份状态再推一次:去重,不再发包
    room.publish()
    expect(play.send).toHaveBeenCalledTimes(2)
    // force(重连补发)必须真发
    room.publish({ force: true })
    expect(play.send).toHaveBeenCalledTimes(4)
    stop()
  })

  it('访客出手走信令发给房主;本机不是裁判时 local 返回发送结果', () => {
    const play = makePlay({
      active: true,
      myId: 3,
      hostId: 1,
      members: [
        { userId: 1, role: 'HOST', name: '房主' },
        { userId: 3, role: 'PLAYER', name: '小B' },
      ],
    })
    const { room, stop } = mountRoom(play)
    expect(room.local({ type: 'pass' })).toBe(true)
    expect(play.send).toHaveBeenCalledWith('dz:act', { to: 1, seat: 1, action: { type: 'pass' } })
    stop()
  })

  it('等到超时返回 null(交给电脑),有人真出手返回其动作', async () => {
    const play = makePlay({ active: false })
    const { room, stop } = mountRoom(play)
    expect(await room.waitAct(0, 10)).toBeNull()
    const p = room.waitAct(0, 1000)
    expect(room.local({ type: 'play', ids: ['x'] })).toBe(true)
    expect(await p).toEqual({ type: 'play', ids: ['x'] })
    // 没有等待者时 local 不再叫醒任何人
    expect(room.local({ type: 'pass' })).toBe(false)
    stop()
  })
})
