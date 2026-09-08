<!-- 存储管理 tab(设置页):设备管理 + 百度网盘接入 + 目录映射同步;文件浏览已拆分为独立功能页 /storage/files -->
<template>
  <div class="storage-settings">
    <!-- 设备管理 -->
    <div class="card settings-card">
      <div class="section-label">{{ $t('storage.devices') }}</div>
      <div v-if="userStore.isOwner" class="mgmt-toolbar">
        <el-button type="primary" plain @click="openDevice()">{{ $t('storage.addDevice') }}</el-button>
      </div>
      <el-table :data="devices" v-loading="loadingDevices" stripe>
        <el-table-column prop="name" :label="$t('storage.deviceName')" :min-width="isMobile ? 120 : 140">
          <template #default="{ row }">
            {{ row.name }}
            <el-tag v-if="row.id === defaultDeviceId" size="small" type="success" style="margin-left: 6px">{{ $t('storage.defaultTag') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isMobile" prop="deviceType" :label="$t('storage.deviceType')" width="130" />
        <el-table-column v-if="!isMobile" :label="$t('storage.rootPath')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.id !== 0 && row.deviceType !== 'BAIDU'">{{ row.rootPath }}</span>
            <span v-else class="path-hidden">—</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" :width="isMobile ? 140 : 280" :fixed="isMobile ? false : 'right'">
          <template #default="{ row }">
            <el-button v-if="row.id !== 0" size="small" @click="goBrowse(row)">{{ $t('storage.browse') }}</el-button>
            <el-button v-if="row.id !== 0 && row.id !== defaultDeviceId && userStore.isOwner" size="small" text type="success" @click="setDefaultDevice(row)">{{ $t('storage.setDefault') }}</el-button>
            <el-button v-if="row.id !== 0 && userStore.isOwner" size="small" text @click="openDevice(row)">{{ $t('common.edit') }}</el-button>
            <el-button v-if="row.id !== 0 && userStore.isOwner" size="small" text type="danger" @click="removeDevice(row)">{{ $t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 百度网盘接入:凭证在「添加设备 → 百度网盘」模态框中配置,此处展示回调地址与授权状态 -->
    <div v-if="userStore.isOwner" class="card settings-card">
      <div class="section-label">{{ $t('storage.baidu.title') }}</div>
      <template v-if="baiduForm.configured">
        <div class="status-row">
          <el-tag v-if="baiduForm.authorized" size="small" type="success">{{ $t('storage.baidu.authorized') }}</el-tag>
          <el-tag v-else size="small" type="info">{{ $t('storage.baidu.notAuthorized') }}</el-tag>
          <el-button :disabled="!baiduForm.secretKeySet" @click="startBaiduAuth">
            {{ baiduForm.authorized ? $t('storage.baidu.reauthorize') : $t('storage.baidu.authorize') }}
          </el-button>
        </div>
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

    <!-- 从设备同步(目录映射) -->
    <div class="card settings-card">
      <div class="section-label">{{ $t('storage.sync') }}</div>
      <p class="map-hint">{{ $t('storage.mapHintLong') }}</p>
      <div v-if="userStore.isOwner" class="mgmt-toolbar">
        <el-button :loading="clearingThumbs" @click="clearThumbs">{{ $t('storage.clearThumbs') }}</el-button>
        <el-button type="primary" @click="syncVisible = true">{{ $t('storage.syncNow') }}</el-button>
      </div>
    </div>

    <SyncDialog v-model="syncVisible" />

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
        <el-form-item v-if="deviceForm.deviceType !== 'BAIDU' && !isWebDavType(deviceForm.deviceType)" :label="$t('storage.rootPath')" required>
          <el-input v-model="deviceForm.rootPath" :placeholder="$t('storage.rootPathPh')" />
        </el-form-item>
        <template v-if="isWebDavType(deviceForm.deviceType)">
          <el-form-item :label="$t('storage.webdav.server')" required>
            <el-input v-model="deviceForm.serverUrl"
              :placeholder="deviceForm.deviceType === 'NEXTCLOUD' ? $t('storage.webdav.serverPhNc') : $t('storage.webdav.serverPh')" />
          </el-form-item>
          <el-form-item :label="$t('storage.webdav.username')" required>
            <el-input v-model="deviceForm.username" :placeholder="$t('storage.webdav.usernamePh')" />
          </el-form-item>
          <el-form-item :label="$t('storage.webdav.password')">
            <el-input v-model="deviceForm.password" type="password" show-password
              :placeholder="deviceForm.id ? $t('storage.webdav.keepPlaceholder') : $t('storage.webdav.passwordPh')" />
          </el-form-item>
        </template>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { storageApi } from '@/api'
import SyncDialog from '@/components/SyncDialog.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const { isMobile } = useDevice()

const devices = ref([])
const syncVisible = ref(false)
const clearingThumbs = ref(false)
// 清空设备缩略图缓存:下次打开映射相册会重新下载生成(复现首次加载/验证缓存效果用)
const clearThumbs = async () => {
  clearingThumbs.value = true
  try {
    const res = await storageApi.clearThumbs()
    ElMessage.success(t('storage.thumbsCleared', { n: res.data }))
  } catch (e) {
    ElMessage.error(t('common.failed'))
  } finally {
    clearingThumbs.value = false
  }
}
const loadingDevices = ref(false)
const deviceDialog = ref(false)
const savingDevice = ref(false)
const deviceForm = ref({})
// 设备类型:BAIDU 走百度网盘 API(凭证在 sys_baidu_credential);NEXTCLOUD/WEBDAV 走 WebDAV(凭证随设备 root_path 加密存储)
const isWebDavType = (t) => t === 'NEXTCLOUD' || t === 'WEBDAV'
const deviceTypes = computed(() => [
  { value: 'NAS', label: 'NAS' },
  { value: 'REMOTE', label: 'REMOTE' },
  { value: 'MOUNT', label: 'MOUNT' },
  { value: 'NEXTCLOUD', label: t('storage.webdav.nextcloudLabel') },
  { value: 'WEBDAV', label: t('storage.webdav.typeLabel') },
  { value: 'BAIDU', label: t('storage.baidu.typeLabel') },
])

// 默认存储设备:localStorage 持久化(家庭级),文件浏览页以此为初始设备
const defaultDeviceId = ref(parseInt(localStorage.getItem('ihomy:default-storage') || '0'))
const setDefaultDevice = (row) => {
  defaultDeviceId.value = row.id
  localStorage.setItem('ihomy:default-storage', String(row.id))
  ElMessage.success(t('common.success'))
}

// 浏览设备文件:跳转独立文件浏览页,URL 带 deviceId 直达
const goBrowse = (row) => router.push({ path: '/storage/files', query: { deviceId: row.id } })

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

async function loadDevices() {
  loadingDevices.value = true
  try {
    devices.value = await storageApi.devices()
  } finally {
    loadingDevices.value = false
  }
}

function openDevice(row) {
  const base = { secretKey: '', signKey: '', appId: '', appKey: '', secretKeySet: false, signKeySet: false, serverUrl: '', username: '', password: '' }
  if (row) {
    // 编辑:百度网盘设备带出已存凭证(密钥只显示"留空保持不变")
    const form = {
      ...row, ...base,
      appId: baiduForm.value.appId || '',
      appKey: baiduForm.value.appKey || '',
      secretKeySet: baiduForm.value.secretKeySet,
      signKeySet: baiduForm.value.signKeySet,
    }
    // WebDAV 设备:rootPath 回传格式 serverUrl|username(密码已脱敏),拆入表单
    if (isWebDavType(row.deviceType) && typeof row.rootPath === 'string' && row.rootPath.includes('|')) {
      const parts = row.rootPath.split('|')
      form.serverUrl = parts[0] || ''
      form.username = parts[1] || ''
    }
    deviceForm.value = form
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
    } else if (isWebDavType(deviceForm.value.deviceType)) {
      // WebDAV 设备:凭证随设备 root_path 加密存储,后端 PROPFIND 测连通过后入库
      if (!deviceForm.value.serverUrl || !deviceForm.value.username) {
        ElMessage.warning(t('storage.webdav.required'))
        return
      }
      if (!deviceForm.value.id && !deviceForm.value.password) {
        ElMessage.warning(t('storage.webdav.passwordRequired'))
        return
      }
      const payload = {
        name: deviceForm.value.name,
        deviceType: deviceForm.value.deviceType,
        rootPath: deviceForm.value.serverUrl,
        username: deviceForm.value.username,
        password: deviceForm.value.password,
      }
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

onMounted(() => {
  loadDevices()
  if (userStore.isOwner) loadBaidu()
})
</script>

<style scoped>
.storage-settings { width: 100%; }
.settings-card {
  margin-bottom: 16px;
}
.mgmt-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.map-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.baidu-hint {
  margin-bottom: 0;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
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
.path-hidden {
  color: var(--el-text-color-secondary);
  opacity: 0.5;
}

@media (max-width: 768px) {
  .storage-settings { max-width: 100%; overflow-x: hidden; }
  /* 移动端设备表:去掉 fixed 后表格自身横向滚动,这里限制卡片不撑破页面 */
  .settings-card :deep(.el-table) { font-size: 12px; }
}
</style>
