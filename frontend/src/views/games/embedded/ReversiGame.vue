<!-- Based on litegame-hub (MIT © 2026 DongGuoTao), localized as a Vue component.
     黑白棋 · 奥赛罗:夹子翻面规则 + 三档电脑(贪心/1 层/α-β 4 层),双人/人机、悔棋、U/R 快捷键;
     规则引擎与搜索逐字保留原实现,棋盘沿用原版命令式渲染(翻面动画零改动),只换外壳与 i18n。 -->
<template>
  <GameShell
    ref="shellEl"
    class="rv-shell gs-on-dark"
    :title="$t('games.embedded.reversi.title')"
    body-align="center"
    new-game
    :new-game-disabled="newDisabled"
    :new-game-confirm="isStarted && !isOver"
    @new-game="startGame"
    @back="router.push('/games')"
  >
    <!-- 对战模式与难度同处一行;AI 先手跟在后面 -->
    <template #titleExtra>
      <div class="rv-setup">
        <el-select v-model="mode" class="rv-mode" :teleported="!isFullscreen" @change="onSettingChange">
          <el-option value="pve" :label="$t('games.embedded.reversi.modePve')" />
          <el-option value="pvp" :label="$t('games.embedded.reversi.modePvp')" />
        </el-select>
        <el-select v-model="level" class="rv-level" :teleported="!isFullscreen" @change="onSettingChange">
          <el-option value="easy" :label="$t('games.embedded.reversi.levelEasy')" />
          <el-option value="normal" :label="$t('games.embedded.reversi.levelNormal')" />
          <el-option value="hard" :label="$t('games.embedded.reversi.levelHard')" />
        </el-select>
      </div>
      <el-checkbox v-model="aiFirst" class="rv-ai-first" @change="onSettingChange">
        {{ $t('games.embedded.reversi.aiFirst') }}
      </el-checkbox>
    </template>

    <template #rules>
      <p>{{ $t('games.embedded.reversi.tip') }}</p>
      <p>{{ $t('games.embedded.reversi.rules') }}</p>
    </template>

    <!-- 手机横屏:悔棋 + 棋子数 + 计分板搬进外壳左栏底边;竖屏用棋盘上方那条 HUD,这一份隐藏 -->
    <template #stats>
      <div class="rv-side">
        <div class="rv-side-score">
          <span class="rv-side-count">
            <span class="rv-dot black"></span>{{ $t('games.embedded.reversi.black') }}<b>{{ blackCount }}</b>
          </span>
          <span class="rv-side-count">
            <span class="rv-dot white"></span>{{ $t('games.embedded.reversi.white') }}<b>{{ whiteCount }}</b>
          </span>
        </div>
        <div class="rv-side-status">
          <b :class="['rv-side-line', { over: isOver }]">{{ statusLine }}</b>
          <span class="rv-side-sub">
            <span class="rv-dot" :class="turnDotCls"></span> <b>{{ turnText }}</b> · {{ $t('games.embedded.reversi.moveLabel') }}
            <b>{{ moveCountRef }}</b> · {{ $t('games.embedded.reversi.leadLabel') }} <b>{{ leadText }}</b>
          </span>
        </div>
        <el-button class="rv-side-undo" size="small" round :disabled="undoDisabled" @click="undo">
          {{ $t('games.embedded.reversi.undo') }}
        </el-button>
      </div>
    </template>

    <!-- 记分 HUD 贴棋盘:竖屏/桌面与棋盘同宽排在棋盘上方,手机横屏整条让位给外壳左栏底边 -->
    <div class="rv-layout" @contextmenu.prevent @dblclick.prevent>
      <div class="rv-meta gs-on-dark-surface">
        <div class="rv-actions">
          <el-button size="small" round :disabled="undoDisabled" @click="undo">
            {{ $t('games.embedded.reversi.undo') }}
          </el-button>
        </div>
        <div class="rv-score">
          <span class="rv-lab"><span class="rv-dot black"></span>{{ $t('games.embedded.reversi.black') }}</span>
          <b>{{ blackCount }}</b>
        </div>
        <div class="rv-score">
          <span class="rv-lab"><span class="rv-dot white"></span>{{ $t('games.embedded.reversi.white') }}</span>
          <b>{{ whiteCount }}</b>
        </div>
        <div class="rv-status-line">
          <span :class="['rv-status', { over: isOver }]">{{ statusLine }}</span>
          <span class="rv-sub">
            <span class="rv-dot" :class="turnDotCls"></span> <b>{{ turnText }}</b> · {{ $t('games.embedded.reversi.moveLabel') }}
            <b>{{ moveCountRef }}</b> · {{ $t('games.embedded.reversi.leadLabel') }} <b>{{ leadText }}</b>
          </span>
        </div>
      </div>

      <div class="rv-board-wrap">
        <div ref="boardEl" class="rv-board"></div>

        <div v-if="!isStarted" class="rv-overlay">
          <div class="rv-panel">
            <h2>{{ $t('games.embedded.reversi.title') }}</h2>
            <p>{{ $t('games.embedded.reversi.intro') }}</p>
            <el-button type="primary" size="large" round @click="startGame">
              {{ $t('games.embedded.reversi.start') }}
            </el-button>
          </div>
        </div>

        <div v-if="resultVisible" class="rv-overlay">
          <div class="rv-panel">
            <h2>{{ resultTitle }}</h2>
            <p>{{ resultScoreLine }}</p>
            <el-button size="small" round @click="startGame">{{ $t('games.embedded.reversi.playAgain') }}</el-button>
            <el-button size="small" round @click="resultVisible = false">
              {{ $t('games.embedded.reversi.close') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import GameShell from '@/components/games/GameShell.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const router = useRouter()
const { t, locale } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)
const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)
const tt = (key, vars) => t(`games.embedded.reversi.${key}`, vars)

/* ---------- Constants & state ---------- */
const EMPTY = 0,
  BLACK = 1,
  WHITE = 2
let board, turn, started, gameOver, lastMove, moveCount, undoStack, passSide, result

const boardEl = ref(null)
const mode = ref('pve')
const level = ref('normal')
const aiFirst = ref(false)
const blackCount = ref(2)
const whiteCount = ref(2)
const moveCountRef = ref(0)
const leadText = ref('0')
const turnText = ref('—')
const turnDotCls = ref('neutral')
const statusLine = ref('')
/** 引擎的 gameOver 是普通变量(命令式流程),模板用的这份是它的响应式映像 */
const isOver = ref(false)
/** 同上:开局遮罩的显隐必须走模板能追踪的 ref,不能直接读引擎里的 started */
const isStarted = ref(false)
const undoDisabled = ref(true)
const newDisabled = ref(true)
const resultVisible = ref(false)
const resultTitle = ref('')
const resultScoreLine = ref('')

/* Positional weights: corners are gold, X/C squares are traps. Positive favors BLACK. */
const WEIGHTS = [
  120, -20, 20, 5, 5, 20, -20, 120, -20, -40, -5, -5, -5, -5, -40, -20, 20, -5, 15, 3, 3, 15, -5, 20, 5, -5, 3, 3, 3, 3, -5, 5, 5, -5, 3, 3,
  3, 3, -5, 5, 20, -5, 15, 3, 3, 15, -5, 20, -20, -40, -5, -5, -5, -5, -40, -20, 120, -20, 20, 5, 5, 20, -20, 120,
]

const DIRS = [
  [-1, -1],
  [-1, 0],
  [-1, 1],
  [0, -1],
  [0, 1],
  [1, -1],
  [1, 0],
  [1, 1],
]

/* ---------- Pure rule helpers (operate on any board array) ---------- */
function idx(r, c) {
  return r * 8 + c
}
function inB(r, c) {
  return r >= 0 && r < 8 && c >= 0 && c < 8
}
function other(color) {
  return color === BLACK ? WHITE : BLACK
}

function initialBoard() {
  var b = new Array(64).fill(EMPTY)
  b[idx(3, 3)] = WHITE
  b[idx(4, 4)] = WHITE
  b[idx(3, 4)] = BLACK
  b[idx(4, 3)] = BLACK
  return b
}

/* Returns the list of indices that would flip if `color` is placed at (r,c). */
function flipsFor(bd, r, c, color) {
  if (!inB(r, c) || bd[idx(r, c)] !== EMPTY) return []
  var opp = other(color),
    out = [],
    i,
    rr,
    cc,
    line,
    d
  for (i = 0; i < DIRS.length; i++) {
    d = DIRS[i]
    rr = r + d[0]
    cc = c + d[1]
    line = []
    while (inB(rr, cc) && bd[idx(rr, cc)] === opp) {
      line.push(idx(rr, cc))
      rr += d[0]
      cc += d[1]
    }
    if (line.length && inB(rr, cc) && bd[idx(rr, cc)] === color) {
      for (var k = 0; k < line.length; k++) out.push(line[k])
    }
  }
  return out
}

function legalMovesOn(bd, color) {
  var out = [],
    i,
    r,
    c
  for (i = 0; i < 64; i++) {
    if (bd[i] !== EMPTY) continue
    r = Math.floor(i / 8)
    c = i % 8
    var f = flipsFor(bd, r, c, color)
    if (f.length) out.push({ r: r, c: c, flips: f })
  }
  return out
}

function hasMove(bd, color) {
  return legalMovesOn(bd, color).length > 0
}

/* 落子并把夹住的棋子翻面(对任意棋盘快照都用得上,搜索与实战共用) */
function applyOn(bd, mv, color) {
  bd[idx(mv.r, mv.c)] = color
  for (var i = 0; i < mv.flips.length; i++) bd[mv.flips[i]] = color
}

function countDiscs(bd) {
  var black = 0,
    white = 0
  for (var i = 0; i < 64; i++) {
    if (bd[i] === BLACK) black++
    else if (bd[i] === WHITE) white++
  }
  return { black: black, white: white }
}

/* Decide whose turn follows a move by `justMoved`, handling passes and end. */
function decideNext(bd, justMoved) {
  var nxt = other(justMoved)
  if (!hasMove(bd, nxt)) {
    if (hasMove(bd, justMoved)) return { turn: justMoved, pass: nxt }
    return { end: true }
  }
  return { turn: nxt }
}

/* ---------- Evaluation & search (reusable on arbitrary boards) ---------- */
function posScore(bd) {
  var s = 0
  for (var i = 0; i < 64; i++) {
    if (bd[i] === BLACK) s += WEIGHTS[i]
    else if (bd[i] === WHITE) s -= WEIGHTS[i]
  }
  return s
}
function evalFor(bd, color) {
  return color === BLACK ? posScore(bd) : -posScore(bd)
}
function terminal(bd, color) {
  var d = countDiscs(bd)
  var s = color === BLACK ? d.black - d.white : d.white - d.black
  return s === 0 ? 0 : s > 0 ? 100000 + s : -100000 + s
}

function minimax(bd, depth, alpha, beta, color, passes) {
  var moves = legalMovesOn(bd, color)
  if (moves.length === 0) {
    if (passes >= 1) return terminal(bd, color)
    return -minimax(bd, depth, -beta, -alpha, other(color), passes + 1)
  }
  if (depth <= 0) return evalFor(bd, color)
  moves.sort(function (a, b) {
    return WEIGHTS[b.r * 8 + b.c] - WEIGHTS[a.r * 8 + a.c]
  })
  var best = -Infinity,
    i,
    m,
    nb,
    v
  for (i = 0; i < moves.length; i++) {
    m = moves[i]
    nb = bd.slice()
    applyOn(nb, m, color)
    v = -minimax(nb, depth - 1, -beta, -alpha, other(color), 0)
    if (v > best) best = v
    if (best > alpha) alpha = best
    if (alpha >= beta) break
  }
  return best
}

/* Choose a move for `color` at level `lvl`. Defaults to the live game. */
function chooseAIMove(bd, color, lvl) {
  bd = bd || board
  color = color || aiColorFor()
  lvl = lvl || level.value
  var moves = legalMovesOn(bd, color)
  if (!moves.length) return null
  var i, m, nb, v, best, pool

  if (lvl === 'easy') {
    // Greedy: maximize the number of flipped discs, random tie-break.
    best = -1
    pool = []
    for (i = 0; i < moves.length; i++) {
      m = moves[i]
      v = m.flips.length
      if (v > best) {
        best = v
        pool = [m]
      } else if (v === best) pool.push(m)
    }
    return pool[Math.floor(Math.random() * pool.length)]
  }

  if (lvl === 'normal') {
    // 1-ply search + positional weight table.
    best = -Infinity
    pool = []
    for (i = 0; i < moves.length; i++) {
      m = moves[i]
      nb = bd.slice()
      applyOn(nb, m, color)
      v = posScore(nb)
      if (v > best) {
        best = v
        pool = [m]
      } else if (v === best) pool.push(m)
    }
    return pool[Math.floor(Math.random() * pool.length)]
  }

  // hard: minimax depth 4 (this move + 3 plies) with alpha-beta.
  best = -Infinity
  pool = []
  for (i = 0; i < moves.length; i++) {
    m = moves[i]
    nb = bd.slice()
    applyOn(nb, m, color)
    v = -minimax(nb, 3, -Infinity, Infinity, other(color), 0)
    if (v > best) {
      best = v
      pool = [m]
    } else if (v === best) pool.push(m)
  }
  return pool[Math.floor(Math.random() * pool.length)]
}

/* ---------- Which side is the AI? ---------- */
function aiColorFor() {
  return aiFirst.value ? BLACK : WHITE
}
function humanColor() {
  return other(aiColorFor())
}

/* ---------- Rendering (board 命令式渲染逐字保留;HUD 走 Vue 响应式) ---------- */
let cellEls = [],
  discEls = [],
  hintEls = [],
  curColor = []

function buildBoard() {
  boardEl.value.innerHTML = ''
  cellEls = []
  discEls = []
  hintEls = []
  curColor = new Array(64).fill(-1)
  var r, c, cell, disc, hn
  for (r = 0; r < 8; r++) {
    for (c = 0; c < 8; c++) {
      var i = idx(r, c)
      cell = document.createElement('div')
      cell.className = 'cell'
      disc = document.createElement('div')
      disc.className = 'disc empty'
      hn = document.createElement('span')
      hn.className = 'hintnum'
      cell.appendChild(disc)
      cell.appendChild(hn)
      ;(function (id) {
        cell.addEventListener('click', function () {
          onCellClick(id)
        })
      })(i)
      boardEl.value.appendChild(cell)
      cellEls.push(cell)
      discEls.push(disc)
      hintEls.push(hn)
    }
  }
}

function render() {
  if (!board) return
  var d = countDiscs(board)
  blackCount.value = d.black
  whiteCount.value = d.white
  moveCountRef.value = moveCount
  var lead = d.black - d.white
  leadText.value = lead === 0 ? tt('even') : lead > 0 ? '+' + lead : String(lead)

  statusLine.value = statusText()
  isOver.value = !!gameOver

  if (started && !gameOver) {
    turnText.value = turn === BLACK ? tt('black') : tt('white')
    turnDotCls.value = turn === BLACK ? 'black' : 'white'
  } else if (gameOver) {
    turnText.value = '—'
    turnDotCls.value = 'neutral'
  } else {
    turnText.value = tt('ready')
    turnDotCls.value = 'neutral'
  }

  var legal = started && !gameOver ? legalMovesOn(board, turn) : []
  var legalSet = {}
  for (var k = 0; k < legal.length; k++) legalSet[idx(legal[k].r, legal[k].c)] = legal[k].flips.length

  for (var i = 0; i < 64; i++) {
    var cell = cellEls[i],
      disc = discEls[i],
      v = board[i]
    if (v !== curColor[i]) {
      disc.className = 'disc ' + (v === BLACK ? 'black' : v === WHITE ? 'white' : 'empty')
      if (v !== EMPTY) disc.classList.add('flip')
      curColor[i] = v
    }
    if (legalSet[i] != null && v === EMPTY) {
      cell.classList.add('hint')
      cell.classList.remove('last')
      hintEls[i].textContent = legalSet[i]
    } else {
      cell.classList.remove('hint')
      hintEls[i].textContent = ''
      cell.classList.toggle('last', !!(lastMove && idx(lastMove.r, lastMove.c) === i))
    }
  }

  newDisabled.value = !started
  undoDisabled.value = !started || undoStack.length === 0
}

function statusText() {
  if (!started) return tt('pressStart')
  if (gameOver) return resultText()
  if (passSide) return tt(passSide === BLACK ? 'blackPass' : 'whitePass')
  return tt(turn === BLACK ? 'blackTurn' : 'whiteTurn')
}
function resultText() {
  if (result === 'draw') return tt('draw')
  if (result === 'blackWin') return tt('blackWin')
  if (result === 'whiteWin') return tt('whiteWin')
  if (result === 'aiWin') return tt('aiWin')
  if (result === 'youWin') return tt('youWin')
  return ''
}

/* ---------- Game flow ---------- */
function snapshot() {
  return { board: board.slice(), turn: turn, moveCount: moveCount, lastMove: lastMove, passSide: passSide }
}

function onCellClick(i) {
  if (!started || gameOver) return
  var r = Math.floor(i / 8),
    c = i % 8
  if (mode.value === 'pve' && turn !== humanColor()) return
  var moves = legalMovesOn(board, turn)
  var mv = null
  for (var k = 0; k < moves.length; k++) {
    if (moves[k].r === r && moves[k].c === c) {
      mv = moves[k]
      break
    }
  }
  if (!mv) return
  doPlay(mv, turn)
}

function doPlay(mv, color) {
  undoStack.push(snapshot())
  board[idx(mv.r, mv.c)] = color
  for (var i = 0; i < mv.flips.length; i++) board[mv.flips[i]] = color
  lastMove = { r: mv.r, c: mv.c }
  moveCount++
  afterPlay(color)
}

function afterPlay(color) {
  var next = decideNext(board, color)
  if (next.end) {
    endGame()
    return
  }
  turn = next.turn
  passSide = next.pass || null
  render()
  if (mode.value === 'pve' && turn === aiColorFor() && !gameOver) aiMove()
}

function aiMove() {
  var mv = chooseAIMove()
  if (!mv) {
    if (!hasMove(board, humanColor())) endGame()
    return
  }
  doPlay(mv, aiColorFor())
}

function endGame() {
  gameOver = true
  var d = countDiscs(board)
  if (d.black > d.white) {
    result = mode.value === 'pve' ? (aiColorFor() === BLACK ? 'aiWin' : 'youWin') : 'blackWin'
  } else if (d.white > d.black) {
    result = mode.value === 'pve' ? (aiColorFor() === WHITE ? 'aiWin' : 'youWin') : 'whiteWin'
  } else {
    result = 'draw'
  }
  showResult()
  render()
}

function showResult() {
  var d = countDiscs(board)
  resultTitle.value = resultText()
  resultScoreLine.value = tt('finalScore', { black: d.black, white: d.white, diff: Math.abs(d.black - d.white) })
  resultVisible.value = true
}

function startGame() {
  started = true
  isStarted.value = true
  board = initialBoard()
  turn = BLACK
  gameOver = false
  result = null
  lastMove = null
  moveCount = 0
  undoStack = []
  passSide = null
  resultVisible.value = false
  render()
}

function undo() {
  if (!undoStack || !undoStack.length) return
  var steps = mode.value === 'pve' ? 2 : 1
  for (var k = 0; k < steps && undoStack.length; k++) {
    var s = undoStack.pop()
    board = s.board.slice()
    turn = s.turn
    moveCount = s.moveCount
    lastMove = s.lastMove
    passSide = s.passSide
  }
  gameOver = false
  result = null
  resultVisible.value = false
  render()
}

function onSettingChange() {
  if (started) startGame()
}

function onKeydown(e) {
  if (e.key === 'u' || e.key === 'U') undo()
  else if (e.key === 'r' || e.key === 'R') startGame()
}

onMounted(() => {
  sunLight?.suspendEffects()
  buildBoard()

  board = initialBoard() // preview behind the start overlay
  started = false
  isStarted.value = false
  turn = BLACK
  gameOver = false
  lastMove = null
  moveCount = 0
  undoStack = []
  passSide = null
  result = null
  render()

  document.addEventListener('keydown', onKeydown)
})

// 语言切换:HUD/状态/结算全部重建(数据-i18n 静态节点之外的部分)
watch(locale, () => {
  render()
  if (resultVisible.value) showResult()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 原版深绿渐变整页底(--gs-bg,必须同时给 dark 档) */
.rv-shell {
  --gs-bg: linear-gradient(160deg, #1b2a22, #29503a);
  /* 记分板与状态条落在深绿整页底上:不用主题卡片色(浅色主题是白/奶油卡,深底上像贴了
     一块亮纸),改用半透明玻璃 + 浅色字,深浅主题同一套 */
  --rv-glass-bg: rgba(255, 255, 255, 0.08);
  --rv-glass-line: rgba(255, 255, 255, 0.16);
  --rv-ink: #fff;
  --rv-ink-dim: rgba(255, 255, 255, 0.72);
  --rv-accent: #a8e6a3;
}
html.dark .rv-shell {
  --gs-bg: linear-gradient(160deg, #122019, #1d3d2a);
}
.rv-setup {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.rv-mode {
  width: 96px;
}
.rv-level {
  width: 84px;
}
.rv-ai-first {
  height: var(--gs-control-h);
  margin-right: 6px;
  font-size: 13px;
}

/* 记分 HUD + 棋盘:竖屏自上而下,宽度同棋盘。
   棋盘宽度 = 剩余高度 × 宽高比,不设像素上限:直接铺满游戏区,格子和落点跟着变大 */
.rv-layout {
  --rv-board-w: min(100%, calc((100cqh - 128px) * 0.9677));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}
.rv-meta {
  display: flex;
  align-items: stretch;
  flex-wrap: wrap;
  gap: 8px;
  width: var(--rv-board-w);
  min-width: 0;
}
/* 按钮靠这条的右端;横屏时挪到棋盘左上 */
.rv-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-left: auto;
}
/* EP 给相邻按钮加 margin-left:12px,这里间距只走 gap */
.rv-actions .el-button + .el-button {
  margin-left: 0;
}
.rv-score {
  flex: 0 0 78px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--rv-glass-bg);
  border: 1px solid var(--rv-glass-line);
  border-radius: 10px;
  padding: 4px;
}
.rv-lab {
  font-size: 12px;
  color: var(--rv-ink-dim);
  display: flex;
  align-items: center;
  gap: 5px;
}
.rv-score b {
  font-size: 24px;
  line-height: 1.1;
  color: var(--rv-ink);
}
.rv-dot {
  width: 13px;
  height: 13px;
  border-radius: 50%;
  display: inline-block;
  box-shadow: inset 0 -2px 3px rgba(0, 0, 0, 0.4);
}
.rv-dot.black {
  background: radial-gradient(circle at 35% 30%, #6b6b6b, #111 70%);
}
.rv-dot.white {
  background: radial-gradient(circle at 35% 30%, #fff, #cfcfcf 70%);
}
.rv-dot.neutral {
  background: #9aa3a0;
}
.rv-status-line {
  flex: 1 1 auto;
  min-width: 120px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: var(--rv-glass-bg);
  border: 1px solid var(--rv-glass-line);
  border-radius: 10px;
  padding: 4px 8px;
}
.rv-status {
  font-size: 14px;
  font-weight: 700;
  text-align: center;
  min-height: 19px;
  /* 品牌棕在深绿玻璃底上只有 2:1,状态改用同色系的浅绿 */
  color: var(--rv-accent);
}
.rv-status.over {
  color: #ffb3a7;
}
.rv-sub {
  font-size: 12px;
  color: var(--rv-ink-dim);
  margin-top: 2px;
  text-align: center;
}
.rv-sub b {
  color: var(--rv-ink);
}

/* Board */
/* 宽度落在容器上:棋盘本身没有内在宽,靠上一层的 100% 解析会退化成 min-content(36px) */
.rv-board-wrap {
  position: relative;
  width: var(--rv-board-w);
  max-width: 100%;
}
.rv-board {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  aspect-ratio: 1;
  background: #2e7d32;
  border: 7px solid #5d4037;
  border-radius: 10px;
  padding: 4px;
  gap: 2px;
  box-shadow:
    0 10px 30px rgba(0, 0, 0, 0.45),
    inset 0 0 26px rgba(0, 0, 0, 0.4);
  touch-action: manipulation;
  user-select: none;
}
.rv-board :deep(.cell) {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 3px;
  background:
    linear-gradient(rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.05)),
    repeating-linear-gradient(0deg, transparent, transparent 1px, rgba(0, 0, 0, 0.28) 1px, rgba(0, 0, 0, 0.28) 2px),
    repeating-linear-gradient(90deg, transparent, transparent 1px, rgba(0, 0, 0, 0.28) 1px, rgba(0, 0, 0, 0.28) 2px);
}
.rv-board :deep(.cell.hint) {
  background: rgba(255, 255, 255, 0.1);
}
.rv-board :deep(.cell.hint .disc) {
  background: rgba(255, 255, 255, 0.22);
  box-shadow: none;
}
.rv-board :deep(.cell.last) {
  box-shadow: inset 0 0 0 3px rgba(255, 214, 0, 0.95);
}
.rv-board :deep(.disc) {
  width: 80%;
  height: 80%;
  border-radius: 50%;
  transition:
    background 0.25s ease,
    transform 0.25s ease;
}
.rv-board :deep(.disc.empty) {
  background: transparent !important;
  box-shadow: none;
}
.rv-board :deep(.disc.black) {
  background: radial-gradient(circle at 35% 30%, #7a7a7a, #0c0c0c 72%);
  box-shadow:
    inset 0 -3px 7px rgba(0, 0, 0, 0.6),
    0 2px 4px rgba(0, 0, 0, 0.4);
}
.rv-board :deep(.disc.white) {
  background: radial-gradient(circle at 35% 30%, #ffffff, #cfcfcf 72%);
  box-shadow:
    inset 0 -3px 7px rgba(0, 0, 0, 0.25),
    0 2px 4px rgba(0, 0, 0, 0.4);
}
.rv-board :deep(.disc.flip) {
  animation: rv-flip 0.42s ease;
}
@keyframes rv-flip {
  0% {
    transform: rotateY(0deg);
  }
  49% {
    transform: rotateY(90deg);
  }
  50% {
    transform: rotateY(90deg);
  }
  100% {
    transform: rotateY(0deg);
  }
}
.rv-board :deep(.hintnum) {
  position: absolute;
  font-size: 11px;
  font-weight: 700;
  color: #1d3b22;
  pointer-events: none;
  display: none;
}
.rv-board :deep(.cell.hint .hintnum) {
  display: block;
}

/* Overlays */
.rv-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 10px;
  z-index: 20;
  padding: 14px;
  text-align: center;
}
.rv-panel {
  background: var(--color-card, #fff);
  border-radius: 14px;
  padding: 22px 20px;
  max-width: 320px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}
.rv-panel h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: var(--color-text-primary, #1f2328);
}
.rv-panel p {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--color-text-secondary, #6b7280);
}

/* 手机横屏左栏底边的记分板:棋子数、状态、悔棋自上而下,窄栏里也读得清 */
.rv-side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  min-width: 0;
  padding: 6px 8px;
  background: var(--rv-glass-bg);
  border: 1px solid var(--rv-glass-line);
  border-radius: 10px;
}
.rv-side-score {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.rv-side-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--rv-ink-dim);
  white-space: nowrap;
}
.rv-side-count b {
  font-size: 16px;
  line-height: 1.1;
  color: var(--rv-ink);
  font-variant-numeric: tabular-nums;
}
.rv-side-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 0;
  text-align: center;
}
.rv-side-line {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--rv-accent);
}
.rv-side-line.over {
  color: #ffb3a7;
}
.rv-side-sub {
  font-size: 11px;
  line-height: 1.4;
  color: var(--rv-ink-dim);
}
.rv-side-sub b {
  color: var(--rv-ink);
}
.rv-side-undo {
  width: 100%;
  margin-left: 0;
}

/* 外壳里的记分板是「第二份摆放」:竖屏与桌面用棋盘上方那条 HUD,
   外壳左栏底边那块只在手机横屏出现(样式在 main.css 横屏档) */
.rv-shell :deep(.gs-stats) {
  display: none;
}

/* 手机横屏:HUD 整条让位,悔棋 + 棋子数 + 计分板进外壳左栏底边,棋盘独占右侧;
   对战模式与难度同处一行(见 .rv-setup),新游戏在外壳头部「规则」左边 */
@media (orientation: landscape) and (max-height: 520px) {
  /* 左栏加宽一点:一行两个选择器各得一半,「人机对战」四个字才放得下 */
  .page.game-shell.rv-shell {
    --gs-rail-w: clamp(168px, 24%, 210px);
  }
  .rv-shell :deep(.gs-stats) {
    display: flex;
  }
  /* 左栏窄:两个选择器各占一半,文字区才不会被挤没 */
  .rv-setup > .el-select {
    flex: 1 1 0;
    width: auto;
    min-width: 0;
  }
  .rv-setup :deep(.el-select__wrapper) {
    padding: 4px 6px;
    gap: 2px;
    font-size: 12px;
  }
  .rv-layout {
    --rv-board-w: min(100%, calc((100cqh - 16px) * 0.9677));
    max-height: 100%;
    justify-content: center;
  }
  .rv-meta {
    display: none;
  }
  .rv-board-wrap {
    max-width: 100%;
  }
}
</style>
