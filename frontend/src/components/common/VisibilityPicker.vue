<template>
  <!-- 可见范围选择器:各处发布/编辑弹窗共用,标签文案由调用方按业务命名空间传入 -->
  <el-radio-group :model-value="modelValue" :size="size" :disabled="disabled" @update:model-value="onChange">
    <template v-if="button">
      <el-radio-button v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </el-radio-button>
    </template>
    <template v-else>
      <el-radio v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </el-radio>
    </template>
  </el-radio-group>
</template>

<script setup>
/**
 * 可见范围选择(纯展示,不做取值归一):
 * 选项由调用方给出,便于博客/日记/相册/书架各用各的文案;后续「亲友圈」只是多一个选项。
 */
defineProps({
  modelValue: { type: String, default: '' },
  /** [{ value, label }] */
  options: { type: Array, required: true },
  /** 紧凑按钮组(日记页脚这类窄容器用) */
  button: { type: Boolean, default: false },
  size: { type: String, default: 'default' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

function onChange(value) {
  emit('update:modelValue', value)
}
</script>
