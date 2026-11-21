import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { musicApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { bindMediaSession, clearMediaSession, syncPlaybackState, updatePosition } from '@/utils/mediaSession'

/** One audio owner for library selections, the mini player and the system transport. */
export const useWindowMusicStore = defineStore('windowMusic', () => {
  const user = useUserStore()
  const scope = computed(() =>
    user.isLoggedIn && user.userInfo?.id && user.userInfo?.familyId ? [user.userInfo.id, user.userInfo.familyId].join('/') : '',
  )
  const queue = ref([])
  const index = ref(0)
  const current = computed(() => queue.value[index.value])
  const explicit = ref(false)
  const playing = ref(false)
  const wantsPlay = ref(false)
  const interrupted = ref(false)
  const error = ref('')
  const position = ref(0)
  const duration = ref(0)
  let audio = null
  let generation = 0
  let bgGeneration = 0

  function release() {
    generation++
    if (audio) {
      audio.onplay = audio.onpause = audio.ontimeupdate = audio.onloadedmetadata = audio.onended = audio.onerror = null
      audio.pause()
      audio.removeAttribute('src')
      audio.load()
      audio = null
    }
    playing.value = false
    position.value = duration.value = 0
    clearMediaSession('window-music')
  }
  function reset() {
    bgGeneration++
    release()
    queue.value = []
    index.value = 0
    explicit.value = wantsPlay.value = false
    error.value = ''
  }
  watch(scope, reset, { flush: 'sync' })

  function pause() {
    wantsPlay.value = false
    audio?.pause()
  }
  async function resume() {
    if (!current.value || !scope.value) return
    wantsPlay.value = true
    if (interrupted.value) return
    if (!audio || error.value === 'music.playFailed') return load(true)
    if (!audio.getAttribute('src')) return
    const active = audio
    const gen = generation
    try {
      await active.play()
    } catch (e) {
      if (gen !== generation || interrupted.value || !wantsPlay.value) return
      wantsPlay.value = false
      error.value = e?.name === 'NotAllowedError' ? 'windowTools.listen' : 'music.playFailed'
    }
  }
  async function load(autoplay) {
    release()
    error.value = ''
    wantsPlay.value = autoplay
    const track = current.value
    if (!track || !scope.value) return
    const gen = generation
    const el = new Audio()
    audio = el
    el.preload = 'metadata'
    const state = () => {
      if (gen !== generation) return
      if (interrupted.value && !el.paused) el.pause()
      playing.value = !el.paused
      syncPlaybackState(el)
    }
    el.onplay = () => {
      error.value = ''
      state()
    }
    el.onpause = state
    el.onended = () => step(1, true)
    el.ontimeupdate = () => {
      position.value = el.currentTime
      updatePosition(el)
    }
    el.onloadedmetadata = () => {
      duration.value = Number.isFinite(el.duration) ? el.duration : 0
      updatePosition(el, true)
    }
    el.onerror = () => {
      if (gen === generation) {
        error.value = 'music.playFailed'
        pause()
      }
    }
    try {
      const url =
        track.sourceDeviceId || String(track.url || '').startsWith('storage://') ? (await musicApi.playUrl(track.id)).url : track.url
      if (gen !== generation) return
      if (!url) throw new Error('Empty music URL')
      el.src = url
      bindMediaSession(el, track, { play: resume, pause, next: () => step(1), prev: () => step(-1) }, 'window-music')
      if (wantsPlay.value && !interrupted.value) await resume()
    } catch {
      if (gen === generation) {
        error.value = 'music.playFailed'
        pause()
      }
    }
  }
  function choose(tracks, id) {
    if (!scope.value) return
    const selected = tracks.findIndex((track) => track.id === id)
    if (selected < 0) return
    bgGeneration++
    queue.value = [...tracks]
    index.value = selected
    explicit.value = true
    return load(true)
  }
  function step(delta, autoplay = wantsPlay.value) {
    if (!queue.value.length) return
    index.value = (index.value + delta + queue.value.length) % queue.value.length
    return load(autoplay)
  }
  function seek(value) {
    if (audio && duration.value) audio.currentTime = Math.min(duration.value, Math.max(0, Number(value)))
  }
  function setInterrupted(value) {
    if (interrupted.value === value) return
    interrupted.value = value
    if (value) audio?.pause()
    else if (wantsPlay.value) resume()
  }
  async function background(enabled) {
    const request = ++bgGeneration
    if (explicit.value) return
    if (!scope.value || !enabled) {
      reset()
      return
    }
    try {
      const data = await musicApi.getBackground()
      if (request !== bgGeneration || explicit.value) return
      release()
      queue.value = (data?.tracks || []).filter((t) => t?.url || t?.sourceDeviceId)
      index.value = 0
      wantsPlay.value = false
      error.value = ''
    } catch {
      if (request === bgGeneration) reset()
    }
  }
  return {
    scope,
    queue,
    index,
    current,
    explicit,
    playing,
    wantsPlay,
    interrupted,
    position,
    duration,
    error,
    choose,
    step,
    resume,
    pause,
    seek,
    setInterrupted,
    background,
    reset,
  }
})
