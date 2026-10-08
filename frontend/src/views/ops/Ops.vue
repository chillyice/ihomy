<!-- 运维管理页(V3.8):仅 OPS 角色可见。标签聚合外壳:
     资源总览 / 服务器状态 / 访问统计 / 操作日志 / 详细日志 / 异常预警 / AI 统计 / 和风天气 / 开源组件台账。
     各标签内容拆为 tabs/ 下的子组件,首次切到该标签才挂载(onMounted 取数),离开后保留状态。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('ops.log') }]" />
    <el-tabs v-model="tab" class="ops-tabs">
      <el-tab-pane v-if="showSystemTabs" :label="$t('ops.overview')" name="stats" lazy>
        <OpsStats />
      </el-tab-pane>

      <el-tab-pane v-if="showSystemTabs" :label="$t('ops.server')" name="server" lazy>
        <OpsServer :active="tab === 'server'" />
      </el-tab-pane>

      <el-tab-pane v-if="showSystemTabs" :label="$t('ops.traffic')" name="traffic" lazy>
        <OpsTraffic />
      </el-tab-pane>

      <el-tab-pane v-if="showSystemTabs" :label="$t('ops.logs')" name="logs" lazy>
        <OpsLogs @navigate-trace="navigateTrace" />
      </el-tab-pane>

      <el-tab-pane v-if="showSystemTabs" :label="$t('ops.traceLogs')" name="trace" lazy>
        <OpsTrace :preset="tracePreset" />
      </el-tab-pane>

      <el-tab-pane v-if="showSystemTabs" name="alerts" lazy>
        <template #label>
          <span
            >{{ $t('ops.alerts')
            }}<em v-if="alertSummary.open" class="alert-count">{{ alertSummary.open > 99 ? '99+' : alertSummary.open }}</em></span
          >
        </template>
        <OpsAlerts :summary="alertSummary" :active="tab === 'alerts'" @navigate-trace="navigateTrace" @refresh-summary="loadAlertSummary" />
      </el-tab-pane>

      <el-tab-pane :label="$t('ops.ai')" name="ai" lazy>
        <OpsAi />
      </el-tab-pane>

      <el-tab-pane :label="$t('ops.weather')" name="weather" lazy>
        <OpsWeather />
      </el-tab-pane>

      <el-tab-pane v-if="showSystemTabs" :label="$t('ops.oss.title')" name="oss" lazy>
        <OpsOss />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { opsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import OpsStats from './tabs/OpsStats.vue'
import OpsServer from './tabs/OpsServer.vue'
import OpsTraffic from './tabs/OpsTraffic.vue'
import OpsLogs from './tabs/OpsLogs.vue'
import OpsTrace from './tabs/OpsTrace.vue'
import OpsAlerts from './tabs/OpsAlerts.vue'
import OpsAi from './tabs/OpsAi.vue'
import OpsWeather from './tabs/OpsWeather.vue'
import OpsOss from './tabs/OpsOss.vue'

// 运维管理:数据接口须 ops:view 权限,后端 OpsAccessFilter 还会把 OPS 角色限定在 /ops 与 /auth
const route = useRoute()
const userStore = useUserStore()

// 角色渲染:家长(OWNER 非 OPS)只见「AI 统计」「天气」;OPS 见全部标签(含系统级报表)
const isOps = computed(() => userStore.isOps)
const showSystemTabs = computed(() => isOps.value)

const tab = ref(isOps.value ? 'stats' : 'ai')

// 角标与异常预警卡片共用同一份汇总数(进入运维页即取,不必切到该标签)
const alertSummary = ref({ open: 0, today: 0, total: 0 })
const loadAlertSummary = async () => {
  try {
    alertSummary.value = await opsApi.alertSummary()
  } catch (e) {
    /* 角标失败不阻塞其它标签 */
  }
}

// 操作日志 / 异常预警里的 TID 点击 → 切到「详细日志」标签并按该日期直接查询(nonce 触发子组件重查)
const tracePreset = reactive({ tid: '', date: '', nonce: 0 })
const navigateTrace = ({ tid, date }) => {
  tracePreset.tid = tid
  tracePreset.date = date || ''
  tracePreset.nonce++
  tab.value = 'trace'
}

onMounted(() => {
  if (!isOps.value) return
  // 支持 /ops?tab=trace&tid=xxx&date=yyyy-MM-dd 或 /ops?tab=traffic 直达(分享/书签)
  if (route.query.tab === 'trace' && route.query.tid) {
    tab.value = 'trace'
    tracePreset.tid = String(route.query.tid)
    tracePreset.date = route.query.date ? String(route.query.date) : ''
  }
  if (route.query.tab === 'traffic') tab.value = 'traffic'
  if (route.query.tab === 'alerts') tab.value = 'alerts'
  loadAlertSummary()
})
</script>

<style scoped>
/* 标签页头随滚动冻结:光尘下窗口滚动,顶到 sticky 面包屑下方(42px);暖居下 gc-main 内部滚动,
 * 顶到内容上边界(0,由 main.css html.theme-warm 覆写)。背景取页面底色,盖住滚过的卡片。 */
.ops-tabs :deep(.el-tabs__header) {
  position: sticky;
  top: 42px;
  z-index: 15;
  background: var(--color-bg);
}
/* 异常预警:标签页角标 */
.alert-count {
  display: inline-block;
  margin-left: 6px;
  padding: 0 6px;
  border-radius: 9px;
  background: var(--color-accent);
  color: #fff;
  font-size: 11px;
  font-style: normal;
  line-height: 16px;
  vertical-align: 1px;
}
</style>
