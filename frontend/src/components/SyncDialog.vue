<!-- 从设备同步向导:选设备 → 目录树懒加载勾选 → 确定后目录映射为影子记录(不拷贝文件);完成后通知父组件刷新 -->
<!-- target: album=映射为相册(默认) / video=映射为放映厅视频 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="$t(target === 'video' ? 'cinema.syncFromDevice' : 'album.syncFromDevice')" width="560px" @closed="cleanup">
    <!-- 步骤一:选设备 -->
    <div v-if="step === 1">
      <p class="wizard-hint">{{ $t(target === 'video' ? 'storage.mapHintVideo' : 'storage.mapHint') }}</p>
      <div
        v-for="d in devices"
        :key="d.id"
        class="device-row card"
        :class="{ active: deviceId === d.id }"
        @click="pickDevice(d)"
      >
        <span class="device-icon">{{ deviceIcon(d.deviceType) }}</span>
        <div class="device-info">
          <span class="device-name">{{ d.name }}</span>
          <span class="device-type">{{ d.deviceType }}</span>
        </div>
        <el-tag v-if="deviceId === d.id" size="small" type="success">{{ $t('common.selected') }}</el-tag>
      </div>
      <el-empty v-if="!devices.length && !loadingTree" :description="$t('storage.noDeviceHint')" :image-size="60" />
    </div>

    <!-- 步骤二:目录树勾选(懒加载) -->
    <div v-else>
      <p class="wizard-hint">{{ $t('storage.mapTreeHint', { device: deviceName }) }}</p>
      <div v-loading="loadingTree" class="tree-wrap">
        <el-tree
          ref="treeRef"
          :key="treeKey"
          lazy
          node-key="path"
          show-checkbox
          :props="{ label: 'name', isLeaf: (d) => d.isLeaf }"
          :load="loadNode"
          highlight-current
        />
      </div>
    </div>

    <div v-if="syncing" class="sync-progress">
      <el-progress :percentage="progressPct" :status="progressStatus" />
      <span class="progress-msg">{{ progressMsg }}</span>
    </div>
    <div v-else-if="result" class="sync-result">{{ result.message }}</div>

    <template #footer>
      <el-button v-if="step === 2 && !syncing" @click="step = 1">{{ $t('common.back') }}</el-button>
      <el-button @click="visible = false">{{ syncing ? $t('storage.syncInBackground') : $t('common.cancel') }}</el-button>
      <el-button
        v-if="step === 2 && !syncing"
        type="primary"
        :disabled="!checkedPaths.length"
        @click="start"
      >{{ $t('storage.mapNow', { n: checkedPaths.length }) }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { storageApi, videoApi } from '@/api'
import { useSyncStore } from '@/stores/sync'

const { t } = useI18n()
const syncStore = useSyncStore()
const props = defineProps({ modelValue: Boolean, target: { type: String, default: 'album' } })
const emit = defineEmits(['update:modelValue', 'synced'])

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v; if (v) loadDevices() })
watch(visible, (v) => emit('update:modelValue', v))

const step = ref(1)
const devices = ref([])
const deviceId = ref(0)
const deviceName = ref('')
const treeRef = ref(null)
const treeKey = ref(0)
const loadingTree = ref(false)
const syncing = ref(false)
const result = ref(null)
const progressData = ref(null)
let timer = null
let runningTaskId = null

// 百度网盘路径以 / 开头,本地设备为相对路径;根层调用 path 传空
const ROOT = (id) => devices.value.find((d) => d.id === id)?.deviceType === 'BAIDU' ? '/' : ''

const deviceIcon = (type) => ({ BAIDU: '☁️', NAS: '🗄️', MOUNT: '📁', REMOTE: '🌐' }[type] || '💾')

async function loadDevices() {
  step.value = 1
  deviceId.value = 0
  result.value = null
  devices.value = (await storageApi.devices()).filter((d) => d.id !== 0)
}

function pickDevice(d) {
  deviceId.value = d.id
  deviceName.value = d.name
  treeKey.value++
  step.value = 2
}

// 懒加载目录节点:只列目录(isDir),文件不进树(映射对象是目录)
async function loadNode(node, resolve) {
  const path = node.level === 0 ? ROOT(deviceId.value) : node.data.path
  loadingTree.value = true
  try {
    const items = await storageApi.browse(deviceId.value, path)
    resolve(items.filter((i) => i.isDir).map((i) => ({
      name: i.name,
      path: joinPath(path, i.name),
      isLeaf: false, // 是否有子目录未知,展开后见分晓;空目录显示为可展开但无内容
    })))
  } catch (e) {
    ElMessage.error(e.message || t('storage.browseFailed'))
    resolve([])
  } finally {
    loadingTree.value = false
  }
}

function joinPath(parent, name) {
  if (!parent || parent === '/') return '/' + name
  return parent.replace(/\/$/, '') + '/' + name
}

// 勾选的目录:剔除已被勾选祖先覆盖的子目录(后端递归整个子树,子路径冗余)
const checkedPaths = computed(() => {
  if (!treeRef.value) return []
  const checked = treeRef.value.getCheckedNodes()
  return checked
    .filter((n) => !checked.some((o) => o !== n && o.path.length > n.path.length && o.path.startsWith(n.path + '/')))
    .map((n) => n.path)
})

async function start() {
  syncing.value = true
  result.value = null
  try {
    const payload = { deviceId: deviceId.value, paths: checkedPaths.value }
    const { taskId } = props.target === 'video' ? await videoApi.map(payload) : await storageApi.map(payload)
    runningTaskId = taskId
    timer = setInterval(async () => {
      try {
        const p = await storageApi.syncProgress(taskId)
        progressData.value = p
        if (p.status === 'DONE' || p.status === 'FAILED') {
          stopTimer()
          syncing.value = false
          if (p.status === 'DONE') {
            result.value = p
            ElMessage.success(p.message || t('storage.syncDone'))
            emit('synced')
            setTimeout(() => { visible.value = false }, 800) // 前台等待:展示 100% 片刻后自动关闭
          } else {
            ElMessage.error(p.message || t('storage.syncFailed'))
          }
        }
      } catch { stopTimer(); syncing.value = false }
    }, 1000)
  } catch { syncing.value = false }
}

const progressPct = computed(() => {
  const p = progressData.value
  if (!p || !p.totalDirs) return 0
  return Math.min(100, Math.round(((p.doneDirs || 0) / p.totalDirs) * 100))
})
const progressStatus = computed(() => (progressData.value?.status === 'DONE' ? 'success' : undefined))
const progressMsg = computed(() => progressData.value?.lastAlbum
  ? `${t('storage.syncing')} · ${progressData.value.lastAlbum}`
  : t('storage.syncing'))

function stopTimer() { if (timer) { clearInterval(timer); timer = null } }
function cleanup() {
  // 同步进行中关窗 = 转后台:全局 store 继续轮询,完成时弹通知
  if (syncing.value && runningTaskId) syncStore.watch(runningTaskId)
  stopTimer()
  syncing.value = false
  runningTaskId = null
}
</script>

<style scoped>
.wizard-hint { margin: 0 0 12px; font-size: 13px; color: var(--color-text-secondary); }
.device-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 16px; margin-bottom: 10px; cursor: pointer;
  border: 1px solid transparent; transition: border-color 0.15s, transform 0.15s;
}
.device-row:hover { transform: translateY(-1px); }
.device-row.active { border-color: var(--color-primary, #b88c6e); }
.device-icon { font-size: 22px; }
.device-info { display: flex; flex-direction: column; gap: 2px; flex: 1; }
.device-name { font-size: 14px; font-weight: 600; color: var(--color-text); }
.device-type { font-size: 11px; color: var(--color-text-secondary); }
.tree-wrap { max-height: 380px; overflow-y: auto; border: 1px solid var(--color-border, #e4ddd0); border-radius: 10px; padding: 8px; }
.sync-progress { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; }
.progress-msg { font-size: 12px; color: var(--color-text-secondary); }
.sync-result { margin-top: 8px; padding: 8px 12px; background: var(--color-bg-2); border-radius: 6px; font-size: 13px; }
</style>
