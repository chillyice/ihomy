<!-- 聊天面板:消息区 + 输入区 + 连接提示,/chat 页与全局浮窗(ChatFloat)共用。
     数据与连接都在 stores/chat.js,这里只组装 -->
<template>
  <div class="chat-panel">
    <ChatMessages />
    <ChatComposer />
    <div class="conn-hint" :class="connected ? 'ok' : 'bad'">
      {{ connected ? $t('chat.connected') : connecting ? $t('chat.connecting') : $t('chat.reconnecting') }}
    </div>
  </div>
</template>

<script setup>
// 组装层:消息滚动/发送逻辑分别在 ChatMessages/ChatComposer
import { computed } from 'vue'
import { useChatStore } from '@/stores/chat'
import { SOCKET_STATUS } from '@/composables/useChatSocket'
import ChatMessages from '@/components/chat/ChatMessages.vue'
import ChatComposer from '@/components/chat/ChatComposer.vue'

const chatStore = useChatStore()
const connected = computed(() => chatStore.socketStatus === SOCKET_STATUS.OPEN)
const connecting = computed(() => chatStore.socketStatus === SOCKET_STATUS.CONNECTING)
</script>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.conn-hint {
  flex-shrink: 0;
  margin-top: 6px;
  font-size: 12px;
}
.conn-hint.ok {
  color: #67c23a;
}
.conn-hint.bad {
  color: #f56c6c;
}
@media (max-width: 768px) {
  .conn-hint {
    font-size: 11px;
    margin-top: 4px;
  }
}
</style>
