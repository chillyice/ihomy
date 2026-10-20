<!-- 单位换算面板:九类单位 + 温度公式,左侧换算、右侧全单位对照。
     纯逻辑走 utils/tools/unitTool,换算只在本机完成。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.unit.input') }}</span>
        <span>{{ $t(`tools.unit.categories.${category}`) }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-inline">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-unit-cat">{{ $t('tools.unit.category') }}</label>
            <select id="bp-unit-cat" v-model="category" class="bpc-select" @change="onCategoryChange">
              <option v-for="id in CATEGORY_IDS" :key="id" :value="id">{{ $t(`tools.unit.categories.${id}`) }}</option>
            </select>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-unit-value">{{ $t('tools.unit.input') }}</label>
            <input
              id="bp-unit-value"
              v-model="value"
              class="bpc-input"
              type="text"
              inputmode="decimal"
              spellcheck="false"
              :placeholder="$t('tools.unit.valuePlaceholder')"
            />
          </div>
        </div>

        <div class="bpc-row-2">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-unit-from">{{ $t('tools.unit.from') }}</label>
            <select id="bp-unit-from" v-model="from" class="bpc-select">
              <option v-for="id in units" :key="`f-${id}`" :value="id">{{ unitLabel(id) }}</option>
            </select>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-unit-to">{{ $t('tools.unit.to') }}</label>
            <select id="bp-unit-to" v-model="to" class="bpc-select">
              <option v-for="id in units" :key="`t-${id}`" :value="id">{{ unitLabel(id) }}</option>
            </select>
          </div>
        </div>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" @click="swap">⇄ {{ $t('tools.unit.swap') }}</button>
        </div>

        <pre class="bpc-out bpc-out-short" :class="{ 'bpc-out-empty': !resultText }">{{ resultText || $t('tools.unit.empty') }}</pre>
        <p v-if="error" class="bpc-err">{{ error }}</p>
        <p v-else class="bpc-hint">{{ $t('tools.unit.note') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.unit.all') }}</span>
        <span>{{ rows.length }}</span>
      </div>
      <div class="bpc-card-bd">
        <div v-if="rows.length" class="bpc-rows">
          <div v-for="row in rows" :key="row.unit" class="bpc-row">
            <span class="bpc-row-k">{{ unitLabel(row.unit) }}</span>
            <span class="bpc-row-v" :class="{ 'is-accent': row.unit === to }">{{ row.text }}</span>
            <BpcCopy :value="row.text" :label="$t('tools.unit.copy')" />
          </div>
        </div>
        <p v-else class="bpc-hint">{{ $t('tools.unit.empty') }}</p>
        <p class="bpc-hint">{{ $t('tools.unit.privacy') }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { CATEGORY_IDS, convert, convertAll, formatNumber, unitsOf } from '@/utils/tools/unitTool'

const { t } = useI18n()

const category = ref('length')
const value = ref('1')
const from = ref('m')
const to = ref('km')

const units = computed(() => unitsOf(category.value))
const unitLabel = (id) => t(`tools.unit.units.${id}`)

const parsed = computed(() => {
  const number = Number(value.value)
  return Number.isFinite(number) ? number : null
})

const resultText = computed(() => {
  if (parsed.value === null) return ''
  try {
    const result = convert(parsed.value, category.value, from.value, to.value)
    return `${formatNumber(parsed.value)} ${unitLabel(from.value)} = ${formatNumber(result)} ${unitLabel(to.value)}`
  } catch {
    return ''
  }
})

const error = computed(() => (parsed.value === null && value.value !== '' ? t('tools.unit.errNumber') : ''))

const rows = computed(() => {
  if (parsed.value === null) return []
  return convertAll(parsed.value, category.value, from.value).map((item) => ({
    unit: item.unit,
    text: `${formatNumber(item.value)} ${unitLabel(item.unit)}`,
  }))
})

const onCategoryChange = () => {
  const list = units.value
  from.value = list[0]
  to.value = list[1] || list[0]
}

const swap = () => {
  const previous = from.value
  from.value = to.value
  to.value = previous
}
</script>
