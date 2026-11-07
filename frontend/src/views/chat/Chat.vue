<!-- 聊天室页(V3.3):家庭房间实时聊天。WS 连接由 stores/chat.js 全局单例持有,
     连接后拉一次历史消息;发送即落库并广播到同房间全部在线成员 -->
<template>
  <div class="page chat-page">
    <Breadcrumb :items="[{ label: $t('chat.title') }]" />
    <div class="chat-box card">
      <div class="chat-title">
        {{ $t('chat.roomTitle') }} <span class="online-hint">{{ $t('chat.onlineHint') }}</span>
      </div>

      <!-- 消息区(贴近底部才自动滚动,翻历史时改出「新消息」浮标) -->
      <div ref="msgBox" class="msg-list" v-loading="loading" @scroll="onMsgScroll">
        <el-empty v-if="!loading && !messages.length" :description="$t('chat.noData')" :image-size="70" />
        <TransitionGroup name="msg" tag="div">
          <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.senderId === myId }">
            <el-avatar :size="30" class="msg-avatar">{{ m.senderName?.charAt(0) }}</el-avatar>
            <div class="msg-body">
              <div class="msg-meta">{{ m.senderName }} · {{ fmtTime(m.createdAt) }}</div>
              <div class="msg-bubble">{{ m.content }}</div>
            </div>
          </div>
        </TransitionGroup>
      </div>

      <!-- 翻历史时的新消息浮标:点一下回到底部 -->
      <button v-if="unreadNew > 0" type="button" class="msg-jump" @click="scrollBottom({ clearUnread: true })">
        {{ $t('chat.newMessages', { n: unreadNew }) }}
      </button>

      <!-- 输入区 -->
      <div class="msg-input">
        <el-input
          v-model="draft"
          :placeholder="$t('chat.placeholder')"
          @keyup.enter="send"
          :disabled="!connected"
          maxlength="2000"
          show-word-limit
        />
        <el-button type="primary" :disabled="!connected" @click="send">{{ $t('chat.send') }}</el-button>
      </div>
      <div class="conn-hint" :class="connected ? 'ok' : 'bad'">
        {{ connected ? $t('chat.connected') : connecting ? $t('chat.connecting') : $t('chat.reconnecting') }}
      </div>
    </div>
  </div>
</template>

<script setup>
// 聊天室:连接/心跳/重连在 stores/chat.js(全局单连接,页面只做展示与滚动);
// token 追加在 URL 查询参数(浏览器 WS API 无法自定义 Authorization 头)
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import Breadcrumb from '@/components/Breadcrumb.vue'

const userStore = useUserStore()
const chatStore = useChatStore()
const myId = computed(() => userStore.userInfo?.id)

const messages = computed(() => chatStore.messages)
const draft = ref('')
const loading = ref(false)
const connected = computed(() => chatStore.socketStatus === 'open')
const connecting = computed(() => chatStore.socketStatus === 'connecting')
const msgBox = ref(null)
// 距底多少像素内算「贴着底部」:在这个范围内收消息才自动滚
const NEAR_BOTTOM_PX = 80
// 用户在翻历史时累计的新消息条数,点浮标或自己滚回底部即清零
const unreadNew = ref(0)

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

// WS 新消息到达(含自己发的):贴底/自己发的回底部,否则累计浮标计数。
// 消息本体已由 store 进缓存,这里只处理滚动。
watch(
  () => chatStore.lastIncoming,
  (m) => {
    if (!m) return
    if (m.senderId === myId.value || isNearBottom()) scrollBottom({ clearUnread: true })
    else unreadNew.value += 1
  },
)

/** 是否贴着底部(必须在插入新消息之前判断:插入后高度已变) */
const isNearBottom = () => {
  const el = msgBox.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= NEAR_BOTTOM_PX
}

const scrollBottom = ({ clearUnread = false } = {}) => {
  if (clearUnread) unreadNew.value = 0
  requestAnimationFrame(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

/** 自己滚回底部时浮标自然消失;回调只做一次布尔判断,不需要节流 */
const onMsgScroll = () => {
  if (unreadNew.value > 0 && isNearBottom()) unreadNew.value = 0
}

const send = () => {
  if (!connected.value) return
  if (chatStore.sendText(draft.value)) draft.value = ''
}

const loadHistory = async () => {
  loading.value = true
  try {
    // 初始化:标记已读到当前游标,让未读归零
    await chatStore.loadHistory()
    // 首次进入定位最新
    scrollBottom({ clearUnread: true })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 连接是全局单例:页面只声明自己在看与补建连接,离开不断开(浮窗/未读在别的页面继续收)
  chatStore.viewing = true
  await loadHistory()
  chatStore.ensureConnected()
})
onBeforeUnmount(() => {
  chatStore.viewing = false
})
</script>

<style scoped>
.chat-box {
  position: relative;
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

/* 新消息从底部轻轻抬起;历史一次性加载时不播,只影响后续追加 */
.msg-enter-active {
  transition:
    opacity 0.22s ease,
    transform 0.26s var(--ease-soft);
}
.msg-leave-active {
  transition: opacity 0.16s ease;
}
.msg-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.msg-leave-to {
  opacity: 0;
}
.msg-move {
  transition: transform 0.24s var(--ease-soft);
}
.msg-avatar {
  flex-shrink: 0;
}
.msg-body {
  max-width: 78%;
  min-width: 0;
}
.msg-row.mine .msg-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
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
  max-width: 100%;
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
/* 翻历史时的新消息浮标:浮在输入区上方居中 */
.msg-jump {
  position: absolute;
  left: 50%;
  bottom: 72px;
  transform: translateX(-50%);
  z-index: var(--z-action);
  padding: 5px 14px;
  border: none;
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.5;
  cursor: pointer;
  color: var(--color-brand-text);
  background: rgba(var(--color-brand-rgb), 0.92);
  box-shadow: var(--shadow);
  transition: transform var(--dur-fast) var(--ease-soft);
}
.msg-jump:hover {
  transform: translateX(-50%) translateY(-1px);
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

@media (max-width: 768px) {
  .chat-box {
    height: calc(100vh - 120px);
    min-height: 300px;
    padding: 12px;
  }
  .chat-title {
    font-size: 14px;
    margin-bottom: 8px;
  }
  .online-hint {
    display: none;
  }
  .msg-avatar {
    width: 28px !important;
    height: 28px !important;
  }
  .msg-meta {
    font-size: 11px;
  }
  .msg-bubble {
    font-size: 14px;
    padding: 8px 10px;
  }
  .msg-input {
    gap: 8px;
  }
  .msg-input .el-button {
    flex-shrink: 0;
  }
  .conn-hint {
    font-size: 11px;
    margin-top: 6px;
  }
}
</style>
