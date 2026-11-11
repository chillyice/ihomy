<template>
  <div class="sol-card card game-surface" @contextmenu.prevent @dblclick.prevent>
    <div class="sol-head">
      <div class="sol-title">{{ $t('games.solitaire.title') }}{{ play.active ? ' · ' + $t('games.room.together') : '' }}</div>
      <div class="sol-stats">
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.solitaire.score') }}</span>
          <b class="sol-stat-value">{{ game.score }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.solitaire.best') }}</span>
          <b class="sol-stat-value">{{ best }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.solitaire.moves') }}</span>
          <b class="sol-stat-value">{{ game.moves }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.solitaire.time') }}</span>
          <b class="sol-stat-value">{{ formatClock(elapsed) }}</b>
        </div>
      </div>
    </div>

    <div class="sol-actions">
      <el-select v-model="drawCount" size="small" class="sol-draw" :teleported="!gameFullscreen" :disabled="play.active">
        <el-option :value="1" :label="$t('games.solitaire.draw1')" />
        <el-option :value="3" :label="$t('games.solitaire.draw3')" />
      </el-select>
      <el-button size="small" round @click="showHint">{{ $t('games.solitaire.hint') }}</el-button>
      <el-button v-if="!play.active" size="small" round :disabled="!history.length" @click="undo">{{
        $t('games.solitaire.undo')
      }}</el-button>
      <el-button size="small" round @click="autoFinishAll">{{ $t('games.solitaire.autoFinish') }}</el-button>
      <el-button type="primary" size="small" round @click="startNew">{{ $t('games.solitaire.newGame') }}</el-button>
    </div>

    <GameRoomBar
      :room="play.room"
      :is-host="play.isHost"
      :score="game.score"
      :reported="play.reported"
      :ended="play.ended"
      :result="play.spec?.result"
      :my-id="play.myId"
      @submit="submitRoomScore"
      @leave="play.leave"
    />

    <div ref="feltEl" class="sol-felt" :style="feltStyle">
      <div class="sol-row sol-top">
        <div class="sol-slot">
          <div
            v-if="game.stock.length"
            class="sol-stock"
            :class="{ 'hint-source': hint && hint.kind === 'draw' }"
            :aria-label="$t('games.solitaire.stock')"
            @click="draw"
          >
            <span class="sol-stock-count">{{ game.stock.length }}</span>
          </div>
          <div
            v-else
            class="sol-empty sol-recycle"
            :class="{ 'hint-source': hint && hint.kind === 'draw' }"
            :aria-label="$t('games.solitaire.recycle')"
            @click="draw"
          >
            ↻
          </div>
        </div>
        <div class="sol-slot sol-waste-slot">
          <template v-if="wasteCards.length">
            <div
              v-for="(card, idx) in wasteCards"
              :key="card.id"
              class="sol-cardface sol-waste-card"
              :class="[
                cardClass(card),
                {
                  'is-top': idx === wasteCards.length - 1,
                  selected: idx === wasteCards.length - 1 && selected && selected.type === 'waste',
                  'hint-source': idx === wasteCards.length - 1 && hint && hint.from && hint.from.type === 'waste',
                },
              ]"
              :style="{ '--fan-index': idx, zIndex: idx + 1 }"
              @pointerdown="idx === wasteCards.length - 1 && startDrag({ type: 'waste' }, $event)"
              @click.stop="idx === wasteCards.length - 1 && onWasteClick()"
            >
              <PlayingCard :card="card" />
            </div>
          </template>
          <div v-else class="sol-empty"></div>
        </div>
        <div class="sol-slot sol-spacer"></div>
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
            @click.stop="onCardClick(pi, ci, card)"
          >
            <PlayingCard v-if="card.faceUp" :card="card" />
          </div>
        </div>
      </div>
    </div>

    <div class="sol-tip">{{ hintNote || $t('games.solitaire.tip') }}</div>

    <Transition name="game-overlay">
      <div v-if="isWon || isLost" class="sol-overlay game-overlay">
        <div class="sol-overlay-card game-overlay-card">
          <div class="sol-overlay-icon">{{ isWon ? '🏆' : '🚫' }}</div>
          <div class="sol-overlay-title">{{ isWon ? $t('games.solitaire.win') : $t('games.solitaire.lose') }}</div>
          <div class="sol-overlay-meta">
            {{
              isWon
                ? $t('games.solitaire.winMeta', { score: game.score, moves: game.moves })
                : $t('games.solitaire.loseMeta', { score: game.score, moves: game.moves })
            }}
          </div>
          <div v-if="rewardMsg" class="sol-overlay-reward">{{ rewardMsg }}</div>
          <div class="sol-overlay-actions">
            <el-button v-if="isLost && history.length" size="small" round @click="undo">{{ $t('games.solitaire.undo') }}</el-button>
            <el-button type="primary" size="small" round @click="startNew">{{ $t('games.solitaire.newGame') }}</el-button>
          </div>
        </div>
      </div>
    </Transition>

    <CardDragGhost :drag="drag" />
  </div>
</template>

<script setup>
// 纸牌接龙(Klondike):发牌/翻牌/叠牌规则/自动收牌/提示/无路可走判定全在 utils/games/solitaire.js(纯函数,单测覆盖)。
// 本组件负责点选与拖动移动、双击自动收纳、撤销快照、计时与最高分;牌宽与叠牌步进来自 utils/games/cardLayout.js,
// 拖拽落点由 useCardDrag 通过 [data-drop] 容器识别,牌面图案统一交给 PlayingCard 渲染。
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { gameApi } from '@/api'
import PlayingCard from '@/components/games/PlayingCard.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { useCardDrag } from '@/composables/useCardDrag'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { formatClock } from '@/utils/games/gameClock'
import { seededRng } from '@/utils/games/seededRng'
import { CARD_RATIO, fitCardWidth, pileSteps } from '@/utils/games/cardLayout'
import {
  STATUS,
  SUITS,
  autoFinish,
  autoMove,
  cardColor,
  cloneState,
  deal,
  drawStock,
  findHint,
  isValidState,
  move,
  sourceCards,
} from '@/utils/games/solitaire'
import CardDragGhost from './CardDragGhost.vue'

const BEST_KEY = 'ihomy:games:solitaire:best:v1'
const STATE_KEY = 'ihomy:games:solitaire:state:v1'
const MAX_HISTORY = 50
const SUIT_SYMBOL = { S: '♠', H: '♥', D: '♦', C: '♣' }
const DOUBLE_TAP_MS = 320
const COLUMNS = 7

const { t } = useI18n()
// 牌桌全屏时下拉要留在全屏元素内部(挂 body 会被全屏层遮住)
const gameFullscreen = inject('gameFullscreen', ref(false))

const feltEl = ref(null)
const feltSize = ref({ w: 0, h: 0 })
const play = useGameRoomPlay()
/** 房间模式的同种子发牌:同一 seed + 同 drawCount 得到同一副牌 */
let rng = null
const game = ref(deal(1))
const elapsed = ref(0)
const best = ref(0)
const rewardMsg = ref('')
const selected = ref(null)
const history = ref([])
const drawCount = ref(1)
const hint = ref(null)
const hintNote = ref('')

const isWon = computed(() => game.value.status === STATUS.WON)
const isLost = computed(() => game.value.status === STATUS.LOST)
const wasteTop = computed(() => game.value.waste[game.value.waste.length - 1] || null)
const wasteCards = computed(() => {
  const count = Math.min(game.value.drawCount, 3)
  return game.value.waste.slice(-count)
})
const foundationTop = (suit) => {
  const pile = game.value.foundations[suit]
  return pile && pile.length ? pile[pile.length - 1] : null
}

const cardClass = (card) => ({ red: card.faceUp && cardColor(card) === 'red', back: !card.faceUp })
const isSelected = (pile, index) =>
  !!selected.value && selected.value.type === 'tableau' && selected.value.pile === pile && index >= selected.value.index

const isCardHint = (pile, index) => {
  const from = hint.value && hint.value.from
  return !!from && from.type === 'tableau' && from.pile === pile && index >= from.index
}
const isPileHint = (pile) => {
  const to = hint.value && hint.value.to
  return !!to && to.type === 'tableau' && to.pile === pile
}
const isFoundationHint = (suit) => {
  const to = hint.value && hint.value.to
  return !!to && to.type === 'foundation' && to.suit === suit
}

// ---------- 一屏布局:牌宽随可用宽高收缩,叠牌步进按列长分配 ----------
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
  if (play.active) return
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify({ game: game.value, elapsed: elapsed.value }))
  } catch (e) {
    // 忽略
  }
}
const clearState = () => {
  if (play.active) return
  try {
    localStorage.removeItem(STATE_KEY)
  } catch (e) {
    // 忽略
  }
}
const loadState = () => {
  if (play.active) return false
  try {
    const raw = JSON.parse(localStorage.getItem(STATE_KEY) || 'null')
    if (!raw || !isValidState(raw.game) || !Number.isInteger(raw.elapsed) || raw.elapsed < 0) return false
    if (raw.game.status !== STATUS.PLAYING) return false
    game.value = raw.game
    elapsed.value = raw.elapsed
    drawCount.value = raw.game.drawCount
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
  rng = play.active ? seededRng(play.seed) : null
  game.value = deal(drawCount.value, rng || undefined)
  history.value = []
  selected.value = null
  elapsed.value = 0
  rewardMsg.value = ''
  clearState()
  startTimer()
  saveState()
}

const grantReward = async () => {
  if (play.active) return
  try {
    const res = await gameApi.solitaireReward()
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
  if (game.value.score > best.value) {
    best.value = game.value.score
    saveBest()
  }
  clearState()
  if (play.active) play.report(game.value.score)
  grantReward()
}

// 输局不写最高分,也不保留存档,避免重进后直接卡在死局
const finishLose = () => {
  stopTimer()
  clearHint()
  clearState()
  selected.value = null
  if (play.active) play.report(0)
}

const submitRoomScore = () => play.report(game.value.score)

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

const draw = () => {
  if (game.value.status !== STATUS.PLAYING) return
  if (shouldIgnoreClick()) return
  selected.value = null
  commit(drawStock(game.value))
}

const undo = () => {
  if (game.value.status === STATUS.WON || !history.value.length) return
  clearHint()
  game.value = history.value.pop()
  selected.value = null
  startTimer()
  if (game.value.score > best.value) {
    best.value = game.value.score
    saveBest()
  }
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

const onWasteClick = () => {
  if (shouldIgnoreClick()) return
  if (game.value.status !== STATUS.PLAYING) return
  if (isDoubleTap('waste')) {
    selected.value = null
    commit(autoMove(game.value, { type: 'waste' }))
    return
  }
  selected.value = selected.value?.type === 'waste' ? null : wasteTop.value ? { type: 'waste' } : null
}

const onFoundationClick = (suit) => {
  if (shouldIgnoreClick()) return
  if (game.value.status !== STATUS.PLAYING) return
  if (selected.value) {
    const next = move(game.value, selected.value, { type: 'foundation', suit })
    selected.value = null
    commit(next)
    return
  }
  if (foundationTop(suit)) selected.value = { type: 'foundation', suit }
}

const onCardPointerDown = (pile, index, card, event) => {
  if (game.value.status !== STATUS.PLAYING || !card.faceUp) return
  startDrag({ type: 'tableau', pile, index }, event)
}

const onCardClick = (pile, index, card) => {
  if (shouldIgnoreClick()) return
  if (game.value.status !== STATUS.PLAYING) return
  if (!card.faceUp) return
  if (isDoubleTap(`t${pile}-${index}`)) {
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

// 切回前台恢复计时(浏览器暂停定时器后仍按真实经过时间补齐)
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

watch(drawCount, (value) => {
  if (game.value.drawCount === value) return
  game.value = { ...game.value, drawCount: value }
  selected.value = null
  clearHint()
  saveState()
})

onMounted(async () => {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('resize', onResize)
  observeFelt()
  loadBest()
  if (play.active) {
    await play.load()
    startNew()
  } else if (!loadState()) {
    startNew()
  } else {
    if (game.value.score > best.value) {
      best.value = game.value.score
      saveBest()
    }
    startTimer()
  }
  // 首帧量一次(父级固定高度可能晚一帧才生效),再补量一次兜底
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
