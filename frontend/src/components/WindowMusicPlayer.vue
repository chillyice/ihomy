<template>
  <aside v-if="music.current" class="window-player" :aria-label="$t('windowTools.player')">
    <ol v-if="expanded" class="window-queue">
      <li v-for="(track, i) in music.queue" :key="track.id">
        <button type="button" :aria-current="i === music.index ? 'true' : undefined" @click="music.choose(music.queue, track.id)">
          {{ track.title || $t('music.unknownTrack') }}
        </button>
      </li>
    </ol>
    <div class="window-transport">
      <router-link to="/music" class="track" :title="$t('windowTools.returnMusic')">
        <strong>{{ music.current.title || $t('music.unknownTrack') }}</strong>
        <small aria-live="polite">{{
          music.interrupted ? $t('windowTools.interrupted') : music.error ? $t(music.error) : music.current.artist
        }}</small>
      </router-link>
      <input
        v-if="music.duration"
        type="range"
        min="0"
        :max="music.duration"
        :value="music.position"
        :aria-label="$t('windowTools.progress')"
        @input="music.seek($event.target.value)"
      />
      <button type="button" :aria-label="$t('music.prevTrack')" @click="music.step(-1)">
        <el-icon><CaretLeft /></el-icon>
      </button>
      <button
        type="button"
        class="play"
        :aria-label="$t(music.wantsPlay ? (music.interrupted ? 'windowTools.cancelResume' : 'music.pauseToggle') : 'music.playToggle')"
        @click="music.wantsPlay ? music.pause() : music.resume()"
      >
        <el-icon><VideoPause v-if="music.wantsPlay" /><VideoPlay v-else /></el-icon>
      </button>
      <button type="button" :aria-label="$t('music.nextTrack')" @click="music.step(1)">
        <el-icon><CaretRight /></el-icon>
      </button>
      <button type="button" :aria-expanded="expanded" :aria-label="$t('windowTools.queue')" @click="expanded = !expanded">
        <el-icon><List /></el-icon>
      </button>
    </div>
  </aside>
</template>

<script setup>
import { watch, onBeforeUnmount } from 'vue'
import { CaretLeft, CaretRight, VideoPause, VideoPlay, List } from '@element-plus/icons-vue'
import { useWindowMusicStore } from '@/stores/windowMusic'
import { useUserStore } from '@/stores/user'
import { useFloatsStore } from '@/stores/floats'
import { useCallStore } from '@/stores/call'
import { isVoiceCaptureActive } from '@/composables/useVoiceRecorder'
import { useWindowPanel } from '@/utils/windowPanel'

const music = useWindowMusicStore()
const user = useUserStore()
const floats = useFloatsStore()
const call = useCallStore()
const expanded = useWindowPanel('music')
watch(
  () => [music.scope, floats.music, user.bgMusicVersion],
  () => music.background(floats.music),
  { immediate: true },
)
watch(() => call.inCall || isVoiceCaptureActive(), music.setInterrupted, { immediate: true, flush: 'sync' })
watch(
  () => music.scope,
  () => {
    expanded.value = false
  },
)
watch(
  () => !!music.current,
  (visible) => document.documentElement.classList.toggle('window-player-visible', visible),
  { immediate: true },
)
onBeforeUnmount(() => {
  music.reset()
  document.documentElement.classList.remove('window-player-visible')
})
</script>

<style scoped>
.window-player {
  position: fixed;
  z-index: var(--z-float);
  left: 50%;
  transform: translateX(-50%);
  bottom: max(12px, env(safe-area-inset-bottom));
  width: min(660px, calc(100% - 32px));
  background: var(--color-brand);
  color: var(--color-brand-text);
  border-radius: 14px;
  box-shadow: var(--shadow);
}
.window-transport {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
}
.track {
  display: grid;
  flex: 1;
  min-width: 0;
  color: inherit;
}
.track strong,
.track small {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.track small:empty {
  display: none;
}
.window-transport input {
  width: 20%;
  accent-color: var(--color-brand-text);
}
.window-player button {
  cursor: pointer;
  font: inherit;
  border: 0;
  background: transparent;
  color: inherit;
}
.window-transport button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  flex: none;
}
.window-transport .play {
  border-radius: 50%;
  background: var(--color-card);
  color: var(--color-brand);
}
.window-queue {
  max-height: min(35dvh, 250px);
  overflow: auto;
  margin: 0;
  padding: 10px 24px 6px 45px;
}
.window-queue button {
  text-align: left;
  padding: 6px;
}
.window-queue [aria-current] {
  text-decoration: underline;
  font-weight: bold;
}
@media (max-width: 768px) {
  .window-player {
    bottom: calc(68px + env(safe-area-inset-bottom));
    width: calc(100% - 16px);
  }
  .window-transport {
    gap: 2px;
    padding: 5px 8px 5px 14px;
  }
  .window-transport input {
    display: none;
  }
}
@media (max-height: 500px) and (orientation: landscape) {
  .window-player {
    width: min(440px, 65%);
    bottom: calc(48px + env(safe-area-inset-bottom));
  }
  .track small {
    display: none;
  }
  .window-transport {
    padding: 2px 8px;
  }
}
</style>
