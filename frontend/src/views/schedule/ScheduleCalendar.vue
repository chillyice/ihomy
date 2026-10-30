<!-- 月历网格(自研 CSS Grid,不用 el-calendar):日期 + 农历文案 + 语义圆点 + 休/班角标。
     农历/节气/节日/休班全部来自后端 /public/calendar,补格日期没有基础数据时只显示日号。 -->
<template>
  <section class="cal card">
    <header class="cal-head">
      <div class="cal-nav">
        <button type="button" class="cal-arrow" :aria-label="$t('schedule.prevMonth')" @click="$emit('change-month', -1)">
          <el-icon><ArrowLeft /></el-icon>
        </button>
        <span class="cal-title">{{ $t('schedule.monthTitle', { year, month }) }}</span>
        <button type="button" class="cal-arrow" :aria-label="$t('schedule.nextMonth')" @click="$emit('change-month', 1)">
          <el-icon><ArrowRight /></el-icon>
        </button>
      </div>
      <el-button size="small" plain @click="pickToday">{{ $t('schedule.today') }}</el-button>
    </header>

    <div class="cal-week">
      <span v-for="k in WEEKDAY_KEYS" :key="k">{{ $t('schedule.weekday.' + k) }}</span>
    </div>

    <div class="cal-grid">
      <button
        v-for="cell in cells"
        :key="cell.date"
        type="button"
        class="cal-cell"
        :class="{ 'out-month': !cell.inMonth, selected: cell.selected, 'is-today': cell.isToday }"
        @click="$emit('update:selected', cell.date)"
      >
        <span class="cell-day">{{ cell.day }}</span>
        <span v-if="cell.lunar" class="cell-lunar">{{ cell.lunar }}</span>
        <span v-if="cell.holiday" class="cell-holiday" :class="cell.holiday.type.toLowerCase()">{{
          cell.holiday.type === 'HOLIDAY' ? $t('schedule.holidayRest') : $t('schedule.holidayWork')
        }}</span>
        <span v-if="cell.dots.length" class="cell-dots">
          <i class="dot dot-a" :class="cell.dots[0]" />
          <i v-if="cell.dots.length > 1" class="dot dot-b" :class="cell.dots[1]" />
        </span>
      </button>
    </div>
  </section>
</template>

<script setup>
// 纯展示组件:网格由 utils/scheduleCalendar.buildMonthGrid 生成,数据由外壳并行拉取
import { computed } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { WEEKDAY_KEYS, todayString } from '@/utils/scheduleCalendar'

const props = defineProps({
  /** 42 格 [{date, day, inMonth, weekday}] */
  grid: { type: Array, required: true },
  /** 日期 → /public/calendar 的单日信息 */
  calendarDays: { type: Object, default: () => ({}) },
  /** 日期 → /schedule/events 的事项数组 */
  events: { type: Object, default: () => ({}) },
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  selected: { type: String, default: '' },
})
const emit = defineEmits(['update:selected', 'change-month'])

// 初一显示农历月名(闰月带「闰」前缀),其余显示 初二…三十
const lunarOf = (info) => {
  if (!info) return ''
  return info.lunarDay === 1 ? info.lunarMonthText : info.lunarText
}

const cells = computed(() => {
  const today = todayString()
  return props.grid.map((cell) => {
    const info = props.calendarDays[cell.date]
    const hasSchedule = (props.events[cell.date] || []).length > 0
    const hasFestival = !!(info && (info.festivals?.length || info.term))
    const dots = []
    if (hasSchedule) dots.push('schedule')
    if (hasFestival) dots.push('festival')
    return {
      ...cell,
      lunar: lunarOf(info),
      dots,
      holiday: info?.holiday || null,
      selected: cell.date === props.selected,
      isToday: cell.date === today,
    }
  })
})

const pickToday = () => emit('update:selected', todayString())
</script>

<style scoped>
.cal {
  padding: 16px 18px;
}
.cal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.cal-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}
.cal-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 110px;
  text-align: center;
}
.cal-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: background 0.2s ease;
}
.cal-arrow:hover {
  background: rgba(var(--color-brand-rgb), 0.12);
}
.cal-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  margin-bottom: 6px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
}
.cal-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-height: 62px;
  padding: 6px 2px 4px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;
}
.cal-cell:hover {
  background: rgba(var(--color-brand-rgb), 0.1);
}
.cal-cell.out-month {
  opacity: 0.4;
}
.cal-cell.is-today {
  border-color: var(--color-brand);
}
.cal-cell.selected {
  background: rgba(var(--color-brand-rgb), 0.2);
  border-color: var(--color-brand-hover);
}
.cell-day {
  font-size: 15px;
  font-weight: 600;
  line-height: 20px;
  font-variant-numeric: tabular-nums;
}
.cell-lunar {
  font-size: 11px;
  line-height: 14px;
  color: var(--color-text-secondary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cell-holiday {
  position: absolute;
  top: 3px;
  right: 3px;
  font-size: 10px;
  line-height: 14px;
  padding: 0 3px;
  border-radius: 4px;
}
.cell-holiday.holiday {
  background: rgba(168, 72, 58, 0.14);
  color: var(--color-accent);
}
.cell-holiday.workday {
  background: rgba(31, 58, 95, 0.12);
  color: var(--color-text-secondary);
}
.cell-dots {
  display: flex;
  gap: 3px;
  height: 6px;
  align-items: center;
}
.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  display: inline-block;
}
.dot.schedule {
  background: var(--color-brand);
}
.dot.festival {
  background: var(--color-accent);
}

/* 窄屏:隐藏农历文案与第二个圆点(语义不变,只减信息密度) */
@media (max-width: 480px) {
  .cal {
    padding: 12px 10px;
  }
  .cal-cell {
    min-height: 48px;
    padding: 4px 1px 2px;
  }
  .cell-day {
    font-size: 14px;
  }
  .cell-lunar,
  .dot-b {
    display: none;
  }
  .cal-title {
    min-width: 0;
    font-size: 15px;
  }
}
</style>
