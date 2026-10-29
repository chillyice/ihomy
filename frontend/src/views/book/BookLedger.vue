<!-- 总账页签:KPI + 范围切换 + 收支趋势 + 月度总账(年行展开到月) + 同比/分类/成员 + 资金分布 + CSV 导出 -->
<template>
  <div class="book-ledger">
    <!-- 总账页签:游客只给登录引导,不发注定 401 的请求 -->
    <PageToolbar v-if="userStore.isLoggedIn" :card="false">
      <div class="tb-left">
        <!-- 范围 chips:全部 + 有记录的年份(后端 years 倒序) -->
        <button
          v-for="c in scopeChips"
          :key="c.value"
          type="button"
          class="lg-chip"
          :class="{ active: scope === c.value }"
          @click="pickScope(c.value)"
        >
          {{ c.label }}
        </button>
      </div>
      <div class="tb-right">
        <el-button size="small" :disabled="!hasRecords" @click="exportCsv">{{ $t('book.ledger.export') }}</el-button>
      </div>
    </PageToolbar>

    <!-- 家庭账本私有:游客不发注定 401 的请求 -->
    <el-empty v-if="!userStore.isLoggedIn" :description="$t('book.guestEmpty')" />

    <div v-else v-loading="loading">
      <el-empty v-if="!hasRecords" :description="$t('book.ledger.empty')" />

      <template v-else>
        <div class="book-kpi-grid cols-4">
          <div class="book-kpi card">
            <div class="book-kpi-label">{{ $t('book.ledger.kpiIncome') }}</div>
            <div class="book-kpi-value is-income">+{{ money(totals.income) }}</div>
          </div>
          <div class="book-kpi card">
            <div class="book-kpi-label">{{ $t('book.ledger.kpiExpense') }}</div>
            <div class="book-kpi-value is-expense">-{{ money(totals.expense) }}</div>
          </div>
          <div class="book-kpi card">
            <div class="book-kpi-label">{{ $t('book.ledger.kpiBalance') }}</div>
            <div class="book-kpi-value">{{ money(totals.balance) }}</div>
          </div>
          <div class="book-kpi card">
            <div class="book-kpi-label">{{ $t('book.ledger.kpiCount') }}</div>
            <div class="book-kpi-value">{{ totals.count || 0 }}</div>
            <div class="book-kpi-sub">{{ spanText }}</div>
            <div v-if="totals.transferCount" class="book-kpi-sub">
              {{ $t('book.ledger.transferNote', { n: totals.transferCount }) }}
            </div>
          </div>
        </div>

        <div class="lg-main">
          <div class="lg-left">
            <!-- 趋势:全部 → 按年柱;某年 → 12 个月柱 -->
            <div class="card lg-panel">
              <div class="section-label">{{ $t('book.ledger.trend') }}</div>
              <BookTrendChart :rows="trendRows" />
            </div>

            <!-- 月度总账:年行点开是 12 个月,点某月切回记账本定位 -->
            <div class="card lg-panel">
              <div class="section-label">{{ $t('book.ledger.table') }}</div>
              <div class="lt-scroll">
                <table class="lt-table">
                  <thead>
                    <tr>
                      <th class="lt-period">{{ $t('book.ledger.colPeriod') }}</th>
                      <th>{{ $t('book.ledger.colOpening') }}</th>
                      <th>{{ $t('book.ledger.colIncome') }}</th>
                      <th>{{ $t('book.ledger.colExpense') }}</th>
                      <th>{{ $t('book.ledger.colClosing') }}</th>
                      <th>{{ $t('book.ledger.colCount') }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    <template v-for="y in tableYears" :key="y.year">
                      <tr class="lt-year" v-a11y-click @click="toggleYear(y.year)">
                        <td class="lt-period">
                          <el-icon class="lt-caret" :class="{ open: expandedYear === y.year }"><ArrowRight /></el-icon>
                          {{ $t('book.ledger.yearLabel', { year: y.year }) }}
                        </td>
                        <td>{{ money(y.opening) }}</td>
                        <td class="is-income">+{{ money(y.income) }}</td>
                        <td class="is-expense">-{{ money(y.expense) }}</td>
                        <td>{{ money(y.closing) }}</td>
                        <td>{{ y.count }}</td>
                      </tr>
                      <tr
                        v-for="m in expandedYear === y.year ? monthRows(y.year) : []"
                        :key="m.month"
                        class="lt-month"
                        v-a11y-click
                        @click="emit('pick-month', m.month)"
                      >
                        <td class="lt-period">{{ m.month }}</td>
                        <td>{{ money(m.opening) }}</td>
                        <td class="is-income">{{ m.income ? `+${money(m.income)}` : '—' }}</td>
                        <td class="is-expense">{{ m.expense ? `-${money(m.expense)}` : '—' }}</td>
                        <td>{{ money(m.closing) }}</td>
                        <td>{{ m.count || '—' }}</td>
                      </tr>
                    </template>
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <div class="lg-right">
            <!-- 年度同比:上期为 0 时给「无基数」,不显示 Infinity -->
            <div class="card lg-panel">
              <div class="section-label">{{ $t('book.ledger.yoy') }}</div>
              <div v-for="r in yoyRows" :key="r.year" class="lg-yoy-row">
                <span class="lg-yoy-year">{{ $t('book.ledger.yearLabel', { year: r.year }) }}</span>
                <span class="lg-yoy-cell">
                  <span class="lg-yoy-key">{{ $t('book.ledger.yoyIncome') }}</span>
                  <span :class="yoyCls('income', r.income)">{{ yoyText(r.income) }}</span>
                </span>
                <span class="lg-yoy-cell">
                  <span class="lg-yoy-key">{{ $t('book.ledger.yoyExpense') }}</span>
                  <span :class="yoyCls('expense', r.expense)">{{ yoyText(r.expense) }}</span>
                </span>
              </div>
              <div v-if="!yoyRows.length" class="book-empty">{{ $t('book.ledger.yoyEmpty') }}</div>
            </div>

            <!-- 支出 / 收入构成:金额 + 占比 + 占比条 -->
            <div class="card lg-panel">
              <div class="section-label">{{ $t('book.ledger.expenseComposition') }}</div>
              <div v-for="c in expenseCats" :key="c.category" class="lg-cat">
                <div class="lg-cat-head">
                  <span class="lg-cat-name">{{ c.category }}</span>
                  <span class="lg-cat-amount">{{ money(c.total) }} · {{ pctText(c.share) }}</span>
                </div>
                <div class="lg-bar"><div class="lg-bar-fill is-expense" :style="{ width: pctWidth(c.share) }"></div></div>
              </div>
              <div v-if="!expenseCats.length" class="book-empty">{{ $t('book.ledger.compositionEmpty') }}</div>
            </div>

            <div class="card lg-panel">
              <div class="section-label">{{ $t('book.ledger.incomeComposition') }}</div>
              <div v-for="c in incomeCats" :key="c.category" class="lg-cat">
                <div class="lg-cat-head">
                  <span class="lg-cat-name">{{ c.category }}</span>
                  <span class="lg-cat-amount">{{ money(c.total) }} · {{ pctText(c.share) }}</span>
                </div>
                <div class="lg-bar"><div class="lg-bar-fill is-income" :style="{ width: pctWidth(c.share) }"></div></div>
              </div>
              <div v-if="!incomeCats.length" class="book-empty">{{ $t('book.ledger.compositionEmpty') }}</div>
            </div>

            <!-- 成员收支:跟随范围,转账不计入 -->
            <div class="card lg-panel">
              <div class="section-label">{{ $t('book.ledger.member') }}</div>
              <div v-for="m in members" :key="m.userId" class="lg-member">
                <span class="lg-member-name">{{ m.name }}</span>
                <span class="lg-member-nums">
                  <span class="is-income">+{{ money(m.income) }}</span>
                  <span class="is-expense">-{{ money(m.expense) }}</span>
                </span>
              </div>
              <div v-if="!members.length" class="book-empty">{{ $t('book.ledger.memberEmpty') }}</div>
            </div>
          </div>
        </div>

        <!-- 资金分布:账户实时余额 + 家庭资金合计(含期初),与记账本余额条同源 -->
        <div class="card lg-fund">
          <div class="section-label">{{ $t('book.ledger.fund') }}</div>
          <div v-if="accounts.length" class="lg-fund-grid">
            <div v-for="a in accounts" :key="a.id" class="lg-fund-item">
              <div class="lg-fund-name">
                {{ a.name }}
                <span class="lg-fund-type">{{ accountTypeLabel(a.accountType, t) }}</span>
              </div>
              <div class="lg-fund-amount" :class="{ 'is-negative': Number(a.balance) < 0 }">¥{{ money(a.balance) }}</div>
            </div>
            <div class="lg-fund-item is-total">
              <div class="lg-fund-name">{{ $t('book.ledger.fundTotal') }}</div>
              <div class="lg-fund-amount">¥{{ money(fundTotal) }}</div>
            </div>
          </div>
          <div v-else class="book-empty">{{ $t('book.ledger.fundEmpty') }}</div>
          <div class="lg-note">{{ $t('book.ledger.fundNote') }}</div>
          <div v-if="unassigned" class="lg-note">{{ $t('book.account.unassignedCount', { n: unassigned }) }}</div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
// 总账页签:一次取总账聚合 + 账户余额(Promise.all);范围切换只重拉这两个接口
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight } from '@element-plus/icons-vue'

import { bookApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatYuan } from '@/utils/loan'
import { accountTypeLabel, fillMonths, ledgerCsvRows, ratio, sumBalances, yoy } from '@/utils/bookLedger'
import { saveBlob } from '@/utils/tools/download'
import PageToolbar from '@/components/PageToolbar.vue'
import BookTrendChart from './BookTrendChart.vue'
import './bookPage.css'

const emit = defineEmits(['pick-month'])

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
// scope:'ALL' 或年份字符串;后端 year 缺省即全部
const scope = ref('ALL')
const ledger = ref(null)
const accounts = ref([])
// 同时只展开一个年份,避免表格长到失控
const expandedYear = ref(null)

const totals = computed(() => ledger.value?.totals || {})
const hasRecords = computed(() => Number(totals.value.count || 0) > 0 || Number(totals.value.transferCount || 0) > 0)
const unassigned = computed(() => Number(ledger.value?.unassigned?.count || 0))
const fundTotal = computed(() => sumBalances(accounts.value))

const scopeChips = computed(() => [
  { value: 'ALL', label: t('book.ledger.scopeAll') },
  ...(ledger.value?.years || []).map((y) => ({ value: String(y), label: t('book.ledger.yearLabel', { year: y }) })),
])

const spanText = computed(() => {
  const { firstDate, lastDate } = totals.value
  return firstDate && lastDate ? t('book.ledger.span', { from: firstDate, to: lastDate }) : ''
})

// 表格只列范围覆盖的年份:全部 → 所有年;某年 → 只该年
// (只有转账的年份不进 byYear,补一行全 0 的年行,期初/期末承接范围期初)
const tableYears = computed(() => {
  const rows = ledger.value?.byYear || []
  if (scope.value === 'ALL') return rows
  const year = Number(scope.value)
  const found = rows.find((r) => r.year === year)
  if (found) return [found]
  const opening = Number(ledger.value?.opening) || 0
  return [{ year, income: 0, expense: 0, balance: 0, count: 0, opening, closing: opening }]
})

// 每个年份 12 个月补零(后端只下发有记录的月份)
const monthsByYear = computed(() => {
  const map = new Map()
  const base = Number(ledger.value?.opening) || 0
  for (const y of tableYears.value) {
    const filled = fillMonths(ledger.value?.byMonth, y.year)
    // 该年没有任何收支(例如只有转账):12 槽按范围期初平铺,不显示成从 0 起算
    const empty = filled.every((m) => !m.income && !m.expense)
    map.set(y.year, empty ? filled.map((m) => ({ ...m, opening: base, closing: base })) : filled)
  }
  return map
})
const monthRows = (year) => monthsByYear.value.get(year) || []

// 趋势:全部 → 每年两根柱;某年 → 12 个月两根柱
const trendRows = computed(() => {
  if (scope.value === 'ALL') {
    return (ledger.value?.byYear || []).map((r) => ({
      key: `y${r.year}`,
      label: String(r.year),
      income: Number(r.income) || 0,
      expense: Number(r.expense) || 0,
    }))
  }
  const year = Number(scope.value)
  return fillMonths(ledger.value?.byMonth, year).map((m) => ({
    key: m.month,
    label: String(Number(m.month.slice(5))),
    income: Number(m.income) || 0,
    expense: Number(m.expense) || 0,
  }))
})

// 年度同比:每年对前一年(byYear 升序,首年没有基数);倒序展示最近年在最上
const yoyRows = computed(() => {
  const rows = ledger.value?.byYear || []
  if (rows.length < 2) return []
  return rows
    .map((r, i) => ({
      year: r.year,
      income: i > 0 ? yoy(r.income, rows[i - 1].income) : null,
      expense: i > 0 ? yoy(r.expense, rows[i - 1].expense) : null,
    }))
    .reverse()
})

// 分类占比:前端按同一口径复算(合计 0 → 0),不依赖后端下发的小数精度
const withShare = (list) => {
  const rows = list || []
  const sum = rows.reduce((s, r) => s + (Number(r.total) || 0), 0)
  return rows.map((r) => ({ ...r, share: ratio(r.total, sum) }))
}
const expenseCats = computed(() => withShare(ledger.value?.expenseByCategory))
const incomeCats = computed(() => withShare(ledger.value?.incomeByCategory))
const members = computed(() => ledger.value?.byMember || [])

const money = (v) => formatYuan(v)
const pctText = (v) => `${((Number(v) || 0) * 100).toFixed(1)}%`
const pctWidth = (v) => `${Math.min(100, Math.max(0, (Number(v) || 0) * 100))}%`
const yoyText = (v) => (v == null ? t('book.ledger.yoyNoBase') : `${v > 0 ? '+' : ''}${(v * 100).toFixed(1)}%`)

// 收入涨是好事、支出涨是坏事:只影响颜色,不改变数值
const yoyCls = (kind, v) => {
  if (v == null || Math.abs(v) < 0.00005) return 'is-flat'
  return (kind === 'income' ? v > 0 : v < 0) ? 'is-good' : 'is-bad'
}

const load = async () => {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    // 总账聚合与账户余额互不依赖,并行取
    const [data, acc] = await Promise.all([bookApi.ledger(scope.value === 'ALL' ? undefined : Number(scope.value)), bookApi.accounts()])
    ledger.value = data || null
    accounts.value = acc || []
    // 默认展开:选中某年就展开该年,全部范围展开最近一年
    const years = (data?.byYear || []).map((r) => r.year)
    expandedYear.value = scope.value === 'ALL' ? (years[years.length - 1] ?? null) : Number(scope.value)
  } finally {
    loading.value = false
  }
}

const pickScope = (value) => {
  if (scope.value === value) return
  scope.value = value
}

const toggleYear = (year) => {
  expandedYear.value = expandedYear.value === year ? null : year
}

// CSV 行模型:每个年行 + 该年 12 个月(不受展开状态影响,导出总是完整)
const csvRows = () => {
  const rows = []
  for (const y of ledger.value?.byYear || []) {
    rows.push({ label: t('book.ledger.yearLabel', { year: y.year }), ...y })
    for (const m of fillMonths(ledger.value?.byMonth, y.year)) rows.push({ label: m.month, ...m })
  }
  return rows
}

const exportCsv = () => {
  const header = [
    t('book.ledger.colPeriod'),
    t('book.ledger.colIncome'),
    t('book.ledger.colExpense'),
    t('book.ledger.colBalance'),
    t('book.ledger.colCount'),
    t('book.ledger.colOpening'),
    t('book.ledger.colClosing'),
  ]
  // BOM + CRLF 只为 Excel 直接双击不乱码/不并成一行
  const text =
    '\ufeff' +
    ledgerCsvRows(csvRows(), header)
      .map((line) => line.join(','))
      .join('\r\n')
  const scopeLabel = scope.value === 'ALL' ? t('book.ledger.scopeAll') : t('book.ledger.yearLabel', { year: scope.value })
  saveBlob(new Blob([text], { type: 'text/csv;charset=utf-8' }), t('book.ledger.csvFilename', { scope: scopeLabel }))
}

watch(scope, load)
onMounted(load)
</script>

<style scoped>
/* 本页只做布局:EP 组件配色/圆角走全局 main.css,KPI/空态走 bookPage.css */
.lg-main {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}
.lg-left,
.lg-right {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.lg-panel {
  padding: 16px 18px;
  min-width: 0;
}

/* 范围 chips */
.lg-chip {
  padding: 5px 14px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-card-2);
  color: var(--color-text-secondary, #999);
  font-size: 13px;
  line-height: 1.4;
  cursor: pointer;
  transition:
    color 0.2s,
    border-color 0.2s,
    transform 0.2s;
}
.lg-chip:hover {
  transform: translateY(-1px);
}
.lg-chip.active {
  border-color: var(--color-brand);
  color: var(--color-brand);
  font-weight: 600;
}

/* 月度总账表 */
.lt-scroll {
  overflow-x: auto;
  scrollbar-width: thin;
}
.lt-table {
  width: 100%;
  min-width: 480px;
  border-collapse: collapse;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.lt-table th {
  text-align: right;
  font-weight: 500;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}
.lt-table th.lt-period,
.lt-table td.lt-period {
  text-align: left;
}
.lt-table td {
  text-align: right;
  padding: 8px;
  border-bottom: 1px dashed var(--color-border);
  white-space: nowrap;
}
.lt-year {
  cursor: pointer;
  font-weight: 600;
}
.lt-year:hover {
  background: var(--color-card-2);
}
.lt-caret {
  margin-right: 4px;
  font-size: 11px;
  transition: transform 0.2s;
  vertical-align: middle;
}
.lt-caret.open {
  transform: rotate(90deg);
}
.lt-month {
  cursor: pointer;
  color: var(--color-text-secondary, #999);
}
.lt-month:hover {
  background: var(--color-card-2);
  color: var(--color-text, #3a2e22);
}
.is-income {
  color: #67c23a;
}
.is-expense {
  color: #f56c6c;
}

/* 年度同比 */
.lg-yoy-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--color-border);
  font-size: 12px;
}
.lg-yoy-year {
  flex: 1;
  font-weight: 600;
  font-size: 13px;
}
.lg-yoy-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.lg-yoy-key {
  color: var(--color-text-secondary, #999);
}
.is-good {
  color: #67c23a;
}
.is-bad {
  color: #f56c6c;
}
.is-flat {
  color: var(--color-text-secondary, #999);
}

/* 分类构成 */
.lg-cat {
  padding: 7px 0;
}
.lg-cat-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}
.lg-cat-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lg-cat-amount {
  color: var(--color-text-secondary, #999);
  font-size: 12px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.lg-bar {
  height: 4px;
  margin-top: 5px;
  border-radius: 2px;
  background: var(--color-card-2);
  overflow: hidden;
}
.lg-bar-fill {
  height: 100%;
  border-radius: 2px;
}
.lg-bar-fill.is-income {
  background: #67c23a;
}
.lg-bar-fill.is-expense {
  background: #f56c6c;
}

/* 成员收支 */
.lg-member {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--color-border);
  font-size: 13px;
}
.lg-member-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lg-member-nums {
  display: inline-flex;
  gap: 10px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

/* 资金分布 */
.lg-fund {
  margin-top: 14px;
  padding: 16px 18px;
}
.lg-fund-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}
.lg-fund-item {
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  min-width: 0;
}
.lg-fund-item.is-total {
  border-color: var(--color-brand);
}
.lg-fund-name {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lg-fund-type {
  margin-left: 6px;
  font-size: 11px;
  opacity: 0.8;
}
.lg-fund-amount {
  margin-top: 4px;
  font-size: 16px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.lg-fund-amount.is-negative {
  color: #f56c6c;
}
.lg-note {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  line-height: 1.6;
}

@media (max-width: 768px) {
  .lg-main {
    grid-template-columns: 1fr;
  }
  .lt-table {
    min-width: 440px;
  }
}
</style>
