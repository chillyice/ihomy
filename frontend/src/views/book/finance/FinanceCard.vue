<!-- 理财产品卡:类型/机构、本金与年化、持有进度、预期与已计提、到期与剩余天数、资金来源与入账标记 -->
<template>
  <div class="fc-card card" :class="{ 'is-redeemed': isRedeemed }">
    <div class="fc-head">
      <div class="fc-title">
        <span class="fc-name" :title="product.name">{{ product.name }}</span>
        <el-tag size="small" :type="statusType" effect="plain">{{ statusLabel }}</el-tag>
      </div>
      <div class="fc-meta">
        {{ typeLabel }}<template v-if="product.institution"> · {{ product.institution }}</template>
      </div>
    </div>

    <div class="fc-money">
      <span class="fc-principal">¥{{ formatYuan(product.principal) }}</span>
      <span class="fc-rate">{{ rateText }}</span>
    </div>

    <!-- 已赎回:展示到账、实际收益与年化,不再显示进度 -->
    <template v-if="isRedeemed">
      <div class="fc-rows">
        <div class="fc-row">
          <span class="fc-key">{{ $t('book.finance.redeemAmount') }}</span>
          <span class="fc-val">¥{{ money(product.redeemAmount) }}</span>
        </div>
        <div class="fc-row">
          <span class="fc-key">{{ $t('book.finance.actualInterest') }}</span>
          <span class="fc-val" :class="interestCls">¥{{ money(interestValue) }}</span>
        </div>
        <div class="fc-row">
          <span class="fc-key">{{ $t('book.finance.redeemDate') }}</span>
          <span class="fc-val">{{ product.redeemDate || $t('book.finance.unbounded') }}</span>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="fc-progress">
        <div class="fc-bar"><div class="fc-bar-inner" :style="{ width: `${progress}%` }"></div></div>
        <div class="fc-progress-text">{{ daysText }}</div>
      </div>

      <div class="fc-rows">
        <div class="fc-row">
          <span class="fc-key">{{ $t('book.finance.expectInterest') }}</span>
          <span class="fc-val">{{ expectedText }}</span>
        </div>
        <div class="fc-row">
          <span class="fc-key">{{ $t('book.finance.accruedInterest') }}</span>
          <span class="fc-val">{{ accruedText }}</span>
        </div>
        <div class="fc-row">
          <span class="fc-key">{{ $t('book.finance.maturity') }}</span>
          <span class="fc-val">{{ maturityText }}</span>
        </div>
      </div>

      <div class="fc-due" :class="{ 'is-overdue': overdue }">
        <span v-if="!endDate">{{ $t('book.finance.holdDaysOnly', { n: held }) }}</span>
        <span v-else>{{ $t('book.finance.maturityDate', { date: endDate }) }} · {{ remainText }}</span>
      </div>
    </template>

    <div class="fc-foot">
      <div class="fc-source">
        <span class="fc-account">{{ accountText }}</span>
        <el-tag v-if="product.accountId" size="small" :type="investBooked ? 'success' : 'info'" effect="plain">
          {{ investBooked ? $t('book.finance.invested') : $t('book.finance.notBooked') }}
        </el-tag>
        <el-tag v-if="redeemBooked" size="small" type="success" effect="plain">
          {{ $t('book.finance.redeemReturned') }}
        </el-tag>
      </div>
      <div class="fc-actions">
        <el-button v-if="canRedeem" size="small" type="primary" plain @click="emit('redeem', product)">
          {{ $t('book.finance.redeem') }}
        </el-button>
        <el-button size="small" text @click="emit('edit', product)">{{ $t('book.finance.edit') }}</el-button>
        <el-button size="small" text type="danger" @click="emit('remove', product)">{{ $t('common.delete') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 单张产品卡:所有金额/天数/进度都由 utils/finance.js 现算,卡片只做展示与事件上抛
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatYuan } from '@/utils/money'
import {
  accruedInterest,
  actualInterest,
  derivedStatus,
  endDateOf,
  expectedInterest,
  financeTypeLabel,
  heldDays,
  maturityAmount,
  progressPct,
  remainingDays,
  totalDays,
} from '@/utils/finance'

const props = defineProps({
  product: { type: Object, required: true },
})
const emit = defineEmits(['edit', 'redeem', 'remove'])

const { t } = useI18n()

const status = computed(() => derivedStatus(props.product))
const isRedeemed = computed(() => status.value === 'REDEEMED')
const canRedeem = computed(() => status.value === 'HOLDING' || status.value === 'MATURED')
const statusType = computed(
  () => ({ HOLDING: 'primary', MATURED: 'warning', REDEEMED: 'success', CANCELLED: 'info' })[status.value] || 'info',
)
const statusLabel = computed(() => t(`book.finance.status.${status.value}`))
const typeLabel = computed(() => financeTypeLabel(props.product.productType, t))

const endDate = computed(() => endDateOf(props.product))
const total = computed(() => totalDays(props.product) ?? 0)
const held = computed(() => heldDays(props.product))
const progress = computed(() => Math.round(progressPct(props.product) * 10) / 10)
const daysText = computed(() =>
  total.value > 0
    ? t('book.finance.holdDays', { held: held.value, total: total.value })
    : t('book.finance.holdDaysOnly', { n: held.value }),
)

const rateText = computed(() => {
  const rate = props.product.annualRate
  if (rate === null || rate === undefined || rate === '') return t('book.finance.floating')
  return `${Number(rate)}%`
})

const expectedText = computed(() => {
  const value = expectedInterest(props.product)
  return value === null ? t('book.finance.floating') : `¥${formatYuan(value)}`
})
const accruedText = computed(() => {
  const value = accruedInterest(props.product)
  return value === null ? t('book.finance.floating') : `¥${formatYuan(value)}`
})
const maturityText = computed(() => {
  const value = maturityAmount(props.product)
  return value === null ? t('book.finance.floating') : `¥${formatYuan(value)}`
})

const interestValue = computed(() => {
  const value = actualInterest(props.product)
  return value === null ? 0 : value
})
const interestCls = computed(() => (interestValue.value < 0 ? 'is-expense' : 'is-income'))

const remain = computed(() => remainingDays(props.product))
const overdue = computed(() => remain.value !== null && remain.value < 0)
const remainText = computed(() => {
  const left = remain.value
  if (left === null) return ''
  if (left > 0) return t('book.finance.remaining', { n: left })
  if (left === 0) return t('book.finance.dueToday')
  return t('book.finance.overdue', { n: -left })
})

// 已赎回:到账与收益来自落库值,卡片不再推算年化(年化在编辑弹窗里看)
const investBooked = computed(() => !!props.product.investBooked)
const redeemBooked = computed(() => !!props.product.redeemBooked)
const accountText = computed(() =>
  props.product.accountId
    ? t('book.finance.accountSource', { name: props.product.accountName || t('book.finance.noAccount') })
    : t('book.finance.noAccount'),
)

const money = (v) => (v === null || v === undefined ? t('book.finance.unbounded') : formatYuan(v))
</script>

<style scoped>
/* 卡片只放本组件排版;KPI/空态在 bookPage.css,EP 配色圆角走全局 main.css */
.fc-card {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.fc-card.is-redeemed {
  opacity: 0.92;
}
.fc-head {
  min-width: 0;
}
.fc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.fc-name {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fc-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.fc-money {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.fc-principal {
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.fc-rate {
  font-size: 13px;
  color: #e6a23c;
  font-variant-numeric: tabular-nums;
}
.fc-progress {
  display: flex;
  align-items: center;
  gap: 10px;
}
.fc-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: rgba(150, 150, 150, 0.18);
  overflow: hidden;
  min-width: 0;
}
.fc-bar-inner {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #7bb8ff, #4a8df0);
}
.fc-progress-text {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.fc-rows {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.fc-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
}
.fc-key {
  color: var(--color-text-secondary, #999);
}
.fc-val {
  font-variant-numeric: tabular-nums;
}
.fc-val.is-income {
  color: #67c23a;
}
.fc-val.is-expense {
  color: #f56c6c;
}
.fc-due {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.fc-due.is-overdue {
  color: #e6a23c;
}
.fc-foot {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.fc-source {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}
.fc-account {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 180px;
}
.fc-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.fc-actions :deep(.el-button) {
  padding: 5px 8px;
}
.fc-actions :deep(.el-button + .el-button) {
  margin-left: 2px;
}

@media (max-width: 768px) {
  .fc-account {
    max-width: 100%;
  }
  .fc-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
