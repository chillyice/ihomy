<template>
  <div class="sol-flip" :class="{ 'is-down': !card.faceUp }">
    <div v-if="everUp" class="sol-face sol-face-front">
      <PlayingCard :card="card" />
    </div>
    <div class="sol-face sol-face-back" aria-hidden="true"></div>
  </div>
</template>

<script setup>
// 牌面双面结构:faceUp 切换时 .sol-flip 的 rotateY 过渡出 3D 翻牌(规则引擎在 move 里翻面,这里只管渲染)。
// 正面首次翻开后常驻(everUp),撤销翻回背面也能动画,同时避免开局就加载全部 52 张牌面图。
import { ref, watch } from 'vue'
import PlayingCard from '@/components/games/PlayingCard.vue'

const props = defineProps({
  card: { type: Object, required: true },
})

const everUp = ref(props.card.faceUp)
watch(
  () => props.card.faceUp,
  (value) => {
    if (value) everUp.value = true
  },
)
</script>
