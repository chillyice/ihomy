<template>
  <button
    type="button"
    class="warm-mode-toggle"
    role="switch"
    :aria-label="$t('theme.duskMode')"
    :aria-checked="themeStore.isDusk"
    @click="themeStore.toggleMode()"
  >
    <span class="warm-mode-face warm-mode-dawn" aria-hidden="true">
      <el-icon><Sunny /></el-icon>
      <span class="warm-mode-label">{{ $t('theme.dawn') }}</span>
    </span>
    <span class="warm-mode-face warm-mode-dusk" aria-hidden="true">
      <el-icon><Moon /></el-icon>
      <span class="warm-mode-label">{{ $t('theme.dusk') }}</span>
    </span>
  </button>
</template>

<script setup>
import { Moon, Sunny } from '@element-plus/icons-vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()
</script>

<style>
.warm-mode-toggle {
  display: inline-grid;
  align-items: center;
  isolation: isolate;
  min-height: 44px;
  padding: 3px 0;
  border: 0;
  border-radius: 12px;
  background: transparent;
  font: inherit;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.warm-mode-toggle:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}
.warm-mode-face {
  grid-area: 1 / 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  min-width: 62px;
  padding: 0 13px;
  border: 1px solid var(--mode-rim);
  border-radius: 11px;
  background-color: var(--mode-paper);
  color: var(--mode-ink);
  box-shadow: 0 2px 3px rgb(46 32 23 / 14%);
  font-size: 13px;
  font-weight: 650;
  line-height: 1;
  white-space: nowrap;
  transform: translateY(1px) scaleY(0.86);
  transition: transform 230ms var(--ease-soft);
}
.warm-mode-dawn {
  --mode-paper: #fae7bc;
  --mode-rim: #b79462;
  --mode-ink: #654624;
  --mode-turn: -35deg;
  margin-right: 8px;
}
.warm-mode-dusk {
  --mode-paper: #4a3b46;
  --mode-rim: #796572;
  --mode-ink: #fff0d6;
  --mode-turn: 30deg;
  margin-left: 8px;
}
.warm-mode-face .el-icon {
  font-size: 17px;
  transform: translateY(3px) rotate(var(--mode-turn)) scale(0.72);
  transition: transform 300ms var(--ease-soft);
}
.warm-mode-label {
  opacity: 0;
  transform: translateY(4px);
  transition:
    opacity 180ms ease,
    transform 240ms var(--ease-soft);
}
/* 根节点的晨暮状态也驱动扫光幕布里的按钮副本，切换时两处同时翻到正面。 */
html:not(.dark) .warm-mode-dawn,
html.dark .warm-mode-dusk {
  z-index: 1;
  transform: none;
}
html:not(.dark) .warm-mode-dawn .el-icon,
html.dark .warm-mode-dusk .el-icon,
html:not(.dark) .warm-mode-dawn .warm-mode-label,
html.dark .warm-mode-dusk .warm-mode-label {
  transform: none;
  opacity: 1;
}
</style>
