<!-- JSON 面板:格式化 / 压缩 / 键名排序。解析用 utils/jsonTools 的自写 token 解析器,
     保留长整数与重复键的原始字面量;内容只在内存里,不落任何存储。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.json.input') }}</span>
        <span>{{ $t('tools.json.inputCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.json.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply(false)"
          @keydown.meta.enter.prevent="apply(false)"
        ></textarea>

        <div class="bpc-inline">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-json-indent">{{ $t('tools.json.indent') }}</label>
            <select id="bp-json-indent" v-model="indent" class="bpc-select">
              <option value="  ">{{ $t('tools.json.indent2') }}</option>
              <option value="    ">{{ $t('tools.json.indent4') }}</option>
              <option :value="'\t'">{{ $t('tools.json.indentTab') }}</option>
            </select>
          </div>
          <div class="bpc-field">
            <span class="bpc-label">{{ $t('tools.json.sortKeys') }}</span>
            <button class="bpc-chip" :class="{ 'is-on': sortKeys }" type="button" :aria-pressed="sortKeys" @click="toggleSort">
              {{ $t('tools.json.sortKeys') }}
            </button>
          </div>
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply(false)">{{ $t('tools.json.format') }}</button>
          <button class="bpc-btn" type="button" @click="apply(true)">{{ $t('tools.json.minify') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.json.sample') }}</button>
          <button class="bpc-btn" type="button" @click="reset">{{ $t('tools.json.clear') }}</button>
        </div>

        <p v-if="error" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.json.inputPlaceholder') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.json.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.json.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.json.emptyOutput') }}</pre>
        <div class="bpc-meta">
          <span v-if="stats">{{ statsText }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { JSON_MAX_DEPTH, formatJson } from '@/utils/jsonTools'

const { t } = useI18n()

const SAMPLE = '{"id":1,"name":"bit_pouch","tags":["local","offline"],"plugin":{"enabled":true,"bytes":9007199254740993}}'
const ERROR_KEYS = {
  empty: 'empty',
  unexpectedChar: 'unexpectedChar',
  unexpectedEnd: 'unexpectedEnd',
  unterminatedString: 'unterminatedString',
  invalidEscape: 'invalidEscape',
  invalidNumber: 'invalidNumber',
  expectedKey: 'expectedKey',
  expectedColon: 'expectedColon',
  expectedComma: 'expectedComma',
  trailingComma: 'trailingComma',
  trailingContent: 'trailingContent',
  tooDeep: 'tooDeep',
}

const input = ref('')
const output = ref('')
const stats = ref(null)
const error = ref(null)
const indent = ref('  ')
const sortKeys = ref(false)
let minified = false

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const statsText = computed(() =>
  stats.value ? t('tools.json.stats', { lines: stats.value.lines, chars: stats.value.chars, size: formatSize(stats.value.bytes) }) : '',
)

const errorText = computed(() => {
  if (!error.value) return ''
  const key = ERROR_KEYS[error.value.code] || 'invalid'
  return t(`tools.json.errors.${key}`, {
    line: error.value.line,
    column: error.value.column,
    char: error.value.char,
    max: JSON_MAX_DEPTH,
  })
})

const apply = (minify) => {
  if (!input.value.trim()) {
    output.value = ''
    stats.value = null
    error.value = { code: 'empty', line: 0, column: 0, char: '' }
    return
  }
  const result = formatJson(input.value, { indent: minify ? '' : indent.value, sortKeys: sortKeys.value })
  if (!result.ok) {
    output.value = ''
    stats.value = null
    error.value = result.error
    return
  }
  output.value = result.text
  stats.value = result.stats
  error.value = null
  minified = minify
}

const toggleSort = () => {
  sortKeys.value = !sortKeys.value
  if (output.value || error.value) apply(minified)
}

const loadSample = () => {
  input.value = SAMPLE
  apply(false)
}

const reset = () => {
  input.value = ''
  output.value = ''
  stats.value = null
  error.value = null
}
</script>
