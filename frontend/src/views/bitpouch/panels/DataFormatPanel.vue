<!-- 数据格式面板:YAML / TOML / INI / .env / Properties 与 JSON 互转。
     核心逻辑在 utils/tools/dataFormatTool,与 bit_pouch 内置插件同一套规则;
     内容只在内存里,不落任何存储,也不发起网络请求。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.data-format.input') }}</span>
        <span>{{ $t('tools.data-format.inputCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />
        <BpcChips v-model="format" :options="formatOptions" />

        <div class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-df-target">{{ $t('tools.data-format.target') }}</label>
            <select id="bp-df-target" v-model="target" class="bpc-select" :disabled="mode !== 'fromJson'">
              <option v-for="opt in targetOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-df-indent">{{ $t('tools.data-format.indent') }}</label>
            <select id="bp-df-indent" v-model="indent" class="bpc-select">
              <option value="2">2</option>
              <option value="4">4</option>
              <option value="tab">Tab</option>
            </select>
          </div>
        </div>

        <div class="bpc-inline">
          <label class="bpc-label"><input v-model="flatten" type="checkbox" /> {{ $t('tools.data-format.flatten') }}</label>
          <label class="bpc-label"><input v-model="sortKeys" type="checkbox" /> {{ $t('tools.data-format.sortKeys') }}</label>
        </div>

        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.data-format.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply"
          @keydown.meta.enter.prevent="apply"
        ></textarea>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply">{{ $t('tools.data-format.run') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.data-format.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.data-format.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.data-format.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.data-format.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.data-format.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.data-format.emptyOutput') }}</pre>
        <div class="bpc-meta">
          <span v-if="statsText">{{ statsText }}</span>
        </div>
        <p v-for="(warning, index) in warnings" :key="index" class="bpc-hint">· {{ warning }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { SAMPLE_ENV, SAMPLE_INI, SAMPLE_JSON, SAMPLE_PROPERTIES, SAMPLE_TOML, SAMPLE_YAML, run } from '@/utils/tools/dataFormatTool'

const { t, locale } = useI18n()

const mode = ref('toJson')
const format = ref('auto')
const target = ref('yaml')
const indent = ref('2')
const flatten = ref(true)
const sortKeys = ref(false)
const input = ref(SAMPLE_YAML)
const output = ref('')
const errorText = ref('')
const warnings = ref([])
const stats = ref(null)

const modeOptions = computed(() => [
  { value: 'toJson', label: t('tools.data-format.modeToJson') },
  { value: 'fromJson', label: t('tools.data-format.modeFromJson') },
])

const formatOptions = computed(() => [
  { value: 'auto', label: t('tools.data-format.auto') },
  { value: 'json', label: 'JSON' },
  { value: 'yaml', label: 'YAML' },
  { value: 'toml', label: 'TOML' },
  { value: 'ini', label: 'INI' },
  { value: 'env', label: '.env' },
  { value: 'properties', label: 'Properties' },
])

const targetOptions = computed(() => formatOptions.value.filter((option) => option.value !== 'auto'))

const SAMPLES = {
  json: SAMPLE_JSON,
  yaml: SAMPLE_YAML,
  toml: SAMPLE_TOML,
  ini: SAMPLE_INI,
  env: SAMPLE_ENV,
  properties: SAMPLE_PROPERTIES,
}

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const statsText = computed(() => {
  const value = stats.value
  if (!value) return ''
  if (value.convert)
    return `${value.convert} · ${t('tools.data-format.stats', { keys: value.keys, chars: value.chars, size: formatSize(value.bytes || 0) })}`
  return Object.entries(value)
    .map(([key, item]) => `${key}: ${item}`)
    .join(' · ')
})

async function apply() {
  if (!input.value.trim()) {
    output.value = ''
    errorText.value = ''
    warnings.value = []
    stats.value = null
    return
  }
  const result = await run(
    input.value,
    {
      mode: mode.value,
      format: format.value,
      target: target.value,
      indent: indent.value,
      flatten: flatten.value,
      sortKeys: sortKeys.value,
    },
    { locale: locale.value },
  )
  if (!result.ok) {
    output.value = ''
    errorText.value = result.error.message
    warnings.value = []
    stats.value = null
    return
  }
  output.value = result.output
  errorText.value = ''
  warnings.value = result.data?.warnings || []
  stats.value = {
    convert: `${result.data.sourceFormat} → ${result.data.targetFormat}`,
    keys: result.data.keyCount,
    chars: result.stats?.chars ?? result.output.length,
    bytes: result.stats?.bytes ?? 0,
  }
}

function loadSample() {
  if (mode.value === 'fromJson') {
    input.value = SAMPLE_JSON
    return
  }
  input.value = SAMPLES[format.value] || SAMPLE_YAML
}

function clearAll() {
  input.value = ''
  output.value = ''
  errorText.value = ''
  warnings.value = []
  stats.value = null
}
</script>
