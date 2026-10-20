<!-- 工作台通用:单选 / 多选开关组(模式、算法、标志等)。 -->
<template>
  <div class="bpc-chips" role="group">
    <button
      v-for="opt in options"
      :key="opt.value"
      class="bpc-chip"
      :class="{ 'is-on': isOn(opt.value) }"
      type="button"
      :aria-pressed="isOn(opt.value)"
      @click="pick(opt.value)"
    >
      <span v-if="multiple" class="bpc-chip-dot" aria-hidden="true"></span>{{ opt.label }}
    </button>
  </div>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: [String, Array], required: true },
  options: { type: Array, required: true },
  multiple: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const isOn = (value) => (props.multiple ? props.modelValue.includes(value) : props.modelValue === value)

const pick = (value) => {
  if (!props.multiple) {
    emit('update:modelValue', value)
    return
  }
  const next = props.modelValue.includes(value) ? props.modelValue.filter((item) => item !== value) : [...props.modelValue, value]
  emit('update:modelValue', next)
}
</script>
