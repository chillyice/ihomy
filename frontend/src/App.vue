<!-- 根组件:全局顶栏 + 页面内容 + 回到顶部,并负责首页数据的初始化与登录态同步 -->
<template>
  <AppHeader />
  <router-view />
  <BackToTop />
</template>

<script setup>
import { onMounted, watch } from 'vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import BackToTop from '@/components/BackToTop.vue'

const appStore = useAppStore()
const userStore = useUserStore()

onMounted(() => appStore.init())

// 登录态变化(登录/登出)时清空并重新拉取首页数据,保证界面内容与登录状态一致
watch(
  () => userStore.isLoggedIn,
  () => {
    appStore.reset()
    appStore.init()
  },
)
</script>