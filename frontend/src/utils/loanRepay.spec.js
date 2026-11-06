import { describe, it, expect } from 'vitest'
import { loanLedger, loanEngineInput, periodDate, LOAN_EVENT, REPAY_METHOD } from './loan'
import { loanRepayItems } from './loanRepay'

// 100 万 / 30 年 / 3.6%,首期还款日 2026-01-01;放款日 2025-12-01(首期按 31 天计息)
const baseLoan = {
  id: 1,
  name: '建行房贷',
  amount: 1000000,
  months: 360,
  method: REPAY_METHOD.EQUAL_INSTALLMENT,
  rate: 3.6,
  loanDate: '2025-12-01',
  firstPayDate: '2026-01-01',
  accountId: 7,
  bookedPeriods: [],
  events: [],
}

/** 某笔贷款第 n 期的流水行(金额口径的唯一来源) */
const rowOf = (loan, period) => loanLedger(loanEngineInput(loan)).rows[period - 1]

describe('loanRepayItems 待补记期次', () => {
  it('已到期且绑定了账户的期次全部带上,金额/日期取流水口径', () => {
    const items = loanRepayItems([baseLoan], new Date(2026, 2, 1))
    expect(items.map((x) => x.period)).toEqual([1, 2, 3])
    for (const it of items) {
      expect(it.loanId).toBe(1)
      expect(it.accountId).toBe(7)
      expect(it.dueDate).toBe(periodDate(baseLoan.firstPayDate, it.period))
      expect(it.amount).toBe(rowOf(baseLoan, it.period).payment)
      expect(it.principal).toBe(rowOf(baseLoan, it.period).principal)
      expect(it.interest).toBe(rowOf(baseLoan, it.period).interest)
    }
  })

  it('已记过的期次不再提交(重复同步不会重复记账)', () => {
    const items = loanRepayItems([{ ...baseLoan, bookedPeriods: [1, 2] }], new Date(2026, 2, 1))
    expect(items.map((x) => x.period)).toEqual([3])
  })

  it('首期还款日还没到 / 没填首期还款日 → 不产生待补记', () => {
    expect(loanRepayItems([baseLoan], new Date(2025, 11, 31))).toEqual([])
    expect(loanRepayItems([{ ...baseLoan, firstPayDate: null }], new Date(2026, 2, 1))).toEqual([])
  })

  it('未绑定账户不自动记账', () => {
    expect(loanRepayItems([{ ...baseLoan, accountId: null }], new Date(2026, 2, 1))).toEqual([])
  })

  it('换账户事件从第 N+1 期起改扣新账户;中途解绑后不再记账', () => {
    const loan = {
      ...baseLoan,
      events: [
        { type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 2, accountId: 9 },
        { type: LOAN_EVENT.ACCOUNT_CHANGE, effectivePeriod: 4, accountId: null },
      ],
    }
    const items = loanRepayItems([loan], new Date(2026, 5, 1))
    expect(items.map((x) => [x.period, x.accountId])).toEqual([
      [1, 7],
      [2, 7],
      [3, 9],
      [4, 9],
    ])
  })

  it('已结清的贷款最多补到最后一期,不补空期', () => {
    const short = { ...baseLoan, amount: 100000, months: 12 }
    const items = loanRepayItems([short], new Date(2027, 0, 1))
    expect(items).toHaveLength(12)
    expect(Math.max(...items.map((x) => x.period))).toBe(12)
  })

  it('无效贷款参数直接跳过,不影响其它贷款', () => {
    const items = loanRepayItems([{ ...baseLoan, id: 2, amount: 0 }, baseLoan], new Date(2026, 0, 1))
    expect(items.map((x) => x.loanId)).toEqual([1])
  })
})
