// 房间临时聊天:分桶去重、未读口径、头像气泡过期、上行参数的清洗。
// 消息都从服务端回声进桶(带 seq),这里验证去重键与排序不会被重复回放冲乱。
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const sendSignal = vi.fn(() => true)
const ensureConnected = vi.fn()

vi.mock('@/stores/chat', () => ({
  useChatStore: () => ({ sendSignal, ensureConnected, onGameSignal: () => () => {}, socketStatus: 'open' }),
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ userInfo: { id: 7 }, isLoggedIn: true }),
}))

const { useRoomChatStore, ROOM_CHAT_MAX_LEN } = await import('./roomChat')

const msg = (seq, from, text = 'hi') => ({ seq, from, text, at: seq })

describe('stores/roomChat', () => {
  let store

  beforeEach(() => {
    vi.useFakeTimers()
    setActivePinia(createPinia())
    sendSignal.mockClear()
    ensureConnected.mockClear()
    store = useRoomChatStore()
  })

  afterEach(() => {
    store.reset()
    vi.useRealTimers()
  })

  it('按 seq 去重,只保留最后 MAX_KEEP 条', () => {
    for (let i = 1; i <= 120; i++) store.push(5, msg(i, 9))
    store.push(5, msg(120, 9)) // 服务端回声重复到达
    const list = store.messagesOf(5)
    expect(list).toHaveLength(100)
    expect(list[0].seq).toBe(21)
    expect(list.at(-1).seq).toBe(120)
  })

  it('chat:init 回放与本地合并后按 seq 排序', () => {
    store.push(5, msg(3, 9))
    store.onSignal({ roomId: 5, event: 'chat:init', messages: [msg(2, 8), msg(1, 8), msg(3, 9)] })
    expect(store.messagesOf(5).map((m) => m.seq)).toEqual([1, 2, 3])
  })

  it('别人发言累计未读,自己发言不累计;面板展开后清零', () => {
    store.push(5, msg(1, 9))
    store.push(5, msg(2, 7))
    expect(store.unreadOf(5)).toBe(1)
    store.setOpen(5, true)
    expect(store.unreadOf(5)).toBe(0)
    store.push(5, msg(3, 9))
    expect(store.unreadOf(5)).toBe(0)
  })

  it('头像气泡 6s 后自动消失,连发以最新一条为准', () => {
    store.push(5, msg(1, 9, '先说的'))
    expect(store.bubbleOf(5, 9)).toBe('先说的')
    store.push(5, msg(2, 9, '后来的'))
    expect(store.bubbleOf(5, 9)).toBe('后来的')
    vi.advanceTimersByTime(5999)
    expect(store.bubbleOf(5, 9)).toBe('后来的')
    vi.advanceTimersByTime(1)
    expect(store.bubbleOf(5, 9)).toBe('')
  })

  it('上行前折叠空白;空消息与超长消息不上行', () => {
    expect(store.send(5, '   ')).toBe(false)
    expect(store.send(5, 'x'.repeat(ROOM_CHAT_MAX_LEN + 1))).toBe(false)
    expect(sendSignal).not.toHaveBeenCalled()
    expect(store.send(5, ' 你  好 \n')).toBe(true)
    expect(sendSignal).toHaveBeenCalledWith({ type: 'game-signal', data: { roomId: 5, event: 'chat', text: '你 好' } })
  })

  it('只保留当前房间的桶', () => {
    store.push(4, msg(1, 9))
    store.push(5, msg(1, 9))
    store.pruneOtherRooms(5)
    expect(store.messagesOf(4)).toEqual([])
    expect(store.messagesOf(5)).toHaveLength(1)
  })
})
