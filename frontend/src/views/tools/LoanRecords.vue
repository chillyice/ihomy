<template>
  <div class="lr-root">
    <!-- 未登录:贷款记录是家庭数据,试算/反推可游客用,这里需要登录 -->
    <el-empty v-if="!userStore.isLoggedIn" :description="$t('tools.loan.rec.loginHint')">
      <template #image><el-icon :size="42" class="lr-empty-icon"><Lock /></el-icon></template>
      <el-button type="primary" size="small" @click="$router.push('/login')">{{ $t('tools.loan.rec.goLogin') }}</el-button>
    </el-empty>

    <template v-else>
      <el-alert v-if="ledgerError" :title="ledgerError" type="warning" :closable="false" show-icon class="lr-alert" />

      <div class="lr-layout">
        <!-- 左:贷款列表(组表头 + 组内成员 + 独立贷款) -->
        <div class="lr-col">
          <div class="card lc-card">
            <div class="lc-head">
              <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.listTitle') }}</div>
              <el-button v-if="canManage" size="small" type="primary" @click="openEditor()">{{ $t('tools.loan.rec.addLoan') }}</el-button>
            </div>

            <template v-if="listRows.length">
              <template v-for="row in listRows" :key="row.view.id">
                <!-- 组表头:点看合计 -->
                <div v-a11y-click
                  v-if="row.type === 'group'"
                  class="lr-item lr-group-item"
                  :class="{ on: selectedKey === row.view.id }"
                  @click="selectedKey = row.view.id"
                >
                  <div class="lr-item-top">
                    <span class="lr-item-name">{{ row.view.name }}</span>
                    <span class="lr-badge lr-badge-group">{{ $t('tools.loan.rec.groupBadge', { n: row.view.groupLoans.length }) }}</span>
                  </div>
                  <div class="lr-item-sub">
                    <span>{{ $t('tools.loan.rec.remainPrincipal') }} {{ money(row.view.snapshot.balance) }}</span>
                    <span v-if="row.view.snapshot.settled">{{ $t('tools.loan.rec.settledBadge') }}</span>
                    <span v-else>{{ $t('tools.loan.rec.nextPayment') }} {{ money(row.view.snapshot.currentPayment) }}</span>
                  </div>
                </div>
                <!-- 单笔贷款 -->
                <div v-a11y-click
                  v-else
                  class="lr-item"
                  :class="{ on: selectedKey === String(row.view.id), child: row.child }"
                  @click="selectedKey = String(row.view.id)"
                >
                  <div class="lr-item-top">
                    <span class="lr-item-name">{{ row.view.name }}</span>
                    <span v-if="row.view.snapshot.settled" class="lr-badge">{{ $t('tools.loan.rec.settledBadge') }}</span>
                    <el-tag v-else size="small" type="info" effect="plain" class="lr-tag">{{ dictText(t, 'loan_channel', row.view.channel) }}</el-tag>
                  </div>
                  <div class="lr-item-sub">
                    <span>{{ $t('tools.loan.rec.principal') }} {{ money(row.view.amount / 10000) }}{{ $t('tools.loan.wanShort') }}</span>
                    <span>{{ $t('tools.loan.rec.currentRate') }} {{ rate(row.view.snapshot.currentRate) }}%</span>
                  </div>
                  <div class="lr-item-sub">
                    <template v-if="row.view.snapshot.settled">
                      <span>{{ $t('tools.loan.rec.settledAtLine', { n: row.view.snapshot.settledAt }) }}</span>
                    </template>
                    <template v-else>
                      <span>{{ $t('tools.loan.rec.remainPrincipal') }} {{ money(row.view.snapshot.balance) }}</span>
                      <span>{{ $t('tools.loan.rec.remainPeriodsOf', { n: row.view.snapshot.remainingPeriods }) }}</span>
                    </template>
                  </div>
                </div>
              </template>
            </template>
            <div v-else-if="loadError" style="padding: 20px 0; text-align: center; color: var(--color-text-secondary)">
              {{ $t('common.loadFailed') }} <el-button text size="small" @click="load">{{ $t('common.retry') }}</el-button>
            </div>
            <el-empty v-else-if="!loading" :description="$t('tools.loan.rec.emptyHint')" :image-size="72" />
            <p v-if="!canManage && loans.length" class="lc-hint">{{ $t('tools.loan.rec.viewOnlyHint') }}</p>
          </div>
        </div>

        <!-- 右:进度 + 时间轴 + 分段 + 对比 -->
        <div class="lr-col">
          <template v-if="current">
            <div class="card lc-card">
              <div class="lc-head">
                <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.progressTitle') }}</div>
                <div v-if="canManage && !current.isGroup" class="lc-head-actions">
                  <el-button size="small" @click="openEditor(current)">{{ $t('common.edit') }}</el-button>
                  <el-button size="small" type="danger" plain @click="onDeleteLoan(current)">{{ $t('common.delete') }}</el-button>
                </div>
                <span v-else-if="current.isGroup" class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.groupEditHint') }}</span>
              </div>
              <div class="lc-tiles">
                <div class="lc-tile emph">
                  <span>{{ current.snapshot.settled ? $t('tools.loan.rec.settledBadge') : $t('tools.loan.rec.nextPayment') }}</span>
                  <b>{{ current.snapshot.settled ? '—' : money(current.snapshot.currentPayment) }}</b>
                  <em v-if="current.isGroup">{{ $t('tools.loan.rec.groupTileLine', { n: current.groupLoans.length }) }}</em>
                  <em v-else>{{ $t('tools.loan.rec.currentRateIs', { v: rate(current.snapshot.currentRate) }) }}</em>
                </div>
                <div class="lc-tile">
                  <span>{{ $t('tools.loan.rec.remainPrincipal') }}</span>
                  <b>{{ money(current.snapshot.balance) }}</b>
                  <em>{{ current.snapshot.settled ? $t('tools.loan.rec.settledAtLine', { n: current.snapshot.settledAt }) : $t('tools.loan.rec.remainPeriodsOf', { n: current.snapshot.remainingPeriods }) }}</em>
                </div>
                <div class="lc-tile">
                  <span>{{ $t('tools.loan.rec.paidPrincipalLabel') }}</span>
                  <b>{{ money(current.snapshot.paidPrincipal) }}</b>
                  <em>{{ $t('tools.loan.rec.paidInterestLine', { v: money(current.snapshot.paidInterest) }) }}</em>
                </div>
                <div class="lc-tile">
                  <span>{{ $t('tools.loan.rec.totalInterestLabel') }}</span>
                  <b>{{ money(current.ledger.summary.totalInterest) }}</b>
                  <em>{{ $t('tools.loan.rec.interestRatioLine', { v: percent(current.ledger.summary.interestRatio) }) }}</em>
                </div>
              </div>
              <div class="lc-mini">
                <div><span>{{ $t('tools.loan.rec.paidPeriodsLabel') }}</span><b>{{ current.snapshot.paidPeriods }} / {{ current.ledger.rows.length }}</b></div>
                <div><span>{{ $t('tools.loan.rec.prepaidTotal') }}</span><b>{{ current.ledger.summary.prepaidTotal > 0 ? money(current.ledger.summary.prepaidTotal) : '—' }}</b></div>
                <div v-for="m in (current.groupLoans || [])" :key="m.id"><span>{{ m.name }}</span><b>{{ money(m.snapshot.balance) }}</b></div>
                <div v-if="!current.isGroup && current.ledger.firstDays !== 30"><span>{{ $t('tools.loan.rec.firstDaysLabel') }}</span><b>{{ $t('tools.loan.rec.firstDaysValue', { n: current.ledger.firstDays }) }}</b></div>
                <div v-if="!current.isGroup && current.firstPayment"><span>{{ $t('tools.loan.rec.formFirstPayment') }}</span><b>{{ money(current.firstPayment) }}</b></div>
              </div>
            </div>

            <div class="card lc-card">
              <div class="lc-head">
                <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.timelineTitle') }}</div>
                <div v-if="canManage && !current.isGroup" class="lc-head-actions">
                  <el-button size="small" @click="openEvent(LOAN_EVENT.RATE_CHANGE)">{{ $t('tools.loan.rec.addRateChange') }}</el-button>
                  <el-button size="small" @click="openEvent(LOAN_EVENT.PREPAY)">{{ $t('tools.loan.rec.addPrepay') }}</el-button>
                </div>
              </div>
              <div v-if="current.events.length" class="lr-timeline">
                <div v-a11y-click v-for="(ev, idx) in current.events" :key="ev.id || idx" class="lr-event" :class="'lr-event-' + ev.type.toLowerCase()" @click="onEventClick(ev)">
                  <span class="lr-event-dot" />
                  <div class="lr-event-body">
                    <div class="lr-event-head">
                      <span class="lr-event-title">
                        {{ ev.type === LOAN_EVENT.RATE_CHANGE
                          ? $t('tools.loan.rec.rateChangeLine', { v: rate(ev.rate) })
                          : $t('tools.loan.rec.prepayLine', { v: money(ev.amount) }) }}
                      </span>
                      <el-tag v-if="current.isGroup" size="small" effect="plain" class="lr-tag">{{ ev.loanName }}</el-tag>
                      <el-tag size="small" effect="plain" class="lr-tag">
                        {{ ev.type === LOAN_EVENT.PREPAY ? $t('dict.loan_prepay_strategy.' + (ev.strategy || 'SHORTEN')) : $t('dict.loan_event_type.RATE_CHANGE') }}
                      </el-tag>
                    </div>
                    <div class="lr-event-sub">
                      {{ $t('tools.loan.rec.timelineAtLine', { n: ev.effectivePeriod }) }}<template v-if="periodMonth(current, ev.effectivePeriod)">{{ $t('tools.loan.rec.timelineAtMonth', { m: periodMonth(current, ev.effectivePeriod) }) }}</template>
                      <span v-if="ev.note" class="lr-event-note">· {{ ev.note }}</span>
                    </div>
                  </div>
                  <el-icon v-a11y-click v-if="canManage" class="lr-event-del" @click.stop="onEventDelete(ev)"><Close /></el-icon>
                </div>
              </div>
              <p v-else class="lc-hint lc-hint-block">{{ $t('tools.loan.rec.timelineEmpty') }}</p>
            </div>

            <div v-if="!current.isGroup && current.ledger.segments.length > 1" class="card lc-card">
              <div class="section-label">{{ $t('tools.loan.rec.segmentsTitle') }}</div>
              <el-table :data="segmentRows" size="small" border>
                <el-table-column :label="$t('tools.loan.rec.colPeriods')" min-width="120" align="right">
                  <template #default="{ row }">{{ $t('tools.loan.rec.segPeriods', { a: row.fromPeriod, b: row.toPeriod }) }}</template>
                </el-table-column>
                <el-table-column :label="$t('tools.loan.colRate')" width="90" align="right">
                  <template #default="{ row }">{{ rate(row.rate) }}%</template>
                </el-table-column>
                <el-table-column :label="$t('tools.loan.rec.segPayment')" min-width="118" align="right">
                  <template #default="{ row }">
                    <div>{{ money(row.payment) }}</div>
                    <span v-if="row.firstPayment !== row.payment" class="lc-sub">{{ $t('tools.loan.rec.segFirstLine', { v: money(row.firstPayment) }) }}</span>
                  </template>
                </el-table-column>
                <el-table-column :label="$t('tools.loan.rec.segInterest')" min-width="120" align="right">
                  <template #default="{ row }">{{ money(row.interest) }}</template>
                </el-table-column>
              </el-table>
            </div>

            <div class="card lc-card">
              <div class="lc-head">
                <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.compareTitle') }}</div>
                <span class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.compareHint') }}</span>
              </div>
              <div class="lc-tiles">
                <div class="lc-tile">
                  <span>{{ $t('tools.loan.rec.rateImpact') }}</span>
                  <b :class="{ 'lr-good': current.ledger.delta.rateImpact < 0, 'lr-bad': current.ledger.delta.rateImpact > 0 }">
                    {{ current.ledger.delta.rateImpact === 0 ? '—' : (current.ledger.delta.rateImpact > 0 ? '+' : '') + money(current.ledger.delta.rateImpact) }}
                  </b>
                  <em>{{ $t('tools.loan.rec.baseInterestLine', { v: money(current.ledger.baseline.totalInterest) }) }}</em>
                </div>
                <div class="lc-tile">
                  <span>{{ $t('tools.loan.rec.prepaySaved') }}</span>
                  <b class="lr-good">{{ current.ledger.delta.prepaySaved > 0 ? money(current.ledger.delta.prepaySaved) : '—' }}</b>
                  <em>{{ $t('tools.loan.rec.totalDiffLine', { v: (current.ledger.delta.totalDiff > 0 ? '+' : '') + money(current.ledger.delta.totalDiff) }) }}</em>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="card lc-card lr-select-hint">
            <el-empty :description="loans.length ? $t('tools.loan.rec.noSelect') : $t('tools.loan.rec.emptyTitle')" :image-size="72" />
          </div>
        </div>
      </div>

      <!-- 还款流水线:自下而上,最下第 1 期,最上下一期;事件节点配色区分;可拖入新事件,也可拖动已有事件 -->
      <div v-if="current && current.ledger.rows.length" class="card lc-card lc-block">
        <div class="lc-head">
          <div class="section-label lc-label-flat">
            {{ $t('tools.loan.rec.pipeTitle') }}
            <span class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.pipeHint') }}</span>
          </div>
          <div v-if="canManage && !current.isGroup" class="lr-drag-palette">
            <span class="lc-hint lc-hint-inline">{{ $t('tools.loan.rec.dragHint') }}</span>
            <div
              class="lr-chip lr-chip-rate"
              draggable="true"
              @dragstart="onChipDragStart($event, LOAN_EVENT.RATE_CHANGE)"
              @dragend="onDragEnd"
            >{{ $t('tools.loan.rec.addRateChange') }}</div>
            <div
              class="lr-chip lr-chip-prepay"
              draggable="true"
              @dragstart="onChipDragStart($event, LOAN_EVENT.PREPAY)"
              @dragend="onDragEnd"
            >{{ $t('tools.loan.rec.addPrepay') }}</div>
          </div>
        </div>
        <div class="lr-pipe" @dragover.prevent="onDragOver($event)" @drop.prevent="onDrop($event)" @dragleave="onDragLeave">
          <!-- 落点提示条:绝对定位(不挤动节点,避免夹缝处来回跳)+ 常驻 DOM(拖拽中不增删节点) -->
          <div
            class="lr-dropmark"
            :class="[dragType === LOAN_EVENT.RATE_CHANGE ? 'lr-drop-rate' : 'lr-drop-prepay', { on: dropOn }]"
            :style="{ top: dropTop + 'px' }"
          >{{ dropLabel }}</div>
          <template v-for="node in pipeNodes" :key="node.key">
            <div v-if="node.newYear" class="lr-yline"><span class="lr-ylabel">{{ node.ym.slice(0, 4) }}</span></div>
            <el-tooltip
              :disabled="!!dragType"
              placement="right"
              :show-after="120"
              popper-class="lr-tip-popper"
            >
              <!-- 事件节点(已生效贴在历史段;未生效的以虚线幽灵节点浮在顶部)与期次节点共用一颗节点,靠类名区分 -->
              <div
                class="lr-pnode"
                :class="nodeClass(node)"
                :data-key="node.key"
                :data-period="node.period"
                :draggable="canDragNode(node)"
                @dragstart="onNodeDragStart($event, node)"
                @dragend="onDragEnd"
              >
                <span v-if="node.event" class="lr-shape" />
                <span v-else class="lr-dot" />
                <span class="lr-pnode-text">
                  <template v-if="node.event">
                    <b>{{ $t('tools.loan.rec.evAfterLine', { n: node.period }) }}</b>
                    {{ node.event.type === LOAN_EVENT.RATE_CHANGE
                      ? $t('tools.loan.rec.rateChangeLine', { v: rate(node.event.rate) })
                      : $t('tools.loan.rec.prepayLine', { v: money(node.event.amount) }) }}
                    <el-tag v-if="current.isGroup" size="small" effect="plain" class="lr-tag">{{ node.event.loanName }}</el-tag>
                    <el-tag v-if="node.kind === 'future'" size="small" effect="plain" class="lr-tag lr-tag-sage">{{ $t('tools.loan.rec.futureTag') }}</el-tag>
                    <span v-if="node.event.note" class="lr-ev-note">· {{ node.event.note }}</span>
                  </template>
                  <template v-else>
                    <b>{{ $t('tools.loan.rec.nodePeriod', { n: node.period }) }}</b>
                    <span v-if="node.ym" class="lr-node-ym">{{ node.ym }}</span>
                    <span>{{ money(node.row ? node.row.payment : current.snapshot.currentPayment) }}</span>
                    <span v-if="node.row && node.row.days" class="lr-node-days">{{ $t('tools.loan.rec.firstPeriodTag', { d: node.row.days }) }}</span>
                    <el-tag v-if="node.kind === 'next'" size="small" class="lr-tag lr-tag-sage">{{ $t('tools.loan.rec.nextTag') }}</el-tag>
                  </template>
                </span>
              </div>
              <template #content>
                <div class="lr-tip">
                  <div class="lr-tip-head">
                    <b>{{ $t('tools.loan.rec.nodePeriod', { n: node.period }) }}</b>
                    <span v-if="node.ym">{{ node.ym }}</span>
                    <em v-if="node.kind === 'next'">{{ $t('tools.loan.rec.nextTag') }}</em>
                    <em v-else-if="node.kind === 'future'">{{ $t('tools.loan.rec.futureTag') }}</em>
                  </div>
                  <div v-for="it in tipRows(node)" :key="it.k" class="lr-tip-row" :class="{ emph: it.emph }">
                    <span>{{ it.k }}</span><b>{{ it.v }}</b>
                  </div>
                  <template v-if="node.event">
                    <div class="lr-tip-row">
                      <span>{{ $t('tools.loan.rec.tipEvent') }}</span>
                      <b>{{ node.event.type === LOAN_EVENT.RATE_CHANGE
                        ? $t('tools.loan.rec.rateChangeLine', { v: rate(node.event.rate) })
                        : $t('tools.loan.rec.prepayLine', { v: money(node.event.amount) }) }}</b>
                    </div>
                    <div v-if="node.event.type === LOAN_EVENT.PREPAY" class="lr-tip-row">
                      <span>{{ $t('tools.loan.rec.prepayStrategy') }}</span>
                      <b>{{ $t('dict.loan_prepay_strategy.' + (node.event.strategy || 'SHORTEN')) }}</b>
                    </div>
                    <div v-if="node.tip && node.tip.afterPayment" class="lr-tip-row">
                      <span>{{ $t('tools.loan.rec.tipAfterPayment') }}</span><b>{{ money(node.tip.afterPayment) }}</b>
                    </div>
                    <div v-if="node.event.triggerDate" class="lr-tip-row">
                      <span>{{ $t('tools.loan.rec.tipTriggerDate') }}</span><b>{{ node.event.triggerDate }}</b>
                    </div>
                    <div v-if="node.event.note" class="lr-tip-note">{{ node.event.note }}</div>
                  </template>
                  <div v-if="node.kind === 'future'" class="lr-tip-foot">{{ $t('tools.loan.rec.tipProjected') }}</div>
                </div>
              </template>
            </el-tooltip>
          </template>
        </div>
      </div>

      <!-- 流水明细 -->
      <div v-if="current && current.ledger.rows.length" class="card lc-card lc-block">
        <div class="lc-head">
          <div class="section-label lc-label-flat">{{ $t('tools.loan.rec.detailTitle') }}</div>
          <el-radio-group v-model="detailMode" size="small">
            <el-radio-button value="month">{{ $t('tools.loan.byMonth') }}</el-radio-button>
            <el-radio-button value="year">{{ $t('tools.loan.byYear') }}</el-radio-button>
          </el-radio-group>
        </div>

        <el-table v-if="detailMode === 'month'" :data="pagedRows" size="small" border>
          <el-table-column :label="$t('tools.loan.rec.colPeriod')" width="76" align="right">
            <template #default="{ row }">
              {{ row.period }}
              <span v-if="row.days" class="lc-sub">{{ $t('tools.loan.rec.firstPeriodTag', { d: row.days }) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.rec.colDate')" width="96" align="right">
            <template #default="{ row }">{{ periodMonth(current, row.period) }}</template>
          </el-table-column>
          <el-table-column v-if="!current.isGroup" :label="$t('tools.loan.colRate')" width="86" align="right">
            <template #default="{ row }">{{ rate(row.rate) }}%</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colPayment')" min-width="110" align="right">
            <template #default="{ row }">
              <span :class="{ 'lr-event-row': row.rateChanged || row.prepaid }">{{ money(row.payment) }}</span>
              <span v-if="row.rateChanged" class="lc-sub lr-mark">{{ $t('dict.loan_event_type.RATE_CHANGE') }}</span>
              <span v-if="row.prepaid" class="lc-sub lr-mark">{{ $t('dict.loan_event_type.PREPAY') }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colPrincipal')" min-width="110" align="right">
            <template #default="{ row }">{{ money(row.principal) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colInterest')" min-width="110" align="right">
            <template #default="{ row }">{{ money(row.interest) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colBalance')" min-width="120" align="right">
            <template #default="{ row }">{{ money(row.balance) }}</template>
          </el-table-column>
        </el-table>

        <el-table v-else :data="yearlyRows" size="small" border>
          <el-table-column prop="year" :label="$t('tools.loan.colYear')" width="86" align="right" />
          <el-table-column :label="$t('tools.loan.colPayment')" min-width="120" align="right">
            <template #default="{ row }">{{ money(row.payment) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colPrincipal')" min-width="120" align="right">
            <template #default="{ row }">{{ money(row.principal) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colInterest')" min-width="120" align="right">
            <template #default="{ row }">{{ money(row.interest) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colBalance')" min-width="132" align="right">
            <template #default="{ row }">{{ money(row.balance) }}</template>
          </el-table-column>
        </el-table>

        <div v-if="detailMode === 'month'" class="lc-pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="pageSize"
            :page-sizes="[12, 24, 60]"
            :total="monthRows.length"
            layout="total, sizes, prev, pager, next"
            size="small"
            background
          />
        </div>
      </div>
    </template>

    <!-- 新增/编辑贷款 -->
    <el-dialog v-model="editor.visible" :title="editor.form.id ? $t('tools.loan.rec.editorTitleEdit') : $t('tools.loan.rec.editorTitleNew')" width="480px" append-to-body>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.formName') }}</label>
        <el-input v-model="editor.form.name" :placeholder="$t('tools.loan.rec.formNamePlaceholder')" maxlength="60" />
      </div>
      <div class="lr-form-grid">
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.formChannel') }}</label>
          <el-select v-model="editor.form.channel" style="width: 100%">
            <el-option value="COMMERCIAL" :label="$t('dict.loan_channel.COMMERCIAL')" />
            <el-option value="FUND" :label="$t('dict.loan_channel.FUND')" />
            <el-option value="OTHER" :label="$t('dict.loan_channel.OTHER')" />
          </el-select>
        </div>
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.formMethod') }}</label>
          <el-select v-model="editor.form.method" style="width: 100%">
            <el-option value="EQUAL_INSTALLMENT" :label="$t('dict.loan_method.EQUAL_INSTALLMENT')" />
            <el-option value="EQUAL_PRINCIPAL" :label="$t('dict.loan_method.EQUAL_PRINCIPAL')" />
          </el-select>
        </div>
      </div>
      <div class="lr-form-grid">
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.formAmount') }}<span class="lc-unit">{{ $t('tools.loan.wanUnit') }}</span></label>
          <el-input-number v-model="editor.form.amountWan" :min="0.0001" :max="99999" :step="10" :precision="2" :controls="false" style="width: 100%" />
        </div>
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.formYears') }}<span class="lc-unit">{{ $t('tools.loan.yearUnit') }}</span></label>
          <el-input-number v-model="editor.form.years" :min="1" :max="40" :step="1" :precision="0" :controls="false" style="width: 100%" />
        </div>
      </div>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.formRate') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label>
        <el-input-number v-model="editor.form.rate" :min="0" :max="36" :step="0.05" :precision="4" :controls="false" style="width: 100%" />
      </div>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.groupName') }}</label>
        <el-input v-model="editor.form.groupName" maxlength="50" :placeholder="$t('tools.loan.rec.groupNamePlaceholder')" />
        <span class="lc-hint">{{ $t('tools.loan.rec.groupNameHint') }}</span>
        <span v-if="groupNames.length" class="lc-hint">{{ $t('tools.loan.rec.groupNameExisting') }}{{ groupNames.join($t('tools.loan.rec.groupNameSep')) }}</span>
      </div>
      <div class="lr-form-grid">
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.formLoanDate') }}</label>
          <el-date-picker v-model="editor.form.loanDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </div>
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.formFirstPayDate') }}</label>
          <el-date-picker v-model="editor.form.firstPayDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </div>
      </div>
      <div class="lc-field lc-field-last">
        <label>{{ $t('tools.loan.rec.formFirstPayment') }}<span class="lc-unit">{{ $t('tools.loan.yuanUnit') }}</span></label>
        <el-input-number v-model="editor.form.firstPayment" :min="0" :step="100" :precision="2" :controls="false" style="width: 100%" />
        <span v-if="editorPreviewDays" class="lc-hint">
          {{ editorPreviewPayment > 0
            ? $t('tools.loan.rec.firstPreviewHint', { d: editorPreviewDays, v: money(editorPreviewPayment) })
            : $t('tools.loan.rec.firstDaysOnlyHint', { d: editorPreviewDays }) }}
        </span>
        <span v-else class="lc-hint">{{ $t('tools.loan.rec.firstPaymentOptionalHint') }}</span>
      </div>
      <div class="lc-field lc-field-last">
        <label>{{ $t('tools.loan.rec.formNote') }}</label>
        <el-input v-model="editor.form.note" maxlength="200" />
      </div>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="editor.saving" @click="onSaveLoan">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 事件(利率调整/提前还款):触发时间可反推生效期次,也可直接填期次 -->
    <el-dialog v-model="eventDlg.visible" :title="eventDlg.type === LOAN_EVENT.RATE_CHANGE ? $t('tools.loan.rec.addRateChange') : $t('tools.loan.rec.addPrepay')" width="420px" append-to-body>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.triggerDateLabel') }}</label>
        <el-date-picker
          v-model="eventDlg.triggerDate"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
          @change="onTriggerDateChange"
        />
        <span class="lc-hint">{{ $t('tools.loan.rec.triggerDateHint') }}</span>
      </div>
      <div class="lc-field">
        <label>{{ $t('tools.loan.rec.eventPeriodLabel') }}</label>
        <el-input-number
          v-model="eventDlg.effectivePeriod"
          :min="1"
          :max="eventDlgMax"
          :step="1"
          :precision="0"
          :controls="false"
          style="width: 100%"
          @change="onPeriodManualChange"
        />
        <span class="lc-hint">{{ $t('tools.loan.rec.eventPeriodHint') }}</span>
      </div>
      <div v-if="eventDlg.type === LOAN_EVENT.RATE_CHANGE" class="lc-field">
        <label>{{ $t('tools.loan.rec.newRateLabel') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label>
        <el-input-number v-model="eventDlg.rate" :min="0" :max="36" :step="0.05" :precision="4" :controls="false" style="width: 100%" />
      </div>
      <template v-else>
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.prepayAmountInput') }}<span class="lc-unit">{{ $t('tools.loan.yuanUnit') }}</span></label>
          <el-input-number v-model="eventDlg.amount" :min="0" :step="10000" :precision="2" :controls="false" style="width: 100%" />
        </div>
        <div class="lc-field">
          <label>{{ $t('tools.loan.rec.prepayStrategy') }}</label>
          <el-radio-group v-model="eventDlg.strategy" size="small">
            <el-radio-button value="SHORTEN">{{ $t('dict.loan_prepay_strategy.SHORTEN') }}</el-radio-button>
            <el-radio-button value="REDUCE">{{ $t('dict.loan_prepay_strategy.REDUCE') }}</el-radio-button>
          </el-radio-group>
          <span class="lc-hint">{{ $t('tools.loan.rec.prepayStrategyHint') }}</span>
        </div>
      </template>
      <div class="lc-field lc-field-last">
        <label>{{ $t('tools.loan.rec.formNote') }}</label>
        <el-input v-model="eventDlg.note" maxlength="100" />
      </div>
      <template #footer>
        <el-button @click="eventDlg.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="eventDlg.saving" @click="onSaveEvent">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Close, Lock } from '@element-plus/icons-vue'
import { loanApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { dictText } from '@/utils/dict'
import {
  loanLedger,
  ledgerSnapshot,
  mergeLedgers,
  firstPaymentPreview,
  daysBetween,
  periodsPaid,
  periodDate,
  periodYearMonth,
  formatYuan,
  LOAN_EVENT,
  REPAY_METHOD,
} from '@/utils/loan'

const { t } = useI18n()
const userStore = useUserStore()

const LEDGER_ERROR_KEYS = {
  BAD_PERIODS: 'errBadPeriods',
  NO_AMOUNT: 'errNoAmount',
  BAD_FIRST_DAYS: 'errBadFirstDays',
  BAD_EVENT_PERIOD: 'errBadEventPeriod',
  BAD_EVENT_VALUE: 'errBadEventValue',
  FIRST_PAYMENT_TOO_LOW: 'errFirstPaymentTooLow',
  PAYMENT_TOO_LOW: 'errPaymentTooLow',
}

const loading = ref(false)
const loadError = ref(false)
const loans = ref([])
const selectedKey = ref('') // 'g:<组名>' 看合计,或 '<贷款id>' 看单笔
const detailMode = ref('month')
const page = ref(1)
const pageSize = ref(12)

// 已还期数/还款进度依赖「今天」;定时 + 回到页面可见时刷新 now,跨还款日自动重算
const now = ref(new Date())
const tickNow = () => { now.value = new Date() }
const nowTimer = setInterval(tickNow, 60000)
const onVisibility = () => { if (!document.hidden) tickNow() }
document.addEventListener('visibilitychange', onVisibility)
onBeforeUnmount(() => {
  clearInterval(nowTimer)
  document.removeEventListener('visibilitychange', onVisibility)
})

const canManage = computed(() => userStore.isOwner || userStore.hasPerm('loan:manage'))

const toNum = (v) => (v == null ? null : Number(v))

/** 每笔贷款 → 引擎入参(首期天数由放款日与首期还款日推出) */
const engineInput = (loan) => ({
  amount: toNum(loan.amount),
  months: Number(loan.months),
  method: loan.method,
  rate: toNum(loan.rate),
  firstDays: daysBetween(loan.loanDate, loan.firstPayDate),
  firstPayment: toNum(loan.firstPayment),
  events: (loan.events || []).map((e) => ({
    type: e.type,
    effectivePeriod: Number(e.effectivePeriod),
    rate: toNum(e.rate),
    amount: toNum(e.amount),
    strategy: e.strategy,
  })),
})

/** 卡片/详情用的逐笔视图:流水 + 进度快照(列表量级小,直接全算) */
const cardViews = computed(() => loans.value.map((loan) => {
  const ledger = loanLedger(engineInput(loan))
  const paidPeriods = loan.firstPayDate ? periodsPaid(loan.firstPayDate, now.value) : 0
  const snapshot = ledgerSnapshot(ledger, paidPeriods)
  return { ...loan, ledger, snapshot, paidPeriods, valid: ledger.valid }
}))

/** 贷款组:同名 group_name 且 ≥2 笔 → 合计视图(逐期相加;delta/baseline 可加) */
const groupViews = computed(() => {
  const byName = new Map()
  for (const v of cardViews.value) {
    const g = (v.groupName || '').trim()
    if (!g) continue
    if (!byName.has(g)) byName.set(g, [])
    byName.get(g).push(v)
  }
  const out = []
  for (const [name, members] of byName) {
    if (members.length < 2) continue
    const ledger = mergeLedgers(members.map((m) => m.ledger))
    if (!ledger) continue
    const paidPeriods = Math.max(...members.map((m) => m.paidPeriods))
    const snapshot = ledgerSnapshot(ledger, paidPeriods)
    const delta = members.reduce((s, m) => ({
      rateImpact: s.rateImpact + ((m.ledger.delta && m.ledger.delta.rateImpact) || 0),
      prepaySaved: s.prepaySaved + ((m.ledger.delta && m.ledger.delta.prepaySaved) || 0),
      totalDiff: s.totalDiff + ((m.ledger.delta && m.ledger.delta.totalDiff) || 0),
    }), { rateImpact: 0, prepaySaved: 0, totalDiff: 0 })
    const events = members
      .flatMap((m) => (m.events || []).map((e) => ({ ...e, loanId: m.id, loanName: m.name })))
      .sort((a, b) => a.effectivePeriod - b.effectivePeriod)
    out.push({
      id: 'g:' + name,
      isGroup: true,
      name,
      groupName: name,
      channel: 'GROUP',
      amount: members.reduce((s, m) => s + (Number(m.amount) || 0), 0),
      months: Math.max(...members.map((m) => Number(m.months) || 0)),
      // 组的年月/流水线辅助线用成员里最早的首期还款日作基准(各笔首期还款日通常一致)
      firstPayDate: members.map((m) => m.firstPayDate).filter(Boolean).sort()[0] || null,
      firstPayment: null,
      rate: null,
      note: '',
      ledger: {
        ...ledger,
        delta,
        baseline: { totalInterest: members.reduce((s, m) => s + ((m.ledger.baseline && m.ledger.baseline.totalInterest) || 0), 0) },
      },
      snapshot,
      paidPeriods,
      events,
      groupLoans: members,
      valid: true,
    })
  }
  return out
})

/** 列表行:组表头在上、成员缩进随其后,独立贷款保持原有顺序 */
const listRows = computed(() => {
  const rows = []
  const grouped = new Set()
  for (const g of groupViews.value) {
    rows.push({ type: 'group', view: g })
    for (const m of g.groupLoans) {
      rows.push({ type: 'loan', view: m, child: true })
      grouped.add(m.id)
    }
  }
  for (const v of cardViews.value) {
    if (!grouped.has(v.id)) rows.push({ type: 'loan', view: v })
  }
  return rows
})

const firstListKey = () => {
  const row = listRows.value[0]
  return row ? (row.type === 'group' ? row.view.id : String(row.view.id)) : ''
}

const current = computed(() => {
  if (!selectedKey.value) return null
  if (selectedKey.value.startsWith('g:')) return groupViews.value.find((g) => g.id === selectedKey.value) || null
  const id = Number(selectedKey.value)
  return cardViews.value.find((v) => v.id === id) || null
})

const groupNames = computed(() => {
  const names = new Set()
  for (const v of cardViews.value) {
    const g = (v.groupName || '').trim()
    if (g) names.add(g)
  }
  return [...names]
})

const ledgerError = computed(() => {
  const bad = cardViews.value.find((x) => !x.valid && x.ledger.error)
  return bad ? t('tools.loan.rec.' + (LEDGER_ERROR_KEYS[bad.ledger.error] || 'errBadEventValue')) : ''
})

const monthRows = computed(() => (current.value && current.value.ledger.rows) || [])
const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return monthRows.value.slice(start, start + pageSize.value)
})

const yearlyRows = computed(() => {
  const out = []
  let cur = null
  for (const r of monthRows.value) {
    const year = Math.ceil(r.period / 12)
    if (!cur || cur.year !== year) {
      cur = { year, payment: 0, principal: 0, interest: 0, balance: r.balance }
      out.push(cur)
    }
    cur.payment += r.payment
    cur.principal += r.principal
    cur.interest += r.interest
    cur.balance = r.balance
  }
  return out
})

const segmentRows = computed(() => (current.value && current.value.ledger.segments) || [])

const load = async () => {
  loading.value = true
  loadError.value = false
  try {
    loans.value = await loanApi.list()
    const keys = new Set(listRows.value.map((r) => (r.type === 'group' ? r.view.id : String(r.view.id))))
    if (!keys.has(selectedKey.value)) selectedKey.value = firstListKey()
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

watch(() => userStore.isLoggedIn, (on) => { if (on) load() }, { immediate: true })
watch(monthRows, () => { page.value = 1 })
watch(selectedKey, () => { page.value = 1 })

const money = (v, digits = 2) => formatYuan(v, digits)
const rate = (v) => (Number(v) || 0).toFixed(4)
const percent = (v) => `${((Number(v) || 0) * 100).toFixed(2)}%`
const periodMonth = (view, period) => periodYearMonth(view && view.firstPayDate, period)

/** ==================== 流水线图 ==================== */

/**
 * 自下而上:最下第 1 期,最上下一期(未还,配色区分);事件节点贴在「第 N 期后」的位置,
 * 尚未生效的事件以虚线幽灵节点浮在顶部;年度辅助线在跨年处标注年份,节点自带年月。
 * 每颗节点带 key(拖拽落点定位/高亮用)与 tip(悬浮显示该期的还款与待还本金等数据)。
 */
const pipeNodes = computed(() => {
  const c = current.value
  if (!c || !c.ledger.rows.length) return []
  const paid = c.snapshot.paidPeriods
  const rows = c.ledger.rows
  const evByPeriod = {}
  for (const e of c.events || []) {
    if (!evByPeriod[e.effectivePeriod]) evByPeriod[e.effectivePeriod] = []
    evByPeriod[e.effectivePeriod].push(e)
  }
  // 累计已还利息(截至该期含本期),节点悬浮数据用
  const cumInterest = []
  let acc = 0
  for (const r of rows) {
    acc += r.interest
    cumInterest.push(Math.round(acc * 100) / 100)
  }
  const tipOf = (period, row) => {
    const r = row || rows[period - 1]
    if (!r) return null
    return {
      payment: r.payment,
      principal: r.principal,
      interest: r.interest,
      balance: r.balance,
      // 组合贷的合计流水没有单一利率,取不到就不显示这一行
      rate: r.rate == null ? null : r.rate,
      remaining: Math.max(0, rows.length - period),
      cumInterest: cumInterest[period - 1] == null ? null : cumInterest[period - 1],
      // 事件在「第 period 期还款后」生效,故生效后的月供就是下一期那一行的月供
      afterPayment: rows[period] ? rows[period].payment : null,
    }
  }
  const out = []
  if (!c.snapshot.settled && paid + 1 <= rows.length) {
    out.push({ kind: 'next', period: paid + 1, row: rows[paid] })
  }
  for (let k = Math.min(paid, rows.length); k >= 1; k--) {
    for (const e of evByPeriod[k] || []) out.push({ kind: 'event', period: k, event: e })
    out.push({ kind: 'paid', period: k, row: rows[k - 1] })
  }
  const future = (c.events || [])
    .filter((e) => e.effectivePeriod > paid)
    .sort((a, b) => b.effectivePeriod - a.effectivePeriod)
  for (const e of future) out.unshift({ kind: 'future', period: e.effectivePeriod, event: e })
  let prevYm = ''
  for (const n of out) {
    // 同一期可能有多颗节点(期次节点 + 事件节点),key 带上事件 id 与所属贷款保证唯一
    n.key = n.event
      ? 'e' + (n.event.id != null ? n.event.id : '') + '@' + (n.event.loanId != null ? n.event.loanId : '') + '-' + n.period
      : n.kind + '-' + n.period
    n.tip = tipOf(n.period, n.row)
    n.ym = periodYearMonth(c.firstPayDate, n.period)
    n.newYear = Boolean(prevYm && n.ym && n.ym.slice(0, 4) !== prevYm.slice(0, 4))
    if (n.ym) prevYm = n.ym
  }
  return out
})

/** 节点类名:事件菱形(利率调整/提前还款两色)/历史期次/下一期/未生效幽灵 + 拖拽中的落点与起点 */
const nodeClass = (node) => {
  const cls = []
  if (node.event) {
    cls.push('lr-pev', node.event.type === LOAN_EVENT.RATE_CHANGE ? 'lr-pev-rate' : 'lr-pev-prepay')
    if (node.kind === 'future') cls.push('lr-future')
  } else {
    cls.push(node.kind === 'next' ? 'lr-pnext' : 'lr-ppaid')
  }
  if (dragKey.value === node.key) cls.push('lr-droptarget')
  if (dragFromKey.value === node.key) cls.push('lr-dragging')
  return cls
}

/** 悬浮数据行(值为 0 的行也照常显示,便于对账;取不到的行直接不显示) */
const tipRows = (node) => {
  const tp = node.tip
  if (!tp) return []
  const out = [
    { k: t('tools.loan.rec.tipPayment'), v: money(tp.payment) },
    { k: t('tools.loan.rec.tipPrincipal'), v: money(tp.principal) },
    { k: t('tools.loan.rec.tipInterest'), v: money(tp.interest) },
    { k: t('tools.loan.rec.tipBalance'), v: money(tp.balance), emph: true },
    { k: t('tools.loan.rec.tipRemaining'), v: t('tools.loan.rec.tipPeriods', { n: tp.remaining }) },
  ]
  if (tp.rate != null) out.push({ k: t('tools.loan.colRate'), v: rate(tp.rate) + '%' })
  if (tp.cumInterest != null) out.push({ k: t('tools.loan.rec.tipCumInterest'), v: money(tp.cumInterest) })
  return out
}

/** ==================== 事件拖拽 ==================== */

const dragType = ref('') // 拖拽中的事件类型;空串 = 没在拖
const dragEv = ref(null) // 从流水线上拖起的事件(从芯片新建时为 null)
const dragFromKey = ref('') // 被拖起的节点 key(拖拽期间淡化)
const dragKey = ref('') // 当前落点节点 key
const dragAt = ref(0) // 当前落点期次
const dropTop = ref(0) // 落点提示条位置(相对 .lr-pipe 内容,px)
const dropOn = computed(() => dragAt.value > 0)
const dropLabel = computed(() => t('tools.loan.rec.' + (dragEv.value ? 'dropMarkMove' : 'dropMark'), { n: dragAt.value }))
let lastDropEl = null // 上一次的落点节点(迟滞判定用;DOM 引用不必进响应式)

/** 落点迟滞(px):光标仍在目标节点上下这个范围内就不改落点,夹缝处不再来回跳 */
const DROP_HOLD = 8

const clearDrop = () => {
  dragAt.value = 0
  dragKey.value = ''
  lastDropEl = null
}

const onChipDragStart = (e, type) => {
  dragType.value = type
  dragEv.value = null
  dragFromKey.value = ''
  clearDrop()
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'copyMove'
    e.dataTransfer.setData('text/plain', type)
  }
}

/** 只有事件节点能拖(期次节点不参与),且要有管理权、单笔视图(组视图的事件归属别的贷款) */
const canDragNode = (node) => Boolean(node.event) && canManage.value && !(current.value && current.value.isGroup)

/** 流水线上已有的事件也能拖:拖起后按落点处理(见 onDrop) */
const onNodeDragStart = (e, node) => {
  if (!canDragNode(node)) return
  dragType.value = node.event.type
  dragEv.value = node.event
  dragFromKey.value = node.key
  clearDrop()
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', node.event.type)
  }
  // 起点先当作落点:拖一小段又放回原处时保持不动,不会误挪一期
  const el = e.currentTarget
  if (el && el.dataset && el.dataset.key) {
    lastDropEl = el
    dragKey.value = node.key
    dragAt.value = node.period
    dropTop.value = Math.max(0, el.offsetTop - 9)
  }
}

const onDragEnd = () => {
  dragType.value = ''
  dragEv.value = null
  dragFromKey.value = ''
  clearDrop()
}

const dropPointOf = (el) => ({ el, key: el.dataset.key, period: Number(el.dataset.period), top: el.offsetTop })

/**
 * 光标位置 → 落点节点:光标落在某颗节点上就取它;落在夹缝/空白处取下方最近的那颗
 * (与「事件在第 N 期还款后生效」的语义一致);低于全部节点取最下那颗(第 1 期)。
 */
const dropPointFrom = (container, e) => {
  const hit = e.target && e.target.closest ? e.target.closest('.lr-pnode[data-key]') : null
  if (hit && container.contains(hit)) return dropPointOf(hit)
  if (lastDropEl && container.contains(lastDropEl)) {
    const r = lastDropEl.getBoundingClientRect()
    if (e.clientY >= r.top - DROP_HOLD && e.clientY <= r.bottom + DROP_HOLD) return dropPointOf(lastDropEl)
  }
  const nodes = [...container.querySelectorAll('.lr-pnode[data-key]')]
  if (!nodes.length) return null
  let best = null
  for (const el of nodes) {
    const r = el.getBoundingClientRect()
    if (r.bottom > e.clientY && (best === null || r.top < best.top)) best = { top: r.top, el }
  }
  return dropPointOf(best ? best.el : nodes[nodes.length - 1])
}

const onDragOver = (e) => {
  if (!dragType.value) return
  const point = dropPointFrom(e.currentTarget, e)
  if (!point) {
    clearDrop()
    return
  }
  lastDropEl = point.el
  dragKey.value = point.key
  dragAt.value = point.period
  dropTop.value = Math.max(0, point.top - 9)
}

/** 光标划过节点子元素时浏览器也会在流水线上触发 dragleave:按坐标判定,只有真离开流水线才清落点 */
const onDragLeave = (e) => {
  const r = e.currentTarget.getBoundingClientRect()
  if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) return
  clearDrop()
}

const onDrop = (e) => {
  const type = dragType.value
  const moving = dragEv.value
  const view = current.value
  const point = type ? dropPointFrom(e.currentTarget, e) : null
  onDragEnd()
  if (!type || !view || view.isGroup || !point) return
  if (moving) {
    // 拖动已有的那条事件:填过事件时间的要重新确认日期(期次按落点走、日期按落点重推)→ 开编辑;
    // 没填事件时间的只挪期次,直接保存不打扰。
    if (moving.triggerDate) openEvent(moving.type, moving, view, point.period)
    else moveEvent(moving, point.period, view)
    return
  }
  openEvent(type, null, view, point.period)
}

/** 没填事件时间的事件:重新拖拽只换生效期次(其余字段原样整体提交) */
const moveEvent = async (ev, period, view) => {
  const target = Math.min(Number(period) || 0, Number(view.months) || 0)
  if (target < 1 || target === Number(ev.effectivePeriod)) return
  const events = (view.events || []).map((e) => (e.id === ev.id ? { ...e, effectivePeriod: target } : e))
  await loanApi.update(view.id, loanToPayload(view, events))
  ElMessage.success(t('tools.loan.rec.eventMoved', { n: target }))
  await load()
}

/** ==================== 事件弹窗(触发时间 ↔ 生效期次) ==================== */

const eventMaxPeriod = computed(() => (current.value ? Number(current.value.months) : 1200))

const eventDlg = reactive({
  visible: false,
  saving: false,
  type: LOAN_EVENT.RATE_CHANGE,
  loanId: 0,
  editingId: 0,
  triggerDate: '',
  effectivePeriod: 1,
  rate: 3.1,
  amount: 100000,
  strategy: 'SHORTEN',
  note: '',
})

const eventDlgMax = computed(() => {
  const target = cardViews.value.find((v) => v.id === eventDlg.loanId)
  return target ? Number(target.months) : 1200
})

/** 事件作用的目标贷款(组视图下事件归属它所属的那笔) */
const eventTarget = () => cardViews.value.find((v) => v.id === eventDlg.loanId) || null

/**
 * 打开事件弹窗(新增或编辑)。
 * @param {string} type 事件类型
 * @param {object|null} ev 已有事件(编辑)或 null(新建)
 * @param {object} loanView 事件所属贷款视图
 * @param {number} [presetPeriod] 拖拽落点期次:新建/拖动已有事件时生效期次取它,事件时间按它重推(可改可清);
 *   不传(点开编辑)则回显事件自己存的期次与事件时间。
 */
const openEvent = (type, ev, loanView, presetPeriod) => {
  const target = loanView || current.value
  if (!target || target.isGroup) return
  const paid = target.paidPeriods || 0
  const maxPeriod = Math.max(1, Number(target.months) || 1)
  const period = presetPeriod
    ? Math.max(1, Math.min(presetPeriod, maxPeriod))
    : (ev ? Number(ev.effectivePeriod) : Math.max(1, Math.min(paid || 1, maxPeriod)))
  eventDlg.type = type
  eventDlg.loanId = target.id
  eventDlg.editingId = ev ? ev.id : 0
  eventDlg.effectivePeriod = period
  eventDlg.triggerDate = presetPeriod
    ? periodDate(target.firstPayDate, period)
    : (ev && ev.triggerDate ? ev.triggerDate : '')
  eventDlg.rate = ev ? toNum(ev.rate) : toNum(target.rate)
  eventDlg.amount = ev ? toNum(ev.amount) : 100000
  eventDlg.strategy = ev ? ev.strategy || 'SHORTEN' : 'SHORTEN'
  eventDlg.note = ev ? ev.note || '' : ''
  eventDlg.visible = true
}

const onEventClick = (ev) => {
  if (!canManage.value) return
  const target = current.value.isGroup
    ? (current.value.groupLoans || []).find((x) => x.id === ev.loanId)
    : current.value
  openEvent(ev.type, ev, target)
}

/** 填触发时间 → 反推生效期次(该日期时已还到第几期,事件在其后) */
const deriving = ref(false)
const onTriggerDateChange = (date) => {
  if (!date || deriving.value) return
  const target = eventTarget()
  if (!target) return
  if (!target.firstPayDate) {
    eventDlg.triggerDate = ''
    ElMessage.warning(t('tools.loan.rec.triggerNoFirstPay'))
    return
  }
  deriving.value = true
  const derived = Math.max(1, Math.min(periodsPaid(target.firstPayDate, date), eventDlgMax.value))
  eventDlg.effectivePeriod = derived
  deriving.value = false
}

/** 手改生效期次 → 触发时间让位清空(以手填为准) */
const onPeriodManualChange = () => {
  if (deriving.value) return
  if (eventDlg.triggerDate) eventDlg.triggerDate = ''
}

const onSaveEvent = async () => {
  const loan = eventTarget()
  if (!loan) return
  if (eventDlg.type === LOAN_EVENT.PREPAY && !(Number(eventDlg.amount) > 0)) {
    ElMessage.warning(t('tools.loan.rec.errBadEventValue'))
    return
  }
  const fresh = {
    type: eventDlg.type,
    effectivePeriod: Number(eventDlg.effectivePeriod),
    // 事件时间(触发日)落库:留着下次重新拖拽时决定要不要重新确认日期
    triggerDate: eventDlg.triggerDate || null,
    rate: eventDlg.type === LOAN_EVENT.RATE_CHANGE ? Number(eventDlg.rate) : null,
    amount: eventDlg.type === LOAN_EVENT.PREPAY ? Number(eventDlg.amount) : null,
    strategy: eventDlg.type === LOAN_EVENT.PREPAY ? eventDlg.strategy : null,
    note: eventDlg.note || null,
  }
  const events = (loan.events || [])
    .filter((e) => (eventDlg.editingId ? e.id !== eventDlg.editingId : true))
    .concat([eventDlg.editingId ? { ...fresh, id: eventDlg.editingId } : fresh])
    .sort((a, b) => a.effectivePeriod - b.effectivePeriod)
  eventDlg.saving = true
  try {
    await loanApi.update(loan.id, loanToPayload(loan, events))
    eventDlg.visible = false
    ElMessage.success(t('common.saved'))
    await load()
  } finally {
    eventDlg.saving = false
  }
}

const onEventDelete = (ev) => {
  const target = current.value.isGroup
    ? (current.value.groupLoans || []).find((x) => x.id === ev.loanId)
    : current.value
  if (!target) return
  onDeleteEvent(target, ev)
}

const onDeleteEvent = async (loan, ev) => {
  await ElMessageBox.confirm(
    t('tools.loan.rec.deleteEventConfirm'),
    t('common.deleteConfirm'),
    { type: 'warning', closeOnClickModal: true },
  )
  const events = (loan.events || []).filter((e) => e.id !== ev.id)
  await loanApi.update(loan.id, loanToPayload(loan, events))
  ElMessage.success(t('common.deleted'))
  await load()
}

/** ==================== 贷款编辑器 ==================== */

/** 编辑器预览:填了放款日 + 首期还款日即回显首期天数与参考还款额 */
const editorPreviewDays = computed(() => {
  const f = editor.form
  const days = daysBetween(f.loanDate, f.firstPayDate)
  return days > 0 ? days : 0
})
const editorPreviewPayment = computed(() => {
  const f = editor.form
  if (!editorPreviewDays.value || !f.amountWan) return 0
  return firstPaymentPreview({
    amount: (Number(f.amountWan) || 0) * 10000,
    months: (Number(f.years) || 0) * 12,
    method: f.method,
    rate: f.rate,
    firstDays: editorPreviewDays.value,
  })
})

const editor = reactive({
  visible: false,
  saving: false,
  form: {},
})

const openEditor = (loan) => {
  editor.form = loan
    ? {
        id: loan.id,
        name: loan.name,
        channel: loan.channel,
        method: loan.method,
        amountWan: toNum(loan.amount) / 10000,
        years: Math.max(1, Math.round(Number(loan.months) / 12)),
        rate: toNum(loan.rate),
        groupName: loan.groupName || '',
        loanDate: loan.loanDate || null,
        firstPayDate: loan.firstPayDate || null,
        firstPayment: toNum(loan.firstPayment),
        note: loan.note || '',
        events: (loan.events || []).map((e) => ({ ...e })),
      }
    : {
        id: 0,
        name: '',
        channel: 'COMMERCIAL',
        method: REPAY_METHOD.EQUAL_INSTALLMENT,
        amountWan: 100,
        years: 30,
        rate: 3.1,
        groupName: '',
        loanDate: null,
        firstPayDate: null,
        firstPayment: null,
        note: '',
        events: [],
      }
  editor.visible = true
}

const onSaveLoan = async () => {
  const f = editor.form
  if (!f.name || !f.name.trim()) {
    ElMessage.warning(t('tools.loan.rec.errNoName'))
    return
  }
  editor.saving = true
  try {
    const payload = {
      name: f.name.trim(),
      channel: f.channel,
      method: f.method,
      amount: Math.round((Number(f.amountWan) || 0) * 10000 * 100) / 100,
      months: (Number(f.years) || 0) * 12,
      loanDate: f.loanDate || null,
      firstPayDate: f.firstPayDate || null,
      firstPayment: Number(f.firstPayment) > 0 ? Number(f.firstPayment) : null,
      rate: Number(f.rate) || 0,
      groupName: (f.groupName || '').trim() || null,
      note: f.note || null,
      events: (f.events || []).map((e) => ({
        type: e.type,
        effectivePeriod: Number(e.effectivePeriod),
        triggerDate: e.triggerDate || null,
        rate: toNum(e.rate),
        amount: toNum(e.amount),
        strategy: e.strategy || null,
        note: e.note || null,
      })),
    }
    if (f.id) await loanApi.update(f.id, payload)
    else selectedKey.value = String(await loanApi.create(payload))
    editor.visible = false
    ElMessage.success(t('common.saved'))
    await load()
  } finally {
    editor.saving = false
  }
}

const onDeleteLoan = async (loan) => {
  await ElMessageBox.confirm(
    t('tools.loan.rec.deleteLoanConfirm', { name: loan.name }),
    t('common.deleteConfirm'),
    { type: 'warning', closeOnClickModal: true },
  )
  await loanApi.remove(loan.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

/** 由列表行还原编辑载荷(事件整体替换的其余字段保持不变) */
const loanToPayload = (loan, events) => ({
  name: loan.name,
  channel: loan.channel,
  method: loan.method,
  amount: toNum(loan.amount),
  months: Number(loan.months),
  loanDate: loan.loanDate || null,
  firstPayDate: loan.firstPayDate || null,
  firstPayment: toNum(loan.firstPayment),
  rate: toNum(loan.rate),
  groupName: loan.groupName || null,
  note: loan.note || null,
  events: events.map((e) => ({
    type: e.type,
    effectivePeriod: Number(e.effectivePeriod),
    triggerDate: e.triggerDate || null,
    rate: toNum(e.rate),
    amount: toNum(e.amount),
    strategy: e.strategy || null,
    note: e.note || null,
  })),
})
</script>

<style scoped>
/* 事件与未来配色(取自暖居调色板,晨暮各一套):利率调整 = 主题粉,未生效事件与下一期 = 鼠尾草绿,
 * 提前还款仍是陶土橙 —— 事件类型与期次状态一眼可分(不再借用 --color-accent,避免暖居下两处撞色) */
.lr-root {
  --lr-pink: #c9807a;
  --lr-pink-rgb: 201, 128, 122;
  --lr-sage: #7c8b6c;
  --lr-sage-rgb: 124, 139, 108;
  --lr-orange: #b06a3b;
  --lr-orange-rgb: 176, 106, 59;
}
html.dark .lr-root {
  --lr-pink: #e19a8c;
  --lr-pink-rgb: 225, 154, 140;
  --lr-sage: #8fa080;
  --lr-sage-rgb: 143, 160, 128;
}

.lr-alert { margin-bottom: 12px; }
.lr-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr);
  gap: 16px;
  align-items: start;
}
.lr-col { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.lc-card { padding: 18px 20px; }
.lc-card .section-label { margin-bottom: 14px; }
.lc-block { margin-top: 16px; }
.lr-empty-icon { color: var(--color-text-secondary, #7a6b5a); }
.lr-select-hint { min-height: 200px; display: flex; align-items: center; justify-content: center; }

.lc-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.lc-head-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.lc-label-flat { margin-bottom: 0 !important; }
.lc-head .lc-hint-inline { flex: 1; min-width: 160px; }

/* 贷款卡片 */
.lr-item {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-card-2, #e8dec8);
  cursor: pointer;
  transition: background 0.2s;
}
.lr-item + .lr-item { margin-top: 10px; }
.lr-item.on { background: rgba(var(--color-brand-rgb), 0.16); }
.lr-item.child { margin-left: 16px; }
.lr-group-item { border: 1px dashed rgba(var(--color-brand-rgb), 0.55); }
.lr-item-top { display: flex; align-items: center; gap: 8px; }
.lr-item-name { font-weight: 600; color: var(--color-text, #3a2e22); flex: 1; min-width: 0; }
.lr-badge {
  font-size: 11px;
  color: var(--color-brand, #b88c6e);
  border: 1px solid currentColor;
  border-radius: 999px;
  padding: 0 8px;
  line-height: 20px;
  flex-shrink: 0;
}
.lr-badge-group { color: var(--color-accent, #a8483a); }
.lr-item-sub {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b5a);
  word-break: break-all;
}

/* 时间轴 */
.lr-timeline { display: flex; flex-direction: column; }
.lr-event {
  position: relative;
  display: flex;
  gap: 10px;
  padding: 10px 0 10px 18px;
  border-left: 2px solid var(--color-border, rgba(58, 46, 34, 0.12));
  cursor: default;
}
.lr-event:last-child { border-left-color: transparent; }
.lr-event-dot {
  position: absolute;
  left: -6px;
  top: 16px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-brand, #b88c6e);
}
.lr-event-prepay .lr-event-dot { background: var(--lr-orange); }
.lr-event-rate_change .lr-event-dot { background: var(--lr-pink); }
.lr-event-body { flex: 1; min-width: 0; }
.lr-event-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.lr-event-title { font-size: 13px; font-weight: 600; color: var(--color-text, #3a2e22); }
.lr-event-sub { margin-top: 2px; font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.lr-event-note { word-break: break-all; }
.lr-event-del { cursor: pointer; color: var(--color-text-secondary, #7a6b5a); flex-shrink: 0; }
.lr-event-del:hover { color: var(--color-accent, #a8483a); }

/* 概览磁贴(与计算器同款) */
.lc-tiles { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.lc-tile { padding: 12px 14px; border-radius: 12px; background: var(--color-card-2, #e8dec8); min-width: 0; }
.lc-tile span { display: block; font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.lc-tile b { display: block; margin-top: 4px; font-size: 19px; font-weight: 700; color: var(--color-text, #3a2e22); word-break: break-all; }
.lc-tile em { display: block; margin-top: 4px; font-size: 11px; font-style: normal; color: var(--color-text-secondary, #7a6b5a); }
.lc-tile.emph { background: rgba(var(--color-brand-rgb), 0.16); }
.lc-tile.emph b { color: var(--color-accent, #a8483a); }

.lc-mini {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.12));
}
.lc-mini div { display: flex; justify-content: space-between; gap: 8px; font-size: 12px; min-width: 0; }
.lc-mini span { color: var(--color-text-secondary, #7a6b5a); flex-shrink: 0; }
.lc-mini b { color: var(--color-text, #3a2e22); font-weight: 600; word-break: break-all; }

.lc-field { margin-bottom: 14px; }
.lc-field-last { margin-bottom: 0; }
.lc-field > label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary, #7a6b5a);
  margin-bottom: 6px;
}
.lc-unit { font-weight: 400; }
.lc-hint { display: block; margin-top: 6px; font-size: 12px; line-height: 1.6; color: var(--color-text-secondary, #7a6b5a); }
.lc-hint-block { margin: 0; }
.lc-hint-inline { margin: 0; }
.lr-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 12px; }

/* ---- 还款流水线(自下而上) ---- */
.lr-drag-palette { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.lr-chip {
  height: 26px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  cursor: grab;
  border: 1px dashed;
  display: inline-flex;
  align-items: center;
  user-select: none;
  flex-shrink: 0;
}
.lr-chip:active { cursor: grabbing; }
.lr-chip-rate { color: var(--lr-pink); border-color: rgba(var(--lr-pink-rgb), 0.6); background: rgba(var(--lr-pink-rgb), 0.08); }
.lr-chip-prepay { color: var(--lr-orange); border-color: rgba(var(--lr-orange-rgb), 0.6); background: rgba(var(--lr-orange-rgb), 0.08); }
.lr-pipe {
  position: relative;
  margin-top: 6px;
  padding: 8px 4px 8px 0;
  max-height: 480px;
  overflow-y: auto;
}
.lr-pipe::before {
  content: '';
  position: absolute;
  left: 23px;
  top: 12px;
  bottom: 12px;
  width: 2px;
  border-radius: 1px;
  background: var(--color-border, rgba(58, 46, 34, 0.18));
}
.lr-pnode {
  position: relative;
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 7px 0 7px 42px;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b5a);
  min-width: 0;
}
.lr-pnode-text { display: inline-flex; align-items: baseline; gap: 8px; flex-wrap: wrap; min-width: 0; word-break: break-all; }
.lr-pnode-text b { color: var(--color-text, #3a2e22); font-weight: 600; }
/* 拖拽落点:只加背景与内描边,不动布局(改 padding/border 会把节点挤走,落点就会来回跳) */
.lr-droptarget {
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.16);
  border-radius: 8px;
  box-shadow: inset 3px 0 0 rgba(var(--color-brand-rgb, 184, 140, 110), 0.75);
}
.lr-dragging { opacity: 0.35; }
.lr-pipe .lr-pnode { cursor: grab; }
.lr-pipe .lr-pnode.lr-pev:active { cursor: grabbing; }
/* 历史期次:品牌色圆点 */
.lr-ppaid::before {
  content: '';
  position: absolute;
  left: 19px;
  top: 50%;
  transform: translateY(-50%);
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-brand, #b88c6e);
  box-shadow: 0 0 0 3px rgba(var(--color-brand-rgb, 184, 140, 110), 0.15);
}
/* 下一期:鼠尾草绿大圆点 + 光环(未还的第一期),文案加重 */
.lr-pnext { padding-top: 10px; padding-bottom: 10px; }
.lr-pnext::before {
  content: '';
  position: absolute;
  left: 17px;
  top: 50%;
  transform: translateY(-50%);
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--lr-sage);
  box-shadow: 0 0 0 4px rgba(var(--lr-sage-rgb), 0.2);
}
.lr-pnext .lr-pnode-text b { font-size: 13px; color: var(--lr-sage); }
.lr-node-ym { flex-shrink: 0; }
.lr-node-days { color: var(--color-accent, #a8483a); }
/* 事件节点:菱形,利率调整=主题粉 / 提前还款=陶土橙 */
.lr-pev .lr-shape {
  position: absolute;
  left: 20px;
  top: 50%;
  transform: translateY(-50%) rotate(45deg);
  width: 9px;
  height: 9px;
}
.lr-pev-rate .lr-shape { background: var(--lr-pink); }
.lr-pev-prepay .lr-shape { background: var(--lr-orange); }
.lr-pev-rate .lr-pnode-text b { color: var(--lr-pink); }
.lr-pev-prepay .lr-pnode-text b { color: var(--lr-orange); }
/* 未生效事件:虚线幽灵浮在顶部,整体走鼠尾草绿(状态优先于事件类型,类型由标签与悬浮说明给出) */
.lr-pnode.lr-future { opacity: 0.75; }
.lr-pnode.lr-future .lr-shape { background: transparent; border: 1.5px dashed var(--lr-sage); }
.lr-pnode.lr-future .lr-pnode-text b { color: var(--lr-sage); }
.lr-ev-note { color: var(--color-text-secondary, #7a6b5a); }
/* 年度辅助线:跨年处的横向虚线 + 年份 */
.lr-yline {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0 6px 42px;
  font-size: 11px;
  color: var(--color-text-secondary, #7a6b5a);
}
.lr-yline::before {
  content: '';
  width: 16px;
  border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.3));
}
.lr-yline::after {
  content: '';
  flex: 1;
  border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.3));
}
/* 拖拽落点提示条:绝对定位 + 常驻 DOM(不参与布局,拖拽中不增删元素,光标下的节点不变)
 * —— 夹缝处不再频闪,也不会因节点被换掉而丢掉这次拖拽 */
.lr-dropmark {
  position: absolute;
  left: 42px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  height: 18px;
  padding: 0 10px;
  font-size: 11px;
  line-height: 1;
  white-space: nowrap;
  border-radius: 999px;
  border: 1px dashed;
  background: var(--color-card, #faf6ec);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.12s ease;
}
.lr-dropmark.on { opacity: 1; }
.lr-drop-rate { color: var(--lr-pink); border-color: rgba(var(--lr-pink-rgb), 0.75); }
.lr-drop-prepay { color: var(--lr-orange); border-color: rgba(var(--lr-orange-rgb), 0.75); }

/* 节点悬浮数据(内容被 teleport 到 body,容器类名靠 :global 限定) */
.lr-tip { display: flex; flex-direction: column; gap: 3px; min-width: 190px; font-size: 12px; line-height: 1.5; }
.lr-tip-head { display: flex; align-items: center; gap: 8px; padding-bottom: 3px; border-bottom: 1px solid rgba(255, 255, 255, 0.18); }
.lr-tip-head b { font-size: 13px; }
.lr-tip-head span { opacity: 0.8; }
.lr-tip-head em { font-style: normal; opacity: 0.75; }
.lr-tip-row { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; }
.lr-tip-row > span { opacity: 0.78; }
.lr-tip-row.emph > b { font-weight: 700; }
.lr-tip-note { margin-top: 2px; padding-top: 3px; border-top: 1px solid rgba(255, 255, 255, 0.18); opacity: 0.8; word-break: break-all; }
.lr-tip-foot { margin-top: 2px; opacity: 0.7; }
:global(.lr-tip-popper) { max-width: 300px; }

.lc-sub { display: block; font-size: 11px; line-height: 1.5; color: var(--color-text-secondary, #7a6b5a); }
.lr-mark { color: var(--color-accent, #a8483a); }
.lr-event-row { font-weight: 600; }
.lr-good { color: var(--color-brand, #b88c6e); }
.lr-bad { color: var(--color-accent, #a8483a); }
.lc-pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.lc-card :deep(.el-table .cell) { word-break: break-word; }

@media (max-width: 920px) {
  .lr-layout { grid-template-columns: minmax(0, 1fr); }
  .lc-tiles { grid-template-columns: minmax(0, 1fr); }
  .lc-mini { grid-template-columns: minmax(0, 1fr); }
  .lr-form-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
