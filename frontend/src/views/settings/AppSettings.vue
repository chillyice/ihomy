<!-- 安卓客户端设置(仅壳内可见):系统日历单向导出做提醒兜底。文案只讲用户能做什么。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('appSettings.title') }}</div>

    <p class="share-tip app-lead">{{ $t('appSettings.lead') }}</p>

    <el-form label-position="top" class="settings-form">
      <el-form-item :label="$t('appSettings.aboutTitle')">
        <div class="ver-now">{{ $t('appSettings.currentVersion', { version: appVersion }) }}</div>
        <div class="share-tip">{{ $t('appSettings.updateDesc') }}</div>
        <div class="cal-actions">
          <el-button type="primary" :loading="checking" @click="onCheckUpdate">
            {{ $t('appUpdate.check') }}
          </el-button>
        </div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.calendarTitle')">
        <div class="cal-head">
          <el-tag size="small" :type="status.granted ? 'success' : 'info'">
            {{ status.granted ? $t('appSettings.calendarGranted') : $t('appSettings.calendarNotGranted') }}
          </el-tag>
          <span class="share-tip">{{ $t('appSettings.calendarCount', { n: status.count }) }}</span>
        </div>

        <div class="share-tip">{{ $t('appSettings.calendarDesc') }}</div>

        <ul class="cal-points">
          <li v-for="(x, i) in $tm('appSettings.calendarPoints')" :key="i">{{ x }}</li>
        </ul>

        <div class="cal-last">
          {{ $t('appSettings.calendarLast') }}: <b>{{ lastText }}</b>
        </div>

        <div class="cal-actions">
          <el-button type="primary" :loading="busy" @click="onExport">
            {{ $t('appSettings.calendarExport') }}
          </el-button>
          <el-button :disabled="busy || !status.count" @click="onClear">
            {{ $t('appSettings.calendarClear') }}
          </el-button>
          <el-button link :disabled="busy" @click="load">{{ $t('appSettings.refresh') }}</el-button>
        </div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.vaultLockTitle')">
        <div class="cal-head">
          <el-switch v-model="vaultLock" :disabled="!bioAvailable" @change="onVaultLockChange" />
          <el-tag size="small" :type="bioAvailable ? 'success' : 'info'">
            {{ bioAvailable ? $t('appSettings.vaultLockOn') : $t('appSettings.vaultLockNoHardware') }}
          </el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.vaultLockDesc') }}</div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.appLockTitle')">
        <div class="cal-head">
          <el-switch v-model="appLockOn" :disabled="!bioAvailable" @change="onAppLockChange" />
          <el-tag size="small" :type="bioAvailable ? 'success' : 'info'">
            {{ bioAvailable ? $t('appSettings.vaultLockOn') : $t('appSettings.vaultLockNoHardware') }}
          </el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.appLockDesc') }}</div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.hapticsTitle')">
        <div class="cal-head">
          <el-switch v-model="hapticsOn" @change="onHapticsChange" />
          <el-tag size="small" :type="hapticsOn ? 'success' : 'info'">
            {{ hapticsOn ? $t('appSettings.hapticsOn') : $t('appSettings.hapticsOff') }}
          </el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.hapticsDesc') }}</div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.kioskTitle')">
        <div class="share-tip">{{ $t('appSettings.kioskDesc') }}</div>
        <div class="cal-actions">
          <el-button @click="router.push('/kiosk')">{{ $t('appSettings.kioskOpen') }}</el-button>
        </div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.unreadNotifyTitle')">
        <div class="cal-head">
          <el-switch v-model="unreadNotify" @change="onUnreadNotifyChange" />
          <el-tag size="small" type="info">{{ $t('appSettings.unreadNotifyEvery') }}</el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.unreadNotifyDesc') }}</div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.dailyWeatherTitle')">
        <div class="cal-head">
          <el-switch v-model="dailyWeather" @change="onDailyWeatherChange" />
          <el-time-select
            v-model="dailyWeatherTime"
            start="00:00"
            step="00:30"
            end="23:30"
            :disabled="!dailyWeather"
            :placeholder="$t('appSettings.dailyWeatherTimePlaceholder')"
            @change="onDailyWeatherTimeChange"
          />
          <el-button link :disabled="!dailyWeather" @click="onWeatherPreview">
            {{ $t('appSettings.dailyWeatherPreview') }}
          </el-button>
        </div>
        <div class="share-tip">{{ $t('appSettings.dailyWeatherDesc') }}</div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.notifySettingsTitle')">
        <div class="cal-head">
          <el-tag size="small" type="info">{{ $t('appSettings.notifySettingsChannels') }}</el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.notifySettingsDesc') }}</div>
        <div class="cal-actions">
          <el-button @click="onOpenNotificationSettings">{{ $t('appSettings.notifySettingsOpen') }}</el-button>
        </div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.autoUpdateTitle')">
        <div class="cal-head">
          <el-switch v-model="autoUpdate" @change="onAutoUpdateChange" />
          <el-tag v-if="autoUpdateDeviceOwner" size="small" type="success">
            {{ $t('appSettings.autoUpdateSilent') }}
          </el-tag>
          <el-tag v-else size="small" type="info">{{ $t('appSettings.autoUpdateNeedConfirm') }}</el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.autoUpdateDesc') }}</div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.photoBackupTitle')">
        <div class="cal-head">
          <el-switch v-model="backupOn" :disabled="backupBusy" @change="onBackupToggle" />
          <el-tag size="small" :type="backupOn ? 'success' : 'info'">
            {{ backupOn ? $t('appSettings.photoBackupOn') : $t('appSettings.photoBackupOff') }}
          </el-tag>
          <el-tag size="small" type="info">{{ $t('appSettings.photoBackupPending', { n: backupPending }) }}</el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.photoBackupDesc') }}</div>
        <div class="share-tip">{{ $t('appSettings.photoBackupPartialHint') }}</div>

        <div class="cal-head bk-switches">
          <span class="share-tip bk-switch">
            <el-switch v-model="backupWifi" size="small" @change="onBackupWifiChange" />
            {{ $t('appSettings.photoBackupWifi') }}
          </span>
          <span class="share-tip bk-switch">
            <el-switch v-model="backupCharge" size="small" @change="onBackupChargeChange" />
            {{ $t('appSettings.photoBackupCharge') }}
          </span>
        </div>
        <div class="share-tip">{{ $t('appSettings.photoBackupSwitchHint') }}</div>

        <div class="cal-last">
          {{ $t('appSettings.photoBackupLast') }}: <b>{{ backupLastText }}</b>
        </div>
        <div v-if="pbState.running" class="cal-last">
          {{ $t('appSettings.photoBackupRunning', { done: pbState.scanned }) }}
        </div>

        <div class="cal-actions">
          <el-button type="primary" :loading="pbState.running" @click="onBackupNow">
            {{ $t('appSettings.photoBackupNow') }}
          </el-button>
          <el-button :disabled="pbState.running || backupBusy" @click="onBackupFull">
            {{ $t('appSettings.photoBackupFull') }}
          </el-button>
          <el-button v-if="pbState.running" @click="onBackupStop">
            {{ $t('appSettings.photoBackupStop') }}
          </el-button>
          <el-button link :disabled="pbState.running" @click="loadPhotoBackup">{{ $t('appSettings.refresh') }}</el-button>
        </div>
      </el-form-item>

      <el-form-item :label="$t('appSettings.diagnosticsTitle')">
        <div class="cal-head">
          <el-tag size="small" :type="diag.crashes ? 'warning' : 'info'">
            {{ $t('appSettings.diagnosticsCrashes', { n: diag.crashes }) }}
          </el-tag>
          <el-tag size="small" :type="diag.errors ? 'warning' : 'info'">
            {{ $t('appSettings.diagnosticsErrors', { n: diag.errors }) }}
          </el-tag>
        </div>
        <div class="share-tip">{{ $t('appSettings.diagnosticsDesc') }}</div>
        <div class="cal-actions">
          <el-button
            type="primary"
            :loading="diagBusy"
            :disabled="diagBusy || (!diag.crashes && !diag.errors)"
            @click="onExportDiagnostics"
          >
            {{ $t('appSettings.diagnosticsExport') }}
          </el-button>
          <el-button :disabled="diagBusy || (!diag.crashes && !diag.errors)" @click="onClearDiagnostics">
            {{ $t('appSettings.diagnosticsClear') }}
          </el-button>
          <el-button link :disabled="diagBusy" @click="loadDiagnostics">{{ $t('appSettings.refresh') }}</el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWebVersion } from '@/utils/platform'
import { haptic, hapticsEnabled, setHapticsEnabled } from '@/utils/haptics'

const { t } = useI18n()
const router = useRouter()

const status = ref({ granted: false, count: 0, last: null })
const busy = ref(false)
/** 客户端版本:先给构建期版本号,进页后再取安装包 versionName 覆盖 */
const appVersion = ref(getWebVersion())
const checking = ref(false)
/** 保险箱指纹门:本机是否具备可用指纹/人脸 + 开关状态 */
const bioAvailable = ref(false)
const vaultLock = ref(false)
/** 诊断信息:崩溃份数 + 前端错误条数 */
const diag = ref({ crashes: 0, errors: 0 })
const diagBusy = ref(false)
/** 后台未读提醒(默认开,事实源在原生 WidgetPrefs) */
const unreadNotify = ref(true)
/** 每日天气(默认开):开关与时刻都存在原生,时刻按 HH:mm 展示 */
const dailyWeather = ref(true)
const dailyWeatherTime = ref('07:30')
/** 应用锁(默认关;本机无生物识别时禁用) */
const appLockOn = ref(false)
/** 触觉反馈(默认开):只影响语义动作的轻震,跟随系统触感开关 */
const hapticsOn = ref(hapticsEnabled())

// 相册自动备份:开关与进度都在 native/photoBackup 里,这里只做界面与回填
const backupOn = ref(false)
const backupWifi = ref(true)
const backupCharge = ref(false)
const backupPending = ref(0)
const backupBusy = ref(false)
const backupLast = ref({ at: 0, created: 0 })
const pbState = ref({ running: false, scanned: 0, created: 0, skipped: 0, failed: 0, phase: 'idle' })

const backupModule = () => import('@/native/photoBackup')

const backupLastText = computed(() => {
  const last = backupLast.value || pbState.value
  const at = last && last.at ? last.at : 0
  if (!at) return t('appSettings.photoBackupNever')
  const d = new Date(at)
  const pad = (n) => String(n).padStart(2, '0')
  const time = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  return t('appSettings.photoBackupLastValue', { time, n: last.created || 0 })
})

// 原生模块动态引入:Capacitor 运行时只在壳内用到,不进首屏包
const native = () => import('@/native/calendarExport')

const lastText = computed(() => {
  const last = status.value.last
  if (!last) return t('appSettings.calendarNever')
  const d = new Date(last.at)
  const pad = (n) => String(n).padStart(2, '0')
  const time = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  return t('appSettings.calendarLastValue', { time, n: last.inserted || 0 })
})

async function load() {
  try {
    const m = await native()
    status.value = await m.calendarStatus()
  } catch (e) {
    console.warn('[appSettings] 读取日历状态失败', e)
  }
}

async function onExport() {
  busy.value = true
  try {
    const m = await native()
    const out = await m.exportToCalendar()
    ElMessage.success(t('appSettings.exportOk', { n: out.inserted || 0 }))
    if (out.truncated) ElMessage.warning(t('appSettings.exportTruncated'))
    await load()
  } catch (e) {
    if (String(e && e.message) === 'permission') {
      ElMessageBox.alert(t('appSettings.permissionGuide'), t('appSettings.permissionTitle'), { type: 'warning' })
    } else if (String(e && e.message) === 'guest') {
      ElMessage.warning(t('appSettings.guestOnly'))
    } else {
      console.warn('[appSettings] 导出日历失败', e)
      ElMessage.error(t('appSettings.fail'))
    }
  } finally {
    busy.value = false
  }
}

async function onClear() {
  busy.value = true
  try {
    const m = await native()
    const out = await m.clearCalendar()
    ElMessage.success(t('appSettings.clearOk', { n: out.removed || 0 }))
    await load()
  } catch (e) {
    if (String(e && e.message) === 'permission') {
      ElMessageBox.alert(t('appSettings.permissionGuide'), t('appSettings.permissionTitle'), { type: 'warning' })
    } else {
      console.warn('[appSettings] 清空日历失败', e)
      ElMessage.error(t('appSettings.fail'))
    }
  } finally {
    busy.value = false
  }
}

// 指纹门开关:读写都在 native/biometric 里,这里只做界面与状态回填
const bio = () => import('@/native/biometric')

async function loadBiometric() {
  try {
    const m = await bio()
    const st = await m.biometricStatus()
    bioAvailable.value = !!(st && st.available)
    vaultLock.value = bioAvailable.value && m.vaultLockEnabled()
    // 本机指纹被移除/关闭(录入被删)时顺手关掉开关,否则下次进保险箱会被挡在锁屏且验不过
    if (!bioAvailable.value && m.vaultLockEnabled()) m.setVaultLockEnabled(false)
  } catch (e) {
    bioAvailable.value = false
    vaultLock.value = false
  }
}

function onVaultLockChange(v) {
  bio().then((m) => m.setVaultLockEnabled(!!v))
}

// 应用锁:开关存 localStorage;本机无可用指纹/被移除时自动关闭,避免永久锁死
const appLockModule = () => import('@/native/appLock')

async function loadAppLock() {
  try {
    const m = await appLockModule()
    appLockOn.value = bioAvailable.value && m.appLockEnabled()
    if (!bioAvailable.value && m.appLockEnabled()) m.setAppLockEnabled(false)
  } catch (e) {
    appLockOn.value = false
  }
}

function onAppLockChange(v) {
  appLockModule().then((m) => m.setAppLockEnabled(!!v))
}

// 触觉反馈:开关只存本机;打开时立刻震一下,让用户知道手感是什么
function onHapticsChange(v) {
  setHapticsEnabled(!!v)
  if (v) haptic('tap')
}

// 后台未读提醒:开关状态存在原生(Worker 直接读),这里只做界面回填
const widgetNative = () => import('@/native/widgets')

/** 闲时自动更新(默认开):排期与去重都在原生;设备所有者才真静默,其余人下好给一条通知 */
const autoUpdate = ref(true)
const autoUpdateDeviceOwner = ref(false)
const updaterNative = () => import('@/native/updater')

async function loadAutoUpdate() {
  try {
    const m = await updaterNative()
    const st = await m.getAutoUpdate()
    autoUpdate.value = !!st.enabled
    autoUpdateDeviceOwner.value = !!st.deviceOwner
  } catch (e) {
    autoUpdate.value = true
  }
}

function onAutoUpdateChange(v) {
  updaterNative().then((m) => m.setAutoUpdate(!!v))
}

async function loadUnreadNotify() {
  try {
    const m = await widgetNative()
    unreadNotify.value = await m.getUnreadNotifyEnabled()
  } catch (e) {
    unreadNotify.value = true
  }
}

function onUnreadNotifyChange(v) {
  widgetNative().then((m) => m.setUnreadNotifyEnabled(!!v))
}

const DEFAULT_WEATHER_MINUTE = 7 * 60 + 30

/** 原生存分钟数(0–1439),界面用 HH:mm;非法值回落到 07:30 */
function minuteToTime(minute) {
  const m = Number.isFinite(minute) && minute >= 0 && minute < 1440 ? minute : DEFAULT_WEATHER_MINUTE
  const hh = String(Math.floor(m / 60)).padStart(2, '0')
  const mm = String(m % 60).padStart(2, '0')
  return `${hh}:${mm}`
}

function timeToMinute(time) {
  const [hh, mm] = String(time || '')
    .split(':')
    .map(Number)
  if (!Number.isFinite(hh) || !Number.isFinite(mm)) return DEFAULT_WEATHER_MINUTE
  return hh * 60 + mm
}

async function loadDailyWeather() {
  try {
    const m = await widgetNative()
    const st = await m.getDailyWeather()
    dailyWeather.value = !!st.enabled
    dailyWeatherTime.value = minuteToTime(st.minute)
  } catch (e) {
    dailyWeather.value = true
    dailyWeatherTime.value = minuteToTime(DEFAULT_WEATHER_MINUTE)
  }
}

async function onDailyWeatherChange(v) {
  const m = await widgetNative()
  await m.setDailyWeatherEnabled(!!v)
  if (!v) return
  // 打开开关顺手要一次通知权限:否则第二天早上什么都不会出现,用户会以为坏了
  try {
    const { LocalNotifications } = await import('@capacitor/local-notifications')
    const perm = await LocalNotifications.checkPermissions()
    if (perm.display !== 'granted') {
      const req = await LocalNotifications.requestPermissions()
      if (req.display !== 'granted') ElMessage.warning(t('appSettings.dailyWeatherPreviewDenied'))
    }
  } catch (e) {
    // 权限询问失败不改变开关本身,系统设置里仍可手动补
    console.warn('[appSettings] 通知权限询问失败', e)
  }
}

function onDailyWeatherTimeChange(v) {
  if (!v) return
  widgetNative().then((m) => m.setDailyWeatherMinute(timeToMinute(v)))
}

// 系统通知设置:直达本 App 的通知页,4 条通道的开关交给系统管理(不重建通道)
async function onOpenNotificationSettings() {
  try {
    const m = await widgetNative()
    await m.openNotificationSettings()
  } catch (e) {
    console.warn('[appSettings] 打开系统通知设置失败', e)
    ElMessage.error(t('appSettings.fail'))
  }
}

async function onWeatherPreview() {
  try {
    const m = await widgetNative()
    await m.previewDailyWeather()
    ElMessage.success(t('appSettings.dailyWeatherPreviewSent'))
  } catch (e) {
    const msg = String(e && e.message)
    if (msg.includes('permission')) ElMessage.warning(t('appSettings.dailyWeatherPreviewDenied'))
    else if (msg.includes('network')) ElMessage.warning(t('appSettings.dailyWeatherPreviewOffline'))
    else ElMessage.error(t('appSettings.fail'))
  }
}

// 相册自动备份:所有状态都在原生模块里,这里只回填与触发
async function loadPhotoBackup() {
  try {
    const m = await backupModule()
    pbState.value = m.photoBackupState
    backupOn.value = m.photoBackupEnabled()
    backupWifi.value = m.wifiOnlyEnabled()
    backupCharge.value = m.chargeOnlyEnabled()
    backupLast.value = m.photoBackupLast()
    backupPending.value = await m.photoBackupPending()
  } catch (e) {
    console.warn('[appSettings] 读取备份状态失败', e)
  }
}

function onBackupToggle(v) {
  backupModule().then((m) => {
    m.setPhotoBackupEnabled(!!v)
    backupOn.value = !!v
    if (v) {
      // 开启当场会尝试一次:成功后把待备份数刷掉
      setTimeout(loadPhotoBackup, 1200)
    }
  })
}

function onBackupWifiChange(v) {
  backupModule().then((m) => m.setWifiOnly(!!v))
}

function onBackupChargeChange(v) {
  backupModule().then((m) => m.setChargeOnly(!!v))
}

function backupResultMessage(out) {
  const o = out || {}
  if (o.reason === 'permission')
    ElMessageBox.alert(t('appSettings.photoBackupPermission'), t('appSettings.photoBackupTitle'), { type: 'warning' })
  else if (o.reason === 'network') ElMessage.warning(t('appSettings.photoBackupNoNetwork'))
  else if (o.reason === 'charging') ElMessage.warning(t('appSettings.photoBackupNoCharge'))
  else if (o.reason === 'error') ElMessage.error(t('appSettings.photoBackupFailed', { created: pbState.value.created || 0 }))
  else if (o.reason) return
  else if (pbState.value.phase === 'stopped') ElMessage.info(t('appSettings.photoBackupStopped', { created: o.created || 0 }))
  else ElMessage.success(t('appSettings.photoBackupDone', { created: o.created || 0, skipped: o.skipped || 0 }))
}

async function onBackupNow() {
  backupBusy.value = true
  try {
    const m = await backupModule()
    const out = await m.runPhotoBackup()
    backupResultMessage(out)
  } catch (e) {
    console.warn('[appSettings] 备份失败', e)
    ElMessage.error(t('appSettings.photoBackupFailed', { created: 0 }))
  } finally {
    backupBusy.value = false
    await loadPhotoBackup()
  }
}

async function onBackupFull() {
  try {
    await ElMessageBox.confirm(t('appSettings.photoBackupFullConfirm'), t('appSettings.photoBackupTitle'), {
      type: 'warning',
      confirmButtonText: t('appSettings.photoBackupFull'),
      cancelButtonText: t('common.cancel'),
    })
  } catch (e) {
    return
  }
  backupBusy.value = true
  try {
    const m = await backupModule()
    const out = await m.runPhotoBackup({ full: true })
    backupResultMessage(out)
  } catch (e) {
    console.warn('[appSettings] 全量备份失败', e)
    ElMessage.error(t('appSettings.photoBackupFailed', { created: 0 }))
  } finally {
    backupBusy.value = false
    await loadPhotoBackup()
  }
}

function onBackupStop() {
  backupModule().then((m) => m.stopPhotoBackup())
}

// 诊断信息:只读摘要;导出走系统分享,清空会删掉原生里的崩溃与错误文件
const diagModule = () => import('@/native/diagnostics')

async function loadDiagnostics() {
  try {
    const m = await diagModule()
    diag.value = await m.diagnosticsSummary()
  } catch (e) {
    diag.value = { crashes: 0, errors: 0 }
  }
}

async function onExportDiagnostics() {
  diagBusy.value = true
  try {
    const m = await diagModule()
    await m.exportDiagnostics()
  } catch (e) {
    console.warn('[appSettings] 导出诊断信息失败', e)
    ElMessage.error(t('appSettings.diagnosticsFail'))
  } finally {
    diagBusy.value = false
    loadDiagnostics()
  }
}

async function onClearDiagnostics() {
  try {
    await ElMessageBox.confirm(t('appSettings.diagnosticsClearConfirm'), t('appSettings.diagnosticsTitle'), {
      type: 'warning',
      confirmButtonText: t('appSettings.diagnosticsClear'),
      cancelButtonText: t('common.cancel'),
    })
  } catch (e) {
    return
  }
  diagBusy.value = true
  try {
    const m = await diagModule()
    const removed = await m.clearDiagnostics()
    ElMessage.success(t('appSettings.diagnosticsClearOk', { n: removed }))
  } catch (e) {
    console.warn('[appSettings] 清空诊断信息失败', e)
    ElMessage.error(t('appSettings.diagnosticsFail'))
  } finally {
    diagBusy.value = false
    loadDiagnostics()
  }
}

/** 版本号以安装包为准(与更新检查同一来源),取不到就保留构建期版本 */
async function loadVersion() {
  try {
    const m = await import('@/native/version')
    appVersion.value = await m.getAppVersion()
  } catch (e) {
    /* 保留构建期版本号 */
  }
}

/** 手动检查更新:有新版本会弹出更新弹窗,已是最新/失败给一条轻提示 */
async function onCheckUpdate() {
  checking.value = true
  try {
    const m = await import('@/native/appUpdate')
    await m.checkAppUpdate({ interactive: true })
    await loadVersion()
  } catch (e) {
    console.warn('[appSettings] 检查更新失败', e)
    ElMessage.warning(t('appUpdate.checkFailed'))
  } finally {
    checking.value = false
  }
}

onMounted(async () => {
  load()
  loadVersion()
  loadDiagnostics()
  loadUnreadNotify()
  loadDailyWeather()
  loadAutoUpdate()
  loadPhotoBackup()
  await loadBiometric()
  await loadAppLock()
})
</script>

<style scoped>
.share-tip {
  color: #776e62;
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
}
html.dark .share-tip {
  color: #9a9088;
}
.app-lead {
  margin-bottom: 12px;
}
.ver-now {
  font-size: 13.5px;
  font-weight: 600;
}
.cal-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.bk-switches {
  margin-top: 10px;
  gap: 18px;
}
.bk-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 0;
}
.cal-points {
  margin: 8px 0 0;
  padding-left: 1.2em;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--color-text-secondary, #8a8a8a);
}
.cal-last {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--color-text-secondary, #8a8a8a);
}
.cal-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}
</style>
