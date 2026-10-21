<!-- 格式化面板:JSON / XML / HTML / SVG 统一入口,自动识别格式。
     解析由 utils/jsonTools 与 utils/tools/markupTool 完成,
     分派逻辑在 utils/tools/formatTool,与 bit_pouch 内置 format 插件一致。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.format.input') }}</span>
        <span>{{ $t('tools.format.inputCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <div class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-format-indent">{{ $t('tools.format.indent') }}</label>
            <select id="bp-format-indent" v-model="indent" class="bpc-select">
              <option value="  ">{{ $t('tools.format.indent2') }}</option>
              <option value="    ">{{ $t('tools.format.indent4') }}</option>
              <option :value="'\t'">{{ $t('tools.format.indentTab') }}</option>
            </select>
          </div>
          <div v-if="mode === 'auto' || mode === 'json'" class="bpc-field">
            <span class="bpc-label">{{ $t('tools.format.sortKeys') }}</span>
            <button class="bpc-chip" :class="{ 'is-on': sortKeys }" type="button" :aria-pressed="sortKeys" @click="toggleSort">
              {{ $t('tools.format.sortKeys') }}
            </button>
          </div>
        </div>

        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.format.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply(false)"
          @keydown.meta.enter.prevent="apply(false)"
        ></textarea>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply(false)">{{ $t('tools.format.format') }}</button>
          <button class="bpc-btn" type="button" @click="apply(true)">{{ $t('tools.format.minify') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.format.sample') }}</button>
          <button class="bpc-btn" type="button" @click="reset">{{ $t('tools.format.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.format.inputPlaceholder') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.format.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.format.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.format.emptyOutput') }}</pre>
        <div class="bpc-meta">
          <span v-if="statsText">{{ statsText }}</span>
          <span v-if="tagText"> · {{ tagText }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { MARKUP_MAX_INPUT, SAMPLE_HTML, SAMPLE_XML } from '@/utils/tools/markupTool'
import { formatAny } from '@/utils/tools/formatTool'

const { t } = useI18n()

const SAMPLE_JSON = '{"id":1,"name":"bit_pouch","tags":["local","offline"],"plugin":{"enabled":true,"bytes":9007199254740993}}'

const JSON_ERROR_KEYS = {
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

const MARKUP_ERROR_KEYS = {
  empty: 'empty',
  tooLarge: 'tooLarge',
  tooDeep: 'tooDeep',
  unterminatedComment: 'unterminatedComment',
  unterminatedCdata: 'unterminatedCdata',
  unterminatedTag: 'unterminatedTag',
  unterminatedDeclaration: 'unterminatedDeclaration',
  invalidTag: 'invalidTag',
  unclosedTag: 'unclosedTag',
  mismatchedTag: 'mismatchedTag',
  strayCloseTag: 'strayCloseTag',
}

const mode = ref('auto')
const indent = ref('  ')
const sortKeys = ref(false)
const input = ref('')
const output = ref('')
const stats = ref(null)
const error = ref(null)
let minified = false

const modeOptions = computed(() => [
  { value: 'auto', label: t('tools.format.modeAuto') },
  { value: 'json', label: 'JSON' },
  { value: 'xml', label: 'XML' },
  { value: 'html', label: 'HTML' },
])

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const errorText = computed(() => {
  if (!error.value) return ''
  const value = error.value
  if (value.kind === 'json') {
    const key = JSON_ERROR_KEYS[value.code] || 'invalid'
    return t(`tools.json.errors.${key}`, {
      line: value.line,
      column: value.column,
      char: value.char,
      max: 200,
    })
  }
  const key = MARKUP_ERROR_KEYS[value.code] || 'invalid'
  return t(`tools.markup.errors.${key}`, {
    line: value.line,
    column: value.column,
    char: value.char,
    detail: value.detail || '',
    max: MARKUP_MAX_INPUT,
  })
})

const statsText = computed(() =>
  stats.value
    ? t('tools.format.stats', {
        mode: stats.value.mode.toUpperCase(),
        lines: stats.value.lines,
        chars: stats.value.chars,
        size: formatSize(stats.value.bytes),
      })
    : '',
)

const tagText = computed(() => (stats.value && stats.value.tags !== undefined ? t('tools.format.tags', { n: stats.value.tags }) : ''))

const apply = (minify) => {
  if (!input.value.trim()) {
    output.value = ''
    stats.value = null
    error.value = { kind: 'markup', code: 'empty', line: 0, column: 0, char: '' }
    return
  }
  const result = formatAny(input.value, {
    mode: mode.value,
    indent: indent.value,
    minify,
    sortKeys: sortKeys.value,
  })
  if (!result.ok) {
    output.value = ''
    stats.value = null
    error.value = { ...result.error, kind: result.kind }
    return
  }
  output.value = result.output
  stats.value = { ...result.stats, mode: result.mode }
  error.value = null
  minified = minify
}

const toggleSort = () => {
  sortKeys.value = !sortKeys.value
  if (output.value || error.value) apply(minified)
}

const loadSample = () => {
  if (mode.value === 'xml') input.value = SAMPLE_XML
  else if (mode.value === 'html') input.value = SAMPLE_HTML
  else input.value = SAMPLE_JSON
  apply(false)
}

const reset = () => {
  input.value = ''
  output.value = ''
  stats.value = null
  error.value = null
}

watch([mode, indent], () => {
  if (output.value) apply(minified)
})
</script>
