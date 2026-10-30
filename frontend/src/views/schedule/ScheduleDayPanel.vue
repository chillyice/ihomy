<!-- 当日详情:日程(时刻升序) + 节日节气 + 假期三段;游客只看登录引导,不发需登录请求。 -->
<template>
  <section id="schedule-day" class="day card">
    <header class="day-head">
      <div class="day-title">
        <span class="day-date">{{ titleText }}</span>
        <span v-if="lunarText" class="day-lunar">{{ lunarText }}</span>
        <span v-for="f in festivals" :key="f" class="day-fest">{{ f }}</span>
      </div>
      <el-button v-if="isLoggedIn" size="small" type="primary" plain @click="$emit('add', date)">
        {{ $t('schedule.addToDay') }}
      </el-button>
    </header>

    <div class="section-label">{{ $t('schedule.sectionSchedule') }}</div>
    <!-- 游客:纪念日与「访客可读」的既有口径保持一致照常展示(只读),其余日程换成登录引导 -->
    <div v-if="!isLoggedIn" class="guest-box">
      <span>{{ $t('schedule.guestLogin') }}</span>
      <el-button size="small" type="primary" plain @click="goLogin">{{ $t('schedule.loginAction') }}</el-button>
    </div>
    <div v-if="items.length" class="row-list">
      <ScheduleRow
        v-for="it in items"
        :key="it.kind + '-' + it.id"
        :item="it"
        :record="recordOf(it)"
        :can-edit="isLoggedIn"
        :show-type="false"
        @open="$emit('open', $event)"
        @toggle="$emit('toggle', $event)"
        @toggle-task="$emit('toggle-task', $event)"
        @edit="$emit('edit', $event)"
        @delete="$emit('delete', $event)"
      />
    </div>
    <div v-else-if="isLoggedIn" class="empty-hint">{{ $t('schedule.dayEmpty') }}</div>

    <template v-if="festivalLine">
      <div class="section-label">{{ $t('schedule.sectionFestival') }}</div>
      <div class="meta-line">{{ festivalLine }}</div>
    </template>

    <template v-if="holidayLine">
      <div class="section-label">{{ $t('schedule.sectionHoliday') }}</div>
      <div class="meta-line">{{ holidayLine }}</div>
    </template>
  </section>
</template>

<script setup>
// 三段式当日详情:日程在前(可勾完成/编辑删除),节日节气与假期只在有数据时出现
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

const festivals = computed(() => props.info?.festivals || [])

const festivalLine = computed(() => {
  const parts = [...(props.info?.festivals || [])]
  if (props.info?.term) parts.push(props.info.term)
  return parts.join(' · ')
})

const holidayLine = computed(() => {
  const h = props.info?.holiday
  if (!h) return ''
  const tag = h.type === 'HOLIDAY' ? t('schedule.holidayRest') : t('schedule.holidayWork')
  return h.name ? `${tag} · ${h.name}` : tag
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
.day-fest {
  font-size: 13px;
  color: var(--color-accent);
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
.meta-line {
  font-size: 13px;
  color: var(--color-text-secondary);
  padding: 0 0 6px;
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
</style>
