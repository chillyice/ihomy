<!-- 照片瀑布(相册内视图):家庭照片随风飘落的沉浸式浏览,悬停暂停看信息,点击进全屏查看器。
     原独立页 /cascade 已并入相册:由相册页「照片瀑布」按钮或 /album?cascade=1 唤起,不再单独占导航入口。 -->
<template>
  <div v-if="visible" class="pc-overlay">
    <div class="pc-head">
      <span class="pc-title paper-surface">{{ $t('cascade.title') }}</span>
      <span class="pc-hint">{{ $t('cascade.hint') }}</span>
      <span class="pc-close" v-a11y-click :title="$t('common.close')" @click="close">
        <el-icon><Close /></el-icon>
      </span>
    </div>

    <div ref="stage" class="cascade-stage">
      <div v-for="c in cards" :key="c.key" class="leaf-wrap" :class="{ fading: c.fading }" :style="cardStyle(c)">
        <div class="leaf-swing" @animationiteration.self="turnCard(c)">
          <div
            v-a11y-click
            class="leaf-card"
            :class="{ hovered: c.hovered }"
            @mouseenter="pauseCard(c)"
            @mouseleave="resumeCard(c)"
            @touchstart="pauseCard(c)"
            @touchend="resumeCard(c)"
            @click.stop="openViewer(c)"
          >
            <AlbumThumbnail :url="c.photo.url" :alt="c.photo.description || ''" />
            <div v-if="c.hovered" class="photo-info">
              <div v-if="c.photo.description" class="info-desc">{{ c.photo.description }}</div>
              <div class="info-meta">
                <span v-if="c.photo.sharedFrom">{{
                  $t('audience.sharedFrom', { name: c.photo.sharedFrom.alias || c.photo.sharedFrom.familyName })
                }}</span>
                <span v-if="c.photo.uploaderName">{{ c.photo.uploaderName }}</span>
                <span v-if="c.photo.takenAt">{{ formatDate(c.photo.takenAt) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="!loading && !photos.length" class="cascade-empty">
      <el-empty :description="$t('cascade.empty')" />
    </div>

    <PhotoViewer v-model:visible="viewerVisible" :photos="photos" :initial-index="viewerIdx" />
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { photoApi } from '@/api'
import PhotoViewer from '@/components/PhotoViewer.vue'
import AlbumThumbnail from '@/components/AlbumThumbnail.vue'

const props = defineProps({ visible: { type: Boolean, default: false } })
const emit = defineEmits(['update:visible'])

const stage = ref(null)
const photos = ref([])
const cards = ref([])
const loading = ref(true)
const viewerVisible = ref(false)
const viewerIdx = ref(0)

let cardSeq = 0
let spawnTimer = null
let fadeTimers = []

const clearTimers = () => {
  if (spawnTimer) {
    clearInterval(spawnTimer)
    spawnTimer = null
  }
  fadeTimers.forEach((t) => clearTimeout(t))
  fadeTimers = []
}

// Leave room for the smaller drift that accompanies the photo's tilt.
const swingDistance = (limit) => limit * (0.4 + Math.random() * 0.4)

const turnCard = (card) => {
  // Alternate iterations meet at one edge. Change only the opposite destination.
  if (card.headingRight) card.leftSwing = swingDistance(card.reach)
  else card.rightSwing = swingDistance(card.reach)
  card.headingRight = !card.headingRight
}

const spawnCard = () => {
  if (!photos.value.length) return
  const photo = photos.value[Math.floor(Math.random() * photos.value.length)]
  // 窄屏(移动端)缩小卡片尺寸,避免超出视口被 overflow:hidden 裁掉
  const narrow = window.innerWidth < 768
  const maxSize = narrow ? 150 : 220
  const minSize = narrow ? 90 : 130
  const size = minSize + Math.random() * (maxSize - minSize)
  const width = stage.value?.clientWidth || window.innerWidth
  const room = Math.max(0, width - size)
  const reach = Math.min(room * 0.35, narrow ? 65 : 145)
  const card = {
    key: ++cardSeq,
    photo,
    x: reach + Math.random() * (room - 2 * reach),
    duration: 20 + Math.random() * 12,
    size,
    rot: (Math.random() - 0.5) * 14,
    reach,
    leftSwing: swingDistance(reach),
    rightSwing: swingDistance(reach),
    headingRight: true,
    swing: 3.2 + Math.random() * 1.8,
    phase: -Math.random() * 1.5,
    breeze: reach * (Math.random() < 0.5 ? -0.2 : 0.2),
    flutter: 6 + Math.random() * 4,
    flutterPhase: -Math.random() * 6,
    hovered: false,
    fading: false,
  }
  cards.value.push(card)
  if (cards.value.length > 20) {
    const old = cards.value[0]
    old.fading = true
    fadeTimers.push(
      setTimeout(() => {
        cards.value = cards.value.filter((c) => c !== old)
      }, 1500),
    )
  }
}

const pauseCard = (c) => {
  c.hovered = true
}
const resumeCard = (c) => {
  c.hovered = false
}

const cardStyle = (c) => ({
  left: c.x + 'px',
  width: c.size + 'px',
  animationDuration: c.duration + 's',
  '--swing-time': c.swing + 's',
  '--swing-phase': c.phase + 's',
  '--breeze': c.breeze + 'px',
  '--flutter-time': c.flutter + 's',
  '--flutter-phase': c.flutterPhase + 's',
  '--leaf-rot': c.rot + 'deg',
  '--left-swing': -c.leftSwing + 'px',
  '--right-swing': c.rightSwing + 'px',
  animationPlayState: c.hovered ? 'paused' : 'running',
})

const openViewer = (c) => {
  const idx = photos.value.findIndex((p) => p.id === c.photo.id)
  viewerIdx.value = Math.max(0, idx)
  viewerVisible.value = true
}

const formatDate = (d) => {
  if (!d) return ''
  // 纯日期串(10 位)补上 T00:00:00 再解析:new Date('yyyy-MM-dd') 按 UTC 解析,
  // 在东八区会退成前一天,补时间后按本地时区解析才对得上拍摄日期
  const dt = new Date(String(d).length === 10 ? d + 'T00:00:00' : d)
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
}

const close = () => emit('update:visible', false)

const load = async () => {
  loading.value = true
  cards.value = []
  try {
    photos.value = (await photoApi.cascade()) || []
    if (photos.value.length) {
      for (let i = 0; i < 5; i++) {
        fadeTimers.push(setTimeout(() => spawnCard(), i * 1000))
      }
      spawnTimer = setInterval(spawnCard, 3000)
    }
  } catch (e) {
    photos.value = []
  } finally {
    loading.value = false
  }
}

// 每次打开重新拉取并起飘落;关闭即停表并清空,避免后台空转(持续动画页性能规范)
watch(
  () => props.visible,
  (v) => {
    if (v) load()
    else {
      clearTimers()
      cards.value = []
      viewerVisible.value = false
    }
  },
)

onBeforeUnmount(clearTimers)
</script>

<style scoped>
.pc-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-immersive);
  overflow: hidden;
  background: var(--color-bg);
}
.pc-head {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  pointer-events: none;
}
.pc-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
html.warm-paper-page .pc-title {
  flex: none;
  padding: 2px 8px;
  border-radius: 4px;
  background-color: var(--color-card);
}
.pc-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pc-close {
  pointer-events: auto;
  flex: none;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  cursor: pointer;
  color: var(--color-text-secondary);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  transition:
    color 0.2s,
    transform 0.2s;
}
.pc-close:hover {
  color: var(--color-primary);
  transform: scale(1.08);
}

.cascade-stage {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: auto;
}

.leaf-wrap {
  --left-swing: -48px;
  --right-swing: 48px;
  --swing-time: 3s;
  --swing-phase: 0s;
  --breeze: 10px;
  --flutter-time: 8s;
  --flutter-phase: 0s;
  position: absolute;
  top: -200px;
  animation: leaf-fall linear infinite;
  will-change: transform;
}
.leaf-wrap.fading .leaf-swing {
  opacity: 0;
}
.leaf-card {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  animation: leaf-flutter var(--flutter-time) ease-in-out var(--flutter-phase) infinite;
  animation-play-state: inherit;
  transform-origin: 48% 35%;
  transition:
    scale 0.3s ease,
    box-shadow 0.3s ease;
}
.leaf-card img {
  display: block;
  width: 100%;
  height: auto;
  pointer-events: none;
}
.leaf-card.hovered {
  scale: 1.15;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

@keyframes leaf-fall {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(calc(100vh + 260px));
  }
}
.leaf-swing {
  animation: leaf-swing var(--swing-time) cubic-bezier(0.5, 0, 0.5, 1) var(--swing-phase) infinite alternate;
  animation-play-state: inherit;
  transition: opacity 1.5s ease;
}
@keyframes leaf-swing {
  from {
    transform: translateX(var(--left-swing)) rotate(-6deg);
  }
  to {
    transform: translateX(var(--right-swing)) rotate(6deg);
  }
}
/* The window leaf catches a breeze, folds slightly, then drifts back.
   Give that motion its own rhythm so it carries through each turn. */
@keyframes leaf-flutter {
  0%,
  100% {
    transform: translate(0, 0) rotate(var(--leaf-rot)) scaleX(0.99);
  }
  24% {
    transform: translate(var(--breeze), -7px) rotate(calc(var(--leaf-rot) + 7deg)) scaleX(0.93);
  }
  60% {
    transform: translate(calc(var(--breeze) * 0.3), 5px) rotate(calc(var(--leaf-rot) - 5deg)) scaleX(1);
  }
}

.photo-info {
  position: absolute;
  right: 6px;
  bottom: 6px;
  max-width: 90%;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.4;
}
.info-desc {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.info-meta {
  display: flex;
  gap: 8px;
  opacity: 0.85;
  flex-wrap: nowrap;
  overflow: hidden;
  white-space: nowrap;
}

.cascade-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
}

@media (max-width: 768px) {
  .leaf-card {
    border-radius: 6px;
  }
  .photo-info {
    font-size: 10px;
  }
  .pc-hint {
    display: none;
  }
}
</style>
