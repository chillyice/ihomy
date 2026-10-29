<!-- 理财页签:持仓概览 KPI + 状态筛选 + 到期日历 + 产品卡片,编辑/赎回/删除走弹窗 -->
<template>
  <div class="book-finance">
    <!-- 家庭理财是私有内容:游客只给登录引导,不发注定 401 的请求 -->
    <PageToolbar v-if="userStore.isLoggedIn" :card="false">
      <div class="tb-left">
        <button
          v-for="c in statusChips"
          :key="c.value"
          type="button"
          class="fn-chip"
          :class="{ active: status === c.value }"
          @click="status = c.value"
        >
          {{ c.label }}<span v-if="c.count" class="fn-chip-count">{{ c.count }}</span>
        </button>
      </div>
      <div class="tb-right">
        <el-button size="small" type="primary" @click="openEditor(null)">{{ $t('book.finance.add') }}</el-button>
      </div>
    </PageToolbar>

    <el-empty v-if="!userStore.isLoggedIn" :description="$t('book.guestEmpty')" />

    <div v-else v-loading="loading">
      <!-- 概览:持仓本金 / 已计提 / 到期预期 / 加权年化(浮动收益产品不计入,不留 NaN) -->
      <div class="book-kpi-grid cols-4">
        <div class="book-kpi card">
          <div class="book-kpi-label">{{ $t('book.finance.kpiPrincipal') }}</div>
          <div class="book-kpi-value">¥{{ money(summary.holdingPrincipal) }}</div>
          <div class="book-kpi-sub">
            {{ $t('book.finance.kpiCount', { n: summary.holdingCount }) }}
            <template v-if="summary.dueSoonCount"> · {{ $t('book.finance.kpiDueSoon', { n: summary.dueSoonCount }) }}</template>
          </div>
        </div>
        <div class="book-kpi card">
          <div class="book-kpi-label">{{ $t('book.finance.kpiAccrued') }}</div>
          <div class="book-kpi-value is-income">+{{ money(summary.accrued) }}</div>
          <div class="book-kpi-sub">{{ $t('book.finance.expectInterest') }} ¥{{ money(summary.expected) }}</div>
        </div>
        <div class="book-kpi card">
          <div class="book-kpi-label">{{ $t('book.finance.kpiRealized') }}</div>
          <div class="book-kpi-value" :class="summary.realized < 0 ? 'is-expense' : 'is-income'">
            {{ summary.realized < 0 ? '' : '+' }}{{ money(summary.realized) }}
          </div>
        </div>
        <div class="book-kpi card">
          <div class="book-kpi-label">{{ $t('book.finance.kpiRate') }}</div>
          <div class="book-kpi-value">{{ summary.weightedRate === null ? $t('book.finance.unbounded') : `${summary.weightedRate}%` }}</div>
          <div class="book-kpi-sub">{{ $t('book.finance.kpiCount', { n: summary.holdingCount }) }}</div>
        </div>
      </div>

      <!-- 到期日历:未来 90 天横排,展示到期日与剩余天数 -->
      <FinanceCalendar v-if="rows.length" :list="rows" />

      <el-empty v-if="!rows.length" :description="$t('book.finance.empty')">
        <el-button type="primary" @click="openEditor(null)">{{ $t('book.finance.add') }}</el-button>
      </el-empty>
      <el-empty v-else-if="!visibleRows.length" :description="$t('book.finance.filterEmpty')" />

      <div v-else class="fn-grid">
        <FinanceCard v-for="p in visibleRows" :key="p.id" :product="p" @edit="openEditor" @redeem="openRedeem" @remove="onRemove" />
      </div>
    </div>

    <!-- 新增/编辑:product 为空即新增 -->
    <FinanceEditorDialog v-model:visible="editor.visible" :product="editor.product" :accounts="accounts" @saved="load" />

    <!-- 赎回:到账金额 / 实际收益 / 转回本金与收益入账勾选 -->
    <FinanceRedeemDialog v-model:visible="redeem.visible" :product="redeem.product" @saved="load" />
  </div>
</template>

<script setup>
// 理财页签:一次并行取持仓与账户(账户下拉与余额联动都要用);状态筛选纯前端派生
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

import { bookApi, financeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatYuan } from '@/utils/money'
import { derivedStatus, summarizePortfolio } from '@/utils/finance'
import PageToolbar from '@/components/PageToolbar.vue'
import FinanceCalendar from './FinanceCalendar.vue'
import FinanceCard from './FinanceCard.vue'
import FinanceEditorDialog from './FinanceEditorDialog.vue'
import FinanceRedeemDialog from './FinanceRedeemDialog.vue'
import '../bookPage.css'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const rows = ref([])
const accounts = ref([])
// 筛选:持仓中 / 已到期 / 已赎回 / 全部(与卡片上的派生状态同名)
const status = ref('HOLDING')
const editor = reactive({ visible: false, product: null })
const redeem = reactive({ visible: false, product: null })

const summary = computed(() => summarizePortfolio(rows.value))

const statusChips = computed(() => {
  const counts = { HOLDING: 0, MATURED: 0, REDEEMED: 0 }
  for (const p of rows.value) {
    const s = derivedStatus(p)
    if (counts[s] !== undefined) counts[s]++
  }
  return [
    { value: 'HOLDING', label: t('book.finance.filterHolding'), count: counts.HOLDING },
    { value: 'MATURED', label: t('book.finance.filterMatured'), count: counts.MATURED },
    { value: 'REDEEMED', label: t('book.finance.filterRedeemed'), count: counts.REDEEMED },
    { value: 'ALL', label: t('book.finance.filterAll'), count: rows.value.length },
  ]
})

// 排序:持有中 → 已到期 → 已赎回/作废;同组内到期日早的靠前(活期垫底)
const RANK = { HOLDING: 0, MATURED: 1, REDEEMED: 2, CANCELLED: 3 }
const visibleRows = computed(() => {
  const list = status.value === 'ALL' ? rows.value : rows.value.filter((p) => derivedStatus(p) === status.value)
  return [...list].sort((a, b) => {
    const d = (RANK[derivedStatus(a)] ?? 9) - (RANK[derivedStatus(b)] ?? 9)
    if (d !== 0) return d
    return String(a.endDate || '9999-12-31').localeCompare(String(b.endDate || '9999-12-31'))
  })
})

const money = (v) => formatYuan(v)

const load = async () => {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    // 持仓与账户互不依赖,并行取
    const [list, acc] = await Promise.all([financeApi.list(), bookApi.accounts()])
    rows.value = list || []
    accounts.value = acc || []
  } finally {
    loading.value = false
  }
}

const openEditor = (product) => {
  editor.product = product || null
  editor.visible = true
}

const openRedeem = (product) => {
  redeem.product = product
  redeem.visible = true
}

const onRemove = async (product) => {
  try {
    await ElMessageBox.confirm(t('book.finance.deleteMessage', { name: product.name }), t('common.deleteConfirm'), { type: 'warning' })
  } catch {
    return
  }
  await financeApi.remove(product.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
// 从游客切到登录(或换家庭)时补拉数据
watch(
  () => userStore.isLoggedIn,
  (v) => v && load(),
)
</script>

<style scoped>
/* 本页只做布局:EP 配色圆角走全局 main.css,KPI/空态走 bookPage.css */
.fn-chip {
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
  white-space: nowrap;
}
.fn-chip:hover {
  transform: translateY(-1px);
}
.fn-chip.active {
  border-color: var(--color-brand);
  color: var(--color-brand);
  font-weight: 600;
}
.fn-chip-count {
  margin-left: 6px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  opacity: 0.75;
}

/* 产品卡:桌面自适应多列,窄屏单列(375px 不横向溢出) */
.fn-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
  align-items: stretch;
}

@media (max-width: 768px) {
  .fn-grid {
    grid-template-columns: 1fr;
  }
}
</style>
