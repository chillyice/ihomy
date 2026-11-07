<!-- 记账本页签:账户余额条 + 月度统计卡 + 明细(行内账户标签)+ 支出分类排行 + 记账弹窗 -->
<template>
  <div class="book-daily">
    <!-- 上下文条(无卡片外壳):左 = 月份导航(前后月快捷按钮 + 选择器 + 回到本月),右 = 三个入口 -->
    <div v-if="userStore.isLoggedIn" class="book-context">
      <div class="bc-left">
        <el-button text class="bc-nav" :title="$t('schedule.prevMonth')" :aria-label="$t('schedule.prevMonth')" @click="shiftMonth(-1)">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <el-date-picker v-model="monthModel" type="month" value-format="YYYY-MM" style="width: 140px" />
        <el-button text class="bc-nav" :title="$t('schedule.nextMonth')" :aria-label="$t('schedule.nextMonth')" @click="shiftMonth(1)">
          <el-icon><ArrowRight /></el-icon>
        </el-button>
        <el-button v-if="!isCurrentMonth" text type="primary" @click="backToThisMonth">{{ $t('book.backToThisMonth') }}</el-button>
      </div>
      <!-- 三个入口并排:记一笔(主)、周期账目、导入 CSV;只读档位不显示 -->
      <div v-if="userStore.canWriteBook" class="bc-right">
        <el-button type="primary" @click="openEditor()">{{ $t('book.add') }}</el-button>
        <el-button @click="recurringVisible = true">{{ $t('book.recurring.entry') }}</el-button>
        <el-button @click="importVisible = true">{{ $t('book.import.entry') }}</el-button>
      </div>
    </div>

    <!-- 家庭账本私有:游客只给登录引导,不发注定 401 的请求(与总账页签同口径) -->
    <el-empty v-if="!userStore.isLoggedIn" :description="$t('book.guestEmpty')" />

    <template v-else>
      <!-- 账户余额条:chip 用按钮语义(键盘可达 + aria-pressed);未归属多于一笔时也是一枚可点的 chip -->
      <div class="book-balance-strip card">
        <div ref="chipsEl" class="bs-chips">
          <button
            v-for="a in accounts"
            :key="a.id"
            type="button"
            class="bs-chip"
            :class="{ 'is-active': activeAccountId === a.id }"
            :aria-pressed="activeAccountId === a.id"
            :title="$t('book.account.filterHint', { name: a.name })"
            @click="toggleAccountFilter(a.id)"
          >
            <span class="bs-chip-name">{{ a.name }}</span>
            <span class="bs-chip-amount" :class="{ 'is-negative': Number(a.balance) < 0 }">¥{{ formatYuan(a.balance) }}</span>
          </button>
          <template v-if="!accounts.length">
            <span class="bs-hint">{{ $t('book.account.noAccounts') }}</span>
            <el-button v-if="userStore.canWriteBook" size="small" text type="primary" @click="accountMgrVisible = true">{{
              $t('book.account.add')
            }}</el-button>
          </template>
        </div>
        <div class="bs-actions">
          <button
            v-if="unassigned > 0"
            type="button"
            class="bs-chip"
            :class="{ 'is-active': isUnassignedFilter }"
            :aria-pressed="isUnassignedFilter"
            :title="$t('book.account.unassignedFilter')"
            @click="toggleUnassignedFilter"
          >
            <span class="bs-chip-name">{{ $t('book.account.unassignedCount', { n: unassigned }) }}</span>
          </button>
          <el-button v-if="userStore.canWriteBook" size="small" @click="accountMgrVisible = true">{{
            $t('book.account.manage')
          }}</el-button>
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
        <div class="book-kpi card book-kpi-wide">
          <div class="book-kpi-label">{{ $t('book.balance') }}</div>
          <div class="book-kpi-value">{{ money(stats.balance) }}</div>
          <div v-if="budgetSubline" class="book-kpi-sub" :class="{ 'is-over': isBudgetOver }">{{ budgetSubline }}</div>
        </div>
      </div>

      <div class="content">
        <!-- 明细列表:点账户余额条上的账户只看该账户的账目(统计卡与预算仍是本月全部) -->
        <div class="records card">
          <div class="records-head">
            <div class="section-label">{{ $t('book.records') }}</div>
            <div v-if="isFilterActive" class="records-filter">
              <span class="rf-text">{{ $t('book.account.filterOnly', { name: activeFilterName, n: filteredRecords.length }) }}</span>
              <span class="rf-hint">{{ $t('book.account.filterScope') }}</span>
              <el-button size="small" text @click="clearAccountFilter">{{ $t('book.account.filterClear') }}</el-button>
            </div>
            <span v-else class="records-count">{{ $t('book.recordsCount', { n: filteredRecords.length }) }}</span>
          </div>
          <div v-loading="loading">
            <template v-for="g in groupedRecords" :key="g.date">
              <!-- 日期头:本地化「11月7日 周五」+ 当日小计(收入/支出分开,转账只报笔数) -->
              <div class="day-head">
                <span class="dh-date">{{ dayTitle(g) }}</span>
                <span class="dh-sum">
                  <span v-if="g.expense" class="dh-num is-expense">-{{ formatYuan(g.expense) }}</span>
                  <span v-if="g.income" class="dh-num is-income">+{{ formatYuan(g.income) }}</span>
                  <span v-if="g.transferCount" class="dh-transfer">{{ $t('book.dayTransfers', { n: g.transferCount }) }}</span>
                </span>
              </div>
              <div v-for="(r, i) in g.items" :key="r.id" class="record-row" :class="{ 'is-last': i === g.items.length - 1 }">
                <div class="record-main">
                  <span class="record-cat" :class="typeCls(r)">{{ catText(r) }}</span>
                  <div class="record-info">
                    <div class="record-remark">{{ r.remark || r.category }}</div>
                    <div class="record-meta">
                      <span>{{ r.creatorName }}</span>
                      <span class="record-account" :class="{ 'is-unassigned': !hasAccount(r) }">{{ accountText(r) }}</span>
                    </div>
                  </div>
                </div>
                <div class="record-amount" :class="typeCls(r)">{{ amountText(r) }}</div>
                <div v-if="canEditRow(r)" class="record-actions">
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
            </template>
            <el-empty
              v-if="!filteredRecords.length"
              :description="isFilterActive ? $t('book.account.filterEmpty') : $t('book.noRecords')"
            />
          </div>
        </div>

        <!-- 预算与分类排行:预算卡在上,超支一眼可见 -->
        <div class="side">
          <BudgetPanel :data="budgetData" :readonly="!userStore.canWriteBook" @saved="loadBudget" />

          <!-- 分类排行:与总账构成卡同一套构成行(名称/金额·占比 + 比例条),占比相对本月总支出 -->
          <div class="cats card">
            <div class="section-label">{{ $t('book.expenseCategories') }}</div>
            <div v-for="c in catRows" :key="c.category" class="book-cat">
              <div class="book-cat-head">
                <span class="book-cat-name">{{ c.category }}</span>
                <span class="book-cat-amount">{{ formatYuan(c.total) }} · {{ pctText(c.share) }}</span>
              </div>
              <div class="book-cat-bar">
                <div class="book-cat-bar-fill is-expense" :style="{ width: pctWidth(c.share) }"></div>
              </div>
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

    <!-- CSV 导入 / 周期账目:导入或自动生成都会新增流水,关窗后重拉本月明细与账户 -->
    <ImportDialog v-model:visible="importVisible" @saved="load" />
    <RecurringManagerDialog v-model:visible="recurringVisible" @saved="load" />
  </div>
</template>

<script setup>
// 记账本页签:月度明细 + 收支统计 + 账户余额;改删仅记录人/家长(后端校验),前端按条件隐藏按钮
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, ArrowRight, Delete, Edit } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { bookApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatYuan } from '@/utils/money'
import { bindHorizontalWheel } from '@/utils/horizontalWheel'
import { weekdayKey, shiftMonth as shiftCalendarMonth, todayString } from '@/utils/scheduleCalendar'
import {
  BOOK_CATEGORIES,
  UNASSIGNED_ID,
  accountRecords,
  groupRecordsByDate,
  pctText,
  pctWidth,
  ratio,
  unassignedCount,
} from '@/utils/bookLedger'
import AccountManagerDialog from './accounts/AccountManagerDialog.vue'
import BudgetPanel from './budget/BudgetPanel.vue'
import ImportDialog from './import/ImportDialog.vue'
import RecurringManagerDialog from './recurring/RecurringManagerDialog.vue'
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
const importVisible = ref(false)
const recurringVisible = ref(false)
const editor = reactive({ visible: false, form: {} })
// 余额条的横向滚动靠原生滑动(触屏)+ 滚轮映射(鼠标),不显示原生滚动条
const chipsEl = ref(null)
let unbindWheel = null

// 账户筛选:点余额条上的账户只看它的账目,再点一次取消;切月保留(对比同一账户的跨月流水很方便)
const activeAccountId = ref(null)
const activeAccount = computed(() => accounts.value.find((a) => a.id === activeAccountId.value) || null)
const filteredRecords = computed(() => accountRecords(records.value, activeAccountId.value))
const isFilterActive = computed(() => activeAccountId.value != null)
// 「未归属」不是真实账户:哨兵筛三个账户列全空的记录,与余额条上的未归属 chip 同一口径
const isUnassignedFilter = computed(() => activeAccountId.value === UNASSIGNED_ID)
const activeFilterName = computed(() => (isUnassignedFilter.value ? t('book.account.unassignedFilter') : activeAccount.value?.name || ''))

const toggleAccountFilter = (id) => {
  activeAccountId.value = activeAccountId.value === id ? null : id
}
const toggleUnassignedFilter = () => {
  activeAccountId.value = isUnassignedFilter.value ? null : UNASSIGNED_ID
}
const clearAccountFilter = () => {
  activeAccountId.value = null
}

// 账户被删或换了家庭后列表里没有它:自动清除筛选,否则会卡在「空明细 + 找不到账户」的死角
// (未归属哨兵不是账户,不参与这个清除)
watch(accounts, (list) => {
  if (
    activeAccountId.value != null &&
    activeAccountId.value !== UNASSIGNED_ID &&
    !(list || []).some((a) => a.id === activeAccountId.value)
  ) {
    activeAccountId.value = null
  }
})

const monthModel = computed({
  get: () => props.month,
  set: (v) => emit('update:month', v),
})

// 月份导航:前后月一键切换 + 回到本月;月份本身仍由父页持有(总账钻取同一份)
const isCurrentMonth = computed(() => props.month === todayString().slice(0, 7))
const shiftMonth = (delta) => {
  const [y, m] = String(props.month).split('-').map(Number)
  if (!y || !m) return
  const next = shiftCalendarMonth(y, m, delta)
  monthModel.value = `${next.year}-${String(next.month).padStart(2, '0')}`
}
const backToThisMonth = () => {
  monthModel.value = todayString().slice(0, 7)
}

// 明细按日分组:日期头 + 当日小计;账户筛选生效时组内同口径(日期倒序、日内保持后端顺序)
const groupedRecords = computed(() => groupRecordsByDate(filteredRecords.value))
const dayTitle = (g) => {
  const wk = weekdayKey(g.date)
  if (!wk) return g.date
  return t('schedule.dayTitle', {
    month: Number(g.date.slice(5, 7)),
    day: Number(g.date.slice(8, 10)),
    weekday: t(`schedule.weekdayLong.${wk}`),
  })
}

// 分类排行:占比相对本月总支出(与总账构成卡同口径),配合比例条一眼看出大头
const catRows = computed(() => {
  const list = stats.value.categoryStats || []
  const total = Number(stats.value.expense) || 0
  return list.map((c) => ({ ...c, share: ratio(c.total, total) }))
})

const unassigned = computed(() => unassignedCount(records.value))
// 金额展示全站唯一口径:千分位 + 两位小数(与总账/预算/理财一致,不再用 toFixed)
const money = (v) => formatYuan(v)

// 结余卡副标:本月预算的余量/超支(与预算卡同一份数据),没设预算就不显示
const isBudgetOver = computed(
  () => !!budgetData.value?.total?.over || Number(budgetData.value?.total?.spent) > Number(budgetData.value?.total?.budget),
)
const budgetSubline = computed(() => {
  const total = budgetData.value?.total
  if (!total || !(Number(total.budget) > 0)) return ''
  return isBudgetOver.value
    ? t('book.kpiBudgetOver', { amount: money(Number(total.spent) - Number(total.budget)) })
    : t('book.kpiBudgetLeft', { amount: money(Number(total.budget) - Number(total.spent)) })
})

// 行内改删:可写档位下,本人记的或可管理账本者才能改;只读档位整列隐藏(后端仍逐条校验)
const canEditRow = (r) => userStore.canWriteBook && (userStore.canManageBook || r.createdBy === userStore.userInfo?.id)

const catText = (r) => (r.type === 'INCOME' ? t('book.type.INCOME') : r.type === 'TRANSFER' ? t('book.type.TRANSFER') : r.category)

// 后端返回英文单词(type: EXPENSE/INCOME/TRANSFER),表单直接沿用字典词提交
const typeCls = (r) => ({ EXPENSE: 't0', INCOME: 't1', TRANSFER: 't2' })[r.type] || 't0'

// 转账不带正负号:它不改收支也不改结余
const amountText = (r) => (r.type === 'EXPENSE' ? '-' : r.type === 'INCOME' ? '+' : '') + formatYuan(r.amount)

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
// 余额条只在登录态渲染:模板 ref 从 null 变为元素时再挂滚轮,游客不会空挂监听
watch(chipsEl, (el) => {
  unbindWheel?.()
  unbindWheel = el ? bindHorizontalWheel(el) : null
})
onBeforeUnmount(() => unbindWheel?.())
</script>

<style scoped>
/* 只放本页布局;统计卡/余额条/空态样式在 bookPage.css,EP 组件样式走全局 main.css */
/* 上下文条(无卡片外壳):左 = 月份导航(◀ 选择器 ▶ + 回到本月),右 = 三入口;
   水平内边距对齐卡片内容线(页签栏/余额条的内容都从卡片 16px 内边距起,按钮右缘不凸出卡边);
   页签栏管「看哪个月账本」,这行管「这个月怎么操作」 */
.book-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 0 16px;
  margin: 8px 0 10px;
}
.book-context .bc-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex-wrap: wrap;
}
.book-context .bc-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  flex-wrap: wrap;
}
.content {
  display: grid;
  /* minmax(0,…) 与总账页 .lg-main 同口径:fr 轨道的自动下限是内容 min-content,不归零会被长内容顶破列宽 */
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 14px;
  /* 网格默认 stretch:明细很长时右栏被拉到同高,下方留一大片空白;两栏各自按内容高度排 */
  align-items: start;
}
.records,
.cats {
  padding: 16px 18px;
  min-width: 0;
}
/* 明细标题行:左边「明细」,筛选生效时右边挂筛选说明与清除入口 */
.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.records-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.rf-text {
  font-size: 12px;
  color: var(--color-text-primary);
  padding: 2px 8px;
  border-radius: 8px;
  background: rgba(var(--color-brand-rgb), 0.12);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 口径提示:统计卡/预算/分类排行不受账户筛选影响,免得以为数字算错了 */
.rf-hint {
  font-size: 11px;
  color: var(--color-text-tertiary, #b5a98c);
  white-space: nowrap;
}
/* 右栏:预算卡 + 分类排行纵向排列,窄屏跟随主栏顺序落到明细下方;
   sticky 让明细滚动时预算与超支常驻视口(暖居顶栏在文档流内,两主题同一偏移) */
.side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  position: sticky;
  top: 16px;
  align-self: start;
}
/* 明细标题行右侧的「共 N 笔」(无筛选时),与筛选态的「只看 X · N 笔」同一视觉位 */
.records-count {
  font-size: 12px;
  color: var(--color-text-tertiary, #b5a98c);
  white-space: nowrap;
}
/* 日期头:次要色小字 + 当日小计(右对齐);第一组头顶格起,与「明细」标题只隔标题自身边距 */
.day-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 0 4px;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.day-head:first-child {
  padding-top: 0;
}
.dh-sum {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.dh-num {
  font-weight: 600;
}
.dh-num.is-expense {
  color: var(--color-expense);
}
.dh-num.is-income {
  color: var(--color-income);
}
.dh-transfer {
  font-size: 11px;
  color: var(--color-text-tertiary, #b5a98c);
}
.record-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border);
}
/* 组内虚线只在行间:每组最后一行压线,把边界让给下一个日期头 */
.record-row.is-last {
  border-bottom: none;
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
  color: var(--color-income);
}
.record-cat.t2 {
  background: rgba(230, 162, 60, 0.14);
  color: var(--color-warning);
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
  color: var(--color-text-tertiary);
}
.record-amount {
  font-weight: 600;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.record-amount.t0 {
  color: var(--color-expense);
}
.record-amount.t1 {
  color: var(--color-income);
}
.record-amount.t2 {
  color: var(--color-warning);
}
.record-actions {
  flex-shrink: 0;
  transition: opacity 0.15s;
}
.record-actions :deep(.el-button) {
  padding: 5px 6px;
}
.record-actions :deep(.el-button + .el-button) {
  margin-left: 4px;
}
/* 桌面端悬停/键盘聚焦才亮出改删(触屏常显):只动透明度不做 display,键盘 Tab 不会丢失 */
@media (hover: hover) {
  .record-row .record-actions {
    opacity: 0;
  }
  .record-row:hover .record-actions,
  .record-row:focus-within .record-actions {
    opacity: 1;
  }
}
.form-row {
  display: flex;
  gap: 12px;
}

@media (max-width: 768px) {
  .content {
    grid-template-columns: 1fr;
  }
  /* 单列下 sticky 无意义(内容就一行),退回文档流 */
  .side {
    position: static;
  }
  .form-row {
    flex-direction: column;
    gap: 0;
  }
  /* 窄屏只留「只看 X · N 笔」与清除按钮,口径提示隐藏,免得标题行换行占太多高度 */
  .rf-hint {
    display: none;
  }
}
</style>
