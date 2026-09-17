<template>
  <GbaPlayer
    v-if="game && game.type === 'GBA'"
    :src="game.fileUrl"
    :title="game.name"
    back-to="/games"
  />
  <FlashPlayer
    v-else-if="game"
    :src="game.fileUrl"
    :title="game.name"
    back-to="/games"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import FlashPlayer from '@/views/tools/FlashPlayer.vue'
import GbaPlayer from '@/views/games/GbaPlayer.vue'
import { gameApi } from '@/api'

const route = useRoute()
const game = ref(null)

onMounted(async () => {
  game.value = await gameApi.get(route.params.id)
})
</script>
