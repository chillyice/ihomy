<!-- 聊天室页(V3.3→V11.55):家庭房间实时聊天。WS 连接由 stores/chat.js 全局单例持有,
     消息区/输入区组装在 ChatPanel(与全局浮窗共用) -->
<template>
  <div class="page chat-page">
    <Breadcrumb v-if="theme.theme !== 'window'" :items="[{ label: $t('chat.title') }]" />
    <div
      ref="chatBox"
      class="chat-box card"
      :class="{ 'keyboard-open': !!keyboardFrame, 'has-chat-float': chatFloatOn }"
      :style="keyboardFrame"
    >
      <header v-if="theme.theme === 'window'" class="room-heading">
        <div>
          <small>{{ user.userInfo?.nickname }}</small>
          <h2>{{ $t(route.query.target === 'assistant' ? 'chat.assistant.title' : 'chat.title') }}</h2>
        </div>
        <label class="float-toggle">{{ $t('chat.floatToggle') }} <el-switch v-model="chatFloatOn" /></label>
      </header>
      <div v-else class="chat-title">
        <span class="chat-title-text"
          >{{ $t('chat.title') }} <span class="online-hint">{{ $t('chat.onlineHint') }}</span></span
        >
        <!-- 浮窗开关:关掉后全局聊天浮窗不再显示;拖到底部删除条关的也是它 -->
        <label class="float-toggle">
          <span class="float-toggle-label">{{ $t('chat.floatToggle') }}</span>
          <el-switch v-model="chatFloatOn" size="small" />
        </label>
      </div>

      <div v-if="theme.theme === 'window' && route.query.target === 'assistant' && chatStore.activeKey === 'ai'" class="assistant-desk">
        <aside>
          <router-link to="/chat">{{ $t('windowTools.contacts') }}</router-link
          ><strong>{{ $t('windowTools.privateChat') }}</strong> <router-link to="/plan">{{ $t('windowTools.scheduleLink') }}</router-link
          ><router-link to="/item?focus=search">{{ $t('windowTools.find') }}</router-link>
        </aside>
        <section><AssistantConversation /></section>
      </div>
      <ChatContactRail v-else-if="showContacts" :key="familyScope" class="window-contacts" @select="selected" />
      <div v-else-if="theme.theme === 'window' && !singlePane" class="family-inbox">
        <aside>
          <h3>{{ $t('windowTools.contacts') }}</h3>
          <ChatContactRail :key="familyScope" @select="selected" />
        </aside>
        <ChatPanel single-pane @contacts="focusContacts" @select="selected" />
      </div>
      <ChatPanel v-else :single-pane="singlePane" @contacts="contacts" @select="selected" />
    </div>
  </div>
</template>

<script setup>
// 页面壳:声明在看状态、拉历史、补建连接;离开不断开(全局连接,浮窗/未读继续收)
import { computed, ref, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { useFloatsStore } from '@/stores/floats'
import Breadcrumb from '@/components/Breadcrumb.vue'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import AssistantConversation from '@/components/chat/AssistantConversation.vue'
import ChatContactRail from '@/components/chat/ChatContactRail.vue'

const chatStore = useChatStore()
const floatsStore = useFloatsStore()
const route = useRoute()
const router = useRouter()
const user = useUserStore()
const theme = useThemeStore()
const { isWide } = useDevice()
const singlePane = computed(() => theme.theme === 'window' && !isWide.value)
const familyScope = computed(() => [user.userInfo?.id, user.userInfo?.familyId].join('/'))
const showContacts = computed(() => singlePane.value && !['assistant', 'conversation'].includes(route.query.target))
const chatBox = ref(null)
const keyboardFrame = ref(null)
let restingHeight = 0
let restingWidth = 0
function fitKeyboard() {
  const viewport = window.visualViewport
  const height = viewport?.height || window.innerHeight
  const width = window.innerWidth
  const editing =
    chatBox.value?.contains(document.activeElement) &&
    document.activeElement.matches('textarea,input:not([type="checkbox"]):not([type="range"])')
  if (width !== restingWidth || (!editing && !keyboardFrame.value)) {
    restingWidth = width
    restingHeight = window.innerHeight
  }
  const reduced = Math.max(restingHeight, window.innerHeight) - height > 90
  const open = singlePane.value && (editing || keyboardFrame.value) && reduced && (!viewport || viewport.scale === 1)
  keyboardFrame.value = open ? { '--keyboard-top': (viewport?.offsetTop || 0) + 'px', '--keyboard-height': height + 'px' } : null
  document.documentElement.classList.toggle('window-chat-keyboard', !!open)
}
function selected() {
  if (theme.theme !== 'window') return
  const target = chatStore.activeKey === 'ai' ? 'assistant' : 'conversation'
  if (showContacts.value) chatStore.chatPageActive = true
  if (chatStore.activeKey == null && chatStore.unread) chatStore.loadHistory({ force: true, markRead: true })
  if (route.query.target === target) return
  router[showContacts.value ? 'push' : 'replace']({ path: '/chat', query: { target } })
}
function contacts() {
  if (window.history.state?.back === '/chat') router.back()
  else router.replace({ path: '/chat' })
}
function focusContacts() {
  chatBox.value?.querySelector('.family-inbox aside button')?.focus()
}
watch(
  () => [route.query.target, familyScope.value],
  ([target]) => {
    if (target === 'assistant' && chatStore.activeKey !== 'ai') chatStore.openAssistant()
    else if (target !== 'assistant' && chatStore.activeKey === 'ai' && theme.theme === 'window') chatStore.closeDm()
  },
  { immediate: true },
)
watch(showContacts, (show) => {
  chatStore.chatPageActive = !show
})

/** 聊天浮窗显隐(本机偏好):与浮窗拖到底部删除条共用同一个键 */
const chatFloatOn = computed({
  get: () => floatsStore.chat,
  set: (v) => floatsStore.setChat(v),
})

onMounted(() => {
  fitKeyboard()
  window.addEventListener('resize', fitKeyboard)
  window.visualViewport?.addEventListener('resize', fitKeyboard)
  window.visualViewport?.addEventListener('scroll', fitKeyboard)
  document.addEventListener('focusin', fitKeyboard)
  chatStore.chatPageActive = !showContacts.value
  // 建连与拉历史互不依赖:先建连,别让历史请求(含续期)拖住 WS;
  // loadHistory 内部吞掉单路失败且会置 hydrated,失败也不会让消息区永久转圈
  chatStore.ensureConnected()
  chatStore.loadHistory({ force: true, markRead: chatStore.viewing && chatStore.activeKey == null })
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', fitKeyboard)
  window.visualViewport?.removeEventListener('resize', fitKeyboard)
  window.visualViewport?.removeEventListener('scroll', fitKeyboard)
  document.removeEventListener('focusin', fitKeyboard)
  document.documentElement.classList.remove('window-chat-keyboard')
  chatStore.chatPageActive = false
})
</script>

<style scoped>
:global(html.window-chat-keyboard .mobile-tabbar),
:global(html.window-chat-keyboard .window-player),
:global(html.window-chat-keyboard .chat-float-ball) {
  visibility: hidden;
}
:global(html.theme-window .chat-box.keyboard-open) {
  --keyboard-top: 0px;
  --keyboard-height: 100dvh;
  position: fixed;
  inset: var(--keyboard-top) 0 auto;
  box-sizing: border-box;
  height: var(--keyboard-height);
  min-height: 0;
  border-radius: 0;
  padding: 6px max(10px, env(safe-area-inset-right)) 6px max(10px, env(safe-area-inset-left));
  z-index: var(--z-overlay);
}
.keyboard-open .chat-title {
  display: none;
}
@media (orientation: landscape) {
  .keyboard-open :deep(.composer-toolbar) {
    display: none;
  }
}
.window-contacts {
  width: 100%;
  min-height: 0;
  flex: 1;
}
:global(html.theme-window .chat-box) {
  min-height: min(320px, 50dvh);
  height: min(740px, calc(100dvh - 180px));
}
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

/* 通信簿的布封、索引签和信纸只属于整页聊天室；浮窗也会复用里面的组件。 */
html.theme-warm .chat-box {
  --chat-inset: clamp(8px, 1.4vw, 18px);
  padding: 0;
  background-color: var(--color-card);
  background-image: var(--caption-paper);
  background-size: auto, var(--home-grain-size);
  border-color: var(--mat-edge);
  border-radius: 12px;
}
html.theme-warm .chat-box:hover {
  transform: none;
  box-shadow: var(--shadow);
}
html.theme-warm .chat-title {
  margin: 0;
  padding: 8px var(--chat-inset);
  min-height: 48px;
  flex: none;
  border-radius: 11px 11px 0 0;
  background: var(--album-cloth-cover);
  background-blend-mode: soft-light;
  color: var(--album-cloth-ink);
  box-shadow: inset 0 -3px rgb(34 38 26 / 24%);
}
html.theme-warm .chat-title-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
}
html.theme-warm .chat-title-text::before {
  content: '';
  width: 52px;
  height: 36px;
  flex: none;
  background: url('@/assets/windowdesk/envelope.webp') center / contain no-repeat;
}
html.theme-warm .chat-title :is(.online-hint, .float-toggle) {
  color: inherit;
}
html.theme-warm .chat-page :deep(.chat-rail) {
  padding-top: 8px;
  background-color: var(--color-card-2);
  border-radius: 0 0 0 11px;
}
html.theme-warm .chat-page :deep(.rail-item.active) {
  color: var(--color-text);
  background-color: var(--color-card);
  background-image: var(--caption-paper);
  box-shadow: inset 3px 0 var(--color-accent);
  border-radius: 0 8px 8px 0;
}
html.theme-warm .chat-page :deep(.chat-main-head),
html.theme-warm .chat-page :deep(.chat-composer),
html.theme-warm .chat-page :deep(.assistant-compose) {
  padding: 8px var(--chat-inset);
}
html.theme-warm .chat-page :deep(.chat-composer) {
  border-top: 1px solid var(--mat-edge);
}
html.theme-warm .chat-page :deep(.msg-list),
html.theme-warm .chat-page :deep(.assistant-stream) {
  padding: 12px var(--chat-inset);
  background: repeating-linear-gradient(transparent 0 27px, var(--color-line) 27px 28px);
}
html.theme-warm .chat-page :deep(.msg-bubble:not(.is-card)),
html.theme-warm .chat-page :deep(.assistant-bubble) {
  border: 1px solid var(--mat-edge);
  border-radius: 2px 12px 12px;
  background: var(--color-card);
}
html.theme-warm .chat-page :deep(.mine .msg-bubble:not(.is-card)),
html.theme-warm .chat-page :deep(.assistant-bubble.mine) {
  border-radius: 12px 2px 12px 12px;
  background: var(--color-card-2);
}
@media (max-width: 768px) {
  /* 在会话和联系人下方留一整行写信，展开或拖宽联系人时也能点到每个工具。 */
  html.theme-warm .chat-page :deep(.chat-panel-body:has(.chat-composer)) {
    display: grid;
    grid-template-columns: auto minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr) auto;
  }
  html.theme-warm .chat-page :deep(.chat-main:has(.chat-composer)) {
    display: contents;
  }
  html.theme-warm .chat-page :deep(.chat-panel-body:has(.chat-composer) .rail-slot) {
    grid-row: 1 / 3;
  }
  html.theme-warm .chat-page :deep(.chat-composer) {
    grid-column: 1 / -1;
  }
  html.theme-warm .chat-page :deep(.composer-toolbar) {
    flex-wrap: wrap;
  }
  html.theme-warm .has-chat-float :deep(.msg-input) {
    padding-inline-end: 60px;
  }
}
</style>
