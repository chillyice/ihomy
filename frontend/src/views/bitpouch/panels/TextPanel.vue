<!-- 文本面板:清洗(去空白/空行/重复、排序、全半角)、命名风格转换、行级对比。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ mode === 'diff' ? $t('tools.text.left') : $t('tools.text.input') }}</span>
        <span>{{ $t('tools.text.charCount', { n: left.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <textarea v-model="left" class="bpc-textarea bpc-tall" spellcheck="false" :placeholder="$t('tools.text.placeholder')"></textarea>

        <div v-if="mode === 'clean'" class="bpc-chips">
          <button
            v-for="opt in cleanOptions"
            :key="opt.key"
            class="bpc-chip"
            :class="{ 'is-on': clean[opt.key] }"
            type="button"
            :aria-pressed="clean[opt.key]"
            @click="clean[opt.key] = !clean[opt.key]"
          >
            <span class="bpc-chip-dot" aria-hidden="true"></span>{{ opt.label }}
          </button>
        </div>

        <div v-if="mode === 'clean'" class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-text-sort">{{ $t('tools.text.sort') }}</label>
            <select id="bp-text-sort" v-model="sort" class="bpc-select">
              <option value="none">{{ $t('tools.text.sortNone') }}</option>
              <option value="asc">{{ $t('tools.text.sortAsc') }}</option>
              <option value="desc">{{ $t('tools.text.sortDesc') }}</option>
              <option value="length">{{ $t('tools.text.sortLength') }}</option>
            </select>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-text-width">{{ $t('tools.text.width') }}</label>
            <select id="bp-text-width" v-model="width" class="bpc-select">
              <option value="none">{{ $t('tools.text.widthNone') }}</option>
              <option value="half">{{ $t('tools.text.widthHalf') }}</option>
              <option value="full">{{ $t('tools.text.widthFull') }}</option>
            </select>
          </div>
        </div>

        <template v-if="mode === 'diff'">
          <div class="bpc-field">
            <span class="bpc-label">{{ $t('tools.text.right') }}</span>
            <textarea v-model="right" class="bpc-textarea bpc-short" spellcheck="false"></textarea>
          </div>
          <div class="bpc-chips">
            <button
              class="bpc-chip"
              :class="{ 'is-on': ignoreWhitespace }"
              type="button"
              :aria-pressed="ignoreWhitespace"
              @click="ignoreWhitespace = !ignoreWhitespace"
            >
              <span class="bpc-chip-dot" aria-hidden="true"></span>{{ $t('tools.text.ignoreWhitespace') }}
            </button>
            <button
              class="bpc-chip"
              :class="{ 'is-on': ignoreCase }"
              type="button"
              :aria-pressed="ignoreCase"
              @click="ignoreCase = !ignoreCase"
            >
              <span class="bpc-chip-dot" aria-hidden="true"></span>{{ $t('tools.text.ignoreCase') }}
            </button>
          </div>
        </template>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.text.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.text.clear') }}</button>
        </div>

        <p v-if="error" class="bpc-err">{{ error }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.text.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.text.result') }}</span>
        <BpcCopy :value="copyValue" :label="$t('tools.text.copy')" />
      </div>
      <div class="bpc-card-bd">
        <template v-if="mode === 'diff'">
          <div v-if="diffRows.length" class="bpc-table-wrap">
            <table class="bpc-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>{{ $t('tools.text.left') }}</th>
                  <th>#</th>
                  <th>{{ $t('tools.text.right') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in diffRows" :key="i">
                  <td>{{ row.leftNo ?? '' }}</td>
                  <td :class="{ 'is-del': row.type === 'del' || row.type === 'change' }">{{ row.left }}</td>
                  <td>{{ row.rightNo ?? '' }}</td>
                  <td :class="{ 'is-add': row.type === 'add' || row.type === 'change' }">{{ row.right }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.text.empty') }}</p>
          <div v-if="diffStats" class="bpc-meta">
            <span class="is-add">+{{ diffStats.added }}</span>
            <span class="is-del">-{{ diffStats.removed }}</span>
            <span>{{ $t('tools.text.sameLines', { n: diffStats.same }) }}</span>
          </div>
        </template>

        <template v-else-if="mode === 'case'">
          <div v-if="caseRows.length" class="bpc-rows">
            <div v-for="row in caseRows" :key="row.key" class="bpc-row">
              <span class="bpc-row-k">{{ row.key }}</span>
              <span class="bpc-row-v">{{ row.value }}</span>
              <BpcCopy :value="row.value" :label="$t('tools.text.copyOne')" />
            </div>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.text.empty') }}</p>
        </template>

        <template v-else>
          <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.text.empty') }}</pre>
          <div v-if="cleanStats" class="bpc-meta">
            <span>{{ statsText }}</span>
          </div>
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { run } from '@/utils/tools/textTool'

const { t, locale } = useI18n()

const SAMPLE = '  apple \n\nbanana\napple\n  中文  \n'
const SAMPLE_RIGHT = 'apple\nbanana\ncherry\n中文\n'

const mode = ref('clean')
const left = ref(SAMPLE)
const right = ref(SAMPLE_RIGHT)
const sort = ref('none')
const width = ref('none')
const ignoreWhitespace = ref(false)
const ignoreCase = ref(false)
const clean = reactive({ trim: true, dropEmpty: true, dedupe: false, reverse: false, numberLines: false })

const modeOptions = computed(() => [
  { value: 'clean', label: t('tools.text.modeClean') },
  { value: 'case', label: t('tools.text.modeCase') },
  { value: 'diff', label: t('tools.text.modeDiff') },
])

const cleanOptions = computed(() => [
  { key: 'trim', label: t('tools.text.trim') },
  { key: 'dropEmpty', label: t('tools.text.dropEmpty') },
  { key: 'dedupe', label: t('tools.text.dedupe') },
  { key: 'reverse', label: t('tools.text.reverse') },
  { key: 'numberLines', label: t('tools.text.numberLines') },
])

const result = computed(() => {
  if (!left.value) return { ok: true, output: '', data: null, stats: null }
  const options =
    mode.value === 'diff'
      ? { mode: 'diff', second: right.value, ignoreWhitespace: ignoreWhitespace.value, ignoreCase: ignoreCase.value }
      : mode.value === 'case'
        ? { mode: 'case' }
        : { mode: 'clean', ...clean, sort: sort.value, width: width.value }
  return run(left.value, options, { locale: locale.value?.startsWith('en') ? 'en' : 'zh' })
})

const error = computed(() => (result.value.ok ? '' : result.value.error.message))
const output = computed(() => (result.value.ok ? result.value.output : ''))

const diffRows = computed(() => (mode.value === 'diff' && result.value.ok ? result.value.data : []))
const diffStats = computed(() => (mode.value === 'diff' && result.value.ok ? result.value.stats : null))
const cleanStats = computed(() => (mode.value === 'clean' && result.value.ok ? result.value.stats : null))

const statsText = computed(() =>
  cleanStats.value
    ? t('tools.text.stats', {
        chars: cleanStats.value.chars,
        cjk: cleanStats.value.cjk,
        words: cleanStats.value.words,
        lines: cleanStats.value.lines,
        bytes: cleanStats.value.bytes,
      })
    : '',
)

const CASE_KEYS = ['camel', 'pascal', 'snake', 'kebab', 'constant']
const caseRows = computed(() => {
  if (mode.value !== 'case' || !result.value.ok || !result.value.data) return []
  return CASE_KEYS.map((key) => ({ key, value: result.value.data[key] }))
})

const copyValue = computed(() => {
  if (mode.value === 'diff') return diffRows.value.map((row) => row.right || row.left).join('\n')
  if (mode.value === 'case') return caseRows.value.map((row) => `${row.key}: ${row.value}`).join('\n')
  return output.value
})

const loadSample = () => {
  left.value = SAMPLE
  right.value = SAMPLE_RIGHT
}

const clearAll = () => {
  left.value = ''
  right.value = ''
}
</script>
