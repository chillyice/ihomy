<!-- 健康数据导入 / 导出(设置 → 健康数据)。
     导入:把 iPhone「健康」App 导出的 导出.zip / export.xml 一次性灌进来,多年的历史也能导;
     解析与归一全在浏览器里做(utils/health/appleHealthImport.js),只走既有的上报通道,
     数据落登录者本人,家庭成员照旧看不到。报表页只看报表,入口统一收在这里。 -->
<template>
  <div class="card settings-card health-import" v-loading="phase === 'parse'">
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

    <div class="import-divider" />

    <div class="row-title">{{ $t('settings.health.exportTitle') }}</div>
    <div class="share-tip">{{ $t('settings.health.exportHint') }}</div>
    <div class="import-row">
      <el-button :loading="exporting" @click="exportCsv">
        <el-icon><Download /></el-icon>
        <span>{{ $t('settings.health.exportBtn') }}</span>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { healthApi } from '@/api'
import { saveBlobResponse } from '@/utils/download'
import {
  IMPORT_PROVIDER,
  buildImportPlan,
  createCollector,
  feedChunk,
  sleepPayload,
  streamExportText,
} from '@/utils/health/appleHealthImport'

const { t } = useI18n()

// 导出范围与报表页原先一致:近 90 天
const EXPORT_DAYS = 90
// zip 解不开时给的具体说法(不同浏览器/压缩方式分开讲,别只丢一句「失败」)
const ZIP_HINTS = {
  ZIP_NO_EOCD: 'importErrZip',
  ZIP_NO_XML: 'importErrZipXml',
  ZIP_UNSUPPORTED: 'importErrZipType',
  ZIP_NO_INFLATE: 'importErrZipBrowser',
}

const fileInput = ref(null)
const fileName = ref('')
const phase = ref('idle') // idle | parse | plan | send
const parsePercent = ref(0)
const sendPercent = ref(0)
const sent = ref(0)
const total = ref(0)
const plan = ref(null)
const result = ref(null)
const exporting = ref(false)

const busy = computed(() => phase.value === 'parse' || phase.value === 'send')

/** 本机时区偏移 +08:00:导出包里的时间自带偏移,这里只是兜底(存疑时间用它) */
const tzOffset = () => {
  const min = -new Date().getTimezoneOffset()
  const abs = Math.abs(min)
  const p = (n) => String(n).padStart(2, '0')
  return `${min < 0 ? '-' : '+'}${p(Math.floor(abs / 60))}:${p(abs % 60)}`
}

const shiftDay = (offset) => {
  const d = new Date()
  d.setHours(12, 0, 0, 0)
  d.setDate(d.getDate() + offset)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const errorText = (err) => {
  const key = ZIP_HINTS[err?.message]
  return key ? t(`settings.health.${key}`) : t('settings.health.importErrGeneric', { message: err?.message || '' })
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

const exportCsv = async () => {
  const from = shiftDay(-(EXPORT_DAYS - 1))
  const to = shiftDay(0)
  exporting.value = true
  try {
    const blob = await healthApi.exportCsv({ from, to })
    await saveBlobResponse(blob, `health-${from}_${to}.csv`)
    ElMessage.success(t('settings.health.exportDone', { from, to }))
  } catch (e) {
    ElMessage.error(e?.message || t('common.failed'))
  } finally {
    exporting.value = false
  }
}
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
@media (max-width: 640px) {
  .import-row {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
