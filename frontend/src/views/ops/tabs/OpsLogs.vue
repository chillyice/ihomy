<template>
  <div class="filter-row">
    <el-input v-model="logFilter.keyword" :placeholder="$t('ops.logKeyword')" clearable style="width: 200px" @keyup.enter="loadLogs(1)" />
    <el-input v-model.number="logFilter.operatorId" :placeholder="$t('ops.operatorId')" style="width: 130px" />
    <el-select
      v-model="logFilter.modules"
      multiple
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :placeholder="$t('ops.module')"
      style="width: 180px"
      @change="loadLogs(1)"
    >
      <el-option v-for="m in logOptions.modules" :key="m" :value="m" :label="m" />
    </el-select>
    <el-select
      v-model="logFilter.operationTypes"
      multiple
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :placeholder="$t('ops.type')"
      style="width: 150px"
      @change="loadLogs(1)"
    >
      <el-option v-for="ty in logOptions.operationTypes" :key="ty" :value="ty" :label="ty" />
    </el-select>
    <el-select
      v-model="logFilter.results"
      multiple
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :placeholder="$t('ops.result')"
      style="width: 120px"
      @change="loadLogs(1)"
    >
      <el-option value="SUCCESS" :label="$t('ops.success')" />
      <el-option value="FAILED" :label="$t('ops.fail')" />
    </el-select>
    <el-date-picker v-model="logFilter.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.startDate')" />
    <el-date-picker v-model="logFilter.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.endDate')" />
    <el-button type="primary" @click="loadLogs(1)">{{ $t('ops.query') }}</el-button>
  </div>
  <el-table v-loading="logsLoading" :data="logPage.records" border stripe>
    <el-table-column prop="createdAt" :label="$t('ops.time')" width="165" />
    <el-table-column prop="operatorName" :label="$t('ops.operator')" width="110" />
    <el-table-column prop="module" :label="$t('ops.module')" width="90" />
    <el-table-column prop="operationType" :label="$t('ops.type')" width="90" />
    <el-table-column prop="description" :label="$t('ops.description')" min-width="180" show-overflow-tooltip />
    <el-table-column prop="requestUrl" label="URL" min-width="160" show-overflow-tooltip />
    <el-table-column prop="resultStatus" :label="$t('ops.result')" width="70">
      <template #default="{ row }">
        <el-tag :type="row.resultStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
          {{ row.resultStatus === 'SUCCESS' ? $t('ops.success') : $t('ops.fail') }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="costTime" :label="$t('ops.costTime')" width="90" />
    <el-table-column prop="traceId" label="TID" width="150">
      <template #default="{ row }">
        <span v-a11y-click v-if="row.traceId" class="tid-link mono" :title="$t('ops.tidJump')" @click="jumpToTrace(row)">{{
          row.traceId
        }}</span>
        <span v-else>-</span>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination
    v-model:current-page="logPageNum"
    v-model:page-size="logPageSize"
    :page-sizes="[20, 50, 100, 200]"
    :total="logTotal"
    layout="total, sizes, prev, pager, next"
    style="margin-top: 14px; justify-content: flex-end"
    @current-change="loadLogs"
    @size-change="loadLogs(1)"
  />
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { opsApi } from '@/api'

const emit = defineEmits(['navigate-trace'])

const logsLoading = ref(false)
const logPage = ref({ records: [] })
const logTotal = ref(0)
const logPageNum = ref(1)
const logPageSize = ref(20)
const logFilter = reactive({ keyword: '', operatorId: '', modules: [], operationTypes: [], results: [], startDate: '', endDate: '' })
// 筛选下拉数据源(distinct 模块/操作类型,来自 sys_operation_log)
const logOptions = ref({ modules: [], operationTypes: [] })
const loadOptions = async () => {
  try {
    logOptions.value = await opsApi.logOptions()
  } catch (e) {
    /* 选项加载失败不阻塞列表 */
  }
}

const loadLogs = async (page = logPageNum.value) => {
  logsLoading.value = true
  logPageNum.value = page
  try {
    const data = await opsApi.logs({
      current: page,
      size: logPageSize.value,
      keyword: logFilter.keyword || null,
      operatorId: logFilter.operatorId || null,
      module: logFilter.modules.length ? logFilter.modules.join(',') : null,
      operationType: logFilter.operationTypes.length ? logFilter.operationTypes.join(',') : null,
      result: logFilter.results.length ? logFilter.results.join(',') : null,
      startDate: logFilter.startDate || null,
      endDate: logFilter.endDate || null,
    })
    logPage.value = data
    logTotal.value = data.total
  } finally {
    logsLoading.value = false
  }
}

/** 操作日志行点击 TID:交给外壳跳到详细日志标签并按该条日志的日期直接查询 */
const jumpToTrace = (row) => {
  emit('navigate-trace', { tid: row.traceId, date: (row.createdAt || '').slice(0, 10) || '' })
}

onMounted(() => {
  loadLogs(1)
  loadOptions()
})
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
.tid-link {
  color: var(--color-brand);
  cursor: pointer;
  font-size: 12px;
}
.tid-link:hover {
  text-decoration: underline;
}
</style>
