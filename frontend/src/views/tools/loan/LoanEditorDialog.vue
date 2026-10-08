<template>
  <el-dialog
    v-model="editor.visible"
    :title="editor.form.id ? $t('tools.loan.rec.editorTitleEdit') : $t('tools.loan.rec.editorTitleNew')"
    width="480px"
    append-to-body
  >
    <div class="lc-field">
      <label>{{ $t('tools.loan.rec.formName') }}</label>
      <el-input v-model="editor.form.name" :placeholder="$t('tools.loan.rec.formNamePlaceholder')" maxlength="60" />
    </div>
    <div class="lr-form-grid">
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.formChannel') }}</label>
        <el-select v-model="editor.form.channel" style="width: 100%">
          <el-option value="COMMERCIAL" :label="$t('dict.loan_channel.COMMERCIAL')" />
          <el-option value="FUND" :label="$t('dict.loan_channel.FUND')" />
          <el-option value="OTHER" :label="$t('dict.loan_channel.OTHER')" />
        </el-select>
      </div>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.formMethod') }}</label>
        <el-select v-model="editor.form.method" style="width: 100%">
          <el-option value="EQUAL_INSTALLMENT" :label="$t('dict.loan_method.EQUAL_INSTALLMENT')" />
          <el-option value="EQUAL_PRINCIPAL" :label="$t('dict.loan_method.EQUAL_PRINCIPAL')" />
        </el-select>
      </div>
    </div>
    <div class="lr-form-grid">
      <div class="lc-field">
        <label
          >{{ $t('tools.loan.rec.formAmount') }}<span class="lc-unit">{{ $t('tools.loan.wanUnit') }}</span></label
        >
        <el-input-number
          v-model="editor.form.amountWan"
          :min="0.0001"
          :max="99999"
          :step="10"
          :precision="2"
          :controls="false"
          style="width: 100%"
        />
      </div>
      <div class="lc-field">
        <label
          >{{ $t('tools.loan.rec.formYears') }}<span class="lc-unit">{{ $t('tools.loan.yearUnit') }}</span></label
        >
        <el-input-number v-model="editor.form.years" :min="1" :max="40" :step="1" :precision="0" :controls="false" style="width: 100%" />
      </div>
    </div>
    <div class="lc-field">
      <label
        >{{ $t('tools.loan.rec.formRate') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label
      >
      <el-input-number v-model="editor.form.rate" :min="0" :max="36" :step="0.05" :precision="4" :controls="false" style="width: 100%" />
    </div>
    <div class="lc-field">
      <label>{{ $t('tools.loan.rec.groupName') }}</label>
      <el-input v-model="editor.form.groupName" maxlength="50" :placeholder="$t('tools.loan.rec.groupNamePlaceholder')" />
      <span class="lc-hint">{{ $t('tools.loan.rec.groupNameHint') }}</span>
      <span v-if="groupNames.length" class="lc-hint"
        >{{ $t('tools.loan.rec.groupNameExisting') }}{{ groupNames.join($t('tools.loan.rec.groupNameSep')) }}</span
      >
    </div>
    <div class="lr-form-grid">
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.formLoanDate') }}</label>
        <el-date-picker v-model="editor.form.loanDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </div>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.formFirstPayDate') }}</label>
        <el-date-picker v-model="editor.form.firstPayDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </div>
    </div>
    <div class="lc-field lc-field-last">
      <label
        >{{ $t('tools.loan.rec.formFirstPayment') }}<span class="lc-unit">{{ $t('tools.loan.yuanUnit') }}</span></label
      >
      <el-input-number v-model="editor.form.firstPayment" :min="0" :step="100" :precision="2" :controls="false" style="width: 100%" />
      <span v-if="editorPreviewDays" class="lc-hint">
        {{
          editorPreviewPayment > 0
            ? $t('tools.loan.rec.firstPreviewHint', { d: editorPreviewDays, v: money(editorPreviewPayment) })
            : $t('tools.loan.rec.firstDaysOnlyHint', { d: editorPreviewDays })
        }}
      </span>
      <span v-else class="lc-hint">{{ $t('tools.loan.rec.firstPaymentOptionalHint') }}</span>
    </div>
    <div class="lc-field lc-field-last">
      <label>{{ $t('tools.loan.rec.formNote') }}</label>
      <el-input v-model="editor.form.note" maxlength="200" />
    </div>
    <template #footer>
      <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="editor.saving" @click="onSaveLoan">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { loanApi } from '@/api'
import { firstPaymentPreview, daysBetween, formatYuan, REPAY_METHOD } from '@/utils/loan'
import { toNum } from './loanShared'

defineProps({
  groupNames: { type: Array, default: () => [] },
})

const emit = defineEmits(['saved'])

const { t } = useI18n()

const money = (v, digits = 2) => formatYuan(v, digits)

const editor = reactive({
  visible: false,
  saving: false,
  form: {},
})

/** 编辑器预览:填了放款日 + 首期还款日即回显首期天数与参考还款额 */
const editorPreviewDays = computed(() => {
  const f = editor.form
  const days = daysBetween(f.loanDate, f.firstPayDate)
  return days > 0 ? days : 0
})
const editorPreviewPayment = computed(() => {
  const f = editor.form
  if (!editorPreviewDays.value || !f.amountWan) return 0
  return firstPaymentPreview({
    amount: (Number(f.amountWan) || 0) * 10000,
    months: (Number(f.years) || 0) * 12,
    method: f.method,
    rate: f.rate,
    firstDays: editorPreviewDays.value,
  })
})

const open = (loan) => {
  editor.form = loan
    ? {
        id: loan.id,
        name: loan.name,
        channel: loan.channel,
        method: loan.method,
        amountWan: toNum(loan.amount) / 10000,
        years: Math.max(1, Math.round(Number(loan.months) / 12)),
        rate: toNum(loan.rate),
        groupName: loan.groupName || '',
        loanDate: loan.loanDate || null,
        firstPayDate: loan.firstPayDate || null,
        firstPayment: toNum(loan.firstPayment),
        note: loan.note || '',
        events: (loan.events || []).map((e) => ({ ...e })),
      }
    : {
        id: 0,
        name: '',
        channel: 'COMMERCIAL',
        method: REPAY_METHOD.EQUAL_INSTALLMENT,
        amountWan: 100,
        years: 30,
        rate: 3.1,
        groupName: '',
        loanDate: null,
        firstPayDate: null,
        firstPayment: null,
        note: '',
        events: [],
      }
  editor.visible = true
}

const onSaveLoan = async () => {
  const f = editor.form
  if (!f.name || !f.name.trim()) {
    ElMessage.warning(t('tools.loan.rec.errNoName'))
    return
  }
  editor.saving = true
  try {
    const payload = {
      name: f.name.trim(),
      channel: f.channel,
      method: f.method,
      amount: Math.round((Number(f.amountWan) || 0) * 10000 * 100) / 100,
      months: (Number(f.years) || 0) * 12,
      loanDate: f.loanDate || null,
      firstPayDate: f.firstPayDate || null,
      firstPayment: Number(f.firstPayment) > 0 ? Number(f.firstPayment) : null,
      rate: Number(f.rate) || 0,
      groupName: (f.groupName || '').trim() || null,
      note: f.note || null,
      events: (f.events || []).map((e) => ({
        type: e.type,
        effectivePeriod: Number(e.effectivePeriod),
        triggerDate: e.triggerDate || null,
        rate: toNum(e.rate),
        amount: toNum(e.amount),
        strategy: e.strategy || null,
        note: e.note || null,
      })),
    }
    let newId = 0
    if (f.id) await loanApi.update(f.id, payload)
    else newId = await loanApi.create(payload)
    editor.visible = false
    ElMessage.success(t('common.saved'))
    emit('saved', newId)
  } finally {
    editor.saving = false
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
.lr-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}
@media (max-width: 920px) {
  .lr-form-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
