<!-- 文本清洗与对比:清洗 / 命名转换 / Markdown 表格 / 文本 diff,全部在本机完成 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.text.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="clean">{{ $t('tools.text.modeClean') }}</el-radio-button>
          <el-radio-button value="case">{{ $t('tools.text.modeCase') }}</el-radio-button>
          <el-radio-button value="table">{{ $t('tools.text.modeTable') }}</el-radio-button>
          <el-radio-button value="diff">{{ $t('tools.text.modeDiff') }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tb-right">
        <el-button size="small" @click="loadSample">{{ $t('tools.text.sample') }}</el-button>
        <el-button size="small" :disabled="!left && !right" @click="clearAll">{{ $t('tools.text.clear') }}</el-button>
        <el-button size="small" :disabled="!output && !caseResult" @click="copyAll">{{ $t('tools.text.copy') }}</el-button>
      </div>
    </PageToolbar>

    <div class="text-grid">
      <div class="card text-pane">
        <div class="text-pane-head">
          <span class="section-label">{{ mode === 'diff' ? $t('tools.text.left') : $t('tools.text.input') }}</span>
          <span class="text-meta">{{ $t('tools.text.charCount', { n: left.length }) }}</span>
        </div>
        <textarea
          v-model="left"
          class="text-area"
          spellcheck="false"
          autocomplete="off"
          :placeholder="$t('tools.text.placeholder')"
        ></textarea>

        <template v-if="mode === 'diff'">
          <div class="text-pane-head">
            <span class="section-label">{{ $t('tools.text.right') }}</span>
            <span class="text-meta">{{ $t('tools.text.charCount', { n: right.length }) }}</span>
          </div>
          <textarea
            v-model="right"
            class="text-area text-area-sm"
            spellcheck="false"
            autocomplete="off"
            :placeholder="$t('tools.text.placeholder')"
          ></textarea>
          <div class="text-inline">
            <el-checkbox v-model="diffIgnoreWhitespace" size="small">{{ $t('tools.text.ignoreWhitespace') }}</el-checkbox>
            <el-checkbox v-model="diffIgnoreCase" size="small">{{ $t('tools.text.ignoreCase') }}</el-checkbox>
          </div>
        </template>

        <template v-else-if="mode === 'clean'">
          <div class="text-options">
            <el-checkbox v-model="clean.trim" size="small">{{ $t('tools.text.trim') }}</el-checkbox>
            <el-checkbox v-model="clean.dropEmpty" size="small">{{ $t('tools.text.dropEmpty') }}</el-checkbox>
            <el-checkbox v-model="clean.dedupe" size="small">{{ $t('tools.text.dedupe') }}</el-checkbox>
            <el-checkbox v-model="clean.reverse" size="small">{{ $t('tools.text.reverse') }}</el-checkbox>
            <el-checkbox v-model="clean.numberLines" size="small">{{ $t('tools.text.numberLines') }}</el-checkbox>
          </div>
          <div class="text-inline">
            <label class="text-label" for="text-sort">{{ $t('tools.text.sort') }}</label>
            <el-select id="text-sort" v-model="clean.sort" size="small" style="width: 120px">
              <el-option value="none" :label="$t('tools.text.sortNone')" />
              <el-option value="asc" :label="$t('tools.text.sortAsc')" />
              <el-option value="desc" :label="$t('tools.text.sortDesc')" />
              <el-option value="length" :label="$t('tools.text.sortLength')" />
            </el-select>
            <label class="text-label" for="text-width">{{ $t('tools.text.width') }}</label>
            <el-select id="text-width" v-model="clean.width" size="small" style="width: 110px">
              <el-option value="none" :label="$t('tools.text.widthNone')" />
              <el-option value="half" :label="$t('tools.text.widthHalf')" />
              <el-option value="full" :label="$t('tools.text.widthFull')" />
            </el-select>
          </div>
          <div class="text-inline">
            <el-input v-model="clean.prefix" size="small" :placeholder="$t('tools.text.prefix')" style="width: 130px" />
            <el-input v-model="clean.suffix" size="small" :placeholder="$t('tools.text.suffix')" style="width: 130px" />
            <el-input v-model="clean.joinWith" size="small" :placeholder="$t('tools.text.joinWith')" style="width: 160px" />
          </div>
        </template>

        <template v-else-if="mode === 'table'">
          <div class="text-inline">
            <el-radio-group v-model="tableDirection" size="small">
              <el-radio-button value="markdown">{{ $t('tools.text.toMarkdown') }}</el-radio-button>
              <el-radio-button value="csv">{{ $t('tools.text.toCsv') }}</el-radio-button>
            </el-radio-group>
            <el-select v-if="tableDirection === 'markdown'" v-model="tableAlign" size="small" style="width: 110px">
              <el-option value="left" :label="$t('tools.text.alignLeft')" />
              <el-option value="center" :label="$t('tools.text.alignCenter')" />
              <el-option value="right" :label="$t('tools.text.alignRight')" />
            </el-select>
          </div>
        </template>

        <p class="text-hint">{{ $t('tools.text.privacy') }}</p>
      </div>

      <div class="card text-pane">
        <div class="text-pane-head">
          <span class="section-label">{{ $t('tools.text.result') }}</span>
          <span v-if="statsText" class="text-meta">{{ statsText }}</span>
        </div>

        <div v-if="error" class="text-error">{{ error }}</div>
        <div v-else-if="!left && !right" class="text-empty">{{ $t('tools.text.empty') }}</div>

        <template v-else-if="mode === 'case'">
          <div v-for="row in caseRows" :key="row.key" class="text-row">
            <div class="text-row-head">
              <span class="text-row-label">{{ row.label }}</span>
              <el-button size="small" text :disabled="!row.value" @click="copy(row.value)">{{ $t('tools.text.copyOne') }}</el-button>
            </div>
            <code class="text-value">{{ row.value || '—' }}</code>
          </div>
        </template>

        <template v-else-if="mode === 'diff'">
          <div v-if="diffResult.error" class="text-error">{{ diffResult.error }}</div>
          <div class="text-diff-stats">
            <span class="is-add">+{{ diffResult.stats.added }}</span>
            <span class="is-del">-{{ diffResult.stats.removed }}</span>
            <span>{{ $t('tools.text.sameLines', { n: diffResult.stats.same }) }}</span>
          </div>
          <div class="text-diff-wrap">
            <table class="text-diff">
              <thead>
                <tr>
                  <th class="text-diff-no"></th>
                  <th>{{ $t('tools.text.left') }}</th>
                  <th class="text-diff-no"></th>
                  <th>{{ $t('tools.text.right') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in diffResult.rows" :key="index" :class="'is-' + row.type">
                  <td class="text-diff-no">{{ row.leftNo ?? '' }}</td>
                  <td class="text-diff-cell">{{ row.left }}</td>
                  <td class="text-diff-no">{{ row.rightNo ?? '' }}</td>
                  <td class="text-diff-cell">{{ row.right }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>

        <pre v-else class="text-out">{{ output }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  cleanText,
  convertCase,
  markdownToDelimited,
  parseDelimited,
  run,
  textStats,
  toMarkdownTable,
  toFullWidth,
  toHalfWidth,
} from '@/utils/tools/textTool'

const { t, locale } = useI18n()

const mode = ref('clean')
const left = ref('')
const right = ref('')

const clean = reactive({
  trim: false,
  dropEmpty: false,
  dedupe: false,
  reverse: false,
  numberLines: false,
  sort: 'none',
  width: 'none',
  prefix: '',
  suffix: '',
  joinWith: '',
})

const tableDirection = ref('markdown')
const tableAlign = ref('left')
const diffIgnoreWhitespace = ref(false)
const diffIgnoreCase = ref(false)

const coreLocale = () => (locale.value?.startsWith('en') ? 'en' : 'zh')

const error = ref('')
const output = ref('')
const stats = ref(null)

const compute = () => {
  error.value = ''
  output.value = ''
  stats.value = null
  if (mode.value === 'diff') return

  if (mode.value === 'case') {
    if (!left.value) return
    const response = run(left.value, { mode: 'case' }, { locale: coreLocale() })
    if (!response.ok) {
      error.value = response.error.message
      return
    }
    output.value = response.output
    return
  }

  if (mode.value === 'table') {
    if (!left.value.trim()) return
    try {
      if (tableDirection.value === 'csv') {
        output.value = markdownToDelimited(left.value, ',')
        stats.value = textStats(output.value)
      } else {
        output.value = toMarkdownTable(parseDelimited(left.value), { align: tableAlign.value })
        const rows = parseDelimited(left.value)
        stats.value = { rows: rows.length, columns: rows[0]?.length || 0 }
      }
    } catch (e) {
      error.value = e?.message || String(e)
    }
    return
  }

  if (!left.value.length) return
  if (clean.width !== 'none') {
    output.value = clean.width === 'half' ? toHalfWidth(left.value) : toFullWidth(left.value)
  } else {
    output.value = cleanText(left.value, { ...clean, width: undefined })
  }
  stats.value = textStats(output.value)
  if (left.value.length > 1_000_000) error.value = t('tools.text.tooLong')
}

watch([mode, left, right, clean, tableDirection, tableAlign, diffIgnoreWhitespace, diffIgnoreCase], compute, {
  deep: true,
  immediate: true,
})

// 模板里直接用 computed 结果,避免与响应式对象同名冲突
const caseRows = computed(() => {
  if (!left.value) return []
  const data = convertCase(left.value)
  return [
    { key: 'camel', label: 'camelCase', value: data.camel },
    { key: 'pascal', label: 'PascalCase', value: data.pascal },
    { key: 'snake', label: 'snake_case', value: data.snake },
    { key: 'kebab', label: 'kebab-case', value: data.kebab },
    { key: 'constant', label: 'CONSTANT_CASE', value: data.constant },
  ]
})

const diffResult = computed(() => {
  if (!left.value && !right.value) return { rows: [], stats: { added: 0, removed: 0, same: 0 } }
  try {
    const result = run(
      left.value,
      {
        mode: 'diff',
        second: right.value,
        ignoreWhitespace: diffIgnoreWhitespace.value,
        ignoreCase: diffIgnoreCase.value,
      },
      { locale: coreLocale() },
    )
    if (!result.ok) return { rows: [], stats: { added: 0, removed: 0, same: 0 }, error: result.error.message }
    return { rows: result.data, stats: result.stats }
  } catch (e) {
    return { rows: [], stats: { added: 0, removed: 0, same: 0 }, error: e?.message || String(e) }
  }
})

const statsText = computed(() => {
  if (!stats.value) return ''
  const value = stats.value
  if (value.rows !== undefined) return t('tools.text.tableStats', { rows: value.rows, columns: value.columns ?? '-' })
  return t('tools.text.stats', { chars: value.chars, cjk: value.cjk, words: value.words, lines: value.lines, bytes: value.bytes })
})

const copy = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('tools.text.copied'))
  } catch {
    ElMessage.error(t('tools.text.copyFailed'))
  }
}

const copyAll = () => copy(output.value)

const clearAll = () => {
  left.value = ''
  right.value = ''
  output.value = ''
  error.value = ''
  stats.value = null
}

const SAMPLES = {
  clean: '  ihomy  \n\n相册\n\n日记\n相册  ',
  case: 'user profile-id',
  table: '姓名,年龄,城市\n阿岚,30,杭州\n小满,8,杭州',
  diff: '第一行\n第二行\n第三行',
}

const loadSample = () => {
  left.value = SAMPLES[mode.value] || ''
  right.value = mode.value === 'diff' ? '第一行\n第二行改过\n第三行\n第四行' : ''
}
</script>

<style scoped>
.text-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.text-pane {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.text-pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.text-pane-head .section-label {
  margin: 0;
}
.text-meta,
.text-hint,
.text-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.text-hint {
  margin: 0;
}
.text-area {
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
.text-area-sm {
  min-height: 150px;
}
.text-area:focus {
  border-color: var(--color-accent);
}
.text-options,
.text-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}
.text-empty {
  padding: 30px 12px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.text-error {
  padding: 9px 12px;
  border-radius: 8px;
  background: rgba(220, 80, 80, 0.12);
  color: var(--color-accent);
  font-size: 12.5px;
  line-height: 1.6;
}
.text-out {
  margin: 0;
  min-height: 240px;
  max-height: 620px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow: auto;
  overflow-wrap: anywhere;
}
.text-row {
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg);
}
.text-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.text-row-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.text-value {
  display: block;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  overflow-wrap: anywhere;
}
.text-diff-stats {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.text-diff-stats .is-add {
  color: #2e9a5a;
}
.text-diff-stats .is-del {
  color: #c0392b;
}
.text-diff-wrap {
  max-height: 520px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.text-diff {
  width: 100%;
  border-collapse: collapse;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  table-layout: fixed;
}
.text-diff th,
.text-diff td {
  padding: 4px 8px;
  border-bottom: 1px solid var(--color-border);
  text-align: left;
  vertical-align: top;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.text-diff th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--color-card);
  font-weight: 600;
  color: var(--color-text-secondary);
}
.text-diff-no {
  width: 44px;
  color: var(--color-text-secondary);
  text-align: right;
}
.text-diff-cell {
  width: calc(50% - 44px);
}
.text-diff tr.is-add td:nth-child(3),
.text-diff tr.is-add td:nth-child(4) {
  background: rgba(46, 154, 90, 0.1);
}
.text-diff tr.is-del td:nth-child(1),
.text-diff tr.is-del td:nth-child(2) {
  background: rgba(220, 80, 80, 0.1);
}
.text-diff tr.is-change td:nth-child(1),
.text-diff tr.is-change td:nth-child(2) {
  background: rgba(220, 80, 80, 0.1);
}
.text-diff tr.is-change td:nth-child(3),
.text-diff tr.is-change td:nth-child(4) {
  background: rgba(46, 154, 90, 0.1);
}
@media (max-width: 900px) {
  .text-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .text-area {
    min-height: 180px;
  }
}
</style>
