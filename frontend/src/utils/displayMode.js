// 显示模式(老人模式 / 儿童模式)的纯逻辑与本地存储。
// 只作用于本机界面:不改变账号权限、不上传服务端,换设备/换浏览器不跟随。
// 儿童模式的退出密码只是挡住孩子随手切走的本地门槛(哈希后存 localStorage),不是安全凭证。

export const DISPLAY_MODE_KEY = 'ihomy:display-mode:v1'

/** 三种模式:normal 默认 / elder 老人(大字+高对比+简化首页) / child 儿童(导航白名单) */
export const DISPLAY_MODES = ['normal', 'elder', 'child']

/** 儿童模式可停留的路径前缀白名单:只留内容消费与首页,写操作页/设置/记账本一律不放 */
export const CHILD_ALLOWED_PATHS = [
  '/',
  '/home',
  '/blog',
  '/diary',
  '/album',
  '/library',
  '/cinema',
  '/music',
  '/games',
  '/weather',
  '/help',
  '/points',
  '/plant',
  '/login',
  '/reset-password',
  '/app',
]

/** 是否允许停留在该路径(前缀级判定,忽略 query/hash) */
export function isChildAllowedPath(path) {
  const p =
    String(path || '/')
      .split('?')[0]
      .split('#')[0] || '/'
  return CHILD_ALLOWED_PATHS.some((base) => p === base || p.startsWith(base + '/'))
}

const isValidMode = (m) => DISPLAY_MODES.includes(m)

/** 读本机模式:{ mode, pin };缺键/坏 JSON/存储不可用都回落默认,不抛错 */
export function readDisplayMode(storage = globalThis.localStorage) {
  const fallback = { mode: 'normal', pin: null }
  try {
    const raw = storage?.getItem(DISPLAY_MODE_KEY)
    if (!raw) return fallback
    const saved = JSON.parse(raw)
    if (!saved || typeof saved !== 'object') return fallback
    return {
      mode: isValidMode(saved.mode) ? saved.mode : 'normal',
      pin: saved.pin && typeof saved.pin === 'object' ? saved.pin : null,
    }
  } catch (e) {
    return fallback
  }
}

/** 写本机模式:存储不可用(隐私模式)时静默失败,界面仍按内存状态工作 */
export function writeDisplayMode(state, storage = globalThis.localStorage) {
  try {
    storage?.setItem(DISPLAY_MODE_KEY, JSON.stringify({ mode: state?.mode || 'normal', pin: state?.pin || null }))
  } catch (e) {
    // 忽略配额/禁用存储
  }
}

// —— 按账号分开的记录 ——
// 同一台设备上,儿童模式只该锁住使用它的那个成员(家长的账号不受影响),所以
// 记录按账号 id 分开存;旧版那条不分账号的记录会在首次绑定时迁移给第一个登录的账号。
const USER_KEY_PREFIX = 'ihomy:display-mode:u'
const userKey = (userId) => `${USER_KEY_PREFIX}${userId ?? 'guest'}:v1`
const isActivePin = (pin) => (pin && typeof pin === 'object' ? pin : null)

/** 读某个账号在这台设备上的显示模式;没有记录时回落默认(不含密码,未开启即无密码) */
export function readUserDisplayMode(userId, storage = globalThis.localStorage) {
  const fallback = { mode: 'normal', pin: null }
  try {
    const raw = storage?.getItem(userKey(userId))
    if (raw) {
      const saved = JSON.parse(raw)
      if (!saved || typeof saved !== 'object') return fallback
      return { mode: isValidMode(saved.mode) ? saved.mode : 'normal', pin: isActivePin(saved.pin) }
    }
    // 旧版全局记录(不分账号)借给第一个绑定的账号,迁完即删,避免之后又借给别的账号
    const legacy = readDisplayMode(storage)
    if (legacy.mode !== 'normal' || legacy.pin) {
      writeUserDisplayMode(userId, legacy, storage)
      try {
        storage?.removeItem(DISPLAY_MODE_KEY)
      } catch (e) {
        // 忽略
      }
    }
    return legacy
  } catch (e) {
    return fallback
  }
}

/** 写某个账号的显示模式(家长替孩子设置时也走这里) */
export function writeUserDisplayMode(userId, state, storage = globalThis.localStorage) {
  try {
    storage?.setItem(userKey(userId), JSON.stringify({ mode: state?.mode || 'normal', pin: isActivePin(state?.pin) }))
  } catch (e) {
    // 忽略配额/禁用存储
  }
}

const SALT_BYTES = 16
const PIN_RE = /^\d{4,8}$/

/** 4–8 位纯数字密码(输入框与校验共用同一口径) */
export function isValidPin(pin) {
  return PIN_RE.test(String(pin || ''))
}

function toHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}

function randomSalt() {
  const bytes = new Uint8Array(SALT_BYTES)
  const c = globalThis.crypto
  if (c?.getRandomValues) c.getRandomValues(bytes)
  else for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256)
  return toHex(bytes)
}

// 仅供本地门槛使用的退化哈希(无 WebCrypto 时):FNV-1a 混合两轮,不做安全承诺
function fallbackHash(text) {
  let h1 = 0x811c9dc5
  let h2 = 0x01000193
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i)
    h1 = Math.imul(h1 ^ c, 0x01000193) >>> 0
    h2 = Math.imul(h2 ^ c, 0x85ebca6b) >>> 0
  }
  return `f${h1.toString(16)}${h2.toString(16)}`
}

/** SHA-256(盐+密码),WebCrypto 不可用时退化为本地哈希 */
export async function hashPin(pin, salt) {
  const text = `${salt || ''}:${pin || ''}`
  const subtle = globalThis.crypto?.subtle
  if (subtle) {
    try {
      const buf = await subtle.digest('SHA-256', new TextEncoder().encode(text))
      return toHex(new Uint8Array(buf))
    } catch (e) {
      // 落到退化哈希
    }
  }
  return fallbackHash(text)
}

/** 生成密码记录 { salt, hash } */
export async function makePinRecord(pin) {
  const salt = randomSalt()
  return { salt, hash: await hashPin(pin, salt) }
}

/** 校验密码记录(记录缺失时视为通过:没设过密码就不该被锁住) */
export async function verifyPinRecord(pin, record) {
  if (!record?.salt || !record?.hash) return true
  return (await hashPin(pin, record.salt)) === record.hash
}
