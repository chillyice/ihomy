<!-- 运维管理页(V3.8):仅 OPS 角色可见。六个标签页:
      资源总览(各表数量+时间/用户/家庭过滤)、服务器状态(JVM/磁盘)、访问统计(access 日志扫描聚合+24h柱状图+接口Top)、
      操作日志检索(模块/类型/结果多选可搜索)、详细日志(tid 检索三类日志文件,来源/级别过滤)、和风天气 API -->
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

      <el-tab-pane :label="$t('ops.traffic')" name="traffic">
        <div class="filter-row">
          <el-date-picker v-model="trafficFilter.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.startDate')" />
          <el-date-picker v-model="trafficFilter.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.endDate')" />
          <el-button type="primary" @click="loadTraffic">{{ $t('ops.query') }}</el-button>
          <span class="traffic-hint">{{ $t('ops.trafficHint') }}</span>
        </div>
        <div v-loading="trafficLoading">
          <div v-if="traffic" class="stats-grid traffic-grid">
            <div v-for="c in trafficCards" :key="c.key" class="stat-card card">
              <div class="stat-name">{{ c.name }}</div>
              <div class="stat-num">{{ traffic[c.key] ?? 0 }}</div>
            </div>
          </div>
          <el-empty v-if="traffic && !traffic.total" :description="$t('ops.traceEmpty')" :image-size="40" />
          <template v-if="traffic && traffic.total">
            <h4 class="ops-section-title" style="margin-top: 20px">{{ $t('ops.hourly') }}</h4>
            <div class="chart-wrap">
              <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="line-chart">
                <line v-for="(tk, i) in tYTicks" :key="'tg'+i" :x1="padL" :x2="chartW - padR" :y1="tk.y" :y2="tk.y" stroke="var(--color-border)" stroke-width="1" stroke-dasharray="3 3" />
                <text v-for="(tk, i) in tYTicks" :key="'tl'+i" :x="padL - 8" :y="tk.y + 4" text-anchor="end" fill="var(--color-text-secondary)" font-size="11">{{ tk.label }}</text>
                <template v-for="(h, i) in traffic.hours" :key="'bar'+i">
                  <rect :x="barX(i)" :y="barY(h.total)" :width="barW" :height="barH(h.total)" fill="#b88c6e" rx="2" />
                  <rect v-if="h.failed" :x="barX(i)" :y="barY(h.failed)" :width="barW" :height="barH(h.failed)" fill="#b04a3a" rx="2" />
                </template>
                <text v-for="hx in [0, 3, 6, 9, 12, 15, 18, 21]" :key="'hx'+hx" :x="padL + (hx + 0.5) * barStep"
                  :y="chartH - padB + 16" text-anchor="middle" fill="var(--color-text-secondary)" font-size="11">{{ hx }}h</text>
              </svg>
              <div class="chart-legend">
                <span class="legend-item"><span class="legend-dot" style="background:#b88c6e"></span>{{ $t('ops.trafficTotal') }}</span>
                <span class="legend-item"><span class="legend-dot" style="background:#b04a3a"></span>{{ $t('ops.trafficFailed') }}</span>
              </div>
            </div>
            <h4 class="ops-section-title" style="margin-top: 20px">{{ $t('ops.topPaths') }}</h4>
            <el-table :data="traffic.topPaths" size="small" stripe>
              <el-table-column prop="path" :label="$t('ops.path')" min-width="280">
                <template #default="{ row }"><span class="mono">{{ row.path }}</span></template>
              </el-table-column>
              <el-table-column prop="count" :label="$t('ops.reqCount')" width="110" />
              <el-table-column prop="failed" :label="$t('ops.trafficFailed')" width="110" />
              <el-table-column prop="avgCostMs" :label="$t('ops.trafficAvgCost')" width="130" />
            </el-table>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('ops.logs')" name="logs">
        <div class="filter-row">
          <el-input v-model="logFilter.keyword" :placeholder="$t('ops.logKeyword')" clearable style="width: 200px" @keyup.enter="loadLogs(1)" />
          <el-input v-model.number="logFilter.operatorId" :placeholder="$t('ops.operatorId')" style="width: 130px" />
          <el-select v-model="logFilter.modules" multiple filterable clearable collapse-tags collapse-tags-tooltip
            :placeholder="$t('ops.module')" style="width: 180px" @change="loadLogs(1)">
            <el-option v-for="m in logOptions.modules" :key="m" :value="m" :label="m" />
          </el-select>
          <el-select v-model="logFilter.operationTypes" multiple filterable clearable collapse-tags collapse-tags-tooltip
            :placeholder="$t('ops.type')" style="width: 150px" @change="loadLogs(1)">
            <el-option v-for="ty in logOptions.operationTypes" :key="ty" :value="ty" :label="ty" />
          </el-select>
          <el-select v-model="logFilter.results" multiple filterable clearable collapse-tags collapse-tags-tooltip
            :placeholder="$t('ops.result')" style="width: 120px" @change="loadLogs(1)">
            <el-option value="SUCCESS" :label="$t('ops.success')" />
            <el-option value="FAILED" :label="$t('ops.fail')" />
          </el-select>
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
          <el-table-column prop="traceId" label="TID" width="150">
            <template #default="{ row }">
              <span v-if="row.traceId" class="tid-link mono" :title="$t('ops.tidJump')" @click="jumpToTrace(row)">{{ row.traceId }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-model:current-page="logPageNum" v-model:page-size="logPageSize" :page-sizes="[20, 50, 100, 200]" :total="logTotal"
          layout="total, sizes, prev, pager, next" style="margin-top: 14px; justify-content: flex-end" @current-change="loadLogs" @size-change="loadLogs(1)" />
      </el-tab-pane>

      <el-tab-pane :label="$t('ops.traceLogs')" name="trace">
        <div class="filter-row">
          <el-input v-model="traceFilter.tid" :placeholder="$t('ops.tidPlaceholder')" clearable style="width: 320px" @keyup.enter="loadTrace" />
          <el-date-picker v-model="traceFilter.date" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.traceDate')" />
          <el-select v-model="traceFilter.sources" multiple filterable clearable collapse-tags collapse-tags-tooltip
            :placeholder="$t('ops.filterSource')" style="width: 170px">
            <el-option v-for="s in TRACE_SOURCES" :key="s" :value="s" :label="$t('ops.source_' + s)" />
          </el-select>
          <el-select v-model="traceFilter.levels" multiple filterable clearable collapse-tags collapse-tags-tooltip
            :placeholder="$t('ops.filterLevel')" style="width: 150px">
            <el-option v-for="l in TRACE_LEVELS" :key="l" :value="l" :label="l" />
          </el-select>
          <el-button type="primary" @click="loadTrace">{{ $t('ops.query') }}</el-button>
        </div>
        <el-alert v-if="traceResult && traceResult.truncated" type="warning" :closable="false" show-icon style="margin-bottom: 10px"
          :title="$t('ops.traceTruncated')" />
        <el-alert v-if="!traceLoading && traceResult && !traceResult.entries.length" type="info" :closable="false" show-icon
          :title="$t('ops.traceEmpty')" />
        <div v-if="traceResult && traceResult.entries.length" class="trace-count">
          {{ $t('ops.traceCount', { n: traceResult.count }) }} · {{ traceResult.date }}
        </div>
        <div v-loading="traceLoading" class="trace-list">
          <div v-for="(e, i) in traceResult?.entries || []" :key="i" class="trace-entry"
            :class="'lv-' + String(e.level || '').toLowerCase()">
            <div class="trace-head">
              <span class="trace-time mono">{{ e.time }}</span>
              <el-tag size="small" :type="sourceTagType(e.source)">{{ $t('ops.source_' + e.source) }}</el-tag>
              <el-tag size="small" :type="levelTagType(e.level)">{{ e.level }}</el-tag>
              <span class="trace-logger">{{ e.logger }}</span>
            </div>
            <pre class="trace-msg">{{ e.message }}</pre>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('ops.weather')" name="weather">
        <div v-loading="weatherLoading">
          <!-- 1) 调用趋势(最关注):时间段 + API 类型多选筛选 -->
          <div class="filter-row">
            <el-radio-group v-model="timelineRange" size="small" @change="loadTimeline">
              <el-radio-button value="24h">{{ $t('ops.weather24h') }}</el-radio-button>
              <el-radio-button value="month">{{ $t('ops.weatherMonth') }}</el-radio-button>
              <el-radio-button value="30d">{{ $t('ops.weather30d') }}</el-radio-button>
              <el-radio-button value="year">{{ $t('ops.weatherYear') }}</el-radio-button>
            </el-radio-group>
            <el-select v-model="timelineTypes" multiple filterable clearable collapse-tags collapse-tags-tooltip
              :placeholder="$t('ops.weatherApiType')" style="width: 240px" @change="loadTimeline">
              <el-option v-for="at in WEATHER_API_TYPES" :key="at" :value="at" :label="$t('ops.apiType.' + at)" />
            </el-select>
          </div>
          <div v-if="timelineData.length || pieSlices.length" class="charts-row">
            <div class="chart-wrap chart-hover-wrap" style="flex: 1.6">
              <div class="chart-summary">
                <span>{{ $t('ops.weatherTotalCalls') }} <b>{{ timelineTotal }}</b></span>
                <span>{{ $t('ops.trafficFailed') }} <b :class="{ 'fail-num': timelineFailed > 0 }">{{ timelineFailed }}</b></span>
                <span>{{ $t('ops.weatherFailRate') }} <b>{{ timelineFailRate }}%</b></span>
              </div>
              <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="line-chart">
                <line v-for="(t, i) in yTicks" :key="'grid'+i" :x1="padL" :x2="chartW - padR" :y1="t.y" :y2="t.y" stroke="var(--color-border)" stroke-width="1" stroke-dasharray="3 3" />
                <text v-for="(t, i) in yTicks" :key="'yl'+i" :x="padL - 8" :y="t.y + 4" text-anchor="end" fill="var(--color-text-secondary)" font-size="11">{{ t.label }}</text>
                <text v-for="(lb, i) in xLabels" :key="'xl'+i" :x="lb.x" :y="chartH - padB + 16" text-anchor="middle" fill="var(--color-text-secondary)" font-size="11">{{ lb.label }}</text>
                <polyline :points="linePoints(timelineData.map(d => d.total))" fill="none" stroke="#b88c6e" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
                <polyline :points="linePoints(timelineData.map(d => d.failed))" fill="none" stroke="#b04a3a" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
                <!-- 悬浮列:透明矩形全高捕获 hover -->
                <rect v-for="(d, i) in timelineData" :key="'hv'+i" :x="xPos(i) - hoverColW / 2" :y="padT"
                  :width="hoverColW" :height="chartH - padB - padT" fill="transparent"
                  @mouseenter="hoverIdx = i" @mouseleave="hoverIdx = -1" />
                <line v-if="hoverIdx >= 0 && timelineData[hoverIdx]" :x1="xPos(hoverIdx)" :x2="xPos(hoverIdx)"
                  :y1="padT" :y2="chartH - padB" stroke="#b88c6e" stroke-width="1" stroke-dasharray="4 2" />
                <circle v-if="hoverIdx >= 0 && timelineData[hoverIdx]" :cx="xPos(hoverIdx)" :cy="yVal(timelineData[hoverIdx].total)" r="4" fill="#b88c6e" stroke="#fff" stroke-width="1.5" />
                <circle v-if="hoverIdx >= 0 && timelineData[hoverIdx]" :cx="xPos(hoverIdx)" :cy="yVal(timelineData[hoverIdx].failed)" r="4" fill="#b04a3a" stroke="#fff" stroke-width="1.5" />
              </svg>
              <div class="chart-legend">
                <span class="legend-item"><span class="legend-dot" style="background:#b88c6e"></span>{{ $t('ops.weatherTotalCalls') }}</span>
                <span class="legend-item"><span class="legend-dot" style="background:#b04a3a"></span>{{ $t('ops.trafficFailed') }}</span>
              </div>
              <!-- 数据点悬浮提示 -->
              <div v-if="hoverIdx >= 0 && timelineData[hoverIdx]" class="chart-tooltip" :style="tooltipStyle">
                <div class="ct-time">{{ timelineData[hoverIdx].time_bucket }}</div>
                <div class="ct-row"><span class="ct-dot" style="background:#b88c6e"></span>{{ $t('ops.weatherTotalCalls') }} <b>{{ timelineData[hoverIdx].total }}</b></div>
                <div class="ct-row"><span class="ct-dot" style="background:#b04a3a"></span>{{ $t('ops.trafficFailed') }} <b>{{ timelineData[hoverIdx].failed }}</b></div>
              </div>
            </div>
            <div class="chart-wrap" style="flex: 1">
              <div class="ops-sub-title">{{ $t('ops.weatherTypeShare') }}</div>
              <svg v-if="pieSlices.length" viewBox="0 0 400 260" class="pie-chart">
                <path v-for="(s, i) in pieSlices" :key="'ps'+i" :d="s.path" :fill="s.color" stroke="var(--color-card-2)" stroke-width="2" />
                <path v-for="(s, i) in pieSlices" :key="'pl'+i" :d="s.line" fill="none" stroke="var(--color-text-secondary)" stroke-width="1" />
                <text v-for="(s, i) in pieSlices" :key="'pt'+i" :x="s.labelX" :y="s.labelY" :text-anchor="s.anchor" font-size="12" fill="var(--color-text)">{{ s.label }}</text>
              </svg>
              <el-empty v-else :description="$t('ops.weatherNoData')" :image-size="40" />
            </div>
          </div>
          <el-empty v-else-if="!timelineData.length" :description="$t('ops.weatherNoData')" :image-size="40" />

          <!-- 2) 本月配额(本地统计):4 卡片一行 + 横向进度条 -->
          <h4 class="ops-section-title section-gap">{{ $t('ops.weatherQuota') }}</h4>
          <div class="quota-grid">
            <div class="finance-card">
              <div class="finance-label">{{ $t('ops.weatherQuotaUsed') }}</div>
              <div class="finance-value">{{ weatherQuota?.used ?? '-' }}</div>
            </div>
            <div class="finance-card">
              <div class="finance-label">{{ $t('ops.weatherQuotaLimit') }}</div>
              <div class="finance-value">{{ weatherQuota?.quota ?? '-' }}</div>
            </div>
            <div class="finance-card">
              <div class="finance-label">{{ $t('ops.weatherQuotaRemaining') }}</div>
              <div class="finance-value">{{ weatherQuota?.remaining ?? '-' }}</div>
            </div>
            <div class="finance-card">
              <div class="finance-label">{{ $t('ops.weatherQuotaPercent') }}</div>
              <div class="finance-value">{{ weatherQuota ? weatherQuota.usagePercent + '%' : '-' }}</div>
            </div>
          </div>
          <div class="quota-progress">
            <div class="qp-track">
              <div class="qp-fill" :class="quotaBarClass" :style="{ width: Math.min(weatherQuota?.usagePercent || 0, 100) + '%' }"></div>
            </div>
            <span class="qp-text">{{ (weatherQuota?.used ?? 0).toLocaleString() }} / {{ (weatherQuota?.quota ?? 50000).toLocaleString() }}</span>
          </div>

          <!-- 3) 24h 请求量(按 API,成功/错误/失败率合并一表) -->
          <h4 class="ops-section-title section-gap">{{ $t('ops.weather24hStats') }}</h4>
          <el-table v-if="weatherStatRows.length" :data="weatherStatRows" size="small" stripe>
            <el-table-column prop="api" label="API" min-width="140" />
            <el-table-column prop="ok" :label="$t('ops.weatherSuccess')" width="110" />
            <el-table-column prop="err" :label="$t('ops.weatherError')" width="110" />
            <el-table-column :label="$t('ops.weatherFailRate')" width="110">
              <template #default="{ row }">{{ row.failRate }}%</template>
            </el-table-column>
          </el-table>
          <el-alert v-else-if="!weatherLoading" type="warning" :closable="false" show-icon :title="$t('ops.weatherStatsFail')" />

          <!-- 4) 财务汇总(最不关注,放最底) -->
          <h4 class="ops-section-title section-gap">{{ $t('ops.weatherFinance') }}</h4>
          <div v-if="weatherFinance" class="finance-grid">
            <div class="finance-card">
              <div class="finance-label">{{ $t('ops.weatherBalance') }}</div>
              <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.balance ?? '-' }}</div>
            </div>
            <div class="finance-card">
              <div class="finance-label">{{ $t('ops.weatherThisMonth') }}</div>
              <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.thisMonth ?? '0' }}</div>
            </div>
            <div class="finance-card">
              <div class="finance-label">{{ $t('ops.weatherYesterday') }}</div>
              <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.previousDay ?? '0' }}</div>
            </div>
          </div>
          <el-alert v-else-if="!weatherLoading" type="warning" :closable="false" show-icon :title="$t('ops.weatherFinanceFail')" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
// 运维管理:标签聚合页;数据接口须 ops:view 权限,后端 OpsAccessFilter 还会把 OPS 角色限定在 /ops 与 /auth
// 详细日志:按 tid 检索 access/server/thirdparty 三类日志文件;操作日志 TID 列可点击跳转并自动查询
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { opsApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const route = useRoute()

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
const logPageSize = ref(20)
const logFilter = reactive({ keyword: '', operatorId: '', modules: [], operationTypes: [], results: [], startDate: '', endDate: '' })
// 筛选下拉数据源(distinct 模块/操作类型,来自 sys_operation_log)
const logOptions = ref({ modules: [], operationTypes: [] })
const loadOptions = async () => {
  try {
    logOptions.value = await opsApi.logOptions()
  } catch (e) { /* 选项加载失败不阻塞列表 */ }
}

// ---------- 访问量统计(扫描 access 日志文件按天聚合) ----------
const traffic = ref(null)
const trafficLoading = ref(false)
const trafficFilter = reactive({ startDate: '', endDate: '' })
const trafficCards = [
  { key: 'total', name: t('ops.trafficTotal') },
  { key: 'failed', name: t('ops.trafficFailed') },
  { key: 'slow', name: t('ops.trafficSlow') },
  { key: 'users', name: t('ops.trafficUsers') },
  { key: 'ips', name: t('ops.trafficIps') },
  { key: 'avgCostMs', name: t('ops.trafficAvgCost') },
]
const loadTraffic = async () => {
  trafficLoading.value = true
  try {
    traffic.value = await opsApi.trafficStats({
      startDate: trafficFilter.startDate || null,
      endDate: trafficFilter.endDate || null,
    })
  } finally {
    trafficLoading.value = false
  }
}

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

// 访问统计 24 小时柱状图几何(总请求=暖棕柱,失败=底部红色叠层)
const barStep = (chartW - padL - padR) / 24
const barW = barStep * 0.55
const barX = (i) => padL + i * barStep + (barStep - barW) / 2
const tYMax = computed(() => Math.max(...(traffic.value?.hours || []).map(h => h.total), 1))
const barH = (v) => (chartH - padB - padT) * v / tYMax.value
const barY = (v) => chartH - padB - barH(v)
const tYTicks = computed(() => {
  const ticks = []
  for (let i = 0; i <= 4; i++) {
    ticks.push({ y: chartH - padB - (chartH - padB - padT) * i / 4, label: Math.round(tYMax.value * i / 4) })
  }
  return ticks
})

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
  // 采样标签:约 8 个 + 恒含首末;坐标用真实数据下标计算(修标签全挤左侧的 bug)
  const step = Math.max(1, Math.ceil(n / 8))
  const indices = []
  for (let i = 0; i < n; i += step) indices.push(i)
  if (indices[indices.length - 1] !== n - 1) indices.push(n - 1)
  return indices.map(i => ({ x: xPos(i), label: timelineData.value[i].time_bucket }))
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

// 天气 API 类型(与后端 parseApiType 对齐)
const WEATHER_API_TYPES = ['now', 'forecast', 'hourly', 'warning', 'air', 'indices', 'minutely', 'location', 'quota', 'finance', 'metrics', 'other']
const timelineTypes = ref([])

const loadTimeline = async () => {
  loadTypeDist() // 饼图与折线图共用时间范围,并行加载
  try {
    timelineData.value = await opsApi.weatherTimeline(timelineRange.value, timelineTypes.value)
  } catch (e) {
    timelineData.value = []
  }
}

// ---------- 折线图悬浮提示 ----------
const hoverIdx = ref(-1)
const hoverColW = computed(() => (chartW - padL - padR) / Math.max(timelineData.value.length, 1))
const yVal = (v) => chartH - padB - (chartH - padB - padT) * v / yMax.value
const tooltipStyle = computed(() => {
  if (hoverIdx.value < 0) return {}
  // 比例定位 + 边缘 clamp,防止 tooltip 超出容器
  const ratio = Math.min(Math.max(xPos(hoverIdx.value) / chartW, 0.15), 0.85)
  return { left: (ratio * 100) + '%' }
})

// ---------- API 类型占比饼图(与折线图共用时间范围) ----------
const typeDist = ref([])
const loadTypeDist = async () => {
  try {
    typeDist.value = await opsApi.weatherTypeDistribution(timelineRange.value)
  } catch (e) {
    typeDist.value = []
  }
}
// ponytail: 小占比扇区标签可能重叠,类型通常 4-6 个可接受;类型多时再加外部图例
const PIE_COLORS = ['#b88c6e', '#a87c5e', '#c4a884', '#8a6d3b', '#b04a3a', '#d4b298', '#6b8a6b', '#e0862f', '#4a90d9', '#9b8ec4', '#c97474', '#9a9a9a']
const pieSlices = computed(() => {
  const dist = typeDist.value || []
  const total = dist.reduce((a, d) => a + (d.count || 0), 0)
  if (!total) return []
  const cx = 200, cy = 130, r = 75
  let angle = -Math.PI / 2
  return dist.map((d, i) => {
    const frac = (d.count || 0) / total
    const a2 = angle + frac * Math.PI * 2
    const large = frac > 0.5 ? 1 : 0
    const x1 = cx + r * Math.cos(angle), y1 = cy + r * Math.sin(angle)
    const x2 = cx + r * Math.cos(a2), y2 = cy + r * Math.sin(a2)
    const mid = (angle + a2) / 2
    const cos = Math.cos(mid), sin = Math.sin(mid)
    // 引导线:扇区外缘(r+4)→ 外扩(r+16)→ 水平延伸 16px
    const lx1 = cx + (r + 4) * cos, ly1 = cy + (r + 4) * sin
    const lx2 = cx + (r + 16) * cos, ly2 = cy + (r + 16) * sin
    const lx3 = lx2 + (cos >= 0 ? 16 : -16)
    const pct = Math.round(frac * 1000) / 10
    const slice = {
      // 单一类型占 100% 时画整圆
      path: frac >= 0.999
        ? `M ${cx - r} ${cy} a ${r} ${r} 0 1 0 ${2 * r} 0 a ${r} ${r} 0 1 0 -${2 * r} 0 Z`
        : `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`,
      line: `M ${lx1} ${ly1} L ${lx2} ${ly2} L ${lx3} ${ly2}`,
      labelX: lx3 + (cos >= 0 ? 3 : -3),
      labelY: ly2 + 4,
      anchor: cos >= 0 ? 'start' : 'end',
      color: PIE_COLORS[i % PIE_COLORS.length],
      label: `${t('ops.apiType.' + (d.apiType || 'other'))} ${pct}%`,
    }
    angle = a2
    return slice
  })
})

// ---------- 配额进度条 ----------
const quotaBarClass = computed(() => {
  const pct = weatherQuota.value?.usagePercent || 0
  return pct >= 90 ? 'danger' : pct >= 70 ? 'warn' : ''
})

// 趋势摘要(所选范围合计)
const timelineTotal = computed(() => timelineData.value.reduce((a, d) => a + d.total, 0))
const timelineFailed = computed(() => timelineData.value.reduce((a, d) => a + d.failed, 0))
const timelineFailRate = computed(() => timelineTotal.value === 0 ? 0 : Math.round(timelineFailed.value * 1000 / timelineTotal.value) / 10)

// 24h 请求量:成功/错误两表合一,补失败率
const weatherStatRows = computed(() => {
  const s = weatherStats.value
  if (!s) return []
  const map = new Map()
  for (const r of s.success || []) {
    map.set(r.api, { api: r.api, ok: (r.hours || []).reduce((a, b) => a + b, 0), err: 0 })
  }
  for (const r of s.errors || []) {
    const row = map.get(r.api) || { api: r.api, ok: 0, err: 0 }
    row.err = (r.hours || []).reduce((a, b) => a + b, 0)
    map.set(r.api, row)
  }
  return [...map.values()].map(r => ({
    ...r,
    failRate: r.ok + r.err === 0 ? 0 : Math.round(r.err * 1000 / (r.ok + r.err)) / 10,
  }))
})

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
      size: logPageSize.value,
      keyword: logFilter.keyword || null,
      operatorId: logFilter.operatorId || null,
      module: logFilter.modules.length ? logFilter.modules.join(',') : null,
      operationType: logFilter.operationTypes.length ? logFilter.operationTypes.join(',') : null,
      result: logFilter.results.length ? logFilter.results.join(',') : null,
      startDate: logFilter.startDate || null,
      endDate: logFilter.endDate || null,
    })
    logPage.value = data
    logTotal.value = data.total
  } finally {
    logsLoading.value = false
  }
}

// ---------- 详细日志(按 tid 检索三类日志文件) ----------
const traceLoading = ref(false)
const traceResult = ref(null)
const TRACE_SOURCES = ['access', 'server', 'thirdparty']
const TRACE_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG']
const traceFilter = reactive({ tid: '', date: '', sources: [], levels: [] })

const loadTrace = async () => {
  const tid = traceFilter.tid.trim()
  if (tid.length < 6) return
  traceLoading.value = true
  try {
    traceResult.value = await opsApi.traceLogs({
      tid,
      date: traceFilter.date || null,
      sources: traceFilter.sources.length ? traceFilter.sources.join(',') : null,
      levels: traceFilter.levels.length ? traceFilter.levels.join(',') : null,
    })
  } finally {
    traceLoading.value = false
  }
}

/** 操作日志行点击 TID:跳详细日志 tab,按该条日志的日期直接查询 */
const jumpToTrace = (row) => {
  tab.value = 'trace'
  traceFilter.tid = row.traceId
  traceFilter.date = (row.createdAt || '').slice(0, 10) || ''
  traceResult.value = null
  loadTrace()
}

const sourceTagType = (s) => (s === 'access' ? 'primary' : s === 'thirdparty' ? 'success' : 'warning')
const levelTagType = (l) => (l === 'ERROR' ? 'danger' : l === 'WARN' ? 'warning' : 'info')

const loadWeatherQuota = async () => {
  weatherLoading.value = true
  loadTimeline() // 与下面三个并行,不串行等待
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
}

onMounted(async () => {
  // 支持 /ops?tab=trace&tid=xxx&date=yyyy-MM-dd 或 /ops?tab=traffic 直达(分享/书签)
  if (route.query.tab === 'trace' && route.query.tid) {
    tab.value = 'trace'
    traceFilter.tid = String(route.query.tid)
    if (route.query.date) traceFilter.date = String(route.query.date)
    loadTrace()
  }
  if (route.query.tab === 'traffic') tab.value = 'traffic'
  await Promise.all([loadStats(), loadLogs(1), loadOptions()])
})

// 切到访问统计/天气标签页时懒加载
watch(tab, (v) => {
  if (v === 'traffic' && !traffic.value) loadTraffic()
  if (v === 'weather' && !weatherQuota.value) loadWeatherQuota()
})
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
.charts-row { display: flex; gap: 14px; align-items: stretch; }
.chart-wrap { background: var(--color-card-2); border-radius: 10px; padding: 16px; position: relative; }
.line-chart { width: 100%; height: auto; display: block; }
.pie-chart { width: 100%; height: auto; display: block; max-width: 420px; margin: 0 auto; }
.chart-tooltip {
  position: absolute; top: 26px; transform: translateX(-50%);
  background: var(--color-card); border: 1px solid var(--color-border); border-radius: 8px;
  padding: 8px 12px; font-size: 12px; pointer-events: none; z-index: 5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08); white-space: nowrap;
}
.ct-time { color: var(--color-text-secondary); margin-bottom: 4px; }
.ct-row { display: flex; align-items: center; gap: 6px; color: var(--color-text); }
.ct-row b { font-variant-numeric: tabular-nums; }
.ct-dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }
.quota-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.quota-progress { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.qp-track { flex: 1; height: 10px; background: var(--color-card-2); border-radius: 5px; overflow: hidden; }
.qp-fill { height: 100%; border-radius: 5px; background: #b88c6e; transition: width 0.4s ease; }
.qp-fill.warn { background: #d4a13f; }
.qp-fill.danger { background: #b04a3a; }
.qp-text { font-size: 13px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; white-space: nowrap; }
.chart-summary { display: flex; gap: 24px; font-size: 13px; color: var(--color-text-secondary); margin-bottom: 10px; }
.chart-summary b { color: var(--color-text); font-variant-numeric: tabular-nums; }
.chart-summary .fail-num { color: #b04a3a; }
.section-gap { margin-top: 24px; }
@media (max-width: 768px) {
  .charts-row { flex-direction: column; }
  .quota-grid { grid-template-columns: repeat(2, 1fr); }
}
.chart-legend { display: flex; gap: 20px; justify-content: center; margin-top: 8px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text-secondary); }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; }
.mono { font-family: Consolas, Monaco, 'Courier New', monospace; }
.tid-link { color: #b88c6e; cursor: pointer; font-size: 12px; }
.tid-link:hover { text-decoration: underline; }
.trace-count { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 10px; }
.trace-list { display: flex; flex-direction: column; gap: 10px; }
.trace-entry { background: var(--color-card-2); border-radius: 10px; padding: 10px 14px; }
.trace-entry.lv-error { border-left: 3px solid #b04a3a; }
.trace-entry.lv-warn { border-left: 3px solid #b88c6e; }
.trace-head { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 6px; }
.trace-time { font-size: 12px; color: var(--color-text-secondary); }
.trace-logger { font-size: 12px; color: var(--color-text-secondary); }
.trace-msg { margin: 0; white-space: pre-wrap; word-break: break-all; font-size: 12px; font-family: Consolas, Monaco, 'Courier New', monospace; max-height: 420px; overflow: auto; }
.traffic-grid { grid-template-columns: repeat(6, 1fr); }
.traffic-hint { font-size: 12px; color: var(--color-text-secondary); }
@media (max-width: 768px) {
  .traffic-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>