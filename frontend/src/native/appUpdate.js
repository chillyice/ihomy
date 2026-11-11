// App 内更新(仅原生壳):检查 → 应用内下载 → 交给系统安装器,全程不离开 ihomy。
//
// 口径:
// - 启动时静默检查一次(见 native/index.js),设置页可手动查;
// - 有新版本弹 AppUpdateDialog(组件只负责渲染,状态都在这里);
// - 低于 minSupported = 强制更新,弹窗不给「稍后」;
// - 版本号解析失败一律不提示(宁漏不误),服务端版本比本机低也不提示降级;
// - **下载交给系统 DownloadManager**:锁屏、切后台、壳进程被杀都继续下,完成后下拉栏有通知;
//   前端只按 1 秒轮询进度,回到前台继续看,不再「锁屏就失败」;
// - 已下好的包(含上次没装成的)直接给「立即安装」,不重下 54MB;
// - 本机没给「安装未知应用」授权时,引导去系统设置,回来后自动继续安装。
import { reactive } from 'vue'
import { App as CapApp } from '@capacitor/app'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import request from '@/api/request'
import { serverUrl } from '@/utils/assetUrl'
import { isAndroidApp } from '@/utils/platform'
import { apkFileName, downloadPercent, updatePlan } from '@/utils/appUpdate'
import { getAppVersion } from './version'
import { canInstallApk, cleanupApks, installApk, openInstallSettings, queryApkDownload, startApkDownload } from './updater'

const t = (key, params) => i18n.global.t(key, params)

/** 安装包下限:真包 50MB 上下,低于这个数说明拿到的是错误页/半截文件,不能交给安装器 */
const MIN_APK_BYTES = 1024 * 1024
/** 进度轮询间隔:系统下载器没有推送,1 秒一次足够平滑(锁屏后轮询自动停,回前台接着查) */
const POLL_MS = 1000

/**
 * 更新弹窗状态。phase:
 * - idle        没在更新
 * - available   已发现新版本,等用户点「立即更新」(downloaded=true 时是「立即安装」)
 * - downloading 下载中(带 percent;可以锁屏/切后台)
 * - installer   已调起系统安装器
 * - permission  缺「安装未知应用」授权,等用户去系统设置
 * - error       下载/安装失败,可重试
 */
export const updateState = reactive({
  visible: false,
  phase: 'idle',
  version: '',
  notes: '',
  required: false,
  downloaded: false,
  percent: 0,
  done: 0,
  total: 0,
  error: '',
})

/** 最近一次检查到的服务端信息 */
let info = null
/** 本次要下载的文件名(纯文件名,原生侧只认它) */
let fileName = ''
let pollTimer = null
let pollResolve = null
let downloading = false
let resumeHooked = false

async function fetchLatest() {
  try {
    const res = await request.get('/public/app/latest')
    return res && typeof res === 'object' ? res : null
  } catch (e) {
    return null
  }
}

/** 从系统设置回来后续上授权/进度:后台下载可能正好下完,回到前台立刻认领 */
function hookResume() {
  if (resumeHooked) return
  resumeHooked = true
  CapApp.addListener('appStateChange', async ({ isActive }) => {
    if (!isActive || !updateState.visible) return
    if (updateState.phase === 'permission') {
      if (await canInstallApk()) installNow()
      return
    }
    // 下载中切走再回来:立即查一次,别等下一次轮询
    if (updateState.phase === 'downloading') tick()
  })
}

/**
 * 检查更新。`interactive = true` 时无论结果都给反馈(设置页手动检查用),静默检查只在新版本时弹窗。
 */
export async function checkAppUpdate({ interactive = false } = {}) {
  // 只有 Android 壳能装 APK;iOS/Web 不打扰
  if (!isAndroidApp()) {
    if (interactive) ElMessage.warning(t('appUpdate.checkFailed'))
    return
  }
  const data = await fetchLatest()
  if (!data) {
    if (interactive) ElMessage.warning(t('appUpdate.checkFailed'))
    return
  }
  const current = await getAppVersion()
  const plan = updatePlan({ current, latest: data.version, minSupported: data.minSupported })
  if (plan.kind === 'none') {
    if (interactive) ElMessage.success(t('appUpdate.latest'))
    return
  }
  if (plan.kind === 'unknown') {
    if (interactive) ElMessage.warning(t('appUpdate.checkFailed'))
    return
  }
  info = data
  fileName = apkFileName(data.version)
  if (!fileName) return
  // 上次的下载还在跑 / 已经下好:接着用,别从头再拉 54MB
  const pending = await queryApkDownload()
  const sameFile = !!(pending && pending.file === fileName)
  const ready = sameFile && pending.status === 'SUCCESSFUL' && Number(pending.size) >= MIN_APK_BYTES
  // 入队/跑着/等网络都算「在飞」:别清它的落盘文件,也别重复入队
  const running = sameFile && ['PENDING', 'RUNNING', 'PAUSED'].includes(pending.status)
  // 其他版本的安装包与残留任务没用了(原生在入队新下载时也会撤掉旧任务);
  // 查询失败(返回 null)时不清,免得把正在写的包当残留删掉
  if (pending && !running) cleanupApks(ready ? fileName : '')
  hookResume()
  Object.assign(updateState, {
    visible: true,
    phase: 'available',
    version: data.version,
    notes: data.notes || '',
    required: plan.required,
    downloaded: ready,
    percent: 0,
    done: 0,
    total: 0,
    error: '',
  })
  if (running) resumeDownload()
}

/** 把一次进度响应落到界面状态上(查询失败不算失败,继续等) */
function applyStatus(st) {
  if (!st) return
  const total = Number(st.total) || 0
  const done = Number(st.bytes) || 0
  if (done > 0) updateState.done = done
  if (total > 0) updateState.total = total
  const pct = downloadPercent(done, total)
  if (pct !== null) updateState.percent = pct
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  pollResolve = null
}

/** 查一次进度;读到终态(成功/失败/任务没了)就结束轮询并把结果交回去 */
async function tick() {
  const st = await queryApkDownload()
  if (!st) return
  applyStatus(st)
  if (st.status === 'SUCCESSFUL' || st.status === 'FAILED' || st.status === 'NONE') {
    const resolve = pollResolve
    stopPolling()
    if (resolve) resolve(st)
  }
}

/** 轮询到终态为止;锁屏时定时器会被系统挂起,回前台由 hookResume 补一次 */
function pollDownload() {
  return new Promise((resolve) => {
    pollResolve = resolve
    pollTimer = setInterval(tick, POLL_MS)
    tick()
  })
}

/** 开始应用内下载(系统托管);完成后自动调起安装器 */
export async function startDownload() {
  if (downloading || !info || !fileName) return
  downloading = true
  Object.assign(updateState, {
    phase: 'downloading',
    downloaded: false,
    percent: 0,
    done: 0,
    total: 0,
    error: '',
  })
  try {
    await startApkDownload({ url: serverUrl(info.url), file: fileName })
    const st = await pollDownload()
    if (st.status !== 'SUCCESSFUL') throw new Error(st.status)
    // 系统下载器已按 Content-Length 校验过,这里再按「大小下限」兜一层(防错误页落盘)
    if (Number(st.size) < MIN_APK_BYTES) throw new Error('incomplete-apk')
    updateState.percent = 100
    await installNow()
  } catch (e) {
    console.warn('[appUpdate] 下载安装包失败', e)
    updateState.phase = 'error'
    updateState.error = t('appUpdate.failed')
  } finally {
    downloading = false
    stopPolling()
  }
}

/** 上次的下载还在跑(比如锁屏期间被系统续上):接着看进度,下完直接装 */
async function resumeDownload() {
  if (downloading || !fileName) return
  downloading = true
  Object.assign(updateState, { phase: 'downloading', downloaded: false, error: '' })
  try {
    const st = await pollDownload()
    if (st.status === 'SUCCESSFUL' && Number(st.size) >= MIN_APK_BYTES) {
      updateState.percent = 100
      await installNow()
    } else {
      updateState.phase = 'error'
      updateState.error = t('appUpdate.failed')
    }
  } finally {
    downloading = false
    stopPolling()
  }
}

/** 调起系统安装器(缺授权时切到授权引导) */
export async function installNow() {
  if (!fileName) return
  Object.assign(updateState, { phase: 'installer', error: '' })
  if (!(await canInstallApk())) {
    updateState.phase = 'permission'
    return
  }
  try {
    await installApk(fileName)
  } catch (e) {
    const code = String((e && e.message) || '')
    if (code.includes('install-permission')) {
      updateState.phase = 'permission'
      return
    }
    // 包不见了(系统清理缓存/用户删了):退回「立即更新」重新下,别让用户卡在错误页
    if (code.includes('file-missing')) updateState.downloaded = false
    updateState.phase = 'error'
    updateState.error = code.includes('file-missing') ? t('appUpdate.fileMissing') : t('appUpdate.noInstaller')
  }
}

/** 打开「安装未知应用」系统设置页 */
export async function openPermissionSettings() {
  try {
    await openInstallSettings()
  } catch (e) {
    console.warn('[appUpdate] 打不开安装授权页', e)
  }
}

/** 关掉弹窗;强制更新不给关 */
export function dismissUpdate() {
  if (updateState.required) return
  updateState.visible = false
  updateState.phase = 'idle'
}
