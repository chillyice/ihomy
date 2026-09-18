<!-- GBA 播放器(EmulatorJS + mGBA 核心):本地加载 .gba(不托管/不分发)或按 src 加载已导入的游戏;支持全屏。 -->
<template>
  <div class="page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <div class="game-top-actions">
        <el-button v-if="backTo" size="small" round @click="router.push(backTo)">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('games.back') }}
        </el-button>
        <el-button v-if="fileName || src" size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          {{ isFullscreen ? $t('gba.exitFullscreen') : $t('gba.fullscreen') }}
        </el-button>
      </div>
    </PageToolbar>

    <div class="gba-card card">
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
        <div class="drop-title">{{ $t('gba.dropTitle') }}</div>
        <div class="drop-sub">{{ $t('gba.dropSub') }}</div>
      </div>

      <template v-else>
        <div class="gba-bar">
          <span class="gba-name">{{ fileName }}</span>
          <el-button v-if="!src" size="small" round @click="pick">{{ $t('gba.reselect') }}</el-button>
          <el-button v-if="!src" size="small" round text type="danger" @click="close">{{ $t('gba.close') }}</el-button>
        </div>
        <div ref="stage" class="gba-stage">
          <div ref="container" :id="playerId" class="gba-stage-inner"></div>
        </div>
      </template>

      <div v-if="loading" class="gba-status">{{ $t('gba.loading') }}</div>
      <div v-if="error" class="gba-status error">{{ error }}</div>

      <input v-if="!src" ref="fileInput" type="file" accept=".gba,.gbc,.gb" class="hidden-input" @change="onPick" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { FullScreen, ArrowLeft } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
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
// EmulatorJS 的 setElements() 用 document.querySelector(element) 定位容器,要求传 CSS 选择器字符串(官方约定 EJS_player='#game'),
// 不能传 DOM 元素(否则抛 "[object HTMLDivElement] is not a valid selector" 导致模拟器永不初始化)。故给容器分配稳定 id。
const playerId = 'gba-stage-inner'
const fileInput = ref(null)
const fileName = ref('')
const dragging = ref(false)
const loading = ref(false)
const error = ref('')
const isFullscreen = ref(false)

let objectUrl = null
let loadFallbackTimer = null

const pick = () => fileInput.value?.click()

// 清理旧模拟器(只清 DOM + 释放全局引用;objectUrl 由调用方在合适时机释放,否则会提前失效)
const teardownEmulator = () => {
  if (loadFallbackTimer) { clearTimeout(loadFallbackTimer); loadFallbackTimer = null }
  if (container.value) container.value.innerHTML = ''
  delete window.EJS_emulator
  delete window.EJS_ready
  delete window.EJS_onGameStart
}

const onEmulatorReady = () => {
  loading.value = false
  if (loadFallbackTimer) { clearTimeout(loadFallbackTimer); loadFallbackTimer = null }
}

const loadRom = async (romUrl) => {
  loading.value = true
  error.value = ''
  try {
    teardownEmulator()
    // fileName/src 变更后 v-else 分支(含 container ref)可能尚未渲染,等下一拍确保 DOM 就位
    await nextTick()

    // 设置 EmulatorJS 全局配置(必须在注入 loader.js 前就位;loader.js 读取后 new EmulatorJS 启动)
    window.EJS_player = '#' + playerId
    window.EJS_core = 'gba'
    window.EJS_gameUrl = romUrl
    window.EJS_pathtodata = '/emulatorjs/data/'
    window.EJS_startOnLoaded = true
    window.EJS_controlScheme = 0
    window.EJS_hideSettings = false
    // 用官方回调关 loading(loader.js 在实例就绪/游戏开始时触发),替代 MutationObserver
    window.EJS_ready = onEmulatorReady
    window.EJS_onGameStart = onEmulatorReady

    // 只注入一次 loader.js(其内部读取全局配置并启动模拟器)
    const s = document.createElement('script')
    s.src = '/emulatorjs/data/loader.js'
    document.head.appendChild(s)

    // 兜底:回调因异常未触发时,15s 后关 loading 避免永久转圈
    loadFallbackTimer = setTimeout(() => { loading.value = false }, 15000)
  } catch (e) {
    error.value = t('gba.loadFailed')
    ElMessage.error(t('gba.loadFailed'))
    loading.value = false
  }
}

const playFile = async (file) => {
  if (!file) return
  if (!/\.(gba|gbc|gb)$/i.test(file.name)) {
    ElMessage.warning(t('gba.invalidType'))
    return
  }
  fileName.value = file.name
  if (objectUrl) URL.revokeObjectURL(objectUrl) // 释放上一个本地 ROM 的 blob URL
  objectUrl = URL.createObjectURL(file)
  await loadRom(objectUrl)
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
  teardownEmulator()
  if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null }
  fileName.value = ''
  error.value = ''
}

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
  sunLight?.suspendEffects()
  if (props.src) {
    fileName.value = props.title || props.src.split('/').pop() || ''
    loadRom(props.src)
  }
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  teardownEmulator()
  if (objectUrl) { URL.revokeObjectURL(objectUrl); objectUrl = null }
  // 清理 EmulatorJS 全局变量
  delete window.EJS_player
  delete window.EJS_core
  delete window.EJS_gameUrl
  delete window.EJS_pathtodata
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.gba-card { padding: 24px; }
.drop-zone {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; min-height: 260px; border: 2px dashed rgba(0,0,0,0.14); border-radius: 14px;
  cursor: pointer; transition: all 0.2s ease; padding: 32px; text-align: center;
}
.drop-zone:hover, .drop-zone.dragging {
  border-color: var(--color-accent, var(--color-brand));
  background: rgba(var(--color-brand-rgb, 184,140,110), 0.06);
}
.drop-icon { font-size: 42px; }
.drop-title { font-size: 16px; font-weight: 600; color: var(--color-text-primary, #333); }
.drop-sub { font-size: 13px; color: var(--color-text-secondary, #888); }
.gba-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.gba-name {
  flex: 1; font-size: 14px; font-weight: 600; color: var(--color-text-primary, #333);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.gba-stage {
  position: relative; height: 480px; border-radius: 12px; background: #000; overflow: hidden;
}
.gba-stage-inner { width: 100%; height: 100%; }
.gba-stage:fullscreen { width: 100vw; height: 100vh; border-radius: 0; }
.gba-status { margin-top: 14px; font-size: 13px; color: var(--color-text-secondary, #888); text-align: center; }
.gba-status.error { color: #f56c6c; }
.hidden-input { display: none; }
@media (max-width: 768px) { .gba-stage { height: 320px; } }
</style>
