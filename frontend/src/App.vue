<!-- 根组件:全局顶栏 + 页面内容 + 回到顶部,负责首页数据初始化、登录态同步与语言环境 -->
<template>
  <el-config-provider :locale="elLocale">
    <AppHeader />
    <router-view />
    <BackToTop />
    <InstallPrompt />
    <SideTabs v-if="!userStore.isOps" />
    <MusicPlayer v-if="!userStore.isOps" />
  </el-config-provider>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import AppHeader from '@/components/AppHeader.vue'
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import SideTabs from '@/components/SideTabs.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'

const appStore = useAppStore()
const userStore = useUserStore()
const { locale } = useI18n()

// Element Plus 组件内置文案(分页/弹窗/表单校验)跟随应用语言
const elLocale = computed(() => (locale.value === 'en' ? en : zhCn))

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