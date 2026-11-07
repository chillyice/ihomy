<!-- 模拟器播放器(EmulatorJS):按机种加载 GBA/GB/GBC/NES/SNES/MD·GG/街机/NDS 核心;
     本地打开 ROM(不托管/不分发)或按 src 播放已导入的游戏;支持全屏与云端存档。 -->
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

    <div class="gba-card card game-surface" @contextmenu.prevent @dblclick.prevent>
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
        <div class="drop-sub">{{ $t('gba.dropSub', { systems: systemList }) }}</div>
      </div>

      <template v-else>
        <div class="gba-bar">
          <span v-if="systemLabel" class="gba-sys">{{ systemLabel }}</span>
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
        <div ref="consoleEl" class="gba-console" :class="{ 'gba-console--pad': padLayout }" :style="{ '--gba-aspect': screenAspect }">
          <!-- 竖屏触屏:手柄拆到屏幕上方(肩键)与下方(方向键/AB/Start·Select),像一个真正的掌机 -->
          <div v-if="padLayout && showShoulder" class="gba-pad-row gba-pad-row--top">
            <button
              type="button"
              class="gba-pad-shoulder"
              :class="{ down: padDown.l }"
              @pointerdown.prevent="padButtonDown('l', $event)"
              @pointerup.prevent="padButtonUp('l')"
              @pointercancel.prevent="padButtonUp('l')"
              @lostpointercapture="padButtonUp('l')"
            >
              L
            </button>
            <button
              type="button"
              class="gba-pad-shoulder"
              :class="{ down: padDown.r }"
              @pointerdown.prevent="padButtonDown('r', $event)"
              @pointerup.prevent="padButtonUp('r')"
              @pointercancel.prevent="padButtonUp('r')"
              @lostpointercapture="padButtonUp('r')"
            >
              R
            </button>
          </div>

          <div ref="stage" class="gba-stage">
            <div ref="container" :id="playerId" class="gba-stage-inner" :class="{ 'gba-stage-inner--pad': padLayout }"></div>
          </div>

          <div v-if="padLayout" class="gba-pad-row gba-pad-row--bottom">
            <div
              ref="dpadEl"
              class="gba-dpad"
              role="group"
              :aria-label="$t('gba.dpad')"
              @pointerdown.prevent="onDpadDown"
              @pointermove.prevent="onDpadMove"
              @pointerup.prevent="onDpadEnd"
              @pointercancel.prevent="onDpadEnd"
              @lostpointercapture="onDpadEnd"
            >
              <i class="gba-dpad-dir up" :class="{ 'is-on': dpadDown.up }"></i>
              <i class="gba-dpad-dir down" :class="{ 'is-on': dpadDown.down }"></i>
              <i class="gba-dpad-dir left" :class="{ 'is-on': dpadDown.left }"></i>
              <i class="gba-dpad-dir right" :class="{ 'is-on': dpadDown.right }"></i>
              <span class="gba-dpad-hub"></span>
            </div>

            <div class="gba-pad-sys">
              <button
                type="button"
                class="gba-pad-sysbtn"
                :class="{ down: padDown.select }"
                @pointerdown.prevent="padButtonDown('select', $event)"
                @pointerup.prevent="padButtonUp('select')"
                @pointercancel.prevent="padButtonUp('select')"
                @lostpointercapture="padButtonUp('select')"
              >
                SELECT
              </button>
              <button
                type="button"
                class="gba-pad-sysbtn"
                :class="{ down: padDown.start }"
                @pointerdown.prevent="padButtonDown('start', $event)"
                @pointerup.prevent="padButtonUp('start')"
                @pointercancel.prevent="padButtonUp('start')"
                @lostpointercapture="padButtonUp('start')"
              >
                START
              </button>
            </div>

            <div class="gba-pad-ab">
              <button
                type="button"
                class="gba-pad-round is-b"
                :class="{ down: padDown.b }"
                @pointerdown.prevent="padButtonDown('b', $event)"
                @pointerup.prevent="padButtonUp('b')"
                @pointercancel.prevent="padButtonUp('b')"
                @lostpointercapture="padButtonUp('b')"
              >
                B
              </button>
              <button
                type="button"
                class="gba-pad-round is-a"
                :class="{ down: padDown.a }"
                @pointerdown.prevent="padButtonDown('a', $event)"
                @pointerup.prevent="padButtonUp('a')"
                @pointercancel.prevent="padButtonUp('a')"
                @lostpointercapture="padButtonUp('a')"
              >
                A
              </button>
            </div>
          </div>
        </div>
      </template>

      <div v-if="loading" class="gba-status">{{ $t('gba.loading') }}</div>
      <div v-if="error" class="gba-status error">{{ error }}</div>

      <input v-if="!src" ref="fileInput" type="file" :accept="romAccept" class="hidden-input" @change="onPick" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { FullScreen, ArrowLeft } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { formatDateTime } from '@/utils/datetime'
import { gameApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { emulatorAccept, emulatorSystemsText, systemOf, typeForFile, typeLabel } from '@/utils/games/emulatorSystems'

const props = defineProps({
  src: { type: String, default: '' },
  title: { type: String, default: '' },
  backTo: { type: String, default: '' },
  // 已导入游戏的机种码(GBA/GB/NES/SNES/SEGA/ARCADE/NDS);工具箱本地模式按文件扩展名自动判定
  system: { type: String, default: 'GBA' },
  // 已导入游戏的 id:带上它才启用「游戏目录 saves/」云端存档(工具箱本地模式没有游戏目录)
  gameId: { type: [Number, String], default: null },
})

const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)
const container = ref(null)
const stage = ref(null)
const consoleEl = ref(null)
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

// 本地打开时按扩展名判定的机种(已导入游戏直接用 props.system)
const localSystem = ref('')
const effectiveType = computed(() => (props.src ? props.system : localSystem.value) || 'GBA')
const systemInfo = computed(() => systemOf(effectiveType.value) || systemOf('GBA'))
const systemLabel = computed(() => typeLabel(effectiveType.value))
const romAccept = emulatorAccept()
const systemList = emulatorSystemsText()

// —— 竖屏机身布局:触屏 + 竖屏 + 该机种键位能装进机身(见 emulatorSystems 的 pad)时,
// 隐藏 EmulatorJS 自带的悬浮手柄,改用屏幕上下两排实体感按键 ——
// 按键值(RetroArch 手柄编号,mGBA 与 EJS 的 GBA 预设一致):B=0 / Select=2 / Start=3 / 上下左右=4~7 / A=8 / L=10 / R=11
const PAD_INPUT = { up: 4, down: 5, left: 6, right: 7, select: 2, start: 3, a: 8, b: 0, l: 10, r: 11 }
const PAD_DIRS = ['up', 'down', 'left', 'right']

const gameStarted = ref(false)
const screenAspect = ref('3 / 2')
const padDown = reactive({ a: false, b: false, l: false, r: false, start: false, select: false })
const dpadDown = reactive({ up: false, down: false, left: false, right: false })
const dpadEl = ref(null)
let dpadPointerId = null

// 是否用「掌机机身」布局:触屏 + 竖屏 + 该机种键位与机身一致(GB/NES 同 GBA 的 AB+Start/Select,SNES 起回退 EJS 悬浮手柄)。
const isTouch = ref(false)
const isPortrait = ref(false)
const padLayout = computed(() => isTouch.value && isPortrait.value && !!systemInfo.value?.pad)
// GBA 才有 L/R 肩键;GB 与 NES 的机身不画肩键
const showShoulder = computed(() => effectiveType.value === 'GBA')

const refreshViewport = () => {
  isTouch.value = (navigator.maxTouchPoints || 0) > 0 || !!window.matchMedia?.('(any-pointer: coarse)').matches
  isPortrait.value = window.innerHeight >= window.innerWidth
}

// 核心就绪前(加载中/起始按钮阶段)不送输入,避免把按键缓存进模拟器
const sendPadRaw = (value, raw) => {
  const manager = gameManager()
  if (!gameStarted.value || !manager) return
  try {
    manager.simulateInput(0, value, raw)
  } catch (e) {
    /* 核心正在重启/已销毁时忽略 */
  }
}
const sendPad = (value, down) => sendPadRaw(value, down ? 1 : 0)

const padButtonDown = (id, e) => {
  if (padDown[id]) return
  padDown[id] = true
  try {
    e.currentTarget.setPointerCapture?.(e.pointerId)
  } catch (err) {
    /* 老浏览器不支持指针捕获时靠 pointerup 兜底 */
  }
  sendPad(PAD_INPUT[id], true)
}

const padButtonUp = (id) => {
  if (!padDown[id]) return
  padDown[id] = false
  sendPad(PAD_INPUT[id], false)
}

const dpadApply = (next) => {
  for (const dir of PAD_DIRS) {
    if (next[dir] === dpadDown[dir]) continue
    dpadDown[dir] = next[dir]
    sendPad(PAD_INPUT[dir], next[dir])
  }
}

// 以圆盘中心算方向,两轴各自过阈值 → 天然支持斜向;阈值按盘面尺寸换算,小屏也不误触
const dpadFromEvent = (e, rect) => {
  const dx = e.clientX - (rect.left + rect.width / 2)
  const dy = e.clientY - (rect.top + rect.height / 2)
  const dead = Math.max(6, rect.width * 0.16)
  return { up: dy < -dead, down: dy > dead, left: dx < -dead, right: dx > dead }
}

const onDpadDown = (e) => {
  if (dpadPointerId !== null) return
  dpadPointerId = e.pointerId
  try {
    e.currentTarget.setPointerCapture?.(e.pointerId)
  } catch (err) {
    /* 同上 */
  }
  dpadApply(dpadFromEvent(e, e.currentTarget.getBoundingClientRect()))
}

const onDpadMove = (e) => {
  if (e.pointerId !== dpadPointerId) return
  dpadApply(dpadFromEvent(e, e.currentTarget.getBoundingClientRect()))
}

const onDpadEnd = (e) => {
  if (dpadPointerId !== null && e.pointerId !== dpadPointerId) return
  dpadPointerId = null
  dpadApply({ up: false, down: false, left: false, right: false })
}

// 切后台/转屏/拆模拟器时把按住的键全部松开,否则会卡住一个方向
const releaseAllPad = () => {
  for (const id of Object.keys(padDown)) {
    if (padDown[id]) padButtonUp(id)
  }
  dpadPointerId = null
  dpadApply({ up: false, down: false, left: false, right: false })
}

// —— 键盘兜底(外接键盘方向键失灵) ——
// EmulatorJS 只把 keydown/keyup 挂在模拟器容器上,且 keyChange 在「设置菜单/模拟器弹窗开着」
// 时直接 return:焦点一旦跑到容器外(点工具栏、存档浮层、页面空白),或模拟器内部状态把按键
// 吃掉,方向键就整体失灵——点画面也救不回来,只有刷新页面才恢复。
// 这里在 document 上兜底:按 EJS 自己的键位表把按键直送核心;只在「EJS 收不到」或
// 「EJS 收到了也会忽略」时补送,正常路径不重复(重复会把快速存档这类动作键按两次)。
const ejsKeyWouldIgnore = () => {
  const emu = window.EJS_emulator
  if (!emu) return true
  try {
    if (emu.settingsMenu && emu.settingsMenu.style.display !== 'none') return true
    if (typeof emu.isPopupOpen === 'function' && emu.isPopupOpen()) return true
    if (typeof emu.getSettingValue === 'function' && emu.getSettingValue('keyboardInput') === 'enabled') return true
  } catch (e) {
    return false /* 模拟器半初始化:交给 EJS 自己处理 */
  }
  return false
}

// 键位表与 keyChange 同源:controls[0][按键序号].value === keyCode
const ejsKeyCodeIndex = (keyCode) => {
  const controls = window.EJS_emulator?.controls?.[0]
  if (!controls) return undefined
  for (const k of Object.keys(controls)) {
    if (controls[k]?.value === keyCode) return Number(k)
  }
  return undefined
}

// 与 keyChange 对齐:16~23 是模拟轴,按下送满量程
const EJS_ANALOG_KEYS = [16, 17, 18, 19, 20, 21, 22, 23]

const onDocumentKey = (e) => {
  if (!gameStarted.value || e.repeat || e.ctrlKey || e.metaKey || e.altKey) return
  const el = container.value
  if (!el) return
  const target = e.target
  // 焦点在输入层(本页没有输入框,存档浮层等 Element Plus 层也不接管)时保持原行为
  if (target instanceof Element && target.closest('input, textarea, select, [contenteditable="true"], .el-popper, .el-overlay')) return
  const reached = el.contains(target)
  if (reached && !ejsKeyWouldIgnore()) return // EJS 正常收到并会处理,不重复送
  const idx = ejsKeyCodeIndex(e.keyCode)
  if (idx === undefined) return // 没绑到游戏按键的键放行(不 preventDefault)
  e.preventDefault() // 已映射的游戏键不让页面跟着滚动
  sendPadRaw(idx, e.type === 'keyup' ? 0 : EJS_ANALOG_KEYS.includes(idx) ? 0x7fff : 1)
}

// 画面比例跟核心实际缓冲一致(GB/GBC 是 10:9,GBA 是 3:2),避免机身布局里被拉伸
const syncScreenAspect = () => {
  const canvas = ejsInstance?.canvas
  if (canvas?.width && canvas?.height) screenAspect.value = `${canvas.width} / ${canvas.height}`
}

watch(padLayout, () => releaseAllPad())

// —— 云端存档:服务端是唯一事实来源,按成员隔离 ——
// 启动前先取服务端最新存档,趁存档库挂载完、ROM 还没加载时(EJS 的 saveDatabaseLoaded)写进模拟器,
// 核心加载 ROM 读 SRAM 时就已经是新档——换设备/清站点数据后的第一次打开也能接着上次的进度。
// 运行中每 30s、切后台、离开页面前把当前存档回写到服务端;本地 IndexedDB 只作断网兜底。
const SYNC_INTERVAL = 30000
const cloudSave = computed(() => props.gameId !== null && props.gameId !== '' && !!systemInfo.value?.saveExt)
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
  const ext = systemInfo.value.saveExt
  try {
    const list = await gameApi.listSaves(props.gameId)
    const latest = list.find((s) => s.name.toLowerCase().endsWith('.' + ext))
    if (!latest) return
    const buffer = await gameApi.downloadSave(props.gameId, latest.name)
    const bytes = new Uint8Array(buffer)
    if (bytes.length) pendingServerSave = { name: latest.name, bytes }
  } catch (e) {
    pendingServerSave = null
  }
}

// ROM 文件名去扩展名 = 核心电池存档的基名;核心就绪前拿不到 getSaveFilePath 时按机种注册表推导
// (saveDir 是核心在 FS 里的存档目录名,各核心不同:mGBA/Gambatte/FCEUmm/Snes9x/Genesis Plus GX/melonDS)
const guessSavePath = () => {
  try {
    const actual = gameManager()?.getSaveFilePath?.()
    if (actual && /^\/data\/saves\/[^/]+\/.+\.[a-z0-9]+$/i.test(actual)) return actual
  } catch (e) {
    /* 核心未初始化:退回推导路径 */
  }
  const raw = props.src.split('/').pop() || ''
  const base = raw.split('?')[0].split('#')[0]
  let name = base
  try {
    name = decodeURIComponent(base)
  } catch (e) {
    /* 保持原样 */
  }
  const info = systemInfo.value
  return `/data/saves/${info.saveDir}/${name.replace(/\.[^.]+$/, '')}.${info.saveExt}`
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
  if (document.hidden) {
    releaseAllPad()
    flushSave()
  }
}

const pick = () => fileInput.value?.click()

// 清理旧模拟器(只清 DOM + 释放全局引用;objectUrl 由调用方在合适时机释放,否则会提前失效)
// EmulatorJS 没有 destroy API:只清 DOM 不停核心,核心的模拟主循环与音频回调会继续跑——
// 返回上级页面后游戏声音仍在放、CPU 照跑。先暂停(等价暂停按钮)再拆 DOM。
const teardownEmulator = () => {
  stopSync()
  releaseAllPad()
  gameStarted.value = false
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
    // 机身布局要在注入 loader.js 前定好:竖屏时关掉 EJS 菜单里的全屏(全屏只覆盖画面,会丢手柄),改由工具栏全屏整台机身
    refreshViewport()
    if (padLayout.value) window.EJS_Buttons = { fullscreen: { visible: false } }
    else delete window.EJS_Buttons
    // fileName/src 变更后 v-else 分支(含 container ref)可能尚未渲染,等下一拍确保 DOM 就位
    await nextTick()

    // 设置 EmulatorJS 全局配置(必须在注入 loader.js 前就位;loader.js 读取后 new EmulatorJS 启动)
    window.EJS_player = '#' + playerId
    window.EJS_core = systemInfo.value.core
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
      syncScreenAspect()
      gameStarted.value = true
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
  const type = typeForFile(file.name)
  if (!type) {
    ElMessage.warning(t('gba.invalidType'))
    return
  }
  localSystem.value = type
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
  // 全屏整台机身(而不是单画面),竖屏机身布局进全屏后手柄仍在
  const target = consoleEl.value || stage.value
  if (!target) return
  if (!document.fullscreenElement) {
    target.requestFullscreen?.()
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
  document.addEventListener('keydown', onDocumentKey)
  document.addEventListener('keyup', onDocumentKey)
  window.addEventListener('resize', refreshViewport)
  window.addEventListener('orientationchange', refreshViewport)
  refreshViewport()
  sunLight?.suspendEffects()
  if (props.src) {
    fileName.value = props.title || props.src.split('/').pop() || ''
    loadRom(props.src)
  }
})
onBeforeUnmount(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  document.removeEventListener('keydown', onDocumentKey)
  document.removeEventListener('keyup', onDocumentKey)
  window.removeEventListener('resize', refreshViewport)
  window.removeEventListener('orientationchange', refreshViewport)
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
  delete window.EJS_Buttons
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 类名沿用历史 gba- 前缀:同一个播放器承载所有机种,不按机种另起前缀,新增样式请沿用 */
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
.gba-sys {
  flex: none;
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--color-text-secondary, #888);
  border: 1px solid rgba(0, 0, 0, 0.12);
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
/* 画布按核心缓冲比例等比落在黑框里,不被容器拉伸(竖屏机身布局与桌面固定高度都受益) */
.gba-stage-inner :deep(canvas) {
  object-fit: contain;
}
/* 机身布局下关掉 EJS 自带的悬浮手柄,避免与本页按键重复 */
.gba-stage-inner--pad :deep(.ejs_virtualGamepad_parent) {
  display: none !important;
}
.gba-stage:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
}

/* ===== 掌机机身布局(触屏 + 竖屏):肩键在画面上方,方向键/AB/Start·Select 在下方 ===== */
.gba-console {
  display: flex;
  flex-direction: column;
}
.gba-console--pad {
  --pad-size: clamp(92px, 27vw, 124px);
  --pad-face: rgba(255, 255, 255, 0.12);
  --pad-face-down: rgba(255, 255, 255, 0.4);
  --pad-line: rgba(255, 255, 255, 0.24);
  gap: 12px;
  padding: 12px;
  border-radius: 20px;
  background: linear-gradient(180deg, #2a2e38 0%, #15171c 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 10px 26px rgba(0, 0, 0, 0.22);
}
.gba-console--pad .gba-stage {
  width: 100%;
  height: auto;
  aspect-ratio: var(--gba-aspect, 3 / 2);
  border-radius: 8px;
}
.gba-console:fullscreen {
  width: 100vw;
  height: 100vh;
  justify-content: center;
  gap: 16px;
  padding: 12px;
  border-radius: 0;
  background: #0b0c10;
}
.gba-console:fullscreen .gba-stage {
  height: 100%;
}
.gba-console--pad:fullscreen .gba-stage {
  height: auto;
}
.gba-pad-row {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
/* 下方三栏:方向键 | Start·Select | AB。两侧按可用宽度等比缩放,窄屏也不会横向溢出 */
.gba-pad-row--bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}
.gba-pad-shoulder {
  width: min(34%, 132px);
  height: 34px;
  border: 1px solid var(--pad-line);
  border-radius: 10px;
  background: var(--pad-face);
  color: rgba(255, 255, 255, 0.9);
  font-family: inherit;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.1em;
  touch-action: none;
  cursor: pointer;
  transition:
    background-color 0.12s ease,
    transform 0.12s ease;
}
.gba-pad-shoulder.down {
  background: var(--pad-face-down);
  transform: translateY(1px);
}
.gba-dpad {
  position: relative;
  justify-self: start;
  width: 100%;
  max-width: var(--pad-size);
  aspect-ratio: 1;
  touch-action: none;
}
.gba-dpad::before,
.gba-dpad::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
  border: 1px solid var(--pad-line);
  border-radius: 10px;
  background: var(--pad-face);
}
.gba-dpad::before {
  left: 33%;
  top: 0;
  width: 34%;
  height: 100%;
}
.gba-dpad::after {
  top: 33%;
  left: 0;
  width: 100%;
  height: 34%;
}
.gba-dpad-hub {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 20%;
  height: 20%;
  margin: -10% 0 0 -10%;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.28);
}
.gba-dpad-dir {
  position: absolute;
  width: 0;
  height: 0;
  border: 7px solid transparent;
  opacity: 0.45;
  transition: opacity 0.1s ease;
}
.gba-dpad-dir.up {
  left: 50%;
  top: 8%;
  margin-left: -7px;
  border-top-width: 0;
  border-bottom-color: #fff;
}
.gba-dpad-dir.down {
  left: 50%;
  bottom: 8%;
  margin-left: -7px;
  border-bottom-width: 0;
  border-top-color: #fff;
}
.gba-dpad-dir.left {
  top: 50%;
  left: 8%;
  margin-top: -7px;
  border-left-width: 0;
  border-right-color: #fff;
}
.gba-dpad-dir.right {
  top: 50%;
  right: 8%;
  margin-top: -7px;
  border-right-width: 0;
  border-left-color: #fff;
}
.gba-dpad-dir.is-on {
  opacity: 1;
}
.gba-pad-sys {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.gba-pad-sysbtn {
  width: clamp(58px, 18vw, 76px);
  height: 28px;
  border: 1px solid var(--pad-line);
  border-radius: 14px;
  background: var(--pad-face);
  color: rgba(255, 255, 255, 0.88);
  font-family: inherit;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
  transform: rotate(-12deg);
  touch-action: none;
  cursor: pointer;
  transition: background-color 0.12s ease;
}
.gba-pad-sysbtn.down {
  background: var(--pad-face-down);
}
.gba-pad-ab {
  position: relative;
  justify-self: end;
  width: 100%;
  max-width: var(--pad-size);
  aspect-ratio: 1;
}
.gba-pad-round {
  position: absolute;
  width: 46%;
  height: 46%;
  border: 1px solid var(--pad-line);
  border-radius: 50%;
  background: var(--pad-face);
  color: rgba(255, 255, 255, 0.92);
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
  touch-action: none;
  cursor: pointer;
  transition:
    background-color 0.12s ease,
    transform 0.12s ease;
}
.gba-pad-round.is-b {
  left: 0;
  bottom: 4%;
}
.gba-pad-round.is-a {
  right: 4%;
  top: 0;
}
.gba-pad-round.down {
  background: var(--pad-face-down);
  transform: scale(0.96);
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
  .gba-card {
    padding: 12px;
  }
  .gba-stage {
    height: 320px;
  }
  /* 机身布局由画面比例决定高度(压过后面的固定高度) */
  .gba-console--pad .gba-stage {
    height: auto;
  }
}
</style>
