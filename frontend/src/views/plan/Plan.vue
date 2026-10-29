<!-- 家庭日程壳页:今日 / 计划 / 纪念日 三页签;路由 query.tab 决定当前页签,游客只看纪念日 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('plan.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <div class="plan-tabs">
          <el-radio-group v-model="tab" size="small">
            <el-radio-button value="today">{{ $t('plan.tabs.today') }}</el-radio-button>
            <el-radio-button value="plan">{{ $t('plan.tabs.plan') }}</el-radio-button>
            <el-radio-button value="anniversary">{{ $t('plan.tabs.anniversary') }}</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <div class="tb-right">
        <el-button v-if="canEdit" type="primary" @click="onCreate">{{ createLabel }}</el-button>
      </div>
    </PageToolbar>

    <TodayTab v-if="tab === 'today'" ref="todayTab" />
    <PlanTab v-else-if="tab === 'plan'" ref="planTab" :focus="focusId" />
    <AnniversaryTab v-else ref="anniTab" />
  </div>
</template>

<script setup>
// 三个页签各自管数据与弹窗;外壳只管页签切换、地址栏同步与工具栏右侧的「新建」按钮
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import TodayTab from './tabs/TodayTab.vue'
import PlanTab from './tabs/PlanTab.vue'
import AnniversaryTab from './tabs/AnniversaryTab.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const TABS = ['today', 'plan', 'anniversary']
const tabFromQuery = () => (TABS.includes(route.query.tab) ? route.query.tab : 'today')
// 计划行点进「计划」页签时带 focus=<id>,用于定位并高亮该卡片
const focusId = computed(() => (route.query.tab === 'plan' ? route.query.focus : null))
const tab = ref(tabFromQuery())

const todayTab = ref(null)
const planTab = ref(null)
const anniTab = ref(null)

const canEdit = computed(() => userStore.isLoggedIn && tab.value !== 'today')
const createLabel = computed(() => (tab.value === 'plan' ? t('plan.newPlan') : tab.value === 'anniversary' ? t('anniversary.add') : ''))

// 页签变化 → 写回地址栏(去掉上一页签遗留的 focus),便于分享/刷新保持同一页
watch(tab, (v) => {
  if (route.query.tab === v) return
  router.replace({ path: '/plan', query: { tab: v } })
})
// 地址栏变化(含浏览器前进后退、外部直达) → 切页签
watch(
  () => route.query.tab,
  () => {
    tab.value = tabFromQuery()
  },
)

const onCreate = () => {
  if (tab.value === 'plan') planTab.value?.openEditor()
  else if (tab.value === 'anniversary') anniTab.value?.openEditor()
}
</script>

<style scoped>
/* 页签在窄屏横向滚动(与记账本页同口径);EP 配色圆角由全局 main.css 负责 */
.plan-tabs {
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}
.plan-tabs::-webkit-scrollbar {
  display: none;
}
.plan-tabs :deep(.el-radio-group) {
  flex-wrap: nowrap;
}
.plan-tabs :deep(.el-radio-button) {
  flex-shrink: 0;
}
</style>
