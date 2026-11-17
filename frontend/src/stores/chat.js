// 聊天室 store:全局单条 WS 连接的生命周期(连接/换 token 重连/登出清场)、
// 最近消息缓存、未读数与已读推进。/chat 页与全局浮窗(P2)共用,socket 实例
// 放模块级单例(不进 state,避免被响应式代理)。
import { defineStore } from 'pinia'
import { watch } from 'vue'
import { chatApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { createChatSocket, SOCKET_STATUS } from '@/composables/useChatSocket'
import { serializePayload, mergeMessages } from '@/utils/chatMsg'
import { useCallStore } from '@/stores/call'
import { rewriteAssetUrls } from '@/utils/assetUrl'
import { ensureFreshToken } from '@/api/request'
import { isNativeApp } from '@/utils/platform'

let socket = null
let wired = false
/** 本次登录是否已经建立过 WS(区分首连与重连:重连要补拉历史) */
let wsEverOpened = false
/** game-signal 页面级订阅(SnakeMatch 对战页);socket 重建后由新连接重新分发 */
let gameSignalListeners = []
/** 历史加载中的共享 Promise(/chat 页与浮窗同时挂载时只发一次请求) */
let historyPromise = null
/** 登出/换号时递增:在途历史请求回来后按旧世代丢弃,避免把上个账号的消息写进清空后的缓存 */
let historyGen = 0
/** 前后台监听只装一次(visibilitychange / pageshow / online / 原生 appStateChange) */
let lifecycleInstalled = false
/** 切后台的时刻:回前台按离开时长决定要不要重建连接 */
let hiddenAt = 0
/** 最近一次回前台处理时间:原生壳里 visibilitychange 与 appStateChange 会前后脚都触发 */
let lastWakeAt = 0

/** 本地保留的消息条数上限(更早的靠 /chat/history 翻页) */
const KEEP_MESSAGES = 200

export const useChatStore = defineStore('chat', {
  state: () => ({
    socketStatus: SOCKET_STATUS.IDLE,
    /** 家庭聊天流(默认会话) */
    messages: [],
    /** 私聊会话分桶:peerId(字符串)→ 消息数组 */
    dmMessages: {},
    /** 私聊未读:peerId(字符串)→ 条数 */
    dmUnread: {},
    /** 私聊历史是否已拉过:peerId(字符串)→ bool */
    dmLoaded: {},
    /** 亲友圈家庭房间:familyId(字符串)→ 消息数组 */
    roomMessages: {},
    /** 亲友圈家庭房间未读:familyId(字符串)→ 条数(不含本家) */
    roomUnread: {},
    /** 亲友圈家庭房间历史是否已拉过:familyId(字符串)→ bool */
    roomLoaded: {},
    /** 我可见的聊天室概要:familyId(字符串)→ {familyId,familyName,alias,coverImage,self} */
    roomsMeta: {},
    /** 本家家庭元数据(名称/封面):左列分组头与会话标题共用,对象列加载时回填 */
    ownFamily: { name: '', coverImage: '' },
    /** 当前正在看的亲友圈家庭房间 id(null=本家) */
    activeRoomId: null,
    /** 当前家庭房间展示名(打开房间时传入,标题用) */
    activeRoomName: '',
    /** 当前会话:null=家庭房间(本家或 activeRoomId),数字=私聊对方 userId */
    activePeer: null,
    /** 当前私聊对方的展示名(打开会话时传入,标题用) */
    activePeerName: '',
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
    /**
     * 进入会话时快照的未读锚点:{ key: null=家庭流 / 'peerId'=私聊, count, firstId }。
     * 由 loadHistory / openDm 在推进已读之前算好,消息区取走后清空(「N 条新消息」跳第一条未读)。
     */
    unreadAnchor: null,
    /** 待发送的富内容卡片(分享注入/站内选择/游戏邀请的预填草稿,{msgType,content,payload} 已是可发送形态) */
    shareCard: null,
    /** 成员在线状态实时覆盖:userId(字符串)→ ONLINE/BUSY/AWAY/OFFLINE(成员列表/聊天左列头像点用) */
    presenceMap: {},
  }),
  getters: {
    /** 聊天界面是否在前台(/chat 页或浮窗展开) */
    viewing: (s) => s.chatPageActive || s.floatOpen,
    /** 当前会话键:null=本家 / p:<userId>=私聊 / f:<familyId>=亲友圈家庭房间 */
    activeKey: (s) => (s.activePeer != null ? `p:${s.activePeer}` : s.activeRoomId != null ? `f:${s.activeRoomId}` : null),
    /** 总未读 = 本家 + 全部私聊 + 亲友圈家庭房间(浮球/侧栏角标) */
    totalUnread: (s) =>
      s.unread +
      Object.values(s.dmUnread).reduce((a, b) => a + (Number(b) || 0), 0) +
      Object.values(s.roomUnread).reduce((a, b) => a + (Number(b) || 0), 0),
    /** 当前会话的消息列表(本家流 / 私聊分桶 / 亲友圈家庭房间分桶) */
    activeMessages: (s) => {
      if (s.activePeer != null) return s.dmMessages[String(s.activePeer)] || []
      if (s.activeRoomId != null) return s.roomMessages[String(s.activeRoomId)] || []
      return s.messages
    },
    /** 当前会话是否已加载过历史(私聊/房间按会话记,本家看 hydrated) */
    activeLoaded: (s) => {
      if (s.activePeer != null) return !!s.dmLoaded[String(s.activePeer)]
      if (s.activeRoomId != null) return !!s.roomLoaded[String(s.activeRoomId)]
      return s.hydrated
    },
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
      // 冷启动/长挂后台后 access token 可能已过期,而 WS 握手不走 HTTP 预检:
      // 先触发共享续期,续期成功后由 token 监听重建连接(本次仍先连,失败交给退避)
      ensureFreshToken()
      socket.connect()
    },

    /**
     * 回到前台/恢复可见:锁屏或切后台期间 WebView 的定时器被系统挂起,旧连接多已僵死却收不到 close,
     * 只靠指数退避最长要等 60s 才重连(用户表现为「App 连不上聊天室」)。
     * 这里直接重建连接,连接成功后的 open 事件会补拉断档期间的历史与未读数。
     */
    async wake() {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) return
      const now = Date.now()
      // 原生壳锁屏/解锁时 visibilitychange 与 appStateChange 会前后脚都触发,只处理第一次
      if (now - lastWakeAt < 2000) return
      lastWakeAt = now
      const away = hiddenAt ? now - hiddenAt : Infinity
      hiddenAt = 0
      if (!socket) {
        this.ensureConnected()
        return
      }
      // 极短的切换(系统权限弹窗/键盘弹出)不值得重建,连接还在就直接用
      if (away < 1500 && socket.status.value === SOCKET_STATUS.OPEN) return
      // 后台期间 access token 可能已过期:WS 握手不走 HTTP 预检,拿过期令牌会一直握手失败。
      // 续期成功会改 token,由 wire() 里的 token 监听自动重建连接
      if (await ensureFreshToken()) return
      socket.reconnectNow()
    },

    /** 切后台(仅原生壳调用):静默断开不排重连;Web 后台标签页仍能收消息,不打断 */
    sleep() {
      hiddenAt = Date.now()
      // 通话中的挂断/信令要一直在线,后台不主动断开(媒体本身是 P2P)
      if (useCallStore().inCall) return
      if (socket) socket.suspend()
    },

    /** 装全局前后台监听(只装一次):回前台立即重连,原生壳切后台暂停连接 */
    installLifecycle() {
      if (lifecycleInstalled || typeof window === 'undefined') return
      lifecycleInstalled = true
      const native = isNativeApp()
      document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
          // 原生壳锁屏/切后台时提前断开,避免 Doze 下留下半开连接
          if (native) this.sleep()
        } else {
          this.wake()
        }
      })
      // 从往返缓存(bfcache)恢复:页面没有重新挂载,连接同样需要确认
      window.addEventListener('pageshow', (e) => {
        if (e.persisted) this.wake()
      })
      // 断网恢复:退避计时器可能已被打断,直接重连
      window.addEventListener('online', () => this.wake())
      if (native) {
        // 原生壳最可靠的信号:锁屏/切后台/回前台都走 appStateChange
        import('@capacitor/app')
          .then(({ App }) => {
            App.addListener('appStateChange', ({ isActive }) => {
              if (isActive) this.wake()
              else this.sleep()
            })
          })
          .catch((e) => console.warn('[chat] 前后台监听装配失败', e))
      }
    },

    createSocket() {
      const s = createChatSocket({ url: () => this.buildUrl() })
      s.on('open', () => {
        const reconnected = wsEverOpened
        wsEverOpened = true
        this.socketStatus = SOCKET_STATUS.OPEN
        // 断线期间的消息不会补推:重连后强制补拉一页历史并回正未读数,
        // 否则浮窗要等下一批新消息才「活过来」(用户表现为经常收不到消息)
        if (reconnected && this.hydrated) this.resync()
      })
      s.on('close', () => {
        if (socket === s) this.socketStatus = SOCKET_STATUS.CLOSED
      })
      s.on('message', (data) => this.onIncoming(data))
      // 成员上下线/手动切换忙碌离开:同家庭房间广播,成员列表与聊天左列的头像点实时更新
      s.on('presence', (data) => this.applyPresence(data))
      s.on('call-signal', (data) => useCallStore().onSignal(data))
      s.on('game-signal', (data) => {
        for (const fn of gameSignalListeners) fn(data)
      })
      return s
    },

    buildUrl() {
      const userStore = useUserStore()
      const token = encodeURIComponent(userStore.token || '')
      // @vite 开发代理不转发 WS,直连 8080 端口(与原 Chat.vue 直连口径一致)
      if (import.meta.env.DEV) return `ws://localhost:8080/api/ws/chat?token=${token}`
      // 按 API 基址推导:套壳后页面 origin 是 https://localhost,用 location.host 会连到壳自己
      const apiBase = import.meta.env.VITE_API_BASE || `${location.origin}/api`
      const url = new URL(apiBase, location.href)
      const proto = url.protocol === 'https:' ? 'wss:' : 'ws:'
      return `${proto}//${url.host}${url.pathname.replace(/\/+$/, '')}/ws/chat?token=${token}`
    },

    /** 一次性接线:登出清场 + token 换代重连(WS 房间绑定握手时 familyId 快照,切换家庭必须重建) */
    wire(userStore) {
      if (wired) return
      wired = true
      this.installLifecycle()
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

    /** WS 在线状态下行:{userId,status}:写入实时覆盖表(成员列表/聊天左列头像点据此渲染) */
    applyPresence(data) {
      if (!data || data.userId == null || !data.status) return
      this.presenceMap[String(data.userId)] = data.status
    },

    /** WS 到达一条消息:进缓存、驱动 lastIncoming、按界面状态推进已读/未读;私聊按 dm 标记分桶 */
    onIncoming(data) {
      if (!data || data.id == null) return
      // 壳内消息里的图片/封面是 /files/... 相对路径,补成服务端绝对地址(Web 端恒等)
      data = rewriteAssetUrls(data)
      const userStore = useUserStore()
      const mine = data.senderId === userStore.userInfo?.id
      // 私聊:data.dm.userId = 对方 id(我的回声里是收件人,对方的下发里是发送人)
      if (data.dm && data.dm.userId != null) {
        this.onDmIncoming(data, mine)
        return
      }
      // 亲友圈家庭房间:下行带 familyId(本家消息也带,与本家 id 相等时走原家庭流)
      const ownFamilyId = Number(userStore.userInfo?.familyId) || null
      const roomId = data.familyId == null ? ownFamilyId : Number(data.familyId)
      if (roomId != null && ownFamilyId != null && roomId !== ownFamilyId) {
        this.onRoomIncoming(data, roomId, mine)
        return
      }
      // 重连补拉与实时推送可能撞车:同一 id 只留一份
      if (this.messages.some((m) => m.id === data.id)) return
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

    /** 亲友圈家庭房间消息进对应分桶;在看的房间立即置已读,否则累计该房未读 */
    onRoomIncoming(data, familyId, mine) {
      const key = String(familyId)
      const bucket = this.roomMessages[key] || (this.roomMessages[key] = [])
      if (bucket.some((m) => m.id === data.id)) return
      bucket.push(data)
      if (bucket.length > KEEP_MESSAGES) {
        bucket.splice(0, bucket.length - KEEP_MESSAGES)
      }
      this.lastIncoming = data
      const openHere = this.viewing && this.activePeer == null && String(this.activeRoomId) === key
      if (mine || openHere) {
        chatApi.read(data.id, familyId).catch(() => {})
        if (openHere) this.roomUnread[key] = 0
      } else {
        this.roomUnread[key] = (Number(this.roomUnread[key]) || 0) + 1
      }
    },

    /** 私聊消息进对应分桶;在看的会话立即置已读,否则累计该会话未读 */
    onDmIncoming(data, mine) {
      const key = String(data.dm.userId)
      const bucket = this.dmMessages[key] || (this.dmMessages[key] = [])
      if (bucket.some((m) => m.id === data.id)) return
      bucket.push(data)
      if (bucket.length > KEEP_MESSAGES) {
        bucket.splice(0, bucket.length - KEEP_MESSAGES)
      }
      this.lastIncoming = data
      const openHere = this.viewing && this.activePeer != null && String(this.activePeer) === key
      if (mine || openHere) {
        chatApi.dmRead(Number(key)).catch(() => {})
        if (openHere) this.dmUnread[key] = 0
      } else {
        this.dmUnread[key] = (Number(this.dmUnread[key]) || 0) + 1
      }
    },

    /**
     * 记下「N 条新消息」的锚点:取当前会话缓存里倒数第 count 条他人消息。
     * 未读数来自服务端(按 ID 差值估算)、缓存又只留 200 条,所以越界就退化成最早一条。
     */
    setUnreadAnchor(key, count) {
      const n = Math.max(0, Number(count) || 0)
      const target = key == null ? null : String(key)
      const list =
        target == null ? this.messages : target.startsWith('f:') ? this.roomMessages[target.slice(2)] || [] : this.dmMessages[target] || []
      const myId = useUserStore().userInfo?.id
      const incoming = list.filter((m) => m.senderId !== myId)
      if (!n || !incoming.length) {
        this.unreadAnchor = null
        return
      }
      // 未读数可能大于本地缓存里的条数(只留 200 条 / 含自己在别处发的),按实际能定位的条数收敛
      const visible = Math.min(n, incoming.length)
      const first = incoming[incoming.length - visible]
      this.unreadAnchor = { key: target, count: visible, firstId: first.id }
    },

    /** 浮标已跳转 / 已读完:锚点作废 */
    clearUnreadAnchor() {
      this.unreadAnchor = null
    },

    /**
     * 打开私聊会话:切 activePeer、拉历史(拉过就跳过)、把对方发来的未读置已读。
     * name 为对方展示名(标题用,可省)。
     */
    async openDm(peerId, name = '') {
      if (peerId == null) return
      this.activePeer = Number(peerId)
      this.activePeerName = name || this.activePeerName || ''
      this.activeRoomId = null
      this.activeRoomName = ''
      const unreadBefore = Number(this.dmUnread[String(peerId)]) || 0
      await this.loadDmHistory(this.activePeer)
      if (this.viewing) {
        chatApi.dmRead(Number(peerId)).catch(() => {})
        this.dmUnread[String(peerId)] = 0
        if (unreadBefore > 0) this.setUnreadAnchor(peerId, unreadBefore)
      }
    },

    /**
     * 打开亲友圈家庭房间:切房间、拉历史(拉过就跳过)、把该房未读置已读。
     * name 为展示名(圈内称呼/家庭名,可省)。
     */
    async openRoom(familyId, name = '') {
      if (familyId == null) return
      const id = Number(familyId)
      this.activePeer = null
      this.activePeerName = ''
      this.activeRoomId = id
      const meta = this.roomsMeta[String(id)]
      this.activeRoomName = name || meta?.alias || meta?.familyName || ''
      const unreadBefore = Number(this.roomUnread[String(id)]) || 0
      await this.loadRoomHistory(id)
      if (this.viewing) {
        chatApi.read(null, id).catch(() => {})
        this.roomUnread[String(id)] = 0
        if (unreadBefore > 0) this.setUnreadAnchor(`f:${id}`, unreadBefore)
      }
    },

    /** 回到本家聊天室 */
    closeDm() {
      this.activePeer = null
      this.activePeerName = ''
      this.activeRoomId = null
      this.activeRoomName = ''
      this.unreadAnchor = null
    },

    /** 拉某个私聊会话的历史(与本地缓存按 id 合并,幂等) */
    async loadDmHistory(peerId, { force = false } = {}) {
      const key = String(peerId)
      if (this.dmLoaded[key] && !force) return
      try {
        const msgs = await chatApi.dmHistory(Number(peerId))
        this.dmMessages[key] = mergeMessages(this.dmMessages[key], msgs, KEEP_MESSAGES)
        this.dmLoaded[key] = true
      } catch {
        // 拉失败不装死:保留已有缓存,下次打开重试
      }
    },

    /** 私聊未读基线(登录后/重连后拉一次;实时增量靠 WS onDmIncoming) */
    async loadDmUnread() {
      try {
        const list = await chatApi.dmUnread()
        const next = {}
        for (const row of list || []) {
          next[String(row.userId)] = Number(row.unread) || 0
        }
        this.dmUnread = next
      } catch {
        // 未读基线拉不到就用现有值
      }
    },

    /** 拉某个亲友圈家庭房间的历史(与本地缓存按 id 合并,幂等) */
    async loadRoomHistory(familyId, { force = false } = {}) {
      const key = String(familyId)
      if (this.roomLoaded[key] && !force) return
      try {
        const msgs = await chatApi.history({ familyId: Number(familyId) })
        this.roomMessages[key] = mergeMessages(this.roomMessages[key], msgs, KEEP_MESSAGES)
        this.roomLoaded[key] = true
      } catch {
        // 拉失败不装死:保留已有缓存,下次打开重试
      }
    },

    /** 亲友圈家庭房间未读基线 + 房间概要(登录后/重连后拉一次;正在看的房间不被旧数据覆盖) */
    async loadRoomUnread() {
      try {
        const list = await chatApi.rooms()
        const nextUnread = {}
        const nextMeta = {}
        for (const row of list || []) {
          if (!row || row.self) continue
          const key = String(row.familyId)
          nextMeta[key] = row
          // 正开着的房间已读位点刚推进,别被基线拉回的旧未读盖掉
          if (this.viewing && String(this.activeRoomId) === key) continue
          nextUnread[key] = Number(row.unread) || 0
        }
        this.roomUnread = nextUnread
        this.roomsMeta = nextMeta
      } catch {
        // 基线拉不到就用现有值
      }
    },

    /** 发送文本消息(≤2000 字);连接不可用时返回 false 由调用方提示。私聊/亲友圈房间按当前会话分流 */
    sendText(text) {
      const t = (text || '').trim()
      if (!t || t.length > 2000 || !socket) return false
      const data = { content: t }
      if (this.activePeer != null) data.dm = { userId: this.activePeer }
      else if (this.activeRoomId != null) data.familyId = this.activeRoomId
      return socket.send({ type: 'message', data })
    },

    /**
     * 发送富内容消息({msgType, content, payload});payload 传结构化对象,这里统一序列化。
     * 私聊/亲友圈房间按当前会话分流。返回 false 表示未发出(连接断开或负载非法)。
     */
    sendContent({ msgType, content, payload }) {
      if (!socket) return false
      const data = { content: content == null ? '' : String(content), msgType: msgType || 'TEXT' }
      if (this.activePeer != null) data.dm = { userId: this.activePeer }
      else if (this.activeRoomId != null) data.familyId = this.activeRoomId
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

    /**
     * 进入聊天界面拉初始数据:未读游标 + 最新一页历史。
     * 浮窗与 /chat 页共用同一份缓存,谁先打开谁拉;hydrated 之后不再重复拉(force 用于 /chat 进页刷新)。
     * markRead=false 时只并历史与回正未读数,不推进已读(断线补拉/浮窗收起时用)。
     * 两条接口互不依赖:任一失败(断网/续期失败)都只丢一路数据,不能让消息区永久转圈。
     */
    async loadHistory({ force = false, markRead = true } = {}) {
      if (historyPromise) return historyPromise
      if (this.hydrated && !force) return
      const gen = historyGen
      historyPromise = (async () => {
        const [un, msgs] = await Promise.allSettled([chatApi.unread(), chatApi.history()])
        if (gen !== historyGen) return
        if (msgs.status === 'fulfilled') {
          // 与 WS 已推入的消息按 id 合并,避免覆盖掉加载期间到达的新消息
          this.messages = mergeMessages(this.messages, msgs.value, KEEP_MESSAGES)
        }
        if (un.status === 'fulfilled') {
          if (markRead) {
            this.setUnreadAnchor(null, un.value?.unread)
            if (un.value?.lastMsgId) chatApi.read(un.value.lastMsgId).catch(() => {})
            this.unread = 0
          } else {
            this.unread = Math.max(0, Number(un.value?.unread) || 0)
          }
        }
        this.hydrated = true
        // 私聊/亲友圈房间未读基线(不阻塞本次加载,失败只影响角标精度)
        this.loadDmUnread()
        this.loadRoomUnread()
      })().finally(() => {
        historyPromise = null
      })
      return historyPromise
    },

    /** 断线重连后补拉:正在看就顺手推进已读,否则只回正角标;正在看的亲友圈房间单独补拉 */
    resync() {
      if (this.activeRoomId != null) this.loadRoomHistory(this.activeRoomId, { force: true })
      return this.loadHistory({ force: true, markRead: this.viewing && this.activeRoomId == null })
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
      historyGen += 1
      historyPromise = null
      wsEverOpened = false
      hiddenAt = 0
      lastWakeAt = 0
      if (socket) {
        socket.disconnect()
        socket = null
      }
      this.socketStatus = SOCKET_STATUS.IDLE
      this.messages = []
      this.dmMessages = {}
      this.dmUnread = {}
      this.dmLoaded = {}
      this.roomMessages = {}
      this.roomUnread = {}
      this.roomLoaded = {}
      this.roomsMeta = {}
      this.ownFamily = { name: '', coverImage: '' }
      this.activeRoomId = null
      this.activeRoomName = ''
      this.activePeer = null
      this.activePeerName = ''
      this.unread = 0
      this.unreadAnchor = null
      this.lastIncoming = null
      this.chatPageActive = false
      this.floatOpen = false
      this.hydrated = false
      this.presenceMap = {}
      gameSignalListeners = []
    },
  },
})
