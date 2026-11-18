<template>
  <GameShell
    ref="shellEl"
    class="bf-shell"
    :title="title"
    :rules-pages="[$t('games.g2048.rules.pagePlay'), $t('games.g2048.rules.pageControl')]"
    body-align="center"
    @back="goBack"
  >
    <!-- 棋盘规格跟游戏名走:和数独的模式/难度同一处,控件行只留数据与按钮 -->
    <template #titleExtra>
      <el-select v-if="!matchId && !play.active" v-model="size" class="bf-size-select" :teleported="!isFullscreen" @change="switchSize">
        <el-option v-for="s in BOARD_SIZES" :key="s" :value="s" :label="s + '×' + s" />
      </el-select>
    </template>
    <template #rules="{ page }">
      <template v-if="page === 0">
        <p>{{ $t('games.g2048.tip') }}</p>
        <ul>
          <li>{{ $t('games.g2048.rules.play1') }}</li>
          <li>{{ $t('games.g2048.rules.play2') }}</li>
          <li>{{ $t('games.g2048.rules.play3') }}</li>
        </ul>
      </template>
      <ul v-else>
        <li>{{ $t('games.g2048.rules.ctrl1') }}</li>
        <li>{{ $t('games.g2048.rules.ctrl2') }}</li>
        <li>{{ $t('games.g2048.rules.ctrl3') }}</li>
        <li>{{ $t('games.g2048.rules.ctrl4') }}</li>
      </ul>
    </template>

    <template #extras>
      <GameRoomBar
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

      <!-- 对战横幅:同种子赛,对手信息与结算结果 -->
      <div v-if="matchId" class="match-banner">
        <span>{{ $t('games.match.vs', { opponent: opponentName }) }}</span>
        <span class="match-state">{{ matchStateText }}</span>
        <span v-if="matchResultText" class="match-result">{{ matchResultText }}</span>
      </div>
    </template>

    <template #overlay>
      <GameScoreShare
        v-model="shareOpen"
        :title="title"
        :stats="shareStats"
        :tiles="tiles"
        :grid-size="size"
        :tile-color="tileColor"
        :append-to-body="!isFullscreen"
        file-base="ihomy-2048"
      />
    </template>

    <!-- 分数贴棋盘:与棋盘同宽的细条,左对齐排在棋盘上方;点一下出分享图。
         撤销/新游戏放在这条的右端(棋盘右上角);手机横屏时整条改成棋盘左侧一列,
         按钮并排贴棋盘一侧,下面是最高分、分数(芯片各带类名,横屏按类名重排) -->
    <div class="bf-layout">
      <div class="bf-meta">
        <span v-a11y-click class="gh-chip is-shareable bf-chip-score" :title="$t('games.share.chipHint')" @click="shareOpen = true"
          ><span>{{ $t('games.g2048.score') }}</span
          ><b>{{ score }}</b></span
        >
        <span v-a11y-click class="gh-chip is-shareable bf-chip-best" :title="$t('games.share.chipHint')" @click="shareOpen = true"
          ><span>{{ $t('games.g2048.best') }}</span
          ><b>{{ best }}</b></span
        >
        <div class="bf-meta-actions">
          <el-button
            v-if="!matchId && !play.active"
            size="small"
            class="gs-icon-btn bf-undo"
            :disabled="!history.length"
            :title="$t('games.g2048.undo')"
            :aria-label="$t('games.g2048.undo')"
            @click="undo"
          >
            <el-icon><RefreshLeft /></el-icon>
          </el-button>
          <el-button
            v-if="!matchId"
            size="small"
            class="gs-icon-btn bf-new"
            :title="$t('games.g2048.newGame')"
            :aria-label="$t('games.g2048.newGame')"
            @click="startNew"
          >
            <el-icon><Plus /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="bf-stage" @contextmenu.prevent @dblclick.prevent>
        <div class="bf-board" :class="'bf-size-' + size" @touchstart="onTouchStart" @touchend="onTouchEnd">
          <div class="bf-playfield" :style="playfieldStyle">
            <div v-for="i in size * size" :key="i" class="bf-slot"></div>
            <div
              v-for="tile in tiles"
              :key="tile.id"
              class="bf-tile"
              :class="[
                'bf-tile-' + Math.min(tile.value, 4096),
                { new: newIds.has(tile.id), merged: mergedIds.has(tile.id), 'len-long': tile.value >= 1000 },
              ]"
              :style="tileStyle(tile)"
            >
              {{ tile.value }}
            </div>
          </div>
        </div>

        <Transition name="game-overlay">
          <div v-if="won || gameOver" class="bf-overlay game-overlay">
            <div class="bf-overlay-card game-overlay-card">
              <div class="bf-overlay-icon">{{ won ? '🏆' : '💤' }}</div>
              <div class="bf-overlay-title">
                {{ won ? $t('games.g2048.win') : $t('games.g2048.lose') }}
              </div>
              <div class="bf-overlay-meta">{{ $t('games.g2048.meta', { score }) }}</div>
              <div v-if="won && rewardMsg" class="bf-overlay-reward">{{ rewardMsg }}</div>
              <div class="bf-overlay-actions">
                <el-button v-if="won" type="primary" @click="keepPlaying">{{ $t('games.g2048.continue') }}</el-button>
                <el-button v-if="won" @click="startNew">{{ $t('games.g2048.playAgain') }}</el-button>
                <el-button v-else type="primary" @click="startNew">{{ $t('games.g2048.playAgain') }}</el-button>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
// 2048:经典数字合并小游戏。移动/合并/落子/胜负全在 utils/games/game2048.js(纯函数,单测覆盖),
// 本页负责方块定位与动画、键盘/滑动输入、最高分与本局进度存档;达成 2048 按积分规则发奖励。
// 列宽模式 4~8 各自独立存档与最高分;4 列沿用旧键,老玩家的进度与最高分不丢。
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Plus, RefreshLeft } from '@element-plus/icons-vue'
import { gameApi, matchApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import GameScoreShare from '@/components/games/GameScoreShare.vue'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { BOARD_SIZES, createGame, hasWon, isGameOver, isValidTiles, move, spawnTile } from '@/utils/games/game2048'
import { seededRng } from '@/utils/games/seededRng'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)

// ---------- 「一起玩」房间(?room=<id>):同 seed 各自跑,终局/交卷上报 ----------
const play = useGameRoomPlay()
/** 房间局返回房间大厅;单机回小游戏列表 */
const goBack = () => {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else router.push('/games')
}

// ---------- 对战模式(?match=<id>):同种子赛,禁存档/撤销/列宽/奖励,终局自动上报 ----------
const matchId = computed(() => Number(route.query.match) || 0)
const matchInfo = ref(null)
const matchRng = ref(null)
const matchResult = ref(null)
const matchReported = ref(false)

const opponentName = computed(() => {
  const m = matchInfo.value
  if (!m) return '?'
  return userStore.userInfo?.id === m.hostId ? m.guestName : m.hostName
})
const matchStateText = computed(() => {
  const m = matchInfo.value
  if (!m) return ''
  const key = {
    WAITING: 'waiting',
    PLAYING: 'playing',
    FINISHED: 'finished',
    CANCELLED: 'cancelled',
  }
  return t(`games.match.status.${key[m.status] || 'playing'}`)
})
const matchResultText = computed(() => {
  const m = matchResult.value?.match || (matchInfo.value?.status === 'FINISHED' ? matchInfo.value : null)
  if (!m || m.status !== 'FINISHED') return ''
  const mine = userStore.userInfo?.id === m.hostId ? m.hostScore : m.guestScore
  const other = userStore.userInfo?.id === m.hostId ? m.guestScore : m.hostScore
  const awarded = matchResult.value?.awarded || 0
  const win =
    m.winnerId == null ? t('games.match.draw') : m.winnerId === userStore.userInfo?.id ? t('games.match.youWin') : t('games.match.youLose')
  return `${mine} : ${other} · ${win}${awarded > 0 ? ' · +' + awarded : ''}`
})

const reportMatchScore = async () => {
  if (!matchId.value || matchReported.value) return
  matchReported.value = true
  try {
    const res = await matchApi.finish(matchId.value, score.value)
    matchResult.value = res
    matchInfo.value = res.match
  } catch {
    matchResult.value = null
  }
}

const MAX_HISTORY = 20

// 外壳自己管全屏/宽屏/一屏适配;页面只读它的状态(全屏时下拉要留在全屏元素内)
const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)
const title = computed(() => '2048' + (matchId.value ? ' · ' + t('games.match.tag') : play.active ? ' · ' + t('games.room.together') : ''))

const size = ref(4)
const tiles = ref([])
const score = ref(0)
const best = ref(0)
const achieved = ref(false)
const won = ref(false)
const rewardMsg = ref('')
const history = ref([])
const newIds = ref(new Set())
const mergedIds = ref(new Set())

const gameOver = computed(() => isGameOver(tiles.value, size.value))

// 终局自动上报:旧 1v1 与「一起玩」都走这里(观测量在 gameOver 定义之后才注册,避免 TDZ)
watch(gameOver, (over) => {
  if (!over) return
  if (matchId.value) reportMatchScore()
  if (play.active) play.report(score.value)
})

const submitRoomScore = () => play.report(score.value)

// 4 列沿用旧键,其余列宽各用独立键
const bestKey = () => (size.value === 4 ? 'ihomy:games:2048:best:v1' : `ihomy:games:2048:best:${size.value}:v1`)
const stateKey = () => (size.value === 4 ? 'ihomy:games:2048:state:v1' : `ihomy:games:2048:state:${size.value}:v1`)

const playfieldStyle = computed(() => ({
  gridTemplateColumns: `repeat(${size.value}, 1fr)`,
  gridTemplateRows: `repeat(${size.value}, 1fr)`,
}))

// ---------- 战绩分享图(点分数/最高分触发) ----------
const shareOpen = ref(false)
const shareStats = computed(() => [
  { label: t('games.g2048.score'), value: score.value },
  { label: t('games.g2048.best'), value: best.value },
])
// Canvas 读不到样式表:分享图沿用棋盘的方块色板(与 .bf-tile-* 同一份色值)
const TILE_PALETTE = {
  2: ['#eee4da', '#776e65'],
  4: ['#ede0c8', '#776e65'],
  8: ['#f2b179', '#f9f6f2'],
  16: ['#f59563', '#f9f6f2'],
  32: ['#f67c5f', '#f9f6f2'],
  64: ['#f65e3b', '#f9f6f2'],
  128: ['#edcf72', '#f9f6f2'],
  256: ['#edcc61', '#f9f6f2'],
  512: ['#edc850', '#f9f6f2'],
  1024: ['#edc53f', '#f9f6f2'],
  2048: ['#edc22e', '#f9f6f2'],
}
const tileColor = (value) => {
  const pair = TILE_PALETTE[value]
  return pair ? { bg: pair[0], fg: pair[1] } : { bg: '#3c3a32', fg: '#f9f6f2' }
}

const tileStyle = (tile) => {
  const n = size.value
  const cell = `calc((100% - ${n - 1} * var(--bf-gap)) / ${n})`
  // translate 的百分比按方块自身尺寸解析:每格步长 = 方块宽 + 间距
  const offset = (i) => `calc(${i} * (100% + var(--bf-gap)))`
  return {
    width: cell,
    height: cell,
    transform: `translate(${offset(tile.col)}, ${offset(tile.row)})`,
  }
}

const flash = (target, ids) => {
  if (!ids.length) return
  target.value = new Set(ids)
  setTimeout(() => {
    target.value = new Set()
  }, 180)
}

// ---------- 存档 ----------
const loadBest = () => {
  try {
    const n = Number(localStorage.getItem(bestKey()))
    best.value = Number.isFinite(n) && n > 0 ? Math.floor(n) : 0
  } catch (e) {
    best.value = 0
  }
}
const saveBest = () => {
  try {
    localStorage.setItem(bestKey(), String(best.value))
  } catch (e) {
    // 隐私模式忽略
  }
}
const saveState = () => {
  if (matchId.value || play.active) return
  try {
    localStorage.setItem(stateKey(), JSON.stringify({ tiles: tiles.value, score: score.value, achieved: achieved.value }))
  } catch (e) {
    // 忽略
  }
}
const clearState = () => {
  if (matchId.value || play.active) return
  try {
    localStorage.removeItem(stateKey())
  } catch (e) {
    // 忽略
  }
}
const loadState = () => {
  if (matchId.value || play.active) return null
  try {
    const raw = JSON.parse(localStorage.getItem(stateKey()) || 'null')
    if (!raw || !isValidTiles(raw.tiles, size.value) || !raw.tiles.length) return null
    if (!Number.isInteger(raw.score) || raw.score < 0) return null
    return raw
  } catch (e) {
    return null
  }
}

// ---------- 游戏流程 ----------
const startNew = () => {
  // 对战/房间模式:从 seed 重建随机序列(首只方块即由该链生成)
  const runSeed = matchId.value ? (matchInfo.value?.seed ?? 0) : play.active ? play.seed : 0
  matchRng.value = runSeed ? seededRng(runSeed) : null
  tiles.value = createGame(matchRng.value ?? Math.random, size.value)
  score.value = 0
  achieved.value = false
  won.value = false
  rewardMsg.value = ''
  history.value = []
  newIds.value = new Set()
  mergedIds.value = new Set()
  clearState()
}

// 切换列宽 = 切到另一份独立存档:当前局面已随每次移动落盘,这里装载新尺寸的档(没有就开新局)
const switchSize = () => {
  loadBest()
  history.value = []
  newIds.value = new Set()
  mergedIds.value = new Set()
  won.value = false
  rewardMsg.value = ''
  const saved = loadState()
  if (saved) {
    tiles.value = saved.tiles
    score.value = saved.score
    achieved.value = !!saved.achieved
    if (hasWon(saved.tiles)) achieved.value = true
  } else {
    startNew()
  }
}

const keepPlaying = () => {
  won.value = false
}

const tryMove = (direction) => {
  if (won.value || gameOver.value) return
  const res = move(tiles.value, direction, size.value)
  if (!res.moved) return

  history.value.push({
    tiles: tiles.value.map((t) => ({ ...t })),
    score: score.value,
    achieved: achieved.value,
  })
  if (history.value.length > MAX_HISTORY) history.value.shift()

  tiles.value = res.tiles
  score.value += res.gained
  flash(mergedIds, res.mergedIds)

  const spawned = spawnTile(tiles.value, matchRng.value ?? Math.random, size.value)
  if (spawned) {
    tiles.value = [...tiles.value, spawned]
    flash(newIds, [spawned.id])
  }

  if (!achieved.value && hasWon(tiles.value)) {
    achieved.value = true
    won.value = true
    grantReward()
  }
  if (score.value > best.value) {
    best.value = score.value
    saveBest()
  }
  if (isGameOver(tiles.value, size.value)) clearState()
  else saveState()
}

const undo = () => {
  if (won.value || matchId.value || play.active || !history.value.length) return
  const last = history.value.pop()
  tiles.value = last.tiles
  score.value = last.score
  achieved.value = last.achieved
  saveState()
}

const grantReward = async () => {
  if (matchId.value || play.active) return
  try {
    const res = await gameApi.game2048Reward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.rewarded', { n: res.awarded })}（${t('points.points')} ${res.balance}）`
        : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
}

// ---------- 键盘 / 触摸 ----------
const KEY_DIRECTION = {
  ArrowLeft: 'left',
  ArrowRight: 'right',
  ArrowUp: 'up',
  ArrowDown: 'down',
  a: 'left',
  d: 'right',
  w: 'up',
  s: 'down',
  A: 'left',
  D: 'right',
  W: 'up',
  S: 'down',
}

const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  const direction = KEY_DIRECTION[event.key]
  if (!direction) return
  event.preventDefault()
  tryMove(direction)
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
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return
  tryMove(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : dy > 0 ? 'down' : 'up')
}

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  sunLight?.suspendEffects()
  loadBest()

  if (matchId.value) {
    // 对战:拉详情(取 seed)后开同种子局;终局由 gameOver watch 自动上报
    try {
      matchInfo.value = await matchApi.get(matchId.value)
    } catch {
      matchInfo.value = null
    }
    startNew()
    return
  }

  if (play.active) {
    // 房间:拉房间取 seed(等房间时间超过 seed 落地),再开同种子局
    await play.load()
    startNew()
    return
  }

  const saved = loadState()
  if (saved) {
    tiles.value = saved.tiles
    score.value = saved.score
    achieved.value = !!saved.achieved
    if (hasWon(saved.tiles)) achieved.value = true
  } else {
    startNew()
  }
})

onBeforeUnmount(() => {
  if (!gameOver.value) saveState()
  document.removeEventListener('keydown', onKeydown)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 对战横幅 */
.match-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 12px;
  margin-bottom: 10px;
  border: 1px dashed rgba(var(--color-brand-rgb), 0.4);
  border-radius: 10px;
  background: rgba(var(--color-brand-rgb), 0.06);
  font-size: 13px;
}
.match-state {
  color: var(--color-text-secondary, #888);
}
.match-result {
  font-weight: 600;
  color: var(--color-brand-text);
}
/* 棋盘区 = 分数条 + 棋盘,共用一列宽(分数按列宽左对齐,紧贴棋盘左上角)。
   棋盘边长取「这一列的宽 / 游戏区剩余高 / 上限」三者的最小值,各让 8px;
   扣高度时先减去分数条与它和棋盘之间的间距,整块才装得进游戏区,横竖屏同一口径 */
.bf-layout {
  --bf-meta-h: calc(var(--gs-control-h) + 8px);
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: min(calc(100% - 16px), calc(100cqh - var(--bf-meta-h) - 16px), 760px);
}
.bf-shell:fullscreen .bf-layout {
  width: min(calc(100% - 16px), calc(100cqh - var(--bf-meta-h) - 16px), 900px);
}
/* 分数/最高分:固定一条控制行高,点一下出分享图(芯片样式沿用 .gh-chip) */
.bf-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  height: var(--gs-control-h);
  min-width: 0;
}
/* 撤销/新游戏靠这条的右端;横屏时这条在棋盘左上,按钮会挪到分数上面 */
.bf-meta-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 0 0 auto;
  margin-left: auto;
}
/* 手机横屏:整条挪到棋盘左侧,上面是并排的撤销/新游戏(贴棋盘一侧,和扫雷一致),
   下面是 最高分 → 分数。棋盘边长 = 游戏区高 − 6(上下各 ~3),
   底边离页面底边只剩「外壳底边距(6,手机上叠安全区) + 3」 */
@media (orientation: landscape) and (max-height: 520px) {
  .bf-layout {
    --bf-meta-w: 132px;
    display: grid;
    grid-template-columns: var(--bf-meta-w) minmax(0, 1fr);
    align-items: start;
    column-gap: 10px;
    row-gap: 0;
    width: min(calc(100% - 16px), calc(100cqh - 6px + var(--bf-meta-w) + 10px));
  }
  .bf-meta {
    flex-direction: column;
    align-items: stretch;
    justify-content: start;
    height: auto;
    gap: 8px;
  }
  /* 芯片撑满这一列宽,标签靠左、数值靠右,和扫雷左列同一读法 */
  .bf-meta > .gh-chip {
    justify-content: space-between;
    min-height: var(--gs-control-h);
  }
  /* 按钮并排靠棋盘一侧,免得按钮和棋盘之间空一截 */
  .bf-meta-actions {
    order: -1;
    justify-content: flex-end;
    margin-left: 0;
  }
  /* EP 给相邻按钮加 margin-left:12px,这里间距只走 gap */
  .bf-meta-actions .el-button + .el-button {
    margin-left: 0;
  }
  .bf-chip-best {
    order: 2;
  }
  .bf-chip-score {
    order: 3;
  }
  .bf-stage {
    width: 100%;
  }
}
.bf-stage {
  position: relative;
  flex: 0 0 auto;
  width: 100%;
  aspect-ratio: 1;
  --bf-gap: clamp(6px, 2vw, 10px);
}

.bf-size-select {
  width: 88px;
}

.bf-board {
  width: 100%;
  height: 100%;
  padding: var(--bf-gap);
  border-radius: 10px;
  background: var(--color-card-2);
  box-shadow: inset 0 2px 10px rgba(0, 0, 0, 0.14);
  touch-action: none;
  user-select: none;
  contain: layout style;
}
.bf-playfield {
  position: relative;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: repeat(4, 1fr);
  gap: var(--bf-gap);
  width: 100%;
  height: 100%;
}
.bf-slot {
  border-radius: 6px;
  background: rgba(var(--color-card-rgb), 0.35);
}
.bf-tile {
  position: absolute;
  top: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-weight: 800;
  font-size: clamp(20px, 6vw, 38px);
  line-height: 1;
  color: #776e65;
  background: var(--color-surface);
  transition: transform 0.13s ease;
  will-change: transform;
}
.bf-tile.len-long {
  font-size: clamp(15px, 4.4vw, 28px);
}
/* 大棋盘字号随列宽递减,8 列下四位数字也不出格 */
.bf-size-5 .bf-tile {
  font-size: clamp(17px, 5vw, 31px);
}
.bf-size-5 .bf-tile.len-long {
  font-size: clamp(13px, 3.8vw, 24px);
}
.bf-size-6 .bf-tile {
  font-size: clamp(14px, 4.2vw, 26px);
}
.bf-size-6 .bf-tile.len-long {
  font-size: clamp(11px, 3vw, 19px);
}
.bf-size-7 .bf-tile {
  font-size: clamp(12px, 3.6vw, 22px);
}
.bf-size-7 .bf-tile.len-long {
  font-size: clamp(10px, 2.6vw, 16px);
}
.bf-size-8 .bf-tile {
  font-size: clamp(10px, 3.2vw, 19px);
}
.bf-size-8 .bf-tile.len-long {
  font-size: clamp(8px, 2.4vw, 14px);
}
.bf-tile.new {
  animation: bf-pop 0.16s ease;
}
.bf-tile.merged {
  animation: bf-merge 0.18s ease;
}
@keyframes bf-pop {
  from {
    transform-origin: center;
    scale: 0.35;
  }
}
@keyframes bf-merge {
  50% {
    scale: 1.14;
  }
}

.bf-tile-2 {
  background: #eee4da;
}
.bf-tile-4 {
  background: #ede0c8;
}
.bf-tile-8 {
  background: #f2b179;
  color: #f9f6f2;
}
.bf-tile-16 {
  background: #f59563;
  color: #f9f6f2;
}
.bf-tile-32 {
  background: #f67c5f;
  color: #f9f6f2;
}
.bf-tile-64 {
  background: #f65e3b;
  color: #f9f6f2;
}
.bf-tile-128 {
  background: #edcf72;
  color: #f9f6f2;
}
.bf-tile-256 {
  background: #edcc61;
  color: #f9f6f2;
}
.bf-tile-512 {
  background: #edc850;
  color: #f9f6f2;
}
.bf-tile-1024 {
  background: #edc53f;
  color: #f9f6f2;
}
.bf-tile-2048 {
  background: #edc22e;
  color: #f9f6f2;
  box-shadow: 0 0 22px rgba(237, 194, 46, 0.55);
}
.bf-tile-4096 {
  background: #3c3a32;
  color: #f9f6f2;
}

.bf-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: rgba(250, 248, 239, 0.72);
  backdrop-filter: blur(2px);
  z-index: 5;
}
.bf-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}
.bf-overlay-icon {
  font-size: 38px;
}
.bf-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.bf-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.bf-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}
.bf-overlay-actions {
  display: flex;
  gap: 8px;
}

html.dark .bf-overlay {
  background: rgba(20, 20, 20, 0.72);
}
</style>
