<template>
  <el-dialog
    v-model="eventDlg.visible"
    :title="eventDlg.type === LOAN_EVENT.RATE_CHANGE ? $t('tools.loan.rec.addRateChange') : $t('tools.loan.rec.addPrepay')"
    width="420px"
    append-to-body
  >
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
      <span class="lc-hint">{{ $t('tools.loan.rec.eventPeriodHint') }}</span>
    </div>
    <div v-if="eventDlg.type === LOAN_EVENT.RATE_CHANGE" class="lc-field">
      <label
        >{{ $t('tools.loan.rec.newRateLabel') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label
      >
      <el-input-number v-model="eventDlg.rate" :min="0" :max="36" :step="0.05" :precision="4" :controls="false" style="width: 100%" />
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
import { periodDate, periodsPaid, LOAN_EVENT } from '@/utils/loan'
import { toNum, loanToPayload } from './loanShared'

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
  note: '',
})

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
