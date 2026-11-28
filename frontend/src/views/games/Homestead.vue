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
        ><b>{{ $t('games.homestead.offlineCapValue') }}</b></span
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
          :class="{ 'is-on': pane === tab.value }"
          :aria-selected="pane === tab.value"
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
        <!-- 技能列:选一个技能,右栏只出它的动作 -->
        <div class="hs-rail">
          <button
            v-for="skill in skills"
            :key="skill.code"
            type="button"
            class="hs-skill"
            :class="{ 'is-on': skill.code === activeSkill, 'is-working': skill.code === workingSkill }"
            @click="selectedSkill = skill.code"
          >
            <span class="hs-skill-row">
              <span class="hs-skill-name">{{ skill.name }}</span>
              <b class="hs-skill-lv">{{ $t('games.homestead.level', { n: skill.level }) }}</b>
            </span>
            <i class="hs-skill-bar"><b :style="{ width: skill.percent + '%' }"></b></i>
            <span class="hs-skill-meta">
              <span>{{ $t('games.homestead.kind.' + skill.kind) }}</span>
              <span v-if="skill.toolTier > 0">{{ $t('games.homestead.toolShort', { n: skill.toolTier }) }}</span>
            </span>
          </button>
        </div>

        <div class="hs-main">
          <div class="hs-actions">
            <!-- 当前动作:进度条由本地按服务端锚点插值,到点就提前对一次账 -->
            <header class="hs-now" :class="{ 'is-warn': currentMaterialsOut }">
              <div class="hs-now-row">
                <span class="hs-now-label">
                  {{
                    currentAction ? $t('games.homestead.working', { name: pickName(currentAction, locale) }) : $t('games.homestead.idle')
                  }}
                </span>
                <span v-if="currentAction && !currentMaterialsOut" class="hs-now-left">
                  {{ $t('games.homestead.remaining', { s: remainingSec }) }}
                </span>
                <el-button v-if="currentAction" size="small" round :disabled="busy" @click="stopAction">
                  {{ $t('games.homestead.stop') }}
                </el-button>
              </div>
              <i v-if="currentAction" class="hs-bar"><b :style="{ width: barPercent + '%' }"></b></i>
              <p v-if="!currentAction" class="hs-now-hint">{{ $t('games.homestead.idleHint') }}</p>
              <p v-else-if="currentMaterialsOut" class="hs-now-hint">{{ $t('games.homestead.materialsAuto') }}</p>
              <p v-else class="hs-now-hint">{{ activeSkillDesc }}</p>
            </header>

            <div class="hs-act-list">
              <div
                v-for="row in actionRows"
                :key="row.code"
                class="hs-act"
                :class="['is-' + row.state.toLowerCase(), { 'is-current': row.current }]"
              >
                <div class="hs-act-info">
                  <span class="hs-act-head">
                    <span class="hs-act-name">{{ row.name }}</span>
                    <span v-if="row.state === 'LOCKED'" class="hs-act-tag is-lock">
                      {{ $t('games.homestead.actionLocked', { n: row.level }) }}
                    </span>
                    <span v-else-if="row.state === 'MATERIALS'" class="hs-act-tag is-warn">
                      {{ $t('games.homestead.actionLack') }}
                    </span>
                    <span v-else-if="row.current" class="hs-act-tag is-live">{{ $t('games.homestead.current') }}</span>
                  </span>
                  <span class="hs-act-meta">{{ $t('games.homestead.actionMeta', { s: row.per, xp: row.xp }) }}</span>
                  <span v-if="row.inputsText" class="hs-act-io">{{ $t('games.homestead.actionIn', { list: row.inputsText }) }}</span>
                  <span class="hs-act-io">{{ $t('games.homestead.actionOut', { list: row.outputsText }) }}</span>
                  <span v-if="row.rareText" class="hs-act-io is-rare">
                    {{ $t('games.homestead.actionRare', { list: row.rareText, n: row.rareEvery }) }}
                  </span>
                  <span v-if="row.missingText" class="hs-act-io is-warn">
                    {{ $t('games.homestead.actionMissing', { list: row.missingText }) }}
                  </span>
                </div>
                <button
                  type="button"
                  class="hs-act-go"
                  :class="{ 'is-stop': row.current }"
                  :disabled="busy || (!row.current && row.state !== 'READY')"
                  @click="row.current ? stopAction() : startAction(row.code)"
                >
                  {{ row.current ? $t('games.homestead.stop') : $t('games.homestead.start') }}
                </button>
              </div>
            </div>
          </div>

          <div class="hs-side">
            <!-- 背包:素材/成品/特产分组,单击卖一个、全卖一键清空 -->
            <section class="hs-bag">
              <h2 class="hs-pane-title">
                <span>{{ $t('games.homestead.bag') }}</span>
                <em v-if="itemKinds">{{ $t('games.homestead.kinds', { n: itemKinds }) }}</em>
              </h2>
              <p v-if="!bagGroups.length" class="hs-empty">{{ $t('games.homestead.bagEmpty') }}</p>
              <template v-for="group in bagGroups" :key="group.category">
                <h3 class="hs-sub">{{ $t('games.homestead.category.' + group.category) }}</h3>
                <div v-for="item in group.rows" :key="item.code" class="hs-item">
                  <span class="hs-item-name">{{ item.name }}</span>
                  <b class="hs-item-qty">×{{ compact(item.qty) }}</b>
                  <span class="hs-item-price">{{ $t('games.homestead.sellPrice', { n: compact(item.price) }) }}</span>
                  <span class="hs-item-btns">
                    <button type="button" class="hs-mini" :disabled="busy" @click="sellItem(item.code, 1)">
                      {{ $t('games.homestead.sellOne') }}
                    </button>
                    <button type="button" class="hs-mini is-main" :disabled="busy" @click="sellItem(item.code, item.qty)">
                      {{ $t('games.homestead.sellAll') }}
                    </button>
                  </span>
                </div>
              </template>
            </section>

            <!-- 工具:每件对应一个技能,升一阶涨 8% 速度 -->
            <section class="hs-tools">
              <h2 class="hs-pane-title">
                <span>{{ $t('games.homestead.tools') }}</span>
                <em>{{ $t('games.homestead.toolHint') }}</em>
              </h2>
              <div v-for="tool in toolRows" :key="tool.code" class="hs-tool" :class="{ 'is-maxed': tool.maxed }">
                <div class="hs-tool-head">
                  <span class="hs-tool-name">{{ tool.name }}</span>
                  <b class="hs-tool-tier">
                    {{ tool.maxed ? $t('games.homestead.toolMaxed') : $t('games.homestead.toolTier', { n: tool.tier, max: tool.max }) }}
                  </b>
                </div>
                <i class="hs-tool-steps">
                  <b v-for="step in tool.max" :key="step" :class="{ 'is-on': step <= tool.tier }"></b>
                </i>
                <p v-if="tool.desc" class="hs-tool-desc">{{ tool.desc }}</p>
                <div class="hs-tool-foot">
                  <span class="hs-tool-cost">
                    {{ tool.costText }}
                    <em v-if="tool.lackText">{{ $t('games.homestead.toolLack', { list: tool.lackText }) }}</em>
                  </span>
                  <button type="button" class="hs-mini is-main" :disabled="busy || !tool.ok" @click="upgradeTool(tool.code)">
                    {{ tool.maxed ? $t('games.homestead.toolMaxed') : $t('games.homestead.toolUpgrade') }}
                  </button>
                </div>
              </div>
            </section>
          </div>
        </div>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
// 小院慢生活(放置):服务端存档 + 离线结算,客户端只渲染状态、按服务端锚点插值进度环。
// 产出/经验/稀有掉落一律以 /state 为准(utils/games/homestead.js 只放展示侧纯函数),
// 本地不模拟收益 —— 否则会和 HomesteadEngine 变成两套引擎,离线回来数字对不上。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Trophy } from '@element-plus/icons-vue'
import GameShell from '@/components/games/GameShell.vue'
import { homesteadApi } from '@/api'
import {
  actionState,
  elapsedText,
  formatCompact,
  hasMaterials,
  msPerItem,
  pickName,
  progressOf,
  toolUpgradeCheck,
  xpRatio,
} from '@/utils/games/homestead'

/** 站在页面里对账的节奏:进度环 1 秒重画一次,状态 60 秒拉一次(写操作直接回全量状态) */
const TICK_MS = 1000
const POLL_MS = 60000
/** 本地插值到点后提前对账,两次请求至少隔这么久(服务端才是入账方,这里只是让数字早点跟上) */
const DUE_SYNC_GAP_MS = 1500
const TABS = [
  { value: 'SKILLS', key: 'games.homestead.tabSkills' },
  { value: 'BAG', key: 'games.homestead.tabBag' },
  { value: 'TOOLS', key: 'games.homestead.tabTools' },
]
const BAG_CATEGORIES = ['MATERIAL', 'PRODUCT', 'RARE']
/** 工具阶数上限由内容表决定,这里只留一个兜底(内容缺失时不至于渲染出空槽) */
const FALLBACK_TIERS = 5

const router = useRouter()
const { t, locale } = useI18n()

const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)

const state = ref(null)
const loading = ref(true)
const loadError = ref(false)
const busy = ref(false)
const syncing = ref(false)
const pane = ref('SKILLS')
const selectedSkill = ref('')

/** 进度环插值锚点:拿到 /state 的本地时刻;nowTick 每秒推进 */
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
const content = computed(() => state.value?.content || { skills: [], items: [], actions: [], tools: [] })
const player = computed(() => state.value?.player || {})
const coins = computed(() => Number(player.value.coins) || 0)
const index = computed(() => Number(state.value?.index) || 0)
const currentCode = computed(() => player.value.actionCode || null)
const compact = (value) => formatCompact(value, locale.value)
const listSep = computed(() => t('games.homestead.listSep'))

const codeMap = (list) => {
  const out = {}
  for (const def of list || []) out[def.code] = def
  return out
}
const skillMap = computed(() => codeMap(content.value.skills))
const itemMap = computed(() => codeMap(content.value.items))
const actionMap = computed(() => codeMap(content.value.actions))

const itemName = (code) => pickName(itemMap.value[code], locale.value)
const pickDesc = (def) => {
  if (!def) return ''
  const en = String(locale.value).toLowerCase().startsWith('en')
  return (en && def.descEn) || def.desc || ''
}

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
const tierBySkill = computed(() => {
  const out = {}
  for (const def of content.value.tools || []) out[def.skill] = tierByTool.value[def.code] || 0
  return out
})
const inventory = computed(() => {
  const out = {}
  for (const row of state.value?.items || []) out[row.code] = Number(row.qty) || 0
  return out
})
const levelOf = (skillCode) => levelBySkill.value[skillCode] || 1

const skills = computed(() =>
  (state.value?.skills || []).map((row) => {
    const def = skillMap.value[row.code] || {}
    return {
      code: row.code,
      name: pickName(def, locale.value),
      kind: def.kind || 'GATHER',
      level: Number(row.level) || 1,
      percent: xpRatio(row) * 100,
      toolTier: tierBySkill.value[row.code] || 0,
    }
  }),
)

const currentAction = computed(() => (currentCode.value ? actionMap.value[currentCode.value] || null : null))
const workingSkill = computed(() => currentAction.value?.skill || '')
const activeSkill = computed(() => selectedSkill.value || workingSkill.value || skills.value[0]?.code || '')
const activeSkillDesc = computed(() => pickDesc(skillMap.value[activeSkill.value]))
const skillActions = computed(() =>
  (content.value.actions || []).filter((def) => def.skill === activeSkill.value).sort((a, b) => a.level - b.level),
)

const elapsedMs = computed(() => Math.max(0, nowTick.value - anchorAt.value))
/** 材料断了就不再让进度环空转(服务端的 progressMs 那一刻是 0,动画只是误导) */
const currentMaterialsOut = computed(() => !!currentAction.value && !hasMaterials(currentAction.value, inventory.value))
const progress = computed(() =>
  currentMaterialsOut.value ? { ratio: 0, remainingMs: 0, due: 0 } : progressOf(state.value, elapsedMs.value),
)
const barPercent = computed(() => Math.round(progress.value.ratio * 100))
const remainingSec = computed(() => Math.max(1, Math.ceil(progress.value.remainingMs / 1000)))
const speedPercent = computed(() => Math.round((Number(state.value?.speedBp) || 10000) / 100))

const actionRows = computed(() =>
  skillActions.value.map((def) => {
    const level = levelOf(def.skill)
    const tier = tierBySkill.value[def.skill] || 0
    const perItem = msPerItem(def, level, tier)
    const inputs = def.inputs || []
    const missing = inputs.filter((need) => (inventory.value[need.item] || 0) < need.qty)
    const amountText = (need) => `${itemName(need.item)} ×${need.qty}`
    return {
      code: def.code,
      name: pickName(def, locale.value),
      level: Number(def.level) || 1,
      xp: Number(def.xp) || 0,
      per: (perItem / 1000).toFixed(perItem % 1000 === 0 ? 0 : 1),
      state: actionState(def, level, inventory.value),
      current: currentCode.value === def.code,
      inputsText: inputs.map(amountText).join(listSep.value),
      outputsText: (def.yields || []).map(amountText).join(listSep.value),
      rareText: def.rare ? amountText(def.rare) : '',
      rareEvery: def.rare?.every || 0,
      missingText: missing.map((need) => `${itemName(need.item)} ×${need.qty - (inventory.value[need.item] || 0)}`).join(listSep.value),
    }
  }),
)

const bagGroups = computed(() => {
  const rows = (state.value?.items || []).map((row) => {
    const def = itemMap.value[row.code] || {}
    return {
      code: row.code,
      name: pickName(def, locale.value),
      qty: Number(row.qty) || 0,
      price: Number(def.sellPrice) || 0,
      category: def.category || 'MATERIAL',
    }
  })
  return BAG_CATEGORIES.map((category) => ({ category, rows: rows.filter((row) => row.category === category) })).filter(
    (group) => group.rows.length > 0,
  )
})
const itemKinds = computed(() => bagGroups.value.reduce((sum, group) => sum + group.rows.length, 0))

const toolRows = computed(() =>
  (content.value.tools || []).map((def) => {
    const tier = tierByTool.value[def.code] || 0
    const steps = def.steps || []
    const step = steps[tier] || null
    const check = toolUpgradeCheck(def, tier, coins.value, inventory.value)
    const mats = (step?.materials || []).map((need) => `${itemName(need.item)} ×${need.qty}`)
    const lack = []
    if (step && check.missingCoins > 0) lack.push(`${t('games.homestead.coins')} ×${compact(check.missingCoins)}`)
    for (const need of check.missingItems || []) {
      lack.push(`${itemName(need.item)} ×${need.qty - (inventory.value[need.item] || 0)}`)
    }
    return {
      code: def.code,
      name: pickName(def, locale.value),
      desc: pickDesc(def),
      tier,
      max: steps.length || FALLBACK_TIERS,
      maxed: check.maxed,
      ok: check.ok,
      costText: !step
        ? ''
        : mats.length
          ? t('games.homestead.toolCost', { coins: compact(step.coins), items: mats.join(listSep.value) })
          : t('games.homestead.toolCostCoins', { coins: compact(step.coins) }),
      lackText: lack.join(listSep.value),
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
  const level = def ? levelOf(def.skill) : 1
  if (!def || actionState(def, level, inventory.value) !== 'READY') return
  runOp((key) => homesteadApi.action(code, key))
}
const stopAction = () => runOp((key) => homesteadApi.stop(key))
const sellItem = (code, qty) => {
  if (qty > 0) runOp((key) => homesteadApi.sell(code, qty, key))
}
const upgradeTool = (code) => runOp((key) => homesteadApi.tool(code, key))

async function openRanking() {
  rankOpen.value = true
  rankLoading.value = true
  rankFailed.value = false
  try {
    const res = await homesteadApi.ranking()
    rankRows.value = Array.isArray(res?.ranking) ? res.ranking : []
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
/* 页面结构:技能列(选择器) | 动作区 | 背包与工具。宽屏三块各自滚动;
   中等宽度技能列收成顶部横条,整块纵向滚动;手机上再收成三个分页(外壳横屏两栏不受影响)。 */
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
  padding: 0 10px;
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
  /* 站点内容列最宽只有 836px(侧栏 230 + studio 上限),三栏按这个宽度设计:
     176 技能列 + 动作区 + 236~288 背包/工具 */
  grid-template-columns: 176px minmax(0, 1fr);
  gap: var(--gs-gap);
}
.hs-main {
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(232px, 30%, 288px);
  gap: var(--gs-gap);
}

/* --- 技能列 --- */
.hs-rail {
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.hs-skill {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 7px 9px;
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
.hs-skill.is-working .hs-skill-name::after {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-left: 5px;
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
.hs-skill-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}
.hs-skill-name {
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-skill-lv {
  flex: 0 0 auto;
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
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
.hs-skill-meta {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
}

/* --- 动作区 --- */
.hs-actions {
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
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 55%);
}
.hs-now.is-warn {
  border-color: rgb(var(--color-danger-rgb) / 35%);
}
.hs-now-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hs-now-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-now-left {
  flex: 0 0 auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}
.hs-bar {
  display: block;
  height: 5px;
  border-radius: 3px;
  background: rgb(var(--color-brand-rgb) / 14%);
  overflow: hidden;
}
.hs-bar b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--gs-accent);
}
.hs-now-hint {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--color-text-tertiary);
}
.hs-now.is-warn .hs-now-hint {
  color: var(--color-danger);
}
.hs-act-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.hs-act {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 45%);
}
.hs-act.is-current {
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 8%);
}
.hs-act.is-locked {
  opacity: 0.62;
}
.hs-act-info {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.hs-act-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.hs-act-name {
  font-size: 13px;
  font-weight: 600;
}
.hs-act-tag {
  padding: 0 5px;
  border-radius: 4px;
  font-size: 10.5px;
  line-height: 16px;
  font-style: normal;
}
.hs-act-tag.is-lock {
  background: rgb(var(--color-brand-rgb) / 12%);
  color: var(--color-text-secondary);
}
.hs-act-tag.is-warn {
  background: rgb(var(--color-danger-rgb) / 12%);
  color: var(--color-danger);
}
.hs-act-tag.is-live {
  background: rgb(var(--color-brand-rgb) / 16%);
  color: var(--gs-accent);
}
.hs-act-meta {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.hs-act-io {
  font-size: 11px;
  line-height: 1.45;
  color: var(--color-text-tertiary);
}
.hs-act-io.is-rare {
  color: var(--color-warning);
}
.hs-act-io.is-warn {
  color: var(--color-danger);
}
.hs-act-go {
  flex: 0 0 auto;
  min-width: 56px;
  height: 28px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: var(--gs-accent);
  color: var(--color-brand-text);
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  cursor: pointer;
}
.hs-act-go.is-stop {
  background: none;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}
.hs-act-go:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* --- 背包与工具(右栏) --- */
.hs-side {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--gs-gap);
  overflow-y: auto;
  overscroll-behavior: contain;
}
.hs-bag,
.hs-tools {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 5px;
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
  font-size: 10.5px;
  font-style: normal;
  font-weight: 400;
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
.hs-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 40%);
  font-size: 12px;
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
.hs-item-btns {
  flex: 0 0 auto;
  display: flex;
  gap: 4px;
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
.hs-tool-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}
.hs-tool-name {
  font-size: 12.5px;
  font-weight: 600;
}
.hs-tool-tier {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--color-text-secondary);
}
.hs-tool-steps {
  display: flex;
  gap: 3px;
}
.hs-tool-steps b {
  flex: 1 1 0;
  height: 3px;
  border-radius: 2px;
  background: rgb(var(--color-brand-rgb) / 14%);
}
.hs-tool-steps b.is-on {
  background: var(--gs-accent);
}
.hs-tool-desc {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--color-text-tertiary);
}
.hs-tool-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.hs-tool-cost {
  min-width: 0;
  font-size: 10.5px;
  line-height: 1.45;
  color: var(--color-text-secondary);
}
.hs-tool-cost em {
  display: block;
  font-style: normal;
  color: var(--color-danger);
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

/* --- 中等宽度(容器 <780):技能列变顶部横条,整块纵向滚动 ---
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
  .hs-main {
    display: block;
  }
  .hs-actions,
  .hs-side {
    overflow: visible;
  }
  .hs-side {
    margin-top: var(--gs-gap);
  }
  .hs-bag,
  .hs-tools {
    margin-bottom: 6px;
  }
}

/* --- 手机:三个分页,一次只看一块 --- */
@container (max-width: 659px) {
  .hs-tabs {
    display: flex;
  }
  /* 技能页高度有限,技能回一行可横划的条(触屏习惯动作);隐藏原生滚动条,不占布局盒 */
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
    flex: 0 0 132px;
  }
  .hs-wrap[data-pane='SKILLS'] .hs-side,
  .hs-wrap[data-pane='BAG'] .hs-rail,
  .hs-wrap[data-pane='BAG'] .hs-actions,
  .hs-wrap[data-pane='BAG'] .hs-tools,
  .hs-wrap[data-pane='TOOLS'] .hs-rail,
  .hs-wrap[data-pane='TOOLS'] .hs-actions,
  .hs-wrap[data-pane='TOOLS'] .hs-bag {
    display: none;
  }
  .hs-side {
    margin-top: 0;
  }
  .hs-act-go {
    min-width: 52px;
  }
}

/* 鼠标设备(含窄窗口的桌面)不靠横划取技能:把技能条换成换行铺开,滚轮即可看到全部 */
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
