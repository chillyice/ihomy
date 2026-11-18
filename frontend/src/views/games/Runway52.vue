<template>
  <Ludo v-if="roomId" />
  <GameShell v-else ref="shellEl" class="rw-shell" :title="$t('games.ludo.title')" body-align="stretch" @back="goBack">
    <div class="runway-stage">
      <iframe class="runway-table" :src="tableUrl" title="Runway 52"></iframe>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'

const Ludo = defineAsyncComponent(() => import('./Ludo.vue'))
const route = useRoute()
const router = useRouter()
const roomId = computed(() => Number(route.query.room) || 0)
const tableUrl = `${import.meta.env.BASE_URL}runway-52.html`

const shellEl = ref(null)

const goBack = () => {
  if (window.history.state && window.history.state.back != null) router.back()
  else router.push('/games')
}
</script>

<style scoped>
.runway-stage {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
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
