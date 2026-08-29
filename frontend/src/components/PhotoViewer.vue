<!--
  照片预览播放器:全屏沉浸式照片浏览 + 幻灯片播放
  z-index: 63(功能层之上 MusicPlayer=62,光影层之下 bright-spot=65)
  光影层(光束/灰尘/暗角)渲染在照片之上,营造氛围感
  支持:键盘导航/触摸滑动/自动播放/Ken Burns缓推/缩略图条/进度条/分享链接/下载
-->
<template>
  <Teleport to="body">
  <transition name="pv-fade">
    <div v-if="visible" class="photo-viewer" @touchstart="onTouchStart" @touchend="onTouchEnd">
      <!-- 背景遮罩:半透明深色(光影层会叠加在上面) -->
      <div class="pv-backdrop" />

      <!-- 照片舞台 -->
      <div class="pv-stage" @click.self="close">
        <transition :name="slideDir === 'next' ? 'pv-slide-next' : 'pv-slide-prev'" mode="out-in">
          <div :key="current.id" class="pv-photo-frame" :class="{ 'ken-burns': playing }">
            <img
              :src="current.url"
              :alt="current.description || ''"
              draggable="false"
              @load="onImgLoad"
            />
          </div>
        </transition>

        <!-- 加载指示 -->
        <div v-if="imgLoading" class="pv-loading">
          <div class="pv-spinner" />
        </div>
      </div>

      <!-- 顶部信息栏 -->
      <div class="pv-topbar">
        <div class="pv-counter">
          <span class="pv-cur">{{ index + 1 }}</span>
          <span class="pv-sep">/</span>
          <span class="pv-total">{{ photos.length }}</span>
        </div>
        <div class="pv-topbar-actions">
          <!-- 播放速度 -->
          <div v-if="playing" class="pv-speed-group">
            <button
              v-for="s in speeds"
              :key="s.value"
              class="pv-speed-btn"
              :class="{ active: speed === s.value }"
              @click="speed = s.value"
            >{{ s.label }}</button>
          </div>
          <!-- 分享链接(有相册分享基准时才显示) -->
          <button v-if="shareBase" class="pv-icon-btn" :title="t('photoViewer.share')" @click="copyShare">
            <svg viewBox="0 0 24 24" width="22" height="22"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z" fill="currentColor"/></svg>
          </button>
          <!-- 下载当前照片 -->
          <button class="pv-icon-btn" :title="t('photoViewer.download')" @click="download">
            <svg viewBox="0 0 24 24" width="22" height="22"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z" fill="currentColor"/></svg>
          </button>
          <button class="pv-icon-btn" :title="t('photoViewer.play')" @click="togglePlay">
            <svg v-if="!playing" viewBox="0 0 24 24" width="22" height="22"><path d="M8 5v14l11-7z" fill="currentColor"/></svg>
            <svg v-else viewBox="0 0 24 24" width="22" height="22"><path d="M6 5h4v14H6zm8 0h4v14h-4z" fill="currentColor"/></svg>
          </button>
          <button class="pv-icon-btn" :title="t('common.close')" @click="close">
            <svg viewBox="0 0 24 24" width="22" height="22"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" fill="currentColor"/></svg>
          </button>
        </div>
      </div>

      <!-- 播放进度条 -->
      <div v-if="playing" class="pv-progress-track">
        <div class="pv-progress-fill" :style="{ animationDuration: speed + 's' }" :key="progressKey" />
      </div>

      <!-- 左右导航 -->
      <button v-if="photos.length > 1" class="pv-nav pv-nav-prev" @click="prev">
        <svg viewBox="0 0 24 24" width="32" height="32"><path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z" fill="currentColor"/></svg>
      </button>
      <button v-if="photos.length > 1" class="pv-nav pv-nav-next" @click="next">
        <svg viewBox="0 0 24 24" width="32" height="32"><path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z" fill="currentColor"/></svg>
      </button>

      <!-- 底部信息 + 缩略图条 -->
      <div class="pv-bottom">
        <!-- 照片元信息 -->
        <transition name="pv-info-fade">
          <div v-if="hasMeta" class="pv-meta">
            <p v-if="current.description" class="pv-desc">{{ current.description }}</p>
            <div class="pv-meta-row">
              <span v-if="current.uploaderName" class="pv-meta-item">
                <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" fill="currentColor"/></svg>
                {{ current.uploaderName }}
              </span>
              <span v-if="current.location" class="pv-meta-item">
                <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5a2.5 2.5 0 010-5 2.5 2.5 0 010 5z" fill="currentColor"/></svg>
                {{ current.location }}
              </span>
              <span v-if="current.takenAt" class="pv-meta-item">
                <svg viewBox="0 0 24 24" width="14" height="14"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm.5 5H11v6l5.25 3.15.75-1.23-4.5-2.67V7z" fill="currentColor"/></svg>
                {{ formatDate(current.takenAt) }}
              </span>
            </div>
          </div>
        </transition>

        <!-- 缩略图条 -->
        <div v-if="photos.length > 1" ref="stripRef" class="pv-thumb-strip">
          <div
            v-for="(p, i) in photos"
            :key="p.id"
            class="pv-thumb"
            :class="{ active: i === index }"
            @click="goTo(i)"
          >
            <img :src="p.url" :alt="p.description || ''" draggable="false" />
          </div>
        </div>
      </div>
    </div>
  </transition>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { shareId } from '@/utils/shareId'

const props = defineProps({
  visible: { type: Boolean, default: false },
  photos: { type: Array, default: () => [] },
  initialIndex: { type: Number, default: 0 },
  shareBase: { type: String, default: '' },
})
const emit = defineEmits(['update:visible', 'close'])

const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const index = ref(0)
const playing = ref(false)
const speed = ref(5) // seconds per slide
const slideDir = ref('next')
const imgLoading = ref(false)
const progressKey = ref(0) // force progress bar animation restart
const speeds = [
  { label: '3s', value: 3 },
  { label: '5s', value: 5 },
  { label: '8s', value: 8 },
]

let playTimer = null
let touchStartX = 0

const stripRef = ref(null)

// 当前激活缩略图滚动到可视区居中(播放到后面的照片时缩略图条跟随移动)
const scrollThumbIntoView = () => {
  nextTick(() => {
    const el = stripRef.value?.querySelector('.pv-thumb.active')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' })
  })
}

watch(index, scrollThumbIntoView)

const current = computed(() => props.photos[index.value] || {})
const hasMeta = computed(() =>
  current.value.description || current.value.uploaderName || current.value.location || current.value.takenAt
)

const formatDate = (d) => {
  if (!d) return ''
  const dt = new Date(String(d).length === 10 ? d + 'T00:00:00' : d)
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
}

const onImgLoad = () => { imgLoading.value = false }

// 复制当前照片分享链接(相册分享链接 + ?p= 混淆照片ID)
const copyShare = async () => {
  if (!props.shareBase || !current.value.id) return
  const url = `${props.shareBase}?p=${shareId(current.value.id)}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success(t('photoViewer.linkCopied'))
  } catch {
    ElMessage.error(t('photoViewer.copyFailed'))
  }
}

// 下载当前照片(同源静态文件,download 属性触发浏览器下载)
const download = () => {
  if (!current.value.url) return
  const a = document.createElement('a')
  a.href = current.value.url
  a.download = ''
  document.body.appendChild(a)
  a.click()
  a.remove()
}

const next = () => {
  if (props.photos.length <= 1) return
  slideDir.value = 'next'
  imgLoading.value = true
  index.value = (index.value + 1) % props.photos.length
}
const prev = () => {
  if (props.photos.length <= 1) return
  slideDir.value = 'prev'
  imgLoading.value = true
  index.value = (index.value - 1 + props.photos.length) % props.photos.length
}
const goTo = (i) => {
  slideDir.value = i > index.value ? 'next' : 'prev'
  imgLoading.value = true
  index.value = i
}

const togglePlay = () => {
  playing.value = !playing.value
  if (playing.value) startPlay()
  else stopPlay()
}

const startPlay = () => {
  stopPlay()
  progressKey.value++
  playTimer = setInterval(() => {
    slideDir.value = 'next'
    imgLoading.value = true
    index.value = (index.value + 1) % props.photos.length
    progressKey.value++ // restart progress bar
  }, speed.value * 1000)
}

const stopPlay = () => {
  if (playTimer) { clearInterval(playTimer); playTimer = null }
}

const close = () => {
  playing.value = false
  stopPlay()
  emit('update:visible', false)
  emit('close')
}

// keyboard
const onKey = (e) => {
  if (!props.visible) return
  if (e.key === 'ArrowRight') next()
  else if (e.key === 'ArrowLeft') prev()
  else if (e.key === ' ') { e.preventDefault(); togglePlay() }
  else if (e.key === 'Escape') close()
}

// touch swipe
const onTouchStart = (e) => { touchStartX = e.touches[0].clientX }
const onTouchEnd = (e) => {
  const dx = e.changedTouches[0].clientX - touchStartX
  if (Math.abs(dx) > 50) { dx < 0 ? next() : prev() }
}

watch(() => props.visible, (v) => {
  if (v) {
    index.value = props.initialIndex
    scrollThumbIntoView()
    document.body.style.overflow = 'hidden'
    sunLight?.suspendEffects()
  } else {
    document.body.style.overflow = ''
    stopPlay()
    sunLight?.restoreEffects()
  }
})

watch(() => props.initialIndex, (v) => { if (props.visible) index.value = v })

// restart timer when speed changes during playback
watch(speed, () => { if (playing.value) startPlay() })

onMounted(() => document.addEventListener('keydown', onKey))
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKey)
  stopPlay()
  document.body.style.overflow = ''
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* z-index 201:Teleport 到 body,高于导航栏(60)和光影层(65-100) */
.photo-viewer {
  position: fixed;
  inset: 0;
  z-index: 201;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.pv-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(8, 6, 4, 0.88);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
html.dark .pv-backdrop { background: rgba(4, 8, 16, 0.9); }

/* 照片舞台 */
.pv-stage {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.pv-photo-frame {
  position: absolute;
  max-width: 88vw;
  max-height: 78vh;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 20px 80px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.06);
}
.pv-photo-frame img {
  max-width: 88vw;
  max-height: 78vh;
  object-fit: contain;
  display: block;
}

/* Ken Burns 缓推效果(播放时) */
.pv-photo-frame.ken-burns img {
  animation: ken-burns 8s ease-out forwards;
}
@keyframes ken-burns {
  0% { transform: scale(1) translate(0, 0); }
  100% { transform: scale(1.12) translate(-2%, -1.5%); }
}

/* 加载指示 */
.pv-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 2;
}
.pv-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: rgba(255, 255, 255, 0.7);
  border-radius: 50%;
  animation: pv-spin 0.8s linear infinite;
}
@keyframes pv-spin { to { transform: rotate(360deg); } }

/* 顶部信息栏 */
.pv-topbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 3;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.5), transparent);
}
.pv-counter {
  font-size: 15px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: 1px;
  font-variant-numeric: tabular-nums;
}
.pv-cur { font-size: 22px; }
.pv-sep { margin: 0 4px; opacity: 0.5; }
.pv-total { opacity: 0.6; }

.pv-topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pv-speed-group {
  display: flex;
  gap: 2px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 2px;
  margin-right: 4px;
}
.pv-speed-btn {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.pv-speed-btn:hover { color: rgba(255, 255, 255, 0.85); }
.pv-speed-btn.active {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
}

.pv-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  transition: all 0.2s;
  backdrop-filter: blur(4px);
}
.pv-icon-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

/* 播放进度条 */
.pv-progress-track {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: rgba(255, 255, 255, 0.08);
  z-index: 4;
}
.pv-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, rgba(184, 140, 110, 0.9), rgba(212, 178, 152, 0.9));
  border-radius: 0 2px 2px 0;
  animation: pv-progress linear forwards;
}
@keyframes pv-progress {
  from { width: 0; }
  to { width: 100%; }
}

/* 左右导航按钮 */
.pv-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.35);
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  transition: all 0.25s;
  backdrop-filter: blur(4px);
}
.pv-nav:hover {
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  transform: translateY(-50%) scale(1.08);
}
.pv-nav-prev { left: 20px; }
.pv-nav-next { right: 20px; }

/* 底部信息 + 缩略图条 */
.pv-bottom {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 3;
  padding: 24px 24px 16px;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.6), transparent);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

/* 照片元信息 */
.pv-meta {
  text-align: center;
  max-width: 600px;
}
.pv-desc {
  color: rgba(255, 255, 255, 0.95);
  font-size: 15px;
  line-height: 1.6;
  margin-bottom: 8px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.5);
}
.pv-meta-row {
  display: flex;
  justify-content: center;
  gap: 18px;
  flex-wrap: wrap;
}
.pv-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
}

/* 缩略图条 */
.pv-thumb-strip {
  display: flex;
  gap: 6px;
  padding: 4px;
  overflow-x: auto;
  max-width: 80vw;
  scrollbar-width: none;
}
.pv-thumb-strip::-webkit-scrollbar { display: none; }
.pv-thumb {
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  opacity: 0.45;
  border: 2px solid transparent;
  transition: all 0.2s;
}
.pv-thumb:hover { opacity: 0.8; }
.pv-thumb.active {
  opacity: 1;
  border-color: rgba(212, 178, 152, 0.9);
  transform: scale(1.08);
}
.pv-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* --- 过渡动画 --- */

/* 整体淡入 */
.pv-fade-enter-active, .pv-fade-leave-active { transition: opacity 0.3s ease; }
.pv-fade-enter-from, .pv-fade-leave-to { opacity: 0; }

/* 照片切换:下一张(新从右进,旧向左出) */
.pv-slide-next-enter-active,
.pv-slide-next-leave-active,
.pv-slide-prev-enter-active,
.pv-slide-prev-leave-active {
  transition: opacity 0.5s ease, transform 0.5s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}
.pv-slide-next-enter-from { opacity: 0; transform: translateX(40px) scale(0.97); }
.pv-slide-next-leave-to { opacity: 0; transform: translateX(-40px) scale(0.97); }
.pv-slide-prev-enter-from { opacity: 0; transform: translateX(-40px) scale(0.97); }
.pv-slide-prev-leave-to { opacity: 0; transform: translateX(40px) scale(0.97); }

/* 元信息淡入 */
.pv-info-fade-enter-active, .pv-info-fade-leave-active { transition: opacity 0.3s ease; }
.pv-info-fade-enter-from, .pv-info-fade-leave-to { opacity: 0; }

/* 移动端适配 */
@media (max-width: 768px) {
  .pv-photo-frame, .pv-photo-frame img { max-width: 96vw; max-height: 70vh; }
  .pv-nav { width: 40px; height: 40px; }
  .pv-nav-prev { left: 8px; }
  .pv-nav-next { right: 8px; }
  .pv-thumb { width: 44px; height: 44px; }
  .pv-topbar { padding: 12px 16px; }
}
</style>
