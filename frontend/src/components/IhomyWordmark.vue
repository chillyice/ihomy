<template>
  <span class="ihomy-wordmark"><img :src="windowMark" alt="" />ihomy</span>
</template>

<script setup>
import windowMark from '@/assets/brand/ihomy-window-96.png'
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
