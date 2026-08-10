<!-- 照片瀑布页:照片卡片从顶部以 3D 翻转姿态飘落,悬停翻正查看信息,点击进入全屏浏览(键盘/触屏/鼠标翻页) -->
<template>
  <div class="page cascade-page">
    <h2 class="page-title">{{ $t('cascade.title') }}</h2>
    <p class="page-sub">{{ $t('cascade.hint') }}</p>

    <div ref="stage" class="cascade-stage" @click="onStageClick">
      <div
        v-for="c in cards"
        :key="c.key"
        class="photo-card"
        :class="{ hovered: c.hovered }"
        :style="cardStyle(c)"
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
            <span v-if="c.photo.location">{{ c.photo.location }}</span>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && !cards.length" :description="$t('cascade.empty')" />

    <!-- 全屏照片查看:前后翻看随机照片流 -->
    <div v-if="viewer.visible" class="viewer" @click.self="closeViewer">
      <div class="viewer-stage" @touchstart="onTouchStart" @touchend="onTouchEnd">
        <img :src="currentPhoto().url" :alt="(currentPhoto().description) || ''" />
        <button class="viewer-nav prev" @click.stop="stepViewer(-1)">‹</button>
        <button class="viewer-nav next" @click.stop="stepViewer(1)">›</button>
        <div class="viewer-info">
          <div v-if="currentPhoto().description" class="info-desc">{{ currentPhoto().description }}</div>
          <div class="info-meta">
            <span v-if="currentPhoto().uploaderName">{{ currentPhoto().uploaderName }}</span>
            <span v-if="currentPhoto().takenAt">{{ formatDate(currentPhoto().takenAt) }}</span>
            <span v-if="currentPhoto().location">{{ currentPhoto().location }}</span>
            <span>{{ viewer.index + 1 }} / {{ viewer.list.length }}</span>
          </div>
        </div>
        <button class="viewer-close" @click.stop="closeViewer">×</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { photoApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const stage = ref(null)
const photos = ref([])
const cards = ref([])
const loading = ref(true)
const viewer = ref(null)

let cardSeq = 0
let spawnTimer = null
let touchX = 0

// 生成一张卡片:固定随机水平位置/角度/延迟/速度,3D 翻转+下落由 keyframes 驱动
const spawnCard = () => {
  if (!stage.value || !photos.value.length) return
  const photo = photos.value[Math.floor(Math.random() * photos.value.length)]
  const card = {
    key: ++cardSeq,
    photo,
    left: 2 + Math.random() * 94,
    deg: (Math.random() - 0.5) * 24,
    delay: -Math.random() * 8,          // 负延迟:图片入场即处于中途,避免空档
    duration: 14 + Math.random() * 12,  // 下落时长 14-26s
    size: 120 + Math.random() * 100,    // 卡片宽度 120-220px
    hovered: false,
  }
  cards.value.push(card)
  if (cards.value.length > 60) cards.value.shift()
}

const cardStyle = (c) => ({
  left: c.left + '%',
  width: c.size + 'px',
  animationDuration: c.duration + 's',
  animationDelay: c.delay + 's',
  '--card-rotate': c.deg + 'deg',
})

// 悬停:暂停动画并翻正
const pauseCard = (c) => { c.hovered = true }
const resumeCard = (c) => { c.hovered = false }

// 打开查看器:以随机顺序浏览全部照片
const openViewer = (c) => {
  const list = [...photos.value].sort(() => Math.random() - 0.5)
  viewer.value = { list, index: Math.max(0, list.indexOf(c.photo)) }
}
const stepViewer = (dir) => {
  if (!viewer.value.list.length) return
  viewer.value.index = (viewer.value.index + dir + viewer.value.list.length) % viewer.value.list.length
}
const closeViewer = () => { viewer.value = null }
const currentPhoto = () => viewer.value?.list[viewer.value.index] || {}

// 键盘方向键翻页 / ESC 关闭
const onKeydown = (e) => {
  if (!viewer.value) return
  if (e.key === 'ArrowLeft') stepViewer(-1)
  else if (e.key === 'ArrowRight') stepViewer(1)
  else if (e.key === 'Escape') closeViewer()
}

// 移动端触摸滑动翻页
const onTouchStart = (e) => { touchX = e.touches[0].clientX }
const onTouchEnd = (e) => {
  const dx = e.changedTouches[0].clientX - touchX
  if (dx > 40) stepViewer(-1)
  else if (dx < -40) stepViewer(1)
}

// 点击空白处(非卡片)无操作——由页面上方返回
const onStageClick = () => { /* 保留:避免误触 */ }

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
    // 先铺 12 张开场,之后每隔 1.2s 补一张
    for (let i = 0; i < 12; i++) spawnCard()
    spawnTimer = setInterval(spawnCard, 1200)
  } catch (e) {
    // 忽略
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  if (spawnTimer) clearInterval(spawnTimer)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.cascade-page { min-height: 100vh; }
.page-title { color: var(--color-text); margin-bottom: 4px; font-size: 18px; }
.page-sub { color: var(--color-text-secondary); font-size: 13px; margin-bottom: 16px; }

.cascade-stage {
  position: relative;
  min-height: 70vh;
  overflow: hidden;
  border-radius: var(--radius);
  background: linear-gradient(180deg, rgba(0,0,0,0.04), rgba(0,0,0,0.02));
}

.photo-card {
  position: absolute;
  top: -140px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.18);
  cursor: pointer;
  transform: translateZ(0);
  animation: card-fall linear infinite;
}
.photo-card img {
  display: block;
  width: 100%;
  aspect-ratio: 3 / 4;
  object-fit: cover;
}
/* 飘落:旋转+翻面+下落,悬停时暂停 */
.photo-card:not(.hovered) {
  animation-name: card-fall;
  animation-play-state: running;
}
.photo-card.hovered {
  animation-play-state: paused;
  border: 2px solid #fff;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
}
@keyframes card-fall {
  0%   { transform: translateY(-160px) rotateX(0deg) rotateY(0deg) rotate(var(--card-rotate)); opacity: 0; }
  10%  { opacity: 1; }
  60%  { transform: translateY(50vh) rotateX(180deg) rotateY(180deg) rotate(var(--card-rotate)); }
  100% { transform: translateY(105vh) rotateX(360deg) rotateY(360deg) rotate(var(--card-rotate)); opacity: 0.9; }
}

.photo-info {
  position: absolute;
  right: 6px;
  bottom: 6px;
  max-width: 90%;
  background: rgba(0, 0, 0, 0.72);
  color: #fff;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.4;
}
.info-desc { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.info-meta {
  display: flex;
  gap: 8px;
  opacity: 0.85;
  flex-wrap: nowrap;
  overflow: hidden;
  white-space: nowrap;
}

.viewer {
  position: fixed;
  inset: 0;
  z-index: 300;
  background: rgba(0, 0, 0, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
}
.viewer-stage { position: relative; max-width: 92vw; max-height: 92vh; }
.viewer-stage img {
  max-width: 92vw;
  max-height: 84vh;
  object-fit: contain;
  border-radius: 8px;
  user-select: none;
  -webkit-user-drag: none;
}
.viewer-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 48px;
  height: 64px;
  border: none;
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 28px;
  cursor: pointer;
  border-radius: 8px;
}
.viewer-nav:hover { background: rgba(255, 255, 255, 0.25); }
.viewer-nav.prev { left: 8px; }
.viewer-nav.next { right: 8px; }
.viewer-close {
  position: absolute;
  top: -44px;
  right: 0;
  width: 32px;
  height: 32px;
  border: none;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  border-radius: 50%;
}
.viewer-info {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
@media (max-width: 768px) {
  .viewer-nav { width: 36px; height: 48px; font-size: 22px; }
}
</style>