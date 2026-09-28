/**
 * 贷款(房贷)计算核心 —— 纯函数,无副作用、无 IO,便于单独校验与复用。
 *
 * 口径说明:
 * - 金额入参单位为「元」,利率为「年利率百分数」(3.1 表示 3.1%);内部统一换算成「分」(整数)计算,
 *   避免浮点累加误差(0.1+0.2 类问题在几百期累加后能差出几元)。
 * - 利息按「剩余本金 × 月利率」逐期计算并四舍五入到分;月供(或月还本金)四舍五入到分后固定不变,
 *   末期按剩余本金结清 —— 与银行实际出账口径一致(末期金额可能比其他期多/少几分到几元)。
 * - 等额本息:每期还款额固定,利息占比逐期下降;等额本金:每期归还本金固定,月供逐期递减。
 * - 组合贷款:商贷与公积金各自独立计息,月供为两者之和(与银行「商贷月供 + 公积金月供」一致)。
 * - 提前还款只有「缩短年限(月供不变)」与「减少月供(期限不变)」两种银行常规处理方式;
 *   不含提前还款违约金、评估费、保险费等费用。
 * - 反推利率:已知「总额 + 期数 + 每期还款额」求利率。等额本息无解析解,靠「月供随利率单调递增」
 *   二分求根;等额本金首期月供 = 本金/期数 + 本金×月利率,直接解出。组合贷款两个利率无法由
 *   一个月供唯一确定,不支持反推。
 * - 首期与后续不同:放款日到首个还款日不足/超过整月时,首期利息按实际天数计
 *   (日利率 = 年利率 ÷ 360,与「月利率 × 天数 ÷ 30」等价),首期还款额默认为标准月供、可按账单覆盖。
 *   银行不因首期少几天利息就重算后续月供,故第 2 期起仍是原月供,差额落在「首期还本多一点 +
 *   末期金额略小」上。不填放款日即按整月口径,与纯试算结果完全一致。
 * - 贷款流水(loanLedger):按「放款 + 利率调整 + 提前还款」事件时间轴逐期重算,用于记录真实贷款走向。
 */

export const LOAN_TYPE = {
  COMMERCIAL: 'COMMERCIAL',
  FUND: 'FUND',
  COMBINED: 'COMBINED',
}

export const REPAY_METHOD = {
  EQUAL_INSTALLMENT: 'EQUAL_INSTALLMENT',
  EQUAL_PRINCIPAL: 'EQUAL_PRINCIPAL',
}

/** 提前还款处理方式:缩短年限(月供不变)/ 减少月供(期限不变) */
export const PREPAY_STRATEGY = {
  SHORTEN: 'SHORTEN',
  REDUCE: 'REDUCE',
}

/** 组合贷提前还款金额在商贷/公积金之间的分摊方式 */
export const PREPAY_ALLOC = {
  PROPORTION: 'PROPORTION',
  HIGH_RATE_FIRST: 'HIGH_RATE_FIRST',
}

/** 贷款记录的资金渠道(组合贷按渠道拆成两条记录,各自跟踪利率) */
export const LOAN_CHANNEL = {
  COMMERCIAL: 'COMMERCIAL',
  FUND: 'FUND',
  OTHER: 'OTHER',
}

/** 贷款流水的事件类型:利率调整 / 提前还款(提前结清 = 金额≥剩余本金的提前还款) */
export const LOAN_EVENT = {
  RATE_CHANGE: 'RATE_CHANGE',
  PREPAY: 'PREPAY',
}

/** 首期计息天数:未填或非法时按整月 30 天 */
const DEFAULT_FIRST_DAYS = 30

/** 首期计息天数的合理上限(放款日到首期还款日不该超过一年) */
const MAX_FIRST_DAYS = 366

/** 期数上限(100 年),防参数异常时死循环 */
const MAX_PERIODS = 1200

/** 反推利率的可接受上限:月利率 100%(名义年利率 1200%),超出按参数异常处理 */
const MAX_SOLVE_MONTHLY_RATE = 1

/** 元 → 分 */
export function toCents(yuan) {
  const n = Number(yuan)
  return Number.isFinite(n) ? Math.round(n * 100) : 0
}

/** 分 → 元 */
export function toYuan(cents) {
  return Math.round(cents) / 100
}

/** 金额格式化(千分位 + 指定小数位),仅用于展示 */
export function formatYuan(yuan, digits = 2) {
  const n = Number(yuan)
  return (Number.isFinite(n) ? n : 0).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

/** 年利率百分数 → 月利率 */
function rateOf(ratePct) {
  return (Number(ratePct) || 0) / 1200
}

/** 首期计息天数归一:未填/非法按整月 30 天 */
function normalizeDays(days) {
  const d = Math.round(Number(days) || 0)
  return d > 0 ? d : DEFAULT_FIRST_DAYS
}

/** 'YYYY-MM-DD' 或 Date → { y, m, d };解析失败返回 null */
function parseDate(value) {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime())
      ? null
      : { y: value.getFullYear(), m: value.getMonth() + 1, d: value.getDate() }
  }
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(String(value || ''))
  return m ? { y: Number(m[1]), m: Number(m[2]), d: Number(m[3]) } : null
}

/**
 * 两个日期之间的天数(算头不算尾)—— 放款日 → 首期还款日的计息天数用它。
 * 只按年月日算,不受时区/时分秒影响。
 */
export function daysBetween(from, to) {
  const a = parseDate(from)
  const b = parseDate(to)
  if (!a || !b) return 0
  return Math.round((Date.UTC(b.y, b.m - 1, b.d) - Date.UTC(a.y, a.m - 1, a.d)) / 86400000)
}

/** 第 n 期的还款年月('YYYY-MM',由首期还款日按月递推);参数不可解析返回空串 */
export function periodYearMonth(firstPayDate, period) {
  const f = parseDate(firstPayDate)
  const n = Math.round(Number(period) || 0)
  if (!f || n < 1) return ''
  const total = f.y * 12 + (f.m - 1) + (n - 1)
  return `${Math.floor(total / 12)}-${String((total % 12) + 1).padStart(2, '0')}`
}

/** 截至某天已还期数(首期还款日当天即算第 1 期已还) */
export function periodsPaid(firstPayDate, today = new Date()) {
  const f = parseDate(firstPayDate)
  const t = parseDate(today)
  if (!f || !t) return 0
  let n = t.y * 12 + t.m - (f.y * 12 + f.m)
  if (n < 0) return 0
  if (t.d < f.d) n -= 1 // 当月还款日还没到
  return Math.max(0, n + 1)
}

/** 第 n 期的还款日期('YYYY-MM-DD',日 = 首期还款日的日,超出当月天数时取月末);参数不可解析返回空串 */
export function periodDate(firstPayDate, period) {
  const f = parseDate(firstPayDate)
  const n = Math.round(Number(period) || 0)
  if (!f || n < 1) return ''
  const total = f.y * 12 + (f.m - 1) + (n - 1)
  const y = Math.floor(total / 12)
  const m = (total % 12) + 1
  const daysInMonth = new Date(Date.UTC(y, m, 0)).getUTCDate()
  const d = Math.min(f.d, daysInMonth)
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

/** 首期利息(分):本金(分)× 年利率 × 天数 ÷ 36000 */
function firstPeriodInterestC(amountC, ratePct, days) {
  return Math.round((amountC * (Number(ratePct) || 0) * normalizeDays(days)) / 36000)
}

/**
 * 首期利息(元):放款日到首个还款日按实际天数计,日利率 = 年利率 ÷ 360。
 * 与「月利率 × 天数 ÷ 30」等价(月利率 = 年利率 ÷ 12),天数 30 即整月利息 —— 全仓只留这一处实现。
 */
export function firstPeriodInterest(amountYuan, ratePct, days) {
  return toYuan(firstPeriodInterestC(toCents(amountYuan), ratePct, days))
}

/**
 * 首期还款额参考值(元):首期本金按整月口径不变、利息换成实际天数。
 * 等额本息 = 标准月供里的本金部分 + 实际天数利息;等额本金 = 本金÷期数 + 实际天数利息。
 * 这是给用户对照账单的参考值,真实首期还款额以账单为准(可直接覆盖)。
 */
export function firstPaymentPreview(input) {
  const amountC = toCents(input && input.amount)
  const months = Math.round(Number(input && input.months) || 0)
  if (amountC <= 0 || months < 1) return 0
  const rate = Math.max(0, Number(input && input.rate) || 0)
  const days = normalizeDays(input && input.firstDays)
  const interest = firstPeriodInterestC(amountC, rate, days)
  const principal = input && input.method === REPAY_METHOD.EQUAL_PRINCIPAL
    ? Math.round(amountC / months)
    : annuityPaymentC(amountC, rate, months) - Math.round(amountC * rateOf(rate))
  return toYuan(Math.max(0, principal) + interest)
}

/** 等额本息月供(分):PMT = P·i·(1+i)^n / ((1+i)^n − 1) */
function annuityPaymentC(balanceC, ratePct, periods) {
  const i = rateOf(ratePct)
  if (i <= 0) return Math.round(balanceC / periods)
  const f = Math.pow(1 + i, periods)
  return Math.round((balanceC * i * f) / (f - 1))
}

/** 汇总一张计划表(分单位) */
function finishC(rows, monthlyDecrease = 0) {
  let totalPayment = 0
  let totalPrincipal = 0
  let totalInterest = 0
  for (const r of rows) {
    totalPayment += r.payment
    totalPrincipal += r.principal
    totalInterest += r.interest
  }
  return {
    rows,
    periods: rows.length,
    totalPayment,
    totalPrincipal,
    totalInterest,
    firstPayment: rows.length ? rows[0].payment : 0,
    lastPayment: rows.length ? rows[rows.length - 1].payment : 0,
    monthlyDecrease,
    closed: rows.length > 0 && rows[rows.length - 1].balance === 0,
  }
}

const emptyPlanC = () => finishC([])

/**
 * 按固定期数生成还款计划(首次试算/期限不变的重算用它)
 * 等额本息:先算固定月供,末期结清;等额本金:每期本金固定,末期结清
 */
function planByPeriodsC(balanceC, ratePct, periods, method) {
  const rows = []
  let remain = balanceC
  const i = rateOf(ratePct)
  const n = Math.round(Number(periods) || 0)
  if (n <= 0 || remain <= 0) return emptyPlanC()
  let monthlyDecrease = 0

  if (method === REPAY_METHOD.EQUAL_PRINCIPAL) {
    const perPrincipal = Math.round(remain / n)
    monthlyDecrease = Math.round(perPrincipal * i)
    for (let k = 1; k <= n; k++) {
      const interest = Math.round(remain * i)
      const principal = k === n ? remain : Math.min(perPrincipal, remain)
      remain -= principal
      rows.push({ period: k, payment: principal + interest, principal, interest, balance: remain })
      if (remain <= 0) break
    }
  } else {
    const pmt = annuityPaymentC(balanceC, ratePct, n)
    for (let k = 1; k <= n; k++) {
      const interest = Math.round(remain * i)
      let principal = pmt - interest
      if (k === n || principal >= remain) principal = remain
      else if (principal <= 0) break // 月供不足以覆盖利息:参数异常,不再往下滚动
      remain -= principal
      rows.push({ period: k, payment: principal + interest, principal, interest, balance: remain })
      if (remain <= 0) break
    }
  }
  return finishC(rows, monthlyDecrease)
}

/**
 * 按固定月供生成还款计划(等额本息·缩短年限用):月供不变,末期不足一期月供时结清
 * @param {number} [maxPeriods] 期数上限:到达上限仍有余额时,最后一期按剩余本金结清。
 *   传入「原计划剩余期数」时,若月供已不足以缩短期数,结果与原计划的剩余部分完全一致
 *   (A=0 或提前还款额极小时不会因末期的取整差额平白多出一期)
 */
function planByPaymentC(balanceC, ratePct, paymentC, maxPeriods = 0) {
  const rows = []
  let remain = balanceC
  const i = rateOf(ratePct)
  const pmt = Math.round(Number(paymentC) || 0)
  const cap = Math.min(maxPeriods > 0 ? maxPeriods : MAX_PERIODS, MAX_PERIODS)
  let k = 0
  while (remain > 0 && k < cap) {
    k++
    const interest = Math.round(remain * i)
    let principal = Math.min(pmt - interest, remain)
    if (principal <= 0) break // 月供不足以覆盖利息:参数异常
    if (k === cap) principal = remain
    remain -= principal
    rows.push({ period: k, payment: principal + interest, principal, interest, balance: remain })
  }
  return finishC(rows, 0)
}

/**
 * 按固定「每月归还本金」生成还款计划(等额本金·缩短年限用):每月本金不变,期限随之缩短
 * 传入原计划的每月本金时,结果与原计划的剩余部分一致
 */
function planByPrincipalC(balanceC, ratePct, perPrincipalC, maxPeriods = 0) {
  const rows = []
  let remain = balanceC
  const i = rateOf(ratePct)
  const step = Math.max(1, Math.round(Number(perPrincipalC) || 0))
  const cap = Math.min(maxPeriods > 0 ? maxPeriods : MAX_PERIODS, MAX_PERIODS)
  let k = 0
  while (remain > 0 && k < cap) {
    k++
    const interest = Math.round(remain * i)
    const principal = k === cap ? remain : Math.min(step, remain)
    remain -= principal
    rows.push({ period: k, payment: principal + interest, principal, interest, balance: remain })
  }
  return finishC(rows, Math.round(step * i))
}

/** 按贷款类型拆出参与计算的贷款部分(金额为 0 的部分不参与) */
function buildPartsC(input) {
  const parts = []
  const push = (key, amountYuan, rate) => {
    const amount = toCents(amountYuan)
    if (amount > 0) parts.push({ key, amount, rate: Math.max(0, Number(rate) || 0) })
  }
  if (input.type === LOAN_TYPE.COMBINED) {
    push('commercial', input.commercialAmount, input.commercialRate)
    push('fund', input.fundAmount, input.fundRate)
  } else if (input.type === LOAN_TYPE.FUND) {
    push('fund', input.amount, input.rate)
  } else {
    push('commercial', input.amount, input.rate)
  }
  return parts
}

/** 入参校验,返回错误码(空串表示通过) */
function validateInput(input) {
  const months = Math.round(Number(input && input.months) || 0)
  if (months < 1 || months > MAX_PERIODS) return 'BAD_PERIODS'
  if (!buildPartsC(input).length) return 'NO_AMOUNT'
  return ''
}

/** 合并多个贷款部分的计划表(各期相加;某部分已结清时该期贡献 0) */
function mergePlansC(plans) {
  const len = plans.reduce((m, p) => Math.max(m, p.schedule.rows.length), 0)
  const rows = []
  let totalPayment = 0
  let totalPrincipal = 0
  let totalInterest = 0
  let monthlyDecrease = 0
  for (const p of plans) monthlyDecrease += p.schedule.monthlyDecrease
  for (let idx = 0; idx < len; idx++) {
    let payment = 0
    let principal = 0
    let interest = 0
    let balance = 0
    const split = {}
    for (const p of plans) {
      const r = p.schedule.rows[idx]
      if (!r) continue
      payment += r.payment
      principal += r.principal
      interest += r.interest
      balance += r.balance
      split[p.key] = r.payment
    }
    totalPayment += payment
    totalPrincipal += principal
    totalInterest += interest
    rows.push({ period: idx + 1, payment, principal, interest, balance, split })
  }
  return {
    rows,
    periods: rows.length,
    totalPayment,
    totalPrincipal,
    totalInterest,
    firstPayment: rows.length ? rows[0].payment : 0,
    lastPayment: rows.length ? rows[rows.length - 1].payment : 0,
    monthlyDecrease,
    closed: rows.length > 0 && rows[rows.length - 1].balance === 0,
  }
}

/** 分单位内部模型:各部分计划 + 合并计划 + 固定的月供/月还本金(供提前还款复用) */
function computeCoreC(input) {
  const months = Math.round(Number(input.months) || 0)
  const method = input.method === REPAY_METHOD.EQUAL_PRINCIPAL
    ? REPAY_METHOD.EQUAL_PRINCIPAL
    : REPAY_METHOD.EQUAL_INSTALLMENT
  const parts = buildPartsC(input).map((p) => {
    const schedule = planByPeriodsC(p.amount, p.rate, months, method)
    return {
      ...p,
      schedule,
      // 缩短年限时保持不变的量:等额本息是月供,等额本金是每月归还本金
      perPrincipalC: method === REPAY_METHOD.EQUAL_PRINCIPAL ? Math.round(p.amount / months) : 0,
      paymentC: method === REPAY_METHOD.EQUAL_PRINCIPAL ? 0 : annuityPaymentC(p.amount, p.rate, months),
    }
  })
  return { months, method, parts, merged: mergePlansC(parts) }
}

/** 计划行(分)→(元):split 存在时转出各贷款部分的月供 */
function planRowsToYuan(rows) {
  return rows.map((r) => ({
    period: r.period,
    payment: toYuan(r.payment),
    principal: toYuan(r.principal),
    interest: toYuan(r.interest),
    balance: toYuan(r.balance),
    splitCommercial: r.split && r.split.commercial !== undefined ? toYuan(r.split.commercial) : null,
    splitFund: r.split && r.split.fund !== undefined ? toYuan(r.split.fund) : null,
  }))
}

/** 合并后的计划行(元) */
const mergeRowsToYuan = (merged) => planRowsToYuan(merged.rows)

/** 计划汇总(分)→(元) */
function summaryOfYuan(schedule) {
  return {
    months: schedule.periods,
    firstPayment: toYuan(schedule.firstPayment),
    lastPayment: toYuan(schedule.lastPayment),
    monthlyDecrease: toYuan(schedule.monthlyDecrease),
    totalPayment: toYuan(schedule.totalPayment),
    totalPrincipal: toYuan(schedule.totalPrincipal),
    totalInterest: toYuan(schedule.totalInterest),
    interestRatio: schedule.totalPrincipal > 0 ? schedule.totalInterest / schedule.totalPrincipal : 0,
    closed: schedule.closed,
  }
}

const zeroSummary = () => ({
  months: 0,
  firstPayment: 0,
  lastPayment: 0,
  monthlyDecrease: 0,
  totalPayment: 0,
  totalPrincipal: 0,
  totalInterest: 0,
  interestRatio: 0,
})

/**
 * 贷款试算(单一贷款或组合贷款)
 * @param {object} input
 * @param {string} input.type            LOAN_TYPE.*
 * @param {string} input.method          REPAY_METHOD.*
 * @param {number} input.months          还款期数(月)
 * @param {number} [input.amount]        贷款总额(元) —— 商业/公积金单一贷款
 * @param {number} [input.rate]          年利率(%) —— 商业/公积金单一贷款
 * @param {number} [input.commercialAmount] 商贷额度(元) —— 组合贷款
 * @param {number} [input.commercialRate]   商贷年利率(%) —— 组合贷款
 * @param {number} [input.fundAmount]       公积金额度(元) —— 组合贷款
 * @param {number} [input.fundRate]         公积金年利率(%) —— 组合贷款
 * @returns {{valid:boolean,error:string,method:string,months:number,parts:Array,rows:Array,summary:object}}
 */
export function calculateLoan(input) {
  const error = validateInput(input)
  if (error) {
    return { valid: false, error, method: '', months: 0, parts: [], rows: [], summary: zeroSummary() }
  }
  const core = computeCoreC(input)
  const merged = core.merged
  return {
    valid: true,
    error: '',
    method: core.method,
    months: core.months,
    parts: core.parts.map((p) => ({
      key: p.key,
      amount: toYuan(p.amount),
      rate: p.rate,
      months: p.schedule.periods,
      firstPayment: toYuan(p.schedule.firstPayment),
      lastPayment: toYuan(p.schedule.lastPayment),
      monthlyDecrease: toYuan(p.schedule.monthlyDecrease),
      totalPayment: toYuan(p.schedule.totalPayment),
      totalPrincipal: toYuan(p.schedule.totalPrincipal),
      totalInterest: toYuan(p.schedule.totalInterest),
    })),
    rows: mergeRowsToYuan(merged),
    summary: summaryOfYuan(merged),
  }
}

/** 提前还款金额在各方之间的分摊(分) */
function allocatePrepay(parts, prepayC, mode) {
  const alloc = {}
  for (const p of parts) alloc[p.key] = 0
  let left = prepayC
  if (left <= 0) return alloc

  if (mode === PREPAY_ALLOC.HIGH_RATE_FIRST) {
    for (const p of [...parts].sort((a, b) => b.rate - a.rate)) {
      if (left <= 0) break
      const take = Math.min(left, p.remainingC)
      alloc[p.key] = take
      left -= take
    }
    return alloc
  }

  const total = parts.reduce((s, p) => s + p.remainingC, 0)
  if (total <= 0) return alloc
  let assigned = 0
  parts.forEach((p, idx) => {
    let take = idx === parts.length - 1 ? prepayC - assigned : Math.round((prepayC * p.remainingC) / total)
    take = Math.max(0, Math.min(take, p.remainingC))
    alloc[p.key] = take
    assigned += take
  })
  // 比例分摊被剩余本金封顶后若有余额,回填给还有剩余本金的贷款部分
  let leftover = prepayC - assigned
  for (const p of parts) {
    if (leftover <= 0) break
    const room = p.remainingC - alloc[p.key]
    const add = Math.min(room, leftover)
    alloc[p.key] += add
    leftover -= add
  }
  return alloc
}

/** 按提前还款方式重算各方计划(分单位) */
function simulateStrategyC(core, parts, alloc, strategy) {
  const remainPeriods = core.months - core.paidPeriods
  const plans = parts.map((p) => {
    const cut = alloc[p.key] || 0
    const balance = Math.max(0, p.remainingC - cut)
    if (balance <= 0 || remainPeriods <= 0) {
      return { key: p.key, rate: p.rate, amount: p.amount, cut, balance, schedule: emptyPlanC() }
    }
    let schedule
    if (strategy === PREPAY_STRATEGY.SHORTEN) {
      // 月供(等额本息)或每月本金(等额本金)保持不变,期限随之缩短;期数上限 = 原剩余期数
      schedule = core.method === REPAY_METHOD.EQUAL_PRINCIPAL
        ? planByPrincipalC(balance, p.rate, p.perPrincipalC, remainPeriods)
        : planByPaymentC(balance, p.rate, p.paymentC, remainPeriods)
    } else {
      schedule = planByPeriodsC(balance, p.rate, remainPeriods, core.method)
    }
    return { key: p.key, rate: p.rate, amount: p.amount, cut, balance, schedule }
  })
  return { plans, merged: mergePlansC(plans) }
}

const sumBy = (list, key) => list.reduce((s, x) => s + x[key], 0)

/**
 * 提前还款方案对比:在同一时点、同一笔金额下,分别算出「缩短年限(月供不变)」与
 * 「减少月供(期限不变)」的剩余利息、节省利息、剩余期数、缩短期数、新月供,以及不提前还款的基准。
 * @param {object} input              同 calculateLoan 的入参
 * @param {number} input.paidPeriods  已还期数(月),提前还款发生在该期还款之后
 * @param {number} input.prepayAmount 提前还款金额(元),超过剩余本金时按剩余本金计(等价一次性结清)
 * @param {string} [input.alloc]      PREPAY_ALLOC.*,组合贷款下提前还款金额的分摊方式
 */
export function prepaymentComparison(input) {
  const error = validateInput(input)
  if (error) return { valid: false, error }
  const core = computeCoreC(input)
  const paidPeriods = Math.min(Math.max(0, Math.round(Number(input.paidPeriods) || 0)), core.months)
  core.paidPeriods = paidPeriods

  const parts = core.parts.map((p) => {
    const paidRows = p.schedule.rows.slice(0, paidPeriods)
    const paidInterestC = sumBy(paidRows, 'interest')
    const paidPrincipalC = sumBy(paidRows, 'principal')
    return {
      ...p,
      paidInterestC,
      paidPrincipalC,
      remainingC: Math.max(0, p.amount - paidPrincipalC),
      // 该部分「不提前还款」时后续还要付的利息,用于算它自己贡献了多少节省
      remainingInterestC: sumBy(p.schedule.rows.slice(paidPeriods), 'interest'),
    }
  })

  const remainingC = sumBy(parts, 'remainingC')
  const paidInterestC = sumBy(parts, 'paidInterestC')
  const paidPrincipalC = sumBy(parts, 'paidPrincipalC')
  const prepayC = Math.min(Math.max(0, toCents(input.prepayAmount)), remainingC)
  const alloc = allocatePrepay(parts, prepayC, input.alloc)

  const remainPeriods = core.months - paidPeriods
  const baseRows = core.merged.rows.slice(paidPeriods)
  const baseRemainingInterestC = sumBy(baseRows, 'interest')
  const baseline = {
    periods: remainPeriods,
    rows: baseRows.map((r) => ({
      period: r.period,
      payment: toYuan(r.payment),
      principal: toYuan(r.principal),
      interest: toYuan(r.interest),
      balance: toYuan(r.balance),
      splitCommercial: r.split.commercial === undefined ? null : toYuan(r.split.commercial),
      splitFund: r.split.fund === undefined ? null : toYuan(r.split.fund),
    })),
    nextPayment: baseRows.length ? toYuan(baseRows[0].payment) : 0,
    lastPayment: baseRows.length ? toYuan(baseRows[baseRows.length - 1].payment) : 0,
    monthlyDecrease: toYuan(core.merged.monthlyDecrease),
    remainingInterest: toYuan(baseRemainingInterestC),
    totalInterest: toYuan(core.merged.totalInterest),
  }

  const buildOption = (strategy) => {
    const sim = simulateStrategyC(core, parts, alloc, strategy)
    const merged = sim.merged
    const remainingInterestC = merged.totalInterest
    const savedInterestC = Math.max(0, baseRemainingInterestC - remainingInterestC)
    const monthlyDecrease = toYuan(merged.monthlyDecrease)
    return {
      strategy,
      periods: merged.periods,
      savedPeriods: Math.max(0, remainPeriods - merged.periods),
      firstPayment: toYuan(merged.firstPayment),
      lastPayment: toYuan(merged.lastPayment),
      monthlyDecrease,
      monthlySaved: Math.max(0, toYuan((baseRows.length ? baseRows[0].payment : 0) - merged.firstPayment)),
      remainingInterest: toYuan(remainingInterestC),
      totalInterest: toYuan(paidInterestC + remainingInterestC),
      savedInterest: toYuan(savedInterestC),
      savedRatio: baseRemainingInterestC > 0 ? savedInterestC / baseRemainingInterestC : 0,
      rows: mergeRowsToYuan(merged),
      parts: sim.plans.map((pl) => {
        const before = parts.find((p) => p.key === pl.key) || {}
        const savedC = Math.max(0, (before.remainingInterestC || 0) - pl.schedule.totalInterest)
        return {
          key: pl.key,
          rate: pl.rate,
          amount: toYuan(pl.amount),
          remaining: toYuan(before.remainingC || 0),
          prepayAmount: toYuan(pl.cut),
          newBalance: toYuan(pl.balance),
          periods: pl.schedule.periods,
          firstPayment: toYuan(pl.schedule.firstPayment),
          lastPayment: toYuan(pl.schedule.lastPayment),
          monthlyDecrease: toYuan(pl.schedule.monthlyDecrease),
          remainingInterest: toYuan(pl.schedule.totalInterest),
          savedInterest: toYuan(savedC),
        }
      }),
    }
  }

  return {
    valid: true,
    error: '',
    method: core.method,
    months: core.months,
    paidPeriods,
    remainPeriods,
    prepayZero: prepayC <= 0,
    paid: {
      periods: paidPeriods,
      principal: toYuan(paidPrincipalC),
      interest: toYuan(paidInterestC),
      total: toYuan(paidPrincipalC + paidInterestC),
    },
    remaining: {
      principal: toYuan(remainingC),
      periods: remainPeriods,
      interest: toYuan(baseRemainingInterestC),
    },
    prepayAmount: toYuan(prepayC),
    prepayCapped: toCents(input.prepayAmount) > remainingC,
    alloc: Object.fromEntries(parts.map((p) => [p.key, toYuan(alloc[p.key] || 0)])),
    baseline,
    options: {
      [PREPAY_STRATEGY.SHORTEN]: buildOption(PREPAY_STRATEGY.SHORTEN),
      [PREPAY_STRATEGY.REDUCE]: buildOption(PREPAY_STRATEGY.REDUCE),
    },
  }
}

/**
 * 按逐期现金流求月利率(IRR):NPV(i) = Σ cₖ ÷ (1+i)^tₖ − P = 0。
 * NPV 关于 i 单调递减,二分即可;首期金额与计息天数可单独给(首期不足/超过整月时按 t₁ = 天数/30 折现)。
 * 首期为整月且金额一致时,方程退化为等额本息年金公式,结果与纯年金求根完全一致。
 * 用 −t 次幂形式,避免长期限 + 高利率下 (1+i)^n 溢出成 Infinity(Infinity/Infinity → NaN)。
 * @returns {number} 月利率(小数,如 0.002583);无解返回 NaN
 */
function solveCashFlowRate(balanceC, periods, paymentC, firstPaymentC = 0, firstDays = DEFAULT_FIRST_DAYS) {
  const n = Math.max(1, Math.round(periods))
  const c1 = firstPaymentC > 0 ? firstPaymentC : paymentC
  const t1 = normalizeDays(firstDays) / 30
  const npv = (i) => {
    let v = -balanceC + c1 * Math.pow(1 + i, -t1)
    for (let k = 2; k <= n; k++) v += paymentC * Math.pow(1 + i, -(t1 + k - 1))
    return v
  }

  // NPV(0) = 还款总额 − 本金 ≤ 0 说明零利率都还不完本金,无正利率解
  if (npv(0) <= 0) return NaN
  let lo = 0
  let hi = 0.01
  while (npv(hi) > 0 && hi < MAX_SOLVE_MONTHLY_RATE) hi = Math.min(MAX_SOLVE_MONTHLY_RATE, hi * 2)
  if (npv(hi) > 0) return NaN
  for (let k = 0; k < 200 && hi - lo > 1e-15; k++) {
    const mid = (lo + hi) / 2
    if (npv(mid) > 0) lo = mid
    else hi = mid
  }
  return (lo + hi) / 2
}

/**
 * 反推利率:由「贷款总额 + 还款期数 + 每期还款额」求放款当时的年利率。
 * - 等额本息:按逐期现金流 IRR 二分求根(见 solveCashFlowRate);
 * - 等额本金:每期归还本金固定,首期还款额 A₁ = P/n + P·i·(天数/30),直接解出
 *   i = (A₁ − P/n) ÷ (P × 天数/30)(天数 30 即整月,退化为 (A₁ − P/n)/P)。
 * **首期与后续不同**(放款日到首期还款日不足/超过整月)时,填首期还款额与首期计息天数即可照账单反推,
 * 不填则按整月口径 —— 直接拿账单首期金额当「每期还款额」会算出偏移的利率。
 * 结果同时给出「名义年利率(月利率×12)」「实际年化(按月折算)」「表面费率(利息合计÷本金÷年数)」。
 * @param {object} input
 * @param {number} input.amount    贷款总额(元)
 * @param {number} input.months    还款期数(月)
 * @param {number} input.payment   每期还款额(元);等额本金下为「首期还款额」
 * @param {string} [input.method]  REPAY_METHOD.*
 * @param {number} [input.firstPayment] 首期还款额(元,可选;不填按每期还款额)
 * @param {number} [input.firstDays]    首期计息天数(可选,默认 30)
 * @returns {{valid:boolean,error:string,method:string,months:number,monthlyRate:number,annualRate:number,
 *   effectiveRate:number,surfaceRate:number,checkPayment:number,rows:Array,summary:object}}
 */
export function solveRate(input) {
  const months = Math.round(Number(input && input.months) || 0)
  const method = input && input.method === REPAY_METHOD.EQUAL_PRINCIPAL
    ? REPAY_METHOD.EQUAL_PRINCIPAL
    : REPAY_METHOD.EQUAL_INSTALLMENT
  const amountC = toCents(input && input.amount)
  const paymentC = toCents(input && input.payment)
  const firstDaysRaw = Number(input && input.firstDays) || 0
  const firstDays = normalizeDays(firstDaysRaw)
  const firstPaymentC = toCents(input && input.firstPayment)

  const invalid = (error) => ({
    valid: false,
    error,
    method,
    months: 0,
    monthlyRate: 0,
    annualRate: 0,
    effectiveRate: 0,
    surfaceRate: 0,
    checkPayment: 0,
    rows: [],
    summary: zeroSummary(),
  })

  if (months < 1 || months > MAX_PERIODS) return invalid('BAD_PERIODS')
  if (amountC <= 0) return invalid('NO_AMOUNT')
  if (paymentC <= 0) return invalid('NO_PAYMENT')
  // 零利率时每期至少也要还「本金 ÷ 期数」,低于它说明利率为负,参数必有一处填错
  if (paymentC <= amountC / months) return invalid('PAYMENT_TOO_LOW')
  if (firstDaysRaw && !(firstDays >= 1 && firstDays <= MAX_FIRST_DAYS)) return invalid('BAD_FIRST_DAYS')

  const monthly = method === REPAY_METHOD.EQUAL_PRINCIPAL
    ? (paymentC - amountC / months) / (amountC * (firstDays / 30))
    : solveCashFlowRate(amountC, months, paymentC, firstPaymentC, firstDays)
  if (!Number.isFinite(monthly) || monthly > MAX_SOLVE_MONTHLY_RATE) return invalid('RATE_TOO_HIGH')
  // 首期还款额连首期利息都不够 → 本金不减反增,账面对不上
  if (firstPaymentC > 0 && firstPaymentC <= firstPeriodInterestC(amountC, monthly * 1200, firstDays)) {
    return invalid('FIRST_PAYMENT_TOO_LOW')
  }

  const annualPct = monthly * 1200
  // 明细与「每期还款额」自证都用流水引擎出,首期金额/天数与输入的账单一致
  const schedule = ledgerToScheduleC(runLedgerC({
    amountC,
    months,
    method,
    rate0: annualPct,
    firstDays,
    firstPaymentC,
    events: [],
  }))
  return {
    valid: true,
    error: '',
    method,
    months: schedule.periods,
    monthlyRate: monthly * 100,
    annualRate: annualPct,
    effectiveRate: (Math.pow(1 + monthly, 12) - 1) * 100,
    surfaceRate: (schedule.totalInterest / amountC / (months / 12)) * 100,
    // 按反推利率正算的首期还款额,与输入的还款额对照即可判断这次反推是否符合账单
    checkPayment: toYuan(schedule.firstPayment),
    rows: planRowsToYuan(schedule.rows),
    summary: summaryOfYuan(schedule),
  }
}

/* ==================== 贷款流水:放款 + 利率调整 + 提前还款 ==================== */

const zeroLedgerSummary = () => ({
  months: 0,
  periods: 0,
  settled: false,
  settledAt: 0,
  firstPayment: 0,
  lastPayment: 0,
  totalPayment: 0,
  totalPrincipal: 0,
  totalInterest: 0,
  interestRatio: 0,
  prepaidTotal: 0,
  currentRate: 0,
})

/** 按当前月供(或每月归还本金)推算还需多少期结清 —— 「缩短年限」后的结清期次用它 */
function periodsToPayoffC(balanceC, ratePct, stepC, method) {
  const i = rateOf(ratePct)
  let remain = balanceC
  let n = 0
  while (remain > 0 && n < MAX_PERIODS) {
    n++
    const interest = Math.round(remain * i)
    const principal = method === REPAY_METHOD.EQUAL_PRINCIPAL
      ? Math.min(stepC, remain)
      : Math.min(stepC - interest, remain)
    if (principal <= 0) return MAX_PERIODS // 月供不足以覆盖利息,不再往下滚
    remain -= principal
  }
  return n
}

/** 事件归一 + 校验:按生效期次排序(同期先利率调整、后提前还款),错误返回错误码 */
function normalizeLedgerEvents(events, months) {
  const list = []
  for (const raw of Array.isArray(events) ? events : []) {
    if (!raw) continue
    const period = Math.round(Number(raw.effectivePeriod) || 0)
    const type = raw.type
    if (period < 1 || period > months) return { error: 'BAD_EVENT_PERIOD' }
    if (type === LOAN_EVENT.RATE_CHANGE) {
      const ratePct = Number(raw.rate)
      if (!Number.isFinite(ratePct) || ratePct < 0 || ratePct > 100) return { error: 'BAD_EVENT_VALUE' }
      list.push({ type, period, ratePct })
    } else if (type === LOAN_EVENT.PREPAY) {
      const amountC = toCents(raw.amount)
      if (amountC <= 0) return { error: 'BAD_EVENT_VALUE' }
      list.push({
        type,
        period,
        amountC,
        strategy: raw.strategy === PREPAY_STRATEGY.REDUCE ? PREPAY_STRATEGY.REDUCE : PREPAY_STRATEGY.SHORTEN,
      })
    } else {
      return { error: 'BAD_EVENT_VALUE' }
    }
  }
  // 同一期两者都有时:利率先生效(第 N+1 期起),提前还款再按新利率推算结清期次
  list.sort((a, b) => a.period - b.period || (a.type === LOAN_EVENT.RATE_CHANGE ? -1 : 1))
  return { events: list, error: '' }
}

/** 流水(分)→ 与 calculateLoan 同构的计划汇总(元),便于反推页复用同一张明细表 */
function ledgerToScheduleC(run) {
  let totalPayment = 0
  let totalPrincipal = 0
  let totalInterest = 0
  for (const r of run.rows) {
    totalPayment += r.payment
    totalPrincipal += r.principal
    totalInterest += r.interest
  }
  const last = run.rows.length ? run.rows[run.rows.length - 1] : null
  return {
    rows: run.rows,
    periods: run.rows.length,
    totalPayment,
    totalPrincipal,
    totalInterest,
    firstPayment: run.rows.length ? run.rows[0].payment : 0,
    lastPayment: last ? last.payment : 0,
    monthlyDecrease: 0,
  }
}

/**
 * 逐期滚动出流水(内部,分单位)。
 * 第 1 期利息按首期天数计;其后沿用既有口径(利息 = 剩余本金 × 月利率取整到分、月供固定、末期结清)。
 * 事件在第 N 期还款之后生效:利率调整 → 第 N+1 期起按新利率;提前还款 → 第 N 期后减少剩余本金。
 */
function runLedgerC({ amountC, months, method, rate0, firstDays, firstPaymentC, events }) {
  const byPeriod = new Map()
  for (const e of events) {
    if (!byPeriod.has(e.period)) byPeriod.set(e.period, [])
    byPeriod.get(e.period).push(e)
  }

  const rows = []
  let remain = amountC
  let rate = rate0
  let planEnd = months
  let payment = method === REPAY_METHOD.EQUAL_PRINCIPAL ? 0 : annuityPaymentC(amountC, rate0, months)
  let perPrincipal = method === REPAY_METHOD.EQUAL_PRINCIPAL ? Math.max(1, Math.round(amountC / months)) : 0
  let prepaidTotal = 0
  let settledAt = 0

  for (let k = 1; k <= planEnd && k <= MAX_PERIODS && remain > 0; k++) {
    // 1) 上一期还款之后生效的事件
    const flags = []
    let recalc = false
    let shorten = false
    for (const e of byPeriod.get(k - 1) || []) {
      if (e.type === LOAN_EVENT.RATE_CHANGE) {
        rate = e.ratePct
        recalc = true
        flags.push({ type: e.type, rate: e.ratePct })
      } else {
        const cut = Math.min(e.amountC, remain)
        remain -= cut
        prepaidTotal += cut
        flags.push({ type: e.type, amount: toYuan(cut), strategy: e.strategy })
        if (e.strategy === PREPAY_STRATEGY.SHORTEN) shorten = true
        else recalc = true
      }
    }
    // 事件落在「上一期还款之后」:标记挂到上一期那一行,并把该行剩余本金更新为事件后的余额
    // —— 提前还款/结清在流水里一眼可见,结清时最后一行余额就是 0
    if (flags.length && rows.length) {
      const prev = rows[rows.length - 1]
      prev.events = flags
      prev.balance = remain
    }
    if (remain <= 0) {
      settledAt = k - 1
      break
    }

    // 2) 缩短年限:月供(或每月本金)不变 → 结清期次提前
    if (shorten) {
      const step = method === REPAY_METHOD.EQUAL_PRINCIPAL ? perPrincipal : payment
      planEnd = Math.min(planEnd, k - 1 + periodsToPayoffC(remain, rate, step, method))
    }
    // 3) 利率调整 / 减少月供:期限不变,按剩余期数重算月供(等额本金重算每月归还本金)
    if (recalc) {
      const left = Math.max(1, planEnd - (k - 1))
      if (method === REPAY_METHOD.EQUAL_PRINCIPAL) perPrincipal = Math.max(1, Math.round(remain / left))
      else payment = annuityPaymentC(remain, rate, left)
    }

    // 4) 本期利息:第 1 期按实际天数(不足/超过整月),其后按整月
    const interest = k === 1 ? firstPeriodInterestC(remain, rate, firstDays) : Math.round(remain * rateOf(rate))
    let pay
    let principal
    if (method === REPAY_METHOD.EQUAL_PRINCIPAL) {
      principal = Math.min(perPrincipal, remain)
      // 账单上的首期还款额优先:首期本金按实收金额倒推
      if (k === 1 && firstPaymentC > 0) principal = firstPaymentC - interest
      pay = principal + interest
    } else {
      // 第 1 期:首期本金按整月口径不变、利息按实际天数 → 首期还款额自然与后续不同(天数 30 时即标准月供);
      // 账单上有准确金额时优先用它,首期本金随之倒推。
      pay = k === 1
        ? (firstPaymentC > 0 ? firstPaymentC : payment - Math.round(remain * rateOf(rate)) + interest)
        : payment
      principal = pay - interest
    }
    if (principal <= 0) break // 还款额不足以覆盖利息:参数异常,不再往下滚
    if (k === planEnd || principal >= remain) {
      principal = remain
      pay = principal + interest
    }
    remain -= principal
    rows.push({
      period: k,
      rate,
      payment: pay,
      principal,
      interest,
      balance: remain,
      days: k === 1 ? normalizeDays(firstDays) : 0,
      events: flags,
    })
  }
  if (remain <= 0 && !settledAt) settledAt = rows.length ? rows[rows.length - 1].period : 0
  return { rows, settledAt, prepaidTotal, planEnd, settled: remain <= 0 }
}

/**
 * 贷款流水:按「放款 + 利率调整 + 提前还款」的事件时间轴逐期重算真实还款流水(纯函数)。
 *
 * 事件统一语义 —— **在第 N 期还款之后生效**(effectivePeriod = N,必须 ≥ 1):
 *   利率调整 → 第 N+1 期起按新利率计息;提前还款 → 第 N 期后减少剩余本金(金额 ≥ 剩余本金即结清)。
 *   第 1 期的利率由贷款的初始年利率决定,同一件事不在两处表达,故事件生效期次不能填 0。
 *
 * 首期(与后续不同):放款日到首个还款日不足/超过整月时,填 firstDays 即按实际天数计首期利息。
 *   首期本金按整月口径不变、利息换成实际天数,故首期还款额与后续月供不同(天数 30 时正好等于标准月供);
 *   firstPayment 可按账单覆盖(不填就用这个参考口径)。第 2 期起仍按标准月供继续,末期结清。
 *
 * 利率调整与「减少月供」都按「期限不变」重算月供;「缩短年限」保持月供、把结清期次提前 ——
 * 与银行处理方式一致。另给「全程不调整不提前还款」的基准对照,拆出利率浮动与提前还款各自的利息影响。
 *
 * @param {object} input
 * @param {number} input.amount          贷款本金(元)
 * @param {number} input.months          原定还款期数
 * @param {string} input.method          REPAY_METHOD.*
 * @param {number} input.rate            初始年利率(%)
 * @param {number} [input.firstDays]     首期计息天数(放款日 → 首期还款日;不填按 30 天整月)
 * @param {number} [input.firstPayment]  首期还款额(元;不填按标准月供)
 * @param {Array}  [input.events]        事件:[{type, effectivePeriod, rate|amount, strategy}]
 * @returns {{valid:boolean,error:string,method:string,months:number,firstDays:number,rows:Array,
 *   segments:Array,summary:object,settled:boolean,settledAt:number,baseline:object,delta:object}}
 */
export function loanLedger(input) {
  const months = Math.round(Number(input && input.months) || 0)
  const method = input && input.method === REPAY_METHOD.EQUAL_PRINCIPAL
    ? REPAY_METHOD.EQUAL_PRINCIPAL
    : REPAY_METHOD.EQUAL_INSTALLMENT
  const amountC = toCents(input && input.amount)
  const rate0 = Math.max(0, Number(input && input.rate) || 0)
  const firstDaysRaw = Number(input && input.firstDays) || 0
  const firstDays = normalizeDays(firstDaysRaw)
  const firstPaymentC = toCents(input && input.firstPayment)
  const parsed = normalizeLedgerEvents(input && input.events, months)

  const invalid = (error) => ({
    valid: false,
    error,
    method,
    months: 0,
    firstDays,
    firstPayment: 0,
    rows: [],
    segments: [],
    summary: zeroLedgerSummary(),
    settled: false,
    settledAt: 0,
    baseline: null,
    delta: null,
  })

  if (months < 1 || months > MAX_PERIODS) return invalid('BAD_PERIODS')
  if (amountC <= 0) return invalid('NO_AMOUNT')
  if (firstDaysRaw && (firstDaysRaw < 1 || firstDaysRaw > MAX_FIRST_DAYS)) return invalid('BAD_FIRST_DAYS')
  if (parsed.error) return invalid(parsed.error)
  // 首期还款额连首期利息都不够 → 本金不减反增,账面对不上
  if (firstPaymentC > 0 && firstPaymentC <= firstPeriodInterestC(amountC, rate0, firstDays)) {
    return invalid('FIRST_PAYMENT_TOO_LOW')
  }

  const run = (events) => runLedgerC({ amountC, months, method, rate0, firstDays, firstPaymentC, events })
  const main = run(parsed.events)
  if (!main.rows.length) return invalid('PAYMENT_TOO_LOW')
  // 基准对照:① 全程不调整、不提前还款 ② 只调利率、不提前还款
  const rateOnly = run(parsed.events.filter((e) => e.type === LOAN_EVENT.RATE_CHANGE))
  const base = run([])

  const rows = main.rows.map((r) => ({
    period: r.period,
    rate: r.rate,
    payment: toYuan(r.payment),
    principal: toYuan(r.principal),
    interest: toYuan(r.interest),
    balance: toYuan(r.balance),
    days: r.days,
    events: r.events,
    rateChanged: r.events.some((e) => e.type === LOAN_EVENT.RATE_CHANGE),
    prepaid: r.events.some((e) => e.type === LOAN_EVENT.PREPAY),
  }))

  // 每段(同一利率)汇总:利率调整后的月供变化在流水里分段呈现;末期取整差额不另立一段。
  // 「月供」取段内首个常规期(首期按实际天数计息的金额单独存 firstPayment,页面以小字标注)。
  const segments = []
  for (const r of rows) {
    const cur = segments[segments.length - 1]
    if (cur && cur.rate === r.rate) {
      cur.toPeriod = r.period
      cur.interest = Math.round((cur.interest + r.interest) * 100) / 100
      cur.principal = Math.round((cur.principal + r.principal) * 100) / 100
      cur.lastPayment = r.payment
      if (!cur.regularPayment && r.period > cur.fromPeriod) cur.regularPayment = r.payment
    } else {
      segments.push({
        fromPeriod: r.period,
        toPeriod: r.period,
        rate: r.rate,
        payment: r.period === 1 && r.days ? 0 : r.payment,
        firstPayment: r.payment,
        lastPayment: r.payment,
        interest: r.interest,
        principal: r.principal,
      })
    }
  }
  for (const s of segments) {
    if (!s.payment) s.payment = s.regularPayment || s.firstPayment
  }

  const totalPaymentC = sumBy(main.rows, 'payment')
  const totalPrincipalC = sumBy(main.rows, 'principal')
  const totalInterestC = sumBy(main.rows, 'interest')
  const baseInterestC = sumBy(base.rows, 'interest')
  const rateOnlyInterestC = sumBy(rateOnly.rows, 'interest')

  return {
    valid: true,
    error: '',
    method,
    months,
    firstDays,
    firstPayment: toYuan(firstPaymentC),
    rows,
    segments,
    settled: main.settled,
    settledAt: main.settledAt,
    summary: {
      months: main.rows.length,
      periods: main.rows.length,
      settled: main.settled,
      settledAt: main.settledAt,
      firstPayment: toYuan(main.rows[0].payment),
      lastPayment: toYuan(main.rows[main.rows.length - 1].payment),
      totalPayment: toYuan(totalPaymentC),
      totalPrincipal: toYuan(totalPrincipalC),
      totalInterest: toYuan(totalInterestC),
      interestRatio: totalPrincipalC > 0 ? totalInterestC / totalPrincipalC : 0,
      prepaidTotal: toYuan(main.prepaidTotal),
      currentRate: main.rows[main.rows.length - 1].rate,
    },
    baseline: {
      periods: base.rows.length,
      settledAt: base.settledAt,
      totalPayment: toYuan(sumBy(base.rows, 'payment')),
      totalInterest: toYuan(baseInterestC),
    },
    delta: {
      // 利率浮动比全程不变多付的利息(负值=比基准更省)
      rateImpact: toYuan(rateOnlyInterestC - baseInterestC),
      // 提前还款省下的利息
      prepaySaved: toYuan(rateOnlyInterestC - totalInterestC),
      // 实际总利息与基准之差(闭合:rateImpact − prepaySaved)
      totalDiff: toYuan(totalInterestC - baseInterestC),
    },
  }
}

/**
 * 按「已还到第几期」截取当前状态(页面显示进度用):剩余本金/剩余期数/当前月供/当前利率/已还本息。
 * 已还期数由调用方给(通常用 periodsPaid(firstPayDate) 从首期还款日与今天推算)。
 */
export function ledgerSnapshot(ledger, paidPeriods) {
  if (!ledger || !ledger.valid || !ledger.rows.length) {
    return {
      paidPeriods: 0,
      settled: false,
      balance: 0,
      remainingPeriods: 0,
      currentPayment: 0,
      currentRate: 0,
      paidPrincipal: 0,
      paidInterest: 0,
    }
  }
  const paid = Math.min(Math.max(0, Math.round(Number(paidPeriods) || 0)), ledger.rows.length)
  const paidRows = ledger.rows.slice(0, paid)
  const settled = Boolean(ledger.settled) && paid >= ledger.settledAt
  // 已还 0 期时的「剩余本金」就是贷款本金(第 1 期还没扣)
  const balance = paid === 0
    ? ledger.rows[0].principal + ledger.rows[0].balance
    : paidRows[paidRows.length - 1].balance
  const next = ledger.rows[paid]
  return {
    paidPeriods: paid,
    settled,
    balance,
    remainingPeriods: settled ? 0 : Math.max(0, ledger.rows.length - paid),
    currentPayment: settled ? 0 : (next ? next.payment : 0),
    currentRate: settled ? ledger.summary.currentRate : (next ? next.rate : ledger.summary.currentRate),
    paidPrincipal: sumBy(paidRows, 'principal'),
    paidInterest: sumBy(paidRows, 'interest'),
  }
}

const round2 = (v) => Math.round(v * 100) / 100

/**
 * 合并多笔贷款的流水为「合计视图」(纯函数):同期次逐项相加(月供/本金/利息/剩余本金),
 * 供贷款组(如商贷 + 公积金组合)看合计。全部结清才算组内结清,结清期次取最晚的一笔。
 * delta(利率浮动/提前还款省下)按笔可加,由调用方对各笔求和。
 */
export function mergeLedgers(ledgers) {
  const list = (ledgers || []).filter((l) => l && l.valid && Array.isArray(l.rows) && l.rows.length)
  if (!list.length) return null
  const len = Math.max(...list.map((l) => l.rows.length))
  const rows = []
  let totalPayment = 0
  let totalPrincipal = 0
  let totalInterest = 0
  for (let i = 0; i < len; i++) {
    let payment = 0
    let principal = 0
    let interest = 0
    let balance = 0
    for (const l of list) {
      const r = l.rows[i]
      if (!r) continue
      payment += r.payment
      principal += r.principal
      interest += r.interest
      balance += r.balance
    }
    payment = round2(payment)
    principal = round2(principal)
    interest = round2(interest)
    balance = round2(balance)
    totalPayment = round2(totalPayment + payment)
    totalPrincipal = round2(totalPrincipal + principal)
    totalInterest = round2(totalInterest + interest)
    rows.push({ period: i + 1, payment, principal, interest, balance, days: 0, events: [], rateChanged: false, prepaid: false })
  }
  const settled = list.every((l) => (l.settled != null ? l.settled : Boolean(l.summary && l.summary.closed)))
  const settledAt = settled ? Math.max(...list.map((l) => l.settledAt || (l.summary && l.summary.months) || 0)) : 0
  return {
    valid: true,
    rows,
    months: len,
    settled,
    settledAt,
    summary: {
      months: len,
      periods: len,
      settled,
      settledAt,
      firstPayment: rows[0].payment,
      lastPayment: rows[rows.length - 1].payment,
      totalPayment,
      totalPrincipal,
      totalInterest,
      interestRatio: totalPrincipal > 0 ? totalInterest / totalPrincipal : 0,
      prepaidTotal: round2(list.reduce((s, l) => s + ((l.summary && l.summary.prepaidTotal) || 0), 0)),
      currentRate: 0,
    },
    segments: [],
  }
}
