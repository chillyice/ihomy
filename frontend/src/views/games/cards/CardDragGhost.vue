<template>
  <Teleport :to="target">
    <div v-if="drag.active" class="card-drag-ghost" :style="layerStyle" aria-hidden="true">
      <div
        v-for="(card, idx) in drag.cards"
        :key="card.id"
        class="sol-cardface card-drag-ghost-card"
        :style="{ top: `${idx * drag.step}px`, zIndex: idx + 1 }"
      >
        <PlayingCard :card="card" />
      </div>
    </div>
  </Teleport>
</template>

<script setup>
// 拖拽时跟随指针的牌组幽灵:位置/牌宽由 useCardDrag 计算,这里只负责把牌画出来。
// 全屏时 DOM 只能渲染在全屏元素内,所以 teleport 目标要跟着 fullscreen 切换。
import { computed, inject, ref } from 'vue'
import PlayingCard from '@/components/games/PlayingCard.vue'

const props = defineProps({
  drag: { type: Object, required: true },
})

const gameFullscreen = inject('gameFullscreen', ref(false))
const target = computed(() => (gameFullscreen.value && typeof document !== 'undefined' && document.fullscreenElement) || 'body')

const layerStyle = computed(() => ({
  '--sol-card-w': `${props.drag.cardW}px`,
  left: `${props.drag.x}px`,
  top: `${props.drag.y}px`,
}))
</script>
