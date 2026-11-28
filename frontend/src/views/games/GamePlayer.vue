<template>
  <EmulatorPlayer
    v-if="game && isEmulatorType(game.type)"
    :src="game.fileUrl"
    :title="game.name"
    :game-id="game.id"
    :system="game.type"
    back-to="/games"
  />
  <DesktopGame v-else-if="game && isDesktopType(game.type)" :game="game" back-to="/games" />
  <FlashPlayer v-else-if="game && game.type !== 'LINK'" :src="game.fileUrl" :title="game.name" back-to="/games" />
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FlashPlayer from '@/views/tools/FlashPlayer.vue'
import EmulatorPlayer from '@/views/games/EmulatorPlayer.vue'
import DesktopGame from '@/views/games/DesktopGame.vue'
import { isEmulatorType } from '@/utils/games/emulatorSystems'
import { isDesktopType } from '@/utils/games/desktopGames'
import { gameApi } from '@/api'
import { recordDoing } from '@/utils/lastDoing'

const route = useRoute()
const router = useRouter()
const game = ref(null)

watch(
  () => route.name === 'GamePlayer' && route.params.id,
  async (id, _, onCleanup) => {
    game.value = null
    if (!id) return
    let active = true
    onCleanup(() => {
      active = false
    })
    const data = await gameApi.get(id)
    if (!active || !data) return
    if (data.type === 'LINK') {
      router.replace(`/games/web/${data.id}`)
      return
    }
    game.value = data
    recordDoing({ kind: 'GAME', id: `/games/play/${data.id}`, title: data.name })
  },
  { immediate: true },
)
</script>
