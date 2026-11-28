<template>
  <GameShell
    ref="shellEl"
    class="hs-shell"
    :title="$t('games.homestead.title')"
    :rules-pages="[
      $t('games.homestead.rules.pagePlay'),
      $t('games.homestead.rules.pageSkill'),
      $t('games.homestead.rules.pageGear'),
      $t('games.homestead.rules.pageNum'),
    ]"
    body-align="stretch"
    @back="goBack"
  >
    <template #stats>
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.coins') }}</span
        ><b>{{ compact(coins) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.points') }}</span
        ><b>{{ compact(points) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.power') }}</span
        ><b>{{ power }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.index') }}</span
        ><b>{{ compact(index) }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.homestead.speed') }}</span
        ><b>{{ currentCode ? speedPercent + '%' : '—' }}</b></span
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
      <ul v-else-if="page === 2">
        <li>{{ $t('games.homestead.rules.gear1') }}</li>
        <li>{{ $t('games.homestead.rules.gear2') }}</li>
        <li>{{ $t('games.homestead.rules.gear3') }}</li>
        <li>{{ $t('games.homestead.rules.gear4') }}</li>
        <li>{{ $t('games.homestead.rules.gear5') }}</li>
        <li>{{ $t('games.homestead.rules.gear6') }}</li>
        <li>{{ $t('games.homestead.rules.gear7') }}</li>
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

      <!-- 探险手记:一趟跑完由服务端落库的报告,逐事件写清判定分与到手的东西 -->
      <el-dialog
        v-model="reportOpen"
        class="dialog-md"
        :append-to-body="!isFullscreen"
        :title="$t('games.homestead.explore.reportTitle')"
        :lock-scroll="false"
      >
        <template v-if="reportInfo">
          <p class="hs-dialog-lead">
            {{
              $t('games.homestead.explore.reportLead', {
                name: reportName,
                p: reportInfo.power,
                n: reportSteps.length,
              })
            }}
          </p>
          <ol class="hs-report">
            <li v-for="step in reportSteps" :key="step.index" class="hs-report-step" :class="{ 'is-pass': step.passed }">
              <div class="hs-report-head">
                <b>{{ step.label }}</b>
                <em>{{ $t('games.homestead.explore.scoreLine', { s: step.score, d: step.difficulty }) }}</em>
                <span class="hs-report-tag">{{
                  step.passed ? $t('games.homestead.explore.passed') : $t('games.homestead.explore.failed')
                }}</span>
              </div>
              <p v-if="step.gainText" class="hs-report-gain">{{ step.gainText }}</p>
            </li>
          </ol>
          <p class="hs-dialog-note">{{ $t('games.homestead.explore.reportPoints', { n: reportInfo.points }) }}</p>
          <ul v-if="reportItems.length" class="hs-offline-list">
            <li v-for="row in reportItems" :key="row.code">
              {{ $t('games.homestead.explore.reportItem', { name: row.name, n: row.qty }) }}
            </li>
          </ul>
          <ul v-if="reportGear.length" class="hs-offline-list">
            <li v-for="(row, i) in reportGear" :key="'g' + i">
              {{ $t('games.homestead.explore.reportGear', { name: row.name, q: row.quality }) }}
              <em v-if="row.affixText" class="hs-report-affix">{{ row.affixText }}</em>
            </li>
          </ul>
        </template>
        <template #footer>
          <el-button type="primary" @click="reportOpen = false">{{ $t('games.homestead.explore.ack') }}</el-button>
        </template>
      </el-dialog>

      <!-- 开箱结果:宝箱与直接兑换都在这里亮底 -->
      <el-dialog
        v-model="openedOpen"
        class="dialog-sm"
        :append-to-body="!isFullscreen"
        :title="$t('games.homestead.explore.openedTitle')"
        :lock-scroll="false"
      >
        <ul v-if="openedRows.length" class="hs-offline-list">
          <li v-for="row in openedRows" :key="row.key">{{ row.text }}</li>
        </ul>
        <p v-else class="hs-dialog-note">{{ $t('games.homestead.explore.openedEmpty') }}</p>
        <template #footer>
          <el-button type="primary" @click="openedOpen = false">{{ $t('games.homestead.explore.ack') }}</el-button>
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

      <div v-else class="hs-grid" :class="{ 'is-solo': !withRail }">
        <!-- 左:工作门类(11 门),等级环 + 正在做的高亮;只有工作与工具两页要用到 -->
        <aside v-if="withRail" class="hs-rail">
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
          <!-- ============ 工作 ============ -->
          <template v-if="pane === 'WORK'">
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
          </template>

          <!-- ============ 物品 ============ -->
          <template v-else-if="pane === 'BAG'">
            <section v-if="marketOrders.length" class="hs-market">
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
            </section>

            <h2 class="hs-pane-title">
              <span>{{ $t('games.homestead.bag') }}</span>
              <em v-if="itemKinds">{{ $t('games.homestead.kinds', { n: itemKinds }) }}</em>
            </h2>
            <p v-if="!bagGroups.length" class="hs-empty">{{ $t('games.homestead.bagEmpty') }}</p>
            <section v-for="group in bagGroups" :key="group.category" class="hs-bag-group">
              <h3 class="hs-sub">{{ $t('games.homestead.category.' + group.category) }}</h3>
              <div class="hs-bag-grid">
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
              </div>
            </section>
          </template>

          <!-- ============ 工具 ============ -->
          <template v-else-if="pane === 'TOOLS'">
            <h2 class="hs-pane-title">
              <span>{{ $t('games.homestead.tools') }}</span>
              <em>{{ $t('games.homestead.toolHint') }}</em>
            </h2>
            <div class="hs-tools">
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
          </template>

          <!-- ============ 装备 ============ -->
          <template v-else-if="pane === 'GEAR'">
            <header class="hs-now hs-gear-head">
              <div class="hs-now-top">
                <span class="hs-now-icon" aria-hidden="true">🛡️</span>
                <div class="hs-now-text">
                  <b class="hs-now-title">{{ $t('games.homestead.gear.powerNow', { n: power }) }}</b>
                  <span class="hs-now-sub">{{ $t('games.homestead.gear.slotsHint') }}</span>
                </div>
              </div>
              <div class="hs-auto">
                <span class="hs-auto-label">{{ $t('games.homestead.gear.autoLabel') }}</span>
                <button
                  v-for="focus in AUTO_FOCUS"
                  :key="focus"
                  type="button"
                  class="hs-mini"
                  :disabled="busy"
                  :title="$t('games.homestead.gear.autoHint.' + focus)"
                  @click="autoWear(focus)"
                >
                  {{ $t('games.homestead.gear.auto.' + focus) }}
                </button>
              </div>
            </header>

            <!-- 12 个穿戴格:点已穿的格＝脱下,点空格的格＝按该部位筛选下面的蓝本 -->
            <section class="hs-slots">
              <button
                v-for="slot in slotRows"
                :key="slot.code"
                type="button"
                class="hs-slot"
                :class="{ 'is-filled': !!slot.gear }"
                :title="
                  slot.gear ? $t('games.homestead.gear.takeOffHint', { name: slot.gear.name }) : $t('games.homestead.gear.filterHint')
                "
                @click="slot.gear ? unwearSlot(slot) : (filterFamily = slot.family)"
              >
                <span class="hs-slot-name">{{ slot.name }}</span>
                <template v-if="slot.gear">
                  <b class="hs-slot-gear" :class="'is-' + slot.gear.quality.toLowerCase()">{{ slot.gear.name }}</b>
                  <span class="hs-slot-power">{{ $t('games.homestead.gear.powerShort', { n: slot.gear.power }) }}</span>
                </template>
                <span v-else class="hs-slot-empty">{{ $t('games.homestead.slotEmpty') }}</span>
              </button>
            </section>

            <div class="hs-loadouts">
              <h2 class="hs-pane-title">
                <span>{{ $t('games.homestead.gear.loadouts') }}</span>
                <em>{{ $t('games.homestead.gear.loadoutsHint', { n: loadoutFree }) }}</em>
              </h2>
              <div class="hs-loadout-grid">
                <article v-for="row in loadoutRows" :key="row.index" class="hs-loadout" :class="{ 'is-locked': !row.owned }">
                  <header class="hs-loadout-head">
                    <b>{{ $t('games.homestead.gear.loadoutN', { n: row.index }) }}</b>
                    <span v-if="row.owned">{{ $t('games.homestead.gear.loadoutPieces', { n: row.pieces }) }}</span>
                  </header>
                  <template v-if="row.owned">
                    <input
                      v-model="loadoutNames[row.index]"
                      class="hs-loadout-name"
                      type="text"
                      maxlength="24"
                      :placeholder="$t('games.homestead.gear.loadoutName')"
                    />
                    <div class="hs-loadout-acts">
                      <button type="button" class="hs-mini" :disabled="busy" @click="saveLoadout(row.index)">
                        {{ $t('games.homestead.gear.loadoutSave') }}
                      </button>
                      <button type="button" class="hs-mini is-main" :disabled="busy || !row.pieces" @click="applyLoadout(row.index)">
                        {{ $t('games.homestead.gear.loadoutApply') }}
                      </button>
                    </div>
                  </template>
                  <template v-else>
                    <p class="hs-empty">{{ $t('games.homestead.gear.loadoutLocked') }}</p>
                    <button type="button" class="hs-mini is-main" :disabled="busy || coins < row.price" @click="buyLoadout(row.index)">
                      {{ $t('games.homestead.gear.loadoutBuy', { n: compact(row.price) }) }}
                    </button>
                  </template>
                </article>
              </div>
            </div>

            <div class="hs-sets">
              <h2 class="hs-pane-title">
                <span>{{ $t('games.homestead.gear.sets') }}</span>
                <em>{{ $t('games.homestead.gear.setsHint') }}</em>
              </h2>
              <div class="hs-set-grid">
                <article v-for="row in setRows" :key="row.code" class="hs-set" :class="{ 'is-on': row.pieces > 0 }">
                  <header class="hs-set-head">
                    <b>{{ row.name }}</b>
                    <span>{{ $t('games.homestead.gear.setPieces', { n: row.pieces }) }}</span>
                  </header>
                  <p v-for="step in row.steps" :key="step.pieces" class="hs-set-step" :class="{ 'is-on': step.active }">
                    <i>{{ step.pieces }}</i>
                    <span>{{ step.text }}</span>
                  </p>
                </article>
              </div>
            </div>

            <div class="hs-filter">
              <button type="button" class="hs-filter-chip" :class="{ 'is-on': filterFamily === 'ALL' }" @click="filterFamily = 'ALL'">
                {{ $t('games.homestead.gear.all') }}
              </button>
              <button
                v-for="family in gearFamilies"
                :key="family"
                type="button"
                class="hs-filter-chip"
                :class="{ 'is-on': filterFamily === family }"
                @click="filterFamily = family"
              >
                {{ slotName(family) }}
              </button>
            </div>

            <div class="hs-gear">
              <h2 class="hs-pane-title">
                <span>{{ $t('games.homestead.gear.mine') }}</span>
                <em>{{ $t('games.homestead.gear.mineHint', { n: gearRows.length }) }}</em>
              </h2>
              <p v-if="!gearRows.length" class="hs-empty">{{ $t('games.homestead.gear.mineEmpty') }}</p>
              <div v-else class="hs-inst-grid">
                <article
                  v-for="row in gearRows"
                  :key="row.id"
                  class="hs-inst"
                  :class="['is-' + row.quality.toLowerCase(), { 'is-worn': !!row.wornSlot, 'is-locked': row.locked }]"
                >
                  <header class="hs-inst-head">
                    <b class="hs-inst-name">{{ row.name }}</b>
                    <span class="hs-q" :class="'is-' + row.quality.toLowerCase()">{{ row.qualityText }}</span>
                    <b class="hs-inst-power">{{ $t('games.homestead.gear.powerShort', { n: row.power }) }}</b>
                  </header>
                  <p class="hs-inst-meta">
                    {{ row.slotName }} ·
                    {{
                      row.max
                        ? $t('games.homestead.gear.enhanceLevel', { n: row.enhance, max: row.max })
                        : $t('games.homestead.gear.noEnhance')
                    }}
                    <template v-if="row.wornSlot"> · {{ $t('games.homestead.gear.wornIn', { slot: slotName(row.wornSlot) }) }}</template>
                  </p>
                  <p v-if="row.affixText" class="hs-inst-affix">{{ row.affixText }}</p>
                  <p v-if="row.setName" class="hs-inst-set">{{ $t('games.homestead.gear.setOf', { name: row.setName }) }}</p>
                  <p v-if="row.enhanceCostText" class="hs-inst-cost">
                    {{ row.enhanceCostText }}
                    <em v-if="row.enhanceLackText">{{ $t('games.homestead.costLack', { list: row.enhanceLackText }) }}</em>
                  </p>
                  <p v-if="row.canReroll" class="hs-inst-cost">{{ rerollCostText }}</p>
                  <p v-else-if="row.rerollHint" class="hs-inst-cost">{{ row.rerollHint }}</p>
                  <div class="hs-inst-acts">
                    <button type="button" class="hs-mini is-main" :disabled="busy || !row.canEnhance" @click="enhanceGear(row)">
                      {{ $t('games.homestead.gear.enhance') }}
                    </button>
                    <button
                      type="button"
                      class="hs-mini"
                      :disabled="busy || (!row.wornSlot && !row.canWear)"
                      @click="row.wornSlot ? unwearInstance(row) : wearGear(row)"
                    >
                      {{ row.wornSlot ? $t('games.homestead.gear.takeOff') : $t('games.homestead.gear.wear') }}
                    </button>
                    <button type="button" class="hs-mini" :disabled="busy || !row.canReroll" @click="rerollGear(row)">
                      {{ $t('games.homestead.gear.reroll') }}
                    </button>
                    <button type="button" class="hs-mini" :disabled="busy" @click="toggleLock(row)">
                      {{ row.locked ? $t('games.homestead.gear.unlock') : $t('games.homestead.gear.lock') }}
                    </button>
                    <button
                      type="button"
                      class="hs-mini is-danger"
                      :disabled="busy || !!row.wornSlot || row.locked"
                      :title="row.dismantleText ? $t('games.homestead.gear.dismantlePreview', { list: row.dismantleText }) : ''"
                      @click="dismantleGear(row)"
                    >
                      {{ $t('games.homestead.gear.dismantle') }}
                    </button>
                  </div>
                </article>
              </div>
            </div>

            <div class="hs-craft">
              <h2 class="hs-pane-title">
                <span>{{ $t('games.homestead.gear.craftBook') }}</span>
                <em>{{ $t('games.homestead.gear.craftBookHint', { n: craftRows.length }) }}</em>
              </h2>
              <div class="hs-craft-grid">
                <article v-for="row in craftRows" :key="row.code" class="hs-craft-card" :class="{ 'is-drop': !row.craftable }">
                  <header class="hs-craft-head">
                    <b>{{ row.name }}</b>
                    <span class="hs-craft-slot">{{ row.slotName }}</span>
                  </header>
                  <p v-if="row.effectText" class="hs-craft-line is-effect">{{ row.effectText }}</p>
                  <p class="hs-craft-line">
                    {{ $t('games.homestead.gear.craftPower', { n: row.power }) }}
                    <template v-if="row.setName"> · {{ $t('games.homestead.gear.setOf', { name: row.setName }) }}</template>
                  </p>
                  <template v-if="row.craftable">
                    <p class="hs-craft-cost">
                      {{ row.costText }}
                      <em v-if="row.lackText">{{ $t('games.homestead.costLack', { list: row.lackText }) }}</em>
                      <em v-else-if="!row.levelOk">{{ $t('games.homestead.gear.requireLevel', { n: row.requireLevel }) }}</em>
                    </p>
                    <div class="hs-craft-foot">
                      <span v-if="row.owned">{{ $t('games.homestead.gear.owned', { n: row.owned }) }}</span>
                      <button type="button" class="hs-mini is-main" :disabled="busy || !row.ok" @click="craftGear(row.code)">
                        {{ $t('games.homestead.gear.craft') }}
                      </button>
                    </div>
                  </template>
                  <p v-else class="hs-craft-line is-drop">
                    {{ $t('games.homestead.gear.dropOnly', { q: row.qualityFloor }) }}
                  </p>
                </article>
              </div>
            </div>
          </template>

          <!-- ============ 探险 ============ -->
          <template v-else-if="pane === 'EXPLORE'">
            <header class="hs-now">
              <div class="hs-now-top">
                <span class="hs-now-icon" aria-hidden="true">🧭</span>
                <div class="hs-now-text">
                  <b class="hs-now-title">{{ $t('games.homestead.explore.title') }}</b>
                  <span class="hs-now-sub">
                    {{ $t('games.homestead.explore.quota', { used: expedition.used, limit: expedition.limit }) }} ·
                    {{ $t('games.homestead.explore.resetIn', { time: expResetText }) }}
                  </span>
                </div>
                <span class="hs-exp-points">{{ $t('games.homestead.explore.pointsNow', { n: compact(points) }) }}</span>
              </div>
              <p class="hs-now-sub">{{ $t('games.homestead.explore.hint', { n: power }) }}</p>
            </header>

            <section v-if="activeTrip" class="hs-now is-trip">
              <div class="hs-now-top">
                <span class="hs-now-icon" aria-hidden="true">⛺</span>
                <div class="hs-now-text">
                  <b class="hs-now-title">{{ $t('games.homestead.explore.onTrip', { name: activeRegionName }) }}</b>
                  <span class="hs-now-sub">
                    {{ $t('games.homestead.explore.tripLeft', { m: activeLeft.minutes, s: pad2(activeLeft.seconds) }) }}
                  </span>
                </div>
              </div>
            </section>

            <h2 class="hs-pane-title">
              <span>{{ $t('games.homestead.explore.regions') }}</span>
              <em>{{ $t('games.homestead.explore.regionsHint') }}</em>
            </h2>
            <div class="hs-region-grid">
              <article v-for="row in regionRows" :key="row.code" class="hs-region" :class="{ 'is-blocked': !row.ok }">
                <header class="hs-region-head">
                  <b>{{ row.name }}</b>
                  <span>{{ $t('games.homestead.explore.minutes', { n: row.minutes }) }}</span>
                </header>
                <p class="hs-region-line">
                  {{ $t('games.homestead.explore.requireLine', { skill: row.skillName, n: row.requireLevel }) }} ·
                  {{ $t('games.homestead.explore.powerFloor', { n: row.powerFloor }) }} ·
                  {{ $t('games.homestead.explore.expectScore', { n: row.expectScore }) }}
                </p>
                <p class="hs-region-line">{{ row.feeText }}</p>
                <p class="hs-region-line">{{ $t('games.homestead.explore.gearHint', { q: row.qualityName, list: row.gearNames }) }}</p>
                <div class="hs-region-events">
                  <span v-for="ev in row.events" :key="ev.key" class="hs-ev"> {{ ev.label }} {{ ev.difficulty }} </span>
                </div>
                <div class="hs-region-foot">
                  <span v-if="row.reason" class="hs-region-reason">{{ row.reason }}</span>
                  <button type="button" class="hs-mini is-main" :disabled="busy || !row.ok" @click="dispatch(row)">
                    {{ $t('games.homestead.explore.dispatch') }}
                  </button>
                </div>
              </article>
            </div>

            <h2 class="hs-pane-title">
              <span>{{ $t('games.homestead.explore.shop') }}</span>
              <em>{{ $t('games.homestead.explore.shopHint') }}</em>
            </h2>
            <div class="hs-shop-grid">
              <article v-for="row in shopRows" :key="row.code" class="hs-shop-item">
                <header class="hs-shop-head">
                  <b>{{ row.name }}</b>
                  <em>{{ $t('games.homestead.explore.pointsCost', { n: row.points }) }}</em>
                </header>
                <p class="hs-craft-line">{{ row.info }}</p>
                <p v-if="row.desc" class="hs-craft-line">{{ row.desc }}</p>
                <div class="hs-craft-foot">
                  <span>{{ row.ok ? '' : $t('games.homestead.explore.notEnough') }}</span>
                  <button type="button" class="hs-mini is-main" :disabled="busy || !row.ok" @click="exchange(row)">
                    {{ $t('games.homestead.explore.exchange') }}
                  </button>
                </div>
              </article>
            </div>

            <h2 class="hs-pane-title">
              <span>{{ $t('games.homestead.explore.history') }}</span>
              <em>{{ $t('games.homestead.explore.historyHint') }}</em>
            </h2>
            <p v-if="!tripHistory.length" class="hs-empty">{{ $t('games.homestead.explore.historyEmpty') }}</p>
            <div v-else class="hs-history">
              <button v-for="row in tripHistory" :key="row.id" type="button" class="hs-history-row" @click="openReport(row.report)">
                <span class="hs-history-name">{{ row.name }}</span>
                <span class="hs-history-meta">
                  {{ $t('games.homestead.explore.historyPassed', { p: row.passed, n: row.total }) }} · + {{ $t('games.homestead.points') }}
                  {{ row.points }}
                </span>
              </button>
            </div>
          </template>

          <!-- ============ 场地 ============ -->
          <template v-else>
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
          </template>
        </section>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
// 小院慢生活(放置):服务端存档 + 离线结算,客户端只渲染状态、按服务端锚点给进度条设动画。
// 产出/经验/稀有掉落/探险判定一律以服务端为准(utils/games/homestead.js 只放展示侧纯函数),
// 本地不模拟收益 —— 否则会和 HomesteadEngine 变成两套引擎,离线回来数字对不上。
// 内容表(蓝本/区域/商店)走独立端点并按 contentVersion 复用,60 秒轮询只带存档本身。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
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
  expeditionScore,
  formatCompact,
  msPerItem,
  pickDesc,
  pickName,
  progressOf,
  sellCoinsWithBonus,
  slotFamily,
  xpRatio,
} from '@/utils/games/homestead'

/** 站在页面里对账的节奏:数字 1 秒重画一次,状态 60 秒拉一次(写操作直接回全量状态) */
const TICK_MS = 1000
const POLL_MS = 60000
/** 本地插值到点后提前对账,两次请求至少隔这么久(服务端才是入账方,这里只是让数字早点跟上) */
const DUE_SYNC_GAP_MS = 1500
/** 一键最优的四个追求方向(与后端 HomesteadService.AUTO_FOCUS 同口径) */
const AUTO_FOCUS = ['SPEED', 'XP', 'RARE', 'YIELD']
const TABS = [
  { value: 'WORK', key: 'games.homestead.tabWork' },
  { value: 'BAG', key: 'games.homestead.tabBag' },
  { value: 'TOOLS', key: 'games.homestead.tabTools' },
  { value: 'GEAR', key: 'games.homestead.tabGear' },
  { value: 'EXPLORE', key: 'games.homestead.tabExplore' },
  { value: 'YARD', key: 'games.homestead.tabYard' },
]
const BAG_CATEGORIES = ['MATERIAL', 'PRODUCT', 'RARE']

const router = useRouter()
const { t, te, locale } = useI18n()

const shellEl = ref(null)
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)

const state = ref(null)
const content = ref(null)
const loading = ref(true)
const loadError = ref(false)
const busy = ref(false)
const syncing = ref(false)
const contentLoading = ref(false)
/** 六个页签共用一个状态:工作与工具两页左侧还要挂门类栏 */
const pane = ref('WORK')
const selectedSkill = ref('')
const filterFamily = ref('ALL')

/** 进度条动画锚点:拿到 /state 的本地时刻;nowTick 每秒推进(只喂数字与到点对账) */
const anchorAt = ref(Date.now())
const nowTick = ref(Date.now())
let syncedAt = 0

const offlineOpen = ref(false)
const offlineInfo = ref(null)
const reportOpen = ref(false)
const reportInfo = ref(null)
const openedOpen = ref(false)
const openedInfo = ref(null)
const loadoutNames = ref({})
const rankOpen = ref(false)
const rankLoading = ref(false)
const rankFailed = ref(false)
const rankRows = ref([])

// ---------- 内容表与服务端状态 ----------
const player = computed(() => state.value?.player || {})
const coins = computed(() => Number(player.value.coins) || 0)
const points = computed(() => Number(player.value.explorePoints) || 0)
const power = computed(() => Number(state.value?.power) || 0)
const index = computed(() => Number(state.value?.index) || 0)
const currentCode = computed(() => player.value.actionCode || null)
const compact = (value) => formatCompact(value, locale.value)
const listSep = computed(() => t('games.homestead.listSep'))
const withRail = computed(() => pane.value === 'WORK' || pane.value === 'TOOLS')

const byCode = (list) => {
  const out = {}
  for (const def of list || []) out[def.code] = def
  return out
}
const skillMap = computed(() => byCode(content.value?.skills))
const itemMap = computed(() => byCode(content.value?.items))
const actionMap = computed(() => byCode(content.value?.actions))
const gearMap = computed(() => byCode(content.value?.equipment))
const buildingMap = computed(() => byCode(content.value?.buildings))
const rarityMap = computed(() => byCode(content.value?.rarities))
const setMap = computed(() => byCode(content.value?.sets))
const regionMap = computed(() => byCode(content.value?.regions))
const toolByAction = computed(() => {
  const out = {}
  for (const tool of content.value?.tools || []) out[tool.action] = tool
  return out
})
const gearFamilies = computed(() => content.value?.gearFamilies || [])
const loadoutFree = computed(() => Number(content.value?.loadoutFree) || 2)
const rerollCoins = computed(() => Number(content.value?.rerollCoins) || 0)
const rerollQty = computed(() => Number(content.value?.rerollQty) || 0)
const rerollCostText = computed(() =>
  t('games.homestead.gear.rerollCost', {
    coins: compact(rerollCoins.value),
    item: itemName(content.value?.rerollItem),
    qty: rerollQty.value,
  }),
)

const itemName = (code) => pickName(itemMap.value[code], locale.value)
const skillName = (code) => pickName(skillMap.value[code], locale.value)
const slotName = (slot) => {
  const key = 'games.homestead.slot.' + slot
  return te(key) ? t(key) : slot
}
const rarityNameOf = (code) => (code ? pickName(rarityMap.value[code], locale.value) : '')
const eventKindLabel = (kind) => {
  const key = 'games.homestead.eventKind.' + kind
  return te(key) ? t(key) : kind
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
const instById = computed(() => {
  const out = {}
  for (const row of state.value?.gear || []) out[row.id] = row
  return out
})
const wornBySlot = computed(() => {
  const out = {}
  for (const row of state.value?.wear || []) out[row.slot] = row.gearId || null
  return out
})
const ownedByCode = computed(() => {
  const out = {}
  for (const row of state.value?.gear || []) out[row.code] = (out[row.code] || 0) + 1
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
const pad2 = (value) => String(Math.max(0, Number(value) || 0)).padStart(2, '0')

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
  (content.value?.actions || [])
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

/** 加成效果文案(工具/装备/场地/词缀/套装共用):基点转百分比,带目标时前置「采集·」或技能名 */
const targetLabel = (target) => {
  if (skillMap.value[target]) return pickName(skillMap.value[target], locale.value)
  const key = 'games.homestead.effectTarget.' + target
  return te(key) ? t(key) : target
}
const effectText = (effect) => {
  if (!effect) return ''
  const key = 'games.homestead.effect.' + effect.kind
  if (!te(key)) return ''
  const text = t(key, {
    p: bpToPercent(effect.bp),
    h: Number(effect.hours) || 0,
  })
  const target = effect.target
  if (!target || target === 'ALL' || target === 'ACTION') return text
  return t('games.homestead.effectTargeted', { target: targetLabel(target), text })
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

// ---------- 工具 ----------
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

// ---------- 装备 ----------
const setsState = computed(() => state.value?.sets || [])
const loadoutsState = computed(() => state.value?.loadouts || [])

const slotRows = computed(() =>
  (content.value?.gearSlots || []).map((slot) => {
    const gearId = wornBySlot.value[slot]
    const inst = gearId ? instById.value[gearId] : null
    const def = inst ? gearMap.value[inst.code] || {} : null
    return {
      code: slot,
      family: slotFamily(slot),
      name: slotName(slot),
      gear: inst
        ? {
            id: inst.id,
            name: pickName(def, locale.value),
            quality: inst.quality,
            power: Number(inst.power) || 0,
          }
        : null,
    }
  }),
)

const setRows = computed(() =>
  setsState.value.map((row) => {
    const def = setMap.value[row.code] || {}
    return {
      code: row.code,
      name: pickName(def, locale.value),
      pieces: Number(row.pieces) || 0,
      steps: (row.steps || []).map((step) => ({
        pieces: Number(step.pieces) || 0,
        active: !!step.active,
        text: effectsText(step.effects),
      })),
    }
  }),
)

const loadoutRows = computed(() =>
  loadoutsState.value.map((row) => ({
    index: row.index,
    owned: !!row.owned,
    name: row.name || '',
    price: Number(row.price) || 0,
    pieces: Object.keys(row.items || {}).length,
  })),
)

function syncLoadoutNames(rows) {
  for (const row of rows || []) {
    if (!loadoutNames.value[row.index] && row.name) loadoutNames.value[row.index] = row.name
  }
}

/** 分解产出按品质倍率折算,和后端 HomesteadLoot.dismantle 同一口径(只用于预览) */
function dismantleTextOf(def, quality) {
  const mult = Number(rarityMap.value[quality]?.multBp) || 10000
  const rows = (def.dismantle || []).map((need) => `${itemName(need.item)} ×${Math.max(1, Math.floor((need.qty * mult) / 10000))}`)
  return rows.join(listSep.value)
}

const gearRows = computed(() =>
  (state.value?.gear || [])
    .map((row) => {
      const def = gearMap.value[row.code] || {}
      const rarity = rarityMap.value[row.quality] || {}
      const max = (def.steps || []).length
      const enhance = Number(row.enhance) || 0
      const step = max > enhance ? def.steps?.[enhance] : null
      const gap = costGap(step, coins.value, inventory.value)
      const levelOk = levelOf(def.skill) >= (Number(def.requireLevel) || 1)
      const rerollable = (Number(rarity.affixes) || 0) > 0
      const rerollOk =
        rerollable &&
        !row.locked &&
        coins.value >= rerollCoins.value &&
        (inventory.value[content.value?.rerollItem] || 0) >= rerollQty.value
      return {
        id: row.id,
        code: row.code,
        name: pickName(def, locale.value),
        family: def.slot,
        slotName: slotName(def.slot),
        quality: row.quality,
        qualityText: pickName(rarity, locale.value),
        power: Number(row.power) || 0,
        enhance,
        max,
        wornSlot: row.wornSlot || null,
        locked: !!row.locked,
        canWear: levelOk,
        canEnhance: !!step && gap.ok,
        enhanceCostText: step ? costTextOf(step) : '',
        enhanceLackText: step ? lackTextOf(gap) : '',
        affixText: (row.affixes || []).map(effectText).filter(Boolean).join(listSep.value),
        setName: def.set ? pickName(setMap.value[def.set], locale.value) : '',
        dismantleText: dismantleTextOf(def, row.quality),
        canReroll: rerollOk,
        rerollHint: rerollable ? '' : t('games.homestead.gear.noAffix'),
      }
    })
    .filter((row) => filterFamily.value === 'ALL' || row.family === filterFamily.value)
    .sort((a, b) => b.power - a.power || a.id - b.id),
)

const craftRows = computed(() =>
  (content.value?.equipment || [])
    .filter((def) => filterFamily.value === 'ALL' || def.slot === filterFamily.value)
    .map((def) => {
      const craftable = !!def.craft
      const gap = costGap(def.craft, coins.value, inventory.value)
      const levelOk = levelOf(def.skill) >= (Number(def.requireLevel) || 1)
      return {
        code: def.code,
        name: pickName(def, locale.value),
        slotName: slotName(def.slot),
        power: Number(def.power) || 0,
        setName: def.set ? pickName(setMap.value[def.set], locale.value) : '',
        effectText: effectsText(def.effects),
        craftable,
        qualityFloor: rarityNameOf(def.source === 'DROP' ? 'FINE' : 'COMMON'),
        owned: ownedByCode.value[def.code] || 0,
        levelOk,
        requireLevel: Number(def.requireLevel) || 1,
        costText: craftable ? costTextOf(def.craft) : '',
        lackText: craftable ? lackTextOf(gap) : '',
        ok: craftable && gap.ok && levelOk,
      }
    }),
)

// ---------- 场地 ----------
const buildingRows = computed(() =>
  (content.value?.buildings || []).map((def) => {
    const level = buildingLevel(def.code)
    const maxLevel = Number(def.maxLevel) || (def.steps || []).length || 10
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

// ---------- 探险 / 手记 / 开箱 ----------
const expedition = computed(
  () =>
    state.value?.expedition || {
      limit: 0,
      used: 0,
      resetAt: 0,
      active: null,
      history: [],
      fresh: [],
    },
)
const activeTrip = computed(() => expedition.value.active || null)
const activeRegionName = computed(() => {
  const code = activeTrip.value?.region
  return code ? pickName(regionMap.value[code], locale.value) : ''
})
const activeLeft = computed(() => countdownParts(Math.max(0, Number(activeTrip.value?.remainingMs) || 0) - elapsedMs.value))
/** 每日余次的重置时刻:以服务端 serverNow 为锚,本地只补流逝 */
const expResetText = computed(() => {
  const resetAt = Number(expedition.value.resetAt) || 0
  const serverNow = Number(state.value?.serverNow) || 0
  if (!resetAt || !serverNow) return '—'
  const left = Math.max(0, resetAt - serverNow - elapsedMs.value)
  const hours = Math.floor(left / 3600000)
  const minutes = Math.floor((left % 3600000) / 60000)
  return `${pad2(hours)}:${pad2(minutes)}`
})

/** 区域的派遣条件:余次 → 在途 → 技能 → 战力 → 路费,顺序与后端校验一致 */
const regionRows = computed(() =>
  (content.value?.regions || []).map((def) => {
    const levelOk = levelOf(def.skill) >= (Number(def.requireLevel) || 1)
    const powerOk = power.value >= (Number(def.powerFloor) || 0)
    const quotaOk = expedition.value.used < expedition.value.limit
    const idleOk = !activeTrip.value
    const feeGap = costGap({ coins: def.feeCoins, materials: def.supplies }, coins.value, inventory.value)
    const supplies = (def.supplies || []).map((need) => `${itemName(need.item)} ×${need.qty}`)
    let reason = ''
    if (!quotaOk) reason = t('games.homestead.explore.noQuota')
    else if (!idleOk) reason = t('games.homestead.explore.busy')
    else if (!levelOk) {
      reason = t('games.homestead.explore.lowLevel', { skill: skillName(def.skill), n: def.requireLevel })
    } else if (!powerOk) reason = t('games.homestead.explore.lowPower', { n: def.powerFloor })
    else if (!feeGap.ok) reason = t('games.homestead.explore.noFee', { list: lackTextOf(feeGap) })
    return {
      code: def.code,
      name: pickName(def, locale.value),
      minutes: Number(def.minutes) || 0,
      skillName: skillName(def.skill),
      requireLevel: Number(def.requireLevel) || 1,
      powerFloor: Number(def.powerFloor) || 0,
      expectScore: expeditionScore(levelOf(def.skill), power.value),
      qualityName: rarityNameOf(def.gearQualityFloor) || rarityNameOf('COMMON'),
      gearNames: (def.gearPool || [])
        .slice(0, 3)
        .map((code) => pickName(gearMap.value[code], locale.value))
        .filter(Boolean)
        .join(listSep.value),
      feeText: supplies.length
        ? t('games.homestead.explore.feeLine', { coins: compact(def.feeCoins), list: supplies.join(listSep.value) })
        : t('games.homestead.costCoins', { coins: compact(def.feeCoins) }),
      events: (def.events || []).map((event, i) => ({
        key: def.code + '-' + i,
        label: eventKindLabel(event.kind),
        difficulty: Number(event.difficulty) || 0,
      })),
      ok: quotaOk && idleOk && levelOk && powerOk && feeGap.ok,
      reason,
    }
  }),
)

const shopRows = computed(() =>
  (content.value?.shops || []).map((def) => {
    const chest = String(def.kind) === 'CHEST'
    return {
      code: def.code,
      name: pickName(def, locale.value),
      points: Number(def.points) || 0,
      info: chest
        ? t('games.homestead.explore.chestInfo', {
            n: Number(def.gearRolls) || 0,
            q: rarityNameOf(def.gearQualityFloor) || rarityNameOf('COMMON'),
          })
        : t('games.homestead.explore.itemInfo', { name: itemName(def.item), n: def.qty }),
      desc: pickDesc(def, locale.value),
      ok: points.value >= (Number(def.points) || 0),
    }
  }),
)

const tripHistory = computed(() =>
  (expedition.value.history || []).map((row) => {
    const report = row.report || {}
    const steps = report.steps || []
    return {
      id: row.id,
      name: pickName(regionMap.value[row.region], locale.value) || report.regionName || row.region,
      passed: steps.filter((step) => step.passed).length,
      total: steps.length,
      points: Number(report.points) || 0,
      report,
    }
  }),
)

/** 词缀串(落库/报告里的紧凑格式 SPEED:ALL:120)与快照里的对象数组统一成 {kind,target,bp} */
function parseAffixes(raw) {
  if (Array.isArray(raw)) return raw
  if (!raw) return []
  return String(raw)
    .split(',')
    .map((part) => {
      const [kind, target, bp] = part.split(':')
      if (!kind) return null
      return { kind, target: target || 'ALL', bp: Number(bp) || 0 }
    })
    .filter(Boolean)
}
const affixTextOf = (raw) => parseAffixes(raw).map(effectText).filter(Boolean).join(listSep.value)

const reportName = computed(() => {
  const info = reportInfo.value
  if (!info) return ''
  return pickName(regionMap.value[info.region], locale.value) || info.regionName || info.region || ''
})
const reportSteps = computed(() =>
  (reportInfo.value?.steps || []).map((step) => {
    const gains = []
    if (step.item) gains.push(t('games.homestead.explore.reportItem', { name: itemName(step.item), n: step.qty }))
    if (step.gear) {
      gains.push(
        t('games.homestead.explore.reportGearInline', {
          name: pickName(gearMap.value[step.gear.code], locale.value),
          q: rarityNameOf(step.gear.quality),
        }),
      )
    }
    if (Number(step.points) > 0) gains.push(t('games.homestead.explore.reportPointGain', { n: step.points }))
    return {
      index: Number(step.index) || 0,
      label: `${eventKindLabel(step.kind)} · ${skillName(step.skill)}`,
      difficulty: Number(step.difficulty) || 0,
      score: Number(step.score) || 0,
      passed: !!step.passed,
      gainText: gains.join(listSep.value),
    }
  }),
)
const reportItems = computed(() =>
  Object.entries(reportInfo.value?.items || {}).map(([code, qty]) => ({
    code,
    name: itemName(code),
    qty: Number(qty) || 0,
  })),
)
const reportGear = computed(() =>
  (reportInfo.value?.gear || []).map((row) => ({
    name: pickName(gearMap.value[row.code], locale.value),
    quality: rarityNameOf(row.quality),
    affixText: affixTextOf(row.affixes),
  })),
)
const openedRows = computed(() => {
  const info = openedInfo.value
  if (!info) return []
  const rows = Object.entries(info.items || {}).map(([code, qty]) => ({
    key: 'i-' + code,
    text: t('games.homestead.explore.reportItem', { name: itemName(code), n: qty }),
  }))
  ;(info.gear || []).forEach((row, i) => {
    const affix = affixTextOf(row.affixes)
    rows.push({
      key: 'g-' + i,
      text:
        t('games.homestead.explore.reportGear', {
          name: pickName(gearMap.value[row.code], locale.value),
          q: rarityNameOf(row.quality),
        }) + (affix ? ' · ' + affix : ''),
    })
  })
  return rows
})

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
  // 内容表按 contentVersion 复用:服务端换了内容(扩表/调数值)就悄悄再拉一次
  if (content.value && res.contentVersion !== content.value.version) loadContent(true)
  if (!selectedSkill.value) {
    const skills = content.value?.skills || []
    selectedSkill.value = res.player?.actionCode ? actionMap.value[res.player.actionCode]?.skill || '' : skills[0]?.code || ''
  }
  syncLoadoutNames(res.loadouts)
  if (res.offline && res.offline.completions > 0) {
    offlineInfo.value = res.offline
    offlineOpen.value = true
  }
  // 本次请求新结算完的探险:只弹最近一趟,其余在手记列表里翻
  const fresh = res.expedition?.fresh || []
  if (fresh.length && !reportOpen.value) {
    const latest = fresh[fresh.length - 1]
    if (latest?.report) openReport(latest.report)
  }
  // 兑换开箱结果只出现在兑换那一次响应里
  if (res.opened) {
    openedInfo.value = res.opened
    openedOpen.value = true
  }
}

async function loadContent(force) {
  if (contentLoading.value) return
  if (!force && content.value) return
  contentLoading.value = true
  try {
    const res = await homesteadApi.content()
    if (!res) return
    content.value = res
    if (!selectedSkill.value) selectedSkill.value = res.skills?.[0]?.code || ''
  } catch {
    /* 首屏由 loadState 统一给重试入口;轮询时静默等下一次 */
  } finally {
    contentLoading.value = false
  }
}

async function loadState() {
  loadError.value = false
  loading.value = true
  try {
    const [stateRes, contentRes] = await Promise.all([
      homesteadApi.state(),
      content.value ? Promise.resolve(content.value) : homesteadApi.content(),
    ])
    if (contentRes && !content.value) content.value = contentRes
    applyState(stateRes)
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
  if (busy.value) return null
  busy.value = true
  try {
    const res = await call(opKey())
    applyState(res)
    return res
  } catch {
    /* 拦截器已提示失败原因(等级不够/材料不够/铜板不够等) */
    return null
  } finally {
    busy.value = false
  }
}

const startAction = (code) => {
  const def = actionMap.value[code]
  if (!def) return
  const next = actionState(def, levelOf(def.skill), inventory.value, buildingLevel(def.requireBuilding))
  if (next !== 'READY') return
  runOp((key) => homesteadApi.action(code, key))
}
const stopAction = () => runOp((key) => homesteadApi.stop(key))
const sellItem = (code, qty) => {
  if (qty > 0) runOp((key) => homesteadApi.sell(code, qty, key))
}
const eatItem = (code) => runOp((key) => homesteadApi.consume(code, 1, key))
const craftTool = (code) => runOp((key) => homesteadApi.tool(code, key))
const upgradeBuilding = (code) => runOp((key) => homesteadApi.building(code, key))

// ---------- 装备 ----------
const craftGear = (code) => runOp((key) => homesteadApi.craftGear(code, key))
const enhanceGear = (row) => runOp((key) => homesteadApi.enhanceGear(row.id, key))
/** 穿上:按蓝本家族自动落到空格(戒指先左手再右手),不需要先选槽 */
const wearGear = (row) => runOp((key) => homesteadApi.wear('', row.id, key))
const unwearSlot = (slot) => runOp((key) => homesteadApi.wear(slot.code, null, key))
const unwearInstance = (row) => runOp((key) => homesteadApi.wear(row.wornSlot, null, key))
const toggleLock = (row) => runOp((key) => homesteadApi.lockGear(row.id, !row.locked, key))
const rerollGear = (row) => runOp((key) => homesteadApi.rerollGear(row.id, key))
const autoWear = (focus) => runOp((key) => homesteadApi.autoGear(focus, key))
const saveLoadout = (index) => runOp((key) => homesteadApi.saveLoadout(index, (loadoutNames.value[index] || '').trim(), key))
const applyLoadout = (index) => runOp((key) => homesteadApi.applyLoadout(index, key))
const buyLoadout = (index) => runOp((key) => homesteadApi.buyLoadout(index, key))

async function dismantleGear(row) {
  try {
    await ElMessageBox.confirm(
      t('games.homestead.gear.dismantleAsk', { name: row.name, list: row.dismantleText }),
      t('games.homestead.gear.dismantle'),
      {
        type: 'warning',
        confirmButtonText: t('games.homestead.gear.dismantle'),
        cancelButtonText: t('common.cancel'),
      },
    )
  } catch {
    return
  }
  runOp((key) => homesteadApi.dismantleGear(row.id, key))
}

// ---------- 探险 ----------
const dispatch = (row) => runOp((key) => homesteadApi.startExpedition(row.code, key))
const exchange = (row) => runOp((key) => homesteadApi.exchange(row.code, key))
function openReport(report) {
  if (!report) return
  reportInfo.value = report
  reportOpen.value = true
}

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
/* 页面结构:六个页签(工作/物品/工具/装备/探险/场地)常驻顶部;
   工作与工具两页左侧挂技能门类栏,其余四页整幅铺开(12 槽网格与区域卡都需要宽度)。 */
.hs-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--gs-gap);
  width: 100%;
  height: 100%;
  min-height: 0;
}
.hs-tabs {
  flex: 0 0 auto;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.hs-tab {
  flex: 1 1 0;
  min-width: 62px;
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
  /* 站点内容列最宽 836px:168 门类 + 其余留给主区(装备 12 槽 / 探险区域卡都在主区里) */
  grid-template-columns: 168px minmax(0, 1fr);
  gap: var(--gs-gap);
}
.hs-grid.is-solo {
  grid-template-columns: minmax(0, 1fr);
}

/* --- 技能门类 --- */
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

/* --- 主区:当前工作 + 各页签内容 --- */
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
.hs-now.is-trip {
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 8%);
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
.hs-bag-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hs-bag-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 5px;
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

/* --- 工具 / 场地(同一张紧凑卡) --- */
.hs-tools,
.hs-gear,
.hs-craft {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.hs-tools {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 5px;
}
.hs-yard-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
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
.hs-mini.is-danger {
  border-color: rgb(var(--color-danger-rgb) / 30%);
  color: var(--color-danger);
}
.hs-mini:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

/* --- 装备:战力头 + 一键最优 --- */
.hs-gear-head .hs-now-top {
  align-items: flex-start;
}
.hs-auto {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.hs-auto-label {
  font-size: 10.5px;
  color: var(--color-text-tertiary);
}

/* 12 个穿戴格:品质色只上在名字上,格子本身保持纸面 */
.hs-slots {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(86px, 1fr));
  gap: 5px;
}
.hs-slot {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-height: 54px;
  padding: 5px 7px;
  border: 1px dashed var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 35%);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.hs-slot.is-filled {
  border-style: solid;
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 8%);
}
.hs-slot-name {
  font-size: 10px;
  color: var(--color-text-tertiary);
}
.hs-slot-gear {
  max-width: 100%;
  overflow: hidden;
  font-size: 11.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-slot-power {
  font-size: 10px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.hs-slot-empty {
  font-size: 11px;
  color: var(--color-text-tertiary);
}
/* 品质色板:普通到秘闻五档,只用既有语义色,不新增自定义属性 */
.hs-q.is-common,
.hs-slot-gear.is-common {
  color: var(--color-text-secondary);
}
.hs-q.is-fine,
.hs-slot-gear.is-fine {
  color: var(--color-green);
}
.hs-q.is-rare,
.hs-slot-gear.is-rare {
  color: var(--gs-accent);
}
.hs-q.is-epic,
.hs-slot-gear.is-epic {
  color: var(--color-warning);
}
.hs-q.is-legendary,
.hs-slot-gear.is-legendary {
  color: var(--color-danger);
}

/* 预设换装 */
.hs-loadouts,
.hs-sets,
.hs-gear {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.hs-loadout-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 5px;
}
.hs-loadout {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 40%);
}
.hs-loadout.is-locked {
  border-style: dashed;
  opacity: 0.8;
}
.hs-loadout-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  font-size: 11.5px;
}
.hs-loadout-head span {
  font-size: 10px;
  color: var(--color-text-tertiary);
}
.hs-loadout-name {
  height: 24px;
  padding: 0 6px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: rgb(var(--color-card-rgb) / 60%);
  color: inherit;
  font: inherit;
  font-size: 11.5px;
}
.hs-loadout-acts {
  display: flex;
  gap: 4px;
}
.hs-loadout-acts .hs-mini {
  flex: 1 1 0;
}

/* 套装进度 */
.hs-set-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 5px;
}
.hs-set {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 6px 8px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 35%);
}
.hs-set.is-on {
  border-color: rgb(var(--color-brand-rgb) / 45%);
}
.hs-set-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  font-size: 11.5px;
}
.hs-set-head span {
  font-size: 10px;
  color: var(--color-text-tertiary);
}
.hs-set-step {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin: 0;
  font-size: 10.5px;
  line-height: 1.45;
  color: var(--color-text-tertiary);
}
.hs-set-step i {
  flex: 0 0 16px;
  height: 16px;
  border-radius: 4px;
  background: rgb(var(--color-brand-rgb) / 12%);
  font-style: normal;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
  color: var(--color-text-secondary);
}
.hs-set-step.is-on {
  color: var(--color-text-primary);
}
.hs-set-step.is-on i {
  background: var(--gs-accent);
  color: var(--color-brand-text);
  font-weight: 600;
}

/* 部位筛选 */
.hs-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.hs-filter-chip {
  height: 22px;
  padding: 0 8px;
  border: 1px solid var(--color-border);
  border-radius: 11px;
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 11px;
  cursor: pointer;
}
.hs-filter-chip.is-on {
  border-color: var(--gs-accent);
  background: rgb(var(--color-brand-rgb) / 12%);
  color: var(--gs-accent);
  font-weight: 600;
}

/* 我的装备(实例卡) */
.hs-inst-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(232px, 1fr));
  gap: 5px;
}
.hs-inst {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid var(--color-border);
  border-left-width: 3px;
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 40%);
}
.hs-inst.is-common {
  border-left-color: rgb(var(--color-brand-rgb) / 25%);
}
.hs-inst.is-fine {
  border-left-color: var(--color-green);
}
.hs-inst.is-rare {
  border-left-color: var(--gs-accent);
}
.hs-inst.is-epic {
  border-left-color: var(--color-warning);
}
.hs-inst.is-legendary {
  border-left-color: var(--color-danger);
}
.hs-inst.is-worn {
  background: rgb(var(--color-brand-rgb) / 9%);
}
.hs-inst.is-locked {
  border-style: dashed;
}
.hs-inst-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.hs-inst-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 12.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-q {
  flex: 0 0 auto;
  padding: 0 5px;
  border-radius: 4px;
  background: rgb(var(--color-brand-rgb) / 10%);
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
}
.hs-inst-power {
  flex: 0 0 auto;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}
.hs-inst-meta,
.hs-inst-affix,
.hs-inst-set,
.hs-inst-cost {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.45;
}
.hs-inst-meta {
  color: var(--color-text-tertiary);
}
.hs-inst-affix {
  color: var(--gs-accent);
}
.hs-inst-set {
  color: var(--color-text-secondary);
}
.hs-inst-cost {
  color: var(--color-text-secondary);
}
.hs-inst-cost em {
  margin-left: 3px;
  font-style: normal;
  color: var(--color-danger);
}
.hs-inst-acts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
}

/* 制造图鉴 */
.hs-craft-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(188px, 1fr));
  gap: 5px;
}
.hs-craft-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 40%);
}
.hs-craft-card.is-drop {
  border-style: dashed;
  background: none;
}
.hs-craft-head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 12px;
}
.hs-craft-head b {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-craft-slot {
  flex: 0 0 auto;
  font-size: 10px;
  color: var(--color-text-tertiary);
}
.hs-craft-line {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.45;
  color: var(--color-text-tertiary);
}
.hs-craft-line.is-effect {
  color: var(--gs-accent);
}
.hs-craft-line.is-drop {
  color: var(--color-warning);
}
.hs-craft-cost {
  margin: 0;
  font-size: 10px;
  line-height: 1.45;
  color: var(--color-text-secondary);
}
.hs-craft-cost em {
  display: block;
  font-style: normal;
  color: var(--color-danger);
}
.hs-craft-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 22px;
}
.hs-craft-foot span {
  font-size: 10px;
  color: var(--color-text-tertiary);
}

/* --- 探险:余次头 + 区域卡 + 商店 + 手记列表 --- */
.hs-exp-points {
  flex: 0 0 auto;
  padding: 0 7px;
  border-radius: 6px;
  background: rgb(var(--color-brand-rgb) / 12%);
  color: var(--gs-accent);
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  font-variant-numeric: tabular-nums;
}
.hs-region-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(206px, 1fr));
  gap: 5px;
}
.hs-region {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid var(--color-border);
  border-radius: var(--gs-radius);
  background: rgb(var(--color-card-rgb) / 40%);
}
.hs-region.is-blocked {
  opacity: 0.75;
}
.hs-region-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  font-size: 12.5px;
}
.hs-region-head span {
  flex: 0 0 auto;
  font-size: 10px;
  color: var(--color-text-tertiary);
}
.hs-region-line {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.45;
  color: var(--color-text-tertiary);
}
.hs-region-events {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}
.hs-ev {
  padding: 0 5px;
  border-radius: 4px;
  background: rgb(var(--color-brand-rgb) / 10%);
  font-size: 10px;
  line-height: 16px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.hs-region-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 2px;
}
.hs-region-reason {
  min-width: 0;
  font-size: 10px;
  line-height: 1.4;
  color: var(--color-danger);
}
.hs-shop-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 5px;
}
.hs-shop-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 40%);
}
.hs-shop-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  font-size: 12px;
}
.hs-shop-head em {
  flex: 0 0 auto;
  font-style: normal;
  font-size: 10.5px;
  font-weight: 600;
  color: var(--color-warning);
  font-variant-numeric: tabular-nums;
}
.hs-history {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.hs-history-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 9px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 35%);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.hs-history-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hs-history-meta {
  flex: 0 0 auto;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* --- 探险手记弹窗 --- */
.hs-report {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.hs-report-step {
  padding: 6px 9px;
  border: 1px solid var(--color-border);
  border-left: 3px solid var(--color-danger);
  border-radius: 8px;
  background: rgb(var(--color-card-rgb) / 40%);
}
.hs-report-step.is-pass {
  border-left-color: var(--color-green);
}
.hs-report-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
}
.hs-report-head em {
  font-style: normal;
  font-size: 10.5px;
  color: var(--color-text-tertiary);
  font-variant-numeric: tabular-nums;
}
.hs-report-tag {
  margin-left: auto;
  padding: 0 5px;
  border-radius: 4px;
  background: rgb(var(--color-danger-rgb) / 12%);
  font-size: 10px;
  line-height: 16px;
  color: var(--color-danger);
}
.hs-report-step.is-pass .hs-report-tag {
  background: rgb(var(--color-brand-rgb) / 14%);
  color: var(--gs-accent);
}
.hs-report-gain {
  margin: 2px 0 0;
  font-size: 10.5px;
  line-height: 1.5;
  color: var(--color-text-secondary);
}
.hs-report-affix {
  margin-left: 5px;
  font-style: normal;
  font-size: 10.5px;
  color: var(--gs-accent);
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

/* --- 中等宽度(容器 <780):门类变顶部横条,主区整块纵向滚动 ---
   断点按游戏区实际宽度定而不是视口:站点内容列最宽 836px,写 979 的话桌面永远进不了两栏 */
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
    overflow: visible;
  }
  .hs-cards {
    grid-template-columns: repeat(auto-fill, minmax(172px, 1fr));
  }
  .hs-inst-grid,
  .hs-craft-grid,
  .hs-region-grid,
  .hs-shop-grid,
  .hs-set-grid {
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  }
}

/* --- 手机(容器 <660):页签换行,门类收成一行可横划的条 --- */
@container (max-width: 659px) {
  .hs-tab {
    flex: 1 1 calc(33% - 6px);
    min-width: 0;
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
  .hs-slots {
    grid-template-columns: repeat(auto-fill, minmax(74px, 1fr));
  }
  .hs-bag-grid,
  .hs-inst-grid,
  .hs-craft-grid,
  .hs-region-grid,
  .hs-shop-grid,
  .hs-set-grid,
  .hs-loadout-grid,
  .hs-tools,
  .hs-yard-grid {
    grid-template-columns: 1fr;
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
