<!-- 统一新增/编辑弹窗:只问日期(可空)、循环、提醒三件事,顶部实时显示推导出的类型。
     保存按 utils/scheduleType.classify 路由到既有接口(计划/提醒/纪念日),后端不做分类推断。 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="isEdit ? $t('schedule.dialog.edit') : $t('schedule.dialog.create')" width="480px">
    <div class="type-line">
      <span class="type-text">{{ $t('schedule.dialog.typeBadge', { type: typeText }) }}</span>
      <el-tag size="small">{{ typeText }}</el-tag>
    </div>

    <el-form :model="form" label-position="top">
      <el-form-item :label="$t('schedule.dialog.title')">
        <el-input v-model="form.title" maxlength="100" :placeholder="$t('schedule.dialog.titlePlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('schedule.dialog.content')">
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="2"
          maxlength="500"
          :placeholder="$t('schedule.dialog.contentPlaceholder')"
        />
      </el-form-item>

      <el-form-item v-if="showDatePicker" :label="$t('schedule.dialog.date')">
        <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        <div class="field-hint">{{ $t('schedule.dialog.dateOptional') }}</div>
      </el-form-item>

      <el-form-item :label="$t('schedule.dialog.repeat')">
        <el-select v-model="form.repeat" style="width: 100%" @change="onRepeatChange">
          <el-option v-for="r in repeatOptions" :key="r" :label="repeatText(r)" :value="r" />
        </el-select>
        <div v-if="form.repeat !== 'NONE'" class="field-hint">{{ $t('schedule.dialog.repeatHint') }}</div>
      </el-form-item>

      <template v-if="form.repeat === 'YEARLY'">
        <el-form-item :label="$t('schedule.dialog.calendar')">
          <el-radio-group v-model="form.calendar" @change="onCalendarChange">
            <el-radio value="solar">{{ $t('schedule.dialog.solar') }}</el-radio>
            <el-radio value="lunar">{{ $t('schedule.dialog.lunar') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="form-row">
          <el-form-item :label="$t('schedule.dialog.monthLabel')">
            <el-select v-model="form.month" style="width: 100%" @change="clampDay">
              <el-option v-for="m in 12" :key="m" :label="$t('schedule.dialog.monthOption', { n: m })" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('schedule.dialog.dayLabel')">
            <el-select v-model="form.day" style="width: 100%">
              <el-option v-for="d in dayOptions" :key="d" :label="$t('schedule.dialog.dayOption', { n: d })" :value="d" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item v-if="form.calendar === 'lunar'" :label="$t('schedule.dialog.leapMonth')">
          <el-switch v-model="form.isLeap" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="kind === 'ANNIVERSARY'" :label="$t('schedule.dialog.members')">
          <el-select
            v-model="form.memberIds"
            multiple
            collapse-tags
            collapse-tags-tooltip
            clearable
            :placeholder="$t('schedule.dialog.membersPlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="m in members" :key="m.id" :label="m.nickname || m.username" :value="m.id" />
          </el-select>
        </el-form-item>
      </template>

      <el-form-item :label="$t('schedule.dialog.remind')">
        <el-select v-model="form.remind" style="width: 100%">
          <el-option v-for="r in remindOptions" :key="r" :label="remindText(r)" :value="r" />
        </el-select>
      </el-form-item>

      <div v-if="form.remind !== 'NONE'" class="form-row">
        <el-form-item v-if="form.remind === 'ADVANCE'" :label="$t('schedule.dialog.advanceDays')">
          <el-select v-model="form.advanceDays" style="width: 100%">
            <el-option v-for="d in 30" :key="d" :label="$t('schedule.dialog.advanceDaysOption', { n: d })" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('schedule.dialog.time')">
          <el-time-picker v-model="form.time" value-format="HH:mm" format="HH:mm" style="width: 100%" />
        </el-form-item>
      </div>

      <div v-if="planReminderHint" class="field-hint">{{ planReminderHint }}</div>
      <!-- 「给谁看」:纪念日/提醒可分享到亲友圈;计划与待办(计划本身)不参与分享 -->
      <div v-if="shareable && audience.state.loaded" class="audience-block">
        <AudiencePicker v-if="audience.state.canShare" v-model="audience.state.selected" :circles="audience.state.circles" />
        <p v-else class="field-hint">{{ $t('audience.canShareFalse') }}</p>
      </div>
      <div v-if="isEdit" class="field-hint">{{ $t('schedule.dialog.lockType') }}</div>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 表单始终只保留「标题/内容/日期/循环/提醒」这些原始开关,分类由 computed 推导;
// 编辑态锁住循环与提醒方式的可选范围,避免跨表搬迁导致数据丢失
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { anniversaryApi, planApi, reminderApi } from '@/api'
import { classify, REPEAT, REPEAT_OPTIONS } from '@/utils/scheduleType'
import { isDateString, todayString } from '@/utils/scheduleCalendar'
import { useAudience } from '@/composables/useAudience'
import AudiencePicker from '@/components/common/AudiencePicker.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 编辑:{ kind, record };新建为 null */
  item: { type: Object, default: null },
  /** 新建时的默认日期(点某日新增时带入) */
  defaultDate: { type: String, default: '' },
  members: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const saving = ref(false)

const pad2 = (n) => String(n).padStart(2, '0')

const blank = () => {
  const today = props.defaultDate && isDateString(props.defaultDate) ? props.defaultDate : todayString()
  const [, m, d] = today.split('-').map(Number)
  return {
    id: null,
    title: '',
    content: '',
    date: today,
    repeat: REPEAT.NONE,
    remind: 'NONE',
    time: '08:00',
    advanceDays: 3,
    calendar: 'solar',
    month: m,
    day: d,
    isLeap: 0,
    memberIds: [],
    /** 编辑计划时,原计划已存在托管提醒:保留不动,只提示 */
    planRemindTime: '',
    initialDate: '',
  }
}

const form = reactive(blank())

// 从完整记录回填表单:ONCE 映射成 NONE,每年循环按历法取月日
const fill = (it) => {
  const base = blank()
  if (!it) return base
  const { kind, record } = it
  if (kind === 'ANNIVERSARY') {
    return {
      ...base,
      id: record.id,
      title: record.name || '',
      repeat: REPEAT.YEARLY,
      calendar: record.calendar || 'solar',
      month: record.month || 1,
      day: record.day || 1,
      isLeap: record.isLeap || 0,
      remind: (record.remindDaysBefore || 0) > 0 ? 'ADVANCE' : 'NONE',
      advanceDays: record.remindDaysBefore || 3,
      time: record.remindTime ? String(record.remindTime).slice(0, 5) : '08:00',
      memberIds: Array.isArray(record.memberIds) ? [...record.memberIds] : [],
    }
  }
  if (kind === 'REMINDER' || kind === 'TASK') {
    // 循环提醒必须回填真实周期:否则每日/每周/每月任务打开编辑会显示「提醒」徽标,保存时还会退化成一次性
    const recurring = !!record.repeatType && record.repeatType !== 'ONCE'
    const yearly = record.repeatType === REPEAT.YEARLY
    const lunar = (record.calendar || 'solar') === 'lunar'
    let month = base.month
    let day = base.day
    if (yearly && lunar) {
      month = record.lunarMonth || base.month
      day = record.lunarDay || base.day
    } else if (yearly && record.remindDate) {
      month = Number(record.remindDate.slice(5, 7))
      day = Number(record.remindDate.slice(8, 10))
    }
    return {
      ...base,
      id: record.id,
      title: record.title || '',
      content: record.content || '',
      date: record.remindDate || '',
      initialDate: record.remindDate || '',
      repeat: recurring ? record.repeatType : REPEAT.NONE,
      remind: 'ON_TIME',
      time: record.remindTime ? String(record.remindTime).slice(0, 5) : '08:00',
      calendar: yearly ? record.calendar || 'solar' : 'solar',
      month,
      day,
      isLeap: record.isLeap || 0,
    }
  }
  // 计划 / 事项
  return {
    ...base,
    id: record.id,
    title: record.title || '',
    content: record.description || '',
    date: record.targetDate || '',
    initialDate: record.targetDate || '',
    repeat: REPEAT.NONE,
    remind: 'NONE',
    planRemindTime: record.remindTime ? String(record.remindTime).slice(0, 5) : '',
  }
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    Object.assign(form, fill(props.item))
    // 编辑时载入该条已选的圈;新建只载圈列表(类型可能随表单变化,保存时再定)
    const editingType = !props.item ? 'reminder' : props.item.kind === 'ANNIVERSARY' ? 'anniversary' : 'reminder'
    await audience.load(props.item ? form.id : null, editingType)
  },
)

const isEdit = computed(() => !!form.id)
const editingKind = computed(() => props.item?.kind || null)
const kind = computed(() =>
  classify({
    hasDate: form.repeat === REPEAT.YEARLY ? true : !!form.date,
    repeat: form.repeat,
    remind: form.remind,
  }),
)
const typeText = computed(() => t('schedule.type.' + kind.value))

// 「给谁看」:只有纪念日与提醒(family_anniversary / family_reminder)可分享,计划与待办不参与
const audience = useAudience('reminder')
const shareable = computed(() => ['ANNIVERSARY', 'REMINDER', 'TASK'].includes(kind.value))

// 每年循环用月/日选择器代替日期选择器
const showDatePicker = computed(() => form.repeat !== REPEAT.YEARLY)

// 该月最大天数:农历每月最多 30;阳历按月份(2024 是闰年,2 月给到 29)。
// 已有值超界时保留在选项里,不静默改动旧数据
const maxDay = () => (form.calendar === 'lunar' ? 30 : new Date(Date.UTC(2024, form.month, 0)).getUTCDate())
const dayOptions = computed(() => Math.max(maxDay(), form.day || 0))
const clampDay = () => {
  const max = maxDay()
  if (form.day > max) form.day = max
}

// 编辑态只允许同类型内调整:选项范围按原始分类收窄
const repeatOptions = computed(() => {
  const k = editingKind.value
  if (!k) return REPEAT_OPTIONS
  if (k === 'ANNIVERSARY') return [REPEAT.YEARLY]
  if (k === 'TASK') return [REPEAT.DAILY, REPEAT.WEEKLY, REPEAT.MONTHLY, REPEAT.YEARLY]
  return [REPEAT.NONE]
})

const remindOptions = computed(() => {
  const k = editingKind.value
  if (!k) return form.repeat === REPEAT.YEARLY ? ['NONE', 'ON_TIME', 'ADVANCE'] : ['NONE', 'ON_TIME']
  if (k === 'ANNIVERSARY') return ['NONE', 'ADVANCE']
  if (k === 'REMINDER' || k === 'TASK') return ['ON_TIME']
  return ['NONE']
})

// 提前提醒只对「每年循环」有意义:切回其它周期时回落为不提醒。
// 用 @change 而不是 watch:程序化回填表单(打开编辑)不该触发这套联动
const onRepeatChange = (r) => {
  // 循环必须有起点:没选日期时默认今天,避免出现「有周期但没有日期」的半截状态
  if (r !== REPEAT.NONE && r !== REPEAT.YEARLY && !form.date) form.date = todayString()
  if (r !== REPEAT.YEARLY && form.remind === 'ADVANCE' && !editingKind.value) form.remind = 'NONE'
  if (r === REPEAT.YEARLY && form.calendar === 'solar' && form.date) seedMonthDay()
}

const seedMonthDay = () => {
  if (!isDateString(form.date)) return
  form.month = Number(form.date.slice(5, 7))
  form.day = Number(form.date.slice(8, 10))
}

const onCalendarChange = (c) => {
  if (c === 'solar') seedMonthDay()
  clampDay()
}

const REPEAT_KEY = {
  [REPEAT.NONE]: 'repeatNone',
  [REPEAT.DAILY]: 'repeatDaily',
  [REPEAT.WEEKLY]: 'repeatWeekly',
  [REPEAT.MONTHLY]: 'repeatMonthly',
  [REPEAT.YEARLY]: 'repeatYearly',
}
const repeatText = (r) => t('schedule.dialog.' + (REPEAT_KEY[r] || 'repeatNone'))
const remindText = (r) =>
  t(
    {
      NONE: 'schedule.dialog.remindNone',
      ON_TIME: 'schedule.dialog.remindOnTime',
      ADVANCE: 'schedule.dialog.remindAdvance',
    }[r] || 'schedule.dialog.remindNone',
  )

const planReminderHint = computed(() => {
  if (!isEdit.value || !form.planRemindTime) return ''
  return t('schedule.dialog.planReminderKept', { time: form.planRemindTime })
})

// 每年(阳历)提醒的锚定公历日:从今年起找第一个「该月日存在且不早于今天」的年份。
// 不能直接写今年的日期:2/29 在平年是非法日期,退化成今天会让每年循环悄悄变成「今天这个月日」
const anchorSolar = () => {
  const today = todayString()
  const baseYear = Number(today.slice(0, 4))
  for (let i = 0; i <= 8; i++) {
    const candidate = `${baseYear + i}-${pad2(form.month)}-${pad2(form.day)}`
    if (isDateString(candidate) && candidate >= today) return candidate
  }
  return ''
}

const onSave = async () => {
  const k = kind.value
  if (!form.title?.trim()) return ElMessage.warning(t('schedule.dialog.fillTitle'))
  // 有周期就必须有起点(每年循环用月/日,不受此限)
  if (form.repeat !== REPEAT.NONE && form.repeat !== REPEAT.YEARLY && !form.date) {
    return ElMessage.warning(t('schedule.dialog.needDate'))
  }
  if (form.remind !== 'NONE' && !form.time) return ElMessage.warning(t('schedule.dialog.needTime'))
  if (k === 'REMINDER' || k === 'TASK') {
    if (form.repeat !== REPEAT.YEARLY && !form.date) return ElMessage.warning(t('schedule.dialog.needDate'))
    if (form.repeat === REPEAT.YEARLY && form.calendar === 'lunar' && (!form.month || !form.day)) {
      return ElMessage.warning(t('schedule.dialog.needLunar'))
    }
  }
  if (k === 'ANNIVERSARY' && form.calendar === 'lunar' && (!form.month || !form.day)) {
    return ElMessage.warning(t('schedule.dialog.needLunar'))
  }
  if (isEdit.value && form.initialDate && !form.date && (k === 'PLAN' || k === 'ITEM')) {
    return ElMessage.warning(t('schedule.dialog.dateLocked'))
  }
  // 阳历每年的锚定日必须真实存在(2/30 这类组合永远等不到)
  const solarAnchor = k === 'TASK' && form.repeat === REPEAT.YEARLY && form.calendar === 'solar' ? anchorSolar() : ''
  if (k === 'TASK' && form.repeat === REPEAT.YEARLY && form.calendar === 'solar' && !solarAnchor) {
    return ElMessage.warning(t('schedule.dialog.invalidDate'))
  }

  saving.value = true
  try {
    let savedId = form.id
    if (k === 'PLAN' || k === 'ITEM') {
      const payload = {
        title: form.title.trim(),
        description: form.content || '',
        targetDate: k === 'ITEM' ? form.date : null,
        // null=不改动计划托管的提醒:本弹窗不新增计划提醒(那是「提醒」类型的职责)
        remindTime: null,
      }
      if (form.id) await planApi.update(form.id, payload)
      else await planApi.create(payload)
    } else if (k === 'REMINDER' || k === 'TASK') {
      const yearly = form.repeat === REPEAT.YEARLY
      const lunar = yearly && form.calendar === 'lunar'
      const payload = {
        title: form.title.trim(),
        content: form.content || '',
        // 农历每年的判定只用 lunarMonth/lunarDay,remind_date 只是展示锚点:
        // 编辑时沿用原锚点,别每次保存都把锚点改成今天
        remindDate: yearly ? (lunar ? form.initialDate || todayString() : solarAnchor) : form.date,
        remindTime: `${form.time}:00`,
        repeatType: k === 'REMINDER' ? 'ONCE' : form.repeat,
        calendar: yearly ? form.calendar : 'solar',
        lunarMonth: lunar ? form.month : null,
        lunarDay: lunar ? form.day : null,
        isLeap: lunar ? form.isLeap : 0,
      }
      if (form.id) await reminderApi.update(form.id, payload)
      else savedId = (await reminderApi.create(payload))?.id
    } else {
      const payload = {
        name: form.title.trim(),
        calendar: form.calendar,
        month: form.month,
        day: form.day,
        isLeap: form.calendar === 'lunar' ? form.isLeap : 0,
        memberIds: form.memberIds,
        recurring: 1,
        remindDaysBefore: form.remind === 'ADVANCE' ? form.advanceDays : 0,
        remindTime: form.remind === 'ADVANCE' ? form.time : null,
      }
      if (form.id) await anniversaryApi.update(form.id, payload)
      else savedId = (await anniversaryApi.create(payload))?.id
    }
    await saveAudience(k, savedId)
    ElMessage.success(t('schedule.dialog.saved'))
    visible.value = false
    emit('saved', { kind: k, id: form.id })
  } finally {
    saving.value = false
  }
}

/**
 * 保存分享范围:新建没勾圈就不发请求;编辑时按面板现状整体覆盖(勾选全取消=收回)。
 * 面板被判定为「不能分享」时不动已有分享,避免一次保存顺手清空。
 */
const saveAudience = async (k, savedId) => {
  if (!savedId || !audience.state.loaded || !audience.state.canShare) return
  if (!['ANNIVERSARY', 'REMINDER', 'TASK'].includes(k)) return
  if (!audience.state.selected.length && !isEdit.value) return
  const type = k === 'ANNIVERSARY' ? 'anniversary' : 'reminder'
  const data = await audience.save(savedId, audience.state.selected, type)
  if (data?.targetCount === 0 && audience.state.selected.length) ElMessage.warning(t('audience.noTarget'))
}
</script>

<style scoped>
.type-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}
.audience-block {
  margin: 4px 0 10px;
}
</style>
