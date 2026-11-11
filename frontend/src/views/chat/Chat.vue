<!-- 聊天室页(V3.3→V11.55):家庭房间实时聊天。WS 连接由 stores/chat.js 全局单例持有,
     消息区/输入区组装在 ChatPanel(与全局浮窗共用) -->
<template>
  <div class="page chat-page">
    <Breadcrumb :items="[{ label: $t('chat.title') }]" />
    <div class="chat-box card">
      <div class="chat-title">
        <span class="chat-title-text"
          >{{ $t('chat.roomTitle') }} <span class="online-hint">{{ $t('chat.onlineHint') }}</span></span
        >
        <!-- 浮窗开关:关掉后全局聊天浮窗不再显示;拖到底部删除条关的也是它 -->
        <label class="float-toggle">
          <span class="float-toggle-label">{{ $t('chat.floatToggle') }}</span>
          <el-switch v-model="chatFloatOn" size="small" />
        </label>
      </div>

      <ChatPanel />
    </div>
  </div>
</template>

<script setup>
// 页面壳:声明在看状态、拉历史、补建连接;离开不断开(全局连接,浮窗/未读继续收)
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useFloatsStore } from '@/stores/floats'
import Breadcrumb from '@/components/Breadcrumb.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'

const chatStore = useChatStore()
const floatsStore = useFloatsStore()

/** 聊天浮窗显隐(本机偏好):与浮窗拖到底部删除条共用同一个键 */
const chatFloatOn = computed({
  get: () => floatsStore.chat,
  set: (v) => floatsStore.setChat(v),
})

onMounted(() => {
  chatStore.chatPageActive = true
  // 建连与拉历史互不依赖:先建连,别让历史请求(含续期)拖住 WS;
  // loadHistory 内部吞掉单路失败且会置 hydrated,失败也不会让消息区永久转圈
  chatStore.ensureConnected()
  chatStore.loadHistory({ force: true })
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.float-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-secondary);
  cursor: pointer;
  flex-shrink: 0;
}
.online-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
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
