<template>
  <section v-loading="loading" class="inventory">
    <header class="inventory-head">
      <h2 class="section-label">{{ $t('ops.overview') }}</h2>
      <el-button :aria-expanded="showFilters" :icon="Filter" @click="showFilters = !showFilters">{{ $t('ops.filters') }}</el-button>
    </header>
    <form v-if="showFilters" class="resource-filters" @submit.prevent="loadStats">
      <el-date-picker
        v-model="draft.startDate"
        type="date"
        value-format="YYYY-MM-DD"
        :aria-label="$t('ops.startDate')"
        :placeholder="$t('ops.startDate')"
      />
      <el-date-picker
        v-model="draft.endDate"
        type="date"
        value-format="YYYY-MM-DD"
        :aria-label="$t('ops.endDate')"
        :placeholder="$t('ops.endDate')"
      />
      <el-input v-model="draft.userId" :aria-label="$t('ops.userId')" :placeholder="$t('ops.userId')" />
      <el-input v-model="draft.familyId" :aria-label="$t('ops.familyId')" :placeholder="$t('ops.familyId')" />
      <el-button type="primary" native-type="submit">{{ $t('ops.query') }}</el-button>
      <el-button @click="reset">{{ $t('ops.reset') }}</el-button>
    </form>
    <p v-if="scope" class="scope">{{ scope }}</p>
    <p v-if="failed" role="alert">
      {{ $t('common.loadFailed') }} <el-button @click="loadStats">{{ $t('common.retry') }}</el-button>
    </p>
    <template v-if="stats">
      <div class="account-metrics">
        <button v-for="key in ['users', 'families']" :key="key" type="button" class="metric" @click="openRecords(key)">
          <span>{{ $t('ops.stat.' + key) }}</span
          ><strong>{{ $n(stats[key]) }}</strong
          ><el-icon><ArrowRight /></el-icon>
        </button>
      </div>
      <div class="resource-groups">
        <details v-for="g in groups" :key="g.key" class="resource-group">
          <summary>
            <el-icon><ArrowRight /></el-icon><span>{{ $t('ops.statGroup.' + g.key) }}</span
            ><b>{{ $n(groupCount(g)) }}</b>
          </summary>
          <div class="resource-list">
            <button v-for="key in g.items" :key="key" type="button" @click="openRecords(key)">
              <span>{{ $t('ops.stat.' + key) }}</span
              ><b>{{ $n(stats[key]) }}</b
              ><el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </details>
      </div>
    </template>
  </section>
  <el-dialog
    v-model="dialog"
    append-to-body
    class="dialog-md"
    :title="$t('ops.resourceDetail', { name: selected ? $t('ops.stat.' + selected) : '' })"
  >
    <p class="scope">{{ recordScope || $t('ops.allRecords') }}</p>
    <div v-loading="recordsLoading">
      <p v-if="recordsFailed" role="alert">
        {{ $t('common.loadFailed') }} <el-button @click="loadPage(page)">{{ $t('common.retry') }}</el-button>
      </p>
      <el-table v-else :data="records" max-height="55vh" stripe :empty-text="$t('ops.emptyRecords')">
        <el-table-column prop="id" :label="$t('ops.recordId')" min-width="80">
          <template #default="{ row }">
            {{ row.id }}
            <template v-if="isMobile && hasOwner">
              <small>{{ $t('ops.recordUser') }} {{ row.userId ?? '—' }}</small>
              <small v-if="selected !== 'operationLogs'">{{ $t('ops.recordFamily') }} {{ row.familyId ?? '—' }}</small>
            </template>
          </template>
        </el-table-column>
        <el-table-column :label="$t('ops.createdTime')" min-width="152"
          ><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column
        >
        <el-table-column v-if="!isMobile && hasOwner" prop="userId" :label="$t('ops.recordUser')" min-width="84" />
        <el-table-column
          v-if="!isMobile && hasOwner && selected !== 'operationLogs'"
          prop="familyId"
          :label="$t('ops.recordFamily')"
          min-width="84"
        />
      </el-table>
    </div>
    <el-pagination
      v-if="total > size"
      :current-page="page"
      :page-size="size"
      :total="total"
      :pager-count="5"
      layout="prev, pager, next"
      @current-change="loadPage"
    />
  </el-dialog>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Filter } from '@element-plus/icons-vue'
import { opsApi } from '@/api'
import { formatDateTime } from '@/utils/datetime'
import { useDevice } from '@/composables/useDevice'

const { t } = useI18n()
const { isMobile } = useDevice()
const groups = [
  { key: 'content', items: ['blogs', 'diaries', 'albums', 'photos', 'videos', 'wishes'] },
  { key: 'interact', items: ['comments', 'likes'] },
  { key: 'family', items: ['checkins', 'plans', 'bookRecords', 'reminders'] },
  { key: 'report', items: ['operationLogs'] },
]
const groupCount = (g) => g.items.reduce((sum, key) => sum + stats.value[key], 0)
const stats = ref(null)
const loading = ref(false)
const failed = ref(false)
const showFilters = ref(false)
const draft = ref({})
const applied = ref({})
function describe(filter) {
  return [
    ['startDate', 'ops.startDate'],
    ['endDate', 'ops.endDate'],
    ['userId', 'ops.recordUser'],
    ['familyId', 'ops.recordFamily'],
  ]
    .filter(([key]) => filter[key])
    .map(([key, label]) => t(label) + ' ' + filter[key])
    .join(' · ')
}
const scope = computed(() => describe(applied.value))
let statsRequest = 0
async function loadStats() {
  const request = ++statsRequest
  const params = Object.fromEntries(Object.entries(draft.value).filter(([, v]) => v))
  loading.value = true
  failed.value = false
  try {
    const data = await opsApi.stats(params)
    if (request === statsRequest) {
      stats.value = data
      applied.value = params
    }
  } catch {
    if (request === statsRequest) {
      failed.value = true
      stats.value = null
    }
  } finally {
    if (request === statsRequest) loading.value = false
  }
}
function reset() {
  draft.value = {}
  loadStats()
}
const dialog = ref(false)
const selected = ref('')
const hasOwner = computed(() => !['users', 'families'].includes(selected.value))
const recordFilter = computed(() => {
  const params = { ...applied.value }
  if (!hasOwner.value) delete params.userId
  if (!hasOwner.value || selected.value === 'operationLogs') delete params.familyId
  return params
})
const recordScope = computed(() => describe(recordFilter.value))
const page = ref(1)
const size = 20
const records = ref([])
const total = ref(0)
const recordsLoading = ref(false)
const recordsFailed = ref(false)
let recordsRequest = 0
function openRecords(key) {
  selected.value = key
  total.value = stats.value[key]
  dialog.value = true
  loadPage(1)
}
async function loadPage(current) {
  const request = ++recordsRequest
  page.value = current
  records.value = []
  recordsLoading.value = true
  recordsFailed.value = false
  try {
    const data = await opsApi.resourceRecords({ ...recordFilter.value, kind: selected.value, current, size })
    if (request !== recordsRequest) return
    total.value = data.total
    records.value = data.records
  } catch {
    if (request === recordsRequest) recordsFailed.value = true
  } finally {
    if (request === recordsRequest) recordsLoading.value = false
  }
}
onMounted(loadStats)
</script>
<style scoped>
small {
  display: block;
  color: var(--color-text-secondary);
  line-height: 1.4;
}
.inventory {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  padding: clamp(14px, 3vw, 28px);
}
.inventory-head,
.account-metrics,
.resource-list button,
summary,
.resource-filters {
  display: flex;
  align-items: center;
}
.inventory-head {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.inventory-head .section-label {
  margin: 0;
}
.resource-filters {
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.resource-filters > :is(.el-input, .el-date-editor) {
  width: 155px;
  max-width: 100%;
}
.scope {
  color: var(--color-text-secondary);
  font-size: 13px;
  margin: 0 0 12px;
}
.account-metrics {
  gap: 16px;
  align-items: stretch;
  margin-bottom: 18px;
}
.metric,
.resource-list button {
  border: 0;
  font: inherit;
  cursor: pointer;
  color: var(--color-text);
  text-align: start;
}
.metric {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 4px;
  padding: 14px 18px;
  background: var(--el-color-primary-light-9);
  border-radius: 10px;
}
.metric span {
  color: var(--color-text-secondary);
  font-size: 13px;
}
.metric strong {
  grid-column: 1;
  font-size: clamp(23px, 3vw, 32px);
  font-variant-numeric: tabular-nums;
}
.metric .el-icon {
  grid-column: 2;
  grid-row: 1/3;
  color: var(--color-brand);
}
.resource-group {
  border-top: 1px solid var(--color-border);
}
summary {
  gap: 12px;
  padding: 15px 4px;
  cursor: pointer;
  list-style: none;
  font-weight: 600;
}
summary::-webkit-details-marker {
  display: none;
}
summary b,
.resource-list b {
  margin-inline-start: auto;
  font-variant-numeric: tabular-nums;
}
summary .el-icon {
  color: var(--color-text-secondary);
  font-size: 13px;
}
details[open] > summary .el-icon {
  transform: rotate(90deg);
}
.resource-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(215px, 100%), 1fr));
  gap: 5px 20px;
  padding: 0 0 14px 22px;
}
.resource-list button {
  padding: 8px;
  gap: 8px;
  background: transparent;
  border-radius: 8px;
}
.resource-list button:hover,
.metric:hover {
  background: var(--el-color-primary-light-8);
}
.resource-list .el-icon {
  font-size: 12px;
  color: var(--color-brand);
}
@media (max-width: 550px) {
  .account-metrics {
    gap: 8px;
  }
  .metric {
    padding: 12px;
  }
  .resource-list {
    padding-left: 8px;
  }
}
</style>
