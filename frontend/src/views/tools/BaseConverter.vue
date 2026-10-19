<!-- 进制转换:2–36 进制互转,整数部分 BigInt 精确,小数按目标进制逐位换算 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.base.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('tools.base.inputLabel') }}</span>
        <span class="base-meta">{{ $t('tools.base.length', { n: input.length }) }}</span>
      </div>
      <div class="tb-right">
        <el-checkbox v-model="uppercase" size="small">{{ $t('tools.base.uppercase') }}</el-checkbox>
        <el-button size="small" @click="loadSample">{{ $t('tools.base.sample') }}</el-button>
        <el-button size="small" :disabled="!input" @click="clearAll">{{ $t('tools.base.clear') }}</el-button>
      </div>
    </PageToolbar>

    <div class="base-grid">
      <div class="card base-pane">
        <div class="base-field">
          <label for="base-source">{{ $t('tools.base.sourceBase') }}</label>
          <el-select id="base-source" v-model="fromBase" size="small">
            <el-option v-for="base in ALL_BASES" :key="base" :value="base" :label="$t('tools.base.baseLabel', { n: base })" />
          </el-select>
        </div>

        <textarea
          v-model="input"
          class="base-area"
          spellcheck="false"
          autocomplete="off"
          autocapitalize="off"
          :placeholder="$t('tools.base.placeholder')"
        ></textarea>

        <div v-if="errorText" class="base-error">{{ errorText }}</div>
        <div v-else class="base-hint">{{ $t('tools.base.fractionHint') }}</div>
        <div class="base-hint">{{ $t('tools.base.privacy') }}</div>
      </div>

      <div class="card base-pane">
        <div class="base-pane-head">
          <span class="section-label">{{ $t('tools.base.results') }}</span>
          <div class="base-custom">
            <label for="base-custom">{{ $t('tools.base.customBase') }}</label>
            <el-input-number id="base-custom" v-model="customBase" :min="BASE_MIN" :max="BASE_MAX" size="small" controls-position="right" />
          </div>
        </div>

        <div class="base-results">
          <div v-for="row in rows" :key="row.base" class="base-row">
            <div class="base-row-head">
              <span class="base-label">{{ $t('tools.base.baseLabel', { n: row.base }) }}</span>
              <span v-if="row.approximate" class="base-tag">{{ $t('tools.base.approxTag') }}</span>
              <el-button size="small" text :disabled="!row.text" @click="copy(row.text)">
                {{ $t('tools.base.copy') }}
              </el-button>
            </div>
            <code class="base-value">{{ row.text || '—' }}</code>
          </div>
        </div>
      </div>
    </div>

    <div class="base-tools">
      <div class="card base-tool">
        <div class="base-pane-head">
          <span class="section-label">{{ $t('tools.base.float.title') }}</span>
          <el-radio-group v-model="floatSize" size="small">
            <el-radio-button value="single">{{ $t('tools.base.float.single') }}</el-radio-button>
            <el-radio-button value="double">{{ $t('tools.base.float.double') }}</el-radio-button>
          </el-radio-group>
        </div>
        <div class="base-tool-row">
          <el-input v-model="floatInput" size="small" spellcheck="false" :placeholder="$t('tools.base.float.valuePlaceholder')" />
          <el-button size="small" @click="runFloatToHex">{{ $t('tools.base.float.toHex') }}</el-button>
        </div>
        <div class="base-tool-row">
          <el-input v-model="floatHex" size="small" spellcheck="false" :placeholder="$t('tools.base.float.hexPlaceholder')" />
          <el-button size="small" @click="runHexToFloat">{{ $t('tools.base.float.toValue') }}</el-button>
        </div>
        <div v-if="floatError" class="base-error">{{ floatError }}</div>
        <div v-else-if="floatInfo" class="base-tool-out">
          <div class="base-tool-line">
            <span>{{ $t('tools.base.float.value') }}</span
            ><code>{{ floatInfo.value }}</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.float.kind') }}</span
            ><code>{{ $t(`tools.base.float.kinds.${floatInfo.kind}`) }}</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.float.sign') }}</span
            ><code>{{ floatInfo.signLabel }}</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.float.exponent') }}</span
            ><code>{{ floatInfo.exponent }} ({{ $t('tools.base.float.real', { n: floatInfo.realExponent }) }})</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.float.mantissa') }}</span
            ><code>0x{{ floatInfo.mantissa }}</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.float.hex') }}</span
            ><code>{{ floatInfo.hex }}</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.float.binary') }}</span
            ><code class="base-binary">{{ floatInfo.binary }}</code>
          </div>
        </div>
      </div>

      <div class="card base-tool">
        <div class="base-pane-head">
          <span class="section-label">{{ $t('tools.base.bitwise.title') }}</span>
        </div>
        <div class="base-tool-row">
          <el-input v-model="bitLeft" size="small" spellcheck="false" :placeholder="$t('tools.base.bitwise.left')" />
          <el-select v-model="bitOp" size="small" style="width: 96px">
            <el-option v-for="op in BITWISE_OPS" :key="op" :value="op" :label="op" />
          </el-select>
          <el-input
            v-model="bitRight"
            size="small"
            spellcheck="false"
            :disabled="bitOp === 'NOT'"
            :placeholder="bitOp === 'SHL' || bitOp === 'SHR' ? $t('tools.base.bitwise.shift') : $t('tools.base.bitwise.right')"
          />
        </div>
        <div class="base-tool-row">
          <el-select v-model="bitBase" size="small" style="width: 108px">
            <el-option v-for="base in [2, 8, 10, 16]" :key="base" :value="base" :label="$t('tools.base.baseLabel', { n: base })" />
          </el-select>
          <el-select v-model="bitWidth" size="small" style="width: 108px">
            <el-option v-for="width in BITWISE_WIDTHS" :key="width" :value="width" :label="$t('tools.base.bitwise.width', { n: width })" />
          </el-select>
          <el-button size="small" type="primary" @click="runBitwise">{{ $t('tools.base.bitwise.run') }}</el-button>
        </div>
        <div v-if="bitError" class="base-error">{{ bitError }}</div>
        <div v-else-if="bitResult" class="base-tool-out">
          <div class="base-tool-line">
            <span>{{ $t('tools.base.bitwise.unsigned') }}</span
            ><code>{{ bitResult.unsigned }}</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.bitwise.signed') }}</span
            ><code>{{ bitResult.signed }}</code>
          </div>
          <div class="base-tool-line">
            <span>HEX</span><code>{{ bitResult.hex }}</code>
          </div>
          <div class="base-tool-line">
            <span>{{ $t('tools.base.bitwise.binary') }}</span
            ><code class="base-binary">{{ bitResult.binary }}</code>
          </div>
        </div>
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
  BASE_MAX,
  BASE_MIN,
  BITWISE_OPS,
  BITWISE_WIDTHS,
  COMMON_BASES,
  bitwiseCalc,
  convertAll,
  describeFloat,
  floatToHex,
  hexToFloat,
} from '@/utils/baseConvert'

const { t } = useI18n()

const ALL_BASES = Array.from({ length: BASE_MAX - BASE_MIN + 1 }, (_, i) => BASE_MIN + i)
// 偏好只存本机;数值本身不落任何存储
const PREF_KEY = 'ihomy:tools:base:v1'
const saved = (() => {
  try {
    return JSON.parse(localStorage.getItem(PREF_KEY)) || {}
  } catch {
    return {}
  }
})()

const input = ref('')
const fromBase = ref(ALL_BASES.includes(saved.fromBase) ? saved.fromBase : 10)
const customBase = ref(ALL_BASES.includes(saved.customBase) ? saved.customBase : 7)
const uppercase = ref(saved.uppercase !== false)

watch([fromBase, customBase, uppercase], () => {
  try {
    localStorage.setItem(PREF_KEY, JSON.stringify({ fromBase: fromBase.value, customBase: customBase.value, uppercase: uppercase.value }))
  } catch {
    // 隐私模式下不落盘,本次会话内的选择仍然有效
  }
})

const result = computed(() => {
  if (!input.value.trim()) return { ok: true, results: [] }
  return convertAll(input.value, fromBase.value, [...COMMON_BASES, customBase.value], { uppercase: uppercase.value })
})

const rows = computed(() => {
  const bases = [...COMMON_BASES, customBase.value]
    .filter((base) => Number.isInteger(base))
    .filter((base, idx, list) => list.indexOf(base) === idx)
  // 空输入或输入有误时保留各行占位,第一次打开就能看出会换算哪几种进制
  if (result.value.ok && input.value.trim()) return result.value.results
  return bases.map((base) => ({ base, text: '', approximate: false }))
})

const ERROR_KEYS = {
  invalidBase: 'errorBase',
  invalidDigit: 'errorDigit',
  invalidNumber: 'errorNumber',
  tooLong: 'errorTooLong',
  empty: 'errorEmpty',
}

const errorText = computed(() => {
  if (!input.value.trim() || result.value.ok) return ''
  const key = ERROR_KEYS[result.value.error] || 'errorNumber'
  return t(`tools.base.${key}`, { char: result.value.char, base: fromBase.value, min: BASE_MIN, max: BASE_MAX })
})

const copy = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('tools.base.copied'))
  } catch {
    ElMessage.error(t('tools.base.copyFailed'))
  }
}

const clearAll = () => {
  input.value = ''
}

const SAMPLES = [
  { value: '255', base: 10 },
  { value: 'FF', base: 16 },
  { value: '1010.11', base: 2 },
  { value: '12345678901234567890', base: 10 },
]

const loadSample = () => {
  const next = SAMPLES.find((sample) => sample.value !== input.value) || SAMPLES[0]
  input.value = next.value
  fromBase.value = next.base
}

// ---------- IEEE 754 与位运算 ----------
const floatSize = ref('single')
const floatInput = ref('')
const floatHex = ref('')
const floatInfo = ref(null)
const floatError = ref('')

const runFloatToHex = () => {
  floatError.value = ''
  try {
    const hex = floatToHex(Number(floatInput.value), { double: floatSize.value === 'double' })
    floatHex.value = hex
    floatInfo.value = describeFloat(hex, { double: floatSize.value === 'double' })
  } catch {
    floatInfo.value = null
    floatError.value = t('tools.base.float.invalidValue')
  }
}

const runHexToFloat = () => {
  floatError.value = ''
  try {
    const parsed = hexToFloat(floatHex.value, { double: floatSize.value === 'double' })
    floatInput.value = String(parsed.value)
    floatInfo.value = describeFloat(parsed.hex, { double: floatSize.value === 'double' })
  } catch {
    floatInfo.value = null
    floatError.value = t('tools.base.float.invalidHex', { bytes: floatSize.value === 'double' ? 8 : 4 })
  }
}

const bitLeft = ref('')
const bitRight = ref('')
const bitOp = ref('AND')
const bitBase = ref(16)
const bitWidth = ref(32)
const bitResult = ref(null)
const bitError = ref('')

const runBitwise = () => {
  bitError.value = ''
  bitResult.value = null
  try {
    bitResult.value = bitwiseCalc(bitLeft.value || '0', bitRight.value || '0', bitOp.value, {
      base: bitBase.value,
      width: bitWidth.value,
    })
  } catch (e) {
    const code = e?.code || 'invalidNumber'
    bitError.value = t(`tools.base.bitwise.errors.${code}`, { char: e?.char ?? '', base: bitBase.value })
  }
}
</script>

<style scoped>
.base-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
}
.base-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.base-field {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.base-field label {
  white-space: nowrap;
}
.base-area {
  display: block;
  margin: 12px 0 0;
  width: 100%;
  min-height: 260px;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.7;
  resize: vertical;
  outline: none;
  overflow-wrap: anywhere;
  word-break: break-all;
}
.base-area:focus {
  border-color: var(--color-accent);
}
.base-area::placeholder {
  color: var(--color-text-secondary);
}
.base-error {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--color-accent);
  line-height: 1.6;
}
.base-hint {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.base-pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.base-pane-head .section-label {
  margin: 0;
}
.base-custom {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.base-custom :deep(.el-input-number) {
  width: 108px;
}
.base-meta {
  margin-left: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.base-results {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.base-row {
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px 10px;
  background: var(--color-bg);
}
.base-row-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.base-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.base-tag {
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--color-accent);
  border: 1px solid currentColor;
}
.base-row-head :deep(.el-button) {
  margin-left: auto;
}
.base-value {
  display: block;
  margin-top: 4px;
  overflow-wrap: anywhere;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text);
}
.base-tools {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  align-items: start;
}
.base-tool {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.base-tool-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.base-tool-out {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.base-tool-line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.base-tool-line code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, 'Courier New', monospace;
  font-size: 12.5px;
  color: var(--color-text);
  overflow-wrap: anywhere;
  text-align: right;
}
.base-binary {
  word-break: break-all;
}
@media (max-width: 900px) {
  .base-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .base-area {
    min-height: 180px;
  }
}
</style>
