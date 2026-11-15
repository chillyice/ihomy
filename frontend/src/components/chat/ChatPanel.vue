<!-- 聊天面板:左侧聊天对象列(可收起)+ 会话头 + 消息区 + 输入区 + 连接提示,/chat 页与全局浮窗(ChatFloat)共用。
     数据与连接都在 stores/chat.js,这里只组装 -->
<template>
  <div class="chat-panel">
    <div class="chat-panel-body">
      <ChatContactRail v-if="railOpen" />
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
// 组装层:对象列收起/展开(状态记本机)、会话标题;消息滚动/发送逻辑分别在 ChatMessages/ChatComposer
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Expand, Fold } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { SOCKET_STATUS } from '@/composables/useChatSocket'
import ChatContactRail from '@/components/chat/ChatContactRail.vue'
import ChatMessages from '@/components/chat/ChatMessages.vue'
import ChatComposer from '@/components/chat/ChatComposer.vue'

const RAIL_KEY = 'ihomy:chat:rail:v1'

const { t } = useI18n()
const chatStore = useChatStore()
const connected = computed(() => chatStore.socketStatus === SOCKET_STATUS.OPEN)
const connecting = computed(() => chatStore.socketStatus === SOCKET_STATUS.CONNECTING)

const railOpen = ref(true)
try {
  railOpen.value = localStorage.getItem(RAIL_KEY) !== '0'
} catch {}
const toggleRail = () => {
  railOpen.value = !railOpen.value
}
watch(railOpen, (v) => {
  try {
    localStorage.setItem(RAIL_KEY, v ? '1' : '0')
  } catch {}
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
