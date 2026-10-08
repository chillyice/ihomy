<!-- 照片瀑布(相册内视图):家庭照片随风飘落的沉浸式浏览,悬停暂停看信息,点击进全屏查看器。
     原独立页 /cascade 已并入相册:由相册页「照片瀑布」按钮或 /album?cascade=1 唤起,不再单独占导航入口。 -->
<template>
  <div v-if="visible" class="pc-overlay">
    <div class="pc-head">
      <span class="pc-title">{{ $t('cascade.title') }}</span>
      <span class="pc-hint">{{ $t('cascade.hint') }}</span>
      <span class="pc-close" v-a11y-click :title="$t('common.close')" @click="close">
        <el-icon><Close /></el-icon>
      </span>
    </div>

    <div ref="stage" class="cascade-stage">
      <div v-for="c in cards" :key="c.key" class="leaf-wrap" :class="{ fading: c.fading }" :style="cardStyle(c)">
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
          <img
            :src="c.photo.url && c.photo.url.includes('?') ? c.photo.url + '&thumb=1' : c.photo.url"
            draggable="false"
            :alt="c.photo.description || ''"
          />
          <div v-if="c.hovered" class="photo-info">
            <div v-if="c.photo.description" class="info-desc">{{ c.photo.description }}</div>
            <div class="info-meta">
              <span v-if="c.photo.uploaderName">{{ c.photo.uploaderName }}</span>
              <span v-if="c.photo.takenAt">{{ formatDate(c.photo.takenAt) }}</span>
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

const spawnCard = () => {
  if (!photos.value.length) return
  const photo = photos.value[Math.floor(Math.random() * photos.value.length)]
  // 窄屏(移动端)缩小卡片尺寸,避免超出视口被 overflow:hidden 裁掉
  const narrow = window.innerWidth < 768
  const maxSize = narrow ? 150 : 220
  const minSize = narrow ? 90 : 130
  const card = {
    key: ++cardSeq,
    photo,
    x: narrow ? 5 + Math.random() * 70 : 5 + Math.random() * 90,
    duration: 15 + Math.random() * 12,
    size: minSize + Math.random() * (maxSize - minSize),
    rot: (Math.random() - 0.5) * 30,
    drift: (Math.random() - 0.5) * 120,
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
  left: c.x + '%',
  width: c.size + 'px',
  animationDuration: c.duration + 's',
  '--leaf-rot': c.rot + 'deg',
  '--leaf-drift': c.drift + 'px',
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
  z-index: 2000;
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
  position: absolute;
  top: -200px;
  animation: leaf-fall linear infinite;
  will-change: transform;
}
.leaf-wrap.fading .leaf-card {
  animation: leaf-fade-out 1.5s ease forwards;
}
@keyframes leaf-fade-out {
  0% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}
.leaf-card {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease;
}
.leaf-card img {
  display: block;
  width: 100%;
  height: auto;
  pointer-events: none;
}
.leaf-card.hovered {
  transform: scale(1.15);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}

@keyframes leaf-fall {
  0% {
    transform: translate3d(0, 0, 0) rotate(var(--leaf-rot));
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  25% {
    transform: translate3d(calc(var(--leaf-drift) * 0.3), 25vh, 0) rotate(calc(var(--leaf-rot) + 15deg));
  }
  50% {
    transform: translate3d(calc(var(--leaf-drift) * -0.2), 50vh, 0) rotate(calc(var(--leaf-rot) - 10deg));
  }
  75% {
    transform: translate3d(calc(var(--leaf-drift) * 0.4), 75vh, 0) rotate(calc(var(--leaf-rot) + 20deg));
  }
  100% {
    transform: translate3d(var(--leaf-drift), 105vh, 0) rotate(calc(var(--leaf-rot) + 30deg));
    opacity: 0.6;
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
