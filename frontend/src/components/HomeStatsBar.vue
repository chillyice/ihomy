<!-- 首页封面统计条:成员数 / 今日动态 -->
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
      <span class="stat-value">{{ feedCount }}</span>
      <span class="stat-label">今日动态</span>
    </div>
  </div>
</template>

<script setup>
// 统计条纯展示组件:成员数与今日动态数(纪念日倒计时改由首页 stats 展示)
defineProps({
  memberCount: { type: Number, default: 0 },
  feedCount: { type: Number, default: 0 },
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
