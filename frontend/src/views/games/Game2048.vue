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

    <div ref="gameCardEl" class="bf-card game-surface" @contextmenu.prevent @dblclick.prevent>
      <div class="bf-head">
        <div class="bf-title">2048</div>
        <div class="bf-scores">
          <div class="bf-score-box">
            <span class="bf-score-label">{{ $t('games.g2048.score') }}</span>
            <b class="bf-score-value">{{ score }}</b>
          </div>
          <div class="bf-score-box">
            <span class="bf-score-label">{{ $t('games.g2048.best') }}</span>
            <b class="bf-score-value">{{ best }}</b>
          </div>
        </div>
      </div>

      <div class="bf-actions">
        <el-select v-model="size" size="small" class="bf-size-select" :teleported="!isFullscreen" @change="switchSize">
          <el-option v-for="s in BOARD_SIZES" :key="s" :value="s" :label="s + '×' + s" />
        </el-select>
        <el-button size="small" round @click="startNew">{{ $t('games.g2048.newGame') }}</el-button>
        <el-button size="small" round :disabled="!history.length" @click="undo">{{ $t('games.g2048.undo') }}</el-button>
      </div>

      <div class="bf-board" :class="'bf-size-' + size" @touchstart="onTouchStart" @touchend="onTouchEnd">
        <div class="bf-playfield" :style="playfieldStyle">
          <div v-for="i in size * size" :key="i" class="bf-slot"></div>
          <div
            v-for="tile in tiles"
            :key="tile.id"
            class="bf-tile"
            :class="[
              'bf-tile-' + Math.min(tile.value, 4096),
              { new: newIds.has(tile.id), merged: mergedIds.has(tile.id), 'len-long': tile.value >= 1000 },
            ]"
            :style="tileStyle(tile)"
          >
            {{ tile.value }}
          </div>
        </div>
      </div>

      <div class="bf-tip">{{ $t('games.g2048.tip') }}</div>

      <Transition name="game-overlay">
        <div v-if="won || gameOver" class="bf-overlay game-overlay">
          <div class="bf-overlay-card game-overlay-card">
            <div class="bf-overlay-icon">{{ won ? '🏆' : '💤' }}</div>
            <div class="bf-overlay-title">
              {{ won ? $t('games.g2048.win') : $t('games.g2048.lose') }}
            </div>
            <div class="bf-overlay-meta">{{ $t('games.g2048.meta', { score }) }}</div>
            <div v-if="won && rewardMsg" class="bf-overlay-reward">{{ rewardMsg }}</div>
            <div class="bf-overlay-actions">
              <el-button v-if="won" type="primary" size="small" round @click="keepPlaying">{{ $t('games.g2048.continue') }}</el-button>
              <el-button v-if="won" size="small" round @click="startNew">{{ $t('games.g2048.playAgain') }}</el-button>
              <el-button v-else type="primary" size="small" round @click="startNew">{{ $t('games.g2048.playAgain') }}</el-button>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup>
// 2048:经典数字合并小游戏。移动/合并/落子/胜负全在 utils/games/game2048.js(纯函数,单测覆盖),
// 本页负责方块定位与动画、键盘/滑动输入、最高分与本局进度存档;达成 2048 按积分规则发奖励。
// 列宽模式 4~8 各自独立存档与最高分;4 列沿用旧键,老玩家的进度与最高分不丢。
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import { useGameView } from '@/composables/useGameView'
import { BOARD_SIZES, createGame, hasWon, isGameOver, isValidTiles, move, spawnTile } from '@/utils/games/game2048'

const { t } = useI18n()
const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)

const MAX_HISTORY = 20

const pageEl = ref(null)
const gameCardEl = ref(null)
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: gameCardEl })

const size = ref(4)
const tiles = ref([])
const score = ref(0)
const best = ref(0)
const achieved = ref(false)
const won = ref(false)
const rewardMsg = ref('')
const history = ref([])
const newIds = ref(new Set())
const mergedIds = ref(new Set())

const gameOver = computed(() => isGameOver(tiles.value, size.value))

// 4 列沿用旧键,其余列宽各用独立键
const bestKey = () => (size.value === 4 ? 'ihomy:games:2048:best:v1' : `ihomy:games:2048:best:${size.value}:v1`)
const stateKey = () => (size.value === 4 ? 'ihomy:games:2048:state:v1' : `ihomy:games:2048:state:${size.value}:v1`)

const playfieldStyle = computed(() => ({
  gridTemplateColumns: `repeat(${size.value}, 1fr)`,
  gridTemplateRows: `repeat(${size.value}, 1fr)`,
}))

const tileStyle = (tile) => {
  const n = size.value
  const cell = `calc((100% - ${n - 1} * var(--bf-gap)) / ${n})`
  // translate 的百分比按方块自身尺寸解析:每格步长 = 方块宽 + 间距
  const offset = (i) => `calc(${i} * (100% + var(--bf-gap)))`
  return {
    width: cell,
    height: cell,
    transform: `translate(${offset(tile.col)}, ${offset(tile.row)})`,
  }
}

const flash = (target, ids) => {
  if (!ids.length) return
  target.value = new Set(ids)
  setTimeout(() => {
    target.value = new Set()
  }, 180)
}

// ---------- 存档 ----------
const loadBest = () => {
  try {
    const n = Number(localStorage.getItem(bestKey()))
    best.value = Number.isFinite(n) && n > 0 ? Math.floor(n) : 0
  } catch (e) {
    best.value = 0
  }
}
const saveBest = () => {
  try {
    localStorage.setItem(bestKey(), String(best.value))
  } catch (e) {
    // 隐私模式忽略
  }
}
const saveState = () => {
  try {
    localStorage.setItem(stateKey(), JSON.stringify({ tiles: tiles.value, score: score.value, achieved: achieved.value }))
  } catch (e) {
    // 忽略
  }
}
const clearState = () => {
  try {
    localStorage.removeItem(stateKey())
  } catch (e) {
    // 忽略
  }
}
const loadState = () => {
  try {
    const raw = JSON.parse(localStorage.getItem(stateKey()) || 'null')
    if (!raw || !isValidTiles(raw.tiles, size.value) || !raw.tiles.length) return null
    if (!Number.isInteger(raw.score) || raw.score < 0) return null
    return raw
  } catch (e) {
    return null
  }
}

// ---------- 游戏流程 ----------
const startNew = () => {
  tiles.value = createGame(Math.random, size.value)
  score.value = 0
  achieved.value = false
  won.value = false
  rewardMsg.value = ''
  history.value = []
  newIds.value = new Set()
  mergedIds.value = new Set()
  clearState()
}

// 切换列宽 = 切到另一份独立存档:当前局面已随每次移动落盘,这里装载新尺寸的档(没有就开新局)
const switchSize = () => {
  loadBest()
  history.value = []
  newIds.value = new Set()
  mergedIds.value = new Set()
  won.value = false
  rewardMsg.value = ''
  const saved = loadState()
  if (saved) {
    tiles.value = saved.tiles
    score.value = saved.score
    achieved.value = !!saved.achieved
    if (hasWon(saved.tiles)) achieved.value = true
  } else {
    startNew()
  }
}

const keepPlaying = () => {
  won.value = false
}

const tryMove = (direction) => {
  if (won.value || gameOver.value) return
  const res = move(tiles.value, direction, size.value)
  if (!res.moved) return

  history.value.push({
    tiles: tiles.value.map((t) => ({ ...t })),
    score: score.value,
    achieved: achieved.value,
  })
  if (history.value.length > MAX_HISTORY) history.value.shift()

  tiles.value = res.tiles
  score.value += res.gained
  flash(mergedIds, res.mergedIds)

  const spawned = spawnTile(tiles.value, Math.random, size.value)
  if (spawned) {
    tiles.value = [...tiles.value, spawned]
    flash(newIds, [spawned.id])
  }

  if (!achieved.value && hasWon(tiles.value)) {
    achieved.value = true
    won.value = true
    grantReward()
  }
  if (score.value > best.value) {
    best.value = score.value
    saveBest()
  }
  if (isGameOver(tiles.value, size.value)) clearState()
  else saveState()
}

const undo = () => {
  if (won.value || !history.value.length) return
  const last = history.value.pop()
  tiles.value = last.tiles
  score.value = last.score
  achieved.value = last.achieved
  saveState()
}

const grantReward = async () => {
  try {
    const res = await gameApi.game2048Reward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.rewarded', { n: res.awarded })}（${t('points.points')} ${res.balance}）`
        : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
}

// ---------- 键盘 / 触摸 ----------
const KEY_DIRECTION = {
  ArrowLeft: 'left',
  ArrowRight: 'right',
  ArrowUp: 'up',
  ArrowDown: 'down',
  a: 'left',
  d: 'right',
  w: 'up',
  s: 'down',
  A: 'left',
  D: 'right',
  W: 'up',
  S: 'down',
}

const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  const direction = KEY_DIRECTION[event.key]
  if (!direction) return
  event.preventDefault()
  tryMove(direction)
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
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return
  tryMove(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : dy > 0 ? 'down' : 'up')
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  sunLight?.suspendEffects()
  loadBest()

  const saved = loadState()
  if (saved) {
    tiles.value = saved.tiles
    score.value = saved.score
    achieved.value = !!saved.achieved
    if (hasWon(saved.tiles)) achieved.value = true
  } else {
    startNew()
  }
})

onBeforeUnmount(() => {
  if (!gameOver.value) saveState()
  document.removeEventListener('keydown', onKeydown)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.bf-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 18px 16px 16px;
  border-radius: 14px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
  --bf-gap: clamp(6px, 2vw, 10px);
}
.bf-card:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  justify-content: flex-start;
  overflow-y: auto;
  padding-top: 28px;
}

.bf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(100%, 480px);
  gap: 12px;
}
.bf-title {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 1px;
}
.bf-scores {
  display: flex;
  gap: 8px;
}
.bf-score-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 72px;
  padding: 5px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.05);
}
.bf-score-label {
  font-size: 10px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
}
.bf-score-value {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.bf-actions {
  display: flex;
  gap: 8px;
  width: min(100%, 480px);
  justify-content: flex-end;
}
.bf-size-select {
  width: 88px;
  margin-right: auto;
}

.bf-board {
  width: min(100%, 480px);
  aspect-ratio: 1;
  padding: var(--bf-gap);
  border-radius: 10px;
  background: var(--color-card-2);
  box-shadow: inset 0 2px 10px rgba(0, 0, 0, 0.14);
  touch-action: none;
  user-select: none;
  contain: layout style;
}
.bf-card:fullscreen .bf-board {
  width: min(74vh, 86vw);
}
.bf-playfield {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(4, 1fr);
  gap: var(--bf-gap);
  width: 100%;
  height: 100%;
}
.bf-slot {
  border-radius: 6px;
  background: rgba(var(--color-card-rgb), 0.35);
}
.bf-tile {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-weight: 800;
  font-size: clamp(20px, 6vw, 38px);
  line-height: 1;
  color: #776e65;
  background: var(--color-surface);
  transition: transform 0.13s ease;
  will-change: transform;
}
.bf-tile.len-long {
  font-size: clamp(15px, 4.4vw, 28px);
}
/* 大棋盘字号随列宽递减,8 列下四位数字也不出格 */
.bf-size-5 .bf-tile {
  font-size: clamp(17px, 5vw, 31px);
}
.bf-size-5 .bf-tile.len-long {
  font-size: clamp(13px, 3.8vw, 24px);
}
.bf-size-6 .bf-tile {
  font-size: clamp(14px, 4.2vw, 26px);
}
.bf-size-6 .bf-tile.len-long {
  font-size: clamp(11px, 3vw, 19px);
}
.bf-size-7 .bf-tile {
  font-size: clamp(12px, 3.6vw, 22px);
}
.bf-size-7 .bf-tile.len-long {
  font-size: clamp(10px, 2.6vw, 16px);
}
.bf-size-8 .bf-tile {
  font-size: clamp(10px, 3.2vw, 19px);
}
.bf-size-8 .bf-tile.len-long {
  font-size: clamp(8px, 2.4vw, 14px);
}
.bf-tile.new {
  animation: bf-pop 0.16s ease;
}
.bf-tile.merged {
  animation: bf-merge 0.18s ease;
}
@keyframes bf-pop {
  from {
    transform-origin: center;
    scale: 0.35;
  }
}
@keyframes bf-merge {
  50% {
    scale: 1.14;
  }
}

.bf-tile-2 {
  background: #eee4da;
}
.bf-tile-4 {
  background: #ede0c8;
}
.bf-tile-8 {
  background: #f2b179;
  color: #f9f6f2;
}
.bf-tile-16 {
  background: #f59563;
  color: #f9f6f2;
}
.bf-tile-32 {
  background: #f67c5f;
  color: #f9f6f2;
}
.bf-tile-64 {
  background: #f65e3b;
  color: #f9f6f2;
}
.bf-tile-128 {
  background: #edcf72;
  color: #f9f6f2;
}
.bf-tile-256 {
  background: #edcc61;
  color: #f9f6f2;
}
.bf-tile-512 {
  background: #edc850;
  color: #f9f6f2;
}
.bf-tile-1024 {
  background: #edc53f;
  color: #f9f6f2;
}
.bf-tile-2048 {
  background: #edc22e;
  color: #f9f6f2;
  box-shadow: 0 0 22px rgba(237, 194, 46, 0.55);
}
.bf-tile-4096 {
  background: #3c3a32;
  color: #f9f6f2;
}

.bf-tip {
  max-width: 480px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #8a8a8a);
  text-align: center;
}

.bf-overlay {
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
.bf-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.bf-overlay-icon {
  font-size: 38px;
}
.bf-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.bf-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.bf-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}
.bf-overlay-actions {
  display: flex;
  gap: 8px;
}

html.dark .bf-score-box {
  background: rgba(255, 255, 255, 0.08);
}
html.dark .bf-overlay {
  background: rgba(20, 20, 20, 0.72);
}

@media (max-width: 560px) {
  .bf-card {
    padding: 14px 10px;
  }
  .bf-title {
    font-size: 22px;
  }
  .bf-score-box {
    min-width: 62px;
    padding: 4px 8px;
  }
}
</style>
