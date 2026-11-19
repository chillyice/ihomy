<template>
  <GameShell
    ref="shellEl"
    class="tt-shell"
    :title="title"
    body-align="center"
    :new-game="!play.active"
    :new-game-confirm="game.status === 'PLAYING'"
    @back="goBack"
    @new-game="startNew"
  >
    <template #rules>
      <p>{{ $t('games.tetris.tip') }}</p>
    </template>

    <!-- 积分/时间:手机横屏进外壳左栏底边(芯片一行);竖屏用棋盘上方那条数据条,这一份隐藏 -->
    <template #stats>
      <span class="gh-chip"
        ><span>{{ $t('games.tetris.score') }}</span
        ><b>{{ game.score }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.tetris.best') }}</span
        ><b>{{ best }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.tetris.lines') }}</span
        ><b>{{ game.lines }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.tetris.level') }}</span
        ><b>{{ game.level }}</b></span
      >
    </template>

    <!-- 棋盘左侧放不下的操作按钮:手机横屏进外壳左栏第三行;竖屏用数据条右端 -->
    <template #actions>
      <el-button
        size="small"
        :disabled="game.status !== 'PLAYING'"
        :title="paused ? $t('games.tetris.resume') : $t('games.tetris.pause')"
        :aria-label="paused ? $t('games.tetris.resume') : $t('games.tetris.pause')"
        @click="togglePause"
      >
        {{ paused ? '▶' : '⏸' }} {{ paused ? $t('games.tetris.resume') : $t('games.tetris.pause') }}
      </el-button>
    </template>

    <template #extras>
      <GameRoomBar
        :room="play.room"
        :is-host="play.isHost"
        :score="game.score"
        :reported="play.reported"
        :ended="play.ended"
        :result="play.spec?.result"
        :my-id="play.myId"
        @submit="submitRoomScore"
        @leave="play.leave"
      />
    </template>

    <!-- 数据条贴棋盘:与棋盘+侧栏同宽的细条,左对齐排在棋盘上方;
         暂停/新游戏放在这条的右端;手机横屏时整条(按钮+芯片)和按键条
         收成棋盘左侧一列(按钮靠上贴棋盘一侧、芯片整列、按键落列底),
         棋盘+暂存/预告侧栏独占右侧按游戏区高度铺满 -->
    <div class="tt-layout" @contextmenu.prevent @dblclick.prevent>
      <div class="tt-meta">
        <span class="gh-chip"
          ><span>{{ $t('games.tetris.score') }}</span
          ><b>{{ game.score }}</b></span
        >
        <span class="gh-chip"
          ><span>{{ $t('games.tetris.best') }}</span
          ><b>{{ best }}</b></span
        >
        <span class="gh-chip"
          ><span>{{ $t('games.tetris.lines') }}</span
          ><b>{{ game.lines }}</b></span
        >
        <span class="gh-chip"
          ><span>{{ $t('games.tetris.level') }}</span
          ><b>{{ game.level }}</b></span
        >
        <div class="tt-meta-actions">
          <el-button
            size="small"
            :disabled="game.status !== 'PLAYING'"
            :title="paused ? $t('games.tetris.resume') : $t('games.tetris.pause')"
            :aria-label="paused ? $t('games.tetris.resume') : $t('games.tetris.pause')"
            @click="togglePause"
          >
            {{ paused ? '▶' : '⏸' }} {{ paused ? $t('games.tetris.resume') : $t('games.tetris.pause') }}
          </el-button>
        </div>
      </div>

      <div class="tt-stage">
        <div class="tt-board-wrap">
          <div class="tt-board">
            <div
              v-for="(cell, i) in boardCells"
              :key="i"
              class="tt-cell"
              :class="cell ? ['tt-' + cell.type, { ghost: cell.ghost }] : ''"
            ></div>
          </div>
        </div>

        <div class="tt-side">
          <div class="tt-box">
            <span class="tt-box-label">{{ $t('games.tetris.hold') }}</span>
            <div class="tt-mini">
              <div v-for="(cell, i) in previewCells(game.hold)" :key="i" class="tt-mini-cell" :class="cell ? 'tt-' + cell : ''"></div>
            </div>
          </div>
          <div class="tt-box">
            <span class="tt-box-label">{{ $t('games.tetris.next') }}</span>
            <div v-for="(type, i) in game.queue.slice(0, 3)" :key="i" class="tt-mini">
              <div v-for="(cell, j) in previewCells(type)" :key="j" class="tt-mini-cell" :class="cell ? 'tt-' + cell : ''"></div>
            </div>
          </div>
        </div>
      </div>

      <div class="tt-pad">
        <el-button class="tt-pad-btn" circle @click="act((s) => tryMove(s, -1, 0))">
          <el-icon><ArrowLeftBold /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn" circle @click="act((s) => rotate(s, 1))">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn" circle @click="act((s) => tryMove(s, 1, 0))">
          <el-icon><ArrowRightBold /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn" circle @click="act((s, r) => softDrop(s, r))">
          <el-icon><ArrowDownBold /></el-icon>
        </el-button>
        <el-button class="tt-pad-btn tt-drop" @click="act((s, r) => hardDrop(s, r))">{{ $t('games.tetris.drop') }}</el-button>
        <el-button class="tt-pad-btn" @click="act((s, r) => holdPiece(s, r))">{{ $t('games.tetris.hold') }}</el-button>
      </div>

      <Transition name="game-overlay">
        <div v-if="paused || game.status === 'OVER'" class="tt-overlay game-overlay">
          <div class="tt-overlay-card game-overlay-card">
            <div class="tt-overlay-icon">{{ game.status === 'OVER' ? '💤' : '⏸' }}</div>
            <div class="tt-overlay-title">
              {{ game.status === 'OVER' ? $t('games.tetris.over') : $t('games.tetris.paused') }}
            </div>
            <div class="tt-overlay-meta">
              {{ $t('games.tetris.meta', { score: game.score, lines: game.lines }) }}
            </div>
            <div v-if="game.status === 'OVER' && rewardMsg" class="tt-overlay-reward">{{ rewardMsg }}</div>
            <el-button v-if="paused && game.status === 'PLAYING'" type="primary" size="small" round @click="togglePause">
              {{ $t('games.tetris.resume') }}
            </el-button>
            <el-button v-else type="primary" size="small" round @click="startNew">{{ $t('games.tetris.newGame') }}</el-button>
          </div>
        </div>
      </Transition>
    </div>
  </GameShell>
</template>

<script setup>
// 俄罗斯方块:7-bag/旋转踢墙/消行计分/暂存全在 utils/games/tetris.js(纯函数,单测覆盖)。
// 本页负责重力节拍、键盘与屏幕按键、幽灵落点预览、最高分与续玩存档;消行达标发一次奖励。
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowDownBold, ArrowLeftBold, ArrowRightBold, RefreshRight } from '@element-plus/icons-vue'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { seededRng } from '@/utils/games/seededRng'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import {
  SHAPES,
  STATUS,
  cellsOf,
  createGame,
  ghostY,
  hardDrop,
  holdPiece,
  isValidState,
  rotate,
  softDrop,
  tick,
  tryMove,
} from '@/utils/games/tetris'

const BEST_KEY = 'ihomy:games:tetris:best:v1'
const STATE_KEY = 'ihomy:games:tetris:state:v1'
const REWARD_LINES = 10

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const shellEl = ref(null)
const play = useGameRoomPlay()
/** 房间局返回房间大厅;单机回小游戏列表 */
const goBack = () => {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else router.push('/games')
}
const title = computed(() => t('games.tetris.title') + (play.active ? ' · ' + t('games.room.together') : ''))
/** 房间模式下的种子随机数:7-bag 序列两端一致 */
let rng = null
const game = ref(createGame())
const paused = ref(false)
const best = ref(0)
const rewardMsg = ref('')

const speedMs = computed(() => Math.max(70, 800 - (game.value.level - 1) * 70))

/** 渲染矩阵:已落方块 + 幽灵落点 + 当前方块(当前块覆盖幽灵) */
const boardCells = computed(() => {
  const board = game.value.board.map((row) => row.map((cell) => (cell ? { type: cell, ghost: false } : null)))
  const current = game.value.current
  if (!current || game.value.status !== STATUS.PLAYING) return board.flat()

  const ghost = { ...current, y: ghostY(game.value) }
  for (const { x, y, type } of cellsOf(ghost)) {
    if (y >= 0 && y < board.length && x >= 0 && x < board[y].length && !board[y][x]) board[y][x] = { type, ghost: true }
  }
  for (const { x, y, type } of cellsOf(current)) {
    if (y >= 0 && y < board.length && x >= 0 && x < board[y].length) board[y][x] = { type, ghost: false }
  }
  return board.flat()
})

/** 4×4 预览:暂存与后续三块 */
const previewCells = (type) => {
  if (!type) return new Array(16).fill('')
  const filled = new Set(SHAPES[type][0].map(([x, y]) => `${x},${y}`))
  return Array.from({ length: 16 }, (_, i) => (filled.has(`${i % 4},${Math.floor(i / 4)}`) ? type : ''))
}

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
  if (play.active) return
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify({ game: game.value, paused: paused.value }))
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
  if (play.active) return false
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
let tickTimer = null
const stopTimer = () => {
  if (!tickTimer) return
  clearTimeout(tickTimer)
  tickTimer = null
}
const schedule = () => {
  stopTimer()
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  tickTimer = setTimeout(() => {
    tickTimer = null
    apply(tick(game.value, rng ?? Math.random))
    schedule()
  }, speedMs.value)
}

const grantReward = async () => {
  if (play.active) return
  try {
    const res = await gameApi.tetrisReward()
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
  if (play.active) play.report(game.value.score)
  if (game.value.lines >= REWARD_LINES) grantReward()
}

const apply = (next) => {
  if (!next || next === game.value) return
  const prev = game.value
  game.value = next
  if (next.score > best.value) {
    best.value = next.score
    saveBest()
  }
  if (next.status === STATUS.OVER) {
    finishGame()
    return
  }
  // clearSeq 只在落定后自增,用它判断是否该落盘
  if (next.clearSeq !== prev.clearSeq) saveState()
}

const act = (fn) => {
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  apply(fn(game.value, rng ?? Math.random))
  schedule()
}

const startNew = () => {
  stopTimer()
  rng = play.active ? seededRng(play.seed) : null
  game.value = rng ? createGame(rng) : createGame()
  paused.value = false
  rewardMsg.value = ''
  clearState()
  schedule()
}

const submitRoomScore = () => play.report(game.value.score)

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

// ---------- 键盘 ----------
const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  switch (event.key) {
    case 'ArrowLeft':
    case 'a':
    case 'A':
      event.preventDefault()
      act((s) => tryMove(s, -1, 0))
      break
    case 'ArrowRight':
    case 'd':
    case 'D':
      event.preventDefault()
      act((s) => tryMove(s, 1, 0))
      break
    case 'ArrowDown':
    case 's':
    case 'S':
      event.preventDefault()
      act((s, r) => softDrop(s, r))
      break
    case 'ArrowUp':
    case 'w':
    case 'W':
    case 'x':
    case 'X':
      event.preventDefault()
      act((s) => rotate(s, 1))
      break
    case ' ':
      event.preventDefault()
      act((s, r) => hardDrop(s, r))
      break
    case 'c':
    case 'C':
      act((s, r) => holdPiece(s, r))
      break
    case 'p':
    case 'P':
    case 'Escape':
      togglePause()
      break
    default:
      break
  }
}

// ---------- 可见性 ----------
const onVisibility = () => {
  if (document.hidden) stopTimer()
  else schedule()
}

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  document.addEventListener('visibilitychange', onVisibility)
  sunLight?.suspendEffects()
  loadBest()
  if (play.active) {
    await play.load()
    startNew()
    return
  }
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
/* 数据条 + 棋盘(含暂存/预告侧栏) + 按键条:竖屏自上而下三段;
   棋盘是 1:2 竖条,边长按「游戏区高 − 数据条 − 按键条」折半推(容器查询单位);
   手机横屏时数据条与按键条收成棋盘左侧一列,棋盘+侧栏独占右侧 */
.tt-layout {
  --tt-meta-h: calc(var(--gs-control-h) + 8px);
  --tt-pad-h: calc(var(--gs-control-h) + 8px);
  --tt-side-w: clamp(58px, 12cqw, 86px);
  --tt-board-w: min(calc((100cqh - var(--tt-meta-h) - var(--tt-pad-h) - 28px) / 2), calc((100cqw - var(--tt-side-w) - 34px) / 1), 300px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}
/* 数据芯片:固定一条控制行高,与棋盘同宽左对齐(芯片样式沿用 .gh-chip) */
.tt-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  width: min(100%, calc(var(--tt-board-w) + var(--tt-side-w) + 30px));
  height: var(--gs-control-h);
  min-width: 0;
}
/* 暂停/新游戏靠这条的右端;横屏时这条在棋盘左上,按钮会挪到数据上面 */
.tt-meta-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-left: auto;
}
/* EP 给相邻按钮加 margin-left:12px,这里间距只走 gap */
.tt-meta-actions .el-button + .el-button {
  margin-left: 0;
}
/* 页面数据条里的按钮与外壳控件同规格(外壳的尺寸规则只罩住 .gs-controls) */
.tt-meta-actions .el-button {
  height: var(--gs-control-h);
}

.tt-stage {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: clamp(8px, 2cqw, 18px);
  width: min(100%, calc(var(--tt-board-w) + var(--tt-side-w) + 40px));
  min-width: 0;
  min-height: 0;
}
.tt-board-wrap {
  padding: 5px;
  border-radius: 12px;
  background: #1b1f26;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
}
.tt-board {
  display: grid;
  grid-template-columns: repeat(10, minmax(0, 1fr));
  grid-template-rows: repeat(20, minmax(0, 1fr));
  gap: 1px;
  width: var(--tt-board-w);
  aspect-ratio: 1 / 2;
}
.tt-cell {
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.045);
  /* 方块左移/下落/消行时,颜色用极短过渡接上,避免整块生硬跳变 */
  transition: background-color 0.1s ease;
}
.tt-cell.ghost {
  opacity: 0.34;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.45);
  transition: opacity 0.12s ease;
}

.tt-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: var(--tt-side-w);
}
.tt-box {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 7px 6px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.05);
}
.tt-box-label {
  font-size: 10px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
  text-align: center;
}
.tt-mini {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  width: 100%;
  aspect-ratio: 1;
}
.tt-mini-cell {
  border-radius: 1px;
  background: transparent;
  transition: background-color 0.12s ease;
}

.tt-pad {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  width: min(100%, 560px);
}
.tt-pad-btn {
  min-width: 42px;
}
.tt-pad-btn.tt-drop {
  min-width: 78px;
}

/* 暂停/结算浮层只盖住棋盘舞台,不挡外壳与按键条 */
.tt-overlay {
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
.tt-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.tt-overlay-icon {
  font-size: 38px;
}
.tt-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.tt-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.tt-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

.tt-I {
  background: #39c5cf;
}
.tt-J {
  background: #4a7bd4;
}
.tt-L {
  background: #e08b3a;
}
.tt-O {
  background: #e3c341;
}
.tt-S {
  background: #4fb36b;
}
.tt-T {
  background: #a163c9;
}
.tt-Z {
  background: #d95656;
}

html.dark .tt-box {
  background: rgba(255, 255, 255, 0.08);
}
html.dark .tt-overlay {
  background: rgba(20, 20, 20, 0.72);
}

/* 外壳里的数据/操作是「第二份摆放」:竖屏与桌面用棋盘上方那条数据条,
   外壳左栏底边那行芯片与左栏第三行只在手机横屏出现(样式在 main.css 横屏档)。 */
.tt-shell :deep(.gs-stats),
.tt-shell :deep(.gs-controls) {
  display: none;
}

/* 手机横屏:数据条与暂停搬进外壳(左栏底边 + 第三行),数据条整条让位;
   按键条留在游戏区(竖排在棋盘右侧,右手拇指区),棋盘+暂存/预告侧栏吃满高度 */
@media (orientation: landscape) and (max-height: 520px) {
  .tt-shell :deep(.gs-stats),
  .tt-shell :deep(.gs-controls) {
    display: flex;
  }
  .tt-layout {
    /* 90 = 按键条那一列(按钮按文字宽 ~78 + 12),36 = 与棋盘间距与内边距 */
    --tt-board-w: min(calc((100cqh - 16px) / 2), calc(100cqw - 90px - var(--tt-side-w) - 36px), 300px);
    display: grid;
    grid-template-columns: auto auto;
    align-items: center;
    justify-content: center;
    column-gap: 12px;
    width: 100%;
    max-height: 100%;
  }
  .tt-meta {
    display: none;
  }
  .tt-stage {
    align-self: center;
    width: auto;
    min-height: 0;
  }
  /* 按键条竖排成棋盘右侧一列,六个键各占一行,拇指从外侧够得到 */
  .tt-pad {
    flex-direction: column;
    flex-wrap: nowrap;
    align-self: center;
    width: auto;
    margin-left: 0;
  }
  .tt-pad-btn {
    width: 100%;
  }
  .tt-pad .el-button + .el-button {
    margin-left: 0;
  }
}
</style>
