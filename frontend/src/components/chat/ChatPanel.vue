<!-- 聊天面板:左侧聊天对象列(可拖动改宽 / 可收起)+ 会话头 + 消息区 + 输入区 + 连接提示,
      /chat 页与全局浮窗(ChatFloat)共用。数据与连接都在 stores/chat.js,这里只组装 -->
<template>
  <div class="chat-panel">
    <div ref="bodyEl" class="chat-panel-body">
      <ChatContactRail v-if="railOpen" :style="railStyle" :narrow="railNarrow" />
      <!-- 对象列宽度拖拽把手:最小缩到只剩头像,收起/展开仍走会话头那个按钮 -->
      <div
        v-if="railOpen"
        class="rail-resizer"
        role="separator"
        aria-orientation="vertical"
        :title="$t('chat.rail.resize')"
        @pointerdown="onRailResizeStart"
      ></div>
      <div class="chat-main">
        <div class="chat-main-head">
          <button
            type="button"
            class="head-toggle"
            :title="railOpen ? $t('chat.rail.collapse') : $t('chat.rail.expand')"
            :aria-label="railOpen ? $t('chat.rail.collapse') : $t('chat.rail.expand')"
            @click="toggleRail"
          >
            <el-icon :size="16"><Expand v-if="!railOpen" /><Fold v-else /></el-icon>
          </button>
          <span class="head-title">{{ conversationTitle }}</span>
          <button v-if="chatStore.activePeer != null" type="button" class="head-back" @click="chatStore.closeDm()">
            {{ $t('chat.rail.backFamily') }}
          </button>
        </div>
        <ChatMessages />
        <ChatComposer />
        <div class="conn-hint" :class="connected ? 'ok' : 'bad'">
          {{ connected ? $t('chat.connected') : connecting ? $t('chat.connecting') : $t('chat.reconnecting') }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 组装层:对象列收起/展开与宽度(都记本机)、会话标题;消息滚动/发送逻辑分别在 ChatMessages/ChatComposer
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Expand, Fold } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useDevice } from '@/composables/useDevice'
import { SOCKET_STATUS } from '@/composables/useChatSocket'
import ChatContactRail from '@/components/chat/ChatContactRail.vue'
import ChatMessages from '@/components/chat/ChatMessages.vue'
import ChatComposer from '@/components/chat/ChatComposer.vue'

const RAIL_KEY = 'ihomy:chat:rail:v1'
const RAIL_W_KEY = 'ihomy:chat:rail:w:v1'
/** 对象列宽度:最小只剩头像(24px 头像 + 左右内边距),再窄就没意义了 */
const RAIL_MIN = 40
const RAIL_MAX = 280
/** 窄于这个宽度就只留头像,隐藏名字/角标文字 */
const RAIL_NARROW = 104

const { t } = useI18n()
const chatStore = useChatStore()
const { isMobile } = useDevice()
const connected = computed(() => chatStore.socketStatus === SOCKET_STATUS.OPEN)
const connecting = computed(() => chatStore.socketStatus === SOCKET_STATUS.CONNECTING)

const railOpen = ref(true)
try {
  railOpen.value = localStorage.getItem(RAIL_KEY) !== '0'
} catch {}

const bodyEl = ref(null)
const defaultRailWidth = isMobile.value ? 132 : 168
/** 允许的上限:消息区至少留 200px(按聊天面板自身宽度算,浮窗只有 360 宽) */
const railMaxWidth = () => {
  const total = bodyEl.value?.clientWidth || window.innerWidth
  return Math.max(RAIL_MIN, Math.min(RAIL_MAX, total - 200))
}
const railWidth = ref(defaultRailWidth)
try {
  const saved = Number(localStorage.getItem(RAIL_W_KEY))
  if (Number.isFinite(saved) && saved > 0) {
    railWidth.value = Math.max(RAIL_MIN, Math.min(RAIL_MAX, saved))
  }
} catch {}

// 面板宽度是会变的(浮窗可缩放、手机抽屉):挂载后与尺寸变化时都把对象列夹回可用范围
let railObserver = null
onMounted(() => {
  const clamp = () => {
    railWidth.value = Math.max(RAIL_MIN, Math.min(railMaxWidth(), railWidth.value))
  }
  clamp()
  if (typeof ResizeObserver !== 'undefined' && bodyEl.value) {
    railObserver = new ResizeObserver(clamp)
    railObserver.observe(bodyEl.value)
  }
})

const railStyle = computed(() => ({ width: `${Math.round(railWidth.value)}px` }))
const railNarrow = computed(() => railWidth.value < RAIL_NARROW)

const toggleRail = () => {
  railOpen.value = !railOpen.value
}
watch(railOpen, (v) => {
  try {
    localStorage.setItem(RAIL_KEY, v ? '1' : '0')
  } catch {}
})

// ---------- 对象列拖宽(指针事件,触屏也吃;松手才落盘) ----------
let railDrag = null
const detachRailDrag = () => {
  window.removeEventListener('pointermove', onRailResizeMove)
  window.removeEventListener('pointerup', onRailResizeEnd)
  window.removeEventListener('pointercancel', onRailResizeEnd)
  railDrag = null
}
const onRailResizeMove = (e) => {
  if (!railDrag || e.pointerId !== railDrag.id) return
  railWidth.value = Math.max(RAIL_MIN, Math.min(railMaxWidth(), railDrag.width + (e.clientX - railDrag.x)))
}
const onRailResizeEnd = (e) => {
  if (!railDrag || e.pointerId !== railDrag.id) return
  detachRailDrag()
  try {
    localStorage.setItem(RAIL_W_KEY, String(Math.round(railWidth.value)))
  } catch {}
}
const onRailResizeStart = (e) => {
  if (e.isPrimary === false || e.button !== 0) return
  railDrag = { id: e.pointerId, x: e.clientX, width: railWidth.value }
  window.addEventListener('pointermove', onRailResizeMove)
  window.addEventListener('pointerup', onRailResizeEnd)
  window.addEventListener('pointercancel', onRailResizeEnd)
  e.preventDefault()
}
onBeforeUnmount(() => {
  detachRailDrag()
  railObserver?.disconnect()
  railObserver = null
})

// 会话标题:家庭聊天室 / 私聊对方名(打开会话时由对象列传入 store)
const conversationTitle = computed(() => {
  if (chatStore.activePeer == null) return t('chat.roomTitle')
  return chatStore.activePeerName || t('chat.dm.fallbackTitle', { id: chatStore.activePeer })
})
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.chat-panel-body {
  display: flex;
  gap: 6px;
  flex: 1;
  min-height: 0;
}
.chat-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.rail-resizer {
  flex: none;
  width: 8px;
  margin: 0 -4px;
  cursor: col-resize;
  touch-action: none; /* 拖拽把手:触屏不被页面滚动抢走 */
  border-radius: 4px;
  background: transparent;
  transition: background var(--dur-fast) ease;
}
.rail-resizer:hover {
  background: rgba(var(--color-brand-rgb), 0.18);
}
.chat-main-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0 8px;
  border-bottom: 1px solid var(--color-border);
  flex: none;
}
.head-toggle,
.head-back {
  border: none;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  font: inherit;
  display: inline-flex;
  align-items: center;
  padding: 2px 4px;
  border-radius: 6px;
}
.head-toggle:hover,
.head-back:hover {
  color: var(--color-accent);
  background: rgba(var(--color-brand-rgb), 0.08);
}
.head-title {
  font-size: 14px;
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.head-back {
  margin-left: auto;
  font-size: 12px;
}
.conn-hint {
  flex-shrink: 0;
  margin-top: 6px;
  font-size: 12px;
}
.conn-hint.ok {
  color: var(--color-green);
}
.conn-hint.bad {
  color: var(--color-danger);
}
@media (max-width: 768px) {
  .conn-hint {
    font-size: 11px;
    margin-top: 4px;
  }
}
</style>
