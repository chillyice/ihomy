<template>
  <div class="sol-card card game-surface" @contextmenu.prevent @dblclick.prevent>
    <div class="sol-head">
      <div class="sol-title">{{ $t('games.solitaire.title') }}</div>
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
      <el-select v-model="drawCount" size="small" class="sol-draw">
        <el-option :value="1" :label="$t('games.solitaire.draw1')" />
        <el-option :value="3" :label="$t('games.solitaire.draw3')" />
      </el-select>
      <el-button size="small" round :disabled="!history.length" @click="undo">{{ $t('games.solitaire.undo') }}</el-button>
      <el-button size="small" round @click="autoFinishAll">{{ $t('games.solitaire.autoFinish') }}</el-button>
      <el-button type="primary" size="small" round @click="startNew">{{ $t('games.solitaire.newGame') }}</el-button>
    </div>

    <div ref="feltEl" class="sol-felt" :style="feltStyle">
      <div class="sol-row sol-top">
        <div class="sol-slot">
          <div v-if="game.stock.length" class="sol-stock" :aria-label="$t('games.solitaire.stock')" @click="draw">
            <span class="sol-stock-count">{{ game.stock.length }}</span>
          </div>
          <div v-else class="sol-empty sol-recycle" :aria-label="$t('games.solitaire.recycle')" @click="draw">↻</div>
        </div>
        <div class="sol-slot">
          <div
            v-if="wasteTop"
            class="sol-cardface"
            :class="[cardClass(wasteTop), { selected: selected && selected.type === 'waste' }]"
            @click="onWasteClick"
          >
            <span class="sol-corner"
              >{{ rankLabel(wasteTop.rank) }}<i>{{ SUIT_SYMBOL[wasteTop.suit] }}</i></span
            >
            <span class="sol-center">{{ SUIT_SYMBOL[wasteTop.suit] }}</span>
          </div>
          <div v-else class="sol-empty"></div>
        </div>
        <div class="sol-slot sol-spacer"></div>
        <div v-for="suit in SUITS" :key="suit" class="sol-slot">
          <div
            v-if="foundationTop(suit)"
            class="sol-cardface"
            :class="[cardClass(foundationTop(suit)), { selected: selected && selected.type === 'foundation' && selected.suit === suit }]"
            @click="onFoundationClick(suit)"
          >
            <span class="sol-corner"
              >{{ rankLabel(foundationTop(suit).rank) }}<i>{{ SUIT_SYMBOL[suit] }}</i></span
            >
            <span class="sol-center">{{ SUIT_SYMBOL[suit] }}</span>
          </div>
          <div v-else class="sol-empty sol-found" @click="onFoundationClick(suit)">{{ SUIT_SYMBOL[suit] }}</div>
        </div>
      </div>

      <div class="sol-row sol-tableau">
        <div v-for="(pile, pi) in game.tableau" :key="pi" class="sol-pile" @click="onPileClick(pi)">
          <div v-if="!pile.length" class="sol-empty"></div>
          <div
            v-for="(card, ci) in pile"
            :key="card.id"
            class="sol-cardface"
            :class="[cardClass(card), { selected: isSelected(pi, ci) }]"
            :style="ci ? { marginTop: margins[pi][ci - 1] } : null"
            @click.stop="onCardClick(pi, ci, card)"
          >
            <template v-if="card.faceUp">
              <span class="sol-corner"
                >{{ rankLabel(card.rank) }}<i>{{ SUIT_SYMBOL[card.suit] }}</i></span
              >
              <span class="sol-center">{{ SUIT_SYMBOL[card.suit] }}</span>
            </template>
          </div>
        </div>
      </div>
    </div>

    <div class="sol-tip">{{ $t('games.solitaire.tip') }}</div>

    <div v-if="isWon" class="sol-overlay">
      <div class="sol-overlay-card">
        <div class="sol-overlay-icon">🏆</div>
        <div class="sol-overlay-title">{{ $t('games.solitaire.win') }}</div>
        <div class="sol-overlay-meta">{{ $t('games.solitaire.winMeta', { score: game.score, moves: game.moves }) }}</div>
        <div v-if="rewardMsg" class="sol-overlay-reward">{{ rewardMsg }}</div>
        <el-button type="primary" size="small" round @click="startNew">{{ $t('games.solitaire.newGame') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 纸牌接龙(Klondike):发牌/翻牌/叠牌规则/自动收牌全在 utils/games/solitaire.js(纯函数,单测覆盖)。
// 本组件负责点选移动、双击自动收纳、撤销快照、计时与最高分;牌宽与叠牌步进来自 utils/games/cardLayout.js,
// 让牌桌固定在一屏内,叠牌比原先更紧。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { gameApi } from '@/api'
import { formatClock } from '@/utils/games/gameClock'
import { CARD_RATIO, fitCardWidth, pileSteps } from '@/utils/games/cardLayout'
import { STATUS, SUITS, autoFinish, autoMove, cardColor, cloneState, deal, drawStock, isValidState, move } from '@/utils/games/solitaire'

const BEST_KEY = 'ihomy:games:solitaire:best:v1'
const STATE_KEY = 'ihomy:games:solitaire:state:v1'
const MAX_HISTORY = 50
const SUIT_SYMBOL = { S: '♠', H: '♥', D: '♦', C: '♣' }
const DOUBLE_TAP_MS = 320
const COLUMNS = 7

const { t } = useI18n()

const feltEl = ref(null)
const feltSize = ref({ w: 0, h: 0 })
const game = ref(deal(1))
const elapsed = ref(0)
const best = ref(0)
const rewardMsg = ref('')
const selected = ref(null)
const history = ref([])
const drawCount = ref(1)

const isWon = computed(() => game.value.status === STATUS.WON)
const wasteTop = computed(() => game.value.waste[game.value.waste.length - 1] || null)
const foundationTop = (suit) => {
  const pile = game.value.foundations[suit]
  return pile && pile.length ? pile[pile.length - 1] : null
}

const rankLabel = (rank) => ({ 1: 'A', 11: 'J', 12: 'Q', 13: 'K' })[rank] || String(rank)
const cardClass = (card) => ({ red: card.faceUp && cardColor(card) === 'red', back: !card.faceUp })
const isSelected = (pile, index) =>
  !!selected.value && selected.value.type === 'tableau' && selected.value.pile === pile && index >= selected.value.index

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
    if (raw.game.status === STATUS.WON) return false
    game.value = raw.game
    elapsed.value = raw.elapsed
    drawCount.value = raw.game.drawCount
    return true
  } catch (e) {
    return false
  }
}

// ---------- 游戏流程 ----------
const startNew = () => {
  stopTimer()
  game.value = deal(drawCount.value)
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
  grantReward()
}

const commit = (next) => {
  if (!next || next === game.value) return false
  history.value.push(cloneState(game.value))
  if (history.value.length > MAX_HISTORY) history.value.shift()
  game.value = next
  if (game.value.status === STATUS.WON) finishWin()
  else saveState()
  return true
}

const draw = () => {
  if (game.value.status !== STATUS.PLAYING) return
  selected.value = null
  commit(drawStock(game.value))
}

const undo = () => {
  if (isWon.value || !history.value.length) return
  game.value = history.value.pop()
  selected.value = null
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
  if (game.value.status !== STATUS.PLAYING) return
  if (isDoubleTap('waste')) {
    selected.value = null
    commit(autoMove(game.value, { type: 'waste' }))
    return
  }
  selected.value = selected.value?.type === 'waste' ? null : wasteTop.value ? { type: 'waste' } : null
}

const onFoundationClick = (suit) => {
  if (game.value.status !== STATUS.PLAYING) return
  if (selected.value) {
    const next = move(game.value, selected.value, { type: 'foundation', suit })
    selected.value = null
    commit(next)
    return
  }
  if (foundationTop(suit)) selected.value = { type: 'foundation', suit }
}

const onCardClick = (pile, index, card) => {
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
  saveState()
})

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('resize', onResize)
  observeFelt()
  loadBest()
  if (!loadState()) {
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
  if (!isWon.value) saveState()
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('resize', onResize)
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>
