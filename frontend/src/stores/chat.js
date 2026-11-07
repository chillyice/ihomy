// 聊天室 store:全局单条 WS 连接的生命周期(连接/换 token 重连/登出清场)、
// 最近消息缓存、未读数与已读推进。/chat 页与全局浮窗(P2)共用,socket 实例
// 放模块级单例(不进 state,避免被响应式代理)。
import { defineStore } from 'pinia'
import { watch } from 'vue'
import { chatApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { createChatSocket, SOCKET_STATUS } from '@/composables/useChatSocket'
import { serializePayload } from '@/utils/chatMsg'
import { useCallStore } from '@/stores/call'

let socket = null
let wired = false
/** game-signal 页面级订阅(SnakeMatch 对战页);socket 重建后由新连接重新分发 */
let gameSignalListeners = []

/** 本地保留的消息条数上限(更早的靠 /chat/history 翻页) */
const KEEP_MESSAGES = 200

export const useChatStore = defineStore('chat', {
  state: () => ({
    socketStatus: SOCKET_STATUS.IDLE,
    messages: [],
    /** 全局未读(非本人且不在聊天界面时累加;进界面清零)——P2 浮窗角标/AppSidebar 用 */
    unread: 0,
    /** 最近一条 WS 到达的消息(驱动页面滚动/浮标,历史加载不触发) */
    lastIncoming: null,
    /** /chat 页是否在前台(与 floatOpen 任意一个为真即「在看」,新消息直接推进已读) */
    chatPageActive: false,
    /** 全局浮窗是否展开(ChatFloat 维护) */
    floatOpen: false,
    /** 本次登录会话是否已拉过历史 */
    hydrated: false,
    /** 待发送的富内容卡片(分享注入/站内选择/游戏邀请的预填草稿,{msgType,content,payload} 已是可发送形态) */
    shareCard: null,
  }),
  getters: {
    /** 聊天界面是否在前台(/chat 页或浮窗展开) */
    viewing: (s) => s.chatPageActive || s.floatOpen,
  },
  actions: {
    /** 建连入口:已连接/连接中是幂等空转;登出态直接忽略 */
    ensureConnected() {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) return
      if (!socket) {
        socket = this.createSocket()
        this.wire(userStore)
      }
      socket.connect()
    },

    createSocket() {
      const s = createChatSocket({ url: () => this.buildUrl() })
      s.on('open', () => {
        this.socketStatus = SOCKET_STATUS.OPEN
      })
      s.on('close', () => {
        if (socket === s) this.socketStatus = SOCKET_STATUS.CLOSED
      })
      s.on('message', (data) => this.onIncoming(data))
      s.on('call-signal', (data) => useCallStore().onSignal(data))
      s.on('game-signal', (data) => {
        for (const fn of gameSignalListeners) fn(data)
      })
      return s
    },

    buildUrl() {
      const userStore = useUserStore()
      const proto = location.protocol === 'https:' ? 'wss' : 'ws'
      // @vite 开发代理不转发 WS,直连 8080 端口(与原 Chat.vue 直连口径一致)
      const base = import.meta.env.DEV ? 'ws://localhost:8080' : `${proto}://${location.host}`
      return `${base}/api/ws/chat?token=${encodeURIComponent(userStore.token)}`
    },

    /** 一次性接线:登出清场 + token 换代重连(WS 房间绑定握手时 familyId 快照,切换家庭必须重建) */
    wire(userStore) {
      if (wired) return
      wired = true
      watch(
        () => userStore.isLoggedIn,
        (loggedIn) => {
          if (!loggedIn) this.reset()
        },
      )
      let rebuildTimer = null
      watch(
        () => userStore.token,
        (token, old) => {
          if (!userStore.isLoggedIn || !old) return
          // 续期/切换家庭都会换 token;轻防抖避免连发重建
          clearTimeout(rebuildTimer)
          rebuildTimer = setTimeout(() => {
            if (!userStore.isLoggedIn || !socket) return
            socket.disconnect()
            socket = this.createSocket()
            socket.connect()
          }, 1000)
        },
      )
    },

    /** WS 到达一条消息:进缓存、驱动 lastIncoming、按界面状态推进已读/未读 */
    onIncoming(data) {
      if (!data || data.id == null) return
      const userStore = useUserStore()
      const mine = data.senderId === userStore.userInfo?.id
      this.messages.push(data)
      if (this.messages.length > KEEP_MESSAGES) {
        this.messages.splice(0, this.messages.length - KEEP_MESSAGES)
      }
      this.lastIncoming = data
      if (mine || this.viewing) {
        // 页面在看的消息直接推进已读游标(与原 Chat.vue 口径一致)
        chatApi.read(data.id).catch(() => {})
      } else {
        this.unread += 1
      }
    },

    /** 发送文本消息(≤2000 字);连接不可用时返回 false 由调用方提示 */
    sendText(text) {
      const t = (text || '').trim()
      if (!t || t.length > 2000 || !socket) return false
      return socket.send({ type: 'message', data: { content: t } })
    },

    /**
     * 发送富内容消息({msgType, content, payload});payload 传结构化对象,这里统一序列化。
     * 返回 false 表示未发出(连接断开或负载非法),由调用方保留草稿提示重试。
     */
    sendContent({ msgType, content, payload }) {
      if (!socket) return false
      const data = { content: content == null ? '' : String(content), msgType: msgType || 'TEXT' }
      if (payload) {
        const s = serializePayload(payload)
        if (!s) return false
        data.payload = s
      }
      return socket.send({ type: 'message', data })
    },

    /**
     * 分享注入点统一入口:各页面(照片/视频/日记/图书/放映厅)把要分享的对象卡放进草稿。
     * P1(浮窗未上线):置草稿 + 提示去聊天室发送;/chat 进页自动预填。P2 起浮窗直接展开。
     * card 形如 {objType, objId, title, sub, url, cover} 或 {msgType:'GAME_INVITE', ...}。
     */
    share(card) {
      if (!card) return
      this.shareCard = card
    },

    /** 聊天界面取走草稿(预填后清空,避免下次进页又冒出来) */
    consumeShareCard() {
      const c = this.shareCard
      this.shareCard = null
      return c
    },

    /** 进入聊天界面拉初始数据:未读游标 + 最新一页历史 */
    async loadHistory() {
      const [un, msgs] = await Promise.all([chatApi.unread(), chatApi.history()])
      this.messages = msgs || []
      this.hydrated = true
      this.unread = 0
      if (un?.lastMsgId) chatApi.read(un.lastMsgId).catch(() => {})
    },

    /** 通话/对战等协议信令上行(结构化 {type,data},由调用方组好) */
    sendSignal(data) {
      return socket ? socket.send(data) : false
    },

    /** 订阅 game-signal 下行(对战页),返回解绑函数 */
    onGameSignal(fn) {
      gameSignalListeners.push(fn)
      return () => {
        gameSignalListeners = gameSignalListeners.filter((f) => f !== fn)
      }
    },

    /** 登出/清场:断开连接并清空全部会话数据(防串号) */
    reset() {
      if (socket) {
        socket.disconnect()
        socket = null
      }
      this.socketStatus = SOCKET_STATUS.IDLE
      this.messages = []
      this.unread = 0
      this.lastIncoming = null
      this.chatPageActive = false
      this.floatOpen = false
      this.hydrated = false
      gameSignalListeners = []
    },
  },
})
