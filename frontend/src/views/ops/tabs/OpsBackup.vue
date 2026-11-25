<!-- 备份与迁移(V14.51):备份策略列表(模块 + 周期 + 清理 + 启停 + 最近执行),
     新建/编辑策略弹窗,以及执行记录/下载/校验/删除;迁移包仍为一次性手动生成。 -->
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
        <div class="backup-stat-label">{{ $t('ops.backup.policyCount') }}</div>
        <div class="backup-stat-value">
          <el-tag v-if="overview.running" type="warning" size="small">{{ $t('ops.backup.running') }}</el-tag>
          <template v-else>{{ overview.policyEnabledCount || 0 }} / {{ overview.policyCount || 0 }}</template>
        </div>
        <div class="backup-stat-hint">{{ $t('ops.backup.policyCountHint') }}</div>
      </div>
      <div class="backup-stat card">
        <div class="backup-stat-label">{{ $t('ops.backup.lastSuccess') }}</div>
        <div class="backup-stat-value">{{ lastSuccessText }}</div>
        <div class="backup-stat-hint">{{ $t('ops.backup.lastSuccessHint') }}</div>
      </div>
    </div>

    <div class="filter-row backup-toolbar">
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>{{ $t('ops.backup.createPolicy') }}
      </el-button>
      <el-button :disabled="overview.running" @click="runMigration">{{ $t('ops.backup.runMigration') }}</el-button>
      <el-button :loading="loading" @click="refreshAll">{{ $t('ops.backup.refresh') }}</el-button>
      <span v-if="overview.running" class="backup-hint">{{ $t('ops.backup.runningHint') }}</span>
    </div>

    <div class="card backup-policies">
      <div class="section-label">{{ $t('ops.backup.policies') }}</div>
      <el-table :data="policies" border stripe size="small" row-key="id" :empty-text="$t('ops.backup.noPolicies')">
        <OpsDetailsColumn :title="$t('ops.backup.policies')" v-slot="{ row }">
          <dt>{{ $t('ops.backup.name') }}</dt>
          <dd>
            <div class="policy-name">{{ row.name }}</div>
          </dd>
          <dt>{{ $t('ops.backup.modules') }}</dt>
          <dd>
            <div class="module-tags">
              <el-tag v-for="m in row.modules" :key="m" size="small" class="module-tag">
                {{ moduleText(m, t) }}
              </el-tag>
            </div>
          </dd>
          <dt>{{ $t('ops.backup.schedule') }}</dt>
          <dd>
            <div>{{ scheduleText(row, t) }}</div>
            <div class="backup-sub">
              {{ row.enabled ? $t('ops.backup.nextRunAt', { time: row.nextRunAt || '—' }) : $t('ops.backup.disabled') }}
            </div>
          </dd>
          <dt>{{ $t('ops.backup.retention') }}</dt>
          <dd>{{ retentionText(row.retentionDays, t) }}</dd>
          <dt>{{ $t('ops.backup.lastRun') }}</dt>
          <dd>
            <template v-if="row.lastJob">
              <el-tag size="small" :type="statusTag(row.lastJob.status)">{{ statusLabel(row.lastJob.status) }}</el-tag>
              <div class="backup-sub">{{ formatDateTime(row.lastJob.startedAt) || '—' }}</div>
            </template>
            <span v-else class="backup-sub">{{ $t('ops.backup.neverRun') }}</span>
          </dd>
        </OpsDetailsColumn>
        <el-table-column :label="$t('ops.backup.name')" :min-width="isMobile ? 80 : 150">
          <template #default="{ row }">
            <div class="policy-name">{{ row.name }}</div>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" :label="$t('ops.backup.schedule')" min-width="170">
          <template #default="{ row }">
            <div>{{ scheduleText(row, t) }}</div>
            <div class="backup-sub">
              {{ row.enabled ? $t('ops.backup.nextRunAt', { time: row.nextRunAt || '—' }) : $t('ops.backup.disabled') }}
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" :label="$t('ops.backup.lastRun')" min-width="150">
          <template #default="{ row }">
            <template v-if="row.lastJob">
              <el-tag size="small" :type="statusTag(row.lastJob.status)">{{ statusLabel(row.lastJob.status) }}</el-tag>
              <div class="backup-sub">{{ formatDateTime(row.lastJob.startedAt) || '—' }}</div>
            </template>
            <span v-else class="backup-sub">{{ $t('ops.backup.neverRun') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('ops.backup.enabled')" :width="isMobile ? 60 : 92" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(v) => togglePolicy(row, v)" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" :width="isMobile ? 88 : 128" fixed="right">
          <template #default="{ row }">
            <el-tooltip :content="$t('ops.backup.runPolicy')" placement="top" :show-after="300">
              <el-button size="small" text :disabled="overview.running" @click="runPolicy(row)">
                <el-icon><VideoPlay /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
              <el-button size="small" text @click="openEdit(row)">
                <el-icon><Edit /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
              <el-button size="small" text type="danger" @click="removePolicy(row)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="card backup-jobs">
      <div class="section-label">{{ $t('ops.backup.jobs') }}</div>
      <div class="filter-row">
        <el-select v-model="filter.policyId" clearable :placeholder="$t('ops.backup.allPolicies')" class="filter-select">
          <el-option v-for="p in policies" :key="p.id" :value="p.id" :label="p.name" />
        </el-select>
        <el-select v-model="filter.kind" clearable :placeholder="$t('ops.backup.kind')" class="filter-select">
          <el-option value="BASIC" :label="$t('ops.backup.kindBasic')" />
          <el-option value="CONTENT" :label="$t('ops.backup.kindContent')" />
          <el-option value="FULL" :label="$t('ops.backup.kindFull')" />
          <el-option value="MIGRATION" :label="$t('ops.backup.kindMigration')" />
          <el-option value="POLICY" :label="$t('ops.backup.kindPolicy')" />
        </el-select>
        <el-select v-model="filter.status" clearable :placeholder="$t('ops.backup.status')" class="filter-select">
          <el-option value="RUNNING" :label="$t('ops.backup.statusRunning')" />
          <el-option value="SUCCESS" :label="$t('ops.backup.statusSuccess')" />
          <el-option value="FAILED" :label="$t('ops.backup.statusFailed')" />
          <el-option value="PURGED" :label="$t('ops.backup.statusPurged')" />
        </el-select>
        <el-button type="primary" @click="loadJobs(1)">{{ $t('ops.query') }}</el-button>
      </div>

      <el-table v-loading="jobsLoading" :data="jobs.records" border stripe size="small" :empty-text="$t('ops.backup.noJobs')">
        <OpsDetailsColumn :title="$t('ops.backup.jobs')" v-slot="{ row }">
          <dt>ID</dt>
          <dd>{{ row.id ?? '—' }}</dd>
          <dt>{{ $t('ops.backup.kind') }}</dt>
          <dd>
            <el-tag size="small" :type="kindTag(row.kind)">{{ kindLabel(row.kind) }}</el-tag>
          </dd>
          <dt>{{ $t('ops.backup.trigger') }}</dt>
          <dd>{{ $t('ops.backup.trigger_' + String(row.triggerType).toLowerCase()) }}</dd>
          <dt>{{ $t('ops.backup.startedAt') }}</dt>
          <dd>{{ formatDateTime(row.startedAt) || '—' }}</dd>
          <dt>{{ $t('ops.backup.duration') }}</dt>
          <dd>{{ durationText(row.durationMs, t) }}</dd>
          <dt>{{ $t('ops.backup.size') }}</dt>
          <dd>{{ formatBytes(row.sizeBytes) }}</dd>
          <dt>{{ $t('ops.backup.fileCount') }}</dt>
          <dd>{{ row.fileCount || 0 }}</dd>
          <dt>{{ $t('ops.backup.rowCount') }}</dt>
          <dd>{{ row.dbRowCount || 0 }}</dd>
          <dt>{{ $t('ops.backup.message') }}</dt>
          <dd>{{ row.message ?? '—' }}</dd>
        </OpsDetailsColumn>
        <el-table-column v-if="!isMobile" :label="$t('ops.backup.kind')" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="kindTag(row.kind)">{{ kindLabel(row.kind) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('ops.backup.policy')" :min-width="isMobile ? 80 : 130" show-overflow-tooltip>
          <template #default="{ row }">{{ policyName(row.policyId) }}</template>
        </el-table-column>
        <el-table-column :label="$t('ops.backup.status')" :width="isMobile ? 72 : 90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" :label="$t('ops.backup.startedAt')" width="165">
          <template #default="{ row }">{{ formatDateTime(row.startedAt) || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="!isMobile" :label="$t('ops.backup.size')" width="100">
          <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" :width="isMobile ? 84 : 130" fixed="right">
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
              <el-button size="small" text type="danger" @click="removeJob(row)"
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

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? $t('ops.backup.editPolicy') : $t('ops.backup.createPolicy')"
      width="min(720px, 94vw)"
      append-to-body
      destroy-on-close
    >
      <el-form label-width="110px" label-position="left">
        <el-form-item :label="$t('ops.backup.name')">
          <el-input v-model="form.name" maxlength="60" :placeholder="$t('ops.backup.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('ops.backup.modules')">
          <el-checkbox-group v-model="form.modules" class="module-grid">
            <el-checkbox v-for="m in moduleCatalog" :key="m.code" :value="m.code" class="module-check">
              <span class="module-name">{{ moduleText(m.code, t) }}</span>
              <span class="module-meta">
                {{ m.withFiles ? $t('ops.backup.modulesFiles') : $t('ops.backup.modulesCount', { n: m.tableCount }) }}
              </span>
            </el-checkbox>
          </el-checkbox-group>
          <div class="backup-hint backup-hint-block">{{ $t('ops.backup.modulesHint') }}</div>
        </el-form-item>
        <el-form-item :label="$t('ops.backup.schedule')">
          <el-radio-group v-model="form.scheduleType" @change="onScheduleTypeChange">
            <el-radio-button value="DAILY">{{ $t('ops.backup.everyDay') }}</el-radio-button>
            <el-radio-button value="WEEKLY">{{ $t('ops.backup.everyWeek') }}</el-radio-button>
            <el-radio-button value="MONTHLY">{{ $t('ops.backup.everyMonth') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('ops.backup.time')">
          <el-time-picker v-model="form.scheduleTime" format="HH:mm" value-format="HH:mm" :clearable="false" class="time-input" />
          <template v-if="form.scheduleType !== 'DAILY'">
            <el-select v-model="form.scheduleDay" class="day-input">
              <el-option v-for="d in scheduleDays(form.scheduleType)" :key="d" :value="d" :label="dayLabel(d)" />
            </el-select>
          </template>
        </el-form-item>
        <el-form-item :label="$t('ops.backup.retention')">
          <el-checkbox v-model="form.autoClean">{{ $t('ops.backup.autoClean') }}</el-checkbox>
          <el-input-number
            v-if="form.autoClean"
            v-model="form.retentionDays"
            :min="1"
            :max="3650"
            :controls="false"
            class="retention-input"
          />
          <span v-if="form.autoClean" class="backup-hint">{{ $t('ops.backup.retentionHint') }}</span>
          <span v-else class="backup-hint">{{ $t('ops.backup.keepForever') }}</span>
        </el-form-item>
        <el-form-item :label="$t('ops.backup.enabled')">
          <el-switch v-model="form.enabled" />
          <span class="backup-hint">{{ $t('ops.backup.enabledHint') }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="savePolicy">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import OpsDetailsColumn from '../OpsDetailsColumn.vue'
import { useDevice } from '@/composables/useDevice'
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Delete, Download, Edit, Plus, VideoPlay } from '@element-plus/icons-vue'
import { opsApi } from '@/api'
import { durationText, formatBytes, moduleText, retentionText, scheduleDays, scheduleText } from '@/utils/backupRules'
import { saveBlobResponse } from '@/utils/download'
import { formatDateTime } from '@/utils/datetime'

const { isMobile } = useDevice()
const { t } = useI18n()

const loading = ref(false)
const jobsLoading = ref(false)
const saving = ref(false)
const overview = ref({})
const policies = ref([])
const moduleCatalog = ref([])
const jobs = ref({ records: [] })
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filter = reactive({ kind: '', status: '', policyId: null })

const dialogVisible = ref(false)
const editingId = ref(null)
const form = reactive({
  name: '',
  modules: [],
  scheduleType: 'DAILY',
  scheduleTime: '03:30',
  scheduleDay: 7,
  autoClean: true,
  retentionDays: 7,
  enabled: true,
})

let pollTimer = null

const lastSuccessText = computed(() => {
  const list = Object.values(overview.value.lastSuccess || {})
  const latest = list
    .map((j) => j?.startedAt)
    .filter(Boolean)
    .sort()
    .pop()
  return latest || '—'
})

const policyName = (id) => policies.value.find((p) => p.id === id)?.name || '—'

const dayLabel = (d) => (form.scheduleType === 'WEEKLY' ? t(`ops.backup.weekday.${d}`) : String(d))

const kindLabel = (kind) => t('ops.backup.kind' + kind.charAt(0) + kind.slice(1).toLowerCase())
const kindTag = (kind) => ({ BASIC: 'primary', CONTENT: 'success', FULL: 'warning', MIGRATION: 'info', POLICY: 'success' })[kind] || 'info'
const statusTag = (status) => ({ RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger', PURGED: 'info' })[status] || 'info'
const statusLabel = (status) => t('ops.backup.status' + status.charAt(0) + status.slice(1).toLowerCase())

const loadOverview = async () => {
  overview.value = await opsApi.backupOverview()
}

const loadPolicies = async () => {
  policies.value = await opsApi.backupPolicies()
}

const loadModules = async () => {
  moduleCatalog.value = await opsApi.backupModules()
}

const loadJobs = async (page = pageNum.value) => {
  jobsLoading.value = true
  try {
    const data = await opsApi.backupJobs({
      current: page,
      size: pageSize.value,
      kind: filter.kind || null,
      status: filter.status || null,
      policyId: filter.policyId || null,
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
    await Promise.all([loadOverview(), loadPolicies(), loadModules(), loadJobs(pageNum.value)])
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingId.value = null
  form.name = ''
  form.modules = moduleCatalog.value.map((m) => m.code)
  form.scheduleType = 'DAILY'
  form.scheduleTime = '03:30'
  form.scheduleDay = 7
  form.autoClean = true
  form.retentionDays = 7
  form.enabled = true
  dialogVisible.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  form.name = row.name
  form.modules = [...(row.modules || [])]
  form.scheduleType = row.scheduleType || 'DAILY'
  form.scheduleTime = row.scheduleTime || '03:30'
  form.scheduleDay = row.scheduleDay || (form.scheduleType === 'WEEKLY' ? 7 : 1)
  form.autoClean = row.retentionDays != null
  form.retentionDays = row.retentionDays ?? 7
  form.enabled = row.enabled !== false
  dialogVisible.value = true
}

const onScheduleTypeChange = (type) => {
  if (type === 'WEEKLY') form.scheduleDay = Math.min(Math.max(form.scheduleDay || 7, 1), 7)
  if (type === 'MONTHLY') form.scheduleDay = Math.min(Math.max(form.scheduleDay || 1, 1), 28)
}

const retentionOf = (source) => {
  if (source.autoClean === false) return null
  if (source.autoClean === true) return source.retentionDays || 7
  return source.retentionDays ?? null
}

const payloadOf = (source, enabled) => ({
  name: source.name,
  modules: source.modules,
  scheduleType: source.scheduleType,
  scheduleTime: source.scheduleTime,
  scheduleDay: source.scheduleType === 'DAILY' ? null : source.scheduleDay,
  retentionDays: retentionOf(source),
  enabled: enabled == null ? source.enabled !== false : enabled,
})

const savePolicy = async () => {
  if (!form.name.trim()) {
    ElMessage.warning(t('ops.backup.nameRequired'))
    return
  }
  if (!form.modules.length) {
    ElMessage.warning(t('ops.backup.modulesRequired'))
    return
  }
  saving.value = true
  try {
    const payload = payloadOf(form)
    if (editingId.value) await opsApi.updateBackupPolicy(editingId.value, payload)
    else await opsApi.createBackupPolicy(payload)
    ElMessage.success(t('ops.backup.policySaved'))
    dialogVisible.value = false
    await Promise.all([loadPolicies(), loadOverview()])
  } finally {
    saving.value = false
  }
}

const togglePolicy = async (row, enabled) => {
  try {
    await opsApi.updateBackupPolicy(row.id, payloadOf(row, enabled))
    await Promise.all([loadPolicies(), loadOverview()])
  } catch (e) {
    ElMessage.error(e?.message || t('ops.backup.policySaveFailed'))
    await loadPolicies()
  }
}

const runPolicy = async (row) => {
  await ElMessageBox.confirm(t('ops.backup.runPolicyConfirm', { name: row.name }), t('common.tip'), {
    type: 'warning',
  })
  await opsApi.runBackupPolicy(row.id)
  ElMessage.success(t('ops.backup.jobStarted'))
  await Promise.all([loadOverview(), loadPolicies(), loadJobs(1)])
  startPolling()
}

const runMigration = async () => {
  await ElMessageBox.confirm(t('ops.backup.runConfirm', { kind: kindLabel('MIGRATION') }), t('common.tip'), {
    type: 'warning',
  })
  await opsApi.createBackupJob('MIGRATION')
  ElMessage.success(t('ops.backup.jobStarted'))
  await Promise.all([loadOverview(), loadJobs(1)])
  startPolling()
}

const removePolicy = async (row) => {
  await ElMessageBox.confirm(t('ops.backup.deletePolicyConfirm'), t('common.tip'), { type: 'warning' })
  await opsApi.deleteBackupPolicy(row.id)
  ElMessage.success(t('ops.backup.policyDeleted'))
  await Promise.all([loadPolicies(), loadOverview(), loadJobs(pageNum.value)])
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

const removeJob = async (row) => {
  await ElMessageBox.confirm(t('ops.backup.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await opsApi.deleteBackupJob(row.id)
  ElMessage.success(t('ops.backup.deleted'))
  await Promise.all([loadOverview(), loadPolicies(), loadJobs()])
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      await Promise.all([loadJobs(pageNum.value), loadOverview(), loadPolicies()])
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
.backup-toolbar {
  margin-bottom: 16px;
}
.backup-policies,
.backup-jobs {
  margin-bottom: 18px;
  padding: 16px 18px;
}
.backup-hint {
  margin-left: 10px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.backup-hint-block {
  display: block;
  margin-left: 0;
  margin-top: 4px;
  line-height: 1.6;
}
.backup-sub {
  margin-top: 3px;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.policy-name {
  font-weight: 600;
}
.module-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.module-tag {
  margin: 0;
}
.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
  width: 100%;
}
.module-check {
  height: auto;
  margin-right: 0;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.module-check :deep(.el-checkbox__label) {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.module-name {
  font-size: 13px;
}
.module-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.time-input {
  width: 128px !important;
}
.day-input {
  width: 128px;
  margin-left: 8px;
}
.retention-input {
  width: 120px;
  margin-left: 8px;
}
.filter-select {
  width: 150px;
}
.backup-pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
