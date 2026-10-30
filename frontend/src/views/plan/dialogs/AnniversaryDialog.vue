<!-- 新建/编辑纪念日弹窗:阳历/农历(闰月)、每年重复、关联多成员 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? t('anniversary.edit') : t('anniversary.add')" width="480px">
    <el-form :model="form" label-position="top">
      <el-form-item :label="t('anniversary.name')">
        <el-input v-model="form.name" :placeholder="t('anniversary.namePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('anniversary.calendar')">
        <el-radio-group v-model="form.calendar">
          <el-radio value="solar">{{ t('anniversary.solar') }}</el-radio>
          <el-radio value="lunar">{{ t('anniversary.lunar') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <div class="form-row">
        <el-form-item :label="t('anniversary.monthLabel')">
          <el-select v-model="form.month" style="width: 100%">
            <el-option v-for="m in 12" :key="m" :label="t('anniversary.monthOption', { n: m })" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('anniversary.dayLabel')">
          <el-select v-model="form.day" style="width: 100%">
            <el-option v-for="d in 31" :key="d" :label="t('anniversary.dayOption', { n: d })" :value="d" />
          </el-select>
        </el-form-item>
      </div>
      <el-form-item v-if="form.calendar === 'lunar'" :label="t('anniversary.leapMonth')">
        <el-switch v-model="form.isLeap" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item :label="t('anniversary.memberLabel')">
        <el-select
          v-model="form.memberIds"
          multiple
          collapse-tags
          collapse-tags-tooltip
          clearable
          :placeholder="t('anniversary.memberPlaceholder')"
          style="width: 100%"
        >
          <el-option v-for="m in members" :key="m.id" :label="m.nickname || m.username" :value="m.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('anniversary.recurring')">
        <el-switch v-model="form.recurring" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item :label="t('anniversary.remindLabel')">
        <el-switch v-model="form.remindEnabled" />
      </el-form-item>
      <div v-if="form.remindEnabled" class="form-row">
        <el-form-item :label="t('anniversary.remindDaysLabel')">
          <el-select v-model="form.remindDaysBefore" style="width: 100%">
            <el-option v-for="d in 30" :key="d" :label="t('anniversary.remindDaysOption', { n: d })" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('anniversary.remindTimeLabel')">
          <el-time-select v-model="form.remindTime" start="06:00" step="00:15" end="23:00" style="width: 100%" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="onSave">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 纪念日表单:memberIds 可不选(家庭级纪念日)
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { anniversaryApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  item: { type: Object, default: null },
  members: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const blank = () => ({
  id: null,
  name: '',
  calendar: 'solar',
  month: 1,
  day: 1,
  isLeap: 0,
  memberIds: [],
  recurring: 1,
  remindEnabled: false,
  remindDaysBefore: 3,
  remindTime: '08:00',
})

const form = reactive(blank())

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const a = props.item
    Object.assign(
      form,
      a
        ? {
            id: a.id,
            name: a.name,
            calendar: a.calendar,
            month: a.month,
            day: a.day,
            isLeap: a.isLeap,
            memberIds: Array.isArray(a.memberIds) ? [...a.memberIds] : [],
            recurring: a.recurring === 'ONCE' ? 0 : 1,
            remindEnabled: (a.remindDaysBefore || 0) > 0,
            remindDaysBefore: (a.remindDaysBefore || 0) > 0 ? a.remindDaysBefore : 3,
            // 后端时刻带秒(LocalTime),时间选择器只认 HH:mm
            remindTime: a.remindTime ? String(a.remindTime).slice(0, 5) : '08:00',
          }
        : blank(),
    )
  },
)

const onSave = async () => {
  if (!form.name) return ElMessage.warning(t('anniversary.nameRequired'))
  // 关闭提醒时两列提交空值,后端据此关闭调度
  const payload = {
    name: form.name,
    calendar: form.calendar,
    month: form.month,
    day: form.day,
    isLeap: form.isLeap,
    memberIds: form.memberIds,
    recurring: form.recurring,
    remindDaysBefore: form.remindEnabled ? form.remindDaysBefore : 0,
    remindTime: form.remindEnabled ? form.remindTime : null,
  }
  if (form.id) await anniversaryApi.update(form.id, payload)
  else await anniversaryApi.create(payload)
  ElMessage.success(t('anniversary.saved'))
  visible.value = false
  emit('saved')
}
</script>

<style scoped>
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
</style>
