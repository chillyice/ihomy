<template>
  <div class="filter-row">
    <el-input v-model="traceFilter.tid" :placeholder="$t('ops.tidPlaceholder')" clearable style="width: 320px" @keyup.enter="loadTrace" />
    <el-date-picker v-model="traceFilter.date" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.traceDate')" />
    <el-select
      v-model="traceFilter.sources"
      multiple
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :placeholder="$t('ops.filterSource')"
      style="width: 170px"
    >
      <el-option v-for="s in TRACE_SOURCES" :key="s" :value="s" :label="$t('ops.source_' + s)" />
    </el-select>
    <el-select
      v-model="traceFilter.levels"
      multiple
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :placeholder="$t('ops.filterLevel')"
      style="width: 150px"
    >
      <el-option v-for="l in TRACE_LEVELS" :key="l" :value="l" :label="l" />
    </el-select>
    <el-button type="primary" @click="loadTrace">{{ $t('ops.query') }}</el-button>
  </div>
  <el-alert
    v-if="traceResult && traceResult.truncated"
    type="warning"
    :closable="false"
    show-icon
    style="margin-bottom: 10px"
    :title="$t('ops.traceTruncated')"
  />
  <el-alert
    v-if="!traceLoading && traceResult && !traceResult.entries.length"
    type="info"
    :closable="false"
    show-icon
    :title="$t('ops.traceEmpty')"
  />
  <div v-if="traceResult && traceResult.entries.length" class="trace-count">
    {{ $t('ops.traceCount', { n: traceResult.count }) }} · {{ traceResult.date }}
  </div>
  <div v-loading="traceLoading" class="trace-list">
    <div v-for="(e, i) in traceResult?.entries || []" :key="i" class="trace-entry" :class="'lv-' + String(e.level || '').toLowerCase()">
      <div class="trace-head">
        <span class="trace-time mono">{{ e.time }}</span>
        <el-tag size="small" :type="sourceTagType(e.source)">{{ $t('ops.source_' + e.source) }}</el-tag>
        <el-tag size="small" :type="levelTagType(e.level)">{{ e.level }}</el-tag>
        <span class="trace-logger">{{ e.logger }}</span>
      </div>
      <pre class="trace-msg">{{ e.message }}</pre>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { opsApi } from '@/api'

const props = defineProps({
  // 由外壳经由操作日志/异常预警的 TID 点击传入;nonce 变化表示新的一次跳转
  preset: { type: Object, default: null },
})

const traceLoading = ref(false)
const traceResult = ref(null)
const TRACE_SOURCES = ['access', 'server', 'thirdparty']
const TRACE_LEVELS = ['ERROR', 'WARN', 'INFO', 'DEBUG']
const traceFilter = reactive({ tid: '', date: '', sources: [], levels: [] })

const loadTrace = async () => {
  const tid = traceFilter.tid.trim()
  if (tid.length < 6) return
  traceLoading.value = true
  try {
    traceResult.value = await opsApi.traceLogs({
      tid,
      date: traceFilter.date || null,
      sources: traceFilter.sources.length ? traceFilter.sources.join(',') : null,
      levels: traceFilter.levels.length ? traceFilter.levels.join(',') : null,
    })
  } finally {
    traceLoading.value = false
  }
}

const applyPreset = () => {
  if (!props.preset?.tid) return
  traceFilter.tid = props.preset.tid
  traceFilter.date = props.preset.date || ''
  traceResult.value = null
  loadTrace()
}

const sourceTagType = (s) => (s === 'access' ? 'primary' : s === 'thirdparty' ? 'success' : 'warning')
const levelTagType = (l) => (l === 'ERROR' ? 'danger' : l === 'WARN' ? 'warning' : 'info')

watch(() => props.preset?.nonce, applyPreset)
onMounted(applyPreset)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.mono {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
.trace-count {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}
.trace-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.trace-entry {
  background: var(--color-card-2);
  border-radius: 10px;
  padding: 10px 14px;
}
.trace-entry.lv-error {
  border-left: 3px solid #b04a3a;
}
.trace-entry.lv-warn {
  border-left: 3px solid var(--color-brand);
}
.trace-head {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.trace-time {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.trace-logger {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.trace-msg {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
  font-family: Consolas, Monaco, 'Courier New', monospace;
  max-height: 420px;
  overflow: auto;
}
</style>
