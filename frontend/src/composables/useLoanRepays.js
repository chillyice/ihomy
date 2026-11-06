/**
 * 贷款还款自动记账的提交侧(纯函数在 utils/loanRepay.js)。
 *
 * 绑定还款账户后,已到期期次会自动补记成该账户的支出;后端按 (贷款, 期次) 幂等,
 * 所以这里可以在每次打开记账本/贷款记录/首页资产卡时放心调用:没缺的期次不会重复记账。
 */
import { loanApi } from '@/api'
import { loanRepayItems } from '@/utils/loanRepay'

/** 单批期次上限:后端 @Size(max=600),这里留足余量,30 年房贷一笔也只需两批 */
const CHUNK = 200

/**
 * 提交待同步期次。
 * @returns {Promise<{created:number, moved:number}>} created=新生成的记账笔数,moved=换账户后迁到新账户的笔数
 */
export async function syncLoanRepays(loans, today = new Date()) {
  const items = loanRepayItems(loans, today)
  if (!items.length) return { created: 0, moved: 0 }
  let created = 0
  let moved = 0
  for (let i = 0; i < items.length; i += CHUNK) {
    const res = await loanApi.syncRepay(items.slice(i, i + CHUNK))
    created += Number(res?.created) || 0
    moved += Number(res?.moved) || 0
  }
  return { created, moved }
}

/** 自己取贷款列表再同步(记账本页/贷款记录页用),返回新生成/迁移的笔数 */
export async function syncLoanRepaysFromServer(today = new Date()) {
  const loans = await loanApi.list()
  return syncLoanRepays(Array.isArray(loans) ? loans : [], today)
}
