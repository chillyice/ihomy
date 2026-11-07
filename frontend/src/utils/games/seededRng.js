// 种子随机数(mulberry32):对战双端以同一 seed 生成同一随机序列,
// 保证同种子赛/实时对战的起始局面与食物序列一致。
export function seededRng(seed) {
  let a = Number(seed) >>> 0
  return function () {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
