<template>
  <GameShell
    ref="shellEl"
    class="hs-shell"
    :title="$t('games.homestead.title')"
    :rules-pages="[$t('games.homestead.rules.pagePlay'), $t('games.homestead.rules.pageSkill'), $t('games.homestead.rules.pageNum')]"
    body-align="stretch"
    @back="goBack"
  >
    <template #stats>
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.coins') }}</span
        ><b>{{ compact(coins) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.index') }}</span
        ><b>{{ compact(index) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.speed') }}</span
        ><b>{{ currentCode ? speedPercent + '%' : '—' }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.offlineCap') }}</span
        ><b>{{ $t('games.homestead.offlineCapHours', { h: capHours }) }}</b></span
      >
    </template>

    <template #actions>
      <el-button size="small" round :title="$t('games.homestead.ranking.title')" @click="openRanking">
        <el-icon><Trophy /></el-icon>
        <span class="hs-btn-label">{{ $t('games.homestead.ranking.title') }}</span>
      </el-button>
    </template>

    <template #rules="{ page }">
      <ul v-if="page === 0">
        <li>{{ $t('games.homestead.rules.play1') }}</li>
        <li>{{ $t('games.homestead.rules.play2') }}</li>
        <li>{{ $t('games.homestead.rules.play3') }}</li>
        <li>{{ $t('games.homestead.rules.play4') }}</li>
        <li>{{ $t('games.homestead.rules.play5') }}</li>
        <li>{{ $t('games.homestead.rules.play6') }}</li>
      </ul>
      <ul v-else-if="page === 1">
        <li>{{ $t('games.homestead.rules.skill1') }}</li>
        <li>{{ $t('games.homestead.rules.skill2') }}</li>
        <li>{{ $t('games.homestead.rules.skill3') }}</li>
        <li>{{ $t('games.homestead.rules.skill4') }}</li>
      </ul>
      <ul v-else>
        <li>{{ $t('games.homestead.rules.num1') }}</li>
        <li>{{ $t('games.homestead.rules.num2') }}</li>
        <li>{{ $t('games.homestead.rules.num3') }}</li>
        <li>{{ $t('games.homestead.rules.num4') }}</li>
      </ul>
    </template>

    <template #overlay>
      <!-- 离线结算:离开够久且有实际产出才弹,把补了多少、封顶没有、材料停在哪一步写清楚 -->
      <el-dialog
        v-model="offlineOpen"
        class="dialog-sm"
        :append-to-body="!isFullscreen"
        :title="$t('games.homestead.offline.title')"
        :lock-scroll="false"
      >
        <p class="hs-dialog-lead">{{ $t('games.homestead.offline.lead', { time: offlineTime }) }}</p>
        <ul v-if="offlineInfo" class="hs-offline-list">
          <li>{{ $t('games.homestead.offline.completions', { n: compact(offlineInfo.completions) }) }}</li>
          <li v-if="offlineInfo.xpGained > 0">{{ $t('games.homestead.offline.xp', { n: compact(offlineInfo.xpGained) }) }}</li>
          <li v-if="offlineInfo.levelUps > 0">{{ $t('games.homestead.offline.levels', { n: offlineInfo.levelUps }) }}</li>
          <li v-if="offlineItems">{{ $t('games.homestead.offline.items', { list: offlineItems }) }}</li>
        </ul>
        <p v-if="offlineInfo && offlineInfo.capped" class="hs-dialog-note">{{ $t('games.homestead.offline.capped') }}</p>
        <p v-if="offlineInfo && offlineInfo.stopped" class="hs-dialog-note">{{ $t('games.homestead.offline.stopped') }}</p>
        <template #footer>
          <el-button type="primary" @click="offlineOpen = false">{{ $t('games.homestead.offline.ack') }}</el-button>
        </template>
      </el-dialog>

      <!-- 家庭小院指数榜:指数全在服务端算,这里只展示 -->
      <el-dialog
        v-model="rankOpen"
        class="dialog-sm"
        :append-to-body="!isFullscreen"
        :title="$t('games.homestead.ranking.title')"
        :lock-scroll="false"
      >
        <p class="hs-dialog-note">{{ $t('games.homestead.ranking.hint') }}</p>
        <div v-if="rankLoading" class="hs-rank-loading"><el-skeleton :rows="4" animated /></div>
        <p v-else-if="rankFailed" class="hs-dialog-note is-warn">{{ $t('games.homestead.ranking.failed') }}</p>
        <p v-else-if="!rankRows.length" class="hs-dialog-note">{{ $t('games.homestead.ranking.empty') }}</p>
        <ol v-else class="hs-rank">
          <li v-for="(row, i) in rankRows" :key="row.userId" class="hs-rank-row" :class="{ 'is-me': row.isMe }">
            <span class="hs-rank-no">{{ row.rank || i + 1 }}</span>
            <span class="hs-rank-name">
              {{ row.name }}
              <em v-if="row.isMe">{{ $t('games.homestead.ranking.me') }}</em>
            </span>
            <b class="hs-rank-score">{{ compact(row.score) }}</b>
          </li>
        </ol>
        <template #footer>
          <el-button :loading="rankLoading" @click="openRanking">{{ $t('games.homestead.ranking.refresh') }}</el-button>
          <el-button type="primary" @click="rankOpen = false">{{ $t('games.rulesAck') }}</el-button>
        </template>
      </el-dialog>
    </template>

    <div class="hs-wrap" :data-pane="pane">
      <nav class="hs-tabs" role="tablist">
        <button
          v-for="tab in TABS"
          :key="tab.value"
          type="button"
          class="hs-tab"
          role="tab"
          :class="{ 'is-on': sideTab === tab.value }"
          :aria-selected="sideTab === tab.value"
          @click="pane = tab.value"
        >
          {{ $t(tab.key) }}
        </button>
      </nav>

      <div v-if="loading" class="hs-state"><el-skeleton :rows="6" animated /></div>
      <div v-else-if="loadError" class="hs-state">
        <p>{{ $t('games.homestead.loadFailed') }}</p>
        <el-button type="primary" @click="loadState">{{ $t('games.homestead.retry') }}</el-button>
      </div>

      <div v-else class="hs-grid">
        <!-- 左:工作门类(11 门),等级环 + 正在做的高亮 -->
        <aside class="hs-rail">
          <button
            v-for="skill in skillRows"
            :key="skill.code"
            type="button"
            class="hs-skill"
            :class="{ 'is-on': skill.code === activeSkill, 'is-working': skill.code === workingSkill }"
            @click="selectedSkill = skill.code"
          >
            <span class="hs-skill-icon" aria-hidden="true">{{ skill.icon }}</span>
            <span class="hs-skill-body">
              <span class="hs-skill-name">{{ skill.name }}</span>
              <i class="hs-skill-bar"><b :style="{ width: skill.percent + '%' }"></b></i>
            </span>
            <b class="hs-skill-lv">{{
              skill.maxed ? $t('games.homestead.maxedLevel') : $t('games.homestead.level', { n: skill.level })
            }}</b>
          </button>
        </aside>

        <section class="hs-main">
          <!-- 当前工作:进度条是 CSS 循环动画(时长 = 单次耗时,负延迟 = 已走部分),不每帧写 ref -->
          <header class="hs-now" :class="{ 'is-warn': currentMaterialsOut, 'is-idle': !currentAction }">
            <div class="hs-now-top">
              <span class="hs-now-icon" aria-hidden="true">{{ currentIcon }}</span>
              <div class="hs-now-text">
                <b class="hs-now-title">
                  {{
                    currentAction ? $t('games.homestead.working', { name: pickName(currentAction, locale) }) : $t('games.homestead.idle')
                  }}
                </b>
                <span class="hs-now-sub">
                  {{
                    currentAction
                      ? $t('games.homestead.actionMeta', { s: perText(currentPerMs), xp: currentXp })
                      : $t('games.homestead.idleHint')
                  }}
                </span>
              </div>
              <el-button v-if="currentAction" size="small" round :disabled="busy" @click="stopAction">
                {{ $t('games.homestead.stop') }}
              </el-button>
            </div>
            <div class="hs-progress" :style="progressStyle">
              <i v-if="currentAction && !currentMaterialsOut" class="hs-progress-fill"></i>
            </div>
            <div class="hs-now-foot">
              <span v-if="currentAction && !currentMaterialsOut" class="hs-now-left">
                {{ $t('games.homestead.remaining', { s: remainingSec }) }}
              </span>
              <span v-if="currentMaterialsOut" class="hs-now-hint">{{ $t('games.homestead.materialsAuto') }}</span>
              <span v-else-if="currentYieldText" class="hs-now-out">
                {{ $t('games.homestead.actionOut', { list: currentYieldText }) }}
              </span>
            </div>
            <div v-if="currentAction && bonusChips.length" class="hs-now-bonus">
              <span v-for="chip in bonusChips" :key="chip" class="hs-bonus">{{ chip }}</span>
            </div>
            <div class="hs-buffs">
              <span class="hs-buffs-label">{{ $t('games.homestead.buffs') }}</span>
              <span v-if="!buffRows.length" class="hs-buff-empty">{{ $t('games.homestead.buffNone') }}</span>
              <span v-for="buff in buffRows" :key="buff.item" class="hs-buff" :class="'is-' + buff.kind.toLowerCase()">
                {{ buff.name }} · {{ $t('games.homestead.buffKind.' + buff.kind) }} +{{ bpToPercent(buff.bp) }}%
                <em>{{ buff.leftText }}</em>
              </span>
            </div>
          </header>

          <!-- 具体工作:卡片网格(门槛/耗材/产出/稀有概率/锁在哪一步) -->
          <div class="hs-cards">
            <article
              v-for="row in actionRows"
              :key="row.code"
              class="hs-card"
              :class="['is-' + row.state.toLowerCase(), { 'is-current': row.current }]"
            >
              <header class="hs-card-head">
                <b class="hs-card-name">{{ row.name }}</b>
                <span class="hs-card-lv">{{ $t('games.homestead.level', { n: row.level }) }}</span>
              </header>
              <p v-if="row.state !== 'READY'" class="hs-card-tag" :class="'is-' + row.state.toLowerCase()">{{ row.tagText }}</p>
              <p class="hs-card-line">{{ $t('games.homestead.actionMeta', { s: row.per, xp: row.xp }) }}</p>
              <p v-if="row.inputsText" class="hs-card-line">{{ $t('games.homestead.actionIn', { list: row.inputsText }) }}</p>
              <p class="hs-card-line">{{ $t('games.homestead.actionOut', { list: row.outputsText }) }}</p>
              <p v-if="row.rareText" class="hs-card-line is-rare">
                {{ $t('games.homestead.actionRare', { p: row.rarePercent, list: row.rareText }) }}
              </p>
              <p v-if="row.missingText" class="hs-card-line is-warn">
                {{ $t('games.homestead.actionMissing', { list: row.missingText }) }}
              </p>
              <p v-if="row.toolText" class="hs-card-line is-tool">{{ row.toolText }}</p>
              <button
                type="button"
                class="hs-card-go"
                :class="{ 'is-stop': row.current }"
                :disabled="busy || (!row.current && row.state !== 'READY')"
                @click="row.current ? stopAction() : startAction(row.code)"
              >
                {{ row.current ? $t('games.homestead.stop') : $t('games.homestead.start') }}
              </button>
            </article>
          </div>
        </section>

        <!-- 右:物品 / 工具(含穿戴)/ 场地 三个子页签 -->
        <aside class="hs-side">
          <nav class="hs-subtabs" role="tablist">
            <button
              v-for="tab in SIDE_TABS"
              :key="tab.value"
              type="button"
              class="hs-subtab"
              role="tab"
              :class="{ 'is-on': sideTab === tab.value }"
              :aria-selected="sideTab === tab.value"
              @click="pane = tab.value"
            >
              {{ $t(tab.key) }}
            </button>
          </nav>

          <div class="hs-side-body">
            <!-- 物品:赶集行情 + 素材/成品/特产分组;带 buff 的成品可以吃 -->
            <section v-show="sideTab === 'BAG'" class="hs-pane">
              <div v-if="marketOrders.length" class="hs-market">
                <h2 class="hs-pane-title">
                  <span>{{ $t('games.homestead.market.title') }}</span>
                  <em>{{ marketResetText }}</em>
                </h2>
                <p class="hs-market-hint">{{ $t('games.homestead.market.hint', { p: marketBonusPercent, n: market.quota }) }}</p>
                <div v-for="row in marketOrders" :key="row.code" class="hs-market-row" :class="{ 'is-done': !row.remaining }">
                  <span class="hs-market-name">{{ row.name }}</span>
                  <b class="hs-market-price">{{ compact(row.price) }}</b>
                  <span class="hs-market-left">
                    {{ row.remaining ? $t('games.homestead.market.left', { n: row.remaining }) : $t('games.homestead.market.soldOut') }}
                  </span>
                </div>
              </div>

              <div class="hs-bag">
                <h2 class="hs-pane-title">
                  <span>{{ $t('games.homestead.bag') }}</span>
                  <em v-if="itemKinds">{{ $t('games.homestead.kinds', { n: itemKinds }) }}</em>
                </h2>
                <p v-if="!bagGroups.length" class="hs-empty">{{ $t('games.homestead.bagEmpty') }}</p>
                <template v-for="group in bagGroups" :key="group.category">
                  <h3 class="hs-sub">{{ $t('games.homestead.category.' + group.category) }}</h3>
                  <div v-for="item in group.rows" :key="item.code" class="hs-item">
                    <div class="hs-item-top">
                      <span class="hs-item-name">{{ item.name }}</span>
                      <b class="hs-item-qty">×{{ compact(item.qty) }}</b>
                      <span class="hs-item-price" :class="{ 'is-market': item.boosted }">
                        {{ $t('games.homestead.sellPrice', { n: compact(item.price) }) }}
                      </span>
                    </div>
                    <p v-if="item.buffText" class="hs-item-buff">{{ item.buffText }}</p>
                    <div class="hs-item-acts">
                      <button type="button" class="hs-mini" :disabled="busy" @click="sellItem(item.code, 1)">
                        {{ $t('games.homestead.sellOne') }}
                      </button>
                      <button type="button" class="hs-mini" :disabled="busy" @click="sellItem(item.code, item.qty)">
                        {{ $t('games.homestead.sellAll') }}
                      </button>
                      <button v-if="item.consumable" type="button" class="hs-mini is-main" :disabled="busy" @click="eatItem(item.code)">
                        {{ $t('games.homestead.consume') }}
                      </button>
                    </div>
                  </div>
                </template>
              </div>
            </section>

            <!-- 工具:当前门类 6 件专属工具 + 五个部位的穿戴 -->
            <section v-show="sideTab === 'TOOLS'" class="hs-pane">
              <div class="hs-tools">
                <h2 class="hs-pane-title">
                  <span>{{ $t('games.homestead.tools') }}</span>
                  <em>{{ $t('games.homestead.toolHint') }}</em>
                </h2>
                <article v-for="tool in toolRows" :key="tool.code" class="hs-tool" :class="{ 'is-maxed': tool.maxed }">
                  <div class="hs-tool-head">
                    <span class="hs-tool-name">{{ tool.name }}</span>
                    <b class="hs-tool-tier">
                      {{ tool.maxed ? $t('games.homestead.toolMaxed') : $t('games.homestead.toolTier', { n: tool.tier, max: tool.max }) }}
                    </b>
                  </div>
                  <p class="hs-tool-sub">{{ tool.actionName }}</p>
                  <i class="hs-steps"><b v-for="step in tool.max" :key="step" :class="{ 'is-on': step <= tool.tier }"></b></i>
                  <p v-if="tool.effectText" class="hs-tool-effect">{{ tool.effectText }}</p>
                  <div class="hs-tool-foot">
                    <span class="hs-tool-cost">
                      {{ tool.costText }}
                      <em v-if="tool.lackText">{{ $t('games.homestead.costLack', { list: tool.lackText }) }}</em>
                      <em v-else-if="!tool.levelOk">{{ $t('games.homestead.toolRequireLevel', { n: tool.requireLevel }) }}</em>
                    </span>
                    <button type="button" class="hs-mini is-main" :disabled="busy || !tool.ok" @click="craftTool(tool.code)">
                      {{
                        tool.maxed
                          ? $t('games.homestead.toolMaxed')
                          : tool.tier > 0
                            ? $t('games.homestead.toolUpgrade')
                            : $t('games.homestead.toolCraft')
                      }}
                    </button>
                  </div>
                </article>
              </div>

              <div class="hs-gear">
                <h2 class="hs-pane-title">
                  <span>{{ $t('games.homestead.gear.title') }}</span>
                  <em>{{ $t('games.homestead.gear.hint') }}</em>
                </h2>
                <template v-for="group in gearGroups" :key="group.slot">
                  <h3 class="hs-sub">{{ group.name }}</h3>
                  <article
                    v-for="gear in group.items"
                    :key="gear.code"
                    class="hs-tool"
                    :class="{ 'is-worn': gear.worn, 'is-maxed': gear.maxed }"
                  >
                    <div class="hs-tool-head">
                      <span class="hs-tool-name">{{ gear.name }}</span>
                      <b class="hs-tool-tier">{{ gear.stateText }}</b>
                    </div>
                    <i v-if="gear.owned" class="hs-steps">
                      <b v-for="step in gear.max" :key="step" :class="{ 'is-on': step <= gear.enhance }"></b>
                    </i>
                    <p v-if="gear.effectText" class="hs-tool-effect">{{ gear.effectText }}</p>
                    <div class="hs-tool-foot">
                      <span class="hs-tool-cost">
                        {{ gear.costText }}
                        <em v-if="gear.lackText">{{ $t('games.homestead.costLack', { list: gear.lackText }) }}</em>
                        <em v-else-if="!gear.levelOk">{{ $t('games.homestead.gear.requireLevel', { n: gear.requireLevel }) }}</em>
                      </span>
                      <span class="hs-tool-btns">
                        <button type="button" class="hs-mini is-main" :disabled="busy || !gear.ok" @click="gearAction(gear)">
                          {{ gear.buttonText }}
                        </button>
                        <button
                          v-if="gear.owned"
                          type="button"
                          class="hs-mini"
                          :disabled="busy || (!gear.worn && !gear.canWear)"
                          @click="toggleWear(gear)"
                        >
                          {{ gear.worn ? $t('games.homestead.gear.takeOff') : $t('games.homestead.gear.wear') }}
                        </button>
                      </span>
                    </div>
                  </article>
                </template>
              </div>
            </section>

            <!-- 场地:14 栋建筑,按级加成本门类;工坊 Lv.5 解锁 70 级工坊工作 -->
            <section v-show="sideTab === 'YARD'" class="hs-pane">
              <div class="hs-yard">
                <h2 class="hs-pane-title">
                  <span>{{ $t('games.homestead.yard.title') }}</span>
                  <em>{{ $t('games.homestead.yard.hint') }}</em>
                </h2>
                <div class="hs-yard-grid">
                  <article v-for="row in buildingRows" :key="row.code" class="hs-tool hs-building" :class="{ 'is-maxed': row.maxed }">
                    <div class="hs-tool-head">
                      <span class="hs-tool-icon" aria-hidden="true">{{ row.icon }}</span>
                      <span class="hs-tool-name">{{ row.name }}</span>
                      <b class="hs-tool-tier">{{ $t('games.homestead.yard.level', { n: row.level, max: row.maxLevel }) }}</b>
                    </div>
                    <i class="hs-steps"><b v-for="step in row.maxLevel" :key="step" :class="{ 'is-on': step <= row.level }"></b></i>
                    <p v-if="row.effectText" class="hs-tool-effect">{{ row.effectText }}</p>
                    <p v-if="row.unlockText" class="hs-tool-desc">{{ row.unlockText }}</p>
                    <div class="hs-tool-foot">
                      <span class="hs-tool-cost">
                        {{ row.costText }}
                        <em v-if="row.lackText">{{ $t('games.homestead.costLack', { list: row.lackText }) }}</em>
                      </span>
                      <button type="button" class="hs-mini is-main" :disabled="busy || !row.ok" @click="upgradeBuilding(row.code)">
                        {{
                          row.maxed
                            ? $t('games.homestead.yard.maxed')
                            : row.level > 0
                              ? $t('games.homestead.yard.upgrade')
                              : $t('games.homestead.yard.build')
                        }}
                      </button>
                    </div>
                  </article>
                </div>
              </div>
            </section>
          </div>
        </aside>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
// 小院慢生活(放置):服务端存档 + 离线结算,客户端只渲染状态、按服务端锚点给进度条设动画。
// 产出/经验/稀有掉落一律以 /state 为准(utils/games/homestead.js 只放展示侧纯函数),
// 本地不模拟收益 —— 否则会和 HomesteadEngine 变成两套引擎,离线回来数字对不上。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Trophy } from '@element-plus/icons-vue'
import GameShell from '@/components/games/GameShell.vue'
import { homesteadApi } from '@/api'
import {
  actionBonus,
  actionState,
  bpToPercent,
  buffRemainingMs,
  chancePercent,
  costGap,
  countdownParts,
  elapsedText,
  formatCompact,
  msPerItem,
  pickName,
  progressOf,
  sellCoinsWithBonus,
  xpRatio,
} from '@/utils/games/homestead'

/** 站在页面里对账的节奏:数字 1 秒重画一次,状态 60 秒拉一次(写操作直接回全量状态) */
const TICK_MS = 1000
const POLL_MS = 60000
/** 本地插值到点后提前对账,两次请求至少隔这么久(服务端才是入账方,这里只是让数字早点跟上) */
const DUE_SYNC_GAP_MS = 1500
const TABS = [
  { value: 'WORK', key: 'games.homestead.tabWork' },
  { value: 'BAG', key: 'games.homestead.tabBag' },
  { value: 'TOOLS', key: 'games.homestead.tabTools' },
  { value: 'YARD', key: 'games.homestead.tabYard' },
]
const SIDE_TABS = TABS.slice(1)
const BAG_CATEGORIES = ['MATERIAL', 'PRODUCT', 'RARE']

const router = useRouter()
const { t, locale } = useI18n()

const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)

const state = ref(null)
const loading = ref(true)
const loadError = ref(false)
const busy = ref(false)
const syncing = ref(false)
/** 手机四页签与桌面右栏三个子页签共用一个状态:WORK 时桌面子页签默认落在「物品」 */
const pane = ref('WORK')
const selectedSkill = ref('')

/** 进度条动画锚点:拿到 /state 的本地时刻;nowTick 每秒推进(只喂数字与到点对账) */
const anchorAt = ref(Date.now())
const nowTick = ref(Date.now())
let syncedAt = 0

const offlineOpen = ref(false)
const offlineInfo = ref(null)
const rankOpen = ref(false)
const rankLoading = ref(false)
const rankFailed = ref(false)
const rankRows = ref([])

// ---------- 内容表与服务端状态 ----------
const content = computed(
  () => state.value?.content || { skills: [], items: [], actions: [], tools: [], equipment: [], buildings: [], gearSlots: [] },
)
const player = computed(() => state.value?.player || {})
const coins = computed(() => Number(player.value.coins) || 0)
const index = computed(() => Number(state.value?.index) || 0)
const currentCode = computed(() => player.value.actionCode || null)
const compact = (value) => formatCompact(value, locale.value)
const listSep = computed(() => t('games.homestead.listSep'))
const sideTab = computed(() => (pane.value === 'WORK' ? 'BAG' : pane.value))

const byCode = (list) => {
  const out = {}
  for (const def of list || []) out[def.code] = def
  return out
}
const skillMap = computed(() => byCode(content.value.skills))
const itemMap = computed(() => byCode(content.value.items))
const actionMap = computed(() => byCode(content.value.actions))
const gearMap = computed(() => byCode(content.value.equipment))
const buildingMap = computed(() => byCode(content.value.buildings))
const toolByAction = computed(() => {
  const out = {}
  for (const tool of content.value.tools || []) out[tool.action] = tool
  return out
})

const itemName = (code) => pickName(itemMap.value[code], locale.value)

const levelBySkill = computed(() => {
  const out = {}
  for (const row of state.value?.skills || []) out[row.code] = Number(row.level) || 1
  return out
})
const tierByTool = computed(() => {
  const out = {}
  for (const row of state.value?.tools || []) out[row.code] = Number(row.tier) || 0
  return out
})
const enhanceByGear = computed(() => {
  const out = {}
  for (const row of state.value?.gear || []) out[row.code] = Number(row.enhance) || 0
  return out
})
const wornBySlot = computed(() => {
  const out = {}
  for (const row of state.value?.wear || []) out[row.slot] = row.gearCode || null
  return out
})
const levelByBuilding = computed(() => {
  const out = {}
  for (const row of state.value?.buildings || []) out[row.code] = Number(row.level) || 0
  return out
})
const inventory = computed(() => {
  const out = {}
  for (const row of state.value?.items || []) out[row.code] = Number(row.qty) || 0
  return out
})
const levelOf = (skillCode) => levelBySkill.value[skillCode] || 1
const buildingLevel = (code) => (code ? levelByBuilding.value[code] || 0 : 0)
/** 服务端折叠好的当前动作加成(工具 + 穿戴 + 场地 + 生效 buff) */
const bonus = computed(() => state.value?.bonus || {})
const capHours = computed(() => Number(bonus.value.capHours) || 24)

const skills = computed(() =>
  (state.value?.skills || []).map((row) => {
    const def = skillMap.value[row.code] || {}
    return {
      code: row.code,
      name: pickName(def, locale.value),
      icon: def.icon || '·',
      kind: def.kind || 'GATHER',
      level: Number(row.level) || 1,
      percent: xpRatio(row) * 100,
      maxed: Number(row.xpToNext) <= 0,
    }
  }),
)
const skillRows = skills

const currentAction = computed(() => (currentCode.value ? actionMap.value[currentCode.value] || null : null))
const currentTool = computed(() => (currentCode.value ? toolByAction.value[currentCode.value] || null : null))
const currentTier = computed(() => (currentTool.value ? tierByTool.value[currentTool.value.code] || 0 : 0))
const workingSkill = computed(() => currentAction.value?.skill || '')
const activeSkill = computed(() => selectedSkill.value || workingSkill.value || skills.value[0]?.code || '')
const currentIcon = computed(() => {
  const def = skillMap.value[currentAction.value?.skill || activeSkill.value]
  return def?.icon || '🌾'
})

/** 某一门工作吃到的加成:从服务端给的总量里换掉「当前动作的工具」那一份 */
const bonusOf = (actionCode) => {
  const tool = toolByAction.value[actionCode] || null
  return actionBonus(bonus.value, currentTool.value, currentTier.value, tool, tool ? tierByTool.value[tool.code] || 0 : 0)
}
const xpText = (baseXp, xpBp) => {
  const base = Number(baseXp) || 0
  if (base <= 0) return 0
  return Math.max(1, Math.floor((base * (10000 + Math.max(0, xpBp))) / 10000))
}
const perText = (ms) => {
  const value = Math.max(0, Number(ms) || 0)
  return value % 1000 === 0 ? String(value / 1000) : (value / 1000).toFixed(1)
}

const perItemMs = computed(() =>
  currentAction.value ? msPerItem(currentAction.value, levelOf(currentAction.value.skill), bonus.value.speedBp) : 0,
)
const currentPerMs = computed(() => Number(state.value?.msPerItem) || perItemMs.value)
const currentXp = computed(() => xpText(currentAction.value?.xp, bonus.value.xpBp))
const currentYieldText = computed(() =>
  (currentAction.value?.yields || []).map((out) => `${itemName(out.item)} ×${out.qty}`).join(listSep.value),
)

const elapsedMs = computed(() => Math.max(0, nowTick.value - anchorAt.value))
/** 材料断了就不再让进度条空转(服务端那一刻的 progressMs 是 0,动画只是误导) */
const currentMaterialsOut = computed(() => {
  if (!currentAction.value) return false
  return (
    actionState(
      currentAction.value,
      levelOf(currentAction.value.skill),
      inventory.value,
      buildingLevel(currentAction.value.requireBuilding),
    ) === 'MATERIALS'
  )
})
const progress = computed(() =>
  currentMaterialsOut.value ? { ratio: 0, remainingMs: 0, due: 0 } : progressOf(state.value, elapsedMs.value),
)
const remainingSec = computed(() => Math.max(1, Math.ceil(progress.value.remainingMs / 1000)))
const speedPercent = computed(() => Math.round((Number(state.value?.speedBp) || 10000) / 100))

/** 进度条:一条 CSS 循环动画,时长 = 单次耗时、负延迟 = 已走的毫秒(状态同步时才重设) */
const progressStyle = computed(() => {
  if (!currentAction.value || currentPerMs.value <= 0) return {}
  const walked = Math.max(0, Number(player.value.progressMs) || 0)
  return { '--hs-per': currentPerMs.value + 'ms', '--hs-delay': '-' + walked + 'ms' }
})

const bonusChips = computed(() => {
  const rows = []
  const push = (key, bp) => {
    const value = Number(bp) || 0
    if (value <= 0) return
    rows.push(t('games.homestead.bonusValue', { label: t('games.homestead.bonus.' + key), p: bpToPercent(value) }))
  }
  push('speed', bonus.value.speedBp)
  push('xp', bonus.value.xpBp)
  push('yield', bonus.value.yieldBp)
  push('rare', bonus.value.rareBp)
  push('efficiency', bonus.value.efficiencyBp)
  push('sell', bonus.value.sellBonusBp)
  if (capHours.value > 24) rows.push(t('games.homestead.bonusCap', { label: t('games.homestead.bonus.cap'), h: capHours.value }))
  return rows
})

const buffRows = computed(() =>
  (state.value?.buffs || []).map((buff) => {
    const left = countdownParts(buffRemainingMs(buff, elapsedMs.value))
    return {
      item: buff.item,
      name: itemName(buff.item),
      kind: buff.kind,
      bp: Number(buff.bp) || 0,
      leftText: t('games.homestead.buffLeft', {
        m: left.minutes,
        s: String(left.seconds).padStart(2, '0'),
      }),
    }
  }),
)

const activeActions = computed(() =>
  (content.value.actions || [])
    .filter((def) => def.skill === activeSkill.value)
    .sort((a, b) => (a.requireBuilding ? 9000 : 0) + a.level - ((b.requireBuilding ? 9000 : 0) + b.level)),
)

const actionRows = computed(() =>
  activeActions.value.map((def) => {
    const level = levelOf(def.skill)
    const own = bonusOf(def.code)
    const perItem = msPerItem(def, level, own.speedBp)
    const inputs = def.inputs || []
    const missing = inputs.filter((need) => (inventory.value[need.item] || 0) < need.qty)
    const amountText = (need) => `${itemName(need.item)} ×${need.qty}`
    const state_ = actionState(def, level, inventory.value, buildingLevel(def.requireBuilding))
    const tool = toolByAction.value[def.code]
    const tier = tool ? tierByTool.value[tool.code] || 0 : 0
    let tagText = ''
    if (state_ === 'LOCKED') tagText = t('games.homestead.actionLocked', { n: def.level })
    else if (state_ === 'BUILDING') {
      const building = buildingMap.value[def.requireBuilding]
      tagText = t('games.homestead.actionBuilding', {
        name: building ? pickName(building, locale.value) : '',
        n: def.requireBuildingLevel,
      })
    } else if (state_ === 'MATERIALS') tagText = t('games.homestead.actionLack')
    return {
      code: def.code,
      name: pickName(def, locale.value),
      level: Number(def.level) || 1,
      xp: xpText(def.xp, own.xpBp),
      per: perText(perItem),
      state: state_,
      tagText,
      current: currentCode.value === def.code,
      inputsText: inputs.map(amountText).join(listSep.value),
      outputsText: (def.yields || []).map(amountText).join(listSep.value),
      rarePercent: def.rare ? chancePercent(Math.min(5000, Number(def.rare.chanceBp) + own.rareBp)) : 0,
      rareText: def.rare ? amountText(def.rare) : '',
      missingText: missing.map((need) => `${itemName(need.item)} ×${need.qty - (inventory.value[need.item] || 0)}`).join(listSep.value),
      toolText: tool ? t('games.homestead.toolTier', { n: tier, max: (tool.steps || []).length }) : '',
    }
  }),
)

// ---------- 物品栏 ----------
const market = computed(() => state.value?.market || { quota: 0, orders: [] })
const marketOrderMap = computed(() => {
  const out = {}
  for (const order of market.value.orders || []) out[order.code] = order
  return out
})
const marketBonusPercent = computed(() => Math.round((Number(market.value.bonusBp) || 0) / 100) - 100)
const marketOrders = computed(() =>
  (market.value.orders || []).map((order) => ({
    code: order.code,
    name: itemName(order.code),
    price: Number(order.price) || 0,
    remaining: Number(order.remaining) || 0,
  })),
)
/** 换货倒计时:以服务端 serverNow 为锚,本地只补流逝(和进度条同一套口径) */
const marketResetText = computed(() => {
  const resetAt = Number(market.value.resetAt) || 0
  const serverNow = Number(state.value?.serverNow) || 0
  if (!resetAt || !serverNow) return ''
  const left = resetAt - serverNow - elapsedMs.value
  if (left <= 0) return t('games.homestead.market.soon')
  const hours = Math.floor(left / 3600000)
  const minutes = Math.floor((left % 3600000) / 60000)
  return hours > 0
    ? t('games.homestead.market.resetHours', { h: hours, m: minutes })
    : t('games.homestead.market.resetMinutes', { m: Math.max(1, minutes) })
})

/** 加成效果文案(工具/装备/场地共用):基点转百分比,带目标类别时前置「采集·」 */
const effectText = (effect) => {
  if (!effect) return ''
  const text = t('games.homestead.effect.' + effect.kind, {
    p: bpToPercent(effect.bp),
    h: Number(effect.hours) || 0,
  })
  const target = effect.target
  if (!target || target === 'ALL' || target === 'ACTION') return text
  return t('games.homestead.effectTargeted', { target: t('games.homestead.effectTarget.' + target), text })
}
const effectsText = (list) => (list || []).map(effectText).filter(Boolean).join(listSep.value)

const bagGroups = computed(() =>
  BAG_CATEGORIES.map((category) => ({
    category,
    rows: (state.value?.items || [])
      .filter((row) => (itemMap.value[row.code]?.category || 'MATERIAL') === category)
      .map((row) => {
        const def = itemMap.value[row.code] || {}
        const order = marketOrderMap.value[row.code]
        const boosted = !!order && Number(order.remaining) > 0
        const buff = def.buff
        return {
          code: row.code,
          name: pickName(def, locale.value),
          qty: Number(row.qty) || 0,
          // 赶集日额度内显示溢价,再叠上卖价加成(实际入账仍以服务端返回为准)
          price: sellCoinsWithBonus((boosted ? order.price : def.sellPrice) || 0, bonus.value.sellBonusBp),
          boosted,
          consumable: !!buff,
          buffText: buff
            ? t('games.homestead.buffText', {
                kind: t('games.homestead.buffKind.' + buff.kind),
                p: bpToPercent(buff.bp),
                m: buff.minutes,
              })
            : '',
        }
      }),
  })).filter((group) => group.rows.length > 0),
)
const itemKinds = computed(() => bagGroups.value.reduce((sum, group) => sum + group.rows.length, 0))

// ---------- 工具与穿戴 ----------
/** 花费文案 + 缺口文案(工具/装备/场地共用) */
function costTextOf(step) {
  if (!step) return ''
  const mats = (step.materials || []).map((need) => `${itemName(need.item)} ×${need.qty}`)
  return mats.length
    ? t('games.homestead.costItems', { coins: compact(step.coins), items: mats.join(listSep.value) })
    : t('games.homestead.costCoins', { coins: compact(step.coins) })
}
function lackTextOf(gap) {
  const rows = []
  if (gap.missingCoins > 0) rows.push(`${t('games.homestead.coins')} ×${compact(gap.missingCoins)}`)
  for (const need of gap.missingItems || []) {
    rows.push(`${itemName(need.item)} ×${need.qty - (inventory.value[need.item] || 0)}`)
  }
  return rows.join(listSep.value)
}

const toolRows = computed(() =>
  activeActions.value.map((actionDef) => {
    const tool = toolByAction.value[actionDef.code]
    if (!tool) {
      return {
        code: actionDef.code,
        name: '',
        actionName: pickName(actionDef, locale.value),
        tier: 0,
        max: 0,
        maxed: true,
        ok: false,
        costText: '',
        lackText: '',
        levelOk: true,
        requireLevel: 1,
        effectText: '',
      }
    }
    const tier = tierByTool.value[tool.code] || 0
    const steps = tool.steps || []
    const step = steps[tier] || null
    const gap = costGap(step, coins.value, inventory.value)
    const levelOk = levelOf(tool.skill) >= (Number(tool.requireLevel) || 1)
    return {
      code: tool.code,
      name: pickName(tool, locale.value),
      actionName: pickName(actionDef, locale.value),
      tier,
      max: steps.length,
      maxed: gap.maxed,
      ok: gap.ok && levelOk,
      costText: costTextOf(step),
      lackText: lackTextOf(gap),
      levelOk,
      requireLevel: Number(tool.requireLevel) || 1,
      effectText: effectsText(tool.effects),
    }
  }),
)

const gearGroups = computed(() =>
  (content.value.gearSlots || []).map((slot) => ({
    slot,
    name: t('games.homestead.slot.' + slot),
    items: (content.value.equipment || [])
      .filter((gear) => gear.slot === slot)
      .map((gear) => {
        const owned = Object.prototype.hasOwnProperty.call(enhanceByGear.value, gear.code)
        const enhance = owned ? enhanceByGear.value[gear.code] : 0
        const max = (gear.steps || []).length
        const maxed = owned && enhance >= max
        const step = owned ? gear.steps?.[enhance] : gear.craft
        const gap = costGap(maxed ? null : step, coins.value, inventory.value)
        const levelOk = levelOf(gear.skill) >= (Number(gear.requireLevel) || 1)
        const worn = wornBySlot.value[slot] === gear.code
        return {
          code: gear.code,
          name: pickName(gear, locale.value),
          owned,
          worn,
          enhance,
          max,
          maxed,
          canWear: levelOk,
          levelOk,
          requireLevel: Number(gear.requireLevel) || 1,
          effectText: effectsText(gear.effects),
          costText: costTextOf(maxed ? null : step),
          lackText: maxed ? '' : lackTextOf(gap),
          ok: !maxed && gap.ok && levelOk,
          stateText: !owned
            ? t('games.homestead.level', { n: gear.requireLevel })
            : maxed
              ? t('games.homestead.toolMaxed')
              : t('games.homestead.gear.enhanceLevel', { n: enhance, max }),
          buttonText: !owned ? t('games.homestead.gear.craft') : t('games.homestead.gear.enhance'),
        }
      }),
  })),
)

// ---------- 场地 ----------
const buildingRows = computed(() =>
  (content.value.buildings || []).map((def) => {
    const level = buildingLevel(def.code)
    const maxLevel = Number(def.maxLevel) || 10
    const maxed = level >= maxLevel
    const step = maxed ? null : def.steps?.[level]
    const gap = costGap(step, coins.value, inventory.value)
    const action = def.unlockAction ? actionMap.value[def.unlockAction] : null
    return {
      code: def.code,
      name: pickName(def, locale.value),
      icon: def.icon || '🏠',
      level,
      maxLevel,
      maxed,
      ok: !maxed && gap.ok,
      costText: costTextOf(step),
      lackText: maxed ? '' : lackTextOf(gap),
      effectText: effectsText(def.effects),
      unlockText: action ? t('games.homestead.yard.unlock', { name: pickName(action, locale.value) }) : '',
    }
  }),
)

// ---------- 离线摘要文案 ----------
const offlineTime = computed(() => {
  if (!offlineInfo.value) return ''
  const span = elapsedText(offlineInfo.value.elapsedMs)
  if (span.unit === 'minute') return t('games.homestead.duration.minute', { n: span.value })
  if (span.unit === 'hourMinute') return t('games.homestead.duration.hourMinute', { h: span.value, m: span.rest })
  return t('games.homestead.duration.hour', { n: span.value })
})
const offlineItems = computed(() => {
  const rows = offlineInfo.value?.items || []
  return rows.map((row) => `${itemName(row.code)} ×${compact(row.qty)}`).join(listSep.value)
})

// ---------- 取数与写操作 ----------
const opKey = () =>
  globalThis.crypto?.randomUUID ? globalThis.crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`

function applyState(res) {
  if (!res) return
  state.value = res
  anchorAt.value = Date.now()
  nowTick.value = anchorAt.value
  if (!selectedSkill.value) {
    selectedSkill.value = res.player?.actionCode
      ? actionMap.value[res.player.actionCode]?.skill || ''
      : res.content?.skills?.[0]?.code || ''
  }
  if (res.offline && res.offline.completions > 0) {
    offlineInfo.value = res.offline
    offlineOpen.value = true
  }
}

async function loadState() {
  loadError.value = false
  loading.value = true
  try {
    applyState(await homesteadApi.state())
  } catch {
    // 拦截器已经提示过,这里只负责给页面一个重试入口
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 对账:静默失败(网络抖一下不该把整页打成错误态) */
async function refreshState() {
  if (syncing.value || busy.value) return
  syncing.value = true
  syncedAt = Date.now()
  try {
    applyState(await homesteadApi.state())
  } catch {
    /* 下一次 tick / focus 再试 */
  } finally {
    syncing.value = false
  }
}

/** 写操作统一入口:结果直接是全量状态,不用再补一次 /state */
async function runOp(call) {
  if (busy.value) return
  busy.value = true
  try {
    applyState(await call(opKey()))
  } catch {
    /* 拦截器已提示失败原因(等级不够/材料不够/铜板不够等) */
  } finally {
    busy.value = false
  }
}

const startAction = (code) => {
  const def = actionMap.value[code]
  if (!def) return
  const stateOf = actionState(def, levelOf(def.skill), inventory.value, buildingLevel(def.requireBuilding))
  if (stateOf !== 'READY') return
  runOp((key) => homesteadApi.action(code, key))
}
const stopAction = () => runOp((key) => homesteadApi.stop(key))
const sellItem = (code, qty) => {
  if (qty > 0) runOp((key) => homesteadApi.sell(code, qty, key))
}
const eatItem = (code) => runOp((key) => homesteadApi.consume(code, 1, key))
const craftTool = (code) => runOp((key) => homesteadApi.tool(code, key))
const gearAction = (gear) => {
  if (!gear.owned) runOp((key) => homesteadApi.craftGear(gear.code, key))
  else if (!gear.maxed) runOp((key) => homesteadApi.enhanceGear(gear.code, key))
}
const toggleWear = (gear) => {
  const slot = gearMap.value[gear.code]?.slot
  if (!slot) return
  if (gear.worn) runOp((key) => homesteadApi.wear(slot, '', key))
  else runOp((key) => homesteadApi.wear(slot, gear.code, key))
}
const upgradeBuilding = (code) => runOp((key) => homesteadApi.building(code, key))

async function openRanking() {
  rankOpen.value = true
  rankLoading.value = true
  rankFailed.value = false
  try {
    const res = await homesteadApi.ranking()
    const rows = Array.isArray(res?.ranking) ? [...res.ranking] : []
    // 自己排在榜尾之外(家庭人多时)也要能看到自己的名次,否则榜单没有参照
    if (res?.me && !rows.some((row) => row.isMe || row.userId === res.me.userId)) rows.push(res.me)
    rankRows.value = rows
  } catch {
    rankFailed.value = true
    rankRows.value = []
  } finally {
    rankLoading.value = false
  }
}

const goBack = () => router.push('/games')

// ---------- 生命周期 ----------
let tickTimer = 0
let pollTimer = 0

const tick = () => {
  nowTick.value = Date.now()
  if (progress.value.due >= 1 && Date.now() - syncedAt > DUE_SYNC_GAP_MS) refreshState()
}
const onVisibility = () => {
  if (document.visibilityState === 'visible') refreshState()
}

onMounted(() => {
  loadState()
  tickTimer = window.setInterval(tick, TICK_MS)
  pollTimer = window.setInterval(refreshState, POLL_MS)
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('focus', refreshState)
})

onBeforeUnmount(() => {
  window.clearInterval(tickTimer)
  window.clearInterval(pollTimer)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('focus', refreshState)
})
</script>

<style scoped>
/* 页面结构:工作门类(选择器) | 当前工作 + 具体工作卡片 | 物品/工具/场地三个子页签。
   宽屏三块各自滚动;中等宽度门类收成顶部横条、整块纵向滚动;手机上收成四个页签。 */
.hs-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--gs-gap);
  width: 100%;
  height: 100%;
  min-height: 0;
}
.hs-tabs {
  display: none;
  flex: 0 0 auto;
  gap: 6px;
}
.hs-tab {
  flex: 1 1 0;
  height: var(--gs-control-h);
  padding: 0 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.hs-tab.is-on {
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 10%);
  color: var(--gs-accent);
  font-weight: 600;
}
/* 头部「家庭榜」键:窄屏只留图标(与规则键同口径) */
.hs-btn-label {
  margin-left: 4px;
}
@media (max-width: 480px) {
  .hs-btn-label {
    display: none;
  }
}
.hs-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  height: 100%;
  padding: 12px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.hs-grid {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  /* 站点内容列最宽 836px:168 门类 + 中间工作区 + 254~300 右栏 */
  grid-template-columns: 168px minmax(0, 1fr) clamp(252px, 32%, 300px);
  gap: var(--gs-gap);
}

/* --- 工作门类 --- */
.hs-rail {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.hs-skill {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 45%);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;
}
.hs-skill.is-on {
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 9%);
}
.hs-skill-icon {
  flex: 0 0 auto;
  font-size: 15px;
  line-height: 1;
}
.hs-skill-body {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.hs-skill-name {
  overflow: hidden;
  font-size: 12.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-skill.is-working .hs-skill-name::after {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-left: 4px;
  border-radius: 50%;
  background: var(--gs-accent);
  vertical-align: middle;
  animation: hs-pulse 1.8s ease-in-out infinite;
}
@keyframes hs-pulse {
  50% {
    opacity: 0.25;
  }
}
.hs-skill-bar {
  display: block;
  height: 3px;
  border-radius: 2px;
  background: rgb(var(--color-brand-rgb) / 14%);
  overflow: hidden;
}
.hs-skill-bar b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--gs-accent);
  transition: width 0.4s ease;
}
.hs-skill-lv {
  flex: 0 0 auto;
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}

/* --- 中间:当前工作 + 工作卡片 --- */
.hs-main {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--gs-gap);
  overflow-y: auto;
  overscroll-behavior: contain;
}
.hs-now {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 9px 11px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 55%);
}
.hs-now.is-warn {
  border-color: rgb(var(--color-danger-rgb) / 35%);
}
.hs-now-top {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hs-now-icon {
  flex: 0 0 auto;
  font-size: 20px;
  line-height: 1;
}
.hs-now-text {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hs-now-title {
  overflow: hidden;
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-now-sub {
  overflow: hidden;
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-progress {
  position: relative;
  height: 6px;
  border-radius: 4px;
  background: rgb(var(--color-brand-rgb) / 14%);
  overflow: hidden;
}
/* 连续进度:一个循环的 scaleX 动画,时长=单次耗时、负延迟=已走部分;不占 JS 帧 */
.hs-progress-fill {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  background: var(--gs-accent);
  transform-origin: left center;
  animation: hs-sweep var(--hs-per, 3000ms) linear infinite;
  animation-delay: var(--hs-delay, 0ms);
}
@keyframes hs-sweep {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}
.hs-now-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  min-height: 14px;
  font-size: 11px;
  color: var(--color-text-secondary);
}
.hs-now-left {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}
.hs-now-out {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-tertiary);
}
.hs-now-hint {
  min-width: 0;
  color: var(--color-danger);
}
.hs-now-bonus {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.hs-bonus {
  padding: 0 6px;
  border-radius: 4px;
  background: rgb(var(--color-brand-rgb) / 12%);
  color: var(--gs-accent);
  font-size: 10.5px;
  line-height: 17px;
  font-weight: 600;
}
.hs-buffs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
}
.hs-buffs-label {
  flex: 0 0 auto;
  font-weight: 600;
}
.hs-buff {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  padding: 0 6px;
  border-radius: 4px;
  background: rgb(var(--color-card-rgb) / 60%);
  border: 1px solid var(--color-border);
  line-height: 17px;
}
.hs-buff em {
  font-style: normal;
  font-variant-numeric: tabular-nums;
  color: var(--gs-accent);
}
.hs-buff.is-xp {
  color: var(--color-text-secondary);
}
.hs-buff-empty {
  opacity: 0.75;
}
.hs-cards {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(156px, 1fr));
  align-content: start;
  gap: 6px;
}
.hs-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 9px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 45%);
}
.hs-card.is-current {
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 8%);
}
.hs-card.is-locked,
.hs-card.is-building {
  opacity: 0.66;
}
.hs-card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}
.hs-card-name {
  font-size: 12.5px;
  font-weight: 600;
}
.hs-card-lv {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.hs-card-tag {
  align-self: flex-start;
  margin: 0;
  padding: 0 5px;
  border-radius: 4px;
  font-size: 10px;
  line-height: 16px;
  background: rgb(var(--color-brand-rgb) / 12%);
  color: var(--color-text-secondary);
}
.hs-card-tag.is-materials {
  background: rgb(var(--color-danger-rgb) / 12%);
  color: var(--color-danger);
}
.hs-card-line {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.45;
  color: var(--color-text-tertiary);
}
.hs-card-line.is-rare {
  color: var(--color-warning);
}
.hs-card-line.is-warn {
  color: var(--color-danger);
}
.hs-card-line.is-tool {
  color: var(--color-text-secondary);
}
.hs-card-go {
  margin-top: 2px;
  height: 26px;
  border: 0;
  border-radius: 8px;
  background: var(--gs-accent);
  color: var(--color-brand-text);
  font: inherit;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}
.hs-card-go.is-stop {
  background: none;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}
.hs-card-go:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* --- 右栏:三个子页签 --- */
.hs-side {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hs-subtabs {
  flex: 0 0 auto;
  display: flex;
  gap: 5px;
}
.hs-subtab {
  flex: 1 1 0;
  height: 26px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.hs-subtab.is-on {
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 10%);
  color: var(--gs-accent);
  font-weight: 600;
}
.hs-side-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.hs-pane {
  display: flex;
  flex-direction: column;
  gap: var(--gs-gap);
}
.hs-pane-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin: 0 0 2px;
  font-size: 12.5px;
  font-weight: 700;
}
.hs-pane-title em {
  font-size: 10px;
  font-style: normal;
  font-weight: 400;
  line-height: 1.35;
  text-align: right;
  color: var(--color-text-tertiary);
}
.hs-sub {
  margin: 4px 0 0;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--color-text-tertiary);
}
.hs-empty {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

/* --- 赶集日 --- */
.hs-market {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 7px 9px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  background: rgb(var(--color-card-rgb) / 35%);
}
.hs-market-hint {
  margin: 0 0 1px;
  font-size: 10.5px;
  line-height: 1.55;
  color: var(--color-text-tertiary);
}
.hs-market-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}
.hs-market-row.is-done {
  opacity: 0.55;
}
.hs-market-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-market-price {
  flex: 0 0 auto;
  color: var(--color-warning);
  font-variant-numeric: tabular-nums;
}
.hs-market-left {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* --- 物品 --- */
.hs-bag {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hs-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 5px 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 40%);
  font-size: 12px;
}
.hs-item-top {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.hs-item-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-item-qty {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}
.hs-item-price {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}
.hs-item-price.is-market {
  color: var(--color-warning);
  font-weight: 600;
}
.hs-item-buff {
  margin: 0;
  font-size: 10px;
  line-height: 1.4;
  color: var(--gs-accent);
}
.hs-item-acts {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}

/* --- 工具 / 穿戴 / 场地(同一张紧凑卡) --- */
.hs-tools,
.hs-gear,
.hs-yard {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.hs-tool {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 7px 9px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 40%);
}
.hs-tool.is-maxed {
  background: rgb(var(--color-brand-rgb) / 7%);
}
.hs-tool.is-worn {
  border-color: var(--gs-accent);
}
.hs-tool-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.hs-tool-icon {
  flex: 0 0 auto;
  font-size: 14px;
  line-height: 1;
}
.hs-tool-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 12.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-tool-tier {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.hs-tool-sub {
  margin: 0;
  font-size: 10px;
  color: var(--color-text-tertiary);
}
.hs-steps {
  display: flex;
  gap: 3px;
}
.hs-steps b {
  flex: 1 1 0;
  height: 3px;
  border-radius: 2px;
  background: rgb(var(--color-brand-rgb) / 14%);
}
.hs-steps b.is-on {
  background: var(--gs-accent);
}
.hs-tool-effect {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--gs-accent);
}
.hs-tool-desc {
  margin: 0;
  font-size: 10px;
  line-height: 1.45;
  color: var(--color-text-tertiary);
}
.hs-tool-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.hs-tool-btns {
  flex: 0 0 auto;
  display: flex;
  gap: 4px;
}
.hs-tool-cost {
  min-width: 0;
  font-size: 10px;
  line-height: 1.45;
  color: var(--color-text-secondary);
}
.hs-tool-cost em {
  display: block;
  font-style: normal;
  color: var(--color-danger);
}
.hs-yard-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 5px;
}
.hs-mini {
  height: 22px;
  padding: 0 7px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 11px;
  white-space: nowrap;
  cursor: pointer;
}
.hs-mini.is-main {
  border-color: transparent;
  background: rgb(var(--color-brand-rgb) / 12%);
  color: var(--gs-accent);
  font-weight: 600;
}
.hs-mini:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* --- 弹窗 --- */
.hs-dialog-lead {
  margin: 0 0 8px;
  font-size: 13px;
  line-height: 1.6;
}
.hs-dialog-note {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}
.hs-dialog-note.is-warn {
  color: var(--color-danger);
}
.hs-offline-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  line-height: 1.8;
}
.hs-rank {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hs-rank-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 9px;
  border-radius: 8px;
  font-size: 12.5px;
}
.hs-rank-row.is-me {
  background: rgb(var(--color-brand-rgb) / 10%);
  font-weight: 600;
}
.hs-rank-no {
  flex: 0 0 22px;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}
.hs-rank-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-rank-name em {
  margin-left: 5px;
  font-size: 10.5px;
  font-style: normal;
  font-weight: 400;
  color: var(--gs-accent);
}
.hs-rank-score {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}
.hs-rank-loading {
  padding: 6px 0;
}

/* --- 中等宽度(容器 <780):门类变顶部横条,三块纵向堆叠 ---
   断点按游戏区实际宽度定而不是视口:站点内容列最宽 836px,写 979 的话桌面永远进不了三栏 */
@container (max-width: 779px) {
  .hs-grid {
    display: block;
    overflow-y: auto;
    overscroll-behavior: contain;
  }
  .hs-rail {
    /* 换行而不是横向滚动条:窄桌面/平板上滚轮推不动横条(UI §11b 也不鼓励裸横滚) */
    flex-direction: row;
    flex-wrap: wrap;
    gap: 6px;
    margin-bottom: var(--gs-gap);
    overflow: visible;
  }
  .hs-skill {
    flex: 1 1 128px;
  }
  .hs-main,
  .hs-side-body {
    overflow: visible;
  }
  .hs-side {
    margin-top: var(--gs-gap);
  }
  .hs-subtabs {
    margin-bottom: 6px;
  }
  .hs-cards {
    grid-template-columns: repeat(auto-fill, minmax(172px, 1fr));
  }
  .hs-yard-grid {
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  }
}

/* --- 手机(容器 <660):四个页签,一次只看一块 --- */
@container (max-width: 659px) {
  .hs-tabs {
    display: flex;
  }
  /* 工作页高度有限,门类回一行可横划的条(触屏习惯动作);隐藏原生滚动条,不占布局盒 */
  .hs-rail {
    flex-direction: row;
    flex-wrap: nowrap;
    gap: 6px;
    margin-bottom: var(--gs-gap);
    overflow-x: auto;
    overflow-y: hidden;
    scrollbar-width: none;
  }
  .hs-rail::-webkit-scrollbar {
    display: none;
  }
  .hs-skill {
    flex: 0 0 138px;
  }
  /* 手机上一屏只看一块:侧栏自己的三个子页签收起(顶部主标签已经在切) */
  .hs-subtabs {
    display: none;
  }
  .hs-wrap[data-pane='WORK'] .hs-side,
  .hs-wrap[data-pane='BAG'] .hs-rail,
  .hs-wrap[data-pane='BAG'] .hs-main,
  .hs-wrap[data-pane='TOOLS'] .hs-rail,
  .hs-wrap[data-pane='TOOLS'] .hs-main,
  .hs-wrap[data-pane='YARD'] .hs-rail,
  .hs-wrap[data-pane='YARD'] .hs-main {
    display: none;
  }
  .hs-side {
    margin-top: 0;
  }
}

/* 鼠标设备(含窄窗口的桌面)不靠横划取门类:把门类条换成换行铺开,滚轮即可看到全部 */
@media (hover: hover) {
  @container (max-width: 659px) {
    .hs-rail {
      flex-wrap: wrap;
      overflow: visible;
    }
    .hs-skill {
      flex: 1 1 128px;
    }
  }
}
</style>
