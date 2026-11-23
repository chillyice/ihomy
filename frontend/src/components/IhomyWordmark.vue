<template>
  <span class="ihomy-wordmark"><img :src="appIcon.image" alt="" />ihomy</span>
</template>

<script setup>
import { useAppIconStore } from '@/stores/appIcon'

const appIcon = useAppIconStore()
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
