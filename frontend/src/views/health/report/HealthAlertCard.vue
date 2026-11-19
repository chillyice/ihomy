<!-- 异常提醒卡片:口径全在后端 common/HealthAlerts(参考区间 WARN / 个人基线 INFO),
     这里只把 level + reason 翻译成人话,不重新判断阈值,所以本人报表与分享快照永远一致。 -->
<template>
  <div class="alerts">
    <div v-if="!card.hasData" class="empty-hint">{{ $t('health.alerts.noData') }}</div>
    <div v-else-if="card.ok" class="alerts-ok">
      <el-icon><CircleCheck /></el-icon>
      <span>{{ $t('health.alerts.ok') }}</span>
    </div>
    <template v-else>
      <div v-for="(item, i) in items" :key="`${item.metric}-${i}`" class="alert-row">
        <el-tag :type="item.level === 'WARN' ? 'warning' : 'info'" size="small" effect="plain">
          {{ $t(item.level === 'WARN' ? 'health.alerts.warn' : 'health.alerts.info') }}
        </el-tag>
        <div class="alert-main">
          <div class="alert-title">
            {{ metricName($t, item.metric) }}
            <b>{{ withUnit(item.metric, formatMetric(item.metric, item.value)) }}</b>
          </div>
          <div class="alert-reason">{{ reasonText(item) }}</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CircleCheck } from '@element-plus/icons-vue'
import { formatMetric, metricName, metricUnit, withUnit } from '@/utils/healthMetrics'

const props = defineProps({
  /** HealthReportService 的 alerts 卡片:{hasData, ok, items[]} */
  card: { type: Object, default: () => ({}) },
})

const { t } = useI18n()

const items = computed(() => (Array.isArray(props.card?.items) ? props.card.items : []))

const unit = (item) => metricUnit(item.metric)

/** 参考区间:说清「正常范围是多少」;基线偏离:说清「你自己的平时值是多少」 */
function reasonText(item) {
  if (item.reason === 'BASELINE') {
    const baseline = `${formatMetric(item.metric, item.baseline)} ${unit(item)}`.trim()
    return t('health.alerts.baseline', { value: baseline })
  }
  const low = formatMetric(item.metric, item.refLow)
  const high = formatMetric(item.metric, item.refHigh)
  return t('health.alerts.refRange', { low, high, unit: unit(item) })
}
</script>

<style scoped>
.alerts-ok {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 2px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.alerts-ok .el-icon {
  color: var(--color-green);
}
.alert-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--color-border);
}
.alert-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.alert-main {
  min-width: 0;
}
.alert-title {
  font-size: 13px;
  color: var(--color-text-primary);
}
.alert-title b {
  margin-left: 6px;
  font-size: 14px;
}
.alert-reason {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.empty-hint {
  padding: 10px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
