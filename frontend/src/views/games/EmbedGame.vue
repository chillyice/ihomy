<!-- 开源小游戏分发器:象棋/黑白棋/消消乐/泡泡龙/小恐龙已本地化为 Vue 组件(embedded/),
     三维弹球继续整包 iframe 直跑(public/mini-games/,wasm 引擎无法自研)。
     iframe 壳保留触控守卫与站内沉浸浮层;全屏由外壳统一提供 -->
<template>
  <component :is="localGame" v-if="localGame" />
  <GameShell
    v-else
    ref="shellEl"
    class="eg-shell"
    :title="title"
    body-align="stretch"
    :new-game="true"
    :new-game-disabled="!frameLoaded"
    :new-game-confirm="true"
    @back="router.push('/games')"
    @new-game="startNewGame"
  >
    <template #head>
      <el-button size="small" round :title="$t('games.web.reload')" :aria-label="$t('games.web.reload')" @click="reloadFrame">
        <el-icon><Refresh /></el-icon>
        <span class="wg-btn-label">{{ $t('games.web.reload') }}</span>
      </el-button>
      <el-button
        class="wg-open-external"
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
        class="wg-page-full"
        size="small"
        round
        :title="pageFull ? $t('games.web.exitPageFullscreen') : $t('games.web.pageFullscreen')"
        @click="pageFull = !pageFull"
      >
        <el-icon><Fold v-if="pageFull" /><Expand v-else /></el-icon>
        <span class="wg-btn-label">{{ pageFull ? $t('games.web.exitPageFullscreen') : $t('games.web.pageFullscreen') }}</span>
      </el-button>
    </template>

    <div ref="stageEl" class="wg-stage" :class="{ 'is-page-full': pageFull }">
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
  </GameShell>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Close, Expand, Fold, Refresh, TopRight } from '@element-plus/icons-vue'
import { defineAsyncComponent } from 'vue'
import GameShell from '@/components/games/GameShell.vue'
import { findEmbeddedGame, embeddedGameSrc } from '@/utils/games/embeddedGames'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useIframeFit } from '@/composables/useIframeFit'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const shellEl = ref(null)
const stageEl = ref(null)
const frameEl = ref(null)
const frameKey = ref(0)
const frameLoaded = ref(false)
const pageFull = ref(false)

// 游戏内容比可视区高时等比缩小(useIframeFit 拉高 iframe 再 transform 缩回,不出游戏内滚动条)
const { refit: refitFrame } = useIframeFit({ stage: stageEl, frame: frameEl })

const onFrameLoad = () => {
  frameLoaded.value = true
  refitFrame()
}

const game = computed(() => findEmbeddedGame(String(route.params.slug || '')))
const title = computed(() => (game.value ? t(game.value.titleKey) : t('games.title')))
const src = computed(() => (game.value ? embeddedGameSrc(game.value.slug) : ''))

// 已本地化的开源游戏:直接渲染 Vue 组件(不再 iframe),名单随 P9/P10 迁移扩大
const LOCAL_EMBEDDED = {
  xiangqi: defineAsyncComponent(() => import('./embedded/XiangqiGame.vue')),
  reversi: defineAsyncComponent(() => import('./embedded/ReversiGame.vue')),
  match3: defineAsyncComponent(() => import('./embedded/Match3Game.vue')),
  bubble: defineAsyncComponent(() => import('./embedded/BubbleGame.vue')),
  trex: defineAsyncComponent(() => import('./embedded/TrexGame.vue')),
}
const localGame = computed(() => LOCAL_EMBEDDED[route.params.slug] || null)

const reloadFrame = () => {
  frameLoaded.value = false
  frameKey.value += 1
}

const openExternal = () => {
  if (src.value) window.open(src.value, '_blank', 'noopener,noreferrer')
}

// 「新游戏」键已搬到外壳头部(规则键左侧):投递给 iframe,由静态页合成 F2 交给引擎
const startNewGame = () => {
  frameEl.value?.contentWindow?.postMessage({ type: 'ihomy:pinball:new' }, window.location.origin)
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

// 光影:iframe 页由分发器挂起;本地化组件自己挂起/还原(见 embedded/*.vue)。
// flush:'post' 保证「本地→iframe」切换时子组件已还原、这里再挂上,不会漏挂。
watch(
  localGame,
  (game) => {
    if (!game) sunLight?.suspendEffects()
  },
  { immediate: true, flush: 'post' },
)

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  if (document.fullscreenElement) document.exitFullscreen?.()
  // 本地化组件的还原在它自己的卸载钩子里完成,这里只管 iframe 态
  if (!localGame.value) sunLight?.restoreEffects?.()
})
</script>

<style scoped>
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
.wg-stage.is-page-full {
  position: absolute;
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
/* 手机上页面已是整屏(外壳的全屏/宽屏同样隐藏),「页面全屏」与「浏览器打开」没有意义,
   直接让位;桌面仍保留。断点与 GameShell 的手机档一致(窄屏 + 手机横屏) */
@media (max-width: 768px), (orientation: landscape) and (max-height: 520px) {
  .wg-open-external,
  .wg-page-full {
    display: none;
  }
}
</style>
