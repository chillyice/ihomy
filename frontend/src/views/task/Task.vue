<!-- 任务悬赏页:成员发布任务(奖励可空/积分/自定义物品),他人领取完成,发布者确认发奖 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('task.title') }]" />

    <div class="list-header">
      <el-button type="primary" @click="openEditor()">{{ $t('task.publish') }}</el-button>
    </div>

    <el-tabs v-model="filter">
      <el-tab-pane :label="$t('more.all')" name="all" />
      <el-tab-pane :label="$t('task.mine')" name="mine" />
      <el-tab-pane :label="$t('task.claimed')" name="claim" />
    </el-tabs>

    <div v-loading="loading">
      <div v-if="filtered.length" class="task-list">
        <div v-for="t in filtered" :key="t.id" class="task-card card">
          <div class="task-head">
            <span class="task-title">{{ t.title }}</span>
            <el-tag :type="tagType(t.status)" size="small">{{ statusText(t.status) }}</el-tag>
          </div>
          <div v-if="t.description" class="task-desc">{{ t.description }}</div>
          <div class="task-meta">
            <span>{{ $t('task.creator', { name: t.creatorName }) }}</span>
            <span v-if="t.assigneeName">{{ $t('task.assignee', { name: t.assigneeName }) }}</span>
            <span class="reward">{{ rewardText(t) }}</span>
            <span>{{ formatTime(t) }}</span>
          </div>
          <div v-if="act(t).length" class="task-actions">
            <el-button v-for="a in act(t)" :key="a.key" size="small" :type="a.type" plain @click="doAction(a.key, t)">
              {{ a.label }}
            </el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="$t('task.noData')" />
    </div>

    <!-- 发布任务弹窗 -->
    <el-dialog v-model="editor.visible" :title="$t('task.publish')" width="460px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('task.titleLabel')">
          <el-input v-model="editor.form.title" :placeholder="$t('task.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('task.desc')">
          <el-input v-model="editor.form.description" type="textarea" :rows="3" :placeholder="$t('task.descPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('task.reward')">
          <el-radio-group v-model="editor.form.rewardType">
            <el-radio :value="0">{{ $t('task.rewardNone') }}</el-radio>
            <el-radio :value="1">{{ $t('task.pointsLabel') }}</el-radio>
            <el-radio :value="2">{{ $t('task.itemDesc') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editor.form.rewardType === 1" :label="$t('task.pointsCount')">
          <el-input-number v-model="editor.form.rewardPoints" :min="1" />
        </el-form-item>
        <el-form-item v-if="editor.form.rewardType === 2" :label="$t('task.rewardItem')">
          <el-input v-model="editor.form.rewardItem" :placeholder="$t('task.itemPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onPublish">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 任务悬赏:状态机 待领取→进行中→待确认→已完成/已取消;当前用户相关操作按钮按身份渲染
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { taskApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const userStore = useUserStore()
const loading = ref(false)
const saving = ref(false)
const tasks = ref([])
const filter = ref('all')

const editor = reactive({ visible: false, form: {} })

const statusText = (s) => t(`task.status.${s}`)
const tagType = (s) => ({ OPEN: 'danger', IN_PROGRESS: 'warning', REVIEW: 'info', DONE: 'success', CANCELLED: 'info' })[s] || 'info'

const formatTime = (task) => new Date(task.createdAt).toLocaleString('zh-CN', { hour12: false }).slice(0, 16)

/** 过滤:全部 / 我发布的 / 我领取的 */
const filtered = computed(() => {
  const uid = userStore.userInfo?.id
  if (filter.value === 'mine') return tasks.value.filter((t) => t.createdBy === uid)
  if (filter.value === 'claim') return tasks.value.filter((t) => t.assigneeId === uid)
  return tasks.value
})

const rewardText = (task) => {
  if (task.rewardType === 'POINTS') return t('task.rewardPointsText', { points: task.rewardPoints })
  if (task.rewardType === 'ITEM') return t('task.rewardItemText', { item: task.rewardItem })
  return t('task.rewardNone')
}

/** 当前用户对任务可执行的操作(按身份与状态) */
const act = (task) => {
  const uid = userStore.userInfo?.id
  const actions = []
  if (task.status === 'OPEN' && task.createdBy !== uid) actions.push({ key: 'claim', label: t('task.claim'), type: 'primary' })
  if (task.status === 'IN_PROGRESS' && task.assigneeId === uid) {
    actions.push({ key: 'finish', label: t('task.finish'), type: 'primary' }, { key: 'abandon', label: t('task.abandon'), type: 'default' })
  }
  if (task.status === 'REVIEW' && task.createdBy === uid) actions.push({ key: 'confirm', label: t('task.confirm'), type: 'success' })
  if ((task.status === 'OPEN' || task.status === 'IN_PROGRESS') && task.createdBy === uid) actions.push({ key: 'cancel', label: t('task.cancel'), type: 'danger' })
  return actions
}

const doAction = async (key, task) => {
  const confirmMap = {
    finish: t('task.finishConfirm'),
    confirm: t('task.checkConfirm'),
    cancel: t('task.cancelConfirm'),
  }
  if (confirmMap[key]) await ElMessageBox.confirm(confirmMap[key], t('common.tip'), { type: 'warning' })
  await taskApi[key](task.id)
  ElMessage.success(t('common.success'))
  await loadTasks()
}

const openEditor = () => {
  editor.visible = true
  editor.form = { title: '', description: '', rewardType: 0, rewardPoints: 10, rewardItem: '' }
}

const onPublish = async () => {
  saving.value = true
  try {
    if (!editor.form.title?.trim()) {
      ElMessage.warning(t('task.fillTitle'))
      return
    }
    if (editor.form.rewardType === 1 && !editor.form.rewardPoints) {
      ElMessage.warning(t('task.fillPoints'))
      return
    }
    await taskApi.create(editor.form)
    ElMessage.success(t('task.published'))
    editor.visible = false
    await loadTasks()
  } finally {
    saving.value = false
  }
}

const loadTasks = async () => {
  loading.value = true
  try {
    tasks.value = await taskApi.list()
  } finally {
    loading.value = false
  }
}

onMounted(loadTasks)
</script>

<style scoped>
/* 任务卡片列表:标题+状态,描述/元信息/操作按钮 */
.task-list {
  display: grid;
  gap: 14px;
}
.task-card {
  padding: 16px 20px;
}
.task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.task-title {
  font-weight: 600;
  font-size: 16px;
}
.task-desc {
  color: #666;
  font-size: 13px;
  margin: 8px 0;
  white-space: pre-wrap;
}
.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: #999;
}
.task-meta .reward {
  color: #e6a23c;
  font-weight: 600;
}
.task-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
</style>