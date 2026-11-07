<template>
  <div ref="pageEl" class="page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions
        :is-fullscreen="isFullscreen"
        :wide="isWide"
        @back="router.push('/games')"
        @fullscreen="toggleFullscreen"
        @wide="toggleWide"
      />
    </PageToolbar>

    <div ref="gameCardEl" class="sn-card card game-surface" @contextmenu.prevent @dblclick.prevent>
      <div class="sn-head">
        <div class="sn-title">{{ $t('games.snake.title') }}</div>
        <div class="sn-stats">
          <div class="sn-stat">
            <span class="sn-stat-label">{{ $t('games.snake.score') }}</span>
            <b class="sn-stat-value">{{ game.score }}</b>
          </div>
          <div class="sn-stat">
            <span class="sn-stat-label">{{ $t('games.snake.best') }}</span>
            <b class="sn-stat-value">{{ best }}</b>
          </div>
          <div class="sn-stat">
            <span class="sn-stat-label">{{ $t('games.snake.length') }}</span>
            <b class="sn-stat-value">{{ game.snake.length }}</b>
          </div>
        </div>
      </div>

      <div class="sn-actions">
        <el-button size="small" round :disabled="game.status !== 'PLAYING'" @click="togglePause">
          {{ paused ? $t('games.snake.resume') : $t('games.snake.pause') }}
        </el-button>
        <el-button type="primary" size="small" round @click="startNew">{{ $t('games.snake.newGame') }}</el-button>
      </div>

      <div class="sn-board" :style="{ '--sn-cols': game.cols }" @touchstart="onTouchStart" @touchend="onTouchEnd">
        <div v-for="(cell, i) in viewCells" :key="i" class="sn-cell" :class="{ snake: cell.snake, head: cell.head, food: cell.food }"></div>
      </div>

      <div class="sn-pad">
        <button type="button" class="sn-key sn-up" :aria-label="$t('games.snake.up')" @click="turnTo('up')">▲</button>
        <button type="button" class="sn-key sn-left" :aria-label="$t('games.snake.left')" @click="turnTo('left')">◀</button>
        <button type="button" class="sn-key sn-down" :aria-label="$t('games.snake.down')" @click="turnTo('down')">▼</button>
        <button type="button" class="sn-key sn-right" :aria-label="$t('games.snake.right')" @click="turnTo('right')">▶</button>
      </div>

      <div class="sn-tip">{{ $t('games.snake.tip') }}</div>

      <Transition name="game-overlay">
        <div v-if="paused || game.status !== 'PLAYING'" class="sn-overlay game-overlay">
          <div class="sn-overlay-card game-overlay-card">
            <div class="sn-overlay-icon">{{ game.status === 'WON' ? '🏆' : game.status === 'LOST' ? '💥' : '⏸' }}</div>
            <div class="sn-overlay-title">
              {{
                game.status === 'WON' ? $t('games.snake.win') : game.status === 'LOST' ? $t('games.snake.lose') : $t('games.snake.paused')
              }}
            </div>
            <div class="sn-overlay-meta">{{ $t('games.snake.meta', { score: game.score }) }}</div>
            <div v-if="game.status !== 'PLAYING' && rewardMsg" class="sn-overlay-reward">{{ rewardMsg }}</div>
            <el-button v-if="paused && game.status === 'PLAYING'" type="primary" size="small" round @click="togglePause">
              {{ $t('games.snake.resume') }}
            </el-button>
            <el-button v-else type="primary" size="small" round @click="startNew">{{ $t('games.snake.newGame') }}</el-button>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup>
// 贪吃蛇:转向/前进/吃食物/撞墙撞身判定全在 utils/games/snake.js(纯函数,单测覆盖)。
// 本页负责节拍、键盘/滑动/方向键输入、最高分与续玩存档;一局吃到 10 个食物发一次奖励。
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import { gameApi } from '@/api'
import { useGameView } from '@/composables/useGameView'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { STATUS, createGame, isValidState, step, stepInterval, turn } from '@/utils/games/snake'

const BEST_KEY = 'ihomy:games:snake:best:v1'
const STATE_KEY = 'ihomy:games:snake:state:v1'
const REWARD_SCORE = 10

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const pageEl = ref(null)
const gameCardEl = ref(null)
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: gameCardEl })
const game = ref(createGame())
const paused = ref(false)
const best = ref(0)
const rewardMsg = ref('')

const viewCells = computed(() => {
  const { cols, snake, food } = game.value
  const snakeSet = new Set(snake.map((c) => c.y * cols + c.x))
  const headKey = snake.length ? snake[0].y * cols + snake[0].x : -1
  const foodKey = food ? food.y * cols + food.x : -1
  return Array.from({ length: game.value.cols * game.value.rows }, (_, i) => ({
    snake: snakeSet.has(i),
    head: i === headKey,
    food: i === foodKey,
  }))
})

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
let stepTimer = null
const stopTimer = () => {
  if (!stepTimer) return
  clearTimeout(stepTimer)
  stepTimer = null
}
const schedule = () => {
  stopTimer()
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  stepTimer = setTimeout(() => {
    stepTimer = null
    apply(step(game.value))
    schedule()
  }, stepInterval(game.value.score))
}

const grantReward = async () => {
  try {
    const res = await gameApi.snakeReward()
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
  if (game.value.score >= REWARD_SCORE || game.value.status === STATUS.WON) grantReward()
}

let stepsSinceSave = 0
const apply = (next) => {
  if (!next || next === game.value) return
  game.value = next
  if (next.score > best.value) {
    best.value = next.score
    saveBest()
  }
  if (next.status !== STATUS.PLAYING) {
    finishGame()
    return
  }
  // 每一步都写 localStorage 太频繁,每 5 步落一次盘,离开页面时再补一次
  if (++stepsSinceSave >= 5) {
    stepsSinceSave = 0
    saveState()
  }
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

const turnTo = (direction) => {
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  game.value = turn(game.value, direction)
}

// ---------- 键盘与滑动 ----------
const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  const map = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
    w: 'up',
    s: 'down',
    a: 'left',
    d: 'right',
    W: 'up',
    S: 'down',
    A: 'left',
    D: 'right',
  }
  if (map[event.key]) {
    event.preventDefault()
    turnTo(map[event.key])
    return
  }
  if (event.key === ' ' || event.key === 'p' || event.key === 'P') {
    event.preventDefault()
    togglePause()
  }
}

let touchStart = null
const onTouchStart = (event) => {
  const touch = event.changedTouches?.[0]
  if (touch) touchStart = { x: touch.clientX, y: touch.clientY }
}
const onTouchEnd = (event) => {
  if (!touchStart) return
  const touch = event.changedTouches?.[0]
  const start = touchStart
  touchStart = null
  if (!touch) return
  const dx = touch.clientX - start.x
  const dy = touch.clientY - start.y
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 20) return
  turnTo(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : dy > 0 ? 'down' : 'up')
}

// ---------- 可见性 ----------
const onVisibility = () => {
  if (document.hidden) stopTimer()
  else schedule()
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
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
  document.removeEventListener('visibilitychange', onVisibility)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.sn-card {
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
.sn-card:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  justify-content: flex-start;
  overflow-y: auto;
  padding-top: 24px;
}

.sn-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(100%, 520px);
  gap: 12px;
  flex-wrap: wrap;
}
.sn-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 1px;
}
.sn-stats {
  display: flex;
  gap: 8px;
}
.sn-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
  padding: 5px 9px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.05);
}
.sn-stat-label {
  font-size: 10px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
  white-space: nowrap;
}
.sn-stat-value {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}
.sn-actions {
  display: flex;
  gap: 8px;
  width: min(100%, 520px);
  justify-content: flex-end;
}

.sn-board {
  display: grid;
  grid-template-columns: repeat(var(--sn-cols, 20), minmax(0, 1fr));
  gap: 1px;
  width: min(88vw, 420px);
  aspect-ratio: 1;
  padding: 6px;
  border-radius: 12px;
  background: #16211a;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
  touch-action: none;
  user-select: none;
}
.sn-cell {
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.035);
}
.sn-cell.snake {
  background: linear-gradient(160deg, #67c98a, #3d9e63);
  border-radius: 3px;
}
.sn-cell.head {
  background: linear-gradient(160deg, #b8f0c9, #6fd694);
  box-shadow: 0 0 5px rgba(140, 240, 175, 0.65);
}
.sn-cell.food {
  background: radial-gradient(circle at 35% 30%, #ff9b8a, #e04b3a 70%);
  border-radius: 50%;
  animation: sn-food 1.1s ease-in-out infinite;
}
@keyframes sn-food {
  50% {
    scale: 1.15;
  }
}

.sn-pad {
  display: grid;
  grid-template-columns: repeat(3, 46px);
  grid-template-rows: repeat(2, 40px);
  gap: 6px;
  margin-top: 2px;
}
.sn-key {
  border: 1px solid var(--color-border, #ddd6ca);
  border-radius: 10px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
  font-size: 15px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    transform 0.1s ease;
}
.sn-key:active {
  transform: translateY(1px) scale(0.96);
  background: rgba(0, 0, 0, 0.06);
}
.sn-up {
  grid-column: 2;
  grid-row: 1;
}
.sn-left {
  grid-column: 1;
  grid-row: 2;
}
.sn-down {
  grid-column: 2;
  grid-row: 2;
}
.sn-right {
  grid-column: 3;
  grid-row: 2;
}

.sn-tip {
  max-width: 520px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #8a8a8a);
  text-align: center;
}

.sn-overlay {
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
.sn-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.sn-overlay-icon {
  font-size: 38px;
}
.sn-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.sn-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.sn-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

html.dark .sn-stat {
  background: rgba(255, 255, 255, 0.08);
}
html.dark .sn-key {
  background: rgba(255, 255, 255, 0.08);
  color: #e6e6e6;
}
html.dark .sn-overlay {
  background: rgba(20, 20, 20, 0.72);
}

@media (max-width: 560px) {
  .sn-card {
    padding: 14px 10px;
  }
  .sn-title {
    font-size: 21px;
  }
  .sn-stat {
    min-width: 52px;
    padding: 4px 7px;
  }
}
</style>
