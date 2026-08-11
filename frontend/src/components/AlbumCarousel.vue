<!-- 照片:一摞叠放,只露最上面一张,点击翻一张,像茶几上的相册 -->
<template>
  <div class="album-stack">
    <div class="stack-stage" @click="next">
      <transition :name="transitionName" mode="out-in">
        <div
          v-if="currentPhoto"
          :key="currentPhoto.id"
          class="stack-photo"
          :style="{ '--rot': currentRot + 'deg' }"
        >
          <img :src="currentPhoto.url" :alt="currentPhoto.description || ''" draggable="false" />
          <div v-if="currentPhoto.description" class="caption">{{ currentPhoto.description }}</div>
        </div>
        <div v-else :key="'empty'" class="stack-empty">
          <el-empty :description="$t('album.carousel.noPhotos')" />
        </div>
      </transition>

      <!-- 底部露出的"一摞"暗示:几张纸的边缘 -->
      <div v-if="photos.length > 1" class="stack-edges" aria-hidden="true">
        <div class="edge edge-1"></div>
        <div class="edge edge-2"></div>
      </div>
    </div>

    <aside class="photo-info">
      <div v-if="currentPhoto" class="info-content">
        <div class="info-title">{{ $t('album.carousel.noteTitle') }}</div>
        <p class="info-desc">{{ currentPhoto.description || $t('album.carousel.noNote') }}</p>
        <div class="info-meta">
          <span>{{ formatDate(currentPhoto.createdAt) }}</span>
        </div>
      </div>
      <div v-else class="info-content">
        <div class="info-title">{{ $t('album.carousel.noteTitle') }}</div>
        <p class="info-desc info-desc-empty">{{ $t('album.carousel.emptyHint') }}</p>
      </div>
      <div v-if="photos.length > 1" class="info-hint">
        <span class="hint-text">{{ $t('album.carousel.tapToFlip') }}</span>
        <span class="hint-count">{{ index + 1 }} / {{ photos.length }}</span>
      </div>
    </aside>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  interval: { type: Number, default: 6000 },
})

const index = ref(0)
const timer = ref(null)
const transitionName = ref('flip-next')

// 每张照片固定一个轻微旋转(按 id 哈希),像随手放的
const ROT_POOL = [-2.5, 1.8, -1.2, 2.3, -0.8, 1.5, -2, 0.5]
const currentRot = computed(() => {
  const p = props.photos[index.value]
  if (!p) return 0
  return ROT_POOL[p.id % ROT_POOL.length]
})

const currentPhoto = computed(() => props.photos[index.value])

const next = () => {
  if (props.photos.length <= 1) return
  transitionName.value = 'flip-next'
  index.value = (index.value + 1) % props.photos.length
}

const start = () => {
  stop()
  if (props.photos.length > 1) timer.value = setInterval(next, props.interval)
}
const stop = () => {
  if (timer.value) { clearInterval(timer.value); timer.value = null }
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
.album-stack {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 0;
  background: var(--color-card);
  border-radius: 8px;
  box-shadow: var(--shadow);
  overflow: hidden;
  min-height: 400px;
}

/* 舞台:相对定位,照片居中叠放 */
.stack-stage {
  position: relative;
  min-height: 400px;
  background: linear-gradient(180deg, #EFE5D0, #E2D5B8);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  padding: 24px;
}
.stack-empty { width: 100%; }

/* 一张照片:白边相纸,轻微旋转,纸张阴影 */
.stack-photo {
  position: relative;
  width: 280px;
  max-width: 90%;
  background: #FFFCF7;
  padding: 10px 10px 36px;
  border-radius: 3px;
  box-shadow: 0 6px 20px rgba(60, 30, 10, 0.25);
  transform: rotate(var(--rot, 0deg));
  transition: transform 0.4s cubic-bezier(.34,1.56,.64,1);
}
.stack-photo:hover {
  transform: rotate(0deg) scale(1.03);
}
.stack-photo img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 2px;
}
.caption {
  position: absolute;
  bottom: 8px;
  left: 10px;
  right: 10px;
  font-size: 12px;
  color: #5C4332;
  font-style: italic;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 底部露出的纸边:暗示这是一摞 */
.stack-edges {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.edge {
  position: absolute;
  left: 50%;
  width: 280px;
  max-width: 90%;
  height: 30px;
  background: #FFFCF7;
  border-radius: 3px;
  box-shadow: 0 2px 8px rgba(60, 30, 10, 0.15);
}
.edge-1 {
  bottom: 28px;
  transform: translateX(-50%) rotate(2deg);
}
.edge-2 {
  bottom: 20px;
  transform: translateX(-50%) rotate(-1.5deg);
}

.photo-info {
  padding: 24px;
  display: flex;
  flex-direction: column;
  background: var(--color-card);
  border-left: 1px dashed var(--color-border);
}
.info-content { flex: 1; }
.info-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px dashed var(--color-border);
}
.info-desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text);
  white-space: pre-wrap;
  word-break: break-word;
}
.info-desc-empty {
  color: var(--color-text-secondary);
  font-style: italic;
}
.info-meta {
  margin-top: 20px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.info-hint {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px dashed var(--color-border);
  font-size: 12px;
}
.hint-text { color: var(--color-text-secondary); }
.hint-count { color: var(--color-accent); font-weight: 600; }

/* 翻页:下一张从右上滑入,当前向左下滑出 */
.flip-next-enter-active, .flip-next-leave-active {
  transition: all 0.4s cubic-bezier(.34,1.56,.64,1);
}
.flip-next-enter-from {
  opacity: 0;
  transform: translate(40px, -20px) rotate(8deg) scale(0.9);
}
.flip-next-leave-to {
  opacity: 0;
  transform: translate(-40px, 20px) rotate(-8deg) scale(0.9);
}

@media (max-width: 768px) {
  .album-stack {
    grid-template-columns: 1fr;
    min-height: 360px;
  }
  .stack-stage { min-height: 280px; padding: 16px; }
  .stack-photo { width: 220px; }
  .edge { width: 220px; }
  .photo-info { padding: 16px; border-left: none; border-top: 1px dashed var(--color-border); }
}
</style>
