import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, reactive } from 'vue'
import { useEightBallRoom } from './useEightBallRoom'
import { createEightBallGame, shootEightBall, stepEightBall, placeEightBallCue } from '@/utils/games/eightBall'

const mocks = vi.hoisted(() => ({ transport: vi.fn(), store: vi.fn(), finish: vi.fn() }))
vi.mock('./useGameRoomPlay', () => ({ useGameRoomPlay: mocks.transport }))
vi.mock('@/stores/chat', () => ({ useChatStore: mocks.store }))
vi.mock('@/api', () => ({ roomApi: { finish: mocks.finish } }))

const members = ['HOST', 'PLAYER', 'SPECTATOR'].map((role, i) => ({ userId: i + 1, name: role, role }))
let clients
function join(id) {
  const scope = effectScope()
  let game = createEightBallGame()
  let receive
  const socket = reactive({ socketStatus: 'open' })
  mocks.store.mockReturnValue(socket)
  mocks.transport.mockImplementation(({ onSignal }) => {
    receive = onSignal
    return reactive({
      room: { id: 8, hostId: 1, roundToken: '73262717112572627' },
      roomId: 8,
      roomGameCode: 'game_eight_ball',
      members,
      myId: id,
      myMember: members[id - 1],
      isSpectator: id === 3,
      isHost: id === 1,
      status: 'PLAYING',
      playing: true,
      ended: false,
      load: vi.fn(),
      send(event, data) {
        if (socket.socketStatus !== 'open') return false
        for (const [other, client] of clients) {
          if (other !== id && (!data.to || other === data.to) && client.socket.socketStatus === 'open')
            client.receive({ ...structuredClone(data), event, from: id })
        }
        return true
      },
    })
  })
  const room = scope.run(() =>
    useEightBallRoom({
      getGame: () => game,
      applyState: (state) => {
        game = state
      },
      applyShot: (angle, power) => shootEightBall(game, angle, power),
      applyPlacement: (x, y) => placeEightBallCue(game, x, y),
    }),
  )
  const client = {
    room,
    socket,
    receive,
    dispose: () => scope.stop(),
    game: () => game,
    roll() {
      for (let i = 0; i < 1000 && game.phase === 'rolling'; i++) stepEightBall(game, 1 / 30)
      expect(game.phase).not.toBe('rolling')
      room.settled()
    },
  }
  clients.set(id, client)
  return client
}

describe('eight-ball room', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    clients = new Map()
    const saved = new Map()
    vi.stubGlobal('localStorage', { getItem: (key) => saved.get(key) || null, setItem: (key, value) => saved.set(key, value) })
  })
  afterEach(() => {
    for (const client of clients.values()) client.dispose()
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })
  it('only accepts the current seated player and replaces every replica with the settled host table', () => {
    const host = join(1),
      guest = join(2),
      watcher = join(3)
    vi.advanceTimersByTime(3100)
    expect(host.room.canAct).toBe(true)
    expect(guest.room.canAct).toBe(false)
    expect(watcher.room.watching).toBe(true)
    const bad = { event: 'eight:act', token: host.room.play.room.roundToken, revision: 0, action: { type: 'shoot', angle: 0, power: 1 } }
    host.receive({ ...bad, from: 3 })
    host.receive({ ...bad, from: 2 })
    host.receive({ ...bad, from: 1, token: 'old' })
    expect(host.game().shots).toBe(0)
    expect(host.room.shoot(1.55, 0.1)).toBe(true)
    expect(guest.game().phase).toBe('rolling')
    expect(watcher.game().phase).toBe('rolling')
    host.roll()
    expect(guest.game()).toMatchObject({ player: 1, ballInHand: true, phase: 'ready' })
    expect(guest.game().balls).toEqual(host.game().balls)
    expect(watcher.room.shoot(0, 0.5)).toBe(false)
    expect(guest.room.place(200, 150)).toBe(true)
    expect(host.game().balls[0].x).toBe(200)
    vi.advanceTimersByTime(6000)
    expect(guest.room.shoot(0, 0.8)).toBe(true)
    expect(host.game().shots).toBe(2)
    host.receive({ ...bad, from: 2 })
    expect(host.game().shots).toBe(2)
  })

  it('replays an in-flight shot after host reload and catches up a reconnecting guest', async () => {
    const first = join(1),
      guest = join(2)
    vi.advanceTimersByTime(3100)
    first.room.shoot(0.1, 0.9)
    first.dispose()
    const host = join(1)
    expect(host.game().shots).toBe(1)
    expect(host.game().phase).toBe('rolling')
    guest.socket.socketStatus = 'closed'
    host.roll()
    vi.advanceTimersByTime(12000)
    expect(host.room.ready).toBe(false)
    guest.socket.socketStatus = 'open'
    await vi.advanceTimersByTimeAsync(3100)
    expect(host.room.ready).toBe(true)
    expect(guest.game().balls).toEqual(host.game().balls)
    expect(guest.game().phase).toBe('ready')
  })
})
