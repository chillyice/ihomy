<template>
  <el-drawer v-model="open" direction="btt" size="72%" :with-header="false" class="mobile-tab-editor" append-to-body>
    <div class="editor">
      <div class="editor-head paper-heading">
        <div class="editor-title">{{ $t('mobile.tabEditTitle') }}</div>
        <el-button text type="primary" @click="close">{{ $t('mobile.tabDone') }}</el-button>
      </div>
      <div class="editor-hint">{{ $t('mobile.tabEditHint') }}</div>

      <!-- 五格预览:点一格,再从下面选功能放进去 -->
      <div class="slot-row paper-surface">
        <div
          v-for="(tab, i) in tabs"
          :key="i"
          v-a11y-click
          class="slot"
          :class="{ picked: i === selected, capture: tab.capture }"
          :aria-label="$t('mobile.tabPosition', { n: i + 1 })"
          @click="selected = i"
        >
          <span class="slot-icon" :class="{ 'is-capture': tab.capture }">
            <el-icon><component :is="iconOf(tab)" /></el-icon>
          </span>
          <span class="slot-label">{{ labelOf(tab) }}</span>
        </div>
      </div>

      <div class="candidate-head">{{ $t('mobile.tabPickFor', { n: selected + 1 }) }}</div>
      <div class="candidate-grid">
        <div
          v-for="c in candidates"
          :key="c.code"
          v-a11y-click
          class="candidate"
          :class="{ used: usedAt(c.code) >= 0 }"
          @click="assign(c.code)"
        >
          <span class="candidate-icon">
            <el-icon><component :is="iconOf(c)" /></el-icon>
          </span>
          <span class="candidate-label">{{ labelOf(c) }}</span>
          <span v-if="usedAt(c.code) >= 0" class="candidate-mark">{{ usedAt(c.code) + 1 }}</span>
        </div>
      </div>

      <div class="editor-foot">
        <el-button text @click="reset">{{ $t('mobile.tabReset') }}</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { HomeFilled, Grid, User, Plus } from '@element-plus/icons-vue'
import { iconComp } from '@/utils/navModules'
import { useMobileTabs } from '@/composables/useMobileTabs'

const props = defineProps({ modelValue: { type: Boolean, default: false } })
const emit = defineEmits(['update:modelValue'])
const { t } = useI18n()
const { candidates, tabs, codes, save, reset: resetTabs } = useMobileTabs()

// 弹层沿用全局弹窗层级(--z-overlay):光影/氛围层本来就要压在弹窗之上,别再抬到这里
const selected = ref(2)
const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

watch(
  () => props.modelValue,
  (v) => {
    // 打开时默认落在中间格,和「中间是加号」的第一直觉一致
    if (v) selected.value = 2
  },
)

const SHELL_ICON = { home: HomeFilled, more: Grid, me: User, capture: Plus }
const iconOf = (item) => SHELL_ICON[item.code] || iconComp(item.code)
const labelOf = (item) => (item.titleKey ? t(item.titleKey) : item.title || '')

const usedAt = (code) => codes.value.indexOf(code)
const close = () => {
  open.value = false
}

// 选中功能直接落到当前格;该功能已在别的格则两格互换,不会出现两个相同入口
const assign = (code) => {
  const next = [...codes.value]
  const existing = next.indexOf(code)
  if (existing >= 0 && existing !== selected.value) next[existing] = next[selected.value]
  next[selected.value] = code
  save(next)
  if (selected.value < next.length - 1) selected.value += 1
}

const reset = () => {
  resetTabs()
  selected.value = 2
}
</script>

<style scoped>
/* 抽屉默认给 body 一圈内边距,会把 100% 高的编辑器顶出去;这里只留顶部一点呼吸位 */
.mobile-tab-editor :deep(.el-drawer__body) {
  padding: 14px 0 0;
  overflow: hidden;
}
.editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 4px 16px calc(16px + env(safe-area-inset-bottom, 0px));
}
.editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.editor-title {
  font-size: 16px;
  font-weight: 600;
}
.editor-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}
.slot-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  margin-top: 14px;
  padding: 10px 6px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.03);
}
html.dark .slot-row {
  background: rgba(255, 255, 255, 0.05);
}
.slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px 2px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.slot.picked {
  border-color: var(--color-brand);
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.1);
}
.slot-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 19px;
  color: var(--color-primary, var(--color-brand));
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.1);
}
.slot-icon.is-capture {
  color: #fff;
  background: var(--color-brand);
}
.slot-label {
  max-width: 100%;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.candidate-head {
  margin-top: 18px;
  font-size: 13px;
  font-weight: 600;
}
.candidate-grid {
  flex: 1;
  overflow-y: auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px 6px;
  padding: 12px 0;
  align-content: start;
}
.candidate {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.candidate:active {
  opacity: 0.7;
}
.candidate-icon {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: var(--color-primary, var(--color-brand));
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.08);
}
.candidate-label {
  font-size: 11px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.candidate.used .candidate-icon {
  box-shadow: 0 0 0 2px var(--color-brand) inset;
}
.candidate-mark {
  position: absolute;
  top: -4px;
  right: calc(50% - 27px);
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #fff;
  background: var(--color-brand);
}
.editor-foot {
  display: flex;
  justify-content: center;
  padding-top: 4px;
}
</style>
