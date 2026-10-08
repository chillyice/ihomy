<template>
  <div class="card lc-card">
    <div class="lc-head">
      <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.listTitle') }}</div>
      <el-button v-if="canManage" size="small" type="primary" @click="emit('add')">{{ $t('tools.loan.rec.addLoan') }}</el-button>
    </div>

    <template v-if="listRows.length">
      <template v-for="row in listRows" :key="row.view.id">
        <!-- 组表头:点看合计 -->
        <div
          v-a11y-click
          v-if="row.type === 'group'"
          class="lr-item lr-group-item"
          :class="{ on: selectedKey === row.view.id }"
          @click="emit('update:selectedKey', row.view.id)"
        >
          <div class="lr-item-top">
            <span class="lr-item-name">{{ row.view.name }}</span>
            <span class="lr-badge lr-badge-group">{{ $t('tools.loan.rec.groupBadge', { n: row.view.groupLoans.length }) }}</span>
          </div>
          <div class="lr-item-sub">
            <span>{{ $t('tools.loan.rec.remainPrincipal') }} {{ money(row.view.snapshot.balance) }}</span>
            <span v-if="row.view.snapshot.settled">{{ $t('tools.loan.rec.settledBadge') }}</span>
            <span v-else>{{ $t('tools.loan.rec.nextPayment') }} {{ money(row.view.snapshot.currentPayment) }}</span>
          </div>
        </div>
        <!-- 单笔贷款 -->
        <div
          v-a11y-click
          v-else
          class="lr-item"
          :class="{ on: selectedKey === String(row.view.id), child: row.child }"
          @click="emit('update:selectedKey', String(row.view.id))"
        >
          <div class="lr-item-top">
            <span class="lr-item-name">{{ row.view.name }}</span>
            <span v-if="row.view.snapshot.settled" class="lr-badge">{{ $t('tools.loan.rec.settledBadge') }}</span>
            <el-tag v-else size="small" type="info" effect="plain" class="lr-tag">{{
              dictText(t, 'loan_channel', row.view.channel)
            }}</el-tag>
          </div>
          <div class="lr-item-sub">
            <span>{{ $t('tools.loan.rec.principal') }} {{ money(row.view.amount / 10000) }}{{ $t('tools.loan.wanShort') }}</span>
            <span>{{ $t('tools.loan.rec.currentRate') }} {{ rate(row.view.snapshot.currentRate) }}%</span>
          </div>
          <div class="lr-item-sub">
            <template v-if="row.view.snapshot.settled">
              <span>{{ $t('tools.loan.rec.settledAtLine', { n: row.view.snapshot.settledAt }) }}</span>
            </template>
            <template v-else>
              <span>{{ $t('tools.loan.rec.remainPrincipal') }} {{ money(row.view.snapshot.balance) }}</span>
              <span>{{ $t('tools.loan.rec.remainPeriodsOf', { n: row.view.snapshot.remainingPeriods }) }}</span>
            </template>
          </div>
        </div>
      </template>
    </template>
    <div v-else-if="loadError" style="padding: 20px 0; text-align: center; color: var(--color-text-secondary)">
      {{ $t('common.loadFailed') }} <el-button text size="small" @click="emit('retry')">{{ $t('common.retry') }}</el-button>
    </div>
    <el-empty v-else-if="!loading" :description="$t('tools.loan.rec.emptyHint')" :image-size="72" />
    <p v-if="!canManage && loans.length" class="lc-hint">{{ $t('tools.loan.rec.viewOnlyHint') }}</p>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'
import { formatYuan } from '@/utils/loan'

defineProps({
  listRows: { type: Array, default: () => [] },
  selectedKey: { type: String, default: '' },
  canManage: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  loadError: { type: Boolean, default: false },
  loans: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:selectedKey', 'add', 'retry'])

const { t } = useI18n()

const money = (v, digits = 2) => formatYuan(v, digits)
const rate = (v) => (Number(v) || 0).toFixed(4)
</script>

<style scoped>
.lc-card {
  padding: 18px 20px;
}
.lc-card .section-label {
  margin-bottom: 14px;
}
.lc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.lc-label-flat {
  margin-bottom: 0 !important;
}

/* 贷款卡片 */
.lr-item {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-card-2, #e8dec8);
  cursor: pointer;
  transition: background 0.2s;
}
.lr-item + .lr-item {
  margin-top: 10px;
}
.lr-item.on {
  background: rgba(var(--color-brand-rgb), 0.16);
}
.lr-item.child {
  margin-left: 16px;
}
.lr-group-item {
  border: 1px dashed rgba(var(--color-brand-rgb), 0.55);
}
.lr-item-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lr-item-name {
  font-weight: 600;
  color: var(--color-text, #3a2e22);
  flex: 1;
  min-width: 0;
}
.lr-badge {
  font-size: 11px;
  color: var(--color-brand, #b88c6e);
  border: 1px solid currentColor;
  border-radius: 999px;
  padding: 0 8px;
  line-height: 20px;
  flex-shrink: 0;
}
.lr-badge-group {
  color: var(--color-accent, #a8483a);
}
.lr-item-sub {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b5a);
  word-break: break-all;
}
.lc-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #7a6b5a);
}
</style>
