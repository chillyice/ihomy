// 手柄适配(纯前端):蓝牙/USB 手柄 → 各小游戏已有的键盘控制。
//
// 约束(AGENTS 前端规范 15/17):
// - 轮询在 rAF 里做,只派发 KeyboardEvent,**不往响应式 ref 里写逐帧状态**;
// - gamepadConnected 只在「第一次真的收到手柄输入」时翻一次,给顶栏提示用;
// - 输入框/弹窗/后台标签页不派发,避免打字时误触;
// - 模拟器(EmulatorJS)自己吃手柄,那里不接本适配器。
import { onBeforeUnmount, onMounted, provide, ref } from 'vue'

/** 注入键:页面用 useGameView 时 provide,顶栏组件 inject */
export const GAMEPAD_KEY = Symbol('ihomy-gamepad')

/** 手柄按键索引 → 键盘键(标准布局:0=A 1=B 2=X 3=Y 8=Select 9=Start 12-15=十字键) */
export const GAMEPAD_KEYMAP = [
  { index: 0, key: 'Enter', code: 'Enter', repeat: false },
  { index: 1, key: 'Escape', code: 'Escape', repeat: false },
  { index: 2, key: ' ', code: 'Space', repeat: false },
  { index: 3, key: 'r', code: 'KeyR', repeat: false },
  { index: 8, key: 'Escape', code: 'Escape', repeat: false },
  { index: 9, key: 'Enter', code: 'Enter', repeat: false },
  { index: 12, key: 'ArrowUp', code: 'ArrowUp', repeat: true },
  { index: 13, key: 'ArrowDown', code: 'ArrowDown', repeat: true },
  { index: 14, key: 'ArrowLeft', code: 'ArrowLeft', repeat: true },
  { index: 15, key: 'ArrowRight', code: 'ArrowRight', repeat: true },
]

const KEY_BY_NAME = {}
GAMEPAD_KEYMAP.forEach((m) => {
  KEY_BY_NAME[m.key] = m
})
const BY_INDEX = {}
GAMEPAD_KEYMAP.forEach((m) => {
  BY_INDEX[m.index] = m
})

/** 左摇杆死区:低于这个值不当方向输入 */
export const DEADZONE = 0.4
/** 方向键长按连发间隔(按钮类动作用边沿触发,不连发) */
export const REPEAT_MS = 120

function addKey(out, seen, mapping) {
  if (!mapping || seen[mapping.key]) return
  seen[mapping.key] = true
  out.push(mapping)
}

/** 读一帧手柄状态 → 当前生效的键(方向键可连发,ABXY 只认边沿) */
export function readGamepadKeys(pad) {
  const out = []
  const seen = {}
  const buttons = (pad && pad.buttons) || []
  for (const m of GAMEPAD_KEYMAP) {
    const b = buttons[m.index]
    if (!b) continue
    if (b.pressed || Number(b.value) > 0.5) addKey(out, seen, m)
  }
  const axes = (pad && pad.axes) || []
  const ax = Number(axes[0]) || 0
  const ay = Number(axes[1]) || 0
  if (ax <= -DEADZONE) addKey(out, seen, KEY_BY_NAME.ArrowLeft)
  else if (ax >= DEADZONE) addKey(out, seen, KEY_BY_NAME.ArrowRight)
  if (ay <= -DEADZONE) addKey(out, seen, KEY_BY_NAME.ArrowUp)
  else if (ay >= DEADZONE) addKey(out, seen, KEY_BY_NAME.ArrowDown)
  return out
}

/**
 * 纯映射:这一帧该派发哪些键盘事件。
 * @param {Gamepad} pad
 * @param {{active?:object, codes?:object, nextRepeat?:object}} prev 上一次的 next
 * @param {number} now 当前时间(ms)
 * @returns {{events:Array<{type:string,key:string,code:string}>, next:object}}
 */
export function mapGamepadState(pad, prev = {}, now = 0) {
  const active = readGamepadKeys(pad)
  const prevActive = prev.active || {}
  const prevCodes = prev.codes || {}
  const repeats = { ...(prev.nextRepeat || {}) }
  const nextActive = {}
  const nextCodes = {}
  const events = []

  for (const m of active) {
    nextActive[m.key] = true
    nextCodes[m.key] = m.code
    if (!prevActive[m.key]) {
      events.push({ type: 'keydown', key: m.key, code: m.code })
      if (m.repeat) repeats[m.key] = now + REPEAT_MS
    } else if (m.repeat && now >= (repeats[m.key] || 0)) {
      events.push({ type: 'keydown', key: m.key, code: m.code })
      repeats[m.key] = now + REPEAT_MS
    }
  }

  for (const key of Object.keys(prevActive)) {
    if (!nextActive[key]) {
      events.push({ type: 'keyup', key, code: prevCodes[key] || key })
      delete repeats[key]
    }
  }

  return { events, next: { active: nextActive, codes: nextCodes, nextRepeat: repeats } }
}

/** 焦点在输入框/文本域/下拉/contenteditable 时不接管 */
export function isTypingTarget(el) {
  if (!el) return false
  const tag = (el.tagName || '').toUpperCase()
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable === true
}

function hasOpenOverlay() {
  try {
    return !!document.querySelector('.el-overlay, .el-message-box__wrapper')
  } catch (e) {
    return false
  }
}

/** 把一次手柄输入变成真键盘事件;document 上的监听与 window 上的监听都能收到(冒泡) */
export function dispatchGamepadKey(evt) {
  if (typeof KeyboardEvent === 'undefined') return
  document.dispatchEvent(
    new KeyboardEvent(evt.type, {
      key: evt.key,
      code: evt.code,
      bubbles: true,
      cancelable: true,
    }),
  )
}

function firstPad() {
  try {
    const pads = navigator.getGamepads ? navigator.getGamepads() : []
    for (const p of pads) if (p && p.connected !== false) return p
  } catch (e) {
    /* 浏览器不支持手柄:静默 */
  }
  return null
}

/**
 * 组件内启用手柄适配:rAF 轮询,边沿/连发都交给 mapGamepadState。
 * @param {{ enabled?: () => boolean }} options enabled 返回 false 时暂停派发(状态仍轮询)
 */
export function useGamepad({ enabled = () => true } = {}) {
  const gamepadConnected = ref(false)
  const gamepadName = ref('')
  let raf = 0
  let prev = {}

  const loop = () => {
    raf = requestAnimationFrame(loop)
    if (document.hidden) return
    const pad = firstPad()
    if (!pad) {
      prev = {}
      return
    }
    if (!enabled() || isTypingTarget(document.activeElement) || hasOpenOverlay()) {
      // 暂停期间清空状态:恢复后不会补发「按住」的旧键
      prev = {}
      return
    }
    const { events, next } = mapGamepadState(pad, prev, performance.now())
    prev = next
    if (events.length) {
      if (!gamepadConnected.value) {
        gamepadConnected.value = true
        gamepadName.value = pad.id || ''
      }
      events.forEach(dispatchGamepadKey)
    }
  }

  onMounted(() => {
    if (typeof navigator === 'undefined' || !navigator.getGamepads) return
    raf = requestAnimationFrame(loop)
  })

  onBeforeUnmount(() => {
    if (raf) cancelAnimationFrame(raf)
    raf = 0
    prev = {}
    gamepadConnected.value = false
  })

  // 顶栏组件(同页子树)直接 inject,不用每个页面都手工传 prop
  provide(GAMEPAD_KEY, gamepadConnected)

  return { gamepadConnected, gamepadName }
}
