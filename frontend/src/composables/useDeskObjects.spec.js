import { createRenderer, nextTick, reactive, ref } from 'vue'
import { expect, it, vi } from 'vitest'
import { planApi, notificationApi } from '@/api'
import { activityRevision, recordActivityWrite } from '@/api/activity'
import { useDeskObjects } from './useDeskObjects'

const user = reactive({ isLoggedIn: true, userInfo: { id: 8, familyId: 1 } })
const home = reactive({
  familyLoaded: true,
  family: { id: 1 },
  generation: 1,
  modules: [{ code: 'plan' }, { code: 'chat' }],
  photos: [],
  photosRevision: 0,
})
const chat = reactive({ totalUnread: 5 })
const route = reactive({ query: {} })
vi.mock('@/stores/user', () => ({ useUserStore: () => user }))
vi.mock('@/stores/app', () => ({ useAppStore: () => home }))
vi.mock('@/stores/chat', () => ({ useChatStore: () => chat }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key) => key }) }))
vi.mock('@/api', () => ({ planApi: { today: vi.fn() }, notificationApi: { unreadCount: vi.fn() } }))
vi.mock('./useDisplayMode', () => ({ useDisplayMode: () => ({ isChild: ref(false) }) }))
vi.mock('@/utils/assetUrl', () => ({ NEED_FILE_ORIGIN: false, SERVER_ORIGIN: '' }))

it('drops delayed results for old families, withdraws read and completed objects and skips requests for visitors', async () => {
  vi.stubGlobal('document', Object.assign(new EventTarget(), { hidden: false }))
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('location', { origin: 'https://family.test' })
  const late = Promise.withResolvers()
  const read = Promise.withResolvers()
  planApi.today
    .mockReturnValueOnce(late.promise)
    .mockResolvedValueOnce({ plans: [{ id: 2 }] })
    .mockResolvedValue({ plans: [] })
  notificationApi.unreadCount.mockReturnValueOnce(read.promise).mockResolvedValueOnce(2).mockResolvedValue(0)
  let candidates
  const enabled = ref(false)
  const redraw = ref(0)
  const renderer = createRenderer({
    createComment: () => ({}),
    insert: () => {},
    remove: () => {},
    parentNode: () => null,
    nextSibling: () => null,
  })
  const harness = renderer.createApp({
    setup() {
      candidates = useDeskObjects(enabled, {}, redraw).candidates
      return () => null
    },
  })
  const settle = async () => {
    await nextTick()
    await Promise.resolve()
    await nextTick()
  }
  const find = (kind) => candidates.value.find((row) => row.kind === kind)
  try {
    harness.mount({})
    expect(planApi.today).not.toHaveBeenCalled()
    enabled.value = true
    await settle()
    expect(planApi.today).toHaveBeenCalledTimes(1)
    home.family.id = user.userInfo.familyId = 2
    home.generation++
    await settle()
    late.resolve({ plans: [{ id: 901 }] })
    read.resolve(99)
    await settle()
    expect(find('note').key).toBe('plan:2')
    expect(find('envelope').labelParams).toEqual({ count: 2 })
    expect(find('phone')).toBeDefined()
    redraw.value++
    route.query = { tab: 'today' }
    await settle()
    expect(planApi.today).toHaveBeenCalledTimes(2)
    recordActivityWrite({ method: 'put', url: '/notification/read-all' })
    recordActivityWrite({ method: 'put', url: '/plan/2' })
    chat.totalUnread = 0
    await settle()
    expect(find('note')).toBeUndefined()
    expect(find('envelope')).toBeUndefined()
    expect(find('phone')).toBeUndefined()
    route.query = { hid: 'another-household' }
    chat.totalUnread = 5
    activityRevision.agenda++
    await settle()
    expect(find('phone')).toBeUndefined()
    expect(planApi.today).toHaveBeenCalledTimes(3)
    user.isLoggedIn = false
    route.query = {}
    await settle()
    expect(find('phone')).toBeUndefined()
    expect(notificationApi.unreadCount).toHaveBeenCalledTimes(3)
  } finally {
    harness.unmount()
    vi.unstubAllGlobals()
  }
})
