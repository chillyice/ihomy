<!-- 颜色面板:格式互转 / WCAG 对比度 / 配色方案 / CSS 变量,以及阴影、渐变、缓动片段。
     纯逻辑走 utils/tools/colorTool,不请求任何服务。 -->
<template>
  <div class="bpc-stack">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.color.conversions') }}</span>
        <span>{{ color ? hex : '—' }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bp-color-inline">
          <input v-model="input" class="bpc-input" type="text" spellcheck="false" :placeholder="$t('tools.color.placeholder')" />
          <input v-model="picker" class="bp-color-picker" type="color" :aria-label="$t('tools.color.picker')" @input="syncFromPicker" />
          <span v-if="color" class="bp-swatch" :style="{ background: hex }"></span>
        </div>
        <div class="bpc-actions">
          <BpcCopy v-if="color" :value="hex" :label="$t('tools.color.copyHex')" />
        </div>
        <p v-if="!color && input" class="bpc-err">{{ $t('tools.color.invalid') }}</p>
        <p v-else-if="!color" class="bpc-hint">{{ $t('tools.color.emptyState') }}</p>
      </div>
    </section>

    <div v-if="color" class="bpc-grid">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.color.conversions') }}</span>
          <span>{{ $t('tools.color.picker') }}</span>
        </div>
        <div class="bpc-card-bd">
          <div class="bpc-rows">
            <div v-for="row in conversions" :key="row.key" class="bpc-row">
              <span class="bpc-row-k">{{ row.label }}</span>
              <span class="bpc-row-v">{{ row.value }}</span>
              <BpcCopy :value="row.value" :label="$t('tools.color.copy')" />
            </div>
          </div>

          <div class="bpc-card-hd bp-sub-hd">{{ $t('tools.color.contrast') }}</div>
          <div class="bpc-rows">
            <div v-for="row in contrastRows" :key="row.bg" class="bpc-row">
              <span class="bp-swatch bp-swatch-sm" :style="{ background: row.bg }"></span>
              <span class="bpc-row-k">{{ row.label }}</span>
              <span class="bpc-row-v">{{ row.ratio }} : 1</span>
              <span class="bp-badges">
                <span class="bp-badge" :class="{ 'is-on': row.level.aa }">AA</span>
                <span class="bp-badge" :class="{ 'is-on': row.level.aaa }">AAA</span>
                <span class="bp-badge" :class="{ 'is-on': row.level.aaLarge }">AA+</span>
              </span>
            </div>
          </div>
          <p v-if="flash" class="bpc-meta">
            <span class="bpc-flash">{{ flash }}</span>
          </p>
          <p class="bpc-hint">{{ $t('tools.color.readable', { hex: readable }) }}</p>

          <div class="bpc-card-hd bp-sub-hd">{{ $t('tools.color.harmonies') }}</div>
          <div class="bp-harmonies">
            <div v-for="group in harmonies" :key="group.kind" class="bp-harmony">
              <span class="bpc-label">{{ $t(`tools.color.harmony.${group.kind}`) }}</span>
              <span class="bp-swatches">
                <button
                  v-for="value in group.colors"
                  :key="value"
                  class="bp-swatch bp-swatch-btn"
                  type="button"
                  :style="{ background: value }"
                  :title="value"
                  :aria-label="value"
                  @click="copyValue(value)"
                ></button>
              </span>
            </div>
          </div>

          <div class="bpc-card-hd bp-sub-hd">{{ $t('tools.color.variables') }}</div>
          <pre class="bpc-out bpc-out-short">{{ css }}</pre>
          <div class="bpc-actions">
            <BpcCopy :value="css" :label="$t('tools.color.copy')" />
          </div>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.color.shadow') }} / {{ $t('tools.color.gradient') }} / {{ $t('tools.color.bezier') }}</span>
          <span>CSS</span>
        </div>
        <div class="bpc-card-bd">
          <div class="bpc-row-2">
            <div class="bpc-field">
              <label class="bpc-label" for="bp-shadow-x">{{ $t('tools.color.offsetX') }}</label>
              <input id="bp-shadow-x" v-model.number="shadow.x" class="bpc-input" type="number" min="-64" max="64" />
            </div>
            <div class="bpc-field">
              <label class="bpc-label" for="bp-shadow-y">{{ $t('tools.color.offsetY') }}</label>
              <input id="bp-shadow-y" v-model.number="shadow.y" class="bpc-input" type="number" min="-64" max="64" />
            </div>
            <div class="bpc-field">
              <label class="bpc-label" for="bp-shadow-blur">{{ $t('tools.color.blur') }}</label>
              <input id="bp-shadow-blur" v-model.number="shadow.blur" class="bpc-input" type="number" min="0" max="128" />
            </div>
            <div class="bpc-field">
              <label class="bpc-label" for="bp-shadow-alpha">{{ $t('tools.color.alpha') }}</label>
              <input id="bp-shadow-alpha" v-model.number="shadow.alpha" class="bpc-input" type="number" min="0" max="1" step="0.05" />
            </div>
          </div>
          <div class="bp-preview" :style="{ boxShadow: shadowCss }"></div>
          <div class="bpc-row">
            <code class="bpc-row-v">{{ shadowCss }}</code>
            <BpcCopy :value="shadowCss" :label="$t('tools.color.copy')" />
          </div>

          <div class="bpc-card-hd bp-sub-hd">{{ $t('tools.color.gradient') }}</div>
          <div class="bp-color-inline">
            <div class="bpc-field">
              <label class="bpc-label" for="bp-grad-from">{{ $t('tools.color.from') }}</label>
              <input id="bp-grad-from" v-model="gradient.from" class="bp-color-picker" type="color" />
            </div>
            <div class="bpc-field">
              <label class="bpc-label" for="bp-grad-to">{{ $t('tools.color.to') }}</label>
              <input id="bp-grad-to" v-model="gradient.to" class="bp-color-picker" type="color" />
            </div>
            <div class="bpc-field">
              <label class="bpc-label" for="bp-grad-angle">{{ $t('tools.color.angle') }}</label>
              <input id="bp-grad-angle" v-model.number="gradient.angle" class="bpc-input" type="number" min="0" max="360" />
            </div>
          </div>
          <div class="bp-preview" :style="{ background: gradientCss }"></div>
          <div class="bpc-row">
            <code class="bpc-row-v">{{ gradientCss }}</code>
            <BpcCopy :value="gradientCss" :label="$t('tools.color.copy')" />
          </div>

          <div class="bpc-card-hd bp-sub-hd">{{ $t('tools.color.bezier') }}</div>
          <div class="bpc-row-2">
            <div v-for="(value, index) in bezier" :key="index" class="bpc-field">
              <label class="bpc-label" :for="`bp-bezier-${index}`">{{ BEZIER_LABELS[index] }}</label>
              <input
                :id="`bp-bezier-${index}`"
                v-model.number="bezier[index]"
                class="bpc-input"
                type="number"
                min="0"
                :max="index % 2 === 0 ? 1 : 2"
                step="0.05"
              />
            </div>
          </div>
          <div class="bp-bezier">
            <svg viewBox="0 0 120 120" role="img" :aria-label="$t('tools.color.bezier')">
              <path d="M10 110 L10 10 L110 10 L110 110" fill="none" stroke="var(--line)" stroke-width="1.5" />
              <path :d="bezierPath" fill="none" stroke="var(--accent)" stroke-width="2.5" />
            </svg>
          </div>
          <div class="bpc-row">
            <code class="bpc-row-v">{{ bezierCss }}</code>
            <BpcCopy :value="bezierCss" :label="$t('tools.color.copy')" />
          </div>

          <p class="bpc-hint">{{ $t('tools.color.privacy') }}</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
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
const flash = ref('')

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

const copyValue = async (value) => {
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    flash.value = t('tools.color.copied')
  } catch {
    flash.value = t('tools.color.copyFailed')
  }
  setTimeout(() => {
    flash.value = ''
  }, 1500)
}
</script>

<style scoped>
.bp-color-inline {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.bp-color-inline .bpc-input {
  flex: 1;
  min-width: 180px;
}
.bp-color-picker {
  width: 44px;
  height: 38px;
  padding: 2px;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--bg);
  cursor: pointer;
}
.bp-swatch {
  display: inline-block;
  width: 30px;
  height: 30px;
  border: 1px solid var(--line);
  border-radius: 2px;
  flex: none;
}
.bp-swatch-sm {
  width: 18px;
  height: 18px;
}
.bp-swatch-btn {
  padding: 0;
  cursor: pointer;
}
.bp-swatches {
  display: flex;
  gap: 6px;
}
.bp-harmonies {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}
.bp-harmony {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.bp-badges {
  display: flex;
  gap: 4px;
}
.bp-badge {
  padding: 1px 6px;
  border: 1px solid var(--line);
  border-radius: 2px;
  color: var(--muted);
  font: 10.5px/1.5 var(--mono);
}
.bp-badge.is-on {
  border-color: transparent;
  background: var(--accent-soft);
  color: var(--accent);
}
.bp-sub-hd {
  border-top: 1px solid var(--line-soft);
  border-bottom: 1px solid var(--line-soft);
  padding-left: 0;
  padding-right: 0;
}
.bp-preview {
  height: 64px;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: #ffffff;
}
.bp-bezier {
  display: flex;
  justify-content: center;
}
.bp-bezier svg {
  width: 120px;
  height: 120px;
}
</style>
