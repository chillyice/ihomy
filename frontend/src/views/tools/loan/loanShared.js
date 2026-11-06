export const toNum = (v) => (v == null ? null : Number(v))

/** 由列表行还原编辑载荷(事件整体替换的其余字段保持不变;accountId 必须带上,否则改事件会解绑账户) */
export const loanToPayload = (loan, events) => ({
  name: loan.name,
  channel: loan.channel,
  method: loan.method,
  amount: toNum(loan.amount),
  months: Number(loan.months),
  loanDate: loan.loanDate || null,
  firstPayDate: loan.firstPayDate || null,
  firstPayment: toNum(loan.firstPayment),
  rate: toNum(loan.rate),
  accountId: loan.accountId ?? null,
  groupName: loan.groupName || null,
  note: loan.note || null,
  events: events.map((e) => ({
    type: e.type,
    effectivePeriod: Number(e.effectivePeriod),
    triggerDate: e.triggerDate || null,
    rate: toNum(e.rate),
    amount: toNum(e.amount),
    strategy: e.strategy || null,
    accountId: e.accountId ?? null,
    note: e.note || null,
  })),
})
