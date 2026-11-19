<template>
  <GameShell
    ref="shellEl"
    class="pl-shell"
    :title="$t('games.petlink.title')"
    body-align="start"
    :new-game="screen === 'game' && !play.active"
    :new-game-confirm="screen === 'game' && !won && !lost"
    @back="goBack"
    @new-game="onRestartClick"
  >
    <template #rules>
      <p>{{ $t('games.petlink.coverHint') }}</p>
    </template>

    <!-- 提示/重排/暂停/静音:竖屏与桌面用棋盘下面那条(.game-bottombar),
         手机横屏搬进外壳左栏头部以下(两条同时只露一条,见样式里的横屏档);封面不显示 -->
    <template #actions>
      <div v-if="screen === 'game'" class="pl-rail-actions">
        <el-button size="small" :disabled="frozen" @click="doHint">💡 {{ $t('games.petlink.hint') }}</el-button>
        <el-button size="small" :disabled="frozen || shufflesLeft <= 0" @click="doShuffle">
          🔀 {{ $t('games.petlink.shuffle') }}
        </el-button>
        <el-button size="small" :disabled="won || lost" @click="togglePause">
          {{ paused ? '▶' : '⏸' }} {{ $t('games.petlink.pause') }}
        </el-button>
        <el-button size="small" :title="$t('games.petlink.muteToggle')" @click="toggleMute">
          {{ muted ? '🔇' : '🔊' }} {{ muted ? $t('games.petlink.unmute') : $t('games.petlink.mute') }}
        </el-button>
      </div>
    </template>

    <template #extras>
      <GameRoomBar
        v-if="screen === 'game'"
        :room="play.room"
        :is-host="play.isHost"
        :score="score"
        :reported="play.reported"
        :ended="play.ended"
        :result="play.spec?.result"
        :my-id="play.myId"
        @submit="submitRoomScore"
        @leave="play.leave"
      />
    </template>

    <div class="pl-layout" :style="{ '--pl-pad': padPx + 'px' }" @contextmenu.prevent @dblclick.prevent>
      <!-- 封面(复刻原版标题屏):开始/继续 + 版权;玩法说明在规则弹窗 -->
      <div v-if="screen === 'cover'" class="cover">
        <button class="start-btn" type="button" @click="continueGame">
          {{ savedState ? $t('games.petlink.resume') : $t('games.petlink.start') }}
        </button>
        <button v-if="savedState" class="cover-new-btn" type="button" @click="startFresh">
          {{ $t('games.petlink.newGame') }}
        </button>
        <div class="cover-credits">{{ $t('games.petlink.credits') }}</div>
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
              <div class="board" ref="boardEl" :class="{ 'is-hinting': hinted.length > 0 }">
                <button
                  v-for="cell in cells"
                  :key="cell.tid ? 't' + cell.tid : 'e' + cell.idx"
                  :data-tid="cell.tid"
                  class="tile"
                  :class="{
                    'is-empty': cell.value === 0,
                    'is-selected': selected === cell.idx,
                    'is-peer-anchor': !!peerAnchorAt(cell.idx),
                    'is-hint': hinted.includes(cell.idx),
                  }"
                  :style="peerAnchorAt(cell.idx) ? { boxShadow: `0 0 0 2px ${peerAnchorAt(cell.idx).color}` } : null"
                  :disabled="animating || paused || won || lost"
                  @click="onClick(cell.idx)"
                >
                  <img v-if="cell.value !== 0" :src="petImages[cell.value - 1]" alt="" class="pet" />
                </button>
              </div>

              <svg class="board-line" :viewBox="`0 0 ${svgW} ${svgH}`">
                <polyline v-if="linePoints" :points="linePoints" class="line" />
                <polyline v-if="hintPoints" :points="hintPoints" class="line line-hint" />
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
          <el-button size="small" :title="$t('games.petlink.muteToggle')" @click="toggleMute">
            {{ muted ? '🔇' : '🔊' }} {{ muted ? $t('games.petlink.unmute') : $t('games.petlink.mute') }}
          </el-button>
        </div>

        <!-- 调试版(地址加 ?debug=1):跳关、自动消除、误消/奇数张体检;不读写玩家存档 -->
        <div v-if="debugMode" class="debug-panel">
          <div class="debug-row">
            <span class="debug-tag">{{ $t('games.petlink.debug.title') }}</span>
            <el-select v-model="debugJumpLevel" size="small" class="debug-level-select" :teleported="!isFullscreen">
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
              <el-button type="primary" size="small" round @click="onResultAction">
                {{ play.active ? $t('games.room.backGames') : $t('games.petlink.playAgain') }}
              </el-button>
            </div>
          </div>
        </Transition>
      </template>
    </div>
  </GameShell>
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
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { seededRng } from '@/utils/games/seededRng'
import { isSpectatorRole } from '@/utils/gameRoom'
import {
  PETLINK_CELLS,
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
// 手机横屏(矮屏 + 左侧信息栏)优先把高度让给棋盘:外圈收到连线本身的极限宽度
// ——列环中心线离边 CELL_W/2+2、行环离边 CELL_H/2,26 还能留出 3~6px,
// 上下共省 36px,牌面按高度等比放大一圈(844×390 实测牌面 26.5 → 30.8px,板面 443×308 → 489×332)。
const PAD_COMPACT = 26
const LEVEL_TIME = PETLINK_LEVEL_TIME
const MAX_LEVEL = PETLINK_MAX_LEVEL
const TIME_BONUS = 3 // 每消除一对补充 3 秒(原件 36 帧)
const BASE_SHUFFLE = 6
const MATCH_LINE_MS = 150 // 消除连线展示时长(原 280,贴近原版 12fps 连线瞬时节奏)
const MATCH_FLIP_MS = 120 // 消除后牌位滑行动画时长(原 220)
const HINT_MS = 2400 // 提示高亮停留时长(原 1600,太短还没找到牌就灭了)

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
// 自己的选中锚点(待配对的第一张牌);房间合作模式里另有 peerAnchors 存其他玩家的锚点
const selected = ref(-1)
const peerAnchors = ref({})
const hinted = ref([])
const animating = ref(false)
const paused = ref(false)
const won = ref(false)
const lost = ref(false)
const linePoints = ref('')
const hintPoints = ref('')
const rewardMsg = ref('')
// 封面读到的可续存档(点「继续游戏」才真正装载)
const savedState = ref(null)

// ---------- 「一起玩」房间(?room=<id>) ----------
// PK:双端同 seed 独立跑,先通关者胜(交卷/终局上报);
// COOP:房主权威,共享盘面由房主广播快照,其他成员只发操作意图,消除结果所有人都能看到。
// 每个玩家有自己的选中锚点(host 按 op.from 分开记账),不再共享一个 selected——
// 多人同时点击不再互相顶掉对方的「第一张牌」,谁先点中配对的那张谁消除。
const play = useGameRoomPlay({ onSignal: onRoomSignal, onRoom: onRoomChange })
const coopGuest = computed(() => play.active && play.coop && !play.isHost)
/** 房间局返回房间大厅(别把玩家丢回小游戏列表,大厅里还要准备/邀请);单机回列表 */
const goBack = () => {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else router.push('/games')
}

let roomTimer = null
// 合作模式:房主每重开一局本地推进一轮,换一副新牌面(同 seed + 同关卡原本会发出一样的牌,
// 「再来一局」看起来像没反应)。访客只看房主快照,不参与计算。
let coopRound = 0

// ---------- 每人一个锚点 ----------
function anchorGet(uid) {
  if (String(uid) === String(play.myId)) return selected.value
  return peerAnchors.value[String(uid)] ?? -1
}
function anchorSet(uid, idx) {
  if (String(uid) === String(play.myId)) {
    selected.value = idx
    return
  }
  peerAnchors.value = { ...peerAnchors.value, [String(uid)]: idx }
}
function anchorClearAll() {
  selected.value = -1
  peerAnchors.value = {}
}
/** 房主广播用的完整锚点表(自己的 + 其他人的) */
function anchorsSnapshot() {
  return { [String(play.myId)]: selected.value, ...peerAnchors.value }
}

function snapshotRoomState() {
  return {
    board: board.value.map((t) => (t ? { id: t.id, type: t.type } : null)),
    score: score.value,
    level: level.value,
    shufflesLeft: shufflesLeft.value,
    timeLeft: timeLeft.value,
    anchors: anchorsSnapshot(),
    won: won.value,
    lost: lost.value,
  }
}

function applyRoomState(state) {
  if (!state || !Array.isArray(state.board) || state.board.length !== PETLINK_CELLS) return
  let maxId = tileSeq
  const nextBoard = state.board.map((t) => {
    if (!t || !Number.isInteger(t.id) || !Number.isInteger(t.type)) return null
    maxId = Math.max(maxId, t.id)
    return { id: t.id, type: t.type }
  })
  tileSeq = maxId
  board.value = nextBoard
  score.value = Math.max(0, Number(state.score) || 0)
  level.value = Math.min(MAX_LEVEL, Math.max(1, Number(state.level) || 1))
  shufflesLeft.value = Math.max(0, Number(state.shufflesLeft) || 0)
  timeLeft.value = Math.max(0, Number(state.timeLeft) || 0)
  const anchors = {}
  if (state.anchors && typeof state.anchors === 'object') {
    for (const [uid, idx] of Object.entries(state.anchors)) {
      if (Number.isInteger(idx) && idx >= -1 && idx < PETLINK_CELLS) anchors[String(uid)] = idx
    }
  }
  selected.value = anchors[String(play.myId)] ?? -1
  const peers = {}
  for (const [uid, idx] of Object.entries(anchors)) {
    if (String(uid) !== String(play.myId)) peers[String(uid)] = idx
  }
  peerAnchors.value = peers
  won.value = !!state.won
  lost.value = !!state.lost
  linePoints.value = ''
  clearHint()
  if (won.value || lost.value) stopTicker()
}

/** 房主广播共享盘面(80ms 合批,避免连点刷屏) */
function broadcastRoomState(immediate = false) {
  if (!play.active || !play.coop || !play.isHost) return
  if (roomTimer) {
    clearTimeout(roomTimer)
    roomTimer = null
  }
  const emit = () => {
    roomTimer = null
    play.send('state', { state: snapshotRoomState() })
  }
  if (immediate) emit()
  else roomTimer = setTimeout(emit, 80)
}

function onRoomSignal(data) {
  if (!data) return
  if (play.coop) {
    if (data.event === 'state' && !play.isHost) {
      applyRoomState(data.state)
      return
    }
    if (data.event === 'sync' && play.isHost) {
      broadcastRoomState(true)
      return
    }
    if (data.event === 'op' && play.isHost) {
      // 观战成员的误操作直接忽略(前端已拦,这里兜底)
      const sender = play.members.find((m) => String(m.userId) === String(data.from))
      if (sender && isSpectatorRole(sender.role)) return
      if (data.kind === 'click') {
        const index = Number(data.index)
        if (Number.isInteger(index) && index >= 0 && index < PETLINK_CELLS) onClick(index, true, data.from)
      } else if (data.kind === 'shuffle') {
        doShuffle()
      } else if (data.kind === 'restart') {
        restart()
      }
      return
    }
  }
}

function onRoomChange(next) {
  if (!next) return
  if (next.status === 'FINISHED' || next.status === 'CANCELLED') stopTicker()
}

const submitRoomScore = () => play.report(score.value)

function onRestartClick() {
  if (play.ended) return
  if (coopGuest.value) {
    play.send('op', { kind: 'restart' })
    return
  }
  restart()
}

function onResultAction() {
  if (play.active) play.leave()
  else restart()
}

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
// 手机横屏(与外壳 §43 横屏档同一门槛)换紧凑外圈:连线坐标与 SVG 盒子一起变,
// 所以 pad 必须是从同一个值派生的,页面别处不许再写死 44。
const compactLandscape = ref(false)
let landscapeQuery = null
const padPx = computed(() => (compactLandscape.value ? PAD_COMPACT : PAD))
const svgW = computed(() => boardW + padPx.value * 2)
const svgH = computed(() => boardH + padPx.value * 2)

const cells = computed(() => board.value.map((t, idx) => ({ idx, value: t ? t.type : 0, tid: t ? t.id : 0 })))

// ---------- 其他玩家的锚点展示(房间合作模式):按成员序固定配色 ----------
const PEER_COLORS = ['#e8a13a', '#4da3ff', '#9d6bff', '#3ecf8e', '#ff7ab2', '#59c2c9']
const peerAnchorList = computed(() =>
  Object.entries(peerAnchors.value)
    .filter(([, idx]) => Number.isInteger(idx) && idx >= 0)
    .map(([uid, idx], i) => ({ uid, idx, color: PEER_COLORS[i % PEER_COLORS.length] })),
)
const peerAnchorAt = (idx) => peerAnchorList.value.find((p) => p.idx === idx)

const boardScrollRef = ref(null)
const boardEl = ref(null)
const boardScale = ref(1)
const scaledW = computed(() => Math.round(svgW.value * boardScale.value))
const scaledH = computed(() => Math.round(svgH.value * boardScale.value))
// 观战成员整体冻结:只看盘面,不参与任何交互(计时也由玩家端推进)
const frozen = computed(() => animating.value || paused.value || won.value || lost.value || play.ended || play.isSpectator)
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

// 统一外壳持有全屏/宽屏;棋盘缩放按外壳游戏区实测高度重算
const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)
function onFullscreenChange() {
  // 全屏/退出后可用宽高变化,重新计算缩放
  nextTick(() => updateBoardScale())
}
/** 横屏档切换(含键盘/折叠屏改视口):换完外圈宽窄,按新 SVG 尺寸重算缩放 */
function onLandscapeQueryChange(e) {
  compactLandscape.value = !!e.matches
  nextTick(() => updateBoardScale())
}

// ---------- 音频(点击/消除/胜利音效,无背景音乐;可静音) ----------
// 走 Web Audio 预解码样本:HTMLAudioElement 每次都要 seek 回 0 再起播,手机上一来一回
// 拖到一两百毫秒;点击音本身只有 21ms,听感就是「点了慢半拍」。BufferSource 一点即响,
// 连点还能叠着响而不互相打断。首次点击时上下文在用户手势里 resume,不会静默失声。
const muted = ref(localStorage.getItem('ihomy:game:petlink:muted') === '1')
const SOUND_URLS = { click: clickUrl, match: matchUrl, win: winUrl }
let audioCtx = null
const soundBuffers = new Map()
let htmlAudios = null

/** 建上下文并把三段音效拉下来解码(解码不要求用户激活,进页面就能先备好) */
function ensureAudioContext() {
  if (audioCtx) return audioCtx
  const Ctx = window.AudioContext || window.webkitAudioContext
  if (!Ctx) return null
  try {
    audioCtx = new Ctx()
  } catch (e) {
    audioCtx = null
    return null
  }
  for (const [key, url] of Object.entries(SOUND_URLS)) {
    fetch(url)
      .then((res) => res.arrayBuffer())
      .then((raw) => {
        // 回调式写法:老 Safari/WKWebView 的 decodeAudioData 不返回 Promise
        audioCtx?.decodeAudioData(
          raw,
          (buffer) => soundBuffers.set(key, buffer),
          () => {},
        )
      })
      .catch(() => {})
  }
  return audioCtx
}

/** 没有 Web Audio 的老环境(极少):退回 <audio> 元素,慢一点但有声 */
function playHtmlAudio(key) {
  if (!htmlAudios) {
    htmlAudios = {}
    for (const [k, url] of Object.entries(SOUND_URLS)) htmlAudios[k] = new Audio(url)
  }
  const el = htmlAudios[key]
  if (!el) return
  try {
    el.currentTime = 0
    el.play().catch(() => {})
  } catch (e) {}
}

function playSound(key) {
  if (muted.value) return
  const ctx = ensureAudioContext()
  if (!ctx) {
    playHtmlAudio(key)
    return
  }
  // 移动端上下文默认 suspended;在点击手势里 resume 当帧就生效
  if (ctx.state === 'suspended') ctx.resume().catch(() => {})
  const buffer = soundBuffers.get(key)
  if (!buffer) return
  const source = ctx.createBufferSource()
  source.buffer = buffer
  source.connect(ctx.destination)
  source.start()
}

function toggleMute() {
  muted.value = !muted.value
  localStorage.setItem('ihomy:game:petlink:muted', muted.value ? '1' : '0')
}
const playClick = () => playSound('click')
const playMatch = () => playSound('match')
const playWin = () => playSound('win')

// ---------- 进度存档 ----------
// 只在对局进行中写档;通关/失败/重新开始即清档,封面按存档显示「继续游戏」。
const saveState = () => {
  if (debugMode.value || play.active) return
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
  if (debugMode.value || play.active) return
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
  anchorClearAll()
  clearHint()
  animating.value = false
  won.value = false
  lost.value = false
  linePoints.value = ''
  rewardMsg.value = ''
  if (ensurePlayable()) startTicker()
}

// ---------- 关卡 ----------

// 发牌走棋盘内核;tileSeq 只负责跨关连续编号,保证 FLIP 动画的牌身份稳定。
// 房间模式下按 (seed, 关卡) 派生随机源,保证 PK 双端首发牌面一致。
function buildBoard(lv) {
  const base = (Number(play.seed) ^ Math.imul(lv, 0x9e3779b1)) >>> 0
  const rand = play.active ? seededRng(play.coop ? (base + coopRound * 7877) >>> 0 : base) : Math.random
  const { board: next, maxId } = createPetLinkBoard(lv, tileSeq, rand)
  tileSeq = maxId
  return next
}

// 连线判定与「找任意一对」由内核提供(最多两拐 + 可绕外圈,见 utils/games/petlinkBoard.js)
const findAnyPair = () => findPetLinkPair(board.value)

// ---------- 交互 ----------

function onClick(i, remote = false, remoteUid = null) {
  if (!remote && coopGuest.value) {
    // 合作模式:非房主只上报意图,盘面由房主统一改动后再广播回来;
    // 本地先乐观预选(自己的锚点),房主快照到达后以权威值覆盖
    if (frozen.value) return
    clearHint()
    if (selected.value === -1) {
      selected.value = i
      playClick()
    } else if (selected.value === i) {
      selected.value = -1
    } else {
      selected.value = i
    }
    play.send('op', { kind: 'click', index: i })
    return
  }
  if (frozen.value) return
  if (!board.value[i]) return
  clearHint()
  // 房主按操作者分开记账(remote=成员 op,本地=房主自己),各自维护自己的锚点
  const uid = remote ? remoteUid : play.myId
  const cur = anchorGet(uid)
  // 锚点落在已消除的格子上(另一名玩家抢先消了这对):当作没有锚点重新选
  if (cur === -1 || cur === i || !board.value[cur]) {
    if (cur === i) {
      anchorSet(uid, -1)
      return
    }
    anchorSet(uid, i)
    playClick()
    return
  }
  if (board.value[i].type !== board.value[cur].type) {
    anchorSet(uid, i)
    playClick()
    return
  }
  const path = findPetLinkPath(board.value, Math.floor(cur / COLS), cur % COLS, Math.floor(i / COLS), i % COLS)
  anchorSet(uid, -1)
  if (path) match(cur, i, path)
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

// 提示:高亮那一对 + 其余牌压暗成聚光灯 + 虚线画出可连路径。
// 只加光环不够看——棋盘列间距 2px、行间距 0,相邻牌会把光环盖掉,手机缩放后更糊。
function doHint() {
  if (frozen.value) return
  const pair = findAnyPair()
  if (!pair) return
  const [a, b] = pair
  const path = findPetLinkPath(board.value, Math.floor(a / COLS), a % COLS, Math.floor(b / COLS), b % COLS)
  hinted.value = [a, b]
  hintPoints.value = path ? path.map(([r, c]) => `${centerX(c)},${centerY(r)}`).join(' ') : ''
  clearTimeout(hintTimer)
  hintTimer = setTimeout(clearHint, HINT_MS)
}

/** 清掉提示高亮与路径(点击、重排、换关、恢复存档都走这里) */
function clearHint() {
  clearTimeout(hintTimer)
  hintTimer = null
  hinted.value = []
  hintPoints.value = ''
}

function doShuffle() {
  if (frozen.value || shufflesLeft.value <= 0) return
  if (coopGuest.value) {
    play.send('op', { kind: 'shuffle' })
    return
  }
  applyShuffle()
  playClick()
}

function applyShuffle() {
  // 房间内按 (seed, 关卡, 剩余重排次数) 派生随机源:双方用同样次数重排时盘面依然一致
  const rand = play.active ? seededRng((Number(play.seed) + level.value * 7919 + shufflesLeft.value * 104729) >>> 0) : Math.random
  shufflePetLinkBoard(board.value, rand)
  shufflesLeft.value -= 1
  // 盘面洗过了:所有人的锚点都作废(旧格子位置已失效)
  anchorClearAll()
  clearHint()
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
  anchorClearAll()
  clearHint()
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
    if (play.active) {
      // 房间:PK 先完成制立即上报;COOP 只有房主上报团队完成
      if (!play.coop || play.isHost) play.report(score.value)
      return
    }
    await grantReward()
    return
  }
  // 进入下一关:重排数 = 6 + (关-1) + max(0, 关-4)(第 1~9 关 6/7/8/9/11/13/15/17/19)
  level.value += 1
  shufflesLeft.value = BASE_SHUFFLE + (level.value - 1) + Math.max(0, level.value - 4)
  board.value = buildBoard(level.value)
  timeLeft.value = LEVEL_TIME
  anchorClearAll()
  clearHint()
  startTicker()
  saveState()
}

async function grantReward() {
  if (play.active) return
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
  // 合作模式只有房主计时,访客按房主快照显示倒计时;观战不跑计时
  if (coopGuest.value || play.isSpectator) return
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
  let scale = availW > 0 ? availW / svgW.value : 1
  // 按外壳游戏区实测高取较小者,棋盘铺满可用区(不封顶,大屏不累眼)
  const bodyEl = shellEl.value?.bodyEl
  if (bodyEl) {
    // 顶部信息栏与底部操作条占掉的高度从实测样式读:手机横屏信息栏压扁、间距变小,
    // 写死 12 会白扣一截;手机横屏的操作条是 display:none(四个键搬去左栏),整条都不能扣
    const outerH = (node) => {
      if (!node || !node.offsetHeight) return 0
      const cs = getComputedStyle(node)
      return node.offsetHeight + (parseFloat(cs.marginTop) || 0) + (parseFloat(cs.marginBottom) || 0)
    }
    const availH = bodyEl.clientHeight - outerH(bodyEl.querySelector('.game-hud')) - outerH(bodyEl.querySelector('.game-bottombar')) - 4
    if (availH > 0) scale = Math.min(scale, availH / svgH.value)
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
  if (play.coop && play.isHost) coopRound += 1
  level.value = 1
  score.value = 0
  shufflesLeft.value = BASE_SHUFFLE
  timeLeft.value = LEVEL_TIME
  anchorClearAll()
  clearHint()
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
  return padPx.value + c * (CELL_W + GAP_X) + CELL_W / 2
}
function centerY(r) {
  return padPx.value + r * (CELL_H + GAP_Y) + CELL_H / 2
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
// 合作模式:房主把共享盘面变化(含每秒倒计时)同步给其他成员
watch(
  [board, score, level, shufflesLeft, timeLeft, selected, won, lost],
  () => {
    if (play.active && play.coop && play.isHost) broadcastRoomState()
  },
  { deep: true },
)

// 封面 → 游戏:棋盘挂载后重算缩放并接管尺寸观察(外壳游戏区高度固定,不重算会溢出被裁)
watch(screen, () => {
  nextTick(() => {
    updateBoardScale()
    if (boardScrollRef.value && resizeObserver) resizeObserver.observe(boardScrollRef.value)
  })
})

const onVisibilityChange = () => {
  if (document.hidden) saveState()
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', updateBoardScale)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('visibilitychange', onVisibilityChange)
  // 进页面就把三段音效解码备好,第一次点击不必等加载
  ensureAudioContext()
  // 外圈宽窄跟着横屏档走:切成紧凑外圈后 SVG 盒子变小,按新尺寸重算缩放
  landscapeQuery = window.matchMedia?.('(orientation: landscape) and (max-height: 520px)') || null
  compactLandscape.value = !!landscapeQuery?.matches
  if (landscapeQuery?.addEventListener) landscapeQuery.addEventListener('change', onLandscapeQueryChange)
  else landscapeQuery?.addListener?.(onLandscapeQueryChange)
  // 进入游戏即关闭全局光影特效(与图片/视频/看书一致),离开时恢复
  sunLight?.suspendEffects()
  resizeObserver = new ResizeObserver(() => updateBoardScale())

  if (play.active) {
    // 房间:跳过封面直接进局。COOP 由房主发牌并广播快照,访客等房主同步。
    screen.value = 'game'
    await play.load()
    if (coopGuest.value) play.requestSync()
    else restart()
    await nextTick()
    updateBoardScale()
    if (boardScrollRef.value) resizeObserver.observe(boardScrollRef.value)
    return
  }

  // 封面先读出可续存档,由「继续游戏」决定是否装载
  savedState.value = loadSavedState()
  nextTick(() => {
    updateBoardScale()
    if (boardScrollRef.value) resizeObserver.observe(boardScrollRef.value)
  })
})
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  if (roomTimer) clearTimeout(roomTimer)
  saveState()
  stopTicker()
  clearHint()
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', updateBoardScale)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (landscapeQuery?.removeEventListener) landscapeQuery.removeEventListener('change', onLandscapeQueryChange)
  else landscapeQuery?.removeListener?.(onLandscapeQueryChange)
  landscapeQuery = null
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
  // 关掉音频线程:手机对同时存在的 AudioContext 有上限,退出游戏就把这个还回去
  if (audioCtx) {
    audioCtx.close().catch(() => {})
    audioCtx = null
    soundBuffers.clear()
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
/* 连连看自带深色底:整页叠一层深色渐变(--gs-bg 必须同时给 dark 档),棋盘区透出 */
.pl-shell {
  --gs-bg: linear-gradient(rgba(12, 14, 11, 0.88), rgba(7, 9, 7, 0.94));
}
html.dark .pl-shell {
  --gs-bg: linear-gradient(rgba(5, 6, 5, 0.92), rgba(3, 4, 3, 0.96));
}
/* 整页自带深色底:外壳头部(返回/游戏名/规则/新游戏)、左栏按钮和棋盘下面那条的按钮
   默认字色取自主题——浅色模式下是暖棕,落在暗底上几乎看不见。主题那条次级按钮规则把
   --color-line / --color-text 硬套进按钮(带 !important),所以不去和它抢选择器,而是
   在这三块容器上把变量改成浅色玻璃口径(含 hover 与禁用档的取色)。深色模式同一套。
   只罩这三处容器:规则弹窗挂在 .gs-rail 下、全屏时不 teleport,改在容器上才不会把
   浅底弹窗里的字也刷白。 */
.pl-shell :deep(.gs-head) .gs-title {
  color: #fff;
}
.pl-shell :deep(.gs-head),
.pl-shell :deep(.gs-controls),
.game-bottombar {
  --color-line: rgba(255, 255, 255, 0.14);
  --color-card-2: rgba(255, 255, 255, 0.24);
  --color-border: rgba(255, 255, 255, 0.24);
  --color-text: #fff;
  --color-text-secondary: rgba(255, 255, 255, 0.82);
  --color-text-tertiary: rgba(255, 255, 255, 0.45);
}
/* 外壳左栏那组按钮是「第二份摆放」:竖屏/桌面用棋盘下面那条,手机横屏才撑出来 */
.pl-shell :deep(.gs-controls) {
  display: none;
}
.pl-rail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
/* 手机横屏:提示/重排/暂停/静音搬进左栏(头部以下),横幅那条整条让位 */
@media (orientation: landscape) and (max-height: 520px) {
  .pl-shell :deep(.gs-controls) {
    display: flex;
  }
  .pl-shell :deep(.gs-actions) {
    justify-content: flex-start;
    margin-inline-start: 0;
    /* 撑满左栏:下面那组按钮按 100% 拉伸才有参照宽度 */
    width: 100%;
  }
  /* 「左栏的下面」:这组按钮贴着左栏底边(doc 顺序在房间条之后,
     用 order 把房间条留在头部下面,按钮自己吃走剩下的空间落到最下) */
  .pl-shell :deep(.gs-controls) {
    order: 1;
    margin-top: auto;
  }
  .pl-shell :deep(.gs-extras) {
    order: 0;
  }
  /* 带 .pl-shell 前缀:.game-bottombar 自己的 display:flex 写在文件更下面,
     同权时后者会赢,这里必须提权才能把横幅那条真正收掉 */
  .pl-shell .game-bottombar {
    display: none;
  }
  /* 四个操作键纵向排列:左栏窄,横着只能挤成两行两列还被压成半截 */
  .pl-rail-actions {
    flex-direction: column;
    align-items: stretch;
    gap: 6px;
    width: 100%;
  }
  .pl-rail-actions .el-button {
    width: 100%;
    margin-left: 0;
  }
  /* 顶部信息栏压成一条:省下的高度全给棋盘(牌面小看不清时,这十几像素很值)。
     带 .pl-layout 前缀提权:这一堆基础样式写在本段之后(1389 行起),同权时后者赢,
     不加前缀整段都是空转(实测 HUD 仍是 24 高 + 12 下边距) */
  .pl-layout .game-hud {
    gap: 10px;
    margin-bottom: 4px;
    font-size: 11px;
  }
  .pl-layout .hud-level-no {
    font-size: 13px;
  }
  .pl-layout .hud-level-name {
    font-size: 12px;
  }
  .pl-layout .hud-life .life-num {
    font-size: 13px;
  }
  .pl-layout .hud-life .life-label {
    font-size: 11px;
  }
  .pl-layout .hud-score b {
    font-size: 14px;
  }
  .pl-layout .timer-bar {
    height: 10px;
  }
  .pl-layout .timer-text {
    font-size: 11px;
    min-width: 30px;
  }
}
.pl-layout {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  /* 外圈留白:桌面/竖屏 44px;手机横屏由脚本按档改写成 26px(内联覆盖)。
     这里给默认值不只是兜底 —— 只在脚本里注入的变量会被 themeTokens 守门判为未定义 */
  --pl-pad: 44px;
  color: #fff;
}

/* 封面 */
.cover {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  text-align: center;
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
  /* 外圈留白:桌面/竖屏 44px,手机横屏由页面按档换成 26px(--pl-pad 内联注入) */
  padding: var(--pl-pad, 44px);
  transform-origin: top left;
}
.board {
  position: relative; /* 提示压暗层的定位基准 */
  display: grid;
  grid-template-columns: repeat(14, 42px);
  grid-auto-rows: 40px;
  column-gap: 2px;
  row-gap: 0;
}
/* 提示聚光灯:其余牌压暗(0 行距下光环会被相邻牌盖住,压暗才看得清) */
.board::after {
  content: '';
  position: absolute;
  /* 压暗层要连外圈一起盖住,留白多少跟着 --pl-pad 走 */
  inset: calc(var(--pl-pad, 44px) * -1);
  z-index: 2;
  background: rgba(0, 0, 0, 0.66);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s ease;
}
.board.is-hinting::after {
  opacity: 1;
}
.tile {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 40px;
  background: var(--color-surface);
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
/* 其他玩家的锚点:描边颜色由内联样式按成员配色注入(不加位移,和自己的选中区分) */
.tile.is-peer-anchor {
  cursor: default;
}
.tile.is-hint {
  /* 压暗层之上 + 放大出格 + 实心洋红描边,缩放到手机尺寸也认得出 */
  z-index: 3;
  border-color: #ff2d8f;
  background: #fff;
  transform: scale(1.1);
  box-shadow:
    0 0 0 3px #ff2d8f,
    0 0 14px 4px rgba(255, 45, 143, 0.85);
  animation: hintPulse 0.72s ease-in-out infinite;
}
.tile.is-hint .pet {
  filter: drop-shadow(0 0 3px rgba(255, 45, 143, 0.9)) brightness(1.12) saturate(1.15);
}
@keyframes hintPulse {
  0%,
  100% {
    transform: scale(1.06);
    box-shadow:
      0 0 0 3px #ff2d8f,
      0 0 10px 3px rgba(255, 45, 143, 0.9);
  }
  50% {
    transform: scale(1.16);
    box-shadow:
      0 0 0 3px #ffd6ea,
      0 0 20px 8px rgba(255, 45, 143, 0.6);
  }
}
.board-line {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 3; /* 高亮牌与提示路径都要压在提示压暗层之上 */
}
.board-line .line {
  fill: none;
  stroke: #0099ff;
  stroke-width: 5;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 4px rgba(0, 153, 255, 0.8));
}
/* 提示路径:洋红流动虚线,与消除瞬间的蓝色实线区分(方头笔帽,圆头会把虚线撑成实线) */
.board-line .line-hint {
  stroke: #ff2d8f;
  stroke-width: 4;
  stroke-linecap: butt;
  stroke-dasharray: 7 7;
  animation: hintDash 0.6s linear infinite;
  filter: drop-shadow(0 0 5px rgba(255, 45, 143, 0.95));
}
@keyframes hintDash {
  to {
    stroke-dashoffset: -28;
  }
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
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
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
  color: var(--color-text-secondary);
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
/* 深色档:结算弹卡随表面色转深(与其余游戏同口径),分数文字走次要色 */
html.dark .overlay-card {
  background: var(--color-surface);
  color: var(--color-text-primary);
}
html.dark .overlay-score {
  color: var(--color-text-secondary);
}
</style>
