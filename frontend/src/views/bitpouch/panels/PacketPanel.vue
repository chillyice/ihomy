<!-- 报文面板:十六进制 / 文本转字节,按字段模板拆包,给出逐字节表与校验值参考。 -->
<template>
  <div class="bpc-grid">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.packet.input') }}</span>
        <span>{{ byteCount }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <textarea
          v-model="input"
          class="bpc-textarea bpc-short"
          spellcheck="false"
          :placeholder="$t('tools.packet.placeholder')"
        ></textarea>
        <p class="bpc-hint">{{ mode === 'hex' ? $t('tools.packet.hexHint') : $t('tools.packet.textHint') }}</p>

        <div class="bpc-field">
          <label class="bpc-label" for="bp-packet-template">{{ $t('tools.packet.template') }}</label>
          <textarea
            id="bp-packet-template"
            v-model="fieldTemplate"
            class="bpc-textarea bpc-short"
            spellcheck="false"
            :placeholder="$t('tools.packet.templatePlaceholder')"
          ></textarea>
        </div>
        <p class="bpc-hint">{{ $t('tools.packet.templateHint') }}</p>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else-if="templateInfo.invalid.length" class="bpc-err">{{ templateInvalid }}</p>
        <p v-else-if="templateSummary.text" class="bpc-hint">{{ templateSummary.text }}</p>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.packet.sample') }}</button>
          <button class="bpc-btn" type="button" @click="input = ''">{{ $t('tools.packet.clear') }}</button>
        </div>
      </div>
    </section>

    <section class="bpc-stack">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.packet.fieldTable') }}</span>
          <BpcCopy :value="fieldCopy" :label="$t('tools.packet.copy')" />
        </div>
        <div class="bpc-card-bd">
          <div v-if="allFieldRows.length" class="bpc-table-wrap">
            <table class="bpc-table">
              <thead>
                <tr>
                  <th>{{ $t('tools.packet.cols.name') }}</th>
                  <th>{{ $t('tools.packet.cols.offset') }}</th>
                  <th>{{ $t('tools.packet.cols.length') }}</th>
                  <th>{{ $t('tools.packet.cols.hex') }}</th>
                  <th>{{ $t('tools.packet.cols.value') }}</th>
                  <th>{{ $t('tools.packet.cols.ascii') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in allFieldRows" :key="row.key">
                  <td>{{ row.name }}</td>
                  <td>{{ row.offsetText }}</td>
                  <td>{{ row.truncated ? $t('tools.packet.truncatedField') : row.length }}</td>
                  <td>{{ row.hex }}</td>
                  <td>{{ row.value }}</td>
                  <td>{{ row.ascii }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.packet.empty') }}</p>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.packet.checksum') }}</span>
          <span>{{ $t('tools.packet.fieldCount', { n: allFieldRows.length }) }}</span>
        </div>
        <div class="bpc-card-bd">
          <div class="bpc-rows">
            <div v-for="row in checksumRows" :key="row.key" class="bpc-row">
              <span class="bpc-row-k">{{ row.label }}</span>
              <span class="bpc-row-v">{{ row.value }}</span>
            </div>
          </div>
          <p class="bpc-hint">{{ $t('tools.packet.checksumHint') }}</p>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.packet.byteTable') }}</span>
          <span>{{ byteCount }}</span>
        </div>
        <div class="bpc-card-bd">
          <div v-if="byteData.rows.length" class="bpc-table-wrap">
            <table class="bpc-table">
              <thead>
                <tr>
                  <th>{{ $t('tools.packet.byteCols.offset') }}</th>
                  <th>{{ $t('tools.packet.byteCols.hex') }}</th>
                  <th>{{ $t('tools.packet.byteCols.dec') }}</th>
                  <th>{{ $t('tools.packet.byteCols.bin') }}</th>
                  <th>{{ $t('tools.packet.byteCols.ascii') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in byteData.rows" :key="row.offset">
                  <td>{{ row.offset }}</td>
                  <td>{{ row.hex }}</td>
                  <td>{{ row.dec }}</td>
                  <td>{{ row.bin }}</td>
                  <td>{{ row.ascii }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.packet.empty') }}</p>
          <p v-if="byteData.truncated" class="bpc-hint">{{ $t('tools.packet.byteTruncated', { n: byteData.rows.length }) }}</p>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import {
  PACKET_MAX_BYTES,
  PacketError,
  asciiOf,
  bigEndianValue,
  checksumSet,
  describeBytes,
  encodeUtf8,
  hexOf,
  parseFieldTemplate,
  parseHexBytes,
  splitFields,
} from '@/utils/packetParse'

const { t } = useI18n()

const SAMPLE_MESSAGE = 'AA 55 00 0C 01 03 00 01 00 02 5C 0A'
const mode = ref('hex')
const input = ref(SAMPLE_MESSAGE)
const fieldTemplate = ref(t('tools.packet.sampleTemplate'))

const modeOptions = computed(() => [
  { value: 'hex', label: t('tools.packet.modeHex') },
  { value: 'text', label: t('tools.packet.modeText') },
])

const parsed = computed(() => {
  if (!input.value.trim()) return { ok: true, bytes: new Uint8Array(0) }
  try {
    const bytes = mode.value === 'hex' ? parseHexBytes(input.value) : encodeUtf8(input.value)
    if (bytes.length > PACKET_MAX_BYTES) throw new PacketError('tooLong', { limit: PACKET_MAX_BYTES })
    return { ok: true, bytes }
  } catch (e) {
    return { ok: false, error: e instanceof PacketError ? e.code : 'invalidHex', limit: e?.limit }
  }
})

const errorText = computed(() => {
  if (parsed.value.ok) return ''
  return t(`tools.packet.errors.${parsed.value.error}`, { limit: parsed.value.limit ?? PACKET_MAX_BYTES })
})

const templateInfo = computed(() => parseFieldTemplate(fieldTemplate.value))
const templateInvalid = computed(() =>
  t('tools.packet.templateInvalid', { lines: templateInfo.value.invalid.map((item) => item.line).join(', ') }),
)

const bytes = computed(() => (parsed.value.ok ? parsed.value.bytes : new Uint8Array(0)))
const byteCount = computed(() => t('tools.packet.bytes', { n: bytes.value.length }))

const split = computed(() => splitFields(bytes.value, templateInfo.value.fields))

const templateSummary = computed(() => {
  const total = bytes.value.length
  const sum = templateInfo.value.total
  if (!total && !sum) return { text: '', warn: false }
  if (sum > total) return { text: t('tools.packet.templateOverflow', { sum, n: total }), warn: true }
  if (sum < total) return { text: t('tools.packet.templateRemainder', { sum, left: total - sum }), warn: false }
  return { text: t('tools.packet.templateTotal', { n: sum }), warn: false }
})

const offsetText = (n) => `0x${n.toString(16).toUpperCase().padStart(4, '0')}`

const valueText = (chunk) => {
  const value = bigEndianValue(chunk)
  return value === null ? t('tools.packet.valuePlaceholder') : value.toString()
}

const fieldRows = computed(() =>
  split.value.rows.map((row) => ({
    key: `field-${row.offset}`,
    name: row.name,
    offsetText: offsetText(row.offset),
    length: row.declared,
    truncated: row.truncated,
    hex: hexOf(row.bytes) || t('tools.packet.valuePlaceholder'),
    value: valueText(row.bytes),
    ascii: asciiOf(row.bytes) || t('tools.packet.valuePlaceholder'),
  })),
)

const restRow = computed(() => {
  const { offset, bytes: rest } = split.value.rest
  if (!rest.length) return null
  return {
    key: `rest-${offset}`,
    name: t('tools.packet.rest'),
    offsetText: offsetText(offset),
    length: rest.length,
    truncated: false,
    hex: hexOf(rest),
    value: valueText(rest),
    ascii: asciiOf(rest),
  }
})

const allFieldRows = computed(() => (restRow.value ? [...fieldRows.value, restRow.value] : fieldRows.value))

const fieldCopy = computed(
  () =>
    `${t('tools.packet.copyTitle')}\n${t('tools.packet.copyBytes', { n: bytes.value.length })}\n\n` +
    allFieldRows.value.map((row) => `${row.name}\t${row.offsetText}\t${row.length}\t${row.hex}\t${row.value}\t${row.ascii}`).join('\n'),
)

const byteData = computed(() => describeBytes(bytes.value, 64))

const checksumRows = computed(() => {
  const set = checksumSet(bytes.value)
  if (!bytes.value.length) return []
  return [
    { key: 'full-sum8', label: `${t('tools.packet.checksumFull')} · SUM8`, value: set.full.sum8 },
    { key: 'full-xor8', label: `${t('tools.packet.checksumFull')} · XOR8`, value: set.full.xor8 },
    { key: 'full-sum16', label: `${t('tools.packet.checksumFull')} · SUM16`, value: set.full.sum16 },
    { key: 'full-crc16', label: `${t('tools.packet.checksumFull')} · CRC16`, value: set.full.crc16 },
    { key: 'body-sum8', label: `${t('tools.packet.checksumBody')} · SUM8`, value: set.body.sum8 },
    { key: 'body-xor8', label: `${t('tools.packet.checksumBody')} · XOR8`, value: set.body.xor8 },
    { key: 'body-sum16', label: `${t('tools.packet.checksumBody')} · SUM16`, value: set.body.sum16 },
    { key: 'body-crc16', label: `${t('tools.packet.checksumBody')} · CRC16`, value: set.body.crc16 },
  ]
})

const loadSample = () => {
  mode.value = 'hex'
  input.value = SAMPLE_MESSAGE
  fieldTemplate.value = t('tools.packet.sampleTemplate')
}
</script>
