// 显示模式的响应式状态(模块级单例):App 外壳、首页、导航、路由守卫共用同一份。
// 根元素只挂两个类:html.elder-mode(大字/高对比)与 html.child-mode(导航白名单)。
import { computed, ref } from 'vue'
import { isValidPin, makePinRecord, readDisplayMode, verifyPinRecord, writeDisplayMode, DISPLAY_MODES } from '@/utils/displayMode'

const initial = readDisplayMode()
const mode = ref(initial.mode)
const pin = ref(initial.pin)

function applyRootClass(next) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.classList.toggle('elder-mode', next === 'elder')
  root.classList.toggle('child-mode', next === 'child')
}

/** 启动时调一次:把本机保存的模式贴回 html(刷新后不闪回默认字号) */
export function initDisplayMode() {
  applyRootClass(mode.value)
}

/** 直接切模式(老人/默认);儿童模式请走 enableChildMode 以便设密码 */
export function setDisplayMode(next) {
  if (!DISPLAY_MODES.includes(next)) return
  mode.value = next
  writeDisplayMode({ mode: next, pin: pin.value })
  applyRootClass(next)
}

/** 开启儿童模式:必须带 4–8 位数字密码(首次设置即保存,之后退出校验用) */
export async function enableChildMode(pinCode) {
  if (!isValidPin(pinCode)) return false
  pin.value = await makePinRecord(pinCode)
  setDisplayMode('child')
  return true
}

/** 退出儿童模式:密码不对就留在儿童模式 */
export async function disableChildMode(pinCode) {
  const ok = await verifyPinRecord(pinCode, pin.value)
  if (ok) setDisplayMode('normal')
  return ok
}

/** 改儿童模式退出密码(已设过密码时必须先给旧密码) */
export async function changeChildPin(oldPin, newPin) {
  if (pin.value && !(await verifyPinRecord(oldPin, pin.value))) return false
  if (!isValidPin(newPin)) return false
  pin.value = await makePinRecord(newPin)
  writeDisplayMode({ mode: mode.value, pin: pin.value })
  return true
}

/** 非组件场景(路由守卫/导航构建)读当前模式快照 */
export function getDisplayMode() {
  return mode.value
}

export function useDisplayMode() {
  return {
    mode,
    isElder: computed(() => mode.value === 'elder'),
    isChild: computed(() => mode.value === 'child'),
    hasChildPin: computed(() => !!pin.value?.hash),
    setDisplayMode,
    enableChildMode,
    disableChildMode,
    changeChildPin,
  }
}
