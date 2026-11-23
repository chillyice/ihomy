<!-- 家人动态 · 横向卡片流:每条动态一张小卡,横向滑动(滚轮/触控板映射横滑),点卡进对应内容 -->
<template>
  <h3 class="gc-card-h3">{{ $t('warm.widget.feed') }}</h3>
  <div v-if="list.length" ref="scrollEl" class="wfc" :class="{ 'is-editing': editMode }">
    <div
      v-for="(f, i) in list"
      :key="f.type + '-' + (f.id ?? i)"
      class="wfc-card"
      :style="{ '--chip': `var(--blob-${feedChip(f.type)})` }"
      v-a11y-click
      @click="!editMode && open(f)"
    >
      <div class="wfc-cover" :class="{ 'has-img': cover(f) }">
        <img v-if="cover(f)" :src="cover(f)" :alt="feedSummary(t, f)" loading="lazy" />
        <span v-else class="wfc-ic">{{ feedIcon(f.type) }}</span>
        <span class="wfc-type">{{ feedTypeLabel(t, f.type) }}</span>
      </div>
      <div class="wfc-body">
        <div class="wfc-title">{{ feedSummary(t, f) }}</div>
        <div class="wfc-author">{{ f.authorName || $t('feed.authorFallback') }}</div>
      </div>
    </div>
  </div>
  <div v-else class="gc-empty">{{ $t('warm.noFeed') }}</div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { feedCover, feedChip, feedIcon, feedPath, feedSummary, feedTypeLabel } from '@/utils/feed'
import { bindHorizontalWheel } from '@/utils/horizontalWheel'

const props = defineProps({
  feeds: { type: Array, default: () => [] },
  editMode: { type: Boolean, default: false },
})

const router = useRouter()
const { t } = useI18n()

// 横滑卡只放首屏这一批;再多的动态去动态页/各内容页看
const MAX_CARDS = 12
const list = computed(() => (props.feeds || []).slice(0, MAX_CARDS))
const cover = (f) => feedCover(f)
const open = (f) => {
  const path = feedPath(f)
  if (path) router.push(path)
}

// 隐藏原生滚动条 + 滚轮映射横滑(见 utils/horizontalWheel,与记账本/麻将牌桌同款)
const scrollEl = ref(null)
let unbindWheel = null
onMounted(() => {
  unbindWheel = bindHorizontalWheel(scrollEl.value)
})
onBeforeUnmount(() => unbindWheel?.())
</script>

<style scoped>
.wfc {
  display: flex;
  flex: 1;
  gap: 10px;
  min-height: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x proximity;
  scrollbar-width: none;
  padding-bottom: 2px;
  /* 边缘渐隐:滚动条不占布局盒,靠渐隐提示还能往两边滑 */
  mask-image: linear-gradient(90deg, transparent 0, #000 10px, #000 calc(100% - 14px), transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 10px, #000 calc(100% - 14px), transparent);
}
.wfc::-webkit-scrollbar {
  display: none;
}
.wfc-card {
  flex: 0 0 auto;
  width: 168px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  background: var(--color-card-2);
  border: 1px solid var(--color-border);
  scroll-snap-align: start;
  cursor: pointer;
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease;
}
.wfc-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.12);
}
.wfc-cover {
  position: relative;
  height: 78px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: color-mix(in srgb, var(--chip) 22%, var(--color-card-2));
}
.wfc-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.wfc-cover.has-img::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(transparent 55%, rgba(0, 0, 0, 0.35));
  pointer-events: none;
}
.wfc-ic {
  font-size: 26px;
  filter: saturate(0.9);
}
.wfc-type {
  position: absolute;
  left: 8px;
  top: 8px;
  z-index: 1;
  font-size: 10.5px;
  font-weight: 600;
  color: #fff;
  padding: 2px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--chip) 78%, #000);
}
.wfc-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px 9px;
  min-height: 0;
}
.wfc-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.wfc-author {
  font-size: 11px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.is-editing .wfc-card {
  cursor: grab;
}
</style>
