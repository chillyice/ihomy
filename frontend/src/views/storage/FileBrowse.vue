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
      <PageToolbar>
        <div class="tb-left">
          <el-select
            v-model="activeDeviceId"
            size="small"
            style="width: 180px"
            clearable
            :placeholder="$t('storage.pickDevice')"
            @change="onDeviceChange"
          >
            <el-option v-for="d in customDevices" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
          <el-button size="small" :disabled="!activePath" @click="goParent">{{ $t('storage.backToParent') }}</el-button>
          <el-breadcrumb v-if="pathParts.length" separator="/" class="crumb">
            <el-breadcrumb-item v-a11y-click v-for="(seg, i) in pathParts" :key="i" @click="navigateToPath(seg.path)">
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
            <el-button size="small" type="danger" plain :disabled="!selectedPaths.length" @click="deleteSelected">{{
              $t('common.delete')
            }}</el-button>
            <el-button size="small" @click="selectMode = false">{{ $t('common.cancel') }}</el-button>
          </template>
        </div>
      </PageToolbar>

      <!-- 宽屏右栏预览:≥900px 且非多选时表格与预览并排,两列各自滚
           (列高由 useWideCols 写 --wide-col-max,通用规则在 main.css 的 .wide-col)。
           窄屏不加 .wide-cols,表格整宽,预览仍走 70% 弹窗。 -->
      <div ref="wideEl" class="file-cols" :class="{ 'wide-cols': previewWide }" :style="previewWide ? colStyle : {}">
        <div :class="{ 'wide-col': previewWide }">
          <div class="card" v-loading="loadingFiles">
            <!-- 未选中/选中设备识别不到:显示空态,不调接口不出错 -->
            <el-table v-if="activeDeviceId" :data="files" stripe @row-dblclick="onFileDblClick" @row-click="onRowClick">
              <!-- 宽屏分栏时左栏只有内容区一半宽(900px 视口约 300px),只留名称+操作,
                   大小/时间移到预览栏标题下,避免表格横向滚动 -->
              <el-table-column :label="$t('storage.name')" :min-width="previewWide ? 160 : isMobile ? 140 : 220">
                <template #default="{ row }">
                  <div class="file-cell" :class="{ selectable: selectMode }">
                    <span v-if="selectMode" class="pick-badge" :class="{ on: isSelected(row) }">
                      <svg viewBox="0 0 16 16" width="12" height="12">
                        <path
                          d="M3 8.5 L6.5 12 L13 4.5"
                          fill="none"
                          stroke="#fff"
                          stroke-width="2.4"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                        />
                      </svg>
                    </span>
                    <span class="file-ico">{{ row.isDir ? '📁' : fileIcon(row.name) }}</span>
                    <span class="file-name">{{ row.name }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column v-if="!isMobile && !previewWide" :label="$t('storage.size')" width="120">
                <template #default="{ row }">{{ row.isDir ? '-' : formatSize(row.size) }}</template>
              </el-table-column>
              <el-table-column v-if="!isMobile && !previewWide" :label="$t('storage.modified')" width="160">
                <template #default="{ row }">{{ formatTime(row.modified) }}</template>
              </el-table-column>
              <el-table-column :label="$t('common.actions')" :width="isMobile ? 120 : 140">
                <template #default="{ row }">
                  <template v-if="!selectMode">
                    <el-tooltip v-if="isPreviewable(row.name)" :content="$t('storage.preview')" placement="top" :show-after="300">
                      <el-button size="small" text @click="preview(row)"
                        ><el-icon><View /></el-icon
                      ></el-button>
                    </el-tooltip>
                    <el-tooltip v-if="userStore.isLoggedIn && !row.isDir" :content="$t('common.share')" placement="top" :show-after="300">
                      <el-button size="small" text @click="shareFile(row)"
                        ><el-icon><Share /></el-icon
                      ></el-button>
                    </el-tooltip>
                    <el-tooltip :content="$t('storage.download')" placement="top" :show-after="300">
                      <el-button size="small" text @click="download(row)"
                        ><el-icon><Download /></el-icon
                      ></el-button>
                    </el-tooltip>
                    <el-tooltip v-if="canManage" :content="$t('storage.rename')" placement="top" :show-after="300">
                      <el-button size="small" text @click="renameEntry(row)"
                        ><el-icon><EditPen /></el-icon
                      ></el-button>
                    </el-tooltip>
                  </template>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else :description="$t('storage.pickDevice')" :image-size="60" />
          </div>
        </div>
        <div v-if="previewWide" class="wide-col preview-col">
          <FilePreviewPane
            v-if="previewSrc"
            class="card"
            :src="previewSrc"
            :name="previewName"
            :meta="previewMeta"
            :is-image="previewIsImage"
            :is-video="previewIsVideo"
            @close="clearPreview"
          />
          <div v-else class="card pane-empty">{{ $t('storage.previewHint') }}</div>
        </div>
      </div>
    </template>

    <!-- 图片/视频预览(窄屏弹窗;宽屏走右栏,同一份组件) -->
    <el-dialog v-model="previewDialog" append-to-body width="70%" top="6vh" :show-close="false">
      <FilePreviewPane
        :src="previewSrc"
        :name="previewName"
        :is-image="previewIsImage"
        :is-video="previewIsVideo"
        @close="previewDialog = false"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { View, Download, EditPen, Share } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { useWideCols } from '@/composables/useWideCols'
import { useGlobalSearch } from '@/composables/useGlobalSearch'
import { storageApi } from '@/api'
import { shareFiles } from '@/native/shareOut'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import FilePreviewPane from './FilePreviewPane.vue'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const { isMobile, isWide: viewWide } = useDevice()
const search = useGlobalSearch()

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
const previewDialog = ref(false)
const previewSrc = ref('')
const previewName = ref('')
const previewMeta = ref('')
const previewIsImage = ref(false)
const previewIsVideo = ref(false)
const entryPath = (row) => (activePath.value ? `${activePath.value}/${row.name}` : row.name)
const isSelected = (row) => selectedPaths.value.includes(entryPath(row))

// 宽屏右侧预览栏:多选是纯选择场景,右栏收起、表格整宽;窄屏不加 .wide-cols。
// 设备列表是异步来的,拿到设备后容器才渲染 —— 一并纳入 enabled,让 composable 那时再量一次列高。
const previewWide = computed(() => viewWide.value && !selectMode.value && customDevices.value.length > 0)
const { wideEl, colStyle } = useWideCols(previewWide)

function clearPreview() {
  previewSrc.value = ''
  previewName.value = ''
  previewMeta.value = ''
  previewIsImage.value = false
  previewIsVideo.value = false
  previewDialog.value = false
}

function enterSelect() {
  clearPreview()
  selectedPaths.value = []
  selectMode.value = true
}

function onRowClick(row, column, event) {
  if (selectMode.value) {
    const p = entryPath(row)
    const i = selectedPaths.value.indexOf(p)
    if (i >= 0) selectedPaths.value.splice(i, 1)
    else selectedPaths.value.push(p)
    return
  }
  // 点操作列按钮(下载/分享/重命名/预览)时不顺带切预览
  if (event?.target?.closest?.('button, a')) return
  // 宽屏单击文件即预览(目录不预览);窄屏维持原样:单击无动作,双击才预览/进目录
  if (previewWide.value && !row.isDir && isPreviewable(row.name)) preview(row)
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
  await ElMessageBox.confirm(t('storage.confirmDeleteEntries', { n: selectedPaths.value.length }), t('common.warning'), {
    type: 'warning',
    closeOnClickModal: true,
  })
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
  clearPreview()
  loadFiles()
}

function navigateToPath(path) {
  activePath.value = path
  selectedPaths.value = []
  clearPreview()
  loadFiles()
}

function goParent() {
  if (!activePath.value) return
  const idx = activePath.value.lastIndexOf('/')
  activePath.value = idx >= 0 ? activePath.value.slice(0, idx) : ''
  selectedPaths.value = []
  clearPreview()
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
  previewMeta.value = row.isDir ? '' : [formatSize(row.size), formatTime(row.modified)].filter(Boolean).join(' · ')
  previewIsImage.value = /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(row.name)
  previewIsVideo.value = /\.(mp4|webm|ogg)$/i.test(row.name)
  // 宽屏出右栏,窄屏才弹 70% 弹窗
  previewDialog.value = !previewWide.value
}

function download(row) {
  window.open(row.signedUrl ? `${row.signedUrl}&download=true` : storageApi.fileUrl(activeDeviceId.value, entryPath(row), true), '_blank')
}

// 分享文件:私有存储需要登录态,壳内下载时带 Bearer;Web 不支持文件分享时退回下载
function shareFile(row) {
  if (row.isDir) return
  const url = row.signedUrl || storageApi.fileUrl(activeDeviceId.value, entryPath(row), false)
  shareFiles([url], row.name)
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
  // 全局搜索的结果要能落到具体目录:?path= 在设备确定后覆盖初始目录
  if (typeof route.query.path === 'string') activePath.value = route.query.path
  if (activeDeviceId.value) loadFiles()
})

// 全局搜索的文件类只搜「当前目录」:把这里当作唯一上下文来源,离开文件页即清空
watch(
  [activeDeviceId, activePath],
  () => {
    search.setFileContext(activeDeviceId.value ? { deviceId: activeDeviceId.value, path: activePath.value } : null)
  },
  { immediate: true },
)

onBeforeUnmount(() => search.setFileContext(null))
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
  background: var(--color-primary, var(--color-brand));
  border-color: var(--color-primary, var(--color-brand));
}
.select-count {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.browse-empty {
  padding: 40px 20px;
}
/* 宽屏右栏预览:预览栏 260–380px 随可用宽度收放(外壳侧栏占掉 320px 左右,
   900px 视口下内容区只有 ~580px,固定 380 会把表格挤到 184px),表格占余下宽度;
   列内滚动由 main.css 的 .wide-col 统一。 */
.file-cols.wide-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(260px, 34%, 380px);
  gap: 16px;
}
.preview-col {
  min-width: 0;
}
.pane-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  color: var(--color-text-secondary);
  font-size: 13px;
}

@media (max-width: 768px) {
  .page {
    overflow-x: hidden;
  }
}
</style>
