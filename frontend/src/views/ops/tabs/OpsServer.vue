<template>
  <div v-loading="serverLoading">
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 14px" :title="$t('ops.alertText')" />

    <!-- 操作行:手动刷新 + 自动刷新开关 + 采集时间 + 前端构建版本 -->
    <div class="server-head">
      <el-button size="small" @click="loadServer()">{{ $t('ops.refresh') }}</el-button>
      <label class="auto-refresh">
        <el-switch v-model="serverAutoRefresh" size="small" />
        <span>{{ $t('ops.autoRefresh') }}</span>
      </label>
      <span class="server-time">{{ $t('ops.time') }}: {{ server.time || '—' }}</span>
      <span class="server-time">{{ $t('ops.version') }}: {{ appVersion }}</span>
    </div>

    <!-- 指标卡:CPU / 物理内存 / 堆内存 / 线程 / GC -->
    <div class="metric-grid">
      <div class="metric-card card">
        <div class="metric-name">{{ $t('ops.cpu') }}</div>
        <div class="metric-num">{{ fmtPct(server.os?.cpuProcess) }}</div>
        <div class="metric-bar">
          <div class="metric-bar-fill" :style="{ width: barWidth((server.os?.cpuProcess || 0) / 100) }"></div>
        </div>
        <div class="metric-sub">{{ $t('ops.cpuSystem') }} {{ fmtPct(server.os?.cpuSystem) }}</div>
      </div>
      <div class="metric-card card">
        <div class="metric-name">{{ $t('ops.memory') }}</div>
        <div class="metric-num">{{ fmtMb(server.os?.memUsed) }} MB</div>
        <div class="metric-bar">
          <div class="metric-bar-fill" :style="{ width: barWidth((server.os?.memUsed || 0) / (server.os?.memTotal || 1)) }"></div>
        </div>
        <div class="metric-sub">{{ $t('ops.memTotal') }} {{ fmtMb(server.os?.memTotal) }} MB</div>
      </div>
      <div class="metric-card card">
        <div class="metric-name">{{ $t('ops.heapUsed') }}</div>
        <div class="metric-num">{{ fmtMb(server.jvm?.heapUsed) }} MB</div>
        <div class="metric-bar">
          <div class="metric-bar-fill" :style="{ width: barWidth((server.jvm?.heapUsed || 0) / (server.jvm?.heapMax || 1)) }"></div>
        </div>
        <div class="metric-sub">{{ $t('ops.heapMax') }} {{ fmtMb(server.jvm?.heapMax) }} MB</div>
      </div>
      <div class="metric-card card">
        <div class="metric-name">{{ $t('ops.threads') }}</div>
        <div class="metric-num">{{ server.jvm?.threads ?? 0 }}</div>
        <div class="metric-bar"></div>
        <div class="metric-sub">{{ $t('ops.peakThreads') }} {{ server.jvm?.peakThreads ?? '—' }}</div>
      </div>
      <div class="metric-card card">
        <div class="metric-name">{{ $t('ops.gc') }}</div>
        <div class="metric-num">{{ server.jvm?.gcCount ?? 0 }}</div>
        <div class="metric-bar"></div>
        <div class="metric-sub">{{ $t('ops.gcTime') }} {{ fmtMs(server.jvm?.gcTimeMs) }}</div>
      </div>
    </div>

    <div class="server-row">
      <div class="card server-block">
        <h3>JVM</h3>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('ops.javaVersion')">{{ server.jvm?.javaVersion }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.pid')">{{ server.jvm?.pid }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.heapUsed')">{{ fmtMb(server.jvm?.heapUsed) }} MB</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.heapMax')">{{ fmtMb(server.jvm?.heapMax) }} MB</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.nonHeapUsed')">{{ fmtMb(server.jvm?.nonHeapUsed) }} MB</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.threads')">{{ server.jvm?.threads }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.uptime')">{{ fmtUptime(server.jvm?.uptimeSec) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.startTime')">{{ server.jvm?.startTime }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="ops-section-title" style="margin-top: 16px">{{ $t('ops.gc') }}</h4>
        <el-table :data="server.jvm?.gc || []" size="small" border>
          <el-table-column prop="name" :label="$t('ops.gcName')" min-width="160" />
          <el-table-column prop="count" :label="$t('ops.gcCount')" width="100" />
          <el-table-column :label="$t('ops.gcTime')" width="110">
            <template #default="{ row }">{{ fmtMs(row.timeMs) }}</template>
          </el-table-column>
        </el-table>
      </div>
      <div class="card">
        <h3>{{ $t('ops.os') }}</h3>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('ops.system')">{{ server.os?.name }} ({{ server.os?.arch }})</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.cores')">{{ server.os?.cores }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.load')">{{ fmtLoad(server.os?.loadAvg) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.cpuProcess')">{{ fmtPct(server.os?.cpuProcess) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.cpuSystem')">{{ fmtPct(server.os?.cpuSystem) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('ops.memory')"
            >{{ fmtMb(server.os?.memUsed) }} / {{ fmtMb(server.os?.memTotal) }} MB</el-descriptions-item
          >
          <el-descriptions-item :label="$t('ops.time')">{{ server.time }}</el-descriptions-item>
        </el-descriptions>
        <h3 style="margin-top: 16px">{{ $t('ops.disk') }}</h3>
        <el-table :data="server.disks || []" size="small" border>
          <el-table-column prop="path" :label="$t('ops.diskPath')" min-width="100" />
          <el-table-column prop="type" :label="$t('ops.diskType')" width="110" />
          <el-table-column :label="$t('ops.diskUsage')" min-width="170">
            <template #default="{ row }">
              <div class="disk-usage">
                <div class="disk-bar">
                  <div
                    class="disk-bar-fill"
                    :class="row.usedPercent >= 90 ? 'danger' : row.usedPercent >= 70 ? 'warn' : ''"
                    :style="{ width: row.usedPercent + '%' }"
                  ></div>
                </div>
                <span class="disk-pct">{{ row.usedPercent }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('ops.diskSpace')" min-width="180">
            <template #default="{ row }">{{ fmtMb(row.free) }} / {{ fmtMb(row.total) }} MB</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'

const props = defineProps({
  active: { type: Boolean, default: false },
})

const { t } = useI18n()
const appVersion = __APP_VERSION__

const server = ref({})
const serverLoading = ref(false)
const serverAutoRefresh = ref(true)

const fmtMb = (bytes) => (bytes == null ? 0 : Math.round(bytes / 1024 / 1024))
const fmtUptime = (sec) => {
  if (!sec) return 'N/A'
  const d = Math.floor(sec / 86400)
  const h = Math.floor((sec % 86400) / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return t('ops.uptimeFormat', { d, h, m })
}
const fmtPct = (v) => (v == null ? '—' : v + '%')
const barWidth = (frac) => {
  const f = Number(frac)
  if (!Number.isFinite(f)) return '0%'
  return Math.min(Math.max(f * 100, 0), 100) + '%'
}
const fmtMs = (ms) => (ms == null ? '—' : ms < 1000 ? ms + ' ms' : (ms / 1000).toFixed(1) + ' s')
const fmtLoad = (v) => (v == null || v < 0 ? 'N/A' : Number(v).toFixed(2))

const loadServer = async (silent = false) => {
  if (serverLoading.value) return
  if (!silent) serverLoading.value = true
  try {
    server.value = await opsApi.server()
  } finally {
    if (!silent) serverLoading.value = false
  }
}

// 服务器状态自动轮询(5s):仅停留在该标签页时开启,离开即停止;手动刷新走非静默(显示 loading)
let serverTimer = null
const startServerPoll = () => {
  stopServerPoll()
  if (serverAutoRefresh.value) serverTimer = setInterval(() => loadServer(true), 5000)
}
const stopServerPoll = () => {
  if (serverTimer) {
    clearInterval(serverTimer)
    serverTimer = null
  }
}
watch(serverAutoRefresh, (v) => {
  if (v && props.active) startServerPoll()
  else stopServerPoll()
})
watch(
  () => props.active,
  (v) => {
    if (v) {
      loadServer()
      startServerPoll()
    } else stopServerPoll()
  },
)

onMounted(() => {
  loadServer()
  if (props.active) startServerPoll()
})
onUnmounted(stopServerPoll)
</script>

<style scoped>
.server-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
.server-row h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.server-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.auto-refresh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.server-time {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.metric-card {
  padding: 14px 16px;
}
.metric-name {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.metric-num {
  font-size: 22px;
  font-weight: 700;
  margin-top: 6px;
  font-variant-numeric: tabular-nums;
}
.metric-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.metric-bar {
  height: 6px;
  background: var(--color-card-2);
  border-radius: 3px;
  overflow: hidden;
  margin-top: 8px;
}
.metric-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--color-brand);
  transition: width 0.4s ease;
}
.disk-usage {
  display: flex;
  align-items: center;
  gap: 8px;
}
.disk-bar {
  flex: 1;
  height: 6px;
  background: var(--color-card-2);
  border-radius: 3px;
  overflow: hidden;
}
.disk-bar-fill {
  height: 100%;
  border-radius: 3px;
  background: var(--color-brand);
  transition: width 0.4s ease;
}
.disk-bar-fill.warn {
  background: #d4a13f;
}
.disk-bar-fill.danger {
  background: #b04a3a;
}
.disk-pct {
  font-size: 12px;
  color: var(--color-text-secondary);
  width: 48px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.ops-section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 10px;
  color: var(--color-text);
}
@media (max-width: 768px) {
  .server-row {
    grid-template-columns: 1fr;
  }
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
