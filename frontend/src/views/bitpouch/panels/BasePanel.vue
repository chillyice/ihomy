<!-- 进制面板:2–36 进制互转,整数按大整数精确换算,小数最多 16 位。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.base.inputLabel') }}</span>
        <span>{{ $t('tools.base.length', { n: input.trim().length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-field">
          <input
            v-model="input"
            class="bpc-input"
            type="text"
            autocomplete="off"
            spellcheck="false"
            :placeholder="$t('tools.base.placeholder')"
          />
        </div>

        <div class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-base-from">{{ $t('tools.base.sourceBase') }}</label>
            <select id="bp-base-from" v-model.number="fromBase" class="bpc-select">
              <option v-for="base in ALL_BASES" :key="base" :value="base">{{ $t('tools.base.baseLabel', { n: base }) }}</option>
            </select>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-base-custom">{{ $t('tools.base.customBase') }}</label>
            <select id="bp-base-custom" v-model.number="customBase" class="bpc-select">
              <option v-for="base in ALL_BASES" :key="base" :value="base">{{ $t('tools.base.baseLabel', { n: base }) }}</option>
            </select>
          </div>
        </div>

        <div class="bpc-actions">
          <button class="bpc-chip" :class="{ 'is-on': uppercase }" type="button" :aria-pressed="uppercase" @click="uppercase = !uppercase">
            {{ $t('tools.base.uppercase') }}
          </button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.base.sample') }}</button>
          <button class="bpc-btn" type="button" @click="input = ''">{{ $t('tools.base.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.base.fractionHint') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.base.results') }}</span>
        <BpcCopy :value="allText" :label="$t('tools.base.copy')" />
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-rows">
          <div v-for="row in rows" :key="row.base" class="bpc-row">
            <span class="bpc-row-k">{{ $t('tools.base.baseLabel', { n: row.base }) }}</span>
            <span class="bpc-row-v" :class="{ 'bpc-empty': !row.text }">{{ row.text || '—' }}</span>
            <span v-if="row.approximate" class="bpc-tag">{{ $t('tools.base.approxTag') }}</span>
            <BpcCopy :value="row.text" :label="$t('tools.base.copy')" />
          </div>
        </div>
        <p class="bpc-hint">{{ $t('tools.base.privacy') }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { BASE_MAX, BASE_MIN, COMMON_BASES, convertAll } from '@/utils/baseConvert'

const { t } = useI18n()

const ALL_BASES = Array.from({ length: BASE_MAX - BASE_MIN + 1 }, (_, i) => BASE_MIN + i)
const SAMPLES = [
  { value: '255', base: 10 },
  { value: 'FF', base: 16 },
  { value: '1010.11', base: 2 },
  { value: '12345678901234567890', base: 10 },
]

const input = ref('')
const fromBase = ref(10)
const customBase = ref(7)
const uppercase = ref(true)

const result = computed(() => {
  if (!input.value.trim()) return { ok: true, results: [] }
  return convertAll(input.value, fromBase.value, [...COMMON_BASES, customBase.value], { uppercase: uppercase.value })
})

const bases = computed(() =>
  [...COMMON_BASES, customBase.value].filter((base) => Number.isInteger(base)).filter((base, i, list) => list.indexOf(base) === i),
)

const rows = computed(() => {
  if (result.value.ok && input.value.trim()) return result.value.results
  return bases.value.map((base) => ({ base, text: '', approximate: false }))
})

const ERROR_KEYS = {
  invalidBase: 'errorBase',
  invalidDigit: 'errorDigit',
  invalidNumber: 'errorNumber',
  tooLong: 'errorTooLong',
  empty: 'errorEmpty',
}

const errorText = computed(() => {
  if (!input.value.trim() || result.value.ok) return ''
  const key = ERROR_KEYS[result.value.error] || 'errorNumber'
  return t(`tools.base.${key}`, {
    char: result.value.char,
    base: fromBase.value,
    min: BASE_MIN,
    max: BASE_MAX,
  })
})

const allText = computed(() =>
  rows.value
    .filter((row) => row.text)
    .map((row) => `${row.base}: ${row.text}`)
    .join('\n'),
)

const loadSample = () => {
  const next = SAMPLES.find((sample) => sample.value !== input.value) || SAMPLES[0]
  input.value = next.value
  fromBase.value = next.base
}
</script>
