<template>
  <GameShell
    ref="shellEl"
    class="bk-shell"
    :title="$t('games.bakery.title')"
    :rules-pages="[$t('games.bakery.rules.pagePlay'), $t('games.bakery.rules.pageMenu'), $t('games.bakery.rules.pageNum')]"
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
      <el-button size="small" round :title="$t('games.bakery.achievements.title')" @click="achOpen = true">
        <el-icon><Medal /></el-icon>
        <span class="bk-action-label">{{ $t('games.bakery.achievements.title') }}</span>
      </el-button>
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
      <ul v-else-if="page === 1">
        <li>{{ $t('games.bakery.rules.menu1') }}</li>
        <li>{{ $t('games.bakery.rules.menu2') }}</li>
        <li>{{ $t('games.bakery.rules.menu3') }}</li>
        <li>{{ $t('games.bakery.rules.menu4') }}</li>
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

      <!-- 解锁新品种是一次性花费,先确认再扣钱 -->
      <el-dialog
        v-model="unlockOpen"
        class="dialog-sm"
        :append-to-body="!isFullscreen"
        :title="$t('games.bakery.unlock.title')"
        :lock-scroll="false"
      >
        <p v-if="pendingUnlock" class="bk-dialog-lead">
          {{
            $t('games.bakery.unlock.confirm', {
              name: pendingUnlock.name,
              cost: compact(pendingUnlock.cost),
              price: compact(pendingUnlock.price),
            })
          }}
        </p>
        <p class="bk-dialog-note">{{ $t('games.bakery.unlock.note') }}</p>
        <template #footer>
          <el-button @click="unlockOpen = false">{{ $t('games.cancel') }}</el-button>
          <el-button type="primary" :disabled="!pendingAfford" @click="onUnlock">{{ $t('games.bakery.unlock.action') }}</el-button>
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
        v-model="achOpen"
        class="dialog-md"
        :append-to-body="!isFullscreen"
        :title="$t('games.bakery.achievements.title')"
        :lock-scroll="false"
      >
        <p class="bk-dialog-note">{{ $t('games.bakery.achievements.hint', { n: achievementBonusPercent }) }}</p>
        <ul class="bk-ach-list">
          <li v-for="a in achievements" :key="a.key" class="bk-ach" :class="{ 'is-done': a.unlocked }">
            <span class="bk-ach-head">
              <b>{{ a.name }}</b>
              <span class="bk-ach-progress">{{ a.unlocked ? $t('games.bakery.achievements.done') : `${a.progress}/${a.target}` }}</span>
            </span>
            <span class="bk-ach-desc">{{ a.desc }}</span>
            <i class="bk-ach-bar"><b :style="{ width: a.percent + '%' }"></b></i>
          </li>
        </ul>
        <template #footer>
          <el-button type="primary" @click="achOpen = false">{{ $t('games.rulesAck') }}</el-button>
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

    <!-- 左(竖屏上)= 现烤现卖、顾客订单与后厨库存,右(竖屏下)= 菜单与六条产线 -->
    <div class="bk-layout">
      <section class="bk-shop">
        <button type="button" class="bk-tap" :aria-label="$t('games.bakery.tap')" @click="onTap">
          <span class="bk-tap-art" aria-hidden="true">{{ currentDef.emoji }}</span>
          <span class="bk-tap-label">{{ $t('games.bakery.tap') }}</span>
          <Transition name="bk-pop" mode="out-in">
            <span v-if="popKey" :key="popKey" class="bk-tap-pop">+{{ tapGain }}</span>
          </Transition>
        </button>

        <!-- 顾客订单:限时交付现货换高额金币;库存不够时按钮禁用,不用猜条件 -->
        <div class="bk-order" :class="{ 'is-ready': order && order.canDeliver }">
          <template v-if="order">
            <div class="bk-order-head">
              <span class="bk-order-title">{{ $t('games.bakery.order.title') }}</span>
              <span class="bk-order-timer">{{ order.seconds }}s</span>
            </div>
            <div class="bk-order-body">{{ $t('games.bakery.order.body', { qty: order.qty, product: order.name }) }}</div>
            <div class="bk-order-foot">
              <span class="bk-order-reward">+{{ compact(order.reward) }} 🪙</span>
              <span class="bk-order-stock">{{ $t('games.bakery.order.stock', { have: order.stock, need: order.qty }) }}</span>
              <el-button size="small" type="primary" round :disabled="!order.canDeliver" @click="onDeliver">
                {{ $t('games.bakery.order.deliver') }}
              </el-button>
            </div>
          </template>
          <template v-else>
            <div class="bk-order-head">
              <span class="bk-order-title">{{ $t('games.bakery.order.title') }}</span>
              <span class="bk-order-rep">{{ $t('games.bakery.order.reputation', { n: reputationPercent }) }}</span>
            </div>
            <div class="bk-order-body">{{ $t('games.bakery.order.waiting', { seconds: orderGapLeft }) }}</div>
          </template>
        </div>

        <div class="bk-stock">
          <span class="bk-stock-row">
            <span class="bk-stock-name">{{ $t('games.bakery.dough') }}</span>
            <b>{{ compact(state.dough) }}</b>
          </span>
          <span class="bk-stock-row">
            <span class="bk-stock-name">{{ currentName }}</span>
            <b>{{ compact(currentStock) }}</b>
          </span>
          <!-- 三段产线的有效吞吐:卡在哪一段一眼能看出该升哪条线 -->
          <p class="bk-rates">
            <span>{{ $t('games.bakery.doughRate') }} {{ compact(rates.dough) }}/s</span>
            <span>{{ $t('games.bakery.bakeRate') }} {{ compact(rates.bake) }}/s</span>
            <span>{{ $t('games.bakery.sellRate') }} {{ compact(rates.sell) }}/s</span>
          </p>
        </div>
      </section>

      <section class="bk-side">
        <h2 class="section-label">{{ $t('games.bakery.menu') }}</h2>
        <div class="bk-menu">
          <button
            v-for="p in products"
            :key="p.key"
            type="button"
            class="bk-menu-item"
            :class="{ 'is-current': p.current, 'is-locked': !p.unlocked, 'is-afford': !p.unlocked && p.afford }"
            :title="p.detail"
            @click="onProduct(p)"
          >
            <span class="bk-menu-emoji" aria-hidden="true">{{ p.emoji }}</span>
            <span class="bk-menu-name">{{ p.name }}</span>
            <span class="bk-menu-meta">
              <template v-if="p.unlocked">{{ compact(p.price) }}</template>
              <template v-else>{{ $t('games.bakery.unlock.short', { cost: compact(p.cost) }) }}</template>
            </span>
          </button>
        </div>

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
// 小面包房(放置挂机):面团 → 面包 → 金币 → 升级产线 → 开分店,期间接顾客订单、攒成就。
// 数值、品种、订单、成就全在 utils/games/bakeryIdle.js(纯函数,单测覆盖);本页只负责
// 每秒推进、存档、离线弹窗、随机订单、家庭榜上报。进度只在本机,云端只有一个净资产数字。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Medal, Trophy } from '@element-plus/icons-vue'
import GameShell from '@/components/games/GameShell.vue'
import { gameApi } from '@/api'
import {
  ACHIEVEMENT_BONUS,
  ACHIEVEMENT_DEFS,
  OFFLINE_POPUP_SECONDS,
  PRESTIGE_FACTOR,
  PRODUCT_DEFS,
  STATE_KEY,
  STORE_BONUS,
  UPGRADE_DEFS,
  achievementProgress,
  buyUpgrade,
  canPrestige,
  catchUp,
  clickPower,
  coinsPerSecond,
  createState,
  formatCompact,
  fulfillOrder,
  isUnlocked,
  itemRates,
  levelOf,
  netWorth,
  orderReputation,
  parseState,
  prestige,
  prestigeRequirement,
  priceOf,
  productDef,
  rollOrder,
  selectProduct,
  serializeState,
  stockOf,
  tapSell,
  tick,
  unlockProduct,
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
const unlockOpen = ref(false)
const pendingUnlock = ref(null)
const achOpen = ref(false)
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
const rates = computed(() => itemRates(state.value))
const currentDef = computed(() => productDef(state.value.product))
const currentName = computed(() => t(`games.bakery.product.${currentDef.value.key}.name`))
const currentStock = computed(() => stockOf(state.value, state.value.product))
const tapGain = computed(() => compact(clickPower(state.value)))
const requirement = computed(() => prestigeRequirement(state.value))
const nextRequirement = computed(() => Math.floor(requirement.value * PRESTIGE_FACTOR))
const canPrestigeNow = computed(() => canPrestige(state.value))
const bonusPercent = computed(() => Math.round(STORE_BONUS * 100))
const prestigePercent = computed(() => Math.max(0, Math.min(100, Math.round((state.value.runCoins / requirement.value) * 100))))
const offlineTime = computed(() => formatDuration(offlineInfo.value?.elapsed || 0))
const orderGapLeft = computed(() => Math.max(0, Math.ceil(Number(state.value.orderGap) || 0)))
const reputationPercent = computed(() => Math.round((orderReputation(state.value) - 1) * 100))
const achievementBonusPercent = computed(() => Math.round(ACHIEVEMENT_BONUS * state.value.achievements.length * 100))
const pendingAfford = computed(() => !!pendingUnlock.value && state.value.coins >= pendingUnlock.value.cost)

const products = computed(() =>
  PRODUCT_DEFS.map((def) => ({
    key: def.key,
    emoji: def.emoji,
    name: t(`games.bakery.product.${def.key}.name`),
    detail: t('games.bakery.productDetail', { dough: def.doughPer, seconds: def.bakeSeconds }),
    price: priceOf(state.value, def.key),
    cost: def.unlockCost,
    unlocked: isUnlocked(state.value, def.key),
    current: state.value.product === def.key,
    afford: state.value.coins >= def.unlockCost,
  })),
)

const order = computed(() => {
  const current = state.value.order
  if (!current) return null
  const stock = stockOf(state.value, current.product)
  return {
    product: current.product,
    name: t(`games.bakery.product.${current.product}.name`),
    qty: current.qty,
    reward: current.reward,
    seconds: Math.max(0, Math.ceil(current.seconds)),
    stock: Math.floor(stock),
    canDeliver: stock >= current.qty,
  }
})

const achievements = computed(() =>
  ACHIEVEMENT_DEFS.map((def) => {
    const progress = Math.min(def.target, Math.floor(achievementProgress(state.value, def)))
    return {
      key: def.key,
      name: t(`games.bakery.ach.${def.key}.name`),
      desc: t(`games.bakery.ach.${def.key}.desc`),
      target: def.target,
      progress,
      percent: Math.min(100, Math.round((progress / def.target) * 100)),
      unlocked: state.value.achievements.includes(def.key),
    }
  }),
)

const upgrades = computed(() =>
  UPGRADE_DEFS.map((def) => {
    const cost = upgradeCost(state.value, def.key)
    return {
      key: def.key,
      name: t(`games.bakery.upgrade.${def.key}.name`),
      effect: t(`games.bakery.upgrade.${def.key}.effect`, {
        n: def.kind === 'MULT' || def.kind === 'PRICE' ? Math.round(def.amount * 100) : def.amount,
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

/** 没订单且间隔到了就请下一位顾客(随机数只在这一层给,内核保持纯函数) */
function nextCustomer(base) {
  return !base.order && base.orderGap <= 0 ? rollOrder(base, Math.random(), Math.random()) : base
}

// ---------- 存档 ----------
function bootstrap() {
  let loaded = null
  try {
    loaded = parseState(localStorage.getItem(STATE_KEY))
  } catch {
    // 隐私模式/坏档:按新档处理
  }
  return nextCustomer(resumeOffline(loaded || createState()))
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

let rewardTimer = null
const showReward = (text) => {
  rewardMsg.value = text
  if (rewardTimer) clearTimeout(rewardTimer)
  rewardTimer = setTimeout(() => {
    rewardMsg.value = ''
  }, 8000)
}

/**
 * 所有改动都走这里:先记下成就数量,解锁了就提示;再顺手补下一单顾客。
 * 每帧 tick 不写存档(由 5 秒兜底与操作后的 300ms 节流负责),避免每秒落盘。
 */
const applyState = (next, { save = true } = {}) => {
  const before = state.value.achievements.length
  state.value = next
  if (next.achievements.length > before) {
    const names = next.achievements
      .slice(before)
      .map((key) => t(`games.bakery.ach.${key}.name`))
      .join(t('games.bakery.listSep'))
    showReward(t('games.bakery.achievements.unlocked', { name: names, n: achievementBonusPercent.value }))
  }
  state.value = nextCustomer(state.value)
  if (save) scheduleSave()
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
  applyState({ ...tick(state.value, elapsed), lastTick: now }, { save: false })
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
  applyState(tapSell(state.value))
  popKey.value += 1
  reportIfChanged()
}

const onBuy = (key) => {
  const result = buyUpgrade(state.value, key)
  if (!result.bought) return
  applyState({ ...result.state, lastTick: Date.now() })
}

const onProduct = (product) => {
  if (product.unlocked) {
    applyState(selectProduct(state.value, product.key))
    return
  }
  pendingUnlock.value = product
  unlockOpen.value = true
}

const onUnlock = () => {
  const product = pendingUnlock.value
  unlockOpen.value = false
  pendingUnlock.value = null
  if (!product) return
  const result = unlockProduct(state.value, product.key)
  if (!result.ok) return
  applyState(result.state)
}

const onDeliver = () => {
  const result = fulfillOrder(state.value)
  if (!result.ok) return
  applyState(result.state)
  showReward(t('games.bakery.order.done', { coins: compact(result.reward) }))
}

const openPrestige = () => {
  if (!canPrestigeNow.value) return
  prestigeOpen.value = true
}

const onPrestige = () => {
  const result = prestige(state.value, Date.now())
  prestigeOpen.value = false
  if (!result.ok) return
  applyState(result.state)
  flushSave()
  submitScore()
  grantReward()
}

const grantReward = async () => {
  try {
    const res = await gameApi.bakeryReward()
    const percent = Math.round((Math.pow(1.15, state.value.stores) - 1) * 100)
    showReward(
      res && res.awarded > 0
        ? `${t('games.bakery.prestige.done', { n: percent })} ${t('games.rewarded', { n: res.awarded })}`
        : `${t('games.bakery.prestige.done', { n: percent })} ${t('games.rewardCapped')}`,
    )
  } catch {
    showReward(t('games.rewardFailed'))
  }
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
  state.value = nextCustomer(resumeOffline(state.value))
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
   整块可滚动 —— 现烤现卖按钮始终在视线里,菜单与产线列表往下翻 */
.bk-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  /* 上下排时每段按内容取高,整块滚动;否则矮屏会把两段都压扁,菜单和产线都点不到 */
  grid-auto-rows: max-content;
  align-content: start;
  gap: var(--gs-gap);
  width: 100%;
  height: 100%;
  /* 横屏矮屏时外壳高度按内容撑,这一栏要允许被压小,内部自己滚 */
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
@container (min-width: 600px) {
  .bk-layout {
    grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
    grid-auto-rows: auto;
    align-content: stretch;
    /* 横屏矮屏时两栏内容仍可能超出游戏区:留着滚动,别把产线列表裁掉 */
    overflow-y: auto;
  }
}

.bk-shop {
  display: flex;
  flex-direction: column;
  align-items: center;
  /* safe:容器比内容矮时退回顶端对齐,否则居中会把「现烤现卖」上半截挤出滚动区 */
  justify-content: safe center;
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
  width: min(100%, 28cqh, 200px);
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

/* 顾客订单:一张窄卡,交付条件(库存/倒计时)全写在同一行 */
.bk-order {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: min(100%, 300px);
  padding: 7px 10px;
  border: 1px solid rgb(122 90 58 / 14%);
  border-radius: 12px;
  background: rgb(255 252 244 / 62%);
  font-size: 12px;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease;
}
html.dark .bk-order {
  background: rgb(60 50 40 / 42%);
  border-color: rgb(255 240 219 / 12%);
}
.bk-order.is-ready {
  border-color: rgb(var(--color-brand-rgb) / 46%);
  background: rgb(255 248 235 / 92%);
}
html.dark .bk-order.is-ready {
  background: rgb(78 62 45 / 78%);
}
.bk-order-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}
.bk-order-title {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-secondary, #7a6a58);
}
.bk-order-timer {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-brand-text, #b6664e);
  font-variant-numeric: tabular-nums;
}
.bk-order-rep {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-brand-text, #b6664e);
  font-variant-numeric: tabular-nums;
}
.bk-order-body {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bk-order-foot {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.bk-order-reward {
  flex: 0 0 auto;
  font-weight: 650;
  color: var(--color-brand-text, #b6664e);
  font-variant-numeric: tabular-nums;
}
.bk-order-stock {
  flex: 1 1 auto;
  overflow: hidden;
  font-size: 11px;
  color: var(--color-text-secondary, #7a6a58);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.bk-stock {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: min(100%, 300px);
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
  /* 两栏矮屏时右栏装不下菜单+产线:整栏可滚,产线列表至少露出一行 */
  overflow-y: auto;
}
.bk-side .section-label {
  margin: 0;
}

/* 菜单:两列小格,当前品种亮起;锁着显示解锁价,买得起用虚线框提示 */
.bk-menu {
  display: grid;
  flex: 0 0 auto;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  min-height: 0;
}
.bk-menu-item {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 5px 8px;
  border: 1px solid rgb(122 90 58 / 14%);
  border-radius: 10px;
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
html.dark .bk-menu-item {
  background: rgb(60 50 40 / 42%);
  border-color: rgb(255 240 219 / 12%);
}
.bk-menu-item.is-current {
  border-color: rgb(var(--color-brand-rgb) / 52%);
  background: rgb(255 248 235 / 92%);
  box-shadow: inset 0 0 0 1px rgb(var(--color-brand-rgb) / 22%);
}
html.dark .bk-menu-item.is-current {
  background: rgb(78 62 45 / 78%);
}
.bk-menu-item.is-locked {
  opacity: 0.72;
}
.bk-menu-item.is-locked.is-afford {
  border-style: dashed;
  opacity: 1;
}
.bk-menu-item.is-current:hover,
.bk-menu-item.is-locked.is-afford:hover {
  transform: translateY(-1px);
}
.bk-menu-emoji {
  flex: 0 0 auto;
  font-size: 16px;
  line-height: 1;
}
.bk-menu-name {
  flex: 1 1 auto;
  overflow: hidden;
  min-width: 0;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bk-menu-meta {
  flex: 0 0 auto;
  font-size: 11px;
  font-weight: 650;
  color: var(--color-text-secondary, #7a6a58);
  font-variant-numeric: tabular-nums;
}
.bk-menu-item.is-current .bk-menu-meta {
  color: var(--color-brand-text, #b6664e);
}

.bk-up-list {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 6px;
  min-height: 72px;
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

/* 弹窗内的正文、成就列表与榜单 */
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
.bk-ach-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: min(56vh, 480px);
  margin: 10px 0 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}
.bk-ach {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  border-radius: 10px;
  background: rgb(122 90 58 / 6%);
}
.bk-ach.is-done {
  background: rgb(var(--color-brand-rgb) / 12%);
}
.bk-ach-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}
.bk-ach-head b {
  font-weight: 600;
}
.bk-ach-progress {
  font-size: 11px;
  color: var(--color-text-secondary, #7a6a58);
  font-variant-numeric: tabular-nums;
}
.bk-ach.is-done .bk-ach-progress {
  font-weight: 600;
  color: var(--color-brand-text, #b6664e);
}
.bk-ach-desc {
  font-size: 11.5px;
  color: var(--color-text-secondary, #7a6a58);
}
.bk-ach-bar {
  display: block;
  overflow: hidden;
  height: 3px;
  margin-top: 2px;
  border-radius: 2px;
  background: rgb(122 90 58 / 14%);
}
.bk-ach-bar b {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: var(--color-brand, #c98a5c);
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
