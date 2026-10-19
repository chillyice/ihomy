<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.hash.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="source" size="small">
          <el-radio-button value="text">{{ $t('tools.hash.modeText') }}</el-radio-button>
          <el-radio-button value="file">{{ $t('tools.hash.modeFile') }}</el-radio-button>
        </el-radio-group>
        <span class="hash-meta">{{ metaText }}</span>
      </div>
      <div class="tb-right">
        <el-select v-model="format" size="small" style="width: 108px">
          <el-option value="hex" :label="$t('tools.hash.hex')" />
          <el-option value="base64" :label="$t('tools.hash.base64')" />
        </el-select>
        <el-button size="small" @click="loadSample">{{ $t('tools.hash.sample') }}</el-button>
        <el-button size="small" :disabled="!hasInput" @click="clearAll">{{ $t('tools.hash.clear') }}</el-button>
      </div>
    </PageToolbar>

    <div class="hash-grid">
      <div class="card hash-pane">
        <div class="section-label">{{ $t('tools.hash.input') }}</div>

        <textarea
          v-if="source === 'text'"
          v-model="text"
          class="hash-area"
          spellcheck="false"
          autocomplete="off"
          :placeholder="$t('tools.hash.textPlaceholder')"
        ></textarea>

        <label v-else class="hash-drop">
          <input type="file" class="hash-file" @change="onFileChange" />
          <span class="hash-drop-title">{{ file ? file.name : $t('tools.hash.chooseFile') }}</span>
          <span class="hash-drop-sub">{{ file ? formatBytes(file.size) : $t('tools.hash.fileHint') }}</span>
        </label>

        <div class="hash-options">
          <span class="section-label">{{ $t('tools.hash.algorithms') }}</span>
          <el-checkbox-group v-model="algorithms" size="small">
            <el-checkbox-button v-for="algorithm in HASH_ALGORITHMS" :key="algorithm" :value="algorithm">
              {{ algorithmLabel(algorithm) }}
            </el-checkbox-button>
          </el-checkbox-group>
        </div>

        <div class="hash-field">
          <label for="hash-hmac">{{ $t('tools.hash.hmacKey') }}</label>
          <el-input
            id="hash-hmac"
            v-model="hmacKey"
            type="password"
            show-password
            size="small"
            :placeholder="$t('tools.hash.hmacPlaceholder')"
          />
          <span class="hash-hint">{{ $t('tools.hash.hmacHint') }}</span>
        </div>

        <div class="hash-field">
          <label for="hash-expected">{{ $t('tools.hash.expected') }}</label>
          <el-input id="hash-expected" v-model="expected" size="small" :placeholder="$t('tools.hash.expectedPlaceholder')" />
        </div>

        <p class="hash-note">{{ $t('tools.hash.md5Note') }}</p>
        <p class="hash-privacy">{{ $t('tools.hash.privacy') }}</p>
      </div>

      <div class="card hash-pane">
        <div class="hash-result-head">
          <span class="section-label">{{ $t('tools.hash.result') }}</span>
          <el-button size="small" text :disabled="!canCopy" @click="copyAll">{{ $t('tools.hash.copyAll') }}</el-button>
        </div>

        <div v-if="computing" class="hash-empty">{{ $t('tools.hash.computing') }}</div>
        <div v-else-if="error" class="hash-error">{{ error }}</div>
        <div v-else-if="!result || !result.ok || !result.data.results.length" class="hash-empty">
          {{ $t('tools.hash.emptyResult') }}
        </div>
        <template v-else>
          <div v-if="result.data.expected" class="hash-compare" :class="result.data.expected.match ? 'is-match' : 'is-miss'">
            {{ result.data.expected.match ? $t('tools.hash.expectedMatch') : $t('tools.hash.expectedMiss') }}
          </div>

          <div v-for="row in result.data.results" :key="row.algorithm" class="hash-row">
            <div class="hash-row-head">
              <span class="hash-row-label">{{ algorithmLabel(row.algorithm) }}</span>
              <el-button size="small" text @click="copy(row.value)">{{ $t('tools.hash.copy') }}</el-button>
            </div>
            <code class="hash-value">{{ row.value }}</code>
            <div class="hash-row-meta">{{ $t('tools.hash.bits', { n: row.bits }) }}</div>
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
import { HASH_ALGORITHMS, MAX_FILE_BYTES, formatBytes, run, runFile } from '@/utils/tools/hashTool'

const { t, locale } = useI18n()

const source = ref('text')
const text = ref('')
const file = ref(null)
const algorithms = ref(['md5', 'sha256'])
const format = ref('hex')
const hmacKey = ref('')
const expected = ref('')

const result = ref(null)
const error = ref('')
const computing = ref(false)

let timer = null
let sequence = 0

const coreLocale = () => (locale.value?.startsWith('en') ? 'en' : 'zh')
const algorithmLabel = (algorithm) => (algorithm === 'sha1' ? 'SHA-1' : algorithm.toUpperCase())
const hasInput = computed(() => (source.value === 'text' ? text.value.length > 0 : Boolean(file.value)))
const canCopy = computed(() => Boolean(result.value?.ok && result.value.data.results.length))
const metaText = computed(() => {
  if (source.value === 'file') {
    const size = file.value?.size ?? 0
    return t('tools.hash.fileSize', { size: formatBytes(size), max: formatBytes(MAX_FILE_BYTES) })
  }
  return t('tools.hash.textCount', { n: text.value.length })
})

const options = () => ({ algorithms: algorithms.value, format: format.value, hmacKey: hmacKey.value, expected: expected.value })

async function compute() {
  const current = ++sequence
  if (!algorithms.value.length) {
    result.value = null
    error.value = t('tools.hash.errNoAlgorithm')
    return
  }
  if (!hasInput.value && source.value === 'file') {
    result.value = null
    error.value = ''
    return
  }

  computing.value = true
  error.value = ''
  try {
    const response =
      source.value === 'file'
        ? await runFile(file.value, options(), { locale: coreLocale() })
        : await run(text.value, options(), { locale: coreLocale() })
    if (current !== sequence) return
    if (response.ok) {
      result.value = response
    } else {
      result.value = null
      error.value = response.error.message
    }
  } catch {
    if (current !== sequence) return
    result.value = null
    error.value = t('tools.hash.errRead')
  } finally {
    if (current === sequence) computing.value = false
  }
}

const schedule = () => {
  clearTimeout(timer)
  timer = setTimeout(compute, 220)
}

watch([text, algorithms, format, hmacKey, expected, file], () => {
  if (source.value === 'text' || file.value) schedule()
})
watch(source, () => {
  result.value = null
  error.value = ''
  if (source.value === 'text' || file.value) schedule()
})

const onFileChange = (event) => {
  const next = event.target.files?.[0] ?? null
  file.value = next
  result.value = null
  error.value = ''
  if (next) compute()
}

const copy = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('tools.hash.copied'))
  } catch {
    ElMessage.error(t('tools.hash.copyFailed'))
  }
}

const copyAll = () => {
  if (!canCopy.value) return
  copy(result.value.output)
}

const clearAll = () => {
  text.value = ''
  file.value = null
  expected.value = ''
  result.value = null
  error.value = ''
}

const SAMPLES = ['Hello, ihomy', '家庭共享平台', '{"hello":"world"}']
const loadSample = () => {
  source.value = 'text'
  const next = SAMPLES.find((item) => item !== text.value) || SAMPLES[0]
  text.value = next
}

onBeforeUnmount(() => clearTimeout(timer))
</script>

<style scoped>
.hash-meta {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.hash-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .hash-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.hash-pane {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.hash-area {
  width: 100%;
  min-height: 190px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-soft);
  color: var(--color-text);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
}
.hash-area:focus {
  outline: none;
  border-color: var(--color-accent);
}
.hash-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 130px;
  padding: 18px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  color: var(--color-text-secondary);
  cursor: pointer;
  text-align: center;
}
.hash-drop:hover,
.hash-drop:focus-within {
  border-color: var(--color-accent);
  color: var(--color-text);
}
.hash-file {
  display: none;
}
.hash-drop-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--color-text);
}
.hash-drop-sub {
  font-size: 12px;
}
.hash-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hash-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hash-field label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.hash-hint,
.hash-row-meta,
.hash-privacy {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.hash-note {
  margin: 0;
  padding: 9px 12px;
  border-left: 3px solid var(--color-accent);
  border-radius: 4px;
  background: var(--color-bg-soft);
  font-size: 12px;
  color: var(--color-text-secondary);
}
.hash-privacy {
  margin: 0;
}
.hash-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.hash-empty {
  padding: 24px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.hash-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-danger-weak, rgba(220, 80, 80, 0.12));
  color: var(--color-danger, #c0392b);
  font-size: 13px;
}
.hash-compare {
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
}
.hash-compare.is-match {
  background: rgba(46, 160, 90, 0.14);
  color: #2e9a5a;
}
.hash-compare.is-miss {
  background: rgba(220, 80, 80, 0.12);
  color: #c0392b;
}
.hash-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}
.hash-row:last-child {
  border-bottom: 0;
}
.hash-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.hash-row-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.hash-value {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.55;
  word-break: break-all;
}
</style>
