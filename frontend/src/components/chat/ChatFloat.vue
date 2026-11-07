<!-- 全局聊天浮窗:收起=可拖拽悬浮球(未读角标),展开=桌面可拖拽缩放面板 / 移动端半屏抽屉。
     内部自守卫(登录且非独立页,ChangePasswordDialog 模式);球位置记忆键 ihomy:chat:float:v1。
     沉浸页(放映厅/GBA/游戏页)只显示收起球,分享注入不自动展开(拍板 #2)。 -->
<template>
  <template v-if="show">
    <!-- 移动端:悬浮球 + 半屏抽屉(拍板 #3) -->
    <template v-if="isMobile">
      <div v-if="expanded" class="chat-float-sheet">
        <div class="sheet-head">
          <span class="sheet-title">{{ $t('chat.roomTitle') }}</span>
          <el-button text circle size="small" @click="collapse">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <ChatPanel />
      </div>
      <button v-else type="button" class="chat-float-ball mobile-ball" @pointerdown="onBallDown" @pointerup="onBallUp">
        <el-badge :value="chatStore.unread" :hidden="!chatStore.unread" :max="99" class="ball-badge">
          <el-icon :size="22"><ChatDotRound /></el-icon>
        </el-badge>
      </button>
    </template>

    <!-- 桌面端:可拖拽缩放面板 + 可拖拽球 -->
    <template v-else>
      <div v-if="expanded" class="chat-float-panel" :style="panelStyle">
        <div class="panel-head" @pointerdown="onDragStart">
          <span class="panel-title">{{ $t('chat.roomTitle') }}</span>
          <el-button text circle size="small" @click="collapse">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <ChatPanel />
        <div class="resize-handle" @pointerdown="onResizeStart"></div>
      </div>
      <button v-else type="button" class="chat-float-ball" :style="ballStyle" @pointerdown="onBallDown" @pointerup="onBallUp">
        <el-badge :value="chatStore.unread" :hidden="!chatStore.unread" :max="99" class="ball-badge">
          <el-icon :size="22"><ChatDotRound /></el-icon>
        </el-badge>
      </button>
    </template>
  </template>
</template>

<script setup>
// 浮窗:连接生命周期在 stores/chat.js;球位置 useDragResize(Pointer,位置记忆键登记在 LightSettings 清理清单)
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useDevice } from '@/composables/useDevice'
import { useDragResize } from '@/utils/useDragResize'
import ChatPanel from '@/components/chat/ChatPanel.vue'

const FLOAT_POS_KEY = 'ihomy:chat:float:v1'
const BALL_SIZE = 48
const PANEL_W = 360
const PANEL_H = 520

const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()
const { isMobile } = useDevice()

const expanded = ref(false)

/** 登录且非独立页才挂载(独立页自带配色接管,不套外壳) */
const show = computed(() => userStore.isLoggedIn && !route.meta.standalone)

/** 沉浸页(游戏/GBA):只显示收起球,不自动展开(拍板 #2) */
const immersiveRoute = computed(() => route.path.startsWith('/games') || route.path.startsWith('/tools/gba'))

// 球位置(持久化);面板位置不持久化,每次展开落右侧
const ball = useDragResize({
  x: Math.max(16, window.innerWidth - 84),
  y: Math.max(80, window.innerHeight - 200),
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
  storageKey: '',
})

const ballStyle = computed(() => ({ left: `${ball.pos.value.x}px`, top: `${ball.pos.value.y}px`, zIndex: 'var(--z-float)' }))
const panelStyle = computed(() => ({
  left: `${panelPos.value.x}px`,
  top: `${panelPos.value.y}px`,
  width: `${panelSize.value.w}px`,
  height: `${panelSize.value.h}px`,
  zIndex: 'var(--z-float)',
}))

// 球:按下记起点,抬起位移 <6px 视为点击展开(否则是拖拽)
let ballDown = null
const onBallDown = (e) => {
  ballDown = { x: e.clientX, y: e.clientY }
  ball.onDragStart(e)
}
const onBallUp = (e) => {
  if (ballDown && Math.hypot(e.clientX - ballDown.x, e.clientY - ballDown.y) < 6) expand()
  ballDown = null
}

const expand = () => {
  expanded.value = true
}
const collapse = () => {
  expanded.value = false
}

// 展开=在看(推进已读),收起=交还未读计数;/chat 页经由 chatPageActive 与这里叠加
watch(expanded, (v) => {
  chatStore.floatOpen = v
})
watch(show, (v) => {
  if (!v) collapse()
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
.mobile-ball {
  right: 16px;
  bottom: 68px; /* 避开 56px 底栏(MusicPlayer 先例) */
  left: auto;
  top: auto;
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
.resize-handle {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 18px;
  height: 18px;
  cursor: nwse-resize;
  touch-action: none; /* 缩放把手 */
}
.resize-handle::after {
  content: '';
  position: absolute;
  left: 4px;
  bottom: 4px;
  width: 8px;
  height: 8px;
  border-left: 2px solid var(--color-border, rgba(128, 128, 128, 0.4));
  border-bottom: 2px solid var(--color-border, rgba(128, 128, 128, 0.4));
}

/* 移动端半屏抽屉 */
.chat-float-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 52vh;
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
</style>
