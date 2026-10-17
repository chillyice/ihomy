<!-- 报文解析:十六进制/文本报文拆字段、逐字节查看与校验值参考,全部在本机完成 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.packet.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="hex">{{ $t('tools.packet.modeHex') }}</el-radio-button>
          <el-radio-button value="text">{{ $t('tools.packet.modeText') }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tb-right">
        <el-button size="small" @click="loadSample">{{ $t('tools.packet.sample') }}</el-button>
        <el-button size="small" :disabled="!input && !fieldTemplate" @click="clearAll">{{ $t('tools.packet.clear') }}</el-button>
        <el-button size="small" :disabled="!parsed.ok || !byteData.total" @click="copyBreakdown">
          {{ $t('tools.packet.copy') }}
        </el-button>
      </div>
    </PageToolbar>

    <div class="packet-grid">
      <div class="packet-side">
        <div class="card packet-pane">
          <div class="packet-pane-head">
            <span class="section-label">{{ $t('tools.packet.input') }}</span>
            <span class="packet-meta">{{ parsed.ok ? $t('tools.packet.bytes', { n: byteData.total }) : '' }}</span>
          </div>
          <textarea
            v-model="input"
            class="packet-area packet-input"
            spellcheck="false"
            autocomplete="off"
            autocapitalize="off"
            :placeholder="$t('tools.packet.placeholder')"
          ></textarea>
          <div class="packet-hint">{{ mode === 'hex' ? $t('tools.packet.hexHint') : $t('tools.packet.textHint') }}</div>
        </div>

        <div class="card packet-pane">
          <div class="packet-pane-head">
            <span class="section-label">{{ $t('tools.packet.template') }}</span>
            <span class="packet-meta">{{ $t('tools.packet.templateTotal', { n: templateInfo.total }) }}</span>
          </div>
          <textarea
            v-model="fieldTemplate"
            class="packet-area packet-template"
            spellcheck="false"
            autocomplete="off"
            autocapitalize="off"
            :placeholder="$t('tools.packet.templatePlaceholder')"
          ></textarea>
          <div class="packet-hint">{{ $t('tools.packet.templateHint') }}</div>
          <div class="packet-hint">{{ $t('tools.packet.privacy') }}</div>
        </div>
      </div>

      <div class="card packet-pane packet-result">
        <div class="packet-pane-head">
          <span class="section-label">{{ $t('tools.packet.result') }}</span>
          <span v-if="parsed.ok && byteData.total" class="packet-meta">
            {{ $t('tools.packet.fieldCount', { n: allFieldRows.length }) }}
          </span>
        </div>

        <div v-if="!input.trim()" class="packet-empty">{{ $t('tools.packet.empty') }}</div>
        <pre v-else-if="!parsed.ok" class="packet-error">{{ errorText }}</pre>
        <template v-else>
          <div class="packet-stats">
            <span class="packet-stat">{{ $t('tools.packet.bytes', { n: byteData.total }) }}</span>
            <span v-if="templateSummary.text" class="packet-stat" :class="{ warn: templateSummary.warn }">
              {{ templateSummary.text }}
            </span>
          </div>
          <div v-if="invalidLinesText" class="packet-alert">{{ invalidLinesText }}</div>

          <div class="packet-block">
            <span class="section-label">{{ $t('tools.packet.fieldTable') }}</span>
            <div class="packet-table-wrap">
              <table class="packet-table">
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
                    <td class="packet-name">
                      {{ row.name }}
                      <span v-if="row.truncated" class="packet-tag">{{ $t('tools.packet.truncatedField') }}</span>
                    </td>
                    <td>
                      <code>{{ offsetText(row.offset) }}</code>
                    </td>
                    <td>{{ row.length }}</td>
                    <td>
                      <code>{{ row.hex }}</code>
                    </td>
                    <td>
                      <code>{{ row.value }}</code>
                    </td>
                    <td>
                      <code>{{ row.ascii }}</code>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div class="packet-block">
            <span class="section-label">{{ $t('tools.packet.byteTable') }}</span>
            <div class="packet-table-wrap">
              <table class="packet-table">
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
                    <td>
                      <code>{{ offsetText(row.offset) }}</code>
                    </td>
                    <td>
                      <code>{{ row.hex }}</code>
                    </td>
                    <td>{{ row.dec }}</td>
                    <td>
                      <code>{{ row.bin }}</code>
                    </td>
                    <td class="packet-ascii">{{ row.ascii }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-if="byteData.truncated" class="packet-hint">
              {{ $t('tools.packet.byteTruncated', { n: byteData.rows.length }) }}
            </div>
          </div>

          <div class="packet-block">
            <span class="section-label">{{ $t('tools.packet.checksum') }}</span>
            <div class="packet-table-wrap">
              <table class="packet-table">
                <thead>
                  <tr>
                    <th>{{ $t('tools.packet.algorithm') }}</th>
                    <th>{{ $t('tools.packet.checksumFull') }}</th>
                    <th>{{ $t('tools.packet.checksumBody') }}</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="alg in CHECKSUM_ALGORITHMS" :key="alg">
                    <td>{{ $t(`tools.packet.algorithms.${alg}`) }}</td>
                    <td>
                      <code>{{ checksums.full[alg] }}</code>
                    </td>
                    <td>
                      <code>{{ checksums.body[alg] }}</code>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="packet-hint">{{ $t('tools.packet.checksumHint') }}</div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  CHECKSUM_ALGORITHMS,
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
// 模式与字段模板存本机,下次打开保持;报文内容本身不落存储
const PREF_KEY = 'ihomy:tools:packet:v1'
const savedPrefs = (() => {
  try {
    return JSON.parse(localStorage.getItem(PREF_KEY)) || {}
  } catch {
    return {}
  }
})()

const mode = ref(savedPrefs.mode === 'text' ? 'text' : 'hex')
const input = ref(SAMPLE_MESSAGE)
const fieldTemplate = ref(typeof savedPrefs.template === 'string' ? savedPrefs.template : t('tools.packet.sampleTemplate'))

watch([mode, fieldTemplate], () => {
  try {
    localStorage.setItem(PREF_KEY, JSON.stringify({ mode: mode.value, template: fieldTemplate.value }))
  } catch {
    // 隐私模式下不落盘,本次会话内的选择仍然有效
  }
})

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

const invalidLinesText = computed(() =>
  templateInfo.value.invalid.length
    ? t('tools.packet.templateInvalid', { lines: templateInfo.value.invalid.map((item) => item.line).join(', ') })
    : '',
)

const split = computed(() => splitFields(parsed.value.ok ? parsed.value.bytes : new Uint8Array(0), templateInfo.value.fields))

const offsetText = (n) => `0x${n.toString(16).toUpperCase().padStart(4, '0')}`

const valueText = (bytes) => {
  const value = bigEndianValue(bytes)
  return value === null ? t('tools.packet.valuePlaceholder') : value.toString()
}

const fieldRows = computed(() =>
  split.value.rows.map((row) => ({
    key: `field-${row.offset}`,
    name: row.name,
    offset: row.offset,
    length: row.declared,
    truncated: row.truncated,
    hex: hexOf(row.bytes) || t('tools.packet.valuePlaceholder'),
    value: valueText(row.bytes),
    ascii: asciiOf(row.bytes) || t('tools.packet.valuePlaceholder'),
  })),
)

const restRow = computed(() => {
  const { offset, bytes } = split.value.rest
  if (!bytes.length) return null
  return {
    key: `rest-${offset}`,
    name: t('tools.packet.rest'),
    offset,
    length: bytes.length,
    truncated: false,
    hex: hexOf(bytes),
    value: valueText(bytes),
    ascii: asciiOf(bytes),
  }
})

const allFieldRows = computed(() => (restRow.value ? [...fieldRows.value, restRow.value] : fieldRows.value))

const byteData = computed(() => describeBytes(parsed.value.ok ? parsed.value.bytes : new Uint8Array(0)))

const checksums = computed(() => checksumSet(parsed.value.ok ? parsed.value.bytes : new Uint8Array(0)))

const templateSummary = computed(() => {
  const total = byteData.value.total
  const sum = templateInfo.value.total
  if (!total && !sum) return { text: '', warn: false }
  if (sum > total) return { text: t('tools.packet.templateOverflow', { sum, n: total }), warn: true }
  if (sum < total) return { text: t('tools.packet.templateRemainder', { sum, left: total - sum }), warn: false }
  return { text: t('tools.packet.templateTotal', { n: sum }), warn: false }
})

const copyBreakdown = async () => {
  const lines = [t('tools.packet.copyTitle'), t('tools.packet.copyBytes', { n: byteData.value.total }), '']
  if (allFieldRows.value.length) {
    lines.push(t('tools.packet.copyFieldHead'))
    allFieldRows.value.forEach((row, i) => {
      const tag = row.truncated ? ` [${t('tools.packet.truncatedField')}]` : ''
      lines.push(`${i + 1}. ${row.name} @${offsetText(row.offset)} (${row.length}) ${row.hex}${tag}`)
    })
    lines.push('')
  }
  lines.push(t('tools.packet.copyByteHead'))
  byteData.value.rows.forEach((row) => {
    lines.push(`${offsetText(row.offset)}  ${row.hex}  ${String(row.dec).padStart(3, ' ')}  ${row.bin}  ${row.ascii}`)
  })
  if (byteData.value.truncated) lines.push(t('tools.packet.byteTruncated', { n: byteData.value.rows.length }))
  lines.push('', t('tools.packet.copyChecksumHead'))
  for (const alg of CHECKSUM_ALGORITHMS) {
    lines.push(`${t(`tools.packet.algorithms.${alg}`)}: ${checksums.value.full[alg]} / ${checksums.value.body[alg]}`)
  }
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success(t('tools.packet.copied'))
  } catch {
    ElMessage.error(t('tools.packet.copyFailed'))
  }
}

const loadSample = () => {
  mode.value = 'hex'
  input.value = SAMPLE_MESSAGE
  fieldTemplate.value = t('tools.packet.sampleTemplate')
}

const clearAll = () => {
  input.value = ''
  fieldTemplate.value = ''
}
</script>

<style scoped>
.packet-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
  align-items: start;
}
.packet-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}
.packet-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.packet-pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.packet-pane-head .section-label,
.packet-block .section-label {
  margin: 0;
}
.packet-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.packet-area {
  display: block;
  margin: 10px 0 0;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
  outline: none;
}
.packet-input {
  min-height: 240px;
}
.packet-template {
  min-height: 150px;
}
.packet-area:focus {
  border-color: var(--color-accent);
}
.packet-area::placeholder {
  color: var(--color-text-secondary);
}
.packet-error {
  margin: 10px 0 0;
  padding: 12px 14px;
  min-height: 120px;
  border: 1px solid rgba(168, 72, 58, 0.35);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-accent);
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.packet-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
  min-height: 240px;
  padding: 12px 14px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.packet-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.packet-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.packet-stat {
  padding: 3px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-bg);
  font-size: 12px;
  color: var(--color-text-secondary);
}
.packet-stat.warn {
  border-color: rgba(168, 72, 58, 0.35);
  color: var(--color-accent);
}
.packet-alert {
  margin-top: 8px;
  padding: 8px 10px;
  border: 1px solid rgba(168, 72, 58, 0.25);
  border-radius: 8px;
  background: rgba(168, 72, 58, 0.06);
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-accent);
}
.packet-block {
  margin-top: 16px;
}
.packet-block .section-label {
  display: block;
  margin-bottom: 8px;
}
.packet-table-wrap {
  max-height: 320px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.packet-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.packet-table th,
.packet-table td {
  padding: 6px 10px;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-border);
}
.packet-table th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--color-card);
  font-weight: 600;
  color: var(--color-text-secondary);
}
.packet-table tbody tr:last-child td {
  border-bottom: 0;
}
.packet-table code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
}
.packet-name {
  font-weight: 600;
}
.packet-ascii {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  text-align: center;
}
.packet-tag {
  margin-left: 6px;
  padding: 1px 6px;
  border: 1px solid rgba(168, 72, 58, 0.3);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  color: var(--color-accent);
}
@media (max-width: 900px) {
  .packet-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .packet-input,
  .packet-empty {
    min-height: 180px;
  }
}
</style>
