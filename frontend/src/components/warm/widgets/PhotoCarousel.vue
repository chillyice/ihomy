<!-- 相册 · 横向自动切换:单张全宽幻灯片,4s 自动横移、hover 暂停、左右箭头/圆点,点卡进相册 -->
<template>
  <h3 class="gc-card-h3">
    {{ $t('warm.widget.photos') }}<button class="gc-more" @click.stop="!editMode && go('/album')">{{ $t('warm.albumArrow') }}</button>
  </h3>
  <div v-if="slides.length" class="pcs" :class="{ 'is-editing': editMode }" @mouseenter="pause(true)" @mouseleave="pause(false)">
    <div class="pcs-track" :style="{ transform: `translate3d(-${index * 100}%, 0, 0)` }">
      <div v-for="(p, i) in slides" :key="p.id || i" class="pcs-slide" @click="!editMode && go('/album')">
        <img :src="p.url" :alt="p.description || ''" loading="lazy" />
      </div>
    </div>

    <div class="pcs-meta">
      <span class="pcs-cap">{{ caption }}</span>
      <span class="pcs-count">{{ index + 1 }} / {{ slides.length }}</span>
    </div>

    <template v-if="slides.length > 1 && !editMode">
      <button class="pcs-nav prev" :title="$t('warm.prevPhoto')" @click.stop="prev">
        <svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7" /></svg>
      </button>
      <button class="pcs-nav next" :title="$t('warm.nextPhoto')" @click.stop="next">
        <svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7" /></svg>
      </button>
    </template>

    <div v-if="slides.length > 1 && slides.length <= 12" class="pcs-dots">
      <button
        v-for="(p, i) in slides"
        :key="`dot-${p.id || i}`"
        class="pcs-dot"
        :class="{ on: i === index }"
        :aria-label="String(i + 1)"
        @click.stop="go2(i)"
      ></button>
    </div>
  </div>
  <div v-else class="gc-empty gc-empty-link" v-a11y-click @click="!editMode && go('/album')">
    {{ $t('warm.addPhoto') }}
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSlideShow } from '@/composables/useSlideShow'
import { formatDate } from '@/utils/datetime'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  editMode: { type: Boolean, default: false },
})

const router = useRouter()
const { t } = useI18n()
const go = (path) => router.push(path)

// 首页只展示最近一批:照片再多也不在首屏铺几百个 img(lazy 也顶不住布局),其余去相册页看
const MAX_SLIDES = 30
const slides = computed(() => (props.photos || []).slice(0, MAX_SLIDES))
const reducedMotion =
  typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)').matches : false
const {
  index,
  next,
  prev,
  go: go2,
  pause,
} = useSlideShow({
  count: () => slides.value.length,
  active: () => !props.editMode && !reducedMotion,
  interval: 4000,
})

const current = computed(() => slides.value[index.value] || null)
const caption = computed(() => {
  const p = current.value
  if (!p) return ''
  return p.description || formatDate(p.takenAt || p.createdAt) || t('warm.familyPhoto')
})
</script>

<style scoped>
.pcs {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 12px;
  background: var(--color-line);
  cursor: pointer;
}
.pcs-track {
  display: flex;
  height: 100%;
  transition: transform 0.55s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform;
}
.pcs-slide {
  flex: 0 0 100%;
  height: 100%;
}
.pcs-slide img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
/* 读数条常驻在底部:横向切换时说明与序号不闪烁 */
.pcs-meta {
  position: absolute;
  inset: auto 0 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 28px 14px 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.62));
  pointer-events: none;
}
.pcs-cap {
  min-width: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pcs-count {
  flex-shrink: 0;
  font-size: 11px;
  color: #fff;
  opacity: 0.85;
  font-variant-numeric: tabular-nums;
}
.pcs-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(6px);
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}
.pcs-nav:hover {
  background: rgba(0, 0, 0, 0.6);
  transform: translateY(-50%) scale(1.06);
}
.pcs-nav.prev {
  left: 8px;
}
.pcs-nav.next {
  right: 8px;
}
.pcs-nav svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.pcs-dots {
  position: absolute;
  left: 50%;
  bottom: 38px;
  transform: translateX(-50%);
  display: flex;
  gap: 5px;
  z-index: 2;
}
.pcs-dot {
  width: 6px;
  height: 6px;
  padding: 0;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.45);
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}
.pcs-dot.on {
  background: #fff;
  transform: scale(1.25);
}
/* 编辑态不响应自动切换,也不显示导航,拖拽/缩放优先 */
.is-editing .pcs-dots,
.is-editing .pcs-nav {
  display: none;
}
</style>
