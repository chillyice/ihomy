// 触控笔(iPadOS Apple Pencil)的点击合成不可靠:Safari 只保证 pointerdown/pointerup 送达,
// click 有时整组不补发,纸牌的点选与双击自动移动就会「看着没反应」。
// 这里给出两个纯判定:一次手写笔按下能不能算「点」,以及浏览器补发的那次真 click 要不要吞掉,
// 具体补发与吞掉的动作在 composables/usePenTap.js 里做。
export const PEN_MOVE_TOLERANCE = 10
export const PEN_CLICK_SWALLOW_MS = 400
export const PEN_CLICK_RADIUS = 24

/**
 * 手写笔的 pointerup 是否可以当作一次点击补发。
 * 起拖(拖牌/滚动页面)与其它指针类型都不接管。
 * @param {{ pointerType?: string, distance: number, busy: boolean, tolerance?: number }} opts
 */
export function isPenTapCandidate({ pointerType, distance, busy, tolerance = PEN_MOVE_TOLERANCE }) {
  if (pointerType !== 'pen') return false
  if (busy) return false
  return !(distance > tolerance)
}

/**
 * 浏览器自己补发的那次 click 要不要拦住。
 * 我们补发的点击带 synthetic 标记,放行;手写笔来源的一律拦掉(否则一次按下算两次点击);
 * 手指/鼠标照常放行;老 Safari 的 click 是 MouseEvent(没有 pointerType),只能按「离刚才落笔点很近且时间很短」兜。
 * @param {{ pointerType?: string, synthetic?: boolean, sinceTap: number, distanceFromTap: number, windowMs?: number, radius?: number }} opts
 */
export function shouldSwallowPenClick({
  pointerType,
  synthetic,
  sinceTap,
  distanceFromTap,
  windowMs = PEN_CLICK_SWALLOW_MS,
  radius = PEN_CLICK_RADIUS,
}) {
  if (synthetic) return false
  if (pointerType === 'pen') return true
  if (pointerType) return false
  if (!(sinceTap >= 0 && sinceTap < windowMs)) return false
  return Number.isFinite(distanceFromTap) && distanceFromTap <= radius
}
