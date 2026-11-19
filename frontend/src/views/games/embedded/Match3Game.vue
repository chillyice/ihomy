<!-- Based on litegame-hub (MIT © 2026 DongGuoTao), localized as a Vue component.
     消消乐:交换相邻宝石连成 3+,连锁倍率计分,三档难度(色数/步数/目标)、提示/重排;
     匹配/重力/补位/洗牌/难度种子逐字保留原实现,DOM 网格渲染与 rAF 状态机零改动,只换外壳与 i18n。 -->
<template>
  <GameShell
    ref="shellEl"
    class="m3-shell"
    :title="$t('games.embedded.match3.title')"
    body-align="center"
    new-game
    :new-game-disabled="newDisabled"
    @new-game="restart"
    @back="router.push('/games')"
  >
    <!-- 难度跟游戏名走(和象棋/黑白棋同一处),开局后锁住 -->
    <template #titleExtra>
      <el-select
        v-model="diffKey"
        class="m3-diff"
        :disabled="diffDisabled"
        :teleported="!isFullscreen"
        :aria-label="$t('games.embedded.match3.difficulty')"
        @change="onDiffChange"
      >
        <el-option value="easy" :label="$t('games.embedded.match3.diff_easy')" />
        <el-option value="normal" :label="$t('games.embedded.match3.diff_normal')" />
        <el-option value="hard" :label="$t('games.embedded.match3.diff_hard')" />
      </el-select>
    </template>

    <template #rules>
      <p>{{ $t('games.embedded.match3.tip') }}</p>
      <p>{{ $t('games.embedded.match3.aim') }}</p>
    </template>

    <!-- 记分 HUD + 操作贴棋盘:与棋盘同宽排在棋盘上方;手机横屏记分收成棋盘左侧一列,
         提示/重排挪到棋盘右侧(竖排两键),新游戏在外壳头部「规则」左边 -->
    <div class="m3-layout" @contextmenu.prevent @dblclick.prevent>
      <div class="m3-meta">
        <div class="m3-stats">
          <span v-for="chip in chips" :key="chip.key" class="m3-stat">
            <small>{{ tt(chip.key) }}</small>
            <b>{{ chip.value }}</b>
          </span>
        </div>
        <div class="m3-actions">
          <el-button size="small" round :disabled="hintDisabled" @click="doHint">
            {{ $t('games.embedded.match3.hint') }}
          </el-button>
          <el-button size="small" round :disabled="shuffleDisabled" @click="doShuffle">
            {{ $t('games.embedded.match3.shuffle') }}
          </el-button>
        </div>
        <p class="m3-notice">{{ noticeText }}</p>
      </div>

      <div class="m3-board-wrap">
        <div
          ref="boardEl"
          class="m3-board"
          @click="onBoardClick"
          @pointerdown="onBoardDown"
          @pointermove="onBoardMove"
          @pointerup="onBoardUp"
          @pointercancel="onBoardCancel"
        ></div>
        <div ref="fxLayerEl" class="m3-fx"></div>

        <!-- 开始遮罩:原作入口,难度已在头部选择,这里只留说明与开始 -->
        <div v-if="readyVisible" class="m3-overlay">
          <div class="m3-panel">
            <h2>{{ $t('games.embedded.match3.title') }}</h2>
            <p>{{ $t('games.embedded.match3.aim') }}</p>
            <el-button type="primary" size="large" round @click="startGame">
              {{ $t('games.embedded.match3.start') }}
            </el-button>
          </div>
        </div>

        <!-- 结算遮罩:点空白处收起(与原作一致),按钮再来一局 -->
        <div v-if="resultVisible" class="m3-overlay" @click.self="resultVisible = false">
          <div class="m3-panel">
            <h2>{{ resultTitle }}</h2>
            <p>{{ resultSub }}</p>
            <el-button type="primary" size="small" round @click="restart">
              {{ $t('games.embedded.match3.replay') }}
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
const tt = (key, vars) => t('games.embedded.match3.' + key, vars)

/* ---------- 模板可见状态(引擎里的普通变量 → 响应式映像;原版状态口径不动) ---------- */
const boardEl = ref(null)
const fxLayerEl = ref(null)
const diffKey = ref('normal') // 难度选择框双向绑定,是难度的唯一来源
const scoreNum = ref(0)
const movesNum = ref(0)
const targetNum = ref(0)
const bestNum = ref(0)
const comboText = ref('-')
const levelLabel = ref('')
const noticeText = ref('')
/** 引擎 status 的映像:开始遮罩显隐只能追踪 ref */
const statusRef = ref('ready')
const resultTitle = ref('')
const resultSub = ref('')
const resultVisible = ref(false)
const newDisabled = ref(true)
const hintDisabled = ref(true)
const shuffleDisabled = ref(true)
const diffDisabled = ref(false)
const readyVisible = computed(() => statusRef.value === 'ready')
/** 记分芯片(顺序固定:得分/步数/目标/最佳/连击/难度),标签走 tt 随语言切换 */
const chips = computed(() => [
  { key: 'score', value: scoreNum.value },
  { key: 'moves', value: movesNum.value },
  { key: 'target', value: targetNum.value },
  { key: 'best', value: bestNum.value },
  { key: 'combo', value: comboText.value },
  { key: 'level', value: levelLabel.value },
])

/* -------------------------------------------------------------- constants(原版逐字保留) */
var ROWS = 8 // board height in cells
var COLS = 8 // board width in cells
var BASE = 10 // base score per cleared gem

/* Gem colours. Index 0..6; a difficulty uses the first N of them. */
var PALETTE = ['#f43f5e', '#f59e0b', '#22c55e', '#3b82f6', '#a855f7', '#06b6d4', '#ec4899']

/* Difficulty table: colour count, move limit and score target. */
var DIFF = {
  easy: { colors: 5, moves: 30, target: 1000 },
  normal: { colors: 6, moves: 25, target: 1500 },
  hard: { colors: 7, moves: 20, target: 2000 },
}

/* Phase durations (ms). The CSS animations are tuned to match, and the state
 * machine still owns all timing: an animation that never plays (a reduced-motion
 * user, a background tab) changes nothing about the flow. */
var SWAP_MS = 190 // tiles slide across, then the machine parks
var REVERT_MS = 190 // illegal swap: shake, then restore
var CLEAR_MS = 320 // pop -> white flash -> shrink + spin out
var FALL_MS = 300 // gravity drop with a landing squash
var REFILL_MS = 220 // new gems drop in from above the board
var SHUFFLE_MS = 320

/* --------------------------------------------------------------- state */
var rows = ROWS // live board dimensions
var cols = COLS
var grid = [] // grid[r][c] = colour index, or null when empty
var status = 'ready' // 'ready' | 'playing' | 'over'
var config = DIFF.normal // active difficulty config
var score = 0
var moves = 0
var target = 0
var best = 0
var combo = 0 // cascade round of the current move
var selected = null // [r, c] of the selected cell, or null

var phase = 'idle' // animation state machine (see header)
var phaseElapsed = 0
var phaseDuration = 0
var pendingClear = null // cells playing the 'clear' animation
var revertCells = null // two cells playing the 'bad' (revert) animation
var hintCells = null // two cells highlighted by the Hint button
var hintTimer = 0 // ms left on the hint highlight
var noticeKey = null // i18n key for the status line
var noticeVars = null // interpolation values for the status line
var endWin = false // result of the finished game
var cascadeDepth = 0 // safety cap on refill->clear loops
var dragFrom = null // [r, c] where a drag started
var dragStart = null // {x, y} client coords where the drag started
var dragLimit = 0 // swipe trigger distance sampled at pointerdown
var suppressClick = false // swallow the click that follows a drag
var rng = Math.random // swappable for a seeded generator
var lastFrameTs = 0 // previous rAF timestamp
var cellEls = [] // cellEls[r][c] -> the tile element

/* ---------------------------------------------------------- fx state
 * `fxAt[r][c]` is a transient per-tile animation descriptor ({cls, vars}) that
 * `render()` folds into the tile's className. render() rebuilds the WHOLE
 * className every pass, so an effect is set once on phase entry (and cleared on
 * phase exit) through this table — the animation then always starts cleanly. */
var fxAt = [] // fxAt[r][c] = { cls: 'swap', vars: {...} } | null
var fxNodes = [] // decorations living on .m3-fx: [{el, ttl}]
var FX_MAX_NODES = 90 // hard ceiling on live decoration nodes
var FX_MAX_BURSTS = 14 // at most this many particle bursts per clear
var boardShakeTtl = 0 // ms left on the board shake class

/* ======================================================================
 * Pure helpers — safe to call with any rectangular grid: they never mutate
 * their argument.
 * ==================================================================== */

/* Dimensions of an arbitrary grid, tolerating ragged rows. */
function gridDims(g) {
  var r
  var h = g && g.length ? g.length : 0
  var w = 0
  for (r = 0; r < h; r++) {
    if (g[r] && g[r].length > w) w = g[r].length
  }
  return [h, w]
}

/* Read a colour, or null for an empty / out-of-range cell. */
function cellVal(g, r, c) {
  if (!g || r < 0 || r >= g.length) return null
  var row = g[r]
  if (!row || c < 0 || c >= row.length) return null
  var v = row[c]
  return v === null || v === undefined || v < 0 ? null : v
}

/* Deep copy of a rectangular grid. */
function copyGrid(g) {
  var out = []
  for (var r = 0; r < g.length; r++) out.push(g[r] ? g[r].slice() : [])
  return out
}

/* All cells that belong to a horizontal or vertical run of >= 3 equal colours.
 * Returns an array of [r, c] pairs, de-duplicated. Pure. */
function findMatches(g) {
  var dims = gridDims(g)
  var h = dims[0],
    w = dims[1]
  var out = []
  var seen = {}
  var r, c

  function push(rr, cc) {
    var k = rr + ':' + cc
    if (!seen[k]) {
      seen[k] = 1
      out.push([rr, cc])
    }
  }
  function filled(rr, cc) {
    var v = cellVal(g, rr, cc)
    return v !== null
  }

  // horizontal runs
  for (r = 0; r < h; r++) {
    var start = 0
    for (c = 1; c <= w; c++) {
      var cont = c < w && filled(r, c) && filled(r, c - 1) && cellVal(g, r, c) === cellVal(g, r, c - 1)
      if (!cont) {
        if (c - start >= 3 && filled(r, start)) {
          for (var k = start; k < c; k++) push(r, k)
        }
        start = c
      }
    }
  }
  // vertical runs
  for (c = 0; c < w; c++) {
    var top = 0
    for (r = 1; r <= h; r++) {
      var cont2 = r < h && filled(r, c) && filled(r - 1, c) && cellVal(g, r, c) === cellVal(g, r - 1, c)
      if (!cont2) {
        if (r - top >= 3 && filled(top, c)) {
          for (var k2 = top; k2 < r; k2++) push(k2, c)
        }
        top = r
      }
    }
  }
  return out
}

/* Whether a grid contains any match at all. Pure, early-exit. */
function hasAnyMatch(g) {
  return findMatches(g).length > 0
}

/* True when swapping (r1,c1) with (r2,c2) would create a match. Pure. */
function swapMakesMatch(g, r1, c1, r2, c2) {
  var t = copyGrid(g)
  var tmp = t[r1][c1]
  t[r1][c1] = t[r2][c2]
  t[r2][c2] = tmp
  return findMatches(t).length > 0
}

/* Whether any single adjacent swap on the board produces a match. Pure. */
function hasMove(g) {
  var dims = gridDims(g)
  var h = dims[0],
    w = dims[1]
  for (var r = 0; r < h; r++) {
    for (var c = 0; c < w; c++) {
      if (c + 1 < w && swapMakesMatch(g, r, c, r, c + 1)) return true
      if (r + 1 < h && swapMakesMatch(g, r, c, r + 1, c)) return true
    }
  }
  return false
}

/* The first swap that would score, as [r1, c1, r2, c2], or null. Pure. */
function findHintMove(g) {
  var dims = gridDims(g)
  var h = dims[0],
    w = dims[1]
  for (var r = 0; r < h; r++) {
    for (var c = 0; c < w; c++) {
      if (c + 1 < w && swapMakesMatch(g, r, c, r, c + 1)) return [r, c, r, c + 1]
      if (r + 1 < h && swapMakesMatch(g, r, c, r + 1, c)) return [r, c, r + 1, c]
    }
  }
  return null
}

/* Gravity: every gem falls to the lowest free cell of its column, empties end
 * up on top. Returns a new grid. Pure. */
function applyGravity(g) {
  var dims = gridDims(g)
  var h = dims[0],
    w = dims[1]
  var out = []
  var r, c
  for (r = 0; r < h; r++) {
    var row = []
    for (c = 0; c < w; c++) row.push(null)
    out.push(row)
  }
  for (c = 0; c < w; c++) {
    var write = h - 1
    for (r = h - 1; r >= 0; r--) {
      var v = cellVal(g, r, c)
      if (v !== null) {
        out[write][c] = v
        write--
      }
    }
  }
  return out
}

/* Fill every empty cell with a random colour in [0, colorCount). Existing
 * values are preserved. Returns a new grid. Pure. */
function refill(g, colorCount) {
  var cc = typeof colorCount === 'number' && colorCount > 0 ? Math.floor(colorCount) : 1
  var dims = gridDims(g)
  var h = dims[0],
    w = dims[1]
  var out = []
  for (var r = 0; r < h; r++) {
    var row = []
    for (var c = 0; c < w; c++) {
      var v = cellVal(g, r, c)
      row.push(v === null ? randInt(cc) : v)
    }
    out.push(row)
  }
  return out
}

/* ------------------------------------------------------------- rng utils */
/* Integer in [0, n) drawn from the active generator. */
function randInt(n) {
  return Math.floor(rng() * n)
}

/* A seeded mulberry32 generator; accepts a number or a string seed. */
function makeRng(seed) {
  var s = seed
  if (typeof s === 'string') {
    var h = 2166136261
    for (var i = 0; i < s.length; i++) {
      h ^= s.charCodeAt(i)
      h = Math.imul(h, 16777619) >>> 0
    }
    s = h
  }
  s = Number(s)
  if (isNaN(s) || s === Infinity || s === -Infinity) s = 1
  s = s >>> 0
  if (s === 0) s = 0x9e3779b9
  return function () {
    s = (s + 0x6d2b79f5) >>> 0
    var t = s
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/* Fisher–Yates shuffle of an array in place, using the active generator. */
function shuffleArr(a) {
  for (var i = a.length - 1; i > 0; i--) {
    var j = Math.floor(rng() * (i + 1))
    var t = a[i]
    a[i] = a[j]
    a[j] = t
  }
}

/* ======================================================================
 * Board construction — always yields a board with NO pre-existing match and
 * at least one legal swap, so a fresh game can never be dead on arrival.
 * ==================================================================== */

/* Randomly fill a board that has no run of 3 (constraints on the two cells to
 * the left and the two above). No move guarantee — callers verify. */
function buildNoMatchBoard(colors) {
  var cc = colors && colors > 0 ? colors : 3
  var g = []
  for (var r = 0; r < rows; r++) {
    var row = []
    for (var c = 0; c < cols; c++) {
      var banned = {}
      var l1 = c >= 1 ? row[c - 1] : null
      var l2 = c >= 2 ? row[c - 2] : null
      if (l1 !== null && l1 === l2) banned[l1] = 1
      var u1 = r >= 1 ? g[r - 1][c] : null
      var u2 = r >= 2 ? g[r - 2][c] : null
      if (u1 !== null && u1 === u2) banned[u1] = 1
      var v,
        tries = 0
      do {
        v = randInt(cc)
        tries++
      } while (banned[v] && tries < 64)
      row.push(v)
    }
    g.push(row)
  }
  return g
}

/* Deterministic last-resort board: plant an L that is one swap away from a
 * line, then verify the board has no immediate match but does have a move. */
function patchedBoard(colors) {
  if (rows < 3 || cols < 3) return buildNoMatchBoard(colors)
  for (var attempt = 0; attempt < 80; attempt++) {
    var g = buildNoMatchBoard(colors)
    for (var x = 0; x < colors; x++) {
      for (var y = 0; y < colors; y++) {
        if (y === x) continue
        var t = copyGrid(g)
        // (0,0)=X (0,1)=X (0,2)=Y  and  (1,2)=X :
        // swapping (0,2) with (1,2) turns row 0 into X X X. No initial line.
        t[0][0] = x
        t[0][1] = x
        t[0][2] = y
        t[1][2] = x
        if (!hasAnyMatch(t) && hasMove(t)) return t
      }
    }
  }
  return buildNoMatchBoard(colors)
}

/* A guaranteed-playable starting board. */
function buildPlayableBoard(colors) {
  for (var i = 0; i < 400; i++) {
    var g = buildNoMatchBoard(colors)
    if (hasMove(g)) return g
  }
  return patchedBoard(colors)
}

/* Reshuffle the live board, preserving the multiset of colours, until it has
 * no initial match and at least one swap. Falls back to a fresh board. */
function shuffleBoard(colors) {
  var flat = []
  for (var r = 0; r < rows; r++) {
    for (var c = 0; c < cols; c++) {
      var v = cellVal(grid, r, c)
      if (v !== null) flat.push(v)
    }
  }
  if (flat.length !== rows * cols) return buildPlayableBoard(colors)

  for (var i = 0; i < 400; i++) {
    shuffleArr(flat)
    var g = []
    var idx = 0
    for (var r2 = 0; r2 < rows; r2++) {
      var row = []
      for (var c2 = 0; c2 < cols; c2++) {
        row.push(flat[idx])
        idx++
      }
      g.push(row)
    }
    if (!hasAnyMatch(g) && hasMove(g)) return g
  }
  return buildPlayableBoard(colors)
}

/* ======================================================================
 * Rendering
 * ==================================================================== */

/* Build the 64 tile elements once and cache them by coordinate. */
function buildDom() {
  var el = boardEl.value
  if (!el) return
  el.innerHTML = ''
  cellEls = []
  for (var r = 0; r < rows; r++) {
    var row = []
    for (var c = 0; c < cols; c++) {
      var cell = document.createElement('div')
      cell.className = 'cell empty'
      el.appendChild(cell)
      row.push(cell)
    }
    cellEls.push(row)
  }
}

function inList(list, r, c) {
  if (!list) return false
  for (var i = 0; i < list.length; i++) {
    if (list[i][0] === r && list[i][1] === c) return true
  }
  return false
}

/* -------------------------------------------------------------- fx helpers */

/* Safe CSS custom-property write: never let an unknown property bubble. */
function setVar(el, key, value) {
  if (!el || !el.style || typeof el.style.setProperty !== 'function') return
  try {
    el.style.setProperty(key, String(value))
  } catch {
    /* ignore */
  }
}

/* Detach a decoration node, tolerating whichever parent link the DOM sets. */
function detachNode(el) {
  if (!el) return
  var p = el.parentNode || el.parent
  if (p && typeof p.removeChild === 'function') {
    try {
      p.removeChild(el)
    } catch {
      /* ignore */
    }
  }
}

/* Record a transient animation for one tile. render() folds `cls` into the tile
 * className and writes `vars` as CSS custom properties. */
function setFx(r, c, cls, vars) {
  if (r < 0 || r >= rows || c < 0 || c >= cols) return
  if (!fxAt[r]) fxAt[r] = []
  fxAt[r][c] = { cls: cls, vars: vars || null }
}

/* Drop every per-tile effect — called on each phase transition, so an effect
 * lives exactly as long as its phase. */
function clearFx() {
  fxAt = []
}

/* Attach a decoration and start its life clock. Retirement is driven by the rAF
 * `update(dt)` clock and the list is hard-capped so a pathological cascade
 * cannot grow the DOM. */
function addFxNode(el, ttl) {
  if (!fxLayerEl.value || !el) return
  fxLayerEl.value.appendChild(el)
  fxNodes.push({ el: el, ttl: ttl })
  while (fxNodes.length > FX_MAX_NODES) detachNode(fxNodes.shift().el)
}

/* Retire every live decoration (used when the board is rebuilt). */
function clearFxNodes() {
  for (var i = 0; i < fxNodes.length; i++) detachNode(fxNodes[i].el)
  fxNodes = []
}

/* Age the decorations; anything past its ttl is removed. */
function ageFxNodes(dt) {
  if (!fxNodes.length) return
  var keep = []
  for (var i = 0; i < fxNodes.length; i++) {
    var n = fxNodes[i]
    n.ttl -= dt
    if (n.ttl <= 0) detachNode(n.el)
    else keep.push(n)
  }
  fxNodes = keep
}

/* An empty anchor already positioned over tile (r, c) on the decoration grid,
 * so a burst lines up with its tile without reading geometry back. */
function fxAnchor(r, c) {
  var a = document.createElement('div')
  a.className = 'fx-anchor'
  setVar(a, 'grid-row', String(r + 1))
  setVar(a, 'grid-column', String(c + 1))
  return a
}

/* Impact decoration for one cleared gem: an expanding ring plus a fan of sparks
 * tinted with the gem's own colour. */
function spawnBurst(r, c, colorIndex) {
  if (!fxLayerEl.value) return
  if (fxNodes.length > FX_MAX_NODES - 8) return // budget guard
  var anchor = fxAnchor(r, c)

  var ring = document.createElement('div')
  ring.className = 'fx-ring'
  anchor.appendChild(ring)

  var col = PALETTE[colorIndex] || '#ffffff'
  var n = 6
  for (var i = 0; i < n; i++) {
    var ang = (Math.PI * 2 * i) / n + (r * 0.7 + c * 0.4)
    var sp = document.createElement('div')
    sp.className = 'fx-spark'
    setVar(sp, '--sx', Math.cos(ang).toFixed(3))
    setVar(sp, '--sy', Math.sin(ang).toFixed(3))
    setVar(sp, '--spread', 26 + ((i * 7 + r * 5 + c * 3) % 16) + 'px')
    setVar(sp, '--spark', col)
    anchor.appendChild(sp)
  }
  addFxNode(anchor, CLEAR_MS + 260)
}

/* "+30" drifting up from a cleared cluster. Pure digits — no new copy. */
function spawnScoreFloat(r, c, text) {
  if (!fxLayerEl.value) return
  var anchor = fxAnchor(r, c)
  var f = document.createElement('div')
  f.className = 'fx-float'
  f.textContent = text
  anchor.appendChild(f)
  addFxNode(anchor, 900)
}

/* The big "x2" / "x3" pop in the middle of the board. */
function spawnComboFloat(mult) {
  if (!fxLayerEl.value) return
  var a = document.createElement('div')
  a.className = 'fx-anchor fx-full'
  var t = document.createElement('div')
  t.className = 'fx-combo'
  t.textContent = '\u00d7' + mult // -> x2
  a.appendChild(t)
  addFxNode(a, 1000)
}

/* A very short board wobble when a chain lands. */
function shakeBoard() {
  if (!boardEl.value || !boardEl.value.classList) return
  boardEl.value.classList.add('shake')
  boardShakeTtl = 260
}

/* Translate a cleared grid into per-tile fall offsets. `before` is the grid with
 * the matched cells already set to null; because gravity preserves column order
 * and stacks the surviving gems on the bottom, a gem's landing row is implied by
 * its index in the column's compacted list. */
function applyFallFx(before) {
  for (var c = 0; c < cols; c++) {
    var src = []
    for (var r = 0; r < rows; r++) {
      if (cellVal(before, r, c) !== null) src.push(r)
    }
    var m = src.length
    for (var i = 0; i < m; i++) {
      var dst = rows - m + i
      var fell = dst - src[i]
      if (fell > 0) setFx(dst, c, 'fall', { '--fx-ny': -fell, '--fx-ms': FALL_MS + 'ms' })
    }
  }
}

/* New gems in a column are the consecutive rows at the top, so they all travel
 * the same distance and enter as one block from above the board. */
function applyRefillFx(before) {
  for (var c = 0; c < cols; c++) {
    var m = 0
    for (var r = 0; r < rows; r++) if (cellVal(before, r, c) === null) m++
    if (!m) continue
    for (var i = 0; i < m; i++) {
      setFx(i, c, 'drop', { '--fx-ny': -m, '--fx-ms': REFILL_MS + 'ms' })
    }
  }
}

/* Repaint every tile from the current grid + transient phase state. */
function render() {
  if (!cellEls.length) return
  for (var r = 0; r < rows; r++) {
    for (var c = 0; c < cols; c++) {
      var el = cellEls[r] && cellEls[r][c]
      if (!el) continue
      var v = cellVal(grid, r, c)
      var cls = 'cell'
      cls += v === null ? ' empty' : ' c' + v
      if (selected && selected[0] === r && selected[1] === c) cls += ' sel'
      if (phase === 'reverting' && inList(revertCells, r, c)) cls += ' bad'
      if (hintTimer > 0 && inList(hintCells, r, c)) cls += ' hint'
      var fx = fxAt[r] && fxAt[r][c] ? fxAt[r][c] : null
      if (fx) cls += ' ' + fx.cls
      el.className = cls
      if (fx && fx.vars) {
        for (var k in fx.vars) {
          if (Object.prototype.hasOwnProperty.call(fx.vars, k)) setVar(el, k, fx.vars[k])
        }
      }
    }
  }
}

/* ======================================================================
 * HUD — every string that depends on the language is written here, so a single
 * locale watcher is enough to re-localise the whole screen.
 * ==================================================================== */
function updateHud() {
  scoreNum.value = score
  movesNum.value = Math.max(0, moves)
  targetNum.value = target
  bestNum.value = best
  comboText.value = combo > 1 ? 'x' + combo : '-'
  levelLabel.value = tt('diff_' + diffKey.value)
  noticeText.value = noticeKey ? tt(noticeKey, noticeVars) : ''
  statusRef.value = status

  if (status === 'over') {
    resultTitle.value = tt(endWin ? 'win' : 'lose')
    resultSub.value = tt('resultSub', { score: score, target: target, best: best })
  }

  newDisabled.value = status === 'ready'
  hintDisabled.value = status !== 'playing' || phase !== 'idle'
  shuffleDisabled.value = status !== 'playing' || phase !== 'idle'
  diffDisabled.value = status === 'playing'
}

/* ======================================================================
 * Persistence — localStorage only (no removeItem anywhere,原版键名保留).
 * ==================================================================== */
function loadBest() {
  try {
    var v = parseInt(localStorage.getItem('match3_best') || '0', 10)
    best = isNaN(v) ? 0 : v
  } catch {
    best = 0
  }
}
function saveBest() {
  try {
    localStorage.setItem('match3_best', String(best))
  } catch {
    /* ignore */
  }
}

/* ======================================================================
 * Game lifecycle
 * ==================================================================== */

/* Build a fresh board for the current difficulty. Ends in the 'ready' state. */
function setupBoard(seed) {
  config = DIFF[diffKey.value] || DIFF.normal
  rows = ROWS
  cols = COLS
  rng = seed === null || seed === undefined ? Math.random : makeRng(seed)

  grid = buildPlayableBoard(config.colors)
  score = 0
  moves = config.moves
  target = config.target
  combo = 0
  selected = null
  phase = 'idle'
  phaseElapsed = 0
  phaseDuration = 0
  pendingClear = null
  revertCells = null
  hintCells = null
  hintTimer = 0
  cascadeDepth = 0
  dragFrom = null
  suppressClick = false
  clearFx()
  clearFxNodes()
  boardShakeTtl = 0
  if (boardEl.value && boardEl.value.classList) boardEl.value.classList.remove('shake')
  loadBest()
  status = 'ready'
  noticeKey = 'readyHint'
  noticeVars = null
  resultVisible.value = false
  updateHud()
  render()
}

/* Leave the ready state and begin play. */
function startGame() {
  if (status === 'playing') return
  if (status === 'over') {
    restart()
    return
  }
  status = 'playing'
  noticeKey = 'play'
  noticeVars = null
  resultVisible.value = false
  updateHud()
  render()
}

/* Start a brand new game (used by the buttons and by difficulty changes). */
function newGame(opts) {
  opts = opts || {}
  if (opts.difficulty && DIFF[opts.difficulty]) diffKey.value = opts.difficulty
  setupBoard(opts.seed)
  status = 'playing'
  noticeKey = 'play'
  noticeVars = null
  resultVisible.value = false
  updateHud()
  render()
}

/* Restart at the current difficulty, skipping the start overlay. */
function restart() {
  newGame({ difficulty: diffKey.value })
}

/* Finish the game. */
function endGame(win) {
  status = 'over'
  endWin = !!win
  phase = 'idle'
  phaseElapsed = 0
  clearFx()
  boardShakeTtl = 0
  if (boardEl.value && boardEl.value.classList) boardEl.value.classList.remove('shake')
  if (score > best) {
    best = score
    saveBest()
  }
  resultVisible.value = true
  updateHud()
}

/* ======================================================================
 * Interaction
 * ==================================================================== */
function isAdjacent(r1, c1, r2, c2) {
  return Math.abs(r1 - r2) + Math.abs(c1 - c2) === 1
}

/* Map a tile element back to its [r, c] by identity scan. */
function posOfCell(el) {
  for (var r = 0; r < cellEls.length; r++) {
    for (var c = 0; c < cellEls[r].length; c++) {
      if (cellEls[r][c] === el) return [r, c]
    }
  }
  return null
}

/* Client coords carried by a pointer event, or null when it has none. */
function pointerXY(ev) {
  if (!ev || typeof ev.clientX !== 'number' || typeof ev.clientY !== 'number') return null
  return { x: ev.clientX, y: ev.clientY }
}

/* Resolve a pointer event to a board cell using the container's client rect.
 * Returns null when the coordinates are unavailable. */
function posFromEvent(ev) {
  var xy = pointerXY(ev)
  if (!xy) return null
  if (!boardEl.value || typeof boardEl.value.getBoundingClientRect !== 'function') return null
  var rect = boardEl.value.getBoundingClientRect()
  if (!rect || !rect.width || !rect.height) return null
  var cw = rect.width / cols
  var ch = rect.height / rows
  var c = Math.floor((xy.x - rect.left) / cw)
  var r = Math.floor((xy.y - rect.top) / ch)
  if (r < 0 || r >= rows || c < 0 || c >= cols) return null
  return [r, c]
}

/* Resolve a pointer event to a board cell: the tile under the pointer first
 * (exact, includes gaps), the container rect only as a fallback for synthetic
 * events whose target is not a tile. */
function cellFromEvent(ev) {
  var t = ev && ev.target
  var cellEl = t && typeof t.closest === 'function' ? t.closest('.cell') : null
  if (cellEl) {
    var pos = posOfCell(cellEl)
    if (pos) return pos
  }
  return posFromEvent(ev)
}

/* Swipe trigger distance. One tile is the natural step on a match-3 board, so a
 * drag only has to clear a share of it (24px cap, 16px floor for narrow boards)
 * instead of landing exactly one cell away — a fast flick overshoots and a
 * thumb drifts, both of which used to fall outside the adjacency test. */
function swipeLimit() {
  var rect = boardEl.value && typeof boardEl.value.getBoundingClientRect === 'function' ? boardEl.value.getBoundingClientRect() : null
  var pitch = rect && rect.width ? rect.width / cols : 0
  return Math.min(24, Math.max(16, pitch * 0.4))
}

/* Click-select flow: first click selects, a second click on a neighbour swaps. */
function cellSelect(r, c) {
  if (status !== 'playing' || phase !== 'idle') return
  if (!selected) {
    selected = [r, c]
    render()
    return
  }
  var sr = selected[0],
    sc = selected[1]
  if (sr === r && sc === c) {
    selected = null
    render()
    return
  }
  if (isAdjacent(sr, sc, r, c)) {
    selected = null
    attemptSwap(sr, sc, r, c)
  } else {
    selected = [r, c]
    render()
  }
}

/* Try to swap two adjacent cells. A legal swap (one that makes a match) costs a
 * move and kicks off the resolve pipeline; an illegal swap animates back and
 * costs nothing. Returns whether the swap was legal. */
function attemptSwap(r1, c1, r2, c2) {
  if (status !== 'playing' || phase !== 'idle') return false

  var a = grid[r1][c1]
  grid[r1][c1] = grid[r2][c2]
  grid[r2][c2] = a

  if (hasAnyMatch(grid)) {
    moves--
    combo = 0
    cascadeDepth = 0
    noticeKey = 'play'
    noticeVars = null
    /* The data layer has already swapped the two colours, so each tile must
     * START on its partner's square and slide home — that reads as the two gems
     * crossing over rather than blinking to a new colour. */
    clearFx()
    setFx(r1, c1, 'swap', { '--fx-nx': c2 - c1, '--fx-ny': r2 - r1, '--fx-ms': SWAP_MS + 'ms' })
    setFx(r2, c2, 'swap', { '--fx-nx': c1 - c2, '--fx-ny': r1 - r2, '--fx-ms': SWAP_MS + 'ms' })
    phase = 'swapping'
    phaseElapsed = 0
    phaseDuration = SWAP_MS
    updateHud()
    render()
    return true
  }

  var b = grid[r1][c1]
  grid[r1][c1] = grid[r2][c2]
  grid[r2][c2] = b

  revertCells = [
    [r1, c1],
    [r2, c2],
  ]
  noticeKey = 'noMatch'
  noticeVars = null
  phase = 'reverting'
  phaseElapsed = 0
  phaseDuration = REVERT_MS
  updateHud()
  render()
  return false
}

/* ------------------------------------------------------------- phases */

/* Begin one cascade round: score the matches, animate them out. */
function beginClear() {
  pendingClear = findMatches(grid)
  if (!pendingClear.length) {
    finishMove()
    return
  }

  cascadeDepth++
  if (cascadeDepth > 40) {
    finishMove()
    return
  } // safety net

  combo++
  var gained = pendingClear.length * BASE * combo
  score += gained
  if (score > best) {
    best = score
    saveBest()
  }

  /* One effect class for every cleared tile, then the decorations: a burst on
   * the first few tiles (bounded so a 60-gem clear cannot spawn hundreds of
   * nodes), the "+N" award floating up from the cluster's centre, and — from the
   * second cascade round on — a big multiplier pop plus a board wobble. */
  clearFx()
  var i,
    pr,
    pc,
    cx = 0,
    cy = 0,
    bursts = 0
  for (i = 0; i < pendingClear.length; i++) {
    pr = pendingClear[i][0]
    pc = pendingClear[i][1]
    setFx(pr, pc, 'clear', null)
    cx += pc
    cy += pr
    if (bursts < FX_MAX_BURSTS) {
      spawnBurst(pr, pc, cellVal(grid, pr, pc))
      bursts++
    }
  }
  cx = Math.round(cx / pendingClear.length)
  cy = Math.round(cy / pendingClear.length)
  spawnScoreFloat(cy, cx, '+' + gained)
  if (combo >= 2) {
    spawnComboFloat(combo)
    shakeBoard()
  }

  phase = 'clearing'
  phaseElapsed = 0
  phaseDuration = CLEAR_MS
  updateHud()
  render()
}

function onSwapDone() {
  clearFx()
  beginClear()
}

function onRevertDone() {
  revertCells = null
  clearFx()
  phase = 'idle'
  updateHud()
  render()
}

function onClearDone() {
  var i
  for (i = 0; i < pendingClear.length; i++) {
    grid[pendingClear[i][0]][pendingClear[i][1]] = null
  }
  pendingClear = null
  /* Gravity is a pure transform of the grid, so the fall distance is already
   * implied by the cleared grid: within a column the non-empty gems keep their
   * order and stack on the bottom. Do not skip the call: the timing machine is
   * unchanged either way, but the tiles need the offsets to animate. */
  var settled = applyGravity(grid)
  clearFx()
  applyFallFx(grid)
  grid = settled
  phase = 'falling'
  phaseElapsed = 0
  phaseDuration = FALL_MS
  render()
}

function onFallDone() {
  var before = grid // settled grid, empties along the top
  grid = refill(grid, config.colors)
  clearFx()
  applyRefillFx(before)
  phase = 'refilling'
  phaseElapsed = 0
  phaseDuration = REFILL_MS
  render()
}

function onRefillDone() {
  if (hasAnyMatch(grid)) {
    beginClear() // chain reaction -> new cascade round
  } else {
    finishMove()
  }
}

function onShuffleDone() {
  grid = shuffleBoard(config.colors)
  clearFx()
  phase = 'idle'
  phaseElapsed = 0
  noticeKey = 'reshuffled'
  noticeVars = null
  if (!hasMove(grid)) grid = buildPlayableBoard(config.colors)
  updateHud()
  render()
}

/* End of a move: settle into idle, then decide win / lose / auto-shuffle. */
function finishMove() {
  phase = 'idle'
  phaseElapsed = 0
  /* This is the convergence point of every resolve path, so the last phase's
   * per-tile effects are retired here — otherwise the refill `drop` descriptor
   * would outlive its phase and sit on the tiles until the next legal swap. */
  clearFx()
  if (score > best) {
    best = score
    saveBest()
  }

  if (score >= target) {
    endGame(true)
    return
  }
  if (moves <= 0) {
    endGame(false)
    return
  }
  if (!hasMove(grid)) {
    startShuffle()
    return
  }
  updateHud()
  render()
}

/* Enter the auto-shuffle phase (also reused by the Shuffle button). */
function startShuffle() {
  if (phase === 'shuffling') return
  phase = 'shuffling'
  phaseElapsed = 0
  phaseDuration = SHUFFLE_MS
  noticeKey = 'shuffling'
  noticeVars = null
  updateHud()
  render()
}

/* -------------------------------------------------------------- buttons */
function doHint() {
  if (status !== 'playing' || phase !== 'idle') return
  var mv = findHintMove(grid)
  if (!mv) {
    startShuffle()
    return
  }
  hintCells = [
    [mv[0], mv[1]],
    [mv[2], mv[3]],
  ]
  hintTimer = 1200
  render()
}

function doShuffle() {
  if (status !== 'playing' || phase !== 'idle') return
  startShuffle()
}

function onDiffChange() {
  // 原版口径:开局后选择框禁用;未开局时换难度直接重铺棋盘(仍停在准备状态)
  if (status !== 'playing') setupBoard()
}

/* ------------------------------------------------------------- pointer / keys */
function onBoardClick(ev) {
  if (suppressClick) {
    suppressClick = false
    return
  }
  var t = ev && ev.target
  var cellEl = t && typeof t.closest === 'function' ? t.closest('.cell') : t
  if (!cellEl) return
  var pos = posOfCell(cellEl)
  if (pos) cellSelect(pos[0], pos[1])
}

function onBoardDown(ev) {
  /* The click that follows the previous gesture has already been delivered by
   * now, so a fresh press clears the flag and the next tap selects again. */
  suppressClick = false
  dragFrom = null
  dragStart = null
  dragLimit = 0
  /* Armed even while a resolve/fall is still running: the gesture must survive
   * the animation tail instead of being dropped for touching down too early. */
  if (status !== 'playing') return
  var pos = cellFromEvent(ev)
  if (!pos) return
  dragFrom = pos
  dragStart = pointerXY(ev)
  /* The board cannot resize mid-gesture, so measure once instead of on every
   * pointermove. */
  dragLimit = swipeLimit()
}

/* A press-drag is resolved as soon as it clears the trigger distance, so the
 * swap lands mid-drag instead of waiting for the finger to lift on the exact
 * neighbour tile (fast flicks flew past it, diagonal thumbs missed the row). */
function onBoardMove(ev) {
  if (!dragFrom || !dragStart) return
  trySwipe(ev)
}

function onBoardUp(ev) {
  /* Fallback for a flick that delivered no pointermove at all. */
  if (dragFrom && dragStart) trySwipe(ev)
  dragFrom = null
  dragStart = null
  dragLimit = 0
}

function onBoardCancel() {
  dragFrom = null
  dragStart = null
  dragLimit = 0
}

/* Turn the current drag into a one-step swap along its dominant axis. Below the
 * trigger distance nothing happens here — the trailing click selects instead. */
function trySwipe(ev) {
  if (!dragFrom || !dragStart) return
  var xy = pointerXY(ev)
  if (!xy) return
  var dx = xy.x - dragStart.x
  var dy = xy.y - dragStart.y
  var limit = dragLimit || swipeLimit()
  if (Math.abs(dx) < limit && Math.abs(dy) < limit) return
  /* Still resolving / falling: stay armed and let the next pointermove retry,
   * so a swipe begun during the animation tail lands the moment the board
   * settles instead of vanishing. */
  if (phase !== 'idle') return

  var from = dragFrom
  dragFrom = null
  dragStart = null

  var dr = 0,
    dc = 0
  if (Math.abs(dx) >= Math.abs(dy)) dc = dx > 0 ? 1 : -1
  else dr = dy > 0 ? 1 : -1

  var r = from[0] + dr
  var c = from[1] + dc
  if (r < 0 || r >= rows || c < 0 || c >= cols) return
  suppressClick = true
  selected = null
  attemptSwap(from[0], from[1], r, c)
}

function onKeydown(ev) {
  // 焦点在头部选择框/输入框时不抢键(空格与回车会误开局)
  var tag = ev.target && ev.target.tagName
  if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') return
  var k = ev.key
  if (status === 'ready' || status === 'over') {
    if (k === ' ' || k === 'Enter') {
      ev.preventDefault()
      startGame()
    }
    return
  }
  if (k === 'h' || k === 'H') {
    doHint()
  } else if (k === 'r' || k === 'R') {
    doShuffle()
  }
}

/* ======================================================================
 * Frame loop — a single rAF chain driving the phase timers. No setTimeout.
 * ==================================================================== */
function update(dt) {
  /* Decoration lifetimes and the board wobble run on their own clocks, so they
   * keep expiring even while the phase machine sits idle. */
  ageFxNodes(dt)
  if (boardShakeTtl > 0) {
    boardShakeTtl -= dt
    if (boardShakeTtl <= 0) {
      boardShakeTtl = 0
      if (boardEl.value && boardEl.value.classList) boardEl.value.classList.remove('shake')
    }
  }

  if (hintTimer > 0) {
    hintTimer -= dt
    if (hintTimer <= 0) {
      hintTimer = 0
      hintCells = null
      render()
    }
  }

  if (phase === 'idle') return

  phaseElapsed += dt
  if (phaseElapsed < phaseDuration) return
  phaseElapsed = 0

  if (phase === 'swapping') onSwapDone()
  else if (phase === 'reverting') onRevertDone()
  else if (phase === 'clearing') onClearDone()
  else if (phase === 'falling') onFallDone()
  else if (phase === 'refilling') onRefillDone()
  else if (phase === 'shuffling') onShuffleDone()
}

var rafId = 0
var disposed = false

function loop(ts) {
  if (disposed) return
  var dt = 16 // nominal frame when no timestamp is given
  if (typeof ts === 'number') {
    if (lastFrameTs) {
      dt = ts - lastFrameTs
      if (dt <= 0 || dt > 200) dt = 16
    }
    lastFrameTs = ts
  }
  update(dt)
  rafId = requestAnimationFrame(loop)
}

/* 外壳尺寸变化(全屏/宽屏/旋屏)时兜底重绘一次:棋盘由 CSS 容器自动缩放,
   装饰层锚点是网格坐标,不需要重算像素几何 */
function onWindowResize() {
  render()
}

onMounted(() => {
  sunLight?.suspendEffects()
  buildDom()
  setupBoard()
  lastFrameTs = 0
  rafId = requestAnimationFrame(loop)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onWindowResize, { passive: true })
})

// 语言切换:芯片/状态行/结算文案全部重写(等价于原版 T.onChange 订阅)
watch(locale, () => {
  updateHud()
  render()
})

onBeforeUnmount(() => {
  disposed = true
  if (rafId) cancelAnimationFrame(rafId)
  rafId = 0
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onWindowResize)
  clearFxNodes()
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 原版整页渐变(#eef2fb 顶白径向);风格色只走 --gs-bg / --gs-accent,dark 档必须补 */
.m3-shell {
  --gs-accent: #6366f1;
  --gs-bg: radial-gradient(120% 120% at 50% 0%, rgba(255, 255, 255, 0.92) 0%, rgba(238, 242, 251, 0.9) 62%);
}
html.dark .m3-shell {
  --gs-bg: radial-gradient(120% 120% at 50% 0%, rgba(99, 102, 241, 0.16) 0%, rgba(17, 24, 39, 0.62) 62%);
}
.m3-diff {
  width: 96px;
}

/* 记分 HUD + 操作贴棋盘:竖屏自上而下、宽度同棋盘 */
.m3-layout {
  /* 方形棋盘:三限 = 容器宽 / 上限 560 / 竖屏纵向预算(两行芯片 + 按钮行 + 提示行 + 间隙) */
  --m3-board-w: min(100%, 560px, calc(100cqh - 158px));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}
.m3-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: var(--m3-board-w);
  min-width: 0;
}
.m3-stats {
  display: flex;
  flex: 1 1 100%;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  min-width: 0;
}
.m3-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid var(--color-border, #d7deee);
  border-radius: 9px;
  background: var(--color-card, #f5f7fd);
  color: var(--color-text-secondary, #3a4668);
  font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.m3-stat small {
  font-size: 10.5px;
  font-weight: 400;
  opacity: 0.75;
}
.m3-stat b {
  color: var(--el-color-primary, #6366f1);
}
/* 按钮靠这条的右端(竖屏/桌面在棋盘上方那条的右端);手机横屏竖排成棋盘右侧一列 */
.m3-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-left: auto;
}
/* EP 给相邻按钮加 margin-left:12px,这里间距只走 gap */
.m3-actions .el-button + .el-button {
  margin-left: 0;
}
.m3-notice {
  width: 100%;
  min-height: 18px;
  margin: 0;
  text-align: center;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary, #6b7592);
}

/* Board */
.m3-board-wrap {
  position: relative;
  width: var(--m3-board-w);
  max-width: 100%;
  touch-action: manipulation;
  /* 格子步长 / 消除时长:棋盘与装饰层共享(滑落/掉落动画按它换算) */
  --gap: 6px;
  --pad: 8px;
  --clear-ms: 320ms;
}
.m3-board {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: var(--gap);
  width: 100%;
  aspect-ratio: 1 / 1;
  padding: var(--pad);
  border: 1px solid var(--color-border, #d7deee);
  border-radius: 16px;
  background: linear-gradient(180deg, #f3f6fe, #e9eefb);
  box-shadow: inset 0 2px 10px rgba(30, 39, 64, 0.07);
  /* 原版 touch-action:none —— 按住拖到相邻格是核心手势,交给 pointer 事件全权处理 */
  touch-action: none;
  user-select: none;
  overflow: hidden;
}
html.dark .m3-board {
  border-color: rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, #222a3d, #181f2f);
}

.m3-board :deep(.cell) {
  position: relative; /* anchor for the ::before / ::after fx */
  border-radius: 10px;
  background: #e2e8f5;
  box-shadow: inset 0 1px 3px rgba(30, 39, 64, 0.12);
  cursor: pointer;
  transition:
    transform 0.12s ease,
    filter 0.12s ease,
    opacity 0.12s ease;
  will-change: transform, opacity;
}
html.dark .m3-board :deep(.cell) {
  background: #2b344a;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.35);
}
.m3-board :deep(.cell.empty) {
  background: #e7ecf6;
  box-shadow: inset 0 1px 4px rgba(30, 39, 64, 0.1);
  cursor: default;
}
html.dark .m3-board :deep(.cell.empty) {
  background: #232b3e;
}
/* The selected look deliberately uses NO transform: a running slide / drop
 * animation owns `transform`, so an inset ring + glow conveys selection. */
.m3-board :deep(.cell.sel) {
  z-index: 3;
  outline: 3px solid var(--el-color-primary, #6366f1);
  outline-offset: -3px;
  box-shadow:
    inset 0 0 0 3px rgba(99, 102, 241, 0.55),
    0 0 10px rgba(99, 102, 241, 0.4);
  filter: brightness(1.08);
}

/* gem colours (7-slot palette, indexed by colour) */
.m3-board :deep(.cell.c0) {
  background: radial-gradient(circle at 32% 28%, #fda4af, #f43f5e 72%);
}
.m3-board :deep(.cell.c1) {
  background: radial-gradient(circle at 32% 28%, #fcd34d, #f59e0b 72%);
}
.m3-board :deep(.cell.c2) {
  background: radial-gradient(circle at 32% 28%, #86efac, #22c55e 72%);
}
.m3-board :deep(.cell.c3) {
  background: radial-gradient(circle at 32% 28%, #93c5fd, #3b82f6 72%);
}
.m3-board :deep(.cell.c4) {
  background: radial-gradient(circle at 32% 28%, #d8b4fe, #a855f7 72%);
}
.m3-board :deep(.cell.c5) {
  background: radial-gradient(circle at 32% 28%, #67e8f9, #06b6d4 72%);
}
.m3-board :deep(.cell.c6) {
  background: radial-gradient(circle at 32% 28%, #f9a8d4, #ec4899 72%);
}
/* 深色档换一组宝石色:深底上原来那组会被压暗,红/粉(c0/c6)与蓝/紫(c3/c4)
   两两糊在一起(OKLab 最小色差 8),七色棋盘根本分不出来。
   这组按色相铺开、明度错开、整体提亮(c6 由粉换成冰银,躲开与 c0 红的冲突),
   两两最小色差 15、与棋盘底最浅也有 40 以上;再给每颗宝石补一圈亮边,
   边界落在深底上更利落。发光粒子走 PALETTE,不受这里影响 */
html.dark .m3-board :deep(.cell:not(.empty):not(.sel)) {
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.22),
    inset 0 1px 3px rgba(0, 0, 0, 0.3);
}
html.dark .m3-board :deep(.cell.c0) {
  background: radial-gradient(circle at 32% 28%, #ffb4bd, #ff5c6c 72%);
}
html.dark .m3-board :deep(.cell.c1) {
  background: radial-gradient(circle at 32% 28%, #ffe19a, #ffbe2e 72%);
}
html.dark .m3-board :deep(.cell.c2) {
  background: radial-gradient(circle at 32% 28%, #a8f5c6, #2fd672 72%);
}
html.dark .m3-board :deep(.cell.c3) {
  background: radial-gradient(circle at 32% 28%, #b6f2fb, #37c9e0 72%);
}
html.dark .m3-board :deep(.cell.c4) {
  background: radial-gradient(circle at 32% 28%, #a9c3ff, #3a6dff 72%);
}
html.dark .m3-board :deep(.cell.c5) {
  background: radial-gradient(circle at 32% 28%, #e4c8ff, #c48bff 72%);
}
html.dark .m3-board :deep(.cell.c6) {
  background: radial-gradient(circle at 32% 28%, #ffffff, #cfd8e3 72%);
}

/* Transient phase animations. Each class only names the animation; the distance
 * travelled is passed per cell through --fx-* custom properties. */
.m3-board :deep(.cell.swap) {
  z-index: 4;
  animation: m3-swapSlide var(--fx-ms, 190ms) cubic-bezier(0.22, 0.72, 0.24, 1) both;
}
.m3-board :deep(.cell.bad) {
  animation: m3-shake 0.18s ease;
}
.m3-board :deep(.cell.clear) {
  z-index: 6;
  animation: m3-clearBurst var(--clear-ms, 320ms) cubic-bezier(0.3, 0.9, 0.3, 1) both;
}
.m3-board :deep(.cell.fall) {
  z-index: 4;
  animation: m3-fallDrop var(--fx-ms, 300ms) cubic-bezier(0.3, 0.05, 0.35, 1.25) both;
}
.m3-board :deep(.cell.drop) {
  z-index: 4;
  animation: m3-refillDrop var(--fx-ms, 220ms) cubic-bezier(0.3, 0.05, 0.35, 1.25) both;
}
.m3-board :deep(.cell.hint) {
  animation: m3-hintPulse 0.6s ease 2;
}

/* White flash + expanding ring for a cleared gem, drawn as pseudo-elements. */
.m3-board :deep(.cell.clear::before),
.m3-board :deep(.cell.clear::after) {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
}
.m3-board :deep(.cell.clear::before) {
  background: radial-gradient(circle, rgba(255, 255, 255, 0.95) 0%, rgba(255, 255, 255, 0) 68%);
  animation: m3-clearFlash var(--clear-ms, 320ms) ease-out both;
}
.m3-board :deep(.cell.clear::after) {
  border: 2px solid rgba(255, 255, 255, 0.92);
  animation: m3-clearRing var(--clear-ms, 320ms) ease-out both;
}

@keyframes m3-swapSlide {
  0% {
    transform: translate(calc(var(--fx-nx, 0) * (100% + var(--gap))), calc(var(--fx-ny, 0) * (100% + var(--gap))));
  }
  100% {
    transform: translate(0, 0);
  }
}
@keyframes m3-clearBurst {
  0% {
    transform: scale(1) rotate(0deg);
    filter: brightness(1) saturate(1);
    opacity: 1;
  }
  30% {
    transform: scale(1.2) rotate(0deg);
    filter: brightness(1.75) saturate(1.4);
    opacity: 1;
  }
  62% {
    transform: scale(1.06) rotate(8deg);
    filter: brightness(1.85) saturate(1.5);
    opacity: 0.95;
  }
  100% {
    transform: scale(0.25) rotate(22deg);
    filter: brightness(2) saturate(1.5);
    opacity: 0;
  }
}
@keyframes m3-clearFlash {
  0% {
    opacity: 0;
  }
  22% {
    opacity: 0.78;
  }
  100% {
    opacity: 0;
  }
}
@keyframes m3-clearRing {
  0% {
    transform: scale(1);
    opacity: 0.95;
  }
  100% {
    transform: scale(2.1);
    opacity: 0;
  }
}
/* Gravity: accelerate down, then squash on landing and settle back. */
@keyframes m3-fallDrop {
  0% {
    transform: translateY(calc(var(--fx-ny, 0) * (100% + var(--gap))));
  }
  78% {
    transform: translateY(0) scaleY(1);
  }
  88% {
    transform: translateY(0) scale(1.14, 0.86);
  }
  96% {
    transform: translateY(0) scale(0.96, 1.05);
  }
  100% {
    transform: translateY(0) scale(1);
  }
}
/* Refill: same fall, but the new gems also fade in as they enter. */
@keyframes m3-refillDrop {
  0% {
    transform: translateY(calc(var(--fx-ny, 0) * (100% + var(--gap))));
    opacity: 0;
  }
  18% {
    opacity: 1;
  }
  78% {
    transform: translateY(0) scaleY(1);
    opacity: 1;
  }
  88% {
    transform: translateY(0) scale(1.12, 0.88);
  }
  100% {
    transform: translateY(0) scale(1);
  }
}
@keyframes m3-shake {
  0%,
  100% {
    transform: translateX(0);
  }
  25% {
    transform: translateX(-3px);
  }
  75% {
    transform: translateX(3px);
  }
}
@keyframes m3-hintPulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(99, 102, 241, 0);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.55);
  }
}
.m3-board.shake {
  animation: m3-boardShake 0.24s ease-out;
}
@keyframes m3-boardShake {
  0%,
  100% {
    transform: translate(0, 0);
  }
  25% {
    transform: translate(-2px, 1px);
  }
  50% {
    transform: translate(2px, -1px);
  }
  75% {
    transform: translate(-1px, 1px);
  }
}

/* ---------- decoration layer ---------- */
.m3-fx {
  position: absolute;
  inset: 0;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  grid-template-rows: repeat(8, 1fr);
  gap: var(--gap, 6px);
  padding: var(--pad, 8px);
  pointer-events: none;
  z-index: 8;
  overflow: visible;
}
.m3-fx :deep(.fx-anchor) {
  position: relative;
  pointer-events: none;
}
.m3-fx :deep(.fx-anchor.fx-full) {
  grid-row: 1 / -1;
  grid-column: 1 / -1;
}
.m3-fx :deep(.fx-ring) {
  position: absolute;
  inset: 0;
  border: 2px solid rgba(255, 255, 255, 0.95);
  border-radius: 10px;
  animation: m3-fxRing 0.42s ease-out both;
  pointer-events: none;
}
@keyframes m3-fxRing {
  0% {
    transform: scale(0.65);
    opacity: 0.9;
  }
  100% {
    transform: scale(2.4);
    opacity: 0;
  }
}
.m3-fx :deep(.fx-spark) {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 7px;
  height: 7px;
  margin: -3.5px 0 0 -3.5px;
  border-radius: 50%;
  /* A bright core so a spark reads as light, tinted by the gem it came from. */
  background: radial-gradient(circle, #ffffff 0%, #ffffff 45%, var(--spark, #ffffff) 100%);
  box-shadow: 0 0 8px 1px var(--spark, #ffffff);
  animation: m3-fxSpark 0.46s cubic-bezier(0.2, 0.7, 0.4, 1) both;
  pointer-events: none;
}
@keyframes m3-fxSpark {
  0% {
    transform: translate(0, 0) scale(0.45);
    opacity: 1;
  }
  100% {
    transform: translate(calc(var(--sx, 0) * var(--spread, 30px)), calc(var(--sy, 0) * var(--spread, 30px))) scale(0);
    opacity: 0;
  }
}
.m3-fx :deep(.fx-float) {
  position: absolute;
  left: 50%;
  top: 50%;
  font:
    700 13px/1 'SFMono-Regular',
    Menlo,
    Consolas,
    monospace;
  color: #fff;
  text-shadow: 0 1px 3px rgba(20, 27, 48, 0.65);
  white-space: nowrap;
  animation: m3-fxFloat 0.8s ease-out both;
  pointer-events: none;
}
@keyframes m3-fxFloat {
  0% {
    transform: translate(-50%, -50%) scale(0.7);
    opacity: 0;
  }
  25% {
    transform: translate(-50%, -60%) scale(1.06);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -125%) scale(1);
    opacity: 0;
  }
}
.m3-fx :deep(.fx-combo) {
  position: absolute;
  left: 50%;
  top: 50%;
  font:
    800 40px/1 'SFMono-Regular',
    Menlo,
    Consolas,
    monospace;
  color: #fff;
  text-shadow:
    0 3px 14px rgba(79, 70, 229, 0.75),
    0 1px 2px rgba(20, 27, 48, 0.45);
  animation: m3-fxCombo 0.9s cubic-bezier(0.2, 1.4, 0.4, 1) both;
  pointer-events: none;
}
@keyframes m3-fxCombo {
  0% {
    transform: translate(-50%, -50%) scale(0.4);
    opacity: 0;
  }
  30% {
    transform: translate(-50%, -50%) scale(1.25);
    opacity: 1;
  }
  55% {
    transform: translate(-50%, -50%) scale(1);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -58%) scale(1);
    opacity: 0;
  }
}

/* Respect the platform motion preference: keep the flow identical but make
 * every decorative animation effectively instantaneous. Cleanup is driven by
 * the JS clock, never by animation events, so nothing leaks. */
@media (prefers-reduced-motion: reduce) {
  .m3-board :deep(.cell.swap),
  .m3-board :deep(.cell.fall),
  .m3-board :deep(.cell.drop),
  .m3-board :deep(.cell.clear),
  .m3-board :deep(.cell.clear::before),
  .m3-board :deep(.cell.clear::after),
  .m3-fx :deep(.fx-ring),
  .m3-fx :deep(.fx-spark),
  .m3-fx :deep(.fx-float),
  .m3-fx :deep(.fx-combo) {
    animation-duration: 1ms !important;
    animation-iteration-count: 1 !important;
  }
  .m3-board.shake {
    animation: none !important;
  }
}

/* ---------- overlays(开始 / 结算):外壳内绝对定位遮罩,不用 el-dialog ---------- */
.m3-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  border-radius: 16px;
  background: rgba(20, 27, 48, 0.42);
  text-align: center;
}
.m3-panel {
  width: min(320px, 100%);
  max-height: 100%;
  overflow: auto;
  padding: 20px 22px 18px;
  border: 1px solid var(--color-border, #d7deee);
  border-radius: 18px;
  background: var(--color-card, #fff);
  box-shadow: 0 16px 50px rgba(20, 27, 48, 0.3);
  animation: m3-rise 0.2s ease-out;
}
@keyframes m3-rise {
  from {
    transform: translateY(12px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}
.m3-panel h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: var(--color-text-primary, #1e2740);
}
.m3-panel p {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--color-text-secondary, #6b7592);
}

@media (max-width: 480px) {
  .m3-board-wrap {
    --gap: 4px;
    --pad: 6px;
  }
  .m3-stat {
    padding: 3px 7px;
    font-size: 11px;
  }
}

/* 手机横屏:三列 —— 左列记分 + 提示行,中列棋盘,右列 提示 重排 两键(贴棋盘右侧);
   记分与提示行本来住在 .m3-meta 里,这里 display:contents 把这一层拆掉,
   二者的盒子直接当网格项用,竖屏那套 DOM 不动 */
@media (orientation: landscape) and (max-height: 520px) {
  .m3-layout {
    --m3-meta-w: 122px;
    --m3-action-w: 54px;
    --m3-board-w: min(100%, 560px, calc(100cqh - 16px));
    display: grid;
    grid-template-columns: var(--m3-meta-w) minmax(0, 1fr) var(--m3-action-w);
    grid-template-rows: minmax(0, 1fr) auto;
    grid-template-areas:
      'stats board actions'
      'notice board actions';
    column-gap: 10px;
    row-gap: 4px;
    width: min(100%, calc(var(--m3-meta-w) + 20px + var(--m3-action-w) + var(--m3-board-w) + 16px));
    max-height: 100%;
  }
  .m3-meta {
    display: contents;
  }
  .m3-stats {
    grid-area: stats;
    align-self: start;
    flex: 0 0 auto;
    flex-direction: column;
    flex-wrap: nowrap;
    /* 芯片只包自身内容:左列不再整栏拉满,读起来紧凑 */
    align-items: flex-start;
    gap: 4px;
    width: 100%;
  }
  /* 横屏列里芯片压成一行:标签与数值相邻,内边距/字号跟外壳芯片同一紧凑档 */
  .m3-stat {
    justify-content: flex-start;
    padding: 2px 7px;
    font-size: 11px;
  }
  .m3-stat b {
    font-size: 12px;
  }
  .m3-notice {
    grid-area: notice;
    align-self: start;
    text-align: left;
  }
  /* 提示/重排:棋盘右侧竖排一列,整列贴着棋盘垂直居中 */
  .m3-actions {
    grid-area: actions;
    flex-direction: column;
    align-self: center;
    margin-left: 0;
    gap: 6px;
  }
  .m3-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
  .m3-board-wrap {
    grid-area: board;
    align-self: center;
  }
}
</style>
