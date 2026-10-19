<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.color.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-input v-model="input" size="small" :placeholder="$t('tools.color.placeholder')" style="width: 220px" />
        <input v-model="picker" type="color" class="color-picker" :aria-label="$t('tools.color.picker')" @input="syncFromPicker" />
        <span v-if="color" class="color-swatch" :style="{ background: hex }"></span>
        <span v-else-if="input" class="color-error">{{ $t('tools.color.invalid') }}</span>
      </div>
      <div class="tb-right">
        <el-button size="small" :disabled="!color" @click="copy(hex)">{{ $t('tools.color.copyHex') }}</el-button>
      </div>
    </PageToolbar>

    <div v-if="color" class="color-grid">
      <div class="card color-pane">
        <div class="section-label">{{ $t('tools.color.conversions') }}</div>
        <div v-for="row in conversions" :key="row.key" class="color-row">
          <span class="color-row-label">{{ row.label }}</span>
          <code class="color-row-value">{{ row.value }}</code>
          <el-button size="small" text @click="copy(row.value)">{{ $t('tools.color.copy') }}</el-button>
        </div>

        <div class="section-label">{{ $t('tools.color.contrast') }}</div>
        <div v-for="row in contrastRows" :key="row.bg" class="color-row">
          <span class="color-swatch color-swatch-sm" :style="{ background: row.bg }"></span>
          <span class="color-row-label">{{ row.label }}</span>
          <code class="color-row-value">{{ row.ratio }} : 1</code>
          <span class="color-badges">
            <span class="color-badge" :class="{ on: row.level.aa }">AA</span>
            <span class="color-badge" :class="{ on: row.level.aaa }">AAA</span>
            <span class="color-badge" :class="{ on: row.level.aaLarge }">AA+</span>
          </span>
        </div>
        <p class="color-hint">{{ $t('tools.color.readable', { hex: readable }) }}</p>

        <div class="section-label">{{ $t('tools.color.harmonies') }}</div>
        <div class="color-harmonies">
          <div v-for="group in harmonies" :key="group.kind" class="color-harmony">
            <div class="color-harmony-title">{{ $t(`tools.color.harmony.${group.kind}`) }}</div>
            <div class="color-swatches">
              <button
                v-for="value in group.colors"
                :key="value"
                type="button"
                class="color-swatch color-swatch-btn"
                :style="{ background: value }"
                :title="value"
                :aria-label="value"
                @click="copy(value)"
              ></button>
            </div>
          </div>
        </div>

        <div class="section-label">{{ $t('tools.color.variables') }}</div>
        <pre class="color-css">{{ css }}</pre>
        <el-button size="small" text @click="copy(css)">{{ $t('tools.color.copy') }}</el-button>
      </div>

      <div class="card color-pane">
        <div class="section-label">{{ $t('tools.color.shadow') }}</div>
        <div class="color-fields">
          <label>{{ $t('tools.color.offsetX') }}<el-input-number v-model="shadow.x" size="small" :min="-64" :max="64" /></label>
          <label>{{ $t('tools.color.offsetY') }}<el-input-number v-model="shadow.y" size="small" :min="-64" :max="64" /></label>
          <label>{{ $t('tools.color.blur') }}<el-input-number v-model="shadow.blur" size="small" :min="0" :max="128" /></label>
          <label>{{ $t('tools.color.alpha') }}<el-input-number v-model="shadow.alpha" size="small" :min="0" :max="1" :step="0.05" /></label>
        </div>
        <div class="color-preview" :style="{ background: '#fff', boxShadow: shadowCss }"></div>
        <div class="color-row">
          <code class="color-row-value">{{ shadowCss }}</code>
          <el-button size="small" text @click="copy(shadowCss)">{{ $t('tools.color.copy') }}</el-button>
        </div>

        <div class="section-label">{{ $t('tools.color.gradient') }}</div>
        <div class="color-fields">
          <label>{{ $t('tools.color.from') }}<input v-model="gradient.from" type="color" class="color-picker" /></label>
          <label>{{ $t('tools.color.to') }}<input v-model="gradient.to" type="color" class="color-picker" /></label>
          <label>{{ $t('tools.color.angle') }}<el-input-number v-model="gradient.angle" size="small" :min="0" :max="360" /></label>
        </div>
        <div class="color-preview" :style="{ background: gradientCss }"></div>
        <div class="color-row">
          <code class="color-row-value">{{ gradientCss }}</code>
          <el-button size="small" text @click="copy(gradientCss)">{{ $t('tools.color.copy') }}</el-button>
        </div>

        <div class="section-label">{{ $t('tools.color.bezier') }}</div>
        <div class="color-fields">
          <label v-for="(value, index) in bezier" :key="index">
            {{ BEZIER_LABELS[index] }}
            <el-input-number v-model="bezier[index]" size="small" :min="0" :max="index % 2 === 0 ? 1 : 2" :step="0.05" />
          </label>
        </div>
        <div class="color-bezier">
          <svg viewBox="0 0 120 120" class="color-bezier-svg" role="img" :aria-label="$t('tools.color.bezier')">
            <path d="M10 110 L10 10 L110 10 L110 110" fill="none" stroke="var(--color-border)" stroke-width="1.5" />
            <path :d="bezierPath" fill="none" stroke="var(--color-accent)" stroke-width="2.5" />
          </svg>
        </div>
        <div class="color-row">
          <code class="color-row-value">{{ bezierCss }}</code>
          <el-button size="small" text @click="copy(bezierCss)">{{ $t('tools.color.copy') }}</el-button>
        </div>

        <p class="color-hint">{{ $t('tools.color.privacy') }}</p>
      </div>
    </div>

    <div v-else class="card color-empty">{{ $t('tools.color.emptyState') }}</div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  contrastRatio,
  cssVariables,
  harmonize,
  parseColor,
  readableText,
  rgbToHex,
  rgbToHsl,
  rgbToHsv,
  rgbToLab,
  wcagReport,
} from '@/utils/tools/colorTool'

const { t } = useI18n()

const BEZIER_LABELS = ['P1 x', 'P1 y', 'P2 x', 'P2 y']
const WHITE = { r: 255, g: 255, b: 255 }
const BLACK = { r: 0, g: 0, b: 0 }

const input = ref('#4f8cff')
const picker = ref('#4f8cff')

const color = computed(() => parseColor(input.value))
const hex = computed(() => (color.value ? rgbToHex({ ...color.value, a: 1 }) : ''))
const readable = computed(() => (color.value ? readableText(color.value) : ''))
const hsl = computed(() => (color.value ? rgbToHsl(color.value) : null))
const hsv = computed(() => (color.value ? rgbToHsv(color.value) : null))
const lab = computed(() => (color.value ? rgbToLab(color.value) : null))
const harmonies = computed(() => (color.value ? harmonize(color.value) : []))
const css = computed(() => (color.value ? cssVariables(color.value) : ''))

const conversions = computed(() => {
  if (!color.value) return []
  const c = color.value
  return [
    { key: 'hex', label: 'HEX', value: rgbToHex(c) },
    { key: 'rgb', label: 'RGB', value: `rgb(${c.r}, ${c.g}, ${c.b})` },
    { key: 'hsl', label: 'HSL', value: `hsl(${hsl.value.h}, ${hsl.value.s}%, ${hsl.value.l}%)` },
    { key: 'hsv', label: 'HSV', value: `hsv(${hsv.value.h}, ${hsv.value.s}%, ${hsv.value.v}%)` },
    { key: 'lab', label: 'LAB', value: `lab(${lab.value.l}, ${lab.value.a}, ${lab.value.b})` },
  ]
})

const contrastRows = computed(() => {
  if (!color.value) return []
  return [
    { bg: '#ffffff', label: t('tools.color.onWhite'), ratio: contrastRatio(color.value, WHITE) },
    { bg: '#000000', label: t('tools.color.onBlack'), ratio: contrastRatio(color.value, BLACK) },
  ].map((row) => ({ ...row, level: wcagReport(row.ratio) }))
})

const shadow = ref({ x: 0, y: 8, blur: 24, alpha: 0.18 })
const shadowCss = computed(
  () =>
    `box-shadow: ${shadow.value.x}px ${shadow.value.y}px ${shadow.value.blur}px rgba(${color.value?.r}, ${color.value?.g}, ${color.value?.b}, ${shadow.value.alpha});`,
)

const gradient = ref({ from: '#4f8cff', to: '#a855f7', angle: 135 })
const gradientCss = computed(() => `linear-gradient(${gradient.value.angle}deg, ${gradient.value.from}, ${gradient.value.to})`)

const bezier = ref([0.4, 0, 0.2, 1])
const bezierCss = computed(() => `cubic-bezier(${bezier.value.map((value) => Number(value).toFixed(2)).join(', ')})`)
const bezierPath = computed(() => {
  const [x1, y1, x2, y2] = bezier.value.map((value) => Number(value))
  const px = (x) => 10 + x * 100
  const py = (y) => 110 - y * 50
  return `M10 110 C${px(x1)} ${py(y1)} ${px(x2)} ${py(y2)} 110 10`
})

watch(hex, (value) => {
  if (value && picker.value !== value) picker.value = value
})

const syncFromPicker = () => {
  input.value = picker.value
}

const copy = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success(t('tools.color.copied'))
  } catch {
    ElMessage.error(t('tools.color.copyFailed'))
  }
}
</script>

<style scoped>
.color-picker {
  width: 40px;
  height: 30px;
  padding: 2px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-soft);
  cursor: pointer;
}
.color-swatch {
  display: inline-block;
  width: 30px;
  height: 30px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}
.color-swatch-sm {
  width: 18px;
  height: 18px;
  border-radius: 4px;
}
.color-swatch-btn {
  cursor: pointer;
  padding: 0;
}
.color-error {
  color: var(--color-danger, #c0392b);
  font-size: 12px;
}
.color-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .color-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.color-pane {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.color-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.color-row-label {
  flex: 0 0 88px;
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.color-row-value {
  flex: 1;
  min-width: 0;
  padding: 5px 8px;
  border-radius: 6px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.color-badges {
  display: flex;
  gap: 4px;
}
.color-badge {
  padding: 1px 6px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 10.5px;
}
.color-badge.on {
  border-color: transparent;
  background: rgba(46, 160, 90, 0.16);
  color: #2e9a5a;
}
.color-hint {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.color-harmonies {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}
.color-harmony-title {
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.color-swatches {
  display: flex;
  gap: 6px;
}
.color-css {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.color-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.color-fields label {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.color-preview {
  height: 64px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.color-bezier {
  display: flex;
  justify-content: center;
}
.color-bezier-svg {
  width: 120px;
  height: 120px;
}
.color-empty {
  padding: 40px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
</style>
