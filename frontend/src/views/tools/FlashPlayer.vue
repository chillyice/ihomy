<!-- Flash 播放器(Ruffle):本地加载 .swf(不托管/不分发)或按 src 加载已导入的游戏;支持全屏。 -->
<template>
  <div class="page">
    <!-- 顶栏:返回 + 全屏(工具按钮统一进顶栏,与物品定位一致) -->
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <div class="game-top-actions">
        <el-button v-if="backTo" size="small" round @click="router.push(backTo)">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('tools.flash.back') }}
        </el-button>
        <el-button v-if="fileName || src" size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          {{ isFullscreen ? $t('tools.flash.exitFullscreen') : $t('tools.flash.fullscreen') }}
        </el-button>
      </div>
    </PageToolbar>

    <div class="flash-card card">
      <!-- 本地模式:拖拽/点击选择 .swf -->
      <div
        v-if="!fileName && !src"
        class="drop-zone"
        :class="{ dragging }"
        @click="pick"
        @dragover.prevent="dragging = true"
        @dragleave="dragging = false"
        @drop.prevent="onDrop"
      >
        <div class="drop-icon">🎮</div>
        <div class="drop-title">{{ $t('tools.flash.dropTitle') }}</div>
        <div class="drop-sub">{{ $t('tools.flash.dropSub') }}</div>
      </div>

      <!-- 已加载:播放器 + 操作条 -->
      <template v-else>
        <div class="flash-bar">
          <span class="flash-name">{{ fileName }}</span>
          <el-button v-if="!src" size="small" round @click="pick">{{ $t('tools.flash.reselect') }}</el-button>
          <el-button v-if="!src" size="small" round text type="danger" @click="close">{{ $t('tools.flash.close') }}</el-button>
        </div>
        <div ref="stage" class="flash-stage">
          <div ref="container" class="flash-stage-inner"></div>
        </div>
      </template>

      <div v-if="loading" class="flash-status">{{ $t('tools.flash.loading') }}</div>
      <div v-if="error" class="flash-status error">{{ error }}</div>

      <input v-if="!src" ref="fileInput" type="file" accept=".swf" class="hidden-input" @change="onPick" />
    </div>
  </div>
</template>

<script setup>
// Flash 播放器:动态加载 /ruffle/ruffle.js(Ruffle 自托管运行时)。
// 无 src 时为本地模式(Blob URL 播放本地 .swf);有 src 时直接按 URL 播放已导入的游戏。支持浏览器原生全屏。
import { ref, onMounted, onBeforeUnmount, nextTick, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { FullScreen, ArrowLeft } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { loadRuffle } from '@/utils/ruffle'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const props = defineProps({
  src: { type: String, default: '' },
  title: { type: String, default: '' },
  backTo: { type: String, default: '' },
})

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)
const container = ref(null)
const stage = ref(null)
const fileInput = ref(null)
const fileName = ref('')
const dragging = ref(false)
const loading = ref(false)
const error = ref('')
const isFullscreen = ref(false)

let player = null
let objectUrl = null

const pick = () => fileInput.value?.click()

const destroyPlayer = () => {
  if (player) {
    try { player.remove() } catch (e) { /* 忽略卸载异常 */ }
    player = null
  }
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = null
  }
}

/** 创建 Ruffle 播放器并调用 loadFn 加载(loadFn 接收 player api) */
const mountAndLoad = async (loadFn) => {
  loading.value = true
  error.value = ''
  try {
    const RufflePlayer = await loadRuffle()
    destroyPlayer()
    const p = RufflePlayer.newest().createPlayer()
    p.style.width = '100%'
    p.style.height = '100%'
    player = p
    await nextTick()
    container.value.appendChild(p)
    const api = typeof p.ruffle === 'function' ? p.ruffle() : p
    await loadFn(api)
  } catch (e) {
    error.value = t('tools.flash.loadFailed')
    ElMessage.error(t('tools.flash.loadFailed'))
  } finally {
    loading.value = false
  }
}

const playFile = async (file) => {
  if (!file) return
  if (!/\.swf$/i.test(file.name)) {
    ElMessage.warning(t('tools.flash.invalidType'))
    return
  }
  fileName.value = file.name
  objectUrl = URL.createObjectURL(file)
  await mountAndLoad((api) => api.load(objectUrl))
}

const loadUrl = (url) => {
  fileName.value = props.title || (url.split('/').pop() || '')
  mountAndLoad((api) => api.load(url))
}

const onPick = (e) => {
  const f = e.target.files?.[0]
  if (f) playFile(f)
  e.target.value = ''
}
const onDrop = (e) => {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) playFile(f)
}
const close = () => {
  destroyPlayer()
  fileName.value = ''
  error.value = ''
}

// 全屏:对 .flash-stage 走浏览器原生 Fullscreen API,Ruffle 播放器随容器尺寸自适应
const toggleFullscreen = () => {
  if (!stage.value) return
  if (!document.fullscreenElement) {
    stage.value.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}
const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  // 播放 Flash/小游戏时关闭全局光影特效(与图片/视频/看书一致),离开时恢复
  sunLight?.suspendEffects()
  if (props.src) loadUrl(props.src)
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  destroyPlayer()
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.flash-card {
  padding: 24px;
}
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 260px;
  border: 2px dashed rgba(0, 0, 0, 0.14);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 32px;
  text-align: center;
}
.drop-zone:hover,
.drop-zone.dragging {
  border-color: var(--color-accent, var(--color-brand));
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.06);
}
.drop-icon {
  font-size: 42px;
}
.drop-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary, #333);
}
.drop-sub {
  font-size: 13px;
  color: var(--color-text-secondary, #888);
}
.flash-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.flash-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary, #333);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.flash-stage {
  position: relative;
  height: 480px;
  border-radius: 12px;
  background: #000;
  overflow: hidden;
}
.flash-stage-inner {
  width: 100%;
  height: 100%;
}
.flash-stage:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
}
.flash-status {
  margin-top: 14px;
  font-size: 13px;
  color: var(--color-text-secondary, #888);
  text-align: center;
}
.flash-status.error {
  color: #f56c6c;
}
.hidden-input {
  display: none;
}
@media (max-width: 768px) {
  .flash-stage { height: 320px; }
}
</style>
