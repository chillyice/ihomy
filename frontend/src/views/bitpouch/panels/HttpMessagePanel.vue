<!-- HTTP 报文面板:解析原始请求/响应,或从 JSON 描述生成报文。
     核心逻辑在 utils/tools/httpMessageTool,与 bit_pouch 内置插件同一套规则;
     Cookie / Authorization 只在当前页面内存中,不落存储、不发网络请求。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.http-message.input') }}</span>
        <span>{{ $t('tools.http-message.inputCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />
        <BpcChips v-if="mode === 'parse'" v-model="kind" :options="kindOptions" />

        <div v-if="mode === 'build'" class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-http-le">{{ $t('tools.http-message.lineEnding') }}</label>
            <select id="bp-http-le" v-model="lineEnding" class="bpc-select">
              <option value="crlf">CRLF</option>
              <option value="lf">LF</option>
            </select>
          </div>
          <div class="bpc-field">
            <label class="bpc-label"><input v-model="autoLength" type="checkbox" /> {{ $t('tools.http-message.autoLength') }}</label>
          </div>
        </div>

        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="placeholder"
          @keydown.ctrl.enter.prevent="apply"
          @keydown.meta.enter.prevent="apply"
        ></textarea>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply">{{ $t('tools.http-message.run') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.http-message.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.http-message.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.http-message.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.http-message.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.http-message.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.http-message.emptyOutput') }}</pre>
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
import { SAMPLE_BUILD, SAMPLE_PARSE, run } from '@/utils/tools/httpMessageTool'

const { t, locale } = useI18n()

const mode = ref('parse')
const kind = ref('auto')
const lineEnding = ref('crlf')
const autoLength = ref(true)
const input = ref(SAMPLE_PARSE)
const output = ref('')
const errorText = ref('')
const warnings = ref([])
const stats = ref(null)

const modeOptions = computed(() => [
  { value: 'parse', label: t('tools.http-message.modeParse') },
  { value: 'build', label: t('tools.http-message.modeBuild') },
])

const kindOptions = computed(() => [
  { value: 'auto', label: t('tools.http-message.kindAuto') },
  { value: 'request', label: t('tools.http-message.kindRequest') },
  { value: 'response', label: t('tools.http-message.kindResponse') },
])

const placeholder = computed(() =>
  mode.value === 'build' ? t('tools.http-message.buildPlaceholder') : t('tools.http-message.parsePlaceholder'),
)

const statsText = computed(() => {
  if (!stats.value) return ''
  return Object.entries(stats.value)
    .map(([key, value]) => `${key}: ${value}`)
    .join(' · ')
})

watch(mode, (value) => {
  input.value = value === 'build' ? SAMPLE_BUILD : SAMPLE_PARSE
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
  const result = run(
    input.value,
    { mode: mode.value, kind: kind.value, lineEnding: lineEnding.value, autoContentLength: autoLength.value },
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
  stats.value = result.stats || null
}

function loadSample() {
  input.value = mode.value === 'build' ? SAMPLE_BUILD : SAMPLE_PARSE
}

function clearAll() {
  input.value = ''
  output.value = ''
  errorText.value = ''
  warnings.value = []
  stats.value = null
}
</script>
