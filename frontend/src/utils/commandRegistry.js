// 命令面板数据与过滤纯函数(Ctrl/⌘+K)。
//
// 这里只有静态数据 + 纯函数:不读 window、不读 store、不请求网络。
// 运行时状态(登录/权限/当前主题)由调用方以 ctx 喂进来,命令的 run 也只用 ctx,
// 这样过滤结果可以稳定单测(AGENTS 前端规范 17:computed 内禁止随机/时间/副作用)。
// 跳转路径复用 navModules 的 NAV_PATHS(首页不在 sys_home_module,单独写 /home)。
import { NAV_PATHS } from '@/utils/navModules'

// 需要登录的页面:游客不出现(与路由 meta.requiresAuth 同口径)
const authOnly = (ctx) => !ctx?.isLoggedIn

export const COMMANDS = [
  { id: 'nav-home', group: 'nav', labelKey: 'command.nav.home', path: '/home' },
  { id: 'nav-album', group: 'nav', labelKey: 'command.nav.album', path: NAV_PATHS.album },
  { id: 'nav-diary', group: 'nav', labelKey: 'command.nav.diary', path: NAV_PATHS.diary },
  { id: 'nav-blog', group: 'nav', labelKey: 'command.nav.blog', path: NAV_PATHS.blog },
  { id: 'nav-plan', group: 'nav', labelKey: 'command.nav.plan', path: NAV_PATHS.plan },
  { id: 'nav-library', group: 'nav', labelKey: 'command.nav.library', path: NAV_PATHS.library },
  { id: 'nav-files', group: 'nav', labelKey: 'command.nav.files', path: NAV_PATHS.storage, hidden: authOnly },
  { id: 'nav-member', group: 'nav', labelKey: 'command.nav.member', path: NAV_PATHS.member, hidden: authOnly },
  { id: 'nav-book', group: 'nav', labelKey: 'command.nav.book', path: NAV_PATHS.book },
  { id: 'nav-wish', group: 'nav', labelKey: 'command.nav.wish', path: NAV_PATHS.wish },
  { id: 'nav-music', group: 'nav', labelKey: 'command.nav.music', path: NAV_PATHS.music },
  { id: 'nav-cinema', group: 'nav', labelKey: 'command.nav.cinema', path: NAV_PATHS.cinema },
  { id: 'nav-tools', group: 'nav', labelKey: 'command.nav.tools', path: NAV_PATHS.tools },
  { id: 'nav-settings', group: 'nav', labelKey: 'command.nav.settings', path: NAV_PATHS.settings, hidden: authOnly },
  // 动作命令只放「无需一次性 query 语义」的:切主题、聚焦本页搜索。
  // 「新建提醒 / 记一笔」属于下一批 S3(?new=1),本批不加。
  {
    id: 'action-theme-warm',
    group: 'action',
    labelKey: 'command.action.themeWarm',
    run: (ctx) => ctx?.setTheme?.('warm'),
    hidden: (ctx) => ctx?.theme === 'warm',
  },
  {
    id: 'action-theme-guangchen',
    group: 'action',
    labelKey: 'command.action.themeGuangchen',
    run: (ctx) => ctx?.setTheme?.('guangchen'),
    hidden: (ctx) => ctx?.theme === 'guangchen',
  },
  {
    id: 'action-focus-search',
    group: 'action',
    labelKey: 'command.action.focusSearch',
    run: (ctx) => ctx?.focusSearch?.(),
  },
]

/** 按运行时上下文过滤掉不可用命令(游客看不到文件/成员/设置) */
export function visibleCommands(ctx = {}) {
  return COMMANDS.filter((c) => !(typeof c.hidden === 'function' && c.hidden(ctx)))
}

// 子序列匹配:输入「blg」也能命中「Blog」(路径/英文名快速跳转)
function isSubsequence(needle, haystack) {
  let j = 0
  for (let i = 0; i < haystack.length && j < needle.length; i++) {
    if (haystack[i] === needle[j]) j += 1
  }
  return j === needle.length
}

// 0 = 子串命中;1 = 子序列命中;-1 = 不命中
function matchScore(command, q) {
  const label = String(command.label ?? '').toLowerCase()
  const id = String(command.id ?? '').toLowerCase()
  if (label.includes(q) || id.includes(q)) return 0
  if (isSubsequence(q, label) || isSubsequence(q, id)) return 1
  return -1
}

/**
 * 过滤命令:空 query 取前 limit 条;否则按 label / id 做不区分大小写的子串/子序列匹配。
 * 顺序稳定:同分按原始顺序返回。
 */
export function filterCommands(commands, query, limit = 12) {
  const list = Array.isArray(commands) ? commands : []
  const max = Math.max(0, limit)
  const q = String(query ?? '')
    .trim()
    .toLowerCase()
  if (!q) return list.slice(0, max)

  const hits = []
  list.forEach((c, index) => {
    const score = matchScore(c, q)
    if (score >= 0) hits.push({ c, score, index })
  })
  hits.sort((a, b) => a.score - b.score || a.index - b.index)
  return hits.slice(0, max).map((h) => h.c)
}
