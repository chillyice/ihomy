<!-- 百度网盘 OAuth 授权回调页:百度授权后重定向到 ?code=&state=,本页换取 token 后跳回存储设置 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: t('storage.baidu.cbTitle') }]" />
    <div class="card cb-box">
      <template v-if="state === 'loading'">
        <p class="cb-text">{{ t('storage.baidu.cbProcessing') }}</p>
      </template>
      <template v-else-if="state === 'ok'">
        <p class="cb-text ok">{{ t('storage.baidu.cbSuccess') }}</p>
        <el-button type="primary" @click="router.push('/settings?tab=storage')">{{ t('storage.baidu.backToStorage') }}</el-button>
      </template>
      <template v-else>
        <p class="cb-text fail">{{ error }}</p>
        <el-button @click="router.push('/settings?tab=storage')">{{ t('storage.baidu.backToStorage') }}</el-button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storageApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const state = ref('loading')
const error = ref('')

onMounted(async () => {
  const redirectUri = `${location.origin}/storage/baidu/callback`
  // 百度侧失败(用户拒绝等)会带 error/error_description 回跳
  if (route.query.error) {
    state.value = 'fail'
    error.value = `${t('storage.baidu.cbFail')}: ${route.query.error_description || route.query.error}`
    return
  }
  if (!userStore.isLoggedIn) {
    state.value = 'fail'
    error.value = t('storage.baidu.cbNeedLogin')
    return
  }
  try {
    await storageApi.baiduAuthCallback({ code: route.query.code, state: route.query.state, redirectUri })
    state.value = 'ok'
  } catch (e) {
    state.value = 'fail'
    error.value = e?.message || t('storage.baidu.cbFail')
  }
})
</script>

<style scoped>
.cb-box {
  max-width: 480px;
  margin: 80px auto 0;
  padding: 40px;
  text-align: center;
}
.cb-text {
  font-size: 15px;
  margin-bottom: 20px;
}
.cb-text.ok { color: var(--el-color-success); }
.cb-text.fail { color: var(--el-color-danger); word-break: break-all; }
</style>
