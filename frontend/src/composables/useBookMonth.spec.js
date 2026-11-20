import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useBookMonth } from './useBookMonth'

function fixture() {
  const month = ref('2026-10')
  const api = {
    list: vi.fn(async (m) => ({ records: [m], stats: { month: m } })),
    budget: vi.fn(async (m) => ({ month: m })),
    accounts: vi.fn(async () => ['account']),
  }
  return {
    month,
    api,
    page: useBookMonth(
      () => month.value,
      () => true,
      api,
    ),
  }
}

describe('monthly ledger refresh', () => {
  it('keeps the most recently selected month when the old list arrives last', async () => {
    const { api, page, month } = fixture()
    const slow = Promise.withResolvers()
    api.list.mockReturnValueOnce(slow.promise)
    const previous = page.load()
    month.value = '2026-11'
    await page.load()
    slow.resolve({ records: ['October'], stats: { month: 'October' } })
    await previous
    expect(page.loadedMonth.value).toBe(month.value)
    expect(page.records.value).toEqual([month.value])
    expect(page.stats.value.month).toBe(month.value)
    expect(page.budgetData.value.month).toBe(month.value)
  })

  it('keeps old results hidden when a third month fails and accepts a retry', async () => {
    const { page, api, month } = fixture()
    await page.load()
    const november = Promise.withResolvers()
    const december = Promise.withResolvers()
    api.list.mockReturnValueOnce(november.promise).mockReturnValueOnce(december.promise)
    month.value = '2026-11'
    const oldRequest = page.load()
    month.value = '2026-12'
    const currentRequest = page.load()
    november.resolve({ records: ['November'] })
    await oldRequest
    expect(page.loading.value).toBe(true)
    december.reject(new Error('offline'))
    await currentRequest
    expect(page.loading.value).toBe(false)
    expect(page.loadedMonth.value).not.toBe(month.value)
    await page.load()
    expect(page.loadedMonth.value).toBe(month.value)
    expect(page.records.value).toEqual([month.value])
  })

  it('requires a retry after a failed same-month refresh following a write', async () => {
    const { api, page, month } = fixture()
    await page.load()
    api.list.mockRejectedValueOnce(new Error('offline'))
    await page.load()
    expect(page.loadedMonth.value).toBeNull()
    expect(page.loading.value).toBe(false)
    api.list.mockResolvedValueOnce({ records: ['new record'] })
    await page.load()
    expect(page.loadedMonth.value).toBe(month.value)
    expect(page.records.value).toEqual(['new record'])
  })

  it('preserves new budget and account changes during a same-month refresh', async () => {
    const { page, api, month } = fixture()
    await page.load()
    const oldList = Promise.withResolvers()
    api.list.mockReturnValueOnce(oldList.promise)
    const refresh = page.load()
    api.budget.mockResolvedValueOnce({ month: month.value, total: 240 })
    api.accounts.mockResolvedValueOnce(['renamed'])
    await Promise.all([page.loadBudget(), page.loadAccounts()])
    oldList.resolve({ records: ['updated'] })
    await refresh
    expect(page.records.value).toEqual(['updated'])
    expect(page.budgetData.value.total).toBe(240)
    expect(page.accounts.value).toEqual(['renamed'])
  })

  it('ignores a budget-only result after navigating to another month', async () => {
    const { page, api, month } = fixture()
    await page.load()
    const slow = Promise.withResolvers()
    api.budget.mockReturnValueOnce(slow.promise)
    const refresh = page.loadBudget()
    month.value = '2026-11'
    await page.load()
    slow.resolve({ month: '2026-10' })
    await refresh
    expect(page.budgetData.value.month).toBe(month.value)
  })
})
