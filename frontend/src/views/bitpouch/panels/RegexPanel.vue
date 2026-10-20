<!-- 正则面板:实时匹配 / 捕获组 / 替换预览。输入长度与匹配条数都有上限,避免卡住页面。 -->
<template>
  <div class="bpc-grid">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.regex.pattern') }}</span>
        <span>{{ flagsLabel }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-field">
          <label class="bpc-label" for="bp-regex-preset">{{ $t('tools.regex.presets') }}</label>
          <select id="bp-regex-preset" class="bpc-select" :value="preset" @change="applyPreset($event.target.value)">
            <option value="">{{ $t('tools.regex.presets') }}</option>
            <option v-for="item in REGEX_PRESETS" :key="item.id" :value="item.id">
              {{ $t(`tools.regex.presetNames.${item.id}`) }}
            </option>
          </select>
        </div>

        <div class="bpc-field">
          <input
            v-model="pattern"
            class="bpc-input"
            type="text"
            autocomplete="off"
            spellcheck="false"
            :placeholder="$t('tools.regex.patternPlaceholder')"
          />
        </div>

        <div class="bpc-field">
          <span class="bpc-label">{{ $t('tools.regex.result') }} · flags</span>
          <BpcChips v-model="flags" :options="flagOptions" multiple />
        </div>

        <div class="bpc-field">
          <label class="bpc-label" for="bp-regex-replace">{{ $t('tools.regex.replacement') }}</label>
          <input
            id="bp-regex-replace"
            v-model="replacement"
            class="bpc-input"
            type="text"
            autocomplete="off"
            spellcheck="false"
            :placeholder="$t('tools.regex.replacementPlaceholder')"
          />
        </div>

        <div class="bpc-field">
          <span class="bpc-label">{{ $t('tools.regex.text') }}</span>
          <textarea v-model="text" class="bpc-textarea" spellcheck="false" :placeholder="$t('tools.regex.textPlaceholder')"></textarea>
        </div>

        <p v-if="error" class="bpc-err">{{ error }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.regex.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-stack">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.regex.result') }}</span>
          <BpcCopy :value="matchText" :label="$t('tools.regex.copyMatches')" />
        </div>
        <div class="bpc-card-bd">
          <div v-if="matches.length" class="bpc-table-wrap">
            <table class="bpc-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>{{ $t('tools.regex.result') }}</th>
                  <th>index</th>
                  <th>{{ $t('bitPouch.tools.groups') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in matches" :key="`${row.index}-${i}`">
                  <td>{{ i + 1 }}</td>
                  <td class="is-hit">{{ row.value || '∅' }}</td>
                  <td>{{ row.index }}–{{ row.end }}</td>
                  <td>{{ groupText(row) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="bpc-hint">{{ error || $t('tools.regex.noMatch') }}</p>
          <p v-if="truncated" class="bpc-hint">{{ $t('tools.regex.truncated', { max: 500 }) }}</p>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.regex.replaced') }}</span>
          <BpcCopy :value="replaced || ''" :label="$t('tools.regex.copy')" />
        </div>
        <div class="bpc-card-bd">
          <pre class="bpc-out bpc-out-short" :class="{ 'bpc-out-empty': !replaced }">{{ replaced || $t('tools.regex.empty') }}</pre>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { MAX_MATCHES, REGEX_FLAGS, REGEX_PRESETS, executeRegex } from '@/utils/tools/regexTool'

const { locale } = useI18n()

const pattern = ref('(\\d+)-(\\d{2})-(\\d{2})')
const flags = ref(['g'])
const replacement = ref('')
const text = ref('订单 2026-10-20，发货 2026-10-22。')
const preset = ref('')
const result = ref(null)
const error = ref('')

const flagOptions = REGEX_FLAGS.map((flag) => ({ value: flag, label: flag }))
const flagsLabel = computed(() => flags.value.join('') || '—')

const matches = computed(() => (result.value?.data.matches || []).slice(0, MAX_MATCHES))
const replaced = computed(() => result.value?.data.replaced ?? '')
const truncated = computed(() => Boolean(result.value?.data.truncated))

const groupText = (row) => {
  const parts = row.groups.map((group) => `$${group.index}: ${group.value}`)
  for (const [name, value] of Object.entries(row.named)) parts.push(`$${name}: ${value}`)
  return parts.length ? parts.join(' · ') : '—'
}

const matchText = computed(() => matches.value.map((row) => row.value).join('\n'))

let timer = null
const runTool = () => {
  if (!pattern.value || !text.value) {
    result.value = null
    error.value = ''
    return
  }
  const out = executeRegex(
    text.value,
    // 替换框留空时不生成替换预览；传空字符串会被内核当成「替换为空」
    { pattern: pattern.value, flags: flags.value.join(''), replacement: replacement.value || undefined },
    { locale: locale.value?.startsWith('en') ? 'en' : 'zh' },
  )
  if (!out.ok) {
    result.value = null
    error.value = out.error.message
    return
  }
  error.value = ''
  result.value = out
}

const schedule = () => {
  clearTimeout(timer)
  timer = setTimeout(runTool, 150)
}

watch([pattern, flags, replacement, text], schedule, { deep: true, immediate: true })
onBeforeUnmount(() => clearTimeout(timer))

const applyPreset = (id) => {
  preset.value = id
  const item = REGEX_PRESETS.find((entry) => entry.id === id)
  if (!item) return
  pattern.value = item.pattern
  flags.value = [...new Set([...item.flags])]
}
</script>
