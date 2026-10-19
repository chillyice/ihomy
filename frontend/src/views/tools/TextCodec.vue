<!-- 字符编码转换:Unicode 转义 / HTML 实体 / URL / Base64 / UTF-8 字节 / 逐字码位,全部在本机完成 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.codec.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="encode">{{ $t('tools.codec.modeEncode') }}</el-radio-button>
          <el-radio-button value="decode">{{ $t('tools.codec.modeDecode') }}</el-radio-button>
          <el-radio-button value="repair">{{ $t('tools.codec.modeRepair') }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tb-right">
        <el-button size="small" @click="loadSample">{{ $t('tools.codec.sample') }}</el-button>
        <el-button size="small" :disabled="!input" @click="clearAll">{{ $t('tools.codec.clear') }}</el-button>
        <el-button v-if="mode === 'repair'" size="small" type="primary" :disabled="!input" :loading="repairing" @click="doRepair">
          {{ $t('tools.codec.repair.run') }}
        </el-button>
        <el-button v-else-if="mode === 'encode'" size="small" :disabled="!input" @click="copyAll">
          {{ $t('tools.codec.copyAll') }}
        </el-button>
        <el-button v-else size="small" :disabled="!decoded.text" @click="copy(decoded.text)">
          {{ $t('tools.codec.copy') }}
        </el-button>
      </div>
    </PageToolbar>

    <div class="codec-grid">
      <div class="card codec-pane">
        <div class="codec-pane-head">
          <span class="section-label">
            {{
              mode === 'encode'
                ? $t('tools.codec.original')
                : mode === 'decode'
                  ? $t('tools.codec.encoded')
                  : $t('tools.codec.repair.input')
            }}
          </span>
          <span class="codec-meta">{{ $t('tools.codec.charCount', { n: charCount }) }}</span>
        </div>

        <div v-if="mode === 'decode'" class="codec-format">
          <label for="codec-format">{{ $t('tools.codec.format') }}</label>
          <el-select id="codec-format" v-model="decodeFormat" size="small">
            <el-option v-for="key in FORMATS" :key="key" :value="key" :label="$t(`tools.codec.formats.${key}`)" />
          </el-select>
        </div>

        <textarea
          v-model="input"
          class="codec-area"
          spellcheck="false"
          autocomplete="off"
          autocapitalize="off"
          :placeholder="
            mode === 'encode'
              ? $t('tools.codec.inputPlaceholder')
              : mode === 'decode'
                ? $t('tools.codec.decodePlaceholder')
                : $t('tools.codec.repair.placeholder')
          "
        ></textarea>
        <div class="codec-hint">{{ $t('tools.codec.privacy') }}</div>
        <div v-if="mode === 'repair'" class="codec-hint">{{ $t('tools.codec.repair.hint') }}</div>
      </div>

      <div class="card codec-pane">
        <div class="codec-pane-head">
          <span class="section-label">
            {{
              mode === 'encode'
                ? $t('tools.codec.results')
                : mode === 'decode'
                  ? $t('tools.codec.decoded')
                  : $t('tools.codec.repair.result')
            }}
          </span>
        </div>

        <div v-if="mode === 'encode'" class="codec-results">
          <div v-for="row in encodeRows" :key="row.key" class="codec-row">
            <div class="codec-row-head">
              <span class="codec-label">{{ $t(`tools.codec.formats.${row.key}`) }}</span>
              <el-button size="small" text :disabled="!row.value" @click="copy(row.value)">
                {{ $t('tools.codec.copy') }}
              </el-button>
            </div>
            <code class="codec-value">{{ row.value || '—' }}</code>
          </div>
        </div>

        <div v-else-if="mode === 'repair'" class="codec-results">
          <div v-if="!repairResults.length" class="codec-area codec-empty">{{ $t('tools.codec.repair.empty') }}</div>
          <div v-for="(row, index) in repairResults" :key="index" class="codec-row">
            <div class="codec-row-head">
              <span class="codec-label">{{ row.label }}</span>
              <el-button size="small" text @click="useRepair(row.text)">{{ $t('tools.codec.repair.apply') }}</el-button>
            </div>
            <code class="codec-value">{{ row.text }}</code>
          </div>
        </div>

        <template v-else>
          <pre v-if="decoded.error" class="codec-area codec-error">{{ $t(`tools.codec.errors.${decoded.error}`) }}</pre>
          <pre v-else-if="decoded.text" class="codec-area codec-out">{{ decoded.text }}</pre>
          <div v-else class="codec-area codec-empty">{{ $t('tools.codec.emptyDecoded') }}</div>
        </template>
      </div>
    </div>

    <div v-if="mode === 'encode' && charTable.items.length" class="card codec-table-card">
      <div class="codec-table-head">
        <span class="section-label">{{ $t('tools.codec.charsTitle') }}</span>
        <span class="codec-meta">{{ $t('tools.codec.charsHint') }}</span>
      </div>
      <div class="codec-table-wrap">
        <table class="codec-table">
          <thead>
            <tr>
              <th>{{ $t('tools.codec.cols.char') }}</th>
              <th>{{ $t('tools.codec.cols.codePoint') }}</th>
              <th>{{ $t('tools.codec.cols.decimal') }}</th>
              <th>{{ $t('tools.codec.cols.utf8') }}</th>
              <th>{{ $t('tools.codec.cols.entity') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, idx) in charTable.items" :key="idx">
              <td class="codec-cell-char">{{ row.char }}</td>
              <td>
                <code>{{ row.codePoint }}</code>
              </td>
              <td>
                <code>{{ row.dec }}</code>
              </td>
              <td>
                <code>{{ row.utf8 }}</code>
              </td>
              <td>
                <code>{{ row.entity }}</code>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="charTable.truncated" class="codec-hint">
        {{ $t('tools.codec.truncated', { n: charTable.items.length }) }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  CODEC_FORMATS,
  decodeText,
  describeChars,
  repairMojibake,
  toBase64,
  toHtmlEntities,
  toUnicodeEscapes,
  toUrlEncoded,
  toUtf8Hex,
} from '@/utils/textCodec'
import { toFullWidth, toHalfWidth } from '@/utils/tools/textTool'

const { t } = useI18n()

const FORMATS = CODEC_FORMATS
// 偏好只存本机,下次打开保持;内容本身不落任何存储
const PREF_KEY = 'ihomy:tools:codec:v1'
const saved = (() => {
  try {
    return JSON.parse(localStorage.getItem(PREF_KEY)) || {}
  } catch {
    return {}
  }
})()

const input = ref('')
const mode = ref(['decode', 'repair'].includes(saved.mode) ? saved.mode : 'encode')
const decodeFormat = ref(FORMATS.includes(saved.decodeFormat) ? saved.decodeFormat : 'unicode')
const repairResults = ref([])
const repairing = ref(false)

const savePrefs = () => {
  try {
    localStorage.setItem(PREF_KEY, JSON.stringify({ mode: mode.value, decodeFormat: decodeFormat.value }))
  } catch {
    // 隐私模式下不落盘,本次会话内的选择仍然有效
  }
}
watch([mode, decodeFormat], savePrefs)

// 按码位计数,emoji 与大多数汉字一样算 1 个字符
const charCount = computed(() => Array.from(input.value).length)

const encodeRows = computed(() => {
  const text = input.value
  return [
    { key: 'unicode', value: text ? toUnicodeEscapes(text) : '' },
    { key: 'html', value: text ? toHtmlEntities(text) : '' },
    { key: 'url', value: text ? toUrlEncoded(text) : '' },
    { key: 'base64', value: text ? toBase64(text) : '' },
    { key: 'hex', value: text ? toUtf8Hex(text) : '' },
    { key: 'half', value: text ? toHalfWidth(text) : '' },
    { key: 'full', value: text ? toFullWidth(text) : '' },
  ]
})

const decoded = computed(() => (input.value ? decodeText(decodeFormat.value, input.value) : { ok: true, text: '' }))

const charTable = computed(() => (input.value ? describeChars(input.value) : { items: [], total: 0, truncated: false }))

// 反查编码表要枚举双字节组合,放到按钮后执行,并让出一帧先渲染 loading
const doRepair = async () => {
  if (!input.value) return
  repairing.value = true
  repairResults.value = []
  await new Promise((resolve) => setTimeout(resolve, 30))
  try {
    repairResults.value = repairMojibake(input.value)
  } finally {
    repairing.value = false
  }
}

const useRepair = (text) => {
  input.value = text
  repairResults.value = []
}

const copy = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('tools.codec.copied'))
  } catch {
    ElMessage.error(t('tools.codec.copyFailed'))
  }
}

const copyAll = () => {
  const body = encodeRows.value.map((row) => `${t(`tools.codec.formats.${row.key}`)}: ${row.value}`).join('\n\n')
  copy(body)
}

const clearAll = () => {
  input.value = ''
}

const SAMPLE_TEXT = '你好, ihomy 😀'

const loadSample = () => {
  input.value = mode.value === 'encode' ? SAMPLE_TEXT : toUnicodeEscapes(SAMPLE_TEXT)
  savePrefs()
}
</script>

<style scoped>
.codec-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}
.codec-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.codec-pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.codec-pane-head .section-label,
.codec-table-head .section-label {
  margin: 0;
}
.codec-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.codec-format {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.codec-area {
  display: block;
  margin: 10px 0 0;
  width: 100%;
  min-height: 320px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
}
textarea.codec-area:focus {
  border-color: var(--color-accent);
}
textarea.codec-area::placeholder {
  color: var(--color-text-secondary);
}
pre.codec-area {
  margin: 10px 0 0;
  min-height: 320px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  overflow: auto;
}
.codec-out {
  font-family: inherit;
}
.codec-error {
  border-color: rgba(168, 72, 58, 0.35);
  color: var(--color-accent);
  font-family: inherit;
}
.codec-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border-style: dashed;
  color: var(--color-text-secondary);
  font-family: inherit;
  font-size: 13px;
  text-align: center;
}
.codec-results {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 520px;
  overflow: auto;
  padding-right: 4px;
}
.codec-row {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px 10px;
  background: var(--color-bg);
}
.codec-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.codec-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.codec-value {
  display: block;
  margin-top: 4px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text);
}
.codec-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.codec-table-card {
  margin-top: 16px;
}
.codec-table-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.codec-table-wrap {
  margin-top: 10px;
  max-height: 420px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.codec-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.codec-table th,
.codec-table td {
  padding: 6px 10px;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-border);
}
.codec-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--color-card);
  font-weight: 600;
  color: var(--color-text-secondary);
}
.codec-table tbody tr:last-child td {
  border-bottom: 0;
}
.codec-cell-char {
  font-size: 15px;
  text-align: center;
}
@media (max-width: 900px) {
  .codec-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .codec-area,
  pre.codec-area {
    min-height: 220px;
  }
}
</style>
