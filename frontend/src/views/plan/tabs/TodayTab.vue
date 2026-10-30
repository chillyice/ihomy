<!-- 今日页签:今天的计划 + 今天的提醒(可勾完成)+ 全部提醒(维护重复提醒);游客只看登录邀请 -->
<template>
  <div v-loading="loading">
    <el-empty v-if="!userStore.isLoggedIn" :description="$t('plan.today.guestEmpty')" />

    <template v-else>
      <div v-if="events.length" class="event-strip card" v-a11y-click @click="openAnniversary">
        <el-icon class="ev-icon"><Calendar /></el-icon>
        <span v-for="(e, i) in events" :key="'e' + i" class="ev-item">{{ eventText(e) }}</span>
      </div>

      <div class="section-label">{{ $t('plan.today.planSection') }}</div>
      <div v-if="plans.length" class="today-list">
        <div v-for="p in plans" :key="'p' + p.id" class="today-row card" v-a11y-click @click="openPlan(p.id)">
          <span class="t-title">{{ p.title }}</span>
          <el-tag v-if="p.overdue" type="danger" size="small">
            {{ $t('plan.today.overdue', { n: p.overdueDays }) }}
          </el-tag>
          <span class="t-meta">{{ p.targetDate }}</span>
          <span v-if="p.remindTime" class="t-time">{{ timeOf(p.remindTime) }}</span>
          <span v-if="p.totalCount" class="t-progress">{{ p.doneCount }}/{{ p.totalCount }}</span>
        </div>
      </div>
      <div v-else class="empty-hint">{{ $t('plan.today.empty') }}</div>

      <div class="section-label">{{ $t('plan.today.reminderSection') }}</div>
      <div v-if="reminders.length" class="today-list">
        <div v-for="r in reminders" :key="'r' + r.id" class="today-row card">
          <el-checkbox :model-value="false" @change="onToggle(r)" />
          <span class="t-title">{{ r.title }}</span>
          <el-tag v-if="r.fromPlan" size="small" effect="plain">{{ $t('plan.today.fromPlan') }}</el-tag>
          <span class="t-meta">{{ repeatText(r.repeatType) }}</span>
          <span class="t-time">{{ timeOf(r.remindTime) }}</span>
        </div>
      </div>
      <div v-else class="empty-hint">{{ $t('plan.today.noReminder') }}</div>

      <div class="section-head">
        <div class="section-label">{{ $t('plan.today.allSection') }}</div>
        <el-button size="small" plain @click="openEditor(null)">{{ $t('reminder.add') }}</el-button>
      </div>
      <div class="section-hint">{{ $t('plan.today.allHint') }}</div>
      <div v-if="allReminders.length" class="today-list">
        <div v-for="r in allReminders" :key="'a' + r.id" class="today-row card" :class="{ done: r.done === 1 }">
          <el-checkbox :model-value="r.done === 1" @change="onToggle(r)" />
          <span class="t-title">{{ r.title }}</span>
          <span class="t-meta">{{ repeatText(r.repeatType) }} · {{ r.remindDate }} {{ timeOf(r.remindTime) }}</span>
          <span class="t-actions">
            <template v-if="!r.planId">
              <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
                <el-button size="small" text @click="openEditor(r)"
                  ><el-icon><Edit /></el-icon
                ></el-button>
              </el-tooltip>
              <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                <el-button size="small" text type="danger" @click="onDel(r)"
                  ><el-icon><Delete /></el-icon
                ></el-button>
              </el-tooltip>
            </template>
            <el-tag v-else size="small" effect="plain">{{ $t('plan.today.fromPlan') }}</el-tag>
          </span>
        </div>
      </div>
      <div v-else class="empty-hint">{{ $t('reminder.noData') }}</div>
    </template>

    <ReminderDialog v-model="editorVisible" :reminder="editing" @saved="load" />
  </div>
</template>

<script setup>
// 今日视图:GET /plan/today(今天/逾期的计划 + 今天应触发的提醒)+ 全部提醒维护
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Edit, Delete, Calendar } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { planApi, reminderApi } from '@/api'
import { timeOf } from '@/utils/agenda'
import { useUserStore } from '@/stores/user'
import ReminderDialog from '../dialogs/ReminderDialog.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const plans = ref([])
const reminders = ref([])
const events = ref([])
const allReminders = ref([])
const editorVisible = ref(false)
const editing = ref(null)

// 后端返回英文枚举,表单用同一批词;未知值按一次性展示
const repeatKey = { ONCE: 'once', DAILY: 'daily', WEEKLY: 'weekly', MONTHLY: 'monthly' }
const repeatText = (x) => t('reminder.' + (repeatKey[x] || 'once'))

// 重要日期提示行:当天显示「今天」,其余显示剩余天数;不生成待办
const eventText = (e) =>
  e.days === 0 ? t('plan.today.eventToday', { name: e.label }) : t('plan.today.eventInDays', { name: e.label, n: e.days })

const load = async () => {
  // 今日视图与提醒都是私有内容:游客不打注定 401 的请求
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    const [today, all] = await Promise.all([planApi.today(), reminderApi.list().catch(() => [])])
    plans.value = Array.isArray(today?.plans) ? today.plans : []
    reminders.value = Array.isArray(today?.reminders) ? today.reminders : []
    events.value = Array.isArray(today?.events) ? today.events : []
    allReminders.value = Array.isArray(all) ? all : []
  } finally {
    loading.value = false
  }
}

const openPlan = (id) => router.push({ path: '/plan', query: { tab: 'plan', focus: id } })
const openAnniversary = () => router.push({ path: '/plan', query: { tab: 'anniversary' } })

const openEditor = (r) => {
  editing.value = r
  editorVisible.value = true
}

const onToggle = async (r) => {
  await reminderApi.toggleDone(r.id)
  await load()
}

const onDel = async (r) => {
  await ElMessageBox.confirm(t('reminder.deleteMessage', { title: r.title }), t('common.deleteConfirm'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  await reminderApi.remove(r.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped>
/* 今日清单:一行一条,标题吃掉剩余宽度,时间/进度靠右 */
.today-list {
  display: grid;
  gap: 8px;
  margin-bottom: 8px;
}
.today-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: default;
}
.today-row[role='button'] {
  cursor: pointer;
}
.today-row.done {
  opacity: 0.55;
}
.today-row.done .t-title {
  text-decoration: line-through;
}
.t-title {
  font-weight: 600;
  font-size: 14px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.t-meta {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.t-time {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
  color: var(--color-primary);
  font-size: 13px;
}
.t-progress {
  color: var(--color-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.t-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
}
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.section-hint {
  color: var(--color-text-secondary);
  font-size: 12px;
  margin: -4px 0 10px;
}
.empty-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
  padding: 8px 2px 12px;
}
/* 重要日期提示条:一行放不下就换行,整体可点进纪念日页签 */
.event-strip {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 14px;
  padding: 10px 14px;
  margin-bottom: 14px;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.event-strip .ev-icon {
  color: var(--color-primary);
}
.event-strip .ev-item {
  color: var(--color-text);
}

@media (max-width: 768px) {
  .today-row {
    flex-wrap: wrap;
  }
  .t-meta {
    width: 100%;
    padding-left: 26px;
  }
}
</style>
