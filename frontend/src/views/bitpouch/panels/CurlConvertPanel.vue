<!-- cURL 转换面板:cURL 命令 → fetch / axios / Python / Go / PowerShell。
     核心逻辑在 utils/tools/curlConvertTool,与 bit_pouch 内置插件同一套规则;
     只做词法解析,不执行命令、不发网络请求,命令原文与 token 不落存储。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.curl-convert.input') }}</span>
        <span>{{ $t('tools.curl-convert.inputCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="target" :options="targetOptions" />
        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.curl-convert.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply"
          @keydown.meta.enter.prevent="apply"
        ></textarea>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply">{{ $t('tools.curl-convert.run') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample('json')">{{ $t('tools.curl-convert.sampleJson') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample('form')">{{ $t('tools.curl-convert.sampleForm') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.curl-convert.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.curl-convert.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.curl-convert.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.curl-convert.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.curl-convert.emptyOutput') }}</pre>
        <div class="bpc-meta">
          <span v-if="summary">{{ summary }}</span>
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
import { SAMPLE_FORM, SAMPLE_JSON, run } from '@/utils/tools/curlConvertTool'

const { t, locale } = useI18n()

const target = ref('fetch')
const input = ref(SAMPLE_JSON)
const output = ref('')
const errorText = ref('')
const warnings = ref([])
const request = ref(null)

const targetOptions = computed(() => [
  { value: 'fetch', label: 'fetch' },
  { value: 'axios', label: 'axios' },
  { value: 'python', label: 'Python' },
  { value: 'go', label: 'Go' },
  { value: 'powershell', label: 'PowerShell' },
])

const summary = computed(() => {
  if (!request.value) return ''
  return t('tools.curl-convert.stats', {
    method: request.value.method,
    headers: request.value.headers.length,
    chars: output.value.length,
  })
})

function apply() {
  if (!input.value.trim()) {
    output.value = ''
    errorText.value = ''
    warnings.value = []
    request.value = null
    return
  }
  const result = run(input.value, { target: target.value }, { locale: locale.value })
  if (!result.ok) {
    output.value = ''
    errorText.value = result.error.message
    warnings.value = []
    request.value = null
    return
  }
  output.value = result.output
  errorText.value = ''
  warnings.value = result.data?.warnings || []
  request.value = result.data?.request || null
}

function loadSample(kind) {
  input.value = kind === 'form' ? SAMPLE_FORM : SAMPLE_JSON
}

function clearAll() {
  input.value = ''
  output.value = ''
  errorText.value = ''
  warnings.value = []
  request.value = null
}
</script>
