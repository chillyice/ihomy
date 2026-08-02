<template>
  <div class="album-carousel">
    <div class="carousel-stage">
      <transition name="fade" mode="out-in">
        <div
          v-if="currentPhoto"
          :key="currentPhoto.id"
          class="stage-image-wrap"
          :style="{ backgroundImage: `url(${currentPhoto.url})` }"
        >
          <img :src="currentPhoto.url" :alt="currentPhoto.description" class="stage-image" />
        </div>
        <div v-else class="stage-empty">
          <el-empty description="暂无公开照片" />
        </div>
      </transition>

      <button v-if="photos.length > 1" class="nav-btn nav-prev" @click="prev">‹</button>
      <button v-if="photos.length > 1" class="nav-btn nav-next" @click="next">›</button>

      <div v-if="photos.length > 1" class="dots">
        <span
          v-for="(p, i) in photos"
          :key="p.id"
          :class="['dot', { active: i === index }]"
          @click="index = i"
        />
      </div>
    </div>

    <aside class="photo-info">
      <div v-if="currentPhoto" class="info-content">
        <div class="info-title">照片备注</div>
        <p class="info-desc">{{ currentPhoto.description || '这张照片没有留下备注' }}</p>
        <div class="info-meta">
          <span>{{ formatDate(currentPhoto.createdAt) }}</span>
        </div>
      </div>
      <div v-else class="info-content">
        <div class="info-title">照片备注</div>
        <p class="info-desc info-desc-empty">公开相册的备注信息会随照片滚动显示在这里</p>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  interval: { type: Number, default: 5000 },
})

const index = ref(0)
const timer = ref(null)

const currentPhoto = computed(() => props.photos[index.value])

const next = () => {
  if (props.photos.length <= 1) return
  index.value = (index.value + 1) % props.photos.length
}
const prev = () => {
  if (props.photos.length <= 1) return
  index.value = (index.value - 1 + props.photos.length) % props.photos.length
}

const start = () => {
  stop()
  if (props.photos.length > 1) {
    timer.value = setInterval(next, props.interval)
  }
}
const stop = () => {
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
}

watch(() => props.photos, () => {
  index.value = 0
  start()
})

onMounted(start)
onBeforeUnmount(stop)

const formatDate = (d) => {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
}
</script>

<style scoped>
.album-carousel {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 24px;
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  overflow: hidden;
  min-height: 420px;
}
.carousel-stage {
  position: relative;
  background: #000;
  overflow: hidden;
}
.stage-image-wrap {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
}
.stage-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.stage-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1F3A5F, #2E74B5);
}
.nav-btn {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  background: rgba(0, 0, 0, 0.4);
  color: #fff;
  border: none;
  width: 40px;
  height: 64px;
  font-size: 28px;
  cursor: pointer;
  transition: background 0.15s;
  z-index: 2;
}
.nav-btn:hover { background: rgba(0, 0, 0, 0.7); }
.nav-prev { left: 0; border-radius: 0 8px 8px 0; }
.nav-next { right: 0; border-radius: 8px 0 0 8px; }
.dots {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 6px;
  z-index: 2;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.dot.active {
  background: #fff;
  transform: scale(1.3);
}

.photo-info {
  padding: 24px;
  display: flex;
  flex-direction: column;
  background: var(--color-card);
}
.info-content { flex: 1; }
.info-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(31, 58, 95, 0.08);
}
.info-desc {
  font-size: 15px;
  line-height: 1.8;
  color: var(--color-text);
  white-space: pre-wrap;
  word-break: break-word;
}
.info-desc-empty {
  color: var(--color-text-secondary);
  font-style: italic;
}
.info-meta {
  margin-top: 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.6s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 768px) {
  .album-carousel {
    grid-template-columns: 1fr;
    min-height: 320px;
  }
  .carousel-stage { min-height: 280px; }
  .photo-info { padding: 16px; }
}
</style>
