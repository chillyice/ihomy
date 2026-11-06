<template>
  <el-dialog v-model="eventDlg.visible" :title="dialogTitle" width="420px" append-to-body>
    <div class="lc-field">
      <label>{{ $t('tools.loan.rec.triggerDateLabel') }}</label>
      <el-date-picker
        v-model="eventDlg.triggerDate"
        type="date"
        value-format="YYYY-MM-DD"
        style="width: 100%"
        @change="onTriggerDateChange"
      />
      <span class="lc-hint">{{ $t('tools.loan.rec.triggerDateHint') }}</span>
    </div>
    <div class="lc-field">
      <label>{{ $t('tools.loan.rec.eventPeriodLabel') }}</label>
      <el-input-number
        v-model="eventDlg.effectivePeriod"
        :min="1"
        :max="eventDlgMax"
        :step="1"
        :precision="0"
        :controls="false"
        style="width: 100%"
        @change="onPeriodManualChange"
      />
      <span class="lc-hint">{{ periodHint }}</span>
    </div>
    <div v-if="eventDlg.type === LOAN_EVENT.RATE_CHANGE" class="lc-field">
      <label
        >{{ $t('tools.loan.rec.newRateLabel') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label
      >
      <el-input-number v-model="eventDlg.rate" :min="0" :max="36" :step="0.05" :precision="4" :controls="false" style="width: 100%" />
    </div>
    <!-- 换还款账户:留空 = 从此不再自动记账(已记的流水不回改) -->
    <div v-else-if="eventDlg.type === LOAN_EVENT.ACCOUNT_CHANGE" class="lc-field">
      <label>{{ $t('tools.loan.rec.accountLabel') }}</label>
      <el-select v-model="eventDlg.accountId" clearable style="width: 100%" :placeholder="$t('tools.loan.rec.accountUnbound')">
        <el-option v-for="a in accounts" :key="a.id" :value="a.id" :label="a.name" />
      </el-select>
      <span class="lc-hint">{{ $t('tools.loan.rec.accountChangeHint') }}</span>
    </div>
    <template v-else>
      <div class="lc-field">
        <label
          >{{ $t('tools.loan.rec.prepayAmountInput') }}<span class="lc-unit">{{ $t('tools.loan.yuanUnit') }}</span></label
        >
        <el-input-number v-model="eventDlg.amount" :min="0" :step="10000" :precision="2" :controls="false" style="width: 100%" />
      </div>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.prepayStrategy') }}</label>
        <el-radio-group v-model="eventDlg.strategy" size="small">
          <el-radio-button value="SHORTEN">{{ $t('dict.loan_prepay_strategy.SHORTEN') }}</el-radio-button>
          <el-radio-button value="REDUCE">{{ $t('dict.loan_prepay_strategy.REDUCE') }}</el-radio-button>
        </el-radio-group>
        <span class="lc-hint">{{ $t('tools.loan.rec.prepayStrategyHint') }}</span>
      </div>
    </template>
    <div class="lc-field lc-field-last">
      <label>{{ $t('tools.loan.rec.formNote') }}</label>
      <el-input v-model="eventDlg.note" maxlength="100" />
    </div>
    <template #footer>
      <el-button @click="eventDlg.visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="eventDlg.saving" @click="onSaveEvent">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { loanApi } from '@/api'
import { loanAccountOfPeriod, periodDate, periodsPaid, LOAN_EVENT } from '@/utils/loan'
import { toNum, loanToPayload } from './loanShared'

defineProps({
  /** 可绑定的记账账户(无账本权限时为空数组,换账户入口自然没有可选账户) */
  accounts: { type: Array, default: () => [] },
})

const emit = defineEmits(['saved'])

const { t } = useI18n()

const eventDlg = reactive({
  visible: false,
  saving: false,
  type: LOAN_EVENT.RATE_CHANGE,
  loanId: 0,
  editingId: 0,
  triggerDate: '',
  effectivePeriod: 1,
  rate: 3.1,
  amount: 100000,
  strategy: 'SHORTEN',
  accountId: null,
  note: '',
})

/** 三种事件的标题(沿用原按钮文案,编辑/新建一致) */
const dialogTitle = computed(() => {
  if (eventDlg.type === LOAN_EVENT.ACCOUNT_CHANGE) return t('tools.loan.rec.accountChangeEntry')
  return eventDlg.type === LOAN_EVENT.RATE_CHANGE ? t('tools.loan.rec.addRateChange') : t('tools.loan.rec.addPrepay')
})

/** 生效期次说明:换账户不参与计息,文案与利率/提前还款分开 */
const periodHint = computed(() =>
  eventDlg.type === LOAN_EVENT.ACCOUNT_CHANGE ? t('tools.loan.rec.accountEventHint') : t('tools.loan.rec.eventPeriodHint'),
)

/** 事件作用的目标贷款(组视图下事件归属它所属的那笔) */
const targetView = ref(null)

const eventDlgMax = computed(() => (targetView.value ? Number(targetView.value.months) : 1200))
const eventTarget = () => targetView.value

/**
 * 打开事件弹窗(新增或编辑)。
 * @param {string} type 事件类型
 * @param {object|null} ev 已有事件(编辑)或 null(新建)
 * @param {object} loanView 事件所属贷款视图
 * @param {number} [presetPeriod] 拖拽落点期次:新建/拖动已有事件时生效期次取它,事件时间按它重推(可改可清);
 *   不传(点开编辑)则回显事件自己存的期次与事件时间。
 */
const open = (type, ev, loanView, presetPeriod) => {
  const target = loanView
  if (!target || target.isGroup) return
  const paid = target.paidPeriods || 0
  const maxPeriod = Math.max(1, Number(target.months) || 1)
  const period = presetPeriod
    ? Math.max(1, Math.min(presetPeriod, maxPeriod))
    : ev
      ? Number(ev.effectivePeriod)
      : Math.max(1, Math.min(paid || 1, maxPeriod))
  targetView.value = target
  eventDlg.type = type
  eventDlg.loanId = target.id
  eventDlg.editingId = ev ? ev.id : 0
  eventDlg.effectivePeriod = period
  eventDlg.triggerDate = presetPeriod ? periodDate(target.firstPayDate, period) : ev && ev.triggerDate ? ev.triggerDate : ''
  eventDlg.rate = ev ? toNum(ev.rate) : toNum(target.rate)
  eventDlg.amount = ev ? toNum(ev.amount) : 100000
  eventDlg.strategy = ev ? ev.strategy || 'SHORTEN' : 'SHORTEN'
  // 换账户:编辑回显自己选的账户;新建默认填「该期当前扣款账户」,用户只需改要换去的那个
  eventDlg.accountId = type !== LOAN_EVENT.ACCOUNT_CHANGE ? null : ev ? (ev.accountId ?? null) : loanAccountOfPeriod(target, period)
  eventDlg.note = ev ? ev.note || '' : ''
  eventDlg.visible = true
}

/** 填触发时间 → 反推生效期次(该日期时已还到第几期,事件在其后) */
const deriving = ref(false)
const onTriggerDateChange = (date) => {
  if (!date || deriving.value) return
  const target = eventTarget()
  if (!target) return
  if (!target.firstPayDate) {
    eventDlg.triggerDate = ''
    ElMessage.warning(t('tools.loan.rec.triggerNoFirstPay'))
    return
  }
  deriving.value = true
  const derived = Math.max(1, Math.min(periodsPaid(target.firstPayDate, date), eventDlgMax.value))
  eventDlg.effectivePeriod = derived
  deriving.value = false
}

/** 手改生效期次 → 触发时间让位清空(以手填为准) */
const onPeriodManualChange = () => {
  if (deriving.value) return
  if (eventDlg.triggerDate) eventDlg.triggerDate = ''
}

const onSaveEvent = async () => {
  const loan = eventTarget()
  if (!loan) return
  if (eventDlg.type === LOAN_EVENT.PREPAY && !(Number(eventDlg.amount) > 0)) {
    ElMessage.warning(t('tools.loan.rec.errBadEventValue'))
    return
  }
  const fresh = {
    type: eventDlg.type,
    effectivePeriod: Number(eventDlg.effectivePeriod),
    // 事件时间(触发日)落库:留着下次重新拖拽时决定要不要重新确认日期
    triggerDate: eventDlg.triggerDate || null,
    rate: eventDlg.type === LOAN_EVENT.RATE_CHANGE ? Number(eventDlg.rate) : null,
    amount: eventDlg.type === LOAN_EVENT.PREPAY ? Number(eventDlg.amount) : null,
    strategy: eventDlg.type === LOAN_EVENT.PREPAY ? eventDlg.strategy : null,
    // 换账户留空 = 从此不再自动记账(已记的流水不回改)
    accountId: eventDlg.type === LOAN_EVENT.ACCOUNT_CHANGE ? (eventDlg.accountId ?? null) : null,
    note: eventDlg.note || null,
  }
  const events = (loan.events || [])
    .filter((e) => (eventDlg.editingId ? e.id !== eventDlg.editingId : true))
    .concat([eventDlg.editingId ? { ...fresh, id: eventDlg.editingId } : fresh])
    .sort((a, b) => a.effectivePeriod - b.effectivePeriod)
  eventDlg.saving = true
  try {
    await loanApi.update(loan.id, loanToPayload(loan, events))
    eventDlg.visible = false
    ElMessage.success(t('common.saved'))
    emit('saved')
  } finally {
    eventDlg.saving = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.lc-field {
  margin-bottom: 14px;
}
.lc-field-last {
  margin-bottom: 0;
}
.lc-field > label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary, #7a6b5a);
  margin-bottom: 6px;
}
.lc-unit {
  font-weight: 400;
}
.lc-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #7a6b5a);
}
</style>
