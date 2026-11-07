<!-- 气象预警信号图标:级别色方块 + 预警类型图形(和风图标库)+ 类型名。
     参照气象台预警信号(蓝/黄/橙/红底 + 类型图形 + 名称),多预警并排时也能一眼区分类型。
     图形编号取自预警数据 code(1001-1089 与图标库同号),未知类型回退通用预警图标。 -->
<template>
  <span
    class="warn-signal"
    :class="{ compact }"
    :style="{ background: warnLevelColor(level), color: warnInkColor(level) }"
    aria-hidden="true"
  >
    <i :class="`qi-${warnIconCode(code)}`" class="warn-signal-glyph"></i>
    <span v-if="typeName" class="warn-signal-name">{{ typeName }}</span>
  </span>
</template>

<script setup>
import { warnIconCode, warnInkColor, warnLevelColor } from '@/utils/dict'

defineProps({
  level: { type: String, default: '' },
  typeName: { type: String, default: '' },
  code: { type: [String, Number], default: '' },
  /** 单条预警卡片 / 弹窗里用更小的一档 */
  compact: { type: Boolean, default: false },
})
</script>

<style scoped>
.warn-signal {
  display: inline-flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 58px;
  height: 58px;
  padding: 5px 3px 4px;
  border-radius: 9px;
  line-height: 1;
  user-select: none;
}
.warn-signal.compact {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  gap: 1px;
  padding: 4px 2px 3px;
}
.warn-signal-glyph {
  font-size: 25px;
  line-height: 1;
}
.warn-signal.compact .warn-signal-glyph {
  font-size: 20px;
}
.warn-signal-name {
  max-width: 100%;
  font-size: 10px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.warn-signal.compact .warn-signal-name {
  font-size: 9px;
}
</style>
