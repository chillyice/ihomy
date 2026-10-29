<!-- 新建/编辑提醒弹窗:标题/内容/首次日期/触发时间/重复;计划托管的提醒不在此编辑 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? $t('reminder.editTitle') : $t('reminder.add')" width="440px">
    <el-form :model="form" label-position="top">
      <el-form-item :label="$t('reminder.headline')">
        <el-input v-model="form.title" :placeholder="$t('reminder.titlePlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('reminder.content')">
        <el-input v-model="form.content" type="textarea" :rows="2" :placeholder="$t('reminder.contentPlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('reminder.firstDate')">
        <el-date-picker v-model="form.remindDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="$t('reminder.remindTime')">
        <el-time-picker v-model="form.remindTime" value-format="HH:mm:ss" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="$t('reminder.repeat')">
        <el-radio-group v-model="form.repeatType">
          <el-radio value="ONCE">{{ $t('reminder.once') }}</el-radio>
          <el-radio value="DAILY">{{ $t('reminder.daily') }}</el-radio>
          <el-radio value="WEEKLY">{{ $t('reminder.weekly') }}</el-radio>
          <el-radio value="MONTHLY">{{ $t('reminder.monthly') }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 提醒表单:首次日期同时是每周/每月的基准(星期/日号)
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { reminderApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  reminder: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const saving = ref(false)
const blank = () => ({ id: null, title: '', content: '', remindDate: '', remindTime: '08:00:00', repeatType: 'ONCE' })
const form = reactive(blank())

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const r = props.reminder
    Object.assign(
      form,
      r
        ? {
            id: r.id,
            title: r.title,
            content: r.content || '',
            remindDate: r.remindDate || '',
            remindTime: r.remindTime || '08:00:00',
            repeatType: r.repeatType || 'ONCE',
          }
        : blank(),
    )
  },
)

const onSave = async () => {
  if (!form.title?.trim()) return ElMessage.warning(t('reminder.fillTitle'))
  saving.value = true
  try {
    const { id, ...data } = form
    if (id) await reminderApi.update(id, data)
    else await reminderApi.create(data)
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>
