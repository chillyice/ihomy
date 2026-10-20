<!-- 子网面板:IPv4 网络地址 / 广播地址 / 可用主机范围 / 掩码与二进制对照。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.subnet.title') }}</span>
        <span>/{{ parsed.ok ? parsed.info.prefix : '—' }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-field">
          <label class="bpc-label" for="bp-subnet-ip">{{ $t('tools.subnet.ipLabel') }}</label>
          <input
            id="bp-subnet-ip"
            v-model="ip"
            class="bpc-input"
            type="text"
            autocomplete="off"
            spellcheck="false"
            :placeholder="$t('tools.subnet.ipPlaceholder')"
          />
        </div>
        <div class="bpc-field">
          <label class="bpc-label" for="bp-subnet-mask">{{ $t('tools.subnet.maskLabel') }}</label>
          <input
            id="bp-subnet-mask"
            v-model="mask"
            class="bpc-input"
            type="text"
            autocomplete="off"
            spellcheck="false"
            :placeholder="$t('tools.subnet.maskPlaceholder')"
          />
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="loadSample">{{ $t('tools.subnet.sample') }}</button>
          <button class="bpc-btn" type="button" @click="clearAll">{{ $t('tools.subnet.clear') }}</button>
        </div>

        <p v-if="errorText" class="bpc-err">{{ errorText }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.subnet.hint') }}</p>
        <p v-if="parsed.ok && typeLabel" class="bpc-hint">{{ $t('tools.subnet.typeLabel') }}: {{ typeLabel }}</p>
      </div>
    </section>

    <section class="bpc-stack">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.subnet.result') }}</span>
          <BpcCopy :value="copyText" :label="$t('tools.subnet.copy')" />
        </div>
        <div class="bpc-card-bd">
          <div v-if="resultRows.length" class="bpc-rows">
            <div v-for="row in resultRows" :key="row.key" class="bpc-row">
              <span class="bpc-row-k">{{ row.label }}</span>
              <span class="bpc-row-v" :class="{ 'is-accent': row.key === 'network' }">{{ row.value }}</span>
            </div>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.subnet.empty') }}</p>
          <p v-if="parsed.ok" class="bpc-hint">{{ $t('tools.subnet.hostHint') }}</p>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.subnet.binaryTitle') }}</span>
        </div>
        <div class="bpc-card-bd">
          <div v-if="binaryRows.length" class="bpc-rows">
            <div v-for="row in binaryRows" :key="row.key" class="bpc-row">
              <span class="bpc-row-k">{{ row.label }}</span>
              <span class="bpc-row-v">{{ row.binary }}</span>
            </div>
          </div>
          <p v-else class="bpc-hint">{{ $t('tools.subnet.binaryHint') }}</p>
          <p v-if="binaryRows.length" class="bpc-hint">{{ $t('tools.subnet.binaryHint') }}</p>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { SubnetError, binaryWithPrefix, ipToString, parseIpv4, parseMask, subnetInfo } from '@/utils/subnet'

const { t } = useI18n()

const ip = ref('192.168.1.10')
const mask = ref('24')

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

const typeLabel = computed(() => (parsed.value.ok ? t(`tools.subnet.types.${parsed.value.info.type}`) : ''))

const formatNumber = (n) => n.toLocaleString('en-US')

const resultRows = computed(() => {
  if (!parsed.value.ok) return []
  const info = parsed.value.info
  return [
    { key: 'network', label: t('tools.subnet.labels.network'), value: ipToString(info.network) },
    { key: 'broadcast', label: t('tools.subnet.labels.broadcast'), value: ipToString(info.broadcast) },
    { key: 'range', label: t('tools.subnet.labels.range'), value: `${ipToString(info.first)} – ${ipToString(info.last)}` },
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
  return [
    ['ip', t('tools.subnet.rows.ip'), info.ip],
    ['mask', t('tools.subnet.rows.mask'), info.mask],
    ['wildcard', t('tools.subnet.rows.wildcard'), info.wildcard],
    ['network', t('tools.subnet.rows.network'), info.network],
    ['broadcast', t('tools.subnet.rows.broadcast'), info.broadcast],
  ].map(([key, label, value]) => ({
    key,
    label,
    binary: `${ipToString(value)} · ${binaryWithPrefix(value, info.prefix)}`,
  }))
})

const copyText = computed(() => resultRows.value.map((row) => `${row.label}: ${row.value}`).join('\n'))

const loadSample = () => {
  ip.value = t('tools.subnet.sampleIp')
  mask.value = t('tools.subnet.sampleMask')
}

const clearAll = () => {
  ip.value = ''
  mask.value = ''
}
</script>
