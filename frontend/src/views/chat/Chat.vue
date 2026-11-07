<!-- 聊天室页(V3.3→V11.55):家庭房间实时聊天。WS 连接由 stores/chat.js 全局单例持有,
     消息区/输入区组装在 ChatPanel(与全局浮窗共用) -->
<template>
  <div class="page chat-page">
    <Breadcrumb :items="[{ label: $t('chat.title') }]" />
    <div class="chat-box card">
      <div class="chat-title">
        {{ $t('chat.roomTitle') }} <span class="online-hint">{{ $t('chat.onlineHint') }}</span>
      </div>

      <ChatPanel />
    </div>
  </div>
</template>

<script setup>
// 页面壳:声明在看状态、拉历史、补建连接;离开不断开(全局连接,浮窗/未读继续收)
import { onBeforeUnmount, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import Breadcrumb from '@/components/Breadcrumb.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'

const chatStore = useChatStore()

onMounted(async () => {
  chatStore.chatPageActive = true
  await chatStore.loadHistory()
  chatStore.ensureConnected()
})
onBeforeUnmount(() => {
  chatStore.chatPageActive = false
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
}
</style>
