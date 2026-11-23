<template>
  <span class="game-icon" :style="{ '--icon-size': size + 'px' }" aria-hidden="true">
    <img v-if="src" :src="src" alt="" />
    <el-icon v-else><Trophy /></el-icon>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { Trophy } from '@element-plus/icons-vue'

const props = defineProps({
  code: { type: String, default: '' },
  size: { type: Number, default: 56 },
})

const artwork = import.meta.glob('/src/assets/games/icons/*.svg', { eager: true, import: 'default', query: '?url&no-inline' })
const src = computed(() => artwork[`/src/assets/games/icons/${String(props.code || '').replace(/^game_/, '')}.svg`])
</script>

<style scoped>
.game-icon {
  --icon-size: 56px;
  position: relative;
  isolation: isolate;
  display: inline-grid;
  place-items: center;
  flex: none;
  width: var(--icon-size);
  height: var(--icon-size);
  overflow: hidden;
  border-radius: 24% 26% 23% 25%;
  background-color: #efdfc3;
  box-shadow: inset 0 0 0 1px rgb(89 71 55 / 14%);
  color: #594737;
  font-size: calc(var(--icon-size) * 0.55);
  vertical-align: middle;
}
html.dark .game-icon {
  background-color: #ceba9b;
}
.game-icon img {
  width: 90%;
  height: 90%;
  display: block;
}
.game-icon::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: var(--home-grain, none);
  background-size: 112px;
  mix-blend-mode: multiply;
  opacity: 0.18;
  pointer-events: none;
}
</style>
