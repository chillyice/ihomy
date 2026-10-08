<template>
  <div class="filter-row">
    <el-radio-group v-model="alertFilter.status" @change="loadAlerts(1)">
      <el-radio-button value="OPEN">{{ $t('ops.alertStatusOpen') }}</el-radio-button>
      <el-radio-button value="ACKED">{{ $t('ops.alertStatusAcked') }}</el-radio-button>
      <el-radio-button value="">{{ $t('ops.alertStatusAll') }}</el-radio-button>
    </el-radio-group>
    <el-select v-model="alertFilter.level" clearable :placeholder="$t('ops.alertLevel')" style="width: 130px" @change="loadAlerts(1)">
      <el-option value="ERROR" label="ERROR" />
      <el-option value="WARN" label="WARN" />
    </el-select>
    <el-button type="primary" @click="loadAlerts(alertPageNum)">{{ $t('ops.query') }}</el-button>
    <el-button :disabled="!summary.open" @click="ackAllAlerts">{{ $t('ops.alertAckAll') }}</el-button>
  </div>
  <div class="alert-summary">
    <div class="alert-sum-item">
      <span class="alert-sum-num danger">{{ summary.open }}</span>
      <span class="alert-sum-lbl">{{ $t('ops.alertSummaryOpen') }}</span>
    </div>
    <div class="alert-sum-item">
      <span class="alert-sum-num">{{ summary.today }}</span>
      <span class="alert-sum-lbl">{{ $t('ops.alertSummaryToday') }}</span>
    </div>
    <div class="alert-sum-item">
      <span class="alert-sum-num">{{ summary.total }}</span>
      <span class="alert-sum-lbl">{{ $t('ops.alertSummaryTotal') }}</span>
    </div>
  </div>
  <el-alert v-if="!alertsLoading && !alertPage.records.length" type="info" :closable="false" show-icon :title="$t('ops.alertEmpty')" />
  <el-table v-else v-loading="alertsLoading" :data="alertPage.records" border stripe>
    <el-table-column prop="lastSeen" :label="$t('ops.alertLastSeen')" width="165" />
    <el-table-column prop="level" :label="$t('ops.alertLevel')" width="80">
      <template #default="{ row }">
        <el-tag size="small" :type="levelTagType(row.level)">{{ row.level }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="title" :label="$t('ops.alertTitle')" min-width="230" show-overflow-tooltip />
    <el-table-column prop="logger" :label="$t('ops.alertLogger')" width="170" show-overflow-tooltip />
    <el-table-column :label="$t('ops.alertCount')" width="150">
      <template #default="{ row }">
        {{ $t('ops.alertTimes', { n: row.occurrenceCount }) }} / {{ $t('ops.alertSeconds', { n: row.windowSeconds }) }}
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.alertStatus')" width="90">
      <template #default="{ row }">
        <el-tag size="small" :type="row.status === 'OPEN' ? 'danger' : 'success'">
          {{ row.status === 'OPEN' ? $t('ops.alertStatusOpen') : $t('ops.alertStatusAcked') }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.alertSample')" min-width="240">
      <template #default="{ row }">
        <span class="alert-sample mono" :title="row.sampleMessage">{{ row.sampleMessage }}</span>
        <span v-a11y-click v-if="row.sampleTraceId" class="tid-link mono" :title="$t('ops.tidJump')" @click="jumpAlertTrace(row)">TID</span>
      </template>
    </el-table-column>
    <el-table-column label="" width="120" fixed="right">
      <template #default="{ row }">
        <el-button v-if="row.status === 'OPEN'" size="small" @click="ackAlert(row)">{{ $t('ops.alertAck') }}</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    v-if="alertTotal > alertPageSize"
    v-model:current-page="alertPageNum"
    v-model:page-size="alertPageSize"
    :page-sizes="[20, 50, 100]"
    :total="alertTotal"
    layout="total, sizes, prev, pager, next"
    style="margin-top: 14px; justify-content: flex-end"
    @current-change="loadAlerts"
    @size-change="loadAlerts(1)"
  />
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'
import { ElMessage } from 'element-plus'

const emit = defineEmits(['navigate-trace', 'refresh-summary'])

const props = defineProps({
  // 汇总数由外壳统一加载(标签页角标与卡片共用同一份),用后要求外壳刷新
  summary: { type: Object, default: () => ({ open: 0, today: 0, total: 0 }) },
  // 每次切回该标签都重新查询(与拆分前 watch(tab) 语义一致)
  active: { type: Boolean, default: false },
})

const { t } = useI18n()

const alertsLoading = ref(false)
const alertPage = ref({ records: [] })
const alertTotal = ref(0)
const alertPageNum = ref(1)
const alertPageSize = ref(20)
const alertFilter = reactive({ status: 'OPEN', level: '' })

const loadAlerts = async (page = alertPageNum.value) => {
  alertsLoading.value = true
  try {
    const data = await opsApi.alerts({
      current: page,
      size: alertPageSize.value,
      status: alertFilter.status || null,
      level: alertFilter.level || null,
    })
    alertPage.value = data
    alertTotal.value = data.total || 0
    alertPageNum.value = data.current || page
  } finally {
    alertsLoading.value = false
  }
}
const ackAlert = async (row) => {
  await opsApi.ackAlert(row.id)
  ElMessage.success(t('ops.alertAckDone'))
  emit('refresh-summary')
  await loadAlerts()
}
const ackAllAlerts = async () => {
  const n = await opsApi.ackAllAlerts()
  ElMessage.success(t('ops.alertAckAllDone', { n }))
  emit('refresh-summary')
  await loadAlerts(1)
}
// 预警摘录里的 tid 跳「详细日志」:异步链路(定时任务)没有 tid 时不跳
const jumpAlertTrace = (row) => {
  if (!row.sampleTraceId) return
  emit('navigate-trace', { tid: row.sampleTraceId, date: (row.lastSeen || '').slice(0, 10) || '' })
}

const levelTagType = (l) => (l === 'ERROR' ? 'danger' : l === 'WARN' ? 'warning' : 'info')

onMounted(() => {
  loadAlerts(1)
  emit('refresh-summary')
})

watch(
  () => props.active,
  (v) => {
    if (v) {
      loadAlerts(1)
      emit('refresh-summary')
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
.alert-summary {
  display: flex;
  gap: 28px;
  margin-bottom: 14px;
}
.alert-sum-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.alert-sum-num {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}
.alert-sum-num.danger {
  color: var(--color-accent);
}
.alert-sum-lbl {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.alert-sample {
  display: inline-block;
  max-width: calc(100% - 40px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.mono {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
.tid-link {
  color: var(--color-brand);
  cursor: pointer;
  font-size: 12px;
}
.tid-link:hover {
  text-decoration: underline;
}
</style>
