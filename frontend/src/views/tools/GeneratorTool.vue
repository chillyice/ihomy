<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.generator.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-select v-model="mode" size="small" style="width: 150px" @change="generate">
          <el-option v-for="item in MODES" :key="item.value" :value="item.value" :label="$t(item.labelKey)" />
        </el-select>
        <span class="gen-meta">{{ metaText }}</span>
      </div>
      <div class="tb-right">
        <el-button size="small" :icon="Refresh" @click="generate">{{ $t('tools.generator.regenerate') }}</el-button>
        <el-button size="small" type="primary" :disabled="!output" @click="copyAll">{{ $t('tools.generator.copy') }}</el-button>
      </div>
    </PageToolbar>

    <div class="gen-grid">
      <div class="card gen-pane">
        <div class="section-label">{{ $t('tools.generator.options') }}</div>

        <template v-if="mode === 'password'">
          <div class="gen-field">
            <label for="gen-length">{{ $t('tools.generator.length') }}</label>
            <el-input-number id="gen-length" v-model="options.passwordLength" :min="4" :max="128" size="small" controls-position="right" />
          </div>
          <div class="gen-field">
            <label for="gen-count">{{ $t('tools.generator.count') }}</label>
            <el-input-number id="gen-count" v-model="options.count" :min="1" :max="200" size="small" controls-position="right" />
          </div>
          <div class="gen-checks">
            <el-checkbox v-model="options.lower">{{ $t('tools.generator.lower') }}</el-checkbox>
            <el-checkbox v-model="options.upper">{{ $t('tools.generator.upper') }}</el-checkbox>
            <el-checkbox v-model="options.digits">{{ $t('tools.generator.digits') }}</el-checkbox>
            <el-checkbox v-model="options.symbols">{{ $t('tools.generator.symbols') }}</el-checkbox>
            <el-checkbox v-model="options.excludeAmbiguous">{{ $t('tools.generator.excludeAmbiguous') }}</el-checkbox>
            <el-checkbox v-model="options.requireEach">{{ $t('tools.generator.requireEach') }}</el-checkbox>
          </div>
        </template>

        <template v-else-if="mode === 'pin'">
          <div class="gen-field">
            <label for="gen-pin-length">{{ $t('tools.generator.length') }}</label>
            <el-input-number id="gen-pin-length" v-model="options.pinLength" :min="3" :max="12" size="small" controls-position="right" />
          </div>
          <div class="gen-field">
            <label for="gen-pin-count">{{ $t('tools.generator.count') }}</label>
            <el-input-number id="gen-pin-count" v-model="options.count" :min="1" :max="200" size="small" controls-position="right" />
          </div>
        </template>

        <template v-else-if="mode === 'uuid'">
          <div class="gen-field">
            <label for="gen-uuid-version">{{ $t('tools.generator.version') }}</label>
            <el-select id="gen-uuid-version" v-model="options.version" size="small">
              <el-option value="v4" label="UUID v4" />
              <el-option value="v7" label="UUID v7" />
            </el-select>
          </div>
          <div class="gen-field">
            <label for="gen-uuid-count">{{ $t('tools.generator.count') }}</label>
            <el-input-number id="gen-uuid-count" v-model="options.count" :min="1" :max="200" size="small" controls-position="right" />
          </div>
          <div class="gen-checks">
            <el-checkbox v-model="options.uppercase">{{ $t('tools.generator.uppercase') }}</el-checkbox>
            <el-checkbox v-model="options.hyphens">{{ $t('tools.generator.hyphens') }}</el-checkbox>
          </div>
        </template>

        <template v-else-if="mode === 'ulid'">
          <div class="gen-field">
            <label for="gen-ulid-count">{{ $t('tools.generator.count') }}</label>
            <el-input-number id="gen-ulid-count" v-model="options.count" :min="1" :max="200" size="small" controls-position="right" />
          </div>
          <div class="gen-checks">
            <el-checkbox v-model="options.lowercase">{{ $t('tools.generator.lowercase') }}</el-checkbox>
          </div>
        </template>

        <template v-else-if="mode === 'nano'">
          <div class="gen-field">
            <label for="gen-nano-length">{{ $t('tools.generator.length') }}</label>
            <el-input-number id="gen-nano-length" v-model="options.nanoLength" :min="4" :max="128" size="small" controls-position="right" />
          </div>
          <div class="gen-field">
            <label for="gen-nano-count">{{ $t('tools.generator.count') }}</label>
            <el-input-number id="gen-nano-count" v-model="options.count" :min="1" :max="200" size="small" controls-position="right" />
          </div>
          <div class="gen-field">
            <label for="gen-nano-alphabet">{{ $t('tools.generator.alphabet') }}</label>
            <el-input id="gen-nano-alphabet" v-model="options.alphabet" size="small" />
          </div>
        </template>

        <template v-else-if="mode === 'bytes'">
          <div class="gen-field">
            <label for="gen-bytes-length">{{ $t('tools.generator.byteLength') }}</label>
            <el-input-number
              id="gen-bytes-length"
              v-model="options.bytesLength"
              :min="1"
              :max="1024"
              size="small"
              controls-position="right"
            />
          </div>
          <div class="gen-field">
            <label for="gen-bytes-count">{{ $t('tools.generator.count') }}</label>
            <el-input-number id="gen-bytes-count" v-model="options.count" :min="1" :max="200" size="small" controls-position="right" />
          </div>
          <div class="gen-field">
            <label for="gen-bytes-format">{{ $t('tools.generator.format') }}</label>
            <el-select id="gen-bytes-format" v-model="options.format" size="small">
              <el-option value="hex" label="HEX" />
              <el-option value="base64" label="Base64" />
            </el-select>
          </div>
        </template>

        <template v-else-if="mode === 'number'">
          <div class="gen-field">
            <label for="gen-min">{{ $t('tools.generator.min') }}</label>
            <el-input-number id="gen-min" v-model="options.min" size="small" controls-position="right" />
          </div>
          <div class="gen-field">
            <label for="gen-max">{{ $t('tools.generator.max') }}</label>
            <el-input-number id="gen-max" v-model="options.max" size="small" controls-position="right" />
          </div>
          <div class="gen-field">
            <label for="gen-number-count">{{ $t('tools.generator.count') }}</label>
            <el-input-number id="gen-number-count" v-model="options.count" :min="1" :max="200" size="small" controls-position="right" />
          </div>
          <div class="gen-checks">
            <el-checkbox v-model="options.unique">{{ $t('tools.generator.unique') }}</el-checkbox>
          </div>
        </template>

        <template v-else>
          <div class="gen-field gen-field-block">
            <label for="gen-items">{{ $t('tools.generator.items') }}</label>
            <textarea
              id="gen-items"
              v-model="itemsInput"
              class="gen-area"
              spellcheck="false"
              :placeholder="$t('tools.generator.itemsPlaceholder')"
            ></textarea>
          </div>
          <div v-if="mode === 'pick' || mode === 'group'" class="gen-field">
            <label for="gen-pick-count">{{ mode === 'group' ? $t('tools.generator.groupCount') : $t('tools.generator.count') }}</label>
            <el-input-number
              v-if="mode === 'group'"
              id="gen-pick-count"
              v-model="options.groupCount"
              :min="2"
              :max="200"
              size="small"
              controls-position="right"
            />
            <el-input-number
              v-else
              id="gen-pick-count"
              v-model="options.count"
              :min="1"
              :max="200"
              size="small"
              controls-position="right"
            />
          </div>
        </template>

        <p class="gen-privacy">{{ $t('tools.generator.privacy') }}</p>
      </div>

      <div class="card gen-pane">
        <div class="gen-result-head">
          <span class="section-label">{{ $t('tools.generator.result') }}</span>
          <span v-if="infoText" class="gen-info">{{ infoText }}</span>
        </div>
        <div v-if="error" class="gen-error">{{ error }}</div>
        <pre v-else-if="output" class="gen-output">{{ output }}</pre>
        <div v-else class="gen-empty">{{ $t('tools.generator.empty') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { run } from '@/utils/tools/generatorTool'

const { t, locale } = useI18n()

const MODES = [
  { value: 'password', labelKey: 'tools.generator.modePassword' },
  { value: 'pin', labelKey: 'tools.generator.modePin' },
  { value: 'uuid', labelKey: 'tools.generator.modeUuid' },
  { value: 'ulid', labelKey: 'tools.generator.modeUlid' },
  { value: 'nano', labelKey: 'tools.generator.modeNano' },
  { value: 'bytes', labelKey: 'tools.generator.modeBytes' },
  { value: 'number', labelKey: 'tools.generator.modeNumber' },
  { value: 'pick', labelKey: 'tools.generator.modePick' },
  { value: 'shuffle', labelKey: 'tools.generator.modeShuffle' },
  { value: 'group', labelKey: 'tools.generator.modeGroup' },
]

const mode = ref('password')
const itemsInput = ref('Alpha\nBravo\nCharlie\nDelta\nEcho')
const options = reactive({
  passwordLength: 20,
  pinLength: 6,
  nanoLength: 21,
  bytesLength: 16,
  count: 1,
  lower: true,
  upper: true,
  digits: true,
  symbols: true,
  excludeAmbiguous: true,
  requireEach: true,
  version: 'v4',
  uppercase: false,
  hyphens: true,
  lowercase: false,
  alphabet: '_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ',
  format: 'hex',
  min: 1,
  max: 100,
  unique: false,
  groupCount: 2,
})

const result = ref(null)
const error = ref('')

const output = computed(() => (result.value?.ok ? result.value.output : ''))
const coreLocale = () => (locale.value?.startsWith('en') ? 'en' : 'zh')
const strengthKey = computed(() => {
  const strength = result.value?.data?.strength
  return strength ? `tools.generator.strength.${strength}` : ''
})
const infoText = computed(() => {
  if (!result.value?.ok) return ''
  if (mode.value === 'password' && result.value.data.entropy) {
    return t('tools.generator.entropyInfo', {
      bits: result.value.data.entropy,
      strength: t(strengthKey.value),
    })
  }
  return t('tools.generator.countInfo', { n: result.value.data.items.length })
})
const metaText = computed(() => t(`tools.generator.modeHint.${mode.value}`))

const generate = () => {
  const lengthByMode = {
    password: options.passwordLength,
    pin: options.pinLength,
    nano: options.nanoLength,
    bytes: options.bytesLength,
  }
  const payload = { ...options, mode: mode.value, length: lengthByMode[mode.value] }
  const response = run(itemsInput.value, payload, { locale: coreLocale() })
  if (response.ok) {
    result.value = response
    error.value = ''
  } else {
    result.value = null
    error.value = response.error.message
  }
}

const copyAll = async () => {
  if (!output.value) return
  try {
    await navigator.clipboard.writeText(output.value)
    ElMessage.success(t('tools.generator.copied'))
  } catch {
    ElMessage.error(t('tools.generator.copyFailed'))
  }
}

generate()
</script>

<style scoped>
.gen-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.gen-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .gen-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.gen-pane {
  display: flex;
  flex-direction: column;
  gap: 13px;
}
.gen-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.gen-field > label {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.gen-field-block {
  align-items: stretch;
  flex-direction: column;
}
.gen-area {
  width: 100%;
  min-height: 120px;
  padding: 10px 12px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-bg-soft);
  color: var(--color-text);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: vertical;
}
.gen-area:focus {
  outline: none;
  border-color: var(--color-accent);
}
.gen-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 14px;
}
.gen-result-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.gen-info {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.gen-output {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}
.gen-empty {
  padding: 24px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.gen-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-danger-weak, rgba(220, 80, 80, 0.12));
  color: var(--color-danger, #c0392b);
  font-size: 13px;
}
.gen-privacy {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
