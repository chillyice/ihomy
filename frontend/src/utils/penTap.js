// 触控笔(iPadOS Apple Pencil)的点击合成不可靠:Safari 只保证 pointerdown/pointerup 送达,
// click 有时整组不补发。凡是「只吃 click」的交互(按钮点按、卡片选中、纸牌双击自动移动)在 iPad + 手写笔下会静默失效。
// 这里只放纯判定:一次手写笔按下能不能算「点」、浏览器补发的那次真 click 要不要吞、哪些区域不接管。
// 补发与拦截的动作在全站唯一一处:composables/usePenTap.js(App.vue 挂一次)。
export const PEN_MOVE_TOLERANCE = 10
// 超过这个时长算长按(长按菜单自己处理),不补发点击
export const PEN_LONG_PRESS_MS = 600
export const PEN_CLICK_SWALLOW_MS = 400
export const PEN_CLICK_RADIUS = 24
// 手写/绘图画布与输入区不接管:补发 click 会干扰笔迹、抢焦点;需要例外的地方自己标 data-pen-tap="off"
export const PEN_TAP_EXCLUDED_SELECTOR = [
  'canvas',
  'input',
  'textarea',
  'select',
  '[contenteditable=""]',
  '[contenteditable="true"]',
  '[contenteditable="plaintext-only"]',
  '[data-pen-tap="off"]',
].join(', ')

/**
 * 手写笔的 pointerup 是否可以当作一次点击补发。
 * 起拖(位移超阈值)、长按、以及其它指针类型都不接管。
 * @param {{ pointerType?: string, distance: number, duration: number, busy?: boolean, tolerance?: number, longPressMs?: number }} opts
 */
export function isPenTapCandidate({
  pointerType,
  distance,
  duration,
  busy = false,
  tolerance = PEN_MOVE_TOLERANCE,
  longPressMs = PEN_LONG_PRESS_MS,
}) {
  if (pointerType !== 'pen') return false
  if (busy) return false
  if (duration > longPressMs) return false
  return !(distance > tolerance)
}

/**
 * 浏览器自己补发的那次 click 要不要拦住。
 * 我们补发的点击带 synthetic 标记,放行;不接管的区域(canvas 手写板/输入框)放行,免得抢焦点;
 * 其余只按「离刚才落笔点很近且时间很短」兜——范围外的手指点击不会被误吞。
 * @param {{ pointerType?: string, synthetic?: boolean, excluded?: boolean, sinceTap: number, distanceFromTap: number, windowMs?: number, radius?: number }} opts
 */
export function shouldSwallowPenClick({
  synthetic,
  excluded = false,
  sinceTap,
  distanceFromTap,
  windowMs = PEN_CLICK_SWALLOW_MS,
  radius = PEN_CLICK_RADIUS,
}) {
  if (synthetic) return false
  if (excluded) return false
  if (!(sinceTap >= 0 && sinceTap < windowMs)) return false
  return Number.isFinite(distanceFromTap) && distanceFromTap <= radius
}

/** 落点是否在「不接管」的区域里(canvas 手写板 / 输入框 / 显式标了 data-pen-tap="off" 的容器) */
export function isPenTapExcluded(target) {
  if (!target || typeof target.closest !== 'function') return false
  if (target.closest(PEN_TAP_EXCLUDED_SELECTOR)) return true
  return target.isContentEditable === true
}
