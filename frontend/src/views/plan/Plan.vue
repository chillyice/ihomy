<!-- 家庭计划页:中长期目标+子任务清单,勾选子任务后计划自动完成,进度条实时 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('plan.title') }]" />

    <div class="list-header">
      <el-button type="primary" @click="openPlanEditor()">{{ $t('plan.newPlan') }}</el-button>
    </div>

    <div v-loading="loading">
      <div v-if="plans.length" class="plan-list">
        <div v-for="p in plans" :key="p.id" class="plan-card card" :class="{ done: p.status === 'DONE' }">
          <div class="plan-head">
            <div class="plan-title">
              {{ p.title }}
              <el-tag v-if="p.status === 'ACTIVE'" type="warning" size="small">{{ $t('plan.status.ACTIVE') }}</el-tag>
              <el-tag v-else-if="p.status === 'DONE'" type="success" size="small">{{ $t('plan.status.DONE') }}</el-tag>
              <el-tag v-else size="small">{{ $t('plan.status.CANCELLED') }}</el-tag>
            </div>
            <div class="plan-actions">
              <el-button size="small" text @click="openPlanEditor(p)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" text type="danger" @click="onDelPlan(p)">{{ $t('common.delete') }}</el-button>
            </div>
          </div>
          <div v-if="p.description" class="plan-desc">{{ p.description }}</div>
          <div class="plan-meta">
            <span>{{ p.targetDate ? $t('plan.target', { date: p.targetDate }) : $t('plan.targetNone') }}</span>
            <span v-if="p.assigneeName">{{ $t('plan.member', { name: p.assigneeName }) }}</span>
          </div>
          <div v-if="p.totalCount" class="plan-progress">
            <el-progress :percentage="Math.round((p.doneCount / p.totalCount) * 100)" :stroke-width="8" />
            <span class="progress-text">{{ p.doneCount }}/{{ p.totalCount }}</span>
          </div>

          <!-- 子任务 -->
          <div class="task-sub-list">
            <div v-for="t in p.tasks" :key="t.id" class="task-sub card-inset">
              <el-checkbox :model-value="t.done === 1" @change="onToggleTask(p, t)" />
              <span class="task-sub-title" :class="{ done: t.done === 1 }">{{ t.title }}</span>
              <span v-if="t.dueDate" class="task-sub-due">{{ t.dueDate }}</span>
              <el-button size="small" text type="danger" @click="onDelTask(p, t)">{{ $t('common.delete') }}</el-button>
            </div>
            <div class="task-add-row">
              <el-input v-model="p._newTask" size="small" :placeholder="$t('plan.taskPlaceholder')" @keyup.enter="onAddTask(p)" style="max-width: 300px" />
              <el-button size="small" type="primary" plain @click="onAddTask(p)">{{ $t('plan.addTask') }}</el-button>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else :description="$t('plan.noData')" />
    </div>

    <el-dialog v-model="editor.visible" :title="editor.form.id ? $t('plan.editPlan') : $t('plan.newPlan')" width="440px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('plan.planTitle')">
          <el-input v-model="editor.form.title" :placeholder="$t('plan.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('plan.description')">
          <el-input v-model="editor.form.description" type="textarea" :rows="2" :placeholder="$t('plan.descPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('plan.targetDate')">
          <el-date-picker v-model="editor.form.targetDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePlan">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 家庭计划:计划 CRUD + 子任务 CRUD,勾选子任务自动联动计划完成状态
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { planApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()

const loading = ref(false)
const saving = ref(false)
const plans = ref([])
const editor = reactive({ visible: false, form: {} })

const load = async () => {
  loading.value = true
  try {
    plans.value = await planApi.list()
  } finally {
    loading.value = false
  }
}

const openPlanEditor = (p) => {
  editor.visible = true
  editor.form = p
    ? { id: p.id, title: p.title, description: p.description || '', targetDate: p.targetDate || '', status: p.status }
    : { id: null, title: '', description: '', targetDate: '', status: 0 }
}

const onSavePlan = async () => {
  if (!editor.form.title?.trim()) return ElMessage.warning(t('plan.fillTitle'))
  saving.value = true
  try {
    const { id, status, ...data } = editor.form
    if (id) await planApi.update(id, data)
    else await planApi.create(data)
    ElMessage.success(t('common.saveSuccess'))
    editor.visible = false
    await load()
  } finally {
    saving.value = false
  }
}

const onDelPlan = async (p) => {
  await ElMessageBox.confirm(t('plan.deleteMessage', { name: p.title }), t('common.deleteConfirm'), { type: 'warning' })
  await planApi.remove(p.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

const onAddTask = async (p) => {
  const title = p._newTask?.trim()
  if (!title) return
  await planApi.addTask(p.id, { title })
  p._newTask = ''
  await load()
}

// 勾选子任务:更新完成态并整体刷新(后端已自动联动计划状态)
const onToggleTask = async (p, t) => {
  await planApi.updateTask(t.id, { done: t.done === 1 ? 0 : 1 })
  await load()
}

const onDelTask = async (p, t) => {
  await planApi.removeTask(t.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 计划卡片:头部/描述/进度条/子任务清单 */
.plan-list {
  display: grid;
  gap: 16px;
}
.plan-card {
  padding: 18px 20px;
}
.plan-card.done {
  opacity: 0.7;
}
.plan-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.plan-title {
  font-weight: 600;
  font-size: 16px;
}
.plan-title .el-tag {
  margin-left: 8px;
}
.plan-desc {
  color: #666;
  font-size: 13px;
  margin-top: 8px;
}
.plan-meta {
  color: #999;
  font-size: 12px;
  margin: 8px 0;
  display: flex;
  gap: 16px;
}
.plan-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.plan-progress .el-progress {
  flex: 1;
}
.progress-text {
  color: #999;
  font-size: 12px;
  white-space: nowrap;
}
.task-sub-list {
  border-top: 1px dashed #eee;
  padding-top: 10px;
  display: grid;
  gap: 6px;
}
.task-sub {
  display: flex;
  align-items: center;
  gap: 10px;
}
.task-sub-title.done {
  text-decoration: line-through;
  color: #999;
}
.task-sub-due {
  color: #bbb;
  font-size: 12px;
  margin-left: auto;
}
.task-add-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>