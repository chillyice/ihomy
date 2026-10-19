// 网格拖拽落位:给定各卡片矩形(按视觉/DOM 顺序)与指针坐标,返回应插入的下标。
// 规则:优先取指针命中的卡片;落在间隙/边缘时取中心最近的一张;再按指针相对该卡水平中线的
// 左右决定插到其前(hit)或后(hit+1)。
// 不要用「逐张 y < 卡片中心就返回」——同一视觉行里卡片行高不一时,右侧矮卡上方区域会被误判成
// 排在左侧高卡之前,落位明显偏离鼠标。
export function pickInsertIndex(rects, x, y) {
  if (!rects.length) return 0
  let hit = 0
  let bestDist = Infinity
  for (let i = 0; i < rects.length; i++) {
    const r = rects[i]
    const dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0
    const dy = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0
    const d = dx * dx + dy * dy
    if (d < bestDist) {
      bestDist = d
      hit = i
      if (d === 0) break
    }
  }
  const r = rects[hit]
  return x < r.left + (r.right - r.left) / 2 ? hit : hit + 1
}
