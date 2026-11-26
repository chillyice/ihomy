// 功能开关:导航模块与单个小游戏各有「启用 / 禁用 / 仅成年人」三态。
// 与显示模式同一套口径——记录按账号存在本机(ihomy:feature-gates:u<id>:v1),不上传服务端:
// 「禁用」指这台设备上看不到(导航、首页、直达链接一并消失),不是账号权限;
// 「仅成年人」指未成年模式下隐藏,家长/普通成员都仍可自行关闭。
import { NAV_PATHS } from './navModules'
import { GAME_ENTRIES, gameFeatureId } from './games/gameEntries'

export const FEATURE_GATES_KEY_PREFIX = 'ihomy:feature-gates:u'
export const FEATURE_GATE_STATES = ['on', 'off', 'adult']
export const DEFAULT_FEATURE_GATE = 'on'

const gateKey = (userId) => `${FEATURE_GATES_KEY_PREFIX}${userId ?? 'guest'}:v1`

/** 三态归一:任何不认识的取值(旧数据/手改缓存)都当「启用」,不让功能莫名消失 */
export const normalizeGateState = (state) => (FEATURE_GATE_STATES.includes(state) ? state : DEFAULT_FEATURE_GATE)

/** 读某个账号的功能开关:只保留合法的 featureId → 状态项,坏 JSON 回落空表 */
export function readUserFeatureGates(userId, storage = globalThis.localStorage) {
  try {
    const raw = storage?.getItem(gateKey(userId))
    if (!raw) return {}
    const saved = JSON.parse(raw)
    if (!saved || typeof saved !== 'object' || Array.isArray(saved)) return {}
    const out = {}
    for (const [id, state] of Object.entries(saved)) {
      const next = normalizeGateState(state)
      // 默认态不落表:缓存里只留改过的项,项数少、读起来也直观
      if (typeof id === 'string' && id && next !== DEFAULT_FEATURE_GATE) out[id] = next
    }
    return out
  } catch (e) {
    return {}
  }
}

/** 整表覆盖写(存储不可用时静默失败,界面仍按内存状态工作) */
export function writeUserFeatureGates(userId, gates, storage = globalThis.localStorage) {
  const out = {}
  for (const [id, state] of Object.entries(gates || {})) {
    const next = normalizeGateState(state)
    if (id && next !== DEFAULT_FEATURE_GATE) out[id] = next
  }
  try {
    if (Object.keys(out).length) storage?.setItem(gateKey(userId), JSON.stringify(out))
    else storage?.removeItem(gateKey(userId))
  } catch (e) {
    // 忽略配额/禁用存储
  }
  return out
}

/** 改一项并返回新的整表(调用方拿它刷新内存状态) */
export function setUserFeatureGate(userId, featureId, state, storage = globalThis.localStorage) {
  if (!featureId) return readUserFeatureGates(userId, storage)
  const next = { ...readUserFeatureGates(userId, storage) }
  const normalized = normalizeGateState(state)
  if (normalized === DEFAULT_FEATURE_GATE) delete next[featureId]
  else next[featureId] = normalized
  return writeUserFeatureGates(userId, next, storage)
}

/** 恢复默认:直接删记录(等价于全部启用) */
export function clearUserFeatureGates(userId, storage = globalThis.localStorage) {
  try {
    storage?.removeItem(gateKey(userId))
  } catch (e) {
    // 同上
  }
  return {}
}

/** 取某项的三态(缺省 = 启用) */
export const gateOf = (gates, featureId) => normalizeGateState(gates?.[featureId])

/** 三态在当前显示模式下是否可见:on 恒可见;off 恒隐藏;adult 只在未成年模式下隐藏 */
export function isGateVisible(state, mode) {
  const normalized = normalizeGateState(state)
  if (normalized === 'off') return false
  if (normalized === 'adult') return mode !== 'child'
  return true
}

/** 某个功能(id 为模块 code 或 game:<key>)在当前状态与模式下是否可见 */
export const isFeatureVisible = (featureId, gates, mode) => isGateVisible(gateOf(gates, featureId), mode)

// —— 路径 → 功能:路由守卫与「直达链接」用 ——
// 只登记真有页面入口的功能;设置/运维不参与(否则会把自己锁在外面)。
// 子项路径比父级长,按长度倒序匹配即可让单个小游戏盖过 /games。
const VIRTUAL_GATE_PATHS = [
  { id: 'weather', path: '/weather' },
  { id: 'help', path: '/help' },
]
const UNGATED_CODES = ['settings', 'ops']

let pathRows = null

function pathIndex() {
  if (pathRows) return pathRows
  const rows = []
  for (const [code, path] of Object.entries(NAV_PATHS)) {
    if (UNGATED_CODES.includes(code) || !path) continue
    rows.push({ id: code, path })
  }
  rows.push(...VIRTUAL_GATE_PATHS)
  for (const game of GAME_ENTRIES) {
    rows.push({
      id: gameFeatureId(game.key),
      path: game.path,
      // /games 之外(如植物养成 /tools/plant)不跟随「游戏」总开关,它同时是工具页入口
      parent: game.path.startsWith('/games/') ? 'games' : null,
    })
  }
  pathRows = rows.sort((a, b) => b.path.length - a.path.length)
  return pathRows
}

/** 该路径是否被功能开关隐藏(命中模块或小游戏;父级 games 关闭时子项一并隐藏) */
export function isPathHidden(path, gates, mode) {
  const p =
    String(path || '/')
      .split('?')[0]
      .split('#')[0] || '/'
  const hit = pathIndex().find((row) => p === row.path || p.startsWith(row.path + '/'))
  if (!hit) return false
  if (hit.parent && !isFeatureVisible(hit.parent, gates, mode)) return true
  return !isFeatureVisible(hit.id, gates, mode)
}

/** 该路径对应的功能 id(测试与调试用);没有对应功能时返回 null */
export function featureIdForPath(path) {
  const p =
    String(path || '/')
      .split('?')[0]
      .split('#')[0] || '/'
  return pathIndex().find((row) => p === row.path || p.startsWith(row.path + '/'))?.id ?? null
}
