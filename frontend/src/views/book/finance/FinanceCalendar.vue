<!-- 到期日历:未来 90 天内到期的持仓横排,按到期日升序;只读展示,点卡片由产品卡负责 -->
<template>
  <div class="card fn-cal">
    <div class="section-label">{{ $t('book.finance.calendar') }}</div>
    <div v-if="!items.length" class="book-empty">{{ $t('book.finance.calendarEmpty') }}</div>
    <div v-else class="fn-cal-strip">
      <div v-for="it in items" :key="it.id" class="fn-cal-item" :class="itemCls(it)">
        <div class="fn-cal-date">{{ it.endDate }}</div>
        <div class="fn-cal-name" :title="it.name">{{ it.name }}</div>
        <div class="fn-cal-foot">
          <span class="fn-cal-amount">¥{{ formatYuan(it.principal) }}</span>
          <span class="fn-cal-days">{{ daysText(it) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 只做 90 天窗口的横排展示;窗口与筛选口径都在 utils/finance.js(upcomingMaturities)
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatYuan } from '@/utils/money'
import { upcomingMaturities } from '@/utils/finance'

const props = defineProps({
  list: { type: Array, default: () => [] },
})

const { t } = useI18n()

const items = computed(() => upcomingMaturities(props.list, new Date(), 90))

// 7 天内到期高亮;已过期(理论上窗口内不含负数)仍按警示色兜底
const itemCls = (it) => ({ 'is-soon': !it.overdue && it.daysLeft <= 7, 'is-overdue': it.overdue })

const daysText = (it) => {
  if (it.daysLeft === 0) return t('book.finance.dueToday')
  if (it.daysLeft < 0) return t('book.finance.overdue', { n: -it.daysLeft })
  return t('book.finance.calendarDays', { n: it.daysLeft })
}
</script>

<style scoped>
.fn-cal {
  padding: 14px 18px;
  margin-bottom: 14px;
  min-width: 0;
}
.fn-cal-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  scrollbar-width: thin;
  padding-bottom: 4px;
}
.fn-cal-item {
  flex: 0 0 auto;
  width: 168px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card-2);
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.fn-cal-item.is-soon {
  border-color: #e6a23c;
}
.fn-cal-item.is-overdue {
  border-color: #f56c6c;
}
.fn-cal-date {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  font-variant-numeric: tabular-nums;
}
.fn-cal-name {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fn-cal-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.fn-cal-amount {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.fn-cal-days {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.fn-cal-item.is-soon .fn-cal-days {
  color: #e6a23c;
}
.fn-cal-item.is-overdue .fn-cal-days {
  color: #f56c6c;
}
</style>
