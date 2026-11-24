import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { assistantApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'

/** Both chat surfaces share the same private conversation, draft and microphone. */
export const useAssistantStore = defineStore('assistant', () => {
  const user = useUserStore()
  const eligible = computed(() => user.isLoggedIn && !!user.userInfo?.familyId && !user.isPureOps)
  const active = ref(false)
  const status = ref(null)
  const messages = ref([])
  const pending = ref(0)
  const draft = ref('')
  const sending = ref('')
  const loading = ref(false)
  const hasOlder = ref(false)
  const clearing = ref(false)
  const deciding = ref([])
  const error = ref('')
  const voice = ref('idle')
  const busy = computed(() => !!sending.value || loading.value || clearing.value || deciding.value.length > 0)
  let generation = 0
  let statusRequest = null
  let take = null
  let userMessageKey = 0

  function discardVoice(owner) {
    if (owner && take?.owner !== owner) return
    const old = take
    take = null
    voice.value = 'idle'
    if (!old) return
    clearTimeout(old.timer)
    if (old.started) old.recorder.stop()
  }

  function leave() {
    active.value = false
    discardVoice()
  }

  function reset() {
    generation++
    leave()
    statusRequest = null
    status.value = null
    pending.value = 0
    messages.value = []
    draft.value = sending.value = error.value = ''
    loading.value = clearing.value = hasOlder.value = false
    deciding.value = []
  }

  watch(() => [user.isLoggedIn, user.userInfo?.id, user.userInfo?.familyId, user.userInfo?.role].join('/'), reset, { flush: 'sync' })

  async function refreshStatus() {
    if (!eligible.value) return null
    if (statusRequest) return statusRequest
    const gen = generation
    statusRequest = assistantApi
      .status()
      .then((value) => {
        if (gen !== generation) return null
        status.value = value
        if (!value.available || !value.asrAvailable) discardVoice()
        return value
      })
      .catch((e) => {
        if (gen === generation) {
          status.value = null
          error.value = e.message
        }
        return null
      })
      .finally(() => {
        if (gen === generation) statusRequest = null
      })
    return statusRequest
  }

  async function refreshPending() {
    if (!eligible.value) return
    const gen = generation
    try {
      const data = await assistantApi.pending()
      if (gen === generation) pending.value = Number(data.count) || 0
    } catch {
      /* Reconciled on the next visit. */
    }
  }

  async function load(older = false) {
    if (!eligible.value || busy.value) return
    if (older && !hasOlder.value) return
    const gen = generation
    loading.value = true
    error.value = ''
    try {
      const data = await assistantApi.conversation(older ? { beforeId: messages.value[0].id } : undefined)
      if (gen === generation) {
        const rows = data.messages || []
        messages.value = older ? [...rows, ...messages.value] : rows
        hasOlder.value = rows.length >= 100 && messages.value.length < 200
      }
    } catch (e) {
      if (gen === generation) error.value = e.message
    } finally {
      if (gen === generation) loading.value = false
    }
  }

  async function open() {
    if (!eligible.value) return
    active.value = true
    const available = await refreshStatus()
    if (active.value && available) await Promise.all([load(), refreshPending()])
  }

  async function send(pageContext) {
    const text = draft.value.trim()
    if (!text || busy.value || !status.value?.available || voice.value !== 'idle') return
    const gen = generation
    sending.value = text
    error.value = draft.value = ''
    try {
      const result = await assistantApi.chat({ text, pageContext })
      if (gen !== generation) return
      const actions = result.pendingActions || []
      messages.value.push(
        { id: 'local-' + ++userMessageKey, role: 'USER', content: text },
        {
          id: result.messageId,
          role: 'ASSISTANT',
          content: result.reply,
          toolRuns: result.toolRuns,
          pendingActions: actions,
          notices: result.notices,
        },
      )
      messages.value = messages.value.slice(-200)
      pending.value += actions.filter((a) => a.status === 'PENDING').length
    } catch (e) {
      if (gen === generation) {
        draft.value = text
        error.value = e.message
        await refreshStatus()
      }
    } finally {
      if (gen === generation) sending.value = ''
    }
  }

  async function decide(action, confirm, visibility) {
    if (busy.value || !eligible.value || (confirm && !status.value?.enabled) || action.status !== 'PENDING') return
    const gen = generation
    deciding.value.push(action.id)
    try {
      const change = action.toolName === 'create_diary' ? { visibility } : {}
      const result = confirm ? await assistantApi.confirm(action.id, change) : await assistantApi.cancel(action.id)
      if (gen !== generation) return
      Object.assign(action, result)
      if (action.status !== 'PENDING') pending.value = Math.max(0, pending.value - 1)
    } catch (e) {
      if (gen === generation) {
        error.value = e.message
        await refreshStatus()
      }
    } finally {
      if (gen === generation) deciding.value = deciding.value.filter((id) => id !== action.id)
    }
  }

  async function clear() {
    if (!eligible.value || busy.value) return
    const gen = generation
    clearing.value = true
    try {
      await assistantApi.clear()
      if (gen === generation) {
        discardVoice()
        messages.value = []
        hasOlder.value = false
        pending.value = 0
        draft.value = error.value = ''
      }
    } catch (e) {
      if (gen === generation) error.value = e.message
    } finally {
      if (gen === generation) clearing.value = false
    }
  }

  /** options.autoSend:长按浮球说完即发(松手转写成功后直接发送);options.pageContext 随消息带上当前页 */
  async function startVoice(t, owner, options = {}) {
    if (!eligible.value || !active.value || take || sending.value) return
    const recording = { recorder: useVoiceRecorder(), started: false, timer: null, owner, options }
    take = recording
    voice.value = 'starting'
    error.value = ''
    const capability = await refreshStatus()
    if (take !== recording) {
      if (active.value && capability?.available && !capability.asrAvailable) error.value = t('chat.assistant.noAsr')
      return
    }
    if (!capability?.available || !capability.asrAvailable) {
      discardVoice()
      return
    }
    try {
      await recording.recorder.start()
      if (take !== recording) {
        await recording.recorder.stop()
        return
      }
      recording.started = true
      voice.value = 'recording'
      recording.timer = setTimeout(() => finishVoice(t), 60000)
    } catch {
      await recording.recorder.stop()
      if (take === recording) {
        discardVoice()
        error.value = t('chat.assistant.micFailed')
      }
    }
  }

  async function finishVoice(t, owner, options) {
    const recording = take
    if (owner && recording?.owner !== owner) return
    if (!recording || voice.value === 'transcribing') return
    if (!recording.started) {
      discardVoice()
      return
    }
    const opts = options || recording.options || {}
    clearTimeout(recording.timer)
    recording.started = false
    voice.value = 'transcribing'
    let said = ''
    try {
      const blob = await recording.recorder.stop()
      if (take !== recording) return
      if (blob.size <= 44) throw new Error()
      const result = await assistantApi.transcribe(new File([blob], 'voice.wav', { type: blob.type }))
      if (take !== recording) return
      if (result.text?.trim()) {
        said = result.text.trim()
        draft.value = [draft.value.trim(), said].filter(Boolean).join(' ')
      } else error.value = t('chat.assistant.noSpeech')
    } catch {
      if (take === recording) error.value = t('chat.assistant.noSpeech')
    } finally {
      if (take === recording) discardVoice()
    }
    // 长按浮球:松手转写成功即发,不必再点一次发送;输入区麦克风仍只填输入框,可先编辑
    if (said && opts.autoSend) await send(opts.pageContext)
  }

  return {
    eligible,
    active,
    status,
    messages,
    pending,
    draft,
    sending,
    loading,
    hasOlder,
    clearing,
    deciding,
    busy,
    error,
    voice,
    open,
    load,
    refreshStatus,
    refreshPending,
    leave,
    reset,
    send,
    decide,
    clear,
    startVoice,
    finishVoice,
    discardVoice,
  }
})
