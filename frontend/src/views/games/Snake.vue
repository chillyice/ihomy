<template>
  <GameShell
    ref="shellEl"
    class="sn-shell"
    :title="title"
    body-align="center"
    :new-game="!play.active"
    :new-game-confirm="game.status === 'PLAYING'"
    @back="goBack"
    @new-game="startNew"
  >
    <template #rules>
      <p>{{ $t('games.snake.tip') }}</p>
    </template>

    <!-- 积分/时间:手机横屏进外壳左栏底边(芯片一行);竖屏用棋盘上方那条数据条,这一份隐藏 -->
    <template #stats>
      <span class="gh-chip"
        ><span>{{ $t('games.snake.score') }}</span
        ><b>{{ game.score }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.snake.best') }}</span
        ><b>{{ best }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.snake.length') }}</span
        ><b>{{ game.snake.length }}</b></span
      >
    </template>

    <!-- 棋盘左侧放不下的操作按钮:手机横屏进外壳左栏第三行;竖屏用数据条右端 -->
    <template #actions>
      <el-button
        size="small"
        :disabled="game.status !== 'PLAYING'"
        :title="paused ? $t('games.snake.resume') : $t('games.snake.pause')"
        :aria-label="paused ? $t('games.snake.resume') : $t('games.snake.pause')"
        @click="togglePause"
      >
        {{ paused ? '▶' : '⏸' }} {{ paused ? $t('games.snake.resume') : $t('games.snake.pause') }}
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

    <!-- 数据条贴棋盘:与棋盘同宽的细条,左对齐排在棋盘上方;
         暂停/新游戏放在这条的右端(棋盘右上角);手机横屏时整条(按钮+芯片)和方向键
         收成棋盘左侧一列(按钮靠上贴棋盘一侧、芯片整列、方向键落列底),
         棋盘独占右侧按游戏区高度铺满 -->
    <div class="sn-layout" @contextmenu.prevent @dblclick.prevent>
      <div class="sn-meta">
        <span class="gh-chip"
          ><span>{{ $t('games.snake.score') }}</span
          ><b>{{ game.score }}</b></span
        >
        <span class="gh-chip"
          ><span>{{ $t('games.snake.best') }}</span
          ><b>{{ best }}</b></span
        >
        <span class="gh-chip"
          ><span>{{ $t('games.snake.length') }}</span
          ><b>{{ game.snake.length }}</b></span
        >
        <div class="sn-meta-actions">
          <el-button
            size="small"
            :disabled="game.status !== 'PLAYING'"
            :title="paused ? $t('games.snake.resume') : $t('games.snake.pause')"
            :aria-label="paused ? $t('games.snake.resume') : $t('games.snake.pause')"
            @click="togglePause"
          >
            {{ paused ? '▶' : '⏸' }} {{ paused ? $t('games.snake.resume') : $t('games.snake.pause') }}
          </el-button>
        </div>
      </div>

      <div class="sn-stage">
        <div class="sn-board" :style="{ '--sn-cols': game.cols }" @touchstart="onTouchStart" @touchend="onTouchEnd">
          <div
            v-for="(cell, i) in viewCells"
            :key="i"
            class="sn-cell"
            :class="{ snake: cell.snake, head: cell.head, food: cell.food }"
          ></div>
        </div>

        <Transition name="game-overlay">
          <div v-if="paused || game.status !== 'PLAYING'" class="sn-overlay game-overlay">
            <div class="sn-overlay-card game-overlay-card">
              <div class="sn-overlay-icon">{{ game.status === 'WON' ? '🏆' : game.status === 'LOST' ? '💥' : '⏸' }}</div>
              <div class="sn-overlay-title">
                {{
                  game.status === 'WON' ? $t('games.snake.win') : game.status === 'LOST' ? $t('games.snake.lose') : $t('games.snake.paused')
                }}
              </div>
              <div class="sn-overlay-meta">{{ $t('games.snake.meta', { score: game.score }) }}</div>
              <div v-if="game.status !== 'PLAYING' && rewardMsg" class="sn-overlay-reward">{{ rewardMsg }}</div>
              <el-button v-if="paused && game.status === 'PLAYING'" type="primary" size="small" round @click="togglePause">
                {{ $t('games.snake.resume') }}
              </el-button>
              <el-button v-else type="primary" size="small" round @click="startNew">{{ $t('games.snake.newGame') }}</el-button>
            </div>
          </div>
        </Transition>
      </div>

      <div class="sn-pad">
        <button type="button" class="sn-key sn-up" :aria-label="$t('games.snake.up')" @click="turnTo('up')">▲</button>
        <button type="button" class="sn-key sn-left" :aria-label="$t('games.snake.left')" @click="turnTo('left')">◀</button>
        <button type="button" class="sn-key sn-down" :aria-label="$t('games.snake.down')" @click="turnTo('down')">▼</button>
        <button type="button" class="sn-key sn-right" :aria-label="$t('games.snake.right')" @click="turnTo('right')">▶</button>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
// 贪吃蛇:转向/前进/吃食物/撞墙撞身判定全在 utils/games/snake.js(纯函数,单测覆盖)。
// 本页负责节拍、键盘/滑动/方向键输入、最高分与续玩存档;一局吃到 10 个食物发一次奖励。
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { gameApi } from '@/api'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { seededRng } from '@/utils/games/seededRng'
import { STATUS, createGame, isValidState, step, stepInterval, turn } from '@/utils/games/snake'

const BEST_KEY = 'ihomy:games:snake:best:v1'
const STATE_KEY = 'ihomy:games:snake:state:v1'
const REWARD_SCORE = 10

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
const title = computed(() => t('games.snake.title') + (play.active ? ' · ' + t('games.room.together') : ''))
/** 房间模式下的种子随机数:两条蛇/食物序列由同一 seed 生成,各自跑各自的盘面 */
let rng = null
const game = ref(createGame())
const paused = ref(false)
const best = ref(0)
const rewardMsg = ref('')

const viewCells = computed(() => {
  const { cols, snake, food } = game.value
  const snakeSet = new Set(snake.map((c) => c.y * cols + c.x))
  const headKey = snake.length ? snake[0].y * cols + snake[0].x : -1
  const foodKey = food ? food.y * cols + food.x : -1
  return Array.from({ length: game.value.cols * game.value.rows }, (_, i) => ({
    snake: snakeSet.has(i),
    head: i === headKey,
    food: i === foodKey,
  }))
})

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
let stepTimer = null
const stopTimer = () => {
  if (!stepTimer) return
  clearTimeout(stepTimer)
  stepTimer = null
}
const schedule = () => {
  stopTimer()
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  stepTimer = setTimeout(() => {
    stepTimer = null
    apply(step(game.value, rng ?? Math.random))
    schedule()
  }, stepInterval(game.value.score))
}

const grantReward = async () => {
  if (play.active) return
  try {
    const res = await gameApi.snakeReward()
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
  if (game.value.score >= REWARD_SCORE || game.value.status === STATUS.WON) grantReward()
}

let stepsSinceSave = 0
const apply = (next) => {
  if (!next || next === game.value) return
  game.value = next
  if (next.score > best.value) {
    best.value = next.score
    saveBest()
  }
  if (next.status !== STATUS.PLAYING) {
    finishGame()
    return
  }
  // 每一步都写 localStorage 太频繁,每 5 步落一次盘,离开页面时再补一次
  if (++stepsSinceSave >= 5) {
    stepsSinceSave = 0
    saveState()
  }
}

const startNew = () => {
  stopTimer()
  rng = play.active ? seededRng(play.seed) : null
  game.value = rng ? createGame(undefined, undefined, rng) : createGame()
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

const turnTo = (direction) => {
  if (paused.value || game.value.status !== STATUS.PLAYING) return
  game.value = turn(game.value, direction)
}

// ---------- 键盘与滑动 ----------
const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  const map = {
    ArrowUp: 'up',
    ArrowDown: 'down',
    ArrowLeft: 'left',
    ArrowRight: 'right',
    w: 'up',
    s: 'down',
    a: 'left',
    d: 'right',
    W: 'up',
    S: 'down',
    A: 'left',
    D: 'right',
  }
  if (map[event.key]) {
    event.preventDefault()
    turnTo(map[event.key])
    return
  }
  if (event.key === ' ' || event.key === 'p' || event.key === 'P') {
    event.preventDefault()
    togglePause()
  }
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
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 20) return
  turnTo(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : dy > 0 ? 'down' : 'up')
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
/* 数据条 + 棋盘 + 方向键:竖屏自上而下三段,棋盘边长按「游戏区高 − 数据条 − 方向键」推
   (容器查询单位,不依赖 JS 测量时机);手机横屏时数据条与方向键收成棋盘左侧一列 */
.sn-layout {
  --sn-meta-h: calc(var(--gs-control-h) + 8px);
  --sn-pad-h: calc(40px * 2 + 6px);
  /* 棋盘边长按容器尺寸直接推(舞台是内容定宽,百分比会循环解析,必须用 cqw/cqh) */
  --sn-board: min(calc(100cqw - 16px), calc(100cqh - var(--sn-meta-h) - var(--sn-pad-h) - 24px), 420px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}
/* 数据芯片:固定一条控制行高,与棋盘同宽左对齐(芯片样式沿用 .gh-chip);
   三个读数一行、各占等分宽度(不放圆角小方卡) */
.sn-meta {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 6px;
  width: min(100%, calc(var(--sn-board) + 8px));
  height: var(--gs-control-h);
  min-width: 0;
}
/* 暂停/新游戏靠这条的右端;横屏时这条在棋盘左上,按钮会挪到数据上面 */
.sn-meta-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-left: auto;
}
/* EP 给相邻按钮加 margin-left:12px,这里间距只走 gap */
.sn-meta-actions .el-button + .el-button {
  margin-left: 0;
}
/* 页面数据条里的文字/图标按钮与外壳控件同规格(外壳的尺寸规则只罩住 .gs-controls) */
.sn-meta-actions .el-button {
  height: var(--gs-control-h);
}

.sn-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
}

.sn-board {
  display: grid;
  grid-template-columns: repeat(var(--sn-cols, 20), minmax(0, 1fr));
  gap: 1px;
  width: var(--sn-board);
  aspect-ratio: 1;
  padding: 6px;
  border-radius: 12px;
  background: #16211a;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06);
  touch-action: none;
  user-select: none;
}
.sn-cell {
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.035);
}
.sn-cell.snake {
  background: linear-gradient(160deg, #67c98a, #3d9e63);
  border-radius: 3px;
}
.sn-cell.head {
  background: linear-gradient(160deg, #b8f0c9, #6fd694);
  box-shadow: 0 0 5px rgba(140, 240, 175, 0.65);
}
.sn-cell.food {
  background: radial-gradient(circle at 35% 30%, #ff9b8a, #e04b3a 70%);
  border-radius: 50%;
  animation: sn-food 1.1s ease-in-out infinite;
}
@keyframes sn-food {
  50% {
    scale: 1.15;
  }
}

.sn-pad {
  display: grid;
  grid-template-columns: repeat(3, 46px);
  grid-template-rows: repeat(2, 40px);
  gap: 6px;
}
.sn-key {
  border: 1px solid var(--color-border, #ddd6ca);
  border-radius: 10px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
  font-size: 15px;
  cursor: pointer;
  transition:
    background 0.15s ease,
    transform 0.1s ease;
}
.sn-key:active {
  transform: translateY(1px) scale(0.96);
  background: rgba(0, 0, 0, 0.06);
}
.sn-up {
  grid-column: 2;
  grid-row: 1;
}
.sn-left {
  grid-column: 1;
  grid-row: 2;
}
.sn-down {
  grid-column: 2;
  grid-row: 2;
}
.sn-right {
  grid-column: 3;
  grid-row: 2;
}

/* 暂停/结算浮层只盖住棋盘舞台,不挡外壳与方向键 */
.sn-overlay {
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
.sn-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.sn-overlay-icon {
  font-size: 38px;
}
.sn-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.sn-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.sn-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

html.dark .sn-key {
  background: rgba(255, 255, 255, 0.08);
  color: #e6e6e6;
}
html.dark .sn-overlay {
  background: rgba(20, 20, 20, 0.72);
}

/* 外壳里的数据/操作是「第二份摆放」:竖屏与桌面用棋盘上方那条数据条,
   外壳左栏底边那行芯片与左栏第三行只在手机横屏出现(样式在 main.css 横屏档)。 */
.sn-shell :deep(.gs-stats),
.sn-shell :deep(.gs-controls) {
  display: none;
}

/* 手机横屏:数据与暂停搬进外壳(左栏底边 + 第三行),数据条整条让位;
   方向键留在游戏区(棋盘右侧,右手拇指区),棋盘边长 = 宽高最小值,高度优先吃满 */
@media (orientation: landscape) and (max-height: 520px) {
  .sn-shell :deep(.gs-stats),
  .sn-shell :deep(.gs-controls) {
    display: flex;
  }
  .sn-layout {
    /* 150 = 方向键三列(3×46+2×6),12 = 与棋盘间距,16 = 上下各让 8 */
    --sn-board: min(calc(100cqh - 16px), calc(100cqw - 178px), 420px);
    display: grid;
    grid-template-columns: auto auto;
    align-items: center;
    justify-content: center;
    column-gap: 12px;
    width: 100%;
    max-height: 100%;
  }
  .sn-meta {
    display: none;
  }
  .sn-stage {
    min-height: 0;
  }
  .sn-board {
    width: var(--sn-board);
  }
}
</style>
