<!-- 账户管理弹窗:现金/银行卡/支付宝等账户的列表、行内编辑删除与新增;删除被拦时后端消息由请求层原样提示 -->
<template>
  <el-dialog v-model="dialogVisible" append-to-body width="620px" :title="$t('book.account.manageTitle')">
    <div class="acct-bar">
      <span class="acct-count">{{ $t('book.account.count', { n: rows.length }) }}</span>
      <el-button type="primary" size="small" @click="openEditor()">{{ $t('book.account.add') }}</el-button>
    </div>

    <el-table v-if="rows.length" v-loading="loading" :data="rows" size="small" max-height="380">
      <el-table-column prop="name" :label="$t('book.account.name')" min-width="110" show-overflow-tooltip />
      <el-table-column :label="$t('book.account.typeLabel')" width="90">
        <template #default="{ row }">{{ accountTypeLabel(row.accountType, t) }}</template>
      </el-table-column>
      <el-table-column prop="institution" :label="$t('book.account.institution')" min-width="100" show-overflow-tooltip />
      <el-table-column prop="cardTail" :label="$t('book.account.cardTail')" width="80" />
      <el-table-column :label="$t('book.account.initialBalance')" width="106" align="right">
        <template #default="{ row }">{{ money(row.initialBalance) }}</template>
      </el-table-column>
      <el-table-column :label="$t('book.account.balance')" width="106" align="right">
        <template #default="{ row }">{{ money(row.balance) }}</template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="96" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click="openEditor(row)">{{ $t('common.edit') }}</el-button>
          <el-button size="small" text type="danger" @click="onDel(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else :description="$t('book.account.empty')">
      <el-button type="primary" @click="openEditor()">{{ $t('book.account.addFirst') }}</el-button>
    </el-empty>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ $t('common.close') }}</el-button>
    </template>

    <!-- 新增/编辑表单:字段与后端表单一一对应(名称/类型/开户行/尾号/期初/备注) -->
    <el-dialog
      v-model="editor.visible"
      append-to-body
      width="460px"
      :title="editor.form.id ? $t('book.account.edit') : $t('book.account.add')"
    >
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('book.account.name')" required>
          <el-input v-model="editor.form.name" maxlength="50" :placeholder="$t('book.account.namePlaceholder')" />
        </el-form-item>
        <div class="form-row">
          <el-form-item :label="$t('book.account.typeLabel')" style="flex: 1">
            <el-select v-model="editor.form.accountType" style="width: 100%">
              <el-option v-for="tp in ACCOUNT_TYPES" :key="tp" :label="accountTypeLabel(tp, t)" :value="tp" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('book.account.cardTail')" style="flex: 1">
            <el-input v-model="editor.form.cardTail" maxlength="10" :placeholder="$t('book.account.cardTailPlaceholder')" />
          </el-form-item>
        </div>
        <el-form-item :label="$t('book.account.institution')">
          <el-input v-model="editor.form.institution" maxlength="60" :placeholder="$t('book.account.institutionPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('book.account.initialBalance')">
          <el-input-number v-model="editor.form.initialBalance" :precision="2" :step="100" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('book.account.note')">
          <el-input v-model="editor.form.note" maxlength="200" :placeholder="$t('book.account.notePlaceholder')" />
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
// 账户管理:列表自带实时余额(后端按流水算),这里只负责增删改与刷新
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

import { bookApi } from '@/api'
import { ACCOUNT_TYPES, accountTypeLabel } from '@/utils/bookLedger'

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
    rows.value = (await bookApi.accounts()) || []
  } finally {
    loading.value = false
  }
}

const openEditor = (row) => {
  editor.visible = true
  editor.form = row
    ? {
        id: row.id,
        name: row.name,
        accountType: row.accountType || 'BANK',
        institution: row.institution || '',
        cardTail: row.cardTail || '',
        initialBalance: Number(row.initialBalance ?? 0),
        note: row.note || '',
      }
    : { id: null, name: '', accountType: 'BANK', institution: '', cardTail: '', initialBalance: 0, note: '' }
}

const onSave = async () => {
  const f = editor.form
  if (!f.name?.trim()) return ElMessage.warning(t('book.account.fillName'))
  const data = {
    name: f.name.trim(),
    accountType: f.accountType,
    institution: f.institution || null,
    cardTail: f.cardTail || null,
    initialBalance: f.initialBalance ?? 0,
    note: f.note || null,
  }
  saving.value = true
  try {
    if (f.id) await bookApi.updateAccount(f.id, data)
    else await bookApi.createAccount(data)
    ElMessage.success(t('common.saveSuccess'))
    editor.visible = false
    await load()
    emit('saved')
  } finally {
    saving.value = false
  }
}

const onDel = async (row) => {
  try {
    await ElMessageBox.confirm(t('book.account.deleteMessage', { name: row.name }), t('common.deleteConfirm'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await bookApi.removeAccount(row.id)
    ElMessage.success(t('common.deleted'))
    await load()
    emit('saved')
  } catch {
    // 账户下还有记录时后端返回 400,提示文案由 api/request.js 拦截器原样弹出,这里不再拼一遍
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
.acct-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.acct-count {
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.form-row {
  display: flex;
  gap: 12px;
}

@media (max-width: 768px) {
  .form-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
