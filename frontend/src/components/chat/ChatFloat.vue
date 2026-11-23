<!-- 全局聊天浮窗:收起=可拖拽悬浮球(未读角标),展开=桌面可拖拽缩放面板 / 移动端半屏抽屉。
     内部自守卫(登录且非独立页,ChangePasswordDialog 模式);球位置记忆键 ihomy:chat:float:v1。
     沉浸页(放映厅/GBA/游戏页)只显示收起球,分享注入不自动展开(拍板 #2)。
     桌面端可拖到窗口底部的删除区松手隐藏(stores/floats.js 开关),聊天室页可重新开启。 -->
<template>
  <template v-if="show">
    <template v-if="expanded">
      <!-- 移动端:半屏抽屉(顶部把手可上下拖动改高度,往上拖即放大聊天框) -->
      <div v-if="isMobile" ref="sheetEl" class="chat-float-sheet">
        <div
          class="sheet-grip"
          role="separator"
          aria-orientation="horizontal"
          :aria-label="$t('chat.sheet.resize')"
          :title="$t('chat.sheet.resize')"
          @pointerdown="onSheetGripDown"
        >
          <span class="sheet-grip-bar" aria-hidden="true"></span>
        </div>
        <div class="sheet-head paper-heading">
          <!-- 在「一起玩」房间里多一个房间页:同一扇浮窗承载两路会话,不再单独起房间聊天浮球 -->
          <div v-if="roomActive" class="float-tabs" role="tablist">
            <button
              type="button"
              class="float-tab"
              :class="{ active: tab === 'family' }"
              role="tab"
              :aria-selected="tab === 'family'"
              @click="tab = 'family'"
            >
              {{ $t('chat.title') }}
              <span v-if="chatStore.totalUnread" class="float-tab-badge">{{ badgeText(chatStore.totalUnread) }}</span>
            </button>
            <button
              type="button"
              class="float-tab"
              :class="{ active: tab === 'room' }"
              role="tab"
              :aria-selected="tab === 'room'"
              @click="tab = 'room'"
            >
              {{ $t('games.roomChat.title') }}
              <span v-if="roomUnread" class="float-tab-badge">{{ badgeText(roomUnread) }}</span>
            </button>
          </div>
          <span v-else class="sheet-title">{{ $t('chat.title') }}</span>
          <span class="float-head-actions">
            <!-- 房间里通话就在聊天里:直接拉房间成员语音,不必再回大厅找人 -->
            <RoomCallPicker
              v-if="roomActive && tab === 'room'"
              :members="roomChatStore.roomMeta?.members || []"
              :my-id="userStore.userInfo?.id"
            />
            <el-button text circle size="small" @click="collapse">
              <el-icon><Close /></el-icon>
            </el-button>
          </span>
        </div>
        <ChatPanel v-if="tab === 'family'" />
        <RoomChatPanel v-else />
      </div>

      <!-- 桌面端:可拖拽缩放面板 -->
      <div v-else class="chat-float-panel" :class="{ pinned: pinnedRoom }" :style="panelStyle">
        <div class="panel-head paper-heading" @pointerdown="onPanelDown">
          <!-- 在「一起玩」房间里多一个房间页:同一扇浮窗承载两路会话,不再单独起房间聊天浮球 -->
          <div v-if="roomActive" class="float-tabs" role="tablist">
            <button
              type="button"
              class="float-tab"
              :class="{ active: tab === 'family' }"
              role="tab"
              :aria-selected="tab === 'family'"
              @click="tab = 'family'"
            >
              {{ $t('chat.title') }}
              <span v-if="chatStore.totalUnread" class="float-tab-badge">{{ badgeText(chatStore.totalUnread) }}</span>
            </button>
            <button
              type="button"
              class="float-tab"
              :class="{ active: tab === 'room' }"
              role="tab"
              :aria-selected="tab === 'room'"
              @click="tab = 'room'"
            >
              {{ $t('games.roomChat.title') }}
              <span v-if="roomUnread" class="float-tab-badge">{{ badgeText(roomUnread) }}</span>
            </button>
          </div>
          <span v-else class="panel-title">{{ $t('chat.title') }}</span>
          <span class="float-head-actions">
            <!-- 房间里通话就在聊天里:直接拉房间成员语音,不必再回大厅找人 -->
            <RoomCallPicker
              v-if="roomActive && tab === 'room'"
              :members="roomChatStore.roomMeta?.members || []"
              :my-id="userStore.userInfo?.id"
            />
            <el-button text circle size="small" @click="collapse">
              <el-icon><Close /></el-icon>
            </el-button>
          </span>
        </div>
        <ChatPanel v-if="tab === 'family'" />
        <RoomChatPanel v-else />
        <!-- 四角和四边的透明缩放区(房间对局里是钉住的尺寸,不给缩放) -->
        <div
          v-for="dir in pinnedRoom ? [] : RESIZE_DIRS"
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
      :class="{ pinned: pinnedRoom }"
      :style="ballStyle"
      :aria-label="$t('chat.title')"
      :title="assistant.eligible ? $t('chat.assistant.holdHint') : $t('chat.title')"
      @contextmenu.prevent
      @pointerdown="onBallDown"
      @pointerup="onBallUp"
      @click="onBallClick"
    >
      <el-badge :value="ballBadge" :hidden="!ballBadge" :max="99" class="ball-badge">
        <el-icon :size="22"><ChatDotRound /></el-icon>
      </el-badge>
    </button>

    <!-- 房间对局里的最新一句聊天:没有座位头像的游戏(牌桌之外)挂在浮球下面,
         压在游戏区内部、右缘与浮球对齐,不会挤出屏幕 -->
    <Transition name="chat-bubble">
      <div v-if="roomBubble && !expanded" class="chat-float-bubble" :style="bubbleStyle" role="status" aria-live="polite">
        {{ roomBubble }}
      </div>
    </Transition>

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
import { useThemeStore } from '@/stores/theme'
import { useWindowPanel } from '@/utils/windowPanel'
import { useChatStore } from '@/stores/chat'
import { useAssistantStore } from '@/stores/assistant'
import { trackAssistantHold } from '@/utils/assistantHold'
import { useRoomChatStore } from '@/stores/roomChat'
import { useFloatsStore } from '@/stores/floats'
import { useDevice } from '@/composables/useDevice'
import { useDragResize } from '@/utils/useDragResize'
import { haptic } from '@/utils/haptics'
import { useFloatDelete, FLOAT_DELETE_ZONE } from '@/composables/useFloatDelete'
import ChatPanel from '@/components/chat/ChatPanel.vue'
import RoomCallPicker from '@/components/games/RoomCallPicker.vue'
import RoomChatPanel from '@/components/chat/RoomChatPanel.vue'
import FloatDeleteDock from '@/components/ui/FloatDeleteDock.vue'

const FLOAT_POS_KEY = 'ihomy:chat:float:v1'
const PANEL_POS_KEY = 'ihomy:chat:panel:v1'
const SHEET_H_KEY = 'ihomy:chat:sheet:v1'
const BALL_SIZE = 48
const PANEL_W = 360
const PANEL_H = 520
const RESIZE_DIRS = ['n', 's', 'e', 'w', 'ne', 'nw', 'se', 'sw']

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const theme = useThemeStore()
const chatStore = useChatStore()
const assistant = useAssistantStore()
const floatVoiceOwner = Symbol()
const roomChatStore = useRoomChatStore()
const floatsStore = useFloatsStore()
const { isMobile } = useDevice()

const legacyExpanded = ref(false)
const windowExpanded = useWindowPanel('chat')
const expanded = computed({
  get: () => (theme.theme === 'window' ? windowExpanded.value : legacyExpanded.value),
  set(value) {
    if (theme.theme === 'window') windowExpanded.value = value
    else legacyExpanded.value = value
  },
})
watch(
  () => theme.theme,
  () => {
    windowExpanded.value = false
    legacyExpanded.value = false
  },
)
watch(windowExpanded, (open) => {
  if (!open) assistant.discardVoice(floatVoiceOwner)
})
/** 当前页分页:family=家庭聊天室(原浮窗) / room=「一起玩」房间临时聊天;没有房间时不显示分页 */
const tab = ref('family')
const roomActive = computed(() => !!roomChatStore.roomMeta)
const roomUnread = computed(() => (roomChatStore.roomMeta ? roomChatStore.unreadOf(roomChatStore.roomMeta.id) : 0))
const ballBadge = computed(() => chatStore.totalUnread + roomUnread.value)
const badgeText = (n) => (n > 99 ? '99+' : String(n))
// 进房间自动切到房间页(浮窗此时多半是收起的,展开即落在房间聊天);房间结束回到家庭聊天室
watch(roomActive, (v) => {
  tab.value = v ? 'room' : 'family'
})

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

// ---------- 房间对局:浮窗钉在游戏区右上角 ----------
// 「一起玩」的游戏页(?room=)里球/面板固定在 .gs-body 的右上角,聊天随时可见且不挡手牌;
// 钉住期间不拖不记位置,离开房间自动回到用户自己拖到的位置。
const PIN_GAP = 12
const pinnedRoom = computed(() => roomActive.value && !!route.query.room)
/** 桌布类游戏自己显示房间聊天(麻将座位气泡 / 飞行棋侧栏一条),其他游戏用浮球下面那条 */
const seatBubbles = computed(() => route.path === '/games/mahjong' || route.path === '/games/ludo')
const bodyRect = ref(null)
let bodyObserver = null
let bodyEl = null
let pinTimer = null
/**
 * 量游戏区盒子;返回是否量到了有效尺寸。
 * 游戏页是懒加载的,浮窗挂载时页面上往往还没有 .gs-body 或它还是 0 高(沉浸态样式没铺完),
 * 这时算出来的位置会把球丢到屏幕外,所以没量到就交给 retry 阶梯继续等。
 */
const measureBody = () => {
  const el = typeof document === 'undefined' ? null : document.querySelector('.game-shell .gs-body')
  if (!el) {
    bodyRect.value = null
    return false
  }
  const r = el.getBoundingClientRect()
  if (!r.width || !r.height) {
    bodyRect.value = null
    return false
  }
  bodyRect.value = { left: r.left, right: r.right, top: r.top, width: r.width, height: r.height }
  // 全屏/宽屏切换、规则面板开合都会改游戏区尺寸,直接跟这个盒子(只在钉住期间需要)
  if (el !== bodyEl) {
    bodyObserver?.disconnect()
    bodyEl = el
    if (typeof ResizeObserver !== 'undefined') {
      bodyObserver = new ResizeObserver(() => measureBody())
      bodyObserver.observe(el)
    }
  }
  return true
}
const clearPinTimer = () => {
  if (pinTimer) clearTimeout(pinTimer)
  pinTimer = null
}
/** 游戏页刚挂载、规则面板展开、横竖屏切换都会改几何:量到就先钉住,没量到按阶梯等到页面铺开 */
const syncPinned = () => {
  clearPinTimer()
  if (!pinnedRoom.value) {
    bodyObserver?.disconnect()
    bodyObserver = null
    bodyEl = null
    bodyRect.value = null
    return
  }
  if (measureBody()) return
  // 懒加载 chunk 冷启动可能几秒才铺完游戏区,阶梯给到 ~5s;中途量到立即停
  const ladder = [60, 120, 240, 400, 700, 1000, 1400, 2000, 2600, 3400]
  let i = 0
  const retry = () => {
    pinTimer = null
    if (!pinnedRoom.value || measureBody()) return
    if (i >= ladder.length) return
    pinTimer = setTimeout(retry, ladder[i++])
  }
  pinTimer = setTimeout(retry, ladder[i++])
}
// 还没量到游戏区时先把球按视口右上角摆着(绝不使用上次拖动留下的屏幕外坐标)
const VIEWPORT_FALLBACK = Object.freeze({ right: `${PIN_GAP}px`, top: `${PIN_GAP}px` })
const pinnedBallStyle = computed(() => {
  if (!pinnedRoom.value) return null
  const r = bodyRect.value
  if (!r) return { left: 'auto', ...VIEWPORT_FALLBACK }
  return { left: `${Math.round(r.right - BALL_SIZE - PIN_GAP)}px`, top: `${Math.round(r.top + PIN_GAP)}px` }
})
const pinnedPanelStyle = computed(() => {
  if (!pinnedRoom.value) return null
  const r = bodyRect.value
  if (!r) return { left: 'auto', width: `${PANEL_W}px`, height: `${PANEL_H}px`, ...VIEWPORT_FALLBACK }
  const w = Math.max(240, Math.min(PANEL_W, r.width - PIN_GAP * 2))
  const h = Math.max(240, Math.min(PANEL_H, r.height - PIN_GAP * 2))
  return {
    left: `${Math.round(Math.max(r.left + PIN_GAP, r.right - w - PIN_GAP))}px`,
    top: `${Math.round(r.top + PIN_GAP)}px`,
    width: `${Math.round(w)}px`,
    height: `${Math.round(h)}px`,
  }
})

/** 最新一条房间聊天(浮球下面那条):只算当前房间、别人发的 */
const roomBubble = computed(() => {
  const meta = roomChatStore.roomMeta
  if (!pinnedRoom.value || seatBubbles.value || !meta?.id) return ''
  const prefix = `${meta.id}:`
  let best = null
  for (const [key, value] of Object.entries(roomChatStore.bubbles)) {
    if (!key.startsWith(prefix) || !value?.text) continue
    const uid = Number(key.slice(prefix.length))
    if (uid === userStore.userInfo?.id) continue
    if (!best || (value.at || 0) > (best.at || 0)) best = { text: value.text, at: value.at, uid }
  }
  if (!best) return ''
  const name = meta.members?.find((m) => m.userId === best.uid)?.name || ''
  return name ? `${name}:${best.text}` : best.text
})
const bubbleStyle = computed(() => {
  const r = bodyRect.value
  // 还没量到游戏区:先贴着视口右上角(和球的兜底位置同源),量到后自动收回游戏区内
  if (!r) {
    return { right: `${PIN_GAP}px`, top: `${PIN_GAP + BALL_SIZE + 6}px`, zIndex: 'var(--z-float)' }
  }
  // 右缘与浮球对齐、往上顶到球下面:左边界再兜一层 max-width,窄屏也出不了游戏区
  return {
    right: `${Math.round(window.innerWidth - (r.right - PIN_GAP))}px`,
    top: `${Math.round(r.top + PIN_GAP + BALL_SIZE + 6)}px`,
    zIndex: 'var(--z-float)',
  }
})

const ballStyle = computed(() => ({
  ...(pinnedBallStyle.value || {
    left: `${ball.pos.value.x}px`,
    top: theme.theme === 'window' ? `min(${ball.pos.value.y}px, calc(100dvh - var(--chat-ball-clearance, 48px)))` : `${ball.pos.value.y}px`,
  }),
  zIndex: 'var(--z-float)',
}))
const panelStyle = computed(() => ({
  ...(pinnedPanelStyle.value || {
    left: `${panelPos.value.x}px`,
    top: `${panelPos.value.y}px`,
    width: `${panelSize.value.w}px`,
    height: `${panelSize.value.h}px`,
  }),
  zIndex: 'var(--z-float)',
}))

// ---------- 手机端抽屉高度:顶部把手往上拖即放大聊天框 ----------
// 抽屉高度和内容页下边距共用 html 上的 --chat-sheet-h(main.css),自定义值以 px 覆盖它;
// null = 用 CSS 默认 52dvh,用户没拖过就不写死任何尺寸。
const sheetEl = ref(null)
const sheetH = ref(null)
try {
  const saved = Math.round(Number(localStorage.getItem(SHEET_H_KEY)))
  if (Number.isFinite(saved) && saved >= 200) sheetH.value = saved
} catch {}
const SHEET_MIN_H = 240
const SHEET_TOP_GAP = 8
const applySheetH = () => {
  if (typeof document === 'undefined') return
  const h = sheetActive.value ? sheetH.value : null
  if (h == null) document.documentElement.style.removeProperty('--chat-sheet-h')
  else document.documentElement.style.setProperty('--chat-sheet-h', `${h}px`)
}
/** 拖拽的高度上限:CSS max-height 已按视口与刘海算好,取解析后的像素值(算不出来再退回视口高度) */
const sheetCapH = () => {
  const el = sheetEl.value
  const cap = el ? parseFloat(getComputedStyle(el).maxHeight) : NaN
  if (Number.isFinite(cap)) return Math.max(SHEET_MIN_H, Math.round(cap))
  return Math.max(SHEET_MIN_H, (window.innerHeight || 0) - SHEET_TOP_GAP)
}
let sheetDrag = null
const detachSheetDrag = () => {
  window.removeEventListener('pointermove', onSheetMove)
  window.removeEventListener('pointerup', onSheetEnd)
  window.removeEventListener('pointercancel', onSheetEnd)
  sheetDrag = null
}
const onSheetMove = (e) => {
  if (!sheetDrag || e.pointerId !== sheetDrag.id) return
  sheetDrag.moved = true
  // 往上拖(dy 为负)→ 更高;夹在 [最小高度, 上限] 之间(上限在按下时定死,拖动中不再抖动)
  const next = sheetDrag.h + (sheetDrag.y - e.clientY)
  sheetH.value = Math.round(Math.max(SHEET_MIN_H, Math.min(next, sheetDrag.cap)))
}
const onSheetEnd = (e) => {
  if (!sheetDrag || e.pointerId !== sheetDrag.id) return
  const moved = sheetDrag.moved
  detachSheetDrag()
  if (!moved || sheetH.value == null) return
  try {
    localStorage.setItem(SHEET_H_KEY, String(sheetH.value))
  } catch {}
  haptic('tap')
}
const onSheetGripDown = (e) => {
  if (e.isPrimary === false || e.button !== 0) return
  const h = sheetEl.value?.getBoundingClientRect().height
  if (!h) return
  sheetDrag = { id: e.pointerId, y: e.clientY, h, cap: sheetCapH(), moved: false }
  window.addEventListener('pointermove', onSheetMove)
  window.addEventListener('pointerup', onSheetEnd)
  window.addEventListener('pointercancel', onSheetEnd)
  e.preventDefault()
}

// 松手时记录拖动状态，拦住随拖动产生的 click，同时保留键盘激活。
let ignoreBallClick = false
let cancelHold = null
const onBallDown = (e) => {
  if (e.isPrimary === false || e.button !== 0) return
  ignoreBallClick = false
  if (!pinnedRoom.value) {
    if (!ball.onDragStart(e)) return
    trackToDock(e)
  }
  cancelHold?.()
  if (!assistant.eligible) return
  cancelHold = trackAssistantHold(e, {
    hold() {
      if (ball.dragging.value || dockActive.value) return
      ignoreBallClick = true
      ball.cancelPendingDrag()
      detachTrack?.()
      dockCancel()
      tab.value = 'family'
      chatStore.openAssistant()
      expand()
      haptic('tap')
      assistant.startVoice(t, floatVoiceOwner)
    },
    release: () => assistant.finishVoice(t, floatVoiceOwner),
    cancel: () => assistant.discardVoice(floatVoiceOwner),
  })
}
const onBallUp = (e) => {
  if (e.isPrimary && e.button === 0 && ball.dragging.value) ignoreBallClick = true
}
const onBallClick = (e) => {
  // 钉住态每次点球都重量一次:布局在这之前变过(规则面板收起/旋转)也能立刻收回游戏区
  if (pinnedRoom.value) syncPinned()
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

// 面板只能从标题栏拖;拖动与 useDragResize 共用同一次 pointerdown(钉住时不给拖)
const onPanelDown = (e) => {
  if (pinnedRoom.value) return
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
  chatStore.loadHistory({ force: true, markRead: !assistant.active })
  expanded.value = true
  clearToast()
}
const collapse = () => {
  cancelHold?.()
  assistant.discardVoice(floatVoiceOwner)
  expanded.value = false
}

// ---------- 新消息横幅 ----------
// 浮窗收起或不在聊天界面时来消息,除了角标/系统提示音,再给一条 6 秒的可见横幅。
const notifyMsg = ref(null)
let notifyTimer = null
const toastPreview = computed(() => {
  const m = notifyMsg.value
  if (!m) return ''
  if (theme.theme === 'window') return t('windowTools.newMessage')
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
  const message = notifyMsg.value
  if (message) {
    tab.value = 'family'
    if (message.dm) chatStore.openDm(message.dm.userId, message.senderName)
    else if (message.familyId && Number(message.familyId) !== Number(userStore.userInfo?.familyId)) chatStore.openRoom(message.familyId)
    else chatStore.closeDm()
  }
  clearToast()
  expand()
}
watch(
  () => [userStore.userInfo?.id, userStore.userInfo?.familyId].join('/'),
  () => {
    clearToast()
    collapse()
  },
)
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

// 展开且在家庭聊天室页=在看(推进已读),收起或停在房间页=交还未读计数(房间页只看房间未读);
// /chat 页经由 chatPageActive 与这里叠加
watch([expanded, tab], ([open, on]) => {
  chatStore.floatOpen = open && on === 'family'
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
  window.addEventListener('resize', onViewportResize, { passive: true })
  syncPinned()
})

// 房间对局页进/出、路由切换时重算钉住位置(游戏区尺寸靠 ResizeObserver 跟)
watch([pinnedRoom, () => route.fullPath], syncPinned)
const onViewportResize = () => {
  if (pinnedRoom.value) measureBody()
}

// 手机端抽屉打开时给 <html> 挂个类:内容页按抽屉高度留出下边距,滚到底能看全(样式在 main.css)
const sheetActive = computed(() => show.value && expanded.value && isMobile.value)
watch(
  sheetActive,
  (v) => {
    if (typeof document === 'undefined') return
    document.documentElement.classList.toggle('chat-sheet-open', v)
    applySheetH()
  },
  { immediate: true },
)
// 拖动中实时改高度(内容页下边距同源跟着变)
watch(sheetH, applySheetH)

// 卸载兜底:拖动中被隐藏/路由切走时不残留窗口级监听与删除条
onBeforeUnmount(() => {
  cancelHold?.()
  assistant.discardVoice(floatVoiceOwner)
  detachTrack?.()
  detachSheetDrag()
  dockCancel()
  clearPinTimer()
  bodyObserver?.disconnect()
  bodyObserver = null
  window.removeEventListener('resize', onViewportResize)
  if (notifyTimer) clearTimeout(notifyTimer)
  if (typeof document !== 'undefined') {
    document.documentElement.classList.remove('chat-sheet-open')
    document.documentElement.style.removeProperty('--chat-sheet-h')
  }
})
</script>

<style scoped>
@media (max-width: 768px) {
  :global(html.window-player-visible) .chat-float-ball {
    --chat-ball-clearance: calc(180px + env(safe-area-inset-bottom));
  }
}
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
/* 房间对局:钉在游戏区右上角,不给「可拖」的手型暗示 */
.chat-float-ball.pinned {
  cursor: pointer;
  touch-action: manipulation;
}
.ball-badge :deep(.el-badge__content) {
  z-index: 1;
}

/* 没有座位头像的游戏里,房间最新一句挂在浮球正下方:右缘与球对齐、限宽不越界 */
.chat-float-bubble {
  position: fixed;
  max-width: min(240px, calc(100vw - 32px));
  padding: 6px 10px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  box-shadow: var(--shadow);
  color: var(--color-text);
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}
.chat-bubble-enter-active,
.chat-bubble-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.chat-bubble-enter-from,
.chat-bubble-leave-to {
  opacity: 0;
  transform: translateY(-4px);
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
/* 钉住时标题栏不再是拖拽把手 */
.chat-float-panel.pinned .panel-head,
.chat-float-panel.pinned .panel-head:active {
  cursor: default;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
}
/* 浮窗分页:家庭聊天室 / 房间聊天共用同一扇窗,进房间时多出第二页 */
.float-tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}
.float-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
}
.float-tab.active {
  color: var(--color-accent);
  background: rgba(var(--color-brand-rgb), 0.12);
  font-weight: 600;
}
.float-tab-badge {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--color-brand);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}
.chat-float-panel :deep(.chat-panel),
.chat-float-panel :deep(.room-chat) {
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
  /* 拖到最高时兜底:顶边不顶出屏幕、不压状态栏(safe-area 在刘海屏上还要再让一截) */
  max-height: calc(100vh - 8px);
  max-height: calc(100dvh - var(--safe-area-inset-top, env(safe-area-inset-top, 0px)) - 8px);
  display: flex;
  flex-direction: column;
  background: var(--color-card);
  border-radius: 16px 16px 0 0;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.18);
  z-index: var(--z-float);
  padding: 0 12px 8px;
}
/* 顶部把手:一条短横,往上拖放大聊天框(热区撑满抽屉宽,触屏拖拽不吃页面滚动) */
.sheet-grip {
  flex: none;
  margin: 0 -12px;
  padding: 8px 0 2px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: ns-resize;
  touch-action: none; /* 拖拽把手 */
}
.sheet-grip-bar {
  width: 36px;
  height: 4px;
  border-radius: 999px;
  background: var(--color-border);
  transition: background var(--dur-fast) ease;
}
.sheet-grip:hover .sheet-grip-bar,
.sheet-grip:active .sheet-grip-bar {
  background: var(--color-brand-text);
}
.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 2px 4px 6px;
  flex-shrink: 0;
}
.sheet-title {
  font-size: 14px;
  font-weight: 600;
}
/* 头部右侧:房间通话键 + 收起键(手机抽屉与桌面面板同一处) */
.float-head-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}
.chat-float-sheet :deep(.chat-panel),
.chat-float-sheet :deep(.room-chat) {
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
    top: calc(12px + var(--safe-area-inset-top, env(safe-area-inset-top, 0px)));
    max-width: none;
  }
}
</style>
