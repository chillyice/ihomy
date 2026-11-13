<template>
  <!-- 分享家庭选择器:不勾任何家庭 = 只给自家人;可选家庭来自家庭设置里的亲友圈 -->
  <div class="audience-picker">
    <div class="ap-label">{{ $t('audience.label') }}</div>
    <div class="ap-family-only" :class="{ active: !modelValue.length }">
      <el-icon><Lock /></el-icon>
      <span>{{ $t('audience.familyOnly') }}</span>
      <span class="ap-note">{{ $t('audience.familyOnlyHint') }}</span>
    </div>
    <el-checkbox-group
      v-if="families.length"
      class="ap-families"
      :model-value="modelValue"
      :disabled="disabled"
      @update:model-value="onChange"
    >
      <el-checkbox v-for="f in families" :key="f.familyId" :value="f.familyId" :disabled="disabled" border>
        <span class="ap-family-name">{{ f.alias || f.familyName }}</span>
        <span v-if="circleLabel(f)" class="ap-circle">{{ circleLabel(f) }}</span>
      </el-checkbox>
    </el-checkbox-group>
    <p class="ap-hint">{{ families.length ? $t('audience.circleHint') : $t('audience.noCircle') }}</p>
  </div>
</template>

<script setup>
import { Lock } from '@element-plus/icons-vue'

defineProps({
  /** 选中的接收家庭 id 列表;空数组 = 只给自家人 */
  modelValue: { type: Array, default: () => [] },
  /** [{ familyId, familyName, alias, circleName }] */
  families: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

function onChange(value) {
  emit('update:modelValue', value)
}

// 家庭名可能与圈名重复,只在有圈名时做小字补充,不暴露成员
function circleLabel(family) {
  const name = family.alias ? family.familyName : null
  return [name, family.circleName].filter(Boolean).join(' · ')
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
.ap-families {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.ap-families :deep(.el-checkbox) {
  margin-right: 0;
  height: auto;
  padding: 7px 12px;
  border-radius: 10px;
}
.ap-families :deep(.el-checkbox__label) {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  white-space: normal;
  line-height: 1.4;
  padding-left: 8px;
}
.ap-family-name {
  font-size: 13.5px;
  color: var(--color-text);
}
.ap-circle {
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
