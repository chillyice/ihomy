<!-- 健康报表分享弹窗:勾选这张快照包含的卡片 → 生成一条 7 天有效的只读报告链接。
     分享的是「此刻的报告」冻结副本,不是实时数据通道;敏感指标(月经类)默认不含,
     要进快照必须本人已在设置里开启采集同意,并且这里再勾一次。 -->
<template>
  <el-dialog v-model="visible" :title="$t('health.share.title')" class="dialog-md" append-to-body>
    <p class="dialog-hint">{{ $t('health.share.hint') }}</p>

    <div v-if="!report" class="empty-hint">{{ $t('health.share.noData') }}</div>
    <template v-else>
      <div class="field">
        <div class="field-label">{{ $t('health.share.cards') }}</div>
        <el-checkbox-group v-model="picked" class="card-picks">
          <el-checkbox v-for="c in pickable" :key="c.id" :value="c.id" :disabled="c.sensitive && !consentOn" :label="cardTitle($t, c.id)" />
        </el-checkbox-group>
        <div v-if="!consentOn" class="field-tip">{{ $t('health.share.sensitiveDisabled') }}</div>
      </div>

      <div v-if="consentOn" class="field switch-field">
        <div>
          <div class="field-label">{{ $t('health.share.sensitive') }}</div>
          <div class="field-tip">{{ $t('health.share.sensitiveHint') }}</div>
        </div>
        <el-switch v-model="includeSensitive" />
      </div>

      <div class="field">
        <el-button type="primary" :loading="creating" :disabled="!picked.length" @click="create">
          {{ $t('health.share.create') }}
        </el-button>
        <span class="field-tip">{{ $t('health.share.expireNote') }}</span>
      </div>

      <div class="link-box">
        <el-input :model-value="latestLink" readonly>
          <template #append>
            <el-button :disabled="!latestLink" @click="copy(latestLink)">{{ $t('health.share.copy') }}</el-button>
          </template>
        </el-input>
      </div>
    </template>

    <div class="section-label share-list-title">{{ $t('health.share.list') }}</div>
    <div v-if="!shares.length" class="empty-hint">{{ $t('health.share.empty') }}</div>
    <div v-for="s in shares" :key="s.id" class="share-row">
      <div class="share-main">
        <div class="share-cards">{{ cardsText(s.cards) }}</div>
        <div class="share-meta">
          {{ $t('health.share.remaining', { n: s.remainingDays }) }} ·
          {{ $t('health.share.views', { n: s.viewCount }) }}
          <span v-if="s.includeSensitive" class="share-flag">{{ $t('health.share.sensitiveFlag') }}</span>
        </div>
      </div>
      <div class="share-ops">
        <el-button size="small" @click="copy(linkOf(s))">{{ $t('health.share.copy') }}</el-button>
        <el-button size="small" type="danger" plain @click="remove(s)">{{ $t('health.share.delete') }}</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { healthApi } from '@/api'
import { copyText as copyToClipboard } from '@/utils/clipboard'
import { HEALTH_REPORT_CARDS, cardTitle, isSensitiveCard, sanitizeCards } from './reportCards'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 当前报表(HealthReportService.build 的返回);null 时只显示已有链接与空态 */
  report: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()

const shares = ref([])
const picked = ref([])
const includeSensitive = ref(false)
const consentOn = ref(false)
const creating = ref(false)
const latestLink = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// 敏感卡片在未开启采集同意时不可选,避免生成一份「注定被后端丢掉」的快照
const pickable = computed(() => HEALTH_REPORT_CARDS.filter((c) => !c.sensitive || consentOn.value))

const linkOf = (s) => `${location.origin}/health/shared/${s.token}`
const cardsText = (ids) => (Array.isArray(ids) ? ids.map((id) => cardTitle(t, id)).join(' · ') : '')

/** 打开弹窗时:按当前报表的卡片预勾选(分享默认就是眼前这份),并拉已有链接 + 同意开关 */
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    const ids = (props.report?.cards || []).map((c) => c.id).filter((id) => !isSensitiveCard(id))
    picked.value = sanitizeCards(ids)
    includeSensitive.value = false
    latestLink.value = ''
    await Promise.all([loadConsent(), loadShares()])
  },
)

async function loadConsent() {
  const data = await healthApi.consent().catch(() => null)
  consentOn.value = !!data?.reproductive
}

async function loadShares() {
  const data = await healthApi.shares().catch(() => null)
  shares.value = Array.isArray(data) ? data : []
}

async function create() {
  if (!picked.value.length) return
  creating.value = true
  try {
    const data = await healthApi.createShare({
      cards: picked.value,
      includeSensitive: consentOn.value && includeSensitive.value,
      date: props.report?.anchorDate || null,
    })
    latestLink.value = data?.path ? `${location.origin}${data.path}` : ''
    ElMessage.success(t('health.share.created'))
    await loadShares()
  } catch (e) {
    ElMessage.error(e?.message || t('common.failed'))
  } finally {
    creating.value = false
  }
}

async function copy(link) {
  if (!link) return
  const ok = await copyToClipboard(link)
  if (ok) ElMessage.success(t('health.share.copied'))
  else ElMessage.error(t('settings.health.copyFailed'))
}

async function remove(share) {
  try {
    await ElMessageBox.confirm(t('health.share.deleteConfirm'), t('health.share.delete'), { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await healthApi.deleteShare(share.id)
    ElMessage.success(t('health.share.deleted'))
    if (latestLink.value === linkOf(share)) latestLink.value = ''
    await loadShares()
  } catch (e) {
    ElMessage.error(e?.message || t('common.failed'))
  }
}
</script>

<style scoped>
.dialog-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.field {
  margin-bottom: 12px;
}
.field-label {
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--color-text-primary);
}
.field-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.switch-field {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.card-picks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}
.link-box {
  margin-top: 4px;
}
.share-list-title {
  margin-top: 16px;
}
.share-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--color-border);
}
.share-row:last-child {
  border-bottom: none;
}
.share-main {
  min-width: 0;
}
.share-cards {
  font-size: 13px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.share-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.share-flag {
  margin-left: 6px;
  padding: 0 6px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
}
.share-ops {
  display: flex;
  flex: none;
  gap: 6px;
}
.empty-hint {
  padding: 8px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
