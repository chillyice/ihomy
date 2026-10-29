<!-- 理财新增/编辑弹窗:表单字段 + 实时试算条(预期利息 / 到期本息 / 折合年化)+ 购买转出勾选 -->
<template>
  <el-dialog v-model="dialogVisible" append-to-body width="640px" :title="isEdit ? $t('book.finance.edit') : $t('book.finance.add')">
    <el-form :model="form" label-position="top">
      <div class="fe-row">
        <el-form-item :label="$t('book.finance.name')" required class="fe-grow">
          <el-input v-model="form.name" maxlength="100" :placeholder="$t('book.finance.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('book.finance.typeLabel')" class="fe-side">
          <el-select v-model="form.productType" style="width: 100%">
            <el-option v-for="tp in FINANCE_TYPES" :key="tp" :label="financeTypeLabel(tp, t)" :value="tp" />
          </el-select>
        </el-form-item>
      </div>

      <div class="fe-row">
        <el-form-item :label="$t('book.finance.institution')" class="fe-grow">
          <el-input v-model="form.institution" maxlength="60" :placeholder="$t('book.finance.institutionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('book.finance.account')" class="fe-side">
          <el-select v-model="form.accountId" clearable :placeholder="$t('book.finance.accountPlaceholder')" style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
      </div>

      <div class="fe-row">
        <el-form-item :label="$t('book.finance.principal')" required class="fe-grow">
          <el-input-number v-model="form.principal" :min="0" :step="1000" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('book.finance.annualRate')" class="fe-side">
          <el-input-number
            v-model="form.annualRate"
            :min="0"
            :max="99.9999"
            :step="0.1"
            :precision="4"
            :controls="false"
            :placeholder="$t('book.finance.floating')"
            style="width: 100%"
          />
        </el-form-item>
      </div>

      <div class="fe-row">
        <el-form-item :label="$t('book.finance.interestMode')" class="fe-grow">
          <el-select v-model="form.interestMode" style="width: 100%">
            <el-option v-for="m in INTEREST_MODES" :key="m" :label="interestModeLabel(m, t)" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('book.finance.termMonths')" class="fe-side">
          <el-input-number
            v-model="form.termMonths"
            :min="0"
            :max="1200"
            :precision="0"
            :step="1"
            :controls="false"
            :placeholder="$t('book.finance.termMonthsPlaceholder')"
            style="width: 100%"
          />
        </el-form-item>
        <!-- 基准天数只对按日折算/复利有意义,按月折算不需要 -->
        <el-form-item v-if="form.interestMode !== 'BY_MONTH'" :label="$t('book.finance.dayCount')" class="fe-side">
          <el-select v-model="form.dayCount" style="width: 100%">
            <el-option v-for="d in DAY_COUNTS" :key="d" :label="$t('book.finance.dayCountValue', { n: d })" :value="d" />
          </el-select>
        </el-form-item>
      </div>

      <div class="fe-row">
        <el-form-item :label="$t('book.finance.startDate')" required class="fe-grow">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('book.finance.endDate')" class="fe-side">
          <el-date-picker
            v-model="form.endDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="$t('book.finance.endDatePlaceholder')"
            :disabled-date="beforeStart"
            style="width: 100%"
          />
          <div v-if="derivedEnd" class="fe-hint">{{ $t('book.finance.endDateDerived', { date: derivedEnd }) }}</div>
        </el-form-item>
      </div>

      <div class="fe-row">
        <el-form-item :label="$t('book.finance.riskLevel')" class="fe-side">
          <el-select v-model="form.riskLevel" clearable :placeholder="$t('book.finance.optional')" style="width: 100%">
            <el-option v-for="r in RISK_LEVELS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('book.finance.note')" class="fe-grow">
          <el-input v-model="form.note" maxlength="500" :placeholder="$t('book.finance.notePlaceholder')" />
        </el-form-item>
      </div>

      <!-- 购买转出只在新增时可选:账户余额减少本金,账本里记一笔转出(不算支出) -->
      <el-form-item v-if="!isEdit">
        <el-checkbox v-model="form.bookInvestTransfer" :disabled="!form.accountId">
          {{ $t('book.finance.investBook') }}
        </el-checkbox>
        <div class="fe-hint">{{ $t('book.finance.investHint') }}</div>
      </el-form-item>

      <!-- 实时试算:口径与卡片、后端同一套(utils/finance.js) -->
      <div class="fe-trial">
        <template v-if="trial">
          <div class="fe-trial-cell">
            <span class="fe-trial-key">{{ $t('book.finance.trialInterest') }}</span>
            <span class="fe-trial-val">¥{{ formatYuan(trial.interest) }}</span>
          </div>
          <div class="fe-trial-cell">
            <span class="fe-trial-key">{{ $t('book.finance.trialMaturity') }}</span>
            <span class="fe-trial-val">¥{{ formatYuan(trial.maturity) }}</span>
          </div>
          <div class="fe-trial-cell">
            <span class="fe-trial-key">{{ $t('book.finance.trialAnnualized') }}</span>
            <span class="fe-trial-val">{{ trial.annualized === null ? $t('book.finance.floating') : `${trial.annualized}%` }}</span>
          </div>
        </template>
        <span v-else class="fe-trial-empty">{{ $t('book.finance.trialEmpty') }}</span>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 表单与后端 FinanceDTO 对齐:文本清空提交空串、账户清空提交 0(后端按「不挂账户」处理),
// 年化留空 = 浮动收益;到期日留空由后端按「起息日 + 期限」补算。
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

import { financeApi } from '@/api'
import {
  DAY_COUNTS,
  FINANCE_TYPES,
  INTEREST_MODES,
  RISK_LEVELS,
  dayCountOf,
  endDateOf,
  expectedInterest,
  financeTypeLabel,
  interestModeLabel,
  maturityAmount,
  totalDays,
} from '@/utils/finance'
import { todayStr } from '@/utils/date'
import { formatYuan } from '@/utils/money'

const props = defineProps({
  visible: { type: Boolean, default: false },
  // 空 = 新增,有值 = 编辑该笔持仓
  product: { type: Object, default: null },
  accounts: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'saved'])

const { t } = useI18n()
const saving = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const blank = () => ({
  id: null,
  name: '',
  productType: 'DEPOSIT',
  institution: '',
  accountId: null,
  principal: null,
  annualRate: null,
  interestMode: 'BY_MONTH',
  dayCount: 365,
  termMonths: null,
  startDate: todayStr(),
  endDate: null,
  riskLevel: '',
  note: '',
  bookInvestTransfer: false,
})
const form = reactive(blank())
const isEdit = computed(() => !!form.id)

const reset = () => {
  Object.assign(form, blank())
  const p = props.product
  if (!p) return
  Object.assign(form, {
    id: p.id,
    name: p.name || '',
    productType: p.productType || 'DEPOSIT',
    institution: p.institution || '',
    accountId: p.accountId ?? null,
    principal: p.principal === null || p.principal === undefined ? null : Number(p.principal),
    annualRate: p.annualRate === null || p.annualRate === undefined ? null : Number(p.annualRate),
    interestMode: p.interestMode || 'BY_MONTH',
    dayCount: p.dayCount === 360 ? 360 : 365,
    termMonths: p.termMonths ?? null,
    startDate: p.startDate || todayStr(),
    endDate: p.endDate || null,
    riskLevel: p.riskLevel || '',
    note: p.note || '',
  })
}

watch(
  () => props.visible,
  (v) => {
    if (v) reset()
  },
)

// 新增时选了账户就默认勾上「购买转出」(从空到有才自动勾,避免覆盖手动取消)
watch(
  () => form.accountId,
  (val, old) => {
    if (form.id) return
    if (!val) form.bookInvestTransfer = false
    else if (!old) form.bookInvestTransfer = true
  },
)

// 到期日不能早于起息日
const beforeStart = (d) => !!form.startDate && todayStr(d) < form.startDate

// 试算:本金/年化/日期不全时返回 null,界面给引导文案而不是 NaN
const trial = computed(() => {
  const principal = Number(form.principal)
  if (!(principal > 0)) return null
  const interest = expectedInterest(form)
  if (interest === null) return null
  const maturity = maturityAmount(form)
  const days = totalDays(form)
  const annualized = days > 0 ? Math.round((interest / principal / (days / dayCountOf(form))) * 10000) / 100 : null
  return { interest, maturity: maturity ?? principal, annualized }
})

// 到期日展示在提示里(留空时按期限补算),避免用户以为是活期
const derivedEnd = computed(() => (form.endDate ? null : endDateOf(form)))

const onSave = async () => {
  if (!form.name?.trim()) return ElMessage.warning(t('book.finance.fillName'))
  if (!(Number(form.principal) > 0)) return ElMessage.warning(t('book.finance.fillPrincipal'))
  if (!form.startDate) return ElMessage.warning(t('book.finance.fillStartDate'))

  const data = {
    name: form.name.trim(),
    productType: form.productType,
    institution: form.institution || '',
    // 0 = 不挂账户(后端把 <=0 归一成 null);null 在编辑语义里是「不修改」
    accountId: form.accountId ?? 0,
    principal: Number(form.principal),
    annualRate: form.annualRate === null || form.annualRate === '' ? null : Number(form.annualRate),
    interestMode: form.interestMode,
    dayCount: form.dayCount,
    // 0 = 活期(清空期限);null 在编辑语义里是「不修改」
    termMonths: form.termMonths ?? 0,
    startDate: form.startDate,
    endDate: form.endDate || null,
    riskLevel: form.riskLevel || '',
    note: form.note || '',
  }
  if (!isEdit.value) data.bookInvestTransfer = !!form.bookInvestTransfer && !!form.accountId

  saving.value = true
  try {
    if (isEdit.value) await financeApi.update(form.id, data)
    else await financeApi.create(data)
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
/* 弹窗内两列表单:桌面并排,窄屏单列;样式只做排版,EP 圆角配色走全局 */
.fe-row {
  display: flex;
  gap: 12px;
}
.fe-grow {
  flex: 1;
  min-width: 0;
}
.fe-side {
  flex: 0 0 210px;
  min-width: 0;
}
.fe-hint {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  line-height: 1.5;
  margin-top: -6px;
}
.fe-trial {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
  padding: 10px 14px;
  border-radius: 12px;
  border: 1px dashed var(--color-border);
  background: var(--color-card-2);
  font-variant-numeric: tabular-nums;
}
.fe-trial-cell {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.fe-trial-key {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.fe-trial-val {
  font-size: 14px;
  font-weight: 600;
}
.fe-trial-empty {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}

@media (max-width: 768px) {
  .fe-row {
    flex-direction: column;
    gap: 0;
  }
  .fe-side {
    flex: 1 1 auto;
  }
  .fe-trial {
    gap: 10px;
  }
}
</style>
