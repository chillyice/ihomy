<template>
  <div ref="pageEl" class="page game-page game-page-readable">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions
        :is-fullscreen="isFullscreen"
        :wide="isWide"
        @back="router.push('/games')"
        @fullscreen="toggleFullscreen"
        @wide="toggleWide"
      />
    </PageToolbar>

    <div ref="stageEl" class="cards-stage">
      <div class="cards-modes">
        <nav v-if="!route.query.room" class="cards-nav" :aria-label="$t('games.cards.title')">
          <RouterLink
            v-for="(title, mode) in modes"
            :key="mode"
            :to="{ name: 'Cards', params: { game: mode } }"
            replace
            :class="{ active: mode === currentGame }"
            :aria-current="mode === currentGame ? 'page' : undefined"
          >
            {{ $t(title) }}
          </RouterLink>
        </nav>
        <span v-else>{{ $t(modes[currentGame]) }}</span>
        <GameTopActions v-if="isFullscreen" :show-back="false" :show-wide="false" :is-fullscreen="true" @fullscreen="toggleFullscreen" />
      </div>

      <KlondikeBoard v-if="currentGame === 'klondike'" />
      <SpiderBoard v-else-if="currentGame === 'spider'" />
      <FreeCellBoard v-else />
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import { useGameView } from '@/composables/useGameView'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import KlondikeBoard from './cards/KlondikeBoard.vue'
import SpiderBoard from './cards/SpiderBoard.vue'
import FreeCellBoard from './cards/FreeCellBoard.vue'
import './cards/cards.css'

const MODE_KEY = 'ihomy:games:cards:mode:v1'
const modes = {
  klondike: 'games.solitaire.title',
  spider: 'games.spider.title',
  freecell: 'games.freecell.title',
}

function savedMode() {
  try {
    const mode = localStorage.getItem(MODE_KEY)
    if (Object.hasOwn(modes, mode)) return mode
  } catch {
    // Storage may be disabled.
  }
  return 'klondike'
}

const route = useRoute()
const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)
const pageEl = ref(null)
const stageEl = ref(null)
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: stageEl, scale: false })
provide('gameFullscreen', isFullscreen)

const initialMode = savedMode()
const currentGame = computed(() => (Object.hasOwn(modes, route.params.game) ? route.params.game : initialMode))
watch(
  currentGame,
  (mode) => {
    try {
      localStorage.setItem(MODE_KEY, mode)
    } catch {
      // Storage may be disabled.
    }
  },
  { immediate: true },
)

onMounted(() => sunLight?.suspendEffects())
onBeforeUnmount(() => sunLight?.restoreEffects())
</script>

<style scoped>
.cards-stage {
  display: flex;
  /* 允许收缩:矮视口(手机横屏)下 useGameView 锁定的页高小于基础高度时,
     舞台要能让出空间,牌面由 fitCardWidth 按 felt 实测高度自适应 */
  flex: 1 1 320px;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.cards-stage:fullscreen {
  padding: clamp(8px, 2vw, 20px);
  overflow: auto;
  background: var(--color-bg);
}
</style>
