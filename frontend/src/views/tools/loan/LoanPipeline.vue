<template>
  <div class="card lc-card lc-block">
    <div class="lc-head">
      <div class="section-label lc-label-flat">
        {{ $t('tools.loan.rec.pipeTitle') }}
        <span class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.pipeHint') }}</span>
      </div>
      <div v-if="canManage && !view.isGroup" class="lr-drag-palette">
        <span class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.dragHint') }}</span>
        <div
          class="lr-chip lr-chip-rate"
          draggable="true"
          @dragstart="onChipDragStart($event, LOAN_EVENT.RATE_CHANGE)"
          @dragend="onDragEnd"
        >
          {{ $t('tools.loan.rec.addRateChange') }}
        </div>
        <div class="lr-chip lr-chip-prepay" draggable="true" @dragstart="onChipDragStart($event, LOAN_EVENT.PREPAY)" @dragend="onDragEnd">
          {{ $t('tools.loan.rec.addPrepay') }}
        </div>
      </div>
    </div>
    <div class="lr-pipe" @dragover.prevent="onDragOver($event)" @drop.prevent="onDrop($event)" @dragleave="onDragLeave">
      <!-- 落点提示条:绝对定位(不挤动节点,避免夹缝处来回跳)+ 常驻 DOM(拖拽中不增删节点) -->
      <div
        class="lr-dropmark"
        :class="[dragType === LOAN_EVENT.RATE_CHANGE ? 'lr-drop-rate' : 'lr-drop-prepay', { on: dropOn }]"
        :style="{ top: dropTop + 'px' }"
      >
        {{ dropLabel }}
      </div>
      <template v-for="node in pipeNodes" :key="node.key">
        <div v-if="node.newYear" class="lr-yline">
          <span class="lr-ylabel">{{ node.ym.slice(0, 4) }}</span>
        </div>
        <el-tooltip :disabled="!!dragType" placement="right" :show-after="120" popper-class="lr-tip-popper">
          <!-- 事件节点(已生效贴在历史段;未生效的以虚线幽灵节点浮在顶部)与期次节点共用一颗节点,靠类名区分 -->
          <div
            class="lr-pnode"
            :class="nodeClass(node)"
            :data-key="node.key"
            :data-period="node.period"
            :draggable="canDragNode(node)"
            @dragstart="onNodeDragStart($event, node)"
            @dragend="onDragEnd"
          >
            <span v-if="node.event" class="lr-shape" />
            <span v-else class="lr-dot" />
            <span class="lr-pnode-text">
              <template v-if="node.event">
                <b>{{ $t('tools.loan.rec.evAfterLine', { n: node.period }) }}</b>
                {{
                  node.event.type === LOAN_EVENT.RATE_CHANGE
                    ? $t('tools.loan.rec.rateChangeLine', { v: rate(node.event.rate) })
                    : $t('tools.loan.rec.prepayLine', { v: money(node.event.amount) })
                }}
                <el-tag v-if="view.isGroup" size="small" effect="plain" class="lr-tag">{{ node.event.loanName }}</el-tag>
                <el-tag v-if="node.kind === 'future'" size="small" effect="plain" class="lr-tag lr-tag-sage">{{
                  $t('tools.loan.rec.futureTag')
                }}</el-tag>
                <span v-if="node.event.note" class="lr-ev-note">· {{ node.event.note }}</span>
              </template>
              <template v-else>
                <b>{{ $t('tools.loan.rec.nodePeriod', { n: node.period }) }}</b>
                <span v-if="node.ym" class="lr-node-ym">{{ node.ym }}</span>
                <span>{{ money(node.row ? node.row.payment : view.snapshot.currentPayment) }}</span>
                <span v-if="node.row && node.row.days" class="lr-node-days">{{
                  $t('tools.loan.rec.firstPeriodTag', { d: node.row.days })
                }}</span>
                <el-tag v-if="node.kind === 'next'" size="small" class="lr-tag lr-tag-sage">{{ $t('tools.loan.rec.nextTag') }}</el-tag>
              </template>
            </span>
          </div>
          <template #content>
            <div class="lr-tip">
              <div class="lr-tip-head">
                <b>{{ $t('tools.loan.rec.nodePeriod', { n: node.period }) }}</b>
                <span v-if="node.ym">{{ node.ym }}</span>
                <em v-if="node.kind === 'next'">{{ $t('tools.loan.rec.nextTag') }}</em>
                <em v-else-if="node.kind === 'future'">{{ $t('tools.loan.rec.futureTag') }}</em>
              </div>
              <div v-for="it in tipRows(node)" :key="it.k" class="lr-tip-row" :class="{ emph: it.emph }">
                <span>{{ it.k }}</span
                ><b>{{ it.v }}</b>
              </div>
              <template v-if="node.event">
                <div class="lr-tip-row">
                  <span>{{ $t('tools.loan.rec.tipEvent') }}</span>
                  <b>{{
                    node.event.type === LOAN_EVENT.RATE_CHANGE
                      ? $t('tools.loan.rec.rateChangeLine', { v: rate(node.event.rate) })
                      : $t('tools.loan.rec.prepayLine', { v: money(node.event.amount) })
                  }}</b>
                </div>
                <div v-if="node.event.type === LOAN_EVENT.PREPAY" class="lr-tip-row">
                  <span>{{ $t('tools.loan.rec.prepayStrategy') }}</span>
                  <b>{{ $t('dict.loan_prepay_strategy.' + (node.event.strategy || 'SHORTEN')) }}</b>
                </div>
                <div v-if="node.tip && node.tip.afterPayment" class="lr-tip-row">
                  <span>{{ $t('tools.loan.rec.tipAfterPayment') }}</span
                  ><b>{{ money(node.tip.afterPayment) }}</b>
                </div>
                <div v-if="node.event.triggerDate" class="lr-tip-row">
                  <span>{{ $t('tools.loan.rec.tipTriggerDate') }}</span
                  ><b>{{ node.event.triggerDate }}</b>
                </div>
                <div v-if="node.event.note" class="lr-tip-note">{{ node.event.note }}</div>
              </template>
              <div v-if="node.kind === 'future'" class="lr-tip-foot">{{ $t('tools.loan.rec.tipProjected') }}</div>
            </div>
          </template>
        </el-tooltip>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatYuan, periodYearMonth, LOAN_EVENT } from '@/utils/loan'

const props = defineProps({
  view: { type: Object, required: true },
  canManage: { type: Boolean, default: false },
})

const emit = defineEmits(['open-event', 'move-event'])

const { t } = useI18n()

const money = (v, digits = 2) => formatYuan(v, digits)
const rate = (v) => (Number(v) || 0).toFixed(4)

/** ==================== 流水线图 ==================== */

/**
 * 自下而上:最下第 1 期,最上下一期(未还,配色区分);事件节点贴在「第 N 期后」的位置,
 * 尚未生效的事件以虚线幽灵节点浮在顶部;年度辅助线在跨年处标注年份,节点自带年月。
 * 每颗节点带 key(拖拽落点定位/高亮用)与 tip(悬浮显示该期的还款与待还本金等数据)。
 */
const pipeNodes = computed(() => {
  const c = props.view
  if (!c || !c.ledger.rows.length) return []
  const paid = c.snapshot.paidPeriods
  const rows = c.ledger.rows
  const evByPeriod = {}
  for (const e of c.events || []) {
    if (!evByPeriod[e.effectivePeriod]) evByPeriod[e.effectivePeriod] = []
    evByPeriod[e.effectivePeriod].push(e)
  }
  // 累计已还利息(截至该期含本期),节点悬浮数据用
  const cumInterest = []
  let acc = 0
  for (const r of rows) {
    acc += r.interest
    cumInterest.push(Math.round(acc * 100) / 100)
  }
  const tipOf = (period, row) => {
    const r = row || rows[period - 1]
    if (!r) return null
    return {
      payment: r.payment,
      principal: r.principal,
      interest: r.interest,
      balance: r.balance,
      // 组合贷的合计流水没有单一利率,取不到就不显示这一行
      rate: r.rate == null ? null : r.rate,
      remaining: Math.max(0, rows.length - period),
      cumInterest: cumInterest[period - 1] == null ? null : cumInterest[period - 1],
      // 事件在「第 period 期还款后」生效,故生效后的月供就是下一期那一行的月供
      afterPayment: rows[period] ? rows[period].payment : null,
    }
  }
  const out = []
  if (!c.snapshot.settled && paid + 1 <= rows.length) {
    out.push({ kind: 'next', period: paid + 1, row: rows[paid] })
  }
  for (let k = Math.min(paid, rows.length); k >= 1; k--) {
    for (const e of evByPeriod[k] || []) out.push({ kind: 'event', period: k, event: e })
    out.push({ kind: 'paid', period: k, row: rows[k - 1] })
  }
  const future = (c.events || []).filter((e) => e.effectivePeriod > paid).sort((a, b) => b.effectivePeriod - a.effectivePeriod)
  for (const e of future) out.unshift({ kind: 'future', period: e.effectivePeriod, event: e })
  let prevYm = ''
  for (const n of out) {
    // 同一期可能有多颗节点(期次节点 + 事件节点),key 带上事件 id 与所属贷款保证唯一
    n.key = n.event
      ? 'e' + (n.event.id != null ? n.event.id : '') + '@' + (n.event.loanId != null ? n.event.loanId : '') + '-' + n.period
      : n.kind + '-' + n.period
    n.tip = tipOf(n.period, n.row)
    n.ym = periodYearMonth(c.firstPayDate, n.period)
    n.newYear = Boolean(prevYm && n.ym && n.ym.slice(0, 4) !== prevYm.slice(0, 4))
    if (n.ym) prevYm = n.ym
  }
  return out
})

/** 节点类名:事件菱形(利率调整/提前还款两色)/历史期次/下一期/未生效幽灵 + 拖拽中的落点与起点 */
const nodeClass = (node) => {
  const cls = []
  if (node.event) {
    cls.push('lr-pev', node.event.type === LOAN_EVENT.RATE_CHANGE ? 'lr-pev-rate' : 'lr-pev-prepay')
    if (node.kind === 'future') cls.push('lr-future')
  } else {
    cls.push(node.kind === 'next' ? 'lr-pnext' : 'lr-ppaid')
  }
  if (dragKey.value === node.key) cls.push('lr-droptarget')
  if (dragFromKey.value === node.key) cls.push('lr-dragging')
  return cls
}

/** 悬浮数据行(值为 0 的行也照常显示,便于对账;取不到的行直接不显示) */
const tipRows = (node) => {
  const tp = node.tip
  if (!tp) return []
  const out = [
    { k: t('tools.loan.rec.tipPayment'), v: money(tp.payment) },
    { k: t('tools.loan.rec.tipPrincipal'), v: money(tp.principal) },
    { k: t('tools.loan.rec.tipInterest'), v: money(tp.interest) },
    { k: t('tools.loan.rec.tipBalance'), v: money(tp.balance), emph: true },
    { k: t('tools.loan.rec.tipRemaining'), v: t('tools.loan.rec.tipPeriods', { n: tp.remaining }) },
  ]
  if (tp.rate != null) out.push({ k: t('tools.loan.colRate'), v: rate(tp.rate) + '%' })
  if (tp.cumInterest != null) out.push({ k: t('tools.loan.rec.tipCumInterest'), v: money(tp.cumInterest) })
  return out
}

/** ==================== 事件拖拽 ==================== */

const dragType = ref('') // 拖拽中的事件类型;空串 = 没在拖
const dragEv = ref(null) // 从流水线上拖起的事件(从芯片新建时为 null)
const dragFromKey = ref('') // 被拖起的节点 key(拖拽期间淡化)
const dragKey = ref('') // 当前落点节点 key
const dragAt = ref(0) // 当前落点期次
const dropTop = ref(0) // 落点提示条位置(相对 .lr-pipe 内容,px)
const dropOn = computed(() => dragAt.value > 0)
const dropLabel = computed(() => t('tools.loan.rec.' + (dragEv.value ? 'dropMarkMove' : 'dropMark'), { n: dragAt.value }))
let lastDropEl = null // 上一次的落点节点(迟滞判定用;DOM 引用不必进响应式)

/** 落点迟滞(px):光标仍在目标节点上下这个范围内就不改落点,夹缝处不再来回跳 */
const DROP_HOLD = 8

const clearDrop = () => {
  dragAt.value = 0
  dragKey.value = ''
  lastDropEl = null
}

const onChipDragStart = (e, type) => {
  dragType.value = type
  dragEv.value = null
  dragFromKey.value = ''
  clearDrop()
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'copyMove'
    e.dataTransfer.setData('text/plain', type)
  }
}

/** 只有事件节点能拖(期次节点不参与),且要有管理权、单笔视图(组视图的事件归属别的贷款) */
const canDragNode = (node) => Boolean(node.event) && props.canManage && !(props.view && props.view.isGroup)

/** 流水线上已有的事件也能拖:拖起后按落点处理(见 onDrop) */
const onNodeDragStart = (e, node) => {
  if (!canDragNode(node)) return
  dragType.value = node.event.type
  dragEv.value = node.event
  dragFromKey.value = node.key
  clearDrop()
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', node.event.type)
  }
  // 起点先当作落点:拖一小段又放回原处时保持不动,不会误挪一期
  const el = e.currentTarget
  if (el && el.dataset && el.dataset.key) {
    lastDropEl = el
    dragKey.value = node.key
    dragAt.value = node.period
    dropTop.value = Math.max(0, el.offsetTop - 9)
  }
}

const onDragEnd = () => {
  dragType.value = ''
  dragEv.value = null
  dragFromKey.value = ''
  clearDrop()
}

const dropPointOf = (el) => ({ el, key: el.dataset.key, period: Number(el.dataset.period), top: el.offsetTop })

/**
 * 光标位置 → 落点节点:光标落在某颗节点上就取它;落在夹缝/空白处取下方最近的那颗
 * (与「事件在第 N 期还款后生效」的语义一致);低于全部节点取最下那颗(第 1 期)。
 */
const dropPointFrom = (container, e) => {
  const hit = e.target && e.target.closest ? e.target.closest('.lr-pnode[data-key]') : null
  if (hit && container.contains(hit)) return dropPointOf(hit)
  if (lastDropEl && container.contains(lastDropEl)) {
    const r = lastDropEl.getBoundingClientRect()
    if (e.clientY >= r.top - DROP_HOLD && e.clientY <= r.bottom + DROP_HOLD) return dropPointOf(lastDropEl)
  }
  const nodes = [...container.querySelectorAll('.lr-pnode[data-key]')]
  if (!nodes.length) return null
  let best = null
  for (const el of nodes) {
    const r = el.getBoundingClientRect()
    if (r.bottom > e.clientY && (best === null || r.top < best.top)) best = { top: r.top, el }
  }
  return dropPointOf(best ? best.el : nodes[nodes.length - 1])
}

const onDragOver = (e) => {
  if (!dragType.value) return
  const point = dropPointFrom(e.currentTarget, e)
  if (!point) {
    clearDrop()
    return
  }
  lastDropEl = point.el
  dragKey.value = point.key
  dragAt.value = point.period
  dropTop.value = Math.max(0, point.top - 9)
}

/** 光标划过节点子元素时浏览器也会在流水线上触发 dragleave:按坐标判定,只有真离开流水线才清落点 */
const onDragLeave = (e) => {
  const r = e.currentTarget.getBoundingClientRect()
  if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) return
  clearDrop()
}

const onDrop = (e) => {
  const type = dragType.value
  const moving = dragEv.value
  const view = props.view
  const point = type ? dropPointFrom(e.currentTarget, e) : null
  onDragEnd()
  if (!type || !view || view.isGroup || !point) return
  if (moving) {
    // 拖动已有的那条事件:填过事件时间的要重新确认日期(期次按落点走、日期按落点重推)→ 开编辑;
    // 没填事件时间的只挪期次,直接保存不打扰。
    if (moving.triggerDate) emit('open-event', { type: moving.type, ev: moving, period: point.period })
    else emit('move-event', { ev: moving, period: point.period })
    return
  }
  emit('open-event', { type, ev: null, period: point.period })
}
</script>

<style scoped>
.lc-card {
  padding: 18px 20px;
}
.lc-card .section-label {
  margin-bottom: 14px;
}
.lc-block {
  margin-top: 16px;
}
.lc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.lc-label-flat {
  margin-bottom: 0 !important;
}
.lc-head .lc-hint-inline {
  flex: 1;
  min-width: 160px;
}
.lc-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-hint-inline {
  margin: 0;
}

/* ---- 还款流水线(自下而上) ---- */
.lr-drag-palette {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.lr-chip {
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  cursor: grab;
  border: 1px dashed;
  display: inline-flex;
  align-items: center;
  user-select: none;
  flex-shrink: 0;
}
.lr-chip:active {
  cursor: grabbing;
}
.lr-chip-rate {
  color: var(--lr-pink);
  border-color: rgba(var(--lr-pink-rgb), 0.6);
  background: rgba(var(--lr-pink-rgb), 0.08);
}
.lr-chip-prepay {
  color: var(--lr-orange);
  border-color: rgba(var(--lr-orange-rgb), 0.6);
  background: rgba(var(--lr-orange-rgb), 0.08);
}
.lr-pipe {
  position: relative;
  margin-top: 6px;
  padding: 8px 4px 8px 0;
  max-height: 480px;
  overflow-y: auto;
}
.lr-pipe::before {
  content: '';
  position: absolute;
  left: 23px;
  top: 12px;
  bottom: 12px;
  width: 2px;
  border-radius: 1px;
  background: var(--color-border, rgba(58, 46, 34, 0.18));
}
.lr-pnode {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 7px 0 7px 42px;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b5a);
  min-width: 0;
}
.lr-pnode-text {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
  word-break: break-all;
}
.lr-pnode-text b {
  color: var(--color-text, #3a2e22);
  font-weight: 600;
}
/* 拖拽落点:只加背景与内描边,不动布局(改 padding/border 会把节点挤走,落点就会来回跳) */
.lr-droptarget {
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.16);
  border-radius: 8px;
  box-shadow: inset 3px 0 0 rgba(var(--color-brand-rgb, 184, 140, 110), 0.75);
}
.lr-dragging {
  opacity: 0.35;
}
.lr-pipe .lr-pnode {
  cursor: grab;
}
.lr-pipe .lr-pnode.lr-pev:active {
  cursor: grabbing;
}
/* 历史期次:品牌色圆点 */
.lr-ppaid::before {
  content: '';
  position: absolute;
  left: 19px;
  top: 50%;
  transform: translateY(-50%);
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-brand, #b88c6e);
  box-shadow: 0 0 0 3px rgba(var(--color-brand-rgb, 184, 140, 110), 0.15);
}
/* 下一期:鼠尾草绿大圆点 + 光环(未还的第一期),文案加重 */
.lr-pnext {
  padding-top: 10px;
  padding-bottom: 10px;
}
.lr-pnext::before {
  content: '';
  position: absolute;
  left: 17px;
  top: 50%;
  transform: translateY(-50%);
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--lr-sage);
  box-shadow: 0 0 0 4px rgba(var(--lr-sage-rgb), 0.2);
}
.lr-pnext .lr-pnode-text b {
  font-size: 13px;
  color: var(--lr-sage);
}
.lr-node-ym {
  flex-shrink: 0;
}
.lr-node-days {
  color: var(--color-accent, #a8483a);
}
/* 事件节点:菱形,利率调整=主题粉 / 提前还款=陶土橙 */
.lr-pev .lr-shape {
  position: absolute;
  left: 20px;
  top: 50%;
  transform: translateY(-50%) rotate(45deg);
  width: 9px;
  height: 9px;
}
.lr-pev-rate .lr-shape {
  background: var(--lr-pink);
}
.lr-pev-prepay .lr-shape {
  background: var(--lr-orange);
}
.lr-pev-rate .lr-pnode-text b {
  color: var(--lr-pink);
}
.lr-pev-prepay .lr-pnode-text b {
  color: var(--lr-orange);
}
/* 未生效事件:虚线幽灵浮在顶部,整体走鼠尾草绿(状态优先于事件类型,类型由标签与悬浮说明给出) */
.lr-pnode.lr-future {
  opacity: 0.75;
}
.lr-pnode.lr-future .lr-shape {
  background: transparent;
  border: 1.5px dashed var(--lr-sage);
}
.lr-pnode.lr-future .lr-pnode-text b {
  color: var(--lr-sage);
}
.lr-ev-note {
  color: var(--color-text-secondary, #7a6b5a);
}
/* 年度辅助线:跨年处的横向虚线 + 年份 */
.lr-yline {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 6px 42px;
  font-size: 11px;
  color: var(--color-text-secondary, #7a6b5a);
}
.lr-yline::before {
  content: '';
  width: 16px;
  border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.3));
}
.lr-yline::after {
  content: '';
  flex: 1;
  border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.3));
}
/* 拖拽落点提示条:绝对定位 + 常驻 DOM(不参与布局,拖拽中不增删元素,光标下的节点不变)
 * —— 夹缝处不再频闪,也不会因节点被换掉而丢掉这次拖拽 */
.lr-dropmark {
  position: absolute;
  left: 42px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 10px;
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
  border-radius: 999px;
  border: 1px dashed;
  background: var(--color-card, #faf6ec);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease;
}
.lr-dropmark.on {
  opacity: 1;
}
.lr-drop-rate {
  color: var(--lr-pink);
  border-color: rgba(var(--lr-pink-rgb), 0.75);
}
.lr-drop-prepay {
  color: var(--lr-orange);
  border-color: rgba(var(--lr-orange-rgb), 0.75);
}

/* 节点悬浮数据(内容被 teleport 到 body,容器类名靠 :global 限定) */
.lr-tip {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 190px;
  font-size: 12px;
  line-height: 1.5;
}
.lr-tip-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 3px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.18);
}
.lr-tip-head b {
  font-size: 13px;
}
.lr-tip-head span {
  opacity: 0.8;
}
.lr-tip-head em {
  font-style: normal;
  opacity: 0.75;
}
.lr-tip-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}
.lr-tip-row > span {
  opacity: 0.78;
}
.lr-tip-row.emph > b {
  font-weight: 700;
}
.lr-tip-note {
  margin-top: 2px;
  padding-top: 3px;
  border-top: 1px solid rgba(255, 255, 255, 0.18);
  opacity: 0.8;
  word-break: break-all;
}
.lr-tip-foot {
  margin-top: 2px;
  opacity: 0.7;
}
:global(.lr-tip-popper) {
  max-width: 300px;
}
</style>
