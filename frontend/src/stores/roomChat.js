// 「一起玩」房间临时聊天(只存内存):消息按 roomId 分桶、未读数、头像气泡。
// 消息由服务端发号(seq)后连发送者一起广播,各端都从服务器回声渲染,不做本地乐观插入;
// 进房/刷新/断线重连靠 chat:sync 补最近 50 条。房间结束或服务重启即失,符合「临时聊天室」口径。
import { defineStore } from 'pinia'
import { watch } from 'vue'
import { SOCKET_STATUS } from '@/composables/useChatSocket'
import { useChatStore } from './chat'
import { useUserStore } from './user'

/** 本端最多保留的条数(服务端只回 50,给重连补齐留余量) */
const MAX_KEEP = 100
/** 头像气泡停留时长 */
const BUBBLE_MS = 6000
/** 单条消息上限,与服务端 RoomChatBuffer.MAX_TEXT_LEN 一致 */
export const ROOM_CHAT_MAX_LEN = 200

let unsubscribe = null
let statusWatch = null
let refs = 0
const bubbleTimers = new Map()
const syncTimers = new Set()

const bubbleKey = (roomId, userId) => `${roomId}:${userId}`

export const useRoomChatStore = defineStore('roomChat', {
  state: () => ({
    /** roomId -> msg[] */
    buckets: {},
    /** roomId -> 未读条数 */
    unread: {},
    /** `${roomId}:${userId}` -> { text, at } */
    bubbles: {},
    /** roomId -> 面板是否展开(展开即已读,不再累计未读) */
    opened: {},
  }),

  getters: {
    messagesOf: (s) => (roomId) => s.buckets[String(roomId)] || [],
    unreadOf: (s) => (roomId) => s.unread[String(roomId)] || 0,
    bubbleOf: (s) => (roomId, userId) => s.bubbles[bubbleKey(roomId, userId)]?.text || '',
  },

  actions: {
    /** 进房挂载:引用计数 + 订阅 game-signal + 补拉最近消息 */
    attach(roomId) {
      const id = Number(roomId)
      if (!id) return
      refs += 1
      this.pruneOtherRooms(id)
      const chatStore = useChatStore()
      if (!unsubscribe) {
        unsubscribe = chatStore.onGameSignal((data) => this.onSignal(data))
      }
      if (!statusWatch) {
        // 断线期间的消息不会补推:连接恢复就对已挂载的房间各补拉一次
        statusWatch = watch(
          () => chatStore.socketStatus,
          (s) => {
            if (s === SOCKET_STATUS.OPEN) this.sync(id)
          },
        )
      }
      chatStore.ensureConnected()
      this.sync(id)
    },

    detach(roomId) {
      const id = Number(roomId)
      refs = Math.max(0, refs - 1)
      if (refs > 0) return
      unsubscribe?.()
      unsubscribe = null
      statusWatch?.()
      statusWatch = null
      syncTimers.forEach((t) => clearTimeout(t))
      syncTimers.clear()
      const roomIdStr = String(id)
      bubbleTimers.forEach((timer, key) => {
        if (key.startsWith(`${roomIdStr}:`)) {
          clearTimeout(timer)
          bubbleTimers.delete(key)
        }
      })
    },

    /** 请求服务端回放最近消息;WS 刚建连时 send 可能失败,重试几秒等连接就绪 */
    sync(roomId) {
      const id = Number(roomId)
      if (!id) return
      const chatStore = useChatStore()
      let tries = 0
      const attempt = () => {
        if (chatStore.sendSignal({ type: 'game-signal', data: { roomId: id, event: 'chat:sync' } })) return
        if (++tries > 20) return
        const timer = setTimeout(() => {
          syncTimers.delete(timer)
          attempt()
        }, 500)
        syncTimers.add(timer)
      }
      attempt()
    },

    onSignal(data) {
      if (!data || !data.roomId) return
      const roomId = String(data.roomId)
      if (data.event === 'chat:init') {
        // 回放以服务端为准:并集去重后按 seq 排序,避免本地多出来的旧消息被冲掉
        const incoming = Array.isArray(data.messages) ? data.messages : []
        const merged = new Map()
        for (const m of [...(this.buckets[roomId] || []), ...incoming]) merged.set(m.seq, m)
        this.buckets[roomId] = [...merged.values()].sort((a, b) => a.seq - b.seq).slice(-MAX_KEEP)
        return
      }
      if (data.event !== 'chat' || !data.msg) return
      this.push(roomId, data.msg)
    },

    push(roomId, msg) {
      const id = String(roomId)
      const list = this.buckets[id] || (this.buckets[id] = [])
      if (list.some((m) => m.seq === msg.seq)) return
      list.push(msg)
      if (list.length > MAX_KEEP) list.splice(0, list.length - MAX_KEEP)
      this.showBubble(id, msg)
      const myId = useUserStore().userInfo?.id
      if (!this.opened[id] && msg.from !== myId) {
        this.unread[id] = (this.unread[id] || 0) + 1
      }
    },

    /** 头像气泡:6s 后自动消失,同一人连发以最新一条为准并重置计时 */
    showBubble(roomId, msg) {
      const key = bubbleKey(roomId, msg.from)
      this.bubbles[key] = { text: msg.text, at: msg.at }
      clearTimeout(bubbleTimers.get(key))
      bubbleTimers.set(
        key,
        setTimeout(() => {
          bubbleTimers.delete(key)
          delete this.bubbles[key]
        }, BUBBLE_MS),
      )
    },

    setOpen(roomId, open) {
      const id = String(roomId)
      this.opened[id] = !!open
      if (open) this.unread[id] = 0
    },

    markRead(roomId) {
      this.unread[String(roomId)] = 0
    },

    /** 上行一条消息(服务端发号后经 game-signal 回声回来才进桶) */
    send(roomId, text) {
      const body = String(text || '')
        .replace(/\s+/g, ' ')
        .trim()
      if (!body || body.length > ROOM_CHAT_MAX_LEN) return false
      const chatStore = useChatStore()
      chatStore.ensureConnected()
      return chatStore.sendSignal({
        type: 'game-signal',
        data: { roomId: Number(roomId), event: 'chat', text: body },
      })
    },

    /** 只留当前房间的桶:换房/登出后不残留别的房间消息 */
    pruneOtherRooms(roomId) {
      const keep = String(roomId)
      for (const key of Object.keys(this.buckets)) if (key !== keep) delete this.buckets[key]
      for (const key of Object.keys(this.unread)) if (key !== keep) delete this.unread[key]
      for (const key of Object.keys(this.opened)) if (key !== keep) delete this.opened[key]
    },

    reset() {
      bubbleTimers.forEach((timer) => clearTimeout(timer))
      bubbleTimers.clear()
      syncTimers.forEach((t) => clearTimeout(t))
      syncTimers.clear()
      this.buckets = {}
      this.unread = {}
      this.bubbles = {}
      this.opened = {}
    },
  },
})
