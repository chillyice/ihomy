import { describe, it, expect } from 'vitest'
import {
  dayCountOf,
  endDateOf,
  totalDays,
  heldDays,
  progressPct,
  remainingDays,
  expectedInterest,
  accruedInterest,
  maturityAmount,
  derivedStatus,
  actualInterest,
  actualRate,
  actualAnnualized,
  weightedRate,
  summarizePortfolio,
  upcomingMaturities,
  financeTypeLabel,
  interestModeLabel,
} from './finance'

/** 验收基准:10 万 / 2.9% / 3 年(2024-03-12 → 2027-03-12) */
const base = (over = {}) => ({
  id: 1,
  name: '招行3年期大额存单',
  productType: 'LARGE_DEPOSIT',
  principal: 100000,
  annualRate: 2.9,
  interestMode: 'BY_MONTH',
  dayCount: 365,
  termMonths: 36,
  startDate: '2024-03-12',
  endDate: '2027-03-12',
  status: 'HOLDING',
  ...over,
})

describe('到期日与计息天数', () => {
  it('显式到期日优先,其次按起息日 + 期限补算,都没有则为活期', () => {
    expect(endDateOf(base())).toBe('2027-03-12')
    expect(endDateOf(base({ endDate: null }))).toBe('2027-03-12')
    expect(endDateOf(base({ endDate: null, termMonths: null }))).toBeNull()
    expect(endDateOf(base({ endDate: null, termMonths: 0 }))).toBeNull()
  })

  it('月末补算收拢到当月最后一天(与后端 LocalDate.plusMonths 一致)', () => {
    expect(endDateOf({ startDate: '2024-01-31', termMonths: 1 })).toBe('2024-02-29')
    expect(endDateOf({ startDate: '2025-01-31', termMonths: 1 })).toBe('2025-02-28')
  })

  it('计息总天数算头不算尾;当日到期为 0;活期为 null', () => {
    expect(totalDays(base())).toBe(1095)
    expect(totalDays(base({ endDate: '2024-03-13' }))).toBe(1)
    expect(totalDays(base({ startDate: '2027-03-12', endDate: '2027-03-12', termMonths: 0 }))).toBe(0)
    expect(totalDays(base({ endDate: null, termMonths: null }))).toBeNull()
  })

  it('已持有天数:未起息 0、持有中按实际天数、到期后钉在总天数', () => {
    expect(heldDays(base(), '2024-01-01')).toBe(0)
    expect(heldDays(base(), '2025-03-12')).toBe(365)
    expect(heldDays(base(), '2026-03-12')).toBe(730)
    expect(heldDays(base(), '2030-01-01')).toBe(1095)
  })

  it('活期按起息日到今天算持有天数', () => {
    expect(heldDays({ startDate: '2026-01-01' }, '2026-01-31')).toBe(30)
    expect(heldDays({ startDate: '2026-06-01' }, '2026-01-31')).toBe(0)
  })

  it('进度:持有中按比例、当日到期 100%、到期后仍 100%、活期 0', () => {
    expect(progressPct(base(), '2025-03-12')).toBeCloseTo(33.33, 2)
    expect(progressPct(base(), '2026-03-12')).toBeCloseTo(66.67, 2)
    expect(progressPct(base(), '2027-03-12')).toBe(100)
    expect(progressPct(base(), '2030-01-01')).toBe(100)
    expect(progressPct(base({ endDate: '2027-03-12', startDate: '2027-03-12', termMonths: 0 }), '2027-03-12')).toBe(100)
    expect(progressPct(base({ endDate: null, termMonths: null }), '2026-01-01')).toBe(0)
  })

  it('剩余天数:未来为正、已过期为负、活期为 null', () => {
    expect(remainingDays(base(), '2026-03-12')).toBe(365)
    expect(remainingDays(base(), '2027-03-12')).toBe(0)
    expect(remainingDays(base(), '2027-04-11')).toBe(-30)
    expect(remainingDays(base({ endDate: null, termMonths: null }), '2026-03-12')).toBeNull()
  })

  it('基准天数只认 360,其余按 365', () => {
    expect(dayCountOf({ dayCount: 360 })).toBe(360)
    expect(dayCountOf({ dayCount: 365 })).toBe(365)
    expect(dayCountOf({ dayCount: 30 })).toBe(365)
    expect(dayCountOf({})).toBe(365)
  })
})

describe('预期利息与到期本息', () => {
  it('10 万 / 2.9% / 3 年按月折算 → 利息 8,700、本息 108,700', () => {
    expect(expectedInterest(base())).toBe(8700)
    expect(maturityAmount(base())).toBe(108700)
  })

  it('没填期限时按起止日倒推整月,结果同样是 8,700', () => {
    expect(expectedInterest(base({ termMonths: null }))).toBe(8700)
  })

  it('按日折算:365 天基准 8,700,360 天基准 8,820.83(同日数下 360 更高)', () => {
    const byDay = base({ interestMode: 'BY_DAY' })
    expect(expectedInterest(byDay)).toBe(8700)
    expect(expectedInterest({ ...byDay, dayCount: 360 })).toBe(8820.83)
  })

  it('复利高于单利:3 年 2.9% 复利约 8,954.74 > 单利 8,700', () => {
    const compound = expectedInterest(base({ interestMode: 'COMPOUND' }))
    expect(compound).toBeCloseTo(8954.74, 2)
    expect(compound).toBeGreaterThan(8700)
  })

  it('年化为空(基金)时预期与到期本息都是 null,不产生 NaN', () => {
    const fund = base({ productType: 'FUND', annualRate: null })
    expect(expectedInterest(fund)).toBeNull()
    expect(maturityAmount(fund)).toBeNull()
    expect(Number.isNaN(expectedInterest(fund))).toBe(false)
  })

  it('年化 0 是有效值,利息为 0 而不是 null', () => {
    expect(expectedInterest(base({ annualRate: 0 }))).toBe(0)
    expect(maturityAmount(base({ annualRate: 0 }))).toBe(100000)
  })

  it('活期按日折算没有到期日,预期利息为 null(没有「到期」可言)', () => {
    expect(expectedInterest(base({ interestMode: 'BY_DAY', endDate: null, termMonths: null }))).toBeNull()
  })
})

describe('已计提利息', () => {
  it('按月折算按已持有天数占比:730/1095 天 → 5,800', () => {
    expect(accruedInterest(base(), '2026-03-12')).toBe(5800)
  })

  it('起息日当天 0,到期日等于应得利息', () => {
    expect(accruedInterest(base(), '2024-03-12')).toBe(0)
    expect(accruedInterest(base(), '2027-03-12')).toBe(8700)
  })

  it('按月折算当日到期(总天数 0)为 null,不带出 0/0', () => {
    expect(accruedInterest(base({ startDate: '2027-03-12', endDate: '2027-03-12', termMonths: 0 }), '2027-03-12')).toBeNull()
  })

  it('按日折算按已持有天数计,且不超过应得利息', () => {
    const byDay = base({ interestMode: 'BY_DAY' })
    expect(accruedInterest(byDay, '2025-03-12')).toBe(2900)
    expect(accruedInterest(byDay, '2030-01-01')).toBe(8700)
  })

  it('复利按已持有天数指数计算,与应得利息同一公式', () => {
    const compound = base({ interestMode: 'COMPOUND' })
    expect(accruedInterest(compound, '2027-03-12')).toBeCloseTo(expectedInterest(compound), 2)
    expect(accruedInterest(compound, '2024-03-12')).toBe(0)
  })

  it('基金(无年化)已计提为 null,不出现 Infinity', () => {
    const fund = base({ productType: 'FUND', annualRate: null })
    expect(accruedInterest(fund, '2025-03-12')).toBeNull()
    expect(Number.isFinite(accruedInterest(fund, '2025-03-12'))).toBe(false)
  })
})

describe('派生状态', () => {
  it('持有中且到期日已过 → 已到期(待赎回)', () => {
    expect(derivedStatus(base(), '2027-03-12')).toBe('HOLDING')
    expect(derivedStatus(base(), '2027-03-13')).toBe('MATURED')
  })

  it('已赎回/已作废原样返回,不受到期日影响', () => {
    expect(derivedStatus(base({ status: 'REDEEMED' }), '2030-01-01')).toBe('REDEEMED')
    expect(derivedStatus(base({ status: 'CANCELLED' }), '2030-01-01')).toBe('CANCELLED')
  })

  it('库里写了已到期但到期日还没到,以日期为准回到持有中', () => {
    expect(derivedStatus(base({ status: 'MATURED' }), '2025-01-01')).toBe('HOLDING')
  })
})

describe('实际收益与实际年化', () => {
  const redeemed = base({
    status: 'REDEEMED',
    startDate: '2026-01-01',
    endDate: '2027-01-01',
    termMonths: 12,
    redeemDate: '2027-01-01',
    redeemAmount: 102900,
  })

  it('赎回 102,900 → 实际收益 2,900、收益率 2.9%', () => {
    expect(actualInterest(redeemed)).toBe(2900)
    expect(actualRate(redeemed)).toBeCloseTo(0.029, 6)
  })

  it('落库的实际收益优先于「到账 − 本金」', () => {
    expect(actualInterest({ ...redeemed, actualInterest: 2800 })).toBe(2800)
  })

  it('亏损可为负,收益率同步为负', () => {
    const loss = { ...redeemed, redeemAmount: 98000, actualInterest: -2000 }
    expect(actualInterest(loss)).toBe(-2000)
    expect(actualRate(loss)).toBeCloseTo(-0.02, 6)
  })

  it('实际年化按真实持有天数折年:一年整 2.9% → 2.9%', () => {
    expect(actualAnnualized(redeemed)).toBeCloseTo(2.9, 2)
  })

  it('持有一半时间拿到同样收益,年化翻倍', () => {
    const half = { ...redeemed, redeemDate: '2026-07-02', actualInterest: 2900 }
    const annual = actualAnnualized(half)
    expect(annual).toBeGreaterThan(5.7)
    expect(annual).toBeLessThan(6)
  })

  it('持有 0 天或没填到账金额 → null,不产生 Infinity/NaN', () => {
    expect(actualAnnualized({ ...redeemed, redeemDate: '2026-01-01', actualInterest: 100 })).toBeNull()
    expect(actualAnnualized({ ...redeemed, redeemAmount: null, actualInterest: null })).toBeNull()
    expect(actualInterest({ principal: 100000 })).toBeNull()
  })
})

describe('加权年化与持仓概览', () => {
  const two = [
    base({ id: 1, principal: 100000, annualRate: 2.9 }),
    base({ id: 2, principal: 50000, annualRate: 3.2, termMonths: 6, endDate: '2024-09-12' }),
  ]

  it('加权年化 = Σ(本金 × 年化) / Σ 本金', () => {
    expect(weightedRate(two)).toBeCloseTo(3, 4)
  })

  it('已赎回与浮动收益产品不参与加权年化', () => {
    const mixed = [...two, base({ id: 3, status: 'REDEEMED', annualRate: 9 }), base({ id: 4, productType: 'FUND', annualRate: null })]
    expect(weightedRate(mixed)).toBeCloseTo(3, 4)
    expect(weightedRate([base({ productType: 'FUND', annualRate: null })])).toBeNull()
    expect(weightedRate([])).toBeNull()
  })

  it('概览:持仓本金 / 已计提 / 预期 / 已实现 / 30 天内到期 / 类型占比', () => {
    const list = [
      base({ id: 1, principal: 100000, annualRate: 2.9 }),
      base({ id: 2, principal: 50000, annualRate: 3.2, termMonths: 6, endDate: '2024-10-12', productType: 'BANK_WMP' }),
      base({
        id: 3,
        status: 'REDEEMED',
        startDate: '2023-01-01',
        endDate: '2024-01-01',
        termMonths: 12,
        redeemDate: '2024-01-01',
        redeemAmount: 102900,
      }),
    ]
    const s = summarizePortfolio(list, '2024-09-20')
    expect(s.holdingCount).toBe(2)
    expect(s.holdingPrincipal).toBe(150000)
    expect(s.realized).toBe(2900)
    expect(s.expected).toBeGreaterThan(8700)
    expect(s.accrued).toBeGreaterThan(0)
    expect(s.accrued).toBeLessThan(s.expected)
    // 第二笔 2024-10-12 到期,距 2024-09-20 还有 22 天
    expect(s.dueSoonCount).toBe(1)
    expect(s.byType).toHaveLength(2)
    expect(s.byType[0].ratio).toBeCloseTo(0.6667, 3)
    expect(s.byType.reduce((sum, x) => sum + x.ratio, 0)).toBeCloseTo(1, 4)
  })

  it('概览遇到基金(无年化)不产生 NaN/Infinity', () => {
    const s = summarizePortfolio([base({ productType: 'FUND', annualRate: null, termMonths: null, endDate: null })], '2026-01-01')
    expect(s.holdingPrincipal).toBe(100000)
    expect(s.expected).toBe(0)
    expect(s.accrued).toBe(0)
    expect(s.weightedRate).toBeNull()
    expect(Number.isFinite(s.expected)).toBe(true)
    expect(s.byType[0].ratio).toBe(1)
  })

  it('空列表各项归零,不抛错', () => {
    const s = summarizePortfolio([], '2026-01-01')
    expect(s).toMatchObject({ holdingPrincipal: 0, holdingCount: 0, accrued: 0, expected: 0, realized: 0, dueSoonCount: 0 })
    expect(s.weightedRate).toBeNull()
    expect(s.byType).toEqual([])
    expect(summarizePortfolio(null).holdingPrincipal).toBe(0)
  })
})

describe('到期日历', () => {
  it('只列未来 90 天内到期的持仓,按到期日升序', () => {
    const list = [
      base({ id: 1, endDate: '2026-03-01', termMonths: null }),
      base({ id: 2, endDate: '2026-01-20', termMonths: null }),
      base({ id: 3, endDate: '2027-01-01', termMonths: null }),
      base({ id: 4, endDate: '2025-12-01', termMonths: null }),
      base({ id: 5, status: 'REDEEMED', endDate: '2026-01-10', termMonths: null }),
    ]
    const rows = upcomingMaturities(list, '2026-01-01', 90)
    expect(rows.map((r) => r.id)).toEqual([2, 1])
    expect(rows[0].daysLeft).toBe(19)
    expect(rows[1].productType).toBe('LARGE_DEPOSIT')
  })

  it('活期产品没有到期日,不进日历', () => {
    expect(upcomingMaturities([base({ endDate: null, termMonths: null })], '2026-01-01')).toEqual([])
  })
})

describe('字典文案', () => {
  it('缺键时回落枚举本身,不显示空白', () => {
    const t = (key) => key
    expect(financeTypeLabel('FUND', t)).toBe('FUND')
    expect(interestModeLabel('BY_DAY', t)).toBe('BY_DAY')
    expect(financeTypeLabel('', t)).toBe('')
    const dict = { 'book.finance.type.FUND': '基金', 'book.finance.mode.BY_DAY': '按日折算' }
    const t2 = (key) => dict[key] ?? key
    expect(financeTypeLabel('FUND', t2)).toBe('基金')
    expect(interestModeLabel('BY_DAY', t2)).toBe('按日折算')
  })
})
