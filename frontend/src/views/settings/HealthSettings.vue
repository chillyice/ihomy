<!-- 健康数据同步(V12.88):手机/手表把睡眠与体征上报到本站。
     上报令牌只够 /health/ingest 与 /health/ping,明文只在生成时显示一次;
     敏感指标同意开关与「谁看过我的数据」同页管理,授权以服务端为准。 -->
<template>
  <div>
    <div class="card settings-card" v-loading="loading">
      <div class="section-label">{{ $t('settings.health.title') }}</div>
      <div class="share-tip">{{ $t('settings.health.hint') }}</div>

      <div class="health-endpoint">
        <span class="endpoint-label">{{ $t('settings.health.endpoint') }}</span>
        <code class="endpoint-url">{{ endpoint }}</code>
      </div>

      <div class="health-actions">
        <el-input v-model="tokenName" :placeholder="$t('settings.health.namePlaceholder')" maxlength="50" clearable class="health-name" />
        <el-button type="primary" :loading="creating" @click="createToken">{{ $t('settings.health.create') }}</el-button>
        <el-button :loading="loading" @click="load">{{ $t('common.refresh') }}</el-button>
      </div>

      <div v-if="plainToken" class="plain-box">
        <div class="plain-title">{{ $t('settings.health.tokenOnce') }}</div>
        <div class="plain-row">
          <code class="plain-token">{{ plainToken }}</code>
          <el-button size="small" type="primary" plain @click="copyPlain">{{ $t('settings.health.copy') }}</el-button>
        </div>
        <div class="share-tip">{{ $t('settings.health.usage') }}</div>
      </div>

      <el-table v-if="tokens.length" :data="tokens" border stripe size="small" class="health-table">
        <el-table-column prop="name" :label="$t('settings.health.tokenName')" min-width="130" show-overflow-tooltip />
        <el-table-column :label="$t('settings.health.prefix')" width="110">
          <template #default="{ row }"
            ><code>{{ row.tokenPrefix }}…</code></template
          >
        </el-table-column>
        <el-table-column :label="$t('settings.health.lastUsed')" width="150">
          <template #default="{ row }">{{ formatDateTime(row.lastUsedAt) || $t('settings.health.never') }}</template>
        </el-table-column>
        <el-table-column :label="$t('settings.health.lastIngest')" width="150">
          <template #default="{ row }">{{ formatDateTime(row.lastIngestAt) || $t('settings.health.never') }}</template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" width="80" fixed="right">
          <template #default="{ row }">
            <el-tooltip :content="$t('settings.health.revoke')" placement="top" :show-after="300">
              <el-button size="small" text type="danger" @click="revoke(row)"
                ><el-icon><Delete /></el-icon
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :description="$t('settings.health.empty')" :image-size="70" />

      <div v-if="lastResult" class="result-row">
        <span class="result-label">{{ $t('settings.health.lastResult') }}</span>
        <el-tag size="small" type="success">{{ $t('settings.health.accepted') }} {{ lastResult.accepted || 0 }}</el-tag>
        <el-tag size="small" type="info">{{ $t('settings.health.duplicated') }} {{ lastResult.duplicated || 0 }}</el-tag>
        <el-tag size="small" type="warning">{{ $t('settings.health.rejected') }} {{ lastResult.rejected || 0 }}</el-tag>
        <el-tag size="small" type="info">{{ $t('settings.health.skipped') }} {{ lastResult.skipped || 0 }}</el-tag>
        <span class="result-time">{{ formatDateTime(lastResult.at) }}</span>
      </div>
      <div v-if="rejectedDetail.length" class="share-tip reject-tip">
        <div v-for="d in rejectedDetail" :key="`${d.index}-${d.metric}`">
          {{ $t('settings.health.rejectLine', { index: d.index + 1, metric: d.metric, reason: d.reason }) }}
        </div>
      </div>
    </div>

    <div class="card settings-card health-privacy">
      <div class="section-label">{{ $t('settings.health.privacyTitle') }}</div>

      <div class="privacy-row">
        <div class="privacy-text">
          <div class="row-title">{{ $t('settings.health.reproductive') }}</div>
          <div class="share-tip">{{ $t('settings.health.reproductiveHint') }}</div>
        </div>
        <el-switch v-model="reproductive" :loading="savingConsent" @change="onConsentChange" />
      </div>

      <div class="privacy-row">
        <div class="privacy-text">
          <div class="row-title">{{ $t('settings.health.clearTitle') }}</div>
          <div class="share-tip">{{ $t('settings.health.clearHint') }}</div>
        </div>
        <div class="clear-row">
          <el-select v-model="clearSource" class="clear-select">
            <el-option v-for="s in SOURCES" :key="s.value" :label="$t(s.labelKey)" :value="s.value" />
          </el-select>
          <el-button :loading="clearing" @click="clearData">{{ $t('settings.health.clear') }}</el-button>
        </div>
      </div>

      <div class="privacy-row">
        <div class="privacy-text">
          <div class="row-title">{{ $t('settings.health.viewLog') }}</div>
          <div class="share-tip">{{ $t('settings.health.viewLogHint') }}</div>
        </div>
        <el-button size="small" :loading="loadingViewLog" @click="loadViewLog">{{ $t('settings.health.viewLogShow') }}</el-button>
      </div>

      <el-table v-if="viewLogs.length" :data="viewLogs" border stripe size="small">
        <el-table-column :label="$t('settings.health.viewlogViewer')" min-width="110">
          <template #default="{ row }">{{ row.viewerName || row.viewerId }}</template>
        </el-table-column>
        <el-table-column :label="$t('settings.health.viewlogScope')" min-width="120">
          <template #default="{ row }">{{ scopeText(row) }}</template>
        </el-table-column>
        <el-table-column :label="$t('settings.health.viewlogTime')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <div v-else-if="viewLogLoaded" class="share-tip">{{ $t('settings.health.viewLogEmpty') }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { healthApi } from '@/api'
import { formatDateTime } from '@/utils/datetime'

const { t } = useI18n()

const SOURCES = [
  { value: 'SHORTCUT', labelKey: 'settings.health.sourceShortcut' },
  { value: 'HEALTH_CONNECT', labelKey: 'settings.health.sourceHealthConnect' },
  { value: 'IOS_NATIVE', labelKey: 'settings.health.sourceNative' },
]

const loading = ref(false)
const creating = ref(false)
const clearing = ref(false)
const savingConsent = ref(false)
const loadingViewLog = ref(false)
const viewLogLoaded = ref(false)

const tokenName = ref('')
const tokens = ref([])
const lastResult = ref(null)
// 明文令牌只在生成的那一次回显,刷新页面即消失(库里只有哈希)
const plainToken = ref('')

const reproductive = ref(false)
const clearSource = ref('SHORTCUT')
const viewLogs = ref([])

// 上传地址按当前站点域名拼,内嵌壳与局域网访问都对得上
const endpoint = computed(() => `${window.location.origin}/api/health/ingest`)

const rejectedDetail = computed(() => {
  const list = lastResult.value?.rejectedDetail
  return Array.isArray(list) ? list.slice(0, 5) : []
})

const scopeText = (row) => {
  const key = { OVERVIEW: 'viewlogScopeOverview', SLEEP: 'viewlogScopeSleep', METRIC: 'viewlogScopeMetric' }[row.scope]
  const base = key ? t(`settings.health.${key}`) : row.scope
  return row.metric ? `${base} · ${row.metric}` : base
}

const load = async () => {
  loading.value = true
  try {
    const data = await healthApi.tokens()
    tokens.value = data?.tokens || []
    lastResult.value = data?.lastResult || null
  } finally {
    loading.value = false
  }
}

const loadConsent = async () => {
  try {
    const data = await healthApi.consent()
    reproductive.value = !!data?.reproductive
  } catch (e) {
    /* 读不到就按默认关处理,不阻塞其它卡片 */
  }
}

const createToken = async () => {
  creating.value = true
  try {
    const data = await healthApi.createToken(tokenName.value ? { name: tokenName.value } : {})
    plainToken.value = data?.token || ''
    tokenName.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e?.message || t('common.saveFailed'))
  } finally {
    creating.value = false
  }
}

/** 复制统一入口:内嵌壳/局域网 http 下 navigator.clipboard 不可用,退回 execCommand */
const copyPlain = async () => {
  const text = plainToken.value
  if (!text) return
  let ok = false
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ok = true
    }
  } catch (e) {
    /* 走 execCommand 兜底 */
  }
  if (!ok) {
    try {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      ok = document.execCommand('copy')
      document.body.removeChild(ta)
    } catch (e) {
      ok = false
    }
  }
  if (ok) ElMessage.success(t('settings.health.copied'))
  else ElMessage.warning(t('settings.health.copyFailed'))
}

const revoke = async (row) => {
  await ElMessageBox.confirm(t('settings.health.revokeConfirm', { name: row.name }), t('common.tip'), { type: 'warning' })
  try {
    await healthApi.revokeToken(row.id)
    if (plainToken.value && row.tokenPrefix && plainToken.value.startsWith(row.tokenPrefix)) plainToken.value = ''
    ElMessage.success(t('settings.health.revoked'))
    await load()
  } catch (e) {
    ElMessage.error(e?.message || t('common.saveFailed'))
  }
}

/** 关掉开关时问一次「是否一并清除已采集数据」,取消=仅关闭、保留历史 */
const onConsentChange = async (on) => {
  savingConsent.value = true
  try {
    let clearData = false
    if (!on) {
      try {
        await ElMessageBox.confirm(t('settings.health.clearConsentConfirm'), t('common.tip'), {
          type: 'warning',
          confirmButtonText: t('settings.health.clearConsentYes'),
          cancelButtonText: t('settings.health.clearConsentNo'),
        })
        clearData = true
      } catch (e) {
        clearData = false
      }
    }
    await healthApi.saveConsent({ reproductive: on, clearData })
    reproductive.value = on
    ElMessage.success(t('settings.health.consentSaved'))
  } catch (e) {
    reproductive.value = !on
    ElMessage.error(e?.message || t('common.saveFailed'))
  } finally {
    savingConsent.value = false
  }
}

const clearData = async () => {
  await ElMessageBox.confirm(t('settings.health.clearConfirm'), t('common.tip'), { type: 'warning' })
  clearing.value = true
  try {
    const data = await healthApi.clearData(clearSource.value)
    ElMessage.success(t('settings.health.cleared', { count: (data?.samples || 0) + (data?.sleeps || 0) }))
    await load()
  } catch (e) {
    ElMessage.error(e?.message || t('common.saveFailed'))
  } finally {
    clearing.value = false
  }
}

const loadViewLog = async () => {
  loadingViewLog.value = true
  try {
    const data = await healthApi.viewLog(30)
    viewLogs.value = Array.isArray(data) ? data : []
    viewLogLoaded.value = true
  } catch (e) {
    ElMessage.error(e?.message || t('common.saveFailed'))
  } finally {
    loadingViewLog.value = false
  }
}

onMounted(() => {
  load()
  loadConsent()
})
</script>

<style scoped>
.health-privacy {
  margin-top: 16px;
}
.health-endpoint {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}
.endpoint-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.endpoint-url {
  font-size: 12.5px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--color-bg-2);
  word-break: break-all;
}
.health-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
.health-name {
  width: 220px;
}
.plain-box {
  margin-top: 12px;
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--el-color-success-light-5);
  background: var(--el-color-success-light-9);
}
.plain-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.plain-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.plain-token {
  flex: 1;
  min-width: 200px;
  font-size: 12.5px;
  word-break: break-all;
}
.health-table {
  margin-top: 12px;
}
.result-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.result-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.result-time {
  font-size: 12.5px;
  color: var(--color-text-secondary);
}
.reject-tip {
  margin-top: 6px;
}
.privacy-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}
.privacy-row:last-of-type {
  border-bottom: none;
}
.privacy-text {
  min-width: 0;
}
.row-title {
  font-size: 13.5px;
}
.clear-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
.clear-select {
  width: 160px;
}
@media (max-width: 640px) {
  .health-name,
  .clear-select {
    width: 100%;
  }
  .health-actions {
    flex-direction: column;
    align-items: stretch;
  }
  .privacy-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
