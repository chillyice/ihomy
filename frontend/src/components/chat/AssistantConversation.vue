<template>
  <div ref="stream" class="assistant-stream" aria-live="polite">
    <el-button v-if="assistant.hasOlder" text size="small" :disabled="assistant.busy" @click="loadOlder">{{
      $t('chat.assistant.older')
    }}</el-button>
    <div v-if="assistant.status && !assistant.status.available" class="assistant-note">
      {{ assistant.status.reason || $t('chat.assistant.unavailable') }}
    </div>
    <div v-if="!assistant.messages.length && assistant.status?.available && !assistant.loading && !assistant.sending" class="suggestions">
      <button v-for="key in suggestions" :key="key" type="button" @click="suggest(key)">
        {{ $t('chat.assistant.suggestions.' + key) }}
      </button>
    </div>
    <article v-for="message in assistant.messages" :key="message.id" class="assistant-bubble" :class="{ mine: message.role === 'USER' }">
      <template v-if="message.role === 'USER'">{{ message.content }}</template>
      <template v-else>
        <div v-if="message.content" class="reply" v-html="renderMarkdown(message.content, proseOnly)" />
        <template v-for="(run, index) in message.toolRuns" :key="index">
          <router-link v-if="run.name === 'open_page' && safePath(run.result?.path)" :to="safePath(run.result.path)">
            {{ $t('chat.assistant.open') }} ·
            {{ $te('nav.' + run.result.page) ? $t('nav.' + run.result.page) : $t('chat.assistant.tools.open_page') }}
          </router-link>
          <details v-else-if="readTools.includes(run.name)">
            <summary>{{ $t('chat.assistant.tools.' + run.name) }}</summary>
            <p v-if="run.result?.reply">{{ run.result.reply }}</p>
            <ul v-if="(run.result?.items || run.result?.matches)?.length">
              <li v-for="(item, idx) in (run.result.items || run.result.matches).slice(0, 20)" :key="idx">
                <router-link v-if="safePath(item.path)" :to="safePath(item.path)">{{ itemLabel(item) }}</router-link>
                <span v-else>{{ itemLabel(item) }}</span>
              </li>
            </ul>
            <p v-else-if="!run.result?.reply">{{ run.result?.error || $t('chat.assistant.emptyResult') }}</p>
          </details>
        </template>
        <AssistantActionCard v-for="action in message.pendingActions" :key="action.id" :action="action" />
        <p v-for="(notice, index) in message.notices" :key="index" class="assistant-note">{{ notice }}</p>
      </template>
    </article>
    <article v-if="assistant.sending" class="assistant-bubble mine">{{ assistant.sending }}</article>
    <p v-if="assistant.sending || assistant.loading" role="status" class="assistant-note">
      {{ $t(assistant.sending ? 'chat.assistant.thinking' : 'chat.assistant.loading') }}
    </p>
  </div>
  <div v-if="assistant.error" class="assistant-note" role="alert">
    {{ assistant.error }}
    <el-button text size="small" :disabled="assistant.busy" @click="assistant.open()">{{ $t('chat.assistant.reload') }}</el-button>
  </div>
  <form class="assistant-compose" @submit.prevent="send">
    <textarea
      ref="input"
      v-model="assistant.draft"
      :disabled="!assistant.status?.available || !!assistant.sending"
      rows="2"
      maxlength="4000"
      :aria-label="$t('chat.assistant.placeholder')"
      :placeholder="$t('chat.assistant.placeholder')"
      @keydown.enter="onEnter"
    />
    <div class="assistant-controls">
      <el-button
        native-type="button"
        circle
        size="small"
        :disabled="assistant.busy || !assistant.status?.available || assistant.voice === 'transcribing'"
        :aria-label="voiceHint"
        :title="voiceHint"
        :type="assistant.voice === 'recording' ? 'danger' : 'default'"
        @click="toggleVoice"
      >
        <el-icon><Microphone /></el-icon>
      </el-button>
      <span v-if="assistant.voice !== 'idle'" class="assistant-note" role="status">{{ voiceHint }}</span>
      <el-button
        v-if="assistant.messages.length"
        text
        size="small"
        :disabled="assistant.busy || assistant.voice !== 'idle'"
        :title="$t('chat.assistant.clear')"
        :aria-label="$t('chat.assistant.clear')"
        @click="askClear"
        ><el-icon><Delete /></el-icon
      ></el-button>
      <el-button
        native-type="submit"
        class="send"
        type="primary"
        size="small"
        :disabled="assistant.busy || assistant.voice !== 'idle' || !assistant.status?.available || !assistant.draft.trim()"
        >{{ $t('chat.assistant.send') }}</el-button
      >
    </div>
  </form>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { Delete, Microphone } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useAssistantStore } from '@/stores/assistant'
import { useUserStore } from '@/stores/user'
import { renderMarkdown } from '@/utils/markdown'
import { assistantPath } from '@/utils/assistantNavigation'
import { getDisplayMode } from '@/composables/useDisplayMode'
import AssistantActionCard from './AssistantActionCard.vue'

const assistant = useAssistantStore()
const user = useUserStore()
const route = useRoute()
const { t } = useI18n()
const stream = ref(null)
const input = ref(null)
const micOwner = Symbol()
const suggestions = computed(() =>
  user.userInfo?.role === 'GUEST' ? ['schedule', 'item', 'diary'] : ['schedule', 'reminder', 'item', 'diary'],
)
const readTools = ['get_schedule', 'find_item', 'search_content', 'list_family_members']
// Only server tool results get interactive navigation. Model prose may contain formatting.
const proseOnly = {
  ALLOWED_TAGS: [
    'p',
    'br',
    'em',
    'strong',
    'ul',
    'ol',
    'li',
    'code',
    'pre',
    'blockquote',
    'h2',
    'h3',
    'h4',
    'table',
    'thead',
    'tbody',
    'tr',
    'td',
    'th',
  ],
  ALLOWED_ATTR: [],
}
const safePath = (path) => assistantPath(path, getDisplayMode() === 'child')
const itemLabel = (item) => [item.date, item.time, item.title || item.name || item.itemName, item.location].filter(Boolean).join(' · ')
const voiceHint = computed(() => t('chat.assistant.voice.' + assistant.voice))
function toggleVoice() {
  if (assistant.voice === 'idle') assistant.startVoice(t, micOwner)
  else assistant.finishVoice(t)
}
function suggest(key) {
  assistant.draft = t('chat.assistant.suggestions.' + key)
  input.value?.focus()
}
function send() {
  assistant.send({ path: route.path.slice(0, 200), name: String(route.name || '').slice(0, 80) })
}
function onEnter(e) {
  if (e.isComposing || e.keyCode === 229 || e.shiftKey) return
  e.preventDefault()
  send()
}
async function askClear() {
  const { id, familyId } = user.userInfo
  try {
    await ElMessageBox.confirm(t('chat.assistant.clearPrompt'), t('chat.assistant.clear'), {
      confirmButtonText: t('chat.assistant.clear'),
      cancelButtonText: t('chat.assistant.cancel'),
    })
  } catch {
    return
  }
  if (assistant.active && user.userInfo?.id === id && user.userInfo?.familyId === familyId) await assistant.clear()
}
function scrollBottom() {
  nextTick(() => {
    if (stream.value) stream.value.scrollTop = stream.value.scrollHeight
  })
}
async function loadOlder() {
  const el = stream.value
  const height = el.scrollHeight
  await assistant.load(true)
  await nextTick()
  el.scrollTop += el.scrollHeight - height
}
watch(() => [assistant.messages.at(-1)?.id, assistant.sending].join(':'), scrollBottom, { immediate: true })
const refreshSettings = () => assistant.refreshStatus()
onMounted(() => {
  assistant.open()
  window.addEventListener('ihomy:assistant-settings', refreshSettings)
})
onBeforeUnmount(() => {
  assistant.discardVoice(micOwner)
  window.removeEventListener('ihomy:assistant-settings', refreshSettings)
})
</script>

<style scoped>
.assistant-stream {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 4px;
}
.assistant-bubble {
  max-width: 100%;
  padding: 8px 10px;
  box-sizing: border-box;
  border-radius: 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  overflow-wrap: anywhere;
  font-size: 13px;
}
.assistant-bubble.mine {
  align-self: flex-end;
  white-space: pre-wrap;
  background: rgba(var(--color-brand-rgb), 0.12);
}
.reply :deep(:first-child) {
  margin-top: 0;
}
.reply :deep(:last-child) {
  margin-bottom: 0;
}
.reply :deep(pre) {
  white-space: pre-wrap;
}
summary {
  cursor: pointer;
}
details {
  margin-top: 6px;
}
ul {
  padding-left: 18px;
}
.suggestions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.suggestions button {
  border-radius: 12px;
  background: var(--color-card);
  color: var(--color-text);
  border: 1px solid var(--color-border);
  padding: 6px 10px;
  cursor: pointer;
  font: inherit;
  text-align: left;
}
.assistant-note {
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow-wrap: anywhere;
}
.assistant-compose {
  border-top: 1px solid var(--color-border);
  padding: 6px 0;
}
textarea {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  resize: vertical;
  max-height: 100px;
  padding: 6px;
  background: var(--color-card);
  color: inherit;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  font: inherit;
}
.assistant-controls {
  display: flex;
  gap: 4px;
  align-items: center;
  flex-wrap: wrap;
}
.send {
  margin-inline-start: auto;
}
@media (max-width: 768px) {
  /* The chat page keeps a draggable ball above the bottom navigation. Leave its default corner clear. */
  .chat-page .assistant-controls {
    padding-inline-end: 64px;
  }
}
</style>
