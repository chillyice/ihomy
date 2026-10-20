<!-- 随机生成器面板:密码 / PIN / UUID / ULID / NanoID / 随机字节 / 随机整数。
     随机数来自浏览器加密安全随机源,结果不保存。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.generator.options') }}</span>
        <span>{{ mode.toUpperCase() }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <div class="bpc-row-2">
          <div v-if="supportsLength" class="bpc-field">
            <label class="bpc-label" for="bp-gen-length">
              {{ mode === 'bytes' ? $t('tools.generator.byteLength') : $t('tools.generator.length') }}
            </label>
            <input
              id="bp-gen-length"
              v-model.number="length"
              class="bpc-input"
              type="number"
              min="1"
              :max="mode === 'bytes' ? 1024 : 128"
            />
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-gen-count">{{ $t('tools.generator.count') }}</label>
            <input id="bp-gen-count" v-model.number="count" class="bpc-input" type="number" min="1" max="200" />
          </div>
        </div>

        <div v-if="mode === 'number'" class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-gen-min">{{ $t('tools.generator.min') }}</label>
            <input id="bp-gen-min" v-model="min" class="bpc-input" type="number" />
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-gen-max">{{ $t('tools.generator.max') }}</label>
            <input id="bp-gen-max" v-model="max" class="bpc-input" type="number" />
          </div>
        </div>

        <div v-if="mode === 'uuid'" class="bpc-field">
          <span class="bpc-label">{{ $t('tools.generator.version') }}</span>
          <BpcChips v-model="version" :options="versionOptions" />
        </div>

        <div v-if="mode === 'bytes'" class="bpc-field">
          <span class="bpc-label">{{ $t('tools.generator.format') }}</span>
          <BpcChips v-model="byteFormat" :options="byteFormatOptions" />
        </div>

        <template v-if="mode === 'password'">
          <div class="bpc-field">
            <span class="bpc-label">{{ $t('tools.generator.options') }}</span>
            <div class="bpc-chips">
              <button
                v-for="opt in charsetOptions"
                :key="opt.key"
                class="bpc-chip"
                :class="{ 'is-on': charset[opt.key] }"
                type="button"
                :aria-pressed="charset[opt.key]"
                @click="charset[opt.key] = !charset[opt.key]"
              >
                <span class="bpc-chip-dot" aria-hidden="true"></span>{{ opt.label }}
              </button>
            </div>
          </div>
          <div class="bpc-actions">
            <button
              class="bpc-chip"
              :class="{ 'is-on': charset.excludeAmbiguous }"
              type="button"
              :aria-pressed="charset.excludeAmbiguous"
              @click="charset.excludeAmbiguous = !charset.excludeAmbiguous"
            >
              {{ $t('tools.generator.excludeAmbiguous') }}
            </button>
            <button
              class="bpc-chip"
              :class="{ 'is-on': charset.requireEach }"
              type="button"
              :aria-pressed="charset.requireEach"
              @click="charset.requireEach = !charset.requireEach"
            >
              {{ $t('tools.generator.requireEach') }}
            </button>
          </div>
        </template>

        <div class="bpc-actions">
          <button class="bpc-btn bpc-btn-primary" type="button" @click="generate">{{ $t('tools.generator.regenerate') }}</button>
        </div>

        <p v-if="error" class="bpc-err">{{ error }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.generator.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.generator.result') }}</span>
        <BpcCopy :value="output" :label="$t('tools.generator.copy')" />
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall" :class="{ 'bpc-empty': !output }">{{ output || $t('tools.generator.empty') }}</pre>
        <div class="bpc-meta">
          <span>{{ $t('tools.generator.countInfo', { n: items.length }) }}</span>
          <span v-if="entropyText">{{ entropyText }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { run } from '@/utils/tools/generatorTool'

const { t, locale } = useI18n()

const mode = ref('password')
const count = ref(1)
const length = ref(20)
const version = ref('v4')
const byteFormat = ref('hex')
const min = ref(1)
const max = ref(100)
const charset = reactive({ lower: true, upper: true, digits: true, symbols: true, excludeAmbiguous: true, requireEach: true })

const items = ref([])
const output = ref('')
const entropy = ref(null)
const error = ref('')

const modeOptions = computed(() => [
  { value: 'password', label: t('tools.generator.modePassword') },
  { value: 'pin', label: t('tools.generator.modePin') },
  { value: 'uuid', label: t('tools.generator.modeUuid') },
  { value: 'ulid', label: t('tools.generator.modeUlid') },
  { value: 'nano', label: t('tools.generator.modeNano') },
  { value: 'bytes', label: t('tools.generator.modeBytes') },
  { value: 'number', label: t('tools.generator.modeNumber') },
])

const versionOptions = computed(() => [
  { value: 'v4', label: 'v4' },
  { value: 'v7', label: 'v7' },
])

const byteFormatOptions = computed(() => [
  { value: 'hex', label: t('tools.hash.hex') },
  { value: 'base64', label: t('tools.hash.base64') },
])

const charsetOptions = computed(() => [
  { key: 'lower', label: t('tools.generator.lower') },
  { key: 'upper', label: t('tools.generator.upper') },
  { key: 'digits', label: t('tools.generator.digits') },
  { key: 'symbols', label: t('tools.generator.symbols') },
])

const supportsLength = computed(() => ['password', 'pin', 'nano', 'bytes'].includes(mode.value))

const entropyText = computed(() => {
  if (!entropy.value) return ''
  return t('tools.generator.entropyInfo', {
    bits: entropy.value.bits,
    strength: t(`tools.generator.strength.${entropy.value.strength}`),
  })
})

const generate = () => {
  const options = {
    mode: mode.value,
    count: Number(count.value) || 1,
    length: Number(length.value) || undefined,
    version: version.value,
    format: byteFormat.value,
    min: min.value,
    max: max.value,
    lower: charset.lower,
    upper: charset.upper,
    digits: charset.digits,
    symbols: charset.symbols,
    excludeAmbiguous: charset.excludeAmbiguous,
    requireEach: charset.requireEach,
  }
  const result = run('', options, { locale: locale.value?.startsWith('en') ? 'en' : 'zh' })
  if (!result.ok) {
    error.value = result.error.message
    items.value = []
    output.value = ''
    entropy.value = null
    return
  }
  error.value = ''
  items.value = result.data.items
  output.value = result.output
  entropy.value = result.data.entropy ? { bits: result.data.entropy, strength: result.data.strength } : null
}

// 切换模式换一套默认选项并立刻给一份结果;其余参数由「重新生成」触发,避免边输入边刷新。
watch(mode, (next) => {
  if (next === 'password') length.value = 20
  else if (next === 'pin') length.value = 6
  else if (next === 'nano') length.value = 21
  else if (next === 'bytes') length.value = 16
  generate()
})

generate()
</script>
