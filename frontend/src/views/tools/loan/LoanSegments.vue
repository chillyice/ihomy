<template>
  <div class="card lc-card">
    <div class="section-label">{{ $t('tools.loan.rec.segmentsTitle') }}</div>
    <el-table :data="segmentRows" size="small" border>
      <el-table-column :label="$t('tools.loan.rec.colPeriods')" min-width="120" align="right">
        <template #default="{ row }">{{ $t('tools.loan.rec.segPeriods', { a: row.fromPeriod, b: row.toPeriod }) }}</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colRate')" width="90" align="right">
        <template #default="{ row }">{{ rate(row.rate) }}%</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.rec.segPayment')" min-width="118" align="right">
        <template #default="{ row }">
          <div>{{ money(row.payment) }}</div>
          <span v-if="row.firstPayment !== row.payment" class="lc-sub">{{
            $t('tools.loan.rec.segFirstLine', { v: money(row.firstPayment) })
          }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.rec.segInterest')" min-width="120" align="right">
        <template #default="{ row }">{{ money(row.interest) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatYuan } from '@/utils/loan'

const props = defineProps({
  view: { type: Object, required: true },
})

const money = (v, digits = 2) => formatYuan(v, digits)
const rate = (v) => (Number(v) || 0).toFixed(4)

const segmentRows = computed(() => (props.view && props.view.ledger.segments) || [])
</script>

<style scoped>
.lc-card {
  padding: 18px 20px;
}
.lc-card .section-label {
  margin-bottom: 14px;
}
.lc-sub {
  display: block;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-card :deep(.el-table .cell) {
  word-break: break-word;
}
</style>
