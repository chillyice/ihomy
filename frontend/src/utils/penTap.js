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

// 原生 click 语义:disabled 表单控件不产生点击;补发点击同样跳过(比如牌局还没轮到你时点手牌)
export const PEN_DISABLED_SELECTOR = [
  'button[disabled]',
  'input[disabled]',
  'select[disabled]',
  'textarea[disabled]',
  'fieldset[disabled]',
].join(', ')

export function isDisabledControl(target) {
  return !!(target && typeof target.closest === 'function' && target.closest(PEN_DISABLED_SELECTOR))
}

/**
 * 补发点击的目标元素,对齐原生 click 的落点语义:
 * 按下/抬起落在同一元素或互为祖先 → 该元素;落到不同元素(小幅漂移蹭到相邻元素)→ 最近公共祖先,
 * 不会误点隔壁的牌/按钮;按下时的元素已被移除(询问条已关、手牌重排)→ 以抬起处命中的元素为准。
 * 之所以不能按按下时的旧元素补发:按下→抬起之间 DOM 会重排(摸牌插入手牌、弹层出现/消失),
 * 旧元素可能已经挪走或被移除,补发过去就是「点这里、别处响应」。
 * @param {{ upTarget?: Node | null, downTarget?: Node | null, fallbackTarget?: Node | null }} opts
 */
export function resolvePenTapTarget({ upTarget, downTarget, fallbackTarget }) {
  const down = downTarget && downTarget.isConnected ? downTarget : null
  if (!upTarget && !down) return fallbackTarget || null
  if (!upTarget) return down
  if (!down) return upTarget
  if (upTarget === down) return upTarget
  if (down.contains(upTarget)) return down
  if (upTarget.contains(down)) return upTarget
  const ancestors = new Set()
  for (let node = upTarget; node; node = node.parentElement) ancestors.add(node)
  for (let node = down; node; node = node.parentElement) {
    if (ancestors.has(node)) return node
  }
  return fallbackTarget || null
}
