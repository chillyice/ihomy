<!-- 字符编码面板:一进多出(Unicode / HTML 实体 / URL / Base64 / UTF-8 字节 / 全半角),
     反向按所选格式还原。全部在本机浏览器完成。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.codec.original') }}</span>
        <span>{{ $t('tools.codec.charCount', { n: charCount }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <textarea
          v-model="input"
          class="bpc-textarea bpc-tall"
          spellcheck="false"
          :placeholder="mode === 'encode' ? $t('tools.codec.inputPlaceholder') : $t('tools.codec.decodePlaceholder')"
        ></textarea>

        <div v-if="mode === 'decode'" class="bpc-field">
          <label class="bpc-label" for="bp-codec-format">{{ $t('tools.codec.format') }}</label>
          <select id="bp-codec-format" v-model="format" class="bpc-select">
            <option v-for="item in FORMATS" :key="item" :value="item">{{ $t(`tools.codec.formats.${item}`) }}</option>
          </select>
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.codec.sample') }}</button>
          <button class="bpc-btn" type="button" @click="input = ''">{{ $t('tools.codec.clear') }}</button>
        </div>

        <p class="bpc-hint">{{ $t('tools.codec.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ mode === 'encode' ? $t('tools.codec.results') : $t('tools.codec.decoded') }}</span>
        <BpcCopy v-if="mode === 'encode'" :value="allEncoded" :label="$t('tools.codec.copyAll')" />
        <BpcCopy v-else :value="decoded.ok ? decoded.text : ''" :label="$t('tools.codec.copy')" />
      </div>
      <div class="bpc-card-bd">
        <div v-if="mode === 'encode'" class="bpc-rows">
          <div v-for="row in rows" :key="row.key" class="bpc-row">
            <span class="bpc-row-k">{{ $t(`tools.codec.formats.${row.key}`) }}</span>
            <span class="bpc-row-v" :class="{ 'bpc-empty': !row.value }">{{ row.value || '—' }}</span>
            <BpcCopy :value="row.value" :label="$t('tools.codec.copy')" />
          </div>
        </div>

        <template v-else>
          <pre v-if="decoded.ok" class="bpc-out bpc-out-tall" :class="{ 'bpc-out-empty': !decoded.text }">{{
            decoded.text || $t('tools.codec.emptyDecoded')
          }}</pre>
          <p v-else class="bpc-err">{{ $t(`tools.codec.errors.${decoded.error}`) }}</p>
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { CODEC_FORMATS, decodeText, toBase64, toHtmlEntities, toUnicodeEscapes, toUrlEncoded, toUtf8Hex } from '@/utils/textCodec'
import { toFullWidth, toHalfWidth } from '@/utils/tools/textTool'

const { t } = useI18n()

const FORMATS = CODEC_FORMATS
const SAMPLE = '你好, ihomy 😀'
const mode = ref('encode')
const format = ref('unicode')
const input = ref('')

const modeOptions = computed(() => [
  { value: 'encode', label: t('tools.codec.modeEncode') },
  { value: 'decode', label: t('tools.codec.modeDecode') },
])

const charCount = computed(() => Array.from(input.value).length)

const rows = computed(() => {
  const text = input.value
  return [
    { key: 'unicode', value: text ? toUnicodeEscapes(text) : '' },
    { key: 'html', value: text ? toHtmlEntities(text) : '' },
    { key: 'url', value: text ? toUrlEncoded(text) : '' },
    { key: 'base64', value: text ? toBase64(text) : '' },
    { key: 'hex', value: text ? toUtf8Hex(text) : '' },
    { key: 'half', value: text ? toHalfWidth(text) : '' },
    { key: 'full', value: text ? toFullWidth(text) : '' },
  ]
})

const allEncoded = computed(() => rows.value.map((row) => `${t(`tools.codec.formats.${row.key}`)}: ${row.value}`).join('\n\n'))

const decoded = computed(() => (input.value ? decodeText(format.value, input.value) : { ok: true, text: '' }))

const loadSample = () => {
  input.value = mode.value === 'encode' ? SAMPLE : toUnicodeEscapes(SAMPLE)
}
</script>
