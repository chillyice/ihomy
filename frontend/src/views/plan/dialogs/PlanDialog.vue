<!-- 新建/编辑计划弹窗:标题/描述/目标日期/提醒时间(填了时间会到点通知全家) -->
<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? $t('plan.editPlan') : $t('plan.newPlan')" width="440px">
    <el-form :model="form" label-position="top">
      <el-form-item :label="$t('plan.planTitle')">
        <el-input v-model="form.title" :placeholder="$t('plan.titlePlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('plan.description')">
        <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="$t('plan.descPlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('plan.targetDate')">
        <el-date-picker v-model="form.targetDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="$t('plan.today.remindTime')">
        <el-time-picker v-model="form.remindTime" value-format="HH:mm:ss" style="width: 100%" />
        <div class="field-hint">{{ $t('plan.today.noTime') }}</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 计划表单:提醒时间非空时后端会托管一条一次性提醒,需先有目标日期
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { planApi } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  plan: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])
const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})
const saving = ref(false)
const form = reactive({ id: null, title: '', description: '', targetDate: '', remindTime: '' })

const fill = (p) => {
  if (p) {
    Object.assign(form, {
      id: p.id,
      title: p.title || '',
      description: p.description || '',
      targetDate: p.targetDate || '',
      remindTime: p.remindTime || '',
    })
  } else {
    Object.assign(form, { id: null, title: '', description: '', targetDate: '', remindTime: '' })
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) fill(props.plan)
  },
)

const onSave = async () => {
  if (!form.title?.trim()) return ElMessage.warning(t('plan.fillTitle'))
  if (form.remindTime && !form.targetDate) return ElMessage.warning(t('plan.today.timeNeedsDate'))
  saving.value = true
  try {
    // 提醒时间统一传字符串:空串=清除关联提醒,后端未传(null)才表示不改
    const payload = {
      title: form.title,
      description: form.description,
      targetDate: form.targetDate || null,
      remindTime: form.remindTime || '',
    }
    if (form.id) await planApi.update(form.id, payload)
    else await planApi.create(payload)
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
