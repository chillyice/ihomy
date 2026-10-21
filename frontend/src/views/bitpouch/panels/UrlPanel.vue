<!-- URL 解析面板:协议、主机、端口、路径、片段与查询参数。
     核心逻辑在 utils/tools/urlTool,与 bit_pouch 内置 url 插件一致;
     只在本机解析,不发任何请求。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.url.input') }}</span>
        <span>{{ $t('tools.url.inputCount', { n: input.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="$t('tools.url.inputPlaceholder')"
          @keydown.ctrl.enter.prevent="apply"
          @keydown.meta.enter.prevent="apply"
        ></textarea>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="apply">{{ $t('tools.url.run') }}</button>
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.url.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.url.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.url.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-stack">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.url.output') }}</span>
          <BpcCopy :value="copyText" :label="$t('tools.url.copy')" />
        </div>
        <div class="bpc-card-bd">
          <div v-if="rows.length" class="bpc-rows">
            <div v-for="row in rows" :key="row.key" class="bpc-row">
              <span class="bpc-row-k">{{ row.label }}</span>
              <span class="bpc-row-v">{{ row.value || '—' }}</span>
            </div>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.url.emptyOutput') }}</p>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.url.params') }}</span>
          <span>{{ params.length }}</span>
        </div>
        <div class="bpc-card-bd">
          <div v-if="params.length" class="bpc-rows">
            <div v-for="(param, index) in params" :key="index" class="bpc-row">
              <span class="bpc-row-k">{{ param.key }}</span>
              <span class="bpc-row-v">{{ param.value }}</span>
            </div>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.url.noParams') }}</p>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { run } from '@/utils/tools/urlTool'

const { t, locale } = useI18n()

const SAMPLE = 'https://example.com:8443/api/items?tag=local&tag=offline&q=%E4%B8%AD%E6%96%87#top'

const input = ref(SAMPLE)
const output = ref('')
const errorText = ref('')
const data = ref(null)

const params = computed(() => data.value?.params || [])

const rows = computed(() => {
  if (!data.value) return []
  return [
    { key: 'href', label: t('tools.url.labels.href'), value: data.value.href },
    { key: 'protocol', label: t('tools.url.labels.protocol'), value: data.value.protocol },
    { key: 'host', label: t('tools.url.labels.host'), value: data.value.host },
    { key: 'hostname', label: t('tools.url.labels.hostname'), value: data.value.hostname },
    { key: 'port', label: t('tools.url.labels.port'), value: data.value.port },
    { key: 'pathname', label: t('tools.url.labels.pathname'), value: data.value.pathname },
    { key: 'search', label: t('tools.url.labels.search'), value: data.value.search },
    { key: 'hash', label: t('tools.url.labels.hash'), value: data.value.hash },
    { key: 'origin', label: t('tools.url.labels.origin'), value: data.value.origin },
  ]
})

const copyText = computed(() => output.value)

function apply() {
  if (!input.value.trim()) {
    output.value = ''
    errorText.value = ''
    data.value = null
    return
  }
  const result = run(input.value, {}, { locale: locale.value })
  if (!result.ok) {
    output.value = ''
    errorText.value = result.error.message
    data.value = null
    return
  }
  output.value = result.output
  errorText.value = ''
  data.value = result.data
}

function loadSample() {
  input.value = SAMPLE
  apply()
}

function clearAll() {
  input.value = ''
  output.value = ''
  errorText.value = ''
  data.value = null
}
</script>
