<!-- 聊天室页(V3.3):家庭房间实时聊天。WS 连接 /ws/chat?token=,
     连接后拉一次历史消息;发送即落库并广播到同房间全部在线成员 -->
<template>
  <div class="page chat-page">
    <Breadcrumb :items="[{ label: $t('chat.title') }]" />
    <div class="chat-box card">
      <div class="chat-title">{{ $t('chat.roomTitle') }} <span class="online-hint">{{ $t('chat.onlineHint') }}</span></div>

      <!-- 消息区(自动滚动到底部) -->
      <div ref="msgBox" class="msg-list" v-loading="loading">
        <el-empty v-if="!loading && !messages.length" :description="$t('chat.noData')" :image-size="70" />
        <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.senderId === myId }">
          <el-avatar :size="30" class="msg-avatar">{{ m.senderName?.charAt(0) }}</el-avatar>
          <div class="msg-body">
            <div class="msg-meta">{{ m.senderName }} · {{ fmtTime(m.createdAt) }}</div>
            <div class="msg-bubble">{{ m.content }}</div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="msg-input">
        <el-input v-model="draft" :placeholder="$t('chat.placeholder')" @keyup.enter="send" :disabled="!connected" maxlength="2000" show-word-limit />
        <el-button type="primary" :disabled="!connected" @click="send">{{ $t('chat.send') }}</el-button>
      </div>
      <div class="conn-hint" :class="connected ? 'ok' : 'bad'">
        {{ connected ? $t('chat.connected') : (connecting ? $t('chat.connecting') : $t('chat.reconnecting')) }}
      </div>
    </div>
  </div>
</template>

<script setup>
// 聊天室:原生 WebSocket(浏览器内置),无第三方库;
// token 追加在 URL 查询参数(浏览器 WS API 无法自定义 Authorization 头)
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { chatApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'

const userStore = useUserStore()
const myId = computed(() => userStore.userInfo?.id)

const messages = ref([])
const draft = ref('')
const loading = ref(false)
const connected = ref(false)
const connectedError = ref(false)
const connecting = ref(false)
const msgBox = ref(null)
let ws = null
let heartbeatTimer = null
let reconnectTimer = null

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

// 组件卸载时关闭连接与心跳,避免泄漏
const cleanup = () => {
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  if (reconnectTimer) clearTimeout(reconnectTimer)
  if (ws) { ws.onclose = null; ws.close() }
}

const connect = () => {
  cleanup() // 先断开旧的,再建立新连接
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  // @vite 开发代理不转发 WS,直连 8080 端口即可(Vite HTTP 代理不支持 WS 时后端同源)
  const base = (import.meta.env.DEV ? `ws://localhost:8080` : `${proto}://${location.host}`)
  ws = new WebSocket(`${base}/api/ws/chat?token=${userStore.token}`)
  connecting.value = true
  ws.onopen = () => { connected.value = true; connectedError = false; connecting.value = false }
  ws.onclose = () => {
    connected.value = false
    connecting.value = false
    // 断开后 3 秒自动重连(服务端停机重启或网络波动恢复)
    reconnectTimer = setTimeout(() => { if (userStore.isLoggedIn) connect() }, 3000)
  }
  ws.onerror = () => { connectedError.value = true }
  ws.onmessage = (evt) => {
    let pkt
    try { pkt = JSON.parse(evt.data) } catch { return }
    if (pkt.type === 'message') {
      pushMsg(pkt.data)
      // 自己发的也回显(服务端广播全房间),这里统一推进已读
      chatApi.read(pkt.data.id).catch(() => {})
    }
  }
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === 1) ws.send(JSON.stringify({ type: 'ping' }))
  }, 25000)
}

/** 追加消息(仅保留最近 200 条,过长截断) */
const pushMsg = (m) => {
  messages.value.push(m)
  if (messages.value.length > 200) messages.value.splice(0, messages.value.length - 200)
  scrollBottom()
}

const scrollBottom = () => {
  requestAnimationFrame(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

const send = () => {
  const text = draft.value.trim()
  if (!text || !ws || ws.readyState !== 1) return
  ws.send(JSON.stringify({ content: text }))
  draft.value = ''
}

const loadHistory = async () => {
  loading.value = true
  try {
    // 初始化:标记已读到当前游标,让未读归零
    const un = await chatApi.unread()
    const msgs = await chatApi.history()
    messages.value = msgs
    scrollBottom()
    await chatApi.read(un.lastMsgId)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadHistory()
  if (userStore.isLoggedIn) connect()
})
onBeforeUnmount(cleanup)
</script>

<style scoped>
.chat-box {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 220px);
  min-height: 420px;
  padding: 16px 18px;
}
.chat-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.online-hint {
  font-size: 12px;
  color: #999;
  font-weight: 400;
  margin-left: 8px;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px 4px;
}
.msg-row {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.msg-row.mine {
  flex-direction: row-reverse;
}
.msg-avatar {
  flex-shrink: 0;
}
.msg-meta {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}
.msg-row.mine .msg-meta {
  text-align: right;
}
.msg-bubble {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  display: inline-block;
  max-width: 70%;
}
.msg-row.mine .msg-bubble {
  background: #e8f3ff;
  text-align: left;
}
.msg-input {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}
.conn-hint {
  margin-top: 8px;
  font-size: 12px;
}
.conn-hint.ok {
  color: #67c23a;
}
.conn-hint.bad {
  color: #f56c6c;
}
</style>