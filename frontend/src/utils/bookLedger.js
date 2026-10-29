// 记账本共享计算:账户类型文案、家庭资金合计、未归属笔数、总账补零/同比/占比/余额复算/CSV 行。
// 金额一律先转「分」整数再累加(复用 loan.js 的 toCents/toYuan),避免浮点误差;
// 余额口径与后端一致:balance = 期初 + 收入 − 支出 − 转出 + 转入(由后端算好下发,前端只做求和;
// computeAccountBalances 按同一公式复算,专供对账与单测)。
import { toCents, toYuan } from './loan'

/** 账户类型(与 sys_dict_item 的 book_account_type 一一对应,顺序即下拉顺序) */
export const ACCOUNT_TYPES = ['CASH', 'BANK', 'ALIPAY', 'WECHAT', 'CREDIT', 'OTHER']

/** 账户类型文案(走 i18n book.account.type.*;缺键回落原始枚举,不显示空白) */
export function accountTypeLabel(type, t) {
  if (!type) return ''
  const key = `book.account.type.${type}`
  const msg = t(key)
  return msg === key ? type : msg
}

/** 家庭资金合计(元):各账户实时余额之和,信用卡负余额照样计入;空列表 / 非法金额按 0 */
export function sumBalances(accounts) {
  const totalCents = (accounts || []).reduce((sum, a) => sum + toCents(a?.balance), 0)
  return toYuan(totalCents)
}

/** 未归属笔数:三个账户列全空的记录(历史数据),转账只要填了一侧就算已归属 */
export function unassignedCount(records) {
  return (records || []).filter((r) => !r?.accountId && !r?.fromAccountId && !r?.toAccountId).length
}

/**
 * 12 槽补零:把某年 `byMonth`(后端只含有记录的月份)铺成 1~12 月,期初/期末逐槽滚动。
 * 年初期初取该年首条记录的 `opening`(后端已按全时段滚动算好,等于该年之前累计结余);
 * 该年没有记录时为 0。返回项按月份升序,金额单位元、保留 2 位。
 */
export function fillMonths(byMonth, year) {
  const y = Number(year)
  if (!Number.isFinite(y)) return []
  const rows = (byMonth || [])
    .filter((r) => String(r?.month || '').startsWith(`${y}-`))
    .sort((a, b) => String(a.month).localeCompare(String(b.month)))
  const byKey = new Map(rows.map((r) => [r.month, r]))
  // 后端已算好「该月之前累计结余」,直接作为滚动起点;缺记录说明该年无收支 → 0
  let rolling = rows.length ? toCents(rows[0].opening) : 0
  const out = []
  for (let m = 1; m <= 12; m++) {
    const month = `${y}-${String(m).padStart(2, '0')}`
    const row = byKey.get(month)
    const income = toCents(row?.income)
    const expense = toCents(row?.expense)
    const opening = rolling
    const closing = opening + income - expense
    out.push({
      month,
      income: toYuan(income),
      expense: toYuan(expense),
      balance: toYuan(income - expense),
      count: Number(row?.count) || 0,
      opening: toYuan(opening),
      closing: toYuan(closing),
    })
    rolling = closing
  }
  return out
}

/**
 * 同比:(本期 − 上期) / 上期,返回小数(0.125 = +12.5%),保留 4 位。
 * 上期为 0 或不可比 → null(不产生 Infinity/NaN,展示层显示占位符)。
 */
export function yoy(cur, prev) {
  const a = Number(cur)
  const b = Number(prev)
  if (!Number.isFinite(a) || !Number.isFinite(b) || b === 0) return null
  return round4((a - b) / b)
}

/** 占比:part / total,合计为 0 → 0;保留 4 位(与后端 BookLedgerUtil 的 HALF_UP 口径一致) */
export function ratio(part, total) {
  const p = Number(part)
  const t = Number(total)
  if (!Number.isFinite(p) || !Number.isFinite(t) || t === 0) return 0
  return round4(p / t)
}

/** 四舍五入保留 4 位(负数按绝对值进位,避免 Math.round 对负数「向大取整」) */
function round4(v) {
  const abs = Math.round(Math.abs(v) * 10000) / 10000
  return v < 0 ? -abs : abs
}

/**
 * 按「balance = 期初 + 收入 − 支出 − 转出 + 转入」复算每个账户(与后端 /book/accounts 对账用)。
 * 转账按两侧各记一次(与后端一致),未归属记录不计入任何账户;返回顺序与传入 accounts 一致。
 */
export function computeAccountBalances(accounts, records) {
  const map = new Map()
  for (const a of accounts || []) {
    if (a?.id == null) continue
    map.set(a.id, {
      id: a.id,
      name: a.name ?? '',
      accountType: a.accountType ?? '',
      initialCents: toCents(a.initialBalance),
      income: 0,
      expense: 0,
      transferIn: 0,
      transferOut: 0,
      count: 0,
    })
  }
  for (const r of records || []) {
    if (!r) continue
    const amount = toCents(r.amount)
    const type = String(r.type || '').toUpperCase()
    if (type === 'TRANSFER') {
      const from = r.fromAccountId == null ? null : map.get(r.fromAccountId)
      const to = r.toAccountId == null ? null : map.get(r.toAccountId)
      if (from) {
        from.transferOut += amount
        from.count++
      }
      if (to) {
        to.transferIn += amount
        to.count++
      }
      continue
    }
    const acc = r.accountId == null ? null : map.get(r.accountId)
    if (!acc) continue
    if (type === 'INCOME') acc.income += amount
    else acc.expense += amount
    acc.count++
  }
  return [...map.values()].map((a) => ({
    id: a.id,
    name: a.name,
    accountType: a.accountType,
    initialBalance: toYuan(a.initialCents),
    income: toYuan(a.income),
    expense: toYuan(a.expense),
    transferIn: toYuan(a.transferIn),
    transferOut: toYuan(a.transferOut),
    count: a.count,
    balance: toYuan(a.initialCents + a.income - a.expense - a.transferOut + a.transferIn),
  }))
}

/**
 * 总账 CSV 行(纯函数,不含 BOM/CRLF —— 由下载处加 `\ufeff` 前缀与 `\r\n` 连接)。
 * rows 由页面按总账表模型生成:{ label, income, expense, balance, count, opening, closing };
 * header 走 i18n 传入,默认不生成表头。含逗号/引号/换行的单元格按 RFC4180 转义。
 */
export function ledgerCsvRows(rows, header) {
  const cell = (v) => {
    const s = v == null ? '' : String(v)
    return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const money = (v) => (Number(v) || 0).toFixed(2)
  const out = Array.isArray(header) && header.length ? [header.map(cell)] : []
  for (const r of rows || []) {
    // 列序与总账表一致:期间/收入/支出/结余/笔数/期初/期末
    const line = [
      r?.label,
      money(r?.income),
      money(r?.expense),
      money(r?.balance),
      String(Number(r?.count) || 0),
      money(r?.opening),
      money(r?.closing),
    ]
    out.push(line.map(cell))
  }
  return out
}
