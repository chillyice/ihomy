<!-- 悬浮音乐播放器:黑胶可视化+歌曲名+歌单+播放/暂停/切歌+进度条,右下角固定 -->
<template>
  <div v-if="playlist.length" class="music-player" :class="{ expanded }">
    <!-- 黑胶可视化 + 展开切换 -->
    <div class="player-left" @click="expanded = !expanded">
      <div class="vinyl" :class="{ playing }">
        <div class="vinyl-disc">
          <div class="vinyl-groove"></div>
          <div class="vinyl-label"></div>
        </div>
        <div class="vinyl-arm"></div>
      </div>
    </div>

    <!-- 展开内容:歌名+控件+进度条+歌单 -->
    <div v-if="expanded" class="player-body">
      <div class="track-info">
        <div class="track-title" :title="currentTrack.title">{{ currentTrack.title || '未知曲目' }}</div>
        <div class="track-sub">{{ playing ? '正在播放' : '已暂停' }}</div>
      </div>

      <!-- 进度条 -->
      <div class="progress-row">
        <span class="time">{{ formatTime(currentTime) }}</span>
        <div class="progress-bar" @click="onSeek">
          <div class="progress-buffered" :style="{ width: bufferedPct + '%' }"></div>
          <div class="progress-played" :style="{ width: playedPct + '%' }"></div>
        </div>
        <span class="time">{{ formatTime(duration) }}</span>
      </div>

      <!-- 控件 -->
      <div class="controls">
        <button class="ctrl-btn" @click="prev" title="上一首"><el-icon><CaretLeft /></el-icon></button>
        <button class="ctrl-btn ctrl-main" @click="togglePlay" :title="playing ? '暂停' : '播放'">
          <el-icon><VideoPause v-if="playing" /><VideoPlay v-else /></el-icon>
        </button>
        <button class="ctrl-btn" @click="next" title="下一首"><el-icon><CaretRight /></el-icon></button>
        <button class="ctrl-btn ctrl-list" @click="showList = !showList" title="歌单">
          <el-icon><List /></el-icon>
        </button>
      </div>

      <!-- 歌单 -->
      <div v-if="showList" class="playlist">
        <div
          v-for="(t, i) in playlist"
          :key="t.id || i"
          class="playlist-item"
          :class="{ active: i === trackIdx }"
          @click="selectTrack(i)"
        >
          <span class="pl-idx">{{ i + 1 }}</span>
          <span class="pl-title" :title="t.title">{{ t.title || '未知曲目' }}</span>
          <el-icon v-if="i === trackIdx && playing" class="pl-playing"><VideoPlay /></el-icon>
        </div>
      </div>
    </div>

    <audio
      ref="audioEl"
      :src="currentTrack.url"
      @ended="next"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMeta"
      @progress="onProgress"
      preload="auto"
    ></audio>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { musicApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { CaretLeft, CaretRight, VideoPlay, VideoPause, List } from '@element-plus/icons-vue'

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

const currentTrack = computed(() => playlist.value[trackIdx.value] || {})
const playedPct = computed(() => duration.value ? (currentTime.value / duration.value) * 100 : 0)
const bufferedPct = computed(() => duration.value ? (buffered.value / duration.value) * 100 : 0)

const loadPlaylist = async () => {
  if (!userStore.isLoggedIn) { playlist.value = []; return }
  try {
    playlist.value = await musicApi.list()
    if (trackIdx.value >= playlist.value.length) trackIdx.value = 0
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
    audioEl.value.play().then(() => { playing.value = true }).catch(() => {})
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
  // 切歌后如果之前在播放,继续播放新曲目
  const wasPlaying = playing.value
  setTimeout(() => {
    if (wasPlaying && audioEl.value) {
      audioEl.value.play().then(() => { playing.value = true }).catch(() => { playing.value = false })
    } else {
      playing.value = false
    }
  }, 50)
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
  }
}
const onLoadedMeta = () => {
  if (audioEl.value) duration.value = audioEl.value.duration || 0
}
const onProgress = () => {
  if (audioEl.value && audioEl.value.buffered.length) {
    buffered.value = audioEl.value.buffered.end(audioEl.value.buffered.length - 1)
  }
}

const formatTime = (s) => {
  if (!s || isNaN(s)) return '0:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return `${m}:${sec.toString().padStart(2, '0')}`
}

// 登录态变化时重新加载歌单
watch(() => userStore.isLoggedIn, loadPlaylist)
watch(() => userStore.userInfo?.familyId, loadPlaylist)

onMounted(loadPlaylist)
</script>

<style scoped>
.music-player {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 60;
  display: flex;
  align-items: flex-end;
  gap: 0;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(24px) saturate(1.3);
  -webkit-backdrop-filter: blur(24px) saturate(1.3);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 20px;
  box-shadow: 0 12px 40px rgba(58, 46, 34, 0.18), 0 2px 8px rgba(58, 46, 34, 0.08),
              inset 0 1px 0 rgba(255, 255, 255, 0.6), inset 0 -1px 0 rgba(58, 46, 34, 0.04);
  overflow: hidden;
  transition: width 0.3s ease, height 0.3s ease;
  user-select: none;
}
html.dark .music-player {
  background: rgba(30, 40, 65, 0.65);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5), 0 2px 8px rgba(0, 0, 0, 0.3),
              inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.player-left {
  padding: 12px;
  cursor: pointer;
  flex-shrink: 0;
}

/* 黑胶可视化 */
.vinyl {
  position: relative;
  width: 56px;
  height: 56px;
  transition: transform 0.3s ease;
}
.music-player:hover .vinyl { transform: scale(1.05); }
.vinyl-disc {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, #3A2E22 0%, #1A1410 30%, #2A2018 60%, #1A1410 100%);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4), inset 0 0 10px rgba(0,0,0,0.5);
  border: 1px solid #5C4332;
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
  background: radial-gradient(circle, #A8483A 0%, #6B2E26 100%);
  border: 1px solid #3A2E22;
}
.vinyl-arm {
  position: absolute;
  top: -3px;
  right: 10px;
  width: 3px;
  height: 28px;
  background: linear-gradient(to bottom, #C9A876, #8B6F47);
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
  background: #C9A876;
  border-radius: 50%;
}
.vinyl.playing .vinyl-disc { animation: spin 4s linear infinite; }
.vinyl.playing .vinyl-arm { transform: rotate(15deg); }
@keyframes spin { to { transform: rotate(360deg); } }

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
  color: #3A2E22;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}
html.dark .track-title { color: #E8DCC8; }
.track-sub {
  font-size: 11px;
  color: #7A6B5A;
  flex-shrink: 0;
}
html.dark .track-sub { color: #B0A898; }

/* 进度条 */
.progress-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.time {
  font-size: 10px;
  color: #7A6B5A;
  min-width: 28px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
html.dark .time { color: #B0A898; }
.progress-bar {
  flex: 1;
  height: 4px;
  background: rgba(58, 46, 34, 0.1);
  border-radius: 2px;
  position: relative;
  cursor: pointer;
  overflow: hidden;
}
html.dark .progress-bar { background: rgba(232, 220, 200, 0.1); }
.progress-buffered {
  position: absolute;
  inset: 0;
  background: rgba(58, 46, 34, 0.12);
  border-radius: 2px;
}
html.dark .progress-buffered { background: rgba(232, 220, 200, 0.12); }
.progress-played {
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  background: linear-gradient(to right, #A8483A, #C9A876);
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
  color: #3A2E22;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}
html.dark .ctrl-btn {
  background: rgba(232, 220, 200, 0.08);
  color: #E8DCC8;
}
.ctrl-btn:hover {
  background: rgba(58, 46, 34, 0.12);
  transform: scale(1.1);
}
html.dark .ctrl-btn:hover { background: rgba(232, 220, 200, 0.15); }
.ctrl-main {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #A8483A, #6B2E26);
  color: #F5EFE0;
  box-shadow: 0 4px 12px rgba(168, 72, 58, 0.3);
}
.ctrl-main:hover {
  background: linear-gradient(135deg, #B8584A, #7B3E36);
  transform: scale(1.1);
}
.ctrl-list { margin-left: 4px; }

/* 歌单 */
.playlist {
  max-height: 180px;
  overflow-y: auto;
  border-top: 1px solid rgba(58, 46, 34, 0.08);
  padding-top: 6px;
  margin-top: 2px;
}
html.dark .playlist { border-top-color: rgba(232, 220, 200, 0.08); }
.playlist-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 6px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: #3A2E22;
  transition: background 0.15s;
}
html.dark .playlist-item { color: #E8DCC8; }
.playlist-item:hover { background: rgba(58, 46, 34, 0.06); }
html.dark .playlist-item:hover { background: rgba(232, 220, 200, 0.06); }
.playlist-item.active {
  background: rgba(168, 72, 58, 0.1);
  color: #A8483A;
  font-weight: 600;
}
html.dark .playlist-item.active {
  background: rgba(168, 72, 58, 0.2);
  color: #D4886A;
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
  color: #A8483A;
}

/* 滚动条 */
.playlist::-webkit-scrollbar { width: 4px; }
.playlist::-webkit-scrollbar-thumb { background: rgba(58, 46, 34, 0.15); border-radius: 2px; }

/* 移动端 */
@media (max-width: 768px) {
  .music-player { right: 12px; bottom: 12px; }
  .player-body { width: 240px; }
}
</style>
