<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.loan.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="tab" size="small">
          <el-radio-button value="calc">{{ $t('tools.loan.tabCalc') }}</el-radio-button>
          <el-radio-button value="prepay">{{ $t('tools.loan.tabPrepay') }}</el-radio-button>
          <el-radio-button value="solve">{{ $t('tools.loan.tabSolve') }}</el-radio-button>
          <el-radio-button value="rec">{{ $t('tools.loan.tabRec') }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tb-right">
        <el-button size="small" @click="resetForm">{{ $t('tools.loan.reset') }}</el-button>
      </div>
    </PageToolbar>

    <el-alert
      v-if="inputError"
      :title="inputError"
      type="warning"
      :closable="false"
      show-icon
      class="lc-alert"
    />

    <div v-if="tab !== 'rec'" class="lc-layout">
      <!-- 左:参数 -->
      <div class="lc-col">
        <div v-if="tab !== 'solve'" class="card lc-card">
          <div class="section-label">{{ $t('tools.loan.params') }}</div>

          <div class="lc-field">
            <label>{{ $t('tools.loan.loanType') }}</label>
            <el-radio-group v-model="form.type" size="small">
              <el-radio-button :value="LOAN_TYPE.COMMERCIAL">{{ $t('tools.loan.typeCommercial') }}</el-radio-button>
              <el-radio-button :value="LOAN_TYPE.FUND">{{ $t('tools.loan.typeFund') }}</el-radio-button>
              <el-radio-button :value="LOAN_TYPE.COMBINED">{{ $t('tools.loan.typeCombined') }}</el-radio-button>
            </el-radio-group>
          </div>

          <!-- 单一贷款:总额 + 利率 -->
          <template v-if="!isCombined">
            <div class="lc-field">
              <label>{{ $t('tools.loan.totalAmount') }}<span class="lc-unit">{{ $t('tools.loan.wanUnit') }}</span></label>
              <el-input-number
                v-model="form.amount"
                :min="0"
                :max="99999"
                :step="10"
                :precision="2"
                :controls="false"
                style="width: 100%"
              />
            </div>
            <div class="lc-field">
              <label>{{ isFund ? $t('tools.loan.fundRate') : $t('tools.loan.commercialRate') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label>
              <el-input-number
                v-model="singleRate"
                :min="0"
                :max="36"
                :step="0.05"
                :precision="4"
                :controls="false"
                style="width: 100%"
              />
              <span class="lc-hint">{{ $t('tools.loan.rateHint') }}</span>
            </div>
          </template>

          <!-- 组合贷款:商贷 + 公积金 -->
          <template v-else>
            <div class="lc-field">
              <label>{{ $t('tools.loan.commercialAmount') }}<span class="lc-unit">{{ $t('tools.loan.wanUnit') }}</span></label>
              <el-input-number
                v-model="form.commercialAmount"
                :min="0"
                :max="99999"
                :step="10"
                :precision="2"
                :controls="false"
                style="width: 100%"
              />
            </div>
            <div class="lc-field">
              <label>{{ $t('tools.loan.commercialRate') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label>
              <el-input-number
                v-model="form.commercialRate"
                :min="0"
                :max="36"
                :step="0.05"
                :precision="4"
                :controls="false"
                style="width: 100%"
              />
            </div>
            <div class="lc-field">
              <label>{{ $t('tools.loan.fundAmount') }}<span class="lc-unit">{{ $t('tools.loan.wanUnit') }}</span></label>
              <el-input-number
                v-model="form.fundAmount"
                :min="0"
                :max="99999"
                :step="10"
                :precision="2"
                :controls="false"
                style="width: 100%"
              />
            </div>
            <div class="lc-field">
              <label>{{ $t('tools.loan.fundRate') }}<span class="lc-unit">{{ $t('tools.loan.percentUnit') }}</span></label>
              <el-input-number
                v-model="form.fundRate"
                :min="0"
                :max="36"
                :step="0.05"
                :precision="4"
                :controls="false"
                style="width: 100%"
              />
              <span class="lc-hint">{{ $t('tools.loan.rateHint') }}</span>
            </div>
            <div class="lc-mini">
              <div><span>{{ $t('tools.loan.combinedTotal') }}</span><b>{{ money(combinedTotalWan, 2) }} {{ $t('tools.loan.wanShort') }}</b></div>
              <div><span>{{ $t('tools.loan.months') }}</span><b>{{ months }} {{ $t('tools.loan.periodUnit') }}</b></div>
            </div>
          </template>

          <div class="lc-field">
            <label>{{ $t('tools.loan.years') }}<span class="lc-unit">{{ $t('tools.loan.yearUnit') }}</span></label>
            <el-input-number
              v-model="form.years"
              :min="1"
              :max="40"
              :step="1"
              :precision="0"
              :controls="false"
              style="width: 100%"
            />
            <div class="lc-quick">
              <button v-for="y in YEARS_QUICK" :key="y" type="button" :class="{ on: form.years === y }" @click="form.years = y">
                {{ y }}{{ $t('tools.loan.yearShort') }}
              </button>
            </div>
          </div>

          <div class="lc-field lc-field-last">
            <label>{{ $t('tools.loan.repayMethod') }}</label>
            <el-radio-group v-model="form.method" size="small">
              <el-radio-button :value="REPAY_METHOD.EQUAL_INSTALLMENT">{{ $t('tools.loan.equalInstallment') }}</el-radio-button>
              <el-radio-button :value="REPAY_METHOD.EQUAL_PRINCIPAL">{{ $t('tools.loan.equalPrincipal') }}</el-radio-button>
            </el-radio-group>
            <span class="lc-hint">{{ isEqualPrincipal ? $t('tools.loan.principalHint') : $t('tools.loan.installmentHint') }}</span>
          </div>
        </div>

        <!-- 反推利率:利率是待求量,不能和试算共用「贷款参数」表单,故自带一份参数 -->
        <div v-if="tab === 'solve'" class="card lc-card">
          <div class="section-label">{{ $t('tools.loan.solveParams') }}</div>

          <div class="lc-field">
            <label>{{ $t('tools.loan.totalAmount') }}<span class="lc-unit">{{ $t('tools.loan.wanUnit') }}</span></label>
            <el-input-number
              v-model="solve.amount"
              :min="0"
              :max="99999"
              :step="10"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
          </div>

          <div class="lc-field">
            <label>{{ $t('tools.loan.years') }}<span class="lc-unit">{{ $t('tools.loan.yearUnit') }}</span></label>
            <el-input-number
              v-model="solve.years"
              :min="1"
              :max="40"
              :step="1"
              :precision="0"
              :controls="false"
              style="width: 100%"
            />
            <div class="lc-quick">
              <button v-for="y in YEARS_QUICK" :key="y" type="button" :class="{ on: solve.years === y }" @click="solve.years = y">
                {{ y }}{{ $t('tools.loan.yearShort') }}
              </button>
            </div>
          </div>

          <div class="lc-field">
            <label>
              {{ isSolvePrincipal ? $t('tools.loan.solveFirstPayment') : $t('tools.loan.solvePayment') }}
              <span class="lc-unit">{{ $t('tools.loan.yuanUnit') }}</span>
            </label>
            <el-input-number
              v-model="solve.payment"
              :min="0"
              :step="100"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
            <span class="lc-hint">
              {{ isSolvePrincipal ? $t('tools.loan.solveFirstPaymentHint') : $t('tools.loan.solvePaymentHint') }}
            </span>
          </div>

          <!-- 首期与后续不同(放款日到首期还款日不足/超过整月)时,照账单补两个选填项 -->
          <div class="lc-field">
            <label>{{ $t('tools.loan.solveFirstPaymentOpt') }}<span class="lc-unit">{{ $t('tools.loan.yuanUnit') }}</span></label>
            <el-input-number
              v-model="solve.firstPayment"
              :min="0"
              :step="100"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
            <div class="lc-field lc-solve-days">
              <label>{{ $t('tools.loan.solveFirstDays') }}</label>
              <el-input-number
                v-model="solve.firstDays"
                :min="1"
                :max="366"
                :step="1"
                :precision="0"
                :controls="false"
                style="width: 100%"
              />
            </div>
            <span class="lc-hint">{{ $t('tools.loan.solveFirstOptHint') }}</span>
          </div>

          <div class="lc-field lc-field-last">
            <label>{{ $t('tools.loan.repayMethod') }}</label>
            <el-radio-group v-model="solve.method" size="small">
              <el-radio-button :value="REPAY_METHOD.EQUAL_INSTALLMENT">{{ $t('tools.loan.equalInstallment') }}</el-radio-button>
              <el-radio-button :value="REPAY_METHOD.EQUAL_PRINCIPAL">{{ $t('tools.loan.equalPrincipal') }}</el-radio-button>
            </el-radio-group>
            <span class="lc-hint">{{ $t('tools.loan.solveMethodHint') }}</span>
          </div>
        </div>

        <!-- 提前还款参数 -->
        <div v-if="tab === 'prepay'" class="card lc-card">
          <div class="section-label">{{ $t('tools.loan.prepayParams') }}</div>

          <div class="lc-field">
            <label>{{ $t('tools.loan.paidPeriods') }}<span class="lc-unit">{{ $t('tools.loan.monthUnit') }}</span></label>
            <el-input-number
              v-model="prepay.paidMonths"
              :min="0"
              :max="Math.max(0, months - 1)"
              :step="1"
              :precision="0"
              :controls="false"
              style="width: 100%"
            />
            <span class="lc-hint">{{ $t('tools.loan.paidPeriodsHint') }}</span>
          </div>

          <div class="lc-field">
            <label>{{ $t('tools.loan.prepayAmount') }}<span class="lc-unit">{{ $t('tools.loan.yuanUnit') }}</span></label>
            <el-input-number
              v-model="prepay.amount"
              :min="0"
              :step="10000"
              :precision="2"
              :controls="false"
              style="width: 100%"
            />
            <div class="lc-quick">
              <button v-for="q in QUICK_PREPAY" :key="q.value" type="button" @click="prepay.amount = q.value">
                {{ $t('tools.loan.' + q.key) }}
              </button>
              <button type="button" @click="prepay.amount = remainingPrincipal">{{ $t('tools.loan.payoff') }}</button>
            </div>
            <span v-if="prepayResult.valid && prepayResult.prepayCapped" class="lc-hint">
              {{ $t('tools.loan.prepayCappedHint', { amount: money(prepayResult.remaining.principal) }) }}
            </span>
          </div>

          <div v-if="isCombined" class="lc-field">
            <label>{{ $t('tools.loan.alloc') }}</label>
            <el-radio-group v-model="prepay.alloc" size="small">
              <el-radio-button :value="PREPAY_ALLOC.PROPORTION">{{ $t('tools.loan.allocProportion') }}</el-radio-button>
              <el-radio-button :value="PREPAY_ALLOC.HIGH_RATE_FIRST">{{ $t('tools.loan.allocHighRate') }}</el-radio-button>
            </el-radio-group>
            <span class="lc-hint">{{ $t('tools.loan.allocHint') }}</span>
          </div>

          <div v-if="prepayResult.valid" class="lc-mini">
            <div><span>{{ $t('tools.loan.paidPrincipal') }}</span><b>{{ money(prepayResult.paid.principal) }}</b></div>
            <div><span>{{ $t('tools.loan.paidInterest') }}</span><b>{{ money(prepayResult.paid.interest) }}</b></div>
            <div v-if="isCombined"><span>{{ $t('tools.loan.splitCommercial') }}</span><b>{{ money(prepayResult.alloc.commercial || 0) }}</b></div>
            <div v-if="isCombined"><span>{{ $t('tools.loan.splitFund') }}</span><b>{{ money(prepayResult.alloc.fund || 0) }}</b></div>
          </div>
        </div>
      </div>

      <!-- 右:结果概览 -->
      <div class="lc-col">
        <div v-if="tab === 'calc'" class="card lc-card">
          <div class="section-label">{{ $t('tools.loan.resultSummary') }}</div>
          <div class="lc-tiles">
            <div class="lc-tile emph">
              <span>{{ isEqualPrincipal ? $t('tools.loan.firstPayment') : $t('tools.loan.monthlyPayment') }}</span>
              <b>{{ money(summary.firstPayment) }}</b>
              <em v-if="isEqualPrincipal">{{ $t('tools.loan.decreasePerMonth', { v: money(summary.monthlyDecrease) }) }}</em>
              <em v-else>{{ $t('tools.loan.fixedMonthly') }}</em>
            </div>
            <div class="lc-tile">
              <span>{{ $t('tools.loan.totalInterest') }}</span>
              <b>{{ money(summary.totalInterest) }}</b>
              <em>{{ $t('tools.loan.interestRatio') }} {{ percent(summary.interestRatio) }}</em>
            </div>
            <div class="lc-tile">
              <span>{{ $t('tools.loan.totalPayment') }}</span>
              <b>{{ money(summary.totalPayment) }}</b>
              <em>{{ $t('tools.loan.principalOf', { v: money(summary.totalPrincipal) }) }}</em>
            </div>
            <div class="lc-tile">
              <span>{{ isEqualPrincipal ? $t('tools.loan.lastPayment') : $t('tools.loan.payoffPeriods') }}</span>
              <b v-if="isEqualPrincipal">{{ money(summary.lastPayment) }}</b>
              <b v-else>{{ summary.months }}</b>
              <em v-if="isEqualPrincipal">{{ summary.months }} {{ $t('tools.loan.periodUnit') }}</em>
              <em v-else>{{ form.years }} {{ $t('tools.loan.yearShort') }}</em>
            </div>
          </div>

          <el-table v-if="isCombined && calcResult.parts.length" :data="calcResult.parts" size="small" border class="lc-part-table">
            <el-table-column :label="$t('tools.loan.part')" min-width="96">
              <template #default="{ row }">{{ partLabel(row.key) }}</template>
            </el-table-column>
            <el-table-column :label="$t('tools.loan.colRate')" width="86" align="right">
              <template #default="{ row }">{{ row.rate }}%</template>
            </el-table-column>
            <el-table-column :label="$t('tools.loan.colPayment')" min-width="104" align="right">
              <template #default="{ row }">{{ money(row.firstPayment) }}</template>
            </el-table-column>
            <el-table-column :label="$t('tools.loan.colInterestTotal')" min-width="110" align="right">
              <template #default="{ row }">{{ money(row.totalInterest) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else-if="tab === 'prepay'" class="card lc-card">
          <div class="section-label">{{ $t('tools.loan.prepaySummary') }}</div>
          <template v-if="prepayResult.valid">
            <div class="lc-tiles">
              <div class="lc-tile emph">
                <span>{{ $t('tools.loan.remainingPrincipal') }}</span>
                <b>{{ money(prepayResult.remaining.principal) }}</b>
                <em>{{ $t('tools.loan.atPeriod', { n: prepayResult.paidPeriods }) }}</em>
              </div>
              <div class="lc-tile">
                <span>{{ $t('tools.loan.remainingInterest') }}</span>
                <b>{{ money(prepayResult.remaining.interest) }}</b>
                <em>{{ $t('tools.loan.remainingPeriods') }} {{ prepayResult.remaining.periods }}</em>
              </div>
              <div class="lc-tile">
                <span>{{ $t('tools.loan.prepayAmount') }}</span>
                <b>{{ money(prepayResult.prepayAmount) }}</b>
                <em>{{ isCombined ? $t('tools.loan.splitShown') : $t('tools.loan.singleLoan') }}</em>
              </div>
              <div class="lc-tile">
                <span>{{ $t('tools.loan.originalTotalInterest') }}</span>
                <b>{{ money(prepayResult.baseline.totalInterest) }}</b>
                <em>{{ $t('tools.loan.paidPlusLeft', { a: money(prepayResult.paid.interest), b: money(prepayResult.remaining.interest) }) }}</em>
              </div>
            </div>
            <p v-if="prepayResult.prepayZero" class="lc-hint lc-hint-block">{{ $t('tools.loan.prepayZeroHint') }}</p>
          </template>
          <p v-else class="lc-hint lc-hint-block">{{ $t('tools.loan.fillFirst') }}</p>
        </div>

        <div v-else class="card lc-card">
          <div class="section-label">{{ $t('tools.loan.solveResult') }}</div>
          <template v-if="solveResult.valid">
            <div class="lc-tiles">
              <div class="lc-tile emph">
                <span>{{ $t('tools.loan.annualRateSolved') }}</span>
                <b>{{ rate(solveResult.annualRate) }}%</b>
                <em>{{ $t('tools.loan.monthlyRateIs', { v: rate(solveResult.monthlyRate) }) }}</em>
              </div>
              <div class="lc-tile">
                <span>{{ $t('tools.loan.effectiveRate') }}</span>
                <b>{{ rate(solveResult.effectiveRate) }}%</b>
                <em>{{ $t('tools.loan.effectiveRateNote') }}</em>
              </div>
              <div class="lc-tile">
                <span>{{ $t('tools.loan.totalPayment') }}</span>
                <b>{{ money(solveResult.summary.totalPayment) }}</b>
                <em>{{ $t('tools.loan.principalOf', { v: money(solveResult.summary.totalPrincipal) }) }}</em>
              </div>
              <div class="lc-tile">
                <span>{{ $t('tools.loan.totalInterest') }}</span>
                <b>{{ money(solveResult.summary.totalInterest) }}</b>
                <em>{{ $t('tools.loan.interestRatio') }} {{ percent(solveResult.summary.interestRatio) }}</em>
              </div>
            </div>
            <div class="lc-mini">
              <div><span>{{ $t('tools.loan.surfaceRate') }}</span><b>{{ rate(solveResult.surfaceRate) }}%</b></div>
              <div><span>{{ $t('tools.loan.checkPayment') }}</span><b>{{ money(solveResult.checkPayment) }}</b></div>
            </div>
            <p class="lc-hint lc-hint-block">{{ $t('tools.loan.solveExplain') }}</p>
          </template>
          <p v-else class="lc-hint lc-hint-block">{{ $t('tools.loan.fillSolveFirst') }}</p>
        </div>
      </div>
    </div>

    <!-- 贷款记录(家庭数据,须登录;独立组件,自带参数/时间轴/流水) -->
    <LoanRecords v-if="tab === 'rec'" />

    <!-- 提前还款方案对比 -->
    <div v-if="tab === 'prepay'" class="card lc-card lc-block">
      <div class="lc-head">
        <div class="section-label lc-label-flat">{{ $t('tools.loan.compareTitle') }}</div>
        <span class="lc-hint lc-hint-inline">{{ $t('tools.loan.compareHint') }}</span>
      </div>
      <el-table :data="compareRows" size="small" border>
        <el-table-column :label="$t('tools.loan.colPlan')" min-width="150">
          <template #default="{ row }">
            <span :class="{ 'lc-best': row.best }">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colFirstPayment')" min-width="140" align="right">
          <template #default="{ row }">
            <div>{{ row.periods > 0 ? money(row.payment) : '—' }}</div>
            <span v-if="row.decrease > 0" class="lc-sub">{{ $t('tools.loan.decreasePerMonth', { v: money(row.decrease) }) }}</span>
            <span v-else-if="row.monthlySaved > 0 && row.periods > 0" class="lc-sub">{{ $t('tools.loan.monthlySaved', { v: money(row.monthlySaved) }) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colPeriodsLeft')" min-width="118" align="right">
          <template #default="{ row }">
            <div>{{ row.periods }}</div>
            <span v-if="row.savedPeriods > 0" class="lc-sub lc-good">{{ $t('tools.loan.minusPeriods', { n: row.savedPeriods }) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colRemainingInterest')" min-width="128" align="right">
          <template #default="{ row }">{{ money(row.remainingInterest) }}</template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colSavedInterest')" min-width="132" align="right">
          <template #default="{ row }">
            <div v-if="row.savedInterest > 0" class="lc-good">{{ money(row.savedInterest) }}</div>
            <div v-else>—</div>
            <span v-if="row.savedRatio > 0" class="lc-sub">{{ $t('tools.loan.savedRatio') }} {{ percent(row.savedRatio) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colClosePeriod')" min-width="112" align="right">
          <template #default="{ row }">
            <div v-if="row.periods > 0">{{ $t('tools.loan.periodNth', { n: row.closePeriod }) }}</div>
            <div v-else class="lc-good">{{ $t('tools.loan.paidOff') }}</div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 还款明细 -->
    <div v-if="tab !== 'rec'" class="card lc-card lc-block">
      <div class="lc-head">
        <div class="section-label lc-label-flat">
          {{ $t('tools.loan.' + detailTitleKey) }}
        </div>
        <div class="lc-head-actions">
          <el-radio-group v-if="tab === 'prepay'" v-model="prepayView" size="small">
            <el-radio-button :value="PREPAY_STRATEGY.SHORTEN">{{ $t('tools.loan.planShorten') }}</el-radio-button>
            <el-radio-button :value="PREPAY_STRATEGY.REDUCE">{{ $t('tools.loan.planReduce') }}</el-radio-button>
          </el-radio-group>
          <el-radio-group v-model="detailMode" size="small">
            <el-radio-button value="month">{{ $t('tools.loan.byMonth') }}</el-radio-button>
            <el-radio-button value="year">{{ $t('tools.loan.byYear') }}</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <el-table
        v-if="isCombined && tab === 'prepay' && prepayPartRows.length"
        :data="prepayPartRows"
        size="small"
        border
        class="lc-part-table lc-part-table-block"
      >
        <el-table-column :label="$t('tools.loan.part')" min-width="110">
          <template #default="{ row }">{{ partLabel(row.key) }}</template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colRate')" width="86" align="right">
          <template #default="{ row }">{{ row.rate }}%</template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.remainingPrincipal')" min-width="126" align="right">
          <template #default="{ row }">{{ money(row.remaining) }}</template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.prepayThisTime')" min-width="126" align="right">
          <template #default="{ row }">{{ money(row.prepayAmount) }}</template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colFirstPayment')" min-width="118" align="right">
          <template #default="{ row }">
            <div>{{ row.periods > 0 ? money(row.firstPayment) : '—' }}</div>
            <span v-if="row.monthlyDecrease > 0" class="lc-sub">{{ $t('tools.loan.decreasePerMonth', { v: money(row.monthlyDecrease) }) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.remainingPeriods')" width="104" align="right">
          <template #default="{ row }">{{ row.periods }}</template>
        </el-table-column>
        <el-table-column :label="$t('tools.loan.colSavedInterest')" min-width="126" align="right">
          <template #default="{ row }">{{ money(row.savedInterest) }}</template>
        </el-table-column>
      </el-table>

      <template v-if="viewRows.length">
        <el-table v-if="detailMode === 'month'" :data="pagedRows" size="small" border>
          <el-table-column prop="period" :label="$t('tools.loan.colPeriod')" width="86" align="right" />
          <el-table-column :label="$t('tools.loan.colPayment')" min-width="112" align="right">
            <template #default="{ row }">{{ money(row.payment) }}</template>
          </el-table-column>
          <el-table-column v-if="isCombined" :label="$t('tools.loan.splitCommercial')" min-width="112" align="right">
            <template #default="{ row }">{{ row.splitCommercial == null ? '—' : money(row.splitCommercial) }}</template>
          </el-table-column>
          <el-table-column v-if="isCombined" :label="$t('tools.loan.splitFund')" min-width="112" align="right">
            <template #default="{ row }">{{ row.splitFund == null ? '—' : money(row.splitFund) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colPrincipal')" min-width="112" align="right">
            <template #default="{ row }">{{ money(row.principal) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colInterest')" min-width="112" align="right">
            <template #default="{ row }">{{ money(row.interest) }}</template>
          </el-table-column>
          <el-table-column :label="$t('tools.loan.colBalance')" min-width="126" align="right">
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
            :total="viewRows.length"
            layout="total, sizes, prev, pager, next"
            size="small"
            background
          />
        </div>
        <p v-if="tab === 'prepay'" class="lc-foot">{{ $t('tools.loan.prepayDetailHint', { n: prepayResult.paidPeriods }) }}</p>
      </template>
      <p v-else class="lc-hint lc-hint-block">{{ $t('tools.loan.' + detailEmptyKey) }}</p>
    </div>

    <p class="lc-foot">{{ $t('tools.loan.disclaimer') }}</p>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  calculateLoan,
  prepaymentComparison,
  solveRate,
  formatYuan,
  LOAN_TYPE,
  REPAY_METHOD,
  PREPAY_STRATEGY,
  PREPAY_ALLOC,
} from '@/utils/loan'

// 贷款记录与记账本/工具箱共用同一组件同一份家庭数据;异步加载,只做试算的用户不为它买单
const LoanRecords = defineAsyncComponent(() => import('@/views/tools/LoanRecords.vue'))

const { t } = useI18n()

const YEARS_QUICK = [5, 10, 15, 20, 25, 30]
const QUICK_PREPAY = [
  { key: 'quick10', value: 100000 },
  { key: 'quick20', value: 200000 },
  { key: 'quick30', value: 300000 },
]

/** 表单默认值(重置用同一份,避免两处走偏) */
const DEFAULTS = {
  type: LOAN_TYPE.COMMERCIAL,
  method: REPAY_METHOD.EQUAL_INSTALLMENT,
  amount: 100, // 万元
  commercialAmount: 70, // 万元
  fundAmount: 30, // 万元
  commercialRate: 3.1, // %
  fundRate: 2.6, // %
  years: 30,
}
const PREPAY_DEFAULTS = { paidMonths: 12, amount: 200000, alloc: PREPAY_ALLOC.PROPORTION }
/** 反推利率默认值:100 万 / 30 年 / 每期 4270.16 元 —— 对应常见的 3.1% 房贷 */
const SOLVE_DEFAULTS = {
  amount: 100, // 万元
  years: 30,
  payment: 4270.16, // 元
  firstPayment: null, // 首期还款额(选填;首期与后续不同时照账单填)
  firstDays: null, // 首期计息天数(选填;不填按整月)
  method: REPAY_METHOD.EQUAL_INSTALLMENT,
}

const tab = ref('calc')
const form = reactive({ ...DEFAULTS })
const prepay = reactive({ ...PREPAY_DEFAULTS })
const solve = reactive({ ...SOLVE_DEFAULTS })
const prepayView = ref(PREPAY_STRATEGY.SHORTEN)
const detailMode = ref('month')
const page = ref(1)
const pageSize = ref(12)

const isCombined = computed(() => tab.value !== 'solve' && form.type === LOAN_TYPE.COMBINED)
const isFund = computed(() => form.type === LOAN_TYPE.FUND)
const isEqualPrincipal = computed(() => form.method === REPAY_METHOD.EQUAL_PRINCIPAL)
const months = computed(() => Math.max(0, Math.round(Number(form.years) || 0) * 12))
const combinedTotalWan = computed(() => (Number(form.commercialAmount) || 0) + (Number(form.fundAmount) || 0))

/** 单一贷款的利率字段:商业贷与公积金各留一处,切换类型不互相覆盖 */
const singleRate = computed({
  get: () => (isFund.value ? form.fundRate : form.commercialRate),
  set: (v) => {
    if (isFund.value) form.fundRate = v
    else form.commercialRate = v
  },
})

const wanToYuan = (wan) => Math.max(0, Number(wan) || 0) * 10000

const loanInput = computed(() => ({
  type: form.type,
  method: form.method,
  months: months.value,
  amount: wanToYuan(form.amount),
  rate: Number(singleRate.value) || 0,
  commercialAmount: wanToYuan(form.commercialAmount),
  commercialRate: Number(form.commercialRate) || 0,
  fundAmount: wanToYuan(form.fundAmount),
  fundRate: Number(form.fundRate) || 0,
}))

const calcResult = computed(() => calculateLoan(loanInput.value))
const summary = computed(() => calcResult.value.summary)

const INPUT_ERROR_KEYS = {
  NO_AMOUNT: 'invalidAmount',
  BAD_PERIODS: 'invalidYears',
  NO_PAYMENT: 'invalidPayment',
  PAYMENT_TOO_LOW: 'paymentTooLow',
  RATE_TOO_HIGH: 'rateTooHigh',
  BAD_FIRST_DAYS: 'badFirstDays',
  FIRST_PAYMENT_TOO_LOW: 'firstPaymentTooLow',
}
const inputError = computed(() => {
  if (tab.value === 'rec') return '' // 贷款记录组件有自己的告警
  const code = tab.value === 'solve' ? solveResult.value.error : calcResult.value.error
  return code ? t('tools.loan.' + (INPUT_ERROR_KEYS[code] || 'invalidAmount')) : ''
})

const prepayResult = computed(() =>
  prepaymentComparison({
    ...loanInput.value,
    paidPeriods: Math.min(Math.max(0, Math.round(Number(prepay.paidMonths) || 0)), Math.max(0, months.value - 1)),
    prepayAmount: Math.max(0, Number(prepay.amount) || 0),
    alloc: prepay.alloc,
  }),
)
const remainingPrincipal = computed(() => (prepayResult.value.valid ? prepayResult.value.remaining.principal : 0))

/** 反推利率:只取总金额/期限/每期还款额 —— 利率本身是待求量 */
const isSolvePrincipal = computed(() => solve.method === REPAY_METHOD.EQUAL_PRINCIPAL)
const solveResult = computed(() =>
  solveRate({
    amount: wanToYuan(solve.amount),
    months: Math.max(0, Math.round(Number(solve.years) || 0) * 12),
    payment: Math.max(0, Number(solve.payment) || 0),
    firstPayment: Number(solve.firstPayment) > 0 ? Number(solve.firstPayment) : 0,
    firstDays: Number(solve.firstDays) > 0 ? Number(solve.firstDays) : 0,
    method: solve.method,
  }),
)

/** 方案对比表:不提前还款 / 缩短年限(月供不变) / 减少月供(期限不变) */
const compareRows = computed(() => {
  const r = prepayResult.value
  if (!r.valid) return []
  const base = {
    key: 'BASE',
    name: t('tools.loan.planKeep'),
    payment: r.baseline.nextPayment,
    decrease: r.baseline.monthlyDecrease,
    monthlySaved: 0,
    periods: r.baseline.periods,
    savedPeriods: 0,
    remainingInterest: r.baseline.remainingInterest,
    savedInterest: 0,
    savedRatio: 0,
    closePeriod: r.months,
    best: false,
  }
  const planKeys = [
    [PREPAY_STRATEGY.SHORTEN, 'planShorten'],
    [PREPAY_STRATEGY.REDUCE, 'planReduce'],
  ]
  const options = planKeys.map(([key, label]) => {
    const o = r.options[key]
    return {
      key,
      name: t('tools.loan.' + label),
      payment: o.firstPayment,
      decrease: o.monthlyDecrease,
      monthlySaved: o.monthlySaved,
      periods: o.periods,
      savedPeriods: o.savedPeriods,
      remainingInterest: o.remainingInterest,
      savedInterest: o.savedInterest,
      savedRatio: o.savedRatio,
      closePeriod: r.paidPeriods + o.periods,
      best: false,
    }
  })
  const bestInterest = Math.max(...options.map((o) => o.savedInterest))
  for (const o of options) o.best = o.savedInterest > 0 && o.savedInterest === bestInterest
  return [base, ...options]
})

/** 明细数据源:试算/反推看各自计划;提前还款看所选方案的新计划 */
const viewRows = computed(() => {
  if (tab.value === 'calc') return calcResult.value.rows
  if (tab.value === 'solve') return solveResult.value.rows
  return prepayResult.value.valid ? prepayResult.value.options[prepayView.value].rows : []
})

const pagedRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return viewRows.value.slice(start, start + pageSize.value)
})

/** 组合贷提前还款:各贷款部分自己的新计划(商贷结清后公积金仍在还,单看合并期数看不出差别) */
const prepayPartRows = computed(() => {
  if (tab.value !== 'prepay' || !prepayResult.value.valid) return []
  return prepayResult.value.options[prepayView.value].parts
})

/** 明细表为空时的说明:贷款已结清 vs 参数还没填好,是两回事 */
const detailEmptyKey = computed(() => {
  if (tab.value === 'solve') return 'fillSolveFirst'
  if (!calcResult.value.valid) return 'fillFirst'
  if (tab.value === 'prepay' && prepayResult.value.valid && prepayResult.value.options[prepayView.value].periods === 0) {
    return 'detailPaidOff'
  }
  return 'fillFirst'
})

const DETAIL_TITLE_KEYS = { calc: 'detailTitle', prepay: 'prepayDetailTitle', solve: 'solveDetailTitle' }
const detailTitleKey = computed(() => DETAIL_TITLE_KEYS[tab.value] || 'detailTitle')

/** 按年汇总(按期数每 12 期归一年,末期不足一年照常累计) */
const yearlyRows = computed(() => {
  const out = []
  let cur = null
  for (const r of viewRows.value) {
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

// 任何参数变化都回到第一页,避免停留在超出范围的页码上
watch(viewRows, () => { page.value = 1 })
watch(pageSize, () => { page.value = 1 })
watch(() => form.type, () => { if (!isCombined.value) prepay.alloc = PREPAY_ALLOC.PROPORTION })

const money = (v, digits = 2) => formatYuan(v, digits)
const percent = (v) => `${((Number(v) || 0) * 100).toFixed(2)}%`
const rate = (v) => (Number(v) || 0).toFixed(4)
const partLabel = (key) => (key === 'fund' ? t('tools.loan.splitFund') : t('tools.loan.splitCommercial'))

function resetForm() {
  Object.assign(form, DEFAULTS)
  Object.assign(prepay, PREPAY_DEFAULTS)
  Object.assign(solve, SOLVE_DEFAULTS)
  prepayView.value = PREPAY_STRATEGY.SHORTEN
  detailMode.value = 'month'
  page.value = 1
}
</script>

<style scoped>
.lc-alert { margin-bottom: 12px; }
.lc-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.lc-col { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.lc-card { padding: 18px 20px; }
.lc-card .section-label { margin-bottom: 14px; }
.lc-block { margin-top: 16px; }

/* ---- 表单 ---- */
.lc-field { margin-bottom: 16px; }
.lc-field-last { margin-bottom: 0; }
.lc-solve-days { margin: 12px 0 0; }
.lc-field > label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary, #7a6b5a);
  margin-bottom: 6px;
}
.lc-unit { font-weight: 400; }
.lc-hint {
  display: block;
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-hint-block { margin: 0; }
.lc-hint-inline { margin: 0; }
.lc-quick { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.lc-quick button {
  height: 26px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  background: transparent;
  color: var(--color-text-secondary, #7a6b5a);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s;
}
.lc-quick button:hover { color: var(--color-brand); border-color: var(--color-brand); }
.lc-quick button.on { color: var(--color-brand); border-color: var(--color-brand); background: rgba(var(--color-brand-rgb), 0.1); }

/* ---- 概览磁贴 ---- */
.lc-tiles {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.lc-tile {
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--color-card-2, #e8dec8);
  min-width: 0;
}
.lc-tile span { display: block; font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.lc-tile b {
  display: block;
  margin-top: 4px;
  font-size: 19px;
  font-weight: 700;
  color: var(--color-text, #3a2e22);
  word-break: break-all;
}
.lc-tile em {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  font-style: normal;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-tile.emph { background: rgba(var(--color-brand-rgb), 0.16); }
.lc-tile.emph b { color: var(--color-accent, #a8483a); }

/* ---- 参数/结果里的小结行 ---- */
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

/* ---- 卡片头 ---- */
.lc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.lc-head-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.lc-label-flat { margin-bottom: 0 !important; }
.lc-head .lc-hint-inline { flex: 1; min-width: 180px; }
.lc-part-table { margin-top: 16px; }
.lc-part-table-block { margin: 0 0 14px; }

/* ---- 表格内小字 ---- */
.lc-sub { display: block; font-size: 11px; line-height: 1.5; color: var(--color-text-secondary, #7a6b5a); }
.lc-good { color: var(--color-brand, #b88c6e); }
.lc-best { font-weight: 700; color: var(--color-accent, #a8483a); }
.lc-pager { display: flex; justify-content: flex-end; margin-top: 12px; }
.lc-foot {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-secondary, #7a6b5a);
}
.lc-card > .lc-foot:last-child { margin-top: 16px; }

/* 表格数字右对齐后避免末尾空格挤字 */
.lc-card :deep(.el-table .cell) { word-break: break-word; }

@media (max-width: 920px) {
  .lc-layout { grid-template-columns: minmax(0, 1fr); }
  .lc-tiles { grid-template-columns: minmax(0, 1fr); }
  .lc-mini { grid-template-columns: minmax(0, 1fr); }
  .lc-head-actions { width: 100%; }
}
</style>
