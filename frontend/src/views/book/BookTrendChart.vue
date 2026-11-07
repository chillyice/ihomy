<!-- 总账趋势图:纯 CSS 双柱(收入绿 / 支出红),容器宽度自适应,悬停显示当期金额;不引图表库 -->
<template>
  <div class="book-trend">
    <!-- 图例 + 悬停读数:悬停列时顶部显示该列期间与两柱金额,避免逐柱 popover 撑爆 DOM -->
    <div class="bt-head">
      <div class="bt-legend">
        <span class="bt-dot is-income"></span>{{ $t('book.ledger.legendIncome') }} <span class="bt-dot is-expense"></span
        >{{ $t('book.ledger.legendExpense') }}
      </div>
      <div class="bt-readout" :class="{ 'is-idle': !hoverBar }">
        <template v-if="hoverBar">
          <span class="bt-readout-label">{{ hoverBar.label }}</span>
          <span class="bt-readout-item is-income">+{{ formatYuan(hoverBar.income) }}</span>
          <span class="bt-readout-item is-expense">-{{ formatYuan(hoverBar.expense) }}</span>
        </template>
      </div>
    </div>

    <div ref="scrollEl" class="bt-scroll">
      <div class="bt-cols">
        <div v-for="(bar, i) in bars" :key="bar.key" class="bt-col" @mouseenter="hover = i" @mouseleave="hover = -1">
          <div class="bt-bars">
            <div class="bt-bar is-income" :style="{ height: bar.incomeH }"></div>
            <div class="bt-bar is-expense" :style="{ height: bar.expenseH }"></div>
          </div>
          <div class="bt-label">{{ bar.label }}</div>
        </div>
      </div>
    </div>

    <!-- 空数据照样画 0 柱(基线),下面补一句空态文案 -->
    <div v-if="empty" class="book-empty">{{ $t('book.ledger.trendEmpty') }}</div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { formatYuan } from '@/utils/money'
import { bindHorizontalWheel } from '@/utils/horizontalWheel'

// rows: [{ key, label, income, expense }],金额单位元
const props = defineProps({
  rows: { type: Array, default: () => [] },
})

const hover = ref(-1)
// 横向滚动容器:列多时滚轮映射成横滑(隐藏原生滚动条,见 utils/horizontalWheel)
const scrollEl = ref(null)
let unbindWheel = null
onMounted(() => {
  unbindWheel = bindHorizontalWheel(scrollEl.value)
})
onBeforeUnmount(() => unbindWheel?.())

const max = computed(() => (props.rows || []).reduce((m, r) => Math.max(m, Number(r.income) || 0, Number(r.expense) || 0), 0))

// 高度按当期最大值归一;0 值给 0(空数据就是一条平的基线),非 0 至少 2px 免得看不见
const heightOf = (v) => {
  const n = Number(v) || 0
  if (n <= 0) return '0'
  if (max.value <= 0) return '0'
  return `max(2px, ${Math.round((n / max.value) * 1000) / 10}%)`
}

const bars = computed(() =>
  (props.rows || []).map((r) => ({
    key: r.key,
    label: r.label,
    income: Number(r.income) || 0,
    expense: Number(r.expense) || 0,
    incomeH: heightOf(r.income),
    expenseH: heightOf(r.expense),
  })),
)

const empty = computed(() => !bars.value.length || max.value <= 0)
const hoverBar = computed(() => (hover.value >= 0 ? bars.value[hover.value] : null))
</script>

<style scoped>
/* 只做本组件的图形排版;颜色沿用记账本页签既有配色(收入绿/支出红),不覆写 EP 组件 */
.book-trend {
  padding-top: 4px;
}
.bt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 22px;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.bt-legend {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}
.bt-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  display: inline-block;
}
.bt-dot.is-income {
  background: var(--color-income);
}
.bt-dot.is-expense {
  background: var(--color-expense);
  margin-left: 6px;
}
.bt-readout {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 22px;
  font-variant-numeric: tabular-nums;
}
.bt-readout.is-idle {
  visibility: hidden;
}
.bt-readout-label {
  color: var(--color-text, #3a2e22);
  font-weight: 600;
}
.bt-readout-item.is-income {
  color: var(--color-income);
}
.bt-readout-item.is-expense {
  color: var(--color-expense);
}

/* 横向可滚动:列多(12 个月)时窄屏也能看全,列宽随容器拉伸;
   隐藏原生滚动条(不占布局盒),靠边缘渐隐 + 滚轮映射提示与操作 */
.bt-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  mask-image: linear-gradient(90deg, transparent 0, #000 12px, #000 calc(100% - 16px), transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent 0, #000 12px, #000 calc(100% - 16px), transparent);
}
.bt-scroll::-webkit-scrollbar {
  display: none;
}
.bt-cols {
  display: flex;
  align-items: flex-end;
  gap: 6px;
  min-width: 100%;
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 0;
}
.bt-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1 1 0;
  min-width: 22px;
}
.bt-bars {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
  width: 100%;
  height: 132px;
}
.bt-bar {
  flex: 1 1 0;
  max-width: 12px;
  border-radius: 3px 3px 0 0;
  transition: height 0.25s ease;
}
.bt-bar.is-income {
  background: var(--color-income);
}
.bt-bar.is-expense {
  background: var(--color-expense);
}
/* 悬停列整列提亮,指出读数对应哪一列 */
.bt-col:hover .bt-bar {
  filter: brightness(1.12);
}
.bt-label {
  font-size: 11px;
  color: var(--color-text-secondary, #999);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .bt-bars {
    height: 104px;
  }
  .bt-head {
    flex-wrap: wrap;
  }
  /* 12 个月在窄屏一行放不下:列宽按最小可读值撑住,超出由 .bt-scroll 横向滚动(不把柱子压成 1px) */
  .bt-col {
    flex: 0 0 auto;
    min-width: 30px;
  }
}
</style>
