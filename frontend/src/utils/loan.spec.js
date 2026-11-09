import { describe, it, expect } from 'vitest'
import {
  calculateLoan,
  prepaymentComparison,
  solveRate,
  loanLedger,
  ledgerSnapshot,
  loanEngineInput,
  loanAccountOfPeriod,
  totalRemainingPrincipal,
  periodsPaid,
  periodDate,
  firstPeriodInterest,
  LOAN_TYPE,
  REPAY_METHOD,
  PREPAY_STRATEGY,
  LOAN_EVENT,
} from './loan'

const baseInstallment = {
  type: LOAN_TYPE.COMMERCIAL,
  method: REPAY_METHOD.EQUAL_INSTALLMENT,
  amount: 1000000,
  rate: 3.6,
  months: 360,
}

describe('calculateLoan 等额本息', () => {
  it('本金归还总额等于贷款额、末期结清、月供符合年金公式', () => {
    const r = calculateLoan(baseInstallment)
    expect(r.valid).toBe(true)
    expect(r.rows).toHaveLength(360)
    expect(r.summary.totalPrincipal).toBeCloseTo(1000000, 2)
    expect(r.summary.closed).toBe(true)
    // 100 万 / 3.6% / 30 年月供约 4546.45(末期按剩余本金结清,金额可略有出入)
    expect(r.summary.firstPayment).toBeCloseTo(4546.45, 1)
    expect(r.summary.lastPayment).toBeGreaterThan(0)
  })
})

describe('calculateLoan 等额本金', () => {
  it('首期月供 = 本金/期数 + 首期利息,月供逐期递减', () => {
    const r = calculateLoan({ ...baseInstallment, method: REPAY_METHOD.EQUAL_PRINCIPAL })
    expect(r.valid).toBe(true)
    expect(r.summary.totalPrincipal).toBeCloseTo(1000000, 2)
    expect(r.summary.firstPayment).toBeCloseTo(5777.78, 1)
    expect(r.rows[359].payment).toBeLessThan(r.rows[0].payment)
    expect(r.summary.monthlyDecrease).toBeGreaterThan(0)
  })
})

describe('solveRate 反推利率', () => {
  it('由月供反推年利率接近 3.6%', () => {
    const calc = calculateLoan(baseInstallment)
    const s = solveRate({ amount: 1000000, months: 360, payment: calc.summary.firstPayment })
    expect(s.valid).toBe(true)
    expect(Math.abs(s.annualRate - 3.6)).toBeLessThan(0.2)
  })

  it('还款额低于本金/期数时拒绝', () => {
    const s = solveRate({ amount: 1000000, months: 360, payment: 1000 })
    expect(s.valid).toBe(false)
    expect(s.error).toBe('PAYMENT_TOO_LOW')
  })
})

describe('prepaymentComparison 提前还款', () => {
  const input = { ...baseInstallment, paidPeriods: 36, prepayAmount: 100000 }

  it('缩短年限省利息且期数变少', () => {
    const pp = prepaymentComparison(input)
    expect(pp.valid).toBe(true)
    expect(pp.options[PREPAY_STRATEGY.SHORTEN].savedInterest).toBeGreaterThan(0)
    expect(pp.options[PREPAY_STRATEGY.SHORTEN].periods).toBeLessThan(pp.baseline.periods)
  })

  it('减少月供保持期限不变、月供下降', () => {
    const pp = prepaymentComparison(input)
    expect(pp.options[PREPAY_STRATEGY.REDUCE].periods).toBe(pp.baseline.periods)
    expect(pp.options[PREPAY_STRATEGY.REDUCE].monthlySaved).toBeGreaterThan(0)
  })
})

describe('loanLedger 贷款流水', () => {
  it('提前还款(缩短年限)使贷款提前结清并省息', () => {
    const led = loanLedger({
      amount: 1000000,
      months: 360,
      method: REPAY_METHOD.EQUAL_INSTALLMENT,
      rate: 3.6,
      events: [{ type: LOAN_EVENT.PREPAY, effectivePeriod: 12, amount: 500000, strategy: PREPAY_STRATEGY.SHORTEN }],
    })
    expect(led.valid).toBe(true)
    expect(led.settled).toBe(true)
    expect(led.summary.months).toBeLessThan(360)
    expect(led.delta.prepaySaved).toBeGreaterThan(0)
  })

  it('利率调整后该期起按新利率计息', () => {
    const led = loanLedger({
      amount: 1000000,
      months: 360,
      method: REPAY_METHOD.EQUAL_INSTALLMENT,
      rate: 3.6,
      events: [{ type: LOAN_EVENT.RATE_CHANGE, effectivePeriod: 12, rate: 4.2 }],
    })
    expect(led.valid).toBe(true)
    // 事件在第 12 期还款后生效 → 第 13 期(下标 12)起按新利率
    expect(led.rows[11].rate).toBe(3.6)
    expect(led.rows[12].rate).toBe(4.2)
  })

  it('换账户事件不参与计息:月供与不绑账户一致,且不会被判为非法事件', () => {
    const base = { amount: 1000000, months: 360, method: REPAY_METHOD.EQUAL_INSTALLMENT, rate: 3.6 }
    const plain = loanLedger(base)
    const withAccount = loanLedger({
      ...base,
      events: [
        { type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 5, accountId: 9 },
        { type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 18, accountId: null },
      ],
    })
    expect(withAccount.valid).toBe(true)
    expect(withAccount.rows.map((r) => r.payment)).toEqual(plain.rows.map((r) => r.payment))
    expect(withAccount.summary.totalInterest).toBe(plain.summary.totalInterest)
  })
})

describe('调息分段计息(按生产账单复核)', () => {
  // 兴业商贷真实数据:248 万 / 360 期 / 3.1%,放款 2025-02-17、首期还款 2025-03-05 实收 7600.23(16 天利息),
  // 2026-01-01 调息到 3.0%(第 10 期之后生效)
  const loan = {
    amount: 2480000,
    months: 360,
    method: REPAY_METHOD.EQUAL_INSTALLMENT,
    rate: 3.1,
    loanDate: '2025-02-17',
    firstPayDate: '2025-03-05',
    firstPayment: 7600.23,
    events: [{ type: LOAN_EVENT.RATE_CHANGE, effectivePeriod: 10, triggerDate: '2026-01-01', rate: 3.0 }],
  }
  const led = loanLedger(loanEngineInput(loan))

  it('调息当期(2026-01-05)按新旧利率分段计息:4 天新利率、26 天旧利率', () => {
    const row = led.rows[10]
    expect(row.period).toBe(11)
    expect(row.interest).toBeCloseTo(6270.25, 2)
    // 本金沿用调整前的月供计划(10590.01 − 6297.33),不因调息当期就变
    expect(row.principal).toBeCloseTo(4292.68, 2)
    expect(row.payment).toBeCloseTo(10562.93, 2)
  })

  it('第 12 期起才按新利率、剩余 349 期重算月供', () => {
    expect(led.rows[11].payment).toBeCloseTo(10459.16, 2)
    expect(led.rows[15].payment).toBeCloseTo(10459.16, 2)
    expect(led.rows[11].rate).toBe(3)
  })

  it('利率分段表把调息当期单列,常规月供仍取新月供', () => {
    const seg = led.segments.find((s) => s.fromPeriod === 11)
    expect(seg.payment).toBeCloseTo(10459.16, 2)
    expect(seg.transitionPayment).toBeCloseTo(10562.93, 2)
  })

  it('调息前的期次不受影响', () => {
    expect(led.rows[0].payment).toBeCloseTo(7600.23, 2)
    expect(led.rows[1].payment).toBeCloseTo(10590.01, 2)
    expect(led.rows[9].payment).toBeCloseTo(10590.01, 2)
  })
})

describe('提前还款过渡期按日计息', () => {
  // 90 万 / 3.0% / 360 期,放款 2025-12-05、首期还款日 2026-01-05(首期 31 天);
  // 第 13 期还款日 2027-01-05,2027-01-06(还款日后一天)结清 → 1 天过渡期利息
  const loan = {
    amount: 900000,
    months: 360,
    method: REPAY_METHOD.EQUAL_INSTALLMENT,
    rate: 3.0,
    loanDate: '2025-12-05',
    firstPayDate: '2026-01-05',
  }
  const base = loanLedger(loanEngineInput(loan))
  // 过渡期利息基数 = 第 13 期还款后的剩余本金
  const expectedDayInterest = Math.round((base.rows[12].balance * 100 * 3.0 * 1) / 36000) / 100

  const ledgerWith = (event) => loanLedger(loanEngineInput({ ...loan, events: [event] }))

  it('单日利息口径:90 万 × 3% ÷ 360 = 75 元', () => {
    expect(firstPeriodInterest(900000, 3.0, 1)).toBeCloseTo(75, 2)
  })

  it('还款日后一天结清:结清期多收 1 天过渡期利息,余额归零', () => {
    const led = ledgerWith({
      type: LOAN_EVENT.PREPAY,
      effectivePeriod: 13,
      triggerDate: '2027-01-06',
      amount: 900000,
      strategy: PREPAY_STRATEGY.REDUCE,
    })
    expect(led.valid).toBe(true)
    expect(led.settled).toBe(true)
    const row = led.rows[12]
    expect(row.period).toBe(13)
    expect(row.prepayDays).toBe(1)
    expect(row.prepayInterest).toBeCloseTo(expectedDayInterest, 2)
    // 过渡期利息并入该期利息与还款额(本金不变),结清时这就是除剩余本金外要多付的钱
    expect(row.interest).toBeCloseTo(base.rows[12].interest + expectedDayInterest, 2)
    expect(row.payment).toBeCloseTo(row.principal + row.interest, 2)
    expect(row.balance).toBe(0)
    expect(led.summary.prepayInterest).toBeCloseTo(expectedDayInterest, 2)
  })

  it('结清日就是还款日:不收过渡期利息', () => {
    const led = ledgerWith({
      type: LOAN_EVENT.PREPAY,
      effectivePeriod: 13,
      triggerDate: '2027-01-05',
      amount: 900000,
      strategy: PREPAY_STRATEGY.REDUCE,
    })
    expect(led.rows[12].prepayDays).toBe(0)
    expect(led.rows[12].prepayInterest).toBe(0)
    expect(led.rows[12].interest).toBeCloseTo(base.rows[12].interest, 2)
  })

  it('没填结清日期的老数据:不补息,口径与以前一致', () => {
    const led = ledgerWith({ type: LOAN_EVENT.PREPAY, effectivePeriod: 13, amount: 900000, strategy: PREPAY_STRATEGY.REDUCE })
    expect(led.rows[12].prepayInterest).toBe(0)
    expect(led.rows[12].interest).toBeCloseTo(base.rows[12].interest, 2)
  })

  it('部分提前还款同样按天补息,贷款继续按新月供还到结清', () => {
    const led = ledgerWith({
      type: LOAN_EVENT.PREPAY,
      effectivePeriod: 13,
      triggerDate: '2027-01-06',
      amount: 100000,
      strategy: PREPAY_STRATEGY.REDUCE,
    })
    expect(led.settledAt).toBe(360)
    expect(led.rows).toHaveLength(360)
    expect(led.rows[12].prepayDays).toBe(1)
    expect(led.rows[12].prepayInterest).toBeCloseTo(expectedDayInterest, 2)
    // 减少月供:第 14 期起月供比原来低
    expect(led.rows[13].payment).toBeLessThan(base.rows[13].payment)
  })
})

describe('日期工具', () => {
  it('periodsPaid 首期还款日当天即算已还 1 期', () => {
    expect(periodsPaid('2026-01-31', '2026-01-31')).toBe(1)
    expect(periodsPaid('2026-01-31', '2026-01-30')).toBe(0)
    expect(periodsPaid('2026-01-31', '2026-03-01')).toBe(2)
  })

  it('periodDate 超出当月天数时取月末', () => {
    expect(periodDate('2026-01-31', 2)).toBe('2026-02-28')
    expect(periodDate('2026-01-31', 13)).toBe('2027-01-31')
  })
})

describe('totalRemainingPrincipal 净资产口径', () => {
  const loan = {
    amount: 120000,
    months: 120,
    method: REPAY_METHOD.EQUAL_INSTALLMENT,
    rate: 3.6,
    loanDate: '2025-12-01',
    firstPayDate: '2026-01-01',
  }

  it('首期未到:剩余本金等于贷款本金', () => {
    expect(totalRemainingPrincipal([loan], new Date(2025, 11, 1))).toBe(120000)
  })

  it('已还 1 期:剩余本金 = 本金 − 首期归还本金', () => {
    const snap = ledgerSnapshot(loanLedger(loanEngineInput(loan)), 1)
    expect(totalRemainingPrincipal([loan], new Date(2026, 0, 1))).toBeCloseTo(snap.balance, 2)
    expect(totalRemainingPrincipal([loan], new Date(2026, 0, 1))).toBeLessThan(120000)
  })

  it('多笔贷款相加;空列表为 0', () => {
    const a = totalRemainingPrincipal([loan], new Date(2026, 0, 1))
    const b = totalRemainingPrincipal([{ ...loan, amount: 60000 }], new Date(2026, 0, 1))
    expect(totalRemainingPrincipal([loan, { ...loan, amount: 60000 }], new Date(2026, 0, 1))).toBeCloseTo(a + b, 2)
    expect(totalRemainingPrincipal([], new Date(2026, 0, 1))).toBe(0)
    expect(totalRemainingPrincipal(null, new Date(2026, 0, 1))).toBe(0)
  })

  it('loanEngineInput 透传事件与首期天数', () => {
    const input = loanEngineInput({
      ...loan,
      firstPayment: 1200,
      events: [{ type: LOAN_EVENT.RATE_CHANGE, effectivePeriod: 13, rate: 4.2, triggerDate: '2027-01-01' }],
    })
    expect(input.firstDays).toBe(31)
    expect(input.firstPayment).toBe(1200)
    expect(input.firstPayDate).toBe('2026-01-01')
    expect(input.events).toEqual([
      {
        type: LOAN_EVENT.RATE_CHANGE,
        effectivePeriod: 13,
        triggerDate: '2027-01-01',
        rate: 4.2,
        amount: null,
        strategy: undefined,
        accountId: null,
      },
    ])
  })
})

describe('loanAccountOfPeriod 还款账户时间轴', () => {
  const loan = {
    accountId: 7,
    events: [
      { type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 5, accountId: 9 },
      { type: LOAN_EVENT.RATE_CHANGE, effectivePeriod: 3, rate: 4.2 },
    ],
  }

  it('初始账户从第 1 期起生效,换账户事件「第 N 期后」生效', () => {
    expect(loanAccountOfPeriod(loan, 1)).toBe(7)
    expect(loanAccountOfPeriod(loan, 5)).toBe(7)
    // 第 5 期还款后换成 9 → 第 6 期起扣 9
    expect(loanAccountOfPeriod(loan, 6)).toBe(9)
    expect(loanAccountOfPeriod(loan, 360)).toBe(9)
  })

  it('未绑定账户 = 该期不自动记账;中途解绑留空后不再记账', () => {
    expect(loanAccountOfPeriod({ events: [] }, 1)).toBeNull()
    const unbound = {
      accountId: 7,
      events: [{ type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 10, accountId: null }],
    }
    expect(loanAccountOfPeriod(unbound, 10)).toBe(7)
    expect(loanAccountOfPeriod(unbound, 11)).toBeNull()
  })

  it('期次非法返回 null;事件顺序不影响结果', () => {
    expect(loanAccountOfPeriod(loan, 0)).toBeNull()
    const reversed = {
      accountId: 7,
      events: [
        { type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 20, accountId: 11 },
        { type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 8, accountId: 9 },
      ],
    }
    expect(loanAccountOfPeriod(reversed, 7)).toBe(7)
    expect(loanAccountOfPeriod(reversed, 9)).toBe(9)
    expect(loanAccountOfPeriod(reversed, 21)).toBe(11)
  })
})
