<!-- IP 段面板:CIDR 合并 / 范围互转 / 包含判断 / 排除。
     核心逻辑在 utils/tools/ipRangeTool,与 bit_pouch 内置插件同一套规则;
     IPv4 与 IPv6 都只在本机计算,不发起网络请求。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.ip-range.input') }}</span>
        <span>{{ $t('tools.ip-range.inputCount', { n: lines }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />
        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.ip-range.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply"
          @keydown.meta.enter.prevent="apply"
        ></textarea>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply">{{ $t('tools.ip-range.run') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.ip-range.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.ip-range.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.ip-range.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.ip-range.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.ip-range.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.ip-range.emptyOutput') }}</pre>
        <div class="bpc-meta">
          <span v-if="statsText">{{ statsText }}</span>
        </div>
        <p v-for="(warning, index) in warnings" :key="index" class="bpc-hint">· {{ warning }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { SAMPLE_CONTAINS, SAMPLE_EXCLUDE, SAMPLE_MERGE, SAMPLE_RANGE, run } from '@/utils/tools/ipRangeTool'

const { t, locale } = useI18n()

const mode = ref('merge')
const input = ref(SAMPLE_MERGE)
const output = ref('')
const errorText = ref('')
const warnings = ref([])
const stats = ref(null)

const modeOptions = computed(() => [
  { value: 'merge', label: t('tools.ip-range.modeMerge') },
  { value: 'range', label: t('tools.ip-range.modeRange') },
  { value: 'contains', label: t('tools.ip-range.modeContains') },
  { value: 'exclude', label: t('tools.ip-range.modeExclude') },
])

const SAMPLES = {
  merge: SAMPLE_MERGE,
  range: SAMPLE_RANGE,
  contains: SAMPLE_CONTAINS,
  exclude: SAMPLE_EXCLUDE,
}

const lines = computed(() => input.value.split(/\r?\n/).filter((line) => line.trim()).length)

const statsText = computed(() => {
  if (!stats.value) return ''
  return Object.entries(stats.value)
    .map(([key, value]) => `${key}: ${value}`)
    .join(' · ')
})

watch(mode, (value) => {
  input.value = SAMPLES[value] || ''
  output.value = ''
  errorText.value = ''
  warnings.value = []
  stats.value = null
})

function apply() {
  if (!input.value.trim()) {
    output.value = ''
    errorText.value = ''
    warnings.value = []
    stats.value = null
    return
  }
  const result = run(input.value, { mode: mode.value }, { locale: locale.value })
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
  stats.value = result.stats || null
}

function loadSample() {
  input.value = SAMPLES[mode.value] || ''
}

function clearAll() {
  input.value = ''
  output.value = ''
  errorText.value = ''
  warnings.value = []
  stats.value = null
}
</script>
