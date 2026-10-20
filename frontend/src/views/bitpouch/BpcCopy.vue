<!-- 工作台通用:一键复制。纯按钮,不引 Element Plus;复制结果用同一按钮回显。 -->
<template>
  <button class="bpc-btn bpc-btn-sm" type="button" :disabled="!value" :class="{ 'is-flash': state === 'ok' }" @click="run">
    {{ text }}
  </button>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  value: { type: String, default: '' },
  // 默认「复制」;不同面板可传「复制全部 / 复制匹配」等。
  label: { type: String, default: '' },
})

const { t } = useI18n()
const state = ref('idle')
let timer = null

const text = computed(() => {
  if (state.value === 'ok') return t('bitPouch.tools.copied')
  if (state.value === 'fail') return t('bitPouch.tools.copyFailed')
  return props.label || t('bitPouch.tools.copy')
})

const run = async () => {
  if (!props.value) return
  try {
    await navigator.clipboard.writeText(props.value)
    state.value = 'ok'
  } catch {
    state.value = 'fail'
  }
  clearTimeout(timer)
  timer = setTimeout(() => {
    state.value = 'idle'
  }, 1500)
}

onBeforeUnmount(() => clearTimeout(timer))
</script>
