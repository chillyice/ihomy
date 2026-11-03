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

    <div ref="gameCardEl" class="tt-card card game-surface" @contextmenu.prevent @dblclick.prevent>
      <div class="tt-head">
        <div class="tt-title">{{ $t('games.tetris.title') }}</div>
        <div class="tt-stats">
          <div class="tt-stat">
            <span class="tt-stat-label">{{ $t('games.tetris.score') }}</span>
            <b class="tt-stat-value">{{ game.score }}</b>
          </div>
          <div class="tt-stat">
            <span class="tt-stat-label">{{ $t('games.tetris.best') }}</span>
            <b class="tt-stat-value">{{ best }}</b>
          </div>
          <div class="tt-stat">
            <span class="tt-stat-label">{{ $t('games.tetris.lines') }}</span>
            <b class="tt-stat-value">{{ game.lines }}</b>
          </div>
          <div class="tt-stat">
            <span class="tt-stat-label">{{ $t('games.tetris.level') }}</span>
            <b class="tt-stat-value">{{ game.level }}</b>
          </div>
        </div>
      </div>

      <div class="tt-actions">
        <el-button size="small" round :disabled="game.status !== 'PLAYING'" @click="togglePause">
          {{ paused ? $t('games.tetris.resume') : $t('games.tetris.pause') }}
        </el-button>
        <el-button type="primary" size="small" round @click="startNew">{{ $t('games.tetris.newGame') }}</el-button>
      </div>

      <div class="tt-stage">
        <div class="tt-board-wrap">
          <div class="tt-board">
            <div
              v-for="(cell, i) in boardCells"
              :key="i"
              class="tt-cell"
              :class="cell ? ['tt-' + cell.type, { ghost: cell.ghost }] : ''"
            ></div>
          </div>
        </div>

        <div class="tt-side">
          <div class="tt-box">
            <span class="tt-box-label">{{ $t('games.tetris.hold') }}</span>
            <div class="tt-mini">
              <div v-for="(cell, i) in previewCells(game.hold)" :key="i" class="tt-mini-cell" :class="cell ? 'tt-' + cell : ''"></div>
            </div>
          </div>
          <div class="tt-box">
            <span class="tt-box-label">{{ $t('games.tetris.next') }}</span>
            <div v-for="(type, i) in game.queue.slice(0, 3)" :key="i" class="tt-mini">
              <div v-for="(cell, j) in previewCells(type)" :key="j" class="tt-mini-cell" :class="cell ? 'tt-' + cell : ''"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="tt-pad">
        <el-button class="tt-pad-btn" circle @click="act((s) => tryMove(s, -1, 0))">
          <el-icon><ArrowLeftBold /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn" circle @click="act((s) => rotate(s, 1))">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn" circle @click="act((s) => tryMove(s, 1, 0))">
          <el-icon><ArrowRightBold /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn" circle @click="act((s) => softDrop(s))">
          <el-icon><ArrowDownBold /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn tt-drop" @click="act((s) => hardDrop(s))">{{ $t('games.tetris.drop') }}</el-button>
        <el-button class="tt-pad-btn" @click="act((s) => holdPiece(s))">{{ $t('games.tetris.hold') }}</el-button>
      </div>

      <div class="tt-tip">{{ $t('games.tetris.tip') }}</div>

      <Transition name="game-overlay">
        <div v-if="paused || game.status === 'OVER'" class="tt-overlay game-overlay">
          <div class="tt-overlay-card game-overlay-card">
            <div class="tt-overlay-icon">{{ game.status === 'OVER' ? '💤' : '⏸' }}</div>
            <div class="tt-overlay-title">
              {{ game.status === 'OVER' ? $t('games.tetris.over') : $t('games.tetris.paused') }}
            </div>
            <div class="tt-overlay-meta">
              {{ $t('games.tetris.meta', { score: game.score, lines: game.lines }) }}
            </div>
            <div v-if="game.status === 'OVER' && rewardMsg" class="tt-overlay-reward">{{ rewardMsg }}</div>
            <el-button v-if="paused && game.status === 'PLAYING'" type="primary" size="small" round @click="togglePause">
              {{ $t('games.tetris.resume') }}
            </el-button>
            <el-button v-else type="primary" size="small" round @click="startNew">{{ $t('games.tetris.newGame') }}</el-button>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup>
// 俄罗斯方块:7-bag/旋转踢墙/消行计分/暂存全在 utils/games/tetris.js(纯函数,单测覆盖)。
// 本页负责重力节拍、键盘与屏幕按键、幽灵落点预览、最高分与续玩存档;消行达标发一次奖励。
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowDownBold, ArrowLeft, ArrowLeftBold, ArrowRightBold, FullScreen, RefreshRight } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import {
  SHAPES,
  STATUS,
  cellsOf,
  createGame,
  ghostY,
  hardDrop,
  holdPiece,
  isValidState,
  rotate,
  softDrop,
  tick,
  tryMove,
} from '@/utils/games/tetris'

const BEST_KEY = 'ihomy:games:tetris:best:v1'
const STATE_KEY = 'ihomy:games:tetris:state:v1'
const REWARD_LINES = 10

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const gameCardEl = ref(null)
const isFullscreen = ref(false)
const game = ref(createGame())
const paused = ref(false)
const best = ref(0)
const rewardMsg = ref('')

const speedMs = computed(() => Math.max(70, 800 - (game.value.level - 1) * 70))

/** 渲染矩阵:已落方块 + 幽灵落点 + 当前方块(当前块覆盖幽灵) */
const boardCells = computed(() => {
  const board = game.value.board.map((row) => row.map((cell) => (cell ? { type: cell, ghost: false } : null)))
  const current = game.value.current
  if (!current || game.value.status !== STATUS.PLAYING) return board.flat()

  const ghost = { ...current, y: ghostY(game.value) }
  for (const { x, y, type } of cellsOf(ghost)) {
    if (y >= 0 && y < board.length && x >= 0 && x < board[y].length && !board[y][x]) board[y][x] = { type, ghost: true }
  }
  for (const { x, y, type } of cellsOf(current)) {
    if (y >= 0 && y < board.length && x >= 0 && x < board[y].length) board[y][x] = { type, ghost: false }
  }
  return board.flat()
})

/** 4×4 预览:暂存与后续三块 */
const previewCells = (type) => {
  if (!type) return new Array(16).fill('')
  const filled = new Set(SHAPES[type][0].map(([x, y]) => `${x},${y}`))
  return Array.from({ length: 16 }, (_, i) => (filled.has(`${i % 4},${Math.floor(i / 4)}`) ? type : ''))
}

// ---------- 存档 ----------
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
    localStorage.setItem(STATE_KEY, JSON.stringify({ game: game.value, paused: paused.value }))
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
    if (!raw || !isValidState(raw.game) || raw.game.status !== STATUS.PLAYING) return false
    game.value = raw.game
    paused.value = !!raw.paused
    return true
  } catch (e) {
    return false
  }
}

// ---------- 节拍 ----------
let tickTimer = null
const stopTimer = () => {
  if (!tickTimer) return
  clearTimeout(tickTimer)
  tickTimer = null
}
const schedule = () => {
  stopTimer()
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  tickTimer = setTimeout(() => {
    tickTimer = null
    apply(tick(game.value))
    schedule()
  }, speedMs.value)
}

const grantReward = async () => {
  try {
    const res = await gameApi.tetrisReward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.rewarded', { n: res.awarded })}（${t('points.points')} ${res.balance}）`
        : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
}

const finishGame = () => {
  stopTimer()
  clearState()
  if (game.value.lines >= REWARD_LINES) grantReward()
}

const apply = (next) => {
  if (!next || next === game.value) return
  const prev = game.value
  game.value = next
  if (next.score > best.value) {
    best.value = next.score
    saveBest()
  }
  if (next.status === STATUS.OVER) {
    finishGame()
    return
  }
  // clearSeq 只在落定后自增,用它判断是否该落盘
  if (next.clearSeq !== prev.clearSeq) saveState()
}

const act = (fn) => {
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  apply(fn(game.value))
  schedule()
}

const startNew = () => {
  stopTimer()
  game.value = createGame()
  paused.value = false
  rewardMsg.value = ''
  clearState()
  schedule()
}

const togglePause = () => {
  if (game.value.status !== STATUS.PLAYING) return
  paused.value = !paused.value
  if (paused.value) {
    stopTimer()
    saveState()
  } else {
    schedule()
  }
}

// ---------- 键盘 ----------
const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  switch (event.key) {
    case 'ArrowLeft':
    case 'a':
    case 'A':
      event.preventDefault()
      act((s) => tryMove(s, -1, 0))
      break
    case 'ArrowRight':
    case 'd':
    case 'D':
      event.preventDefault()
      act((s) => tryMove(s, 1, 0))
      break
    case 'ArrowDown':
    case 's':
    case 'S':
      event.preventDefault()
      act((s) => softDrop(s))
      break
    case 'ArrowUp':
    case 'w':
    case 'W':
    case 'x':
    case 'X':
      event.preventDefault()
      act((s) => rotate(s, 1))
      break
    case ' ':
      event.preventDefault()
      act((s) => hardDrop(s))
      break
    case 'c':
    case 'C':
      act((s) => holdPiece(s))
      break
    case 'p':
    case 'P':
    case 'Escape':
      togglePause()
      break
    default:
      break
  }
}

// ---------- 全屏与可见性 ----------
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
const onVisibility = () => {
  if (document.hidden) stopTimer()
  else schedule()
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('visibilitychange', onVisibility)
  sunLight?.suspendEffects()
  loadBest()
  if (!loadState()) startNew()
  else if (game.value.score > best.value) {
    best.value = game.value.score
    saveBest()
    schedule()
  } else {
    schedule()
  }
})

onBeforeUnmount(() => {
  stopTimer()
  if (game.value.status === STATUS.PLAYING) saveState()
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('visibilitychange', onVisibility)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.tt-card {
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
.tt-card:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  justify-content: flex-start;
  overflow-y: auto;
  padding-top: 24px;
}

.tt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(100%, 560px);
  gap: 12px;
  flex-wrap: wrap;
}
.tt-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 1px;
}
.tt-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.tt-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 58px;
  padding: 5px 9px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.05);
}
.tt-stat-label {
  font-size: 10px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
  white-space: nowrap;
}
.tt-stat-value {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}
.tt-actions {
  display: flex;
  gap: 8px;
  width: min(100%, 560px);
  justify-content: flex-end;
}

.tt-stage {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: clamp(8px, 2vw, 18px);
  width: 100%;
}
.tt-board-wrap {
  padding: 5px;
  border-radius: 12px;
  background: #1b1f26;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}
.tt-board {
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  grid-template-rows: repeat(20, minmax(0, 1fr));
  gap: 1px;
  width: min(74vw, 300px);
  aspect-ratio: 1 / 2;
}
.tt-cell {
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.045);
  /* 方块左移/下落/消行时,颜色用极短过渡接上,避免整块生硬跳变 */
  transition: background-color 0.1s ease;
}
.tt-cell.ghost {
  opacity: 0.34;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.45);
  transition: opacity 0.12s ease;
}

.tt-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: clamp(58px, 12vw, 86px);
}
.tt-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 7px 6px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.05);
}
.tt-box-label {
  font-size: 10px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
  text-align: center;
}
.tt-mini {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  width: 100%;
  aspect-ratio: 1;
}
.tt-mini-cell {
  border-radius: 1px;
  background: transparent;
  transition: background-color 0.12s ease;
}

.tt-pad {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  width: min(100%, 560px);
}
.tt-pad-btn {
  min-width: 42px;
}
.tt-pad-btn.tt-drop {
  min-width: 78px;
}

.tt-tip {
  max-width: 560px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #8a8a8a);
  text-align: center;
}

.tt-overlay {
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
.tt-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.tt-overlay-icon {
  font-size: 38px;
}
.tt-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.tt-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.tt-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

.tt-I {
  background: #39c5cf;
}
.tt-J {
  background: #4a7bd4;
}
.tt-L {
  background: #e08b3a;
}
.tt-O {
  background: #e3c341;
}
.tt-S {
  background: #4fb36b;
}
.tt-T {
  background: #a163c9;
}
.tt-Z {
  background: #d95656;
}

html.dark .tt-stat,
html.dark .tt-box {
  background: rgba(255, 255, 255, 0.08);
}
html.dark .tt-overlay {
  background: rgba(20, 20, 20, 0.72);
}

@media (max-width: 560px) {
  .tt-card {
    padding: 14px 10px;
  }
  .tt-title {
    font-size: 21px;
  }
  .tt-stat {
    min-width: 50px;
    padding: 4px 7px;
  }
  .tt-board {
    width: min(68vw, 250px);
  }
}
</style>
