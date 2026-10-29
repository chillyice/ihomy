<!-- 赎回弹窗:到账金额与实际收益自动算,转回本金 / 收益入账两个勾选默认勾上,已记过的不再提供 -->
<template>
  <el-dialog v-model="dialogVisible" append-to-body width="520px" :title="$t('book.finance.redeemTitle', { name: product?.name || '' })">
    <div class="fr-sum">
      <div class="fr-sum-cell">
        <span class="fr-sum-key">{{ $t('book.finance.principal') }}</span>
        <span class="fr-sum-val">¥{{ formatYuan(principal) }}</span>
      </div>
      <div class="fr-sum-cell">
        <span class="fr-sum-key">{{ $t('book.finance.accruedInterest') }}</span>
        <span class="fr-sum-val is-income">{{ accrued === null ? $t('book.finance.floating') : `+¥${formatYuan(accrued)}` }}</span>
      </div>
    </div>

    <el-form :model="form" label-position="top">
      <div class="fr-row">
        <el-form-item :label="$t('book.finance.redeemDate')" required class="fr-grow">
          <el-date-picker v-model="form.redeemDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('book.finance.redeemAmount')" required class="fr-grow">
          <el-input-number v-model="form.redeemAmount" :min="0" :precision="2" :step="100" :controls="false" style="width: 100%" />
        </el-form-item>
      </div>

      <el-form-item :label="$t('book.finance.actualInterest')">
        <el-input-number v-model="form.actualInterest" :precision="2" :step="10" :controls="false" style="width: 100%" />
        <div class="fr-hint">{{ $t('book.finance.actualInterestHint') }}</div>
      </el-form-item>

      <!-- 没有关联账户时无法自动记账;已记过的单项不再出现,避免后端拒绝整笔提交 -->
      <div v-if="!product?.accountId" class="fr-note">{{ $t('book.finance.redeemNoAccount') }}</div>
      <template v-else>
        <el-form-item v-if="!product?.redeemBooked">
          <el-checkbox v-model="form.bookRedeemTransfer">{{ $t('book.finance.redeemTransfer') }}</el-checkbox>
        </el-form-item>
        <div v-else class="fr-note">
          <el-tag size="small" type="success" effect="plain">{{ $t('book.finance.redeemAlreadyBooked') }}</el-tag>
        </div>

        <el-form-item v-if="!product?.interestBooked">
          <el-checkbox v-model="form.bookInterest">{{ $t('book.finance.redeemInterest') }}</el-checkbox>
          <div v-if="form.actualInterest < 0" class="fr-hint">{{ $t('book.finance.redeemInterestExpense') }}</div>
        </el-form-item>
        <div v-else class="fr-note">
          <el-tag size="small" type="success" effect="plain">{{ $t('book.finance.interestBooked') }}</el-tag>
        </div>
      </template>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">{{ $t('book.finance.redeemConfirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 赎回:只提交赎回相关字段(name 仅为通过表单校验,后端按 patch 语义其余字段不变)
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

import { financeApi } from '@/api'
import { accruedInterest } from '@/utils/finance'
import { formatYuan, round2 } from '@/utils/money'
import { todayStr } from '@/utils/date'

const props = defineProps({
  visible: { type: Boolean, default: false },
  product: { type: Object, default: null },
})
const emit = defineEmits(['update:visible', 'saved'])

const { t } = useI18n()
const saving = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const principal = computed(() => {
  const p = Number(props.product?.principal)
  return Number.isFinite(p) ? p : 0
})
// 已计提收益(截至今天);浮动收益产品为 null,提示里显示「浮动收益」
const accrued = computed(() => accruedInterest(props.product || {}))

const form = reactive({ redeemDate: todayStr(), redeemAmount: 0, actualInterest: 0, bookRedeemTransfer: true, bookInterest: true })

// 默认到账 = 本金 + 已计提;实际收益随后按「到账 − 本金」带出
watch(
  () => props.visible,
  (v) => {
    if (!v) return
    const acc = accrued.value
    const amount = round2(principal.value + (acc === null ? 0 : acc))
    form.redeemDate = todayStr()
    form.redeemAmount = amount
    form.actualInterest = round2(amount - principal.value)
    // 没关联账户 / 已记过账的项默认不勾(勾了也会被后端拒绝)
    form.bookRedeemTransfer = !!props.product?.accountId && !props.product?.redeemBooked
    form.bookInterest = !!props.product?.accountId && !props.product?.interestBooked
  },
)

// 改到账金额时同步刷新收益;用户仍可手动改写收益(分红、费用等)
watch(
  () => form.redeemAmount,
  (val) => {
    if (val === null || val === undefined || val === '') return
    form.actualInterest = round2(Number(val) - principal.value)
  },
)

const onSave = async () => {
  if (!form.redeemDate) return ElMessage.warning(t('book.finance.fillRedeemDate'))
  if (!(Number(form.redeemAmount) >= 0)) return ElMessage.warning(t('book.finance.fillRedeemAmount'))

  const data = {
    // 后端编辑接口 name 必填,这里原样回传
    name: props.product.name,
    status: 'REDEEMED',
    redeemDate: form.redeemDate,
    redeemAmount: Number(form.redeemAmount),
    actualInterest: Number(form.actualInterest) || 0,
    bookRedeemTransfer: !!form.bookRedeemTransfer,
    bookInterest: !!form.bookInterest,
  }

  saving.value = true
  try {
    await financeApi.update(props.product.id, data)
    ElMessage.success(t('common.saveSuccess'))
    dialogVisible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
/* 仅排版:金额/表格数字统一等宽数字,窄屏两列收成一列 */
.fr-sum {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  padding: 10px 14px;
  margin-bottom: 14px;
  border-radius: 12px;
  background: var(--color-card-2);
  border: 1px solid var(--color-border);
}
.fr-sum-cell {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.fr-sum-key {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.fr-sum-val {
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.fr-sum-val.is-income {
  color: #67c23a;
}
.fr-row {
  display: flex;
  gap: 12px;
}
.fr-grow {
  flex: 1;
  min-width: 0;
}
.fr-hint {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  line-height: 1.5;
  margin-top: 2px;
}
.fr-note {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
  line-height: 1.6;
  margin-bottom: 12px;
}

@media (max-width: 768px) {
  .fr-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
