<!-- MQTT 主题面板:主题解析 / 过滤器匹配 / 合法性校验。
     核心逻辑在 utils/tools/mqttTopicTool,与 bit_pouch 内置插件同一套规则;
     不连接 broker,也不发起任何网络请求。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.mqtt-topic.input') }}</span>
        <span>{{ $t('tools.mqtt-topic.inputCount', { n: lines }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />
        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.mqtt-topic.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply"
          @keydown.meta.enter.prevent="apply"
        ></textarea>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply">{{ $t('tools.mqtt-topic.run') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.mqtt-topic.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.mqtt-topic.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.mqtt-topic.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.mqtt-topic.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.mqtt-topic.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.mqtt-topic.emptyOutput') }}</pre>
        <div class="bpc-meta">
          <span v-if="statsText">{{ statsText }}</span>
        </div>
        <p v-for="(warning, index) in warnings" :key="index" class="bpc-hint">· {{ warning }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { SAMPLE_MATCH, SAMPLE_PARSE, SAMPLE_VALIDATE, run } from '@/utils/tools/mqttTopicTool'

const { t, locale } = useI18n()

const mode = ref('parse')
const input = ref(SAMPLE_PARSE)
const output = ref('')
const errorText = ref('')
const warnings = ref([])
const stats = ref(null)

const modeOptions = computed(() => [
  { value: 'parse', label: t('tools.mqtt-topic.modeParse') },
  { value: 'match', label: t('tools.mqtt-topic.modeMatch') },
  { value: 'validate', label: t('tools.mqtt-topic.modeValidate') },
])

const SAMPLES = {
  parse: SAMPLE_PARSE,
  match: SAMPLE_MATCH,
  validate: SAMPLE_VALIDATE,
}

const lines = computed(() => input.value.split(/\r?\n/).filter((line) => line.trim()).length)

const statsText = computed(() => {
  if (!stats.value) return ''
  return Object.entries(stats.value)
    .map(([key, value]) => `${key}: ${value}`)
    .join(' · ')
})

watch(mode, (value) => {
  input.value = SAMPLES[value] || ''
  output.value = ''
  errorText.value = ''
  warnings.value = []
  stats.value = null
})

function apply() {
  if (!input.value.trim()) {
    output.value = ''
    errorText.value = ''
    warnings.value = []
    stats.value = null
    return
  }
  const result = run(input.value, { mode: mode.value }, { locale: locale.value })
  if (!result.ok) {
    output.value = ''
    errorText.value = result.error.message
    warnings.value = []
    stats.value = null
    return
  }
  output.value = result.output
  errorText.value = ''
  warnings.value = result.data?.warnings || []
  stats.value = result.stats || null
}

function loadSample() {
  input.value = SAMPLES[mode.value] || ''
}

function clearAll() {
  input.value = ''
  output.value = ''
  errorText.value = ''
  warnings.value = []
  stats.value = null
}
</script>
