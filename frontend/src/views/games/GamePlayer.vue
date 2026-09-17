<template>
  <FlashPlayer
    v-if="game"
    :src="game.fileUrl"
    :title="game.name"
    :breadcrumb="[{ label: $t('games.title') }, { label: game.name }]"
    back-to="/games"
  />
</template>

<script setup>
// 小游戏播放:按 id 取游戏详情,复用 FlashPlayer 组件按 URL 播放(支持全屏)
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import FlashPlayer from '@/views/tools/FlashPlayer.vue'
import { gameApi } from '@/api'

const route = useRoute()
const game = ref(null)

onMounted(async () => {
  game.value = await gameApi.get(route.params.id)
})
</script>
