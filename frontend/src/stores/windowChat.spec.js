import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from './chat'
import { useItemSearchStore } from './itemSearch'

let user, assistant
vi.mock('@/api', () => ({ chatApi: {} }))
vi.mock('@/api/request', () => ({ ensureFreshToken: vi.fn() }))
vi.mock('@/stores/call', () => ({ useCallStore: vi.fn() }))
vi.mock('@/stores/user', () => ({ useUserStore: () => user }))
vi.mock('@/stores/assistant', () => ({ useAssistantStore: () => assistant }))

describe('private working state', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    user = reactive({ isLoggedIn: true, userInfo: { id: 8, familyId: 10, role: 'MEMBER' } })
    assistant = reactive({ active: false, reset: vi.fn() })
  })
  it('separates typed text for each family, user and human recipient from the assistant', () => {
    const chat = useChatStore()
    chat.setDraft('family group')
    chat.activePeer = 9
    chat.setDraft('private')
    chat.activePeer = null
    expect(chat.drafts[chat.draftKey]).toBe('family group')
    user.userInfo.familyId = 11
    expect(chat.drafts[chat.draftKey]).toBeUndefined()
    user.userInfo.familyId = 10
    chat.activePeer = 9
    expect(chat.drafts[chat.draftKey]).toBe('private')
    assistant.active = true
    chat.setDraft('wrong destination')
    expect(chat.draftKey).toBe('')
    chat.reset()
    expect(chat.drafts).toEqual({})
  })
  it('drops both the search terms and selected item when membership changes', () => {
    const search = useItemSearchStore()
    search.query = search.submitted = 'umbrella'
    search.selection = 123
    user.userInfo.familyId = 11
    expect([search.query, search.submitted, search.selection]).toEqual(['', '', null])
  })
  it('counts an incoming family message while the member is reading a direct conversation', () => {
    const chat = useChatStore()
    chat.activePeer = 9
    chat.chatPageActive = true
    chat.onIncoming({ id: 42, familyId: 10, senderId: 9, content: 'for the family' })
    expect(chat.unread).toBe(1)
  })
})
