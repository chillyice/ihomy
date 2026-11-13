<template>
  <!-- 分享范围选择器:不选任何圈 = 只给自家人;圈与圈内家庭由调用方传入(纯展示) -->
  <div class="audience-picker">
    <div class="ap-label">{{ $t('audience.label') }}</div>
    <div class="ap-family-only" :class="{ active: !modelValue.length }">
      <el-icon><Lock /></el-icon>
      <span>{{ $t('audience.familyOnly') }}</span>
      <span class="ap-note">{{ $t('audience.familyOnlyHint') }}</span>
    </div>
    <el-checkbox-group
      v-if="circles.length"
      class="ap-circles"
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="onChange"
    >
      <el-checkbox
        v-for="c in circles"
        :key="c.id"
        :value="c.id"
        :disabled="disabled || (!modelValue.includes(c.id) && modelValue.length >= 3)"
        border
      >
        <span class="ap-circle-name">{{ c.name }}</span>
        <span v-if="familyNames(c)" class="ap-families">{{ familyNames(c) }}</span>
      </el-checkbox>
    </el-checkbox-group>
    <p class="ap-hint">{{ circles.length ? $t('audience.circleHint') : $t('audience.noCircle') }}</p>
    <p v-if="circles.length && modelValue.length >= 3" class="ap-hint">{{ $t('audience.maxCircles') }}</p>
  </div>
</template>

<script setup>
import { Lock } from '@element-plus/icons-vue'

defineProps({
  /** 选中的亲友圈 id 列表;空数组 = 只给自家人 */
  modelValue: { type: Array, default: () => [] },
  /** [{ id, name, families: [{ familyName, alias }] }] */
  circles: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

function onChange(value) {
  emit('update:modelValue', value)
}

// 圈内其他家庭:优先圈内称呼,没有就用家庭名;只做一行摘要,不暴露成员
function familyNames(circle) {
  const list = (circle.families || []).map((f) => f.alias || f.familyName).filter(Boolean)
  return list.join('、')
}
</script>

<style scoped>
.audience-picker {
  width: 100%;
}
.ap-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}
.ap-family-only {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: var(--color-card-2);
  font-size: 13px;
  color: var(--color-text-secondary);
}
.ap-family-only.active {
  border-color: rgba(var(--color-brand-rgb), 0.35);
  color: var(--color-text);
}
.ap-note {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.ap-circles {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}
.ap-circles :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
  padding: 8px 12px;
  border-radius: 10px;
  align-items: flex-start;
}
.ap-circles :deep(.el-checkbox__label) {
  display: flex;
  flex-direction: column;
  gap: 2px;
  white-space: normal;
  line-height: 1.4;
  padding-left: 8px;
}
.ap-circle-name {
  font-size: 13.5px;
  color: var(--color-text);
}
.ap-families {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.ap-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary);
}
</style>
