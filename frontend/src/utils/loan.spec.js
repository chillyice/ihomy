import { describe, it, expect } from 'vitest'
import {
  calculateLoan,
  prepaymentComparison,
  solveRate,
  loanLedger,
  periodsPaid,
  periodDate,
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
      events: [
        { type: LOAN_EVENT.PREPAY, effectivePeriod: 12, amount: 500000, strategy: PREPAY_STRATEGY.SHORTEN },
      ],
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
