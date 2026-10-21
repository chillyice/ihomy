<!-- UUID 面板:空输入批量生成 v4,非空输入解析版本与变体。
     核心逻辑在 utils/tools/uuidTool,与 bit_pouch 内置 uuid 插件一致;
     随机源优先 Web Crypto,内容不落存储。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.uuid.input') }}</span>
        <span>{{ $t('tools.uuid.inputCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <textarea
          v-model="input"
          class="bpc-textarea bpc-short"
          spellcheck="false"
          :placeholder="$t('tools.uuid.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply"
          @keydown.meta.enter.prevent="apply"
        ></textarea>

        <div class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-uuid-count">{{ $t('tools.uuid.count') }}</label>
            <input id="bp-uuid-count" v-model.number="count" class="bpc-input" type="number" min="1" max="100" />
          </div>
          <div class="bpc-field">
            <span class="bpc-label">{{ $t('tools.uuid.options') }}</span>
            <label class="bpc-label"><input v-model="uppercase" type="checkbox" /> {{ $t('tools.uuid.uppercase') }}</label>
            <label class="bpc-label"><input v-model="hyphens" type="checkbox" /> {{ $t('tools.uuid.hyphens') }}</label>
          </div>
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply">{{ $t('tools.uuid.run') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.uuid.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.uuid.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.uuid.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.uuid.output') }}</span>
        <BpcCopy :value="output" :label="$t('tools.uuid.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !output }">{{ output || $t('tools.uuid.emptyOutput') }}</pre>
        <div class="bpc-meta">
          <span v-if="statsText">{{ statsText }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { run } from '@/utils/tools/uuidTool'

const { locale } = useI18n()

const input = ref('')
const count = ref(5)
const uppercase = ref(false)
const hyphens = ref(true)
const output = ref('')
const errorText = ref('')
const stats = ref(null)

const statsText = computed(() => {
  if (!stats.value) return ''
  return Object.entries(stats.value)
    .map(([key, value]) => `${key}: ${value}`)
    .join(' · ')
})

function apply() {
  const result = run(input.value, { count: count.value, uppercase: uppercase.value, hyphens: hyphens.value }, { locale: locale.value })
  if (!result.ok) {
    output.value = ''
    errorText.value = result.error.message
    stats.value = null
    return
  }
  output.value = result.output
  errorText.value = ''
  stats.value = result.stats || null
}

function loadSample() {
  input.value = '550e8400-e29b-41d4-a716-446655440000'
  apply()
}

function clearAll() {
  input.value = ''
  output.value = ''
  errorText.value = ''
  stats.value = null
}
</script>
