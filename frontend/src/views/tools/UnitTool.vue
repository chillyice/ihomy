<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.unit.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-select v-model="category" size="small" style="width: 150px" @change="onCategoryChange">
          <el-option v-for="id in CATEGORY_IDS" :key="id" :value="id" :label="$t(`tools.unit.categories.${id}`)" />
        </el-select>
      </div>
      <div class="tb-right">
        <span class="unit-hint">{{ $t('tools.unit.privacy') }}</span>
      </div>
    </PageToolbar>

    <div class="unit-grid">
      <div class="card unit-pane">
        <div class="section-label">{{ $t('tools.unit.input') }}</div>
        <el-input v-model="value" size="small" :placeholder="$t('tools.unit.valuePlaceholder')" />

        <div class="unit-row">
          <div class="unit-field">
            <label>{{ $t('tools.unit.from') }}</label>
            <el-select v-model="from" size="small">
              <el-option v-for="id in units" :key="`f-${id}`" :value="id" :label="unitLabel(id)" />
            </el-select>
          </div>
          <el-button size="small" :icon="Switch" circle :aria-label="$t('tools.unit.swap')" @click="swap" />
          <div class="unit-field">
            <label>{{ $t('tools.unit.to') }}</label>
            <el-select v-model="to" size="small">
              <el-option v-for="id in units" :key="`t-${id}`" :value="id" :label="unitLabel(id)" />
            </el-select>
          </div>
        </div>

        <div class="unit-result">
          <div class="unit-result-value">{{ resultText }}</div>
          <div v-if="error" class="unit-error">{{ error }}</div>
        </div>

        <p class="unit-note">{{ $t('tools.unit.note') }}</p>
      </div>

      <div class="card unit-pane">
        <div class="section-label">{{ $t('tools.unit.all') }}</div>
        <div v-if="rows.length" class="unit-table">
          <div v-for="row in rows" :key="row.unit" class="unit-tr" :class="{ 'is-current': row.unit === to }">
            <span class="unit-tr-label">{{ unitLabel(row.unit) }}</span>
            <code class="unit-tr-value">{{ row.text }}</code>
            <el-button size="small" text @click="copy(row.text)">{{ $t('tools.unit.copy') }}</el-button>
          </div>
        </div>
        <div v-else class="unit-empty">{{ $t('tools.unit.empty') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Switch } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { CATEGORY_IDS, convert, convertAll, formatNumber, unitsOf } from '@/utils/tools/unitTool'

const { t } = useI18n()

const category = ref('length')
const value = ref('1')
const from = ref('m')
const to = ref('km')

const units = computed(() => unitsOf(category.value))
const unitLabel = (id) => t(`tools.unit.units.${id}`)

const parsed = computed(() => {
  const number = Number(value.value)
  return Number.isFinite(number) ? number : null
})

const resultText = computed(() => {
  if (parsed.value === null) return ''
  try {
    return `${formatNumber(parsed.value)} ${unitLabel(from.value)} = ${formatNumber(convert(parsed.value, category.value, from.value, to.value))} ${unitLabel(to.value)}`
  } catch {
    return ''
  }
})

const error = computed(() => (parsed.value === null && value.value !== '' ? t('tools.unit.errNumber') : ''))

const rows = computed(() => {
  if (parsed.value === null) return []
  return convertAll(parsed.value, category.value, from.value).map((item) => ({
    unit: item.unit,
    text: `${formatNumber(item.value)} ${unitLabel(item.unit)}`,
  }))
})

const onCategoryChange = () => {
  const list = units.value
  from.value = list[0]
  to.value = list[1] || list[0]
}

const swap = () => {
  const previous = from.value
  from.value = to.value
  to.value = previous
}

const copy = async (text) => {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('tools.unit.copied'))
  } catch {
    ElMessage.error(t('tools.unit.copyFailed'))
  }
}
</script>

<style scoped>
.unit-hint {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.unit-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .unit-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.unit-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.unit-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
}
.unit-field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.unit-field label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.unit-result {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--color-bg-soft);
}
.unit-result-value {
  font-size: 18px;
  font-weight: 600;
  word-break: break-word;
}
.unit-error {
  color: var(--color-danger, #c0392b);
  font-size: 12px;
}
.unit-note {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.unit-table {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}
.unit-tr {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  font-size: 12.5px;
}
.unit-tr:nth-child(odd) {
  background: var(--color-bg-soft);
}
.unit-tr.is-current {
  box-shadow: inset 3px 0 0 var(--color-accent);
}
.unit-tr-label {
  flex: 0 0 110px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.unit-tr-value {
  flex: 1;
  min-width: 0;
  font-family: 'Cascadia Code', Consolas, monospace;
  word-break: break-all;
}
.unit-empty {
  padding: 24px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
</style>
