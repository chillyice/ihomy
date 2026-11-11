<template>
  <div class="sol-card card game-surface" @contextmenu.prevent @dblclick.prevent>
    <div class="sol-head">
      <div class="sol-title">{{ $t('games.freecell.title') }}</div>
      <div class="sol-stats">
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.freecell.moves') }}</span>
          <b class="sol-stat-value">{{ game.moves }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.freecell.best') }}</span>
          <b class="sol-stat-value">{{ best || '—' }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.freecell.time') }}</span>
          <b class="sol-stat-value">{{ formatClock(elapsed) }}</b>
        </div>
      </div>
    </div>

    <div class="sol-actions">
      <el-button size="small" round class="fc-hint" @click="showHint">{{ $t('games.freecell.hint') }}</el-button>
      <el-button size="small" round :disabled="!history.length" @click="undo">{{ $t('games.freecell.undo') }}</el-button>
      <el-button size="small" round @click="autoFinishAll">{{ $t('games.freecell.autoFinish') }}</el-button>
      <el-button type="primary" size="small" round @click="startNew">{{ $t('games.freecell.newGame') }}</el-button>
    </div>

    <div ref="feltEl" class="sol-felt" :style="feltStyle">
      <div class="sol-row sol-top fc-top">
        <div
          v-for="(card, cell) in game.freeCells"
          :key="`free-${cell}`"
          class="sol-slot fc-cell"
          :class="{ 'hint-target': isCellHint(cell) }"
          data-drop="free"
          :data-cell="cell"
        >
          <div
            v-if="card"
            class="sol-cardface"
            :class="[
              cardClass(card),
              {
                selected: selected && selected.type === 'free' && selected.cell === cell,
                'hint-source': isFreeHint(cell),
              },
            ]"
            @pointerdown="startDrag({ type: 'free', cell }, $event)"
            @click="onFreeClick(cell)"
          >
            <PlayingCard :card="card" />
          </div>
          <div v-else class="sol-empty fc-free-empty" @click="onFreeClick(cell)">＋</div>
        </div>
        <div
          v-for="suit in SUITS"
          :key="suit"
          class="sol-slot"
          :class="{ 'hint-target': isFoundationHint(suit) }"
          data-drop="foundation"
          :data-suit="suit"
        >
          <div
            v-if="foundationTop(suit)"
            class="sol-cardface"
            :class="[cardClass(foundationTop(suit)), { selected: selected && selected.type === 'foundation' && selected.suit === suit }]"
            @pointerdown="startDrag({ type: 'foundation', suit }, $event)"
            @click="onFoundationClick(suit)"
          >
            <PlayingCard :card="foundationTop(suit)" />
          </div>
          <div v-else class="sol-empty sol-found" @click="onFoundationClick(suit)">{{ SUIT_SYMBOL[suit] }}</div>
        </div>
      </div>

      <div class="sol-row sol-tableau">
        <div
          v-for="(pile, pi) in game.tableau"
          :key="pi"
          class="sol-pile"
          :class="{ 'hint-target': isPileHint(pi) }"
          data-drop="tableau"
          :data-pile="pi"
          @click="onPileClick(pi)"
        >
          <div v-if="!pile.length" class="sol-empty"></div>
          <div
            v-for="(card, ci) in pile"
            :key="card.id"
            class="sol-cardface"
            :class="[cardClass(card), { selected: isSelected(pi, ci), 'hint-source': isCardHint(pi, ci) }]"
            :style="ci ? { marginTop: margins[pi][ci - 1] } : null"
            @pointerdown="onCardPointerDown(pi, ci, card, $event)"
            @click.stop="onCardClick(pi, ci)"
          >
            <PlayingCard :card="card" />
          </div>
        </div>
      </div>
    </div>

    <div class="sol-tip">{{ hintNote || $t('games.freecell.tip') }}</div>

    <Transition name="game-overlay">
      <div v-if="isWon || isLost" class="sol-overlay game-overlay">
        <div class="sol-overlay-card game-overlay-card">
          <div class="sol-overlay-icon">{{ isWon ? '🏆' : '🚫' }}</div>
          <div class="sol-overlay-title">{{ isWon ? $t('games.freecell.win') : $t('games.freecell.lose') }}</div>
          <div class="sol-overlay-meta">
            {{ isWon ? $t('games.freecell.winMeta', { moves: game.moves }) : $t('games.freecell.loseMeta', { moves: game.moves }) }}
          </div>
          <div v-if="rewardMsg" class="sol-overlay-reward">{{ rewardMsg }}</div>
          <div class="sol-overlay-actions">
            <el-button v-if="isLost && history.length" size="small" round @click="undo">{{ $t('games.freecell.undo') }}</el-button>
            <el-button type="primary" size="small" round @click="startNew">{{ $t('games.freecell.newGame') }}</el-button>
          </div>
        </div>
      </div>
    </Transition>

    <CardDragGhost :drag="drag" />
  </div>
</template>

<script setup>
// 自由纸牌(FreeCell):发牌/叠牌/整段搬运上限/自动收纳/提示/无路可走判定全在 utils/games/freecell.js。
// 本组件负责点选与拖动移动、双击自动收纳、撤销快照、计时与最少步数纪录;牌面统一由 PlayingCard 渲染。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { gameApi } from '@/api'
import PlayingCard from '@/components/games/PlayingCard.vue'
import { useCardDrag } from '@/composables/useCardDrag'
import { formatClock } from '@/utils/games/gameClock'
import { CARD_RATIO, fitCardWidth, pileSteps } from '@/utils/games/cardLayout'
import {
  COLUMNS,
  STATUS,
  SUITS,
  autoFinish,
  autoMove,
  cardColor,
  cloneState,
  deal,
  findHint,
  isValidState,
  move,
  sourceCards,
} from '@/utils/games/freecell'
import CardDragGhost from './CardDragGhost.vue'

const BEST_KEY = 'ihomy:games:freecell:best:v1'
const STATE_KEY = 'ihomy:games:freecell:state:v1'
const MAX_HISTORY = 100
const SUIT_SYMBOL = { S: '♠', H: '♥', D: '♦', C: '♣' }
const DOUBLE_TAP_MS = 320

const { t } = useI18n()

const feltEl = ref(null)
const feltSize = ref({ w: 0, h: 0 })
const game = ref(deal())
const elapsed = ref(0)
const best = ref(0)
const rewardMsg = ref('')
const selected = ref(null)
const history = ref([])
const hint = ref(null)
const hintNote = ref('')

const isWon = computed(() => game.value.status === STATUS.WON)
const isLost = computed(() => game.value.status === STATUS.LOST)
const foundationTop = (suit) => {
  const pile = game.value.foundations[suit]
  return pile && pile.length ? pile[pile.length - 1] : null
}

const cardClass = (card) => ({ red: cardColor(card) === 'red' })
const isSelected = (pile, index) =>
  !!selected.value && selected.value.type === 'tableau' && selected.value.pile === pile && index >= selected.value.index

const isCardHint = (pile, index) => {
  const from = hint.value && hint.value.from
  return !!from && from.type === 'tableau' && from.pile === pile && index >= from.index
}
const isFreeHint = (cell) => {
  const from = hint.value && hint.value.from
  return !!from && from.type === 'free' && from.cell === cell
}
const isCellHint = (cell) => {
  const to = hint.value && hint.value.to
  return !!to && to.type === 'free' && to.cell === cell
}
const isPileHint = (pile) => {
  const to = hint.value && hint.value.to
  return !!to && to.type === 'tableau' && to.pile === pile
}
const isFoundationHint = (suit) => {
  const to = hint.value && hint.value.to
  return !!to && to.type === 'foundation' && to.suit === suit
}

// ---------- 一屏布局:八列,顶部自由格 + 花色堆占一行 ----------
const gap = computed(() => (feltSize.value.w > 0 && feltSize.value.w < 560 ? 4 : 6))
const layout = computed(() =>
  fitCardWidth({
    availW: feltSize.value.w,
    availH: feltSize.value.h,
    columns: COLUMNS,
    gap: gap.value,
    topUnits: CARD_RATIO,
    rowGap: 10,
    piles: game.value.tableau,
  }),
)
const feltStyle = computed(() => ({
  '--sol-card-w': `${layout.value.cardW}px`,
  '--sol-cols': COLUMNS,
  '--sol-gap': `${gap.value}px`,
}))
const margins = computed(() =>
  game.value.tableau.map((pile) => pileSteps(pile, layout.value.units).map((step) => `-${((CARD_RATIO - step) * 100).toFixed(2)}%`)),
)

let resizeObserver = null
const measureFelt = () => {
  const el = feltEl.value
  if (!el) return
  const cs = getComputedStyle(el)
  const w = el.clientWidth - (parseFloat(cs.paddingLeft) || 0) - (parseFloat(cs.paddingRight) || 0)
  const h = el.clientHeight - (parseFloat(cs.paddingTop) || 0) - (parseFloat(cs.paddingBottom) || 0)
  if (w > 0 && h > 0) feltSize.value = { w, h }
}
const observeFelt = () => {
  if (!feltEl.value || typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => measureFelt())
  resizeObserver.observe(feltEl.value)
}

// ---------- 计时与存档 ----------
let timer = null
const startTimer = () => {
  if (timer) return
  timer = setInterval(() => {
    elapsed.value++
    if (elapsed.value % 5 === 0) saveState()
  }, 1000)
}
const stopTimer = () => {
  if (!timer) return
  clearInterval(timer)
  timer = null
}

const loadBest = () => {
  try {
    const n = Number(localStorage.getItem(BEST_KEY))
    best.value = Number.isFinite(n) && n > 0 ? Math.floor(n) : 0
  } catch (e) {
    best.value = 0
  }
}
const saveBest = () => {
  try {
    localStorage.setItem(BEST_KEY, String(best.value))
  } catch (e) {
    // 隐私模式忽略
  }
}
const saveState = () => {
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify({ game: game.value, elapsed: elapsed.value }))
  } catch (e) {
    // 忽略
  }
}
const clearState = () => {
  try {
    localStorage.removeItem(STATE_KEY)
  } catch (e) {
    // 忽略
  }
}
const loadState = () => {
  try {
    const raw = JSON.parse(localStorage.getItem(STATE_KEY) || 'null')
    if (!raw || !isValidState(raw.game) || !Number.isInteger(raw.elapsed) || raw.elapsed < 0) return false
    if (raw.game.status !== STATUS.PLAYING) return false
    game.value = raw.game
    elapsed.value = raw.elapsed
    return true
  } catch (e) {
    return false
  }
}

// ---------- 提示 ----------
let hintTimer = null
const clearHint = () => {
  if (hintTimer) {
    clearTimeout(hintTimer)
    hintTimer = null
  }
  hint.value = null
  hintNote.value = ''
}
const showHint = () => {
  if (game.value.status !== STATUS.PLAYING) return
  clearHint()
  const next = findHint(game.value)
  hint.value = next
  if (!next) hintNote.value = t('games.cards.hintNone')
  hintTimer = setTimeout(() => {
    hint.value = null
    hintNote.value = ''
    hintTimer = null
  }, 3200)
}

// ---------- 游戏流程 ----------
const startNew = () => {
  stopTimer()
  clearHint()
  game.value = deal()
  history.value = []
  selected.value = null
  elapsed.value = 0
  rewardMsg.value = ''
  clearState()
  startTimer()
  saveState()
}

const grantReward = async () => {
  try {
    const res = await gameApi.freecellReward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.rewarded', { n: res.awarded })}（${t('points.points')} ${res.balance}）`
        : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
}

const finishWin = () => {
  stopTimer()
  if (!best.value || game.value.moves < best.value) {
    best.value = game.value.moves
    saveBest()
  }
  clearState()
  grantReward()
}

const finishLose = () => {
  stopTimer()
  clearHint()
  clearState()
  selected.value = null
}

const commit = (next) => {
  if (!next || next === game.value) return false
  history.value.push(cloneState(game.value))
  if (history.value.length > MAX_HISTORY) history.value.shift()
  game.value = next
  clearHint()
  if (game.value.status === STATUS.WON) finishWin()
  else if (game.value.status === STATUS.LOST) finishLose()
  else saveState()
  return true
}

const undo = () => {
  if (game.value.status === STATUS.WON || !history.value.length) return
  clearHint()
  game.value = history.value.pop()
  selected.value = null
  startTimer()
  saveState()
}

const autoFinishAll = () => {
  if (game.value.status !== STATUS.PLAYING) return
  selected.value = null
  commit(autoFinish(game.value))
}

// ---------- 拖动 ----------
const { drag, startDrag, shouldIgnoreClick, cancelDrag } = useCardDrag({
  getCards: (from) => sourceCards(game.value, from),
  onDrop: (from, to) => {
    if (!to || game.value.status !== STATUS.PLAYING) return
    selected.value = null
    if (to.type === 'foundation') commit(move(game.value, from, { type: 'foundation', suit: to.suit }))
    else if (to.type === 'free') commit(move(game.value, from, { type: 'free', cell: to.cell }))
    else if (to.type === 'tableau') commit(move(game.value, from, { type: 'tableau', pile: to.pile }))
  },
})

// ---------- 点击与双击 ----------
let lastTapKey = ''
let lastTapAt = 0
const isDoubleTap = (key) => {
  const now = Date.now()
  const doubled = key === lastTapKey && now - lastTapAt < DOUBLE_TAP_MS
  lastTapKey = doubled ? '' : key
  lastTapAt = now
  return doubled
}

const onFreeClick = (cell) => {
  if (shouldIgnoreClick()) return
  if (game.value.status !== STATUS.PLAYING) return
  if (selected.value) {
    const from = selected.value
    selected.value = null
    commit(move(game.value, from, { type: 'free', cell }))
    return
  }
  if (game.value.freeCells[cell]) selected.value = { type: 'free', cell }
}

const onFoundationClick = (suit) => {
  if (shouldIgnoreClick()) return
  if (game.value.status !== STATUS.PLAYING) return
  if (selected.value) {
    const from = selected.value
    selected.value = null
    commit(move(game.value, from, { type: 'foundation', suit }))
    return
  }
  if (foundationTop(suit)) selected.value = { type: 'foundation', suit }
}

const onCardPointerDown = (pile, index, card, event) => {
  if (game.value.status !== STATUS.PLAYING || !card.faceUp) return
  startDrag({ type: 'tableau', pile, index }, event)
}

const onCardClick = (pile, index) => {
  if (shouldIgnoreClick()) return
  if (game.value.status !== STATUS.PLAYING) return
  if (isDoubleTap(`f${pile}-${index}`)) {
    selected.value = null
    commit(autoMove(game.value, { type: 'tableau', pile, index }))
    return
  }
  const current = selected.value
  if (current) {
    if (current.type === 'tableau' && current.pile === pile && current.index === index) {
      selected.value = null
      return
    }
    if (commit(move(game.value, current, { type: 'tableau', pile }))) {
      selected.value = null
      return
    }
  }
  selected.value = { type: 'tableau', pile, index }
}

const onPileClick = (pile) => {
  if (shouldIgnoreClick()) return
  if (!selected.value || game.value.status !== STATUS.PLAYING) return
  const from = selected.value
  selected.value = null
  commit(move(game.value, from, { type: 'tableau', pile }))
}

// 切回前台恢复计时
let hiddenAt = 0
const onVisibility = () => {
  if (document.hidden) {
    hiddenAt = Date.now()
    return
  }
  if (!hiddenAt) return
  const missed = Math.floor((Date.now() - hiddenAt) / 1000)
  hiddenAt = 0
  if (missed > 0 && game.value.status === STATUS.PLAYING) elapsed.value += missed
}
const onResize = () => measureFelt()

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('resize', onResize)
  observeFelt()
  loadBest()
  if (!loadState()) startNew()
  else startTimer()
  measureFelt()
  setTimeout(measureFelt, 320)
})

onBeforeUnmount(() => {
  stopTimer()
  clearHint()
  cancelDrag()
  if (game.value.status === STATUS.PLAYING) saveState()
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('resize', onResize)
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>
