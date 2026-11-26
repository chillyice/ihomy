<template>
  <button
    type="button"
    class="warm-lamp"
    :class="{ 'is-on': lit, 'is-auto': lampMode === 'auto' }"
    :aria-label="label"
    :title="label"
    @click="toggleLamp"
  >
    <span class="rocker" aria-hidden="true">
      <i></i>
      <span v-if="lampMode === 'auto'" class="auto-label">{{ $t('sidebar.lampAuto') }}</span>
      <b v-else></b>
    </span>
  </button>
</template>

<script setup>
import { computed, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { lampMode, lampStrength, toggleLamp } = inject(SUN_LIGHT_KEY)
const { t } = useI18n()
const modeLabels = { auto: 'sidebar.lampAuto', on: 'sidebar.lampOn', off: 'sidebar.lampOff' }
const label = computed(() => t('theme.cycleLamp', { mode: t(modeLabels[lampMode.value]) }))
const lit = computed(() => lampStrength.value > 0.05)
</script>

<style scoped>
.warm-lamp {
  display: grid;
  place-items: center;
  width: 40px;
  height: 44px;
  padding: 0;
  background: transparent;
  border: 0;
  color: var(--color-text);
  cursor: pointer;
}
.rocker {
  display: grid;
  place-items: center;
  width: 23px;
  height: 31px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: var(--color-card);
  box-shadow:
    inset 0 -4px 1px rgb(65 48 30 / 24%),
    0 1px 3px rgb(0 0 0 / 22%);
}
.rocker i {
  width: 3px;
  height: 7px;
  border-radius: 2px;
  background: currentColor;
  opacity: 0.55;
}
.rocker b {
  width: 6px;
  height: 6px;
  margin-bottom: 4px;
  border: 1px solid currentColor;
  border-radius: 50%;
  opacity: 0.6;
}
.is-on .rocker {
  box-shadow:
    inset 0 4px 2px rgb(65 48 30 / 26%),
    0 1px 3px rgb(0 0 0 / 22%);
}
.is-on i {
  background: #d78a29;
  box-shadow: 0 0 5px #efb75c;
  opacity: 1;
}
.is-auto .rocker {
  width: 30px;
  box-shadow:
    inset 0 -1px 1px rgb(65 48 30 / 18%),
    0 1px 3px rgb(0 0 0 / 22%);
}
.auto-label {
  font-size: 10px;
  font-weight: 650;
  line-height: 1;
}
.warm-lamp:focus-visible {
  outline: 2px solid var(--color-brand);
  border-radius: 8px;
}
</style>
