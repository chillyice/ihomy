<!-- 到期日历:未来 90 天内到期的持仓横排,按到期日升序;只读展示,点卡片由产品卡负责 -->
<template>
  <div class="card fn-cal">
    <div class="section-label">{{ $t('book.finance.calendar') }}</div>
    <div v-if="!items.length" class="book-empty">{{ $t('book.finance.calendarEmpty') }}</div>
    <div v-else ref="scrollEl" class="fn-cal-strip">
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
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatYuan } from '@/utils/money'
import { bindHorizontalWheel } from '@/utils/horizontalWheel'
import { upcomingMaturities } from '@/utils/finance'
import { useDayTick } from '@/composables/useDayTick'

const props = defineProps({
  list: { type: Array, default: () => [] },
})

const { t } = useI18n()

// 90 天窗口与「剩余天数」都以「今天」为基准:用跨天 tick 而不是 computed 里的 new Date(),
// 否则页面长开跨零点后窗口不会重算(computed 只追踪响应式依赖)
const { now } = useDayTick()
const items = computed(() => upcomingMaturities(props.list, now.value, 90))

// 7 天内到期高亮;已过期(理论上窗口内不含负数)仍按警示色兜底
const itemCls = (it) => ({ 'is-soon': !it.overdue && it.daysLeft <= 7, 'is-overdue': it.overdue })

const daysText = (it) => {
  if (it.daysLeft === 0) return t('book.finance.dueToday')
  if (it.daysLeft < 0) return t('book.finance.overdue', { n: -it.daysLeft })
  return t('book.finance.calendarDays', { n: it.daysLeft })
}

// 横排只在有到期项时渲染:ref 出现后再挂滚轮映射
const scrollEl = ref(null)
let unbindWheel = null
watch(scrollEl, (el) => {
  unbindWheel?.()
  unbindWheel = el ? bindHorizontalWheel(el) : null
})
onBeforeUnmount(() => unbindWheel?.())
</script>

<style scoped>
.fn-cal {
  /* 与记账本其余卡片同档「高密度卡」(16px 18px),见 bookPage.css .book-kpi 注释 */
  padding: 16px 18px;
  margin-bottom: 14px;
  min-width: 0;
}
/* 到期横排:隐藏原生滚动条(不占布局盒,行高只由卡片决定),边缘渐隐 + 滚轮映射保留横向入口 */
.fn-cal-strip {
  display: flex;
  gap: 10px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  mask-image: linear-gradient(90deg, transparent 0, #000 12px, #000 calc(100% - 16px), transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 12px, #000 calc(100% - 16px), transparent);
}
.fn-cal-strip::-webkit-scrollbar {
  display: none;
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
  border-color: var(--color-expense);
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
  color: var(--color-expense);
}
</style>
