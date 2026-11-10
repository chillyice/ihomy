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
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getWebVersion } from '@/utils/platform'

const { t } = useI18n()

const status = ref({ granted: false, count: 0, last: null })
const busy = ref(false)
/** 客户端版本:先给构建期版本号,进页后再取安装包 versionName 覆盖 */
const appVersion = ref(getWebVersion())
const checking = ref(false)
/** 保险箱指纹门:本机是否具备可用指纹/人脸 + 开关状态 */
const bioAvailable = ref(false)
const vaultLock = ref(false)

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

onMounted(() => {
  load()
  loadBiometric()
  loadVersion()
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
