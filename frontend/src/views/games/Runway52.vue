<template>
  <Ludo v-if="roomId" />
  <div v-else ref="pageEl" class="page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions :is-fullscreen="isFullscreen" :show-wide="false" @back="goBack" @fullscreen="toggleFullscreen" />
    </PageToolbar>
    <div ref="stageEl" class="runway-stage game-surface">
      <iframe class="runway-table" :src="tableUrl" title="Runway 52"></iframe>
    </div>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import { useGameView } from '@/composables/useGameView'

const Ludo = defineAsyncComponent(() => import('./Ludo.vue'))
const route = useRoute()
const router = useRouter()
const roomId = computed(() => Number(route.query.room) || 0)
const tableUrl = `${import.meta.env.BASE_URL}runway-52.html`

const pageEl = ref(null)
const stageEl = ref(null)
const { isFullscreen, toggleFullscreen } = useGameView({ page: pageEl, stage: stageEl, scale: false })

const goBack = () => {
  if (window.history.state && window.history.state.back != null) router.back()
  else router.push('/games')
}
</script>

<style scoped>
.page {
  overflow: hidden;
}

.runway-stage {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
}

.runway-table {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 320px;
  border: 0;
  border-radius: 14px;
  background: #172522;
}
:global(html.game-mobile-immersive) .runway-table {
  min-height: 0;
  border-radius: 0;
}
</style>
