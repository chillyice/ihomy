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
    </el-tabs>
  </div>
</template>

<script setup>
// 运维管理:三标签聚合页;数据接口须 ops:view 权限,后端 OpsAccessFilter 还会把 OPS 角色限定在 /ops 与 /auth
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'

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

onMounted(async () => {
  await loadStats()
  // 默认取一页近期日志(时间倒序分页,天然轻量)
  await loadLogs(1)
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
</style>