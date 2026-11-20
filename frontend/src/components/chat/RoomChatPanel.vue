<!-- 房间临时聊天内容区:挂在全局聊天浮窗(ChatFloat)的「房间」页,不再单独起一个浮球。
     会话身份=当前「一起玩」房间(stores/roomChat 的 roomMeta);消息由服务端发号后广播,这里只渲染。
     表情与聊天室同一套预设(CHAT_EMOJI),点一下直接发送,不插输入框。 -->
<template>
  <div class="room-chat">
    <div ref="listEl" class="rc-list">
      <p v-if="!messages.length" class="rc-empty">{{ $t('games.roomChat.empty') }}</p>
      <div v-for="m in messages" :key="m.seq" class="rc-msg" :class="{ mine: m.from === myId }">
        <span v-if="m.from !== myId" class="rc-name">{{ nameOf(m.from) }}</span>
        <span class="rc-text">{{ m.text }}</span>
      </div>
    </div>
    <div class="rc-compose">
      <el-popover v-model:visible="emojiVisible" placement="top-start" :width="300" :title="$t('chat.composer.emojiTitle')" trigger="click">
        <template #reference>
          <button type="button" class="rc-emoji-btn" :title="$t('games.roomChat.emoji')" :aria-label="$t('games.roomChat.emoji')">
            🙂
          </button>
        </template>
        <div class="rc-emoji-panel">
          <button v-for="e in CHAT_EMOJI" :key="e" type="button" class="rc-emoji" :aria-label="e" @click="sendEmoji(e)">
            {{ e }}
          </button>
        </div>
      </el-popover>
      <input
        v-model="draft"
        class="rc-input"
        type="text"
        enterkeyhint="send"
        :maxlength="ROOM_CHAT_MAX_LEN"
        :placeholder="$t('games.roomChat.placeholder')"
        :aria-label="$t('games.roomChat.placeholder')"
        @keyup.enter="submit"
      />
      <button type="button" class="rc-send" :disabled="!canSend" @click="submit">{{ $t('games.roomChat.send') }}</button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useRoomChatStore, ROOM_CHAT_MAX_LEN } from '@/stores/roomChat'
import { useUserStore } from '@/stores/user'
import { CHAT_EMOJI } from '@/utils/chatEmoji'

const { t } = useI18n()
const store = useRoomChatStore()
const userStore = useUserStore()

const draft = ref('')
const emojiVisible = ref(false)
const listEl = ref(null)

const roomId = computed(() => Number(store.roomMeta?.id) || 0)
const messages = computed(() => store.messagesOf(roomId.value))
const myId = computed(() => userStore.userInfo?.id)
const canSend = computed(() => !!draft.value.trim())

const nameOf = (userId) => (store.roomMeta?.members || []).find((m) => m.userId === userId)?.name || ''

const scrollToBottom = async () => {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

// 只看条数与最后一条的 seq:数组是原地 push,watch 数组本身不会触发
watch(() => [messages.value.length, messages.value[messages.value.length - 1]?.seq], scrollToBottom)

const submit = () => {
  if (!canSend.value) return
  if (store.send(roomId.value, draft.value)) draft.value = ''
  else ElMessage.warning(t('games.roomChat.sendFailed'))
}

/** 表情点一下直接发(与聊天室同口径:按普通文本上行,不进输入框) */
const sendEmoji = (emoji) => {
  if (!store.send(roomId.value, emoji)) ElMessage.warning(t('games.roomChat.sendFailed'))
}

// 面板可见即「在看这个房间」:进面板清未读,收起(卸载)后继续累计
onMounted(() => {
  scrollToBottom()
  store.setOpen(roomId.value, true)
})
onBeforeUnmount(() => {
  store.setOpen(roomId.value, false)
})
watch(roomId, (id, prev) => {
  if (prev) store.setOpen(prev, false)
  if (id) store.setOpen(id, true)
})
</script>

<style scoped>
.room-chat {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.rc-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 2px 0 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rc-empty {
  margin: auto;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.rc-msg {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  max-width: 92%;
}
.rc-msg.mine {
  align-self: flex-end;
  align-items: flex-end;
}
.rc-name {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.rc-text {
  padding: 5px 9px;
  border-radius: 10px;
  background: rgba(var(--color-brand-rgb), 0.1);
  word-break: break-word;
  font-size: 13px;
}
.rc-msg.mine .rc-text {
  background: rgba(var(--color-brand-rgb), 0.22);
}
.rc-compose {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
  padding-top: 6px;
  border-top: 1px solid var(--color-border);
}
.rc-emoji-btn {
  flex: none;
  width: 30px;
  height: 30px;
  padding: 0;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: transparent;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.rc-emoji-btn:hover {
  background: rgba(var(--color-brand-rgb), 0.1);
}
/* 表情面板:与聊天室同款宫格,多了在面板里滚动 */
.rc-emoji-panel {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  max-height: 216px;
  overflow-y: auto;
}
.rc-emoji {
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 18px;
  line-height: 1;
  padding: 6px 0;
  cursor: pointer;
  transition:
    background var(--dur-fast) ease,
    transform var(--dur-fast) var(--ease-soft);
}
.rc-emoji:hover {
  background: rgba(var(--color-brand-rgb), 0.1);
  transform: scale(1.12);
}
.rc-input {
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
.rc-input:focus {
  border-color: var(--color-accent);
}
.rc-send {
  flex: none;
  height: 30px;
  padding: 0 12px;
  border-radius: 10px;
  border: none;
  background: var(--color-brand);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.rc-send:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
