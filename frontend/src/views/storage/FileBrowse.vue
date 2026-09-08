<!-- 文件浏览页:独立功能页,家庭成员浏览/预览/下载存储设备文件;管理操作(新建/重命名/删除)仅 OWNER -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('storage.browseTitle') }]" />

    <!-- 未初始化设备:引导添加(仅 OWNER),成员只读提示 -->
    <div v-if="!customDevices.length" class="card browse-empty">
      <el-empty :description="userStore.isOwner ? $t('storage.noDeviceOwnerHint') : $t('storage.noDevice')">
        <el-button v-if="userStore.isOwner" type="primary" @click="$router.push('/settings')">{{ $t('storage.goSettings') }}</el-button>
      </el-empty>
    </div>

    <template v-else>
      <div class="card page-toolbar">
        <div class="tb-left">
          <el-select v-model="activeDeviceId" size="small" style="width: 180px" clearable
            :placeholder="$t('storage.pickDevice')" @change="onDeviceChange">
            <el-option v-for="d in customDevices" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <el-button size="small" :disabled="!activePath" @click="goParent">{{ $t('storage.backToParent') }}</el-button>
          <el-breadcrumb v-if="pathParts.length" separator="/" class="crumb">
            <el-breadcrumb-item v-for="(seg, i) in pathParts" :key="i" @click="navigateToPath(seg.path)">
              {{ seg.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div v-if="canManage" class="tb-right">
          <template v-if="!selectMode">
            <el-button size="small" @click="createDir">{{ $t('storage.mkdir') }}</el-button>
            <el-button size="small" @click="enterSelect">{{ $t('common.select') }}</el-button>
          </template>
          <template v-else>
            <span class="select-count">{{ $t('storage.selectedCount', { n: selectedPaths.length }) }}</span>
            <el-button size="small" type="danger" plain :disabled="!selectedPaths.length" @click="deleteSelected">{{ $t('common.delete') }}</el-button>
            <el-button size="small" @click="selectMode = false">{{ $t('common.cancel') }}</el-button>
          </template>
        </div>
      </div>

      <div class="card" v-loading="loadingFiles">
        <!-- 未选中/选中设备识别不到:显示空态,不调接口不出错 -->
        <el-table v-if="activeDeviceId" :data="files" stripe @row-dblclick="onFileDblClick" @row-click="onRowClick">
          <el-table-column :label="$t('storage.name')" :min-width="isMobile ? 140 : 220">
            <template #default="{ row }">
              <div class="file-cell" :class="{ selectable: selectMode }">
                <span v-if="selectMode" class="pick-badge" :class="{ on: isSelected(row) }">
                  <svg viewBox="0 0 16 16" width="12" height="12"><path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
                <span class="file-ico">{{ row.isDir ? '📁' : fileIcon(row.name) }}</span>
                <span class="file-name">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="!isMobile" :label="$t('storage.size')" width="120">
            <template #default="{ row }">{{ row.isDir ? '-' : formatSize(row.size) }}</template>
          </el-table-column>
          <el-table-column v-if="!isMobile" :label="$t('storage.modified')" width="160">
            <template #default="{ row }">{{ formatTime(row.modified) }}</template>
          </el-table-column>
          <el-table-column :label="$t('common.actions')" :width="isMobile ? 140 : 220">
            <template #default="{ row }">
              <template v-if="!selectMode">
                <el-button v-if="isPreviewable(row.name)" size="small" @click="preview(row)">{{ $t('storage.preview') }}</el-button>
                <el-button size="small" text @click="download(row)">{{ $t('storage.download') }}</el-button>
                <el-button v-if="canManage" size="small" text @click="renameEntry(row)">{{ $t('storage.rename') }}</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else :description="$t('storage.pickDevice')" :image-size="60" />
      </div>
    </template>

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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { storageApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const { isMobile } = useDevice()

const devices = ref([])
const customDevices = computed(() => devices.value.filter((d) => d.id !== 0))

const activeDeviceId = ref(0)
const activePath = ref('')
const files = ref([])
const loadingFiles = ref(false)

// 文件管理(新建/重命名/删除):仅 OWNER + 自定义设备(系统设备存业务内容,后端只读)
const canManage = computed(() => userStore.isOwner && activeDeviceId.value > 0)
const selectMode = ref(false)
const selectedPaths = ref([])
const entryPath = (row) => (activePath.value ? `${activePath.value}/${row.name}` : row.name)
const isSelected = (row) => selectedPaths.value.includes(entryPath(row))

function enterSelect() {
  selectedPaths.value = []
  selectMode.value = true
}

function onRowClick(row) {
  if (!selectMode.value) return
  const p = entryPath(row)
  const i = selectedPaths.value.indexOf(p)
  if (i >= 0) selectedPaths.value.splice(i, 1)
  else selectedPaths.value.push(p)
}

async function createDir() {
  const { value } = await ElMessageBox.prompt(t('storage.dirNamePrompt'), t('storage.mkdir'), {
    inputPattern: /\S/,
    inputErrorMessage: t('storage.nameRequired'),
  }).catch(() => null)
  if (!value) return
  await storageApi.mkdir(activeDeviceId.value, value.trim())
  ElMessage.success(t('common.success'))
  loadFiles()
}

async function renameEntry(row) {
  const { value } = await ElMessageBox.prompt(t('storage.renamePrompt'), t('storage.rename'), {
    inputValue: row.name,
    inputPattern: /\S/,
    inputErrorMessage: t('storage.nameRequired'),
  }).catch(() => null)
  if (!value || value.trim() === row.name) return
  await storageApi.rename(activeDeviceId.value, entryPath(row), value.trim())
  ElMessage.success(t('common.success'))
  loadFiles()
}

async function deleteSelected() {
  await ElMessageBox.confirm(
    t('storage.confirmDeleteEntries', { n: selectedPaths.value.length }),
    t('common.warning'), { type: 'warning', closeOnClickModal: true })
  await storageApi.removeEntries(activeDeviceId.value, selectedPaths.value)
  ElMessage.success(t('common.deleted'))
  selectMode.value = false
  selectedPaths.value = []
  loadFiles()
}

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

function onDeviceChange() {
  activePath.value = ''
  selectedPaths.value = []
  loadFiles()
}

function navigateToPath(path) {
  activePath.value = path
  selectedPaths.value = []
  loadFiles()
}

function goParent() {
  if (!activePath.value) return
  const idx = activePath.value.lastIndexOf('/')
  activePath.value = idx >= 0 ? activePath.value.slice(0, idx) : ''
  selectedPaths.value = []
  loadFiles()
}

async function loadFiles() {
  if (!activeDeviceId.value) {
    files.value = []
    return
  }
  loadingFiles.value = true
  try {
    files.value = await storageApi.browse(activeDeviceId.value, activePath.value)
  } catch (e) {
    // 选中设备识别不到/不可达(如 NAS 掉线):清空列表显示空态,报错 toast 由 request.js 统一提示
    files.value = []
  } finally {
    loadingFiles.value = false
  }
}

function onFileDblClick(row) {
  if (selectMode.value) return
  if (row.isDir) navigateToPath(activePath.value ? `${activePath.value}/${row.name}` : row.name)
  else if (isPreviewable(row.name)) preview(row)
}

function preview(row) {
  previewSrc.value = row.signedUrl || storageApi.fileUrl(activeDeviceId.value, entryPath(row), false)
  previewName.value = row.name
  previewIsImage.value = /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(row.name)
  previewIsVideo.value = /\.(mp4|webm|ogg)$/i.test(row.name)
  previewDialog.value = true
}

function download(row) {
  window.open(row.signedUrl ? `${row.signedUrl}&download=true` : storageApi.fileUrl(activeDeviceId.value, entryPath(row), true), '_blank')
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

onMounted(async () => {
  devices.value = await storageApi.devices()
  // 初始设备:URL ?deviceId=(设置页跳转) > localStorage 默认设备 > 首个自定义设备
  const fromQuery = Number(route.query.deviceId)
  const saved = parseInt(localStorage.getItem('ihomy:default-storage') || '0')
  if (fromQuery && customDevices.value.some((d) => d.id === fromQuery)) activeDeviceId.value = fromQuery
  else if (saved && customDevices.value.some((d) => d.id === saved)) activeDeviceId.value = saved
  else if (customDevices.value.length) activeDeviceId.value = customDevices.value[0].id
  if (activeDeviceId.value) loadFiles()
})
</script>

<style scoped>
.crumb {
  cursor: pointer;
}
.file-ico {
  margin-right: 6px;
}
.file-name {
  cursor: default;
}
.file-cell {
  display: flex;
  align-items: center;
}
.file-cell.selectable {
  cursor: pointer;
}
/* 多选对勾圆标(统一交互,与相册多选一致;禁左上 checkbox 角标) */
.pick-badge {
  flex: none;
  width: 20px;
  height: 20px;
  margin-right: 8px;
  border-radius: 50%;
  border: 2px solid var(--el-border-color);
  background: var(--el-fill-color-light);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.pick-badge.on {
  background: var(--color-primary, #b88c6e);
  border-color: var(--color-primary, #b88c6e);
}
.select-count {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.browse-empty {
  padding: 40px 20px;
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

@media (max-width: 768px) {
  .page { overflow-x: hidden; }
}
</style>
