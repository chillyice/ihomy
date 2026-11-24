function distanceToBox(box, x, y) {
  const dx = Math.max(box.left - x, 0, x - box.right)
  const dy = Math.max(box.top - y, 0, y - box.bottom)
  return dx * dx + dy * dy
}

// boxes 使用布局坐标(忽略让位动效的 transform)。others 是移除源卡后的完整布局,含当前隐藏项。
export function pickInsertIndex(boxes, others, x, y, current, sourceId, gridRect) {
  if (!boxes.length || y < gridRect?.top) return 0
  if (y > gridRect?.bottom) return others.length

  // 空隙也能投放。按矩形边缘而非中心选最近的卡片,避免大卡片在靠边时误选邻居。
  let target = null
  let minDistance = Infinity
  for (const box of boxes) {
    const distance = distanceToBox(box, x, y)
    if (distance < minDistance || (distance === minDistance && box.id === sourceId)) {
      minDistance = distance
      target = box
    }
  }
  if (target.id === sourceId) return current ?? others.length
  const index = others.findIndex((entry) => entry.id === target.id)
  if (index < 0) return current ?? others.length

  // 上下行之间取上下边,同行取左右边。独占整行的卡片(含手机单列)始终按高度判断。
  const spansRow = gridRect && target.left <= gridRect.left + 1 && target.right >= gridRect.right - 1
  const vertical = spansRow || y < target.top || y > target.bottom
  const offset = vertical ? y - (target.top + target.bottom) / 2 : x - (target.left + target.right) / 2
  // 保留中线两侧的几像素,触控及鼠标轻微颤动不会反复触发让位。
  if (Math.abs(offset) <= 4 && (current === index || current === index + 1)) return current
  return offset < 0 ? index : index + 1
}
