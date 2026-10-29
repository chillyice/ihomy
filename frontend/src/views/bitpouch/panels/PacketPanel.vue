<!-- 网络报文面板:自动识别以太网 / IPv4 / IPv6 / ARP / TCP / UDP / ICMP / DNS / HTTP / TLS。
     左侧按输入行展示原文与 ASCII,右侧逐字段给出值与含义;悬停或点击字段查看解释。
     核心逻辑在 utils/packetDecode.js,与 bit_pouch 内置插件同一套规则;纯本地计算。 -->
<template>
  <div class="bpk">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.packet.input') }}</span>
        <span>{{ $t('tools.packet.bytes', { n: bytes.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />
        <textarea
          v-model="input"
          class="bpc-textarea bpk-source"
          spellcheck="false"
          :placeholder="$t('tools.packet.placeholder')"
        ></textarea>
        <p class="bpc-hint">{{ hint }}</p>
        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.packet.sample') }}</button>
          <button class="bpc-btn" type="button" @click="input = ''">{{ $t('tools.packet.clear') }}</button>
        </div>
      </div>
    </section>

    <div class="bpc-grid bpc-grid-wide">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.packet.rawPane') }}</span>
          <BpcCopy :value="packetHex" :label="$t('tools.packet.copy')" />
        </div>
        <div class="bpc-card-bd">
          <div v-if="!bytes.length" class="bpc-empty">{{ $t('tools.packet.empty') }}</div>
          <div v-else class="bpk-raw" @mousemove="moveTip" @mouseleave="hideTip">
            <p class="bpc-hint bpk-hint">{{ $t('tools.packet.rawHint') }}</p>
            <div v-for="(line, index) in lines" :key="index" class="bpk-line">
              <div v-if="mode === 'text'" class="bpk-hex bpk-text">{{ line.text || ' ' }}</div>
              <div v-else class="bpk-hex">
                <span
                  v-for="(byte, byteIndex) in line.bytes"
                  :key="byteIndex"
                  class="bpk-byte"
                  :class="{ 'is-on': isActive(line.offset + byteIndex) }"
                  @mouseenter="showTip(fieldAt(line.offset + byteIndex), $event)"
                >{{ hexByte(byte) }}</span>
              </div>
              <div class="bpk-ascii">{{ line.ascii || ' ' }}</div>
            </div>
          </div>
          <p v-if="bytes.length" class="bpc-hint">{{ $t('tools.packet.hoverHint') }}</p>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.packet.detailPane') }}</span>
          <BpcCopy :value="detailText" :label="$t('tools.packet.copy')" />
        </div>
        <div class="bpc-card-bd">
          <div v-if="!analysis" class="bpc-empty">{{ $t('tools.packet.empty') }}</div>
          <template v-else>
            <p class="bpk-format">{{ analysis.format }}</p>
            <div class="bpk-detail" @mousemove="moveTip" @mouseleave="hideTip">
              <section v-for="(layer, layerIndex) in analysis.layers" :key="layerIndex" class="bpk-layer">
                <header class="bpk-layer-head">
                  <span class="bpk-layer-name">{{ layer.name }}</span>
                  <span v-if="layer.note" class="bpk-layer-note">{{ layer.note }}</span>
                </header>
                <div
                  v-for="(field, fieldIndex) in layer.fields"
                  :key="fieldIndex"
                  class="bpk-field"
                  :class="{ 'is-pinned': pinned === `${layerIndex}:${fieldIndex}` }"
                  tabindex="0"
                  @mouseenter="showTip(field, $event)"
                  @focus="showTip(field, $event)"
                  @click="togglePin(`${layerIndex}:${fieldIndex}`)"
                  @keydown.enter.prevent="togglePin(`${layerIndex}:${fieldIndex}`)"
                >
                  <span class="bpk-fname">{{ field.name }}</span>
                  <span class="bpk-fval">{{ field.value }}</span>
                  <span class="bpk-fpos">+{{ field.offset }} · {{ field.length }}B</span>
                  <p v-if="pinned === `${layerIndex}:${fieldIndex}`" class="bpk-fmean">{{ field.meaning }}</p>
                </div>
              </section>
              <p v-for="(warning, index) in analysis.warnings" :key="`w-${index}`" class="bpk-warn">· {{ warning }}</p>
            </div>
          </template>
        </div>
      </section>
    </div>

    <details v-if="bytes.length" class="bpc-card bpk-adv">
      <summary class="bpc-card-hd">{{ $t('tools.packet.advanced') }}</summary>
      <div class="bpc-card-bd">
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
        <p v-if="templateInfo.invalid.length" class="bpc-err">{{ templateInvalid }}</p>

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

        <div class="bpc-rows">
          <div v-for="row in checksumRows" :key="row.key" class="bpc-row">
            <span class="bpc-row-k">{{ row.label }}</span>
            <span class="bpc-row-v">{{ row.value }}</span>
          </div>
        </div>
        <p class="bpc-hint">{{ $t('tools.packet.checksumHint') }}</p>

        <div class="bpc-table-wrap">
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
        <p v-if="byteData.truncated" class="bpc-hint">{{ $t('tools.packet.byteTruncated', { n: byteData.rows.length }) }}</p>
      </div>
    </details>

    <div v-if="tip" class="bpk-tip" :style="{ left: `${tip.x}px`, top: `${tip.y}px` }">
      <div class="bpk-tip-head"><strong>{{ tip.name }}</strong><span>{{ tip.value }}</span></div>
      <p>{{ tip.meaning }}</p>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { analyzePacket, parsePacketInput } from '@/utils/packetDecode'
import {
  PACKET_MAX_BYTES,
  bigEndianValue,
  checksumSet,
  describeBytes,
  hexOf,
  parseFieldTemplate,
  splitFields,
} from '@/utils/packetParse'

const { t, locale } = useI18n()

const SAMPLE = `00 11 22 33 44 55 66 77 88 99 AA BB 08 00
45 00 00 2D 1C 46 40 00 40 06 9C 6C C0 A8 00 01 C0 A8 00 C7
00 50 01 BB 00 00 00 00 00 00 00 00 50 02 20 00 00 00 00 00
68 65 6C 6C 6F`

const mode = ref('auto')
const input = ref(SAMPLE)
const fieldTemplate = ref('')
const pinned = ref('')
const tip = ref(null)
const activeRange = ref(null)

const modeOptions = computed(() => [
  { value: 'auto', label: t('tools.packet.modeAuto') },
  { value: 'hex', label: t('tools.packet.modeHex') },
  { value: 'text', label: t('tools.packet.modeText') },
])

const hint = computed(() =>
  mode.value === 'text' ? t('tools.packet.textHint') : t('tools.packet.hexHint'),
)

const parsed = computed(() => {
  if (!input.value.trim()) return { ok: true, value: { bytes: new Uint8Array(0), lines: [], mode: 'hex' } }
  try {
    return { ok: true, value: parsePacketInput(input.value, mode.value) }
  } catch (error) {
    return { ok: false, code: error?.code || 'invalidHex', limit: error?.limit ?? PACKET_MAX_BYTES }
  }
})

const errorText = computed(() => (parsed.value.ok ? '' : t(`tools.packet.errors.${parsed.value.code}`, { limit: parsed.value.limit })))
const bytes = computed(() => (parsed.value.ok ? parsed.value.value.bytes : new Uint8Array(0)))
const lines = computed(() => (parsed.value.ok ? parsed.value.value.lines : []))
const packetHex = computed(() => hexOf(bytes.value))
const analysis = computed(() => (parsed.value.ok && bytes.value.length ? analyzePacket(bytes.value, locale.value) : null))
const flatFields = computed(() => analysis.value?.layers.flatMap((layer) => layer.fields) || [])

const detailText = computed(() => {
  if (!analysis.value) return ''
  const rows = [`${analysis.value.format} · ${bytes.value.length} bytes`]
  for (const layer of analysis.value.layers) {
    rows.push(`\n[${layer.name}]`)
    for (const field of layer.fields) rows.push(`${field.name}: ${field.value}  (+${field.offset}, ${field.length}B)  ${field.meaning}`)
  }
  return rows.join('\n')
})

const hexByte = (byte) => byte.toString(16).toUpperCase().padStart(2, '0')

function fieldAt(offset) {
  let best = null
  for (const field of flatFields.value) {
    if (field.length > 0 && offset >= field.offset && offset < field.offset + field.length) {
      if (!best || field.length < best.length) best = field
    }
  }
  return best
}

const isActive = (offset) => Boolean(activeRange.value && offset >= activeRange.value[0] && offset < activeRange.value[1])

function moveTip(event) {
  if (!tip.value) return
  tip.value.x = Math.min(event.clientX + 14, Math.max(8, window.innerWidth - 316))
  tip.value.y = Math.min(event.clientY + 16, Math.max(8, window.innerHeight - 150))
}

function showTip(field, event) {
  if (!field) return
  activeRange.value = [field.offset, field.offset + field.length]
  tip.value = { x: 0, y: 0, name: field.name, value: field.value, meaning: field.meaning }
  if (event instanceof MouseEvent) {
    moveTip(event)
    return
  }
  const rect = event.target?.getBoundingClientRect?.()
  tip.value.x = rect ? Math.min(rect.left + 12, Math.max(8, window.innerWidth - 316)) : 16
  tip.value.y = rect ? Math.min(rect.bottom + 7, Math.max(8, window.innerHeight - 150)) : 16
}

function hideTip() {
  tip.value = null
  activeRange.value = null
}

function togglePin(key) {
  pinned.value = pinned.value === key ? '' : key
}

function loadSample() {
  input.value = SAMPLE
  mode.value = 'auto'
}

const templateInfo = computed(() => parseFieldTemplate(fieldTemplate.value))
const templateInvalid = computed(() =>
  t('tools.packet.templateInvalid', { lines: templateInfo.value.invalid.map((item) => item.line).join(', ') }),
)
const split = computed(() => splitFields(bytes.value, templateInfo.value.fields))
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
    ascii: asciiText(row.bytes),
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
    ascii: asciiText(rest),
  }
})
const allFieldRows = computed(() => (restRow.value ? [...fieldRows.value, restRow.value] : fieldRows.value))

function asciiText(chunk) {
  return Array.from(chunk, (byte) => (byte === 0x20 ? '␠' : byte > 0x20 && byte < 0x7f ? String.fromCharCode(byte) : '·')).join('')
}

const byteData = computed(() => describeBytes(bytes.value, 64))
const checksumRows = computed(() => {
  const set = checksumSet(bytes.value)
  if (!bytes.value.length) return []
  return [
    { key: 'sum8', label: `${t('tools.packet.checksumFull')} · SUM8`, value: set.full.sum8 },
    { key: 'xor8', label: `${t('tools.packet.checksumFull')} · XOR8`, value: set.full.xor8 },
    { key: 'sum16', label: `${t('tools.packet.checksumFull')} · SUM16`, value: set.full.sum16 },
    { key: 'crc16', label: `${t('tools.packet.checksumFull')} · CRC16`, value: set.full.crc16 },
    { key: 'body-sum8', label: `${t('tools.packet.checksumBody')} · SUM8`, value: set.body.sum8 },
    { key: 'body-xor8', label: `${t('tools.packet.checksumBody')} · XOR8`, value: set.body.xor8 },
    { key: 'body-sum16', label: `${t('tools.packet.checksumBody')} · SUM16`, value: set.body.sum16 },
    { key: 'body-crc16', label: `${t('tools.packet.checksumBody')} · CRC16`, value: set.body.crc16 },
  ]
})
</script>

<style scoped>
.bpk {
  display: grid;
  gap: 16px;
}
.bpk-source {
  min-height: 96px;
}
.bpk-raw,
.bpk-detail {
  font-family: var(--mono);
  font-size: 12px;
  line-height: 1.6;
  max-height: 62vh;
  overflow: auto;
}
.bpk-hint {
  margin-top: 0;
}
.bpk-line + .bpk-line {
  margin-top: 7px;
  padding-top: 7px;
  border-top: 1px dashed var(--line);
}
.bpk-hex {
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text);
}
.bpk-text {
  color: var(--text-dim);
  font-family: var(--mono);
}
.bpk-byte {
  display: inline-block;
  margin-right: 5px;
  padding: 0 1px;
  border-radius: 3px;
  cursor: crosshair;
}
.bpk-byte.is-on {
  background: var(--accent-soft);
  color: var(--accent);
}
.bpk-ascii {
  margin-top: 2px;
  color: var(--text-faint);
  white-space: pre-wrap;
  word-break: break-all;
}
.bpk-format {
  margin: 0 0 10px;
  padding: 7px 9px;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--bg);
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--accent);
  overflow-wrap: anywhere;
}
.bpk-layer + .bpk-layer {
  margin-top: 12px;
}
.bpk-layer-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 6px;
  border-bottom: 1px solid var(--line);
}
.bpk-layer-name {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-faint);
}
.bpk-layer-note {
  margin-left: auto;
  font-size: 10px;
  color: var(--text-faint);
}
.bpk-field {
  display: grid;
  grid-template-columns: minmax(96px, 0.9fr) minmax(0, 1.5fr) auto;
  gap: 4px 10px;
  align-items: baseline;
  padding: 5px 6px;
  border-radius: 2px;
  cursor: help;
}
.bpk-field:hover,
.bpk-field:focus-visible,
.bpk-field.is-pinned {
  background: var(--line-soft);
  outline: none;
}
.bpk-fname {
  color: var(--text-dim);
  font-size: 11.5px;
}
.bpk-fval {
  font-family: var(--mono);
  font-size: 11.5px;
  color: var(--accent);
  overflow-wrap: anywhere;
}
.bpk-fpos {
  font-family: var(--mono);
  font-size: 9.5px;
  color: var(--text-faint);
  white-space: nowrap;
}
.bpk-fmean {
  grid-column: 1 / -1;
  margin: 2px 0 0;
  padding-top: 4px;
  border-top: 1px dashed var(--line);
  color: var(--text-dim);
  font-size: 11px;
  line-height: 1.55;
}
.bpk-warn {
  margin: 8px 0 0;
  color: var(--tan);
  font-size: 11.5px;
  line-height: 1.55;
}
.bpk-adv > summary {
  cursor: pointer;
  list-style: none;
}
.bpk-adv > summary::-webkit-details-marker {
  display: none;
}
.bpk-tip {
  position: fixed;
  z-index: var(--z-action);
  max-width: 300px;
  padding: 9px 11px;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--panel);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.45);
  pointer-events: none;
}
.bpk-tip-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}
.bpk-tip-head strong {
  font-size: 11.5px;
  color: var(--text);
}
.bpk-tip-head span {
  margin-left: auto;
  font-family: var(--mono);
  font-size: 10.5px;
  color: var(--accent);
}
.bpk-tip p {
  margin: 0;
  font-size: 11px;
  line-height: 1.55;
  color: var(--dim);
}
</style>
