<!-- 聊天消息列表:/chat 页与全局浮窗(P2)共用。数据源 stores/chat.js,
      新消息到达贴着底部就自动跟到底,翻历史期间累计成右上角「N 条新消息」,
      点一下跳到这一批的第一条未读;气泡按 msgType 分发卡片 -->
<template>
  <div class="chat-messages" :style="textStyleVars">
    <div ref="msgBox" class="msg-list" v-loading="loading" @scroll="onMsgScroll">
      <el-empty v-if="!loading && !messages.length" :description="$t('chat.noData')" :image-size="70" />
      <TransitionGroup name="msg" tag="div">
        <div v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.senderId === myId }" :data-mid="m.id">
          <el-avatar :size="30" class="msg-avatar" :src="m.senderAvatar || ''" :alt="m.senderName || ''">
            {{ m.senderName?.charAt(0) }}
          </el-avatar>
          <div class="msg-body">
            <div class="msg-meta">
              {{ m.senderName }}
              <span v-if="m.senderFamilyName"> · {{ $t('chat.fromFamily', { name: m.senderFamilyName }) }}</span>
              · {{ fmtTime(m.createdAt) }}
            </div>
            <div class="msg-bubble" :class="{ 'is-card': isCard(m) }">
              <span v-if="!isCard(m)">{{ m.content }}</span>
              <MsgCardHost v-else :msg="m" />
            </div>
          </div>
        </div>
      </TransitionGroup>
    </div>

    <!-- 新消息浮标:窗口右上角,点一下跳到本批第一条未读(不是直接到底) -->
    <button v-if="jumpCount > 0" type="button" class="msg-jump" :title="$t('chat.jumpUnread')" @click="jumpToFirstUnread">
      {{ $t('chat.newMessages', { n: jumpCount }) }}
    </button>
  </div>
</template>

<script setup>
// 消息区:仅展示与滚动,连接/收发在 stores/chat.js;卡片渲染见 cards/*
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useChatTextStyle } from '@/composables/useChatTextStyle'
import { MSG_TYPE, messageRoomId } from '@/utils/chatMsg'
import MsgCardHost from '@/components/chat/cards/MsgCardHost.vue'

const userStore = useUserStore()
const chatStore = useChatStore()
const { style: textStyle } = useChatTextStyle()
const myId = computed(() => userStore.userInfo?.id)
const messages = computed(() => chatStore.activeMessages)
// 历史没到位才转圈;WS 已经把消息推上来时不能还盖着遮罩(旧版只看 hydrated,浮窗首次展开会一直转)
const loading = computed(() => !chatStore.activeLoaded && !messages.value.length)

// 字号作用于全部消息,颜色只作用于自己的气泡(未选色时跟随主题)
const textStyleVars = computed(() => ({
  '--chat-font-size': `${textStyle.value.size}px`,
  ...(textStyle.value.color ? { '--chat-color': textStyle.value.color } : {}),
}))

const msgBox = ref(null)
// 距底多少像素内算「贴着底部」:在这个范围内收消息才自动滚
const NEAR_BOTTOM_PX = 80
// 是否贴着底部(默认贴底:进会话看最新,来新消息跟着走);用户主动往上翻历史后暂停
let stickBottom = true
// 翻历史期间累计的新消息 {count, firstId};自己滚回底部即清零
const liveNew = ref(null)

const isCard = (m) => m.msgType && m.msgType !== MSG_TYPE.TEXT

const fmtTime = (t) => (t ? String(t).replace('T', ' ').slice(5, 16) : '')

const activeKey = computed(() => chatStore.activeKey)

/** 进会话时服务端报的未读锚点(本家 / 当前房间 / 当前私聊按 activeKey 对应) */
const anchor = computed(() => {
  const a = chatStore.unreadAnchor
  if (!a || a.key !== activeKey.value) return null
  return a
})

/** 浮标显示哪一批:翻历史期间的新消息优先,否则是进会话时的未读锚点 */
const jumpTarget = computed(() => liveNew.value || anchor.value)
const jumpCount = computed(() => jumpTarget.value?.count || 0)

/** 是否是当前会话的消息(store 的 lastIncoming 本家/房间/私聊共用一份) */
const belongsHere = (m) => {
  if (!m) return false
  if (m.dm && m.dm.userId != null) return activeKey.value != null && String(m.dm.userId) === String(chatStore.activePeer)
  // 家庭消息:按消息所属家庭与当前房间(缺省=本家)比对
  const room = messageRoomId(m, userStore.userInfo?.familyId)
  if (room == null) return chatStore.activeRoomId == null
  const active = chatStore.activeRoomId == null ? Number(userStore.userInfo?.familyId) : Number(chatStore.activeRoomId)
  return active != null && room === active
}

/** 是否贴着底部(必须在插入新消息之前判断:插入后高度已变) */
const isNearBottom = () => {
  const el = msgBox.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= NEAR_BOTTOM_PX
}

const scrollBottom = ({ smooth = false } = {}) => {
  const el = msgBox.value
  if (!el) return
  if (smooth) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  else el.scrollTop = el.scrollHeight
}

/** 贴底时补一次:图片/卡片是异步撑开高度的,收完消息立刻贴底仍会被顶上一截 */
const pinIfSticky = () => {
  if (stickBottom) scrollBottom()
}

/** 跳到某条消息:滚到可视区上部,短暂高亮一下好找到 */
const scrollToMessage = (id) => {
  const el = msgBox.value
  const row = el?.querySelector(`[data-mid="${id}"]`)
  if (!el || !row) {
    scrollBottom()
    return
  }
  const top = row.getBoundingClientRect().top - el.getBoundingClientRect().top + el.scrollTop - 12
  el.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
  row.classList.add('msg-flash')
  setTimeout(() => row.classList.remove('msg-flash'), 1600)
}

/** 点浮标:跳到本批第一条未读,而不是直接到底(底部永远能看到,先看从哪开始漏的) */
const jumpToFirstUnread = () => {
  const target = jumpTarget.value
  if (!target) return
  scrollToMessage(target.firstId)
  chatStore.clearUnreadAnchor()
  liveNew.value = null
}

// WS 新消息到达(含自己发的):贴底/自己发的回底部,否则累计浮标计数。
// 消息本体已由 store 进缓存,这里只处理滚动。
watch(
  () => chatStore.lastIncoming,
  (m) => {
    if (!belongsHere(m)) return
    if (m.senderId === myId.value) {
      stickBottom = true
      liveNew.value = null
      scrollBottom()
      setTimeout(pinIfSticky, 320)
      return
    }
    if (stickBottom) {
      scrollBottom()
      setTimeout(pinIfSticky, 320)
    } else {
      const prev = liveNew.value
      liveNew.value = { count: (prev?.count || 0) + 1, firstId: prev?.firstId ?? m.id }
    }
  },
)

/** 自己滚回底部时浮标自然消失;往上翻则暂停自动跟底 */
const onMsgScroll = () => {
  if (isNearBottom()) {
    stickBottom = true
    liveNew.value = null
  } else {
    stickBottom = false
  }
}

onMounted(() => {
  stickBottom = true
  nextTick(() => {
    scrollBottom()
    // 首屏图片加载完会再撑高一次
    setTimeout(pinIfSticky, 400)
  })
})

// 切换会话(家庭房 ⇄ 圈内其他家庭房 ⇄ 私聊)重置滚动与浮标;进来先看最新
watch(activeKey, () => {
  stickBottom = true
  liveNew.value = null
  nextTick(() => scrollBottom())
})

// 历史补拉完成后高度变化很大,重新贴底
watch(
  () => messages.value.length,
  () => {
    if (stickBottom) nextTick(pinIfSticky)
  },
)
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
  font-size: var(--chat-font-size, 14px);
  line-height: 1.5;
  word-break: break-word;
  display: inline-block;
  max-width: 100%;
}
.msg-row.mine .msg-bubble {
  background: rgba(var(--color-brand-rgb), 0.16);
  text-align: left;
  color: var(--chat-color, inherit);
}
/* 卡片消息去掉内边距,由卡片自带留白 */
.msg-bubble.is-card {
  padding: 4px;
  background: transparent;
}
.msg-row.mine .msg-bubble.is-card {
  background: transparent;
}

/* 跳到第一条未读时闪一下,方便定位 */
.msg-flash .msg-bubble {
  box-shadow: 0 0 0 2px rgba(var(--color-brand-rgb), 0.55);
  transition: box-shadow 0.3s ease;
}

/* 新消息浮标:窗口右上角,不压住最新一条 */
.msg-jump {
  position: absolute;
  right: 6px;
  top: 6px;
  z-index: var(--z-action);
  max-width: calc(100% - 12px);
  padding: 4px 12px;
  border: 1px solid rgba(var(--color-brand-rgb), 0.4);
  border-radius: 999px;
  font-size: 12px;
  line-height: 1.6;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--color-brand-text);
  background: rgba(var(--color-brand-rgb), 0.92);
  box-shadow: var(--shadow);
  transition: transform var(--dur-fast) var(--ease-soft);
}
.msg-jump:hover {
  transform: translateY(-1px);
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
    padding: 8px 10px;
  }
  .msg-bubble.is-card {
    padding: 4px;
  }
}
</style>
