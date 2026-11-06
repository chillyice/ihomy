/**
 * 贷款还款自动记账(纯函数):按还款流水算出「已到期 + 该期有还款账户」的期次,
 * 交给后端 /loan/repay/sync 幂等补插记账支出。金额与还款日都取 utils/loan 的流水口径,
 * 本文件不另算月供 —— 贷款计算唯一来源仍是 utils/loan.js。
 *
 * 「已到期」= 首期还款日到今天的期次(periodsPaid,首期还款日当天即算第 1 期已还);
 * 「该期有还款账户」= 初始账户(第 1 期起)叠加 ACCOUNT_CHANGE 事件(从第 N 期起),
 * 该期无账户(未绑定/中途解绑)就不记账。
 *
 * 已记账的期次也会一起提交:后端据此把「换了还款账户之后」的既有账目迁到新账户
 * (金额/日期仍保持首次记账快照,重复提交不会重复记账)。
 */
import { loanAccountOfPeriod, loanEngineInput, loanLedger, periodDate, periodsPaid } from './loan'

/**
 * 待同步的期次(与 /loan/repay/sync 的 items 同构;含已记账期次,后端按账户变化纠正归属)。
 * @param {Array} loans 贷款列表(含 bookedPeriods:后端已记过的期次,仅展示用)
 * @param {Date} [today] 今天(跨天由调用方的 useDayTick 决定)
 * @returns {Array<{loanId:number,period:number,dueDate:string,amount:number,principal:number,interest:number,accountId:number}>}
 */
export function loanRepayItems(loans, today = new Date()) {
  const out = []
  for (const loan of Array.isArray(loans) ? loans : []) {
    if (!loan || !loan.id || !loan.firstPayDate) continue
    const paid = periodsPaid(loan.firstPayDate, today)
    if (paid < 1) continue
    const ledger = loanLedger(loanEngineInput(loan))
    if (!ledger.valid || !ledger.rows.length) continue
    const upto = Math.min(paid, ledger.rows.length)
    for (let period = 1; period <= upto; period++) {
      const accountId = loanAccountOfPeriod(loan, period)
      if (!accountId) continue
      const row = ledger.rows[period - 1]
      out.push({
        loanId: loan.id,
        period,
        dueDate: periodDate(loan.firstPayDate, period),
        amount: row.payment,
        principal: row.principal,
        interest: row.interest,
        accountId,
      })
    }
  }
  return out
}
