<template>
  <div class="card lc-card">
    <div class="lc-head">
      <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.progressTitle') }}</div>
      <div v-if="canManage && !view.isGroup" class="lc-head-actions">
        <el-button size="small" @click="emit('edit')">{{ $t('common.edit') }}</el-button>
        <el-button size="small" type="danger" plain @click="emit('delete')">{{ $t('common.delete') }}</el-button>
      </div>
      <span v-else-if="view.isGroup" class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.groupEditHint') }}</span>
    </div>
    <div class="lc-tiles">
      <div class="lc-tile emph">
        <span>{{ view.snapshot.settled ? $t('tools.loan.rec.settledBadge') : $t('tools.loan.rec.nextPayment') }}</span>
        <b>{{ view.snapshot.settled ? '—' : money(view.snapshot.currentPayment) }}</b>
        <em v-if="view.isGroup">{{ $t('tools.loan.rec.groupTileLine', { n: view.groupLoans.length }) }}</em>
        <em v-else>{{ $t('tools.loan.rec.currentRateIs', { v: rate(view.snapshot.currentRate) }) }}</em>
      </div>
      <div class="lc-tile">
        <span>{{ $t('tools.loan.rec.remainPrincipal') }}</span>
        <b>{{ money(view.snapshot.balance) }}</b>
        <em>{{
          view.snapshot.settled
            ? $t('tools.loan.rec.settledAtLine', { n: view.snapshot.settledAt })
            : $t('tools.loan.rec.remainPeriodsOf', { n: view.snapshot.remainingPeriods })
        }}</em>
      </div>
      <div class="lc-tile">
        <span>{{ $t('tools.loan.rec.paidPrincipalLabel') }}</span>
        <b>{{ money(view.snapshot.paidPrincipal) }}</b>
        <em>{{ $t('tools.loan.rec.paidInterestLine', { v: money(view.snapshot.paidInterest) }) }}</em>
      </div>
      <div class="lc-tile">
        <span>{{ $t('tools.loan.rec.totalInterestLabel') }}</span>
        <b>{{ money(view.ledger.summary.totalInterest) }}</b>
        <em>{{ $t('tools.loan.rec.interestRatioLine', { v: percent(view.ledger.summary.interestRatio) }) }}</em>
      </div>
    </div>
    <div class="lc-mini">
      <div>
        <span>{{ $t('tools.loan.rec.paidPeriodsLabel') }}</span
        ><b>{{ view.snapshot.paidPeriods }} / {{ view.ledger.rows.length }}</b>
      </div>
      <div>
        <span>{{ $t('tools.loan.rec.prepaidTotal') }}</span
        ><b>{{ view.ledger.summary.prepaidTotal > 0 ? money(view.ledger.summary.prepaidTotal) : '—' }}</b>
      </div>
      <div v-for="m in view.groupLoans || []" :key="m.id">
        <span>{{ m.name }}</span
        ><b>{{ money(m.snapshot.balance) }}</b>
      </div>
      <div v-if="!view.isGroup && view.ledger.firstDays !== 30">
        <span>{{ $t('tools.loan.rec.firstDaysLabel') }}</span
        ><b>{{ $t('tools.loan.rec.firstDaysValue', { n: view.ledger.firstDays }) }}</b>
      </div>
      <div v-if="!view.isGroup && view.firstPayment">
        <span>{{ $t('tools.loan.rec.formFirstPayment') }}</span
        ><b>{{ money(view.firstPayment) }}</b>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatYuan } from '@/utils/loan'

defineProps({
  view: { type: Object, required: true },
  canManage: { type: Boolean, default: false },
})

const emit = defineEmits(['edit', 'delete'])

const money = (v, digits = 2) => formatYuan(v, digits)
const rate = (v) => (Number(v) || 0).toFixed(4)
const percent = (v) => `${((Number(v) || 0) * 100).toFixed(2)}%`
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
.lc-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.lc-label-flat {
  margin-bottom: 0 !important;
}
.lc-head .lc-hint-inline {
  flex: 1;
  min-width: 160px;
}
.lc-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-hint-inline {
  margin: 0;
}

/* 概览磁贴(与计算器同款) */
.lc-tiles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.lc-tile {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-card-2, #e8dec8);
  min-width: 0;
}
.lc-tile span {
  display: block;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-tile b {
  display: block;
  margin-top: 4px;
  font-size: 19px;
  font-weight: 700;
  color: var(--color-text, #3a2e22);
  word-break: break-all;
}
.lc-tile em {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  font-style: normal;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-tile.emph {
  background: rgba(var(--color-brand-rgb), 0.16);
}
.lc-tile.emph b {
  color: var(--color-accent, #a8483a);
}

.lc-mini {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.12));
}
.lc-mini div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  min-width: 0;
}
.lc-mini span {
  color: var(--color-text-secondary, #7a6b5a);
  flex-shrink: 0;
}
.lc-mini b {
  color: var(--color-text, #3a2e22);
  font-weight: 600;
  word-break: break-all;
}

@media (max-width: 920px) {
  .lc-tiles {
    grid-template-columns: minmax(0, 1fr);
  }
  .lc-mini {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
