<template>
  <div class="card lc-card">
    <div class="lc-head">
      <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.timelineTitle') }}</div>
      <div v-if="canManage && !view.isGroup" class="lc-head-actions">
        <el-button size="small" @click="emit('add-rate')">{{ $t('tools.loan.rec.addRateChange') }}</el-button>
        <el-button size="small" @click="emit('add-prepay')">{{ $t('tools.loan.rec.addPrepay') }}</el-button>
      </div>
    </div>
    <div v-if="view.events.length" class="lr-timeline">
      <div
        v-a11y-click
        v-for="(ev, idx) in view.events"
        :key="ev.id || idx"
        class="lr-event"
        :class="'lr-event-' + ev.type.toLowerCase()"
        @click="emit('event-click', ev)"
      >
        <span class="lr-event-dot" />
        <div class="lr-event-body">
          <div class="lr-event-head">
            <span class="lr-event-title">
              {{
                ev.type === LOAN_EVENT.RATE_CHANGE
                  ? $t('tools.loan.rec.rateChangeLine', { v: rate(ev.rate) })
                  : $t('tools.loan.rec.prepayLine', { v: money(ev.amount) })
              }}
            </span>
            <el-tag v-if="view.isGroup" size="small" effect="plain" class="lr-tag">{{ ev.loanName }}</el-tag>
            <el-tag size="small" effect="plain" class="lr-tag">
              {{
                ev.type === LOAN_EVENT.PREPAY
                  ? $t('dict.loan_prepay_strategy.' + (ev.strategy || 'SHORTEN'))
                  : $t('dict.loan_event_type.RATE_CHANGE')
              }}
            </el-tag>
          </div>
          <div class="lr-event-sub">
            {{ $t('tools.loan.rec.timelineAtLine', { n: ev.effectivePeriod })
            }}<template v-if="periodMonth(view, ev.effectivePeriod)">{{
              $t('tools.loan.rec.timelineAtMonth', { m: periodMonth(view, ev.effectivePeriod) })
            }}</template>
            <span v-if="ev.note" class="lr-event-note">· {{ ev.note }}</span>
          </div>
        </div>
        <el-icon v-a11y-click v-if="canManage" class="lr-event-del" @click.stop="emit('event-delete', ev)"><Close /></el-icon>
      </div>
    </div>
    <p v-else class="lc-hint lc-hint-block">{{ $t('tools.loan.rec.timelineEmpty') }}</p>
  </div>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'
import { formatYuan, periodYearMonth, LOAN_EVENT } from '@/utils/loan'

defineProps({
  view: { type: Object, required: true },
  canManage: { type: Boolean, default: false },
})

const emit = defineEmits(['add-rate', 'add-prepay', 'event-click', 'event-delete'])

const money = (v, digits = 2) => formatYuan(v, digits)
const rate = (v) => (Number(v) || 0).toFixed(4)
const periodMonth = (view, period) => periodYearMonth(view && view.firstPayDate, period)
</script>

<style scoped>
.lc-card {
  padding: 18px 20px;
}
.lc-card .section-label {
  margin-bottom: 14px;
}
.lc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.lc-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.lc-label-flat {
  margin-bottom: 0 !important;
}
.lc-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-hint-block {
  margin: 0;
}

/* 时间轴 */
.lr-timeline {
  display: flex;
  flex-direction: column;
}
.lr-event {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 10px 0 10px 18px;
  border-left: 2px solid var(--color-border, rgba(58, 46, 34, 0.12));
  cursor: default;
}
.lr-event:last-child {
  border-left-color: transparent;
}
.lr-event-dot {
  position: absolute;
  left: -6px;
  top: 16px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-brand, #b88c6e);
}
.lr-event-prepay .lr-event-dot {
  background: var(--lr-orange);
}
.lr-event-rate_change .lr-event-dot {
  background: var(--lr-pink);
}
.lr-event-body {
  flex: 1;
  min-width: 0;
}
.lr-event-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.lr-event-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text, #3a2e22);
}
.lr-event-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b5a);
}
.lr-event-note {
  word-break: break-all;
}
.lr-event-del {
  cursor: pointer;
  color: var(--color-text-secondary, #7a6b5a);
  flex-shrink: 0;
}
.lr-event-del:hover {
  color: var(--color-accent, #a8483a);
}
</style>
