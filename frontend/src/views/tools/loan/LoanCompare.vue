<template>
  <div class="card lc-card">
    <div class="lc-head">
      <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.compareTitle') }}</div>
      <span class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.compareHint') }}</span>
    </div>
    <div class="lc-tiles">
      <div class="lc-tile">
        <span>{{ $t('tools.loan.rec.rateImpact') }}</span>
        <b :class="{ 'lr-good': view.ledger.delta.rateImpact < 0, 'lr-bad': view.ledger.delta.rateImpact > 0 }">
          {{
            view.ledger.delta.rateImpact === 0 ? '—' : (view.ledger.delta.rateImpact > 0 ? '+' : '') + money(view.ledger.delta.rateImpact)
          }}
        </b>
        <em>{{ $t('tools.loan.rec.baseInterestLine', { v: money(view.ledger.baseline.totalInterest) }) }}</em>
      </div>
      <div class="lc-tile">
        <span>{{ $t('tools.loan.rec.prepaySaved') }}</span>
        <b class="lr-good">{{ view.ledger.delta.prepaySaved > 0 ? money(view.ledger.delta.prepaySaved) : '—' }}</b>
        <em>{{
          $t('tools.loan.rec.totalDiffLine', {
            v: (view.ledger.delta.totalDiff > 0 ? '+' : '') + money(view.ledger.delta.totalDiff),
          })
        }}</em>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatYuan } from '@/utils/loan'

defineProps({
  view: { type: Object, required: true },
})

const money = (v, digits = 2) => formatYuan(v, digits)
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
.lr-good {
  color: var(--color-brand, #b88c6e);
}
.lr-bad {
  color: var(--color-accent, #a8483a);
}
</style>
