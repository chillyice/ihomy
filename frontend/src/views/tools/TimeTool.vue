<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.time.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="convert">{{ $t('tools.time.modeConvert') }}</el-radio-button>
          <el-radio-button value="diff">{{ $t('tools.time.modeDiff') }}</el-radio-button>
          <el-radio-button value="add">{{ $t('tools.time.modeAdd') }}</el-radio-button>
          <el-radio-button value="cron">{{ $t('tools.time.modeCron') }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tb-right">
        <el-select v-model="timeZone" size="small" filterable style="width: 210px">
          <el-option v-for="zone in TIME_ZONES" :key="zone" :value="zone" :label="zone" />
        </el-select>
        <el-button size="small" @click="useNow">{{ $t('tools.time.now') }}</el-button>
        <el-button size="small" type="primary" @click="generate">{{ $t('tools.time.run') }}</el-button>
      </div>
    </PageToolbar>

    <div class="time-grid">
      <div class="card time-pane">
        <div class="section-label">{{ $t('tools.time.input') }}</div>

        <template v-if="mode === 'convert'">
          <el-input v-model="input" size="small" :placeholder="$t('tools.time.inputPlaceholder')" />
          <div class="time-field">
            <span class="time-label">{{ $t('tools.time.unit') }}</span>
            <el-radio-group v-model="unit" size="small">
              <el-radio-button value="auto">{{ $t('tools.time.unitAuto') }}</el-radio-button>
              <el-radio-button value="s">{{ $t('tools.time.unitS') }}</el-radio-button>
              <el-radio-button value="ms">{{ $t('tools.time.unitMs') }}</el-radio-button>
            </el-radio-group>
          </div>
          <p class="time-hint">{{ $t('tools.time.convertHint') }}</p>
        </template>

        <template v-else-if="mode === 'diff'">
          <textarea v-model="diffInput" class="time-area" spellcheck="false" :placeholder="$t('tools.time.diffPlaceholder')"></textarea>
          <p class="time-hint">{{ $t('tools.time.diffHint') }}</p>
        </template>

        <template v-else-if="mode === 'add'">
          <el-input v-model="input" size="small" :placeholder="$t('tools.time.inputPlaceholder')" />
          <div class="time-field">
            <span class="time-label">{{ $t('tools.time.amount') }}</span>
            <el-input-number v-model="amount" :min="0" :max="100000" size="small" controls-position="right" />
          </div>
          <div class="time-field">
            <span class="time-label">{{ $t('tools.time.addUnit') }}</span>
            <el-select v-model="addUnit" size="small" style="width: 130px">
              <el-option v-for="item in ADD_UNITS" :key="item.value" :value="item.value" :label="$t(item.labelKey)" />
            </el-select>
          </div>
          <div class="time-field">
            <span class="time-label">{{ $t('tools.time.direction') }}</span>
            <el-radio-group v-model="direction" size="small">
              <el-radio-button value="ahead">{{ $t('tools.time.directionAhead') }}</el-radio-button>
              <el-radio-button value="back">{{ $t('tools.time.directionBack') }}</el-radio-button>
            </el-radio-group>
          </div>
          <p class="time-hint">{{ $t('tools.time.addHint') }}</p>
        </template>

        <template v-else>
          <el-input v-model="cronInput" size="small" :placeholder="$t('tools.time.cronPlaceholder')" />
          <div class="time-field">
            <span class="time-label">{{ $t('tools.time.cronCount') }}</span>
            <el-input-number v-model="cronCount" :min="1" :max="20" size="small" controls-position="right" />
          </div>
          <div class="time-presets">
            <el-button v-for="preset in CRON_PRESETS" :key="preset" size="small" text @click="cronInput = preset">
              {{ preset }}
            </el-button>
          </div>
          <p class="time-hint">{{ $t('tools.time.cronHint') }}</p>
        </template>

        <p class="time-privacy">{{ $t('tools.time.privacy') }}</p>
      </div>

      <div class="card time-pane">
        <div class="section-label">{{ $t('tools.time.result') }}</div>
        <div v-if="error" class="time-error">{{ error }}</div>
        <div v-else-if="!result" class="time-empty">{{ $t('tools.time.empty') }}</div>

        <template v-else-if="mode === 'convert'">
          <div v-for="row in convertRows" :key="row.label" class="time-row">
            <div class="time-row-head">
              <span class="time-row-label">{{ row.label }}</span>
              <el-button size="small" text @click="copy(row.value)">{{ $t('tools.time.copy') }}</el-button>
            </div>
            <code class="time-value">{{ row.value }}</code>
          </div>
        </template>

        <template v-else-if="mode === 'diff'">
          <div class="time-big">
            {{ result.data.totalDays }} <span>{{ $t('tools.time.daysUnit') }}</span>
          </div>
          <div class="time-breakdown">
            <span>{{
              $t('tools.time.breakdown', { d: result.data.days, h: result.data.hours, m: result.data.minutes, s: result.data.seconds })
            }}</span>
          </div>
          <div class="time-row">
            <div class="time-row-head">
              <span class="time-row-label">{{ $t('tools.time.start') }}</span>
            </div>
            <code class="time-value">{{ result.data.start.zoned }}</code>
          </div>
          <div class="time-row">
            <div class="time-row-head">
              <span class="time-row-label">{{ $t('tools.time.end') }}</span>
            </div>
            <code class="time-value">{{ result.data.end.zoned }}</code>
          </div>
        </template>

        <template v-else-if="mode === 'add'">
          <div v-for="row in addRows" :key="row.label" class="time-row">
            <div class="time-row-head">
              <span class="time-row-label">{{ row.label }}</span>
              <el-button size="small" text @click="copy(row.value)">{{ $t('tools.time.copy') }}</el-button>
            </div>
            <code class="time-value">{{ row.value }}</code>
          </div>
        </template>

        <template v-else>
          <div v-for="(item, index) in result.data.next" :key="item.iso" class="time-cron-row">
            <span class="time-cron-index">{{ index + 1 }}</span>
            <div class="time-cron-main">
              <code class="time-value">{{ item.zoned }}</code>
              <span class="time-cron-meta">{{ item.weekday }} · {{ item.offset }}</span>
            </div>
            <el-button size="small" text @click="copy(item.iso)">{{ $t('tools.time.copy') }}</el-button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { TIME_ZONES, run } from '@/utils/tools/timeTool'

const { t, locale } = useI18n()

const ADD_UNITS = [
  { value: 'minute', labelKey: 'tools.time.unitMinute' },
  { value: 'hour', labelKey: 'tools.time.unitHour' },
  { value: 'day', labelKey: 'tools.time.unitDay' },
  { value: 'week', labelKey: 'tools.time.unitWeek' },
  { value: 'month', labelKey: 'tools.time.unitMonth' },
  { value: 'year', labelKey: 'tools.time.unitYear' },
]
const CRON_PRESETS = ['*/15 * * * *', '0 9 * * 1-5', '0 0 1 * *', '0 0 * * *']

const mode = ref('convert')
const timeZone = ref(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')
const input = ref(String(Math.floor(Date.now() / 1000)))
const unit = ref('auto')
const diffInput = ref(`${new Date().toISOString()}\n${new Date(Date.now() + 86_400_000).toISOString()}`)
const amount = ref(1)
const addUnit = ref('day')
const direction = ref('ahead')
const cronInput = ref('*/15 * * * *')
const cronCount = ref(5)

const result = ref(null)
const error = ref('')

let timer = null
let sequence = 0

const coreLocale = () => (locale.value?.startsWith('en') ? 'en' : 'zh')

const convertRows = computed(() => {
  if (!result.value?.ok) return []
  const data = result.value.data
  return [
    { label: t('tools.time.rows.seconds'), value: formatNumber(data.ms / 1000) },
    { label: t('tools.time.rows.milliseconds'), value: formatNumber(data.ms) },
    { label: t('tools.time.rows.iso'), value: data.iso },
    { label: t('tools.time.rows.utc'), value: data.utc },
    { label: t('tools.time.rows.zoned'), value: `${data.zoned} (${data.offset})` },
    { label: t('tools.time.rows.local'), value: data.local },
    { label: t('tools.time.rows.weekday'), value: data.weekday },
    { label: t('tools.time.rows.zone'), value: data.zone },
  ]
})

const addRows = computed(() => {
  if (!result.value?.ok) return []
  const data = result.value.data
  return [
    { label: t('tools.time.rows.iso'), value: data.iso },
    { label: t('tools.time.rows.utc'), value: data.utc },
    { label: t('tools.time.rows.zoned'), value: `${data.zoned} (${data.offset})` },
    { label: t('tools.time.rows.weekday'), value: data.weekday },
    { label: t('tools.time.rows.seconds'), value: formatNumber(data.ms / 1000) },
    { label: t('tools.time.rows.milliseconds'), value: formatNumber(data.ms) },
  ]
})

function formatNumber(value) {
  if (!Number.isFinite(value)) return ''
  return Number.isInteger(value) ? String(value) : String(Math.round(value * 1000) / 1000)
}

const generate = () => {
  const current = ++sequence
  const options = { timeZone: timeZone.value }
  let response
  if (mode.value === 'convert') response = run(input.value, { ...options, mode: 'convert', unit: unit.value }, { locale: coreLocale() })
  else if (mode.value === 'diff') response = run(diffInput.value, { ...options, mode: 'diff' }, { locale: coreLocale() })
  else if (mode.value === 'add') {
    response = run(
      input.value,
      { ...options, mode: 'add', amount: direction.value === 'back' ? -amount.value : amount.value, unit: addUnit.value },
      { locale: coreLocale() },
    )
  } else {
    response = run(cronInput.value, { ...options, mode: 'cron', count: cronCount.value }, { locale: coreLocale() })
  }
  if (current !== sequence) return
  if (response.ok) {
    result.value = response
    error.value = ''
  } else {
    result.value = null
    error.value = response.error.message
  }
}

const schedule = () => {
  clearTimeout(timer)
  timer = setTimeout(generate, 250)
}

watch([mode, timeZone, input, unit, diffInput, amount, addUnit, direction, cronInput, cronCount], schedule)

const useNow = () => {
  const now = Date.now()
  if (mode.value === 'diff') diffInput.value = `${new Date(now).toISOString()}\n${new Date(now + 86_400_000).toISOString()}`
  else if (mode.value === 'convert') input.value = String(Math.floor(now / 1000))
  else input.value = new Date(now).toISOString()
}

const copy = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('tools.time.copied'))
  } catch {
    ElMessage.error(t('tools.time.copyFailed'))
  }
}

generate()
onBeforeUnmount(() => clearTimeout(timer))
</script>

<style scoped>
.time-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .time-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.time-pane {
  display: flex;
  flex-direction: column;
  gap: 13px;
}
.time-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.time-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.time-area {
  width: 100%;
  min-height: 110px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-soft);
  color: var(--color-text);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
}
.time-area:focus {
  outline: none;
  border-color: var(--color-accent);
}
.time-hint,
.time-privacy {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.time-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
}
.time-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 9px 0;
  border-bottom: 1px solid var(--color-border);
}
.time-row:last-child {
  border-bottom: 0;
}
.time-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.time-row-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.time-value {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.55;
  word-break: break-all;
}
.time-big {
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 34px;
  font-weight: 600;
  color: var(--color-accent);
}
.time-big span {
  font-size: 14px;
  font-weight: 400;
  color: var(--color-text-secondary);
}
.time-breakdown {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.time-cron-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
}
.time-cron-row:last-child {
  border-bottom: 0;
}
.time-cron-index {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-bg-soft);
  color: var(--color-text-secondary);
  font-size: 11px;
}
.time-cron-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.time-cron-main .time-value {
  background: transparent;
  padding: 0;
}
.time-cron-meta {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.time-empty {
  padding: 24px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.time-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-danger-weak, rgba(220, 80, 80, 0.12));
  color: var(--color-danger, #c0392b);
  font-size: 13px;
}
</style>
