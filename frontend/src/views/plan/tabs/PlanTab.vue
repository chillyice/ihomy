<!-- 计划页签:中长期目标 + 子任务清单;子任务全完成自动完成计划,可填提醒时间(由后端托管一条提醒) -->
<template>
  <div v-loading="loading">
    <div v-if="plans.length" class="plan-list">
      <div
        v-for="p in plans"
        :key="p.id"
        :id="'plan-card-' + p.id"
        class="plan-card card"
        :class="{ done: p.status === 'DONE', focused: String(focus) === String(p.id) }"
      >
        <div class="plan-head">
          <div class="plan-title">
            {{ p.title }}
            <el-tag v-if="p.status === 'ACTIVE'" type="warning" size="small">{{ $t('plan.status.ACTIVE') }}</el-tag>
            <el-tag v-else-if="p.status === 'DONE'" type="success" size="small">{{ $t('plan.status.DONE') }}</el-tag>
            <el-tag v-else size="small">{{ $t('plan.status.CANCELLED') }}</el-tag>
          </div>
          <div class="plan-actions">
            <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
              <el-button size="small" text @click="openEditor(p)"
                ><el-icon><Edit /></el-icon
              ></el-button>
            </el-tooltip>
            <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
              <el-button size="small" text type="danger" @click="onDelPlan(p)"
                ><el-icon><Delete /></el-icon
              ></el-button>
            </el-tooltip>
          </div>
        </div>
        <div v-if="p.description" class="plan-desc">{{ p.description }}</div>
        <div class="plan-meta">
          <span>{{ p.targetDate ? $t('plan.target', { date: p.targetDate }) : $t('plan.targetNone') }}</span>
          <span v-if="p.remindTime">{{ $t('plan.today.remindTime') }} {{ timeOf(p.remindTime) }}</span>
          <span v-if="p.assigneeName">{{ $t('plan.member', { name: p.assigneeName }) }}</span>
        </div>
        <div v-if="p.totalCount" class="plan-progress">
          <el-progress :percentage="Math.round((p.doneCount / p.totalCount) * 100)" :stroke-width="8" />
          <span class="progress-text">{{ p.doneCount }}/{{ p.totalCount }}</span>
        </div>

        <!-- 子任务 -->
        <div class="task-sub-list">
          <div v-for="t in p.tasks" :key="t.id" class="task-sub card-inset">
            <el-checkbox :model-value="t.done === 1" @change="onToggleTask(t)" />
            <span class="task-sub-title" :class="{ done: t.done === 1 }">{{ t.title }}</span>
            <span v-if="t.dueDate" class="task-sub-due">{{ t.dueDate }}</span>
            <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
              <el-button size="small" text type="danger" @click="onDelTask(t)"
                ><el-icon><Delete /></el-icon
              ></el-button>
            </el-tooltip>
          </div>
          <div class="task-add-row">
            <el-input
              v-model="p._newTask"
              size="small"
              :placeholder="$t('plan.taskPlaceholder')"
              @keyup.enter="onAddTask(p)"
              style="max-width: 300px"
            />
            <el-button size="small" type="primary" plain @click="onAddTask(p)">{{ $t('plan.addTask') }}</el-button>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-else :description="userStore.isGuest ? $t('plan.guestEmpty') : $t('plan.noData')" />

    <PlanDialog v-model="editorVisible" :plan="editing" @saved="load" />
  </div>
</template>

<script setup>
// 计划 CRUD + 子任务 CRUD;新建/编辑走 PlanDialog(含提醒时间)
import { nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { planApi } from '@/api'
import { timeOf } from '@/utils/agenda'
import { useUserStore } from '@/stores/user'
import PlanDialog from '../dialogs/PlanDialog.vue'

const props = defineProps({ focus: { type: [String, Number], default: null } })
const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const plans = ref([])
const editorVisible = ref(false)
const editing = ref(null)

const load = async () => {
  // 家庭计划是登录后才有数据的私有内容:游客不打注定 401 的请求,直接空态
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    plans.value = await planApi.list()
  } finally {
    loading.value = false
  }
  await nextTick()
  scrollToFocus()
}

const scrollToFocus = () => {
  if (!props.focus) return
  document.getElementById('plan-card-' + props.focus)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

const openEditor = (p = null) => {
  editing.value = p
  editorVisible.value = true
}

const onDelPlan = async (p) => {
  await ElMessageBox.confirm(t('plan.deleteMessage', { name: p.title }), t('common.deleteConfirm'), {
    type: 'warning',
    closeOnClickModal: true,
  })
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

// 勾选子任务:更新完成态并整体刷新(后端已自动联动计划状态与关联提醒)
const onToggleTask = async (t) => {
  await planApi.updateTask(t.id, { done: t.done === 1 ? 0 : 1 })
  await load()
}

const onDelTask = async (t) => {
  await planApi.removeTask(t.id)
  await load()
}

watch(() => props.focus, scrollToFocus)
onMounted(load)
defineExpose({ openEditor })
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
.plan-card.focused {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
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
.plan-actions :deep(.el-button) {
  padding: 5px 6px;
}
.plan-actions :deep(.el-button + .el-button) {
  margin-left: 4px;
}
.plan-desc {
  color: var(--color-text-secondary);
  font-size: 13px;
  margin-top: 8px;
}
.plan-meta {
  color: var(--color-text-secondary);
  font-size: 12px;
  margin: 8px 0;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
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
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.task-sub-list {
  border-top: 1px dashed rgba(31, 58, 95, 0.12);
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
  color: var(--color-text-secondary);
}
.task-sub-due {
  color: var(--color-text-secondary);
  font-size: 12px;
  margin-left: auto;
}
.task-add-row {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .task-sub {
    flex-wrap: wrap;
  }
  .task-sub-due {
    width: 100%;
    text-align: right;
    margin-left: 0;
  }
  .task-add-row {
    flex-direction: column;
  }
  .task-add-row .el-button {
    width: 100%;
  }
}
</style>
