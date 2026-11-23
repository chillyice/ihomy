// 近 N 个月收支行(记账首页「半年趋势」卡用):把后端 byMonth 补成连续月份,缺月记 0。
// byMonth: [{ month: '2026-05', income, expense }] —— 后端只下发有记录的月份,月份可能是 'yyyy-MM' 或 'yyyy-MM-dd'
export function recentMonthRows(byMonth, months = 6, now = new Date()) {
  const map = new Map()
  for (const row of byMonth || []) {
    const key = normalizeMonth(row?.month)
    if (!key) continue
    const cur = map.get(key) || { income: 0, expense: 0 }
    map.set(key, {
      income: cur.income + (Number(row.income) || 0),
      expense: cur.expense + (Number(row.expense) || 0),
    })
  }
  const count = Math.max(1, Number(months) || 6)
  const year = now.getFullYear()
  const month = now.getMonth() + 1
  const out = []
  for (let i = count - 1; i >= 0; i--) {
    // Date 的月末回卷会自己处理跨年(1 月往前推 5 个月 → 上一年 8 月)
    const d = new Date(year, month - 1 - i, 1)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
    const value = map.get(key) || { income: 0, expense: 0 }
    out.push({ key, label: String(d.getMonth() + 1), income: value.income, expense: value.expense })
  }
  return out
}

function normalizeMonth(value) {
  const raw = String(value || '')
  const m = raw.match(/^(\d{4})-(\d{1,2})/)
  if (!m) return ''
  return `${m[1]}-${String(Number(m[2])).padStart(2, '0')}`
}
