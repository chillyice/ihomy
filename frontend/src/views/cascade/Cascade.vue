<template>
  <div class="page cascade-page">
    <h2 class="page-title">{{ $t('cascade.title') }}</h2>
    <p class="page-sub">{{ $t('cascade.hint') }}</p>

    <div ref="stage" class="cascade-stage">
      <div
        v-for="c in cards"
        :key="c.key"
        class="leaf-wrap"
        :style="cardStyle(c)"
      >
        <div
          class="leaf-card"
          :class="{ hovered: c.hovered }"
          @mouseenter="pauseCard(c)"
          @mouseleave="resumeCard(c)"
          @click.stop="openViewer(c)"
        >
          <img :src="c.photo.url" draggable="false" :alt="c.photo.description || ''" />
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

    <el-empty v-if="!loading && !photos.length" :description="$t('cascade.empty')" />

    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIdx"
      @close="closeViewer"
      hide-on-click-modal
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { photoApi } from '@/api'
import { ElImageViewer } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const stage = ref(null)
const photos = ref([])
const cards = ref([])
const loading = ref(true)
const viewerVisible = ref(false)
const viewerIdx = ref(0)
const viewerUrls = computed(() => photos.value.map(p => p.url))

let cardSeq = 0
let spawnTimer = null
let rafId = null

const spawnCard = () => {
  if (!photos.value.length) return
  const photo = photos.value[Math.floor(Math.random() * photos.value.length)]
  const card = {
    key: ++cardSeq,
    photo,
    x: 5 + Math.random() * 90,
    delay: 0,
    duration: 15 + Math.random() * 12,
    size: 130 + Math.random() * 90,
    rot: (Math.random() - 0.5) * 30,
    drift: (Math.random() - 0.5) * 120,
    hovered: false,
  }
  cards.value.push(card)
  if (cards.value.length > 50) cards.value.shift()
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
const closeViewer = () => { viewerVisible.value = false }

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
    for (let i = 0; i < 8; i++) {
      setTimeout(() => spawnCard(), i * 800)
    }
    spawnTimer = setInterval(spawnCard, 2000)
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
.cascade-page { min-height: 100vh; }
.page-title { color: var(--color-text); margin-bottom: 4px; font-size: 18px; }
.page-sub { color: var(--color-text-secondary); font-size: 13px; margin-bottom: 16px; }

.cascade-stage {
  position: relative;
  min-height: 80vh;
  overflow: hidden;
  border-radius: var(--radius);
  background: linear-gradient(180deg, rgba(255,255,255,0.05), rgba(0,0,0,0.02));
}

.leaf-wrap {
  position: absolute;
  top: -200px;
  animation: leaf-fall linear infinite;
  will-change: transform;
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
  aspect-ratio: 3 / 4;
  object-fit: cover;
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
</style>
