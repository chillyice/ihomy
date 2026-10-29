<!-- 记账本页签:账户余额条 + 月度统计卡 + 明细(行内账户标签)+ 支出分类排行 + 记账弹窗 -->
<template>
  <div class="book-daily">
    <PageToolbar v-if="userStore.isLoggedIn" :card="false">
      <div class="tb-right">
        <el-date-picker v-model="monthModel" type="month" value-format="YYYY-MM" style="width: 140px" />
        <el-button type="primary" @click="openEditor()">{{ $t('book.add') }}</el-button>
      </div>
    </PageToolbar>

    <!-- 家庭账本私有:游客只给登录引导,不发注定 401 的请求(与总账页签同口径) -->
    <el-empty v-if="!userStore.isLoggedIn" :description="$t('book.guestEmpty')" />

    <template v-else>
      <!-- 账户余额条 -->
      <div class="book-balance-strip card">
        <div class="bs-chips">
          <div v-for="a in accounts" :key="a.id" class="bs-chip">
            <span class="bs-chip-name">{{ a.name }}</span>
            <span class="bs-chip-amount" :class="{ 'is-negative': Number(a.balance) < 0 }">¥{{ formatYuan(a.balance) }}</span>
          </div>
          <span v-if="!accounts.length" class="bs-hint">{{ $t('book.account.noAccounts') }}</span>
        </div>
        <div class="bs-actions">
          <span class="bs-hint">{{ $t('book.account.unassignedCount', { n: unassigned }) }}</span>
          <el-button size="small" @click="accountMgrVisible = true">{{ $t('book.account.manage') }}</el-button>
        </div>
      </div>

      <!-- 统计卡 -->
      <div class="book-kpi-grid">
        <div class="book-kpi card">
          <div class="book-kpi-label">{{ $t('book.income') }}</div>
          <div class="book-kpi-value is-income">+{{ money(stats.income) }}</div>
        </div>
        <div class="book-kpi card">
          <div class="book-kpi-label">{{ $t('book.expense') }}</div>
          <div class="book-kpi-value is-expense">-{{ money(stats.expense) }}</div>
        </div>
        <div class="book-kpi card">
          <div class="book-kpi-label">{{ $t('book.balance') }}</div>
          <div class="book-kpi-value">{{ money(stats.balance) }}</div>
        </div>
      </div>

      <div class="content">
        <!-- 明细列表 -->
        <div class="records card">
          <div class="section-label">{{ $t('book.records') }}</div>
          <div v-loading="loading">
            <div v-for="r in records" :key="r.id" class="record-row">
              <div class="record-main">
                <span class="record-cat" :class="typeCls(r)">{{ catText(r) }}</span>
                <div class="record-info">
                  <div class="record-remark">{{ r.remark || r.category }}</div>
                  <div class="record-meta">
                    <span>{{ r.recordDate }} · {{ r.creatorName }}</span>
                    <span class="record-account" :class="{ 'is-unassigned': !hasAccount(r) }">{{ accountText(r) }}</span>
                  </div>
                </div>
              </div>
              <div class="record-amount" :class="typeCls(r)">{{ amountText(r) }}</div>
              <div class="record-actions">
                <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
                  <el-button size="small" text @click="openEditor(r)"
                    ><el-icon><Edit /></el-icon
                  ></el-button>
                </el-tooltip>
                <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                  <el-button size="small" text type="danger" @click="onDel(r)"
                    ><el-icon><Delete /></el-icon
                  ></el-button>
                </el-tooltip>
              </div>
            </div>
            <el-empty v-if="!records.length" :description="$t('book.noRecords')" />
          </div>
        </div>

        <!-- 预算与分类排行:预算卡在上,超支一眼可见 -->
        <div class="side">
          <BudgetPanel :data="budgetData" @saved="loadBudget" />

          <!-- 分类排行 -->
          <div class="cats card">
            <div class="section-label">{{ $t('book.expenseCategories') }}</div>
            <div v-for="c in stats.categoryStats || []" :key="c.category" class="cat-row">
              <span class="cat-name">{{ c.category }}</span>
              <span class="cat-total">{{ Number(c.total).toFixed(2) }}</span>
            </div>
            <div v-if="!stats.categoryStats?.length" class="book-empty">{{ $t('common.empty') }}</div>
          </div>
        </div>
      </div>
    </template>

    <!-- 记账弹窗:收支挂一个账户,转账挂「转出 → 转入」,两侧都可空(空=账户外) -->
    <el-dialog v-model="editor.visible" append-to-body :title="editor.form.id ? $t('book.editRecord') : $t('book.add')" width="420px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('book.typeLabel')">
          <el-radio-group v-model="editor.form.type">
            <el-radio value="EXPENSE">{{ $t('book.type.EXPENSE') }}</el-radio>
            <el-radio value="INCOME">{{ $t('book.type.INCOME') }}</el-radio>
            <el-radio value="TRANSFER">{{ $t('book.type.TRANSFER') }}</el-radio>
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
        <el-form-item v-if="editor.form.type !== 'TRANSFER'" :label="$t('book.account.field')">
          <el-select v-model="editor.form.accountId" clearable :placeholder="$t('book.account.optional')" style="width: 100%">
            <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <div v-else class="form-row">
          <el-form-item :label="$t('book.account.from')" style="flex: 1">
            <el-select v-model="editor.form.fromAccountId" clearable :placeholder="$t('book.account.outside')" style="width: 100%">
              <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('book.account.to')" style="flex: 1">
            <el-select v-model="editor.form.toAccountId" clearable :placeholder="$t('book.account.outside')" style="width: 100%">
              <el-option v-for="a in accounts" :key="a.id" :label="a.name" :value="a.id" />
            </el-select>
          </el-form-item>
        </div>
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
          <el-input v-model="editor.form.remark" :placeholder="$t('book.remarkPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 账户管理:改完账户回来刷新余额条(明细里的账户名不随账户改名回填,下次加载生效) -->
    <AccountManagerDialog v-model:visible="accountMgrVisible" @saved="loadAccounts" />
  </div>
</template>

<script setup>
// 记账本页签:月度明细 + 收支统计 + 账户余额;改删仅记录人/家长(后端校验),前端按条件隐藏按钮
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { bookApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatYuan } from '@/utils/loan'
import { BOOK_CATEGORIES, unassignedCount } from '@/utils/bookLedger'
import PageToolbar from '@/components/PageToolbar.vue'
import AccountManagerDialog from './accounts/AccountManagerDialog.vue'
import BudgetPanel from './budget/BudgetPanel.vue'
import './bookPage.css'

const props = defineProps({
  // 月份归属父页(总账页签点某月要能切回来),本页只读写这一份
  month: { type: String, default: '' },
})
const emit = defineEmits(['update:month'])

// ponytail: 分类为用户自选/自定义数据(入库存储),不做翻译;候选清单与预算弹窗共用一份
const CATEGORIES = BOOK_CATEGORIES

const { t } = useI18n()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const records = ref([])
const stats = ref({})
const accounts = ref([])
const budgetData = ref(null)
const accountMgrVisible = ref(false)
const editor = reactive({ visible: false, form: {} })

const monthModel = computed({
  get: () => props.month,
  set: (v) => emit('update:month', v),
})

const unassigned = computed(() => unassignedCount(records.value))
const money = (v) => Number(v ?? 0).toFixed(2)

const catText = (r) => (r.type === 'INCOME' ? t('book.type.INCOME') : r.type === 'TRANSFER' ? t('book.type.TRANSFER') : r.category)

// 后端返回英文单词(type: EXPENSE/INCOME/TRANSFER),表单直接沿用字典词提交
const typeCls = (r) => ({ EXPENSE: 't0', INCOME: 't1', TRANSFER: 't2' })[r.type] || 't0'

// 转账不带正负号:它不改收支也不改结余
const amountText = (r) => (r.type === 'EXPENSE' ? '-' : r.type === 'INCOME' ? '+' : '') + Number(r.amount).toFixed(2)

// 行内账户标签:收支看 accountName,转账看「转出 → 转入」,空一侧说明钱进出账户之外
const hasAccount = (r) => (r.type === 'TRANSFER' ? !!(r.fromAccountId || r.toAccountId) : !!r.accountId)
const accountText = (r) => {
  if (r.type === 'TRANSFER') {
    return `${r.fromAccountName || t('book.account.outside')} → ${r.toAccountName || t('book.account.outside')}`
  }
  return r.accountName || t('book.account.unassigned')
}

const load = async () => {
  // 家庭账本是私有内容:游客不打注定 401 的请求,直接空态
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    // 明细、账户、预算三者互不依赖,并行取;预算与明细同月份,记一笔后一起重拉
    const [data, acc, budget] = await Promise.all([bookApi.list(props.month), bookApi.accounts(), bookApi.budget(props.month)])
    records.value = data.records || []
    stats.value = data.stats || {}
    accounts.value = acc || []
    budgetData.value = budget || null
  } finally {
    loading.value = false
  }
}

// 预算增删改后只刷预算卡,不重拉明细与账户
const loadBudget = async () => {
  if (!userStore.isLoggedIn) return
  budgetData.value = (await bookApi.budget(props.month)) || null
}

// 账户增删改后只刷余额条,不重拉明细
const loadAccounts = async () => {
  if (!userStore.isLoggedIn) return
  accounts.value = (await bookApi.accounts()) || []
}

const openEditor = (r) => {
  editor.visible = true
  editor.form = r
    ? {
        id: r.id,
        type: r.type,
        amount: Number(r.amount),
        category: r.category,
        remark: r.remark || '',
        recordDate: r.recordDate,
        accountId: r.accountId ?? null,
        fromAccountId: r.fromAccountId ?? null,
        toAccountId: r.toAccountId ?? null,
      }
    : {
        id: null,
        type: 'EXPENSE',
        amount: 0,
        category: '其他',
        remark: '',
        recordDate: new Date().toISOString().slice(0, 10),
        accountId: null,
        fromAccountId: null,
        toAccountId: null,
      }
}

const onSave = async () => {
  const f = editor.form
  if (!f.amount || f.amount <= 0) return ElMessage.warning(t('book.fillAmount'))
  if (f.type === 'TRANSFER' && !f.fromAccountId && !f.toAccountId) return ElMessage.warning(t('book.account.transferNeedOne'))
  const isTransfer = f.type === 'TRANSFER'
  const data = {
    type: f.type,
    amount: f.amount,
    category: f.category,
    remark: f.remark,
    recordDate: f.recordDate,
    accountId: isTransfer ? null : f.accountId || null,
    fromAccountId: isTransfer ? f.fromAccountId || null : null,
    toAccountId: isTransfer ? f.toAccountId || null : null,
  }
  saving.value = true
  try {
    if (f.id) await bookApi.update(f.id, data)
    else await bookApi.create(data)
    ElMessage.success(t('common.saveSuccess'))
    editor.visible = false
    // 记一笔会改账户余额,余额条与明细一起刷
    await load()
  } finally {
    saving.value = false
  }
}

const onDel = async (r) => {
  await ElMessageBox.confirm(t('book.deleteMessage', { amount: r.amount }), t('common.deleteConfirm'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  await bookApi.remove(r.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

// 月份由父页持有:选月/父页钻取都走这里,避免两处各自维护一份
watch(
  () => props.month,
  () => load(),
)
onMounted(load)
</script>

<style scoped>
/* 只放本页布局;统计卡/余额条/空态样式在 bookPage.css,EP 组件样式走全局 main.css */
.content {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
}
.records,
.cats {
  padding: 16px 18px;
  min-width: 0;
}
/* 右栏:预算卡 + 分类排行纵向排列,窄屏跟随主栏顺序落到明细下方 */
.side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.record-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border);
}
.record-main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.record-info {
  min-width: 0;
}
.record-cat {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(150, 150, 150, 0.12);
  color: var(--color-text-secondary, #666);
  white-space: nowrap;
}
.record-cat.t1 {
  background: rgba(103, 194, 58, 0.12);
  color: #67c23a;
}
.record-cat.t2 {
  background: rgba(230, 162, 60, 0.14);
  color: #e6a23c;
}
.record-remark {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.record-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.record-account {
  padding: 0 6px;
  border-radius: 4px;
  background: rgba(150, 150, 150, 0.1);
  white-space: nowrap;
}
.record-account.is-unassigned {
  background: transparent;
  color: #bbb;
}
.record-amount {
  font-weight: 600;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.record-amount.t0 {
  color: #f56c6c;
}
.record-amount.t1 {
  color: #67c23a;
}
.record-amount.t2 {
  color: #e6a23c;
}
.record-actions :deep(.el-button) {
  padding: 5px 6px;
}
.record-actions :deep(.el-button + .el-button) {
  margin-left: 4px;
}
.cat-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px dashed var(--color-border);
  font-size: 13px;
}
.cat-total {
  color: #f56c6c;
  font-weight: 600;
}
.form-row {
  display: flex;
  gap: 12px;
}

@media (max-width: 768px) {
  .content {
    grid-template-columns: 1fr;
  }
  .form-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
