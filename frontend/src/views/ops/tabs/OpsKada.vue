<template>
  <div class="filter-row">
    <el-radio-group v-model="status" @change="load(1)">
      <el-radio-button value="PENDING">{{ $t('ops.kada.statusPending') }}</el-radio-button>
      <el-radio-button value="APPROVED">{{ $t('ops.kada.statusApproved') }}</el-radio-button>
      <el-radio-button value="REJECTED">{{ $t('ops.kada.statusRejected') }}</el-radio-button>
      <el-radio-button value="">{{ $t('ops.kada.statusAll') }}</el-radio-button>
    </el-radio-group>
    <el-select v-model="level" clearable :placeholder="$t('ops.kada.level')" style="width: 140px">
      <el-option :value="0" :label="$t('ops.kada.level0')" />
      <el-option :value="1" :label="$t('ops.kada.level1')" />
      <el-option :value="2" :label="$t('ops.kada.level2')" />
      <el-option :value="3" :label="$t('ops.kada.level3')" />
    </el-select>
    <el-input
      v-model="q"
      clearable
      :placeholder="$t('ops.kada.search')"
      style="width: 220px"
      @keyup.enter="load(1)"
    />
    <el-button type="primary" @click="load(1)">{{ $t('ops.kada.query') }}</el-button>
  </div>

  <div class="kada-summary">
    <div class="kada-sum-item">
      <span class="kada-sum-num danger">{{ summary.pending }}</span>
      <span class="kada-sum-lbl">{{ $t('ops.kada.summaryPending') }}</span>
    </div>
    <div class="kada-sum-item">
      <span class="kada-sum-num danger">{{ summary.risky }}</span>
      <span class="kada-sum-lbl">{{ $t('ops.kada.summaryRisky') }}</span>
    </div>
    <div class="kada-sum-item">
      <span class="kada-sum-num">{{ summary.approved }}</span>
      <span class="kada-sum-lbl">{{ $t('ops.kada.summaryApproved') }}</span>
    </div>
    <div class="kada-sum-item">
      <span class="kada-sum-num">{{ summary.rejected }}</span>
      <span class="kada-sum-lbl">{{ $t('ops.kada.summaryRejected') }}</span>
    </div>
  </div>

  <el-alert
    v-if="!loading && !page.records.length"
    type="info"
    :closable="false"
    show-icon
    :title="$t('ops.kada.empty')"
  />
  <el-table v-else v-loading="loading" :data="page.records" border stripe>
    <el-table-column prop="createdAt" :label="$t('ops.kada.created')" width="165" />
    <el-table-column :label="$t('ops.kada.name')" min-width="200" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="kada-name" v-a11y-click @click="openDetail(row.id)">{{ row.name }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="author" :label="$t('ops.kada.author')" width="120" show-overflow-tooltip />
    <el-table-column :label="$t('ops.kada.level')" width="100">
      <template #default="{ row }">
        <el-tag size="small" :type="levelTagType(row.dangerLevel)">
          {{ $t(`ops.kada.level${row.dangerLevel}`) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.kada.legal')" width="90">
      <template #default="{ row }">
        <el-tag v-if="parseList(row.legalFlagsJson).length" size="small" type="danger">
          {{ $t('ops.kada.legalFlag') }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.kada.status')" width="100">
      <template #default="{ row }">
        <el-tag size="small" :type="statusTagType(row.status)">
          {{ $t(statusKey(row.status)) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column label="" width="150" fixed="right">
      <template #default="{ row }">
        <el-button size="small" @click="openDetail(row.id)">{{ $t('ops.kada.view') }}</el-button>
        <el-button v-if="row.publishedUrl" size="small" link @click="openPublished(row.publishedUrl)">
          {{ $t('ops.kada.open') }}
        </el-button>
      </template>
    </el-table-column>
  </el-table>

  <el-pagination
    v-if="total > pageSize"
    v-model:current-page="pageNum"
    v-model:page-size="pageSize"
    :page-sizes="[20, 50, 100]"
    :total="total"
    layout="total, sizes, prev, pager, next"
    style="margin-top: 14px; justify-content: flex-end"
    @current-change="load"
    @size-change="load(1)"
  />

  <el-dialog
    v-model="detailVisible"
    :title="$t('ops.kada.detailTitle')"
    width="min(880px, 92vw)"
    destroy-on-close
  >
    <template v-if="detail.item">
      <div class="kada-meta">
        <span class="kada-detail-name">{{ detail.item.name }}</span>
        <el-tag size="small" :type="levelTagType(detail.item.dangerLevel)">
          {{ $t(`ops.kada.level${detail.item.dangerLevel}`) }}
        </el-tag>
        <el-tag size="small" :type="statusTagType(detail.item.status)">
          {{ $t(statusKey(detail.item.status)) }}
        </el-tag>
        <span class="kada-meta-text">{{ $t('ops.kada.author') }}：{{ detail.item.author || '—' }}</span>
        <span class="kada-meta-text">#{{ detail.item.id }} · {{ detail.item.createdAt }}</span>
      </div>
      <div class="kada-tags">
        <el-tag v-for="tag in parseList(detail.item.tags)" :key="tag" size="small" effect="plain">
          {{ tag }}
        </el-tag>
      </div>
      <p v-if="detail.item.description" class="kada-desc">{{ detail.item.description }}</p>

      <el-alert
        v-if="parseList(detail.item.legalFlagsJson).length"
        type="error"
        :closable="false"
        show-icon
        :title="$t('ops.kada.legalTitle')"
      />
      <ul v-if="parseList(detail.item.legalFlagsJson).length" class="kada-flag-list">
        <li v-for="flag in parseList(detail.item.legalFlagsJson)" :key="flag">{{ flag }}</li>
      </ul>

      <div class="kada-section-title">{{ $t('ops.kada.findings') }}</div>
      <ul v-if="parseList(detail.item.findingsJson).length" class="kada-findings">
        <li v-for="f in parseList(detail.item.findingsJson)" :key="f">{{ f }}</li>
      </ul>
      <p v-else class="kada-none">{{ $t('ops.kada.none') }}</p>

      <div class="kada-section-title">{{ $t('ops.kada.content') }}</div>
      <pre class="kada-content">{{ detail.content }}</pre>

      <el-input
        v-model="note"
        type="textarea"
        :rows="2"
        maxlength="500"
        show-word-limit
        :placeholder="$t('ops.kada.notePlaceholder')"
      />
      <p v-if="detail.item.reviewNote" class="kada-meta-text">
        {{ $t('ops.kada.reviewNote') }}：{{ detail.item.reviewNote }}
      </p>
    </template>
    <template #footer>
      <el-button @click="detailVisible = false">{{ $t('ops.kada.cancel') }}</el-button>
      <el-button
        v-if="detail.item && detail.item.status !== 'REJECTED'"
        type="danger"
        @click="reject"
      >
        {{ detail.item.status === 'APPROVED' ? $t('ops.kada.takedown') : $t('ops.kada.reject') }}
      </el-button>
      <el-button
        v-if="detail.item && detail.item.status !== 'APPROVED'"
        type="primary"
        @click="approve"
      >
        {{ $t('ops.kada.approve') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { opsApi } from '@/api'

const props = defineProps({
  // 每次切回该标签都重新查询（与 OpsAlerts 一致）
  active: { type: Boolean, default: false },
})

const { t } = useI18n()

const loading = ref(false)
const page = ref({ records: [] })
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const status = ref('PENDING')
const level = ref(null)
const q = ref('')
const summary = ref({ pending: 0, risky: 0, approved: 0, rejected: 0 })

const detailVisible = ref(false)
const detail = reactive({ item: null, content: '' })
const note = ref('')

const parseList = (raw) => {
  if (!raw) return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v : []
  } catch {
    return []
  }
}

const levelTagType = (lv) => ['success', 'info', 'warning', 'danger'][lv] || 'info'
const statusTagType = (s) => (s === 'APPROVED' ? 'success' : s === 'REJECTED' ? 'info' : 'warning')
const statusKey = (s) =>
  s === 'APPROVED'
    ? 'ops.kada.statusApproved'
    : s === 'REJECTED'
      ? 'ops.kada.statusRejected'
      : 'ops.kada.statusPending'

const loadSummary = async () => {
  try {
    summary.value = await opsApi.kadaSnippetSummary()
  } catch {
    /* 概览失败不阻塞列表 */
  }
}

const load = async (pageNo = pageNum.value) => {
  loading.value = true
  try {
    const data = await opsApi.kadaSnippets({
      current: pageNo,
      size: pageSize.value,
      status: status.value || null,
      level: level.value === null || level.value === '' ? null : level.value,
      q: q.value || null,
    })
    page.value = data
    total.value = data.total || 0
    pageNum.value = data.current || pageNo
  } finally {
    loading.value = false
  }
}

const openDetail = async (id) => {
  detail.item = null
  detail.content = ''
  note.value = ''
  const data = await opsApi.kadaSnippetDetail(id)
  detail.item = data.item
  detail.content = data.content || ''
  detailVisible.value = true
}

const openPublished = (url) => {
  if (url) window.open(url, '_blank', 'noopener')
}

const approve = async () => {
  if (!detail.item) return
  try {
    await ElMessageBox.confirm(
      t('ops.kada.approveConfirm', { name: detail.item.name }),
      t('ops.kada.approve'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  await opsApi.approveKadaSnippet(detail.item.id, { note: note.value })
  ElMessage.success(t('ops.kada.approved'))
  detailVisible.value = false
  await loadSummary()
  await load()
}

const reject = async () => {
  if (!detail.item) return
  const isTakedown = detail.item.status === 'APPROVED'
  try {
    await ElMessageBox.confirm(
      t(isTakedown ? 'ops.kada.takedownConfirm' : 'ops.kada.rejectConfirm', { name: detail.item.name }),
      t(isTakedown ? 'ops.kada.takedown' : 'ops.kada.reject'),
      { type: 'warning' },
    )
  } catch {
    return
  }
  await opsApi.rejectKadaSnippet(detail.item.id, { note: note.value })
  ElMessage.success(t(isTakedown ? 'ops.kada.takedownDone' : 'ops.kada.rejected'))
  detailVisible.value = false
  await loadSummary()
  await load()
}

onMounted(() => {
  load(1)
  loadSummary()
})

watch(
  () => props.active,
  (v) => {
    if (v) {
      load(1)
      loadSummary()
    }
  },
)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.kada-summary {
  display: flex;
  gap: 28px;
  margin-bottom: 14px;
}
.kada-sum-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kada-sum-num {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.kada-sum-num.danger {
  color: var(--color-accent);
}
.kada-sum-lbl {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.kada-name {
  color: var(--color-brand);
  cursor: pointer;
}
.kada-name:hover {
  text-decoration: underline;
}
.kada-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.kada-detail-name {
  font-size: 16px;
  font-weight: 600;
}
.kada-meta-text {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.kada-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.kada-desc {
  margin-top: 8px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.kada-section-title {
  margin-top: 16px;
  margin-bottom: 6px;
  font-weight: 600;
}
.kada-flag-list,
.kada-findings {
  margin: 8px 0 0;
  padding-left: 18px;
  line-height: 1.7;
}
.kada-flag-list {
  color: var(--color-accent);
}
.kada-findings {
  color: var(--color-text-secondary);
}
.kada-none {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.kada-content {
  max-height: 320px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
