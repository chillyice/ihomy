// 网格拖拽落位:boxes 是当前渲染出来的卡片矩形(按视觉顺序),others 是「移除被拖拽项后」的序列。
// 规则:只认指针真正压在的那张卡片,按左右半区决定插到它前/后;压在源卡自身、卡片间隙或网格内时
// 保持当前下标(间隙里"最近卡片"只在还没有占位时兜底),指针越过网格上下边界则收口到首/尾。
// 不能按「逐张 y < 卡片中心就返回」推导——同行卡片高度不一时右侧卡上方会被误判到最前,落位会漂。
export function pickInsertIndex(boxes, others, x, y, current, sourceId, gridRect) {
  if (!boxes.length) return 0
  const inside = (r) => x >= r.left && x <= r.right && y >= r.top && y <= r.bottom
  const dist2 = (r) => {
    const dx = x < r.left ? r.left - x : x > r.right ? x - r.right : 0
    const dy = y < r.top ? r.top - y : y > r.bottom ? y - r.bottom : 0
    return dx * dx + dy * dy
  }
  const hit = boxes.find((b) => inside(b))
  if (hit && hit.id === sourceId) return current ?? others.length
  if (!hit) {
    if (gridRect && y > gridRect.bottom) return others.length
    if (gridRect && y < gridRect.top) return 0
    if (current != null) return current
  }
  const target = hit || boxes.reduce((a, b) => (dist2(b) < dist2(a) ? b : a))
  const idx = others.findIndex((e) => e.id === target.id)
  if (idx < 0) return current ?? others.length
  return x < target.left + (target.right - target.left) / 2 ? idx : idx + 1
}
