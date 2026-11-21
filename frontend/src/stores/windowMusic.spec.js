import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { reactive } from 'vue'
import { musicApi } from '@/api'
import { useWindowMusicStore } from './windowMusic'

let user
let elements
vi.mock('@/api', () => ({ musicApi: { getBackground: vi.fn(), playUrl: vi.fn() } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => user }))
vi.mock('@/utils/mediaSession', () => ({
  bindMediaSession: vi.fn(),
  clearMediaSession: vi.fn(),
  syncPlaybackState: vi.fn(),
  updatePosition: vi.fn(),
}))
class MockAudio {
  constructor() {
    elements.push(this)
    this.paused = true
  }
  getAttribute() {
    return this.src
  }
  removeAttribute() {
    this.src = ''
  }
  play() {
    this.paused = false
    this.onplay?.()
    return Promise.resolve()
  }
  pause() {
    this.paused = true
    this.onpause?.()
  }
  load() {}
}
const tracks = [
  { id: 1, title: 'One', url: '/one.mp3' },
  { id: 2, title: 'Two', url: '/two.mp3' },
]
describe('window music', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    user = reactive({ isLoggedIn: true, userInfo: { id: 7, familyId: 3 } })
    elements = []
    vi.stubGlobal('Audio', MockAudio)
  })
  afterEach(() => vi.unstubAllGlobals())
  it('lets a selection win over an in-flight background request and a disabled background preference', async () => {
    const music = useWindowMusicStore()
    let done
    musicApi.getBackground.mockReturnValue(
      new Promise((resolve) => {
        done = resolve
      }),
    )
    const loading = music.background(true)
    await music.choose(tracks, 2)
    done({ tracks: [tracks[0]] })
    await loading
    await music.background(false)
    expect(music.current.id).toBe(2)
    expect(music.playing).toBe(true)
    await music.step(1)
    expect(music.current.id).toBe(1)
    expect(elements.filter((el) => !el.paused)).toHaveLength(1)
  })
  it('remembers explicit pause during an interruption and otherwise resumes the same audio element', async () => {
    const music = useWindowMusicStore()
    await music.choose(tracks, 1)
    music.setInterrupted(true)
    expect(music.playing).toBe(false)
    music.setInterrupted(false)
    expect(music.playing).toBe(true)
    music.setInterrupted(true)
    music.pause()
    music.setInterrupted(false)
    expect(music.playing).toBe(false)
    expect(elements).toHaveLength(1)
  })
  it('drops a signed URL arriving after a family switch and skips guest requests', async () => {
    const music = useWindowMusicStore()
    let done
    musicApi.playUrl.mockReturnValue(
      new Promise((resolve) => {
        done = resolve
      }),
    )
    const loading = music.choose([{ id: 4, url: 'storage://device/song' }], 4)
    user.userInfo.familyId = 9
    done({ url: '/old-family.mp3' })
    await loading
    expect(music.queue).toEqual([])
    expect(elements.every((el) => el.paused && !el.src)).toBe(true)
    user.isLoggedIn = false
    await music.background(true)
    expect(musicApi.getBackground).not.toHaveBeenCalled()
  })
})
