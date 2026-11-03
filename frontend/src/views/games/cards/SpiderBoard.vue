<template>
  <div class="sol-card card game-surface" @contextmenu.prevent @dblclick.prevent>
    <div class="sol-head">
      <div class="sol-title">{{ $t('games.spider.title') }}</div>
      <div class="sol-stats">
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.spider.score') }}</span>
          <b class="sol-stat-value">{{ game.score }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.spider.best') }}</span>
          <b class="sol-stat-value">{{ best }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.spider.moves') }}</span>
          <b class="sol-stat-value">{{ game.moves }}</b>
        </div>
        <div class="sol-stat">
          <span class="sol-stat-label">{{ $t('games.spider.time') }}</span>
          <b class="sol-stat-value">{{ formatClock(elapsed) }}</b>
        </div>
      </div>
    </div>

    <div class="sol-actions">
      <el-select v-model="difficulty" size="small" class="sol-draw">
        <el-option v-for="d in DIFFICULTIES" :key="d" :value="d" :label="difficultyLabel(d)" />
      </el-select>
      <el-button size="small" round :disabled="!history.length" @click="undo">{{ $t('games.spider.undo') }}</el-button>
      <el-button type="primary" size="small" round @click="startNew">{{ $t('games.spider.newGame') }}</el-button>
    </div>

    <div ref="feltEl" class="sol-felt" :style="feltStyle">
      <div class="sp-top">
        <div class="sp-completed">
          <span>{{ $t('games.spider.completed') }}</span>
          <b>{{ game.completed }}</b>
          <span>/ {{ COMPLETE_TARGET }}</span>
        </div>
        <div class="sp-stock-wrap">
          <span class="sp-deal-hint">{{ dealHint }}</span>
          <button
            v-if="game.stock.length"
            type="button"
            class="sol-stock sp-stock"
            :disabled="!canDealNow"
            :aria-label="$t('games.spider.deal')"
            @click="dealRow"
          >
            <span class="sp-stock-count">{{ dealsLeft }}</span>
          </button>
          <div v-else class="sp-stock-empty"></div>
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

    <div class="sol-tip">{{ $t('games.spider.tip') }}</div>

    <Transition name="game-overlay">
      <div v-if="isWon" class="sol-overlay game-overlay">
        <div class="sol-overlay-card game-overlay-card">
          <div class="sol-overlay-icon">🏆</div>
          <div class="sol-overlay-title">{{ $t('games.spider.win') }}</div>
          <div class="sol-overlay-meta">{{ $t('games.spider.winMeta', { score: game.score, moves: game.moves }) }}</div>
          <div v-if="rewardMsg" class="sol-overlay-reward">{{ rewardMsg }}</div>
          <el-button type="primary" size="small" round @click="startNew">{{ $t('games.spider.newGame') }}</el-button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
// 蜘蛛纸牌:规则/发牌/叠牌/自动收组全在 utils/games/spider.js(纯函数,单测覆盖)。
// 本组件只做点选移动、双击自动移动、撤销快照、计时与最高分;牌宽与叠牌步进来自 cardLayout.js。
// 难度 1/2/4 色分别记录最高分与存档,通关按积分规则发奖励(后端 POST /game/spider/reward)。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { gameApi } from '@/api'
import { formatClock } from '@/utils/games/gameClock'
import { CARD_RATIO, fitCardWidth, pileSteps } from '@/utils/games/cardLayout'
import {
  COLUMNS,
  COMPLETE_TARGET,
  DIFFICULTIES,
  STATUS,
  autoMove,
  canDealStock,
  cloneState,
  deal,
  dealStock,
  isValidState,
  move,
  sequenceAt,
} from '@/utils/games/spider'

const BEST_KEY = 'ihomy:games:spider:best:v1'
const STATE_KEY = 'ihomy:games:spider:state:v1'
const MAX_HISTORY = 80
const SUIT_SYMBOL = { S: '♠', H: '♥', D: '♦', C: '♣' }
const DOUBLE_TAP_MS = 320

const { t } = useI18n()

const feltEl = ref(null)
const feltSize = ref({ w: 0, h: 0 })
const difficulty = ref(1)
const game = ref(deal(1))
const elapsed = ref(0)
const bestMap = ref({})
const rewardMsg = ref('')
const selected = ref(null)
const history = ref([])

const isWon = computed(() => game.value.status === STATUS.WON)
const best = computed(() => bestMap.value[game.value.difficulty] || 0)
const canDealNow = computed(() => canDealStock(game.value))
const dealsLeft = computed(() => Math.ceil(game.value.stock.length / COLUMNS))
const dealHint = computed(() => {
  if (!game.value.stock.length) return ''
  return canDealNow.value ? t('games.spider.dealsLeft', { n: dealsLeft.value }) : t('games.spider.cannotDeal')
})

const difficultyLabel = (d) =>
  ({ 1: t('games.spider.oneSuit'), 2: t('games.spider.twoSuits'), 4: t('games.spider.fourSuits') })[d] || String(d)

const rankLabel = (rank) => ({ 1: 'A', 11: 'J', 12: 'Q', 13: 'K' })[rank] || String(rank)
const cardClass = (card) => ({ red: card.faceUp && (card.suit === 'H' || card.suit === 'D'), back: !card.faceUp })
const isSelected = (pile, index) => !!selected.value && selected.value.pile === pile && index >= selected.value.index

// ---------- 一屏布局:十列,顶部牌堆栏固定 44px ----------
const gap = computed(() => (feltSize.value.w > 0 && feltSize.value.w < 560 ? 4 : 6))
const layout = computed(() =>
  fitCardWidth({
    availW: feltSize.value.w,
    availH: feltSize.value.h,
    columns: COLUMNS,
    gap: gap.value,
    topUnits: 0,
    fixedTop: 44,
    rowGap: 10,
    piles: game.value.tableau,
    maxWidth: 96,
    minWidth: 44,
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
    const raw = JSON.parse(localStorage.getItem(BEST_KEY) || 'null')
    bestMap.value = raw && typeof raw === 'object' ? raw : {}
  } catch (e) {
    bestMap.value = {}
  }
}
const saveBest = () => {
  try {
    localStorage.setItem(BEST_KEY, JSON.stringify(bestMap.value))
  } catch (e) {
    // 隐私模式忽略
  }
}
const saveState = () => {
  try {
    const payload = { game: game.value, elapsed: elapsed.value }
    localStorage.setItem(STATE_KEY, JSON.stringify(payload))
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
    difficulty.value = raw.game.difficulty
    return true
  } catch (e) {
    return false
  }
}

// ---------- 游戏流程 ----------
const startNew = () => {
  stopTimer()
  game.value = deal(difficulty.value)
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
    const res = await gameApi.spiderReward()
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
  const key = game.value.difficulty
  if (game.value.score > (bestMap.value[key] || 0)) {
    bestMap.value = { ...bestMap.value, [key]: game.value.score }
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

const undo = () => {
  if (isWon.value || !history.value.length) return
  game.value = history.value.pop()
  selected.value = null
  saveState()
}

const dealRow = () => {
  if (!canDealNow.value) return
  selected.value = null
  commit(dealStock(game.value))
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

const movable = (pile, index) => !!sequenceAt(game.value, pile, index)

const onCardClick = (pile, index, card) => {
  if (game.value.status !== STATUS.PLAYING || !card.faceUp) return
  if (isDoubleTap(`s${pile}-${index}`)) {
    selected.value = null
    commit(autoMove(game.value, { type: 'tableau', pile, index }))
    return
  }
  const current = selected.value
  if (current) {
    if (current.pile === pile && current.index === index) {
      selected.value = null
      return
    }
    if (commit(move(game.value, current, { type: 'tableau', pile }))) {
      selected.value = null
      return
    }
  }
  selected.value = movable(pile, index) ? { type: 'tableau', pile, index } : null
}

const onPileClick = (pile) => {
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

watch(difficulty, (value) => {
  if (game.value.difficulty === value) return
  startNew()
})

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
  if (!isWon.value) saveState()
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('resize', onResize)
  resizeObserver?.disconnect()
  resizeObserver = null
})
</script>
