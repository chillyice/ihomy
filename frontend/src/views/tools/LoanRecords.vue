<template>
  <div class="lr-root">
    <!-- 未登录:贷款记录是家庭数据,试算/反推可游客用,这里需要登录 -->
    <el-empty v-if="!userStore.isLoggedIn" :description="$t('tools.loan.rec.loginHint')">
      <template #image
        ><el-icon :size="42" class="lr-empty-icon"><Lock /></el-icon
      ></template>
      <el-button type="primary" size="small" @click="$router.push('/login')">{{ $t('tools.loan.rec.goLogin') }}</el-button>
    </el-empty>

    <template v-else>
      <el-alert v-if="ledgerError" :title="ledgerError" type="warning" :closable="false" show-icon class="lr-alert" />

      <div class="lr-layout">
        <!-- 左:贷款列表(组表头 + 组内成员 + 独立贷款) -->
        <div class="lr-col">
          <LoanList
            v-model:selectedKey="selectedKey"
            :list-rows="listRows"
            :can-manage="canManage"
            :loading="loading"
            :load-error="loadError"
            :loans="loans"
            @add="openEditor()"
            @retry="load"
          />
        </div>

        <!-- 右:进度 + 时间轴 + 分段 + 对比 -->
        <div class="lr-col">
          <template v-if="current">
            <LoanProgress :view="current" :can-manage="canManage" @edit="openEditor(current)" @delete="onDeleteLoan(current)" />
            <LoanTimeline
              :view="current"
              :can-manage="canManage"
              @add-rate="openEvent(LOAN_EVENT.RATE_CHANGE)"
              @add-prepay="openEvent(LOAN_EVENT.PREPAY)"
              @event-click="onEventClick"
              @event-delete="onEventDelete"
            />
            <LoanSegments v-if="!current.isGroup && current.ledger.segments.length > 1" :view="current" />
            <LoanCompare :view="current" />
          </template>
          <div v-else class="card lc-card lr-select-hint">
            <el-empty :description="loans.length ? $t('tools.loan.rec.noSelect') : $t('tools.loan.rec.emptyTitle')" :image-size="72" />
          </div>
        </div>
      </div>

      <!-- 还款流水线:自下而上,最下第 1 期,最上下一期;事件节点配色区分;可拖入新事件,也可拖动已有事件 -->
      <LoanPipeline
        v-if="current && current.ledger.rows.length"
        :view="current"
        :can-manage="canManage"
        @open-event="onPipelineOpenEvent"
        @move-event="onPipelineMoveEvent"
      />

      <!-- 流水明细 -->
      <LoanDetailTable v-if="current && current.ledger.rows.length" :view="current" />
    </template>

    <LoanEditorDialog ref="editorRef" :group-names="groupNames" @saved="onEditorSaved" />
    <LoanEventDialog ref="eventRef" @saved="load" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lock } from '@element-plus/icons-vue'
import { loanApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { loanLedger, ledgerSnapshot, mergeLedgers, daysBetween, periodsPaid, LOAN_EVENT } from '@/utils/loan'
import { toNum, loanToPayload } from './loan/loanShared'
import LoanList from './loan/LoanList.vue'
import LoanProgress from './loan/LoanProgress.vue'
import LoanTimeline from './loan/LoanTimeline.vue'
import LoanSegments from './loan/LoanSegments.vue'
import LoanCompare from './loan/LoanCompare.vue'
import LoanPipeline from './loan/LoanPipeline.vue'
import LoanDetailTable from './loan/LoanDetailTable.vue'
import LoanEditorDialog from './loan/LoanEditorDialog.vue'
import LoanEventDialog from './loan/LoanEventDialog.vue'

const { t } = useI18n()
const userStore = useUserStore()

const LEDGER_ERROR_KEYS = {
  BAD_PERIODS: 'errBadPeriods',
  NO_AMOUNT: 'errNoAmount',
  BAD_FIRST_DAYS: 'errBadFirstDays',
  BAD_EVENT_PERIOD: 'errBadEventPeriod',
  BAD_EVENT_VALUE: 'errBadEventValue',
  FIRST_PAYMENT_TOO_LOW: 'errFirstPaymentTooLow',
  PAYMENT_TOO_LOW: 'errPaymentTooLow',
}

const loading = ref(false)
const loadError = ref(false)
const loans = ref([])
const selectedKey = ref('') // 'g:<组名>' 看合计,或 '<贷款id>' 看单笔

// 已还期数/还款进度依赖「今天」;定时 + 回到页面可见时刷新 now,跨还款日自动重算
const now = ref(new Date())
const tickNow = () => {
  now.value = new Date()
}
const nowTimer = setInterval(tickNow, 60000)
const onVisibility = () => {
  if (!document.hidden) tickNow()
}
document.addEventListener('visibilitychange', onVisibility)
onBeforeUnmount(() => {
  clearInterval(nowTimer)
  document.removeEventListener('visibilitychange', onVisibility)
})

const canManage = computed(() => userStore.isOwner || userStore.hasPerm('loan:manage'))

/** 每笔贷款 → 引擎入参(首期天数由放款日与首期还款日推出) */
const engineInput = (loan) => ({
  amount: toNum(loan.amount),
  months: Number(loan.months),
  method: loan.method,
  rate: toNum(loan.rate),
  firstDays: daysBetween(loan.loanDate, loan.firstPayDate),
  firstPayment: toNum(loan.firstPayment),
  events: (loan.events || []).map((e) => ({
    type: e.type,
    effectivePeriod: Number(e.effectivePeriod),
    rate: toNum(e.rate),
    amount: toNum(e.amount),
    strategy: e.strategy,
  })),
})

/** 卡片/详情用的逐笔视图:流水 + 进度快照(列表量级小,直接全算) */
const cardViews = computed(() =>
  loans.value.map((loan) => {
    const ledger = loanLedger(engineInput(loan))
    const paidPeriods = loan.firstPayDate ? periodsPaid(loan.firstPayDate, now.value) : 0
    const snapshot = ledgerSnapshot(ledger, paidPeriods)
    return { ...loan, ledger, snapshot, paidPeriods, valid: ledger.valid }
  }),
)

/** 贷款组:同名 group_name 且 ≥2 笔 → 合计视图(逐期相加;delta/baseline 可加) */
const groupViews = computed(() => {
  const byName = new Map()
  for (const v of cardViews.value) {
    const g = (v.groupName || '').trim()
    if (!g) continue
    if (!byName.has(g)) byName.set(g, [])
    byName.get(g).push(v)
  }
  const out = []
  for (const [name, members] of byName) {
    if (members.length < 2) continue
    const ledger = mergeLedgers(members.map((m) => m.ledger))
    if (!ledger) continue
    const paidPeriods = Math.max(...members.map((m) => m.paidPeriods))
    const snapshot = ledgerSnapshot(ledger, paidPeriods)
    const delta = members.reduce(
      (s, m) => ({
        rateImpact: s.rateImpact + ((m.ledger.delta && m.ledger.delta.rateImpact) || 0),
        prepaySaved: s.prepaySaved + ((m.ledger.delta && m.ledger.delta.prepaySaved) || 0),
        totalDiff: s.totalDiff + ((m.ledger.delta && m.ledger.delta.totalDiff) || 0),
      }),
      { rateImpact: 0, prepaySaved: 0, totalDiff: 0 },
    )
    const events = members
      .flatMap((m) => (m.events || []).map((e) => ({ ...e, loanId: m.id, loanName: m.name })))
      .sort((a, b) => a.effectivePeriod - b.effectivePeriod)
    out.push({
      id: 'g:' + name,
      isGroup: true,
      name,
      groupName: name,
      channel: 'GROUP',
      amount: members.reduce((s, m) => s + (Number(m.amount) || 0), 0),
      months: Math.max(...members.map((m) => Number(m.months) || 0)),
      // 组的年月/流水线辅助线用成员里最早的首期还款日作基准(各笔首期还款日通常一致)
      firstPayDate:
        members
          .map((m) => m.firstPayDate)
          .filter(Boolean)
          .sort()[0] || null,
      firstPayment: null,
      rate: null,
      note: '',
      ledger: {
        ...ledger,
        delta,
        baseline: { totalInterest: members.reduce((s, m) => s + ((m.ledger.baseline && m.ledger.baseline.totalInterest) || 0), 0) },
      },
      snapshot,
      paidPeriods,
      events,
      groupLoans: members,
      valid: true,
    })
  }
  return out
})

/** 列表行:组表头在上、成员缩进随其后,独立贷款保持原有顺序 */
const listRows = computed(() => {
  const rows = []
  const grouped = new Set()
  for (const g of groupViews.value) {
    rows.push({ type: 'group', view: g })
    for (const m of g.groupLoans) {
      rows.push({ type: 'loan', view: m, child: true })
      grouped.add(m.id)
    }
  }
  for (const v of cardViews.value) {
    if (!grouped.has(v.id)) rows.push({ type: 'loan', view: v })
  }
  return rows
})

const firstListKey = () => {
  const row = listRows.value[0]
  return row ? (row.type === 'group' ? row.view.id : String(row.view.id)) : ''
}

const current = computed(() => {
  if (!selectedKey.value) return null
  if (selectedKey.value.startsWith('g:')) return groupViews.value.find((g) => g.id === selectedKey.value) || null
  const id = Number(selectedKey.value)
  return cardViews.value.find((v) => v.id === id) || null
})

const groupNames = computed(() => {
  const names = new Set()
  for (const v of cardViews.value) {
    const g = (v.groupName || '').trim()
    if (g) names.add(g)
  }
  return [...names]
})

const ledgerError = computed(() => {
  const bad = cardViews.value.find((x) => !x.valid && x.ledger.error)
  return bad ? t('tools.loan.rec.' + (LEDGER_ERROR_KEYS[bad.ledger.error] || 'errBadEventValue')) : ''
})

const load = async () => {
  loading.value = true
  loadError.value = false
  try {
    loans.value = await loanApi.list()
    const keys = new Set(listRows.value.map((r) => (r.type === 'group' ? r.view.id : String(r.view.id))))
    if (!keys.has(selectedKey.value)) selectedKey.value = firstListKey()
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

watch(
  () => userStore.isLoggedIn,
  (on) => {
    if (on) load()
  },
  { immediate: true },
)

/** ==================== 弹窗桥接 ==================== */

const editorRef = ref(null)
const eventRef = ref(null)

const openEditor = (loan) => editorRef.value && editorRef.value.open(loan)
const openEvent = (type, ev, view, presetPeriod) => eventRef.value && eventRef.value.open(type, ev, view || current.value, presetPeriod)

const onEditorSaved = (id) => {
  if (id) selectedKey.value = String(id)
  return load()
}

const onEventClick = (ev) => {
  if (!canManage.value) return
  const target = current.value.isGroup ? (current.value.groupLoans || []).find((x) => x.id === ev.loanId) : current.value
  openEvent(ev.type, ev, target)
}

const onEventDelete = (ev) => {
  const target = current.value.isGroup ? (current.value.groupLoans || []).find((x) => x.id === ev.loanId) : current.value
  if (!target) return
  onDeleteEvent(target, ev)
}

const onDeleteEvent = async (loan, ev) => {
  await ElMessageBox.confirm(t('tools.loan.rec.deleteEventConfirm'), t('common.deleteConfirm'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  const events = (loan.events || []).filter((e) => e.id !== ev.id)
  await loanApi.update(loan.id, loanToPayload(loan, events))
  ElMessage.success(t('common.deleted'))
  await load()
}

const onDeleteLoan = async (loan) => {
  await ElMessageBox.confirm(t('tools.loan.rec.deleteLoanConfirm', { name: loan.name }), t('common.deleteConfirm'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  await loanApi.remove(loan.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

/** 没填事件时间的事件:重新拖拽只换生效期次(其余字段原样整体提交) */
const moveEvent = async (ev, period, view) => {
  const target = Math.min(Number(period) || 0, Number(view.months) || 0)
  if (target < 1 || target === Number(ev.effectivePeriod)) return
  const events = (view.events || []).map((e) => (e.id === ev.id ? { ...e, effectivePeriod: target } : e))
  await loanApi.update(view.id, loanToPayload(view, events))
  ElMessage.success(t('tools.loan.rec.eventMoved', { n: target }))
  await load()
}

const onPipelineOpenEvent = ({ type, ev, period }) => openEvent(type, ev, current.value, period)
const onPipelineMoveEvent = ({ ev, period }) => moveEvent(ev, period, current.value)
</script>

<style scoped>
/* 事件与未来配色(取自暖居调色板,晨暮各一套):利率调整 = 主题粉,未生效事件与下一期 = 鼠尾草绿,
 * 提前还款仍是陶土橙 —— 事件类型与期次状态一眼可分(不再借用 --color-accent,避免暖居下两处撞色) */
.lr-root {
  --lr-pink: #c9807a;
  --lr-pink-rgb: 201, 128, 122;
  --lr-sage: #7c8b6c;
  --lr-sage-rgb: 124, 139, 108;
  --lr-orange: #b06a3b;
  --lr-orange-rgb: 176, 106, 59;
}
html.dark .lr-root {
  --lr-pink: #e19a8c;
  --lr-pink-rgb: 225, 154, 140;
  --lr-sage: #8fa080;
  --lr-sage-rgb: 143, 160, 128;
}

.lr-alert {
  margin-bottom: 12px;
}
.lr-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
  gap: 16px;
  align-items: start;
}
.lr-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.lc-card {
  padding: 18px 20px;
}
.lr-empty-icon {
  color: var(--color-text-secondary, #7a6b5a);
}
.lr-select-hint {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 920px) {
  .lr-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
