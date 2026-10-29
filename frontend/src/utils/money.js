// 金额换算与展示:全站统一「元 ⇄ 分」口径,避免浮点累加误差。
// 贷款(utils/loan.js)与理财(utils/finance.js)、记账本(utils/bookLedger.js)共用同一份实现,
// 历史上的引用都从 utils/loan.js 再导出,行为保持不变。

/** 元 → 分(四舍五入到整数分);非法值按 0 */
export function toCents(yuan) {
  const n = Number(yuan)
  return Number.isFinite(n) ? Math.round(n * 100) : 0
}

/** 分 → 元 */
export function toYuan(cents) {
  return Math.round(cents) / 100
}

/** 金额格式化(千分位 + 指定小数位),仅用于展示 */
export function formatYuan(yuan, digits = 2) {
  const n = Number(yuan)
  return (Number.isFinite(n) ? n : 0).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
}

/** 金额四舍五入到 2 位(负数按绝对值进位,避免 Math.round 对负数「向大取整」);非法值按 0 */
export function round2(value) {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  const abs = Math.round(Math.abs(n) * 100) / 100
  return n < 0 ? -abs : abs
}
