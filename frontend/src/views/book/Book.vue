<!-- 记账本页：家庭共享账本,月切换/收支统计卡/分类支出排行/明细列表 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('book.title') }]" />

    <div class="list-header">
      <div class="left">
        <el-date-picker v-model="month" type="month" value-format="YYYY-MM" @change="load" style="width: 140px" />
      </div>
      <el-button type="primary" @click="openEditor()">{{ $t('book.add') }}</el-button>
    </div>

    <!-- 统计卡 -->
    <div class="stats-row">
      <div class="stat-card card income">
        <div class="stat-label">{{ $t('book.income') }}</div>
        <div class="stat-value">+{{ stats.income?.toFixed(2) ?? '0.00' }}</div>
      </div>
      <div class="stat-card card expense">
        <div class="stat-label">{{ $t('book.expense') }}</div>
        <div class="stat-value">-{{ stats.expense?.toFixed(2) ?? '0.00' }}</div>
      </div>
      <div class="stat-card card">
        <div class="stat-label">{{ $t('book.balance') }}</div>
        <div class="stat-value">{{ stats.balance?.toFixed(2) ?? '0.00' }}</div>
      </div>
    </div>

    <div class="content">
      <!-- 明细列表 -->
      <div class="records card">
        <h3>{{ $t('book.records') }}</h3>
        <div v-loading="loading">
          <div v-for="r in records" :key="r.id" class="record-row">
            <div class="record-main">
              <span class="record-cat" :class="typeCls(r)">{{ catText(r) }}</span>
              <div class="record-info">
                <div class="record-remark">{{ r.remark || r.category }}</div>
                <div class="record-meta">{{ r.recordDate }} · {{ r.creatorName }}</div>
              </div>
            </div>
            <div class="record-amount" :class="typeCls(r)">{{ (r.type === 'EXPENSE' ? '-' : '+') + r.amount }}</div>
            <div class="record-actions">
              <el-button size="small" text @click="openEditor(r)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" text type="danger" @click="onDel(r)">{{ $t('common.delete') }}</el-button>
            </div>
          </div>
          <el-empty v-if="!records.length" :description="$t('book.noRecords')" />
        </div>
      </div>

      <!-- 分类排行 -->
      <div class="cats card">
        <h3>{{ $t('book.expenseCategories') }}</h3>
        <div v-for="c in stats.categoryStats || []" :key="c.category" class="cat-row">
          <span class="cat-name">{{ c.category }}</span>
          <span class="cat-total">{{ c.total.toFixed(2) }}</span>
        </div>
        <div v-if="!stats.categoryStats?.length" class="cat-empty">{{ $t('common.empty') }}</div>
      </div>
    </div>

    <el-dialog v-model="editor.visible" :title="editor.form.id ? $t('book.editRecord') : $t('book.add')" width="420px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('book.typeLabel')">
          <el-radio-group v-model="editor.form.type">
            <el-radio :value="0">{{ $t('book.type.EXPENSE') }}</el-radio>
            <el-radio :value="1">{{ $t('book.type.INCOME') }}</el-radio>
            <el-radio :value="2">{{ $t('book.type.TRANSFER') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="form-row">
          <el-form-item :label="$t('book.amount')" style="flex: 1">
            <el-input-number v-model="editor.form.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="$t('book.date')" style="flex: 1">
            <el-date-picker v-model="editor.form.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item :label="$t('book.category')">
          <el-select v-model="editor.form.category" filterable allow-create default-first-option :placeholder="$t('book.categoryPlaceholder')" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('book.remark')">
          <el-input v-model="editor.form.remark" :placeholder="$t('book.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 记账本:月度明细+收支统计;改删仅记录人/家长(后端校验),前端按条件隐藏按钮
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { bookApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

// ponytail: 分类为用户自选/自定义数据(入库存储),不做翻译
const CATEGORIES = ['餐饮', '交通', '购物', '家居', '水电燃气', '医疗', '教育', '娱乐', '工资', '红包', '其他']

const { t } = useI18n()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const month = ref(new Date().toISOString().slice(0, 7))
const records = ref([])
const stats = ref({})
const editor = reactive({ visible: false, form: {} })

const catText = (r) =>
  (r.type === 'INCOME' ? t('book.type.INCOME') : r.type === 'TRANSFER' ? t('book.type.TRANSFER') : r.category)

// 后端返回英文单词(type: EXPENSE/INCOME/TRANSFER),表单提交仍是数字由后端转换
const typeCls = (r) => ({ EXPENSE: 't0', INCOME: 't1', TRANSFER: 't2' }[r.type] || 't0')
const typeNum = (r) => ({ EXPENSE: 0, INCOME: 1, TRANSFER: 2 }[r.type] ?? 0)

const load = async () => {
  loading.value = true
  try {
    const data = await bookApi.list(month.value)
    records.value = data.records
    stats.value = data.stats
  } finally {
    loading.value = false
  }
}

const openEditor = (r) => {
  editor.visible = true
  editor.form = r
    ? { id: r.id, type: typeNum(r), amount: Number(r.amount), category: r.category, remark: r.remark || '', recordDate: r.recordDate }
    : { id: null, type: 0, amount: 0, category: '其他', remark: '', recordDate: new Date().toISOString().slice(0, 10) }
}

const onSave = async () => {
  if (!editor.form.amount || editor.form.amount <= 0) return ElMessage.warning(t('book.fillAmount'))
  saving.value = true
  try {
    const { id, ...data } = editor.form
    if (id) await bookApi.update(id, data)
    else await bookApi.create(data)
    ElMessage.success(t('common.saveSuccess'))
    editor.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

const onDel = async (r) => {
  await ElMessageBox.confirm(t('book.deleteMessage', { amount: r.amount }), t('common.deleteConfirm'), { type: 'warning' })
  await bookApi.remove(r.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 统计卡三列;明细行:分类/备注/金额着色;支出分类排行 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.stat-card {
  padding: 14px 18px;
}
.stat-label {
  color: #999;
  font-size: 12px;
}
.stat-value {
  font-size: 22px;
  font-weight: 700;
  margin-top: 6px;
}
.stat-card.income .stat-value {
  color: #67c23a;
}
.stat-card.expense .stat-value {
  color: #f56c6c;
}
.content {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}
.records,
.cats {
  padding: 16px 18px;
}
.records h3,
.cats h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.record-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f0f0;
}
.record-main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.record-cat {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #f5f7fa;
  color: #666;
  white-space: nowrap;
}
.record-cat.t1 {
  background: #f0f9eb;
  color: #67c23a;
}
.record-cat.t2 {
  background: #fdf6ec;
  color: #e6a23c;
}
.record-remark {
  font-size: 14px;
}
.record-meta {
  font-size: 12px;
  color: #999;
}
.record-amount {
  font-weight: 600;
  white-space: nowrap;
}
.record-amount.t0 {
  color: #f56c6c;
}
.record-amount.t1 {
  color: #67c23a;
}
.cat-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed #f0f0f0;
  font-size: 13px;
}
.cat-total {
  color: #f56c6c;
  font-weight: 600;
}
.cat-empty {
  color: #999;
  font-size: 12px;
}
.form-row {
  display: flex;
  gap: 12px;
}

@media (max-width: 768px) {
  .form-row { flex-direction: column; gap: 0; }
  .summary-cards { grid-template-columns: repeat(2, 1fr) !important; }
}
</style>