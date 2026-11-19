<!-- Based on litegame-hub (MIT © 2026 DongGuoTao), localized as a Vue component.
     泡泡龙:六边形错位网格 + 同色三连消除 + 断开掉落 + 天花下压;瞄准/发射/难度/最高分
     逐字保留原实现(仍是命令式 canvas 渲染 + requestAnimationFrame 循环),本组件只换外壳、i18n 与 DOM 绑定。
     规范见 docs/UI设计提示词.md §43;配色取自原版深蓝面板,收在 --bb-* 令牌里 -->
<template>
  <GameShell
    ref="shellEl"
    class="bb-shell gs-on-dark"
    :title="$t('games.embedded.bubble.title')"
    body-align="center"
    @back="router.push('/games')"
  >
    <!-- 难度跟游戏名走(和象棋/黑白棋同一处),开局进行中锁定 -->
    <template #titleExtra>
      <el-select v-model="diffKey" class="bb-diff" :teleported="!isFullscreen" :disabled="diffLocked" @change="onDiffChange">
        <el-option value="easy" :label="$t('games.embedded.bubble.diffEasy')" />
        <el-option value="normal" :label="$t('games.embedded.bubble.diffNormal')" />
        <el-option value="hard" :label="$t('games.embedded.bubble.diffHard')" />
      </el-select>
    </template>

    <template #rules>
      <p>{{ $t('games.embedded.bubble.tip') }}</p>
      <p>{{ $t('games.embedded.bubble.aimTip') }}</p>
    </template>

    <!-- 计分芯片 + 操作贴画布:与画布同宽排在画布上方;手机横屏收成画布左侧一列 -->
    <div class="bb-layout" @contextmenu.prevent @dblclick.prevent>
      <div class="bb-meta gs-on-dark-surface">
        <div class="bb-actions">
          <!-- 本页的「新游戏」走自己的操作行(外壳头部那颗在泡泡龙不挂),配色跟全站一致走主按钮 -->
          <el-button type="primary" size="small" round :disabled="newDisabled" @click="restart">
            {{ $t('games.embedded.bubble.newGame') }}
          </el-button>
        </div>
        <div class="bb-chips">
          <div class="bb-chip">
            <span>{{ $t('games.embedded.bubble.score') }}</span>
            <b>{{ score }}</b>
          </div>
          <div class="bb-chip">
            <span>{{ $t('games.embedded.bubble.shots') }}</span>
            <b>{{ shots }}</b>
          </div>
          <div class="bb-chip">
            <span>{{ $t('games.embedded.bubble.progress') }}</span>
            <b>{{ progress }}</b>
          </div>
          <div class="bb-chip">
            <span>{{ $t('games.embedded.bubble.best') }}</span>
            <b>{{ best }}</b>
          </div>
        </div>
      </div>

      <div class="bb-frame">
        <canvas
          ref="cvRef"
          class="bb-canvas"
          @mousemove="onCanvasMove"
          @click="onCanvasClick"
          @touchstart.passive="onTouchStart"
          @touchmove.prevent="onTouchMove"
          @touchend.prevent="onTouchEnd"
        ></canvas>

        <div v-if="state === 'ready'" class="bb-overlay">
          <div class="bb-panel">
            <h2>{{ $t('games.embedded.bubble.title') }}</h2>
            <p>{{ $t('games.embedded.bubble.aimTip') }}</p>
            <el-button type="primary" size="large" round @click="startGame">
              {{ $t('games.embedded.bubble.start') }}
            </el-button>
          </div>
        </div>

        <div v-else-if="resultVisible" class="bb-overlay" @click.self="resultVisible = false">
          <div class="bb-panel">
            <h2>{{ resultTitle }}</h2>
            <p class="bb-result-sub">{{ resultSub }}</p>
            <el-button type="primary" size="large" round @click="restart">
              {{ $t('games.embedded.bubble.replay') }}
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
/** 命名空间快捷取词 */
const tt = (key, vars) => t(`games.embedded.bubble.${key}`, vars)

/* ================= 几何常量(原版逐字保留) ================= */
const R = 14 // 泡泡半径
const COLS = 14 // 每行泡泡数
const CELL = 2 * R // 偶数行相邻列的水平间距
const FIELD_W = COLS * CELL + R // 场地宽(奇数行刚好放得下)
const ROW_H = R * Math.sqrt(3) // 六边形密排的行高
const CEIL_Y = 8 // 天花板线
const FIELD_H = 600 // 画布高度
const WARN_Y = FIELD_H - 56 // 低于这条线即判负
const SHOOTER_X = FIELD_W / 2
const SHOOTER_Y = FIELD_H - 22
const SPEED = 7 // 每帧步长

const PALETTE = ['#ef4444', '#f59e0b', '#facc15', '#22c55e', '#3b82f6', '#a855f7']
const PALETTE_LIGHT = ['#fca5a5', '#fcd34d', '#fde68a', '#86efac', '#93c5fd', '#d8b4fe']

/* 原版另有 MAX_ROWS=60 的硬上限常量,但从未参与任何逻辑,迁移时省略 */
const DIFF = {
  easy: { colors: 4, initRows: 5, pushEvery: 8 },
  normal: { colors: 5, initRows: 6, pushEvery: 6 },
  hard: { colors: 6, initRows: 7, pushEvery: 5 },
}

/* ================= 状态 ================= */
let grid = [] // grid[r][c] = 颜色下标,空为 -1
const state = ref('ready') // 'ready' | 'playing' | 'over'
const diffKey = ref('normal')
let config = DIFF[diffKey.value]
const score = ref(0)
const shots = ref(0)
const progress = ref(0)
const best = ref(0)
const resultKind = ref('gameover')
const resultVisible = ref(false)
let curColor = 0 // 炮口里的颜色
let nextColor = 0 // NEXT 预览的颜色
let flying = null // 飞行中的泡泡 / null
let aimAngle = -Math.PI / 2 // 默认朝上
let particles = [] // 爆泡泡的碎屑

const newDisabled = computed(() => state.value === 'ready')
const diffLocked = computed(() => state.value === 'playing')
const resultTitle = computed(() => tt(resultKind.value))
const resultSub = computed(() => tt('resultSub', { score: score.value, best: best.value }))

/* ================= 画布 ================= */
const cvRef = ref(null)
let ctx = null
let dpr = 1

function setupCanvas() {
  const cv = cvRef.value
  if (!cv) return
  ctx = cv.getContext('2d')
  dpr = window.devicePixelRatio || 1
  cv.width = Math.round(FIELD_W * dpr)
  cv.height = Math.round(FIELD_H * dpr)
  // 位图按 dpr 放大,绘制坐标始终是 406×600 的逻辑尺寸
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
}
/** 缩放/跨屏后 dpr 变了才重建位图(尺寸本身交给 CSS 推导) */
function onResize() {
  const next = window.devicePixelRatio || 1
  if (Math.abs(next - dpr) < 0.01) return
  setupCanvas()
  render()
}

/* ================= 网格工具 ================= */
function centerX(r, c) {
  return r % 2 === 0 ? c * CELL + R : c * CELL + 2 * R
}
function centerY(r) {
  return CEIL_Y + R + r * ROW_H
}

/** (r,c) 的六个邻居:奇数行整体右移一列,所以上下三邻居的列号要 +1 */
function neighbors(r, c) {
  const even = r % 2 === 0
  const raw = even
    ? [
        [r, c - 1],
        [r, c + 1],
        [r - 1, c - 1],
        [r - 1, c],
        [r + 1, c - 1],
        [r + 1, c],
      ]
    : [
        [r, c - 1],
        [r, c + 1],
        [r - 1, c],
        [r - 1, c + 1],
        [r + 1, c],
        [r + 1, c + 1],
      ]
  const out = []
  for (let i = 0; i < raw.length; i++) {
    const nr = raw[i][0]
    const nc = raw[i][1]
    if (nr >= 0 && nc >= 0 && nc < COLS) out.push([nr, nc])
  }
  return out
}

function getColor(r, c) {
  if (r < 0 || r >= grid.length) return -1
  if (c < 0 || c >= COLS) return -1
  const v = grid[r][c]
  return v == null ? -1 : v
}
function setColor(r, c, v) {
  while (grid.length <= r) {
    const row = []
    for (let i = 0; i < COLS; i++) row[i] = -1
    grid.push(row)
  }
  grid[r][c] = v
}
function totalFilled() {
  let n = 0
  for (let r = 0; r < grid.length; r++) {
    if (!grid[r]) continue
    for (let c = 0; c < COLS; c++) if (grid[r][c] >= 0) n++
  }
  return n
}

/* ================= 消除 / 掉落规则 ================= */
/** 从 (r,c) 出发,同色四连通泛洪 */
function findGroup(r, c) {
  const color = getColor(r, c)
  if (color < 0) return []
  const visited = {}
  const stack = [[r, c]]
  const out = []
  while (stack.length) {
    const p = stack.pop()
    const key = p[0] + ',' + p[1]
    if (visited[key]) continue
    visited[key] = true
    out.push(p)
    const ns = neighbors(p[0], p[1])
    for (let i = 0; i < ns.length; i++) {
      const nr = ns[i][0]
      const nc = ns[i][1]
      const k2 = nr + ',' + nc
      if (!visited[k2] && getColor(nr, nc) === color) stack.push([nr, nc])
    }
  }
  return out
}

/** 与顶行(不论颜色)不连通的格子会整串掉落 */
function findFloating() {
  const visited = {}
  const stack = []
  for (let c = 0; c < COLS; c++) {
    if (getColor(0, c) >= 0) {
      visited['0,' + c] = true
      stack.push([0, c])
    }
  }
  while (stack.length) {
    const p = stack.pop()
    const ns = neighbors(p[0], p[1])
    for (let i = 0; i < ns.length; i++) {
      const nr = ns[i][0]
      const nc = ns[i][1]
      const k = nr + ',' + nc
      if (!visited[k] && getColor(nr, nc) >= 0) {
        visited[k] = true
        stack.push([nr, nc])
      }
    }
  }
  const out = []
  for (let r = 0; r < grid.length; r++) {
    if (!grid[r]) continue
    for (let cc = 0; cc < COLS; cc++) {
      if (grid[r][cc] >= 0 && !visited[r + ',' + cc]) out.push([r, cc])
    }
  }
  return out
}

/** 像素坐标 → 最近的空格(泡泡该粘在哪一格) */
function resolveCell(x, y) {
  let r = Math.round((y - CEIL_Y - R) / ROW_H)
  if (r < 0) r = 0
  const maxR = Math.floor((WARN_Y - CEIL_Y - R) / ROW_H)
  if (r > maxR) r = maxR
  let c = Math.round((x - (r % 2 === 0 ? R : 2 * R)) / CELL)
  if (c < 0) c = 0
  if (c > COLS - 1) c = COLS - 1
  if (getColor(r, c) >= 0) {
    let bestCell = null
    let bestD = Infinity
    const cands = [[r, c]].concat(neighbors(r, c))
    for (let i = 0; i < cands.length; i++) {
      const nr = cands[i][0]
      const nc = cands[i][1]
      if (nr < 0 || nc < 0 || nc >= COLS) continue
      if (getColor(nr, nc) >= 0) continue
      const dx = x - centerX(nr, nc)
      const dy = y - centerY(nr)
      const d = dx * dx + dy * dy
      if (d < bestD) {
        bestD = d
        bestCell = [nr, nc]
      }
    }
    if (bestCell) return bestCell
  }
  return [r, c]
}

/** 新粘上的泡泡走一遍「同色三连爆 + 断开掉落」,返回结果计数 */
function applyMatches(r, c) {
  const group = findGroup(r, c)
  let removed = 0
  let dropped = 0
  if (group.length >= 3) {
    for (let i = 0; i < group.length; i++) {
      spawnParticles(centerX(group[i][0], group[i][1]), centerY(group[i][0]), getColor(group[i][0], group[i][1]))
      setColor(group[i][0], group[i][1], -1)
    }
    removed = group.length
    const floating = findFloating()
    for (let j = 0; j < floating.length; j++) {
      spawnParticles(centerX(floating[j][0], floating[j][1]), centerY(floating[j][0]), getColor(floating[j][0], floating[j][1]))
      setColor(floating[j][0], floating[j][1], -1)
    }
    dropped = floating.length
  }
  return { removed: removed, dropped: dropped }
}

/* ================= 对局生命周期 ================= */
function randColor() {
  return Math.floor(Math.random() * config.colors)
}

function setupBoard() {
  config = DIFF[diffKey.value]
  grid = []
  for (let r = 0; r < config.initRows; r++) {
    for (let c = 0; c < COLS; c++) setColor(r, c, randColor())
  }
  score.value = 0
  shots.value = 0
  flying = null
  particles = []
  curColor = randColor()
  nextColor = randColor()
  aimAngle = -Math.PI / 2
  state.value = 'ready'
  resultVisible.value = false
  updateHud()
}

function startGame() {
  if (state.value === 'playing') return // 已在局中,忽略多余点击
  loadBest()
  state.value = 'playing'
  resultVisible.value = false
  updateHud()
}

function restart() {
  setupBoard()
  startGame()
}

function fire() {
  if (state.value !== 'playing' || flying) return
  const vx = Math.cos(aimAngle) * SPEED
  const vy = Math.sin(aimAngle) * SPEED
  flying = { x: SHOOTER_X, y: SHOOTER_Y, vx: vx, vy: vy, color: curColor }
  shots.value++
  curColor = nextColor
  nextColor = randColor()
  updateHud()
}

function stepPhysics() {
  if (!flying) return
  flying.x += flying.vx
  flying.y += flying.vy
  // 左右墙反弹(每帧至多一次,两侧都可能)
  if (flying.x - R < 0) {
    flying.x = R
    flying.vx = -flying.vx
  }
  if (flying.x + R > FIELD_W) {
    flying.x = FIELD_W - R
    flying.vx = -flying.vx
  }
  // 顶到天花板 → 粘住
  if (flying.y - R <= CEIL_Y) {
    attach()
    return
  }
  // 撞到已有泡泡 → 粘到最近的空格
  let hit = false
  for (let r = 0; r < grid.length; r++) {
    const row = grid[r]
    if (!row) continue
    for (let c = 0; c < COLS; c++) {
      if (row[c] < 0) continue
      const dx = flying.x - centerX(r, c)
      const dy = flying.y - centerY(r)
      const reach = 2 * R * 0.92
      if (dx * dx + dy * dy < reach * reach) {
        hit = true
        break
      }
    }
    if (hit) break
  }
  if (hit) attach()
}

function attach() {
  const cell = resolveCell(flying.x, flying.y)
  const r = cell[0]
  const c = cell[1]
  setColor(r, c, flying.color)
  flying = null

  const res = applyMatches(r, c)
  if (res.removed > 0) {
    score.value += res.removed * 10 + res.dropped * 20 + (res.dropped > 0 ? 50 : 0)
  }

  // 每发射 N 次天花下压一行
  if (shots.value % config.pushEvery === 0) pushRow()

  if (score.value > best.value) {
    best.value = score.value
    saveBest()
  }

  if (totalFilled() === 0) endGame('cleared')
  else checkGameOver()
  updateHud()
}

function pushRow() {
  const row = []
  for (let c = 0; c < COLS; c++) row[c] = randColor()
  grid.unshift(row) // 所有已有行的奇偶随之翻转,正是要的效果
}

function checkGameOver() {
  for (let r = 0; r < grid.length; r++) {
    if (!grid[r]) continue
    for (let c = 0; c < COLS; c++) {
      if (grid[r][c] >= 0 && centerY(r) + R > WARN_Y) {
        endGame('gameover')
        return true
      }
    }
  }
  return false
}

function endGame(kind) {
  state.value = 'over'
  resultKind.value = kind === 'cleared' ? 'cleared' : 'gameover'
  resultVisible.value = true
  updateHud()
}

/* ================= 碎屑 ================= */
function spawnParticles(cx, cy, color) {
  if (color < 0) return
  for (let i = 0; i < 6; i++) {
    const a = Math.random() * Math.PI * 2
    const sp = 1 + Math.random() * 2.5
    particles.push({
      x: cx,
      y: cy,
      vx: Math.cos(a) * sp,
      vy: Math.sin(a) * sp,
      life: 18 + Math.floor(Math.random() * 10),
      color: color,
    })
  }
}
function updateParticles() {
  for (let i = particles.length - 1; i >= 0; i--) {
    const p = particles[i]
    p.x += p.vx
    p.y += p.vy
    p.vy += 0.15
    p.life--
    if (p.life <= 0) particles.splice(i, 1)
  }
}

/* ================= 渲染 ================= */
function drawBubble(cx, cy, colorIdx, scale) {
  if (colorIdx < 0) return
  const rad = R * (scale || 1)
  const col = PALETTE[colorIdx] || '#888'
  const light = PALETTE_LIGHT[colorIdx] || '#bbb'
  const g = ctx.createRadialGradient(cx - rad * 0.35, cy - rad * 0.35, rad * 0.15, cx, cy, rad)
  g.addColorStop(0, light)
  g.addColorStop(1, col)
  ctx.beginPath()
  ctx.arc(cx, cy, rad, 0, Math.PI * 2)
  ctx.fillStyle = g
  ctx.fill()
  // 高光
  ctx.beginPath()
  ctx.arc(cx - rad * 0.3, cy - rad * 0.35, rad * 0.22, 0, Math.PI * 2)
  ctx.fillStyle = 'rgba(255,255,255,0.55)'
  ctx.fill()
}

function render() {
  if (!ctx) return
  ctx.clearRect(0, 0, FIELD_W, FIELD_H)

  // 天花板
  ctx.fillStyle = 'rgba(124,156,255,0.25)'
  ctx.fillRect(0, 0, FIELD_W, CEIL_Y)

  // 警戒线
  ctx.strokeStyle = 'rgba(248,113,113,0.55)'
  ctx.setLineDash([8, 6])
  ctx.beginPath()
  ctx.moveTo(0, WARN_Y)
  ctx.lineTo(FIELD_W, WARN_Y)
  ctx.stroke()
  ctx.setLineDash([])

  // 已落位的泡泡
  for (let r = 0; r < grid.length; r++) {
    if (!grid[r]) continue
    for (let c = 0; c < COLS; c++) {
      if (grid[r][c] >= 0) drawBubble(centerX(r, c), centerY(r), grid[r][c], 1)
    }
  }

  // 碎屑
  for (let i = 0; i < particles.length; i++) {
    const p = particles[i]
    ctx.globalAlpha = Math.max(0, p.life / 24)
    drawBubble(p.x, p.y, p.color, 0.5)
    ctx.globalAlpha = 1
  }

  // 飞行中的泡泡
  if (flying) drawBubble(flying.x, flying.y, flying.color, 1)

  // 炮座(画在装的泡泡底下,当前颜色才看得见)
  ctx.fillStyle = '#2c3358'
  ctx.beginPath()
  ctx.arc(SHOOTER_X, SHOOTER_Y, R + 6, 0, Math.PI * 2)
  ctx.fill()

  // 瞄准虚线 + 炮口里的泡泡(只在局中且没有飞行泡泡时)
  if (state.value === 'playing' && !flying) {
    ctx.strokeStyle = 'rgba(255,255,255,0.28)'
    ctx.setLineDash([4, 6])
    ctx.beginPath()
    ctx.moveTo(SHOOTER_X, SHOOTER_Y)
    ctx.lineTo(SHOOTER_X + Math.cos(aimAngle) * 120, SHOOTER_Y + Math.sin(aimAngle) * 120)
    ctx.stroke()
    ctx.setLineDash([])
    drawBubble(SHOOTER_X, SHOOTER_Y, curColor, 1)
  }

  // 右下角的 NEXT 预览(原版画布上的固定标记,不进 i18n)
  const nx = FIELD_W - 30
  const ny = FIELD_H - 24
  ctx.fillStyle = 'rgba(255,255,255,0.06)'
  ctx.beginPath()
  ctx.arc(nx, ny, R + 8, 0, Math.PI * 2)
  ctx.fill()
  drawBubble(nx, ny, nextColor, 0.92)
  ctx.fillStyle = 'rgba(255,255,255,0.55)'
  ctx.font = '10px sans-serif'
  ctx.fillText('NEXT', nx - 12, ny - R - 12)
}

/* ================= 主循环 ================= */
let rafId = 0
function loop() {
  if (state.value === 'playing' && flying) stepPhysics()
  updateParticles()
  render()
  rafId = requestAnimationFrame(loop)
}

/* ================= 计分 HUD ================= */
function loadBest() {
  try {
    const v = parseInt(localStorage.getItem('bubble_best') || '0', 10)
    best.value = Number.isNaN(v) ? 0 : v
  } catch {
    best.value = 0
  }
}
function saveBest() {
  try {
    localStorage.setItem('bubble_best', String(best.value))
  } catch {
    // 隐私模式/配额满:最高分只影响本机记忆,静默忽略
  }
}

function updateHud() {
  progress.value = config.pushEvery - (shots.value % config.pushEvery)
}

/* ================= 瞄准与事件 ================= */
function clampAim() {
  const min = -Math.PI + 0.12
  const max = -0.12
  if (aimAngle > max) aimAngle = max
  if (aimAngle < min) aimAngle = min
}

function aimFromEvent(ev) {
  if (typeof ev.clientX !== 'number' || typeof ev.clientY !== 'number') return
  const cv = cvRef.value
  if (!cv) return
  const rect = cv.getBoundingClientRect()
  // CSS 可缩放显示尺寸:按 rect 把视口坐标换算回 406×600 的逻辑坐标
  const sx = rect.width ? FIELD_W / rect.width : 1
  const sy = rect.height ? FIELD_H / rect.height : 1
  const fx = (ev.clientX - rect.left) * sx
  const fy = (ev.clientY - rect.top) * sy
  const dx = fx - SHOOTER_X
  const dy = fy - SHOOTER_Y
  if (dy > -2) return // 永远不往下瞄
  aimAngle = Math.atan2(dy, dx)
  clampAim()
}

function onCanvasMove(ev) {
  aimFromEvent(ev)
}

function onCanvasClick(ev) {
  aimFromEvent(ev)
  fire()
}

function onTouchStart(ev) {
  const t2 = ev.changedTouches && ev.changedTouches[0]
  if (t2) aimFromEvent(t2)
}

function onTouchMove(ev) {
  const t2 = ev.changedTouches && ev.changedTouches[0]
  if (t2) aimFromEvent(t2)
}

/** 松手即发射(原版同款:触摸拖动只调角度,不中途发射) */
function onTouchEnd() {
  fire()
}

function onDiffChange() {
  if (state.value !== 'playing') setupBoard() // 局中不许换难度重摆
}

function onKeydown(ev) {
  // 焦点在表单控件里时不抢键(难度下拉/规则弹窗)
  if (ev.target instanceof HTMLElement && ev.target.closest('input, textarea, select, [contenteditable="true"]')) return
  const k = ev.key
  if (state.value === 'ready' || state.value === 'over') {
    if (k === ' ' || k === 'Enter') {
      ev.preventDefault()
      startGame()
    }
    return
  }
  if (k === 'ArrowLeft' || k === 'a' || k === 'A') {
    aimAngle -= 0.06
    clampAim()
    ev.preventDefault()
  } else if (k === 'ArrowRight' || k === 'd' || k === 'D') {
    aimAngle += 0.06
    clampAim()
    ev.preventDefault()
  } else if (k === ' ') {
    ev.preventDefault()
    fire()
  }
}

// 语言切换:结算文案是 computed 自动跟随,这里把 HUD 与画布再刷一遍
watch(locale, () => {
  updateHud()
  render()
})

onMounted(() => {
  sunLight?.suspendEffects()
  setupCanvas()
  setupBoard()
  rafId = requestAnimationFrame(loop)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', onResize, { passive: true })
})

onBeforeUnmount(() => {
  cancelAnimationFrame(rafId)
  rafId = 0
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', onResize)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 原版深蓝整页渐变(--gs-bg,必须同时给 dark 档);面板/气泡色取自原版调色板 */
.bb-shell {
  --bb-panel: #1a1f3a;
  --bb-line: #2c3358;
  --bb-muted: #9aa3c7;
  --bb-ink: #eef1ff;
  --bb-chip-bg: rgba(17, 21, 46, 0.9);
  --bb-chip-ink: #b9c4ff;
  --gs-accent: #7c9cff;
  --gs-bg: radial-gradient(120% 120% at 50% 0%, #1b2148 0%, #0f1226 60%);
}
html.dark .bb-shell {
  --gs-bg: radial-gradient(120% 120% at 50% 0%, #131834 0%, #070915 60%);
}
/* 深底上的标题/头部按钮统一交给全局 .gs-on-dark(见 main.css 的「深色游戏底」段) */
.bb-diff {
  width: 88px;
}

/* 计分芯片 + 画布:竖屏自上而下、芯片横在画布上方;手机横屏收成画布左侧一列 */
.bb-layout {
  /* 画布宽 = min(容器宽, 440, 剩余高度 × 宽高比 406/600);84px 留给上方两行芯片与间隙 */
  --bb-cv-w: min(100%, 440px, calc((100cqh - 84px) * 0.676667));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}
.bb-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: var(--bb-cv-w);
  min-width: 0;
}
/* 按钮靠画布右端;横屏时挪到画布左上 */
.bb-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}
/* EP 给相邻按钮加 margin-left:12px,这里间距只走 gap */
.bb-actions .el-button + .el-button {
  margin-left: 0;
}
.bb-chips {
  display: flex;
  align-items: stretch;
  gap: 6px;
  min-width: 0;
}
.bb-chip {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  padding: 3px 8px;
  background: var(--bb-chip-bg);
  border: 1px solid var(--bb-line);
  border-radius: 8px;
  font-size: 11px;
  color: var(--bb-chip-ink);
  white-space: nowrap;
}
.bb-chip b {
  font-size: 13px;
  color: #fff;
  font-variant-numeric: tabular-nums;
}
/* 窄屏竖屏:四个芯片挤一行会溢出(分数会长到 5~6 位),改 2×2;竖屏这里高度本来就有余量 */
@media (max-width: 420px) and (orientation: portrait) {
  .bb-layout {
    --bb-cv-w: min(100%, 440px, calc((100cqh - 112px) * 0.676667));
  }
  .bb-chips {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}

/* 画布外框:原版深蓝面板 */
.bb-frame {
  position: relative;
  width: var(--bb-cv-w);
  box-sizing: border-box;
  padding: 6px;
  background: var(--bb-panel);
  border: 1px solid var(--bb-line);
  border-radius: 14px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.4);
}
.bb-canvas {
  display: block;
  width: 100%;
  height: auto;
  border: 1px solid var(--bb-line);
  border-radius: 10px;
  background: linear-gradient(180deg, #141935, #0e1126);
  touch-action: none;
  user-select: none;
  cursor: crosshair;
}

/* 开始/结算遮罩:绝对定位盖画布(不用 el-dialog) */
.bb-overlay {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  background: rgba(8, 10, 22, 0.66);
  border-radius: 14px;
  text-align: center;
}
.bb-panel {
  max-width: min(320px, 100%);
  padding: 20px 18px;
  background: var(--bb-panel);
  border: 1px solid var(--bb-line);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.45);
}
.bb-panel h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: var(--bb-ink);
}
.bb-panel p {
  margin: 0 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--bb-muted);
}
.bb-result-sub {
  font-family: 'SFMono-Regular', Menlo, Consolas, monospace;
  color: var(--bb-chip-ink);
}

/* 手机横屏:按钮 + 芯片收成画布左侧一列(按钮贴画布一侧靠上),画布独占右侧 */
@media (orientation: landscape) and (max-height: 520px) {
  .bb-layout {
    --bb-meta-w: 132px;
    --bb-cv-w: min(100%, 440px, calc((100cqh - 22px) * 0.676667));
    display: grid;
    grid-template-columns: var(--bb-meta-w) minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
    grid-template-areas: 'meta frame';
    column-gap: 10px;
    width: min(100%, calc(var(--bb-meta-w) + 10px + var(--bb-cv-w) + 14px));
    max-height: 100%;
  }
  .bb-meta {
    grid-area: meta;
    align-self: start;
    width: 100%;
    height: auto;
    gap: 8px;
  }
  .bb-actions {
    order: -1;
  }
  .bb-chips {
    flex-direction: column;
    gap: 6px;
  }
  /* 横屏列里芯片压成一行:标签靠左、数值靠右(和黑白棋/扫雷左列同一读法) */
  .bb-chip {
    flex: 0 0 auto;
    padding: 4px 8px;
  }
  .bb-frame {
    grid-area: frame;
    align-self: center;
    max-width: 100%;
  }
  .bb-panel {
    padding: 14px 16px;
  }
  .bb-panel h2 {
    font-size: 18px;
  }
  .bb-panel p {
    margin-bottom: 10px;
  }
}
</style>
