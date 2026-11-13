<template>
  <div ref="pageEl" class="page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions :is-fullscreen="isFullscreen" :wide="isWide" @back="goBack" @fullscreen="toggleFullscreen" @wide="toggleWide" />
    </PageToolbar>

    <div ref="gameCardEl" class="ms-card card game-surface" @contextmenu.prevent @dblclick.prevent>
      <GameRoomBar
        :room="play.room"
        :is-host="play.isHost"
        :score="elapsed"
        :reported="play.reported"
        :ended="play.ended"
        :result="play.spec?.result"
        :my-id="play.myId"
        @submit="submitRoomScore"
        @leave="play.leave"
      />
      <div class="ms-head">
        <div class="ms-title">{{ $t('games.minesweeper.title') }}</div>
        <div class="ms-stats">
          <div class="ms-stat">
            <span class="ms-stat-label">{{ $t('games.minesweeper.flags') }}</span>
            <b class="ms-stat-value">{{ remainingMines(board) }}</b>
          </div>
          <div class="ms-stat">
            <span class="ms-stat-label">{{ $t('games.minesweeper.time') }}</span>
            <b class="ms-stat-value">{{ formatClock(elapsed) }}</b>
          </div>
          <div class="ms-stat">
            <span class="ms-stat-label">{{ $t('games.minesweeper.best') }}</span>
            <b class="ms-stat-value">{{ bestText }}</b>
          </div>
        </div>
      </div>

      <div class="ms-actions">
        <el-select :model-value="level" size="small" class="ms-level" :teleported="!isFullscreen" @change="onLevelChange">
          <el-option v-for="key in LEVEL_KEYS" :key="key" :value="key" :label="$t('games.minesweeper.level.' + key)" />
        </el-select>
        <el-button size="small" round :type="flagMode ? 'primary' : 'default'" @click="flagMode = !flagMode">
          {{ flagMode ? $t('games.minesweeper.flagMode') : $t('games.minesweeper.revealMode') }}
        </el-button>
        <el-button size="small" round @click="onNewGameClick">{{ $t('games.minesweeper.newGame') }}</el-button>
      </div>

      <div class="ms-board-wrap">
        <div class="ms-board" :class="board.level.toLowerCase()">
          <button
            v-for="(cell, i) in board.cells"
            :key="i"
            type="button"
            class="ms-cell"
            :class="[cellClass(cell, i)]"
            :aria-label="cellLabel(cell, i)"
            @pointerdown="onPointerDown(i, $event)"
            @pointerup="onPointerUp"
            @pointerleave="onPointerUp"
            @pointercancel="onPointerUp"
            @click="onCellClick(i)"
            @contextmenu.stop.prevent="onCellContextmenu(i, $event)"
          >
            <span v-if="cell.revealed && cell.mine">💣</span>
            <span v-else-if="cell.revealed && cell.adjacent">{{ cell.adjacent }}</span>
            <span v-else-if="cell.flagged">🚩</span>
          </button>
        </div>
      </div>

      <div class="ms-tip">{{ $t('games.minesweeper.tip') }}</div>

      <Transition name="game-overlay">
        <div v-if="isFinished" class="ms-overlay game-overlay">
          <div class="ms-overlay-card game-overlay-card">
            <div class="ms-overlay-icon">{{ isWin ? '🎉' : '💥' }}</div>
            <div class="ms-overlay-title">
              {{ isWin ? $t('games.minesweeper.win') : $t('games.minesweeper.lose') }}
            </div>
            <div class="ms-overlay-meta">{{ $t('games.minesweeper.winMeta', { time: formatClock(elapsed) }) }}</div>
            <div v-if="isWin && rewardMsg" class="ms-overlay-reward">{{ rewardMsg }}</div>
            <el-button type="primary" size="small" round @click="onNewGameClick">
              {{ $t('games.minesweeper.newGame') }}
            </el-button>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup>
// 扫雷:布雷/连锁展开/插旗/和弦全在 utils/games/minesweeper.js(纯函数,单测覆盖)。
// 本页负责渲染、长按插旗与标记模式、计时与最高纪录;通关按积分规则发奖励。
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { gameApi } from '@/api'
import { useGameView } from '@/composables/useGameView'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { formatClock } from '@/utils/games/gameClock'
import { seededRng } from '@/utils/games/seededRng'
import {
  LEVELS,
  LEVEL_KEYS,
  STATUS,
  chord,
  createBoard,
  isValidBoard,
  remainingMines,
  revealCell,
  toggleFlag,
} from '@/utils/games/minesweeper'

const BEST_KEY = 'ihomy:games:minesweeper:best:v1'
const STATE_KEY = 'ihomy:games:minesweeper:state:v1'

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const pageEl = ref(null)
const gameCardEl = ref(null)
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: gameCardEl })
const level = ref('EASY')
const board = ref(createBoard('EASY'))
const elapsed = ref(0)
const flagMode = ref(false)
const rewardMsg = ref('')
const bestTimes = ref({})

// ---------- 「一起玩」房间(扫雷只允许合作) ----------
// 房主权威:只有房主布雷/翻格/插旗,操作结果全量广播;其他成员点击只发意图,倒计时跟房主走。
const play = useGameRoomPlay({ onSignal: onRoomSignal, onRoom: onRoomChange })
/** 房间局返回房间大厅;单机回小游戏列表 */
const goBack = () => {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else router.push('/games')
}
const coopGuest = computed(() => play.active && play.coop && !play.isHost)
const submitRoomScore = () => play.report(elapsed.value)

const randFor = () => (play.active ? seededRng(Number(play.seed) || 1) : Math.random)

function snapshotRoomState() {
  return { board: board.value, elapsed: elapsed.value }
}

function applyRoomState(state) {
  const next = state?.board
  const shape = next && LEVELS[next.level]
  if (!shape || !Array.isArray(next.cells) || next.cells.length !== shape.rows * shape.cols) return
  board.value = next
  level.value = next.level
  elapsed.value = Math.max(0, Number(state.elapsed) || 0)
  if (isFinished.value) stopTimer()
}

const broadcastRoomState = () => {
  if (!play.active || !play.isHost) return
  play.send('state', snapshotRoomState())
}

function onRoomSignal(data) {
  if (!data) return
  if (data.event === 'state' && coopGuest.value) {
    applyRoomState(data.state)
    return
  }
  if (data.event === 'clock' && coopGuest.value) {
    elapsed.value = Math.max(0, Number(data.elapsed) || 0)
    return
  }
  if (data.event === 'sync' && play.isHost) {
    broadcastRoomState()
    return
  }
  if (data.event === 'op' && play.isHost) {
    if (data.kind === 'click') {
      const index = Number(data.index)
      if (!Number.isInteger(index) || !board.value.cells[index]) return
      if (isFinished.value) return
      const cell = board.value.cells[index]
      let next
      if (data.flag) next = toggleFlag(board.value, index)
      else if (cell.revealed) next = chord(board.value, index)
      else next = revealCell(board.value, index, randFor())
      afterAction(next)
    } else if (data.kind === 'newGame') {
      startNew(data.level)
    }
    return
  }
}

function onRoomChange(next) {
  if (!next) return
  if (next.status === 'FINISHED' || next.status === 'CANCELLED') stopTimer()
}

const isWin = computed(() => board.value.status === STATUS.WON)
const isFinished = computed(() => board.value.status === STATUS.WON || board.value.status === STATUS.LOST)
const bestText = computed(() => {
  const best = bestTimes.value[level.value]
  return Number.isInteger(best) ? formatClock(best) : '—'
})

let timer = null
const startTimer = () => {
  if (coopGuest.value) return
  if (timer) return
  timer = setInterval(() => {
    elapsed.value++
    if (play.active && play.isHost) {
      // 合作模式:倒计时以房主为准;盘面只在操作时广播,这里每 5 秒补一次全量快照兜底
      if (elapsed.value % 5 === 0) broadcastRoomState()
      else play.send('clock', { elapsed: elapsed.value })
    }
    if (elapsed.value % 5 === 0) saveState()
  }, 1000)
}
const stopTimer = () => {
  if (!timer) return
  clearInterval(timer)
  timer = null
}
const syncTimer = () => {
  if (coopGuest.value) {
    stopTimer()
    return
  }
  if (board.value.status === STATUS.PLAYING) startTimer()
  else stopTimer()
}

// ---------- 存档 ----------
const loadBest = () => {
  try {
    const raw = JSON.parse(localStorage.getItem(BEST_KEY) || '{}')
    const clean = {}
    for (const key of LEVEL_KEYS) {
      if (Number.isInteger(raw?.[key]) && raw[key] >= 0) clean[key] = raw[key]
    }
    bestTimes.value = clean
  } catch (e) {
    bestTimes.value = {}
  }
}
const saveBest = () => {
  try {
    localStorage.setItem(BEST_KEY, JSON.stringify(bestTimes.value))
  } catch (e) {
    // 隐私模式忽略
  }
}
const saveState = () => {
  if (play.active) return
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify({ board: board.value, elapsed: elapsed.value }))
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
  try {
    const raw = JSON.parse(localStorage.getItem(STATE_KEY) || 'null')
    if (!raw || !isValidBoard(raw.board) || !Number.isInteger(raw.elapsed) || raw.elapsed < 0) return false
    if (raw.board.status === STATUS.WON || raw.board.status === STATUS.LOST) return false
    board.value = raw.board
    level.value = raw.board.level
    elapsed.value = raw.elapsed
    return true
  } catch (e) {
    return false
  }
}

// ---------- 游戏流程 ----------
const startNew = (nextLevel = level.value) => {
  level.value = LEVEL_KEYS.includes(nextLevel) ? nextLevel : 'EASY'
  stopTimer()
  board.value = createBoard(level.value)
  elapsed.value = 0
  rewardMsg.value = ''
  flagMode.value = false
  clearState()
  if (play.active && play.isHost) broadcastRoomState()
}

const grantReward = async () => {
  if (play.active) return
  try {
    const res = await gameApi.minesweeperReward()
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
  if (play.active) {
    if (play.isHost) play.report(elapsed.value)
    return
  }
  const best = bestTimes.value[level.value]
  if (!Number.isInteger(best) || elapsed.value < best) {
    bestTimes.value = { ...bestTimes.value, [level.value]: elapsed.value }
    saveBest()
  }
  clearState()
  grantReward()
}

const afterAction = (next) => {
  if (!next || next === board.value) return
  board.value = next
  if (play.active && play.isHost) broadcastRoomState()
  if (next.status === STATUS.WON) finishWin()
  else if (next.status === STATUS.LOST) {
    stopTimer()
    clearState()
  } else {
    syncTimer()
    saveState()
  }
}

// 长按 420ms 插旗。触摸/手写笔长按成立时系统还会补发 contextmenu,若不拦截会再切一次旗,
// 表现为「旗子出现后立刻消失」;所以同一次按下只能有一方插旗(计时器或 contextmenu)。
const LONG_PRESS_MS = 420
let pressTimer = null
let suppressClick = false
let gestureFlagged = false
let lastPointerType = 'mouse'

const flagByLongPress = (index) => {
  suppressClick = true
  gestureFlagged = true
  onPointerUp()
  flagAt(index)
}

const onPointerDown = (index, event) => {
  const pointerType = event?.pointerType || 'mouse'
  lastPointerType = pointerType
  // 鼠标右键/笔杆键交给 contextmenu,不参与长按计时
  if (pointerType === 'mouse' && event?.button !== 0) return
  onPointerUp()
  suppressClick = false
  gestureFlagged = false
  pressTimer = setTimeout(() => {
    pressTimer = null
    flagByLongPress(index)
  }, LONG_PRESS_MS)
}
const onPointerUp = () => {
  if (!pressTimer) return
  clearTimeout(pressTimer)
  pressTimer = null
}
const onCellContextmenu = (index, event) => {
  const pointerType = event?.pointerType || lastPointerType
  if (pointerType === 'mouse') {
    flagAt(index)
    return
  }
  // 触摸/手写笔:长按事件本身即长按成立 —— 计时器已被 pointercancel 清掉时由这里补插旗,
  // 已插过旗则忽略,任意事件先后顺序都只切一次
  if (!gestureFlagged) flagByLongPress(index)
}

const onCellClick = (index) => {
  if (suppressClick) {
    suppressClick = false
    return
  }
  if (isFinished.value) return
  const cell = board.value.cells[index]
  if (!cell) return
  if (coopGuest.value) {
    play.send('op', { kind: 'click', index, flag: flagMode.value })
    return
  }
  let next
  if (flagMode.value) next = toggleFlag(board.value, index)
  else if (cell.revealed) next = chord(board.value, index)
  else next = revealCell(board.value, index, randFor())
  afterAction(next)
}

const flagAt = (index) => {
  if (isFinished.value) return
  if (coopGuest.value) {
    play.send('op', { kind: 'click', index, flag: true })
    return
  }
  afterAction(toggleFlag(board.value, index))
}

const onLevelChange = (key) => {
  if (coopGuest.value) {
    play.send('op', { kind: 'newGame', level: key })
    return
  }
  startNew(key)
}
const onNewGameClick = () => onLevelChange(level.value)

const cellClass = (cell) => ({
  open: cell.revealed,
  boom: cell.revealed && cell.mine,
  flag: cell.flagged && !cell.revealed,
  ['n' + cell.adjacent]: cell.revealed && !cell.mine && cell.adjacent > 0,
})

const cellLabel = (cell, index) => {
  const row = Math.floor(index / board.value.cols) + 1
  const col = (index % board.value.cols) + 1
  let state
  if (cell.revealed && cell.mine) state = t('games.minesweeper.stateMine')
  else if (cell.revealed)
    state = cell.adjacent ? t('games.minesweeper.stateNumber', { n: cell.adjacent }) : t('games.minesweeper.stateEmpty')
  else if (cell.flagged) state = t('games.minesweeper.stateFlag')
  else state = t('games.minesweeper.stateHidden')
  return t('games.minesweeper.cell', { row, col, state })
}

onMounted(async () => {
  sunLight?.suspendEffects()
  loadBest()
  if (play.active) {
    await play.load()
    if (coopGuest.value) play.requestSync()
    else startNew(level.value)
    return
  }
  if (!loadState()) startNew(level.value)
  syncTimer()
})

onBeforeUnmount(() => {
  stopTimer()
  if (!isFinished.value) saveState()
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.ms-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 18px 16px 16px;
  border-radius: 14px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
}
.ms-card:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  justify-content: flex-start;
  overflow-y: auto;
  padding-top: 24px;
}

.ms-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(100%, 760px);
  gap: 12px;
  flex-wrap: wrap;
}
.ms-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 1px;
}
.ms-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.ms-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 66px;
  padding: 5px 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.05);
}
.ms-stat-label {
  font-size: 10px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
  white-space: nowrap;
}
.ms-stat-value {
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.ms-actions {
  display: flex;
  gap: 8px;
  width: min(100%, 760px);
  justify-content: flex-end;
  flex-wrap: wrap;
}
.ms-level {
  width: 96px;
  margin-right: auto;
}

.ms-board-wrap {
  max-width: 100%;
  overflow: auto;
  padding: 4px;
  border-radius: 12px;
}
.ms-board {
  display: grid;
  gap: 2px;
  padding: 4px;
  border-radius: 10px;
  background: var(--color-border, #d9d2c7);
  touch-action: manipulation;
  user-select: none;
}
.ms-board.easy {
  --ms-size: clamp(28px, 7vw, 38px);
  grid-template-columns: repeat(9, var(--ms-size));
}
.ms-board.medium {
  --ms-size: clamp(20px, 4.6vw, 30px);
  grid-template-columns: repeat(16, var(--ms-size));
}
.ms-board.hard {
  --ms-size: clamp(18px, 3.4vw, 26px);
  grid-template-columns: repeat(30, var(--ms-size));
}
.ms-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--ms-size);
  height: var(--ms-size);
  padding: 0;
  border: none;
  border-radius: 4px;
  background: linear-gradient(160deg, #d8dde3, #b9c1ca);
  color: #2b2b2b;
  font-size: calc(var(--ms-size) * 0.52);
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  line-height: 1;
  transition: filter 0.15s ease;
}
.ms-cell:hover:not(.open) {
  filter: brightness(1.06);
}
/* 翻开/插旗只在该格子状态变化时播一次,连锁展开也会逐格弹一下 */
.ms-cell.open {
  background: var(--color-surface, #fff);
  box-shadow: inset 0 0 0 1px var(--color-border, #e3ded6);
  cursor: default;
  animation: ms-open 0.16s var(--ease-soft);
}
.ms-cell.flag {
  color: #c0392b;
  animation: ms-flag 0.22s var(--ease-spring);
}
@keyframes ms-open {
  from {
    opacity: 0.45;
    transform: scale(0.86);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
@keyframes ms-flag {
  0% {
    transform: scale(0.6);
  }
  70% {
    transform: scale(1.12);
  }
  100% {
    transform: scale(1);
  }
}
.ms-cell.boom {
  background: #f2b7ad;
}
.ms-cell.n1 {
  color: #2f6fdb;
}
.ms-cell.n2 {
  color: #2f8f4e;
}
.ms-cell.n3 {
  color: #d14836;
}
.ms-cell.n4 {
  color: #5b3fa0;
}
.ms-cell.n5 {
  color: #a8402f;
}
.ms-cell.n6 {
  color: #2a7f8f;
}
.ms-cell.n7 {
  color: #333;
}
.ms-cell.n8 {
  color: #888;
}

.ms-tip {
  max-width: 760px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #8a8a8a);
  text-align: center;
}

.ms-overlay {
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
.ms-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.ms-overlay-icon {
  font-size: 38px;
}
.ms-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.ms-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.ms-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

html.dark .ms-stat {
  background: rgba(255, 255, 255, 0.08);
}
html.dark .ms-cell:not(.open) {
  background: linear-gradient(160deg, #4a505a, #333942);
  color: #e6e6e6;
}
html.dark .ms-cell.open {
  background: rgba(255, 255, 255, 0.06);
}
/* 踩中雷的格子:浅色档靠粉底区分,深色档改半透明暗红 */
html.dark .ms-cell.boom {
  background: rgba(201, 116, 116, 0.38);
}
/* 翻开格底色随卡片转深,数字配色整体提亮(浅色档在深底上读不清) */
html.dark .ms-cell.n1 {
  color: #6ea8ff;
}
html.dark .ms-cell.n2 {
  color: #7dba7d;
}
html.dark .ms-cell.n3 {
  color: #f2857a;
}
html.dark .ms-cell.n4 {
  color: #b6a3f5;
}
html.dark .ms-cell.n5 {
  color: #e59a6b;
}
html.dark .ms-cell.n6 {
  color: #63c5d8;
}
html.dark .ms-cell.n7 {
  color: #d5dbe3;
}
html.dark .ms-cell.n8 {
  color: #9aa5b4;
}
html.dark .ms-overlay {
  background: rgba(20, 20, 20, 0.72);
}

@media (max-width: 560px) {
  .ms-card {
    padding: 14px 10px;
  }
  .ms-title {
    font-size: 21px;
  }
  .ms-stat {
    min-width: 58px;
    padding: 4px 8px;
  }
}
</style>
