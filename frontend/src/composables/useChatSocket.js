// 聊天 WebSocket 驱动(全局单连接,由 stores/chat.js 持有):
// 连接 / 指数退避重连(3s 起步翻倍封顶 60s)/ 心跳 / 按下行 type 分发。
// 工厂式设计(socketFactory 可注入),测试用假 socket 驱动重连与时钟。
import { ref } from 'vue'

export const SOCKET_STATUS = {
  IDLE: 'idle',
  CONNECTING: 'connecting',
  OPEN: 'open',
  CLOSED: 'closed',
}

const HEARTBEAT_MS = 25000
const BASE_RETRY_MS = 3000
const MAX_RETRY_MS = 60000

/**
 * 创建一条聊天 WS 连接管理器。
 * @param {Object} options
 * @param {Function|string} options.url 连接地址;传函数则每次(重)连时取最新值(拿最新 token)
 * @param {number} [options.heartbeatMs=25000] 心跳间隔
 * @param {number} [options.baseRetryMs=3000] 重连起始间隔
 * @param {number} [options.maxRetryMs=60000] 重连间隔上限
 * @param {(url: string) => WebSocket} [options.socketFactory] socket 工厂(默认浏览器 WebSocket,测试注入假实现)
 */
export function createChatSocket({
  url,
  heartbeatMs = HEARTBEAT_MS,
  baseRetryMs = BASE_RETRY_MS,
  maxRetryMs = MAX_RETRY_MS,
  socketFactory,
} = {}) {
  const status = ref(SOCKET_STATUS.IDLE)
  const factory = socketFactory || ((u) => new WebSocket(u))
  const listeners = new Map()
  let ws = null
  let heartbeatTimer = null
  let retryTimer = null
  let retryCount = 0
  let disposed = false

  const emit = (type, data) => {
    const set = listeners.get(type)
    if (set) for (const fn of set) fn(data)
  }

  /** 注册下行分发:type 为服务端 envelope 的 type 字段(message/pong/error),返回解绑函数 */
  const on = (type, fn) => {
    if (!listeners.has(type)) listeners.set(type, new Set())
    listeners.get(type).add(fn)
    return () => listeners.get(type)?.delete(fn)
  }

  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  const startHeartbeat = () => {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === 1) ws.send(JSON.stringify({ type: 'ping' }))
    }, heartbeatMs)
  }

  // 断开后指数退避重连:3s→6s→12s…封顶 60s;连接成功即归零
  const scheduleRetry = () => {
    if (disposed) return
    const delay = Math.min(baseRetryMs * 2 ** retryCount, maxRetryMs)
    retryCount += 1
    retryTimer = setTimeout(() => {
      if (!disposed) connect()
    }, delay)
  }

  const connect = () => {
    if (disposed) return
    if (ws && (ws.readyState === 0 || ws.readyState === 1)) return
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    status.value = SOCKET_STATUS.CONNECTING
    const address = typeof url === 'function' ? url() : url
    const socket = factory(address)
    ws = socket
    // 旧 socket 的迟到事件用自校验丢弃(ws !== socket 时不处理)
    socket.onopen = () => {
      if (ws !== socket) return
      retryCount = 0
      status.value = SOCKET_STATUS.OPEN
      startHeartbeat()
      emit('open')
    }
    socket.onmessage = (evt) => {
      if (ws !== socket) return
      let pkt
      try {
        pkt = JSON.parse(evt.data)
      } catch {
        return
      }
      if (pkt && pkt.type) emit(pkt.type, pkt.data)
    }
    socket.onclose = () => {
      if (ws !== socket) return
      stopHeartbeat()
      ws = null
      status.value = SOCKET_STATUS.CLOSED
      emit('close')
      scheduleRetry()
    }
    socket.onerror = () => {
      if (ws !== socket) return
      emit('error')
    }
  }

  /** 静默丢弃当前 socket:先摘回调再关,不触发 close 事件、不排重连 */
  const dropSocket = () => {
    stopHeartbeat()
    if (!ws) return
    const old = ws
    ws = null
    old.onopen = null
    old.onmessage = null
    old.onclose = null
    old.onerror = null
    try {
      old.close()
    } catch {
      /* 已关闭/正在关闭:忽略 */
    }
  }

  /**
   * 回到前台时立即重建连接(不等指数退避)。
   * 锁屏/切后台期间 JS 定时器被系统挂起,旧连接多已僵死但收不到 close,
   * 只靠退避重连最长要等 60s,用户看到的就是「连不上聊天室」。
   */
  const reconnectNow = () => {
    if (disposed) return
    retryCount = 0
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    dropSocket()
    status.value = SOCKET_STATUS.CLOSED
    connect()
  }

  /** 切后台(原生壳):静默断开且不排重连,避免 Doze 下反复失败;回前台再 reconnectNow */
  const suspend = () => {
    if (disposed) return
    retryCount = 0
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    dropSocket()
    status.value = SOCKET_STATUS.CLOSED
    emit('close')
  }

  /** 上行:{type,data} 结构;未连接返回 false 由调用方提示 */
  const send = (obj) => {
    if (!ws || ws.readyState !== 1) return false
    ws.send(JSON.stringify(obj))
    return true
  }

  /** 永久断开:不再重连、停心跳、静默关闭(登出/换 token 重建时用) */
  const disconnect = () => {
    disposed = true
    if (retryTimer) clearTimeout(retryTimer)
    stopHeartbeat()
    if (ws) {
      ws.onclose = null
      ws.close()
      ws = null
    }
    status.value = SOCKET_STATUS.IDLE
  }

  return { status, connect, disconnect, reconnectNow, suspend, send, on }
}
