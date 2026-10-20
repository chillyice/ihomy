<!-- 哈希面板:文本摘要 + 可选 HMAC。算法与输入只在本机计算,密钥不保存。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.hash.input') }}</span>
        <span>{{ $t('tools.hash.textCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.hash.textPlaceholder')"
        ></textarea>

        <div class="bpc-field">
          <span class="bpc-label">{{ $t('tools.hash.algorithms') }}</span>
          <BpcChips v-model="algorithms" :options="algorithmOptions" multiple />
        </div>

        <div class="bpc-field">
          <label class="bpc-label" for="bp-hash-key">{{ $t('tools.hash.hmacKey') }}</label>
          <input
            id="bp-hash-key"
            v-model="hmacKey"
            class="bpc-input"
            type="password"
            autocomplete="off"
            :placeholder="$t('tools.hash.hmacPlaceholder')"
          />
        </div>

        <div class="bpc-inline">
          <div class="bpc-field">
            <span class="bpc-label">{{ $t('bitPouch.tools.format') }}</span>
            <BpcChips v-model="format" :options="formatOptions" />
          </div>
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.hash.sample') }}</button>
          <button class="bpc-btn" type="button" @click="input = ''">{{ $t('tools.hash.clear') }}</button>
        </div>

        <p v-if="error" class="bpc-err">{{ error }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.hash.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.hash.result') }}</span>
        <BpcCopy :value="allText" :label="$t('tools.hash.copyAll')" />
      </div>
      <div class="bpc-card-bd">
        <div v-if="results.length" class="bpc-rows">
          <div v-for="row in results" :key="row.algorithm" class="bpc-row">
            <span class="bpc-row-k">{{ row.algorithm }} · {{ $t('tools.hash.bits', { n: row.bits }) }}</span>
            <span class="bpc-row-v is-accent">{{ row.value }}</span>
            <BpcCopy :value="row.value" :label="$t('tools.hash.copy')" />
          </div>
        </div>
        <p v-else class="bpc-hint">{{ pending ? $t('tools.hash.computing') : $t('tools.hash.emptyResult') }}</p>
        <p v-if="results.length" class="bpc-hint">{{ $t('tools.hash.md5Note') }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { HASH_ALGORITHMS, run } from '@/utils/tools/hashTool'

const { t, locale } = useI18n()

const input = ref('')
const algorithms = ref(['md5', 'sha256'])
const hmacKey = ref('')
const format = ref('hex')
const results = ref([])
const error = ref('')
const pending = ref(false)

const algorithmOptions = HASH_ALGORITHMS.map((item) => ({ value: item, label: item.toUpperCase() }))
const formatOptions = computed(() => [
  { value: 'hex', label: t('tools.hash.hex') },
  { value: 'base64', label: t('tools.hash.base64') },
])

const allText = computed(() => results.value.map((row) => `${row.algorithm.toUpperCase()}: ${row.value}`).join('\n'))

let timer = null
let token = 0

const compute = async () => {
  const current = ++token
  if (!input.value) {
    results.value = []
    error.value = ''
    pending.value = false
    return
  }
  pending.value = true
  const result = await run(
    input.value,
    { algorithms: algorithms.value, hmacKey: hmacKey.value, format: format.value },
    { locale: locale.value?.startsWith('en') ? 'en' : 'zh' },
  )
  if (current !== token) return
  pending.value = false
  if (!result.ok) {
    results.value = []
    error.value = result.error.message
    return
  }
  error.value = ''
  results.value = result.data.results
}

const schedule = () => {
  clearTimeout(timer)
  timer = setTimeout(compute, 200)
}

watch([input, algorithms, hmacKey, format], schedule, { deep: true, immediate: true })
onBeforeUnmount(() => clearTimeout(timer))

const loadSample = () => {
  input.value = 'bit_pouch'
}
</script>
