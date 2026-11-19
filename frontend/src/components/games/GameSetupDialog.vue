<!-- 开局设置弹窗:所有小游戏「新游戏」共用一套形态。
     页面只声明字段(key/label/value/options),点确认拿到一份 draft;
     没有可选字段的游戏不要挂本组件,确认后直接开局。
     页面级弹窗挂 GameShell 的 #overlay(全屏时留在整页里),故 append-to-body 由页面按全屏态传入。 -->
<template>
  <el-dialog
    :model-value="modelValue"
    class="dialog-sm gs-setup"
    :title="title || $t('games.setup.title')"
    :show-close="closable"
    :close-on-click-modal="closable"
    :close-on-press-escape="closable"
    :append-to-body="appendToBody"
    @update:model-value="onVisible"
  >
    <p v-if="note" class="gs-setup-note">{{ note }}</p>
    <div class="gs-setup-fields">
      <label v-for="field in fields" :key="field.key" class="gs-setup-field">
        <span>{{ field.label }}</span>
        <el-select v-model="draft[field.key]" :teleported="appendToBody">
          <el-option v-for="opt in field.options" :key="String(opt.value)" :value="opt.value" :label="opt.label" />
        </el-select>
      </label>
    </div>
    <template #footer>
      <div class="gs-setup-actions">
        <slot name="extra" />
        <el-button v-if="showCancel" @click="close">{{ cancelText || $t('games.cancel') }}</el-button>
        <el-button type="primary" @click="confirm">{{ confirmText || $t('games.setup.confirm') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, watch } from 'vue'

const props = defineProps({
  /** 是否打开 */
  modelValue: { type: Boolean, default: false },
  /** 弹窗标题,缺省「新游戏设置」 */
  title: { type: String, default: '' },
  /** 可选字段:[{ key, label, value, options: [{ value, label }] }] */
  fields: { type: Array, default: () => [] },
  /** 字段上方的说明(如数独「上一局没下完」),空则不显示 */
  note: { type: String, default: '' },
  confirmText: { type: String, default: '' },
  cancelText: { type: String, default: '' },
  /** 首次进入强制先选(数独):不显示右上角关闭,遮罩/ESC 也关不掉 */
  closable: { type: Boolean, default: true },
  showCancel: { type: Boolean, default: true },
  /** 全屏时传 false:弹窗与下拉都留在整页里 */
  appendToBody: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue', 'confirm'])

// 打开时把页面当前值复制一份,取消不会把半路的选择写回页面
const draft = reactive({})
const syncDraft = () => {
  for (const field of props.fields) draft[field.key] = field.value
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) syncDraft()
  },
  { immediate: true },
)

const close = () => emit('update:modelValue', false)
const onVisible = (visible) => {
  if (!visible) close()
}
const confirm = () => {
  emit('confirm', { ...draft })
  close()
}
</script>

<style scoped>
.gs-setup-note {
  margin: 0 0 12px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px dashed var(--color-border, rgba(0, 0, 0, 0.12));
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-line;
  color: var(--color-text-secondary, #8a8a8a);
}
.gs-setup-fields {
  display: grid;
  gap: 10px;
}
.gs-setup-field {
  display: grid;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
.gs-setup-field .el-select {
  width: 100%;
}
.gs-setup-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
</style>
