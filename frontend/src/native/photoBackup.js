// 相册自动备份(仅原生壳):把手机里的新照片逐张传到「手机备份」相册。
//
// 口径:
// - 只在前台跑:App 启动、回到前台、或用户在设置里点「立即备份」时触发;不做后台服务,
//   也不在系统里常驻(计划 §5.5 的 Worker 部分改为前台触发,差异已记进计划文档与当日文档)。
// - 默认只处理近 90 天:首次备份不会把几年前的相册一次全传;「全部备份」需二次确认。
// - 每张先由原生算好内容指纹再上传,服务端按指纹去重,重传同一张只会被跳过。
// - 游标只在**这张确实处理完**之后前进:中途断网/失败就停在这儿,下次从这张继续,不会漏也不会重。
// - 「仅 Wi-Fi」「仅充电」开关的语义:开着=只在满足条件时自动备份,手动点击同样受约束(界面会说明)。
import { Capacitor, registerPlugin } from '@capacitor/core'
import { App } from '@capacitor/app'
import { reactive, watch } from 'vue'
import { isNativeApp } from '@/utils/platform'
import { useUserStore } from '@/stores/user'
import { albumApi, photoApi } from '@/api'
import { fileFromBase64 } from '@/utils/file'
import {
  DEFAULT_DAYS,
  backupBlockedReason,
  generateDeviceId,
  isValidDeviceId,
  makeBackupKey,
  normalizeCursor,
  pendingSinceMs,
  shouldAutoBackup,
} from '@/utils/photoBackupWindow'
import i18n from '@/i18n'

const Plugin = registerPlugin('IhomyPhotoBackup')

const ON_KEY = 'ihomy:photo:backup:on'
const WIFI_KEY = 'ihomy:photo:backup:wifiOnly'
const CHARGE_KEY = 'ihomy:photo:backup:chargeOnly'
const CURSOR_KEY = 'ihomy:photo:backup:cursor'
const LAST_KEY = 'ihomy:photo:backup:last'
const DEVICE_KEY = 'ihomy:photo:backup:device'

/** 首次备份只处理这么多天内的照片(再早的走「全部备份」);口径在 utils/photoBackupWindow */
export { DEFAULT_DAYS }
const PAGE_SIZE = 40

/** 界面状态:运行进度、上次结果都从这里读(自动备份也会更新它) */
export const photoBackupState = reactive({
  running: false,
  scanned: 0,
  created: 0,
  skipped: 0,
  failed: 0,
  total: 0,
  /** idle / running / done / permission / network / charging / offline / stopped / error */
  phase: 'idle',
  lastAt: 0,
  lastCreated: 0,
})

function readFlag(key, dflt) {
  try {
    const v = localStorage.getItem(key)
    return v === null ? dflt : v === '1'
  } catch (e) {
    return dflt
  }
}

function writeFlag(key, on) {
  try {
    if (on) localStorage.setItem(key, '1')
    else localStorage.removeItem(key)
  } catch (e) {
    console.warn('[photoBackup] 保存开关失败', e)
  }
}

export function photoBackupEnabled() {
  return readFlag(ON_KEY, false)
}

export function setPhotoBackupEnabled(on) {
  writeFlag(ON_KEY, on)
  if (on) runPhotoBackup().catch(() => {})
}

/** 仅 Wi-Fi(默认开:家庭流量更常见,不该悄悄吃移动流量) */
export function wifiOnlyEnabled() {
  return readFlag(WIFI_KEY, true)
}

export function setWifiOnly(on) {
  writeFlag(WIFI_KEY, on)
}

/** 仅充电(默认关) */
export function chargeOnlyEnabled() {
  return readFlag(CHARGE_KEY, false)
}

export function setChargeOnly(on) {
  writeFlag(CHARGE_KEY, on)
}

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : fallback
  } catch (e) {
    return fallback
  }
}

function writeJson(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch (e) {
    console.warn('[photoBackup] 保存进度失败', e)
  }
}

/** 本机备份相册的稳定标识:生成一次就固定,服务端靠它找回同一只相册(不靠相册名) */
function readDeviceId() {
  try {
    return localStorage.getItem(DEVICE_KEY) || ''
  } catch (e) {
    return ''
  }
}

function backupKey() {
  let id = readDeviceId()
  if (!isValidDeviceId(id)) {
    id = generateDeviceId()
    try {
      localStorage.setItem(DEVICE_KEY, id)
    } catch (e) {
      /* 存不下就本次会话内用同一个值 */
    }
  }
  return makeBackupKey(id)
}

function readCursor() {
  return normalizeCursor(readJson(CURSOR_KEY, null))
}

function saveCursor(dateAdded, id) {
  writeJson(CURSOR_KEY, { d: dateAdded, i: id })
}

function readLast() {
  return readJson(LAST_KEY, { at: 0, created: 0 })
}

/** 上次备份结果(设置页展示用) */
export function photoBackupLast() {
  return readLast()
}

export async function photoBackupStatus() {
  if (!isNativeApp()) return null
  try {
    return await Plugin.status()
  } catch (e) {
    return null
  }
}

/** 待备份张数(按当前游标或近 90 天口径估算,只用于界面提示) */
export async function photoBackupPending() {
  try {
    const cursor = readCursor()
    const sinceMs = pendingSinceMs(cursor, Date.now())
    const res = await Plugin.count({ sinceMs })
    return res && res.granted ? Number(res.total) || 0 : 0
  } catch (e) {
    return 0
  }
}

let running = null
let stopRequested = false

/** 停止当前备份(已经处理完的照片保留,游标停在完成的位置) */
export function stopPhotoBackup() {
  stopRequested = true
}

/**
 * 跑一次备份。
 * @param {{ full?: boolean }} opts full=true 表示忽略游标与 90 天截断,从头扫一遍(需用户确认)
 */
export function runPhotoBackup(opts = {}) {
  if (running) return running
  running = doRun(opts).finally(() => {
    running = null
  })
  return running
}

async function doRun({ full = false } = {}) {
  const userStore = useUserStore()
  if (!isNativeApp() || !userStore.isLoggedIn) return { reason: 'idle' }

  stopRequested = false
  const st = photoBackupState
  st.running = true
  st.scanned = 0
  st.created = 0
  st.skipped = 0
  st.failed = 0
  st.total = 0
  st.phase = 'running'

  try {
    const device = await photoBackupStatus()
    if (!device) {
      st.phase = 'error'
      return { reason: 'unavailable' }
    }
    if (String(device.permission) !== 'granted') {
      const asked = await Plugin.requestPermission()
      if (!asked || String(asked.state) !== 'granted') {
        st.phase = 'permission'
        return { reason: 'permission' }
      }
    }
    const blocked = backupBlockedReason({
      wifiOnly: wifiOnlyEnabled(),
      wifi: device.wifi,
      chargeOnly: chargeOnlyEnabled(),
      charging: device.charging,
    })
    if (blocked) {
      st.phase = blocked
      return { reason: blocked }
    }

    const album = await albumApi.backup({
      key: backupKey(),
      name: i18n.global.t('appSettings.photoBackupAlbumName'),
    })
    const albumId = album && album.id
    if (!albumId) {
      st.phase = 'error'
      return { reason: 'album' }
    }

    const cursor = full ? null : readCursor()
    const sinceMs = full ? 0 : pendingSinceMs(cursor, Date.now())
    let afterDateAdded = cursor ? cursor.d : 0
    let afterId = cursor ? cursor.i : 0
    let stoppedByFailure = false

    for (let page = 0; page < 500; page++) {
      if (stopRequested) break
      const res = await Plugin.list({
        sinceMs,
        afterDateAdded,
        afterId,
        limit: PAGE_SIZE,
      })
      const items = (res && res.items) || []
      if (!items.length) break

      for (const item of items) {
        if (stopRequested) break
        let prepared = null
        try {
          prepared = await Plugin.prepare({ mediaId: item.mediaId })
          if (!prepared) throw new Error('prepare-empty')
          if (prepared.tooLarge) {
            // 特大原图不传,但游标照走,否则会永远卡在这一张
            st.skipped++
            st.scanned++
            if (!full) saveCursor(item.dateAdded, item.mediaId)
            continue
          }
          const file = await toFile(prepared, item)
          const out = await photoApi.backupUpload(albumId, file, prepared.checksum, prepared.takenAt || item.takenAt || 0)
          if (out && out.skipped) st.skipped++
          else st.created++
          st.scanned++
          if (!full) saveCursor(item.dateAdded, item.mediaId)
        } catch (e) {
          // 单张失败:停下整轮(保留游标,下次从这张继续),避免带着坏网络空转
          console.warn('[photoBackup] 上传失败,本轮停止', e)
          st.failed++
          stoppedByFailure = true
          break
        } finally {
          if (prepared && prepared.path) {
            Plugin.drop({ path: prepared.path }).catch(() => {})
          }
        }
      }

      if (stoppedByFailure) break
      if (!res.hasMore) break
      const last = items[items.length - 1]
      afterDateAdded = last.dateAdded
      afterId = last.mediaId
    }

    if (stopRequested) {
      st.phase = 'stopped'
    } else if (stoppedByFailure) {
      st.phase = 'error'
    } else {
      st.phase = 'done'
      writeJson(LAST_KEY, { at: Date.now(), created: st.created })
      st.lastAt = Date.now()
      st.lastCreated = st.created
    }
    // 缓存里可能还留着未传完的那张,留给下次重试就好;这里只清已处理完的残留
    if (!stoppedByFailure) await Plugin.cleanup().catch(() => {})
    return { created: st.created, skipped: st.skipped, failed: st.failed }
  } catch (e) {
    console.warn('[photoBackup] 备份失败', e)
    st.phase = 'error'
    return { reason: 'error' }
  } finally {
    st.running = false
    if (st.phase === 'done' || st.phase === 'stopped') {
      st.lastAt = st.lastAt || readLast().at || 0
    }
  }
}

/** 原生缓存文件 → File:优先走本地资源地址(流式,不占桥内存),取不到再退到 base64 */
async function toFile(prepared, item) {
  const name = prepared.name || item.name || 'photo.jpg'
  const mime = prepared.mime || item.mime || 'image/jpeg'
  try {
    const url = Capacitor.convertFileSrc(prepared.path)
    const res = await fetch(url)
    if (res.ok) {
      const blob = await res.blob()
      if (blob.size > 0) return new File([blob], name, { type: mime })
    }
  } catch (e) {
    /* 退到 base64 */
  }
  const read = await Plugin.readBase64({ path: prepared.path })
  return fileFromBase64(read && read.data, name, mime)
}

let installed = false
let lastAutoAt = 0

/** App 启动时安装一次:回到前台且满足开关时自动补备份 */
export function installPhotoBackup() {
  if (installed || !isNativeApp()) return
  installed = true
  const userStore = useUserStore()
  const trigger = () => {
    if (
      !shouldAutoBackup({
        enabled: photoBackupEnabled(),
        loggedIn: userStore.isLoggedIn,
        now: Date.now(),
        lastAutoAt,
      })
    )
      return
    lastAutoAt = Date.now()
    runPhotoBackup().catch(() => {})
  }
  App.addListener('appStateChange', ({ isActive }) => {
    if (isActive) trigger()
  }).catch(() => {})
  // 冷启动:登录态可能在桥装好之后才恢复,所以登录变化时也试一次
  watch(
    () => userStore.isLoggedIn,
    (v) => {
      if (v) trigger()
    },
    { immediate: true },
  )
}
