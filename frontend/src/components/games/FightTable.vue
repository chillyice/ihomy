<!-- 对抗牌桌公共外壳:座位环绕、中央出牌区、底部手牌与操作栏。
     规则全部由各玩法页面提供,这里只做布局、选牌与操作按钮的展示。 -->
<template>
  <div class="ft-root game-surface" @contextmenu.prevent @dblclick.prevent>
    <div class="ft-table">
      <div
        v-for="s in seatViews"
        :key="s.seat"
        class="ft-seat"
        :class="[`pos-${s.rel}`, { active: s.active, finished: s.finished, me: s.isMe }]"
        :style="{ left: s.x, top: s.y }"
      >
        <div class="ft-avatar">
          <img v-if="s.avatar" :src="s.avatar" :alt="s.name" />
          <el-icon v-else :size="20"><UserFilled /></el-icon>
          <span v-if="s.active" class="ft-turn-dot" aria-hidden="true" />
        </div>
        <div class="ft-seat-main">
          <div class="ft-seat-name" :title="s.name">{{ s.name }}</div>
          <div v-if="s.roleLabel" class="ft-role" :class="s.tone">{{ s.roleLabel }}</div>
          <div class="ft-seat-count">{{ $t('games.fight.cardsLeft', { n: s.count }) }}</div>
        </div>
        <div v-if="s.finishedLabel" class="ft-finish">{{ s.finishedLabel }}</div>
        <div v-else-if="s.bubble" class="ft-seat-bubble">{{ s.bubble }}</div>
      </div>

      <div class="ft-center">
        <div v-if="infoChips?.length" class="ft-chips">
          <span v-for="chip in infoChips" :key="chip.key" class="ft-chip">
            <b>{{ chip.label }}</b>
            <span v-if="chip.value">{{ chip.value }}</span>
          </span>
        </div>

        <div v-if="lastPlay" class="ft-last" role="region" :aria-label="$t('games.fight.lastPlay')">
          <div class="ft-last-head">
            <span class="ft-last-name">{{ lastPlay.name }}</span>
            <span v-if="lastPlay.label" class="ft-last-combo">{{ lastPlay.label }}</span>
          </div>
          <div class="ft-last-cards" :style="{ '--fc-last-per-row': lastPerRow }">
            <span v-for="c in lastPlay.cards" :key="c.id" class="ft-mini">
              <PlayingCard :card="c" />
            </span>
          </div>
        </div>
        <div v-else class="ft-center-empty">{{ statusText }}</div>

        <div v-if="lastPlay" class="ft-status">{{ statusText }}</div>
        <slot name="center-extra" />
      </div>

      <slot name="table-extra" />
    </div>

    <div class="ft-my-area">
      <div class="ft-my-head">
        <span class="ft-my-name">{{ myName }}</span>
        <span v-if="myRoleLabel" class="ft-role my" :class="myTone">{{ myRoleLabel }}</span>
        <span class="ft-my-count">{{ $t('games.fight.cardsLeft', { n: hand.length }) }}</span>
        <span v-if="myNote" class="ft-my-note">{{ myNote }}</span>
        <!-- 手机横屏把出牌/提示/不要排到手牌正上方,提示语跟过去(left 对齐),中心区那条同档隐藏 -->
        <span v-if="statusText" class="ft-my-status">{{ statusText }}</span>
      </div>
      <div class="ft-hand" :style="{ '--fc-hand-n': Math.max(1, hand.length) }" role="group" :aria-label="$t('games.fight.myHand')">
        <button
          v-for="card in sortedHand"
          :key="card.id"
          type="button"
          class="ft-card"
          :class="{ sel: selectedIds.has(card.id), wild: isWild?.(card) }"
          :aria-pressed="selectedIds.has(card.id)"
          :aria-label="cardLabel(card)"
          :data-id="card.id"
          @click="onCardClick(card)"
          @pointerdown="onCardDown(card, $event)"
        >
          <PlayingCard :card="card" />
          <span v-if="isWild?.(card)" class="ft-wild">{{ $t('games.fight.wild') }}</span>
        </button>
      </div>
      <div class="ft-actions">
        <slot name="actions" />
      </div>
    </div>

    <slot name="overlay" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import PlayingCard from './PlayingCard.vue'
import { cardRankText } from '@/utils/games/cardCore'

const props = defineProps({
  seats: { type: Array, required: true },
  mySeat: { type: Number, default: 0 },
  hand: { type: Array, default: () => [] },
  selected: { type: Array, default: () => [] },
  lastPlay: { type: Object, default: null },
  statusText: { type: String, default: '' },
  infoChips: { type: Array, default: () => [] },
  isWild: { type: Function, default: null },
  myName: { type: String, default: '' },
  myRoleLabel: { type: String, default: '' },
  myTone: { type: String, default: '' },
  myNote: { type: String, default: '' },
  sortValue: { type: Function, default: null },
})

const emit = defineEmits(['select', 'extend-select', 'auto-select'])

// ---------- 手牌拖选(鼠标左/右键 + 触摸/笔) ----------
// 左键或手指按住扫过多张 = 扩选(逐张并入选择);鼠标右键按住扫过多张 = 标记自动出牌,
// 松开右键整体交给页面,轮到自己时自动打出、打不出由页面清空选中让牌落回去。
// 按下时不接管指针(要靠 pointer 落点找新牌),真拖起来才挂 window 监听,松手即摘。
// 触摸阈值放宽:手抖一旦过阈值,起点牌就会被并入选择,与「点选取消」方向相反,宁小勿误。
const MOUSE_THRESHOLD = 6
const TOUCH_THRESHOLD = 10
let dragState = null
let justDragged = false

// 落点取「最上层且本次未扫过」的牌:叠牌层级是右压左,只看 elementFromPoint 命中的
// 第一张可能已在本轮扫过(它下面压着左邻),自上而下找第一张未扫过的才不漏牌
const cardIdAt = (x, y, exclude) => {
  for (const el of document.elementsFromPoint(x, y)) {
    const id = el.closest?.('.ft-card')?.dataset.id
    if (id && !exclude?.has(id)) return id
  }
  return null
}

function onCardClick(card) {
  if (justDragged) {
    justDragged = false
    return
  }
  emit('select', card)
}

function onCardDown(card, e) {
  if (dragState) return
  let mode = ''
  if (e.pointerType === 'mouse') {
    if (e.button === 0) mode = 'extend'
    else if (e.button === 2) mode = 'auto'
  } else if (e.pointerType === 'touch' || e.pointerType === 'pen') {
    mode = 'extend'
  }
  if (!mode) return
  dragState = {
    mode,
    pointerId: e.pointerId,
    threshold: e.pointerType === 'mouse' ? MOUSE_THRESHOLD : TOUCH_THRESHOLD,
    active: false,
    startX: e.clientX,
    startY: e.clientY,
    ids: new Set([card.id]),
  }
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragUp)
  window.addEventListener('pointercancel', onDragUp)
}

function onDragMove(e) {
  if (!dragState || e.pointerId !== dragState.pointerId) return
  if (!dragState.active) {
    if (Math.hypot(e.clientX - dragState.startX, e.clientY - dragState.startY) < dragState.threshold) return
    dragState.active = true
    if (dragState.mode === 'extend') emit('extend-select', [...dragState.ids])
  }
  const id = cardIdAt(e.clientX, e.clientY, dragState.ids)
  if (!id || dragState.ids.has(id)) return
  dragState.ids.add(id)
  if (dragState.mode === 'extend') emit('extend-select', [id])
}

function onDragUp(e) {
  if (dragState && e.pointerId !== dragState.pointerId) return
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragUp)
  window.removeEventListener('pointercancel', onDragUp)
  if (!dragState) return
  const state = dragState
  dragState = null
  if (!state.active) return
  if (state.mode === 'extend') {
    // 拖选落点的那张牌会再收到合成 click,吞掉避免把刚选中的牌又反选回去
    justDragged = true
    setTimeout(() => {
      justDragged = false
    }, 0)
  } else {
    emit('auto-select', [...state.ids])
  }
}

// 相对座位坐标(百分比):0 号永远是自己(下),按逆时针依次环绕。
const POSITIONS = {
  3: [
    [50, 98],
    [10, 46],
    [90, 46],
  ],
  4: [
    [50, 98],
    [7, 55],
    [50, 6],
    [93, 55],
  ],
  5: [
    [50, 98],
    [8, 70],
    [20, 12],
    [80, 12],
    [92, 70],
  ],
  6: [
    [50, 98],
    [6, 72],
    [14, 16],
    [50, 5],
    [86, 16],
    [94, 72],
  ],
}

const selectedIds = computed(() => new Set(props.selected || []))
const sortedHand = computed(() => {
  const list = [...props.hand]
  if (props.sortValue) list.sort((a, b) => props.sortValue(a) - props.sortValue(b))
  return list
})
/* 中央上一手牌:每行张数决定牌面宽度——张少就放大到上限,张多按两行摊开,
   牌面始终在「能读出点数」与「不溢出中央区」之间取最大(尺寸公式见 .ft-mini) */
const lastPerRow = computed(() => {
  const n = props.lastPlay?.cards?.length || 0
  if (!n) return 6
  return n <= 8 ? n : Math.ceil(n / 2)
})
const seatViews = computed(() => {
  const n = Math.max(1, props.seats.length)
  const table = POSITIONS[n] || POSITIONS[6]
  return props.seats.map((s, seat) => {
    const rel = (seat - props.mySeat + n) % n
    const [x, y] = table[rel] || table[0]
    // 座位宽高的一半在 CSS 里(--ft-seat-half / --ft-seat-half-h):
    // 边缘座位按 translate(-50%) 定位会有一半探出牌桌被 overflow:hidden 裁掉(手机竖屏最明显),
    // 用 clamp 把圆心拉回桌内,坐标仍然是原来的百分比
    return {
      ...s,
      seat,
      rel,
      x: `clamp(var(--ft-seat-half, 50px), ${x}%, calc(100% - var(--ft-seat-half, 50px)))`,
      y: `clamp(var(--ft-seat-half-h, 24px), ${y}%, calc(100% - var(--ft-seat-half-h, 24px)))`,
    }
  })
})

const SUIT_SYMBOL = { S: '♠', H: '♥', C: '♣', D: '♦' }
const cardLabel = (card) => {
  if (!card) return ''
  if (card.suit === 'JOKER') return cardRankText(card)
  return `${cardRankText(card)}${SUIT_SYMBOL[card.suit] || ''}`
}
</script>

<style scoped>
.ft-root {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
  /* 座位定位的一半宽高(见 seatViews 的 clamp):max-width 132 / 头像 30 的座位大致一半 */
  --ft-seat-half: 66px;
  --ft-seat-half-h: 27px;
}
.ft-root:fullscreen {
  padding: clamp(8px, 2vw, 18px);
  overflow: auto;
  background: var(--color-bg);
}

.ft-table {
  position: relative;
  flex: 1 1 300px;
  min-height: 300px;
  border-radius: 18px;
  background:
    radial-gradient(ellipse at 50% 38%, rgba(255, 255, 255, 0.1), transparent 62%), linear-gradient(160deg, #1f5c39, #123c26 62%, #0d2f1e);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.08),
    inset 0 26px 60px rgba(0, 0, 0, 0.18);
  overflow: hidden;
  /* 容器查询锚点:中央出牌区的可用宽度按牌桌实际宽收敛(cqw),手机/桌面/全屏一套公式 */
  container-type: inline-size;
  --fc-last-w: min(84cqw, 620px);
  --fc-last-cap: 60px;
}

.ft-seat {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 132px;
  padding: 4px 7px;
  border-radius: 10px;
  background: rgba(6, 26, 15, 0.5);
  color: #eef7ee;
  transform: translate(-50%, -50%);
  transition:
    background 0.2s ease,
    box-shadow 0.2s ease;
}
.ft-seat.me {
  background: rgba(255, 209, 102, 0.16);
}
.ft-seat.active {
  background: rgba(255, 209, 102, 0.26);
  box-shadow:
    0 0 0 1px rgba(255, 209, 102, 0.7),
    0 0 16px rgba(255, 209, 102, 0.35);
}
.ft-seat.finished {
  opacity: 0.6;
}
.ft-avatar {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.14);
}
.ft-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ft-turn-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ffd166;
  box-shadow: 0 0 8px #ffd166;
}
.ft-seat-main {
  min-width: 0;
  line-height: 1.25;
}
.ft-seat-name {
  overflow: hidden;
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ft-seat-count {
  font-size: 11px;
  opacity: 0.82;
}
.ft-role {
  display: inline-block;
  margin-top: 1px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 10px;
  line-height: 15px;
}
.ft-role.landlord,
.ft-role.emperor,
.ft-role.royal {
  background: rgba(255, 209, 102, 0.3);
  color: #ffe6a2;
}
.ft-role.farmer,
.ft-role.rebel {
  background: rgba(130, 195, 255, 0.24);
  color: #d5ecff;
}
.ft-role.wild {
  background: rgba(255, 120, 160, 0.26);
  color: #ffd9e4;
}
.ft-role.my {
  margin-left: 2px;
}
.ft-finish {
  position: absolute;
  right: -4px;
  top: -10px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ffd166;
  color: #3a2600;
  font-size: 10px;
  font-weight: 800;
  white-space: nowrap;
}
.ft-seat-bubble {
  position: absolute;
  left: 50%;
  top: calc(100% + 2px);
  padding: 0 6px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.55);
  font-size: 11px;
  white-space: nowrap;
  transform: translateX(-50%);
}

.ft-center {
  position: absolute;
  left: 50%;
  top: 47%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: var(--fc-last-w, min(64%, 560px));
  color: #f2fff2;
  text-align: center;
  transform: translate(-50%, -50%);
  pointer-events: none;
}
.ft-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 5px;
}
.ft-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.32);
  font-size: 11px;
}
.ft-chip b {
  font-weight: 600;
  opacity: 0.74;
}
.ft-last {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.ft-last-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.ft-last-name {
  font-weight: 700;
}
.ft-last-combo {
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(255, 209, 102, 0.24);
  color: #ffe9ae;
}
.ft-last-cards {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4px;
}
/* 上一手牌是牌桌的视觉焦点,牌面按「每行张数」摊开取最大可读尺寸:
   张少放大到上限(角标点数清晰可辨),张多自动缩到两行放得下,不再出现小到读不出 */
.ft-mini {
  position: relative;
  display: block;
  width: clamp(30px, calc(var(--fc-last-w, 560px) / var(--fc-last-per-row, 6) - 4px), var(--fc-last-cap, 60px));
  aspect-ratio: 5 / 7;
  border-radius: 5px;
  background: #fdfcf8;
  box-shadow: 0 3px 9px rgba(0, 0, 0, 0.38);
  overflow: hidden;
}
.ft-center-empty {
  font-size: 15px;
  font-weight: 600;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5);
}
.ft-status {
  font-size: 12px;
  opacity: 0.86;
}

.ft-my-area {
  /* 容器查询锚点:手牌步长按这里的实际宽度收敛(横屏左栏/全屏下 100vw 都会算错) */
  container-type: inline-size;
  display: flex;
  flex: 0 0 auto;
  min-height: 0;
  flex-direction: column;
  gap: 4px;
}
.ft-my-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
  font-size: 12px;
  color: var(--color-text-secondary, #666);
}
.ft-my-name {
  font-weight: 700;
  color: var(--color-text, #222);
}
.ft-my-count {
  opacity: 0.8;
}
.ft-my-note {
  margin-left: auto;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 提示语:竖屏看中央区那条(默认不显示),手机横屏才挪到操键这一行 */
.ft-my-status {
  display: none;
}
.ft-hand {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  min-height: calc(var(--fc-card-w, 54px) * 1.4 + 20px);
  padding: 20px 6px 2px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  --fc-hand-n: 20;
  /* 步长按容器宽收敛到刚好放得下(除数补上整牌宽度开销),任何手牌数都不再横向滚动,
     滑动拖选才不会被滚动手势抢走;7px 下限兜底极端窄屏(有横向滚动垫底) */
  --fc-pop: 14px;
  --fc-step: clamp(7px, calc((100cqw - 12px) / (var(--fc-hand-n, 20) + 1.15)), 30px);
  --fc-card-w: clamp(16px, calc(var(--fc-step) * 2.1), 56px);
}
.ft-hand::-webkit-scrollbar {
  display: none;
}
.ft-card {
  position: relative;
  flex: 0 0 auto;
  width: var(--fc-card-w);
  aspect-ratio: 5 / 7;
  margin-left: calc(var(--fc-step) - var(--fc-card-w));
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: #fdfcf8;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.28);
  cursor: pointer;
  overflow: hidden;
  transition:
    transform 0.14s ease,
    box-shadow 0.14s ease;
  /* 拖选(含触摸滑动)不被浏览器接管为滚动,牌面必须自禁触摸手势 */
  touch-action: none;
}
.ft-card:first-child {
  margin-left: 0;
}
.ft-card.sel {
  /* 选中=向上探出一块;不做 z-index 抬层——手牌一律右压左,
     探出的牌不许盖住右侧的牌(选中的牌被右邻盖住右缘是预期内的) */
  transform: translateY(calc(var(--fc-pop, 14px) * -1));
  outline: 2px solid #ffd166;
  outline-offset: -2px;
  box-shadow:
    0 0 0 1px rgba(255, 209, 102, 0.55),
    0 2px 6px rgba(0, 0, 0, 0.28);
}
.ft-card.wild {
  box-shadow:
    0 0 0 2px rgba(255, 120, 160, 0.7),
    0 2px 6px rgba(0, 0, 0, 0.28);
}
.ft-card :deep(.pc-face) {
  position: absolute;
  inset: 0;
}
.ft-card :deep(.pc-img) {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ft-wild {
  position: absolute;
  right: 1px;
  bottom: 1px;
  padding: 0 3px;
  border-radius: 4px;
  background: #ff5c8a;
  color: #fff;
  font-size: 9px;
  line-height: 13px;
  font-weight: 800;
}
.ft-actions {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
}
.ft-actions:empty {
  display: none;
}

@media (max-width: 720px) {
  .ft-root {
    --ft-seat-half: 50px;
    --ft-seat-half-h: 24px;
  }
  .ft-table {
    min-height: 260px;
  }
  .ft-seat {
    max-width: 100px;
    padding: 3px 5px;
  }
  .ft-avatar {
    width: 24px;
    height: 24px;
  }
  .ft-seat-name {
    font-size: 11px;
  }
  /* 窄屏中央区窄一档(70cqw),牌面宽度公式随 --fc-last-w 自动收敛 */
  .ft-table {
    --fc-last-w: min(70cqw, 560px);
  }
}

/* 手机横屏/矮窗(高 ≤ 520px):牌桌优先让位,手牌与操作区必须完整可见(裁了就没法出牌)。
   桌面矮窗同样适用——只是把牌桌压缩,不影响玩法。 */
@media (max-height: 520px) {
  .ft-root {
    gap: 5px;
  }
  .ft-table {
    flex: 1 1 140px;
    min-height: 140px;
  }
  .ft-my-head {
    min-height: 0;
    font-size: 11px;
  }
  .ft-hand {
    --fc-pop: 9px;
    min-height: calc(var(--fc-card-w, 40px) * 1.15 + 10px);
    padding: 10px 6px 0;
  }
  .ft-actions {
    min-height: 28px;
    gap: 6px;
  }
  .ft-center {
    font-size: 12px;
  }
  .ft-seat {
    padding: 2px 5px;
  }
}

/* 大屏:手牌放宽一档——每张露出 46px(拇指点得准、不会点到邻居),整牌 84px 看清点数花色。
   窄屏/矮屏仍按上面的 30px / 56px 收敛,保证一屏放得下 */
@media (min-width: 900px) and (min-height: 620px) {
  .ft-hand {
    --fc-step: clamp(7px, calc((100cqw - 12px) / (var(--fc-hand-n, 20) + 1.15)), 46px);
    --fc-card-w: clamp(16px, calc(var(--fc-step) * 1.9), 84px);
    min-height: calc(var(--fc-card-w, 54px) * 1.4 + 20px);
  }
  .ft-table {
    --fc-last-cap: 72px;
  }
}

/* 手机横屏:整页只剩这一块桌毡(main.css 对抗牌局段把外壳左栏浮起来),
   所以这里把牌桌自身的盒子抹掉、并把「出牌/提示/不要」排到手牌正上方。
   手牌照整屏宽重新收敛:20 张也能到 84px 宽,点选/拖选都看得清。 */
@media (orientation: landscape) and (max-height: 520px) {
  .ft-root {
    gap: 4px;
  }
  .ft-table {
    flex: 1 1 120px;
    min-height: 120px;
    border-radius: 0;
  }
  .ft-my-area {
    gap: 4px;
  }
  .ft-my-head {
    order: 0;
    min-height: 0;
    font-size: 11px;
  }
  /* 操作键=手牌上方那一行(横屏只此一份摆放,外壳左栏那份已删) */
  .ft-actions {
    order: 1;
    display: flex;
    flex-wrap: nowrap;
    min-height: 28px;
    gap: 8px;
  }
  .ft-hand {
    order: 2;
    --fc-pop: 9px;
    --fc-step: clamp(7px, calc((100cqw - 12px) / (var(--fc-hand-n, 20) + 1.15)), 40px);
    --fc-card-w: clamp(16px, calc(var(--fc-step) * 2.1), 84px);
    padding: 10px 6px 0;
  }
  .ft-my-status {
    display: block;
    margin-left: auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  /* 同样的提示语只在手上方留一处,中央区那条与空态隐藏 */
  .ft-center .ft-status,
  .ft-center-empty {
    display: none;
  }
  /* 上一手牌以小牌左压右叠成一摞(只留最近一手,不铺牌河) */
  .ft-last-cards {
    flex-wrap: nowrap;
    gap: 0;
  }
  .ft-mini {
    width: 32px;
    margin-left: -16px;
    border-radius: 4px;
  }
  .ft-mini:first-child {
    margin-left: 0;
  }
  .ft-last-head {
    font-size: 11px;
  }
}
</style>
