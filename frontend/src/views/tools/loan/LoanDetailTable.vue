<template>
  <div class="card lc-card lc-block">
    <div class="lc-head">
      <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.detailTitle') }}</div>
      <el-radio-group v-model="detailMode" size="small">
        <el-radio-button value="month">{{ $t('tools.loan.byMonth') }}</el-radio-button>
        <el-radio-button value="year">{{ $t('tools.loan.byYear') }}</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-if="detailMode === 'month'" :data="pagedRows" size="small" border>
      <el-table-column :label="$t('tools.loan.rec.colPeriod')" width="76" align="right">
        <template #default="{ row }">
          {{ row.period }}
          <span v-if="row.days" class="lc-sub">{{ $t('tools.loan.rec.firstPeriodTag', { d: row.days }) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.rec.colDate')" width="96" align="right">
        <template #default="{ row }">{{ periodMonth(view, row.period) }}</template>
      </el-table-column>
      <el-table-column v-if="!view.isGroup" :label="$t('tools.loan.colRate')" width="86" align="right">
        <template #default="{ row }">{{ rate(row.rate) }}%</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colPayment')" min-width="110" align="right">
        <template #default="{ row }">
          <span :class="{ 'lr-event-row': row.rateChanged || row.prepaid }">{{ money(row.payment) }}</span>
          <span v-if="row.rateChanged" class="lc-sub lr-mark">{{ $t('dict.loan_event_type.RATE_CHANGE') }}</span>
          <span v-if="row.prepaid" class="lc-sub lr-mark">{{ $t('dict.loan_event_type.PREPAY') }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colPrincipal')" min-width="110" align="right">
        <template #default="{ row }">{{ money(row.principal) }}</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colInterest')" min-width="110" align="right">
        <template #default="{ row }">{{ money(row.interest) }}</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colBalance')" min-width="120" align="right">
        <template #default="{ row }">{{ money(row.balance) }}</template>
      </el-table-column>
    </el-table>

    <el-table v-else :data="yearlyRows" size="small" border>
      <el-table-column prop="year" :label="$t('tools.loan.colYear')" width="86" align="right" />
      <el-table-column :label="$t('tools.loan.colPayment')" min-width="120" align="right">
        <template #default="{ row }">{{ money(row.payment) }}</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colPrincipal')" min-width="120" align="right">
        <template #default="{ row }">{{ money(row.principal) }}</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colInterest')" min-width="120" align="right">
        <template #default="{ row }">{{ money(row.interest) }}</template>
      </el-table-column>
      <el-table-column :label="$t('tools.loan.colBalance')" min-width="132" align="right">
        <template #default="{ row }">{{ money(row.balance) }}</template>
      </el-table-column>
    </el-table>

    <div v-if="detailMode === 'month'" class="lc-pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[12, 24, 60]"
        :total="monthRows.length"
        layout="total, sizes, prev, pager, next"
        size="small"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { formatYuan, periodYearMonth } from '@/utils/loan'

const props = defineProps({
  view: { type: Object, required: true },
})

const detailMode = ref('month')
const page = ref(1)
const pageSize = ref(12)

const money = (v, digits = 2) => formatYuan(v, digits)
const rate = (v) => (Number(v) || 0).toFixed(4)
const periodMonth = (view, period) => periodYearMonth(view && view.firstPayDate, period)

const monthRows = computed(() => (props.view && props.view.ledger.rows) || [])
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return monthRows.value.slice(start, start + pageSize.value)
})

const yearlyRows = computed(() => {
  const out = []
  let cur = null
  for (const r of monthRows.value) {
    const year = Math.ceil(r.period / 12)
    if (!cur || cur.year !== year) {
      cur = { year, payment: 0, principal: 0, interest: 0, balance: r.balance }
      out.push(cur)
    }
    cur.payment += r.payment
    cur.principal += r.principal
    cur.interest += r.interest
    cur.balance = r.balance
  }
  return out
})

watch(
  () => props.view,
  () => {
    page.value = 1
  },
)
</script>

<style scoped>
.lc-card {
  padding: 18px 20px;
}
.lc-card .section-label {
  margin-bottom: 14px;
}
.lc-block {
  margin-top: 16px;
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
.lc-sub {
  display: block;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-secondary, #7a6b5a);
}
.lr-mark {
  color: var(--color-accent, #a8483a);
}
.lr-event-row {
  font-weight: 600;
}
.lc-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.lc-card :deep(.el-table .cell) {
  word-break: break-word;
}
</style>
