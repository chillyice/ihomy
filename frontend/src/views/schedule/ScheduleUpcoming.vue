<!-- 近期日程提醒:未来 7 天、最多 20 条,横向可换行;点一条跳到对应日期。 -->
<template>
  <section v-if="items.length" class="upcoming card">
    <span class="up-label">
      <el-icon><Bell /></el-icon>
      {{ $t('schedule.upcoming') }}
    </span>
    <button v-for="it in items" :key="it.kind + '-' + it.id + '-' + it.date" type="button" class="up-item" @click="$emit('pick', it)">
      <span class="up-date">{{ dateLabel(it.date) }}</span>
      <span class="up-time">{{ it.time || $t('schedule.allDay') }}</span>
      <span class="up-title">{{ it.title }}</span>
    </button>
  </section>
</template>

<script setup>
// 纯展示:数据来自 /schedule/upcoming(登录后才有内容,游客外壳不渲染本组件)
import { useI18n } from 'vue-i18n'
import { Bell } from '@element-plus/icons-vue'
import { todayString, addDays } from '@/utils/scheduleCalendar'

defineProps({
  items: { type: Array, default: () => [] },
})
defineEmits(['pick'])
const { t } = useI18n()

const dateLabel = (date) => {
  const today = todayString()
  if (date === today) return t('schedule.upcomingToday')
  if (date === addDays(today, 1)) return t('schedule.upcomingTomorrow')
  return date.slice(5)
}
</script>

<style scoped>
.upcoming {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
  padding: 10px 16px;
  margin-bottom: 12px;
  font-size: 13px;
}
.up-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.up-label .el-icon {
  color: var(--color-brand);
}
.up-item {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  max-width: 100%;
  padding: 3px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: transparent;
  color: var(--color-text);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s ease;
}
.up-item:hover {
  background: rgba(var(--color-brand-rgb), 0.12);
}
.up-date {
  color: var(--color-brand-hover);
  font-variant-numeric: tabular-nums;
}
.up-time {
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.up-title {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
