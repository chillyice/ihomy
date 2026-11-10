// App 内更新(仅原生壳):检查 → 应用内下载 → 交给系统安装器,全程不离开 ihomy。
//
// 口径:
// - 启动时静默检查一次(见 native/index.js),设置页可手动查;
// - 有新版本弹 AppUpdateDialog(组件只负责渲染,状态都在这里);
// - 低于 minSupported = 强制更新,弹窗不给「稍后」;
// - 版本号解析失败一律不提示(宁漏不误),服务端版本比本机低也不提示降级;
// - 下载走 @capacitor/filesystem(带进度事件)落到 cache/updates/,安装走插件 IhomyUpdater;
// - 本机没给「安装未知应用」授权时,引导去系统设置,回来后自动继续安装。
import { reactive } from 'vue'
import { App as CapApp } from '@capacitor/app'
import { Directory, Filesystem } from '@capacitor/filesystem'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import request from '@/api/request'
import { serverUrl } from '@/utils/assetUrl'
import { isAndroidApp } from '@/utils/platform'
import { apkFileName, updatePlan } from '@/utils/appUpdate'
import { getAppVersion } from './version'
import { canInstallApk, cleanupApks, installApk, openInstallSettings } from './updater'

const t = (key, params) => i18n.global.t(key, params)

/** 下载目录(应用缓存下),与原生插件 UpdaterPlugin 的 DIR_NAME 一致 */
const DIR = 'updates'
/** 安装包下限:真包 50MB 上下,低于这个数说明拿到的是错误页/半截文件,不能交给安装器 */
const MIN_APK_BYTES = 1024 * 1024

/**
 * 更新弹窗状态。phase:
 * - idle        没在更新
 * - available   已发现新版本,等用户点「立即更新」
 * - downloading 下载中(带 percent)
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
  percent: 0,
  done: 0,
  total: 0,
  error: '',
})

/** 最近一次检查到的服务端信息 */
let info = null
/** 本次要下载的文件名(纯文件名,原生侧只认它) */
let fileName = ''
let progressHandle = null
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

/** 从系统设置回来后自动续上(只剩授权那一步时) */
function hookResume() {
  if (resumeHooked) return
  resumeHooked = true
  CapApp.addListener('appStateChange', async ({ isActive }) => {
    if (!isActive || !updateState.visible || updateState.phase !== 'permission') return
    if (await canInstallApk()) installNow()
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
  // 顺手清掉更早版本的安装包(缓存里留一个 54MB 的包没必要)
  cleanupApks(fileName)
  hookResume()
  Object.assign(updateState, {
    visible: true,
    phase: 'available',
    version: data.version,
    notes: data.notes || '',
    required: plan.required,
    percent: 0,
    done: 0,
    total: 0,
    error: '',
  })
}

async function ensureDir() {
  try {
    await Filesystem.mkdir({ path: DIR, directory: Directory.Cache, recursive: true })
  } catch (e) {
    // 已存在即目标状态,不当错误
  }
}

/** 开始应用内下载;完成后自动调起安装器 */
export async function startDownload() {
  if (downloading || !info || !fileName) return
  downloading = true
  Object.assign(updateState, { phase: 'downloading', percent: 0, done: 0, total: 0, error: '' })
  try {
    await ensureDir()
    progressHandle = await Filesystem.addListener('progress', (p) => {
      // 同一时刻只允许一个下载,进度事件都归本次(不按 URL 匹配,免得重定向/编码差异漏事件)
      if (!p) return
      const total = Number(p.contentLength) || 0
      const done = Number(p.bytes) || 0
      updateState.done = done
      updateState.total = total
      updateState.percent = total > 0 ? Math.min(99, Math.round((done / total) * 100)) : 0
    })
    await Filesystem.downloadFile({
      url: serverUrl(info.url),
      path: `${DIR}/${fileName}`,
      directory: Directory.Cache,
      progress: true,
    })
    await assertApk()
    updateState.percent = 100
    await installNow()
  } catch (e) {
    console.warn('[appUpdate] 下载安装包失败', e)
    await dropApk()
    updateState.phase = 'error'
    updateState.error = t('appUpdate.failed')
  } finally {
    downloading = false
    if (progressHandle) {
      try {
        await progressHandle.remove()
      } catch (e) {
        /* 已移除 */
      }
      progressHandle = null
    }
  }
}

/** 下载完先看体积:错误页/半截包交给系统安装器只会得到一句「解析包时出现问题」 */
async function assertApk() {
  const st = await Filesystem.stat({ path: `${DIR}/${fileName}`, directory: Directory.Cache })
  if (!st || Number(st.size) < MIN_APK_BYTES) throw new Error('incomplete-apk')
}

/** 删掉不可用的安装包,免得下次「重试」直接装坏文件 */
async function dropApk() {
  if (!fileName) return
  try {
    await Filesystem.deleteFile({ path: `${DIR}/${fileName}`, directory: Directory.Cache })
  } catch (e) {
    /* 本来就没有 */
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
