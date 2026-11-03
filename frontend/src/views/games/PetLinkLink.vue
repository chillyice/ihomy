<template>
  <div class="page">
    <!-- 顶栏:返回 + 全屏(工具按钮统一进顶栏,与物品定位一致) -->
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <div class="game-top-actions">
        <el-button size="small" round @click="router.push('/games')">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('games.back') }}
        </el-button>
        <el-button size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          {{ isFullscreen ? $t('games.petlink.exitFullscreen') : $t('games.petlink.fullscreen') }}
        </el-button>
      </div>
    </PageToolbar>

    <div ref="gameCardEl" class="game-card game-surface" @contextmenu.prevent @dblclick.prevent>
      <!-- 封面(复刻原版标题屏):多色标题 + ihomy 版后缀 + 开始/继续 + 版权) -->
      <div v-if="screen === 'cover'" class="cover">
        <h1 class="cover-title">
          <span class="ct-pet">宠</span><span class="ct-pet">物</span><span class="ct-lian">连</span><span class="ct-lian">连</span
          ><span class="ct-kan">看</span>
          <span class="ct-ihomy">-ihomy版</span>
        </h1>
        <button class="start-btn" type="button" @click="continueGame">
          {{ savedState ? $t('games.petlink.resume') : $t('games.petlink.start') }}
        </button>
        <button v-if="savedState" class="cover-new-btn" type="button" @click="startFresh">
          {{ $t('games.petlink.newGame') }}
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

              <Transition name="game-overlay">
                <div v-if="paused" class="pause-mask game-overlay">
                  <div class="pause-label game-overlay-card">{{ $t('games.petlink.paused') }}</div>
                </div>
              </Transition>
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
          <el-button size="small" :title="$t('games.petlink.muteToggle')" @click="toggleMute">
            {{ muted ? '🔇' : '🔊' }} {{ muted ? $t('games.petlink.unmute') : $t('games.petlink.mute') }}
          </el-button>
        </div>

        <!-- 调试版(地址加 ?debug=1):跳关、自动消除、误消/奇数张体检;不读写玩家存档 -->
        <div v-if="debugMode" class="debug-panel">
          <div class="debug-row">
            <span class="debug-tag">{{ $t('games.petlink.debug.title') }}</span>
            <el-select v-model="debugJumpLevel" size="small" class="debug-level-select">
              <el-option v-for="n in MAX_LEVEL" :key="n" :label="$t('games.petlink.levelNames.' + n)" :value="n" />
            </el-select>
            <el-button size="small" @click="debugJump(debugJumpLevel)">{{ $t('games.petlink.debug.jump') }}</el-button>
            <el-button size="small" :type="autoRunning ? 'danger' : 'primary'" @click="toggleAutoPlay">
              {{ autoRunning ? $t('games.petlink.debug.stop') : $t('games.petlink.debug.auto') }}
            </el-button>
            <el-button size="small" @click="runSelfTest">{{ $t('games.petlink.debug.selfTest') }}</el-button>
            <el-button size="small" @click="debugLog = []">{{ $t('games.petlink.debug.clearLog') }}</el-button>
          </div>
          <div class="debug-row debug-stats">
            <span
              >{{ $t('games.petlink.debug.tiles') }} <b>{{ debugTiles }}</b></span
            >
            <span
              >{{ $t('games.petlink.debug.removed') }} <b>{{ debugRemoved }}</b></span
            >
            <span :class="{ 'debug-bad': debugOdd.length }"
              >{{ $t('games.petlink.debug.oddTypes') }} <b>{{ debugOdd.length }}</b></span
            >
            <span :class="{ 'debug-bad': debugIssueCount }"
              >{{ $t('games.petlink.debug.issues') }} <b>{{ debugIssueCount }}</b></span
            >
            <span v-if="debugOdd.length" class="debug-bad">{{ debugOddText }}</span>
          </div>
          <div v-if="debugSelfText" class="debug-row debug-self">{{ debugSelfText }}</div>
          <div class="debug-hint">{{ $t('games.petlink.debug.hint') }}</div>
          <div class="debug-log">
            <div v-for="(line, i) in debugLog" :key="i" class="debug-line" :class="{ 'debug-bad': line.bad }">{{ line.text }}</div>
          </div>
        </div>

        <!-- 胜负弹层 -->
        <Transition name="game-overlay">
          <div v-if="won || lost" class="overlay game-overlay">
            <div class="overlay-card game-overlay-card">
              <div class="overlay-icon">{{ won ? '🎉' : '😿' }}</div>
              <div class="overlay-title">{{ won ? $t('games.petlink.win') : $t('games.petlink.lose') }}</div>
              <div class="overlay-score">{{ $t('games.petlink.score') }} {{ score }}</div>
              <div v-if="won && rewardMsg" class="overlay-reward">{{ rewardMsg }}</div>
              <el-button type="primary" size="small" round @click="restart">{{ $t('games.petlink.playAgain') }}</el-button>
            </div>
          </div>
        </Transition>
      </template>
    </div>
  </div>
</template>

<script setup>
// 宠物连连看 ihomy 版(自 Flash 5「宠物连连看2.5」复刻,原作者村子 cunzi.com):
// 黑底白卡蓝边 40 种宠物、9 关(关卡名:入门/增加难度/上下分离/左右分离/向左看齐/
// 地心引力/飘向天空/向右看齐/中央集中)、150s 倒计时(每消除一对 +3s)、得分 +10/对、
// 过关时间奖励 = 剩余秒数×4、重排(第 1~9 关 6/7/8/9/11/13/15/17/19 次)、
// 蓝色连线动画 + 点击/消除/胜利音效(无背景音乐,可静音);通关按后端发积分。
// 支持全屏,进入时关闭全局光影特效(与图片/视频/看书一致)。
// 进度按关卡存档(棋盘/分数/重排数/剩余时间),下次进来可从封面继续或新开一局。
import { ref, computed, onBeforeUnmount, onMounted, nextTick, inject, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { FullScreen, ArrowLeft } from '@element-plus/icons-vue'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  PETLINK_LEVEL_TIME,
  PETLINK_MAX_LEVEL,
  PETLINK_STORAGE_KEY,
  parsePetLinkState,
  serializePetLinkState,
} from '@/utils/games/petlinkSave'
import {
  PETLINK_COLS,
  PETLINK_ROWS,
  createPetLinkBoard,
  findPetLinkPair,
  findPetLinkPath,
  petLinkOddTypes,
  removePetLinkPair,
  shufflePetLinkBoard,
  simulatePetLinkLevel,
} from '@/utils/games/petlinkBoard'
import clickUrl from '@/assets/games/petlink/sounds/15.wav'
import matchUrl from '@/assets/games/petlink/sounds/294.mp3'
import winUrl from '@/assets/games/petlink/sounds/301.mp3'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const sunLight = inject(SUN_LIGHT_KEY, null)

// 原版棋盘:16×12 格(含一圈 1 格外圈用于绕外圈连线),内圈 14×10 为可玩区;
// 格子 44×40 间距(卡牌 42×40、左右间隙 2px、上下无间隙)。
const ROWS = PETLINK_ROWS
const COLS = PETLINK_COLS
const CELL_W = 42
const CELL_H = 40
const GAP_X = 2
const GAP_Y = 0
const PAD = 44 // 外圈占位(供连线 SVG 绕外圈绘制)
const LEVEL_TIME = PETLINK_LEVEL_TIME
const MAX_LEVEL = PETLINK_MAX_LEVEL
const TIME_BONUS = 3 // 每消除一对补充 3 秒(原件 36 帧)
const BASE_SHUFFLE = 6
const MATCH_LINE_MS = 150 // 消除连线展示时长(原 280,贴近原版 12fps 连线瞬时节奏)
const MATCH_FLIP_MS = 120 // 消除后牌位滑行动画时长(原 220)

// 40 种宠物(按 sprite 149 帧序),用 import.meta.glob 按文件名排序加载
const petModules = import.meta.glob('@/assets/games/petlink/*.png', { eager: true, import: 'default' })
const petImages = Object.keys(petModules)
  .sort()
  .map((k) => petModules[k])

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
// 封面读到的可续存档(点「继续游戏」才真正装载)
const savedState = ref(null)

// ---------- 调试版(地址加 ?debug=1) ----------
// 只做核对:跳关、自动消除、每次消除后校验「是否恰好这两张」与「剩余是否成对」。
// 调试态不写也不清玩家存档,退出清掉参数即回到正常版。
const DEBUG_SHUFFLES = 120
const debugMode = computed(() => String(route.query.debug) === '1')
const debugJumpLevel = ref(1)
const debugFast = ref(false)
const autoRunning = ref(false)
const debugLog = ref([])
const debugIssueCount = ref(0)
const debugRemoved = ref(0)
const debugSelfText = ref('')
const debugTiles = computed(() => board.value.filter(Boolean).length)
const debugOdd = computed(() => petLinkOddTypes(board.value))
const debugOddText = computed(() => debugOdd.value.map((d) => `#${d.type}×${d.count}`).join(' '))

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

// 全屏(原生 Fullscreen API,对 .game-card 全屏)
const gameCardEl = ref(null)
const isFullscreen = ref(false)
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    gameCardEl.value?.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}
function onFullscreenChange() {
  isFullscreen.value = !!document.fullscreenElement
  // 全屏/退出后可用宽高变化,重新计算缩放(退出恢复 1.6 上限)
  nextTick(() => updateBoardScale())
}

// ---------- 音频(点击/消除/胜利音效,无背景音乐;可静音) ----------

const muted = ref(localStorage.getItem('ihomy:game:petlink:muted') === '1')
const audioClick = new Audio(clickUrl)
const audioMatch = new Audio(matchUrl)
const audioWin = new Audio(winUrl)

function toggleMute() {
  muted.value = !muted.value
  localStorage.setItem('ihomy:game:petlink:muted', muted.value ? '1' : '0')
}
function playClick() {
  if (muted.value) return
  try {
    audioClick.currentTime = 0
    audioClick.play()
  } catch (e) {}
}
function playMatch() {
  if (muted.value) return
  try {
    audioMatch.currentTime = 0
    audioMatch.play()
  } catch (e) {}
}
function playWin() {
  if (muted.value) return
  try {
    audioWin.currentTime = 0
    audioWin.play()
  } catch (e) {}
}

// ---------- 进度存档 ----------
// 只在对局进行中写档;通关/失败/重新开始即清档,封面按存档显示「继续游戏」。
const saveState = () => {
  if (debugMode.value) return
  if (screen.value !== 'game' || won.value || lost.value) return
  if (!board.value.length || board.value.every((tile) => tile === null)) return
  try {
    localStorage.setItem(
      PETLINK_STORAGE_KEY,
      JSON.stringify(
        serializePetLinkState({
          board: board.value,
          score: score.value,
          level: level.value,
          shufflesLeft: shufflesLeft.value,
          timeLeft: timeLeft.value,
          paused: paused.value,
        }),
      ),
    )
  } catch (e) {
    // 隐私模式/配额满时放弃存档,不影响游戏
  }
}

const clearState = () => {
  if (debugMode.value) return
  try {
    localStorage.removeItem(PETLINK_STORAGE_KEY)
  } catch (e) {
    // 忽略
  }
}

const loadSavedState = () => {
  try {
    return parsePetLinkState(JSON.parse(localStorage.getItem(PETLINK_STORAGE_KEY) || 'null'))
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

// 恢复出来的盘面若已无解(极少见),先按剩余重排次数自救;救不回来直接判负
const ensurePlayable = () => {
  while (!findAnyPair() && shufflesLeft.value > 0) applyShuffle()
  if (!findAnyPair()) {
    lost.value = true
    stopTicker()
    clearState()
    return false
  }
  return true
}

const applySavedState = (saved) => {
  tileSeq = saved.maxId
  board.value = saved.board
  score.value = saved.score
  level.value = saved.level
  shufflesLeft.value = saved.shufflesLeft
  timeLeft.value = saved.timeLeft
  paused.value = saved.paused
  selected.value = -1
  hinted.value = []
  animating.value = false
  won.value = false
  lost.value = false
  linePoints.value = ''
  rewardMsg.value = ''
  if (ensurePlayable()) startTicker()
}

// ---------- 关卡 ----------

// 发牌走棋盘内核;tileSeq 只负责跨关连续编号,保证 FLIP 动画的牌身份稳定
function buildBoard(lv) {
  const { board: next, maxId } = createPetLinkBoard(lv, tileSeq)
  tileSeq = maxId
  return next
}

// 连线判定与「找任意一对」由内核提供(最多两拐 + 可绕外圈,见 utils/games/petlinkBoard.js)
const findAnyPair = () => findPetLinkPair(board.value)

// ---------- 交互 ----------

function onClick(i) {
  if (frozen.value) return
  if (!board.value[i]) return
  hinted.value = []
  if (selected.value === -1) {
    selected.value = i
    playClick()
    return
  }
  if (selected.value === i) {
    selected.value = -1
    return
  }
  const s = selected.value
  if (board.value[i].type !== board.value[s].type) {
    selected.value = i
    playClick()
    return
  }
  const path = findPetLinkPath(board.value, Math.floor(s / COLS), s % COLS, Math.floor(i / COLS), i % COLS)
  selected.value = -1
  if (path) match(s, i, path)
  else playClick()
}

async function match(a, b, path) {
  animating.value = true
  playMatch()
  const lineMs = debugFast.value ? 0 : MATCH_LINE_MS
  const flipMs = debugFast.value ? 0 : MATCH_FLIP_MS
  const removedTiles = debugMode.value ? [board.value[a], board.value[b]] : null
  const beforeIds = debugMode.value ? new Set(board.value.filter(Boolean).map((t) => t.id)) : null
  linePoints.value = path.map(([r, c]) => `${centerX(c)},${centerY(r)}`).join(' ')
  await sleep(lineMs)
  linePoints.value = ''

  // FLIP:先记录各牌当前屏幕位置,再应用消除/牌位移动,最后从旧位置滑到新位置
  const before = snapshotPositions()
  removePetLinkPair(board.value, level.value, a, b)
  if (debugMode.value) auditRemoval(removedTiles, beforeIds, a, b)
  score.value += 10
  // 消除补充倒计时:每消除一对 +3 秒(原件 thetimer 回退 36 帧)
  timeLeft.value = Math.min(LEVEL_TIME, timeLeft.value + TIME_BONUS)
  await nextTick()
  flipAnimate(before, flipMs)
  await sleep(flipMs)
  animating.value = false

  if (board.value.every((v) => v === null)) {
    await levelComplete()
    return
  }
  while (!findAnyPair() && shufflesLeft.value > 0) {
    applyShuffle()
    ElMessage.info(t('games.petlink.autoShuffle'))
  }
  if (board.value.some((v) => v !== null) && !findAnyPair()) {
    lost.value = true
    stopTicker()
    clearState()
  }
}

function doHint() {
  if (frozen.value) return
  const pair = findAnyPair()
  if (!pair) return
  hinted.value = [...pair]
  clearTimeout(hintTimer)
  hintTimer = setTimeout(() => {
    hinted.value = []
  }, 1600)
}

function doShuffle() {
  if (frozen.value || shufflesLeft.value <= 0) return
  applyShuffle()
  playClick()
}

function applyShuffle() {
  shufflePetLinkBoard(board.value)
  shufflesLeft.value -= 1
  selected.value = -1
  hinted.value = []
}

// ---------- 消除后牌位移动 ----------
// 第 3~9 关的聚拢规则(上下分离/左右分离/向左看齐/地心引力/飘向天空/向右看齐/中央集中)
// 统一在 utils/games/petlinkBoard.js 的 removePetLinkPair 实现,页面不再重复一份。

// ---------- 调试版动作 ----------

function pushDebugLog(text, bad = false) {
  debugLog.value = [...debugLog.value.slice(-11), { text, bad }]
}

// 每次消除后核对:消失的必须恰好是这两张牌;剩余各型必须成对(奇数张必然卡死)
function auditRemoval(tiles, beforeIds, a, b) {
  const alive = new Set(board.value.filter(Boolean).map((t) => t.id))
  const lost = [...beforeIds].filter((id) => !alive.has(id))
  const extra = lost.filter((id) => !tiles.some((t) => t && t.id === id))
  const r1 = Math.floor(a / COLS)
  const c1 = a % COLS
  const r2 = Math.floor(b / COLS)
  const c2 = b % COLS
  const type = tiles[0] ? tiles[0].type : 0
  let bad = false
  debugRemoved.value += 1
  if (lost.length !== 2 || extra.length) {
    bad = true
    debugIssueCount.value += 1
    pushDebugLog(t('games.petlink.debug.extraRemoved', { level: level.value, r1, c1, r2, c2, n: Math.max(0, lost.length - 2) }), true)
    console.warn('[petlink] 消除张数不符', { level: level.value, a, b, tiles, lost })
  }
  if (debugOdd.value.length) {
    bad = true
    debugIssueCount.value += 1
    pushDebugLog(t('games.petlink.debug.oddLeft', { list: debugOddText.value }), true)
    console.warn('[petlink] 盘面出现奇数张', debugOdd.value)
  }
  pushDebugLog(`L${level.value} (${r1},${c1})-(${r2},${c2}) #${type} ${bad ? '✗' : '✓'}`)
}

// 跳到指定关卡的调试盘面:重排给足,免得「无牌可消」提前终止
function debugJump(lv) {
  stopAutoPlay()
  const next = Math.min(MAX_LEVEL, Math.max(1, Number(lv) || 1))
  level.value = next
  board.value = buildBoard(next)
  shufflesLeft.value = DEBUG_SHUFFLES
  timeLeft.value = LEVEL_TIME
  selected.value = -1
  hinted.value = []
  animating.value = false
  won.value = false
  lost.value = false
  linePoints.value = ''
  rewardMsg.value = ''
  debugJumpLevel.value = next
  debugLog.value = []
  debugIssueCount.value = 0
  debugRemoved.value = 0
  debugSelfText.value = ''
  startTicker()
  pushDebugLog(t('games.petlink.debug.jumpTo', { n: next }))
}

function stopAutoPlay() {
  autoRunning.value = false
}

// 自动把当前关卡消到底:连续跑 5/8 关最容易复现「横向连消多消一张」
async function toggleAutoPlay() {
  if (autoRunning.value) {
    stopAutoPlay()
    return
  }
  if (frozen.value) return
  autoRunning.value = true
  debugFast.value = true
  const startLevel = level.value
  let steps = 0
  try {
    while (autoRunning.value && steps < 400) {
      const pair = findPetLinkPair(board.value)
      if (!pair) break
      const [a, b] = pair
      const path = findPetLinkPath(board.value, Math.floor(a / COLS), a % COLS, Math.floor(b / COLS), b % COLS)
      if (!path) break
      await match(a, b, path)
      steps += 1
      if (won.value || lost.value || level.value !== startLevel) break
    }
    if (!findPetLinkPair(board.value) && debugTiles.value > 0) {
      pushDebugLog(t('games.petlink.debug.stuck', { n: debugTiles.value }))
    }
  } finally {
    debugFast.value = false
    autoRunning.value = false
  }
}

// 内核自检:1~9 关各跑若干局,统计「误消/吃错型」
function runSelfTest() {
  const games = 10
  let steps = 0
  const issues = []
  for (let lv = 1; lv <= MAX_LEVEL; lv++) {
    const r = simulatePetLinkLevel(lv, games)
    steps += r.steps
    issues.push(...r.issues)
  }
  debugSelfText.value = t('games.petlink.debug.selfTestDone', { games, levels: MAX_LEVEL, steps, issues: issues.length })
  debugIssueCount.value += issues.length
  debugLog.value = []
  pushDebugLog(debugSelfText.value, issues.length > 0)
  for (const it of issues.slice(0, 5)) {
    pushDebugLog(t('games.petlink.debug.selfTestIssue', { level: it.level, kind: it.kind, step: it.step }), true)
  }
}

// 带 ?level=N 进入时直接落到该关,便于复现第 5/8 关
function applyDebugStart() {
  if (!debugMode.value) return
  const q = Number(route.query.level)
  debugJump(Number.isInteger(q) && q >= 1 && q <= MAX_LEVEL ? q : level.value)
}

// ---------- 关卡推进 ----------

async function levelComplete() {
  stopTicker()
  playWin()
  // 时间奖励:与原件 (1800 - frame)/3 一致 → 剩余秒数 ×4
  const bonus = Math.round(timeLeft.value * 4)
  score.value += bonus
  ElMessage.success(`${t('games.petlink.levelClear', { n: level.value })} +${10 * 70 + bonus}`)

  if (level.value >= MAX_LEVEL) {
    won.value = true
    clearState()
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
  saveState()
}

async function grantReward() {
  try {
    const res = await gameApi.petLinkReward()
    rewardMsg.value =
      res && res.awarded > 0
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
      clearState()
    }
  }, 1000)
}
function stopTicker() {
  if (ticker) {
    clearInterval(ticker)
    ticker = null
  }
}
function togglePause() {
  if (won.value || lost.value) return
  paused.value = !paused.value
}

// ---------- 流程 ----------

function updateBoardScale() {
  const el = boardScrollRef.value
  if (!el) return
  const availW = el.clientWidth
  let scale = availW > 0 ? availW / svgW : 1
  if (isFullscreen.value) {
    // 全屏:同时按可用宽/高取较小者,让棋盘铺满屏幕(不再 1.6 封顶),大屏不累眼
    const card = gameCardEl.value
    const hud = card?.querySelector('.game-hud')
    const bar = card?.querySelector('.game-bottombar')
    const hudH = hud ? hud.offsetHeight + 12 : 0 // .game-hud margin-bottom 12px
    const barH = bar ? bar.offsetHeight + 12 : 0 // .game-bottombar margin-top 12px
    const availH = (card ? card.clientHeight : window.innerHeight) - 40 - hudH - barH // 40 = .game-card 上下 padding
    if (availH > 0) scale = Math.min(scale, availH / svgH)
    scale = Math.max(0.5, scale)
  } else {
    scale = Math.min(1.6, Math.max(0.5, scale))
  }
  boardScale.value = scale
}

// 封面主按钮:有存档就接着上次的盘面,没有就开新局
function continueGame() {
  screen.value = 'game'
  if (savedState.value) {
    applySavedState(savedState.value)
    savedState.value = null
  } else {
    restart()
  }
  applyDebugStart()
}

// 封面「新游戏」:丢掉旧档,从第 1 关开始
function startFresh() {
  screen.value = 'game'
  savedState.value = null
  restart()
  applyDebugStart()
}

function restart() {
  clearState()
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
  saveState()
}

function centerX(c) {
  return PAD + c * (CELL_W + GAP_X) + CELL_W / 2
}
function centerY(r) {
  return PAD + r * (CELL_H + GAP_Y) + CELL_H / 2
}
function sleep(ms) {
  return new Promise((res) => setTimeout(res, ms))
}

// ---------- FLIP 滑行动画(牌对象按 id 稳定身份) ----------

function snapshotPositions() {
  const map = new Map()
  const els = boardEl.value ? boardEl.value.querySelectorAll('[data-tid]') : []
  els.forEach((el) => {
    const tid = el.getAttribute('data-tid')
    if (tid && tid !== '0') map.set(tid, el.getBoundingClientRect())
  })
  return map
}

function flipAnimate(before, ms = 200) {
  const els = boardEl.value ? boardEl.value.querySelectorAll('[data-tid]') : []
  const s = boardScale.value || 1
  els.forEach((el) => {
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
    el.style.transition = `transform ${ms}ms ease`
    el.style.transform = 'translate(0, 0)'
    setTimeout(() => {
      el.style.transition = ''
      el.style.transform = ''
    }, ms)
  })
}

// 棋面/分数/关卡/重排/暂停变化即落盘;计时每秒变化走防抖,避免每秒写 localStorage
watch([board, score, level, shufflesLeft, paused], saveState, { deep: true })
watch(timeLeft, scheduleSave)

const onVisibilityChange = () => {
  if (document.hidden) saveState()
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', updateBoardScale)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('visibilitychange', onVisibilityChange)
  // 进入游戏即关闭全局光影特效(与图片/视频/看书一致),离开时恢复
  sunLight?.suspendEffects()
  // 封面先读出可续存档,由「继续游戏」决定是否装载
  savedState.value = loadSavedState()
  nextTick(() => updateBoardScale())
  resizeObserver = new ResizeObserver(() => updateBoardScale())
  if (boardScrollRef.value) resizeObserver.observe(boardScrollRef.value)
})
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  saveState()
  stopTicker()
  clearTimeout(hintTimer)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', updateBoardScale)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  sunLight?.restoreEffects()
})

// 原版快捷键:空格暂停、F5 重排
function onKeydown(e) {
  if (screen.value !== 'game') return
  if (e.code === 'Space') {
    e.preventDefault()
    togglePause()
  } else if (e.key === 'F5') {
    e.preventDefault()
    doShuffle()
  }
}
</script>

<style scoped>
.game-card {
  position: relative;
  min-height: 480px;
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
.game-card:fullscreen {
  width: 100vw;
  height: 100vh;
  min-height: 100vh;
  border-radius: 0;
}

/* 封面 */
.cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}
.cover-title {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-family: 'Yuanti SC', 'YouYuan', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 58px;
  font-weight: 800;
  letter-spacing: 4px;
  margin: 0;
  text-shadow:
    0 3px 0 rgba(0, 0, 0, 0.35),
    0 0 30px rgba(255, 255, 255, 0.12);
}
/* 原版标题配色:宠物=蓝、连连=粉红、看=蓝、-ihomy版=青绿 */
.ct-pet {
  color: #00ccff;
}
.ct-lian {
  color: #ff0066;
}
.ct-kan {
  color: #00ccff;
}
.ct-ihomy {
  font-size: 28px;
  color: #66ffcc;
  margin-left: 8px;
  text-shadow: 0 2px 0 rgba(0, 0, 0, 0.4);
}
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
  box-shadow:
    0 6px 0 #c2273d,
    0 10px 24px rgba(255, 90, 120, 0.4);
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease;
}
.start-btn:hover {
  transform: translateY(-2px);
  box-shadow:
    0 8px 0 #c2273d,
    0 14px 28px rgba(255, 90, 120, 0.5);
}
.start-btn:active {
  transform: translateY(3px);
  box-shadow:
    0 3px 0 #c2273d,
    0 6px 14px rgba(255, 90, 120, 0.4);
}
.cover-new-btn {
  padding: 8px 26px;
  border: 1px solid rgba(255, 255, 255, 0.26);
  border-radius: 999px;
  background: transparent;
  color: #aebbd0;
  font-size: 14px;
  letter-spacing: 2px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease;
}
.cover-new-btn:hover {
  color: #fff;
  border-color: #fff;
}
.cover-credits {
  font-size: 12px;
  color: #8899aa;
  letter-spacing: 1px;
  margin-top: 6px;
}
.cover-hint {
  font-size: 12px;
  color: #667;
  max-width: 420px;
  line-height: 1.6;
}

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
.hud-level {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.hud-level-no {
  color: #ff0066;
  font-weight: 700;
  font-size: 16px;
}
.hud-level-name {
  color: #00ccff;
  font-size: 14px;
  font-weight: 600;
}
.hud-life {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.hud-life .life-num {
  color: #00ff00;
  font-size: 16px;
  font-weight: 700;
}
.hud-life .life-label {
  color: #00ff00;
  font-size: 12px;
}
.hud-timer {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}
.hud-score {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-left: auto;
}
.hud-score-label {
  color: #b8c4d8;
}
.hud-score b {
  color: #ffcc66;
  font-size: 18px;
}

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
    repeating-linear-gradient(90deg, rgba(0, 0, 0, 0.22) 0px, rgba(0, 0, 0, 0.22) 2px, transparent 2px, transparent 10px),
    repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.1) 0px, rgba(255, 255, 255, 0.1) 1px, transparent 1px, transparent 7px),
    linear-gradient(180deg, #b9e4ff 0%, #6db4ff 45%, #4a8cff 100%);
  image-rendering: pixelated;
  transition: width 1s linear;
}
.timer-text {
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: #4dd2ff;
  min-width: 34px;
  text-align: right;
}
.timer-bar.warn {
  border-color: #ff7a7a;
}
.timer-bar.warn .timer-fill {
  background:
    repeating-linear-gradient(90deg, rgba(0, 0, 0, 0.22) 0px, rgba(0, 0, 0, 0.22) 2px, transparent 2px, transparent 10px),
    repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.1) 0px, rgba(255, 255, 255, 0.1) 1px, transparent 1px, transparent 7px),
    linear-gradient(180deg, #ffb3a0 0%, #ff5a5a 55%, #ff2f4d 100%);
  animation: timeBarPulse 1s ease-in-out infinite;
}
.timer-text.warn {
  color: #ff5a5a;
}
@keyframes timeBarPulse {
  0%,
  100% {
    filter: brightness(1);
  }
  50% {
    filter: brightness(1.5);
  }
}

/* 棋盘 */
.board-scroll {
  width: 100%;
  max-width: 100%;
}
.board-scale {
  position: relative;
  margin: 0 auto;
  overflow: hidden;
}
.board-wrap {
  position: relative;
  width: fit-content;
  padding: 44px;
  transform-origin: top left;
}
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
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease;
  contain: layout style;
}
.tile:hover:not(:disabled):not(.is-empty) {
  transform: translateY(-1px);
}
.tile .pet {
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
}
.tile.is-empty {
  background: transparent;
  border-color: transparent;
  cursor: default;
}
.tile.is-selected {
  box-shadow:
    0 0 0 2px #0033ff,
    inset 0 0 0 1px rgba(27, 28, 250, 0.25);
  transform: translateY(-1px);
}
.tile.is-hint {
  animation: hintPulse 0.8s ease-in-out infinite;
  border-color: #cc0066;
}
@keyframes hintPulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(204, 0, 102, 0.6);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(204, 0, 102, 0.25);
  }
}
.board-line {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
.board-line .line {
  fill: none;
  stroke: #0099ff;
  stroke-width: 5;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 4px rgba(0, 153, 255, 0.8));
}
.board-wrap.is-frozen .tile {
  cursor: default;
}

.pause-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  border-radius: 8px;
}
.pause-label {
  font-size: 26px;
  font-weight: 700;
  color: #fff;
  letter-spacing: 3px;
}

/* 底部操作 */
.game-bottombar {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

/* 调试版面板(?debug=1) */
.debug-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  max-width: 720px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px dashed rgba(0, 204, 255, 0.45);
  border-radius: 10px;
  background: rgba(0, 204, 255, 0.06);
  font-size: 12px;
  color: #b8c4d8;
}
.debug-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.debug-tag {
  color: #00ccff;
  font-weight: 700;
  letter-spacing: 1px;
}
.debug-level-select {
  width: 104px;
}
.debug-stats {
  gap: 14px;
  font-variant-numeric: tabular-nums;
}
.debug-stats b {
  color: #ffcc66;
}
.debug-bad {
  color: #ff5a5a;
  font-weight: 700;
}
.debug-self {
  color: #66ffcc;
}
.debug-hint {
  color: #667;
}
.debug-log {
  max-height: 132px;
  overflow: auto;
  font-family: ui-monospace, Consolas, monospace;
  line-height: 1.5;
}
.debug-line {
  white-space: pre-wrap;
}

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
.overlay-icon {
  font-size: 42px;
}
.overlay-title {
  font-size: 20px;
  font-weight: 700;
}
.overlay-score {
  font-size: 14px;
  color: #888;
}
.overlay-reward {
  font-size: 14px;
  color: #f0a04b;
  font-weight: 700;
}
</style>
