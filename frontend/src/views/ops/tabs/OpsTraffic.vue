<template>
  <div class="filter-row">
    <el-date-picker v-model="trafficFilter.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.startDate')" />
    <el-date-picker v-model="trafficFilter.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.endDate')" />
    <el-button type="primary" @click="loadTraffic">{{ $t('ops.query') }}</el-button>
    <span class="traffic-hint">{{ $t('ops.trafficHint') }}</span>
  </div>
  <div v-loading="trafficLoading">
    <div v-if="traffic" class="stats-grid traffic-grid">
      <div v-for="c in trafficCards" :key="c.key" class="stat-card card">
        <div class="stat-name">{{ c.name }}</div>
        <div class="stat-num">{{ traffic[c.key] ?? 0 }}</div>
      </div>
    </div>
    <el-empty v-if="traffic && !traffic.total" :description="$t('ops.traceEmpty')" :image-size="40" />
    <template v-if="traffic && traffic.total">
      <h4 class="ops-section-title" style="margin-top: 20px">{{ $t('ops.hourly') }}</h4>
      <div class="chart-wrap chart-hover-wrap">
        <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="line-chart">
          <line
            v-for="(tk, i) in tYTicks"
            :key="'tg' + i"
            :x1="padL"
            :x2="chartW - padR"
            :y1="tk.y"
            :y2="tk.y"
            stroke="var(--color-border)"
            stroke-width="1"
            stroke-dasharray="3 3"
          />
          <text
            v-for="(tk, i) in tYTicks"
            :key="'tl' + i"
            :x="padL - 8"
            :y="tk.y + 4"
            text-anchor="end"
            fill="var(--color-text-secondary)"
            font-size="11"
          >
            {{ tk.label }}
          </text>
          <template v-for="(h, i) in traffic.hours" :key="'bar' + i">
            <rect :x="barX(i)" :y="barY(h.total)" :width="barW" :height="barH(h.total)" fill="#b88c6e" rx="2" />
            <rect v-if="h.failed" :x="barX(i)" :y="barY(h.failed)" :width="barW" :height="barH(h.failed)" fill="#b04a3a" rx="2" />
          </template>
          <text
            v-for="hx in [0, 3, 6, 9, 12, 15, 18, 21]"
            :key="'hx' + hx"
            :x="padL + (hx + 0.5) * barStep"
            :y="chartH - padB + 16"
            text-anchor="middle"
            fill="var(--color-text-secondary)"
            font-size="11"
          >
            {{ hx }}h
          </text>
          <rect
            v-if="tHoverIdx >= 0"
            class="tb-hover-col"
            :x="padL + tHoverIdx * barStep"
            :y="padT"
            :width="barStep"
            :height="chartH - padB - padT"
          />
          <rect
            v-for="(h, i) in traffic.hours"
            :key="'thv' + i"
            :x="padL + i * barStep"
            :y="padT"
            :width="barStep"
            :height="chartH - padB - padT"
            fill="transparent"
            @mouseenter="tHoverIdx = i"
            @mouseleave="tHoverIdx = -1"
          />
        </svg>
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot" style="background: #b88c6e"></span>{{ $t('ops.trafficTotal') }}</span>
          <span class="legend-item"><span class="legend-dot" style="background: #b04a3a"></span>{{ $t('ops.trafficFailed') }}</span>
        </div>
        <div v-if="tHoverIdx >= 0 && traffic.hours[tHoverIdx]" class="chart-tooltip" :style="tTooltipStyle">
          <div class="ct-time">{{ String(traffic.hours[tHoverIdx].hour).padStart(2, '0') }}:00</div>
          <div class="ct-row">
            <span class="ct-dot" style="background: #b88c6e"></span>{{ $t('ops.trafficTotal') }}
            <b>{{ traffic.hours[tHoverIdx].total }}</b>
          </div>
          <div class="ct-row">
            <span class="ct-dot" style="background: #b04a3a"></span>{{ $t('ops.trafficFailed') }}
            <b>{{ traffic.hours[tHoverIdx].failed }}</b>
          </div>
        </div>
      </div>
      <h4 class="ops-section-title" style="margin-top: 20px">{{ $t('ops.topPaths') }}</h4>
      <el-table :data="traffic.topPaths" size="small" stripe>
        <el-table-column prop="path" :label="$t('ops.path')" min-width="280">
          <template #default="{ row }"
            ><span class="mono">{{ row.path }}</span></template
          >
        </el-table-column>
        <el-table-column prop="count" :label="$t('ops.reqCount')" width="110" />
        <el-table-column prop="failed" :label="$t('ops.trafficFailed')" width="110" />
        <el-table-column prop="avgCostMs" :label="$t('ops.trafficAvgCost')" width="130" />
      </el-table>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'
import { chartH, chartW, padB, padL, padR, padT } from '../opsChart'

const { t } = useI18n()

const traffic = ref(null)
const trafficLoading = ref(false)
const trafficFilter = reactive({ startDate: '', endDate: '' })
const trafficCards = [
  { key: 'total', name: t('ops.trafficTotal') },
  { key: 'failed', name: t('ops.trafficFailed') },
  { key: 'slow', name: t('ops.trafficSlow') },
  { key: 'users', name: t('ops.trafficUsers') },
  { key: 'ips', name: t('ops.trafficIps') },
  { key: 'avgCostMs', name: t('ops.trafficAvgCost') },
]

// 访问统计 24 小时柱状图几何(总请求=暖棕柱,失败=底部红色叠层)
const barStep = (chartW - padL - padR) / 24
const barW = barStep * 0.55
const barX = (i) => padL + i * barStep + (barStep - barW) / 2
const tYMax = computed(() => Math.max(...(traffic.value?.hours || []).map((h) => h.total), 1))
const barH = (v) => ((chartH - padB - padT) * v) / tYMax.value
const barY = (v) => chartH - padB - barH(v)
const tYTicks = computed(() => {
  const ticks = []
  for (let i = 0; i <= 4; i++) {
    ticks.push({ y: chartH - padB - ((chartH - padB - padT) * i) / 4, label: Math.round((tYMax.value * i) / 4) })
  }
  return ticks
})
// 柱状图悬浮提示:整列拾取 + 高亮列 + 数值提示(与折线图同一套交互)
const tHoverIdx = ref(-1)
const tTooltipStyle = computed(() => {
  if (tHoverIdx.value < 0) return {}
  const ratio = Math.min(Math.max((padL + (tHoverIdx.value + 0.5) * barStep) / chartW, 0.15), 0.85)
  return { left: ratio * 100 + '%' }
})

const loadTraffic = async () => {
  trafficLoading.value = true
  try {
    traffic.value = await opsApi.trafficStats({
      startDate: trafficFilter.startDate || null,
      endDate: trafficFilter.endDate || null,
    })
  } finally {
    trafficLoading.value = false
  }
}

onMounted(loadTraffic)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.stat-card {
  padding: 14px 16px;
  text-align: center;
}
.stat-name {
  color: #999;
  font-size: 12px;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}
.chart-wrap {
  background: var(--color-card-2);
  border-radius: 10px;
  padding: 16px;
  position: relative;
}
.line-chart {
  width: 100%;
  height: auto;
  display: block;
}
.chart-tooltip {
  position: absolute;
  top: 26px;
  transform: translateX(-50%);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
  pointer-events: none;
  z-index: 5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  white-space: nowrap;
}
.ct-time {
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}
.ct-row {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text);
}
.ct-row b {
  font-variant-numeric: tabular-nums;
}
.ct-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.tb-hover-col {
  fill: rgba(184, 140, 110, 0.12);
  pointer-events: none;
}
.chart-legend {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin-top: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.mono {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
.ops-section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 10px;
  color: var(--color-text);
}
.traffic-grid {
  grid-template-columns: repeat(6, 1fr);
}
.traffic-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
@media (max-width: 768px) {
  .traffic-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
