<!-- GBA 播放器(EmulatorJS + mGBA 核心):本地加载 .gba(不托管/不分发)或按 src 加载已导入的游戏;支持全屏。 -->
<template>
  <div class="page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <div class="game-top-actions">
        <el-button v-if="backTo" size="small" round @click="router.push(backTo)">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('games.back') }}
        </el-button>
        <el-button v-if="fileName || src" size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          {{ isFullscreen ? $t('gba.exitFullscreen') : $t('gba.fullscreen') }}
        </el-button>
      </div>
    </PageToolbar>

    <div class="gba-card card">
      <div
        v-a11y-click
        v-if="!fileName && !src"
        class="drop-zone"
        :class="{ dragging }"
        @click="pick"
        @dragover.prevent="dragging = true"
        @dragleave="dragging = false"
        @drop.prevent="onDrop"
      >
        <div class="drop-icon">🎮</div>
        <div class="drop-title">{{ $t('gba.dropTitle') }}</div>
        <div class="drop-sub">{{ $t('gba.dropSub') }}</div>
      </div>

      <template v-else>
        <div class="gba-bar">
          <span class="gba-name">{{ fileName }}</span>
          <el-popover
            v-if="cloudSave"
            v-model:visible="savesOpen"
            placement="bottom-end"
            :width="320"
            trigger="click"
            @after-enter="loadSaveList"
          >
            <template #reference>
              <el-button size="small" round :loading="savesLoading">{{ $t('gba.saves') }}</el-button>
            </template>
            <div class="save-panel">
              <div class="save-panel-title">{{ $t('gba.savesTitle') }}</div>
              <div v-if="saves.length === 0" class="save-panel-empty">{{ $t('gba.savesEmpty') }}</div>
              <div v-for="s in saves" :key="s.name" class="save-row">
                <div class="save-meta">
                  <div class="save-name">{{ s.name }}</div>
                  <div class="save-sub">{{ sizeText(s.size) }} · {{ formatDateTime(s.updatedAt) }}</div>
                </div>
                <el-button size="small" text type="primary" @click="loadSave(s)">{{ $t('gba.saveLoad') }}</el-button>
                <el-button size="small" text type="danger" @click="removeSave(s)">{{ $t('gba.saveDelete') }}</el-button>
              </div>
              <div class="save-panel-hint">{{ $t('gba.savesHint') }}</div>
            </div>
          </el-popover>
          <el-button v-if="!src" size="small" round @click="pick">{{ $t('gba.reselect') }}</el-button>
          <el-button v-if="!src" size="small" round text type="danger" @click="close">{{ $t('gba.close') }}</el-button>
        </div>
        <div ref="stage" class="gba-stage">
          <div ref="container" :id="playerId" class="gba-stage-inner"></div>
        </div>
      </template>

      <div v-if="loading" class="gba-status">{{ $t('gba.loading') }}</div>
      <div v-if="error" class="gba-status error">{{ error }}</div>

      <input v-if="!src" ref="fileInput" type="file" accept=".gba,.gbc,.gb" class="hidden-input" @change="onPick" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen, ArrowLeft } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { formatDateTime } from '@/utils/datetime'
import { gameApi } from '@/api'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  src: { type: String, default: '' },
  title: { type: String, default: '' },
  backTo: { type: String, default: '' },
  // 已导入游戏的 id:带上它才启用「游戏目录 saves/」云端存档(工具箱本地模式没有游戏目录)
  gameId: { type: [Number, String], default: null },
})

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)
const container = ref(null)
const stage = ref(null)
// EmulatorJS 的 setElements() 用 document.querySelector(element) 定位容器,要求传 CSS 选择器字符串(官方约定 EJS_player='#game'),
// 不能传 DOM 元素(否则抛 "[object HTMLDivElement] is not a valid selector" 导致模拟器永不初始化)。故给容器分配稳定 id。
const playerId = 'gba-stage-inner'
const fileInput = ref(null)
const fileName = ref('')
const dragging = ref(false)
const loading = ref(false)
const error = ref('')
const isFullscreen = ref(false)

let objectUrl = null
let loadFallbackTimer = null

// —— 云端存档:服务端是唯一事实来源,按成员隔离 ——
// 启动前先取服务端最新存档,趁存档库挂载完、ROM 还没加载时(EJS 的 saveDatabaseLoaded)写进模拟器,
// 核心加载 ROM 读 SRAM 时就已经是新档——换设备/清站点数据后的第一次打开也能接着上次的进度。
// 运行中每 30s、切后台、离开页面前把当前存档回写到服务端;本地 IndexedDB 只作断网兜底。
const SYNC_INTERVAL = 30000
const cloudSave = computed(() => props.gameId !== null && props.gameId !== '')
const saves = ref([])
const savesLoading = ref(false)
const savesOpen = ref(false)
let syncTimer = null
let emulatorStarted = false
let lastPushedHash = 0
let syncWarned = false
let pendingServerSave = null // 启动前取到的服务端最新电池存档 { name, bytes }
let serverSeedUsed = false // 本次启动是否用服务端存档预置过
let ejsInstance = null

const syncHashKey = () => `ihomy:gba:savehash:${props.gameId}:${userStore.userInfo?.id ?? 'anon'}`

// FNV-1a:只用来判断存档是否变化,不要求密码学强度
const hashBytes = (bytes) => {
  let h = 0x811c9dc5
  for (let i = 0; i < bytes.length; i++) {
    h ^= bytes[i]
    h = Math.imul(h, 0x01000193)
  }
  return h >>> 0
}

const gameManager = () => window.EJS_emulator?.gameManager || null

// 先让核心落盘再读当前存档
const currentSaveBytes = () => {
  const manager = gameManager()
  if (!manager || !emulatorStarted) return null
  try {
    return manager.getSaveFile() || null
  } catch (e) {
    return null
  }
}

const saveFileName = () => {
  try {
    const path = gameManager()?.getSaveFilePath()
    return path ? path.split('/').pop() : 'save.srm'
  } catch (e) {
    return 'save.srm'
  }
}

const pushSave = async (bytes, name) => {
  if (!cloudSave.value) return
  const data = bytes || currentSaveBytes()
  if (!data || !data.length) return
  const hash = hashBytes(data)
  if (hash === lastPushedHash) return
  const fileName = name || saveFileName()
  try {
    await gameApi.uploadSave(props.gameId, new Blob([new Uint8Array(data)]), fileName)
    lastPushedHash = hash
    localStorage.setItem(syncHashKey(), String(hash))
  } catch (e) {
    if (!syncWarned) {
      syncWarned = true
      ElMessage.warning(t('gba.saveSyncFailed'))
    }
  }
}

// 启动前把服务端最新电池存档拿到手(与模拟器生命周期无关,失败则退回本机/空档)
const prefetchServerSave = async () => {
  pendingServerSave = null
  if (!cloudSave.value) return
  try {
    const list = await gameApi.listSaves(props.gameId)
    const latest = list.find((s) => /\.(srm|sav)$/i.test(s.name))
    if (!latest) return
    const buffer = await gameApi.downloadSave(props.gameId, latest.name)
    const bytes = new Uint8Array(buffer)
    if (bytes.length) pendingServerSave = { name: latest.name, bytes }
  } catch (e) {
    pendingServerSave = null
  }
}

// ROM 文件名去扩展名 = 核心电池存档的基名;核心在 ROM 加载前拿不到 getSaveFilePath,只能按同一规则推导
const guessSavePath = () => {
  const raw = props.src.split('/').pop() || ''
  const base = raw.split('?')[0].split('#')[0]
  let name = base
  try {
    name = decodeURIComponent(base)
  } catch (e) {
    /* 保持原样 */
  }
  return `/data/saves/mGBA/${name.replace(/\.[^.]+$/, '')}.srm`
}

// 存档库挂载完(EJS 在挂载 IDBFS 后、加载 ROM 前触发):此刻写进 FS 的存档会被核心读进 SRAM
const seedSaveOnMount = async () => {
  if (!cloudSave.value) return
  const manager = gameManager()
  if (!manager) return
  const path = guessSavePath()
  let local = null
  try {
    if (manager.FS.analyzePath(path).exists) local = manager.FS.readFile(path)
  } catch (e) {
    local = null
  }
  const recorded = Number(localStorage.getItem(syncHashKey()) || 0)
  const localHash = local && local.length ? hashBytes(local) : 0
  const serverBytes = pendingServerSave?.bytes || null

  if (localHash && recorded && localHash !== recorded) {
    // 本机有这台浏览器自己没传完的进度:先回传,核心继续用本机这份(不被服务端旧档回滚)
    await pushSave(local, path.split('/').pop())
    return
  }
  if (serverBytes) {
    // 服务端为准:盖写本地(首次打开/换设备/本机就是上次同步的那份),核心启动时即读到
    try {
      manager.writeFile(path, serverBytes)
      lastPushedHash = hashBytes(serverBytes)
      localStorage.setItem(syncHashKey(), String(lastPushedHash))
      serverSeedUsed = true
    } catch (e) {
      /* 预置失败时由 start 后的补写兜底 */
    }
    return
  }
  if (localHash) {
    // 服务端还没有存档:把本机这份(可能来自旧版本共享档时期)交上去
    await pushSave(local, path.split('/').pop())
  }
}

// start 后校验预置是否落在核心实际路径上(路径推导有出入时补一次,晚于 SRAM 初读但好过没有)
const reconcileAfterStart = () => {
  if (!serverSeedUsed || !pendingServerSave?.bytes) return
  const manager = gameManager()
  if (!manager) return
  try {
    const path = manager.getSaveFilePath()
    if (!path) return
    const exists = manager.FS.analyzePath(path).exists
    const current = exists ? manager.FS.readFile(path) : null
    if (!current || hashBytes(current) !== hashBytes(pendingServerSave.bytes)) {
      manager.writeFile(path, pendingServerSave.bytes)
      manager.loadSaveFiles()
    }
  } catch (e) {
    /* 忽略:下次 30s 周期会以当前进度为准 */
  }
}

// 拦截 EJS_emulator 的赋值:loader.js 创建实例的瞬间挂上 saveDatabaseLoaded 监听,
// 保证监听先于核心下载/ROM 加载(事后补挂会错过事件,存档就赶不上核心读 SRAM)
const installEmulatorInterceptor = () => {
  ejsInstance = null
  Object.defineProperty(window, 'EJS_emulator', {
    configurable: true,
    get: () => ejsInstance,
    set: (v) => {
      ejsInstance = v
      if (v && typeof v.on === 'function') v.on('saveDatabaseLoaded', seedSaveOnMount)
    },
  })
}

const startSync = () => {
  if (!cloudSave.value || emulatorStarted) return
  emulatorStarted = true
  syncWarned = false
  lastPushedHash = Number(localStorage.getItem(syncHashKey()) || 0)
  syncTimer = setInterval(() => pushSave(), SYNC_INTERVAL)
}

const stopSync = () => {
  if (syncTimer) {
    clearInterval(syncTimer)
    syncTimer = null
  }
  emulatorStarted = false
  serverSeedUsed = false
  pendingServerSave = null
}

// 离开页面前补推一次:字节要同步取(teardown 会清掉 window.EJS_emulator)
const flushSave = () => {
  if (!cloudSave.value || !emulatorStarted) return
  const bytes = currentSaveBytes()
  if (bytes) pushSave(bytes)
}

const loadSaveList = async () => {
  if (!cloudSave.value) return
  savesLoading.value = true
  try {
    saves.value = await gameApi.listSaves(props.gameId)
  } catch (e) {
    saves.value = []
  } finally {
    savesLoading.value = false
  }
}

const loadSave = async (save) => {
  try {
    const buffer = await gameApi.downloadSave(props.gameId, save.name)
    const manager = gameManager()
    if (!manager) return
    const bytes = new Uint8Array(buffer)
    manager.writeFile(manager.getSaveFilePath(), bytes)
    manager.loadSaveFiles()
    lastPushedHash = hashBytes(bytes)
    localStorage.setItem(syncHashKey(), String(lastPushedHash))
    savesOpen.value = false
    ElMessage.success(t('gba.saveLoaded'))
  } catch (e) {
    // 请求拦截器已提示失败原因
  }
}

const removeSave = async (save) => {
  try {
    await ElMessageBox.confirm(t('gba.saveDeleteConfirm', { name: save.name }), t('gba.savesTitle'), {
      type: 'warning',
    })
  } catch (e) {
    return
  }
  try {
    await gameApi.deleteSave(props.gameId, save.name)
    // 删掉的是当前正在用的那份时,把本机副本与同步标记一并清掉,避免下次启动被断网兜底悄悄救回来
    if (save.name === saveFileName()) {
      const manager = gameManager()
      try {
        const path = manager?.getSaveFilePath()
        if (path && manager.FS.analyzePath(path).exists) manager.FS.unlink(path)
      } catch (e) {
        /* 模拟器没在运行就无需清理本机 */
      }
    }
    localStorage.removeItem(syncHashKey())
    lastPushedHash = 0
    ElMessage.success(t('gba.saveDeleted'))
    await loadSaveList()
  } catch (e) {
    // 请求拦截器已提示失败原因
  }
}

const sizeText = (bytes) => (bytes >= 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${bytes || 0} B`)

const onVisibilityChange = () => {
  if (document.hidden) flushSave()
}

const pick = () => fileInput.value?.click()

// 清理旧模拟器(只清 DOM + 释放全局引用;objectUrl 由调用方在合适时机释放,否则会提前失效)
// EmulatorJS 没有 destroy API:只清 DOM 不停核心,核心的模拟主循环与音频回调会继续跑——
// 返回上级页面后游戏声音仍在放、CPU 照跑。先暂停(等价暂停按钮)再拆 DOM。
const teardownEmulator = () => {
  stopSync()
  if (loadFallbackTimer) {
    clearTimeout(loadFallbackTimer)
    loadFallbackTimer = null
  }
  try {
    ejsInstance?.pause?.()
  } catch (e) {
    /* 模拟器未启动/已损坏时忽略 */
  }
  try {
    ejsInstance?.gameManager?.toggleMainLoop?.(0)
  } catch (e) {
    /* pause 已停则无操作;paused 标志错乱时这里兜底 */
  }
  if (container.value) container.value.innerHTML = ''
  delete window.EJS_emulator
  ejsInstance = null
  delete window.EJS_ready
  delete window.EJS_onGameStart
}

const onEmulatorReady = () => {
  loading.value = false
  if (loadFallbackTimer) {
    clearTimeout(loadFallbackTimer)
    loadFallbackTimer = null
  }
}

const loadRom = async (romUrl) => {
  loading.value = true
  error.value = ''
  try {
    flushSave()
    teardownEmulator()
    // fileName/src 变更后 v-else 分支(含 container ref)可能尚未渲染,等下一拍确保 DOM 就位
    await nextTick()

    // 设置 EmulatorJS 全局配置(必须在注入 loader.js 前就位;loader.js 读取后 new EmulatorJS 启动)
    window.EJS_player = '#' + playerId
    window.EJS_core = 'gba'
    window.EJS_gameUrl = romUrl
    window.EJS_pathtodata = '/emulatorjs/data/'
    window.EJS_startOnLoaded = true
    window.EJS_controlScheme = 0
    window.EJS_hideSettings = false
    // 按游戏隔离 EJS 的设置键/存档键;本地模式没有游戏 id 就不设
    if (cloudSave.value) window.EJS_gameID = Number(props.gameId) || undefined
    // 用官方回调关 loading(loader.js 在实例就绪/游戏开始时触发),替代 MutationObserver
    window.EJS_ready = onEmulatorReady
    // 游戏跑起来后核心的 /data/saves 才挂载好,这时才接管存档同步
    window.EJS_onGameStart = () => {
      onEmulatorReady()
      reconcileAfterStart()
      startSync()
    }

    // 云端存档:先取好服务端最新档,再装实例拦截器,最后注入 loader——顺序不能倒,
    // 拦截器要在 loader.js 给 window.EJS_emulator 赋值的瞬间挂上 saveDatabaseLoaded 监听
    await prefetchServerSave()
    installEmulatorInterceptor()

    // 只注入一次 loader.js(其内部读取全局配置并启动模拟器)
    const s = document.createElement('script')
    s.src = '/emulatorjs/data/loader.js'
    document.head.appendChild(s)

    // 兜底:回调因异常未触发时,15s 后关 loading 避免永久转圈
    loadFallbackTimer = setTimeout(() => {
      loading.value = false
    }, 15000)
  } catch (e) {
    error.value = t('gba.loadFailed')
    ElMessage.error(t('gba.loadFailed'))
    loading.value = false
  }
}

const playFile = async (file) => {
  if (!file) return
  if (!/\.(gba|gbc|gb)$/i.test(file.name)) {
    ElMessage.warning(t('gba.invalidType'))
    return
  }
  fileName.value = file.name
  if (objectUrl) URL.revokeObjectURL(objectUrl) // 释放上一个本地 ROM 的 blob URL
  objectUrl = URL.createObjectURL(file)
  await loadRom(objectUrl)
}

const onPick = (e) => {
  const f = e.target.files?.[0]
  if (f) playFile(f)
  e.target.value = ''
}
const onDrop = (e) => {
  dragging.value = false
  const f = e.dataTransfer?.files?.[0]
  if (f) playFile(f)
}
const close = () => {
  flushSave()
  teardownEmulator()
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = null
  }
  fileName.value = ''
  error.value = ''
}

const toggleFullscreen = () => {
  if (!stage.value) return
  if (!document.fullscreenElement) {
    stage.value.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}
const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('visibilitychange', onVisibilityChange)
  sunLight?.suspendEffects()
  if (props.src) {
    fileName.value = props.title || props.src.split('/').pop() || ''
    loadRom(props.src)
  }
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  // 先取一次当前存档快照再拆模拟器,路由内跳转时请求能照常完成
  flushSave()
  teardownEmulator()
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = null
  }
  // 清理 EmulatorJS 全局变量
  delete window.EJS_player
  delete window.EJS_core
  delete window.EJS_gameUrl
  delete window.EJS_pathtodata
  delete window.EJS_gameID
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.gba-card {
  padding: 24px;
}
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 260px;
  border: 2px dashed rgba(0, 0, 0, 0.14);
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  padding: 32px;
  text-align: center;
}
.drop-zone:hover,
.drop-zone.dragging {
  border-color: var(--color-accent, var(--color-brand));
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.06);
}
.drop-icon {
  font-size: 42px;
}
.drop-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary, #333);
}
.drop-sub {
  font-size: 13px;
  color: var(--color-text-secondary, #888);
}
.gba-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.gba-name {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary, #333);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.gba-stage {
  position: relative;
  height: 480px;
  border-radius: 12px;
  background: #000;
  overflow: hidden;
}
.gba-stage-inner {
  width: 100%;
  height: 100%;
}
.gba-stage:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
}
.gba-status {
  margin-top: 14px;
  font-size: 13px;
  color: var(--color-text-secondary, #888);
  text-align: center;
}
.gba-status.error {
  color: #f56c6c;
}
.hidden-input {
  display: none;
}
.save-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.save-panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary, #333);
}
.save-panel-empty,
.save-panel-hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary, #888);
}
.save-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}
.save-meta {
  flex: 1;
  min-width: 0;
}
.save-name {
  font-size: 13px;
  color: var(--color-text-primary, #333);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.save-sub {
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}
@media (max-width: 768px) {
  .gba-stage {
    height: 320px;
  }
}
</style>
