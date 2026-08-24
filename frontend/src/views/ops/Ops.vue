<!-- 运维管理页(V3.8):仅 OPS 角色可见。三个标签页:
     资源总览(各表数量+时间/用户/家庭过滤)、服务器状态(JVM/磁盘)、操作日志检索 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('ops.log') }]" />
    <el-tabs v-model="tab">
      <el-tab-pane :label="$t('ops.overview')" name="stats">
        <div class="filter-row">
          <el-date-picker v-model="filter.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.startDate')" />
          <el-date-picker v-model="filter.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.endDate')" />
          <el-input v-model.number="filter.userId" :placeholder="$t('ops.userId')" style="width: 140px" />
          <el-input v-model.number="filter.familyId" :placeholder="$t('ops.familyId')" style="width: 140px" />
          <el-button type="primary" @click="loadStats">{{ $t('ops.query') }}</el-button>
          <el-button @click="resetFilter">{{ $t('ops.reset') }}</el-button>
        </div>
        <div v-loading="statsLoading" class="stats-grid">
          <div v-for="row in statCards" :key="row.key" class="stat-card card">
            <div class="stat-name">{{ row.name }}</div>
            <div class="stat-num">{{ stats[row.key] ?? 0 }}</div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('ops.server')" name="server">
        <div v-loading="serverLoading">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
            :title="$t('ops.alertText')" />
          <div class="server-row">
            <div class="card server-block">
              <h3>JVM</h3>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item :label="$t('ops.javaVersion')">{{ server.jvm?.javaVersion }}</el-descriptions-item>
                <el-descriptions-item :label="$t('ops.heapUsed')">{{ fmtMb(server.jvm?.heapUsed) }} MB</el-descriptions-item>
                <el-descriptions-item :label="$t('ops.heapMax')">{{ fmtMb(server.jvm?.heapMax) }} MB</el-descriptions-item>
                <el-descriptions-item :label="$t('ops.threads')">{{ server.jvm?.threads }}</el-descriptions-item>
                <el-descriptions-item :label="$t('ops.uptime')">{{ fmtUptime(server.jvm?.uptimeSec) }}</el-descriptions-item>
              </el-descriptions>
            </div>
            <div class="card">
              <h3>{{ $t('ops.os') }}</h3>
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item :label="$t('ops.system')">{{ server.os?.name }} ({{ server.os?.arch }})</el-descriptions-item>
                <el-descriptions-item :label="$t('ops.cores')">{{ server.os?.cores }}</el-descriptions-item>
                <el-descriptions-item :label="$t('ops.load')">{{ server.os?.loadAvg ?? 'N/A' }}</el-descriptions-item>
                <el-descriptions-item :label="$t('ops.time')">{{ server.time }}</el-descriptions-item>
              </el-descriptions>
              <h3 style="margin-top: 16px">{{ $t('ops.disk') }}</h3>
              <el-descriptions :column="1" border size="small" v-for="d in server.disks" :key="d.path">
                <el-descriptions-item :label="d.path">{{ $t('ops.diskInfo', { free: fmtMb(d.free), total: fmtMb(d.total) }) }}</el-descriptions-item>
              </el-descriptions>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('ops.logs')" name="logs">
        <div class="filter-row">
          <el-input v-model="logFilter.keyword" :placeholder="$t('ops.logKeyword')" clearable style="width: 200px" @keyup.enter="loadLogs(1)" />
          <el-input v-model.number="logFilter.operatorId" :placeholder="$t('ops.operatorId')" style="width: 130px" />
          <el-input v-model="logFilter.module" :placeholder="$t('ops.module')" style="width: 120px" />
          <el-date-picker v-model="logFilter.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.startDate')" />
          <el-date-picker v-model="logFilter.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.endDate')" />
          <el-button type="primary" @click="loadLogs(1)">{{ $t('ops.query') }}</el-button>
        </div>
        <el-table v-loading="logsLoading" :data="logPage.records" border stripe>
          <el-table-column prop="createdAt" :label="$t('ops.time')" width="165" />
          <el-table-column prop="operatorName" :label="$t('ops.operator')" width="110" />
          <el-table-column prop="module" :label="$t('ops.module')" width="90" />
          <el-table-column prop="operationType" :label="$t('ops.type')" width="90" />
          <el-table-column prop="description" :label="$t('ops.description')" min-width="180" show-overflow-tooltip />
          <el-table-column prop="requestUrl" label="URL" min-width="160" show-overflow-tooltip />
          <el-table-column prop="resultStatus" :label="$t('ops.result')" width="70">
            <template #default="{ row }">
              <el-tag :type="row.resultStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
                {{ row.resultStatus === 'SUCCESS' ? $t('ops.success') : $t('ops.fail') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="costTime" :label="$t('ops.costTime')" width="90" />
        </el-table>
        <el-pagination v-model:current-page="logPageNum" :page-size="20" :total="logTotal"
          layout="total, prev, pager, next" style="margin-top: 14px; justify-content: flex-end" @current-change="loadLogs" />
      </el-tab-pane>

      <el-tab-pane label="和风天气 API" name="weather">
        <div v-loading="weatherLoading">
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px"
            title="和风天气控制台 API:用量统计 + 财务汇总 + 请求量统计(JWT 身份认证)" />

          <!-- 调用折线图 -->
          <h4 class="ops-section-title">API 调用趋势</h4>
          <div class="timeline-controls">
            <el-radio-group v-model="timelineRange" size="small" @change="loadTimeline">
              <el-radio-button value="24h">24小时</el-radio-button>
              <el-radio-button value="month">本月</el-radio-button>
              <el-radio-button value="30d">30天</el-radio-button>
              <el-radio-button value="year">一年</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="timelineData.length" class="chart-wrap">
            <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="line-chart" preserveAspectRatio="none">
              <line v-for="(t, i) in yTicks" :key="'grid'+i" :x1="padL" :x2="chartW - padR" :y1="t.y" :y2="t.y" stroke="var(--color-border)" stroke-width="1" stroke-dasharray="3 3" />
              <text v-for="(t, i) in yTicks" :key="'yl'+i" :x="padL - 8" :y="t.y + 4" text-anchor="end" fill="var(--color-text-secondary)" font-size="11">{{ t.label }}</text>
              <text v-for="(lb, i) in xLabels" :key="'xl'+i" :x="xPos(i)" :y="chartH - padB + 16" text-anchor="middle" fill="var(--color-text-secondary)" font-size="11">{{ lb }}</text>
              <polyline :points="linePoints(timelineData.map(d => d.total))" fill="none" stroke="#b88c6e" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
              <polyline :points="linePoints(timelineData.map(d => d.failed))" fill="none" stroke="#b04a3a" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
            </svg>
            <div class="chart-legend">
              <span class="legend-item"><span class="legend-dot" style="background:#b88c6e"></span>调用总量</span>
              <span class="legend-item"><span class="legend-dot" style="background:#b04a3a"></span>失败总量</span>
            </div>
          </div>
          <el-empty v-else description="暂无调用记录" :image-size="40" />

          <!-- 用量统计 -->
          <h4 class="ops-section-title" style="margin-top:20px">API 用量</h4>
          <div v-if="weatherQuota && weatherQuota.raw">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item v-for="(v, k) in weatherQuota.raw" :key="k" :label="k">{{ typeof v === 'object' ? JSON.stringify(v) : v }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else-if="!weatherLoading" description="未配置和风天气凭证或暂无数据" :image-size="40" />

          <!-- 财务汇总 -->
          <h4 class="ops-section-title" style="margin-top:20px">财务汇总</h4>
          <div v-if="weatherFinance" class="finance-grid">
            <div class="finance-card">
              <div class="finance-label">余额</div>
              <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.balance ?? '-' }}</div>
            </div>
            <div class="finance-card">
              <div class="finance-label">本月消费</div>
              <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.thisMonth ?? '0' }}</div>
            </div>
            <div class="finance-card">
              <div class="finance-label">昨日消费</div>
              <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.previousDay ?? '0' }}</div>
            </div>
          </div>
          <el-alert v-else type="warning" :closable="false" show-icon style="margin-top:8px"
            title="财务数据获取失败(需在和风控制台开启权限)" />

          <!-- 请求量统计 -->
          <h4 class="ops-section-title" style="margin-top:20px">24h 请求量统计</h4>
          <div v-if="weatherStats">
            <el-table :data="weatherStats.success || []" size="small" stripe>
              <el-table-column prop="api" label="API" />
              <el-table-column label="24h 成功请求">
                <template #default="{ row }">{{ (row.hours || []).reduce((a, b) => a + b, 0) }}</template>
              </el-table-column>
            </el-table>
            <div v-if="weatherStats.errors && weatherStats.errors.length" style="margin-top:12px">
              <div class="ops-sub-title">错误请求</div>
              <el-table :data="weatherStats.errors" size="small" stripe>
                <el-table-column prop="api" label="API" />
                <el-table-column label="24h 错误">
                  <template #default="{ row }">{{ (row.hours || []).reduce((a, b) => a + b, 0) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </div>
          <el-alert v-else type="warning" :closable="false" show-icon style="margin-top:8px"
            title="请求量统计获取失败(需在和风控制台开启权限)" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
// 运维管理:三标签聚合页;数据接口须 ops:view 权限,后端 OpsAccessFilter 还会把 OPS 角色限定在 /ops 与 /auth
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()

// 资源卡片定义(键与后端 stats 返回字段一一对应,名称走 i18n)
const STAT_CARDS = [
  'users', 'families', 'blogs', 'diaries', 'albums', 'photos', 'videos',
  'comments', 'likes', 'checkins', 'plans', 'wishes', 'bookRecords',
  'reminders', 'operationLogs',
]
const statCards = STAT_CARDS.map(key => ({ key, name: t('ops.stat.' + key) }))

const tab = ref('stats')
const stats = ref({})
const statsLoading = ref(false)
const filter = reactive({ startDate: '', endDate: '', userId: '', familyId: '' })

const server = ref({})
const serverLoading = ref(false)

const logsLoading = ref(false)
const logPage = ref({ records: [] })
const logTotal = ref(0)
const logPageNum = ref(1)
const logFilter = reactive({ keyword: '', operatorId: '', module: '', startDate: '', endDate: '' })

const weatherLoading = ref(false)
const weatherQuota = ref(null)
const weatherFinance = ref(null)
const weatherStats = ref(null)

const timelineRange = ref('24h')
const timelineData = ref([])
const chartW = 800
const chartH = 280
const padL = 40
const padR = 20
const padB = 30
const padT = 20

const yMax = computed(() => {
  const mx = Math.max(...timelineData.value.map(d => d.total), 1)
  return mx <= 5 ? 5 : Math.ceil(mx / 5) * 5
})
const yTicks = computed(() => {
  const ticks = []
  for (let i = 0; i <= 4; i++) {
    const val = Math.round(yMax.value * i / 4)
    ticks.push({ y: chartH - padB - (chartH - padB - padT) * i / 4, label: val })
  }
  return ticks
})
const xLabels = computed(() => {
  const n = timelineData.value.length
  if (n === 0) return []
  const step = Math.max(1, Math.ceil(n / 8))
  const labels = []
  for (let i = 0; i < n; i += step) labels.push(timelineData.value[i].time_bucket)
  if ((n - 1) % step !== 0) labels.push(timelineData.value[n - 1].time_bucket)
  const indices = []
  for (let i = 0; i < n; i += step) indices.push(i)
  if ((n - 1) % step !== 0) indices.push(n - 1)
  return indices.map(i => timelineData.value[i].time_bucket)
})
const xPos = (i) => {
  const n = timelineData.value.length
  if (n <= 1) return padL
  return padL + (chartW - padL - padR) * i / (n - 1)
}
const linePoints = (vals) => {
  const n = vals.length
  if (n === 0) return ''
  return vals.map((v, i) => {
    const x = n <= 1 ? padL : padL + (chartW - padL - padR) * i / (n - 1)
    const y = chartH - padB - (chartH - padB - padT) * v / yMax.value
    return `${x},${y}`
  }).join(' ')
}

const loadTimeline = async () => {
  try {
    timelineData.value = await opsApi.weatherTimeline(timelineRange.value)
  } catch (e) {
    timelineData.value = []
  }
}

const fmtMb = (bytes) => (bytes == null ? 0 : Math.round(bytes / 1024 / 1024))
const fmtUptime = (sec) => {
  if (!sec) return 'N/A'
  const d = Math.floor(sec / 86400)
  const h = Math.floor((sec % 86400) / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return t('ops.uptimeFormat', { d, h, m })
}

const loadStats = async () => {
  statsLoading.value = true
  try {
    stats.value = await opsApi.stats({
      startDate: filter.startDate || null,
      endDate: filter.endDate || null,
      userId: filter.userId || null,
      familyId: filter.familyId || null,
    })
  } finally {
    statsLoading.value = false
  }
}

const resetFilter = () => {
  Object.assign(filter, { startDate: '', endDate: '', userId: '', familyId: '' })
  loadStats()
}

const loadServer = async () => {
  serverLoading.value = true
  try {
    server.value = await opsApi.server()
  } finally {
    serverLoading.value = false
  }
}

const loadLogs = async (page = logPageNum.value) => {
  logsLoading.value = true
  logPageNum.value = page
  try {
    const data = await opsApi.logs({
      current: page,
      size: 20,
      keyword: logFilter.keyword || null,
      operatorId: logFilter.operatorId || null,
      module: logFilter.module || null,
      startDate: logFilter.startDate || null,
      endDate: logFilter.endDate || null,
    })
    logPage.value = data
    logTotal.value = data.total
  } finally {
    logsLoading.value = false
  }
}

const loadWeatherQuota = async () => {
  weatherLoading.value = true
  try {
    const [quota, finance, stats] = await Promise.allSettled([
      opsApi.weatherQuota(),
      opsApi.weatherFinance(),
      opsApi.weatherStats(),
    ])
    weatherQuota.value = quota.status === 'fulfilled' ? quota.value : null
    weatherFinance.value = finance.status === 'fulfilled' ? finance.value : null
    weatherStats.value = stats.status === 'fulfilled' ? stats.value : null
  } catch (e) {
    weatherQuota.value = null
    weatherFinance.value = null
    weatherStats.value = null
  } finally {
    weatherLoading.value = false
  }
  loadTimeline()
}

onMounted(async () => {
  await loadStats()
  // 默认取一页近期日志(时间倒序分页,天然轻量)
  await loadLogs(1)
})

// 切到天气标签页时加载配额
watch(tab, (v) => { if (v === 'weather' && !weatherQuota.value) loadWeatherQuota() })
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}
.stat-card {
  padding: 14px 16px;
  text-align: center;
}
.stat-name {
  color: #999;
  font-size: 12px;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}
.server-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.server-row h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
@media (max-width: 768px) {
  .stats-grid { grid-template-columns: repeat(3, 1fr); }
  .server-row { grid-template-columns: 1fr; }
}
.ops-section-title { font-size: 14px; font-weight: 600; margin: 0 0 10px; color: var(--color-text); }
.ops-sub-title { font-size: 13px; font-weight: 500; margin: 0 0 6px; color: var(--color-text-secondary); }
.finance-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.finance-card { padding: 14px 16px; text-align: center; background: var(--color-card-2); border-radius: 10px; }
.finance-label { font-size: 12px; color: var(--color-text-secondary); margin-bottom: 6px; }
.finance-value { font-size: 18px; font-weight: 700; font-variant-numeric: tabular-nums; }
.timeline-controls { margin-bottom: 12px; }
.chart-wrap { background: var(--color-card-2); border-radius: 10px; padding: 16px; }
.line-chart { width: 100%; height: 260px; display: block; }
.chart-legend { display: flex; gap: 20px; justify-content: center; margin-top: 8px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text-secondary); }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; }
</style>