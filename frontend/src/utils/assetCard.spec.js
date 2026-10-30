import { describe, expect, it } from 'vitest'

import { resolveAssetCard } from './assetCard'
import { netWorth } from './bookLedger'
import { summarizePortfolio } from './finance'
import { totalRemainingPrincipal } from './loan'

const accounts = [
  { id: 1, name: '招行', balance: '12000.00' },
  { id: 2, name: '微信', balance: '300.50' },
]
const products = [
  { id: 1, name: '定期', productType: 'DEPOSIT', principal: '20000.00', status: 'HOLDING', startDate: '2026-01-01' },
  { id: 2, name: '基金', productType: 'FUND', principal: '5000.00', status: 'REDEEMED' },
]
const loans = [
  {
    id: 1,
    name: '房贷',
    principal: '600000',
    annualRate: '4.2',
    termMonths: 240,
    firstPayDate: '2024-01-01',
  },
]

describe('resolveAssetCard', () => {
  it('sums fund + holding principal - loan remaining using shared calculators', () => {
    const card = resolveAssetCard({ accounts, products, loans, canViewLoan: true })
    const fund = 12300.5
    const invest = 20000
    const debt = Number(totalRemainingPrincipal(loans))

    expect(card.fund).toBeCloseTo(fund, 2)
    expect(card.invest).toBeCloseTo(invest, 2)
    expect(card.debt).toBeCloseTo(debt, 2)
    expect(card.net).toBeCloseTo(netWorth(fund, invest, debt), 2)
    expect(card.debtMissing).toBe(false)
    expect(card.holdingCount).toBe(summarizePortfolio(products).holdingCount)
  })

  it('treats loans as zero when the member cannot view loans and flags the gap', () => {
    const card = resolveAssetCard({ accounts, products, loans, canViewLoan: false })
    expect(card.debt).toBe(0)
    expect(card.debtMissing).toBe(true)
    expect(card.net).toBeCloseTo(netWorth(12300.5, 20000, 0), 2)
  })

  it('also flags missing loan data when the request failed', () => {
    const card = resolveAssetCard({ accounts, products, loans: null, canViewLoan: true })
    expect(card.debt).toBe(0)
    expect(card.debtMissing).toBe(true)
  })

  it('handles empty data without NaN', () => {
    const card = resolveAssetCard({ accounts: [], products: [], loans: [], canViewLoan: true })
    expect(card).toEqual({
      fund: 0,
      invest: 0,
      debt: 0,
      debtMissing: false,
      net: 0,
      accountCount: 0,
      holdingCount: 0,
    })
  })
})
