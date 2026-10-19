<!-- JSON 格式化:格式化 / 压缩 / 键名排序。全部在本机浏览器完成,内容不上传服务器。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.json.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="tab" size="small">
          <el-radio-button value="format">{{ $t('tools.json.tabFormat') }}</el-radio-button>
          <el-radio-button value="convert">{{ $t('tools.json.tabConvert') }}</el-radio-button>
          <el-radio-button value="codegen">{{ $t('tools.json.tabCodegen') }}</el-radio-button>
        </el-radio-group>
        <template v-if="tab === 'format'">
          <span class="section-label">{{ $t('tools.json.indent') }}</span>
          <el-radio-group v-model="indent" size="small">
            <el-radio-button value="  ">{{ $t('tools.json.indent2') }}</el-radio-button>
            <el-radio-button value="    ">{{ $t('tools.json.indent4') }}</el-radio-button>
            <el-radio-button :value="'\t'">{{ $t('tools.json.indentTab') }}</el-radio-button>
          </el-radio-group>
          <el-checkbox v-model="sortKeys" size="small">{{ $t('tools.json.sortKeys') }}</el-checkbox>
        </template>
        <template v-else-if="tab === 'convert'">
          <el-select v-model="convertTo" size="small" style="width: 170px">
            <el-option value="yaml" :label="$t('tools.json.convert.yaml')" />
            <el-option value="fromYaml" :label="$t('tools.json.convert.fromYaml')" />
            <el-option value="csv" :label="$t('tools.json.convert.csv')" />
            <el-option value="fromCsv" :label="$t('tools.json.convert.fromCsv')" />
            <el-option value="xml" :label="$t('tools.json.convert.xml')" />
            <el-option value="fromXml" :label="$t('tools.json.convert.fromXml')" />
          </el-select>
        </template>
        <template v-else>
          <el-select v-model="codeTarget" size="small" style="width: 150px">
            <el-option value="ts" :label="$t('tools.json.codegen.ts')" />
            <el-option value="java" :label="$t('tools.json.codegen.java')" />
            <el-option value="sql" :label="$t('tools.json.codegen.sql')" />
          </el-select>
          <el-input
            v-if="codeTarget === 'java'"
            v-model="javaClass"
            size="small"
            style="width: 130px"
            :placeholder="$t('tools.json.codegen.className')"
          />
          <el-input
            v-if="codeTarget === 'sql'"
            v-model="sqlTable"
            size="small"
            style="width: 130px"
            :placeholder="$t('tools.json.codegen.tableName')"
          />
        </template>
      </div>
      <div class="tb-right">
        <template v-if="tab === 'format'">
          <el-button size="small" @click="loadSample">{{ $t('tools.json.sample') }}</el-button>
          <el-button size="small" :disabled="!input && !output" @click="clearAll">{{ $t('tools.json.clear') }}</el-button>
          <el-button size="small" :disabled="!output" @click="copyOutput">{{ $t('tools.json.copy') }}</el-button>
          <el-button size="small" :disabled="!input.trim()" @click="runFormat(true)">{{ $t('tools.json.minify') }}</el-button>
          <el-button size="small" type="primary" :icon="MagicStick" :disabled="!input.trim()" @click="runFormat(false)">
            {{ $t('tools.json.format') }}
          </el-button>
        </template>
        <template v-else>
          <el-button size="small" @click="loadSample">{{ $t('tools.json.sample') }}</el-button>
          <el-button size="small" :disabled="!input && !output" @click="clearAll">{{ $t('tools.json.clear') }}</el-button>
          <el-button size="small" :disabled="!output" @click="copyOutput">{{ $t('tools.json.copy') }}</el-button>
          <el-button size="small" type="primary" :disabled="!input.trim()" :loading="converting" @click="runConvert">
            {{ tab === 'codegen' ? $t('tools.json.codegen.generate') : $t('tools.json.convert.run') }}
          </el-button>
        </template>
      </div>
    </PageToolbar>

    <div class="json-grid">
      <div class="card json-pane">
        <div class="json-pane-head">
          <span class="section-label">{{ inputLabel }}</span>
          <span class="json-count">{{ $t('tools.json.inputCount', { n: input.length }) }}</span>
        </div>
        <textarea
          ref="inputEl"
          v-model="input"
          class="json-area json-input"
          spellcheck="false"
          autocomplete="off"
          autocapitalize="off"
          :placeholder="inputPlaceholder"
          @keydown="onKeydown"
        ></textarea>
      </div>

      <div class="card json-pane">
        <div class="json-pane-head">
          <span class="section-label">{{ $t('tools.json.output') }}</span>
          <span v-if="output && !error" class="json-count">{{ statsText }}</span>
        </div>

        <pre v-if="tab !== 'format' && convertError" class="json-area json-error"><code>{{ convertError }}</code></pre>
        <pre
          v-else-if="error"
          class="json-area json-error"
        ><code><span class="json-error-title">{{ errorText }}</span><span v-if="error.lineText" class="json-error-line">{{ errorLineText }}</span></code></pre>
        <pre
          v-else-if="output"
          class="json-area json-out"
        ><code v-if="tokens"><span v-for="(token, idx) in tokens" :key="idx" :class="'tk-' + token.type">{{ token.text }}</span></code><code v-else>{{ output }}</code></pre>
        <div v-else class="json-area json-empty">{{ $t('tools.json.emptyOutput') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  JSON_MAX_DEPTH,
  csvToJson,
  formatJson,
  jsonToCsv,
  jsonToXml,
  toJava,
  toSqlInsert,
  toTypeScript,
  tokenizeJson,
  xmlToJson,
} from '@/utils/jsonTools'

const { t } = useI18n()

// 缩进/排序偏好存本机,下次打开保持;JSON 内容本身不落任何存储(避免敏感数据残留)
const PREF_KEY = 'ihomy:tools:json:v1'
const INDENTS = ['  ', '    ', '\t']
const savedPrefs = (() => {
  try {
    return JSON.parse(localStorage.getItem(PREF_KEY)) || {}
  } catch {
    return {}
  }
})()

const inputEl = ref(null)
const input = ref('')
const output = ref('')
const error = ref(null)
const stats = ref(null)
const minified = ref(false)
const indent = ref(INDENTS.includes(savedPrefs.indent) ? savedPrefs.indent : '  ')
const sortKeys = ref(Boolean(savedPrefs.sortKeys))

const tab = ref('format')
const convertTo = ref('yaml')
const codeTarget = ref('ts')
const javaClass = ref('Root')
const sqlTable = ref('data')
const converting = ref(false)
const convertError = ref('')

const inputLabel = computed(() => {
  if (tab.value === 'codegen') return t('tools.json.input')
  if (tab.value === 'convert')
    return ['fromYaml', 'fromCsv', 'fromXml'].includes(convertTo.value) ? t('tools.json.convert.source') : t('tools.json.input')
  return t('tools.json.input')
})

const inputPlaceholder = computed(() => {
  if (tab.value === 'convert' && ['fromYaml', 'fromCsv', 'fromXml'].includes(convertTo.value))
    return t('tools.json.convert.sourcePlaceholder')
  return t('tools.json.inputPlaceholder')
})

watch(tab, () => {
  output.value = ''
  error.value = null
  convertError.value = ''
  stats.value = null
})

watch([indent, sortKeys], () => {
  try {
    localStorage.setItem(PREF_KEY, JSON.stringify({ indent: indent.value, sortKeys: sortKeys.value }))
  } catch {
    // 隐私模式下不落盘,本次会话内的选择仍然有效
  }
  if (output.value || error.value) applyFormat(minified.value)
})

// 超过这个长度不再逐 token 上色,避免几万个 span 拖慢渲染
const HIGHLIGHT_LIMIT = 200_000
const tokens = computed(() => {
  if (tab.value !== 'format') return null
  if (!output.value || output.value.length > HIGHLIGHT_LIMIT) return null
  return tokenizeJson(output.value)
})

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

const errorText = computed(() => {
  if (!error.value) return ''
  const e = error.value
  const key = ERROR_KEYS[e.code] || 'invalid'
  return t(`tools.json.errors.${key}`, { line: e.line, column: e.column, char: e.char, max: JSON_MAX_DEPTH })
})

const errorLineText = computed(() => (error.value?.lineText ? `${error.value.line} │ ${error.value.lineText}` : ''))

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const statsText = computed(() =>
  stats.value ? t('tools.json.stats', { lines: stats.value.lines, chars: stats.value.chars, size: formatSize(stats.value.bytes) }) : '',
)

const applyFormat = (minifyMode) => {
  const result = formatJson(input.value, { indent: minifyMode ? '' : indent.value, sortKeys: sortKeys.value })
  if (!result.ok) {
    output.value = ''
    stats.value = null
    error.value = result.error
    return false
  }
  output.value = result.text
  stats.value = result.stats
  error.value = null
  minified.value = minifyMode
  return true
}

const runFormat = (minifyMode = false) => {
  if (!input.value.trim()) {
    ElMessage.warning(t('tools.json.empty'))
    return
  }
  applyFormat(minifyMode)
}

// 数据转换与代码生成:YAML 依赖 js-yaml,只在真正转换时动态加载,不进入首屏
const runConvert = async () => {
  if (!input.value.trim()) {
    ElMessage.warning(t('tools.json.empty'))
    return
  }
  convertError.value = ''
  stats.value = null
  converting.value = true
  try {
    let text
    if (tab.value === 'codegen') {
      if (codeTarget.value === 'ts') text = toTypeScript(input.value)
      else if (codeTarget.value === 'java') text = toJava(input.value, { className: javaClass.value || 'Root' })
      else text = toSqlInsert(input.value, { table: sqlTable.value || 'data' })
    } else if (convertTo.value === 'yaml' || convertTo.value === 'fromYaml') {
      const yaml = await import('js-yaml')
      if (convertTo.value === 'yaml') text = yaml.dump(JSON.parse(input.value), { noRefs: true, lineWidth: 100 })
      else text = JSON.stringify(yaml.load(input.value), null, 2)
    } else if (convertTo.value === 'csv') {
      text = jsonToCsv(input.value)
    } else if (convertTo.value === 'fromCsv') {
      text = csvToJson(input.value)
    } else if (convertTo.value === 'xml') {
      text = jsonToXml(input.value)
    } else {
      text = xmlToJson(input.value)
    }
    output.value = text
    error.value = null
  } catch (e) {
    output.value = ''
    convertError.value = e?.message && e.message !== e.code ? e.message : t(`tools.json.convertErrors.${e?.code || 'invalid'}`)
  } finally {
    converting.value = false
  }
}

const clearAll = () => {
  input.value = ''
  output.value = ''
  error.value = null
  stats.value = null
  inputEl.value?.focus()
}

const copyOutput = async () => {
  try {
    await navigator.clipboard.writeText(output.value)
    ElMessage.success(t('tools.json.copied'))
  } catch {
    ElMessage.error(t('tools.json.copyFailed'))
  }
}

// 示例直接写成 JSON 文本:长整数 id 若先经过 JS 数字字面量,精度在粘贴前就已经丢了
const SAMPLE_JSON = `{
  "family": "ihomy",
  "members": [
    { "name": "阿岚", "role": "OWNER", "points": 1280, "joinedAt": "2024-03-01" },
    { "name": "小满", "role": "MEMBER", "points": 960, "joinedAt": "2024-05-18" }
  ],
  "modules": { "blog": true, "album": true, "cinema": false },
  "meta": { "id": 12345678901234567890, "version": 10.36, "tags": ["家庭", "共享"] }
}`

const loadSample = () => {
  input.value = SAMPLE_JSON
  output.value = ''
  error.value = null
  stats.value = null
  nextTick(() => inputEl.value?.focus())
}

// Tab 插入缩进(Shift+Tab 保持默认行为以便移出焦点),Ctrl/Cmd+Enter 直接格式化
const onKeydown = (event) => {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    runFormat(false)
    return
  }
  if (event.key !== 'Tab' || event.shiftKey) return
  event.preventDefault()
  const el = inputEl.value
  if (!el) return
  const pad = indent.value
  const start = el.selectionStart
  const end = el.selectionEnd
  input.value = input.value.slice(0, start) + pad + input.value.slice(end)
  nextTick(() => {
    el.selectionStart = el.selectionEnd = start + pad.length
  })
}
</script>

<style scoped>
.json-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}
.json-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.json-pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.json-pane-head .section-label {
  margin: 0;
}
.json-count {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.json-area {
  margin: 10px 0 0;
  width: 100%;
  min-height: 420px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  tab-size: 2;
}
.json-input {
  display: block;
  resize: vertical;
  outline: none;
}
.json-input:focus {
  border-color: var(--color-accent);
}
.json-input::placeholder {
  color: var(--color-text-secondary);
}
pre.json-area {
  white-space: pre;
  overflow: auto;
}
.json-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border-style: dashed;
  color: var(--color-text-secondary);
  font-family: inherit;
  font-size: 13px;
  text-align: center;
}
.json-error {
  border-color: rgba(168, 72, 58, 0.35);
  font-family: inherit;
}
.json-error code {
  display: block;
  font-family: inherit;
  white-space: pre-wrap;
}
.json-error-title {
  display: block;
  color: var(--color-accent);
  font-weight: 600;
}
.json-error-line {
  display: block;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--color-border);
  color: var(--color-text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  overflow-wrap: anywhere;
}
.json-out code {
  font-family: inherit;
}
.tk-key {
  color: #a8483a;
}
.tk-string {
  color: #3f7d4e;
}
.tk-number {
  color: #a8612c;
}
.tk-boolean {
  color: #6b4fa0;
}
.tk-null {
  color: var(--color-text-secondary);
}
.tk-punct {
  color: var(--color-text-secondary);
}
html.dark .tk-key {
  color: #e19a8c;
}
html.dark .tk-string {
  color: #8fce9f;
}
html.dark .tk-number {
  color: #e0aa72;
}
html.dark .tk-boolean {
  color: #b9a3e3;
}
html.dark .tk-null,
html.dark .tk-punct {
  color: #9a927f;
}
@media (max-width: 900px) {
  .json-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .json-area {
    min-height: 260px;
  }
}
</style>
