<!-- 预警三角叹号:圆角三角形 + 镂空感叹号,圆角半径刻意收敛(不做成圆形或大圆角)。
     颜色跟随 currentColor,尺寸由外层 font-size 决定,侧栏迷你天气与天气页预警共用同一形状。 -->
<template>
  <svg class="warn-triangle" viewBox="0 0 24 24" role="presentation" aria-hidden="true">
    <defs>
      <mask :id="maskId" maskUnits="userSpaceOnUse" x="0" y="0" width="24" height="24">
        <path d="M12 4.4 21.1 20H2.9Z" fill="#fff" stroke="#fff" stroke-width="3" stroke-linejoin="round" />
        <rect x="10.95" y="8.5" width="2.1" height="6.3" rx="1.05" fill="#000" />
        <circle cx="12" cy="17.4" r="1.25" fill="#000" />
      </mask>
    </defs>
    <rect width="24" height="24" fill="currentColor" :mask="`url(#${maskId})`" />
  </svg>
</template>

<script setup>
import { useId } from 'vue'

const maskId = `warn-triangle-${useId()}`
</script>

<style scoped>
.warn-triangle {
  display: block;
  width: 1em;
  height: 1em;
  flex: none;
}
</style>
