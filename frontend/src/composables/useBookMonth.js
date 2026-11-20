import { ref } from 'vue'

/** 月份切换、保存和预算编辑都会刷新同一页，按各请求的最新选择更新。 */
export function useBookMonth(getMonth, canRead, api) {
  const loading = ref(false)
  const loadedMonth = ref(null)
  const records = ref([])
  const stats = ref({})
  const accounts = ref([])
  const budgetData = ref(null)
  let loadSequence = 0
  let budgetSequence = 0
  let accountSequence = 0

  async function load() {
    if (!canRead()) return
    const month = getMonth()
    const sequence = ++loadSequence
    const budgetRequest = ++budgetSequence
    const accountRequest = ++accountSequence
    loading.value = true
    try {
      const [data, acc, budget] = await Promise.all([api.list(month), api.accounts(), api.budget(month)])
      if (sequence !== loadSequence || month !== getMonth()) return
      records.value = data.records || []
      stats.value = data.stats || {}
      if (accountRequest === accountSequence) accounts.value = acc || []
      if (budgetRequest === budgetSequence) budgetData.value = budget || null
      loadedMonth.value = month
    } catch {
      // 保存账目后刷新失败时，旧金额也不能当作重新核对过的本月金额。
      if (sequence === loadSequence && month === getMonth()) loadedMonth.value = null
    } finally {
      if (sequence === loadSequence) loading.value = false
    }
  }

  async function loadBudget() {
    if (!canRead()) return
    const month = getMonth()
    const sequence = ++budgetSequence
    const data = await api.budget(month)
    if (sequence === budgetSequence && month === getMonth()) budgetData.value = data || null
  }

  async function loadAccounts() {
    if (!canRead()) return
    const sequence = ++accountSequence
    const data = await api.accounts()
    if (sequence === accountSequence) accounts.value = data || []
  }

  return { loading, loadedMonth, records, stats, accounts, budgetData, load, loadBudget, loadAccounts }
}
