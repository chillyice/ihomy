// 悬浮面板/浮球的视口夹紧:唯一纯函数来源。
// 只在「屏幕变小」时收缩(宽度/高度不会因为视口变大而反向放大),保证窗口缩放、旋屏、
// 地址栏/工具栏收起后小窗仍完整落在视口内。坐标语义与 useDragResize 一致:
//   anchorRight=false → x 是离左边缘距离;true → x 是离右边缘距离。
// 尺寸只缩不放:拖拽/展开时用户自己定的大小不会被这里改大。
export function clampFloatRect(rect, viewport, options = {}) {
  const minY = options.minY ?? 0
  const marginLeft = options.marginLeft ?? 0
  const anchorRight = !!options.anchorRight
  const minW = Math.max(1, Number(options.minW) || 1)
  const minH = Math.max(1, Number(options.minH) || 1)
  const vw = Number(viewport?.width) || 0
  const vh = Number(viewport?.height) || 0

  // 边距只在「放下最小可用尺寸」时才生效:窗口窄到连最小尺寸都挤不下时,可见性优先
  const leftBound = vw - marginLeft < minW ? Math.max(0, vw - minW) : marginLeft
  const topBound = vh - minY < minH ? Math.max(0, vh - minH) : minY
  const maxW = Math.max(1, vw - leftBound)
  const maxH = Math.max(1, vh - topBound)
  const w = Math.min(Math.max(Number(rect?.w) || 0, minW), maxW)
  const h = Math.min(Math.max(Number(rect?.h) || 0, minH), maxH)
  const x = anchorRight
    ? Math.min(Math.max(0, Number(rect?.x) || 0), Math.max(0, vw - leftBound - w))
    : Math.min(Math.max(leftBound, Number(rect?.x) || 0), Math.max(leftBound, vw - w))
  const y = Math.min(Math.max(topBound, Number(rect?.y) || 0), Math.max(topBound, vh - h))

  return { x, y, w, h }
}
