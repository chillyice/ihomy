<template>
  <GameShell
    ref="shellEl"
    class="ms-shell"
    :class="'ms-' + board.level.toLowerCase()"
    :title="title"
    :rules-pages="[$t('games.minesweeper.rules.pagePlay'), $t('games.minesweeper.rules.pageControl')]"
    body-align="center"
    @back="goBack"
  >
    <!-- 等级跟游戏名走(和 2048 的棋盘规格同一处),控件行只留数据与按钮 -->
    <template #titleExtra>
      <el-select :model-value="level" class="ms-level" :teleported="!isFullscreen" @change="onLevelChange">
        <el-option v-for="key in LEVEL_KEYS" :key="key" :value="key" :label="$t('games.minesweeper.level.' + key)" />
      </el-select>
    </template>

    <template #rules="{ page }">
      <template v-if="page === 0">
        <p>{{ $t('games.minesweeper.tip') }}</p>
        <ul>
          <li>{{ $t('games.minesweeper.rules.play1') }}</li>
          <li>{{ $t('games.minesweeper.rules.play2') }}</li>
          <li>{{ $t('games.minesweeper.rules.play3') }}</li>
          <li>{{ $t('games.minesweeper.rules.play4') }}</li>
        </ul>
      </template>
      <ul v-else>
        <li>{{ $t('games.minesweeper.rules.ctrl1') }}</li>
        <li>{{ $t('games.minesweeper.rules.ctrl2') }}</li>
        <li>{{ $t('games.minesweeper.rules.ctrl3') }}</li>
        <li>{{ $t('games.minesweeper.rules.ctrl4') }}</li>
        <li>{{ $t('games.minesweeper.rules.ctrl5') }}</li>
      </ul>
    </template>

    <template #extras>
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
    </template>

    <!-- 数据条贴棋盘:与棋盘同宽的细条,左对齐排在棋盘上方;
         旗标模式/新游戏放在这条的右端(棋盘右上角);手机横屏时整条(按钮+芯片)
         竖排成棋盘左侧一列,把高度全留给棋盘 -->
    <div class="ms-layout" @contextmenu.prevent @dblclick.prevent>
      <div class="ms-meta">
        <span class="gh-chip"
          ><span>{{ $t('games.minesweeper.flags') }}</span
          ><b>{{ remainingMines(board) }}</b></span
        >
        <span class="gh-chip"
          ><span>{{ $t('games.minesweeper.time') }}</span
          ><b>{{ formatClock(elapsed) }}</b></span
        >
        <span class="gh-chip"
          ><span>{{ $t('games.minesweeper.best') }}</span
          ><b>{{ bestText }}</b></span
        >
        <div class="ms-meta-actions">
          <el-button
            size="small"
            class="gs-icon-btn"
            :type="flagMode ? 'primary' : 'default'"
            :title="flagMode ? $t('games.minesweeper.revealMode') : $t('games.minesweeper.flagMode')"
            :aria-label="flagMode ? $t('games.minesweeper.revealMode') : $t('games.minesweeper.flagMode')"
            :aria-pressed="flagMode"
            @click="flagMode = !flagMode"
          >
            <el-icon><Flag /></el-icon>
          </el-button>
          <el-button
            size="small"
            class="gs-icon-btn"
            :title="$t('games.minesweeper.newGame')"
            :aria-label="$t('games.minesweeper.newGame')"
            @click="onNewGameClick"
          >
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="ms-board-wrap">
        <div class="ms-board">
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
  </GameShell>
</template>

<script setup>
// 扫雷:布雷/连锁展开/插旗/和弦全在 utils/games/minesweeper.js(纯函数,单测覆盖)。
// 本页负责渲染、长按插旗与标记模式、计时与最高纪录;通关按积分规则发奖励。
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Flag, Plus } from '@element-plus/icons-vue'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { gameApi } from '@/api'
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

// 外壳自己管全屏/宽屏/一屏适配;页面只读它的状态(全屏时下拉要留在全屏元素内)
const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)

const level = ref('EASY')
const board = ref(createBoard('EASY'))
const elapsed = ref(0)
const flagMode = ref(false)
const rewardMsg = ref('')
const bestTimes = ref({})

// ---------- 「一起玩」房间(扫雷只允许合作) ----------
// 房主权威:只有房主布雷/翻格/插旗,操作结果全量广播;其他成员点击只发意图,倒计时跟房主走。
const play = useGameRoomPlay({ onSignal: onRoomSignal, onRoom: onRoomChange })
const title = computed(() => t('games.minesweeper.title') + (play.active ? ' · ' + t('games.room.together') : ''))
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
/* 数据条 + 棋盘同宽:格子大小由游戏区实测宽高推出(容器查询单位),
   纵向预算 = 游戏区高 − 数据条 − 间距,横向预算 = 游戏区宽,两者取小;
   困难 30 列在手机竖屏一屏放不满时只横向滚动,格子不会被缩到点不准 */
.ms-layout {
  --ms-meta-h: calc(var(--gs-control-h) + 8px);
  --ms-gap: 2px;
  /* 格子边长:先按游戏区宽/高各算一遍能放多大,取小者(两向都装得下);
     上限按等级封顶,下限 16px 只在「高度还够」时兜底 —— 窄屏(手机竖屏玩困难)
     宁可横向滚动也不把格子缩到点不准。高度那一路永远优先,纵向不会出现滚动条 */
  --ms-cell-w: calc((100cqw - 24px - (var(--ms-cols) - 1) * var(--ms-gap)) / var(--ms-cols));
  --ms-cell-h: calc((100cqh - var(--ms-meta-h) - 24px - (var(--ms-rows) - 1) * var(--ms-gap)) / var(--ms-rows));
  --ms-cell: min(var(--ms-cell-max), var(--ms-cell-h), max(var(--ms-cell-w), min(16px, var(--ms-cell-h))));
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: min(calc(100cqw - 4px), calc(var(--ms-cols) * var(--ms-cell) + (var(--ms-cols) - 1) * var(--ms-gap) + 16px));
  max-height: 100%;
}
.ms-shell.ms-easy {
  --ms-cols: 9;
  --ms-rows: 9;
  --ms-cell-max: 46px;
}
.ms-shell.ms-medium {
  --ms-cols: 16;
  --ms-rows: 16;
  --ms-cell-max: 34px;
}
.ms-shell.ms-hard {
  --ms-cols: 30;
  --ms-rows: 16;
  --ms-cell-max: 26px;
}

/* 数据芯片:固定一条控制行高,与棋盘同宽左对齐(芯片样式沿用 .gh-chip) */
.ms-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  height: var(--gs-control-h);
  min-width: 0;
}
/* 旗标模式/新游戏靠这条的右端;横屏时这条在棋盘左上,按钮会挪到数据上面 */
.ms-meta-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-left: auto;
}
/* 手机横屏:数据与按钮竖排成棋盘左侧一列(和外壳 rail 同向),棋盘独占右侧;
   此时纵向预算不再扣数据条,横向预算扣掉这一列的宽度 */
@media (orientation: landscape) and (max-height: 520px) {
  .ms-layout {
    --ms-meta-w: 132px;
    --ms-cell-h: calc((100cqh - 24px - (var(--ms-rows) - 1) * var(--ms-gap)) / var(--ms-rows));
    --ms-cell-w: calc((100cqw - var(--ms-meta-w) - 10px - 24px - (var(--ms-cols) - 1) * var(--ms-gap)) / var(--ms-cols));
    display: grid;
    grid-template-columns: var(--ms-meta-w) minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
    align-items: center;
    column-gap: 10px;
    row-gap: 0;
    /* 装得下时整块按实际宽度收缩(数据列紧贴棋盘,由外壳居中);
       装不下(困难盘窄屏)时吃满宽度,棋盘只横向滚动 */
    width: min(100%, calc(var(--ms-meta-w) + 10px + var(--ms-cols) * var(--ms-cell) + (var(--ms-cols) - 1) * var(--ms-gap) + 16px));
    max-height: 100%;
  }
  .ms-meta {
    flex-direction: column;
    align-items: stretch;
    justify-content: center;
    gap: 8px;
    height: auto;
  }
  /* 竖排后芯片撑满这一列宽,标签靠左、数值靠右,扫一眼就能读 */
  .ms-meta .gh-chip {
    justify-content: space-between;
  }
  .ms-meta-actions {
    order: -1;
    margin-left: 0;
  }
  .ms-board-wrap {
    min-width: 0;
    max-height: 100%;
  }
}

.ms-level {
  width: 96px;
}

/* 窄屏困难盘只横向滚动:高度已按预算算过,纵向不会再出滚动条 */
.ms-board-wrap {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior: contain;
  padding: 4px;
  border-radius: 12px;
}
.ms-board {
  display: grid;
  grid-template-columns: repeat(var(--ms-cols), var(--ms-cell));
  gap: var(--ms-gap);
  padding: 4px;
  border-radius: 10px;
  background: var(--color-border, #d9d2c7);
  touch-action: manipulation;
  user-select: none;
  /* 棋盘按格子实宽收缩:横屏时它只占左侧数据列旁边的位置,
     剩余空间由 auto 外边距居中;棋盘宽过容器(困难盘窄屏)时外边距归零,照常横向滚动 */
  width: max-content;
  margin-inline: auto;
}
.ms-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--ms-cell);
  height: var(--ms-cell);
  padding: 0;
  border: none;
  border-radius: 4px;
  background: linear-gradient(160deg, #d8dde3, #b9c1ca);
  color: #2b2b2b;
  font-size: calc(var(--ms-cell) * 0.52);
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

.ms-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
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
/* 翻开格底色随底板转深,数字配色整体提亮(浅色档在深底上读不清) */
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
</style>
