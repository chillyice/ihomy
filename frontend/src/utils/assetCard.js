// 首页「资产」卡口径:净资产 = 账户资金 + 理财持仓本金 − 贷款剩余本金。
// 账户求和与净资产公式走 utils/bookLedger(记账本唯一计算源),理财持仓本金走 utils/finance,
// 贷款剩余本金走 utils/loan —— 这里只做「取哪几项」的决定,禁止另写一套算法。
import { netWorth, sumBalances } from './bookLedger'
import { summarizePortfolio } from './finance'
import { totalRemainingPrincipal } from './loan'

/**
 * 组装资产卡数据(纯函数,便于单测):
 * - 无 loan:view 或贷款取数失败(loans 为 null)时,贷款项按 0 处理并标记 debtMissing,
 *   卡片必须据此注明「未含贷款」,不能把缺省当真实净资产展示;
 * - 理财持仓本金取「持有中 + 已到期未赎回」,与总账页净资产卡同口径。
 *
 * @param {object} p
 * @param {Array} p.accounts  /book/accounts 列表(带 balance)
 * @param {Array} p.products  /finance/list 列表
 * @param {Array|null} p.loans /loan/list 列表;null = 无权限或取数失败
 * @param {boolean} p.canViewLoan 是否具备 loan:view(OWNER 恒真)
 * @param {Date} [p.today] 计算「今日」用,默认当前时间
 */
export function resolveAssetCard({ accounts, products, loans, canViewLoan, today = new Date() } = {}) {
  const fund = sumBalances(accounts)
  const portfolio = summarizePortfolio(products, today)
  const invest = Number(portfolio.holdingPrincipal) || 0
  const debtMissing = !canViewLoan || loans == null
  const debt = debtMissing ? 0 : Number(totalRemainingPrincipal(loans, today)) || 0
  return {
    fund,
    invest,
    debt,
    debtMissing,
    net: netWorth(fund, invest, debt),
    accountCount: (accounts || []).length,
    holdingCount: Number(portfolio.holdingCount) || 0,
  }
}
