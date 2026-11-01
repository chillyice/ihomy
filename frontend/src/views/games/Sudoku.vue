<template>
  <div class="page">
    <!-- 顶栏:返回 + 全屏(与连连看/物品定位同构) -->
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

    <div ref="gameCardEl" class="sd-card">
      <div class="sd-head">
        <div class="sd-title">{{ $t('games.sudoku.title') }}</div>
        <div class="sd-head-right">
          <el-select v-model="difficulty" size="small" class="sd-difficulty" @change="startNew">
            <el-option v-for="key in DIFFICULTY_KEYS" :key="key" :value="key" :label="$t('games.sudoku.difficulty.' + key)" />
          </el-select>
          <span class="sd-timer">{{ timeText }}</span>
        </div>
      </div>

      <div class="sd-stats">
        <span class="sd-stat">
          <span class="sd-stat-label">{{ $t('games.sudoku.blanks') }}</span>
          <b>{{ blanksLeft }}</b>
        </span>
        <span class="sd-stat" :class="{ warn: conflictCount > 0 }">
          <span class="sd-stat-label">{{ $t('games.sudoku.conflicts') }}</span>
          <b>{{ conflictCount }}</b>
        </span>
        <span v-if="hintsUsed" class="sd-stat">
          <span class="sd-stat-label">{{ $t('games.sudoku.hints') }}</span>
          <b>{{ hintsUsed }}</b>
        </span>
      </div>

      <div class="sd-board" :class="{ 'is-frozen': won }">
        <button
          v-for="(value, index) in board"
          :key="index"
          type="button"
          class="sd-cell"
          :class="cellClass(index)"
          :aria-label="cellLabel(index, value)"
          @click="select(index)"
        >
          <span v-if="value" class="sd-value">{{ value }}</span>
          <span v-else-if="notes[index]" class="sd-notes">
            <span v-for="n in notesList(index)" :key="n" class="sd-note">{{ n }}</span>
          </span>
        </button>
      </div>

      <div class="sd-tools">
        <button type="button" class="sd-tool" :class="{ active: notesMode }" @click="notesMode = !notesMode">
          {{ $t('games.sudoku.notes') }}
        </button>
        <button type="button" class="sd-tool" @click="erase">{{ $t('games.sudoku.erase') }}</button>
        <button type="button" class="sd-tool" :disabled="!history.length" @click="undo">
          {{ $t('games.sudoku.undo') }}
        </button>
        <button type="button" class="sd-tool" @click="useHint">{{ $t('games.sudoku.hint') }}</button>
        <button type="button" class="sd-tool" @click="startNew">{{ $t('games.sudoku.newGame') }}</button>
      </div>

      <div class="sd-numpad">
        <button
          v-for="n in 9"
          :key="n"
          type="button"
          class="sd-num"
          :class="{ done: remaining[n] <= 0 }"
          :disabled="remaining[n] <= 0"
          @click="input(n)"
        >
          <b>{{ n }}</b>
          <span class="sd-num-left">{{ remaining[n] > 0 ? remaining[n] : '' }}</span>
        </button>
      </div>

      <div class="sd-hint">
        {{ $t('games.sudoku.tip') }}
      </div>

      <div v-if="won" class="sd-overlay">
        <div class="sd-overlay-card">
          <div class="sd-overlay-icon">🎉</div>
          <div class="sd-overlay-title">{{ $t('games.sudoku.win') }}</div>
          <div class="sd-overlay-meta">{{ $t('games.sudoku.winMeta', { time: timeText, hints: hintsUsed }) }}</div>
          <div v-if="rewardMsg" class="sd-overlay-reward">{{ rewardMsg }}</div>
          <el-button type="primary" size="small" round @click="startNew">{{ $t('games.sudoku.playAgain') }}</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 数独:纯前端 H5 小游戏。生成器在 utils/games/sudoku.js(挖空时逐步校验唯一解),
// 本页只做棋盘渲染、笔记/提示/撤销/计时与进度存档;通关按后端积分规则发奖励。
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, FullScreen } from '@element-plus/icons-vue'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  CELLS,
  DIFFICULTY_KEYS,
  clearPeerNotes,
  digitCounts,
  findConflicts,
  generatePuzzle,
  isSolved,
  isValidBoard,
  notesOf,
  pickHintIndex,
} from '@/utils/games/sudoku'

const { t } = useI18n()
const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)

const STORAGE_KEY = 'ihomy:games:sudoku:v1'
const MAX_HISTORY = 120

const gameCardEl = ref(null)
const isFullscreen = ref(false)

const difficulty = ref('MEDIUM')
const puzzle = ref(new Array(CELLS).fill(0))
const solution = ref(new Array(CELLS).fill(0))
const board = ref(new Array(CELLS).fill(0))
const notes = ref(new Array(CELLS).fill(0))
const selected = ref(-1)
const notesMode = ref(false)
const history = ref([])
const hintsUsed = ref(0)
const elapsed = ref(0)
const won = ref(false)
const rewardMsg = ref('')
const hintFlash = ref(-1)

const conflicts = computed(() => findConflicts(board.value))
const conflictCount = computed(() => conflicts.value.size)
const blanksLeft = computed(() => board.value.reduce((n, v) => n + (v ? 0 : 1), 0))
const counts = computed(() => digitCounts(board.value))
const remaining = computed(() => {
  const left = [0]
  for (let n = 1; n <= 9; n++) left[n] = Math.max(0, 9 - counts.value[n])
  return left
})
const timeText = computed(() => {
  const m = Math.floor(elapsed.value / 60)
  const s = elapsed.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const selectedValue = computed(() => (selected.value >= 0 ? board.value[selected.value] : 0))
const selectedRow = computed(() => (selected.value >= 0 ? Math.floor(selected.value / 9) : -1))
const selectedCol = computed(() => (selected.value >= 0 ? selected.value % 9 : -1))
const selectedBox = computed(() => (selectedRow.value < 0 ? -1 : Math.floor(selectedRow.value / 3) * 3 + Math.floor(selectedCol.value / 3)))

const isGiven = (index) => puzzle.value[index] !== 0
const notesList = (index) => notesOf(notes.value[index])
const cellLabel = (index, value) =>
  value
    ? t('games.sudoku.cellFilled', { row: Math.floor(index / 9) + 1, col: (index % 9) + 1, value })
    : t('games.sudoku.cellEmpty', { row: Math.floor(index / 9) + 1, col: (index % 9) + 1 })

const cellClass = (index) => {
  const row = Math.floor(index / 9)
  const col = index % 9
  const box = Math.floor(row / 3) * 3 + Math.floor(col / 3)
  return {
    given: isGiven(index),
    'edge-r': col % 3 === 2 && col !== 8,
    'edge-b': row % 3 === 2 && row !== 8,
    selected: index === selected.value,
    peer:
      selected.value >= 0 &&
      index !== selected.value &&
      (row === selectedRow.value || col === selectedCol.value || box === selectedBox.value),
    same: selectedValue.value > 0 && board.value[index] === selectedValue.value && index !== selected.value,
    conflict: conflicts.value.has(index),
    hint: hintFlash.value === index,
  }
}

// ---------- 计时 ----------
let timer = null
const stopTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}
const startTimer = () => {
  stopTimer()
  timer = setInterval(() => {
    if (won.value || document.visibilityState === 'hidden') return
    elapsed.value += 1
  }, 1000)
}

// ---------- 存档 ----------
const saveState = () => {
  if (won.value || !solution.value.length) return
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        difficulty: difficulty.value,
        puzzle: puzzle.value,
        solution: solution.value,
        board: board.value,
        notes: notes.value,
        elapsed: elapsed.value,
        hintsUsed: hintsUsed.value,
      }),
    )
  } catch (e) {
    // 隐私模式/配额满时放弃存档,不影响游戏
  }
}
const clearState = () => {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (e) {
    // 忽略
  }
}
const loadState = () => {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null')
    if (!raw) return null
    if (!DIFFICULTY_KEYS.includes(raw.difficulty)) return null
    if (!isValidBoard(raw.puzzle) || !isValidBoard(raw.board) || !isValidBoard(raw.solution, { allowZero: false })) {
      return null
    }
    if (!Array.isArray(raw.notes) || raw.notes.length !== CELLS || raw.notes.some((m) => !Number.isInteger(m))) {
      return null
    }
    for (let i = 0; i < CELLS; i++) {
      if (raw.puzzle[i] && raw.board[i] !== raw.puzzle[i]) return null
    }
    return raw
  } catch (e) {
    return null
  }
}

let saveTimer = null
const scheduleSave = () => {
  if (saveTimer) return
  saveTimer = setTimeout(() => {
    saveTimer = null
    saveState()
  }, 800)
}
// ---------- 游戏流程 ----------
const startNew = () => {
  const { puzzle: nextPuzzle, solution: nextSolution } = generatePuzzle(difficulty.value)
  puzzle.value = nextPuzzle
  solution.value = nextSolution
  board.value = nextPuzzle.slice()
  notes.value = new Array(CELLS).fill(0)
  selected.value = -1
  notesMode.value = false
  history.value = []
  hintsUsed.value = 0
  elapsed.value = 0
  won.value = false
  rewardMsg.value = ''
  hintFlash.value = -1
  clearState()
  startTimer()
}

const select = (index) => {
  if (won.value) return
  selected.value = index
}

const snapshot = () => {
  history.value.push({ board: board.value.slice(), notes: notes.value.slice() })
  if (history.value.length > MAX_HISTORY) history.value.shift()
}

const input = (n) => {
  const index = selected.value
  if (won.value || index < 0 || isGiven(index)) return
  if (notesMode.value) {
    if (board.value[index]) return
    snapshot()
    notes.value[index] ^= 1 << (n - 1)
    return
  }
  snapshot()
  if (board.value[index] === n) {
    board.value[index] = 0
    return
  }
  board.value[index] = n
  notes.value[index] = 0
  clearPeerNotes(notes.value, index, n)
  if (isSolved(board.value)) finish()
}

const erase = () => {
  const index = selected.value
  if (won.value || index < 0 || isGiven(index)) return
  if (!board.value[index] && !notes.value[index]) return
  snapshot()
  board.value[index] = 0
  notes.value[index] = 0
}

const undo = () => {
  if (won.value || !history.value.length) return
  const last = history.value.pop()
  board.value = last.board
  notes.value = last.notes
}

const useHint = () => {
  if (won.value) return
  let index = selected.value
  if (index < 0 || board.value[index]) index = pickHintIndex(board.value)
  if (index < 0) return
  snapshot()
  board.value[index] = solution.value[index]
  notes.value[index] = 0
  clearPeerNotes(notes.value, index, solution.value[index])
  hintsUsed.value += 1
  selected.value = index
  hintFlash.value = index
  setTimeout(() => {
    if (hintFlash.value === index) hintFlash.value = -1
  }, 900)
  if (isSolved(board.value)) finish()
}

const finish = () => {
  if (won.value) return
  won.value = true
  stopTimer()
  selected.value = -1
  clearState()
  grantReward()
}

const grantReward = async () => {
  try {
    const res = await gameApi.sudokuReward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.rewarded', { n: res.awarded })}（${t('points.points')} ${res.balance}）`
        : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
}

// ---------- 键盘 ----------
const ARROW_DELTA = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -9, ArrowDown: 9 }

const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  if (won.value) return
  if (/^[1-9]$/.test(event.key)) {
    input(Number(event.key))
    event.preventDefault()
    return
  }
  if (event.key === 'Backspace' || event.key === 'Delete' || event.key === '0') {
    erase()
    event.preventDefault()
    return
  }
  if (event.key === 'n' || event.key === 'N') {
    notesMode.value = !notesMode.value
    return
  }
  const delta = ARROW_DELTA[event.key]
  if (delta) {
    const from = selected.value >= 0 ? selected.value : 0
    const next = Math.min(CELLS - 1, Math.max(0, from + delta))
    selected.value = next
    event.preventDefault()
  }
}

// ---------- 全屏 ----------
const toggleFullscreen = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen?.()
    else await gameCardEl.value?.requestFullscreen?.()
  } catch (e) {
    // 浏览器拒绝(如 iOS)时保持窗口态
  }
}
const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

// 棋面/笔记改动立即落盘(撤销后立刻刷新也不会回退),计时每秒变化走防抖,避免每秒写 localStorage
watch([board, notes, difficulty, hintsUsed], saveState, { deep: true })
watch(elapsed, scheduleSave)

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  sunLight?.suspendEffects()

  const saved = loadState()
  if (saved) {
    difficulty.value = saved.difficulty
    puzzle.value = saved.puzzle
    solution.value = saved.solution
    board.value = saved.board
    notes.value = saved.notes
    elapsed.value = Number.isInteger(saved.elapsed) ? saved.elapsed : 0
    hintsUsed.value = Number.isInteger(saved.hintsUsed) ? saved.hintsUsed : 0
    startTimer()
  } else {
    nextTick(startNew)
  }
})

onBeforeUnmount(() => {
  stopTimer()
  if (saveTimer) clearTimeout(saveTimer)
  saveState()
  document.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.sd-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 18px 16px 16px;
  border-radius: 14px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
  --sd-line: rgba(0, 0, 0, 0.12);
  --sd-line-strong: rgba(0, 0, 0, 0.32);
  --sd-cell-bg: var(--color-surface, #fff);
  --sd-given: var(--color-text-primary, #2b2b2b);
  --sd-user: var(--color-accent, #b88c6e);
  --sd-peer: rgba(184, 140, 110, 0.08);
  --sd-same: rgba(184, 140, 110, 0.2);
  --sd-conflict: rgba(214, 76, 76, 0.22);
  --sd-hint: rgba(96, 176, 120, 0.3);
}
.sd-card:fullscreen {
  width: 100vw;
  height: 100vh;
  border-radius: 0;
  justify-content: flex-start;
  overflow-y: auto;
  padding-top: 28px;
}

.sd-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
}
.sd-title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}
.sd-head-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sd-difficulty {
  width: 118px;
}
.sd-timer {
  font-variant-numeric: tabular-nums;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-secondary, #8a8a8a);
}

.sd-stats {
  display: flex;
  gap: 18px;
  width: 100%;
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
.sd-stat {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
}
.sd-stat b {
  font-size: 14px;
  color: var(--color-text-primary, #2b2b2b);
  font-variant-numeric: tabular-nums;
}
.sd-stat.warn b {
  color: #d64c4c;
}

.sd-board {
  display: grid;
  grid-template-columns: repeat(9, 1fr);
  width: min(100%, 520px);
  aspect-ratio: 1;
  border: 2px solid var(--sd-line-strong);
  border-radius: 8px;
  overflow: hidden;
  background: var(--sd-cell-bg);
  contain: layout style;
}
.sd-card:fullscreen .sd-board {
  width: min(78vh, 86vw);
}
.sd-board.is-frozen {
  pointer-events: none;
}

.sd-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-right: 1px solid var(--sd-line);
  border-bottom: 1px solid var(--sd-line);
  background: transparent;
  color: var(--sd-user);
  font-size: clamp(16px, 4.6vw, 28px);
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.sd-cell.edge-r {
  border-right-width: 2px;
  border-right-color: var(--sd-line-strong);
}
.sd-cell.edge-b {
  border-bottom-width: 2px;
  border-bottom-color: var(--sd-line-strong);
}
.sd-cell:nth-child(9n) {
  border-right: 0;
}
.sd-cell:nth-child(n + 73) {
  border-bottom: 0;
}
.sd-cell.given {
  color: var(--sd-given);
  font-weight: 700;
}
.sd-cell.peer {
  background: var(--sd-peer);
}
.sd-cell.same {
  background: var(--sd-same);
}
.sd-cell.selected {
  background: var(--sd-same);
  box-shadow: inset 0 0 0 2px var(--sd-user);
}
.sd-cell.conflict {
  background: var(--sd-conflict);
  color: #c23a3a;
}
.sd-cell.hint {
  background: var(--sd-hint);
}
.sd-value {
  line-height: 1;
}

.sd-notes {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  width: 100%;
  height: 100%;
  padding: 3px;
  box-sizing: border-box;
  font-size: clamp(7px, 1.7vw, 10px);
  font-weight: 500;
  color: var(--color-text-secondary, #8a8a8a);
}
.sd-note {
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.sd-tools {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  width: 100%;
}
.sd-tool {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid var(--sd-line);
  background: transparent;
  color: var(--color-text-primary, #2b2b2b);
  font-size: 13px;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}
.sd-tool:hover:not(:disabled) {
  background: var(--sd-peer);
}
.sd-tool.active {
  background: var(--sd-user);
  border-color: var(--sd-user);
  color: #fff;
}
.sd-tool:disabled {
  opacity: 0.4;
  cursor: default;
}

.sd-numpad {
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  gap: 6px;
  width: min(100%, 520px);
}
.sd-num {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 0;
  border-radius: 10px;
  border: 1px solid var(--sd-line);
  background: transparent;
  color: var(--sd-user);
  font-size: 17px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.sd-num:hover:not(:disabled) {
  background: var(--sd-peer);
}
.sd-num.done {
  opacity: 0.32;
  cursor: default;
}
.sd-num-left {
  position: absolute;
  right: 4px;
  bottom: 1px;
  font-size: 9px;
  color: var(--color-text-secondary, #8a8a8a);
}

.sd-hint {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  text-align: center;
  line-height: 1.6;
  max-width: 520px;
}

.sd-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 14px;
  z-index: 5;
}
.sd-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.sd-overlay-icon {
  font-size: 38px;
}
.sd-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.sd-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.sd-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

html.dark .sd-card {
  --sd-line: rgba(255, 255, 255, 0.13);
  --sd-line-strong: rgba(255, 255, 255, 0.34);
  --sd-cell-bg: rgba(255, 255, 255, 0.03);
  --sd-given: rgba(255, 255, 255, 0.92);
  --sd-peer: rgba(184, 140, 110, 0.14);
  --sd-same: rgba(184, 140, 110, 0.28);
  --sd-conflict: rgba(214, 76, 76, 0.32);
  --sd-hint: rgba(96, 176, 120, 0.34);
}
html.dark .sd-cell.conflict {
  color: #ff9a9a;
}

@media (max-width: 560px) {
  .sd-card {
    padding: 14px 10px;
  }
  .sd-title {
    font-size: 17px;
  }
  .sd-stats {
    gap: 12px;
  }
  .sd-tool {
    padding: 6px 11px;
    font-size: 12px;
  }
  .sd-numpad {
    gap: 4px;
  }
  .sd-num {
    padding: 8px 0;
    font-size: 15px;
    border-radius: 8px;
  }
}
</style>
