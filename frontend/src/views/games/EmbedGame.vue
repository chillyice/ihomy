<!-- 开源小游戏:整包静态页放在 public/mini-games/<slug>/,站内 iframe 直跑(不引 npm 依赖)
     触控守卫沿用 .game-surface;全屏口径与网页游戏一致(浏览器全屏 + 站内沉浸浮层) -->
<template>
  <div ref="pageEl" class="page web-game-page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <el-button size="small" round @click="router.push('/games')">
        <el-icon><ArrowLeft /></el-icon>
        {{ $t('games.back') }}
      </el-button>
      <span class="wg-title">{{ title }}</span>
      <div class="game-top-actions">
        <el-button size="small" round :title="$t('games.web.reload')" :aria-label="$t('games.web.reload')" @click="reloadFrame">
          <el-icon><Refresh /></el-icon>
          <span class="wg-btn-label">{{ $t('games.web.reload') }}</span>
        </el-button>
        <el-button
          size="small"
          round
          :title="$t('games.web.openExternal')"
          :aria-label="$t('games.web.openExternal')"
          @click="openExternal"
        >
          <el-icon><TopRight /></el-icon>
          <span class="wg-btn-label">{{ $t('games.web.openExternal') }}</span>
        </el-button>
        <el-button
          size="small"
          round
          :title="pageFull ? $t('games.web.exitPageFullscreen') : $t('games.web.pageFullscreen')"
          @click="pageFull = !pageFull"
        >
          <el-icon><Fold v-if="pageFull" /><Expand v-else /></el-icon>
          <span class="wg-btn-label">{{ pageFull ? $t('games.web.exitPageFullscreen') : $t('games.web.pageFullscreen') }}</span>
        </el-button>
        <el-button size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          <span class="wg-btn-label">{{ isFullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen') }}</span>
        </el-button>
      </div>
    </PageToolbar>

    <div ref="stageEl" class="wg-stage card game-surface" :class="{ 'is-page-full': pageFull }">
      <iframe
        v-if="src"
        ref="frameEl"
        :key="frameKey"
        class="wg-frame"
        :src="src"
        :title="title"
        allow="fullscreen; autoplay; gamepad"
        allowfullscreen
        referrerpolicy="no-referrer"
        @load="onFrameLoad"
      ></iframe>
      <div v-if="!frameLoaded" class="wg-cover">
        <template v-if="!src">
          <div class="wg-cover-title">{{ $t('games.loadFailed') }}</div>
          <el-button size="small" round type="primary" @click="router.push('/games')">{{ $t('games.back') }}</el-button>
        </template>
        <template v-else>
          <div class="wg-cover-spinner"></div>
          <div class="wg-cover-title">{{ $t('games.loading') }}</div>
        </template>
      </div>
      <div v-if="pageFull" class="wg-float">
        <el-button
          circle
          :title="$t('games.web.exitPageFullscreen')"
          :aria-label="$t('games.web.exitPageFullscreen')"
          @click="pageFull = false"
        >
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Close, Expand, Fold, FullScreen, Refresh, TopRight } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { findEmbeddedGame, embeddedGameSrc } from '@/utils/games/embeddedGames'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useGameView } from '@/composables/useGameView'
import { useIframeFit } from '@/composables/useIframeFit'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const pageEl = ref(null)
const stageEl = ref(null)
const frameEl = ref(null)
const frameKey = ref(0)
const frameLoaded = ref(false)
const pageFull = ref(false)
// iframe 本身不缩放(缩了字会糊);暖居下 fullBleed 把高度交给 CSS 拉满主区,游戏拿满内容区高
const { isFullscreen, toggleFullscreen } = useGameView({ page: pageEl, stage: stageEl, scale: false, fullBleed: true })
// 游戏内容比可视区高时等比缩小(useIframeFit 拉高 iframe 再 transform 缩回,不出游戏内滚动条)
const { refit: refitFrame } = useIframeFit({ stage: stageEl, frame: frameEl })

const onFrameLoad = () => {
  frameLoaded.value = true
  refitFrame()
}

const game = computed(() => findEmbeddedGame(String(route.params.slug || '')))
const title = computed(() => (game.value ? t(game.value.titleKey) : t('games.title')))
const src = computed(() => (game.value ? embeddedGameSrc(game.value.slug) : ''))

const reloadFrame = () => {
  frameLoaded.value = false
  frameKey.value += 1
}

const openExternal = () => {
  if (src.value) window.open(src.value, '_blank', 'noopener,noreferrer')
}

const onKeydown = (event) => {
  if (event.key === 'Escape' && pageFull.value) pageFull.value = false
}

watch(
  () => route.params.slug,
  () => {
    reloadFrame()
  },
)

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  sunLight?.suspendEffects()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  if (document.fullscreenElement) document.exitFullscreen?.()
  sunLight?.restoreEffects?.()
})
</script>

<style scoped>
.wg-title {
  font-size: 14px;
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wg-stage {
  position: relative;
  flex: 1 1 auto;
  height: auto;
  min-height: 320px;
  padding: 0;
  overflow: hidden;
  background: #1b1b1b;
}
.wg-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: var(--color-bg-2);
  touch-action: manipulation;
}
.wg-stage:fullscreen {
  height: 100%;
  border-radius: 0;
}
.wg-stage.is-page-full {
  position: fixed;
  inset: 0;
  z-index: var(--z-immersive);
  height: auto;
  min-height: 0;
  border-radius: 0;
  border: 0;
  box-shadow: none;
}
.wg-cover {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: var(--color-card, #fff);
  color: var(--color-text-secondary, #888);
  font-size: 13px;
}
.wg-cover-spinner {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid var(--color-border, rgba(0, 0, 0, 0.12));
  border-top-color: var(--color-accent, #b88c6e);
  animation: wg-spin 0.9s linear infinite;
}
@keyframes wg-spin {
  to {
    transform: rotate(360deg);
  }
}
.wg-float {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
}
@media (max-width: 768px) {
  .wg-btn-label {
    display: none;
  }
}
</style>
