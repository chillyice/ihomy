// 移动端底部导航(纵屏)的纯逻辑:默认五格、可整格替换、配置的读取/整理。
//
// 设计口径:
// - 五格全部可换(中间那格默认是「拍摄」加号,也可以换成任意功能)。
// - 配置只存「功能 code」,路由/图标/文案在渲染时解析——后端模块改了标题也不会把配置写死。
// - 槽位数固定 5:多退少补,未知 code 回落默认;同一功能不允许占两格(重复的换成默认)。
// - 本机 localStorage 是即时缓存,登录后与账号同步(键见 LOCAL_KEY / SERVER_KEY,同步层 utils/userPref.js)。
import { buildNavItems, NAV_PATHS } from './navModules'

export const MOBILE_TAB_COUNT = 5

// 默认五格:首页 / 相册 / 拍摄(中间加号) / 日程 / 我的
export const DEFAULT_MOBILE_TABS = ['home', 'album', 'capture', 'plan', 'me']

// 不入 sys_home_module 的虚拟入口:三 Tab 外壳(home/more/me)与拍摄
export const VIRTUAL_MOBILE_TABS = [
  { code: 'home', titleKey: 'mobile.home', path: '/home' },
  { code: 'more', titleKey: 'mobile.more', path: '/home' },
  { code: 'me', titleKey: 'mobile.me', path: '/home' },
  { code: 'capture', titleKey: 'mobile.capture', path: '' },
]

export const MOBILE_TABS_LOCAL_KEY = 'ihomy:mobile:tabs:v1'
export const MOBILE_TABS_SERVER_KEY = 'mobile.tabs.v1'

/**
 * 可选功能清单:后端模块(经 buildNavItems 过滤/补虚拟入口)+ 移动外壳自有入口。
 * 返回 [{ code, title?, titleKey?, path }],文案由渲染层解析(titleKey 走 i18n,title 直接显示)。
 */
export function buildMobileTabCandidates(modules, options = {}) {
  const navItems = buildNavItems(modules, options).map((m) => ({
    code: m.code,
    title: m.title,
    path: m.path,
  }))
  const virtual = VIRTUAL_MOBILE_TABS.map((v) => ({ ...v }))
  const list = [...virtual, ...navItems]
  const seen = new Set()
  return list.filter((item) => {
    if (!item.code || seen.has(item.code)) return false
    // 只保留有路由的模块(capture 例外:它是动作不是页面)
    if (item.code !== 'capture' && !item.path) return false
    seen.add(item.code)
    return true
  })
}

/**
 * 把任意长度的配置整理成合法的 5 格:
 * - 非数组/空 → 默认五格
 * - 过滤未知 code、去重(重复项回落默认)
 * - 不足 5 格用默认里还没出现的功能补齐,再不够取候选清单
 */
export function normalizeMobileTabs(codes, validCodes = DEFAULT_MOBILE_TABS) {
  const valid = Array.isArray(validCodes) && validCodes.length ? validCodes : DEFAULT_MOBILE_TABS
  const validSet = new Set(valid)
  const out = new Array(MOBILE_TAB_COUNT).fill(null)
  const used = new Set()

  // 1) 原样落位:未知 code 与重复项先留空,避免后面的合法项整体前移(否则中间加号会跑位)
  const input = Array.isArray(codes) ? codes : []
  for (let i = 0; i < MOBILE_TAB_COUNT; i++) {
    const code = input[i]
    if (validSet.has(code) && !used.has(code)) {
      out[i] = code
      used.add(code)
    }
  }
  // 2) 空位优先用「同一位置」的默认项补,保住默认布局的语义
  for (let i = 0; i < MOBILE_TAB_COUNT; i++) {
    if (out[i]) continue
    const code = DEFAULT_MOBILE_TABS[i]
    if (code && validSet.has(code) && !used.has(code)) {
      out[i] = code
      used.add(code)
    }
  }
  // 3) 还有空位:先用默认清单,再用全部候选
  const fillers = [...DEFAULT_MOBILE_TABS.filter((c) => validSet.has(c)), ...valid]
  for (const code of fillers) {
    const idx = out.indexOf(null)
    if (idx < 0) break
    if (used.has(code)) continue
    out[idx] = code
    used.add(code)
  }
  // 4) 候选不足 5 个时(理论上不会)用拍摄占位,避免渲染出空槽
  return out.map((code) => code || 'capture')
}

/** localStorage 原始字符串 → 配置数组(解析失败按未设置处理,交给默认) */
export function parseMobileTabs(raw) {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return null
    return parsed.filter((x) => typeof x === 'string')
  } catch (e) {
    return null
  }
}

export function readMobileTabs(storage = globalThis.localStorage) {
  try {
    return parseMobileTabs(storage?.getItem(MOBILE_TABS_LOCAL_KEY))
  } catch (e) {
    return null
  }
}

// 存盘只做长度裁剪与类型收敛:不能拿默认清单去 normalize,否则用户选的非默认模块会被清掉
export function serializeMobileTabs(codes) {
  const list = (Array.isArray(codes) ? codes : []).filter((x) => typeof x === 'string' && x)
  return JSON.stringify(list.slice(0, MOBILE_TAB_COUNT))
}

/**
 * 把 code 换成可渲染的入口。找不到(模块被停用/删除)返回 null,
 * 由调用方回落默认,避免底栏出现空白格。
 */
export function resolveMobileTab(code, candidates) {
  const list = candidates || []
  if (code === 'capture') {
    return { code: 'capture', titleKey: 'mobile.capture', path: '', capture: true }
  }
  const hit = list.find((c) => c.code === code)
  if (!hit) return null
  if (hit.code === 'home' || hit.code === 'more' || hit.code === 'me') {
    return { ...hit, shellTab: hit.code }
  }
  return { code: hit.code, title: hit.title, titleKey: hit.titleKey, path: hit.path || NAV_PATHS[hit.code] || '' }
}

/** 当前路由是否命中某个入口(首页判 '/'、'/home';其余前缀匹配,详情页也高亮) */
export function isMobileTabActive(item, path) {
  if (!item || item.capture || !item.path) return false
  if (item.shellTab) return false
  return path === item.path || path.startsWith(item.path.endsWith('/') ? item.path : item.path + '/')
}
