<!-- 首页封面统计条:成员数 / 今日动态 / 最近纪念日倒计时 -->
<template>
  <div class="stats-bar">
    <div class="stat-item">
      <span class="stat-icon">👨‍👩‍👧‍👦</span>
      <span class="stat-value">{{ memberCount }}</span>
      <span class="stat-label">位家人</span>
    </div>
    <div class="stat-divider"></div>
    <div class="stat-item">
      <span class="stat-icon">📝</span>
      <span class="stat-value">{{ todayCount }}</span>
      <span class="stat-label">今日动态</span>
    </div>
    <div class="stat-divider"></div>
    <div class="stat-item" v-if="nearestEvent" :title="nearestEvent.tooltip">
      <span class="stat-icon">🎉</span>
      <span class="stat-value">{{ nearestEvent.label }}</span>
      <span class="stat-label">{{ nearestEvent.daysLabel }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  memberCount: { type: Number, default: 0 },
  feedCount: { type: Number, default: 0 },
  events: { type: Array, default: () => [] },
})

const todayCount = computed(() => props.feedCount)

// 取距今天数最少(最近)的纪念日,并按农历/今天等场景适配文案
const nearestEvent = computed(() => {
  if (!props.events || !props.events.length) return null
  const e = [...props.events].sort((a, b) => a.days - b.days)[0]
  return {
    ...e,
    label: e.calendar === 'lunar' ? `${e.label} · 农历` : e.label,
    daysLabel: e.days === 0 ? '今天' : `还有 ${e.days} 天`,
    tooltip: `${e.calendar === 'lunar' ? '农历' : '阳历'} ${e.date}`,
  }
})
</script>

<style scoped>
.stats-bar {
  display: inline-flex;
  align-items: center;
  gap: 20px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  padding: 10px 20px;
  border-radius: 24px;
  color: #fff;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}
.stat-icon { font-size: 18px; }
.stat-value { font-weight: 600; }
.stat-label { opacity: 0.85; }
.stat-divider {
  width: 1px;
  height: 14px;
  background: rgba(255, 255, 255, 0.3);
}
@media (max-width: 768px) {
  .stats-bar { gap: 12px; padding: 8px 14px; }
  .stat-item { font-size: 12px; gap: 4px; }
  .stat-icon { font-size: 15px; }
}
</style>
