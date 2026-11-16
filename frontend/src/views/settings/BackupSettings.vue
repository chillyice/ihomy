<!-- 家庭数据导出(V12.54):家长把本家庭的数据库记录与引用文件打成迁移包。
      只做导出(下载后按包内 RESTORE.md 恢复到新实例),站内不做整库覆盖恢复。 -->
<template>
  <div class="card settings-card" v-loading="loading">
    <div class="section-label">{{ $t('settings.backup.title') }}</div>
    <div class="share-tip">{{ $t('settings.backup.hint') }}</div>

    <el-alert type="info" :closable="false" show-icon :title="$t('settings.backup.rules')" class="backup-rules" />

    <div class="backup-actions">
      <el-button type="primary" :loading="creating" :disabled="running" @click="create">{{ $t('settings.backup.create') }}</el-button>
      <el-button :loading="loading" @click="load">{{ $t('common.refresh') }}</el-button>
      <span v-if="running" class="backup-running">{{ $t('settings.backup.generating') }}</span>
    </div>

    <el-table v-if="jobs.length" :data="jobs" border stripe size="small">
      <el-table-column :label="$t('ops.backup.startedAt')" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.startedAt) || '—' }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.size')" width="100">
        <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.fileCount')" width="90">
        <template #default="{ row }">{{ row.fileCount || 0 }}</template>
      </el-table-column>
      <el-table-column :label="$t('ops.backup.status')" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)">{{ $t('ops.backup.status' + statusKey(row.status)) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" :label="$t('ops.backup.message')" min-width="180" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="110" fixed="right">
        <template #default="{ row }">
          <el-tooltip v-if="row.status === 'SUCCESS'" :content="$t('ops.backup.download')" placement="top" :show-after="300">
            <el-button size="small" text @click="download(row)"
              ><el-icon><Download /></el-icon
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
    <el-empty v-else :description="$t('settings.backup.empty')" :image-size="70" />

    <div class="share-tip backup-restore">{{ $t('settings.backup.restoreHint') }}</div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Download } from '@element-plus/icons-vue'
import { familyBackupApi } from '@/api'
import { formatBytes } from '@/utils/backupRules'
import { saveBlobResponse } from '@/utils/download'
import { formatDateTime } from '@/utils/datetime'

const { t } = useI18n()

const loading = ref(false)
const creating = ref(false)
const jobs = ref([])

let pollTimer = null

const running = computed(() => jobs.value.some((j) => j.status === 'RUNNING'))

const statusTag = (status) => ({ RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger', PURGED: 'info' })[status] || 'info'
const statusKey = (status) =>
  String(status || '').charAt(0) +
  String(status || '')
    .slice(1)
    .toLowerCase()

const load = async () => {
  loading.value = true
  try {
    const data = await familyBackupApi.jobs({ current: 1, size: 10 })
    jobs.value = data?.records || []
  } finally {
    loading.value = false
  }
}

const create = async () => {
  await ElMessageBox.confirm(t('settings.backup.confirm'), t('common.tip'), { type: 'warning' })
  creating.value = true
  try {
    await familyBackupApi.createJob()
    ElMessage.success(t('settings.backup.created'))
    await load()
    startPolling()
  } finally {
    creating.value = false
  }
}

const download = async (row) => {
  try {
    const blob = await familyBackupApi.downloadJob(row.id)
    await saveBlobResponse(blob, `ihomy-family-${row.id}.zip`)
  } catch (e) {
    ElMessage.error(e?.message || t('settings.backup.downloadFailed'))
  }
}

const remove = async (row) => {
  await ElMessageBox.confirm(t('settings.backup.deleteConfirm'), t('common.tip'), { type: 'warning' })
  await familyBackupApi.deleteJob(row.id)
  ElMessage.success(t('settings.backup.deleted'))
  await load()
}

const startPolling = () => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      await load()
      if (!running.value) stopPolling()
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
  await load()
  if (running.value) startPolling()
})
onUnmounted(stopPolling)
</script>

<style scoped>
.backup-rules {
  margin: 10px 0 14px;
}
.backup-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.backup-running {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.backup-restore {
  margin-top: 10px;
}
</style>
