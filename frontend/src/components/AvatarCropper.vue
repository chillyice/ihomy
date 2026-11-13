<!-- 图片裁剪对话框:图片固定显示,滚轮/滑块缩放选区(中心锚定),canvas 裁剪,无外部依赖。
     比例由 ratio 决定(默认 1:1 头像,家庭封面传 16/9),几何计算全部走 utils/cropGeometry.js -->
<template>
  <el-dialog v-model="visible" append-to-body :title="title" width="500px" :close-on-click-modal="false" @closed="onClosed">
    <div v-if="imgSrc" class="cropper-box" ref="boxRef" @wheel.prevent="onWheel">
      <img :src="imgSrc" class="cropper-img" alt="" :style="{ width: imgW + 'px', height: imgH + 'px' }" @load="onImgLoad" ref="imgRef" />
      <!-- 遮罩 + 选区 -->
      <div class="cropper-overlay" :style="overlayStyle"></div>
      <div class="crop-box" :style="boxStyle" @mousedown="onDragStart" @touchstart="onDragStart">
        <div class="crop-grid"></div>
      </div>
    </div>
    <div v-else class="cropper-loading">{{ $t('common.loading') }}</div>
    <div v-if="imgLoaded" class="zoom-row">
      <span class="zoom-label">{{ $t('common.selectBox') }}</span>
      <el-slider v-model.number="zoom" :min="0.2" :max="1" :step="0.05" :show-tooltip="false" style="flex: 1" @input="applyZoom" />
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
import { clampCropPosition, cropBoxSize, cropOutputSize, cropSourceRect } from '@/utils/cropGeometry'

const props = defineProps({
  title: { type: String, default: '' },
  cancelText: { type: String, default: '' },
  confirmText: { type: String, default: '' },
  /** 选区宽高比:1 = 方图(头像),16/9 = 横图(家庭封面) */
  ratio: { type: Number, default: 1 },
  /** 导出宽度(像素),高度按 ratio 换算 */
  outputWidth: { type: Number, default: 256 },
  /** 导出 MIME:头像 png,封面 jpeg(体积更小) */
  mimeType: { type: String, default: 'image/png' },
  /** 导出文件名(不含扩展名,扩展名按 mimeType 补) */
  fileName: { type: String, default: 'avatar' },
})
const emit = defineEmits(['cropped'])

const visible = ref(false)
const imgSrc = ref('')
const imgLoaded = ref(false)
const boxRef = ref(null)
const imgRef = ref(null)

// 图片实际显示尺寸 + 选区位置/尺寸(按 props.ratio 的矩形)
const imgW = ref(0)
const imgH = ref(0)
const baseW = ref(0) // 适配容器的基准宽度(图片固定显示,不再缩放)
const baseH = ref(0) // 适配容器的基准高度
const zoom = ref(1) // 选区大小 = 图片内可放下的最大同比例矩形 × zoom(0.2~1)
const natW = ref(0) // 原图尺寸
const natH = ref(0)
const boxX = ref(0) // 选区左上角(相对显示图)
const boxY = ref(0)
const boxW = ref(0)
const boxH = ref(0)

// 图片加载后初始化:计算固定显示尺寸,选区居中铺满图片
const onImgLoad = () => {
  const img = imgRef.value
  if (!img) return
  natW.value = img.naturalWidth
  natH.value = img.naturalHeight
  // 显示尺寸:容器宽 460,高限 360,按比例缩放
  const maxW = 460,
    maxH = 360
  const ratio = Math.min(maxW / natW.value, maxH / natH.value, 1)
  baseW.value = natW.value * ratio
  baseH.value = natH.value * ratio
  zoom.value = 1
  boxW.value = 0 // 触发 applyZoom 的居中初始化
  boxH.value = 0
  applyZoom()
  imgLoaded.value = true
}

// 应用选区大小:图片固定,选区以自身中心为锚缩放(中心不动),边界 clamp 在图片内
const applyZoom = () => {
  imgW.value = Math.round(baseW.value)
  imgH.value = Math.round(baseH.value)
  const { w, h } = cropBoxSize(imgW.value, imgH.value, props.ratio, zoom.value)
  const cx = boxW.value > 0 ? boxX.value + boxW.value / 2 : imgW.value / 2
  const cy = boxH.value > 0 ? boxY.value + boxH.value / 2 : imgH.value / 2
  boxW.value = w
  boxH.value = h
  const pos = clampCropPosition(cx - w / 2, cy - h / 2, w, h, imgW.value, imgH.value)
  boxX.value = pos.x
  boxY.value = pos.y
}

// 滚轮缩放选区
const onWheel = (e) => {
  const delta = e.deltaY > 0 ? -0.05 : 0.05
  zoom.value = Math.max(0.2, Math.min(1, Math.round((zoom.value + delta) * 100) / 100))
  applyZoom()
}

const boxStyle = computed(() => ({
  left: boxX.value + 'px',
  top: boxY.value + 'px',
  width: boxW.value + 'px',
  height: boxH.value + 'px',
}))

// 四周半透明遮罩:用 clip-path 挖出选区透明区域,不阻塞选区拖动
const overlayStyle = computed(() => {
  const { x, y } = { x: boxX.value, y: boxY.value }
  const r = x + boxW.value
  const b = y + boxH.value
  return {
    position: 'absolute',
    inset: 0,
    background: `rgba(0,0,0,0.5)`,
    clipPath: `polygon(0 0, 0 100%, ${x}px 100%, ${x}px ${y}px, ${r}px ${y}px, ${r}px ${b}px, ${x}px ${b}px, ${x}px 100%, 100% 100%, 100% 0)`,
  }
})

// 拖动选区
let dragging = false
let startX = 0,
  startY = 0,
  origX = 0,
  origY = 0

const onDragStart = (e) => {
  dragging = true
  const pt = e.touches ? e.touches[0] : e
  startX = pt.clientX
  startY = pt.clientY
  origX = boxX.value
  origY = boxY.value
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
  const pos = clampCropPosition(origX + dx, origY + dy, boxW.value, boxH.value, imgW.value, imgH.value)
  boxX.value = pos.x
  boxY.value = pos.y
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
  const src = cropSourceRect({ x: boxX.value, y: boxY.value, w: boxW.value, h: boxH.value }, imgW.value, imgH.value, natW.value, natH.value)
  const out = cropOutputSize(props.outputWidth, props.ratio)
  const canvas = document.createElement('canvas')
  canvas.width = out.width
  canvas.height = out.height
  const ctx = canvas.getContext('2d')
  const img = imgRef.value
  ctx.drawImage(img, src.sx, src.sy, src.sw, src.sh, 0, 0, out.width, out.height)
  const jpeg = props.mimeType === 'image/jpeg'
  const mime = jpeg ? 'image/jpeg' : 'image/png'
  canvas.toBlob(
    (blob) => {
      if (blob) {
        const file = new File([blob], `${props.fileName}.${jpeg ? 'jpg' : 'png'}`, { type: mime })
        emit('cropped', file)
      }
      visible.value = false
    },
    mime,
    jpeg ? 0.92 : undefined,
  )
}

const onClosed = () => {
  imgSrc.value = ''
  imgLoaded.value = false
  zoom.value = 1
  boxX.value = 0
  boxY.value = 0
  boxW.value = 0
  boxH.value = 0
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
  display: block;
  width: fit-content;
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
.zoom-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.zoom-value {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  width: 40px;
  text-align: right;
}
.cropper-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.crop-box {
  position: absolute;
  cursor: move;
  border: 2px solid #fff;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0);
}
.crop-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.3) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.3) 1px, transparent 1px);
  background-size: 33.33% 33.33%;
}
.cropper-loading {
  text-align: center;
  padding: 60px;
  color: var(--el-text-color-secondary);
}
</style>
