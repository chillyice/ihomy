<!-- 日程单行:类型/来源徽标 + 时刻 + 标题 + 提醒/循环提示 + 完成控件 + 编辑/删除。
     月历的当日详情与清单模式共用,避免两处各写一份行渲染。
     贷款还款/理财到期/食材到期是只读来源:只显示来源徽标与提示,可点击跳原页面,无勾选/编辑/删除。
     徽标始终显示,标明这一行自身属性(纪念日/提醒/贷款还款…)。 -->
<template>
  <div class="sched-row" :class="{ done: done, 'read-only': readOnly }" v-a11y-click @click="$emit('open', item)">
    <span class="sched-badge" :class="badgeClass">{{ badgeText }}</span>
    <span class="sched-time">{{ item.time || $t('schedule.allDay') }}</span>
    <span class="sched-title">{{ item.title }}</span>
    <span v-if="hint" class="sched-hint">{{ hint }}</span>
    <span v-if="doneTag" class="sched-done">{{ doneTag }}</span>
    <span class="sched-actions" @click.stop>
      <el-checkbox v-if="checkable" :model-value="done" @change="onCheck" />
      <el-tooltip v-if="editable" :content="$t('common.edit')" placement="top" :show-after="300">
        <el-button size="small" text @click="$emit('edit', item)"
          ><el-icon><Edit /></el-icon
        ></el-button>
      </el-tooltip>
      <el-tooltip v-if="deletable" :content="$t('common.delete')" placement="top" :show-after="300">
        <el-button size="small" text type="danger" @click="$emit('delete', item)"
          ><el-icon><Delete /></el-icon
        ></el-button>
      </el-tooltip>
    </span>
  </div>
</template>

<script setup>
// 完成控件只给「提醒 / 每日任务 / 计划子任务」;纪念日、事项、计划走详情或跳原页;
// 只读来源(贷款/理财/食材)没有完成语义,一律不给勾选与改删。
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Edit, Delete } from '@element-plus/icons-vue'
import { typeOfKind } from '@/utils/scheduleType'
import { isReadOnlySource, sourceOf } from '@/utils/scheduleSource'

const props = defineProps({
  /** /schedule/events 的一项 */
  item: { type: Object, required: true },
  /** 对应表的完整记录(纪念日看提前提醒天数,计划看子任务等),可为空 */
  record: { type: Object, default: null },
  /** 游客不显示编辑/删除 */
  canEdit: { type: Boolean, default: true },
})
const emit = defineEmits(['open', 'edit', 'delete', 'toggle', 'toggle-task'])
const { t } = useI18n()

const type = computed(() => typeOfKind(props.item.kind))
const readOnly = computed(() => isReadOnlySource(props.item))
const badgeText = computed(() => (readOnly.value ? t('schedule.source.' + sourceOf(props.item)) : t('schedule.type.' + type.value)))
const badgeClass = computed(() => (readOnly.value ? sourceOf(props.item).toLowerCase() : type.value.toLowerCase()))

const REPEAT_KEY = { DAILY: 'repeatDaily', WEEKLY: 'repeatWeekly', MONTHLY: 'repeatMonthly', YEARLY: 'repeatYearly' }

// 提示优先级:纪念日提前提醒 > 贷款期次 > 循环周期(当天时刻已在左侧显示)
const hint = computed(() => {
  if (props.item.kind === 'ANNIVERSARY' && props.record?.remindDaysBefore > 0) {
    return t('schedule.remindAdvance', { n: props.record.remindDaysBefore })
  }
  if (readOnly.value && props.item.kind === 'LOAN' && props.item.period) {
    return t('schedule.source.loanPeriod', { n: props.item.period })
  }
  const key = REPEAT_KEY[props.item.repeat]
  return key ? t('schedule.dialog.' + key) : ''
})

const done = computed(() => {
  if (props.item.kind === 'TASK') return !!props.item.done
  if (props.item.kind === 'REMINDER') return props.item.done === 1
  if (props.item.kind === 'PLAN_TASK') return !!props.item.done
  return false
})

const doneTag = computed(() => {
  if (!done.value) return ''
  // 循环提醒的完成态按日记录:只有当天才说「今日已完成」,回看往日说「已完成」
  return props.item.kind === 'TASK' && props.item.doneToday ? t('schedule.doneToday') : t('schedule.done')
})

const checkable = computed(() => props.canEdit && !readOnly.value && ['REMINDER', 'TASK', 'PLAN_TASK'].includes(props.item.kind))
const editable = computed(() => props.canEdit && !readOnly.value && ['REMINDER', 'TASK', 'ANNIVERSARY', 'PLAN'].includes(props.item.kind))
const deletable = computed(() => props.canEdit && !readOnly.value && props.item.kind !== 'PLAN_TASK')

const onCheck = () => {
  if (props.item.kind === 'PLAN_TASK') emit('toggle-task', props.item)
  else emit('toggle', props.item)
}
</script>

<style scoped>
.sched-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 10px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  min-width: 0;
  cursor: pointer;
}
.sched-row.done {
  opacity: 0.6;
}
/* 只读来源(贷款/理财/食材):虚线边提示「这里只看看,改要去原页面」,配色沿用主题不另起色板 */
.sched-row.read-only {
  border-style: dashed;
}
.sched-row.done .sched-title {
  text-decoration: line-through;
}
.sched-badge {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 18px;
  padding: 0 8px;
  border-radius: 9px;
  background: rgba(var(--color-brand-rgb), 0.16);
  color: var(--color-brand-hover);
  white-space: nowrap;
}
.sched-badge.anniversary {
  background: rgba(230, 162, 60, 0.16);
  color: var(--color-warning);
}
.sched-badge.task {
  background: rgba(168, 72, 58, 0.12);
  color: var(--color-accent);
}
.sched-badge.plan {
  background: rgba(31, 58, 95, 0.08);
  color: var(--color-text-secondary);
}
.sched-badge.loan {
  background: rgba(31, 107, 74, 0.14);
  color: #2c7a58;
}
.sched-badge.finance {
  background: rgba(108, 76, 158, 0.14);
  color: #6b4c9e;
}
.sched-badge.item_expiry {
  background: rgba(122, 138, 40, 0.16);
  color: #6d7c1f;
}
/* 暮色:半透明底不变,只把固定深色文字提亮一档,保证深色卡片上可读 */
html.dark .sched-badge.anniversary {
  color: #e8c06a;
}
html.dark .sched-badge.loan {
  color: #6fc79c;
}
html.dark .sched-badge.finance {
  color: #b79ae0;
}
html.dark .sched-badge.item_expiry {
  color: #b3c45c;
}
.sched-time {
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  font-size: 13px;
  color: var(--color-brand-hover);
  min-width: 40px;
}
.sched-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sched-hint,
.sched-done {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.sched-done {
  color: var(--color-brand-hover);
}
.sched-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.sched-actions :deep(.el-button) {
  padding: 4px 5px;
}
@media (max-width: 768px) {
  .sched-row {
    flex-wrap: wrap;
    gap: 6px 8px;
  }
  .sched-title {
    flex: 1 1 40%;
  }
  .sched-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
