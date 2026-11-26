<template>
  <GameShell
    ref="shellEl"
    class="bk-shell"
    :title="$t('games.bakery.title')"
    :rules-pages="[$t('games.bakery.rules.pagePlay'), $t('games.bakery.rules.pageNum')]"
    body-align="stretch"
    @back="goBack"
  >
    <template #stats>
      <span class="gh-chip"
        ><span>{{ $t('games.bakery.coins') }}</span
        ><b>{{ compact(state.coins) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.bakery.netWorth') }}</span
        ><b>{{ compact(worth) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.bakery.perSecond') }}</span
        ><b>{{ compact(perSecond) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.bakery.stores') }}</span
        ><b>{{ state.stores }}</b></span
      >
    </template>

    <template #actions>
      <el-button size="small" round :title="$t('games.bakery.ranking.title')" @click="openRanking">
        <el-icon><Trophy /></el-icon>
        <span class="bk-action-label">{{ $t('games.bakery.ranking.title') }}</span>
      </el-button>
    </template>

    <template #rules="{ page }">
      <ul v-if="page === 0">
        <li>{{ $t('games.bakery.rules.play1') }}</li>
        <li>{{ $t('games.bakery.rules.play2') }}</li>
        <li>{{ $t('games.bakery.rules.play3') }}</li>
        <li>{{ $t('games.bakery.rules.play4') }}</li>
      </ul>
      <ul v-else>
        <li>{{ $t('games.bakery.rules.num1') }}</li>
        <li>{{ $t('games.bakery.rules.num2') }}</li>
        <li>{{ $t('games.bakery.rules.num3') }}</li>
        <li>{{ $t('games.bakery.rules.num4') }}</li>
      </ul>
    </template>

    <template #overlay>
      <!-- 离线收益:离开超过两分钟才弹,数字与效率口径都摊开,玩家不用猜为什么少了 -->
      <el-dialog
        v-model="offlineOpen"
        class="dialog-sm"
        :append-to-body="!isFullscreen"
        :title="$t('games.bakery.offline.title')"
        :lock-scroll="false"
      >
        <p class="bk-dialog-lead">
          {{ $t('games.bakery.offline.body', { time: offlineTime, coins: compact(offlineInfo?.coins || 0) }) }}
        </p>
        <p v-if="offlineInfo && offlineInfo.efficiency < 1" class="bk-dialog-note">
          {{ offlineInfo.capped ? $t('games.bakery.offline.capped') : $t('games.bakery.offline.half') }}
        </p>
        <template #footer>
          <el-button type="primary" @click="offlineOpen = false">{{ $t('games.bakery.offline.ack') }}</el-button>
        </template>
      </el-dialog>

      <!-- 开分店:重置是不可逆的,先把「失去什么、换到什么」写清楚再确认 -->
      <el-dialog
        v-model="prestigeOpen"
        class="dialog-sm"
        :append-to-body="!isFullscreen"
        :title="$t('games.bakery.prestige.title')"
        :lock-scroll="false"
      >
        <p class="bk-dialog-lead">
          {{ $t('games.bakery.prestige.confirm', { next: compact(nextRequirement) }) }}
        </p>
        <p class="bk-dialog-note">{{ $t('games.bakery.prestige.bonus', { n: bonusPercent }) }}</p>
        <template #footer>
          <el-button @click="prestigeOpen = false">{{ $t('games.cancel') }}</el-button>
          <el-button type="primary" @click="onPrestige">{{ $t('games.bakery.prestige.action') }}</el-button>
        </template>
      </el-dialog>

      <el-dialog
        v-model="rankOpen"
        class="dialog-md"
        :append-to-body="!isFullscreen"
        :title="$t('games.bakery.ranking.title')"
        :lock-scroll="false"
      >
        <p class="bk-dialog-note">{{ $t('games.bakery.ranking.hint') }}</p>
        <div v-if="rankLoading" class="bk-rank-loading">
          <el-skeleton :rows="4" animated />
        </div>
        <p v-else-if="rankFailed" class="bk-dialog-note is-error">{{ $t('games.bakery.ranking.failed') }}</p>
        <p v-else-if="!rankRows.length" class="bk-dialog-note">{{ $t('games.bakery.ranking.empty') }}</p>
        <ol v-else class="bk-rank-list">
          <li v-for="(row, i) in rankRows" :key="row.userId" class="bk-rank-row" :class="{ 'is-me': row.isMe }">
            <span class="bk-rank-no">{{ row.rank || i + 1 }}</span>
            <span class="bk-rank-name">
              {{ row.name }}
              <em v-if="row.isMe">{{ $t('games.bakery.ranking.me') }}</em>
            </span>
            <b class="bk-rank-score">{{ compact(row.score) }}</b>
          </li>
        </ol>
        <template #footer>
          <el-button :loading="rankLoading" @click="openRanking">{{ $t('games.bakery.ranking.load') }}</el-button>
          <el-button type="primary" @click="rankOpen = false">{{ $t('games.rulesAck') }}</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- 左(竖屏上)= 现烤现卖与后厨库存,右(竖屏下)= 六条产线 -->
    <div class="bk-layout">
      <section class="bk-shop">
        <button type="button" class="bk-tap" :aria-label="$t('games.bakery.tap')" @click="onTap">
          <span class="bk-tap-art" aria-hidden="true">🍞</span>
          <span class="bk-tap-label">{{ $t('games.bakery.tap') }}</span>
          <Transition name="bk-pop" mode="out-in">
            <span v-if="popKey" :key="popKey" class="bk-tap-pop">+{{ tapGain }}</span>
          </Transition>
        </button>
        <div class="bk-stock">
          <span class="bk-stock-row">
            <span class="bk-stock-name">{{ $t('games.bakery.dough') }}</span>
            <b>{{ compact(state.dough) }}</b>
          </span>
          <span class="bk-stock-row">
            <span class="bk-stock-name">{{ $t('games.bakery.bread') }}</span>
            <b>{{ compact(state.bread) }}</b>
          </span>
          <!-- 三段产线的实时吞吐:卡在哪一段一眼能看出该升哪条线 -->
          <p class="bk-rates">
            <span>{{ $t('games.bakery.doughRate') }} {{ compact(doughRate) }}/s</span>
            <span>{{ $t('games.bakery.bakeRate') }} {{ compact(bakeRate) }}/s</span>
            <span>{{ $t('games.bakery.sellRate') }} {{ compact(sellRate) }}/s</span>
          </p>
        </div>
      </section>

      <section class="bk-side">
        <h2 class="section-label">{{ $t('games.bakery.upgrades') }}</h2>
        <div class="bk-up-list">
          <button
            v-for="up in upgrades"
            :key="up.key"
            type="button"
            class="bk-up"
            :class="{ 'is-afford': up.afford }"
            :disabled="!up.afford"
            @click="onBuy(up.key)"
          >
            <span class="bk-up-main">
              <span class="bk-up-name"
                >{{ up.name }}<em>{{ $t('games.bakery.level', { n: up.level }) }}</em></span
              >
              <span class="bk-up-fx">{{ up.effect }}</span>
            </span>
            <span class="bk-up-cost">
              <b>{{ compact(up.cost) }}</b>
              <i aria-hidden="true">🪙</i>
            </span>
          </button>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="bk-prestige">
        <i class="bk-prestige-bar"><b :style="{ width: prestigePercent + '%' }"></b></i>
        <div class="bk-prestige-row">
          <span class="bk-prestige-label">{{ $t('games.bakery.prestige.stores', { n: state.stores }) }}</span>
          <span class="bk-prestige-progress">
            {{ $t('games.bakery.prestige.progress', { now: compact(state.runCoins), need: compact(requirement) }) }}
          </span>
          <el-button size="small" type="primary" round :disabled="!canPrestigeNow" @click="openPrestige">
            {{ $t('games.bakery.prestige.action') }}
          </el-button>
        </div>
        <p v-if="rewardMsg" class="bk-reward">{{ rewardMsg }}</p>
      </div>
    </template>
  </GameShell>
</template>

<script setup>
// 小面包房(放置挂机):面团 → 面包 → 金币 → 升级产线 → 开分店。
// 数值与产线耦合全在 utils/games/bakeryIdle.js(纯函数,单测覆盖);本页只负责
// 每秒推进、存档、离线弹窗、家庭榜上报。进度只在本机,云端只有一个净资产数字。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Trophy } from '@element-plus/icons-vue'
import GameShell from '@/components/games/GameShell.vue'
import { gameApi } from '@/api'
import {
  OFFLINE_POPUP_SECONDS,
  PRESTIGE_FACTOR,
  STATE_KEY,
  STORE_BONUS,
  UPGRADE_DEFS,
  bakePerSecond,
  buyUpgrade,
  canPrestige,
  catchUp,
  clickPower,
  coinsPerSecond,
  createState,
  doughPerSecond,
  formatCompact,
  levelOf,
  netWorth,
  parseState,
  prestige,
  prestigeRequirement,
  sellPerSecond,
  serializeState,
  tapSell,
  tick,
  upgradeCost,
} from '@/utils/games/bakeryIdle'

/** 存档节流:点击/购买后 300ms 写一次,推进循环每 5 秒兜底一次 */
const SAVE_DELAY_MS = 300
const SAVE_EVERY_TICKS = 5
/** 上报节流:家庭榜按净资产覆盖更新,净值没变就不必打接口 */
const REPORT_INTERVAL_MS = 30000

const router = useRouter()
const { t, locale } = useI18n()

const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)

const offlineInfo = ref(null)
const offlineOpen = ref(false)
const prestigeOpen = ref(false)
const rewardMsg = ref('')

const rankOpen = ref(false)
const rankLoading = ref(false)
const rankFailed = ref(false)
const rankRows = ref([])

const popKey = ref(0)

const state = ref(bootstrap())

// ---------- 派生数值 ----------
const compact = (value) => formatCompact(value, locale.value)
const worth = computed(() => netWorth(state.value))
const perSecond = computed(() => coinsPerSecond(state.value))
const doughRate = computed(() => doughPerSecond(state.value))
const bakeRate = computed(() => bakePerSecond(state.value))
const sellRate = computed(() => sellPerSecond(state.value))
const tapGain = computed(() => compact(clickPower(state.value)))
const requirement = computed(() => prestigeRequirement(state.value))
const nextRequirement = computed(() => Math.floor(requirement.value * PRESTIGE_FACTOR))
const canPrestigeNow = computed(() => canPrestige(state.value))
const bonusPercent = computed(() => Math.round(STORE_BONUS * 100))
const prestigePercent = computed(() => Math.max(0, Math.min(100, Math.round((state.value.runCoins / requirement.value) * 100))))
const offlineTime = computed(() => formatDuration(offlineInfo.value?.elapsed || 0))

const upgrades = computed(() =>
  UPGRADE_DEFS.map((def) => {
    const cost = upgradeCost(state.value, def.key)
    return {
      key: def.key,
      name: t(`games.bakery.upgrade.${def.key}.name`),
      effect: t(`games.bakery.upgrade.${def.key}.effect`, {
        n: def.kind === 'MULT' ? Math.round(def.amount * 100) : def.amount,
      }),
      level: levelOf(state.value, def.key),
      cost,
      afford: state.value.coins >= cost,
    }
  }),
)

function formatDuration(seconds) {
  const total = Math.max(0, Math.floor(seconds))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (hours > 0) return t('games.bakery.duration.hourMin', { h: hours, m: minutes })
  if (minutes > 0) return t('games.bakery.duration.minute', { m: minutes })
  return t('games.bakery.duration.second', { s: total })
}

/** 离开一段时间回来:按夜班口径补算,并把「这段时间挣了多少金币」算出来给弹窗用 */
function resumeOffline(base) {
  const before = Number(base.coins) || 0
  const result = catchUp(base, Date.now())
  if (result.elapsed >= OFFLINE_POPUP_SECONDS) {
    offlineInfo.value = {
      elapsed: result.elapsed,
      coins: Math.max(0, result.state.coins - before),
      efficiency: result.efficiency,
      capped: result.capped,
    }
    offlineOpen.value = true
  }
  return result.state
}

// ---------- 存档 ----------
function bootstrap() {
  let loaded = null
  try {
    loaded = parseState(localStorage.getItem(STATE_KEY))
  } catch {
    // 隐私模式/坏档:按新档处理
  }
  return resumeOffline(loaded || createState())
}

let saveTimer = null
const flushSave = () => {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
  }
  try {
    localStorage.setItem(STATE_KEY, serializeState(state.value))
  } catch {
    // 隐私模式忽略
  }
}
const scheduleSave = () => {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(flushSave, SAVE_DELAY_MS)
}

// ---------- 家庭榜上报 ----------
let lastReportAt = 0
let lastReportedScore = -1
const submitScore = () => {
  const score = worth.value
  lastReportedScore = score
  lastReportAt = Date.now()
  return gameApi.bakeryScore(score).catch(() => null)
}
const reportIfChanged = (force = false) => {
  const score = worth.value
  const now = Date.now()
  if (!force && (score === lastReportedScore || now - lastReportAt < REPORT_INTERVAL_MS)) return
  submitScore()
}

// ---------- 推进循环 ----------
// 页面可见时按满效率推进(与离线补算共用 tick);切后台时停表,回来交给 catchUp 走夜班口径
let timer = null
let tickCount = 0
const advanceVisible = () => {
  const now = Date.now()
  const elapsed = Math.floor((now - (Number(state.value.lastTick) || now)) / 1000)
  if (elapsed <= 0) return
  state.value = { ...tick(state.value, elapsed), lastTick: now }
  reportIfChanged()
}
const startTimer = () => {
  if (timer) return
  timer = setInterval(() => {
    advanceVisible()
    tickCount += 1
    if (tickCount % SAVE_EVERY_TICKS === 0) flushSave()
  }, 1000)
}
const stopTimer = () => {
  if (!timer) return
  clearInterval(timer)
  timer = null
}

// ---------- 操作 ----------
const onTap = () => {
  state.value = tapSell(state.value)
  popKey.value += 1
  scheduleSave()
  reportIfChanged()
}

const onBuy = (key) => {
  const result = buyUpgrade(state.value, key)
  if (!result.bought) return
  state.value = { ...result.state, lastTick: Date.now() }
  scheduleSave()
}

const openPrestige = () => {
  if (!canPrestigeNow.value) return
  prestigeOpen.value = true
}

const onPrestige = () => {
  const result = prestige(state.value, Date.now())
  prestigeOpen.value = false
  if (!result.ok) return
  state.value = result.state
  flushSave()
  submitScore()
  grantReward()
}

const grantReward = async () => {
  try {
    const res = await gameApi.bakeryReward()
    const percent = Math.round((Math.pow(1.15, state.value.stores) - 1) * 100)
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.bakery.prestige.done', { n: percent })} ${t('games.rewarded', { n: res.awarded })}`
        : `${t('games.bakery.prestige.done', { n: percent })} ${t('games.rewardCapped')}`
  } catch {
    rewardMsg.value = t('games.rewardFailed')
  }
  setTimeout(() => {
    rewardMsg.value = ''
  }, 8000)
}

const openRanking = async () => {
  rankOpen.value = true
  rankLoading.value = true
  rankFailed.value = false
  try {
    await submitScore()
    const res = await gameApi.bakeryRanking()
    rankRows.value = Array.isArray(res?.ranking) ? res.ranking : []
  } catch {
    rankFailed.value = true
    rankRows.value = []
  } finally {
    rankLoading.value = false
  }
}

const goBack = () => {
  flushSave()
  router.push('/games')
}

// ---------- 生命周期 ----------
const onVisibility = () => {
  if (document.hidden) {
    advanceVisible()
    flushSave()
    stopTimer()
    return
  }
  state.value = resumeOffline(state.value)
  startTimer()
  reportIfChanged(true)
}

onMounted(() => {
  startTimer()
  reportIfChanged(true)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibility)
  advanceVisible()
  stopTimer()
  flushSave()
})
</script>

<style scoped>
/* 两栏只在游戏区够宽时成立(手机横屏右栏约 630px 刚好够);窄了退回上下排,
   整块可滚动 —— 现烤现卖按钮始终在视线里,产线列表往下翻 */
.bk-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-content: start;
  gap: var(--gs-gap);
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
@container (min-width: 600px) {
  .bk-layout {
    grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
    align-content: stretch;
    overflow: hidden;
  }
}

.bk-shop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 0;
  min-width: 0;
}

/* 现烤现卖:整页最大的可点目标,按下有回弹;金色是「钱进来」的那一下 */
.bk-tap {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: min(100%, 32cqh, 210px);
  aspect-ratio: 1;
  padding: 0;
  border: 0;
  border-radius: 32%;
  background: radial-gradient(circle at 34% 28%, #fde6b8, #e6bf86 58%, #cfa76c);
  box-shadow:
    inset 0 -6px 12px rgb(122 87 47 / 22%),
    inset 0 4px 10px rgb(255 255 255 / 46%),
    0 6px 16px rgb(89 71 55 / 16%);
  color: #594737;
  font: inherit;
  cursor: pointer;
  touch-action: manipulation;
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease;
}
.bk-tap:active {
  transform: scale(0.96);
  box-shadow:
    inset 0 -3px 8px rgb(122 87 47 / 24%),
    inset 0 2px 6px rgb(255 255 255 / 40%),
    0 3px 8px rgb(89 71 55 / 14%);
}
.bk-tap-art {
  font-size: min(28cqw, 13cqh, 68px);
  line-height: 1;
  filter: drop-shadow(0 3px 4px rgb(89 71 55 / 18%));
}
.bk-tap-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  opacity: 0.82;
}
/* 飘字:点一下冒一次,不占按钮布局位置 */
.bk-tap-pop {
  position: absolute;
  top: 8%;
  right: 10%;
  font-size: 15px;
  font-weight: 700;
  color: var(--color-brand-text, #b6664e);
  pointer-events: none;
}
.bk-pop-enter-active {
  animation: bk-float 0.7s ease-out both;
}
.bk-pop-leave-active {
  display: none;
}
@keyframes bk-float {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  to {
    opacity: 0;
    transform: translateY(-26px) scale(1.15);
  }
}

.bk-stock {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: min(100%, 260px);
}
.bk-stock-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6a58);
}
.bk-stock-name {
  flex: 0 0 auto;
}
.bk-stock-row b {
  font-size: 14px;
  font-weight: 650;
  color: var(--color-text-primary, #3d3127);
  font-variant-numeric: tabular-nums;
}
/* 三段吞吐一条读完:数字用等宽数字,横屏窄栏也不会随数值跳动 */
.bk-rates {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin: 2px 0 0;
  font-size: 10.5px;
  color: var(--color-text-secondary, #7a6a58);
  font-variant-numeric: tabular-nums;
}

.bk-side {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  min-width: 0;
}
.bk-side .section-label {
  margin: 0;
}
.bk-up-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
/* 产线一行 = 左「名称+等级 / 效果」两行文字 + 右价格;买得起才亮,
   买不起整行压暗但文字仍可读(玩家要看到下一级多少钱) */
.bk-up {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
  width: 100%;
  padding: 6px 10px;
  border: 1px solid rgb(122 90 58 / 14%);
  border-radius: 12px;
  background: rgb(255 252 244 / 62%);
  color: var(--color-text-primary, #3d3127);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    transform 0.1s ease;
}
html.dark .bk-up {
  background: rgb(60 50 40 / 42%);
  border-color: rgb(255 240 219 / 12%);
}
.bk-up.is-afford {
  border-color: rgb(var(--color-brand-rgb) / 42%);
  background: rgb(255 248 235 / 92%);
}
html.dark .bk-up.is-afford {
  background: rgb(78 62 45 / 78%);
}
.bk-up.is-afford:hover {
  transform: translateY(-1px);
}
.bk-up:disabled {
  cursor: default;
  opacity: 0.62;
}
.bk-up-main {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1 1 auto;
}
.bk-up-name {
  display: flex;
  align-items: baseline;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
}
.bk-up-name em {
  font-style: normal;
  font-size: 10.5px;
  font-weight: 500;
  opacity: 0.62;
}
.bk-up-fx {
  overflow: hidden;
  font-size: 11px;
  color: var(--color-text-secondary, #7a6a58);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bk-up-cost {
  display: flex;
  align-items: center;
  gap: 3px;
  flex: 0 0 auto;
  font-size: 13px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.bk-up-cost i {
  font-style: normal;
  font-size: 11px;
  opacity: 0.6;
}

/* 分店条:进度用细条压在文字上方的左侧,数字与按钮同一行,横屏也塞得下 */
.bk-prestige {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
}
.bk-prestige-bar {
  display: block;
  overflow: hidden;
  height: 3px;
  border-radius: 2px;
  background: rgb(122 90 58 / 14%);
}
.bk-prestige-bar b {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: var(--color-brand, #c98a5c);
  transition: width 0.4s ease;
}
.bk-prestige-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.bk-prestige-label {
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.bk-prestige-progress {
  flex: 1 1 auto;
  overflow: hidden;
  font-size: 11.5px;
  color: var(--color-text-secondary, #7a6a58);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.bk-reward {
  margin: 0;
  font-size: 12px;
  color: var(--color-brand-text, #b6664e);
}

.bk-action-label {
  margin-left: 4px;
}
@media (max-width: 480px) {
  .bk-action-label {
    display: none;
  }
}

/* 弹窗内的正文与榜单 */
.bk-dialog-lead {
  margin: 0 0 8px;
  font-size: 13.5px;
  line-height: 1.6;
}
.bk-dialog-note {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #7a6a58);
}
.bk-dialog-note.is-error {
  color: var(--color-danger, #c45656);
}
.bk-rank-loading {
  padding: 8px 0;
}
.bk-rank-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}
.bk-rank-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 10px;
  font-size: 13px;
}
.bk-rank-row.is-me {
  background: rgb(var(--color-brand-rgb) / 10%);
}
.bk-rank-no {
  width: 22px;
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-secondary, #7a6a58);
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.bk-rank-name {
  display: flex;
  align-items: baseline;
  gap: 5px;
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bk-rank-name em {
  flex: 0 0 auto;
  padding: 0 4px;
  border-radius: 6px;
  background: rgb(var(--color-brand-rgb) / 18%);
  font-style: normal;
  font-size: 10px;
}
.bk-rank-score {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}
</style>
