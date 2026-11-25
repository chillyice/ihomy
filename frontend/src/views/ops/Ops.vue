<template>
  <div class="page ops-page">
    <Breadcrumb v-if="!userStore.isPureOps" :items="[{ label: $t('mobile.title.ops') }]" />
    <div class="ops-directory">
      <header v-if="userStore.isPureOps" class="console-header">
        <strong>{{ $t('mobile.title.ops') }}</strong>
        <el-button text @click="logout">{{ $t('nav.logout') }}</el-button>
      </header>
      <nav v-if="groups.length > 1" class="workspace-nav" :aria-label="$t('mobile.title.ops')">
        <button
          v-for="g in groups"
          :key="g.key"
          type="button"
          :aria-current="g === group ? 'true' : undefined"
          @click="go(g === group ? tab : g.items[0].key)"
        >
          <el-icon><component :is="g.icon" /></el-icon>
          {{ $t(g.label) }}
        </button>
      </nav>
      <nav class="section-nav" :aria-label="$t(group.label)">
        <button
          v-for="item in group.items"
          :key="item.key"
          type="button"
          :aria-current="tab === item.key ? 'page' : undefined"
          @click="go(item.key)"
        >
          {{ $t(item.label) }}<span v-if="item.key === 'alerts' && summary?.open" class="alert-count">{{ summary.open }}</span>
        </button>
      </nav>
    </div>
    <button
      v-if="tab === 'stats'"
      type="button"
      class="alert-entry paper-surface"
      :class="{ pending: summary?.open }"
      @click="go('alerts')"
    >
      <el-icon><Bell /></el-icon>
      <span class="alert-text"
        ><span>{{ $t('ops.alerts') }}</span
        ><strong>{{
          summary ? $t(summary.open ? 'ops.pendingAlerts' : 'ops.noPendingAlerts', { n: summary.open }) : $t('ops.viewAlerts')
        }}</strong></span
      >
      <el-icon><ArrowRight /></el-icon>
    </button>
    <template v-for="item in available" :key="item.key">
      <section v-if="visited.has(item.key)" v-show="tab === item.key" class="ops-panel" :aria-label="$t(item.label)">
        <component :is="item.panel" v-bind="panelProps(item.key)" v-on="panelEvents(item.key)" />
      </section>
    </template>
  </div>
</template>
<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Monitor, Tickets, Connection, Tools, Bell, ArrowRight } from '@element-plus/icons-vue'
import { opsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import OpsStats from './tabs/OpsStats.vue'
import OpsServer from './tabs/OpsServer.vue'
import OpsTraffic from './tabs/OpsTraffic.vue'
import OpsLogs from './tabs/OpsLogs.vue'
import OpsTrace from './tabs/OpsTrace.vue'
import OpsAlerts from './tabs/OpsAlerts.vue'
import OpsKada from './tabs/OpsKada.vue'
import OpsAi from './tabs/OpsAi.vue'
import OpsWeather from './tabs/OpsWeather.vue'
import OpsOss from './tabs/OpsOss.vue'
import OpsBackup from './tabs/OpsBackup.vue'

const catalog = [
  {
    key: 'monitor',
    label: 'ops.monitorGroup',
    icon: Monitor,
    items: [
      { key: 'stats', label: 'ops.overview', panel: OpsStats },
      { key: 'server', label: 'ops.server', panel: OpsServer },
      { key: 'traffic', label: 'ops.traffic', panel: OpsTraffic },
    ],
  },
  {
    key: 'diagnose',
    label: 'ops.diagnoseGroup',
    icon: Tickets,
    items: [
      { key: 'alerts', label: 'ops.alerts', panel: OpsAlerts },
      { key: 'logs', label: 'ops.logs', panel: OpsLogs },
      { key: 'trace', label: 'ops.traceLogs', panel: OpsTrace },
    ],
  },
  {
    key: 'services',
    label: 'ops.servicesGroup',
    icon: Connection,
    items: [
      { key: 'ai', label: 'ops.ai', panel: OpsAi },
      { key: 'weather', label: 'ops.weather', panel: OpsWeather },
    ],
  },
  {
    key: 'maintain',
    label: 'ops.maintainGroup',
    icon: Tools,
    items: [
      { key: 'backup', label: 'ops.backup.title', panel: OpsBackup },
      { key: 'oss', label: 'ops.oss.title', panel: OpsOss },
      { key: 'kada', label: 'ops.kada.title', panel: OpsKada },
    ],
  },
]
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const groups = computed(() => catalog.filter((g) => userStore.isOps || g.key === 'services'))
const available = computed(() => groups.value.flatMap((g) => g.items))
const tab = computed(() => available.value.find((item) => item.key === route.query.tab)?.key || available.value[0].key)
const group = computed(() => groups.value.find((g) => g.items.some((item) => item.key === tab.value)))
const visited = reactive(new Set())
watch(tab, (key) => visited.add(key), { immediate: true })

function logout() {
  userStore.logout()
  router.replace({ name: 'Login' })
}

function go(key, extra = {}) {
  if (key === tab.value && !extra.tid) return
  const query = { ...route.query, tab: key }
  delete query.tid
  delete query.date
  router.push({ query: { ...query, ...extra } })
}

const summary = ref(null)
async function loadSummary() {
  try {
    summary.value = await opsApi.alertSummary()
  } catch {
    summary.value = null
  }
}
watch(
  () => userStore.isOps,
  (enabled) => {
    if (enabled) loadSummary()
  },
  { immediate: true },
)
const tracePreset = computed(() => ({
  tid: String(route.query.tid || ''),
  date: String(route.query.date || ''),
  nonce: route.fullPath,
}))
function panelProps(key) {
  if (key === 'trace') return { preset: tracePreset.value }
  if (key === 'alerts') return { active: key === tab.value, summary: summary.value || {} }
  if (key === 'server' || key === 'kada') return { active: key === tab.value }
  return {}
}
const navigateTrace = ({ tid, date }) => go('trace', date ? { tid, date } : { tid })
function panelEvents(key) {
  if (key === 'alerts') return { navigateTrace, refreshSummary: loadSummary }
  if (key === 'logs' || key === 'traffic') return { navigateTrace }
  return {}
}
</script>
<style scoped>
.console-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.console-header strong {
  font-size: 20px;
}
.workspace-nav,
.section-nav {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.workspace-nav {
  border-bottom: 1px solid var(--color-border);
  padding: 6px 0 13px;
}
.workspace-nav button,
.section-nav button,
.alert-entry {
  font: inherit;
  cursor: pointer;
  color: var(--color-text-secondary);
}
.workspace-nav button,
.section-nav button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: transparent;
  border: 0;
  border-radius: 10px;
  padding: 9px 14px;
}
.workspace-nav button[aria-current] {
  color: var(--color-brand-text);
  background: var(--color-brand);
  font-weight: 650;
}
.section-nav {
  margin: 12px 0 20px;
}
.section-nav button {
  font-size: 14px;
  border-radius: 8px;
}
.section-nav button[aria-current],
.workspace-nav button:not([aria-current]):hover,
.section-nav button:hover {
  background: var(--el-color-primary-light-9);
  color: var(--color-brand);
}
.section-nav button[aria-current] {
  font-weight: 700;
}
.alert-count {
  font-variant-numeric: tabular-nums;
  color: var(--el-color-danger);
}
.alert-entry {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 16px;
  text-align: start;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-inline-start: 4px solid var(--color-brand);
  border-radius: 14px;
  padding: 18px 22px;
  margin-bottom: 22px;
}
.alert-entry.pending {
  border-inline-start-color: var(--el-color-warning);
}
.alert-entry > .el-icon:first-child {
  font-size: 22px;
  color: var(--color-brand);
}
.alert-text {
  flex: 1;
  display: grid;
  gap: 4px;
  font-size: 12px;
}
.alert-text strong {
  font-size: clamp(18px, 2vw, 24px);
  color: var(--color-text);
}
.ops-panel {
  min-width: 0;
  container-type: inline-size;
}
.ops-panel :deep(.el-pagination) {
  flex-wrap: wrap;
  row-gap: 6px;
}
/* 暖居桌面的书布导航与纸签，沿用全站晨暮材质。 */
html.warm-paper-page .ops-page {
  color: var(--color-text);
}
html.warm-paper-page .ops-directory {
  margin-bottom: 20px;
  background-color: var(--color-card-2);
  background-image: var(--album-cloth-texture);
  background-size: 128px 128px;
  background-blend-mode: soft-light;
  border: 1px solid var(--album-fold);
  border-radius: 12px 19px 16px 10px;
  box-shadow:
    0 2px var(--album-paper),
    0 3px var(--album-fold);
}
html.warm-paper-page .console-header {
  margin: 0;
  padding: 14px clamp(14px, 2vw, 24px) 2px;
}
html.warm-paper-page .workspace-nav {
  padding: 10px clamp(10px, 1.5vw, 20px);
  border: 0;
}
html.warm-paper-page .workspace-nav button {
  color: var(--color-text);
}
html.warm-paper-page .workspace-nav button[aria-current] {
  background-color: var(--color-card);
  background-image: var(--caption-paper);
  background-size: auto, var(--home-grain-size);
  color: var(--color-brand);
  box-shadow:
    inset 0 0 0 1px var(--mat-edge),
    0 2px 3px var(--album-fold);
}
html.warm-paper-page .section-nav {
  margin: 0;
  padding: 6px clamp(10px, 1.5vw, 20px);
  border-radius: inherit;
  background-color: var(--color-card);
  background-image: var(--caption-paper);
  background-size: auto, var(--home-grain-size);
}
html.warm-paper-page .workspace-nav + .section-nav {
  border-radius: 0 0 16px 10px;
  border-top: 1px solid var(--album-fold);
}
html.warm-paper-page .section-nav button[aria-current] {
  box-shadow: inset 0 -2px var(--color-brand);
  border-radius: 4px;
}
html.warm-paper-page .alert-entry {
  border-radius: 6px 12px 12px 6px;
  box-shadow: 0 1px 3px var(--album-fold);
}
html.warm-paper-page .ops-panel :deep(:is(.finance-card, .metric-card, .backup-stat, .stat-card)) {
  background-color: var(--color-card);
  background-image: var(--caption-paper);
  background-size: auto, var(--home-grain-size);
  box-shadow: inset 0 0 0 1px var(--color-border);
}
html.warm-paper-page .ops-panel :deep(.chart-wrap) {
  background-color: var(--color-card);
  box-shadow: inset 0 0 0 1px var(--color-border);
}
@media (max-width: 600px) {
  .workspace-nav {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .workspace-nav button {
    justify-content: flex-start;
  }
  .section-nav {
    gap: 2px;
  }
  .section-nav button {
    padding: 8px;
  }
}
</style>
