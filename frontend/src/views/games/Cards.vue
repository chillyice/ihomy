<template>
  <GameShell class="cards-shell" :title="$t(modes[currentGame])" body-align="stretch" @back="router.push('/games')">
    <template #rules>
      <p>{{ $t(modeRules[currentGame]) }}</p>
    </template>

    <div class="cards-stage">
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

      <KlondikeBoard v-if="currentGame === 'klondike'" />
      <SpiderBoard v-else-if="currentGame === 'spider'" />
      <FreeCellBoard v-else />
    </div>
  </GameShell>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'
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

/** 各玩法的玩法说明(规则弹窗内容) */
const modeRules = {
  klondike: 'games.solitaire.tip',
  spider: 'games.spider.tip',
  freecell: 'games.freecell.tip',
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
const shellEl = ref(null)
/** 牌桌按这个标志切换弹窗 teleport 与快捷键(三桌 inject) */
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)
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
  /* 拉满外壳游戏区:牌桌内部 fitCardWidth 按 felt 实测宽高自适应 */
  flex: 1 1 320px;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
</style>
