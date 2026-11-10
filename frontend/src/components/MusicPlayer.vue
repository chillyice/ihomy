<!-- 悬浮音乐播放器:整体可拖拽,展开/收缩带动画,左下角不动;播放当前家庭绑定的背景音乐歌单 -->
<template>
  <div
    v-if="playlist.length"
    ref="playerEl"
    class="music-player"
    :class="{ expanded }"
    :style="{ left: pos.x + 'px', bottom: pos.bottom + 'px' }"
  >
    <!-- 黑胶可视化 + 展开切换:整个黑胶区就是拖拽手柄,鼠标与触屏都从这里拖 -->
    <div v-a11y-click class="player-left" @pointerdown="onDragStart" @click.stop="onVinylClick">
      <div class="vinyl" :class="{ playing }">
        <div class="vinyl-disc">
          <div class="vinyl-groove"></div>
          <div class="vinyl-label"></div>
        </div>
        <div class="vinyl-arm"></div>
      </div>
    </div>

    <!-- 展开内容:歌名+控件+进度条+歌单(带收缩动画) -->
    <transition name="player-expand">
      <div v-show="expanded" class="player-body" @click.stop>
        <div class="track-info">
          <div class="track-title" :title="currentTrack.title">{{ currentTrack.title || t('music.unknownTrack') }}</div>
          <div class="track-sub">{{ playing ? t('music.playerPlaying') : t('music.playerPaused') }}</div>
        </div>

        <!-- 进度条 -->
        <div class="progress-row">
          <span class="time">{{ formatTime(currentTime) }}</span>
          <div v-a11y-click class="progress-bar" @click="onSeek">
            <div class="progress-buffered" :style="{ width: bufferedPct + '%' }"></div>
            <div class="progress-played" :style="{ width: playedPct + '%' }"></div>
          </div>
          <span class="time">{{ formatTime(duration) }}</span>
        </div>

        <!-- 控件 -->
        <div class="controls">
          <button class="ctrl-btn" @click="prev" :title="t('music.prevTrack')">
            <el-icon><CaretLeft /></el-icon>
          </button>
          <button class="ctrl-btn ctrl-main" @click="togglePlay" :title="playing ? t('music.pauseToggle') : t('music.playToggle')">
            <el-icon><VideoPause v-if="playing" /><VideoPlay v-else /></el-icon>
          </button>
          <button class="ctrl-btn" @click="next" :title="t('music.nextTrack')">
            <el-icon><CaretRight /></el-icon>
          </button>
          <button class="ctrl-btn ctrl-list" @click="showList = !showList" :title="t('music.playlists')">
            <el-icon><List /></el-icon>
          </button>
        </div>

        <!-- 歌单 -->
        <div v-if="showList" class="playlist">
          <div
            v-a11y-click
            v-for="(t, i) in playlist"
            :key="t.id || i"
            class="playlist-item"
            :class="{ active: i === trackIdx }"
            @click="selectTrack(i)"
          >
            <span class="pl-idx">{{ i + 1 }}</span>
            <span class="pl-title" :title="t.title">{{ t.title || t('music.unknownTrack') }}</span>
            <el-icon v-if="i === trackIdx && playing" class="pl-playing"><VideoPlay /></el-icon>
          </div>
        </div>
      </div>
    </transition>

    <audio
      ref="audioEl"
      :src="playSrc"
      @ended="next"
      @play="onPlayState"
      @pause="onPlayState"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMeta"
      @progress="onProgress"
      preload="auto"
    ></audio>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { musicApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { useI18n } from 'vue-i18n'
import { CaretLeft, CaretRight, VideoPlay, VideoPause, List } from '@element-plus/icons-vue'
import { bindMediaSession, clearMediaSession, syncPlaybackState, updatePosition } from '@/utils/mediaSession'

const { t } = useI18n()
const userStore = useUserStore()
const audioEl = ref(null)
const playlist = ref([])
const trackIdx = ref(0)
const playing = ref(false)
const expanded = ref(false)
const showList = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const buffered = ref(0)

// 拖拽位置(bottom 定位,左下角不动);移动端默认抬到 56px 底栏之上,避免压住 Tab 点击区
const { isMobile } = useDevice()
const playerEl = ref(null)
const MOBILE_MIN_BOTTOM = 72
const DEFAULT_POS = { x: 240, bottom: 24 }
const pos = ref({ ...DEFAULT_POS })
try {
  const saved = JSON.parse(localStorage.getItem('ihomy:music:pos') || 'null')
  if (saved && Number.isFinite(saved.x) && Number.isFinite(saved.bottom)) pos.value = saved
} catch (e) {}

let drag = null
let suppressClickUntil = 0
let expandClampTimer = 0

// 位置永远留在视口内:换屏/旋转/展开成宽面板后都不会跑到屏幕外
const clampPos = () => {
  const el = playerEl.value
  if (!el) return
  const minBottom = isMobile.value ? MOBILE_MIN_BOTTOM : 8
  const maxX = Math.max(8, window.innerWidth - el.offsetWidth - 8)
  const maxBottom = Math.max(minBottom, window.innerHeight - el.offsetHeight - 8)
  pos.value = {
    x: Math.min(Math.max(8, pos.value.x), maxX),
    bottom: Math.min(Math.max(minBottom, pos.value.bottom), maxBottom),
  }
}

const detachDrag = () => {
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragUp)
  window.removeEventListener('pointercancel', onDragUp)
}

const onDragStart = (e) => {
  if (e.pointerType === 'mouse' && e.button !== 0) return
  const rect = playerEl.value?.getBoundingClientRect()
  if (!rect) return
  drag = {
    pointerId: e.pointerId,
    startX: e.clientX,
    startY: e.clientY,
    left: rect.left,
    bottom: window.innerHeight - rect.bottom,
    width: rect.width,
    height: rect.height,
    moved: false,
  }
  // 只在真按下手柄时挂监听,松手即摘;触屏移动量小于 6px 仍按点击处理(展开/收起)
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragUp)
  window.addEventListener('pointercancel', onDragUp)
}

const onDragMove = (e) => {
  if (!drag || e.pointerId !== drag.pointerId) return
  const dx = e.clientX - drag.startX
  const dy = e.clientY - drag.startY
  if (!drag.moved && Math.hypot(dx, dy) < 6) return
  drag.moved = true
  const minBottom = isMobile.value ? MOBILE_MIN_BOTTOM : 8
  pos.value = {
    x: Math.min(Math.max(8, drag.left + dx), Math.max(8, window.innerWidth - drag.width - 8)),
    bottom: Math.min(Math.max(minBottom, drag.bottom - dy), Math.max(minBottom, window.innerHeight - drag.height - 8)),
  }
}

const onDragUp = (e) => {
  if (!drag || e.pointerId !== drag.pointerId) return
  const moved = drag.moved
  drag = null
  detachDrag()
  if (!moved) return
  // 拖动结束浏览器会补发一次 click,短暂屏蔽,避免松手就展开/收起
  suppressClickUntil = Date.now() + 350
  clampPos()
  try {
    localStorage.setItem('ihomy:music:pos', JSON.stringify(pos.value))
  } catch (e) {
    /* 无痕环境存不了就仅本次会话生效 */
  }
}

const onVinylClick = () => {
  if (Date.now() < suppressClickUntil) return
  expanded.value = !expanded.value
}

// 展开后播放器会变宽:动画结束再夹一次,保证右边缘不出屏;收起时同理
watch(expanded, () => {
  clearTimeout(expandClampTimer)
  nextTick(() => {
    requestAnimationFrame(clampPos)
    expandClampTimer = setTimeout(clampPos, 380)
  })
})

const currentTrack = computed(() => playlist.value[trackIdx.value] || {})
const playedPct = computed(() => (duration.value ? (currentTime.value / duration.value) * 100 : 0))
const bufferedPct = computed(() => (duration.value ? (buffered.value / duration.value) * 100 : 0))

// 播放地址:设备映射曲目 url 为 storage:// 逻辑地址,切歌时现签(签名 10 分钟过期);本地/外链直用
const playSrc = ref('')
watch(
  currentTrack,
  async (track) => {
    if (!track?.url) {
      playSrc.value = ''
      return
    }
    if (!String(track.url).startsWith('storage://')) {
      playSrc.value = track.url
      return
    }
    const id = track.id
    try {
      const { url } = await musicApi.playUrl(id)
      if (currentTrack.value?.id === id) playSrc.value = url // 防快速切歌竞态
    } catch {
      if (currentTrack.value?.id === id) playSrc.value = ''
    }
  },
  { immediate: true },
)

const loadBackgroundPlaylist = async () => {
  if (!userStore.isLoggedIn) {
    playlist.value = []
    return
  }
  try {
    const data = await musicApi.getBackground()
    const tracks = data?.tracks || []
    playlist.value = tracks.filter((t) => t && t.url)
    if (trackIdx.value >= playlist.value.length) trackIdx.value = 0
    if (playlist.value.length) {
      await nextTick()
      if (audioEl.value) audioEl.value.load()
    }
  } catch (e) {
    playlist.value = []
  }
}

const togglePlay = () => {
  if (!audioEl.value || !currentTrack.value.url) return
  if (playing.value) {
    audioEl.value.pause()
    playing.value = false
  } else {
    audioEl.value
      .play()
      .then(() => {
        playing.value = true
      })
      .catch(() => {
        playing.value = false
      })
  }
}

const next = () => {
  if (!playlist.value.length) return
  trackIdx.value = (trackIdx.value + 1) % playlist.value.length
  playAfterSwitch()
}
const prev = () => {
  if (!playlist.value.length) return
  trackIdx.value = (trackIdx.value - 1 + playlist.value.length) % playlist.value.length
  playAfterSwitch()
}
const selectTrack = (i) => {
  trackIdx.value = i
  playAfterSwitch()
}
const playAfterSwitch = () => {
  const wasPlaying = playing.value
  playing.value = false
  nextTick(() => {
    if (wasPlaying && audioEl.value) {
      audioEl.value
        .play()
        .then(() => {
          playing.value = true
        })
        .catch(() => {
          playing.value = false
        })
    }
  })
}

const onSeek = (e) => {
  if (!audioEl.value || !duration.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  const pct = (e.clientX - rect.left) / rect.width
  audioEl.value.currentTime = Math.max(0, Math.min(duration.value, pct * duration.value))
  currentTime.value = audioEl.value.currentTime
}
const onTimeUpdate = () => {
  if (audioEl.value) {
    currentTime.value = audioEl.value.currentTime
    if (audioEl.value.buffered.length) {
      buffered.value = audioEl.value.buffered.end(audioEl.value.buffered.length - 1)
    }
    updatePosition(audioEl.value)
  }
}
const onLoadedMeta = () => {
  if (audioEl.value) {
    duration.value = audioEl.value.duration || 0
    updatePosition(audioEl.value, true)
  }
}
const onPlayState = () => {
  if (!audioEl.value) return
  playing.value = !audioEl.value.paused
  syncPlaybackState(audioEl.value)
}
const onProgress = () => {
  if (audioEl.value && audioEl.value.buffered.length) {
    buffered.value = audioEl.value.buffered.end(audioEl.value.buffered.length - 1)
  }
}

// 锁屏 / 耳机键:曲目或播放地址一变就把系统媒体会话换成当前这首
watch(
  () => [playlist.value.length, currentTrack.value?.id, playSrc.value],
  async () => {
    if (!playlist.value.length || !playSrc.value) {
      clearMediaSession('float')
      return
    }
    await nextTick()
    const ok = bindMediaSession(audioEl.value, currentTrack.value, { prev, next }, 'float')
    if (ok) syncPlaybackState(audioEl.value)
  },
  { immediate: true },
)

const formatTime = (s) => {
  if (!s || isNaN(s)) return '0:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${sec.toString().padStart(2, '0')}`
}

// 登录态/家庭切换/背景歌单变更时重新加载
watch(() => userStore.isLoggedIn, loadBackgroundPlaylist)
watch(() => userStore.userInfo?.familyId, loadBackgroundPlaylist)
watch(() => userStore.bgMusicVersion, loadBackgroundPlaylist)
// 有歌单后播放器才渲染,渲染出来再校正一次位置(旧存档/小屏可能已越界)
watch(
  () => playlist.value.length,
  (n) => {
    if (n) nextTick(() => requestAnimationFrame(clampPos))
  },
)

onMounted(() => {
  loadBackgroundPlaylist()
  window.addEventListener('resize', clampPos)
})
onBeforeUnmount(() => {
  clearTimeout(expandClampTimer)
  detachDrag()
  window.removeEventListener('resize', clampPos)
  clearMediaSession('float')
})
</script>

<style scoped>
.music-player {
  position: fixed;
  z-index: var(--z-float);
  display: flex;
  align-items: flex-end;
  gap: 0;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(24px) saturate(1.3);
  -webkit-backdrop-filter: blur(24px) saturate(1.3);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 20px;
  box-shadow:
    0 12px 40px rgba(58, 46, 34, 0.18),
    0 2px 8px rgba(58, 46, 34, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.6),
    inset 0 -1px 0 rgba(58, 46, 34, 0.04);
  overflow: hidden;
  user-select: none;
  contain: layout style;
  transform: translateZ(0);
}
html.dark .music-player {
  background: rgba(var(--color-card-rgb), 0.65);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow:
    0 12px 40px rgba(0, 0, 0, 0.5),
    0 2px 8px rgba(0, 0, 0, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.player-left {
  padding: 12px;
  cursor: grab;
  flex-shrink: 0;
  /* 手柄上接管指针:触屏拖动时页面不跟着滚;body 区仍保留原生滚动与点击 */
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}
.player-left:active {
  cursor: grabbing;
}

/* 黑胶可视化 */
.vinyl {
  position: relative;
  width: 56px;
  height: 56px;
  transition: transform 0.3s ease;
}
.music-player:hover .vinyl {
  transform: scale(1.05);
}
.vinyl-disc {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, #3a2e22 0%, #1a1410 30%, #2a2018 60%, #1a1410 100%);
  box-shadow:
    0 4px 12px rgba(0, 0, 0, 0.4),
    inset 0 0 10px rgba(0, 0, 0, 0.5);
  border: 1px solid #5c4332;
}
.vinyl-groove {
  position: absolute;
  inset: 5px;
  border-radius: 50%;
  background: repeating-radial-gradient(circle, transparent 0 1px, rgba(245, 239, 224, 0.04) 1px 2px);
}
.vinyl-label {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 16px;
  height: 16px;
  margin: -8px 0 0 -8px;
  border-radius: 50%;
  background: radial-gradient(circle, #a8483a 0%, #6b2e26 100%);
  border: 1px solid #3a2e22;
}
.vinyl-arm {
  position: absolute;
  top: -3px;
  right: 10px;
  width: 3px;
  height: 28px;
  background: linear-gradient(to bottom, #c9a876, #8b6f47);
  border-radius: 2px;
  transform-origin: top center;
  transform: rotate(-15deg);
  transition: transform 0.4s ease;
}
.vinyl-arm::after {
  content: '';
  position: absolute;
  bottom: -3px;
  left: -2px;
  width: 6px;
  height: 6px;
  background: #c9a876;
  border-radius: 50%;
}
.vinyl.playing .vinyl-disc {
  animation: spin 4s linear infinite;
}
.vinyl.playing .vinyl-arm {
  transform: rotate(15deg);
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 展开内容 */
.player-body {
  width: 280px;
  padding: 10px 14px 12px 4px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.track-info {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 8px;
}
.track-title {
  font-size: 13px;
  font-weight: 600;
  color: #3a2e22;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
html.dark .track-title {
  color: #e8dcc8;
}
.track-sub {
  font-size: 11px;
  color: #7a6b5a;
  flex-shrink: 0;
}
html.dark .track-sub {
  color: #b0a898;
}

/* 进度条 */
.progress-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.time {
  font-size: 10px;
  color: #7a6b5a;
  min-width: 28px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
html.dark .time {
  color: #b0a898;
}
.progress-bar {
  flex: 1;
  height: 4px;
  background: rgba(58, 46, 34, 0.1);
  border-radius: 2px;
  position: relative;
  cursor: pointer;
  overflow: hidden;
}
html.dark .progress-bar {
  background: rgba(232, 220, 200, 0.1);
}
.progress-buffered {
  position: absolute;
  inset: 0;
  background: rgba(58, 46, 34, 0.12);
  border-radius: 2px;
}
html.dark .progress-buffered {
  background: rgba(232, 220, 200, 0.12);
}
.progress-played {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  background: linear-gradient(to right, #a8483a, #c9a876);
  border-radius: 2px;
  transition: width 0.1s linear;
}

/* 控件 */
.controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.ctrl-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: rgba(58, 46, 34, 0.06);
  border-radius: 50%;
  color: #3a2e22;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}
html.dark .ctrl-btn {
  background: rgba(232, 220, 200, 0.08);
  color: #e8dcc8;
}
.ctrl-btn:hover {
  background: rgba(58, 46, 34, 0.12);
  transform: scale(1.1);
}
html.dark .ctrl-btn:hover {
  background: rgba(232, 220, 200, 0.15);
}
.ctrl-main {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #a8483a, #6b2e26);
  color: #f5efe0;
  box-shadow: 0 4px 12px rgba(168, 72, 58, 0.3);
}
.ctrl-main:hover {
  background: linear-gradient(135deg, #b8584a, #7b3e36);
  transform: scale(1.1);
}
.ctrl-list {
  margin-left: 4px;
}

/* 歌单 */
.playlist {
  max-height: 180px;
  overflow-y: auto;
  border-top: 1px solid rgba(58, 46, 34, 0.08);
  padding-top: 6px;
  margin-top: 2px;
}
html.dark .playlist {
  border-top-color: rgba(232, 220, 200, 0.08);
}
.playlist-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 6px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: #3a2e22;
  transition: background 0.15s;
}
html.dark .playlist-item {
  color: #e8dcc8;
}
.playlist-item:hover {
  background: rgba(58, 46, 34, 0.06);
}
html.dark .playlist-item:hover {
  background: rgba(232, 220, 200, 0.06);
}
.playlist-item.active {
  background: rgba(168, 72, 58, 0.1);
  color: #a8483a;
  font-weight: 600;
}
html.dark .playlist-item.active {
  background: rgba(168, 72, 58, 0.2);
  color: #d4886a;
}
.pl-idx {
  width: 18px;
  text-align: center;
  opacity: 0.5;
  font-variant-numeric: tabular-nums;
}
.pl-title {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pl-playing {
  font-size: 12px;
  color: var(--color-brand);
}

/* 滚动条 */
.playlist::-webkit-scrollbar {
  width: 4px;
}
.playlist::-webkit-scrollbar-thumb {
  background: rgba(58, 46, 34, 0.15);
  border-radius: 2px;
}

/* 移动端 */
@media (max-width: 768px) {
  .player-body {
    width: 240px;
  }
}

/* 展开/收缩动画:以左下角为锚点,body 从右侧长出/缩回 */
.player-expand-enter-active,
.player-expand-leave-active {
  transform-origin: left bottom;
  overflow: hidden;
  animation: player-expand 0.35s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}
.player-expand-leave-active {
  animation-direction: reverse;
}
@keyframes player-expand {
  0% {
    opacity: 0;
    transform: scaleX(0);
    width: 0;
  }
  100% {
    opacity: 1;
    transform: scaleX(1);
    width: 280px;
  }
}
@media (max-width: 768px) {
  @keyframes player-expand {
    0% {
      opacity: 0;
      transform: scaleX(0);
      width: 0;
    }
    100% {
      opacity: 1;
      transform: scaleX(1);
      width: 240px;
    }
  }
}
</style>
