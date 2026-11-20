import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { assistantApi } from '@/api'
import { useAssistantStore } from './assistant'

let user
const recorder = vi.hoisted(() => ({ start: vi.fn(), stop: vi.fn() }))
vi.mock('@/composables/useVoiceRecorder', () => ({ useVoiceRecorder: () => recorder }))
vi.mock('@/stores/user', () => ({ useUserStore: () => user }))
vi.mock('@/api', () => ({
  assistantApi: {
    status: vi.fn(),
    conversation: vi.fn(),
    pending: vi.fn(),
    chat: vi.fn(),
    confirm: vi.fn(),
    cancel: vi.fn(),
    clear: vi.fn(),
    transcribe: vi.fn(),
  },
}))

describe('assistant conversation', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    user = reactive({ isLoggedIn: true, isPureOps: false, userInfo: { id: 8, familyId: 1, role: 'MEMBER' } })
    assistantApi.status.mockResolvedValue({ enabled: true, available: true, asrAvailable: true })
    assistantApi.conversation.mockResolvedValue({ messages: [] })
    assistantApi.pending.mockResolvedValue({ count: 0 })
  })

  it('discards a late reply, pending cards and draft after switching families', async () => {
    const store = useAssistantStore()
    await store.open()
    let resolve
    assistantApi.chat.mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    store.draft = 'save a diary entry'
    const sent = store.send({})
    user.userInfo.familyId = 2
    resolve({ reply: 'ready', pendingActions: [{ status: 'PENDING' }] })
    await sent
    expect(store.messages).toEqual([])
    expect(store.active).toBe(false)
    expect(store.pending).toBe(0)
    expect(store.draft).toBe('')
  })

  it('allows selecting the diary audience and sends each pending card only once', async () => {
    const store = useAssistantStore()
    await store.open()
    const card = { id: 42, toolName: 'create_diary', status: 'PENDING' }
    assistantApi.confirm.mockResolvedValue({ status: 'DONE', result: { id: 99 } })
    await Promise.all([store.decide(card, true, 'FAMILY'), store.decide(card, true, 'FAMILY')])
    await store.decide(card, true, 'FAMILY')
    expect(assistantApi.confirm).toHaveBeenCalledExactlyOnceWith(42, { visibility: 'FAMILY' })
    expect(card.status).toBe('DONE')
  })

  it('blocks chat and confirm after disablement but can cancel a pending card', async () => {
    assistantApi.status.mockResolvedValue({ enabled: false, available: false })
    const store = useAssistantStore()
    await store.open()
    const card = { id: 42, status: 'PENDING', toolName: 'create_reminder' }
    store.draft = 'hello'
    await store.send({})
    await store.decide(card, true)
    expect(assistantApi.chat).not.toHaveBeenCalled()
    expect(assistantApi.confirm).not.toHaveBeenCalled()
    assistantApi.cancel.mockResolvedValue({ status: 'CANCELLED' })
    await store.decide(card, false)
    expect(card.status).toBe('CANCELLED')
  })

  it('stops a late microphone grant after release, without sending or transcribing', async () => {
    const store = useAssistantStore()
    await store.open()
    let grant
    recorder.start.mockReturnValue(
      new Promise((done) => {
        grant = done
      }),
    )
    const start = store.startVoice((s) => s)
    await vi.waitFor(() => expect(recorder.start).toHaveBeenCalled())
    await store.finishVoice((s) => s)
    grant()
    await start
    expect(recorder.stop).toHaveBeenCalledOnce()
    expect(store.voice).toBe('idle')
    expect(assistantApi.transcribe).not.toHaveBeenCalled()
  })

  it('only appends a transcript to the draft', async () => {
    const store = useAssistantStore()
    await store.open()
    recorder.start.mockResolvedValue()
    recorder.stop.mockResolvedValue(new Blob([new Uint8Array(100)], { type: 'audio/wav' }))
    assistantApi.transcribe.mockResolvedValue({ text: 'somewhere' })
    store.draft = 'look'
    await store.startVoice((s) => s)
    await store.finishVoice((s) => s)
    expect(store.draft).toBe('look somewhere')
    expect(assistantApi.chat).not.toHaveBeenCalled()
  })
})
