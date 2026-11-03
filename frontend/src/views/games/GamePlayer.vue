<template>
  <GbaPlayer v-if="game && game.type === 'GBA'" :src="game.fileUrl" :title="game.name" :game-id="game.id" back-to="/games" />
  <FlashPlayer v-else-if="game && game.type !== 'LINK'" :src="game.fileUrl" :title="game.name" back-to="/games" />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FlashPlayer from '@/views/tools/FlashPlayer.vue'
import GbaPlayer from '@/views/games/GbaPlayer.vue'
import { gameApi } from '@/api'

const route = useRoute()
const router = useRouter()
const game = ref(null)

onMounted(async () => {
  game.value = await gameApi.get(route.params.id)
  // 网页游戏没有播放器:交给站内 iframe 页(旧链接/收藏夹照常可用)
  if (game.value?.type === 'LINK') {
    router.replace(`/games/web/${game.value.id}`)
  }
})
</script>
