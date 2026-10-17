<!-- 子网掩码计算:IPv4 网络地址/广播地址/可用主机范围/掩码换算,全部在本机完成 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.subnet.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('tools.subnet.title') }}</span>
      </div>
      <div class="tb-right">
        <el-button size="small" @click="loadSample">{{ $t('tools.subnet.sample') }}</el-button>
        <el-button size="small" :disabled="!ip && !mask" @click="clearAll">{{ $t('tools.subnet.clear') }}</el-button>
        <el-button size="small" :disabled="!parsed.ok" @click="copyResult">{{ $t('tools.subnet.copy') }}</el-button>
      </div>
    </PageToolbar>

    <div class="subnet-grid">
      <div class="card subnet-pane">
        <div class="subnet-pane-head">
          <span class="section-label">{{ $t('tools.subnet.ipLabel') }}</span>
        </div>
        <el-input v-model="ip" class="subnet-input" spellcheck="false" autocomplete="off" :placeholder="$t('tools.subnet.ipPlaceholder')" />
        <div class="subnet-pane-head subnet-gap">
          <span class="section-label">{{ $t('tools.subnet.maskLabel') }}</span>
        </div>
        <el-input
          v-model="mask"
          class="subnet-input"
          spellcheck="false"
          autocomplete="off"
          :placeholder="$t('tools.subnet.maskPlaceholder')"
        />
        <div v-if="hasInput && !parsed.ok" class="subnet-alert">{{ errorText }}</div>
        <div class="subnet-hint">{{ $t('tools.subnet.hint') }}</div>
        <div class="subnet-hint">{{ $t('tools.subnet.hostHint') }}</div>
        <div class="subnet-hint">{{ $t('tools.subnet.privacy') }}</div>
      </div>

      <div class="card subnet-pane">
        <div class="subnet-pane-head">
          <span class="section-label">{{ $t('tools.subnet.result') }}</span>
          <span v-if="parsed.ok" class="subnet-type">
            {{ $t('tools.subnet.typeLabel') }} · {{ $t(`tools.subnet.types.${parsed.info.type}`) }}
          </span>
        </div>

        <div v-if="!hasInput" class="subnet-empty">{{ $t('tools.subnet.empty') }}</div>
        <div v-else-if="!parsed.ok" class="subnet-empty subnet-error-state">{{ errorText }}</div>
        <div v-else class="subnet-rows">
          <div v-for="row in resultRows" :key="row.key" class="subnet-row">
            <span class="subnet-key">{{ row.label }}</span>
            <code class="subnet-value">{{ row.value }}</code>
          </div>
        </div>
      </div>
    </div>

    <div v-if="parsed.ok" class="card subnet-binary">
      <div class="subnet-pane-head">
        <span class="section-label">{{ $t('tools.subnet.binaryTitle') }}</span>
      </div>
      <div class="subnet-table-wrap">
        <table class="subnet-table">
          <thead>
            <tr>
              <th>{{ $t('tools.subnet.cols.item') }}</th>
              <th>{{ $t('tools.subnet.cols.decimal') }}</th>
              <th>{{ $t('tools.subnet.cols.binary') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in binaryRows" :key="row.key">
              <td class="subnet-name">{{ row.label }}</td>
              <td>
                <code>{{ row.decimal }}</code>
              </td>
              <td class="subnet-bits">
                <code>{{ row.binary }}</code>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="subnet-hint">{{ $t('tools.subnet.binaryHint') }}</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { SubnetError, binaryWithPrefix, ipToString, parseIpv4, parseMask, subnetInfo } from '@/utils/subnet'

const { t } = useI18n()

const ip = ref('')
const mask = ref('')

const hasInput = computed(() => Boolean(ip.value.trim() || mask.value.trim()))

// IP 里带 /24 时以斜杠后的前缀为准,掩码输入框只在没有斜杠时参与计算
const parsed = computed(() => {
  const rawIp = ip.value.trim()
  if (!rawIp) return { ok: false, error: 'ipMissing' }
  const slash = rawIp.indexOf('/')
  const ipPart = slash >= 0 ? rawIp.slice(0, slash) : rawIp
  const maskPart = slash >= 0 ? rawIp.slice(slash + 1) : mask.value.trim()
  try {
    const parsedIp = parseIpv4(ipPart)
    const { prefix } = parseMask(maskPart)
    return { ok: true, info: subnetInfo(parsedIp.value, prefix) }
  } catch (e) {
    return { ok: false, error: e instanceof SubnetError ? e.code : 'ipInvalid' }
  }
})

const errorText = computed(() => (parsed.value.ok ? '' : t(`tools.subnet.errors.${parsed.value.error}`)))

const formatNumber = (n) => n.toLocaleString('en-US')

const resultRows = computed(() => {
  if (!parsed.value.ok) return []
  const info = parsed.value.info
  return [
    { key: 'network', label: t('tools.subnet.labels.network'), value: ipToString(info.network) },
    { key: 'broadcast', label: t('tools.subnet.labels.broadcast'), value: ipToString(info.broadcast) },
    {
      key: 'range',
      label: t('tools.subnet.labels.range'),
      value: `${ipToString(info.first)} – ${ipToString(info.last)}`,
    },
    { key: 'hosts', label: t('tools.subnet.labels.hosts'), value: formatNumber(info.hosts) },
    { key: 'total', label: t('tools.subnet.labels.total'), value: formatNumber(info.total) },
    { key: 'mask', label: t('tools.subnet.labels.mask'), value: ipToString(info.mask) },
    { key: 'wildcard', label: t('tools.subnet.labels.wildcard'), value: ipToString(info.wildcard) },
    { key: 'prefix', label: t('tools.subnet.labels.prefix'), value: `/${info.prefix}` },
  ]
})

const binaryRows = computed(() => {
  if (!parsed.value.ok) return []
  const info = parsed.value.info
  const rows = [
    ['ip', t('tools.subnet.rows.ip'), info.ip],
    ['mask', t('tools.subnet.rows.mask'), info.mask],
    ['wildcard', t('tools.subnet.rows.wildcard'), info.wildcard],
    ['network', t('tools.subnet.rows.network'), info.network],
    ['broadcast', t('tools.subnet.rows.broadcast'), info.broadcast],
  ]
  return rows.map(([key, label, value]) => ({
    key,
    label,
    decimal: ipToString(value),
    binary: binaryWithPrefix(value, info.prefix),
  }))
})

const copyResult = async () => {
  if (!parsed.value.ok) return
  const info = parsed.value.info
  const lines = [
    t('tools.subnet.copyTitle'),
    `${t('tools.subnet.rows.ip')}: ${ipToString(info.ip)}/${info.prefix}`,
    `${t('tools.subnet.labels.mask')}: ${ipToString(info.mask)}`,
    `${t('tools.subnet.labels.wildcard')}: ${ipToString(info.wildcard)}`,
    `${t('tools.subnet.labels.network')}: ${ipToString(info.network)}`,
    `${t('tools.subnet.labels.broadcast')}: ${ipToString(info.broadcast)}`,
    `${t('tools.subnet.labels.range')}: ${ipToString(info.first)} – ${ipToString(info.last)}`,
    t('tools.subnet.copyHosts', { n: formatNumber(info.hosts) }),
    t('tools.subnet.copyTotal', { n: formatNumber(info.total) }),
  ]
  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success(t('tools.subnet.copied'))
  } catch {
    ElMessage.error(t('tools.subnet.copyFailed'))
  }
}

const loadSample = () => {
  ip.value = t('tools.subnet.sampleIp')
  mask.value = t('tools.subnet.sampleMask')
}

const clearAll = () => {
  ip.value = ''
  mask.value = ''
}
</script>

<style scoped>
.subnet-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.subnet-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.subnet-pane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.subnet-pane-head .section-label {
  margin: 0;
}
.subnet-gap {
  margin-top: 18px;
}
.subnet-input :deep(.el-input__inner) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
}
.subnet-type {
  padding: 2px 8px;
  border: 1px solid rgba(168, 72, 58, 0.18);
  border-radius: 999px;
  background: rgba(168, 72, 58, 0.08);
  font-size: 12px;
  color: var(--color-accent);
  white-space: nowrap;
}
html.dark .subnet-type {
  border-color: rgba(225, 154, 140, 0.2);
  background: rgba(225, 154, 140, 0.12);
  color: #e19a8c;
}
.subnet-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.subnet-alert {
  margin-top: 12px;
  padding: 8px 10px;
  border: 1px solid rgba(168, 72, 58, 0.25);
  border-radius: 8px;
  background: rgba(168, 72, 58, 0.06);
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-accent);
}
.subnet-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 10px;
  min-height: 200px;
  padding: 12px 14px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  text-align: center;
}
.subnet-error-state {
  border-color: rgba(168, 72, 58, 0.35);
  color: var(--color-accent);
}
.subnet-rows {
  margin-top: 6px;
}
.subnet-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px dashed var(--color-border);
}
.subnet-row:last-child {
  border-bottom: 0;
}
.subnet-key {
  flex: 0 0 96px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.subnet-value {
  min-width: 0;
  overflow-wrap: anywhere;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-text);
}
.subnet-binary {
  margin-top: 16px;
}
.subnet-table-wrap {
  margin-top: 10px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.subnet-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.subnet-table th,
.subnet-table td {
  padding: 7px 10px;
  text-align: left;
  white-space: nowrap;
  border-bottom: 1px solid var(--color-border);
}
.subnet-table th {
  background: var(--color-card);
  font-weight: 600;
  color: var(--color-text-secondary);
}
.subnet-table tbody tr:last-child td {
  border-bottom: 0;
}
.subnet-table code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
}
.subnet-name {
  font-weight: 600;
}
.subnet-bits code {
  letter-spacing: 0.02em;
}
@media (max-width: 900px) {
  .subnet-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .subnet-empty {
    min-height: 140px;
  }
  .subnet-bits code {
    font-size: 11.5px;
  }
}
</style>
