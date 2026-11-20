<template>
  <ElderHome v-if="isElder" />
  <Home v-else />
</template>

<script setup>
import { defineAsyncComponent } from 'vue'
import { useDisplayMode } from '@/composables/useDisplayMode'

// 暖居和手机由各自外壳显示首页，只有光尘桌面会挂载这个路由视图。
const Home = defineAsyncComponent(() => import('./Home.vue'))
const ElderHome = defineAsyncComponent(() => import('@/components/ElderHome.vue'))
const { isElder } = useDisplayMode()
</script>
