<!-- 日程详情:纪念日看历法/成员/提前提醒并可许愿;计划看说明/进度/子任务(可增删勾选)。 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="$t('schedule.detail.title')" width="460px">
    <div v-if="!record" class="empty-hint">{{ $t('schedule.detail.noData') }}</div>

    <template v-else>
      <div class="detail-head">
        <el-tag size="small">{{ $t('schedule.type.' + typeName) }}</el-tag>
        <span class="detail-title">{{ title }}</span>
      </div>

      <!-- 亲友圈分享来的:标明来源,只能查看与送祝福,不能改删 -->
      <p v-if="sharedFromText" class="shared-tip">{{ sharedFromText }}</p>

      <template v-if="kind === 'ANNIVERSARY'">
        <div class="detail-line">
          <span class="k">{{ $t('schedule.detail.dateLabel') }}</span>
          <span class="v">{{ anniversaryDate }}</span>
        </div>
        <div v-if="memberText" class="detail-line">
          <span class="k">{{ $t('schedule.detail.members') }}</span>
          <span class="v">{{ memberText }}</span>
        </div>
        <div v-if="record.remindDaysBefore > 0" class="detail-line">
          <span class="k">{{ $t('schedule.detail.remindAdvance', { n: record.remindDaysBefore }) }}</span>
          <span class="v">{{ record.remindTime ? String(record.remindTime).slice(0, 5) : '' }}</span>
        </div>
        <div v-if="isLoggedIn" class="detail-actions">
          <!-- 圈内轻互动:送祝福只写 content_like,不动对方的纪念日数据 -->
          <el-button size="small" :type="blessed ? 'primary' : 'default'" plain :loading="blessLoading" @click="toggleBless">
            {{ $t(blessed ? 'schedule.detail.blessed' : 'schedule.detail.bless') }}
          </el-button>
          <span v-if="blessCount > 0" class="bless-count">{{ $t('schedule.detail.blessCount', { n: blessCount }) }}</span>
          <el-button v-if="!readOnly" size="small" plain @click="wishOpen = true">{{ $t('schedule.detail.addWish') }}</el-button>
        </div>
      </template>

      <template v-else>
        <div class="detail-line">
          <span class="k">{{ $t('schedule.detail.dateLabel') }}</span>
          <span class="v">{{ detailDate || $t('schedule.undated') }}</span>
        </div>
        <div v-if="kind === 'REMINDER' && record.remindTime" class="detail-line">
          <span class="k">{{ $t('schedule.detail.timeLabel') }}</span>
          <span class="v">{{ String(record.remindTime).slice(0, 5) }}</span>
        </div>
        <div v-if="detailDescription" class="detail-line">
          <span class="k">{{ $t('schedule.detail.description') }}</span>
          <span class="v">{{ detailDescription }}</span>
        </div>

        <template v-if="kind === 'PLAN'">
          <div class="detail-line">
            <span class="k">{{ $t('schedule.detail.progress', { done: record.doneCount || 0, total: record.totalCount || 0 }) }}</span>
          </div>

          <div class="section-label">{{ $t('schedule.detail.subtasks') }}</div>
          <div v-if="tasks.length" class="task-list">
            <div v-for="task in tasks" :key="task.id" class="task-row">
              <el-checkbox :model-value="task.done === 1" @change="toggleTask(task)" />
              <span class="task-title" :class="{ done: task.done === 1 }">{{ task.title }}</span>
              <span v-if="task.dueDate" class="task-due">{{ task.dueDate }}</span>
              <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                <el-button size="small" text type="danger" @click="removeTask(task)"
                  ><el-icon><Delete /></el-icon
                ></el-button>
              </el-tooltip>
            </div>
          </div>
          <div v-else class="empty-hint">{{ $t('schedule.detail.noSubtasks') }}</div>
          <div class="task-add">
            <el-input
              v-model="newTask"
              size="small"
              maxlength="100"
              :placeholder="$t('schedule.detail.addTaskPlaceholder')"
              @keyup.enter="addTask"
            />
            <el-button size="small" type="primary" plain :loading="taskSaving" @click="addTask">{{
              $t('schedule.detail.addTask')
            }}</el-button>
          </div>
        </template>
      </template>
    </template>

    <template #footer>
      <el-button v-if="record && isLoggedIn && !readOnly" type="danger" plain @click="$emit('delete', { kind, record })">{{
        $t('schedule.detail.delete')
      }}</el-button>
      <el-button v-if="record && isLoggedIn && !readOnly" type="primary" @click="$emit('edit', { kind, record })">{{
        $t('schedule.detail.edit')
      }}</el-button>
      <el-button @click="visible = false">{{ $t('schedule.detail.close') }}</el-button>
    </template>

    <AddToWishDialog
      v-if="isLoggedIn && !readOnly && kind === 'ANNIVERSARY' && record"
      v-model="wishOpen"
      :source="{ type: 'ANNIVERSARY', id: record.id, title: record.name }"
      :title-placeholder="$t('wish.anniversaryTitlePlaceholder', { name: record.name })"
    />
  </el-dialog>
</template>

<script setup>
// 只读详情 + 计划子任务维护(子任务接口沿用 planApi,写侧不出现第二入口)
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { likeApi, planApi } from '@/api'
import { classify } from '@/utils/scheduleType'
import { useUserStore } from '@/stores/user'
import AddToWishDialog from '@/components/wish/AddToWishDialog.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 'ANNIVERSARY' | 'PLAN' */
  kind: { type: String, required: true },
  /** 对应表的完整记录,已不存在时为 null */
  record: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'edit', 'delete', 'saved'])
const { t } = useI18n()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// 游客可读纪念日,但不给许愿/编辑/删除(与旧纪念日页签口径一致)
const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const title = computed(() => (props.kind === 'ANNIVERSARY' ? props.record?.name : props.record?.title) || '')
// 徽标按用户叫法:有目标日 → 事项,没有 → 计划(与弹窗、清单共用 classify);提醒用自身类型
const typeName = computed(() => {
  if (props.kind === 'ANNIVERSARY') return 'ANNIVERSARY'
  if (props.kind === 'REMINDER') return 'REMINDER'
  return classify({ hasDate: !!props.record?.targetDate })
})
const tasks = computed(() => (Array.isArray(props.record?.tasks) ? props.record.tasks : []))
// 亲友圈分享来的条目标 readOnly:只能查看与送祝福(写侧永远在所属家庭)
const readOnly = computed(() => !!props.record?.readOnly)
const sharedFromText = computed(() => {
  const s = props.record?.sharedFrom
  if (!s) return ''
  return t('audience.sharedFrom', { name: s.alias || s.familyName || '' })
})
const anniversaryDate = computed(() => {
  const a = props.record
  if (!a) return ''
  const calendarText = a.calendar === 'lunar' ? t('anniversary.lunar') : t('anniversary.solar')
  const date = t('anniversary.dateFormat', {
    leap: a.isLeap && a.calendar === 'lunar' ? t('anniversary.leap') : '',
    month: a.month,
    day: a.day,
  })
  return `${calendarText} · ${date}`
})
const memberText = computed(() => {
  const names = props.record?.memberNames
  if (Array.isArray(names) && names.length) return names.join('、')
  // 别人家的纪念日不显示「家庭纪念日」占位(成员名单不跨家庭)
  return readOnly.value ? '' : t('schedule.detail.familyAnniversary')
})
const detailDate = computed(() => props.record?.targetDate || props.record?.remindDate || '')
const detailDescription = computed(() => props.record?.description || props.record?.content || '')

// ---- 送祝福:复用统一点赞(contentType=anniversary),只写 content_like,不动对方的纪念日 ----
const blessed = ref(false)
const blessCount = ref(0)
const blessLoading = ref(false)

const loadBless = async () => {
  blessed.value = false
  blessCount.value = 0
  if (!isLoggedIn.value || props.kind !== 'ANNIVERSARY' || !props.record?.id) return
  try {
    const data = await likeApi.state('anniversary', props.record.id)
    blessed.value = !!data?.liked
    blessCount.value = Number(data?.likeCount) || 0
  } catch {
    // 状态取不到不影响查看详情,按钮仍可点(点后再校验)
  }
}

const toggleBless = async () => {
  if (!props.record?.id) return
  blessLoading.value = true
  try {
    const data = await likeApi.toggle({ contentType: 'anniversary', contentId: props.record.id })
    blessed.value = !!data?.liked
    blessCount.value = Number(data?.likeCount) || 0
  } finally {
    blessLoading.value = false
  }
}

watch(
  () => [props.modelValue, props.kind, props.record?.id],
  ([open]) => {
    if (open) loadBless()
  },
  { immediate: true },
)

const newTask = ref('')
const taskSaving = ref(false)
const wishOpen = ref(false)

const addTask = async () => {
  const title = newTask.value?.trim()
  if (!title || !props.record?.id) return
  taskSaving.value = true
  try {
    await planApi.addTask(props.record.id, { title })
    newTask.value = ''
    emit('saved')
  } finally {
    taskSaving.value = false
  }
}

const toggleTask = async (task) => {
  await planApi.updateTask(task.id, { done: task.done === 1 ? 0 : 1 })
  emit('saved')
}

const removeTask = async (task) => {
  await planApi.removeTask(task.id)
  ElMessage.success(t('common.deleted'))
  emit('saved')
}
</script>

<style scoped>
.detail-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  min-width: 0;
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.detail-line {
  display: flex;
  gap: 10px;
  font-size: 13px;
  margin-bottom: 8px;
}
.detail-line .k {
  flex-shrink: 0;
  color: var(--color-text-secondary);
}
.detail-line .v {
  color: var(--color-text);
  word-break: break-word;
}
.detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 4px;
}
.bless-count {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.shared-tip {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.task-list {
  display: grid;
  gap: 4px;
  margin-bottom: 8px;
}
.task-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.task-title {
  font-size: 13px;
  color: var(--color-text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-title.done {
  text-decoration: line-through;
  color: var(--color-text-secondary);
}
.task-due {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.task-add {
  display: flex;
  gap: 8px;
}
.empty-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
  padding: 4px 0 8px;
}
</style>
