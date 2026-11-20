<!-- 健康数据导入(设置 → 健康数据 → 导入)。两种「补历史」的做法放在同一张卡里:
     ① 导出包:iPhone「健康」导出的 导出.zip / export.xml 一次性灌进来,多年的历史也能导;
     ② 快捷指令:iOS 专属的自动上报(不装 App 的无奈之法),默认收起,只在 iOS 上出现。
     解析与归一全在浏览器里做(utils/health/appleHealthImport.js),只走既有的上报通道,
     数据落登录者本人,家庭成员照旧看不到。通用关联方式(上报令牌)在上面的「同步」卡。 -->
<template>
  <div ref="rootRef" class="card settings-card health-import" v-loading="phase === 'parse'">
    <div class="section-label">{{ $t('settings.health.importTitle') }}</div>
    <div class="share-tip">{{ $t('settings.health.importHint') }}</div>

    <div class="import-row">
      <el-button type="primary" :disabled="busy" @click="fileInput?.click()">
        <el-icon><UploadFilled /></el-icon>
        <span>{{ $t('settings.health.importPick') }}</span>
      </el-button>
      <span class="import-file">{{ fileName || $t('settings.health.importNoFile') }}</span>
      <input ref="fileInput" type="file" accept=".zip,.xml" hidden @change="onFile" />
    </div>

    <div v-if="phase === 'parse'" class="import-progress">
      <el-progress :percentage="parsePercent" :stroke-width="8" :show-text="false" />
      <span class="import-note">{{ $t('settings.health.importParsing') }}</span>
    </div>

    <template v-if="plan">
      <div class="result-row">
        <el-tag size="small" type="info">{{ $t('settings.health.importRange', { from: plan.from, to: plan.to }) }}</el-tag>
        <el-tag size="small" type="success">{{ $t('settings.health.importSamples', { count: plan.sampleRows }) }}</el-tag>
        <el-tag v-if="plan.sleepRows" size="small" type="success">{{
          $t('settings.health.importSleeps', { count: plan.sleepRows })
        }}</el-tag>
        <el-tag v-if="plan.workoutRows" size="small" type="success">{{
          $t('settings.health.importWorkouts', { count: plan.workoutRows })
        }}</el-tag>
      </div>
      <div class="share-tip">{{ $t('settings.health.importPlanHint') }}</div>
    </template>

    <div v-if="phase === 'send'" class="import-progress">
      <el-progress :percentage="sendPercent" :stroke-width="8" :show-text="false" />
      <span class="import-note">{{ $t('settings.health.importSending', { done: sent, total }) }}</span>
    </div>

    <div v-if="result" class="result-row">
      <span class="result-label">{{ $t('settings.health.importResult') }}</span>
      <el-tag size="small" type="success">{{ $t('settings.health.accepted') }} {{ result.accepted }}</el-tag>
      <el-tag size="small" type="info">{{ $t('settings.health.duplicated') }} {{ result.duplicated }}</el-tag>
      <el-tag size="small" type="warning">{{ $t('settings.health.rejected') }} {{ result.rejected }}</el-tag>
      <el-tag size="small" type="info">{{ $t('settings.health.skipped') }} {{ result.skipped }}</el-tag>
      <el-tag v-if="result.failed" size="small" type="danger">{{ $t('settings.health.importFailed', { count: result.failed }) }}</el-tag>
    </div>
    <div v-if="result && result.message" class="share-tip reject-tip">{{ result.message }}</div>

    <div class="import-row">
      <el-button v-if="plan && !result" type="primary" :loading="phase === 'send'" @click="startImport">{{
        $t('settings.health.importStart')
      }}</el-button>
      <el-button v-if="plan && !result" :disabled="phase === 'send'" @click="reset">{{ $t('common.cancel') }}</el-button>
      <router-link v-if="result" to="/health">
        <el-button type="primary">{{ $t('settings.health.importToReport') }}</el-button>
      </router-link>
    </div>

    <div class="share-tip retention-tip">{{ $t('settings.health.importRetention') }}</div>

    <!-- 第二种办法:快捷指令自动上报(仅 iOS;默认收起,桌面看不到) -->
    <template v-if="showShortcut">
      <div class="import-divider" />
      <button type="button" class="method-toggle" @click="shortcutOpen = !shortcutOpen">
        <span class="row-title">{{ $t('settings.health.importShortcutTitle') }}</span>
        <el-icon class="toggle-icon" :class="{ open: shortcutOpen }"><ArrowDown /></el-icon>
      </button>
      <div class="share-tip">
        {{ $t('settings.health.importShortcutHint') }}
        <router-link class="tpl-more" to="/help#health">{{ $t('settings.health.tplMore') }}</router-link>
      </div>

      <div v-show="shortcutOpen" class="method-body">
        <ol class="tpl-steps">
          <li>{{ $t('settings.health.tplStep1') }}</li>
          <li>{{ $t('settings.health.tplStep2') }}</li>
          <li>{{ $t('settings.health.tplStep3') }}</li>
          <li>{{ $t('settings.health.tplStep4') }}</li>
        </ol>

        <div class="tpl-body">
          <div class="tpl-body-head">
            <span class="tpl-body-label">{{ $t('settings.health.tplBodyLabel') }}</span>
            <el-button size="small" type="primary" plain @click="copyText(TPL_BODY)">{{ $t('settings.health.copy') }}</el-button>
          </div>
          <pre class="tpl-code">{{ TPL_BODY }}</pre>
          <div class="share-tip">{{ $t('settings.health.tplBodyNote') }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown, UploadFilled } from '@element-plus/icons-vue'
import { healthApi } from '@/api'
import { copyText as copyToClipboard } from '@/utils/clipboard'
import { HEALTH_ENTRY, healthEntryKind } from '@/utils/healthPlatform'
import { isNativeApp, nativePlatform } from '@/utils/platform'
import { SHORTCUT_BODY } from '@/utils/healthShortcut'
import {
  IMPORT_PROVIDER,
  buildImportPlan,
  createCollector,
  feedChunk,
  sleepPayload,
  streamExportText,
} from '@/utils/health/appleHealthImport'

const { t } = useI18n()

// 快捷指令只有 iPhone 用得上;iPad/桌面不显示,免得整段步骤白占屏
const entryKind = healthEntryKind({
  isNative: isNativeApp(),
  nativePlatform: nativePlatform(),
  ua: typeof navigator === 'undefined' ? '' : navigator.userAgent,
  maxTouchPoints: typeof navigator === 'undefined' ? 0 : navigator.maxTouchPoints,
})
const showShortcut = computed(() => entryKind === HEALTH_ENTRY.IOS)

// 请求体示例与帮助页共用(utils/healthShortcut.js),这里有复制按钮直接取用
const TPL_BODY = SHORTCUT_BODY

// zip 解不开时给的具体说法(不同浏览器/压缩方式分开讲,别只丢一句「失败」)
const ZIP_HINTS = {
  ZIP_NO_EOCD: 'importErrZip',
  ZIP_NO_XML: 'importErrZipXml',
  ZIP_UNSUPPORTED: 'importErrZipType',
  ZIP_NO_INFLATE: 'importErrZipBrowser',
}

const rootRef = ref(null)
const fileInput = ref(null)
const fileName = ref('')
const phase = ref('idle') // idle | parse | plan | send
const parsePercent = ref(0)
const sendPercent = ref(0)
const sent = ref(0)
const total = ref(0)
const plan = ref(null)
const result = ref(null)
const shortcutOpen = ref(false)

const busy = computed(() => phase.value === 'parse' || phase.value === 'send')

/** 本机时区偏移 +08:00:导出包里的时间自带偏移,这里只是兜底(存疑时间用它) */
const tzOffset = () => {
  const min = -new Date().getTimezoneOffset()
  const abs = Math.abs(min)
  const p = (n) => String(n).padStart(2, '0')
  return `${min < 0 ? '-' : '+'}${p(Math.floor(abs / 60))}:${p(abs % 60)}`
}

const errorText = (err) => {
  const key = ZIP_HINTS[err?.message]
  return key ? t(`settings.health.${key}`) : t('settings.health.importErrGeneric', { message: err?.message || '' })
}

/** 复制统一入口:成功后提示;共用 utils/clipboard(内嵌壳/局域网 http 下 clipboard 不可用) */
const copyText = async (text) => {
  const ok = await copyToClipboard(text)
  if (ok) ElMessage.success(t('settings.health.copied'))
  else ElMessage.warning(t('settings.health.copyFailed'))
}

const reset = () => {
  phase.value = 'idle'
  plan.value = null
  result.value = null
  fileName.value = ''
  parsePercent.value = 0
  sendPercent.value = 0
  sent.value = 0
  total.value = 0
}

/** 选文件后先只解析,把「要导多少」摆给用户看,再让他点开始 */
const onFile = async (e) => {
  const file = e.target?.files?.[0]
  // 清掉 value:同一份文件改完再选一次也能触发 change
  if (e.target) e.target.value = ''
  if (!file) return
  reset()
  fileName.value = file.name
  phase.value = 'parse'
  try {
    const collector = createCollector()
    const stream = streamExportText(file, {
      onProgress: (p) => {
        parsePercent.value = Math.min(99, Math.round(p * 100))
      },
    })
    for await (const text of stream) feedChunk(collector, text)
    parsePercent.value = 100
    const built = buildImportPlan(collector)
    if (!built) {
      ElMessage.error(t('settings.health.importEmpty'))
      reset()
      return
    }
    plan.value = built
    phase.value = 'plan'
  } catch (err) {
    ElMessage.error(errorText(err))
    reset()
  }
}

const startImport = async () => {
  if (!plan.value) return
  const acc = { accepted: 0, duplicated: 0, rejected: 0, skipped: 0, failed: 0, message: '' }
  const post = async (body) => {
    const data = await healthApi.ingest({ provider: IMPORT_PROVIDER, timezone: tzOffset(), ...body })
    acc.accepted += data?.accepted || 0
    acc.duplicated += data?.duplicated || 0
    acc.rejected += data?.rejected || 0
    acc.skipped += data?.skipped || 0
    sent.value += 1
    sendPercent.value = Math.round((sent.value / total.value) * 100)
  }

  phase.value = 'send'
  sent.value = 0
  total.value = plan.value.requests
  sendPercent.value = 0
  try {
    for (const samples of plan.value.sampleBatches) await post({ samples })
    for (const batch of plan.value.sleepBatches) await post({ sleep: sleepPayload(batch) })
    for (const workouts of plan.value.workoutBatches) await post({ workouts })
    if (!acc.failed) acc.message = ''
    ElMessage.success(t('settings.health.importCompleted'))
  } catch (err) {
    // 中途断了也算一次结果:已受理的批次不会回滚,重跑同一份文件只会覆盖
    acc.failed = Math.max(1, total.value - sent.value)
    acc.message = err?.message || ''
    ElMessage.error(t('settings.health.importInterrupted', { message: acc.message || t('common.failed') }))
  } finally {
    result.value = acc
    phase.value = 'idle'
  }
}

/** 「连接 Apple 健康」那边的主按钮:滚到这张卡并把快捷指令那段展开 */
const openShortcut = () => {
  if (!showShortcut.value) return
  shortcutOpen.value = true
  rootRef.value?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

defineExpose({ openShortcut })
</script>

<style scoped>
.health-import {
  margin-top: 16px;
}
.import-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}
.import-file {
  font-size: 12.5px;
  color: var(--color-text-secondary);
  word-break: break-all;
}
.import-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0 12px;
}
.import-progress :deep(.el-progress) {
  flex: 1;
}
.import-note {
  flex-shrink: 0;
  font-size: 12.5px;
  color: var(--color-text-secondary);
}
.retention-tip {
  margin-top: 6px;
}
.import-divider {
  margin: 16px 0 12px;
  border-top: 1px solid var(--color-border);
}
.method-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  cursor: pointer;
}
.toggle-icon {
  font-size: 12px;
  color: var(--color-text-secondary);
  transition: transform 0.2s;
}
.toggle-icon.open {
  transform: rotate(180deg);
}
.method-body {
  margin-top: 6px;
}
.tpl-more {
  margin-left: 6px;
  color: var(--color-primary);
  white-space: nowrap;
}
.tpl-steps {
  margin: 10px 0 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--color-text-primary);
}
.tpl-body {
  margin-top: 12px;
}
.tpl-body-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}
.tpl-body-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.tpl-code {
  margin: 0 0 6px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-2);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre;
  overflow-x: auto;
}
@media (max-width: 640px) {
  .import-row {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
