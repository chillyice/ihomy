// createChatSocket 重连/心跳/分发行为守门:
// 指数退避 3s 起步翻倍封顶 60s、连上归零、心跳 25s ping、按 type 分发、dispose 后不重连。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createChatSocket, SOCKET_STATUS } from './useChatSocket'

class FakeWebSocket {
  constructor(url) {
    this.url = url
    this.readyState = 0 // CONNECTING
    this.sent = []
    FakeWebSocket.instances.push(this)
  }
  send(data) {
    this.sent.push(data)
  }
  close() {
    this.readyState = 3
    if (this.onclose) this.onclose({ code: 1000 })
  }
  // 模拟建连成功
  open() {
    this.readyState = 1
    if (this.onopen) this.onopen()
  }
  // 模拟异常断开(网络波动/服务端重启)
  die() {
    this.readyState = 3
    if (this.onclose) this.onclose({ code: 1006 })
  }
  receive(pkt) {
    if (this.onmessage) this.onmessage({ data: JSON.stringify(pkt) })
  }
}

describe('createChatSocket', () => {
  beforeEach(() => {
    FakeWebSocket.instances = []
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  const make = (overrides = {}) =>
    createChatSocket({ url: 'ws://test/api/ws/chat', socketFactory: (u) => new FakeWebSocket(u), ...overrides })

  it('连接成功后状态为 open,并按 25s 间隔发送心跳 ping', () => {
    const sock = make()
    sock.connect()
    expect(sock.status.value).toBe(SOCKET_STATUS.CONNECTING)
    const ws = FakeWebSocket.instances[0]
    ws.open()
    expect(sock.status.value).toBe(SOCKET_STATUS.OPEN)
    vi.advanceTimersByTime(25000)
    expect(ws.sent).toEqual([JSON.stringify({ type: 'ping' })])
    vi.advanceTimersByTime(25000)
    expect(ws.sent.length).toBe(2)
  })

  it('断开后按指数退避重连:3s → 6s → 12s,连上归零后重又从 3s 开始', () => {
    const sock = make()
    sock.connect()
    FakeWebSocket.instances[0].die()
    expect(sock.status.value).toBe(SOCKET_STATUS.CLOSED)
    vi.advanceTimersByTime(2999)
    expect(FakeWebSocket.instances.length).toBe(1)
    vi.advanceTimersByTime(1)
    expect(FakeWebSocket.instances.length).toBe(2)
    FakeWebSocket.instances[1].die()
    vi.advanceTimersByTime(5999)
    expect(FakeWebSocket.instances.length).toBe(2)
    vi.advanceTimersByTime(1)
    expect(FakeWebSocket.instances.length).toBe(3)
    FakeWebSocket.instances[2].die()
    vi.advanceTimersByTime(11999)
    expect(FakeWebSocket.instances.length).toBe(3)
    vi.advanceTimersByTime(1)
    expect(FakeWebSocket.instances.length).toBe(4)
    // 连上后重试计数归零:再断开又从 3s 开始
    FakeWebSocket.instances[3].open()
    FakeWebSocket.instances[3].die()
    vi.advanceTimersByTime(2999)
    expect(FakeWebSocket.instances.length).toBe(4)
    vi.advanceTimersByTime(1)
    expect(FakeWebSocket.instances.length).toBe(5)
  })

  it('重连间隔封顶 60s,不再翻倍', () => {
    const sock = make()
    sock.connect()
    // 连续失败 6 次:间隔 3/6/12/24/48/60(封顶)
    const caps = [3000, 6000, 12000, 24000, 48000, 60000]
    caps.forEach((delay, i) => {
      FakeWebSocket.instances[i].die()
      vi.advanceTimersByTime(delay - 1)
      expect(FakeWebSocket.instances.length).toBe(i + 1)
      vi.advanceTimersByTime(1)
      expect(FakeWebSocket.instances.length).toBe(i + 2)
    })
    // 第 7 次仍是 60s(不出现 >60s 的间隔)
    FakeWebSocket.instances[6].die()
    vi.advanceTimersByTime(59999)
    expect(FakeWebSocket.instances.length).toBe(7)
    vi.advanceTimersByTime(1)
    expect(FakeWebSocket.instances.length).toBe(8)
  })

  it('重连时重新取 url(拿最新 token)', () => {
    let n = 0
    const sock = make({ url: () => `ws://test/api/ws/chat?t=${++n}` })
    sock.connect()
    expect(FakeWebSocket.instances[0].url).toBe('ws://test/api/ws/chat?t=1')
    FakeWebSocket.instances[0].die()
    vi.advanceTimersByTime(3000)
    expect(FakeWebSocket.instances[1].url).toBe('ws://test/api/ws/chat?t=2')
  })

  it('下行按 type 分发,非 JSON 静默忽略;未连接时 send 返回 false', () => {
    const got = []
    const sock = make()
    sock.on('message', (data) => got.push(data))
    expect(sock.send({ type: 'message', data: {} })).toBe(false)
    sock.connect()
    const ws = FakeWebSocket.instances[0]
    ws.open()
    expect(sock.send({ type: 'message', data: { content: 'hi' } })).toBe(true)
    expect(ws.sent).toEqual([JSON.stringify({ type: 'message', data: { content: 'hi' } })])
    ws.receive({ type: 'message', data: { id: 1 } })
    ws.receive({ type: 'pong', data: {} })
    ws.receive('not-json{{')
    expect(got).toEqual([{ id: 1 }])
  })

  it('disconnect 后不再重连', () => {
    const sock = make()
    sock.connect()
    sock.disconnect()
    expect(sock.status.value).toBe(SOCKET_STATUS.IDLE)
    vi.advanceTimersByTime(300000)
    expect(FakeWebSocket.instances.length).toBe(1)
    // 已断开后 connect 不再建连
    sock.connect()
    expect(FakeWebSocket.instances.length).toBe(1)
  })

  it('连接中重复 connect 幂等,不新建连接', () => {
    const sock = make()
    sock.connect()
    sock.connect()
    expect(FakeWebSocket.instances.length).toBe(1)
    FakeWebSocket.instances[0].open()
    sock.connect()
    expect(FakeWebSocket.instances.length).toBe(1)
  })
})
