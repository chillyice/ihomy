import { describe, it, expect } from 'vitest'
import {
  accountTypeLabel,
  sumBalances,
  unassignedCount,
  fillMonths,
  yoy,
  ratio,
  computeAccountBalances,
  ledgerCsvRows,
  ACCOUNT_TYPES,
} from './bookLedger'

// 伪造一个 vue-i18n 的 t:只认 book.account.type.<值>,其余按缺键原样返回(与 vue-i18n 行为一致)
const fakeT = (key) => {
  const map = {
    'book.account.type.CASH': '现金',
    'book.account.type.BANK': '银行卡',
    'book.account.type.CREDIT': '信用卡',
  }
  return map[key] ?? key
}

describe('accountTypeLabel', () => {
  it('已知类型取 i18n 文案', () => {
    expect(accountTypeLabel('BANK', fakeT)).toBe('银行卡')
    expect(accountTypeLabel('CASH', fakeT)).toBe('现金')
  })

  it('缺键时回落原始枚举,不返回空串', () => {
    expect(accountTypeLabel('ALIPAY', fakeT)).toBe('ALIPAY')
  })

  it('类型为空返回空串(不误显示「其他」)', () => {
    expect(accountTypeLabel('', fakeT)).toBe('')
    expect(accountTypeLabel(null, fakeT)).toBe('')
  })

  it('ACCOUNT_TYPES 覆盖字典 6 项', () => {
    expect(ACCOUNT_TYPES).toEqual(['CASH', 'BANK', 'ALIPAY', 'WECHAT', 'CREDIT', 'OTHER'])
  })
})

describe('sumBalances', () => {
  it('空数组为 0', () => {
    expect(sumBalances([])).toBe(0)
    expect(sumBalances(null)).toBe(0)
  })

  it('多账户余额求和(按分累加,不出现浮点尾差)', () => {
    expect(sumBalances([{ balance: 12340.5 }, { balance: 860.1 }, { balance: 0.2 }])).toBe(13200.8)
  })

  it('信用卡负余额照样计入合计', () => {
    expect(sumBalances([{ balance: 1000 }, { balance: -250.5 }])).toBe(749.5)
  })

  it('缺字段/非法金额按 0 处理', () => {
    expect(sumBalances([{ balance: null }, { name: '现金' }, { balance: 'abc' }, { balance: 12.34 }])).toBe(12.34)
  })
})

describe('unassignedCount', () => {
  it('空列表为 0', () => {
    expect(unassignedCount([])).toBe(0)
    expect(unassignedCount(undefined)).toBe(0)
  })

  it('只数三个账户列全空的记录', () => {
    const records = [
      { type: 'EXPENSE', accountId: 1 },
      { type: 'INCOME', accountId: null },
      { type: 'EXPENSE' },
      { type: 'TRANSFER', fromAccountId: 1, toAccountId: 2 },
    ]
    expect(unassignedCount(records)).toBe(2)
  })

  it('转账只填一侧也算已归属', () => {
    const records = [
      { type: 'TRANSFER', fromAccountId: 3, toAccountId: null },
      { type: 'TRANSFER', fromAccountId: null, toAccountId: 4 },
      { type: 'TRANSFER', fromAccountId: null, toAccountId: null },
    ]
    expect(unassignedCount(records)).toBe(1)
  })
})

describe('fillMonths', () => {
  // 后端口径:byMonth 只含有记录的月份,opening 是「该月之前累计结余」
  const byMonth = [
    { month: '2025-11', income: 500, expense: 0, balance: 500, count: 1, opening: -500, closing: 0 },
    { month: '2026-03', income: 1000, expense: 200, balance: 800, count: 3, opening: 0, closing: 800 },
    { month: '2026-07', income: 0, expense: 300, balance: -300, count: 2, opening: 800, closing: 500 },
  ]

  it('固定 12 槽且按月份升序,空月补零', () => {
    const rows = fillMonths(byMonth, 2026)
    expect(rows).toHaveLength(12)
    expect(rows.map((r) => r.month)).toEqual([
      '2026-01',
      '2026-02',
      '2026-03',
      '2026-04',
      '2026-05',
      '2026-06',
      '2026-07',
      '2026-08',
      '2026-09',
      '2026-10',
      '2026-11',
      '2026-12',
    ])
    expect(rows[0]).toMatchObject({ income: 0, expense: 0, balance: 0, count: 0, opening: 0, closing: 0 })
    expect(rows[1]).toMatchObject({ income: 0, expense: 0, opening: 0, closing: 0 })
  })

  it('只取指定年份,别年数据不串味', () => {
    const rows = fillMonths(byMonth, 2026)
    expect(rows[2]).toMatchObject({ income: 1000, expense: 200, balance: 800, count: 3 })
    expect(rows[10]).toMatchObject({ income: 0, expense: 0, count: 0 })
  })

  it('期初/期末逐槽滚动,年末期末 = 年初 + 全年收入 − 全年支出', () => {
    const rows = fillMonths(byMonth, 2026)
    // 年初期初承接 2025-11 的 closing(0)
    expect(rows[0].opening).toBe(0)
    expect(rows[2].opening).toBe(0)
    expect(rows[2].closing).toBe(800)
    // 3 月到 7 月之间没有记录,期初不动
    expect(rows[5]).toMatchObject({ opening: 800, closing: 800 })
    expect(rows[6]).toMatchObject({ opening: 800, income: 0, expense: 300, closing: 500 })
    // 之后一路滚到年末
    expect(rows[11]).toMatchObject({ opening: 500, closing: 500 })
    const income = rows.reduce((s, r) => s + r.income, 0)
    const expense = rows.reduce((s, r) => s + r.expense, 0)
    expect(rows[11].closing).toBeCloseTo(rows[0].opening + income - expense, 2)
  })

  it('每月 closing 与下月 opening 闭合(无断点)', () => {
    const rows = fillMonths(byMonth, 2026)
    for (let i = 1; i < rows.length; i++) {
      expect(rows[i].opening).toBeCloseTo(rows[i - 1].closing, 2)
    }
  })

  it('该年无记录时 12 槽全 0;非法年份返回空数组', () => {
    const rows = fillMonths(byMonth, 2024)
    expect(rows).toHaveLength(12)
    expect(rows.every((r) => r.income === 0 && r.expense === 0 && r.opening === 0 && r.closing === 0)).toBe(true)
    expect(fillMonths(byMonth, 'abc')).toEqual([])
    expect(fillMonths(null, 2026)).toHaveLength(12)
  })
})

describe('yoy', () => {
  it('正常同比返回小数(保留 4 位)', () => {
    expect(yoy(120, 100)).toBe(0.2)
    expect(yoy(80, 100)).toBe(-0.2)
    expect(yoy(100, 300)).toBe(-0.6667)
    expect(yoy(1000, 300)).toBe(2.3333)
  })

  it('上期为 0 → null,不出 Infinity/NaN', () => {
    expect(yoy(100, 0)).toBeNull()
    expect(yoy(0, 0)).toBeNull()
    // 不能直接拿结果做除法
    expect(Number.isFinite(yoy(100, 0))).toBe(false)
  })

  it('非法入参按 null 处理', () => {
    expect(yoy(null, undefined)).toBeNull()
    expect(yoy('abc', 100)).toBeNull()
  })
})

describe('ratio', () => {
  it('正常占比保留 4 位', () => {
    expect(ratio(1, 3)).toBe(0.3333)
    expect(ratio(2, 3)).toBe(0.6667)
    expect(ratio(32, 100)).toBe(0.32)
  })

  it('合计为 0 → 0,不除零', () => {
    expect(ratio(0, 0)).toBe(0)
    expect(ratio(100, 0)).toBe(0)
    expect(ratio(null, undefined)).toBe(0)
  })

  it('分类占比合计为 1', () => {
    const cats = [{ total: 320 }, { total: 500 }, { total: 180 }]
    const sum = cats.reduce((s, c) => s + c.total, 0)
    const total = cats.reduce((s, c) => s + ratio(c.total, sum), 0)
    expect(total).toBe(1)
  })
})

describe('computeAccountBalances', () => {
  const accounts = [
    { id: 1, name: '招行工资卡', accountType: 'BANK', initialBalance: 1000 },
    { id: 2, name: '支付宝', accountType: 'ALIPAY', initialBalance: 0 },
  ]

  it('balance = 期初 + 收入 − 支出 − 转出 + 转入', () => {
    const records = [
      { type: 'EXPENSE', amount: 100, accountId: 1 },
      { type: 'INCOME', amount: 50, accountId: 2 },
      { type: 'TRANSFER', amount: 200, fromAccountId: 1, toAccountId: 2 },
    ]
    const [bank, alipay] = computeAccountBalances(accounts, records)
    expect(bank).toMatchObject({ id: 1, balance: 700, income: 0, expense: 100, transferIn: 0, transferOut: 200 })
    expect(alipay).toMatchObject({ id: 2, balance: 250, income: 50, expense: 0, transferIn: 200, transferOut: 0 })
  })

  it('单边转账只动一侧,未归属记录不计入任何账户', () => {
    const records = [
      { type: 'TRANSFER', amount: 300, fromAccountId: 1, toAccountId: null },
      { type: 'TRANSFER', amount: 120, fromAccountId: null, toAccountId: 2 },
      { type: 'EXPENSE', amount: 66, accountId: null },
      { type: 'INCOME', amount: 99 },
    ]
    const [bank, alipay] = computeAccountBalances(accounts, records)
    expect(bank.balance).toBe(700)
    expect(alipay.balance).toBe(120)
    expect(bank.count).toBe(1)
    expect(alipay.count).toBe(1)
  })

  it('未知账户 id 的记录被忽略,返回顺序与入参一致', () => {
    const rows = computeAccountBalances(accounts, [
      { type: 'EXPENSE', amount: 10, accountId: 99 },
      { type: 'INCOME', amount: 20, accountId: 2 },
    ])
    expect(rows.map((r) => r.id)).toEqual([1, 2])
    expect(rows[0].balance).toBe(1000)
    expect(rows[1].balance).toBe(20)
  })

  it('空入参不炸:无账户 → 空数组,无记录 → 全部等于期初', () => {
    expect(computeAccountBalances(null, null)).toEqual([])
    expect(computeAccountBalances(accounts, []).map((r) => r.balance)).toEqual([1000, 0])
  })
})

describe('ledgerCsvRows', () => {
  const rows = [
    { label: '2026 年', income: 1000, expense: 200.5, balance: 799.5, count: 3, opening: 0, closing: 799.5 },
    { label: '2026-03', income: 0, expense: 0, balance: 0, count: 0, opening: 799.5, closing: 799.5 },
  ]

  it('首行是表头,列为期间/收入/支出/结余/笔数/期初/期末', () => {
    const out = ledgerCsvRows(rows, ['期间', '收入', '支出', '结余', '笔数', '期初', '期末'])
    expect(out[0]).toEqual(['期间', '收入', '支出', '结余', '笔数', '期初', '期末'])
    expect(out).toHaveLength(rows.length + 1)
  })

  it('金额固定 2 位小数,笔数为整数,中文原样保留', () => {
    const out = ledgerCsvRows(rows, ['期间'])
    expect(out[1]).toEqual(['2026 年', '1000.00', '200.50', '799.50', '3', '0.00', '799.50'])
    expect(out[2]).toEqual(['2026-03', '0.00', '0.00', '0.00', '0', '799.50', '799.50'])
  })

  it('不传表头时只出行数据;含逗号/引号的单元格按 CSV 规则转义', () => {
    expect(ledgerCsvRows(rows)).toHaveLength(rows.length)
    const out = ledgerCsvRows([{ label: '餐饮,"外卖"', income: 1 }], ['期间'])
    expect(out[1][0]).toBe('"餐饮,""外卖"""')
  })

  it('空数据:不传表头 → 空数组;传表头 → 只有标题行', () => {
    expect(ledgerCsvRows([], ['期间'])).toEqual([['期间']])
    expect(ledgerCsvRows(null)).toEqual([])
  })
})
