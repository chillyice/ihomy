<!-- 本月预算卡:总进度 + 各分类进度条(超支标红)+ 未设预算支出提示;「管理预算」弹窗在此收口 -->
<template>
  <div class="card budget-panel">
    <div class="budget-head">
      <span class="section-label">{{ $t('book.budget.title') }}</span>
      <el-button size="small" text @click="mgrVisible = true">{{ $t('book.budget.manage') }}</el-button>
    </div>

    <div v-if="overCount > 0" class="budget-alert">
      <el-icon><WarningFilled /></el-icon>
      {{ $t('book.budget.overAlert', { n: overCount }) }}
    </div>

    <template v-if="items.length">
      <div class="budget-total">
        <span>{{ $t('book.budget.total') }}</span>
        <span class="budget-total-nums" :class="{ 'is-over': total.over }"> ¥{{ money(total.spent) }} / ¥{{ money(total.budget) }} </span>
      </div>
      <div class="budget-bar">
        <div class="budget-bar-fill" :class="{ 'is-over': total.over }" :style="{ width: width(total.ratio) }"></div>
      </div>

      <div v-for="it in items" :key="it.id" class="budget-item">
        <div class="bi-head">
          <span class="bi-name">{{ it.category }}</span>
          <span class="bi-nums" :class="{ 'is-over': it.over }">¥{{ money(it.spent) }} / ¥{{ money(it.amount) }}</span>
        </div>
        <div class="budget-bar">
          <div class="budget-bar-fill" :class="{ 'is-over': it.over }" :style="{ width: width(it.ratio) }"></div>
        </div>
        <div class="bi-foot" :class="{ 'is-over': it.over }">
          {{ it.over ? $t('book.budget.overBy', { amount: money(it.excess) }) : $t('book.budget.left', { amount: money(it.remaining) }) }}
        </div>
      </div>
    </template>

    <div v-else class="book-empty">{{ $t('book.budget.empty') }}</div>

    <div v-if="Number(unbudgeted) > 0" class="budget-note">
      {{ $t('book.budget.unbudgeted', { amount: money(unbudgeted) }) }}
    </div>

    <!-- 预算增删改:改完通知父页重拉预算与明细对比 -->
    <BudgetManagerDialog v-model:visible="mgrVisible" @saved="emit('saved')" />
  </div>
</template>

<script setup>
// 预算卡:纯展示(数据由记账本页签统一取),进度比与超支额都由后端算好,这里只画条
import { computed, ref } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'

import { formatYuan } from '@/utils/loan'
import BudgetManagerDialog from './BudgetManagerDialog.vue'

const props = defineProps({
  // 后端 /book/budget 的返回:{ items, total, overCount, unbudgeted }
  data: { type: Object, default: null },
})
const emit = defineEmits(['saved'])

const mgrVisible = ref(false)

const items = computed(() => props.data?.items || [])
const total = computed(() => props.data?.total || { budget: 0, spent: 0, ratio: 0, over: false })
const overCount = computed(() => Number(props.data?.overCount || 0))
const unbudgeted = computed(() => props.data?.unbudgeted || 0)

const money = (v) => formatYuan(v)
// 进度条最多画满,超支不再向外溢出(比例已 >1 时条是满的 + 红色)
const width = (ratio) => `${Math.min(100, Math.max(0, (Number(ratio) || 0) * 100))}%`
</script>

<style scoped>
.budget-panel {
  padding: 16px 18px;
}
.budget-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.budget-alert {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
}
.budget-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 13px;
}
.budget-total-nums {
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  color: var(--color-text-secondary, #999);
}
.budget-total-nums.is-over {
  color: #f56c6c;
  font-weight: 600;
}
.budget-bar {
  height: 4px;
  margin: 6px 0 2px;
  border-radius: 2px;
  background: var(--color-card-2);
  overflow: hidden;
}
.budget-bar-fill {
  height: 100%;
  border-radius: 2px;
  background: var(--color-brand);
  transition: width 0.3s;
}
.budget-bar-fill.is-over {
  background: #f56c6c;
}
.budget-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--color-border);
}
.bi-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}
.bi-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bi-nums {
  color: var(--color-text-secondary, #999);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.bi-nums.is-over {
  color: #f56c6c;
  font-weight: 600;
}
.bi-foot {
  margin-top: 2px;
  font-size: 11px;
  color: var(--color-text-tertiary, #b5a98c);
  font-variant-numeric: tabular-nums;
}
.bi-foot.is-over {
  color: #f56c6c;
}
.budget-note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  line-height: 1.6;
}
</style>
