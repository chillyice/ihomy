<!-- 头像裁剪对话框:图片固定显示,滚轮/滑块缩放正方形选择框(中心锚定),canvas 裁剪,无外部依赖 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="title" width="500px" :close-on-click-modal="false" @closed="onClosed">
    <div v-if="imgSrc" class="cropper-box" ref="boxRef" @wheel.prevent="onWheel">
      <img :src="imgSrc" class="cropper-img" :style="{ width: imgW + 'px', height: imgH + 'px' }" @load="onImgLoad" ref="imgRef" />
      <!-- 遮罩 + 正方形选区 -->
      <div class="cropper-overlay" :style="overlayStyle"></div>
      <div class="crop-square" :style="squareStyle" @mousedown="onDragStart" @touchstart="onDragStart">
        <div class="crop-grid"></div>
      </div>
    </div>
    <div v-else class="cropper-loading">加载中...</div>
    <div v-if="imgLoaded" class="zoom-row">
      <span class="zoom-label">选择框</span>
      <el-slider v-model.number="zoom" :min="0.2" :max="1" :step="0.05" :show-tooltip="false" style="flex:1" @input="applyZoom" />
      <span class="zoom-value">{{ Math.round(zoom * 100) }}%</span>
    </div>
    <template #footer>
      <el-button @click="visible = false">{{ cancelText }}</el-button>
      <el-button type="primary" :disabled="!imgLoaded" @click="onConfirm">{{ confirmText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  title: { type: String, default: '裁剪头像' },
  cancelText: { type: String, default: '取消' },
  confirmText: { type: String, default: '确定' },
})
const emit = defineEmits(['cropped'])

const visible = ref(false)
const imgSrc = ref('')
const imgLoaded = ref(false)
const boxRef = ref(null)
const imgRef = ref(null)

// 图片实际显示尺寸 + 选区位置(正方形)
const imgW = ref(0)
const imgH = ref(0)
const baseW = ref(0)  // 适配容器的基准宽度(图片固定显示,不再缩放)
const baseH = ref(0)  // 适配容器的基准高度
const zoom = ref(1)   // 选择框大小 = 图片短边 × zoom(0.2~1)
const natW = ref(0)  // 原图尺寸
const natH = ref(0)
const sqX = ref(0)   // 选区左上角(相对显示图)
const sqSize = ref(0)

// 图片加载后初始化:计算固定显示尺寸,选区居中铺满短边
const onImgLoad = () => {
  const img = imgRef.value
  if (!img) return
  natW.value = img.naturalWidth
  natH.value = img.naturalHeight
  // 显示尺寸:容器宽 460,高限 360,按比例缩放
  const maxW = 460, maxH = 360
  const ratio = Math.min(maxW / natW.value, maxH / natH.value, 1)
  baseW.value = natW.value * ratio
  baseH.value = natH.value * ratio
  zoom.value = 1
  sqSize.value = 0 // 触发 applyZoom 的居中初始化
  applyZoom()
  imgLoaded.value = true
}

// 应用选择框大小:图片固定,选区以自身中心为锚缩放(中心不动),边界 clamp 在图片内
const applyZoom = () => {
  imgW.value = Math.round(baseW.value)
  imgH.value = Math.round(baseH.value)
  const short = Math.min(imgW.value, imgH.value)
  const newSize = Math.round(short * zoom.value)
  const cx = sqSize.value > 0 ? sqX.value + sqSize.value / 2 : imgW.value / 2
  const cy = sqSize.value > 0 ? sqY.value + sqSize.value / 2 : imgH.value / 2
  sqSize.value = newSize
  sqX.value = Math.max(0, Math.min(imgW.value - newSize, cx - newSize / 2))
  sqY.value = Math.max(0, Math.min(imgH.value - newSize, cy - newSize / 2))
}

// 滚轮缩放选择框
const onWheel = (e) => {
  const delta = e.deltaY > 0 ? -0.05 : 0.05
  zoom.value = Math.max(0.2, Math.min(1, Math.round((zoom.value + delta) * 100) / 100))
  applyZoom()
}
const sqY = ref(0)

const squareStyle = computed(() => ({
  left: sqX.value + 'px',
  top: sqY.value + 'px',
  width: sqSize.value + 'px',
  height: sqSize.value + 'px',
}))

// 四周半透明遮罩(用 4 个 div 模拟,不阻塞选区拖动)
const overlayStyle = computed(() => ({
  position: 'absolute',
  inset: 0,
  background: `rgba(0,0,0,0.5)`,
  // 用 clip-path 挖出选区透明区域
  clipPath: `polygon(0 0, 0 100%, ${sqX.value}px 100%, ${sqX.value}px ${sqY.value}px, ${sqX.value + sqSize.value}px ${sqY.value}px, ${sqX.value + sqSize.value}px ${sqY.value + sqSize.value}px, ${sqX.value}px ${sqY.value + sqSize.value}px, ${sqX.value}px 100%, 100% 100%, 100% 0)`,
}))

// 拖动选区
let dragging = false
let startX = 0, startY = 0, origX = 0, origY = 0

const onDragStart = (e) => {
  dragging = true
  const pt = e.touches ? e.touches[0] : e
  startX = pt.clientX
  startY = pt.clientY
  origX = sqX.value
  origY = sqY.value
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
  window.addEventListener('touchmove', onDragMove, { passive: false })
  window.addEventListener('touchend', onDragEnd)
  e.preventDefault()
}
const onDragMove = (e) => {
  if (!dragging) return
  e.preventDefault?.()
  const pt = e.touches ? e.touches[0] : e
  const dx = pt.clientX - startX
  const dy = pt.clientY - startY
  sqX.value = Math.max(0, Math.min(imgW.value - sqSize.value, origX + dx))
  sqY.value = Math.max(0, Math.min(imgH.value - sqSize.value, origY + dy))
}
const onDragEnd = () => {
  dragging = false
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
  window.removeEventListener('touchmove', onDragMove)
  window.removeEventListener('touchend', onDragEnd)
}

// 确认裁剪:canvas 绘制选区对应原图区域,导出 Blob
const onConfirm = () => {
  const scaleX = natW.value / imgW.value
  const scaleY = natH.value / imgH.value
  const sx = sqX.value * scaleX
  const sy = sqY.value * scaleY
  const sSize = sqSize.value * scaleX
  const out = 256 // 输出 256x256
  const canvas = document.createElement('canvas')
  canvas.width = out
  canvas.height = out
  const ctx = canvas.getContext('2d')
  const img = imgRef.value
  ctx.drawImage(img, sx, sy, sSize, sSize, 0, 0, out, out)
  canvas.toBlob((blob) => {
    if (blob) {
      const file = new File([blob], 'avatar.png', { type: 'image/png' })
      emit('cropped', file)
    }
    visible.value = false
  }, 'image/png')
}

const onClosed = () => {
  imgSrc.value = ''
  imgLoaded.value = false
  zoom.value = 1
}

// 打开:传入 File
const open = (file) => {
  const reader = new FileReader()
  reader.onload = (e) => {
    imgSrc.value = e.target.result
    visible.value = true
  }
  reader.readAsDataURL(file)
}

defineExpose({ open })
</script>

<style scoped>
.cropper-box {
  position: relative;
  display: inline-block;
  margin: 0 auto;
  user-select: none;
}
.cropper-img {
  display: block;
}
.zoom-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding: 0 4px;
}
.zoom-label { font-size: 13px; color: var(--el-text-color-secondary); white-space: nowrap; }
.zoom-value { font-size: 12px; color: var(--el-text-color-secondary); width: 40px; text-align: right; }
.cropper-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.crop-square {
  position: absolute;
  cursor: move;
  border: 2px solid #fff;
  box-shadow: 0 0 0 9999px rgba(0,0,0,0);
}
.crop-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255,255,255,0.3) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.3) 1px, transparent 1px);
  background-size: 33.33% 33.33%;
}
.cropper-loading {
  text-align: center;
  padding: 60px;
  color: var(--el-text-color-secondary);
}
</style>
