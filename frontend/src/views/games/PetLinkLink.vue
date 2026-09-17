<template>
  <div ref="pageEl" class="game-page" :class="{ 'is-cover': screen === 'cover' }">
    <!-- 全屏按钮(原生 Fullscreen API,对 .game-page 全屏) -->
    <button
      class="fullscreen-btn"
      type="button"
      :title="isFullscreen ? $t('games.petlink.exitFullscreen') : $t('games.petlink.fullscreen')"
      @click="toggleFullscreen"
    >
      <el-icon><FullScreen /></el-icon>
    </button>

    <!-- 封面(复刻原版标题屏):多色标题 + ihomy 版后缀 + 开始 + 版权) -->
    <div v-if="screen === 'cover'" class="cover">
      <h1 class="cover-title">
        <span class="ct-pet">宠</span><span class="ct-pet">物</span><span class="ct-lian">连</span><span class="ct-lian">连</span><span class="ct-kan">看</span>
        <span class="ct-ihomy">-ihomy版</span>
      </h1>
      <button class="start-btn" type="button" @click="startGame">
        {{ $t('games.petlink.start') }}
      </button>
      <div class="cover-credits">{{ $t('games.petlink.credits') }}</div>
      <div class="cover-hint">{{ $t('games.petlink.coverHint') }}</div>
    </div>

    <!-- 游戏 -->
    <template v-else>
      <!-- 顶部信息栏(复刻原版 HUD):关卡+关卡名 / 重排 / 倒计时条 / 得分 -->
      <div class="game-hud">
        <div class="hud-level">
          <span class="hud-level-no">{{ levelLabel }}</span>
          <span class="hud-level-name">{{ levelName }}</span>
        </div>
        <div class="hud-life" :title="$t('games.petlink.shufflesLeft', { n: shufflesLeft })">
          <b class="life-num">{{ shufflesLeft }}</b>
          <span class="life-label">{{ $t('games.petlink.life') }}</span>
        </div>
        <div class="hud-timer">
          <div class="timer-bar" :class="{ warn: timeLeft <= 20 }">
            <div class="timer-fill" :style="{ width: timePct + '%' }"></div>
          </div>
          <span class="timer-text" :class="{ warn: timeLeft <= 20 }">{{ timeText }}</span>
        </div>
        <div class="hud-score">
          <span class="hud-score-label">{{ $t('games.petlink.score') }}</span>
          <b>{{ score }}</b>
        </div>
      </div>

      <div class="board-scroll" ref="boardScrollRef">
        <div class="board-scale" :style="{ width: scaledW + 'px', height: scaledH + 'px' }">
          <div class="board-wrap" :class="{ 'is-frozen': animating || paused }" :style="{ transform: `scale(${boardScale})` }">
            <div class="board" ref="boardEl">
              <button
                v-for="cell in cells"
                :key="cell.tid ? 't' + cell.tid : 'e' + cell.idx"
                :data-tid="cell.tid"
                class="tile"
                :class="{
                  'is-empty': cell.value === 0,
                  'is-selected': selected === cell.idx,
                  'is-hint': hinted.includes(cell.idx),
                }"
                :disabled="animating || paused || won || lost"
                @click="onClick(cell.idx)"
              >
                <img v-if="cell.value !== 0" :src="petImages[cell.value - 1]" alt="" class="pet" />
              </button>
            </div>

            <svg class="board-line" :viewBox="`0 0 ${svgW} ${svgH}`">
              <polyline v-if="linePoints" :points="linePoints" class="line" />
            </svg>

            <div v-if="paused" class="pause-mask">
              <div class="pause-label">{{ $t('games.petlink.paused') }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="game-bottombar">
        <el-button size="small" :disabled="frozen" @click="doHint">💡 {{ $t('games.petlink.hint') }}</el-button>
        <el-button size="small" :disabled="frozen || shufflesLeft <= 0" @click="doShuffle">
          🔀 {{ $t('games.petlink.shuffle') }}
        </el-button>
        <el-button size="small" :disabled="won || lost" @click="togglePause">
          {{ paused ? '▶' : '⏸' }} {{ $t('games.petlink.pause') }}
        </el-button>
        <el-button size="small" @click="restart">↺ {{ $t('games.petlink.restart') }}</el-button>
      </div>

      <!-- 胜负弹层 -->
      <div v-if="won || lost" class="overlay">
        <div class="overlay-card">
          <div class="overlay-icon">{{ won ? '🎉' : '😿' }}</div>
          <div class="overlay-title">{{ won ? $t('games.petlink.win') : $t('games.petlink.lose') }}</div>
          <div class="overlay-score">{{ $t('games.petlink.score') }} {{ score }}</div>
          <div v-if="won && rewardMsg" class="overlay-reward">{{ rewardMsg }}</div>
          <el-button type="primary" size="small" round @click="restart">{{ $t('games.petlink.playAgain') }}</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
// 宠物连连看 ihomy 版(自 Flash 5「宠物连连看2.5」复刻,原作者村子 cunzi.com):
// 黑底白卡蓝边 40 种宠物、9 关(关卡名:入门/增加难度/上下分离/左右分离/向左看齐/
// 地心引力/飘向天空/向右看齐/中央集中)、150s 倒计时(每消除一对 +3s)、得分 +10/对、
// 过关时间奖励 = 剩余秒数×4、重排(第 1~9 关 6/7/8/9/11/13/15/17/19 次)、
// 蓝色连线动画;通关按后端发积分。已移除音效(背景音乐不要了),支持全屏,进入时关闭全局光影特效。
import { ref, computed, onBeforeUnmount, onMounted, nextTick, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { FullScreen } from '@element-plus/icons-vue'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { t, locale } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

// 原版棋盘:16×12 格(含一圈 1 格外圈用于绕外圈连线),内圈 14×10 为可玩区;
// 格子 44×40 间距(卡牌 42×40、左右间隙 2px、上下无间隙)。
const ROWS = 10
const COLS = 14
const CELL_W = 42
const CELL_H = 40
const GAP_X = 2
const GAP_Y = 0
const PAD = 44 // 外圈占位(供连线 SVG 绕外圈绘制)
const MAX_LEVEL = 9
const LEVEL_TIME = 150 // 秒,与原件 1800 帧 @12fps 一致
const TIME_BONUS = 3 // 每消除一对补充 3 秒(原件 36 帧)
const BASE_SHUFFLE = 6

// 40 种宠物(按 sprite 149 帧序),用 import.meta.glob 按文件名排序加载
const petModules = import.meta.glob('@/assets/games/petlink/*.png', { eager: true, import: 'default' })
const petImages = Object.keys(petModules).sort().map(k => petModules[k])

const screen = ref('cover')

// 扁平 140,每格为 null(空) 或 { id, type }(id 唯一,用于 FLIP 滑行动画稳定身份)
const board = ref([])
let tileSeq = 0
const score = ref(0)
const level = ref(1)
const shufflesLeft = ref(BASE_SHUFFLE)
const timeLeft = ref(LEVEL_TIME)
const selected = ref(-1)
const hinted = ref([])
const animating = ref(false)
const paused = ref(false)
const won = ref(false)
const lost = ref(false)
const linePoints = ref('')
const rewardMsg = ref('')

const boardW = COLS * CELL_W + (COLS - 1) * GAP_X
const boardH = ROWS * CELL_H + (ROWS - 1) * GAP_Y
const svgW = boardW + PAD * 2
const svgH = boardH + PAD * 2

const cells = computed(() => board.value.map((t, idx) => ({ idx, value: t ? t.type : 0, tid: t ? t.id : 0 })))
const boardScrollRef = ref(null)
const boardEl = ref(null)
const boardScale = ref(1)
const scaledW = computed(() => Math.round(svgW * boardScale.value))
const scaledH = computed(() => Math.round(svgH * boardScale.value))
const frozen = computed(() => animating.value || paused.value || won.value || lost.value)
const levelName = computed(() => t(`games.petlink.levelNames.${level.value}`) || '')
const CN_NUMS = ['一', '二', '三', '四', '五', '六', '七', '八', '九']
// 原版关卡号用中文数字:第一关/第二关…(英文回退 Level N)
const levelLabel = computed(() => {
  if (locale.value === 'en') return `Level ${level.value}`
  return `第${CN_NUMS[level.value - 1] || level.value}关`
})
const timeText = computed(() => {
  const m = Math.floor(timeLeft.value / 60)
  const s = timeLeft.value % 60
  return `${m}:${String(s).padStart(2, '0')}`
})
const timePct = computed(() => Math.max(0, Math.min(100, (timeLeft.value / LEVEL_TIME) * 100)))

let ticker = null
let hintTimer = null
let resizeObserver = null

// 全屏(原生 Fullscreen API,对 .game-page 全屏)
const pageEl = ref(null)
const isFullscreen = ref(false)
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    pageEl.value?.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}
function onFullscreenChange() { isFullscreen.value = !!document.fullscreenElement }

// ---------- 关卡 ----------

function typeCountOf(lv) {
  return lv === 1 ? 30 : lv === 2 ? 35 : 40
}

function buildBoard(lv) {
  const typeCount = typeCountOf(lv)
  const arr = []
  for (let i = 1; i <= typeCount; i++) arr.push(i)
  for (let i = typeCount + 1; i <= 70; i++) arr.push(Math.floor(Math.random() * typeCount) + 1)
  const pairs = [...arr, ...arr]
  for (let k = pairs.length - 1; k > 0; k--) {
    const m = Math.floor(Math.random() * (k + 1))
    ;[pairs[k], pairs[m]] = [pairs[m], pairs[k]]
  }
  return pairs.map(type => ({ id: ++tileSeq, type }))
}

// ---------- 连线算法(移植原 findlu:最多两拐 + 可绕外圈) ----------

function get(r, c) {
  if (r < 0 || r >= ROWS || c < 0 || c >= COLS) return 0
  const t = board.value[r * COLS + c]
  return t ? t.type : 0
}
function walkable(r, c) { return get(r, c) === 0 }
function horizClear(r, c1, c2) {
  const lo = Math.min(c1, c2), hi = Math.max(c1, c2)
  for (let c = lo + 1; c < hi; c++) if (get(r, c) !== 0) return false
  return true
}
function vertClear(c, r1, r2) {
  const lo = Math.min(r1, r2), hi = Math.max(r1, r2)
  for (let r = lo + 1; r < hi; r++) if (get(r, c) !== 0) return false
  return true
}
function findPath(r1, c1, r2, c2) {
  if (r1 === r2 && c1 === c2) return null
  const a = get(r1, c1), b = get(r2, c2)
  if (a === 0 || b === 0 || a !== b) return null
  if (r1 === r2 && horizClear(r1, c1, c2)) return [[r1, c1], [r2, c2]]
  if (c1 === c2 && vertClear(c1, r1, r2)) return [[r1, c1], [r2, c2]]
  if (walkable(r1, c2) && horizClear(r1, c1, c2) && vertClear(c2, r1, r2)) return [[r1, c1], [r1, c2], [r2, c2]]
  if (walkable(r2, c1) && vertClear(c1, r1, r2) && horizClear(r2, c1, c2)) return [[r1, c1], [r2, c1], [r2, c2]]
  for (let c = -1; c <= COLS; c++) {
    if (c === c1 || c === c2) continue
    if (walkable(r1, c) && walkable(r2, c) && horizClear(r1, c1, c) && horizClear(r2, c2, c) && vertClear(c, r1, r2)) {
      return [[r1, c1], [r1, c], [r2, c], [r2, c2]]
    }
  }
  for (let r = -1; r <= ROWS; r++) {
    if (r === r1 || r === r2) continue
    if (walkable(r, c1) && walkable(r, c2) && vertClear(c1, r1, r) && vertClear(c2, r2, r) && horizClear(r, c1, c2)) {
      return [[r1, c1], [r, c1], [r, c2], [r2, c2]]
    }
  }
  return null
}
function findAnyPair() {
  const groups = {}
  board.value.forEach((t, i) => { if (t) (groups[t.type] ||= []).push(i) })
  for (const v in groups) {
    const arr = groups[v]
    for (let a = 0; a < arr.length; a++) {
      for (let b = a + 1; b < arr.length; b++) {
        const i = arr[a], j = arr[b]
        if (findPath(Math.floor(i / COLS), i % COLS, Math.floor(j / COLS), j % COLS)) return [i, j]
      }
    }
  }
  return null
}

// ---------- 交互 ----------

function onClick(i) {
  if (frozen.value) return
  if (!board.value[i]) return
  hinted.value = []
  if (selected.value === -1) { selected.value = i; return }
  if (selected.value === i) { selected.value = -1; return }
  const s = selected.value
  if (board.value[i].type !== board.value[s].type) { selected.value = i; return }
  const path = findPath(Math.floor(s / COLS), s % COLS, Math.floor(i / COLS), i % COLS)
  selected.value = -1
  if (path) match(s, i, path)
}

async function match(a, b, path) {
  animating.value = true
  linePoints.value = path.map(([r, c]) => `${centerX(c)},${centerY(r)}`).join(' ')
  await sleep(280)
  linePoints.value = ''

  // FLIP:先记录各牌当前屏幕位置,再应用消除/牌位移动,最后从旧位置滑到新位置
  const before = snapshotPositions()
  applyGravity(a, b)
  score.value += 10
  // 消除补充倒计时:每消除一对 +3 秒(原件 thetimer 回退 36 帧)
  timeLeft.value = Math.min(LEVEL_TIME, timeLeft.value + TIME_BONUS)
  await nextTick()
  flipAnimate(before)
  await sleep(220)
  animating.value = false

  if (board.value.every(v => v === null)) {
    await levelComplete()
    return
  }
  while (!findAnyPair() && shufflesLeft.value > 0) {
    applyShuffle()
    ElMessage.info(t('games.petlink.autoShuffle'))
  }
  if (board.value.some(v => v !== null) && !findAnyPair()) {
    lost.value = true
    stopTicker()
  }
}

function doHint() {
  if (frozen.value) return
  const pair = findAnyPair()
  if (!pair) return
  hinted.value = [...pair]
  clearTimeout(hintTimer)
  hintTimer = setTimeout(() => { hinted.value = [] }, 1600)
}

function doShuffle() {
  if (frozen.value || shufflesLeft.value <= 0) return
  applyShuffle()
}

function applyShuffle() {
  const remaining = board.value.filter(v => v !== null)
  for (let k = remaining.length - 1; k > 0; k--) {
    const m = Math.floor(Math.random() * (k + 1))
    ;[remaining[k], remaining[m]] = [remaining[m], remaining[k]]
  }
  let p = 0
  board.value = board.value.map(v => (v !== null ? remaining[p++] : null))
  shufflesLeft.value -= 1
  selected.value = -1
  hinted.value = []
}

// ---------- 消除后牌位移动(复刻原版 the_pass_nm 分支) ----------
// 第 1~2 关仅消除;第 3~9 关消除后按关卡名做不同的牌位聚拢:
// 3 上下分离(上半上移/下半下移)、4 左右分离(左半左移/右半右移)、5 向左看齐(整行左移)、
// 6 地心引力(整列下移)、7 飘向天空(整列上移)、8 向右看齐(整行右移)、9 中央集中(向中心聚拢)。

function applyGravity(a, b) {
  const lv = level.value
  if (lv <= 2) {
    board.value[a] = null
    board.value[b] = null
    return
  }
  const ra = Math.floor(a / COLS), ca = a % COLS
  const rb = Math.floor(b / COLS), cb = b % COLS
  let t1 = { r: ra, c: ca }, t2 = { r: rb, c: cb }

  if (lv === 3) {
    // 上下分离:同列且同半区时,上半区行大者先(上移)、下半区行小者先(下移)
    if (ca === cb) {
      if (ra <= 4 && rb <= 4 && ra < rb) [t1, t2] = [t2, t1]
      if (ra >= 5 && rb >= 5 && rb < ra) [t1, t2] = [t2, t1]
    }
    shiftVertHalf(t1.r, t1.c); shiftVertHalf(t2.r, t2.c)
  } else if (lv === 4) {
    // 左右分离:同行且同半区时,左半区列大者先(左移)、右半区列小者先(右移)
    if (ra === rb) {
      if (ca <= 6 && cb <= 6 && ca < cb) [t1, t2] = [t2, t1]
      if (ca >= 7 && cb >= 7 && cb < ca) [t1, t2] = [t2, t1]
    }
    shiftHorizHalf(t1.r, t1.c); shiftHorizHalf(t2.r, t2.c)
  } else if (lv === 5) {
    // 向左看齐:同行时右者先
    if (cb < ca) [t1, t2] = [t2, t1]
    shiftRowLeft(t1.r, t1.c); shiftRowLeft(t2.r, t2.c)
  } else if (lv === 6) {
    // 地心引力(整列下移):同列时上者先
    if (rb < ra) [t1, t2] = [t2, t1]
    shiftColDown(t1.r, t1.c); shiftColDown(t2.r, t2.c)
  } else if (lv === 7) {
    // 飘向天空(整列上移):同列时下者先
    if (ra < rb) [t1, t2] = [t2, t1]
    shiftColUp(t1.r, t1.c); shiftColUp(t2.r, t2.c)
  } else if (lv === 8) {
    // 向右看齐:同行时左者先
    if (ca < cb) [t1, t2] = [t2, t1]
    shiftRowRight(t1.r, t1.c); shiftRowRight(t2.r, t2.c)
  } else if (lv === 9) {
    board.value[a] = null
    board.value[b] = null
    toCenter()
  }
}

function shiftVertHalf(r, c) {
  if (r <= 4) { // 上半区向上
    for (let rr = r; rr < 4; rr++) board.value[rr * COLS + c] = board.value[(rr + 1) * COLS + c]
    board.value[4 * COLS + c] = null
  } else { // 下半区向下
    for (let rr = r; rr > 5; rr--) board.value[rr * COLS + c] = board.value[(rr - 1) * COLS + c]
    board.value[5 * COLS + c] = null
  }
}
function shiftHorizHalf(r, c) {
  if (c <= 6) { // 左半区向左
    for (let cc = c; cc < 6; cc++) board.value[r * COLS + cc] = board.value[r * COLS + cc + 1]
    board.value[r * COLS + 6] = null
  } else { // 右半区向右
    for (let cc = c; cc > 7; cc--) board.value[r * COLS + cc] = board.value[r * COLS + cc - 1]
    board.value[r * COLS + 7] = null
  }
}
function shiftRowLeft(r, c) {
  for (let cc = c; cc < 13; cc++) board.value[r * COLS + cc] = board.value[r * COLS + cc + 1]
  board.value[r * COLS + 13] = null
}
function shiftRowRight(r, c) {
  for (let cc = c; cc > 0; cc--) board.value[r * COLS + cc] = board.value[r * COLS + cc - 1]
  board.value[r * COLS + 0] = null
}
function shiftColDown(r, c) {
  for (let rr = r; rr > 0; rr--) board.value[rr * COLS + c] = board.value[(rr - 1) * COLS + c]
  board.value[0 * COLS + c] = null
}
function shiftColUp(r, c) {
  for (let rr = r; rr < 9; rr++) board.value[rr * COLS + c] = board.value[(rr + 1) * COLS + c]
  board.value[9 * COLS + c] = null
}
function toCenter() {
  let moved
  do {
    moved = false
    for (let i = 0; i < ROWS * COLS; i++) {
      if (board.value[i] !== null) continue
      const r = Math.floor(i / COLS), c = i % COLS
      if (r > 0 && r < ROWS - 1) { // 排除首末行
        if (r < 5 && board.value[i - COLS] !== null) { // 上半区:上方牌下移
          board.value[i] = board.value[i - COLS]
          board.value[i - COLS] = null
          moved = true
        } else if (r >= 5 && board.value[i + COLS] !== null) { // 下半区:下方牌上移
          board.value[i] = board.value[i + COLS]
          board.value[i + COLS] = null
          moved = true
        }
      }
      if (board.value[i] !== null) continue
      if (c > 0 && c < COLS - 1) { // 排除首末列
        if (c < 7 && board.value[i - 1] !== null) { // 左半区:左方牌右移
          board.value[i] = board.value[i - 1]
          board.value[i - 1] = null
          moved = true
        } else if (c >= 7 && board.value[i + 1] !== null) { // 右半区:右方牌左移
          board.value[i] = board.value[i + 1]
          board.value[i + 1] = null
          moved = true
        }
      }
    }
  } while (moved)
}

// ---------- 关卡推进 ----------

async function levelComplete() {
  stopTicker()
  // 时间奖励:与原件 (1800 - frame)/3 一致 → 剩余秒数 ×4
  const bonus = Math.round(timeLeft.value * 4)
  score.value += bonus
  ElMessage.success(`${t('games.petlink.levelClear', { n: level.value })} +${10 * 70 + bonus}`)

  if (level.value >= MAX_LEVEL) {
    won.value = true
    await grantReward()
    return
  }
  // 进入下一关:重排数 = 6 + (关-1) + max(0, 关-4)(第 1~9 关 6/7/8/9/11/13/15/17/19)
  level.value += 1
  shufflesLeft.value = BASE_SHUFFLE + (level.value - 1) + Math.max(0, level.value - 4)
  board.value = buildBoard(level.value)
  timeLeft.value = LEVEL_TIME
  selected.value = -1
  hinted.value = []
  startTicker()
}

async function grantReward() {
  try {
    const res = await gameApi.petLinkReward()
    rewardMsg.value = res && res.awarded > 0
      ? `${t('games.petlink.rewarded', { n: res.awarded })}（${t('points.points')} ${res.balance}）`
      : t('games.petlink.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.petlink.rewardFailed')
  }
}

// ---------- 计时 ----------

function startTicker() {
  stopTicker()
  ticker = setInterval(() => {
    if (paused.value || won.value || lost.value) return
    timeLeft.value -= 1
    if (timeLeft.value <= 0) {
      timeLeft.value = 0
      lost.value = true
      stopTicker()
    }
  }, 1000)
}
function stopTicker() { if (ticker) { clearInterval(ticker); ticker = null } }
function togglePause() {
  if (won.value || lost.value) return
  paused.value = !paused.value
}

// ---------- 流程 ----------

function updateBoardScale() {
  const el = boardScrollRef.value
  if (!el) return
  const avail = el.clientWidth
  // 基准 1:1,允许缩放(最小 0.5 保底、最大 1.6 放大,40 种图源为 120×120 三倍图,放大仍清晰)
  boardScale.value = avail > 0 ? Math.min(1.6, Math.max(0.5, avail / svgW)) : 1
}

function startGame() {
  screen.value = 'game'
  restart()
}

function restart() {
  level.value = 1
  score.value = 0
  shufflesLeft.value = BASE_SHUFFLE
  timeLeft.value = LEVEL_TIME
  selected.value = -1
  hinted.value = []
  animating.value = false
  paused.value = false
  won.value = false
  lost.value = false
  linePoints.value = ''
  rewardMsg.value = ''
  board.value = buildBoard(level.value)
  startTicker()
}

function centerX(c) { return PAD + c * (CELL_W + GAP_X) + CELL_W / 2 }
function centerY(r) { return PAD + r * (CELL_H + GAP_Y) + CELL_H / 2 }
function sleep(ms) { return new Promise(res => setTimeout(res, ms)) }

// ---------- FLIP 滑行动画(牌对象按 id 稳定身份) ----------

function snapshotPositions() {
  const map = new Map()
  const els = boardEl.value ? boardEl.value.querySelectorAll('[data-tid]') : []
  els.forEach(el => {
    const tid = el.getAttribute('data-tid')
    if (tid && tid !== '0') map.set(tid, el.getBoundingClientRect())
  })
  return map
}

function flipAnimate(before) {
  const els = boardEl.value ? boardEl.value.querySelectorAll('[data-tid]') : []
  const s = boardScale.value || 1
  els.forEach(el => {
    const tid = el.getAttribute('data-tid')
    if (!tid || tid === '0' || !before.has(tid)) return
    const b = before.get(tid)
    const a = el.getBoundingClientRect()
    // getBoundingClientRect 返回的是缩放后的屏幕坐标,除以缩放系数还原到局部坐标
    const dx = (b.left - a.left) / s
    const dy = (b.top - a.top) / s
    if (!dx && !dy) return
    el.style.transition = 'none'
    el.style.transform = `translate(${dx}px, ${dy}px)`
    void el.offsetWidth // 强制回流,确保初始 transform 生效
    el.style.transition = 'transform 200ms ease'
    el.style.transform = 'translate(0, 0)'
    setTimeout(() => { el.style.transition = ''; el.style.transform = '' }, 200)
  })
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  // 进入游戏即关闭全局光影特效(与图片/视频/看书一致),离开时恢复
  sunLight?.suspendEffects()
  nextTick(() => updateBoardScale())
  resizeObserver = new ResizeObserver(() => updateBoardScale())
  if (boardScrollRef.value) resizeObserver.observe(boardScrollRef.value)
})
onBeforeUnmount(() => {
  stopTicker(); clearTimeout(hintTimer); window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
  sunLight?.restoreEffects()
})

// 原版快捷键:空格暂停、F5 重排
function onKeydown(e) {
  if (screen.value !== 'game') return
  if (e.code === 'Space') { e.preventDefault(); togglePause() }
  else if (e.key === 'F5') { e.preventDefault(); doShuffle() }
}
</script>

<style scoped>
.game-page {
  position: relative;
  min-height: calc(100vh - 120px);
  background: #000;
  border-radius: 14px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  color: #fff;
}
.game-page.is-cover { justify-content: center; }
.game-page:fullscreen { width: 100vw; height: 100vh; min-height: 100vh; border-radius: 0; }

/* 全屏按钮(右上角固定,封面/游戏态均可用) */
.fullscreen-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 6;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.4);
  color: #cfe6ff;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.fullscreen-btn:hover { background: rgba(0, 0, 0, 0.65); color: #fff; border-color: rgba(255, 255, 255, 0.45); }

/* 封面 */
.cover { display: flex; flex-direction: column; align-items: center; gap: 16px; text-align: center; }
.cover-title {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-family: 'Yuanti SC', 'YouYuan', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 58px;
  font-weight: 800;
  letter-spacing: 4px;
  margin: 0;
  text-shadow: 0 3px 0 rgba(0, 0, 0, 0.35), 0 0 30px rgba(255, 255, 255, 0.12);
}
/* 原版标题配色:宠物=蓝、连连=粉红、看=蓝、-ihomy版=青绿 */
.ct-pet { color: #00ccff; }
.ct-lian { color: #ff0066; }
.ct-kan { color: #00ccff; }
.ct-ihomy { font-size: 28px; color: #66ffcc; margin-left: 8px; text-shadow: 0 2px 0 rgba(0, 0, 0, 0.4); }
.start-btn {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 4px;
  color: #fff;
  padding: 13px 54px;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  background: linear-gradient(180deg, #ff9a3d 0%, #ff6a88 55%, #ff3d81 100%);
  box-shadow: 0 6px 0 #c2273d, 0 10px 24px rgba(255, 90, 120, 0.4);
  transition: transform 0.12s ease, box-shadow 0.12s ease;
}
.start-btn:hover { transform: translateY(-2px); box-shadow: 0 8px 0 #c2273d, 0 14px 28px rgba(255, 90, 120, 0.5); }
.start-btn:active { transform: translateY(3px); box-shadow: 0 3px 0 #c2273d, 0 6px 14px rgba(255, 90, 120, 0.4); }
.cover-credits { font-size: 12px; color: #8899aa; letter-spacing: 1px; margin-top: 6px; }
.cover-hint { font-size: 12px; color: #667; max-width: 420px; line-height: 1.6; }

/* 顶部信息栏(原版 HUD) */
.game-hud {
  display: flex;
  align-items: center;
  gap: 22px;
  width: 100%;
  max-width: 720px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #b8c4d8;
}
.hud-level { display: flex; align-items: baseline; gap: 8px; }
.hud-level-no { color: #ff0066; font-weight: 700; font-size: 16px; }
.hud-level-name { color: #00ccff; font-size: 14px; font-weight: 600; }
.hud-life { display: flex; align-items: baseline; gap: 4px; }
.hud-life .life-num { color: #00ff00; font-size: 16px; font-weight: 700; }
.hud-life .life-label { color: #00ff00; font-size: 12px; }
.hud-timer { display: flex; align-items: center; gap: 8px; flex: 1; }
.hud-score { display: flex; align-items: baseline; gap: 6px; margin-left: auto; }
.hud-score-label { color: #b8c4d8; }
.hud-score b { color: #ffcc66; font-size: 18px; }

/* 倒计时条(复刻原版:白框 + 蓝→红渐变填充 + 马赛克像素分段质感) */
.timer-bar {
  flex: 1;
  height: 14px;
  border-radius: 3px;
  background: #2a2a2a;
  border: 1px solid #fff;
  overflow: hidden;
  box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.6);
}
.timer-fill {
  height: 100%;
  border-radius: 1px;
  /* 马赛克质感:横向分段(10px 块 + 2px 暗缝)叠纵向像素线(7px)叠底色渐变 */
  background:
    repeating-linear-gradient(90deg,
      rgba(0, 0, 0, 0.22) 0px, rgba(0, 0, 0, 0.22) 2px,
      transparent 2px, transparent 10px),
    repeating-linear-gradient(0deg,
      rgba(255, 255, 255, 0.10) 0px, rgba(255, 255, 255, 0.10) 1px,
      transparent 1px, transparent 7px),
    linear-gradient(180deg, #b9e4ff 0%, #6db4ff 45%, #4a8cff 100%);
  image-rendering: pixelated;
  transition: width 1s linear;
}
.timer-text { font-size: 13px; font-variant-numeric: tabular-nums; color: #4dd2ff; min-width: 34px; text-align: right; }
.timer-bar.warn { border-color: #ff7a7a; }
.timer-bar.warn .timer-fill {
  background:
    repeating-linear-gradient(90deg,
      rgba(0, 0, 0, 0.22) 0px, rgba(0, 0, 0, 0.22) 2px,
      transparent 2px, transparent 10px),
    repeating-linear-gradient(0deg,
      rgba(255, 255, 255, 0.10) 0px, rgba(255, 255, 255, 0.10) 1px,
      transparent 1px, transparent 7px),
    linear-gradient(180deg, #ffb3a0 0%, #ff5a5a 55%, #ff2f4d 100%);
  animation: timeBarPulse 1s ease-in-out infinite;
}
.timer-text.warn { color: #ff5a5a; }
@keyframes timeBarPulse {
  0%, 100% { filter: brightness(1); }
  50% { filter: brightness(1.5); }
}

/* 棋盘 */
.board-scroll { width: 100%; max-width: 100%; }
.board-scale { position: relative; margin: 0 auto; overflow: hidden; }
.board-wrap { position: relative; width: fit-content; padding: 44px; transform-origin: top left; }
.board {
  display: grid;
  grid-template-columns: repeat(14, 42px);
  grid-auto-rows: 40px;
  column-gap: 2px;
  row-gap: 0;
}
.tile {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 40px;
  background: #fff;
  border: 1px solid #5283c6;
  border-radius: 3px;
  cursor: pointer;
  padding: 0;
  transition: transform 0.12s ease, box-shadow 0.12s ease;
  contain: layout style;
}
.tile:hover:not(:disabled):not(.is-empty) { transform: translateY(-1px); }
.tile .pet { width: 100%; height: 100%; object-fit: contain; pointer-events: none; }
.tile.is-empty { background: transparent; border-color: transparent; cursor: default; }
.tile.is-selected {
  box-shadow: 0 0 0 2px #0033ff, inset 0 0 0 1px rgba(27, 28, 250, 0.25);
  transform: translateY(-1px);
}
.tile.is-hint { animation: hintPulse 0.8s ease-in-out infinite; border-color: #cc0066; }
@keyframes hintPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(204, 0, 102, 0.6); }
  50% { box-shadow: 0 0 0 4px rgba(204, 0, 102, 0.25); }
}
.board-line { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; }
.board-line .line {
  fill: none;
  stroke: #0099ff;
  stroke-width: 5;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 4px rgba(0, 153, 255, 0.8));
}
.board-wrap.is-frozen .tile { cursor: default; }

.pause-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 8px;
}
.pause-label { font-size: 26px; font-weight: 700; color: #fff; letter-spacing: 3px; }

/* 底部操作 */
.game-bottombar { display: flex; gap: 8px; margin-top: 12px; }

/* 胜负弹层 */
.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  z-index: 5;
}
.overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 44px;
  border-radius: 16px;
  background: #fff;
  color: #333;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}
.overlay-icon { font-size: 42px; }
.overlay-title { font-size: 20px; font-weight: 700; }
.overlay-score { font-size: 14px; color: #888; }
.overlay-reward { font-size: 14px; color: #f0a04b; font-weight: 700; }
</style>
