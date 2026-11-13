<!--
  网页端新版本提示:服务端版本已高于本页跑的 bundle 时,底部条提示「刷新」。
  背景:部署新 dist 后,PWA 预缓存的旧 index-*.js 会让老页面继续跑旧代码(踩坑速查 §7.2),
  以前只能靠用户自己 Ctrl+Shift+R。这里比对 /api/public/version(纯读缓存值,不探依赖),
  发现线上更新就提示;点刷新会先注销 Service Worker 并清缓存,再真正重载,确保拿到新 index.html。
  只在 Web 生效——原生壳(APK)里的页面是打进包里的,刷新 webview 拿不到新版,该由应用内更新接管。
-->
<template>
  <transition name="web-update">
    <div v-if="visible" class="web-update" role="status" aria-live="polite">
      <span class="web-update__text">{{ $t('appUpdate.webReady', { version: serverVersion }) }}</span>
      <el-button size="small" type="primary" @click="reload">{{ $t('appUpdate.webReload') }}</el-button>
      <el-button size="small" text @click="dismiss">{{ $t('appUpdate.later') }}</el-button>
    </div>
  </transition>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { webRefreshNeeded } from '@/utils/appUpdate'
import { serverUrl } from '@/utils/assetUrl'

/** 定时复查间隔:一直在用的人也能在十分钟内看到提示 */
const CHECK_MS = 10 * 60 * 1000
/** 切回前台触发的复查节流:来回切标签页不必每次都问服务端 */
const MIN_GAP_MS = 60 * 1000
/** 首查延迟:让首屏请求先走完,别和首页聚合抢带宽 */
const FIRST_DELAY_MS = 15 * 1000

const visible = ref(false)
const serverVersion = ref('')
let timer = null
let firstTimer = null
let lastCheckAt = 0
/** 用户点了「稍后」:本轮不再打扰,刷新后自然重新判断 */
let dismissed = false

async function check() {
  if (dismissed) return
  lastCheckAt = Date.now()
  try {
    const res = await fetch(serverUrl(`/api/public/version?_=${Date.now()}`), { cache: 'no-store' })
    if (!res.ok) return
    const body = await res.json()
    const version = body?.data?.version || ''
    if (!webRefreshNeeded({ server: version, current: __APP_VERSION__ })) return
    serverVersion.value = version
    visible.value = true
  } catch (e) {
    // 离线 / 接口异常:静默,下次再查
  }
}

/** 刷新:先注销 SW 并清 Cache Storage,否则新 index.html 仍可能被旧缓存顶掉 */
async function reload() {
  visible.value = false
  try {
    const registrations = (await navigator.serviceWorker?.getRegistrations?.()) || []
    await Promise.all(registrations.map((reg) => reg.unregister()))
    const keys = (await window.caches?.keys?.()) || []
    await Promise.all(keys.map((key) => window.caches.delete(key)))
  } catch (e) {
    // 清理失败也照常刷新:最坏是这一次仍跑旧 bundle,下次再提示
  }
  window.location.reload()
}

function dismiss() {
  dismissed = true
  visible.value = false
}

function onVisibility() {
  if (document.visibilityState !== 'visible') return
  if (Date.now() - lastCheckAt < MIN_GAP_MS) return
  check()
}

onMounted(() => {
  firstTimer = setTimeout(check, FIRST_DELAY_MS)
  timer = setInterval(check, CHECK_MS)
  document.addEventListener('visibilitychange', onVisibility)
})

onUnmounted(() => {
  clearTimeout(firstTimer)
  clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<style scoped>
.web-update {
  position: fixed;
  left: 50%;
  bottom: calc(18px + env(safe-area-inset-bottom, 0px));
  transform: translateZ(0) translateX(-50%);
  z-index: var(--z-float-top);
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(560px, calc(100vw - 32px));
  padding: 10px 12px 10px 16px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  box-shadow: var(--shadow-hover);
  color: var(--color-text);
  font-size: 13px;
  line-height: 1.4;
}
.web-update__text {
  flex: 1;
  min-width: 0;
}
.web-update-enter-active,
.web-update-leave-active {
  transition:
    opacity 0.26s ease,
    transform 0.26s ease;
}
.web-update-enter-from,
.web-update-leave-to {
  opacity: 0;
  transform: translateZ(0) translateX(-50%) translateY(12px);
}
@media (max-width: 640px) {
  .web-update {
    flex-wrap: wrap;
    gap: 8px;
  }
  .web-update__text {
    flex-basis: 100%;
  }
}
</style>
