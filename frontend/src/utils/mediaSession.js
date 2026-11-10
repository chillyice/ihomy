// 锁屏 / 耳机键播控(Media Session):只做「让系统知道在放什么、并能控制」,
// 不做后台续播(那是 B2-L2,已明确不做)。Web 与壳内 WebView 都支持;不支持时静默跳过。
import { assetUrl } from './assetUrl'

const ACTIONS = ['play', 'pause', 'previoustrack', 'nexttrack', 'seekto', 'seekbackward', 'seekforward', 'stop']
const POSITION_THROTTLE_MS = 1000

let activeOwner = ''
let lastPosAt = 0
let lastPosSig = ''

export function mediaSessionSupported() {
  return typeof navigator !== 'undefined' && 'mediaSession' in navigator && typeof MediaMetadata !== 'undefined'
}

function setHandler(action, fn) {
  try {
    navigator.mediaSession.setActionHandler(action, typeof fn === 'function' ? fn : null)
  } catch (e) {
    /* 个别动作在当前浏览器不支持:忽略该动作,不影响其它控制 */
  }
}

function absolute(url) {
  if (!url) return ''
  try {
    return new URL(assetUrl(String(url)), location.origin).href
  } catch (e) {
    return ''
  }
}

function seekBy(audioEl, delta) {
  if (!audioEl) return
  const dur = Number(audioEl.duration)
  const max = Number.isFinite(dur) && dur > 0 ? dur : Number.MAX_SAFE_INTEGER
  audioEl.currentTime = Math.max(0, Math.min(max, (audioEl.currentTime || 0) + delta))
}

/**
 * 把某个播放器接到系统媒体会话上。
 * @param {HTMLAudioElement} audioEl 正在播放的 audio 元素
 * @param {{title?:string,artist?:string,album?:string,coverUrl?:string}} track
 * @param {{prev?:Function,next?:Function}} handlers 有上一首/下一首能力时才传
 * @param {string} owner 哪个播放器持有会话(浮窗 / 页内试听),关闭时只清自己那份
 * @returns {boolean} 是否成功绑定
 */
export function bindMediaSession(audioEl, track, handlers = {}, owner = 'default') {
  if (!audioEl || !mediaSessionSupported()) return false
  const title = String(track?.title || '').trim()
  if (!title) {
    clearMediaSession(owner)
    return false
  }
  activeOwner = owner
  lastPosAt = 0
  lastPosSig = ''

  const cover = absolute(track.coverUrl)
  const meta = {
    title,
    artist: track.artist || '',
    album: track.album || '',
    ...(cover ? { artwork: [{ src: cover }] } : {}),
  }
  try {
    navigator.mediaSession.metadata = new MediaMetadata(meta)
  } catch (e) {
    return false
  }

  setHandler('play', () => {
    const p = audioEl.play?.()
    if (p && typeof p.catch === 'function') p.catch(() => {})
  })
  setHandler('pause', () => audioEl.pause?.())
  setHandler('previoustrack', handlers.prev)
  setHandler('nexttrack', handlers.next)
  setHandler('seekto', (details) => {
    const t = Number(details?.seekTime)
    if (!Number.isFinite(t) || t < 0) return
    const dur = Number(audioEl.duration)
    audioEl.currentTime = Number.isFinite(dur) && dur > 0 ? Math.min(dur, t) : t
    syncPlaybackState(audioEl)
    updatePosition(audioEl, true)
  })
  setHandler('seekbackward', (details) => {
    seekBy(audioEl, -Math.abs(Number(details?.seekOffset) || 10))
    updatePosition(audioEl, true)
  })
  setHandler('seekforward', (details) => {
    seekBy(audioEl, Math.abs(Number(details?.seekOffset) || 10))
    updatePosition(audioEl, true)
  })
  setHandler('stop', () => {
    audioEl.pause?.()
    audioEl.currentTime = 0
    syncPlaybackState(audioEl)
  })

  syncPlaybackState(audioEl)
  updatePosition(audioEl, true)
  return true
}

/** 播放/暂停/切歌后同步系统显示的状态 */
export function syncPlaybackState(audioEl) {
  if (!mediaSessionSupported()) return
  try {
    navigator.mediaSession.playbackState = audioEl && !audioEl.paused ? 'playing' : 'paused'
  } catch (e) {
    /* 忽略 */
  }
}

/** 进度同步:节流到 1 秒一次,只 push 合法值(有些流媒体 duration 为 Infinity) */
export function updatePosition(audioEl, force = false) {
  if (!audioEl || !mediaSessionSupported()) return
  const now = Date.now()
  if (!force && now - lastPosAt < POSITION_THROTTLE_MS) return
  const duration = Number(audioEl.duration)
  const position = Number(audioEl.currentTime)
  if (!Number.isFinite(duration) || duration <= 0) return
  if (!Number.isFinite(position) || position < 0) return
  const pos = Math.min(position, duration)
  const sig = `${Math.round(duration * 10)}:${Math.round(pos * 10)}`
  if (sig === lastPosSig) return
  lastPosAt = now
  lastPosSig = sig
  try {
    navigator.mediaSession.setPositionState({ duration, position: pos, playbackRate: audioEl.playbackRate || 1 })
  } catch (e) {
    /* 位置状态是锦上添花,失败不影响播放 */
  }
}

/** 清空会话;传 owner 时只在会话仍归它所有才清(避免关掉试听弹窗把后台播放器的会话也清了) */
export function clearMediaSession(owner) {
  if (!mediaSessionSupported()) return
  if (owner && activeOwner && owner !== activeOwner) return
  ACTIONS.forEach((action) => setHandler(action, null))
  try {
    navigator.mediaSession.metadata = null
    navigator.mediaSession.playbackState = 'none'
  } catch (e) {
    /* 忽略 */
  }
  activeOwner = ''
  lastPosAt = 0
  lastPosSig = ''
}
