<!-- 今日提醒页:家庭提醒事项,到点全家人收到站内通知;支持一次性/每日/每周/每月 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('reminder.title') }]" />

    <div class="list-header">
      <h2>{{ $t('reminder.title') }}</h2>
      <el-button type="primary" @click="openEditor()">{{ $t('reminder.add') }}</el-button>
    </div>

    <div v-loading="loading">
      <div v-if="list.length" class="reminder-list">
        <div v-for="r in list" :key="r.id" class="reminder-card card" :class="{ done: r.done === 1 }">
          <div class="reminder-main">
            <el-checkbox :model-value="r.done === 1" @change="onToggle(r)" />
            <div class="reminder-body">
              <div class="reminder-title">{{ r.title }}</div>
              <div v-if="r.content" class="reminder-content">{{ r.content }}</div>
              <div class="reminder-meta">
                {{ repeatText(r.repeatType) }} · {{ r.remindDate }} {{ (r.remindTime || '').slice(0, 5) }}
              </div>
            </div>
          </div>
          <div class="reminder-actions">
            <el-button size="small" text @click="openEditor(r)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" text type="danger" @click="onDel(r)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="$t('reminder.noData')" />
    </div>

    <el-dialog v-model="editor.visible" :title="editor.form.id ? $t('reminder.editTitle') : $t('reminder.add')" width="440px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('reminder.headline')">
          <el-input v-model="editor.form.title" :placeholder="$t('reminder.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('reminder.content')">
          <el-input v-model="editor.form.content" type="textarea" :rows="2" :placeholder="$t('reminder.contentPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('reminder.firstDate')">
          <el-date-picker v-model="editor.form.remindDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('reminder.remindTime')">
          <el-time-picker v-model="editor.form.remindTime" value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('reminder.repeat')">
          <el-radio-group v-model="editor.form.repeatType">
            <el-radio :value="0">{{ $t('reminder.once') }}</el-radio>
            <el-radio :value="1">{{ $t('reminder.daily') }}</el-radio>
            <el-radio :value="2">{{ $t('reminder.weekly') }}</el-radio>
            <el-radio :value="3">{{ $t('reminder.monthly') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 提醒事项:增删改+完成勾选;触发由后端定时任务推送站内通知
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reminderApi } from '@/api'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const editor = reactive({ visible: false, form: {} })

// 后端返回英文单词(ONCE/DAILY/WEEKLY/MONTHLY),表单仍提交数字由后端转换
const repeatKey = { ONCE: 'once', DAILY: 'daily', WEEKLY: 'weekly', MONTHLY: 'monthly' }
const repeatText = (x) => t('reminder.' + (repeatKey[x] || 'once'))
const repeatNum = (x) => ({ ONCE: 0, DAILY: 1, WEEKLY: 2, MONTHLY: 3 }[x] ?? 0)

const load = async () => {
  loading.value = true
  try {
    list.value = await reminderApi.list()
  } finally {
    loading.value = false
  }
}

const openEditor = (r) => {
  editor.visible = true
  editor.form = r
    ? { id: r.id, title: r.title, content: r.content || '', remindDate: r.remindDate, remindTime: r.remindTime, repeatType: repeatNum(r.repeatType) }
    : { id: null, title: '', content: '', remindDate: '', remindTime: '08:00:00', repeatType: 0 }
}

const onSave = async () => {
  if (!editor.form.title?.trim()) return ElMessage.warning(t('reminder.fillTitle'))
  saving.value = true
  try {
    const { id, ...data } = editor.form
    if (id) await reminderApi.update(id, data)
    else await reminderApi.create(data)
    ElMessage.success(t('common.saveSuccess'))
    editor.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

const onToggle = async (r) => {
  await reminderApi.toggleDone(r.id)
  await load()
}

const onDel = async (r) => {
  await ElMessageBox.confirm(t('reminder.deleteMessage', { title: r.title }), t('common.deleteConfirm'), { type: 'warning' })
  await reminderApi.remove(r.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 提醒卡片:复选框+内容+重复/时间元信息,完成状态灰显 */
.reminder-list {
  display: grid;
  gap: 12px;
}
.reminder-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
}
.reminder-card.done {
  opacity: 0.55;
}
.reminder-card.done .reminder-title {
  text-decoration: line-through;
}
.reminder-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}
.reminder-title {
  font-weight: 600;
}
.reminder-content {
  color: #666;
  font-size: 13px;
  margin-top: 4px;
}
.reminder-meta {
  color: #999;
  font-size: 12px;
  margin-top: 6px;
}
</style>