<!-- 全局聊天浮窗:收起=可拖拽悬浮球(未读角标),展开=桌面可拖拽缩放面板 / 移动端半屏抽屉。
     内部自守卫(登录且非独立页,ChangePasswordDialog 模式);球位置记忆键 ihomy:chat:float:v1。
     沉浸页(放映厅/GBA/游戏页)只显示收起球,分享注入不自动展开(拍板 #2)。
     桌面端可拖到窗口底部的删除区松手隐藏(stores/floats.js 开关),聊天室页可重新开启。 -->
<template>
  <template v-if="show">
    <template v-if="expanded">
      <!-- 移动端:半屏抽屉 -->
      <div v-if="isMobile" class="chat-float-sheet">
        <div class="sheet-head">
          <span class="sheet-title">{{ $t('chat.title') }}</span>
          <el-button text circle size="small" @click="collapse">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <ChatPanel />
      </div>

      <!-- 桌面端:可拖拽缩放面板 -->
      <div v-else class="chat-float-panel" :style="panelStyle">
        <div class="panel-head" @pointerdown="onPanelDown">
          <span class="panel-title">{{ $t('chat.title') }}</span>
          <el-button text circle size="small" @click="collapse">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <ChatPanel />
        <!-- 四角和四边的透明缩放区 -->
        <div
          v-for="dir in RESIZE_DIRS"
          :key="dir"
          class="chat-resize-zone"
          :class="'dir-' + dir"
          @pointerdown="onResizeStart($event, dir)"
        ></div>
      </div>
    </template>
    <button
      v-else
      type="button"
      class="chat-float-ball"
      :style="ballStyle"
      :aria-label="$t('chat.title')"
      @pointerdown="onBallDown"
      @pointerup="onBallUp"
      @click="onBallClick"
    >
      <el-badge :value="chatStore.totalUnread" :hidden="!chatStore.totalUnread" :max="99" class="ball-badge">
        <el-icon :size="22"><ChatDotRound /></el-icon>
      </el-badge>
    </button>

    <!-- 拖到底部删除区:桌面端拖球/拖面板时出现,松手即隐藏浮窗(可在聊天室重新开启) -->
    <FloatDeleteDock :active="dockActive" :over="dockOver" />

    <!-- 新消息横幅:浮窗收起/未在看聊天时来消息,给一条看得见的提示(不只是声音/角标) -->
    <Transition name="chat-toast">
      <button v-if="notifyMsg" type="button" class="chat-toast" @click="openFromToast">
        <span class="chat-toast-avatar">{{ notifyMsg.senderName?.charAt(0) }}</span>
        <span class="chat-toast-body">
          <b class="chat-toast-name">{{ notifyMsg.senderName }}</b>
          <span class="chat-toast-text">{{ toastPreview }}</span>
        </span>
        <span class="chat-toast-open">{{ $t('chat.toastOpen') }}</span>
      </button>
    </Transition>
  </template>
</template>

<script setup>
// 浮窗:连接生命周期在 stores/chat.js;球位置 useDragResize(Pointer,位置记忆键登记在 LightSettings 清理清单)
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useFloatsStore } from '@/stores/floats'
import { useDevice } from '@/composables/useDevice'
import { useDragResize } from '@/utils/useDragResize'
import { useFloatDelete, FLOAT_DELETE_ZONE } from '@/composables/useFloatDelete'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import FloatDeleteDock from '@/components/ui/FloatDeleteDock.vue'

const FLOAT_POS_KEY = 'ihomy:chat:float:v1'
const PANEL_POS_KEY = 'ihomy:chat:panel:v1'
const BALL_SIZE = 48
const PANEL_W = 360
const PANEL_H = 520
const RESIZE_DIRS = ['n', 's', 'e', 'w', 'ne', 'nw', 'se', 'sw']

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()
const floatsStore = useFloatsStore()
const { isMobile } = useDevice()

const expanded = ref(false)

/** 登录且非独立页才挂载(独立页自带配色接管,不套外壳);聊天室页开关可隐藏 */
const show = computed(() => userStore.isLoggedIn && !route.meta.standalone && floatsStore.chat)

/** 沉浸页(游戏/GBA):只显示收起球,不自动展开(拍板 #2) */
const immersiveRoute = computed(() => route.path.startsWith('/games') || route.path.startsWith('/tools/gba'))

// 球位置(持久化);面板位置/尺寸也持久化(四角四边缩放后的窗口形态跟着走)
const ball = useDragResize({
  x: Math.max(16, window.innerWidth - (isMobile.value ? BALL_SIZE + 16 : 84)),
  y: Math.max(80, window.innerHeight - (isMobile.value ? BALL_SIZE + 68 : 200)),
  w: BALL_SIZE,
  h: BALL_SIZE,
  storageKey: FLOAT_POS_KEY,
})
const {
  pos: panelPos,
  size: panelSize,
  onDragStart,
  onResizeStart,
} = useDragResize({
  x: Math.max(16, window.innerWidth - PANEL_W - 24),
  y: 84,
  w: PANEL_W,
  h: PANEL_H,
  storageKey: PANEL_POS_KEY,
})

const ballStyle = computed(() => ({ left: `${ball.pos.value.x}px`, top: `${ball.pos.value.y}px`, zIndex: 'var(--z-float)' }))
const panelStyle = computed(() => ({
  left: `${panelPos.value.x}px`,
  top: `${panelPos.value.y}px`,
  width: `${panelSize.value.w}px`,
  height: `${panelSize.value.h}px`,
  zIndex: 'var(--z-float)',
}))

// 松手时记录拖动状态，拦住随拖动产生的 click，同时保留键盘激活。
let ignoreBallClick = false
const onBallDown = (e) => {
  if (!ball.onDragStart(e)) return
  ignoreBallClick = false
  trackToDock(e)
}
const onBallUp = (e) => {
  if (e.isPrimary && e.button === 0) ignoreBallClick = ball.dragging.value
}
const onBallClick = (e) => {
  if (e.detail === 0 || !ignoreBallClick) expand()
  ignoreBallClick = false
}

// 删除区与拖动判定各算各的:浮球/标题栏自身的位移阈值(6px)就是删除条的开关,
// 不依赖 useDragResize 的内部状态(它只负责挪位置),这样点一下球也不会误删。
// 手机端悬浮球本来就贴底,删除热区收窄,免得随手一拖就没了。
const DOCK_MIN_MOVE = 6
const MOBILE_DELETE_ZONE = 64
const {
  active: dockActive,
  over: dockOver,
  begin: dockBegin,
  update: dockUpdate,
  end: dockEnd,
  cancel: dockCancel,
} = useFloatDelete({ deleteZone: isMobile.value ? MOBILE_DELETE_ZONE : FLOAT_DELETE_ZONE })
let detachTrack = null
const trackToDock = (e) => {
  detachTrack?.()
  const id = e.pointerId
  const origin = { x: e.clientX, y: e.clientY }
  let moved = false
  const onMove = (ev) => {
    if (ev.pointerId !== id) return
    if (!moved) {
      if (Math.hypot(ev.clientX - origin.x, ev.clientY - origin.y) < DOCK_MIN_MOVE) return
      moved = true
      dockBegin()
    }
    dockUpdate(ev.clientY)
  }
  const onUp = (ev) => {
    if (ev.pointerId !== id) return
    detach()
    if (!moved) {
      dockCancel()
      return
    }
    dockUpdate(ev.clientY)
    if (dockEnd()) removeFloat()
  }
  const onCancel = (ev) => {
    if (ev.pointerId !== id) return
    detach()
    dockCancel()
  }
  const detach = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onCancel)
    detachTrack = null
  }
  detachTrack = detach
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
  window.addEventListener('pointercancel', onCancel)
}

// 面板只能从标题栏拖;拖动与 useDragResize 共用同一次 pointerdown
const onPanelDown = (e) => {
  if (onDragStart(e)) trackToDock(e)
}

const removeFloat = () => {
  floatsStore.setChat(false)
  expanded.value = false
  ball.reset()
  clearToast()
  ElMessage.info(t('chat.floatRemoved'))
}

const expand = () => {
  // 浮窗与 /chat 页共用 stores/chat.js 的缓存:首次展开必须自己拉历史,
  // 否则消息区只会保留「加载中」转圈,等 /chat 页那样拉过一次才停
  chatStore.loadHistory({ force: true })
  expanded.value = true
  clearToast()
}
const collapse = () => {
  expanded.value = false
}

// ---------- 新消息横幅 ----------
// 浮窗收起或不在聊天界面时来消息,除了角标/系统提示音,再给一条 6 秒的可见横幅。
const notifyMsg = ref(null)
let notifyTimer = null
const toastPreview = computed(() => {
  const m = notifyMsg.value
  if (!m) return ''
  if (m.msgType && m.msgType !== 'TEXT') return t('chat.toastRich')
  return String(m.content || '').slice(0, 60)
})
const clearToast = () => {
  notifyMsg.value = null
  if (notifyTimer) {
    clearTimeout(notifyTimer)
    notifyTimer = null
  }
}
const openFromToast = () => {
  clearToast()
  expand()
}
watch(
  () => chatStore.lastIncoming,
  (m) => {
    if (!m || !show.value) return
    if (m.senderId === userStore.userInfo?.id) return
    if (chatStore.viewing) return
    notifyMsg.value = m
    if (notifyTimer) clearTimeout(notifyTimer)
    notifyTimer = setTimeout(clearToast, 6000)
  },
)

// 展开=在看(推进已读),收起=交还未读计数;/chat 页经由 chatPageActive 与这里叠加
watch(expanded, (v) => {
  chatStore.floatOpen = v
})
watch(show, (v) => {
  if (!v) {
    collapse()
    clearToast()
  }
})

// 登录后全局建连(浮窗挂载即代表外壳可用);登出收起
watch(
  () => userStore.isLoggedIn,
  (v) => {
    if (v) chatStore.ensureConnected()
    else collapse()
  },
  { immediate: true },
)

// 分享注入:自动展开预填(沉浸页不自动展开,拍板 #2)
watch(
  () => chatStore.shareCard,
  (c) => {
    if (c && !immersiveRoute.value) expand()
  },
)

onMounted(() => {
  chatStore.ensureConnected()
})

// 手机端抽屉打开时给 <html> 挂个类:内容页按抽屉高度留出下边距,滚到底能看全(样式在 main.css)
const sheetActive = computed(() => show.value && expanded.value && isMobile.value)
watch(
  sheetActive,
  (v) => {
    if (typeof document === 'undefined') return
    document.documentElement.classList.toggle('chat-sheet-open', v)
  },
  { immediate: true },
)

// 卸载兜底:拖动中被隐藏/路由切走时不残留窗口级监听与删除条
onBeforeUnmount(() => {
  detachTrack?.()
  dockCancel()
  if (notifyTimer) clearTimeout(notifyTimer)
  if (typeof document !== 'undefined') document.documentElement.classList.remove('chat-sheet-open')
})
</script>

<style scoped>
.chat-float-ball {
  position: fixed;
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-brand-text);
  background: rgba(var(--color-brand-rgb), 0.92);
  box-shadow: var(--shadow);
  touch-action: none; /* 拖拽把手:触屏不被页面滚动抢走 */
  transition: transform var(--dur-fast) var(--ease-soft);
}
.chat-float-ball:hover {
  transform: translateY(-2px);
}
.ball-badge :deep(.el-badge__content) {
  z-index: 1;
}

.chat-float-panel {
  position: fixed;
  display: flex;
  flex-direction: column;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-hover);
  overflow: hidden;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px 8px 14px;
  cursor: grab;
  user-select: none;
  touch-action: none; /* 拖拽把手 */
  border-bottom: 1px solid var(--color-border);
}
.panel-head:active {
  cursor: grabbing;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
}
.chat-float-panel :deep(.chat-panel) {
  flex: 1;
  min-height: 0;
  padding: 6px 12px 10px;
}
.chat-resize-zone {
  position: absolute;
  touch-action: none; /* 缩放把手 */
  z-index: 1;
}
/* 四角 */
.chat-resize-zone.dir-se {
  right: 0;
  bottom: 0;
  width: 16px;
  height: 16px;
  cursor: nwse-resize;
}
.chat-resize-zone.dir-sw {
  left: 0;
  bottom: 0;
  width: 16px;
  height: 16px;
  cursor: nesw-resize;
}
.chat-resize-zone.dir-ne {
  right: 0;
  top: 0;
  width: 16px;
  height: 16px;
  cursor: nesw-resize;
}
.chat-resize-zone.dir-nw {
  left: 0;
  top: 0;
  width: 16px;
  height: 16px;
  cursor: nwse-resize;
}
/* 四边的热区贴面板内缘 */
.chat-resize-zone.dir-n {
  left: 12px;
  right: 12px;
  top: 0;
  height: 6px;
  cursor: ns-resize;
}
.chat-resize-zone.dir-s {
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 6px;
  cursor: ns-resize;
}
.chat-resize-zone.dir-e {
  top: 12px;
  bottom: 12px;
  right: 0;
  width: 6px;
  cursor: ew-resize;
}
.chat-resize-zone.dir-w {
  top: 12px;
  bottom: 12px;
  left: 0;
  width: 6px;
  cursor: ew-resize;
}

/* 移动端半屏抽屉 */
.chat-float-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: var(--chat-sheet-h, 52vh);
  display: flex;
  flex-direction: column;
  background: var(--color-card);
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.18);
  z-index: var(--z-float);
  padding: 0 12px 8px;
}
.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px 6px;
  flex-shrink: 0;
}
.sheet-title {
  font-size: 14px;
  font-weight: 600;
}
.chat-float-sheet :deep(.chat-panel) {
  flex: 1;
  min-height: 0;
}

/* 新消息横幅:桌面右上、移动端顶部,不遮输入区;点一下直接展开浮窗 */
.chat-toast {
  position: fixed;
  right: 18px;
  top: 84px;
  z-index: var(--z-float);
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(360px, calc(100vw - 36px));
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-card);
  box-shadow: var(--shadow-hover);
  text-align: left;
  cursor: pointer;
  font: inherit;
  color: inherit;
}
.chat-toast-avatar {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: var(--color-brand-text);
  background: rgba(var(--color-brand-rgb), 0.9);
}
.chat-toast-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.chat-toast-name {
  font-size: 13px;
}
.chat-toast-text {
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-toast-open {
  flex: none;
  font-size: 12px;
  color: var(--color-accent);
}
.chat-toast-enter-active,
.chat-toast-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.chat-toast-enter-from,
.chat-toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
@media (max-width: 768px) {
  .chat-toast {
    right: 12px;
    left: 12px;
    top: calc(12px + env(safe-area-inset-top, 0px));
    max-width: none;
  }
}
</style>
