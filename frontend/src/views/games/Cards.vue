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
  flex: 1 0 320px;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.cards-stage:fullscreen {
  padding: clamp(8px, 2vw, 20px);
  overflow: auto;
  background: var(--color-bg);
}
.cards-modes,
.cards-nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 12px;
}
.cards-nav a {
  padding: 7px 2px;
  border-bottom: 2px solid transparent;
  color: var(--color-text-secondary);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}
.cards-nav a:hover,
.cards-nav a.active {
  color: var(--color-text-primary);
}
.cards-nav a.active {
  border-color: var(--color-brand);
}
</style>
