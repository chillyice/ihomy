<!-- 备份与迁移(V12.54):全实例备份策略、立即备份、任务列表与下载/校验/删除。
     规则:基础数据每天、内容数据每天(文件硬链接增量)、全量每周;按日/周/月分代保留。 -->
<template>
  <div v-loading="loading">
    <div class="backup-summary">
      <div class="backup-stat card">
        <div class="backup-stat-label">{{ $t('ops.backup.storage') }}</div>
        <div class="backup-stat-value">{{ formatBytes(overview.diskBytes) }}</div>
        <div class="backup-stat-hint">{{ overview.root || '—' }}</div>
      </div>
      <div class="backup-stat card">
        <div class="backup-stat-label">{{ $t('ops.backup.diskFree') }}</div>
        <div class="backup-stat-value">{{ formatBytes(overview.diskFreeBytes) }}</div>
        <div class="backup-stat-hint">
          {{ $t('ops.backup.diskTotal', { total: formatBytes(overview.diskTotalBytes) }) }}
        </div>
      </div>
      <div class="backup-stat card">
        <div class="backup-stat-label">{{ $t('ops.backup.nextRun') }}</div>
        <div class="backup-stat-value">
          <el-tag v-if="overview.running" type="warning" size="small">{{ $t('ops.backup.running') }}</el-tag>
          <template v-else>{{ nextRunText }}</template>
        </div>
        <div class="backup-stat-hint">{{ $t('ops.backup.nextRunHint') }}</div>
      </div>
      <div class="backup-stat card">
        <div class="backup-stat-label">{{ $t('ops.backup.lastSuccess') }}</div>
        <div class="backup-stat-value">{{ lastSuccessText }}</div>
        <div class="backup-stat-hint">{{ $t('ops.backup.lastSuccessHint') }}</div>
      </div>
    </div>

    <div class="filter-row backup-actions">
      <el-button type="primary" :disabled="overview.running" @click="run('BASIC')">{{ $t('ops.backup.runBasic') }}</el-button>
      <el-button :disabled="overview.running" @click="run('CONTENT')">{{ $t('ops.backup.runContent') }}</el-button>
      <el-button :disabled="overview.running" @click="run('FULL')">{{ $t('ops.backup.runFull') }}</el-button>
      <el-button :disabled="overview.running" @click="run('MIGRATION')">{{ $t('ops.backup.runMigration') }}</el-button>
      <el-button :loading="loading" @click="refreshAll">{{ $t('ops.backup.refresh') }}</el-button>
    </div>

    <div class="card backup-policy">
      <div class="section-label">{{ $t('ops.backup.policyTitle') }}</div>
      <el-form label-width="150px" label-position="left">
        <el-form-item :label="$t('ops.backup.enabled')">
          <el-switch v-model="policy.enabled" />
          <span class="backup-hint">{{ $t('ops.backup.enabledHint') }}</span>
        </el-form-item>

        <el-form-item :label="$t('ops.backup.basicTask')">
          <el-switch v-model="policy.basic.enabled" />
          <template v-if="basicAdvanced">
            <el-input v-model="policy.basic.cron" class="cron-input mono" :placeholder="$t('ops.backup.cronPlaceholder')" />
          </template>
          <template v-else>
            <el-time-picker v-model="basicTime" format="HH:mm" value-format="HH:mm" :clearable="false" class="time-input" />
          </template>
          <span class="backup-hint">{{ $t('ops.backup.basicHint') }}</span>
        </el-form-item>

        <el-form-item :label="$t('ops.backup.contentTask')">
          <el-switch v-model="policy.content.enabled" />
          <template v-if="contentAdvanced">
            <el-input v-model="policy.content.cron" class="cron-input mono" :placeholder="$t('ops.backup.cronPlaceholder')" />
          </template>
          <template v-else>
            <el-time-picker v-model="contentTime" format="HH:mm" value-format="HH:mm" :clearable="false" class="time-input" />
          </template>
          <span class="backup-hint">{{ $t('ops.backup.contentHint') }}</span>
        </el-form-item>

        <el-form-item :label="$t('ops.backup.fullTask')">
          <el-switch v-model="policy.full.enabled" />
          <template v-if="fullAdvanced">
            <el-input v-model="policy.full.cron" class="cron-input mono" :placeholder="$t('ops.backup.cronPlaceholder')" />
          </template>
          <template v-else>
            <el-select v-model="fullDow" class="dow-input">
              <el-option v-for="d in 7" :key="d - 1" :value="d - 1" :label="$t('ops.backup.dow.' + (d - 1))" />
            </el-select>
            <el-time-picker v-model="fullTime" format="HH:mm" value-format="HH:mm" :clearable="false" class="time-input" />
          </template>
          <span class="backup-hint">{{ $t('ops.backup.fullHint') }}</span>
        </el-form-item>

        <el-form-item :label="$t('ops.backup.retentionBasic')">
          <div class="retention-row">
            <span>{{ $t('ops.backup.keepDaily') }}</span>
            <el-input-number v-model="policy.retention.basicDaily" :min="1" :max="90" size="small" />
            <span>{{ $t('ops.backup.keepWeekly') }}</span>
            <el-input-number v-model="policy.retention.basicWeekly" :min="0" :max="52" size="small" />
            <span>{{ $t('ops.backup.keepMonthly') }}</span>
            <el-input-number v-model="policy.retention.basicMonthly" :min="0" :max="24" size="small" />
          </div>
        </el-form-item>
        <el-form-item :label="$t('ops.backup.retentionContent')">
          <div class="retention-row">
            <span>{{ $t('ops.backup.keepDaily') }}</span>
            <el-input-number v-model="policy.retention.contentDaily" :min="1" :max="90" size="small" />
            <span>{{ $t('ops.backup.keepWeekly') }}</span>
            <el-input-number v-model="policy.retention.contentWeekly" :min="0" :max="52" size="small" />
            <span>{{ $t('ops.backup.keepMonthly') }}</span>
            <el-input-number v-model="policy.retention.contentMonthly" :min="0" :max="24" size="small" />
          </div>
        </el-form-item>
        <el-form-item :label="$t('ops.backup.retentionFull')">
          <div class="retention-row">
            <span>{{ $t('ops.backup.keepWeekly') }}</span>
            <el-input-number v-model="policy.retention.fullWeekly" :min="1" :max="52" size="small" />
            <span>{{ $t('ops.backup.keepMonthly') }}</span>
            <el-input-number v-model="policy.retention.fullMonthly" :min="0" :max="36" size="small" />
          </div>
        </el-form-item>
        <el-form-item :label="$t('ops.backup.retentionFamily')">
          <div class="retention-row">
            <span>{{ $t('ops.backup.familyDays') }}</span>
            <el-input-number v-model="policy.retention.familyDays" :min="1" :max="365" size="small" />
            <span>{{ $t('ops.backup.familyKeep') }}</span>
            <el-input-number v-model="policy.retention.familyKeep" :min="1" :max="20" size="small" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="savePolicy">{{ $t('ops.backup.savePolicy') }}</el-button>
          <el-button @click="loadPolicy">{{ $t('ops.backup.resetPolicy') }}</el-button>
        </el-form-item>
      </el-form>
      <div class="backup-rule">{{ ruleText }}</div>
    </div>

    <div class="filter-row">
      <el-select v-model="filter.kind" clearable :placeholder="$t('ops.backup.kind')" class="filter-select">
        <el-option value="BASIC" :label="$t('ops.backup.kindBasic')" />
        <el-option value="CONTENT" :label="$t('ops.backup.kindContent')" />
        <el-option value="FULL" :label="$t('ops.backup.kindFull')" />
        <el-option value="MIGRATION" :label="$t('ops.backup.kindMigration')" />
      </el-select>
      <el-select v-model="filter.status" clearable :placeholder="$t('ops.backup.status')" class="filter-select">
        <el-option value="RUNNING" :label="$t('ops.backup.statusRunning')" />
        <el-option value="SUCCESS" :label="$t('ops.backup.statusSuccess')" />
        <el-option value="FAILED" :label="$t('ops.backup.statusFailed')" />
        <el-option value="PURGED" :label="$t('ops.backup.statusPurged')" />
      </el-select>
      <el-button type="primary" @click="loadJobs(1)">{{ $t('ops.query') }}</el-button>
    </div>

    <el-table v-loading="jobsLoading" :data="jobs.records" border stripe size="small">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column :label="$t('ops.backup.kind')" width="105">
        <template #default="{ row }">
          <el-tag size="small" :type="kindTag(row.kind)">{{ kindLabel(row.kind) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.trigger')" width="90">
        <template #default="{ row }">{{ $t('ops.backup.trigger_' + String(row.triggerType).toLowerCase()) }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.status')" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.startedAt')" width="165">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.duration')" width="105">
        <template #default="{ row }">{{ durationText(row.durationMs, t) }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.size')" width="100">
        <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.fileCount')" width="85">
        <template #default="{ row }">{{ row.fileCount || 0 }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.rowCount')" width="95">
        <template #default="{ row }">{{ row.dbRowCount || 0 }}</template>
      </el-table-column>
      <el-table-column prop="message" :label="$t('ops.backup.message')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="130" fixed="right">
        <template #default="{ row }">
          <el-tooltip v-if="row.status === 'SUCCESS'" :content="$t('ops.backup.download')" placement="top" :show-after="300">
            <el-button size="small" text @click="download(row)"
              ><el-icon><Download /></el-icon
            ></el-button>
          </el-tooltip>
          <el-tooltip v-if="row.status === 'SUCCESS'" :content="$t('ops.backup.verify')" placement="top" :show-after="300">
            <el-button size="small" text @click="verify(row)"
              ><el-icon><CircleCheck /></el-icon
            ></el-button>
          </el-tooltip>
          <el-tooltip v-if="row.status !== 'RUNNING'" :content="$t('common.delete')" placement="top" :show-after="300">
            <el-button size="small" text type="danger" @click="remove(row)"
              ><el-icon><Delete /></el-icon
            ></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :page-sizes="[10, 20, 50]"
      :total="total"
      layout="total, sizes, prev, pager, next"
      class="backup-pager"
      @current-change="loadJobs"
      @size-change="loadJobs(1)"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Delete, Download } from '@element-plus/icons-vue'
import { opsApi } from '@/api'
import { durationText, dailyCron, formatBytes, parseDailyCron, parseWeeklyCron, retentionPlan, weeklyCron } from '@/utils/backupRules'
import { saveBlobResponse } from '@/utils/download'
import { formatDateTime } from '@/utils/datetime'

const { t } = useI18n()

const loading = ref(false)
const jobsLoading = ref(false)
const saving = ref(false)
const overview = ref({})
const policy = ref(defaultPolicy())
const jobs = ref({ records: [] })
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filter = reactive({ kind: '', status: '' })

const basicTime = ref('02:30')
const contentTime = ref('03:30')
const fullTime = ref('04:30')
const fullDow = ref(0)
const basicAdvanced = ref(false)
const contentAdvanced = ref(false)
const fullAdvanced = ref(false)

let pollTimer = null

function defaultPolicy() {
  return {
    enabled: true,
    basic: { enabled: true, cron: '0 30 2 * * *' },
    content: { enabled: true, cron: '0 30 3 * * *' },
    full: { enabled: true, cron: '0 30 4 * * 0' },
    retention: {
      basicDaily: 7,
      basicWeekly: 4,
      basicMonthly: 3,
      contentDaily: 7,
      contentWeekly: 4,
      contentMonthly: 3,
      fullWeekly: 8,
      fullMonthly: 12,
      familyDays: 30,
      familyKeep: 3,
    },
  }
}

const nextRunText = computed(() => {
  const runs = overview.value.nextRuns || {}
  const first = runs.BASIC || runs.CONTENT || runs.FULL
  return first || '—'
})

const lastSuccessText = computed(() => {
  const map = overview.value.lastSuccess || {}
  const latest = ['BASIC', 'CONTENT', 'FULL']
    .map((k) => map[k])
    .filter(Boolean)
    .sort((a, b) => String(b.startedAt).localeCompare(String(a.startedAt)))[0]
  return latest ? latest.startedAt || '—' : '—'
})

const ruleText = computed(() => {
  const plan = retentionPlan(policy.value)
  const bt = basicAdvanced.value ? policy.value.basic.cron : basicTime.value
  const ct = contentAdvanced.value ? policy.value.content.cron : contentTime.value
  const ft = fullAdvanced.value ? policy.value.full.cron : `${t('ops.backup.dow.' + fullDow.value)} ${fullTime.value}`
  return t('ops.backup.ruleText', {
    basicTime: bt,
    basicDaily: plan.basic.daily,
    basicWeekly: plan.basic.weekly,
    basicMonthly: plan.basic.monthly,
    contentTime: ct,
    contentDaily: plan.content.daily,
    contentWeekly: plan.content.weekly,
    contentMonthly: plan.content.monthly,
    fullTime: ft,
    fullWeekly: plan.full.weekly,
    fullMonthly: plan.full.monthly,
    familyDays: plan.family.days,
    familyKeep: plan.family.keep,
  })
})

const kindLabel = (kind) => t('ops.backup.kind' + kind.charAt(0) + kind.slice(1).toLowerCase())
const kindTag = (kind) => ({ BASIC: 'primary', CONTENT: 'success', FULL: 'warning', MIGRATION: 'info' })[kind] || 'info'
const statusTag = (status) => ({ RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger', PURGED: 'info' })[status] || 'info'
const statusLabel = (status) => t('ops.backup.status' + status.charAt(0) + status.slice(1).toLowerCase())

const applyPolicy = (data) => {
  policy.value = { ...defaultPolicy(), ...(data || {}) }
  policy.value.retention = { ...defaultPolicy().retention, ...(data?.retention || {}) }
  const b = parseDailyCron(policy.value.basic?.cron)
  basicAdvanced.value = !b
  if (b) basicTime.value = b.text
  const c = parseDailyCron(policy.value.content?.cron)
  contentAdvanced.value = !c
  if (c) contentTime.value = c.text
  const f = parseWeeklyCron(policy.value.full?.cron)
  fullAdvanced.value = !f
  if (f) {
    fullTime.value = f.text
    fullDow.value = f.dow
  }
}

const loadPolicy = async () => {
  const data = await opsApi.backupPolicy()
  applyPolicy(data)
}

const loadOverview = async () => {
  overview.value = await opsApi.backupOverview()
}

const loadJobs = async (page = pageNum.value) => {
  jobsLoading.value = true
  try {
    const data = await opsApi.backupJobs({
      current: page,
      size: pageSize.value,
      kind: filter.kind || null,
      status: filter.status || null,
    })
    jobs.value = data
    total.value = data.total || 0
    pageNum.value = data.current || page
  } finally {
    jobsLoading.value = false
  }
}

const refreshAll = async () => {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadPolicy(), loadJobs(pageNum.value)])
  } finally {
    loading.value = false
  }
}

const savePolicy = async () => {
  if (!basicAdvanced.value) policy.value.basic.cron = dailyCron(...basicTime.value.split(':').map(Number))
  if (!contentAdvanced.value) policy.value.content.cron = dailyCron(...contentTime.value.split(':').map(Number))
  if (!fullAdvanced.value) policy.value.full.cron = weeklyCron(fullDow.value, ...fullTime.value.split(':').map(Number))
  saving.value = true
  try {
    const saved = await opsApi.saveBackupPolicy(policy.value)
    applyPolicy(saved)
    await loadOverview()
    ElMessage.success(t('ops.backup.policySaved'))
  } finally {
    saving.value = false
  }
}

const run = async (kind) => {
  if (kind === 'FULL' || kind === 'MIGRATION') {
    await ElMessageBox.confirm(t('ops.backup.runConfirm', { kind: kindLabel(kind) }), t('common.tip'), {
      type: 'warning',
    })
  }
  await opsApi.createBackupJob(kind)
  ElMessage.success(t('ops.backup.jobStarted'))
  await Promise.all([loadOverview(), loadJobs(1)])
  startPolling()
}

const download = async (row) => {
  try {
    const blob = await opsApi.downloadBackupJob(row.id)
    await saveBlobResponse(blob, `ihomy-${String(row.kind).toLowerCase()}-${row.id}.zip`)
  } catch (e) {
    ElMessage.error(e?.message || t('ops.backup.downloadFailed'))
  }
}

const verify = async (row) => {
  const result = await opsApi.verifyBackupJob(row.id)
  const text = result.ok
    ? t('ops.backup.verifyOk', { n: result.fileCount, rows: result.dbBytes })
    : t('ops.backup.verifyBad', { n: result.missingFiles })
  if (result.ok) ElMessage.success(text)
  else ElMessage.error(text)
}

const remove = async (row) => {
  await ElMessageBox.confirm(t('ops.backup.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await opsApi.deleteBackupJob(row.id)
  ElMessage.success(t('ops.backup.deleted'))
  await Promise.all([loadOverview(), loadJobs()])
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      await loadJobs(pageNum.value)
      await loadOverview()
      if (!(jobs.value.records || []).some((j) => j.status === 'RUNNING')) stopPolling()
    } catch (e) {
      stopPolling()
    }
  }, 5000)
}

const stopPolling = () => {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = null
}

onMounted(async () => {
  await refreshAll()
  if (overview.value.running) startPolling()
})
onUnmounted(stopPolling)
</script>

<style scoped>
.backup-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.backup-stat {
  padding: 14px 16px;
}
.backup-stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.backup-stat-value {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 600;
  word-break: break-all;
}
.backup-stat-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
  word-break: break-all;
}
.backup-actions {
  margin-bottom: 16px;
}
.backup-policy {
  margin-bottom: 18px;
  padding: 16px 18px;
}
.backup-hint {
  margin-left: 10px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.retention-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.retention-row :deep(.el-input-number) {
  width: 92px;
}
.time-input {
  width: 120px !important;
}
.dow-input {
  width: 120px;
  margin-right: 8px;
}
.cron-input {
  width: 220px;
}
.filter-select {
  width: 150px;
}
.backup-rule {
  margin-top: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--color-card-2);
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.backup-pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
