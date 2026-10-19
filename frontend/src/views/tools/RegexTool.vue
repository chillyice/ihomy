<!-- 正则测试:实时匹配 / 捕获组 / 替换预览,匹配在 Worker 中限时执行,防止病态表达式卡死页面 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.regex.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-select v-model="preset" size="small" style="width: 150px" :placeholder="$t('tools.regex.presets')" @change="applyPreset">
          <el-option value="" :label="$t('tools.regex.presets')" />
          <el-option v-for="item in REGEX_PRESETS" :key="item.id" :value="item.id" :label="$t(`tools.regex.presetNames.${item.id}`)" />
        </el-select>
        <span class="regex-flags">
          <el-checkbox-group v-model="flags" size="small">
            <el-checkbox-button v-for="flag in REGEX_FLAGS" :key="flag" :value="flag">{{ flag }}</el-checkbox-button>
          </el-checkbox-group>
        </span>
      </div>
      <div class="tb-right">
        <el-button size="small" @click="loadSample">{{ $t('tools.regex.sample') }}</el-button>
        <el-button size="small" :disabled="!pattern && !input" @click="clearAll">{{ $t('tools.regex.clear') }}</el-button>
        <el-button size="small" :disabled="!result?.ok || !result.data.matches.length" @click="copyMatches">
          {{ $t('tools.regex.copyMatches') }}
        </el-button>
      </div>
    </PageToolbar>

    <div class="regex-grid">
      <div class="card regex-pane">
        <div class="regex-field">
          <label for="regex-pattern">{{ $t('tools.regex.pattern') }}</label>
          <el-input
            id="regex-pattern"
            v-model="pattern"
            size="small"
            spellcheck="false"
            autocomplete="off"
            :placeholder="$t('tools.regex.patternPlaceholder')"
          >
            <template #prepend>/</template>
            <template #append>/{{ flags.join('') }}</template>
          </el-input>
        </div>

        <div class="regex-pane-head">
          <span class="section-label">{{ $t('tools.regex.text') }}</span>
          <span class="regex-meta">{{ $t('tools.regex.charCount', { n: input.length }) }}</span>
        </div>
        <textarea
          v-model="input"
          class="regex-area"
          spellcheck="false"
          autocomplete="off"
          :placeholder="$t('tools.regex.textPlaceholder')"
        ></textarea>

        <div class="regex-field">
          <label for="regex-replace">{{ $t('tools.regex.replacement') }}</label>
          <el-input
            id="regex-replace"
            v-model="replacement"
            size="small"
            spellcheck="false"
            :placeholder="$t('tools.regex.replacementPlaceholder')"
          />
          <span class="regex-hint">{{ $t('tools.regex.replacementHint') }}</span>
        </div>

        <p class="regex-hint">{{ $t('tools.regex.timeoutHint') }}</p>
        <p class="regex-hint">{{ $t('tools.regex.privacy') }}</p>
      </div>

      <div class="card regex-pane">
        <div class="regex-pane-head">
          <span class="section-label">{{ $t('tools.regex.result') }}</span>
          <span v-if="result?.ok" class="regex-meta">
            {{ $t('tools.regex.matchCount', { n: result.data.matches.length, groups: result.stats.groups }) }}
          </span>
        </div>

        <div v-if="error" class="regex-error">{{ error }}</div>
        <div v-else-if="!result" class="regex-empty">{{ $t('tools.regex.empty') }}</div>
        <template v-else>
          <div v-if="result.data.truncated" class="regex-warn">{{ $t('tools.regex.truncated', { max: MAX_MATCHES }) }}</div>

          <div v-if="segments" class="regex-preview">
            <span v-for="(segment, index) in segments" :key="index" :class="{ 'is-hit': segment.hit }">{{ segment.text }}</span>
          </div>

          <div v-if="!result.data.matches.length" class="regex-empty">{{ $t('tools.regex.noMatch') }}</div>
          <div v-else class="regex-match-list">
            <div v-for="(match, index) in result.data.matches" :key="index" class="regex-match">
              <div class="regex-match-head">
                <span class="regex-match-index">#{{ index + 1 }}</span>
                <span class="regex-match-pos">[{{ match.index }}, {{ match.end }})</span>
              </div>
              <code class="regex-match-value">{{ match.value }}</code>
              <div v-if="match.groups.length" class="regex-groups">
                <span v-for="group in match.groups" :key="group.index" class="regex-group">
                  ${{ group.index }} = {{ group.value || '∅' }}
                </span>
              </div>
              <div v-if="Object.keys(match.named).length" class="regex-groups">
                <span v-for="(value, name) in match.named" :key="name" class="regex-group">{{ name }} = {{ value || '∅' }}</span>
              </div>
            </div>
          </div>

          <div v-if="result.data.replaced !== null" class="regex-block">
            <div class="regex-block-head">
              <span class="section-label">{{ $t('tools.regex.replaced') }}</span>
              <el-button size="small" text @click="copy(result.data.replaced)">{{ $t('tools.regex.copy') }}</el-button>
            </div>
            <pre class="regex-out">{{ result.data.replaced }}</pre>
          </div>

          <div class="regex-block">
            <div class="regex-block-head">
              <span class="section-label">{{ $t('tools.regex.split') }}</span>
            </div>
            <pre class="regex-out">{{ result.data.split.join('\n') }}</pre>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { MAX_MATCHES, REGEX_FLAGS, REGEX_PRESETS } from '@/utils/tools/regexTool'
import RegexWorker from '@/workers/regex.worker.js?worker'

const { t, locale } = useI18n()

const TIMEOUT_MS = 1000
const HIGHLIGHT_LIMIT = 200

const pattern = ref('')
const input = ref('')
const replacement = ref('')
const flags = ref(['g'])
const preset = ref('')

const result = ref(null)
const error = ref('')
let timer = null
let sequence = 0
let worker = null
let pending = null

const coreLocale = () => (locale.value?.startsWith('en') ? 'en' : 'zh')

function createWorker() {
  const instance = new RegexWorker()
  instance.onmessage = (event) => {
    if (!pending || event.data?.id !== pending.id) return
    const { resolve } = pending
    pending = null
    instance.terminate()
    worker = null
    resolve({ timedOut: false, result: event.data.result })
  }
  return instance
}

function runInWorker(payload, id) {
  if (!worker) worker = createWorker()
  return new Promise((resolve) => {
    pending = { id, resolve }
    worker.postMessage({ id, ...payload })
    setTimeout(() => {
      if (!pending || pending.id !== id) return
      pending = null
      worker?.terminate()
      worker = null
      resolve({ timedOut: true })
    }, TIMEOUT_MS)
  })
}

async function compute() {
  const current = ++sequence
  if (!pattern.value) {
    result.value = null
    error.value = ''
    return
  }
  const payload = {
    input: input.value,
    options: { pattern: pattern.value, flags: flags.value.join(''), replacement: replacement.value },
    locale: coreLocale(),
  }
  const response = await runInWorker(payload, current)
  if (current !== sequence) return
  if (response.timedOut) {
    result.value = null
    error.value = t('tools.regex.timeout')
    return
  }
  const next = response.result
  if (!next?.ok) {
    result.value = null
    error.value = next?.error?.message || t('tools.regex.invalid')
    return
  }
  error.value = ''
  result.value = next
}

const schedule = () => {
  clearTimeout(timer)
  timer = setTimeout(compute, 260)
}

watch([pattern, input, replacement, flags], schedule, { deep: true })

const segments = computed(() => {
  if (!result.value?.ok) return null
  const matches = result.value.data.matches
  if (!matches.length || matches.length > HIGHLIGHT_LIMIT) return null
  const out = []
  let cursor = 0
  for (const match of matches) {
    if (match.index > cursor) out.push({ text: input.value.slice(cursor, match.index), hit: false })
    out.push({ text: match.value, hit: true })
    cursor = match.end
  }
  if (cursor < input.value.length) out.push({ text: input.value.slice(cursor), hit: false })
  return out
})

const applyPreset = (id) => {
  const item = REGEX_PRESETS.find((presetItem) => presetItem.id === id)
  if (!item) return
  pattern.value = item.pattern
  flags.value = [...new Set([...item.flags])]
}

const copy = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('tools.regex.copied'))
  } catch {
    ElMessage.error(t('tools.regex.copyFailed'))
  }
}

const copyMatches = () => {
  if (!result.value?.ok) return
  copy(result.value.data.matches.map((match) => match.value).join('\n'))
}

const clearAll = () => {
  pattern.value = ''
  input.value = ''
  replacement.value = ''
  preset.value = ''
  result.value = null
  error.value = ''
}

const SAMPLES = [
  { pattern: '(?<year>\\d{4})-(\\d{2})-(\\d{2})', flags: ['g'], text: '今天 2026-10-20,明天 2026-10-21' },
  { pattern: '\\b\\w+@\\w+\\.\\w+\\b', flags: ['g'], text: '联系:a@ihomy.top / b@example.com' },
  { pattern: '\\d+', flags: ['g'], text: '订单 1024 件,共 3 箱' },
]

const loadSample = () => {
  const sample = SAMPLES.find((item) => item.pattern !== pattern.value) || SAMPLES[0]
  pattern.value = sample.pattern
  flags.value = [...sample.flags]
  input.value = sample.text
}

onMounted(() => {
  if (!pattern.value) loadSample()
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  worker?.terminate()
  worker = null
})
</script>

<style scoped>
.regex-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.regex-pane {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.regex-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.regex-field label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.regex-flags :deep(.el-checkbox-button__inner) {
  padding: 5px 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.regex-pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.regex-pane-head .section-label {
  margin: 0;
}
.regex-meta,
.regex-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.regex-hint {
  margin: 0;
}
.regex-area {
  width: 100%;
  min-height: 240px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
}
.regex-area:focus {
  border-color: var(--color-accent);
}
.regex-empty {
  padding: 30px 12px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.regex-error,
.regex-warn {
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 12.5px;
  line-height: 1.6;
}
.regex-error {
  background: rgba(220, 80, 80, 0.12);
  color: var(--color-accent);
}
.regex-warn {
  background: rgba(200, 150, 40, 0.12);
  color: #9a7420;
}
.regex-preview {
  max-height: 180px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow: auto;
  overflow-wrap: anywhere;
}
.regex-preview .is-hit {
  border-radius: 3px;
  background: rgba(168, 72, 58, 0.18);
  color: var(--color-accent);
}
html.dark .regex-preview .is-hit {
  background: rgba(225, 154, 140, 0.22);
  color: #e19a8c;
}
.regex-match-list {
  max-height: 340px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.regex-match {
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}
.regex-match-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--color-text-secondary);
}
.regex-match-index {
  font-weight: 600;
}
.regex-match-value {
  display: block;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  overflow-wrap: anywhere;
}
.regex-groups {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.regex-group {
  padding: 1px 7px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11.5px;
  color: var(--color-text-secondary);
}
.regex-block {
  border-top: 1px dashed var(--color-border);
  padding-top: 8px;
}
.regex-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.regex-block-head .section-label {
  margin: 0;
}
.regex-out {
  margin: 6px 0 0;
  max-height: 200px;
  padding: 9px 11px;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow: auto;
  overflow-wrap: anywhere;
}
@media (max-width: 900px) {
  .regex-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .regex-area {
    min-height: 180px;
  }
}
</style>
