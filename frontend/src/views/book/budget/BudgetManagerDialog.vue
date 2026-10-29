<!-- 预算管理弹窗:按支出分类维护月度额度;分类可自由输入,重名由后端拒绝并原样提示 -->
<template>
  <el-dialog v-model="dialogVisible" append-to-body width="560px" :title="$t('book.budget.manageTitle')">
    <div class="bdg-bar">
      <span class="bdg-count">{{ $t('book.budget.count', { n: rows.length }) }}</span>
      <el-button type="primary" size="small" @click="openEditor()">{{ $t('book.budget.add') }}</el-button>
    </div>

    <el-table v-if="rows.length" v-loading="loading" :data="rows" size="small" max-height="380">
      <el-table-column prop="category" :label="$t('book.budget.category')" min-width="110" show-overflow-tooltip />
      <el-table-column :label="$t('book.budget.amount')" width="120" align="right">
        <template #default="{ row }">¥{{ money(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="note" :label="$t('book.budget.note')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="96" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openEditor(row)">{{ $t('common.edit') }}</el-button>
          <el-button size="small" text type="danger" @click="onDel(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else :description="$t('book.budget.empty')">
      <el-button type="primary" @click="openEditor()">{{ $t('book.budget.addFirst') }}</el-button>
    </el-empty>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ $t('common.close') }}</el-button>
    </template>

    <!-- 新增/编辑:分类 + 每月预算 + 备注 -->
    <el-dialog
      v-model="editor.visible"
      append-to-body
      width="420px"
      :title="editor.form.id ? $t('book.budget.edit') : $t('book.budget.add')"
    >
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('book.budget.category')" required>
          <el-select
            v-model="editor.form.category"
            filterable
            allow-create
            default-first-option
            :placeholder="$t('book.budget.categoryPlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="c in BOOK_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('book.budget.amount')" required>
          <el-input-number v-model="editor.form.amount" :min="0.01" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('book.budget.note')">
          <el-input v-model="editor.form.note" maxlength="200" :placeholder="$t('book.budget.notePlaceholder')" />
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
// 预算管理:列表只展示额度,当月已花/超支在记账本页的预算卡里看
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
const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const editor = reactive({ visible: false, form: {} })

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const money = (v) => Number(v ?? 0).toFixed(2)

const load = async () => {
  loading.value = true
  try {
    // 不带 month:管理弹窗只关心额度本身
    const data = await bookApi.budget()
    rows.value = data?.items || []
  } finally {
    loading.value = false
  }
}

const openEditor = (row) => {
  editor.visible = true
  editor.form = row
    ? { id: row.id, category: row.category, amount: Number(row.amount ?? 0), note: row.note || '' }
    : { id: null, category: '', amount: 0, note: '' }
}

const onSave = async () => {
  const f = editor.form
  if (!f.category?.trim()) return ElMessage.warning(t('book.budget.fillCategory'))
  if (!f.amount || f.amount <= 0) return ElMessage.warning(t('book.budget.fillAmount'))
  const data = { category: f.category.trim(), amount: f.amount, note: f.note || null }
  saving.value = true
  try {
    if (f.id) await bookApi.updateBudget(f.id, data)
    else await bookApi.createBudget(data)
    ElMessage.success(t('common.saveSuccess'))
    editor.visible = false
    await load()
    emit('saved')
  } catch {
    // 分类重名时后端返回 400,提示文案由 api/request.js 拦截器原样弹出,这里不再拼一遍
  } finally {
    saving.value = false
  }
}

const onDel = async (row) => {
  try {
    await ElMessageBox.confirm(t('book.budget.deleteMessage', { category: row.category }), t('common.deleteConfirm'), {
      type: 'warning',
    })
  } catch {
    return
  }
  await bookApi.removeBudget(row.id)
  ElMessage.success(t('common.deleted'))
  await load()
  emit('saved')
}

watch(
  () => props.visible,
  (v) => {
    if (v) load()
  },
)
</script>

<style scoped>
.bdg-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.bdg-count {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
</style>
