<!-- 记账本页:四页签外壳(记账本/总账/贷款记录/理财),月份与页签在此持有,供总账点月钻取回记账本 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('book.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <!-- 页签在窄屏横向滚动(不换行;V10.80 加「理财」后四页签也挤不下) -->
        <div class="book-tabs">
          <el-radio-group v-model="tab" size="small">
            <el-radio-button value="book">{{ $t('book.tabBook') }}</el-radio-button>
            <el-radio-button value="ledger">{{ $t('book.tabLedger') }}</el-radio-button>
            <el-radio-button value="loan">{{ $t('book.tabLoan') }}</el-radio-button>
            <el-radio-button value="finance">{{ $t('book.tabFinance') }}</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </PageToolbar>

    <!-- 记账本页签:明细/统计/账户都在 BookDaily 里,月份走 v-model 双向同步 -->
    <BookDaily v-if="tab === 'book'" v-model:month="month" />

    <!-- 总账页签:总账组件异步引入,点某月由它 emit 回来钻取到记账本 -->
    <BookLedger v-else-if="tab === 'ledger'" @pick-month="onPickMonth" />

    <!-- 贷款记录:与工具箱/贷款计算器同一组件同一份数据(家庭共享) -->
    <LoanRecords v-else-if="tab === 'loan'" />

    <!-- 理财:持仓卡片 + 到期日历,本金不计支出、收益在赎回时按勾选入账 -->
    <FinancePanel v-else />
  </div>
</template>

<script setup>
// 记账本外壳:页签切换 + 月份状态;各页签自己管数据与工具栏右侧操作
import { defineAsyncComponent, ref } from 'vue'

import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import BookDaily from '@/views/book/BookDaily.vue'

// 贷款记录与工具箱/贷款计算器是同一组件同一份家庭数据;异步加载,首屏(记账本)不为它买单
const LoanRecords = defineAsyncComponent(() => import('@/views/tools/LoanRecords.vue'))
// 总账只在切到该页签时加载,记账本用户的首次进入不为它买单
const BookLedger = defineAsyncComponent(() => import('@/views/book/BookLedger.vue'))
// 理财同样按需加载(计算内核 + 4 个组件不进首页 chunk)
const FinancePanel = defineAsyncComponent(() => import('@/views/book/finance/FinancePanel.vue'))

const tab = ref('book')
const month = ref(new Date().toISOString().slice(0, 7))

// 总账页签点某月 → 回到记账本并定位该月
const onPickMonth = (m) => {
  month.value = m
  tab.value = 'book'
}
</script>

<style scoped>
/* 页签横向滚动(窄屏/多页签时不换行、不撑破工具栏);EP 自身的配色圆角仍走全局 main.css */
.book-tabs {
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.book-tabs::-webkit-scrollbar {
  display: none;
}
.book-tabs :deep(.el-radio-group) {
  flex-wrap: nowrap;
}
.book-tabs :deep(.el-radio-button) {
  flex-shrink: 0;
}
</style>
