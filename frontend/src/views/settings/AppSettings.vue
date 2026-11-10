<!-- 安卓客户端设置(仅壳内可见):系统日历单向导出做提醒兜底。文案只讲用户能做什么。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('appSettings.title') }}</div>

    <p class="share-tip app-lead">{{ $t('appSettings.lead') }}</p>

    <el-form label-position="top" class="settings-form">
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
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()

const status = ref({ granted: false, count: 0, last: null })
const busy = ref(false)

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

onMounted(load)
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
