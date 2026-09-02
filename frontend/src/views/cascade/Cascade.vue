<template>
  <div class="page cascade-page">
    <div ref="stage" class="cascade-stage">
      <div
        v-for="c in cards"
        :key="c.key"
        class="leaf-wrap"
        :class="{ fading: c.fading }"
        :style="cardStyle(c)"
      >
        <div
          class="leaf-card"
          :class="{ hovered: c.hovered }"
          @mouseenter="pauseCard(c)"
          @mouseleave="resumeCard(c)"
          @touchstart="pauseCard(c)"
          @touchend="resumeCard(c)"
          @click.stop="openViewer(c)"
        >
          <img :src="c.photo.url && c.photo.url.includes('?') ? c.photo.url + '&thumb=1' : c.photo.url" draggable="false" :alt="c.photo.description || ''" />
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

    <PhotoViewer
      v-model:visible="viewerVisible"
      :photos="photos"
      :initial-index="viewerIdx"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { photoApi } from '@/api'
import PhotoViewer from '@/components/PhotoViewer.vue'

const router = useRouter()
const userStore = useUserStore()

const stage = ref(null)
const photos = ref([])
const cards = ref([])
const loading = ref(true)
const viewerVisible = ref(false)
const viewerIdx = ref(0)

let cardSeq = 0
let spawnTimer = null
let rafId = null

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
    delay: 0,
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
    setTimeout(() => {
      cards.value = cards.value.filter(c => c !== old)
    }, 1500)
  }
}

const pauseCard = (c) => { c.hovered = true }
const resumeCard = (c) => { c.hovered = false }

const cardStyle = (c) => ({
  left: c.x + '%',
  width: c.size + 'px',
  animationDuration: c.duration + 's',
  '--leaf-rot': c.rot + 'deg',
  '--leaf-drift': c.drift + 'px',
  animationPlayState: c.hovered ? 'paused' : 'running',
})

const openViewer = (c) => {
  const idx = photos.value.findIndex(p => p.id === c.photo.id)
  viewerIdx.value = Math.max(0, idx)
  viewerVisible.value = true
}

const formatDate = (d) => {
  if (!d) return ''
  const dt = new Date(String(d).length === 10 ? d + 'T00:00:00' : d)
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`
}

const load = async () => {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  try {
    photos.value = await photoApi.cascade() || []
    for (let i = 0; i < 5; i++) {
      setTimeout(() => spawnCard(), i * 1000)
    }
    spawnTimer = setInterval(spawnCard, 3000)
  } catch (e) {
    // 忽略
  } finally {
    loading.value = false
  }
}

onMounted(() => { load() })
onBeforeUnmount(() => {
  if (spawnTimer) clearInterval(spawnTimer)
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<style scoped>
.cascade-page { min-height: 100vh; padding: 0; }

.cascade-stage {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
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
  0% { opacity: 1; }
  100% { opacity: 0; }
}
.leaf-card {
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
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
.leaf-wrap.hovered-paused {
  animation-play-state: paused;
}

@keyframes leaf-fall {
  0% {
    transform: translate3d(0, 0, 0) rotate(var(--leaf-rot));
    opacity: 0;
  }
  10% { opacity: 1; }
  25% { transform: translate3d(calc(var(--leaf-drift) * 0.3), 25vh, 0) rotate(calc(var(--leaf-rot) + 15deg)); }
  50% { transform: translate3d(calc(var(--leaf-drift) * -0.2), 50vh, 0) rotate(calc(var(--leaf-rot) - 10deg)); }
  75% { transform: translate3d(calc(var(--leaf-drift) * 0.4), 75vh, 0) rotate(calc(var(--leaf-rot) + 20deg)); }
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
.info-desc { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.info-meta { display: flex; gap: 8px; opacity: 0.85; flex-wrap: nowrap; overflow: hidden; white-space: nowrap; }

.cascade-empty {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
}

@media (max-width: 768px) {
  .leaf-card { border-radius: 6px; }
  .photo-info { font-size: 10px; }
}
</style>
