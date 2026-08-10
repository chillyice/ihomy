<!-- 存储管理页:家庭存储设备 + 文件浏览器 + 一键同步(设备目录→相册) -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('storage.title') }]" />

    <!-- 设备管理 -->
    <div class="card section">
      <div class="list-header">
        <h3>{{ $t('storage.devices') }}</h3>
        <el-button v-if="userStore.isOwner" type="primary" plain @click="openDevice()">{{ $t('storage.addDevice') }}</el-button>
      </div>
      <el-table :data="devices" v-loading="loadingDevices" stripe>
        <el-table-column prop="name" :label="$t('storage.deviceName')" min-width="140" />
        <el-table-column prop="deviceType" :label="$t('storage.deviceType')" width="130" />
        <el-table-column prop="rootPath" :label="$t('storage.rootPath')" min-width="200" show-overflow-tooltip />
        <el-table-column :label="$t('common.actions')" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="browseDevice(row)">{{ $t('storage.browse') }}</el-button>
            <el-button v-if="row.id !== 0 && userStore.isOwner" size="small" text @click="openDevice(row)">{{ $t('common.edit') }}</el-button>
            <el-button v-if="row.id !== 0 && userStore.isOwner" size="small" text type="danger" @click="removeDevice(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 文件浏览器 -->
    <div class="card section">
      <div class="list-header">
        <h3>{{ $t('storage.files') }}</h3>
        <div v-if="browsing" class="browse-actions">
          <el-breadcrumb separator="/" class="crumb">
            <el-breadcrumb-item v-for="(seg, i) in pathParts" :key="i" @click="navigateToPath(seg.path)">
              {{ seg.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
      </div>
      <div v-if="browsing" v-loading="loadingFiles">
        <el-table :data="files" stripe @row-dblclick="onFileDblClick">
          <el-table-column :label="$t('storage.name')" min-width="220">
            <template #default="{ row }">
              <span class="file-ico">{{ row.isDir ? '📁' : fileIcon(row.name) }}</span>
              <span class="file-name">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('storage.size')" width="120">
            <template #default="{ row }">{{ row.isDir ? '-' : formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column :label="$t('storage.modified')" width="160">
            <template #default="{ row }">{{ formatTime(row.modified) }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.actions')" width="180">
            <template #default="{ row }">
              <el-button v-if="isPreviewable(row.name)" size="small" @click="preview(row)">{{ $t('storage.preview') }}</el-button>
              <el-button size="small" text @click="download(row)">{{ $t('storage.download') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else :description="$t('storage.pickDevice')" />
    </div>

    <!-- 一键同步 -->
    <div class="card section">
      <div class="list-header">
        <h3>{{ $t('storage.sync') }}</h3>
        <el-button v-if="userStore.isOwner" type="primary" :disabled="syncing" @click="startSync">{{ $t('storage.syncNow') }}</el-button>
      </div>
      <div class="sync-opts">
        <el-select v-model="syncDeviceId" :placeholder="$t('storage.pickDevice')" style="width: 240px">
          <el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
        <el-checkbox v-model="includeEmpty">{{ $t('storage.includeEmpty') }}</el-checkbox>
      </div>
      <div v-if="syncResult" class="sync-result">
        {{ $t('storage.syncDoneSummary', { albums: syncResult.albums ?? 0, photos: syncResult.photos ?? 0, dup: syncResult.skippedDup ?? 0 }) }}
      </div>
    </div>

    <!-- 设备编辑对话框 -->
    <el-dialog v-model="deviceDialog" :title="deviceForm.id ? $t('common.edit') : $t('storage.addDevice')" width="480px">
      <el-form :model="deviceForm" label-width="90px">
        <el-form-item :label="$t('storage.deviceName')" required>
          <el-input v-model="deviceForm.name" :placeholder="$t('storage.deviceNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('storage.deviceType')">
          <el-select v-model="deviceForm.deviceType" style="width: 100%">
            <el-option v-for="t in deviceTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('storage.rootPath')" required>
          <el-input v-model="deviceForm.rootPath" :placeholder="$t('storage.rootPathPh')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingDevice" @click="saveDevice">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 图片/视频预览 -->
    <el-dialog v-model="previewDialog" :title="previewName" width="70%" top="6vh">
      <div class="preview-box">
        <img v-if="previewIsImage" :src="previewSrc" class="preview-img" />
        <video v-else-if="previewIsVideo" :src="previewSrc" class="preview-video" controls autoplay />
        <div v-else class="preview-file">{{ $t('storage.noPreview') }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { storageApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const userStore = useUserStore()

const devices = ref([])
const loadingDevices = ref(false)
const deviceDialog = ref(false)
const savingDevice = ref(false)
const deviceForm = ref({})
const deviceTypes = ['NAS', 'REMOTE', 'MOUNT', 'SYSTEM']

const browsing = ref(false)
const activeDeviceId = ref(0)
const activePath = ref('')
const files = ref([])
const loadingFiles = ref(false)

const syncing = ref(false)
const syncDeviceId = ref(0)
const includeEmpty = ref(false)
const syncResult = ref(null)

const previewDialog = ref(false)
const previewSrc = ref('')
const previewName = ref('')
const previewIsImage = ref(false)
const previewIsVideo = ref(false)

const pathParts = computed(() => {
  if (!activePath.value) return []
  const parts = []
  let acc = ''
  activePath.value.split('/').forEach((seg) => {
    acc = acc ? `${acc}/${seg}` : seg
    parts.push({ name: seg, path: acc })
  })
  return parts
})

async function loadDevices() {
  loadingDevices.value = true
  try {
    devices.value = await storageApi.devices()
  } finally {
    loadingDevices.value = false
  }
}

function openDevice(row) {
  deviceForm.value = row ? { ...row } : { name: '', deviceType: 'NAS', rootPath: '' }
  deviceDialog.value = true
}

async function saveDevice() {
  savingDevice.value = true
  try {
    if (deviceForm.value.id) {
      await storageApi.updateDevice(deviceForm.value.id, deviceForm.value)
    } else {
      await storageApi.addDevice(deviceForm.value)
    }
    ElMessage.success(t('common.success'))
    deviceDialog.value = false
    loadDevices()
  } finally {
    savingDevice.value = false
  }
}

async function removeDevice(row) {
  await ElMessageBox.confirm(t('common.confirmDelete'), t('common.warning'), { type: 'warning' })
  await storageApi.removeDevice(row.id)
  ElMessage.success(t('common.success'))
  loadDevices()
}

async function browseDevice(row) {
  activeDeviceId.value = row.id
  activePath.value = ''
  browsing.value = true
  await loadFiles()
}

function navigateToPath(path) {
  activePath.value = path
  loadFiles()
}

async function loadFiles() {
  loadingFiles.value = true
  try {
    files.value = await storageApi.browse(activeDeviceId.value, activePath.value)
  } finally {
    loadingFiles.value = false
  }
}

function onFileDblClick(row) {
  if (row.isDir) navigateToPath(activePath.value ? `${activePath.value}/${row.name}` : row.name)
  else if (isPreviewable(row.name)) preview(row)
}

function preview(row) {
  const path = activePath.value ? `${activePath.value}/${row.name}` : row.name
  previewSrc.value = storageApi.fileUrl(activeDeviceId.value, path, false)
  previewName.value = row.name
  previewIsImage.value = /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(row.name)
  previewIsVideo.value = /\.(mp4|webm|ogg)$/i.test(row.name)
  previewDialog.value = true
}

function download(row) {
  const path = activePath.value ? `${activePath.value}/${row.name}` : row.name
  window.open(storageApi.fileUrl(activeDeviceId.value, path, true), '_blank')
}

function isPreviewable(name) {
  return /\.(jpg|jpeg|png|gif|webp|bmp|mp4|webm|ogg)$/i.test(name)
}

function fileIcon(name) {
  if (/\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(name)) return '🖼️'
  if (/\.(mp4|webm|ogg)$/i.test(name)) return '🎞️'
  if (/\.(mp3|wav|flac)$/i.test(name)) return '🎵'
  if (/\.(pdf)$/i.test(name)) return '📄'
  if (/\.(doc|docx)$/i.test(name)) return '📘'
  if (/\.(zip|rar|7z)$/i.test(name)) return '🗜️'
  return '📄'
}

function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  return (bytes / 1073741824).toFixed(1) + ' GB'
}

function formatTime(ms) {
  if (!ms) return '-'
  const d = new Date(ms)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function startSync() {
  const target = devices.value.find((d) => d.id === syncDeviceId.value)
  if (!target) {
    ElMessage.warning(t('storage.pickDevice'))
    return
  }
  syncing.value = true
  syncResult.value = null
  try {
    const { taskId } = await storageApi.sync({ deviceId: target.id, includeEmpty: includeEmpty.value })
    ElMessage.success(t('storage.syncStarted'))
    pollProgress(taskId)
  } catch {
    syncing.value = false
  }
}

function pollProgress(taskId) {
  const timer = setInterval(async () => {
    try {
      const p = await storageApi.syncProgress(taskId)
      if (p.status === 'DONE') {
        clearInterval(timer)
        syncResult.value = p
        syncing.value = false
        ElMessage.success(p.message || t('storage.syncDone'))
        loadDevices()
      } else if (p.status === 'FAILED') {
        clearInterval(timer)
        syncing.value = false
        ElMessage.error(p.message || t('storage.syncFailed'))
      }
    } catch {
      clearInterval(timer)
      syncing.value = false
    }
  }, 1000)
}

onMounted(loadDevices)
</script>

<style scoped>
.section {
  margin-bottom: 16px;
  padding: 16px;
}
.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.list-header h3 {
  margin: 0;
  font-size: 16px;
}
.browse-actions {
  display: flex;
  align-items: center;
}
.crumb {
  cursor: pointer;
}
.file-ico {
  margin-right: 6px;
}
.file-name {
  cursor: default;
}
.sync-opts {
  display: flex;
  gap: 16px;
  align-items: center;
  flex-wrap: wrap;
}
.sync-result {
  margin-top: 12px;
  color: var(--el-color-success);
}
.preview-box {
  text-align: center;
}
.preview-img {
  max-width: 100%;
  max-height: 70vh;
}
.preview-video {
  max-width: 100%;
  max-height: 70vh;
}
.preview-file {
  padding: 40px;
  color: var(--el-text-color-secondary);
}
</style>