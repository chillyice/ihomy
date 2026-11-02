<template>
  <div class="page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <div class="game-top-actions">
        <el-button size="small" round @click="router.push('/games')">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('games.back') }}
        </el-button>
        <el-button size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          {{ isFullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen') }}
        </el-button>
      </div>
    </PageToolbar>

    <div ref="gameCardEl" class="sol-card card game-surface" @contextmenu.prevent @dblclick.prevent>
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

      <div class="sol-felt">
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
  </div>
</template>

<script setup>
// 纸牌接龙(Klondike):发牌/翻牌/叠牌规则/自动收牌全在 utils/games/solitaire.js(纯函数,单测覆盖)。
// 本页负责点选移动、双击自动收纳、撤销快照、计时与最高分;通关按积分规则发奖励。
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, FullScreen } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { formatClock } from '@/utils/games/gameClock'
import { STATUS, SUITS, autoFinish, autoMove, cardColor, cloneState, deal, drawStock, isValidState, move } from '@/utils/games/solitaire'

const BEST_KEY = 'ihomy:games:solitaire:best:v1'
const STATE_KEY = 'ihomy:games:solitaire:state:v1'
const MAX_HISTORY = 50
const SUIT_SYMBOL = { S: '♠', H: '♥', D: '♦', C: '♣' }
const DOUBLE_TAP_MS = 320

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const gameCardEl = ref(null)
const isFullscreen = ref(false)
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

// ---------- 全屏 ----------
const toggleFullscreen = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen?.()
    else await gameCardEl.value?.requestFullscreen?.()
  } catch (e) {
    // 浏览器拒绝(iOS)时保持窗口态
  }
}
const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

watch(drawCount, (value) => {
  if (game.value.drawCount === value) return
  game.value = { ...game.value, drawCount: value }
  selected.value = null
  saveState()
})

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('visibilitychange', onVisibility)
  sunLight?.suspendEffects()
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
})

onBeforeUnmount(() => {
  stopTimer()
  if (!isWon.value) saveState()
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('visibilitychange', onVisibility)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.sol-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 18px 16px 16px;
  border-radius: 14px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
}
.sol-card:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  justify-content: flex-start;
  overflow-y: auto;
  padding-top: 24px;
}

.sol-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(100%, 900px);
  gap: 12px;
  flex-wrap: wrap;
}
.sol-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 1px;
}
.sol-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.sol-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 62px;
  padding: 5px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.05);
}
.sol-stat-label {
  font-size: 10px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
  white-space: nowrap;
}
.sol-stat-value {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}
.sol-actions {
  display: flex;
  gap: 8px;
  width: min(100%, 900px);
  justify-content: flex-end;
  flex-wrap: wrap;
}
.sol-draw {
  width: 96px;
  margin-right: auto;
}

.sol-felt {
  width: min(100%, 900px);
  padding: 12px;
  border-radius: 12px;
  background: radial-gradient(120% 140% at 50% 0%, #2f7a58 0%, #1f5c41 55%, #174a34 100%);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
  --sol-gap: clamp(4px, 0.9vw, 9px);
}
.sol-row {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: var(--sol-gap);
}
.sol-top {
  margin-bottom: clamp(8px, 1.6vw, 16px);
}
.sol-slot {
  min-width: 0;
}
.sol-tableau {
  align-items: start;
}
.sol-pile {
  min-width: 0;
  min-height: 40px;
  cursor: pointer;
}

.sol-cardface,
.sol-empty,
.sol-stock {
  position: relative;
  width: 100%;
  aspect-ratio: 5 / 7;
  border-radius: clamp(4px, 0.7vw, 7px);
}
.sol-cardface {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #fdfcf8;
  color: #2a2a2a;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.28);
  cursor: pointer;
  font-weight: 700;
  touch-action: manipulation;
}
.sol-cardface.red {
  color: #c0392b;
}
.sol-cardface.back {
  background: repeating-linear-gradient(45deg, #2c5f9e 0 6px, #244f85 6px 12px), #244f85;
  box-shadow:
    inset 0 0 0 3px #f2f2f2,
    0 2px 6px rgba(0, 0, 0, 0.28);
}
.sol-cardface.selected {
  outline: 2px solid #ffd166;
  outline-offset: -2px;
  box-shadow:
    0 0 0 2px rgba(255, 209, 102, 0.5),
    0 4px 10px rgba(0, 0, 0, 0.32);
}
.sol-pile .sol-cardface + .sol-cardface {
  margin-top: -62%;
}
.sol-corner {
  position: absolute;
  top: 5%;
  left: 8%;
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1;
  font-size: clamp(8px, 1.7vw, 15px);
}
.sol-corner i {
  font-style: normal;
  font-size: 0.9em;
}
.sol-center {
  font-size: clamp(14px, 3.4vw, 34px);
  opacity: 0.9;
  line-height: 1;
}
.sol-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed rgba(255, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.45);
  font-size: clamp(14px, 3vw, 28px);
  box-sizing: border-box;
}
.sol-found {
  cursor: pointer;
}
.sol-stock {
  cursor: pointer;
  background: repeating-linear-gradient(45deg, #2c5f9e 0 6px, #244f85 6px 12px), #244f85;
  box-shadow:
    inset 0 0 0 3px #f2f2f2,
    0 2px 6px rgba(0, 0, 0, 0.28);
}
.sol-stock-count {
  position: absolute;
  right: 5%;
  bottom: 4%;
  padding: 1px 5px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: clamp(8px, 1.6vw, 12px);
  font-weight: 700;
}
.sol-recycle {
  cursor: pointer;
  border-style: solid;
}
.sol-spacer {
  pointer-events: none;
}
.sol-tip {
  max-width: 900px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #8a8a8a);
  text-align: center;
}

.sol-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(250, 248, 239, 0.72);
  backdrop-filter: blur(2px);
  z-index: 5;
}
.sol-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.sol-overlay-icon {
  font-size: 38px;
}
.sol-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.sol-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.sol-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

html.dark .sol-stat {
  background: rgba(255, 255, 255, 0.08);
}
html.dark .sol-cardface {
  background: #f3f1ea;
}
html.dark .sol-cardface.back {
  background: repeating-linear-gradient(45deg, #24405f 0 6px, #1b3149 6px 12px), #1b3149;
}
html.dark .sol-overlay {
  background: rgba(20, 20, 20, 0.72);
}

@media (max-width: 560px) {
  .sol-card {
    padding: 14px 10px;
  }
  .sol-title {
    font-size: 21px;
  }
  .sol-stat {
    min-width: 54px;
    padding: 4px 7px;
  }
  .sol-felt {
    padding: 7px;
  }
}
</style>
