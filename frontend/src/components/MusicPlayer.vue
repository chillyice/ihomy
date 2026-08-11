<!-- 全局背景音乐播放器:登录且当前家庭配置了音乐时,右下角显示迷你控制条;点击开关播放,
     状态存 localStorage(ihomy-music)自动续播,切换家庭时重新拉取当前家庭音乐 -->
<template>
  <div v-if="music" class="music-player">
    <button class="mp-btn" :title="playing ? $t('music.pause') : $t('music.play')" @click="toggle">
      <span class="mp-icon">
        <svg v-if="playing" viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M6 19h4V5H6v14zm8-14v14h4V5h-4z" /></svg>
        <svg v-else viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M8 5v14l11-7z" /></svg>
      </span>
      <span class="mp-title">{{ music.title || '🎵' }}</span>
    </button>
    <audio ref="audioEl" :src="music.url" loop preload="none" @ended="playing = false; persistNow()"></audio>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { familyApi } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const music = ref(null)
const playing = ref(false)
const audioEl = ref(null)

// 播放状态持久化:key=url,value=played?1:0,刷新后自动续播
const KEY = 'ihomy-music'
const persisted = () => {
  try { return JSON.parse(localStorage.getItem(KEY) || '{}') } catch { return {} }
}
const persistNow = () => {
  if (music.value) localStorage.setItem(KEY, JSON.stringify({ url: music.value.url, playing: playing.value }))
}

// 登录后拉当前家庭音乐配置;登录状态变化时重拉
const loadMusic = async () => {
  if (!userStore.isLoggedIn) {
    music.value = null
    playing.value = false
    if (audioEl.value) audioEl.value.pause()
    return
  }
  try {
    const f = await familyApi.get()
    music.value = f.musicUrl
      ? { url: f.musicUrl, title: f.musicTitle || '背景音乐' }
      : null
    playing.value = false
    if (music.value && persisted().playing && persisted().url === music.value.url) {
      // 自动续播:浏览器可能拦截自动播放,失败则保持待播放状态
      audioEl.value?.play?.().catch(() => {})
      playing.value = true
    }
  } catch (e) {
    // 忽略
  }
}

const toggle = async () => {
  if (!audioEl.value) return
  if (playing.value) {
    audioEl.value.pause()
    playing.value = false
  } else {
    try {
      await audioEl.value.play()
      playing.value = true
    } catch (e) {
      // 播放被拒(格式/未交互),忽略
    }
  }
  persistNow()
}

watch(() => userStore.isLoggedIn, loadMusic)
watch(() => userStore.userInfo?.familyId, async () => {
  audioEl.value?.pause?.()
  await loadMusic()
})
onMounted(loadMusic)
onBeforeUnmount(() => audioEl.value?.pause())
</script>

<style scoped>
.music-player {
  position: fixed;
  left: 16px;
  bottom: 16px;
  z-index: 95;
}
.mp-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 220px;
  padding: 7px 14px;
  border: none;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  backdrop-filter: blur(6px);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.25);
  transition: background 0.2s, transform 0.2s;
}
.mp-btn:hover { background: rgba(0, 0, 0, 0.7); transform: scale(1.04); }
.mp-icon { display: flex; align-items: center; }
.mp-title {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>