<!-- 纪念日 · 横滑卡片:每个纪念日一张卡(大天数 + 名称 + 日期 + 进度),横向滑动/箭头,点卡进纪念日页 -->
<template>
  <h3 class="gc-card-h3">
    {{ $t('warm.widget.anni') }}<button class="gc-more" @click.stop="!editMode && go()">{{ $t('warm.anniArrow') }}</button>
  </h3>
  <div v-if="list.length" ref="scrollEl" class="wac" :class="{ 'is-editing': editMode }">
    <div v-for="(a, i) in list" :key="a.id || a.label + '-' + i" class="wac-card" v-a11y-click @click="!editMode && go()">
      <div class="wac-days">{{ a.days }}</div>
      <div class="wac-unit">{{ $t('warm.daysShort') }}</div>
      <div class="wac-label">{{ a.label }}</div>
      <div class="wac-date">{{ a.date }}</div>
      <div class="wac-meter"><i :style="{ transform: `scaleX(${Math.min(100, Math.max(0, Number(a.days) || 0)) / 100})` }"></i></div>
    </div>
  </div>
  <div v-else class="gc-empty">{{ $t('warm.noAnni') }}</div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { bindHorizontalWheel } from '@/utils/horizontalWheel'

const props = defineProps({
  list: { type: Array, default: () => [] },
  editMode: { type: Boolean, default: false },
})

const router = useRouter()
const go = () => router.push('/plan?tab=anniversary')

const MAX_CARDS = 20
const list = computed(() => (props.list || []).slice(0, MAX_CARDS))

// 横滑容器:隐藏原生滚动条 + 滚轮映射横滑(触屏走浏览器原生滑动)
const scrollEl = ref(null)
watch(scrollEl, (el, _, onCleanup) => onCleanup(bindHorizontalWheel(el)))
</script>

<style scoped>
.wac {
  display: flex;
  flex: 1;
  gap: 10px;
  min-height: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x proximity;
  scrollbar-width: none;
  mask-image: linear-gradient(90deg, transparent 0, #000 10px, #000 calc(100% - 14px), transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 10px, #000 calc(100% - 14px), transparent);
}
.wac::-webkit-scrollbar {
  display: none;
}
.wac-card {
  flex: 0 0 auto;
  width: 150px;
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 10px 12px 11px;
  border-radius: 12px;
  background: var(--color-card-2);
  border: 1px solid var(--color-border);
  scroll-snap-align: start;
  cursor: pointer;
}
.wac-days {
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
  color: rgb(var(--chip, var(--color-brand-rgb)));
  font-variant-numeric: tabular-nums;
}
.wac-unit {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.wac-label {
  margin-top: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wac-date {
  font-size: 11px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.wac-meter {
  margin-top: 6px;
  height: 4px;
  border-radius: 999px;
  background: var(--color-line);
  overflow: hidden;
}
.wac-meter i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: rgb(var(--chip, var(--color-brand-rgb)));
  transform-origin: left center;
}
.is-editing .wac-card {
  cursor: grab;
}
@media (prefers-reduced-motion: no-preference) {
  .wac:not(.is-editing) .wac-meter i {
    animation: meter-fill 480ms var(--ease-soft) backwards;
    transition: transform 320ms var(--ease-soft);
  }
  @media (hover: hover) {
    .wac:not(.is-editing) .wac-card {
      transition:
        transform 0.2s ease,
        box-shadow 0.2s ease;
    }
    .wac:not(.is-editing) .wac-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 10px 22px rgba(0, 0, 0, 0.12);
    }
  }
}
@keyframes meter-fill {
  from {
    transform: scaleX(0);
  }
}
</style>
