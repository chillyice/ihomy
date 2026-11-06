<!-- 周期账目弹窗:维护「每月房租/工资」这类固定收支模板,到点自动生成普通流水;可暂停/恢复/删除 -->
<template>
  <el-dialog v-model="dialogVisible" append-to-body width="min(720px, 94vw)" :title="$t('book.recurring.manageTitle')">
    <div class="rc-bar">
      <span class="rc-count">{{ $t('book.recurring.count', { n: rows.length }) }}</span>
      <div class="rc-actions">
        <el-button size="small" :loading="running" @click="onRun">{{ $t('book.recurring.run') }}</el-button>
        <el-button type="primary" size="small" @click="openEditor()">{{ $t('book.recurring.add') }}</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <el-table v-if="rows.length" :data="rows" size="small" max-height="420">
        <el-table-column :label="$t('book.recurring.category')" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.category }}</template>
        </el-table-column>
        <el-table-column :label="$t('book.recurring.amount')" width="110" align="right">
          <template #default="{ row }">
            <span :class="row.type === 'INCOME' ? 'is-income' : 'is-expense'"
              >{{ row.type === 'INCOME' ? '+' : '-' }}{{ money(row.amount) }}</span
            >
          </template>
        </el-table-column>
        <el-table-column :label="$t('book.recurring.periodLabel')" width="90">
          <template #default="{ row }">{{ periodLabel(row.period) }}</template>
        </el-table-column>
        <el-table-column :label="$t('book.recurring.range')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ rangeText(row) }}</template>
        </el-table-column>
        <el-table-column :label="$t('book.recurring.nextDate')" width="110">
          <template #default="{ row }">
            <span v-if="row.enabled && row.nextDate">{{ row.nextDate }}</span>
            <span v-else class="rc-muted">{{ $t('book.recurring.paused') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text @click="openEditor(row)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" text @click="onToggle(row)">{{
              row.enabled ? $t('book.recurring.pause') : $t('book.recurring.resume')
            }}</el-button>
            <el-button size="small" text type="danger" @click="onDel(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else :description="$t('book.recurring.empty')">
        <el-button type="primary" @click="openEditor()">{{ $t('book.recurring.addFirst') }}</el-button>
      </el-empty>
    </div>

    <div class="rc-note">{{ $t('book.recurring.note') }}</div>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ $t('common.close') }}</el-button>
    </template>

    <!-- 新增/编辑模板 -->
    <el-dialog
      v-model="editor.visible"
      append-to-body
      width="min(460px, 94vw)"
      :title="editor.form.id ? $t('book.recurring.edit') : $t('book.recurring.add')"
    >
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('book.typeLabel')">
          <el-radio-group v-model="editor.form.type">
            <el-radio value="EXPENSE">{{ $t('book.type.EXPENSE') }}</el-radio>
            <el-radio value="INCOME">{{ $t('book.type.INCOME') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="form-row">
          <el-form-item :label="$t('book.amount')" style="flex: 1">
            <el-input-number v-model="editor.form.amount" :min="0.01" :precision="2" :step="100" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="$t('book.recurring.periodLabel')" style="flex: 1">
            <el-select v-model="editor.form.period" style="width: 100%">
              <el-option v-for="p in PERIODS" :key="p" :label="periodLabel(p)" :value="p" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="$t('book.recurring.startDate')" style="flex: 1">
            <el-date-picker v-model="editor.form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="$t('book.recurring.endDate')" style="flex: 1">
            <el-date-picker v-model="editor.form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item :label="$t('book.account.field')">
          <el-select v-model="editor.form.accountId" clearable :placeholder="$t('book.account.optional')" style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('book.category')">
          <el-select
            v-model="editor.form.category"
            filterable
            allow-create
            default-first-option
            :placeholder="$t('book.categoryPlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('book.remark')">
          <el-input v-model="editor.form.remark" maxlength="200" :placeholder="$t('book.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
// 周期模板管理:列表 + 新增/编辑 + 暂停恢复 + 删除 + 立即生成(与服务端每日任务同一逻辑,幂等)
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

import { bookApi } from '@/api'
import { BOOK_CATEGORIES } from '@/utils/bookLedger'

const props = defineProps({
  visible: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'saved'])

const { t } = useI18n()
const PERIODS = ['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY']
const CATEGORIES = BOOK_CATEGORIES

const loading = ref(false)
const saving = ref(false)
const running = ref(false)
const rows = ref([])
const accounts = ref([])
const editor = reactive({ visible: false, form: {} })

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const money = (v) => Number(v ?? 0).toFixed(2)
const periodLabel = (p) => t(`book.recurring.period.${p}`)
const rangeText = (row) => (row.endDate ? `${row.startDate} ~ ${row.endDate}` : `${row.startDate}${t('book.recurring.ongoing')}`)

const load = async () => {
  loading.value = true
  try {
    const [list, acc] = await Promise.all([bookApi.recurring(), bookApi.accounts()])
    rows.value = Array.isArray(list) ? list : []
    accounts.value = Array.isArray(acc) ? acc : []
  } finally {
    loading.value = false
  }
}

const openEditor = (row) => {
  editor.visible = true
  editor.form = row
    ? {
        id: row.id,
        type: row.type,
        amount: Number(row.amount ?? 0),
        category: row.category || '其他',
        accountId: row.accountId ?? null,
        remark: row.remark || '',
        period: row.period || 'MONTHLY',
        startDate: row.startDate,
        endDate: row.endDate || null,
        enabled: row.enabled ?? 1,
      }
    : {
        id: null,
        type: 'EXPENSE',
        amount: 0,
        category: '其他',
        accountId: null,
        remark: '',
        period: 'MONTHLY',
        startDate: new Date().toISOString().slice(0, 10),
        endDate: null,
        enabled: 1,
      }
}

const onSave = async () => {
  const f = editor.form
  if (!f.amount || f.amount <= 0) return ElMessage.warning(t('book.fillAmount'))
  if (!f.startDate) return ElMessage.warning(t('book.recurring.fillStart'))
  if (f.endDate && f.endDate < f.startDate) return ElMessage.warning(t('book.recurring.endBeforeStart'))
  const data = {
    type: f.type,
    amount: f.amount,
    category: f.category,
    accountId: f.accountId || null,
    remark: f.remark || null,
    period: f.period,
    startDate: f.startDate,
    endDate: f.endDate || null,
    enabled: f.enabled,
  }
  saving.value = true
  try {
    // 新建保存后后端会按起始日补历史期次;从未生成过的旧模板在编辑保存时也会回补,提示补了几笔
    const res = f.id ? await bookApi.updateRecurring(f.id, data) : await bookApi.createRecurring(data)
    const n = Number(f.id ? res : res?.generated) || 0
    ElMessage.success(n > 0 ? t('book.recurring.createdWithHistory', { n }) : t('common.saveSuccess'))
    editor.visible = false
    await load()
    emit('saved')
  } finally {
    saving.value = false
  }
}

const onToggle = async (row) => {
  await bookApi.setRecurringEnabled(row.id, row.enabled ? 0 : 1)
  ElMessage.success(t('common.saveSuccess'))
  await load()
  emit('saved')
}

const onDel = async (row) => {
  try {
    await ElMessageBox.confirm(t('book.recurring.deleteMessage', { category: row.category }), t('common.deleteConfirm'), {
      type: 'warning',
    })
  } catch {
    return
  }
  await bookApi.removeRecurring(row.id)
  ElMessage.success(t('common.deleted'))
  await load()
  emit('saved')
}

const onRun = async () => {
  running.value = true
  try {
    const data = await bookApi.runRecurring()
    ElMessage.success(t('book.recurring.runDone', { n: data?.generated ?? 0 }))
    await load()
    emit('saved')
  } finally {
    running.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) load()
  },
)
</script>

<style scoped>
.rc-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.rc-count {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.rc-actions {
  display: flex;
  gap: 8px;
}
.rc-muted {
  color: var(--color-text-tertiary, #b5a98c);
  font-size: 12px;
}
.rc-note {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #999);
}
.is-income {
  color: #67c23a;
  font-weight: 600;
}
.is-expense {
  color: #f56c6c;
  font-weight: 600;
}
.form-row {
  display: flex;
  gap: 12px;
}
@media (max-width: 560px) {
  .form-row {
    flex-direction: column;
    gap: 0;
  }
  .rc-bar {
    flex-wrap: wrap;
  }
}
</style>
