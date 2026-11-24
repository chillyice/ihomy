<template>
  <button
    type="button"
    class="warm-lamp"
    :class="lit ? 'is-on' : 'is-off'"
    role="switch"
    :aria-checked="lit"
    :aria-label="$t('theme.lamp')"
    :title="lit ? $t('theme.lampOff') : $t('theme.lampOn')"
    @click="toggle"
  >
    <span class="rocker" aria-hidden="true"><i></i><b></b></span>
  </button>
</template>

<script setup>
import { computed, inject } from 'vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const sunLight = inject(SUN_LIGHT_KEY, null)

// 判定口径取「灯实际有多亮」而不是 lampMode:auto 模式白天本就该是灭的,按模式摆姿态会自相矛盾
const lit = computed(() => Number(sunLight?.lampStrength?.value ?? 0) > 0.05)

const toggle = () => {
  if (!sunLight?.lampMode) return
  sunLight.lampMode.value = lit.value ? 'off' : 'on'
}
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
.warm-lamp:focus-visible {
  outline: 2px solid var(--color-brand);
  border-radius: 8px;
}
</style>
