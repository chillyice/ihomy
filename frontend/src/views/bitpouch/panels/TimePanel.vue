<!-- 时间面板:时间戳 / 日期与时区互转,以及两个时刻的差值。时区交给 Intl 处理。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.time.input') }}</span>
        <span>{{ timeZoneLabel }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <textarea
          v-model="input"
          class="bpc-textarea bpc-short"
          spellcheck="false"
          :placeholder="mode === 'diff' ? $t('tools.time.diffPlaceholder') : $t('tools.time.inputPlaceholder')"
        ></textarea>

        <p class="bpc-hint">{{ mode === 'diff' ? $t('tools.time.diffHint') : $t('tools.time.convertHint') }}</p>

        <div v-if="mode === 'convert'" class="bpc-field">
          <span class="bpc-label">{{ $t('tools.time.unit') }}</span>
          <BpcChips v-model="unit" :options="unitOptions" />
        </div>

        <div class="bpc-field">
          <label class="bpc-label" for="bp-time-zone">{{ $t('bitPouch.tools.timezone') }}</label>
          <select id="bp-time-zone" v-model="timeZone" class="bpc-select">
            <option value="">{{ $t('bitPouch.tools.localZone') }}</option>
            <option v-for="zone in zones" :key="zone" :value="zone">{{ zone }}</option>
          </select>
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="useNow">{{ $t('tools.time.now') }}</button>
          <button class="bpc-btn" type="button" @click="input = ''">{{ $t('tools.json.clear') }}</button>
        </div>

        <p v-if="error" class="bpc-err">{{ error }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.time.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.time.result') }}</span>
        <BpcCopy :value="copyText" :label="$t('tools.time.copy')" />
      </div>
      <div class="bpc-card-bd">
        <div v-if="rows.length" class="bpc-rows">
          <div v-for="row in rows" :key="row.key" class="bpc-row">
            <span class="bpc-row-k">{{ row.label }}</span>
            <span class="bpc-row-v" :class="{ 'is-accent': row.key === 'iso' }">{{ row.value }}</span>
          </div>
        </div>
        <p v-else class="bpc-hint">{{ error || $t('tools.time.empty') }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { TIME_ZONES, run } from '@/utils/tools/timeTool'

const { t, locale } = useI18n()

const mode = ref('convert')
const unit = ref('auto')
const timeZone = ref('')
const input = ref(String(Math.floor(Date.now() / 1000)))
const result = ref(null)
const error = ref('')

const modeOptions = computed(() => [
  { value: 'convert', label: t('tools.time.modeConvert') },
  { value: 'diff', label: t('tools.time.modeDiff') },
])

const unitOptions = computed(() => [
  { value: 'auto', label: t('tools.time.unitAuto') },
  { value: 's', label: t('tools.time.unitS') },
  { value: 'ms', label: t('tools.time.unitMs') },
])

// 本机时区之外的常用项置顶,其余按 IANA 名称排序;完整列表用于需要精确时区的场景。
const zones = computed(() => {
  const local = Intl.DateTimeFormat().resolvedOptions().timeZone
  const pinned = ['UTC', 'Asia/Shanghai', 'Asia/Tokyo', 'Europe/London', 'Europe/Berlin', 'America/New_York', 'America/Los_Angeles']
  return [...new Set([local, ...pinned, ...TIME_ZONES].filter(Boolean))]
})

const timeZoneLabel = computed(() => timeZone.value || Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')

const runTool = () => {
  if (!input.value.trim()) {
    result.value = null
    error.value = ''
    return
  }
  const options =
    mode.value === 'diff' ? { mode: 'diff', timeZone: timeZone.value } : { mode: 'convert', unit: unit.value, timeZone: timeZone.value }
  const out = run(input.value, options, { locale: locale.value?.startsWith('en') ? 'en' : 'zh' })
  if (!out.ok) {
    result.value = null
    error.value = out.error.message
    return
  }
  error.value = ''
  result.value = out
}

watch([input, mode, unit, timeZone], runTool, { immediate: true })

const rows = computed(() => {
  if (!result.value) return []
  const data = result.value.data
  if (data.mode === 'diff') {
    return [
      { key: 'start', label: t('tools.time.start'), value: data.start.local },
      { key: 'end', label: t('tools.time.end'), value: data.end.local },
      {
        key: 'breakdown',
        label: t('tools.time.modeDiff'),
        value: t('tools.time.breakdown', { d: data.days, h: data.hours, m: data.minutes, s: data.seconds }),
      },
      { key: 'totalMs', label: 'ms', value: String(data.totalMs) },
    ]
  }
  return [
    { key: 'iso', label: 'ISO 8601', value: data.iso },
    { key: 'utc', label: 'UTC', value: data.utc },
    { key: 'zoned', label: data.zone, value: data.zoned },
    { key: 'offset', label: 'Offset', value: data.offset },
    { key: 'weekday', label: t('tools.time.modeConvert'), value: data.weekday },
    { key: 'seconds', label: t('tools.time.unitS'), value: String(data.seconds) },
  ]
})

const copyText = computed(() => rows.value.map((row) => `${row.label}: ${row.value}`).join('\n'))

const useNow = () => {
  mode.value = 'convert'
  input.value = String(Math.floor(Date.now() / 1000))
}
</script>
