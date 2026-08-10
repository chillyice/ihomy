<!-- PWA 安装引导:Android/Chrome 弹原生安装条;iOS Safari 显示"添加到主屏幕"提示;已安装/已关闭不再出现 -->
<template>
  <div v-if="visible" class="install-prompt">
    <div class="install-inner">
      <div class="install-text">
        <div class="install-title">{{ $t('pwa.title') }}</div>
        <div class="install-desc">{{ $t(ios ? 'pwa.iosHint' : 'pwa.desc') }}</div>
      </div>
      <el-button v-if="!ios" type="primary" size="small" round @click="install">{{ $t('pwa.install') }}</el-button>
      <el-button v-else size="small" round @click="iosHint">{{ $t('pwa.gotIt') }}</el-button>
      <span class="install-close" @click="dismiss"></span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const DISMISS_KEY = 'ihomy-install-dismissed'
const visible = ref(false)
const ios = ref(false)
let deferredPrompt = null

// 已安装(PWA standalone 模式)或用户关闭过 → 不再弹
const alreadyInstalled = () =>
  window.matchMedia('(display-mode: standalone)').matches ||
  window.navigator.standalone === true

const isIOS = () =>
  /iPad|iPhone|iPod/.test(navigator.userAgent) ||
  (navigator.userAgent.includes('Mac') && 'ontouchend' in document)

// Android/Chrome:捕获安装事件并显示引导(iOS 不触发,只在 4s 后兜底显示 iOS 提示)
const show = () => {
  if (localStorage.getItem(DISMISS_KEY) || alreadyInstalled()) return
  ios.value = isIOS()
  // 非 iOS 又没有安装事件(如桌面 Safari)则不打扰
  if (ios.value || deferredPrompt) visible.value = true
}

// 安装按钮:触发浏览器原生安装面板(无事件时按钮不出现,仅提示)
const install = async () => {
  if (deferredPrompt) {
    deferredPrompt.prompt()
    deferredPrompt = null
  }
  visible.value = false
}

// iOS 无安装 API,点击"知道了"直接收起;提示文案已说明手动步骤
const iosHint = () => { visible.value = false; localStorage.setItem(DISMISS_KEY, '1') }
const dismiss = () => { visible.value = false; localStorage.setItem(DISMISS_KEY, '1') }

onMounted(() => {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault()
    deferredPrompt = e
    show()
  })
  // iOS 无 beforeinstallprompt,进入后稍候给出"添加到主屏幕"提示
  setTimeout(show, 4000)
})
</script>

<style scoped>
.install-prompt {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 200;
  background: var(--color-card);
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.08);
  padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
}
.install-inner {
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
}
.install-text { flex: 1; min-width: 0; }
.install-title { font-size: 14px; font-weight: 600; color: var(--color-text); }
.install-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.install-close {
  width: 20px;
  height: 20px;
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
}
.install-close::before,
.install-close::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 14px;
  height: 1.5px;
  background: var(--color-text-secondary);
}
.install-close::before { transform: translate(-50%, -50%) rotate(45deg); }
.install-close::after { transform: translate(-50%, -50%) rotate(-45deg); }
</style>