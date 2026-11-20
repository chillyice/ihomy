// 顶栏返回键的目标:**上一级(路由层级)**,不是浏览器历史里的上一页。
//
// 为什么不用 router.back():用户常从首页卡片、聊天分享、通知深链直达某个详情页,
// history 里压着的是「来路」而不是「上级」——从博客详情返回可能落到相册,从相册详情
// 返回可能落到首页。按路由表向上退一级,不管从哪儿进来,落点都一致。
//
// 解析规则(纯函数,便于单测):
//   1. 当前页在路由表里带 meta.parent(字符串)= 显式指定上级;meta.parent === false = 不给返回;
//   2. 否则逐段剥掉路径末尾,取第一个能在路由表里命中、且不是当前这条路由本身的路径;
//   3. 一路剥到根都没有 → 回首页(与旧「无历史就回首页」的兜底一致)。
// 说明:跳过「命中同一条路由」是为了 /blog/edit/:id? 这类可选参数——'/blog/edit/5' 剥一段
// 会命中 '/blog/edit'(还是编辑页自己),必须继续剥到 '/blog' 列表。

export const HOME_PATH = '/home'

/** 去掉 query/hash 与末尾斜杠,统一成 '/a/b' 形态 */
export function normalizePath(fullPath) {
  const raw = String(fullPath || '')
    .split('#')[0]
    .split('?')[0]
  if (!raw) return ''
  const withSlash = raw.startsWith('/') ? raw : `/${raw}`
  return withSlash.length > 1 ? withSlash.replace(/\/+$/, '') : withSlash
}

/** 路由模式是否匹配某条具体路径(支持 ':id' 与末尾 ':id?' 可选段;通配/正则路由不参与) */
export function routePatternMatches(pattern, path) {
  if (!pattern || pattern === '/' || /[*()]/.test(pattern)) return false
  const patternSegs = pattern.split('/').filter(Boolean)
  const pathSegs = path.split('/').filter(Boolean)
  if (pathSegs.length > patternSegs.length) return false
  for (let i = 0; i < patternSegs.length; i++) {
    const seg = patternSegs[i]
    if (seg.startsWith(':')) {
      if (pathSegs[i] === undefined && !seg.endsWith('?')) return false
      continue
    }
    if (seg !== pathSegs[i]) return false
  }
  return true
}

/** 在路由表里找一条命中该路径的记录:静态精确匹配优先,其次是动态段最少的模式 */
export function findRoute(path, entries = []) {
  let best = null
  let bestParams = Infinity
  for (const entry of entries) {
    if (!entry || !entry.path || !routePatternMatches(entry.path, path)) continue
    if (normalizePath(entry.path) === path) return entry
    const params = entry.path.split('/').filter((seg) => seg.startsWith(':')).length
    if (params < bestParams) {
      best = entry
      bestParams = params
    }
  }
  return best
}

/** 按模式字符串反查路由记录(用于确认剥出来的前缀是真实存在的一级,而不是碰巧被动态段吃掉) */
function entryByPattern(pattern, entries = []) {
  return entries.find((entry) => entry && entry.path && normalizePath(entry.path) === pattern) || null
}

/**
 * 解析某条路由的「上一级」路径。
 * @param {string} fullPath 当前完整路径(可带 query/hash)
 * @param {Array<{path: string, name?: string, meta?: object}>} entries 路由表(传 router.getRoutes())
 * @param {string} [fallback] 剥到根仍无上级时的落点,默认首页
 * @returns {string|null} 上级路径;当前就是根级/显式禁止返回时返回 null
 */
export function parentPathOf(fullPath, entries = [], fallback = HOME_PATH) {
  const path = normalizePath(fullPath)
  if (!path || path === '/' || path === HOME_PATH) return null

  const current = findRoute(path, entries)
  const override = current?.meta?.parent
  if (override === false || override === null) return null
  if (typeof override === 'string' && override) return override

  // 只在当前路由自己的模式串上做「整段前缀」比较:用真实路径去试会撞上兄弟分支
  // (如 '/album/shared/token' 的两段前缀 '/album/shared' 会被 '/album/:id' 误判为相册详情)
  const pathSegs = path.split('/').filter(Boolean)
  const patternSegs = (current?.path || '').split('/').filter(Boolean)
  for (let k = Math.min(patternSegs.length, pathSegs.length) - 1; k >= 1; k--) {
    const hit = entryByPattern(`/${patternSegs.slice(0, k).join('/')}`, entries)
    if (!hit) continue
    if (hit.meta?.parent === false) continue
    // 剥出来的还是当前这条路由(末尾可选参数段):继续往上剥
    if (current?.name && hit.name === current.name) continue
    return `/${pathSegs.slice(0, k).join('/')}`
  }
  return fallback
}
