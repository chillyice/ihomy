<!-- 记账 · 近半年趋势:近 6 个月收入/支出双柱(紧凑档复用总账趋势图),下方一行半年合计 -->
<template>
  <h3 class="gc-card-h3">
    {{ $t('warm.widget.finance') }}<span class="gc-muted">{{ $t('warm.style.finTrend') }}</span>
  </h3>
  <div class="wft">
    <BookTrendChart :rows="rows" compact />
    <div class="wft-sum">
      <span class="wft-item in">+{{ formatYuan(income) }}</span>
      <span class="wft-item out">-{{ formatYuan(expense) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import BookTrendChart from '@/views/book/BookTrendChart.vue'
import { formatYuan } from '@/utils/money'

const props = defineProps({
  rows: { type: Array, default: () => [] },
})

const income = computed(() => (props.rows || []).reduce((sum, r) => sum + (Number(r.income) || 0), 0))
const expense = computed(() => (props.rows || []).reduce((sum, r) => sum + (Number(r.expense) || 0), 0))
</script>

<style scoped>
.wft {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 4px;
}
.wft-sum {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 12px;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.wft-item.in {
  color: var(--color-income);
}
.wft-item.out {
  color: var(--color-expense);
}
</style>
