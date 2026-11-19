<!-- Based on litegame-hub (MIT © 2026 DongGuoTao), localized as a Vue component.
     中国象棋 · 人机对战:红方玩家 vs 三档 negamax 电脑,长将判负、悔棋、棋谱导出;
     走法生成/评估/搜索逐字保留原实现,本组件只换外壳与 i18n。
     规范见 docs/UI设计提示词.md §43;样式(木纹棋盘/选子高亮)为原版移植,配色收在 --xq-* -->
<template>
  <GameShell ref="shellEl" class="xq-shell" :title="$t('games.embedded.xiangqi.title')" body-align="center" @back="router.push('/games')">
    <!-- 难度与 AI 先手跟游戏名走(和数独模式/难度同一处) -->
    <template #titleExtra>
      <el-select v-model="level" class="xq-level" :teleported="!isFullscreen" @change="onLevelChange">
        <el-option :value="1" :label="$t('games.embedded.xiangqi.lv1')" />
        <el-option :value="3" :label="$t('games.embedded.xiangqi.lv3')" />
        <el-option :value="4" :label="$t('games.embedded.xiangqi.lv4')" />
      </el-select>
      <el-checkbox v-model="aiFirst" class="xq-ai-first" @change="newGame">{{ $t('games.embedded.xiangqi.aiFirst') }}</el-checkbox>
    </template>

    <template #rules>
      <p>{{ $t('games.embedded.xiangqi.tip') }}</p>
    </template>

    <!-- 状态 + 操作贴棋盘:与棋盘同宽排在棋盘上方;手机横屏收成棋盘左侧一列 -->
    <div class="xq-layout" @contextmenu.prevent @dblclick.prevent>
      <div class="xq-meta">
        <span class="xq-status">{{ statusText }}</span>
        <div class="xq-actions">
          <el-button size="small" round @click="newGame">{{ $t('games.embedded.xiangqi.restart') }}</el-button>
          <el-button size="small" round :disabled="aiThinking" @click="undoMove">
            {{ $t('games.embedded.xiangqi.undo') }}
          </el-button>
          <el-button size="small" round @click="exportRecord">{{ $t('games.embedded.xiangqi.export') }}</el-button>
        </div>
      </div>

      <div class="xq-board-wrap">
        <canvas ref="cvRef" class="xq-canvas" @click="onCanvasClick"></canvas>
        <Transition name="game-overlay">
          <div v-if="aiThinking" class="xq-think game-overlay">
            <div class="xq-think-box game-overlay-card">
              <div class="xq-spinner"></div>
              <span>{{ $t('games.embedded.xiangqi.thinking') }}</span>
            </div>
          </div>
        </Transition>
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
/** 命名空间快捷取词 */
const tt = (key, vars) => t(`games.embedded.xiangqi.${key}`, vars)

const cvRef = ref(null)
const statusText = ref('')
const level = ref(3)
const aiFirst = ref(false)
const aiThinking = ref(false)

/* ================= Basic data(原版逐字保留) ================= */
const ROWS = 10,
  COLS = 9,
  N = 90
const RED = 'r',
  BLACK = 'b'
const MATE = 1000000
const VAL = { K: 100000, R: 900, H: 400, C: 470, A: 200, E: 200, P: 100 }

/* 棋子字形是棋盘图形(与麻将牌面同类),不进 i18n */
const CN = {
  r: { K: '帥', A: '仕', E: '相', H: '傌', R: '俥', C: '炮', P: '兵' },
  b: { K: '將', A: '士', E: '象', H: '馬', R: '車', C: '砲', P: '卒' },
}

/* Piece-square tables (red's perspective, row 0 is black's back rank; black mirrors row -> 9-row) */
const T_P = [
  2, 2, 0, -8, -8, -8, 0, 2, 2, 2, 6, 12, 14, 16, 14, 12, 6, 2, 8, 16, 24, 24, 26, 24, 24, 16, 8, 14, 20, 28, 28, 30, 28, 28, 20, 14, 18,
  28, 32, 34, 36, 34, 32, 28, 18, 10, 0, 20, 0, 22, 0, 20, 0, 10, 6, 0, 12, 0, 16, 0, 12, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
  0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
]
const T_C = [
  -6, 4, 9, 16, 20, 16, 9, 4, -6, -6, 2, 11, 15, 17, 15, 11, 2, -6, -6, 4, 10, 14, 16, 14, 10, 4, -6, 0, 1, 4, 8, 10, 8, 4, 1, 0, 2, 3, 6,
  10, 12, 10, 6, 3, 2, 2, 4, 6, 12, 12, 12, 6, 4, 2, 2, 8, 4, 6, 10, 6, 4, 8, 2, -6, 2, 6, 6, 8, 6, 6, 2, -6, -14, -6, -2, 4, 6, 4, -2, -6,
  -14, -10, 0, 10, 6, 14, 6, 10, 0, -10,
]
const T_H = [
  4, 8, 16, 12, 4, 12, 16, 8, 4, 4, 10, 28, 16, 8, 16, 28, 10, 4, 12, 14, 16, 20, 18, 20, 16, 14, 12, 8, 24, 18, 24, 20, 24, 18, 24, 8, 6,
  16, 14, 18, 16, 18, 14, 16, 6, 4, 12, 16, 14, 12, 14, 16, 12, 4, 2, 6, 8, 6, 10, 6, 8, 6, 2, 4, 2, 8, 8, 4, 8, 8, 2, 4, -4, 2, 6, 8, -6,
  8, 6, 2, -4, 0, -4, 6, 8, -6, 8, 6, -4, 0,
]
const T_R = [
  14, 14, 12, 18, 16, 18, 12, 14, 14, 16, 20, 18, 24, 26, 24, 18, 20, 16, 12, 12, 12, 16, 14, 16, 12, 12, 12, 12, 18, 16, 22, 22, 22, 16,
  18, 12, 12, 14, 12, 18, 18, 18, 12, 14, 12, 12, 16, 14, 20, 21, 20, 14, 16, 12, -6, 4, 9, 16, 19, 16, 9, 4, -6, -6, 8, 13, 19, 22, 19, 13,
  8, -6, -6, 8, 13, 18, 18, 18, 13, 8, -6, -6, 8, 13, 19, 19, 19, 13, 8, -6,
]
const T_A = new Array(N).fill(0)
T_A[8 * 9 + 4] = 2
const T_E = new Array(N).fill(0)
T_E[7 * 9 + 4] = 2
const T_K = new Array(N).fill(0)
T_K[7 * 9 + 3] = 1
T_K[7 * 9 + 4] = 1
T_K[7 * 9 + 5] = 1
const TABLES = { K: T_K, A: T_A, E: T_E, H: T_H, R: T_R, C: T_C, P: T_P }

/* ================= Position and moves ================= */
let board, turn, selected, legalTargets, lastMove, gameOver, history, checking
let checkStreakMover = null,
  checkStreakCount = 0 // 长将计数:哪一方在连续将军、连续了几次
let moveLog = [] // 走棋记录(棋谱)

function initialBoard() {
  const b = new Array(N).fill(null)
  const back = ['R', 'H', 'E', 'A', 'K', 'A', 'E', 'H', 'R']
  for (let c = 0; c < 9; c++) {
    b[c] = { t: back[c], color: BLACK }
    b[9 * 9 + c] = { t: back[c], color: RED }
  }
  b[2 * 9 + 1] = { t: 'C', color: BLACK }
  b[2 * 9 + 7] = { t: 'C', color: BLACK }
  b[7 * 9 + 1] = { t: 'C', color: RED }
  b[7 * 9 + 7] = { t: 'C', color: RED }
  for (let c = 0; c < 9; c += 2) {
    b[3 * 9 + c] = { t: 'P', color: BLACK }
    b[6 * 9 + c] = { t: 'P', color: RED }
  }
  return b
}

const rc = (i) => [Math.floor(i / 9), i % 9]
const inB = (r, c) => r >= 0 && r < 10 && c >= 0 && c < 9
const inPalace = (r, c, color) => c >= 3 && c <= 5 && (color === RED ? r >= 7 : r <= 2)
const ownSide = (r, color) => (color === RED ? r >= 5 : r <= 4)

function findKing(b, color) {
  for (let i = 0; i < N; i++) {
    const p = b[i]
    if (p && p.color === color && p.t === 'K') return i
  }
  return -1
}

/* Pseudo-legal move generation */
function genMoves(b, color) {
  const moves = []
  for (let i = 0; i < N; i++) {
    const p = b[i]
    if (!p || p.color !== color) continue
    const [r, c] = rc(i)
    const add = (tr, tc) => {
      const j = tr * 9 + tc
      const q = b[j]
      if (!q || q.color !== color) moves.push({ from: i, to: j })
    }
    switch (p.t) {
      case 'K': {
        for (const [dr, dc] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ]) {
          const tr = r + dr,
            tc = c + dc
          if (inPalace(tr, tc, color)) add(tr, tc)
        }
        break
      }
      case 'A': {
        for (const [dr, dc] of [
          [1, 1],
          [1, -1],
          [-1, 1],
          [-1, -1],
        ]) {
          const tr = r + dr,
            tc = c + dc
          if (inPalace(tr, tc, color)) add(tr, tc)
        }
        break
      }
      case 'E': {
        for (const [dr, dc] of [
          [2, 2],
          [2, -2],
          [-2, 2],
          [-2, -2],
        ]) {
          const tr = r + dr,
            tc = c + dc
          if (!inB(tr, tc) || !ownSide(tr, color)) continue
          if (b[(r + dr / 2) * 9 + (c + dc / 2)]) continue // Blocked elephant eye
          add(tr, tc)
        }
        break
      }
      case 'H': {
        for (const [dr, dc] of [
          [1, 2],
          [2, 1],
          [-1, 2],
          [-2, 1],
          [1, -2],
          [2, -1],
          [-1, -2],
          [-2, -1],
        ]) {
          const tr = r + dr,
            tc = c + dc
          if (!inB(tr, tc)) continue
          // Horse leg: the orthogonal cell adjacent to the horse toward the target
          const lr = Math.abs(dr) === 2 ? r + Math.sign(dr) : r
          const lc = Math.abs(dr) === 2 ? c : c + Math.sign(dc)
          if (b[lr * 9 + lc]) continue
          add(tr, tc)
        }
        break
      }
      case 'R': {
        for (const [dr, dc] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ]) {
          let tr = r + dr,
            tc = c + dc
          while (inB(tr, tc)) {
            const q = b[tr * 9 + tc]
            if (!q) add(tr, tc)
            else {
              if (q.color !== color) add(tr, tc)
              break
            }
            tr += dr
            tc += dc
          }
        }
        break
      }
      case 'C': {
        for (const [dr, dc] of [
          [1, 0],
          [-1, 0],
          [0, 1],
          [0, -1],
        ]) {
          let tr = r + dr,
            tc = c + dc,
            screen = false
          while (inB(tr, tc)) {
            const q = b[tr * 9 + tc]
            if (!screen) {
              if (!q) add(tr, tc)
              else screen = true
            } else if (q) {
              if (q.color !== color) add(tr, tc)
              break
            }
            tr += dr
            tc += dc
          }
        }
        break
      }
      case 'P': {
        const fwd = color === RED ? -1 : 1
        if (inB(r + fwd, c)) add(r + fwd, c)
        if (!ownSide(r, color)) {
          // Crossed the river: can move sideways
          if (inB(r, c + 1)) add(r, c + 1)
          if (inB(r, c - 1)) add(r, c - 1)
        }
        break
      }
    }
  }
  return moves
}

/* Whether (r,c) is attacked by side `by` (including the facing-kings rule) */
function attacked(b, r, c, by) {
  // Lines: chariots, cannons (with screen), facing kings
  for (const [dr, dc] of [
    [1, 0],
    [-1, 0],
    [0, 1],
    [0, -1],
  ]) {
    let tr = r + dr,
      tc = c + dc,
      screen = false
    while (inB(tr, tc)) {
      const q = b[tr * 9 + tc]
      if (q) {
        if (!screen) {
          if (q.color === by) {
            if (q.t === 'R') return true
            if (q.t === 'K' && dc === 0) {
              // Same column with no piece in between => facing kings
              return true
            }
          }
          screen = true
        } else {
          if (q.color === by && q.t === 'C') return true
          break
        }
      }
      tr += dr
      tc += dc
    }
  }
  for (const [dr, dc] of [
    [1, 2],
    [2, 1],
    [-1, 2],
    [-2, 1],
    [1, -2],
    [2, -1],
    [-1, -2],
    [-2, -1],
  ]) {
    const tr = r + dr,
      tc = c + dc
    if (!inB(tr, tc)) continue
    const q = b[tr * 9 + tc]
    if (q && q.color === by && q.t === 'H') {
      // The horse leg sits orthogonally adjacent to the horse (piece q), toward the target
      const lr = Math.abs(dr) === 2 ? tr - Math.sign(dr) : tr
      const lc = Math.abs(dr) === 2 ? tc : tc - Math.sign(dc)
      if (!b[lr * 9 + lc]) return true
    }
  }
  if (by === RED) {
    let q = inB(r + 1, c) ? b[(r + 1) * 9 + c] : null
    if (q && q.color === RED && q.t === 'P') return true
    if (r <= 4)
      for (const d of [-1, 1]) {
        if (!inB(r, c + d)) continue
        q = b[r * 9 + c + d]
        if (q && q.color === RED && q.t === 'P') return true
      }
    // An adjacent enemy king also attacks (rare in practice but correct)
    for (const [dr, dc] of [
      [1, 0],
      [-1, 0],
      [0, 1],
      [0, -1],
    ]) {
      const tr = r + dr,
        tc = c + dc
      if (inB(tr, tc)) {
        const k = b[tr * 9 + tc]
        if (k && k.color === RED && k.t === 'K') return true
      }
    }
  } else {
    let q = inB(r - 1, c) ? b[(r - 1) * 9 + c] : null
    if (q && q.color === BLACK && q.t === 'P') return true
    if (r >= 5)
      for (const d of [-1, 1]) {
        if (!inB(r, c + d)) continue
        q = b[r * 9 + c + d]
        if (q && q.color === BLACK && q.t === 'P') return true
      }
    for (const [dr, dc] of [
      [1, 0],
      [-1, 0],
      [0, 1],
      [0, -1],
    ]) {
      const tr = r + dr,
        tc = c + dc
      if (inB(tr, tc)) {
        const k = b[tr * 9 + tc]
        if (k && k.color === BLACK && k.t === 'K') return true
      }
    }
  }
  return false
}

function inCheck(b, color) {
  const k = findKing(b, color)
  if (k < 0) return true
  const [r, c] = rc(k)
  return attacked(b, r, c, color === RED ? BLACK : RED)
}

function make(b, m) {
  const cap = b[m.to]
  b[m.to] = b[m.from]
  b[m.from] = null
  return cap
}
function unmake(b, m, cap) {
  b[m.from] = b[m.to]
  b[m.to] = cap || null
}

function legalMoves(b, color) {
  const out = []
  for (const m of genMoves(b, color)) {
    const cap = make(b, m)
    if (!inCheck(b, color)) out.push(m)
    unmake(b, m, cap)
  }
  return out
}

/* Whether a simulated move gives check (does not touch the real board; used for perpetual-check rules) */
function moveGivesCheck(mover, m) {
  const cap = make(board, m)
  const gc = inCheck(board, mover === RED ? BLACK : RED)
  unmake(board, m, cap)
  return gc
}

/* ================= Evaluation and search ================= */
function evaluate(b) {
  // Positive favors red
  let s = 0
  for (let i = 0; i < N; i++) {
    const p = b[i]
    if (!p) continue
    const r = Math.floor(i / 9),
      c = i % 9
    const tIdx = p.color === RED ? i : (9 - r) * 9 + c
    const v = VAL[p.t] + TABLES[p.t][tIdx]
    s += p.color === RED ? v : -v
  }
  return s
}

let _nodes = 0
function negamax(b, depth, alpha, beta, color, ply) {
  _nodes++
  const moves = legalMoves(b, color)
  if (moves.length === 0) return -MATE + ply // Checkmated or stalemated
  if (depth === 0) {
    const e = evaluate(b)
    return color === RED ? e : -e
  }
  // Captures first in move ordering
  moves.sort((x, y) => (b[y.to] ? VAL[b[y.to].t] : 0) - (b[x.to] ? VAL[b[x.to].t] : 0))
  const other = color === RED ? BLACK : RED
  let best = -Infinity
  for (const m of moves) {
    const cap = make(b, m)
    const sc = -negamax(b, depth - 1, -beta, -alpha, other, ply + 1)
    unmake(b, m, cap)
    if (sc > best) best = sc
    if (best > alpha) alpha = best
    if (alpha >= beta) break
  }
  return best
}

function searchBest(depth, randomTie) {
  _nodes = 0
  const moves = legalMoves(board, turn)
  if (!moves.length) return null
  moves.sort((x, y) => (board[y.to] ? VAL[board[y.to].t] : 0) - (board[x.to] ? VAL[board[x.to].t] : 0))
  let best = [],
    bestScore = -Infinity
  for (const m of moves) {
    const cap = make(board, m)
    const sc = -negamax(board, depth - 1, -Infinity, -bestScore - (randomTie ? 60 : 0), turn === RED ? BLACK : RED, 1)
    unmake(board, m, cap)
    if (sc > bestScore) {
      bestScore = sc
      best = [m]
    } else if (sc === bestScore) best.push(m)
    else if (randomTie && sc > bestScore - 40) best.push(m) // Low difficulty keeps some randomness
  }
  return best[Math.floor(Math.random() * best.length)]
}

/* ================= Rendering ================= */
const CELL = 54,
  MX = 34,
  MY = 34,
  PR = 24
const W = MX * 2 + CELL * 8,
  H = MY * 2 + CELL * 9
let ctx = null

function setupCanvas() {
  const cv = cvRef.value
  if (!cv) return
  ctx = cv.getContext('2d')
  const dpr = window.devicePixelRatio || 1
  cv.width = W * dpr
  cv.height = H * dpr
  // 显示尺寸交给 CSS(--xq-cv-w 的 min 三限响应式);位图固定 W×H×dpr 保证清晰
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}
const px = (c) => MX + c * CELL
const py = (r) => MY + r * CELL

function draw() {
  if (!ctx) return
  ctx.clearRect(0, 0, W, H)
  // Grid
  ctx.strokeStyle = '#7a5220'
  ctx.lineWidth = 1.2
  for (let r = 0; r < ROWS; r++) {
    ctx.beginPath()
    ctx.moveTo(px(0), py(r))
    ctx.lineTo(px(8), py(r))
    ctx.stroke()
  }
  for (let c = 0; c < COLS; c++) {
    ctx.beginPath()
    ctx.moveTo(px(c), py(0))
    ctx.lineTo(px(c), py(4))
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(px(c), py(5))
    ctx.lineTo(px(c), py(9))
    ctx.stroke()
  }
  // Thicker border
  ctx.lineWidth = 2.5
  ctx.strokeRect(px(0) - 4, py(0) - 4, CELL * 8 + 8, CELL * 9 + 8)
  ctx.lineWidth = 1.2
  // Palace diagonals
  for (const [r0, c0] of [
    [0, 3],
    [7, 3],
  ]) {
    ctx.beginPath()
    ctx.moveTo(px(c0), py(r0))
    ctx.lineTo(px(c0 + 2), py(r0 + 2))
    ctx.moveTo(px(c0 + 2), py(r0))
    ctx.lineTo(px(c0), py(r0 + 2))
    ctx.stroke()
  }
  // River(棋盘图形文字,不进 i18n)
  ctx.fillStyle = 'rgba(122,82,32,.75)'
  ctx.font = '26px KaiTi, serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('楚 河', px(2), py(4.5))
  ctx.fillText('漢 界', px(6), py(4.5))
  // Cannon/pawn position marks
  for (const [r, c] of [
    [2, 1],
    [2, 7],
    [7, 1],
    [7, 7],
  ])
    drawMarks(r, c)
  for (const [r, c] of [
    [3, 0],
    [3, 2],
    [3, 4],
    [3, 6],
    [3, 8],
    [6, 0],
    [6, 2],
    [6, 4],
    [6, 6],
    [6, 8],
  ])
    drawMarks(r, c)

  // Last move marker
  if (lastMove) {
    for (const i of [lastMove.from, lastMove.to]) {
      const [r, c] = rc(i)
      ctx.strokeStyle = 'rgba(30,120,220,.85)'
      ctx.lineWidth = 2
      ctx.strokeRect(px(c) - PR + 3, py(r) - PR + 3, (PR - 3) * 2, (PR - 3) * 2)
    }
    ctx.lineWidth = 1.2
  }
  // Legal targets
  if (selected !== null && legalTargets) {
    for (const j of legalTargets) {
      const [tr, tc] = rc(j)
      ctx.beginPath()
      if (board[j]) {
        ctx.strokeStyle = '#c0392b'
        ctx.lineWidth = 2.5
        ctx.arc(px(tc), py(tr), PR + 2, 0, 7)
        ctx.stroke()
      } else {
        ctx.fillStyle = 'rgba(39,174,96,.8)'
        ctx.arc(px(tc), py(tr), 7, 0, 7)
        ctx.fill()
      }
      ctx.lineWidth = 1.2
    }
  }
  // Pieces
  for (let i = 0; i < N; i++) {
    const p = board[i]
    if (p) {
      const [r, c] = rc(i)
      drawPiece(r, c, p, i === selected, checking === i)
    }
  }
}

function drawMarks(r, c) {
  ctx.strokeStyle = '#7a5220'
  const x = px(c),
    y = py(r),
    g = 5,
    l = 9
  const segs = []
  const corners = [
    [-1, -1],
    [1, -1],
    [-1, 1],
    [1, 1],
  ]
  for (const [sx, sy] of corners) {
    if (c === 0 && sx < 0) continue
    if (c === 8 && sx > 0) continue
    segs.push([x + sx * g, y + sy * g, x + sx * (g + l), y + sy * g])
    segs.push([x + sx * g, y + sy * g, x + sx * g, y + sy * (g + l)])
  }
  ctx.beginPath()
  for (const [x1, y1, x2, y2] of segs) {
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
  }
  ctx.stroke()
}

function drawPiece(r, c, p, sel, chk) {
  const x = px(c),
    y = py(r)
  // Shadow
  ctx.beginPath()
  ctx.arc(x + 2, y + 3, PR, 0, 7)
  ctx.fillStyle = 'rgba(0,0,0,.25)'
  ctx.fill()
  // Base
  const grad = ctx.createRadialGradient(x - 8, y - 10, 4, x, y, PR)
  grad.addColorStop(0, '#fffaf0')
  grad.addColorStop(1, '#e9d8b8')
  ctx.beginPath()
  ctx.arc(x, y, PR, 0, 7)
  ctx.fillStyle = grad
  ctx.fill()
  ctx.strokeStyle = p.color === RED ? '#b03020' : '#2a2a2a'
  ctx.lineWidth = sel ? 3 : 1.8
  ctx.stroke()
  // Inner ring
  ctx.beginPath()
  ctx.arc(x, y, PR - 4, 0, 7)
  ctx.strokeStyle = p.color === RED ? 'rgba(176,48,32,.6)' : 'rgba(42,42,42,.6)'
  ctx.lineWidth = 1
  ctx.stroke()
  if (sel) {
    ctx.beginPath()
    ctx.arc(x, y, PR + 3, 0, 7)
    ctx.strokeStyle = '#2e86c1'
    ctx.lineWidth = 2.5
    ctx.stroke()
  }
  if (chk) {
    ctx.beginPath()
    ctx.arc(x, y, PR + 5, 0, 7)
    ctx.strokeStyle = '#e74c3c'
    ctx.lineWidth = 3
    ctx.stroke()
  }
  ctx.fillStyle = p.color === RED ? '#b03020' : '#222'
  ctx.font = 'bold 24px KaiTi, "Microsoft YaHei", serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(CN[p.color][p.t], x, y + 1)
  ctx.lineWidth = 1.2
}

/* ================= Interaction and flow ================= */
let aiTimer = null
let disposed = false
const scheduleAI = (ms) => {
  clearTimeout(aiTimer)
  aiTimer = setTimeout(aiMove, ms)
}

/* Record one move into the notation (spectator coordinates: file 1-9 left to right, rank 1-10 bottom to top, red's back rank is 1) */
function recordMove(m, mover, cap) {
  const pt = board[m.to].t
  const name = CN[mover][pt]
  const [fr, fc] = rc(m.from)
  const [tr, tc] = rc(m.to)
  const fcV = fc + 1,
    frV = 10 - fr,
    tcV = tc + 1,
    trV = 10 - tr
  const capName = cap ? CN[cap.color][cap.t] : null
  const side = mover === RED ? tt('redShort') : tt('blackShort')
  const n = moveLog.length + 1
  const text = `${n}. ${side}${name} (${fcV},${frV})→(${tcV},${trV})` + (capName ? tt('capture') + capName : '')
  moveLog.push({ n, side, name, text })
}

function refreshCheckMark() {
  checking = -1
  if (!gameOver && inCheck(board, turn)) {
    checking = findKing(board, turn)
  }
}

function newGame() {
  board = initialBoard()
  turn = RED
  selected = null
  legalTargets = null
  lastMove = null
  gameOver = false
  history = []
  checking = -1
  checkStreakMover = null
  checkStreakCount = 0
  moveLog = []
  statusText.value = tt('redTurn')
  draw()
  if (aiFirst.value) {
    aiThinking.value = true
    statusText.value = tt('thinking')
    scheduleAI(200)
  }
}

function afterMove(m, cap, mover) {
  history.push({ m, cap })
  recordMove(m, mover, cap)
  lastMove = m
  turn = mover === RED ? BLACK : RED
  selected = null
  legalTargets = null
  refreshCheckMark()

  // —— Perpetual-check detection: three checks in a row by the same side loses ——
  const opp = mover === RED ? BLACK : RED
  const gc = inCheck(board, opp)
  if (gc) {
    if (checkStreakMover === mover) checkStreakCount++
    else {
      checkStreakMover = mover
      checkStreakCount = 1
    }
    if (checkStreakCount >= 3) {
      // Perpetual check is a violation: loss
      gameOver = true
      const loser = mover === RED ? tt('red') : tt('black')
      const winner = mover === RED ? tt('black') : tt('red')
      statusText.value = tt('perpLose', { loser, winner })
      draw()
      return
    }
  } else if (checkStreakMover === mover) {
    // The mover made a non-checking move: only the side that was running the check streak loses it.
    checkStreakMover = null
    checkStreakCount = 0
  }

  const moves = legalMoves(board, turn)
  if (!moves.length) {
    gameOver = true
    const loserName = turn === RED ? tt('red') : tt('black')
    const winnerName = turn === RED ? tt('black') : tt('red')
    statusText.value = tt('gameEnd', {
      loser: loserName,
      reason: checking >= 0 ? tt('mated') : tt('stalemate'),
      winner: winnerName,
    })
    draw()
    return
  }
  if (checking >= 0) {
    statusText.value = tt('check', { side: turn === RED ? tt('red') : tt('black') })
  } else {
    statusText.value = turn === RED ? tt('redTurn') : tt('thinking')
  }
  draw()
  if (!gameOver && turn === BLACK) {
    aiThinking.value = true
    scheduleAI(120)
  }
}

/* ================= AI moves ================= */
function aiMove() {
  if (disposed) return
  statusText.value = tt('thinking')
  let mv = searchBest(level.value, level.value === 1)
  aiThinking.value = false
  if (!mv) {
    gameOver = true
    statusText.value = tt('redWin')
    draw()
    return
  }
  // Perpetual-check avoidance: if this move would be black's third consecutive check, play a non-checking move instead
  if (moveGivesCheck(BLACK, mv)) {
    const projected = (checkStreakMover === BLACK ? checkStreakCount : 0) + 1
    if (projected >= 3) {
      const alts = legalMoves(board, BLACK).filter((m) => !moveGivesCheck(BLACK, m))
      if (alts.length) mv = alts[Math.floor(Math.random() * alts.length)]
    }
  }
  const cap = make(board, mv)
  afterMove(mv, cap, BLACK)
}

function onCanvasClick(e) {
  if (gameOver || aiThinking.value || turn !== RED) return
  const cv = cvRef.value
  const rect = cv.getBoundingClientRect()
  // CSS 可缩放显示尺寸:按 rect/W 把视口坐标换算回逻辑坐标
  const k = rect.width ? W / rect.width : 1
  const x = (e.clientX - rect.left) * k,
    y = (e.clientY - rect.top) * k
  const c = Math.round((x - MX) / CELL),
    r = Math.round((y - MY) / CELL)
  if (!inB(r, c)) return
  if (Math.hypot(x - px(c), y - py(r)) > PR + 6) return
  const i = r * 9 + c,
    p = board[i]

  if (selected !== null && legalTargets.includes(i)) {
    const mv = { from: selected, to: i }
    // Perpetual check: the same move cannot be a third consecutive check
    if (moveGivesCheck(RED, mv)) {
      const projected = (checkStreakMover === RED ? checkStreakCount : 0) + 1
      if (projected >= 3) {
        statusText.value = tt('perpWarn')
        selected = null
        legalTargets = null
        draw()
        return
      }
    }
    const cap = make(board, mv)
    afterMove(mv, cap, RED)
    return
  }
  if (p && p.color === RED) {
    selected = i
    legalTargets = legalMoves(board, RED)
      .filter((m) => m.from === i)
      .map((m) => m.to)
    if (!legalTargets.length) {
      selected = null
      legalTargets = null
      statusText.value = tt('noLegal')
    }
  } else {
    selected = null
    legalTargets = null
  }
  draw()
}

/* 悔棋:一次撤掉黑方上一手 + 红方上一手,回到红方行棋 */
function undoMove() {
  if (aiThinking.value || !history.length) return
  const undoOne = () => {
    const h = history.pop()
    unmake(board, h.m, h.cap)
    turn = board[h.m.from] ? board[h.m.from].color : RED // Restore the mover's turn
    lastMove = history.length ? history[history.length - 1].m : null
  }
  undoOne()
  if (turn === BLACK && history.length) undoOne()
  gameOver = false
  selected = null
  legalTargets = null
  checkStreakMover = null
  checkStreakCount = 0 // 悔棋后重置长将计数
  refreshCheckMark()
  statusText.value = tt('redTurnUndo')
  draw()
}

const LEVEL_KEYS = { 1: 'lv1', 3: 'lv3', 4: 'lv4' }
const levelName = () => tt(LEVEL_KEYS[level.value])

function onLevelChange() {
  statusText.value = tt('diffChanged', { name: levelName() })
}

/* Export the game notation: spectator coordinates, downloadable as a txt file */
function exportRecord() {
  if (!moveLog.length) {
    statusText.value = tt('noRecord')
    return
  }
  let txt = tt('recTitle')
  txt += tt('recTime') + new Date().toLocaleString() + '\n'
  txt += tt('recLevel') + levelName() + tt('recSides')
  txt += tt('recCoords')
  txt += '--------------------------------\n'
  moveLog.forEach((e2) => {
    txt += e2.text + '\n'
  })
  if (gameOver) txt += '--------------------------------\n' + tt('recResult') + statusText.value + '\n'
  const blob = new Blob([txt], { type: 'text/plain;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = (locale.value.toLowerCase().startsWith('zh') ? 'xiangqi_棋谱_' : 'xiangqi_record_') + Date.now() + '.txt'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(a.href)
  statusText.value = tt('exported', { n: moveLog.length })
}

// Refresh the status line when the language changes.
watch(locale, () => {
  if (gameOver) return
  if (aiThinking.value) statusText.value = tt('thinking')
  else statusText.value = tt('redTurn')
  draw()
})

onMounted(() => {
  sunLight?.suspendEffects()
  setupCanvas()
  newGame()
})

onBeforeUnmount(() => {
  disposed = true
  clearTimeout(aiTimer)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 原版深绿渐变整页底(--gs-bg,必须同时给 dark 档) */
.xq-shell {
  --gs-bg: linear-gradient(135deg, #2c3e50, #4a6741);
}
html.dark .xq-shell {
  --gs-bg: linear-gradient(135deg, #1e2c39, #375032);
}
.xq-level {
  width: 92px;
}
.xq-ai-first {
  height: var(--gs-control-h);
  margin-right: 6px;
  font-size: 13px;
}

/* 数据条贴棋盘:与棋盘同宽排在棋盘上方 */
.xq-layout {
  --xq-cv-w: min(100%, 500px, calc((100cqh - 64px) * 0.9026));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}
.xq-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  width: var(--xq-cv-w);
  min-width: 0;
}
.xq-status {
  flex: 1 1 auto;
  min-height: var(--gs-control-h);
  display: flex;
  align-items: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-primary, #444);
}
html.dark .xq-status {
  color: #ffe9a8;
}
/* 按钮靠这条的右端;横屏时挪到棋盘左上 */
.xq-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-left: auto;
}
/* EP 给相邻按钮加 margin-left:12px,这里间距只走 gap */
.xq-actions .el-button + .el-button {
  margin-left: 0;
}

.xq-board-wrap {
  position: relative;
  /* 原版木色棋盘底,深浅主题同一块木头;不再留无人覆盖的 --xq-wood 间接层 */
  background: #e8c99b;
  border-radius: 10px;
  box-shadow:
    0 8px 30px rgba(0, 0, 0, 0.5),
    inset 0 0 60px rgba(139, 90, 43, 0.25);
  padding: 6px;
}
.xq-canvas {
  display: block;
  width: var(--xq-cv-w);
  height: auto;
  border-radius: 6px;
}

/* 思考浮层:盖住棋盘挡输入 */
.xq-think {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  border-radius: 10px;
  cursor: wait;
}
.xq-think-box {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(30, 30, 30, 0.92);
  color: #ffe9a8;
  padding: 12px 22px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: bold;
  letter-spacing: 1px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
}
.xq-spinner {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 3px solid rgba(255, 233, 168, 0.25);
  border-top-color: #ffe9a8;
  animation: xq-thinkspin 0.8s linear infinite;
}
@keyframes xq-thinkspin {
  to {
    transform: rotate(360deg);
  }
}

/* 手机横屏:状态 + 按钮收成棋盘左侧一列(按钮贴棋盘一侧靠上,状态整条),棋盘独占右侧 */
@media (orientation: landscape) and (max-height: 520px) {
  .xq-layout {
    --xq-meta-w: 132px;
    --xq-cv-w: min(100%, 500px, calc((100cqh - 16px) * 0.9026));
    display: grid;
    grid-template-columns: var(--xq-meta-w) minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
    grid-template-areas: 'meta board';
    column-gap: 10px;
    width: min(100%, calc(var(--xq-meta-w) + 10px + var(--xq-cv-w) + 16px));
    max-height: 100%;
  }
  .xq-meta {
    grid-area: meta;
    flex-direction: column;
    flex-wrap: nowrap;
    align-items: stretch;
    justify-content: start;
    align-self: start;
    width: 100%;
    height: auto;
    gap: 8px;
  }
  .xq-actions {
    order: -1;
    justify-content: flex-end;
    margin-left: 0;
  }
  .xq-actions .el-button + .el-button {
    margin-left: 0;
  }
  .xq-board-wrap {
    grid-area: board;
    align-self: center;
    max-width: 100%;
  }
}
</style>
