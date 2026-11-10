<!-- 聊天消息列表:/chat 页与全局浮窗(P2)共用。数据源 stores/chat.js,
     新消息到达按滚动位置决定自动回底或出「新消息」浮标;气泡按 msgType 分发卡片 -->
<template>
  <div class="chat-messages">
    <div ref="msgBox" class="msg-list" v-loading="loading" @scroll="onMsgScroll">
      <el-empty v-if="!loading && !messages.length" :description="$t('chat.noData')" :image-size="70" />
      <TransitionGroup name="msg" tag="div">
        <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.senderId === myId }">
          <el-avatar :size="30" class="msg-avatar">{{ m.senderName?.charAt(0) }}</el-avatar>
          <div class="msg-body">
            <div class="msg-meta">{{ m.senderName }} · {{ fmtTime(m.createdAt) }}</div>
            <div class="msg-bubble" :class="{ 'is-card': isCard(m) }">
              <span v-if="!isCard(m)">{{ m.content }}</span>
              <MsgCardHost v-else :msg="m" />
            </div>
          </div>
        </div>
      </TransitionGroup>
    </div>

    <!-- 翻历史时的新消息浮标:点一下回到底部 -->
    <button v-if="unreadNew > 0" type="button" class="msg-jump" @click="scrollBottom({ clearUnread: true })">
      {{ $t('chat.newMessages', { n: unreadNew }) }}
    </button>
  </div>
</template>

<script setup>
// 消息区:仅展示与滚动,连接/收发在 stores/chat.js;卡片渲染见 cards/*
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { MSG_TYPE } from '@/utils/chatMsg'
import MsgCardHost from '@/components/chat/cards/MsgCardHost.vue'

const userStore = useUserStore()
const chatStore = useChatStore()
const myId = computed(() => userStore.userInfo?.id)
const messages = computed(() => chatStore.messages)
// 历史没到位才转圈;WS 已经把消息推上来时不能还盖着遮罩(旧版只看 hydrated,浮窗首次展开会一直转)
const loading = computed(() => !chatStore.hydrated && !chatStore.messages.length)

const msgBox = ref(null)
// 距底多少像素内算「贴着底部」:在这个范围内收消息才自动滚
const NEAR_BOTTOM_PX = 80
// 用户在翻历史时累计的新消息条数,点浮标或自己滚回底部即清零
const unreadNew = ref(0)

const isCard = (m) => m.msgType && m.msgType !== MSG_TYPE.TEXT

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

onMounted(() => {
  scrollBottom({ clearUnread: true })
})
onBeforeUnmount(() => {})
</script>

<style scoped>
.chat-messages {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
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
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}
.msg-row.mine .msg-meta {
  text-align: right;
}
.msg-bubble {
  background: var(--color-card-2);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  display: inline-block;
  max-width: 100%;
}
.msg-row.mine .msg-bubble {
  background: rgba(var(--color-brand-rgb), 0.16);
  text-align: left;
}
/* 卡片消息去掉内边距,由卡片自带留白 */
.msg-bubble.is-card {
  padding: 4px;
  background: transparent;
}
.msg-row.mine .msg-bubble.is-card {
  background: transparent;
}

/* 翻历史时的新消息浮标:浮在输入区上方居中 */
.msg-jump {
  position: absolute;
  left: 50%;
  bottom: 10px;
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

@media (max-width: 768px) {
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
  .msg-bubble.is-card {
    padding: 4px;
  }
}
</style>
