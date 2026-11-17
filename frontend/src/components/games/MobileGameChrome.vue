<!-- 手机游戏沉浸态浮层:左上角只留「返回」为主,Web 端再补一个全屏切换(原生壳由状态栏隐藏接管) -->
<template>
  <div class="mobile-game-chrome">
    <button type="button" class="mgc-btn" :title="$t('games.back')" :aria-label="$t('games.back')" @click="$emit('back')">
      <el-icon><ArrowLeft /></el-icon>
    </button>
    <button
      v-if="canFullscreen"
      type="button"
      class="mgc-btn"
      :class="{ active: fullscreen }"
      :title="fullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen')"
      :aria-label="fullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen')"
      :aria-pressed="fullscreen"
      @click="toggleFullscreen"
    >
      <el-icon><FullScreen /></el-icon>
    </button>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, FullScreen } from '@element-plus/icons-vue'
import { isNativeApp } from '@/utils/platform'

defineEmits(['back'])

const fullscreen = ref(false)
// iPhone Safari 等不支持元素全屏的环境不显示这个按钮,避免再出现「点了没反应」
const canFullscreen = ref(false)

const syncFullscreen = () => {
  fullscreen.value = !!document.fullscreenElement
}

const toggleFullscreen = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen?.()
    else await document.documentElement.requestFullscreen?.({ navigationUI: 'hide' })
  } catch {
    // 浏览器拒绝(权限/策略)时保持现状;站内已经是沉浸布局,不影响继续玩
  }
}

onMounted(() => {
  canFullscreen.value = !isNativeApp() && typeof document.documentElement.requestFullscreen === 'function'
  syncFullscreen()
  document.addEventListener('fullscreenchange', syncFullscreen)
})

onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', syncFullscreen)
})
</script>

<style scoped>
.mobile-game-chrome {
  position: fixed;
  top: calc(var(--safe-area-inset-top, env(safe-area-inset-top, 0px)) + 8px);
  left: calc(env(safe-area-inset-left, 0px) + 8px);
  z-index: var(--z-float);
  display: flex;
  align-items: center;
  gap: 6px;
  pointer-events: none;
}

.mgc-btn {
  pointer-events: auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  padding: 0;
  border: 1px solid rgba(var(--color-card-rgb), 0.55);
  border-radius: 999px;
  background: rgba(var(--color-card-rgb), 0.92);
  color: var(--color-text);
  box-shadow: var(--shadow);
  font-size: 17px;
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  cursor: pointer;
}

.mgc-btn :deep(.el-icon) {
  font-size: 18px;
}

.mgc-btn:active {
  transform: scale(0.94);
}

.mgc-btn.active {
  color: var(--color-accent);
  border-color: rgba(var(--color-brand-rgb), 0.55);
}
</style>
