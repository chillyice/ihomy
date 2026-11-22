<template>
  <span class="ihomy-wordmark"><img :src="themeStore.windowMark" alt="" />ihomy</span>
</template>

<script setup>
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
</script>

<style scoped>
.ihomy-wordmark {
  display: inline-flex;
  align-items: center;
  gap: 0.3em;
  vertical-align: middle;
}
.ihomy-wordmark img {
  width: 1.25em;
  height: 1.25em;
}
</style>
