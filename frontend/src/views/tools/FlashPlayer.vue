<!-- Flash 播放器(Ruffle):本地加载 .swf 怀旧小游戏,不托管/不分发任何游戏文件。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.flash.title') }]" />

    <div class="flash-card card">
      <!-- 空态:拖拽/点击选择 .swf -->
      <div
        v-if="!fileName"
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
          <el-button size="small" round @click="pick">{{ $t('tools.flash.reselect') }}</el-button>
          <el-button size="small" round text type="danger" @click="close">{{ $t('tools.flash.close') }}</el-button>
        </div>
        <div ref="container" class="flash-stage"></div>
      </template>

      <div v-if="loading" class="flash-status">{{ $t('tools.flash.loading') }}</div>
      <div v-if="error" class="flash-status error">{{ error }}</div>

      <input ref="fileInput" type="file" accept=".swf" class="hidden-input" @change="onPick" />
    </div>
  </div>
</template>

<script setup>
// Flash 播放器:动态加载 /ruffle/ruffle.js(Ruffle 自托管运行时),用 Blob URL 播放本地 .swf
import { ref, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const container = ref(null)
const fileInput = ref(null)
const fileName = ref('')
const dragging = ref(false)
const loading = ref(false)
const error = ref('')

let player = null
let objectUrl = null

const pick = () => fileInput.value?.click()

/** 懒加载 Ruffle 运行时(脚本标签加载自托管文件,自动解析 wasm 路径) */
const loadRuffle = async () => {
  if (window.RufflePlayer) return window.RufflePlayer
  await new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = '/ruffle/ruffle.js'
    s.onload = resolve
    s.onerror = () => reject(new Error('Ruffle runtime load failed'))
    document.head.appendChild(s)
  })
  try {
    const cfg = window.RufflePlayer.config || (window.RufflePlayer.config = {})
    cfg.publicPath = '/ruffle'
  } catch (e) {
    // 脚本按自身 src 自动推导 publicPath,显式设置失败也不致命
  }
  return window.RufflePlayer
}

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

const playFile = async (file) => {
  if (!file) return
  if (!/\.swf$/i.test(file.name)) {
    ElMessage.warning(t('tools.flash.invalidType'))
    return
  }
  loading.value = true
  error.value = ''
  try {
    const RufflePlayer = await loadRuffle()
    destroyPlayer()
    objectUrl = URL.createObjectURL(file)
    const p = RufflePlayer.newest().createPlayer()
    p.style.width = '100%'
    p.style.height = '100%'
    player = p
    fileName.value = file.name
    await nextTick()
    container.value.appendChild(p)
    const api = typeof p.ruffle === 'function' ? p.ruffle() : p
    await api.load(objectUrl)
  } catch (e) {
    error.value = t('tools.flash.loadFailed')
    ElMessage.error(t('tools.flash.loadFailed'))
  } finally {
    loading.value = false
  }
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

onBeforeUnmount(destroyPlayer)
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
  height: 480px;
  border-radius: 12px;
  background: #000;
  overflow: hidden;
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
