<!-- 「一起玩」房间临时聊天浮窗:进房自动出现,默认落在右上角,可拖(位置记忆 ihomy:room:chat:v1)。
     会话身份就是当前房间(room.id),不开新聊天室、不建表;消息由服务端发号后广播,这里只渲染。
     浮球必须在 .room-bar 之外(room-bar 是 inline-size 容器,会把 fixed 后代变成相对横幅定位)。 -->
<template>
  <div v-if="room" class="grc">
    <button
      v-if="!open"
      type="button"
      class="grc-ball"
      :style="ballStyle"
      :aria-label="$t('games.roomChat.title')"
      @pointerdown="onBallDown"
      @pointerup="onBallUp"
      @click="onBallClick"
    >
      <el-icon :size="20"><ChatDotRound /></el-icon>
      <span v-if="unread" class="grc-badge" aria-hidden="true">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <div v-else class="grc-panel" :style="panelStyle" role="dialog" :aria-label="$t('games.roomChat.title')">
      <div class="grc-head">
        <span class="grc-title">{{ $t('games.roomChat.title') }}</span>
        <button type="button" class="grc-close" :aria-label="$t('games.roomChat.close')" @click="close">
          <el-icon :size="16"><Close /></el-icon>
        </button>
      </div>
      <div ref="listEl" class="grc-list">
        <p v-if="!messages.length" class="grc-empty">{{ $t('games.roomChat.empty') }}</p>
        <div v-for="m in messages" :key="m.seq" class="grc-msg" :class="{ mine: m.from === myId }">
          <span v-if="m.from !== myId" class="grc-name">{{ nameOf(m.from) }}</span>
          <span class="grc-text">{{ m.text }}</span>
        </div>
      </div>
      <div class="grc-quick">
        <button
          v-for="e in QUICK_EMOJI"
          :key="e"
          type="button"
          class="grc-emoji"
          :aria-label="$t('games.roomChat.emoji') + ' ' + e"
          :title="$t('games.roomChat.emoji')"
          @click="sendEmoji(e)"
        >
          {{ e }}
        </button>
      </div>
      <form class="grc-compose" @submit.prevent="submit">
        <input
          v-model="draft"
          class="grc-input"
          type="text"
          enterkeyhint="send"
          :maxlength="ROOM_CHAT_MAX_LEN"
          :placeholder="$t('games.roomChat.placeholder')"
          :aria-label="$t('games.roomChat.placeholder')"
          @keyup.enter="submit"
        />
        <button type="submit" class="grc-send" :disabled="!canSend">{{ $t('games.roomChat.send') }}</button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Close } from '@element-plus/icons-vue'
import { useRoomChatStore, ROOM_CHAT_MAX_LEN } from '@/stores/roomChat'
import { useUserStore } from '@/stores/user'
import { useDragResize } from '@/utils/useDragResize'

const props = defineProps({
  /** 房间视图(play.room / 大厅 room),只用 id 与 members(名字回填) */
  room: { type: Object, default: null },
})

const BALL = 44
const PANEL_W = 280
/** 快捷表情:先放一个看效果,后续按需加 */
const QUICK_EMOJI = ['👍']

const { t } = useI18n()
const store = useRoomChatStore()
const userStore = useUserStore()

const open = ref(false)
const draft = ref('')
const listEl = ref(null)
const viewport = ref({ w: window.innerWidth || 0, h: window.innerHeight || 0 })

const roomId = computed(() => Number(props.room?.id) || 0)
const myId = computed(() => userStore.userInfo?.id)
const messages = computed(() => store.messagesOf(roomId.value))
const unread = computed(() => store.unreadOf(roomId.value))
const canSend = computed(() => !!draft.value.trim())

const drag = useDragResize({
  // anchorRight:pos.x 是离右边缘距离,旋屏后仍贴右侧
  x: 14,
  y: 62,
  w: BALL,
  h: BALL,
  storageKey: 'ihomy:room:chat:v1',
  anchorRight: true,
})

const ballStyle = computed(() => ({
  left: `${Math.max(0, viewport.value.w - drag.pos.value.x - BALL)}px`,
  top: `${drag.pos.value.y}px`,
  zIndex: 'var(--z-float)',
}))

/** 面板从球的位置展开:右边缘贴球右边缘,顶部对齐;始终夹在视口内 */
const panelStyle = computed(() => {
  const { w: vw, h: vh } = viewport.value
  const h = Math.min(360, Math.round(vh * 0.62))
  const left = Math.max(8, Math.min(vw - PANEL_W - 8, vw - drag.pos.value.x - PANEL_W))
  const top = Math.max(8, Math.min(vh - h - 8, drag.pos.value.y))
  return { left: `${left}px`, top: `${top}px`, width: `${PANEL_W}px`, height: `${h}px`, zIndex: 'var(--z-float)' }
})

/** 松手时记录拖动状态,拦住随拖动产生的 click;键盘激活照常打开 */
let ignoreClick = false
const onBallDown = (e) => {
  if (!drag.onDragStart(e)) return
  ignoreClick = false
}
const onBallUp = (e) => {
  if (e.isPrimary && e.button === 0) ignoreClick = drag.dragging.value
}
const onBallClick = (e) => {
  if (e.detail === 0 || !ignoreClick) open.value = true
  ignoreClick = false
}
const close = () => {
  open.value = false
}

const nameOf = (userId) => (props.room?.members || []).find((m) => m.userId === userId)?.name || ''

const scrollToBottom = async () => {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

// 只看条数与最后一条的 seq:数组是原地 push,watch 数组本身不会触发
watch(
  () => [open.value, messages.value.length, messages.value[messages.value.length - 1]?.seq],
  () => {
    if (open.value) scrollToBottom()
  },
)
watch(open, (v) => store.setOpen(roomId.value, v))

const submit = () => {
  if (!canSend.value) return
  if (store.send(roomId.value, draft.value)) draft.value = ''
  else ElMessage.warning(t('games.roomChat.sendFailed'))
}
const sendEmoji = (emoji) => {
  if (!store.send(roomId.value, emoji)) ElMessage.warning(t('games.roomChat.sendFailed'))
}

const onViewport = () => {
  viewport.value = { w: window.innerWidth || 0, h: window.innerHeight || 0 }
}

onMounted(() => {
  window.addEventListener('resize', onViewport, { passive: true })
  window.addEventListener('orientationchange', onViewport, { passive: true })
  if (roomId.value) store.attach(roomId.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onViewport)
  window.removeEventListener('orientationchange', onViewport)
  store.setOpen(roomId.value, false)
  if (roomId.value) store.detach(roomId.value)
})

watch(roomId, (id, prev) => {
  if (prev) store.detach(prev)
  if (id) store.attach(id)
})
</script>

<style scoped>
.grc-ball {
  position: fixed;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-accent);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.16);
  cursor: grab;
  touch-action: none;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}
.grc-ball:hover {
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.22);
}
.grc-ball:active {
  cursor: grabbing;
  transform: scale(0.94);
}
.grc-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--color-brand);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
}
.grc-panel {
  position: fixed;
  display: flex;
  flex-direction: column;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.24);
  overflow: hidden;
  font-size: 13px;
}
.grc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--color-border);
}
.grc-title {
  font-weight: 600;
}
.grc-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.grc-close:hover {
  background: rgba(0, 0, 0, 0.06);
}
.grc-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.grc-empty {
  margin: auto;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.grc-msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  max-width: 92%;
}
.grc-msg.mine {
  align-self: flex-end;
  align-items: flex-end;
}
.grc-name {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.grc-text {
  padding: 5px 9px;
  border-radius: 10px;
  background: rgba(var(--color-brand-rgb), 0.1);
  word-break: break-word;
}
.grc-msg.mine .grc-text {
  background: rgba(var(--color-brand-rgb), 0.22);
}
.grc-quick {
  display: flex;
  gap: 6px;
  padding: 4px 10px 0;
}
.grc-emoji {
  width: 28px;
  height: 28px;
  padding: 0;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  background: transparent;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.grc-emoji:hover {
  background: rgba(0, 0, 0, 0.05);
}
.grc-compose {
  display: flex;
  gap: 6px;
  padding: 6px 10px 10px;
}
.grc-input {
  flex: 1;
  min-width: 0;
  height: 30px;
  padding: 0 9px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  outline: none;
}
.grc-input:focus {
  border-color: var(--color-accent);
}
.grc-send {
  height: 30px;
  padding: 0 12px;
  border-radius: 10px;
  border: none;
  background: var(--color-brand);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.grc-send:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
