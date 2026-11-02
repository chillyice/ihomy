<!-- 当日详情:日程与节日/节气/休班合成一列,每行行首标明自身属性;游客只看登录引导,不发需登录请求。 -->
<template>
  <section id="schedule-day" class="day card">
    <header class="day-head">
      <div class="day-title">
        <span class="day-date">{{ titleText }}</span>
        <span v-if="lunarText" class="day-lunar">{{ lunarText }}</span>
      </div>
      <el-button v-if="isLoggedIn" size="small" type="primary" plain @click="$emit('add', date)">
        {{ $t('schedule.addToDay') }}
      </el-button>
    </header>

    <!-- 游客:纪念日与「访客可读」的既有口径保持一致照常展示(只读),其余日程换成登录引导 -->
    <div v-if="!isLoggedIn" class="guest-box">
      <span>{{ $t('schedule.guestLogin') }}</span>
      <el-button size="small" type="primary" plain @click="goLogin">{{ $t('schedule.loginAction') }}</el-button>
    </div>
    <div v-if="items.length || calendarRows.length" class="row-list">
      <ScheduleRow
        v-for="it in items"
        :key="it.kind + '-' + it.id"
        :item="it"
        :record="recordOf(it)"
        :can-edit="isLoggedIn"
        @open="$emit('open', $event)"
        @toggle="$emit('toggle', $event)"
        @toggle-task="$emit('toggle-task', $event)"
        @edit="$emit('edit', $event)"
        @delete="$emit('delete', $event)"
      />
      <!-- 节日/节气/休班不是日程,没有时刻与操作,只作当日背景信息跟在日程后面 -->
      <div v-for="row in calendarRows" :key="row.key" class="note-row">
        <span class="note-badge" :class="row.cls">{{ row.label }}</span>
        <span v-if="row.title" class="note-title">{{ row.title }}</span>
      </div>
    </div>
    <div v-else-if="isLoggedIn" class="empty-hint">{{ $t('schedule.dayEmpty') }}</div>
  </section>
</template>

<script setup>
// 单列当日详情:日程在前(可勾完成/编辑删除),节日/节气/休班跟在后面;
// 月份格与当日详情共用同一份 /public/calendar 响应,前端不再自己换算农历/节日。
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { weekdayKey } from '@/utils/scheduleCalendar'
import ScheduleRow from './ScheduleRow.vue'

const props = defineProps({
  date: { type: String, required: true },
  /** 当日事项(已排序) */
  items: { type: Array, default: () => [] },
  /** /public/calendar 的单日信息,补格日期为空 */
  info: { type: Object, default: null },
  recordOf: { type: Function, required: true },
})
defineEmits(['add', 'open', 'edit', 'delete', 'toggle', 'toggle-task'])

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const titleText = computed(() => {
  const [, month, day] = props.date.split('-').map(Number)
  const weekday = t('schedule.weekdayLong.' + weekdayKey(props.date))
  return t('schedule.dayTitle', { month, day, weekday })
})

const lunarText = computed(() => {
  if (!props.info) return ''
  return t('schedule.lunarPrefix') + props.info.lunarMonthText + props.info.lunarText
})

// 节日、节气、休班各占一行,行首属性由这里决定;节日与法定节假日同名(如春节)时只留一行,
// 休班(调休补班)不与节日重名、单独成行。
const calendarRows = computed(() => {
  const info = props.info
  if (!info) return []
  const rows = []
  const seen = new Set()
  const push = (key, label, title, cls) => {
    const name = title || ''
    if (name && seen.has(name)) return
    if (name) seen.add(name)
    rows.push({ key, label, title: name, cls })
  }
  ;(info.festivals || []).forEach((name, i) => push('festival-' + i, t('schedule.dayKind.festival'), name, 'festival'))
  push('term', t('schedule.dayKind.term'), info.term, 'festival')
  const h = info.holiday
  if (h) {
    const rest = h.type === 'HOLIDAY'
    push('holiday', t(rest ? 'schedule.dayKind.holiday' : 'schedule.dayKind.workday'), h.name, rest ? 'festival' : 'workday')
  }
  return rows
})

const goLogin = () => router.push('/login')
</script>

<style scoped>
.day {
  margin-top: 12px;
  padding: 16px 18px;
}
.day-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.day-title {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 4px 10px;
  min-width: 0;
}
.day-date {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
.day-lunar {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.row-list {
  display: grid;
  gap: 8px;
}
.empty-hint {
  color: var(--color-text-secondary);
  font-size: 13px;
  padding: 4px 2px 8px;
}
.guest-box {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 4px 0 8px;
}
/* 与 .sched-row 同款行:只读信息行,不能点、没有时刻与操作 */
.note-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 10px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  min-width: 0;
}
.note-badge {
  flex-shrink: 0;
  font-size: 11px;
  line-height: 18px;
  padding: 0 8px;
  border-radius: 9px;
  background: rgba(var(--color-brand-rgb), 0.16);
  color: var(--color-brand-hover);
  white-space: nowrap;
}
.note-badge.festival {
  background: rgba(230, 162, 60, 0.16);
  color: #b8860b;
}
.note-badge.workday {
  background: rgba(31, 58, 95, 0.08);
  color: var(--color-text-secondary);
}
.note-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
