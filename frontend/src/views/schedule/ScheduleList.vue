<!-- 清单模式:与月历共用同一份事件数据,按日期分组 + 类型筛选;focus 计划时滚动高亮。 -->
<template>
  <section v-loading="loading" class="list-wrap">
    <div v-if="!groups.length" class="empty-card card">{{ $t('schedule.listEmpty') }}</div>
    <div v-for="g in groups" :key="g.date" class="list-group">
      <div class="group-head">
        <span class="group-date">{{ dateTitle(g.date) }}</span>
        <span class="group-count">{{ g.items.length }}</span>
      </div>
      <div class="row-list">
        <ScheduleRow
          v-for="it in g.items"
          :id="'schedule-row-' + it.kind + '-' + it.id"
          :key="it.kind + '-' + it.id"
          :class="{ focused: isFocused(it) }"
          :item="it"
          :record="recordOf(it)"
          :can-edit="canEdit"
          @open="$emit('open', $event)"
          @toggle="$emit('toggle', $event)"
          @toggle-task="$emit('toggle-task', $event)"
          @edit="$emit('edit', $event)"
          @delete="$emit('delete', $event)"
        />
      </div>
    </div>
  </section>
</template>

<script setup>
// 分组与排序来自 utils/scheduleList(纯函数,已有单测);本组件只负责渲染与高亮
import { useI18n } from 'vue-i18n'
import { weekdayKey } from '@/utils/scheduleCalendar'
import ScheduleRow from './ScheduleRow.vue'

const props = defineProps({
  /** [{date, items}] */
  groups: { type: Array, default: () => [] },
  /** focus=<planId>:滚动到并高亮该计划 */
  focus: { type: [String, Number], default: null },
  recordOf: { type: Function, required: true },
  canEdit: { type: Boolean, default: true },
  loading: { type: Boolean, default: false },
})
defineEmits(['open', 'edit', 'delete', 'toggle', 'toggle-task'])
const { t } = useI18n()

const dateTitle = (date) => {
  const [, month, day] = date.split('-').map(Number)
  return t('schedule.dayTitle', { month, day, weekday: t('schedule.weekdayLong.' + weekdayKey(date)) })
}

const isFocused = (it) => props.focus != null && it.kind === 'PLAN' && String(it.id) === String(props.focus)
</script>

<style scoped>
.list-wrap {
  min-height: 120px;
}
.empty-card {
  padding: 28px;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.list-group {
  margin-bottom: 14px;
}
.group-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 0 0 8px;
  padding-left: 10px;
  border-left: 3px solid var(--color-accent);
}
.group-date {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.group-count {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.row-list {
  display: grid;
  gap: 8px;
}
.row-list :deep(.focused) {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}
</style>
