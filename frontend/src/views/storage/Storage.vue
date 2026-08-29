<!-- 存储管理页:家庭存储设备 + 文件浏览器 + 一键同步(设备目录→相册) -->
<template>
  <div class="storage-settings">
    <!-- 设备管理 -->
    <div class="card section">
      <div class="page-toolbar">
        <h3>{{ $t('storage.devices') }}</h3>
        <el-button v-if="userStore.isOwner" type="primary" plain @click="openDevice()">{{ $t('storage.addDevice') }}</el-button>
      </div>
      <el-table :data="devices" v-loading="loadingDevices" stripe>
        <el-table-column prop="name" :label="$t('storage.deviceName')" min-width="140">
          <template #default="{ row }">
            {{ row.name }}
            <el-tag v-if="row.id === defaultDeviceId" size="small" type="success" style="margin-left: 6px">{{ $t('storage.defaultTag') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceType" :label="$t('storage.deviceType')" width="130" />
        <el-table-column :label="$t('storage.rootPath')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.id !== 0 && row.deviceType !== 'BAIDU'">{{ row.rootPath }}</span>
            <span v-else class="path-hidden">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="browseDevice(row)">{{ $t('storage.browse') }}</el-button>
            <el-button v-if="row.id !== 0 && row.id !== defaultDeviceId && userStore.isOwner" size="small" text type="success" @click="setDefaultDevice(row)">{{ $t('storage.setDefault') }}</el-button>
            <el-button v-if="row.id !== 0 && userStore.isOwner" size="small" text @click="openDevice(row)">{{ $t('common.edit') }}</el-button>
            <el-button v-if="row.id !== 0 && userStore.isOwner" size="small" text type="danger" @click="removeDevice(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 百度网盘接入:凭证在「添加设备 → 百度网盘」模态框中配置,此处展示回调地址与授权状态 -->
    <div v-if="userStore.isOwner" class="card section">
      <div class="page-toolbar">
        <h3>{{ $t('storage.baidu.title') }}</h3>
        <template v-if="baiduForm.configured">
          <el-tag v-if="baiduForm.authorized" size="small" type="success">{{ $t('storage.baidu.authorized') }}</el-tag>
          <el-tag v-else size="small" type="info">{{ $t('storage.baidu.notAuthorized') }}</el-tag>
          <el-button :disabled="!baiduForm.secretKeySet" @click="startBaiduAuth">
            {{ baiduForm.authorized ? $t('storage.baidu.reauthorize') : $t('storage.baidu.authorize') }}
          </el-button>
        </template>
      </div>
      <template v-if="baiduForm.configured">
        <div class="baidu-callback-url">
          <span class="cb-label">{{ $t('storage.baidu.callbackUrl') }}:</span>
          <code class="cb-value">{{ baiduCallbackUrl }}</code>
          <el-button class="ghost-btn" size="small" @click="copyCallbackUrl">{{ $t('settings.copy') }}</el-button>
        </div>
        <p v-if="baiduForm.authorized && baiduForm.tokenExpiresAt" class="baidu-token-expire">
          {{ $t('storage.baidu.expiresAt', { time: formatTokenExpiry(baiduForm.tokenExpiresAt) }) }}
        </p>
      </template>
      <el-alert v-else type="info" :closable="false" class="baidu-hint" :title="$t('storage.baidu.configFirst')" />
    </div>

    <!-- 文件浏览器 -->
    <div class="card section">
      <div class="page-toolbar">
        <h3>{{ $t('storage.files') }}</h3>
        <div v-if="browsing" class="browse-actions">
          <el-button size="small" :disabled="!activePath" @click="goParent">{{ $t('storage.backToParent') }}</el-button>
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
      <div class="page-toolbar">
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

    <!-- 设备编辑对话框:类型为百度网盘时显示 API 凭证表单(替代根路径) -->
    <el-dialog v-model="deviceDialog" append-to-body :title="deviceForm.id ? $t('common.edit') : $t('storage.addDevice')" width="480px">
      <el-form :model="deviceForm" label-width="90px">
        <el-form-item :label="$t('storage.deviceName')" required>
          <el-input v-model="deviceForm.name" :placeholder="$t('storage.deviceNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('storage.deviceType')">
          <el-select v-model="deviceForm.deviceType" style="width: 100%">
            <el-option v-for="dt in deviceTypes" :key="dt.value" :label="dt.label" :value="dt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="deviceForm.deviceType !== 'BAIDU'" :label="$t('storage.rootPath')" required>
          <el-input v-model="deviceForm.rootPath" :placeholder="$t('storage.rootPathPh')" />
        </el-form-item>
        <template v-if="deviceForm.deviceType === 'BAIDU'">
          <el-form-item label="AppID" required>
            <el-input v-model="deviceForm.appId" placeholder="百度网盘开放平台 AppID" />
          </el-form-item>
          <el-form-item label="AppKey" required>
            <el-input v-model="deviceForm.appKey" placeholder="百度网盘开放平台 AppKey" />
          </el-form-item>
          <el-form-item label="SecretKey">
            <el-input v-model="deviceForm.secretKey" type="password" show-password
              :placeholder="deviceForm.secretKeySet ? $t('storage.baidu.keepPlaceholder') : $t('storage.baidu.secretPlaceholder')" />
          </el-form-item>
          <el-form-item label="SignKey">
            <el-input v-model="deviceForm.signKey" type="password" show-password
              :placeholder="deviceForm.signKeySet ? $t('storage.baidu.keepPlaceholder') : $t('storage.baidu.signPlaceholder')" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="deviceDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="savingDevice" @click="saveDevice">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 图片/视频预览 -->
    <el-dialog v-model="previewDialog" append-to-body :title="previewName" width="70%" top="6vh">
      <div class="preview-box">
        <img v-if="previewIsImage" :src="previewSrc" class="preview-img" />
        <video v-else-if="previewIsVideo" :src="previewSrc" class="preview-video" controls autoplay />
        <div v-else class="preview-file">{{ $t('storage.noPreview') }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { storageApi } from '@/api'

const { t } = useI18n()
const userStore = useUserStore()

const devices = ref([])
let syncTimer = null
const loadingDevices = ref(false)
const deviceDialog = ref(false)
const savingDevice = ref(false)
const deviceForm = ref({})
// 设备类型:BAIDU 走百度网盘 API(凭证在 sys_baidu_credential),无本地根路径
const deviceTypes = computed(() => [
  { value: 'NAS', label: 'NAS' },
  { value: 'REMOTE', label: 'REMOTE' },
  { value: 'MOUNT', label: 'MOUNT' },
  { value: 'BAIDU', label: t('storage.baidu.typeLabel') },
])

// 默认存储设备:localStorage 持久化(家庭级),同步时自动选中
const defaultDeviceId = ref(parseInt(localStorage.getItem('ihomy:default-storage') || '0'))
const setDefaultDevice = (row) => {
  defaultDeviceId.value = row.id
  localStorage.setItem('ihomy:default-storage', String(row.id))
  syncDeviceId.value = row.id
  ElMessage.success(t('common.success'))
}

const browsing = ref(false)
const activeDeviceId = ref(0)
const activePath = ref('')
const files = ref([])
const loadingFiles = ref(false)

const syncing = ref(false)
const syncDeviceId = ref(0)
const includeEmpty = ref(false)
const syncResult = ref(null)

// 百度网盘接入状态(凭证在设备模态框中维护,此处只读展示)
const baiduForm = ref({ appId: '', appKey: '', secretKeySet: false, signKeySet: false, configured: false, authorized: false, tokenExpiresAt: null })
const baiduCallbackUrl = `${location.origin}/storage/baidu/callback`

async function loadBaidu() {
  const c = await storageApi.baiduCredential()
  baiduForm.value = {
    appId: c.appId || '',
    appKey: c.appKey || '',
    secretKeySet: !!c.secretKeySet,
    signKeySet: !!c.signKeySet,
    configured: !!(c.appId && c.appKey),
    authorized: !!c.authorized,
    tokenExpiresAt: c.tokenExpiresAt || null,
  }
}

function formatTokenExpiry(d) {
  return new Date(d).toLocaleString('zh-CN')
}

async function copyCallbackUrl() {
  try {
    await navigator.clipboard.writeText(baiduCallbackUrl)
    ElMessage.success(t('photoViewer.linkCopied'))
  } catch {
    ElMessage.error(t('photoViewer.copyFailed'))
  }
}

// 发起 OAuth 授权:新标签页打开百度授权页,授权后百度重定向回 /storage/baidu/callback
async function startBaiduAuth() {
  const { url } = await storageApi.baiduAuthUrl(baiduCallbackUrl)
  window.open(url, '_blank')
}

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
  const base = { secretKey: '', signKey: '', appId: '', appKey: '', secretKeySet: false, signKeySet: false }
  if (row) {
    // 编辑:百度网盘设备带出已存凭证(密钥只显示"留空保持不变")
    deviceForm.value = {
      ...row, ...base,
      appId: baiduForm.value.appId || '',
      appKey: baiduForm.value.appKey || '',
      secretKeySet: baiduForm.value.secretKeySet,
      signKeySet: baiduForm.value.signKeySet,
    }
  } else {
    deviceForm.value = { name: '', deviceType: 'NAS', rootPath: '', ...base }
  }
  deviceDialog.value = true
}

async function saveDevice() {
  savingDevice.value = true
  try {
    if (deviceForm.value.deviceType === 'BAIDU') {
      // 百度网盘设备:先存凭证,再建设备(rootPath 由后端置 '/')
      if (!deviceForm.value.appId || !deviceForm.value.appKey) {
        ElMessage.warning(t('storage.baidu.required'))
        return
      }
      await storageApi.saveBaiduCredential({
        appId: deviceForm.value.appId,
        appKey: deviceForm.value.appKey,
        secretKey: deviceForm.value.secretKey,
        signKey: deviceForm.value.signKey,
      })
      const payload = { name: deviceForm.value.name, deviceType: 'BAIDU', rootPath: '/' }
      if (deviceForm.value.id) await storageApi.updateDevice(deviceForm.value.id, payload)
      else await storageApi.addDevice(payload)
    } else {
      if (deviceForm.value.id) {
        await storageApi.updateDevice(deviceForm.value.id, deviceForm.value)
      } else {
        await storageApi.addDevice(deviceForm.value)
      }
    }
    ElMessage.success(t('common.success'))
    deviceDialog.value = false
    loadDevices()
    loadBaidu()
  } finally {
    savingDevice.value = false
  }
}

async function removeDevice(row) {
  await ElMessageBox.confirm(t('common.confirmDelete'), t('common.warning'), { type: 'warning', closeOnClickModal: true })
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

function goParent() {
  if (!activePath.value) return
  const idx = activePath.value.lastIndexOf('/')
  activePath.value = idx >= 0 ? activePath.value.slice(0, idx) : ''
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
  syncTimer = setInterval(async () => {
    try {
      const p = await storageApi.syncProgress(taskId)
      if (p.status === 'DONE') {
        clearInterval(syncTimer)
        syncTimer = null
        syncResult.value = p
        syncing.value = false
        ElMessage.success(p.message || t('storage.syncDone'))
        loadDevices()
      } else if (p.status === 'FAILED') {
        clearInterval(syncTimer)
        syncTimer = null
        syncing.value = false
        ElMessage.error(p.message || t('storage.syncFailed'))
      }
    } catch {
      clearInterval(syncTimer)
      syncTimer = null
      syncing.value = false
    }
  }, 1000)
}

onMounted(() => {
  loadDevices()
  if (userStore.isOwner) loadBaidu()
})
onBeforeUnmount(() => { if (syncTimer) clearInterval(syncTimer) })
</script>

<style scoped>
.storage-settings { width: 100%; }
.section {
  margin-bottom: 16px;
  padding: 16px;
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
.baidu-hint {
  margin-bottom: 0;
}
.baidu-callback-url {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.baidu-callback-url .cb-value {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  word-break: break-all;
}
.baidu-token-expire {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
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
.path-hidden {
  color: var(--el-text-color-secondary);
  opacity: 0.5;
}
</style>