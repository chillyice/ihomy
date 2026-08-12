<!-- 根组件:全局顶栏 + 页面内容 + 回到顶部,负责首页数据初始化、登录态同步与语言环境 -->
<template>
  <el-config-provider :locale="elLocale">
    <AppHeader v-if="!isImmersive" />
    <router-view />
    <BackToTop v-if="!isImmersive" />
    <InstallPrompt />
    <SideTabs v-if="!userStore.isOps && !isImmersive" />
    <MusicPlayer v-if="!userStore.isOps && !isImmersive" />
  </el-config-provider>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
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
const route = useRoute()
const { locale } = useI18n()

// 沉浸式页面(如首页房间):隐藏全局顶栏/回到顶部/侧栏/音乐播放器,页面自带沉浸式 UI
const isImmersive = computed(() => !!route.meta.immersive)

const elLocale = computed(() => (locale.value === 'en' ? en : zhCn))

onMounted(() => appStore.init())

watch(
  () => userStore.isLoggedIn,
  () => {
    appStore.reset()
    appStore.init()
  },
)
</script>