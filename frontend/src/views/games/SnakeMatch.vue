<!-- 贪吃蛇 1v1 实时对战:双端同 seed 确定性模拟(共享食物/尸体留场),
     本地输入即时上屏并经 WS game-signal 转发;一方跑结束后等对方,双方都结束按比分结算。
     状态机与判定在 utils/games/snakeMatch.js(纯函数),本页只做渲染/转发/上报 -->
<template>
  <div ref="pageEl" class="page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions
        :is-fullscreen="isFullscreen"
        :wide="isWide"
        @back="router.push(`/games/match/${matchId}`)"
        @fullscreen="toggleFullscreen"
        @wide="toggleWide"
      />
    </PageToolbar>

    <div ref="gameCardEl" class="duel-card card game-surface" @contextmenu.prevent @dblclick.prevent>
      <div class="duel-head">
        <div class="duel-side" :class="{ dead: match.host.status !== STATUS.PLAYING }">
          <span class="duel-dot host"></span>{{ match.hostName || $t('games.match.host') }}
          <b class="duel-score">{{ match.host.score }}</b>
        </div>
        <div class="duel-title">{{ $t('games.match.tag') }}</div>
        <div class="duel-side right" :class="{ dead: match.guest.status !== STATUS.PLAYING }">
          <b class="duel-score">{{ match.guest.score }}</b>
          {{ match.guestName || $t('games.match.guest') }}<span class="duel-dot guest"></span>
        </div>
      </div>

      <div class="duel-stage" @touchstart="onTouchStart" @touchend="onTouchEnd">
        <canvas ref="canvasEl" class="duel-canvas"></canvas>
        <div v-if="!started" class="duel-mask">
          <el-button type="primary" round size="large" @click="begin">{{ $t('games.match.startDuel') }}</el-button>
        </div>
        <div v-else-if="waitingOpponent" class="duel-mask">
          <div class="duel-wait">{{ $t('games.match.waitForOpponent') }}</div>
        </div>
        <div v-else-if="match.over" class="duel-mask">
          <div class="duel-wait">{{ outcomeText }}</div>
          <el-button round @click="router.push(`/games/match/${matchId}`)">{{ $t('games.match.backToDetail') }}</el-button>
        </div>
      </div>

      <div class="duel-hint">{{ $t('games.snake.duelTip') }}</div>
    </div>
  </div>
</template>

<script setup>
// 对战页:同 seed 建局;键盘/触摸转向(自己)+ WS 收对端转向;tick 由本地驱动(两端各跑同一模拟)。
// 一方结束即上报 finish(服务端幂等),双方上报后终局出结果。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { matchApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useGameView } from '@/composables/useGameView'
import { applyInput, createMatch, stepMatch, HOST, GUEST, STATUS } from '@/utils/games/snakeMatch'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'

const TICK_MS = 150

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const chatStore = useChatStore()

const matchId = computed(() => Number(route.params.id) || 0)
const match = ref(createMatch(0))
const started = ref(false)
const reported = ref(false)
const offHandler = ref(null)

const pageEl = ref(null)
const gameCardEl = ref(null)
const canvasEl = ref(null)
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: gameCardEl })

const iAmHost = computed(() => match.value.hostId === userStore.userInfo?.id)
const mySide = computed(() => (iAmHost.value ? HOST : GUEST))
const myState = computed(() => (iAmHost.value ? match.value.host : match.value.guest))
const waitingOpponent = computed(() => started.value && myState.value.status !== STATUS.PLAYING && !match.value.over)

const outcomeText = computed(() => {
  const m = match.value
  if (m.winner == null) return t('games.match.draw')
  const winnerName = m.winner === HOST ? m.hostName : m.guestName
  return m.winner === mySide.value
    ? t('games.match.youWin')
    : t('games.match.winnerIs', { name: winnerName || (m.winner === HOST ? t('games.match.host') : t('games.match.guest')) })
})

// WS 对战信令:对端转向注入本端模拟
const attachSignal = () => {
  detachSignal()
  offHandler.value = chatStore.onGameSignal?.((d) => {
    if (!d || Number(d.matchId) !== matchId.value || d.event !== 'dir') return
    match.value = applyInput(match.value, d.from === match.value.hostId ? HOST : GUEST, d.dir)
  })
}

const detachSignal = () => {
  if (offHandler.value) {
    offHandler.value()
    offHandler.value = null
  }
}

const sendDir = (dir) => {
  chatStore.sendSignal?.({ type: 'game-signal', data: { matchId: matchId.value, event: 'dir', dir } })
}

const begin = () => {
  started.value = true
  attachSignal()
  startTicker()
}

let ticker = null
const startTicker = () => {
  stopTicker()
  ticker = setInterval(() => {
    match.value = stepMatch(match.value)
    render()
    checkReport()
  }, TICK_MS)
}
const stopTicker = () => {
  if (ticker) clearInterval(ticker)
  ticker = null
}

// 本地转向:立即生效并转发;死后不再发
const turn = (dir) => {
  if (!started.value || myState.value.status !== STATUS.PLAYING) return
  match.value = applyInput(match.value, mySide.value, dir)
  sendDir(dir)
}

const KEY_DIR = {
  ArrowUp: 'up',
  ArrowDown: 'down',
  ArrowLeft: 'left',
  ArrowRight: 'right',
  w: 'up',
  s: 'down',
  a: 'left',
  d: 'right',
}
const onKeydown = (e) => {
  const dir = KEY_DIR[e.key]
  if (!dir) return
  e.preventDefault()
  turn(dir)
}

// 触摸滑动转向(游戏页手势:轻扫即转,不做页面滚动)
let touchStart = null
const onTouchStart = (e) => {
  touchStart = e.touches[0]
}
const onTouchEnd = (e) => {
  if (!touchStart) return
  const dx = e.changedTouches[0].clientX - touchStart.clientX
  const dy = e.changedTouches[0].clientY - touchStart.clientY
  touchStart = null
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return
  turn(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : dy > 0 ? 'down' : 'up')
}

/** 我跑结束就上报一次(服务端幂等);双方都报完对方详情页出结果 */
const checkReport = async () => {
  if (reported.value || myState.value.status === STATUS.PLAYING) return
  reported.value = true
  stopTicker()
  try {
    const res = await matchApi.finish(matchId.value, myState.value.score)
    if (res?.match) {
      match.value = { ...match.value, over: res.match.status === 'FINISHED', winner: null }
    }
  } catch {
    /* 上报失败保留本地结果,详情页可手动重试 */
  }
}

// ---------- 渲染 ----------
const render = () => {
  const canvas = canvasEl.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const m = match.value
  const size = canvas.width
  const cell = size / m.cols
  ctx.clearRect(0, 0, size, size)
  // 背景
  ctx.fillStyle = 'rgba(128,128,128,0.06)'
  ctx.fillRect(0, 0, size, size)
  // 共享食物
  const food = m.host.food
  if (food) {
    ctx.fillStyle = '#e2574c'
    ctx.beginPath()
    ctx.arc((food.x + 0.5) * cell, (food.y + 0.5) * cell, cell * 0.32, 0, Math.PI * 2)
    ctx.fill()
  }
  const drawSnake = (state, color, dead) => {
    ctx.fillStyle = dead ? 'rgba(128,128,128,0.4)' : color
    for (const c of state.snake) {
      const pad = cell * 0.08
      ctx.beginPath()
      ctx.roundRect(c.x * cell + pad, c.y * cell + pad, cell - pad * 2, cell - pad * 2, cell * 0.22)
      ctx.fill()
    }
  }
  drawSnake(m.guest, '#e8912d', m.guest.status !== STATUS.PLAYING)
  const hostColor = getComputedStyle(document.documentElement).getPropertyValue('--color-brand-text').trim() || '#6f5bd8'
  drawSnake(m.host, hostColor, m.host.status !== STATUS.PLAYING)
}

const fitCanvas = () => {
  const canvas = canvasEl.value
  if (!canvas) return
  const stage = canvas.parentElement
  const side = Math.max(200, Math.min(stage.clientWidth, 520))
  canvas.width = side
  canvas.height = side
  render()
}

const resizeObserver = ref(null)
onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', fitCanvas)
  try {
    const m = await matchApi.get(matchId.value)
    match.value = {
      ...createMatch(m.seed),
      hostName: m.hostName,
      guestName: m.guestName,
      hostId: m.hostId,
      guestId: m.guestId,
      status: m.status,
    }
    // 尚未开赛(没到对战页就先来这):直接可玩,服务端 PLAYING 已由加入动作置位
    chatStore.ensureConnected()
    setTimeout(fitCanvas)
    resizeObserver.value = new ResizeObserver(fitCanvas)
    resizeObserver.value.observe(gameCardEl.value)
  } catch {
    ElMessage.warning(t('games.match.notFound'))
    router.push('/games')
  }
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('resize', fitCanvas)
  detachSignal()
  stopTicker()
  resizeObserver.value?.disconnect()
  // 离开页时若我还没结束:按当前分数上报(切页面=放弃,成绩仍有效)
  if (started.value && !reported.value && myState.value.status === STATUS.PLAYING) {
    matchApi.finish(matchId.value, myState.value.score).catch(() => {})
  }
})

watch(started, (v) => {
  if (v) fitCanvas()
})
</script>

<style scoped>
.duel-card {
  position: relative;
  max-width: 640px;
  margin: 0 auto;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.duel-head {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 14px;
}
.duel-side {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.duel-side.right {
  justify-content: flex-end;
}
.duel-side.dead {
  opacity: 0.55;
}
.duel-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.duel-dot.host {
  background: var(--color-brand-text);
}
.duel-dot.guest {
  background: #e8912d;
}
.duel-score {
  font-size: 18px;
}
.duel-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-text-secondary, #888);
}
.duel-stage {
  position: relative;
  width: 100%;
  display: flex;
  justify-content: center;
}
.duel-canvas {
  border-radius: 12px;
  background: var(--color-bg-2);
  touch-action: none;
}
.duel-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.72);
}
html.dark .duel-mask {
  background: rgba(10, 12, 16, 0.72);
}
.duel-wait {
  font-size: 16px;
  font-weight: 600;
}
.duel-hint {
  font-size: 12px;
  color: var(--color-text-tertiary, #aaa);
}
</style>
