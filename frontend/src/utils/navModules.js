// 导航共享数据与纯逻辑:首页模块(sys_home_module)→ 导航分组 的单一数据源。
// AppSidebar(光尘/默认主题)、WarmLayout(暖居主题)、MobileMoreGrid(移动端)三处渲染共用,
// 未来新增主题或导航样式时,只需写自己的渲染层,复用这里的数据与分组规则。
// 新增功能模块时,只需在 NAV_PATHS 加一行 code→路由,三端自动同步。
import {
  Document, Notebook, Picture, Calendar, VideoPlay, Headset, Trophy, Aim,
  AlarmClock, List, Star, Wallet, PictureRounded, Share, User, Box, MapLocation,
  ChatDotRound, Food, Reading, Setting, Monitor, Tools, Sunny,
} from '@element-plus/icons-vue'

// code → 路由路径(后端 sys_home_module.code 为字符串标识)
export const NAV_PATHS = {
  blog: '/blog', diary: '/diary', album: '/album', anniversary: '/anniversary',
  cinema: '/cinema', music: '/music', member: '/member', points: '/points', task: '/task',
  reminder: '/reminder', plan: '/plan', wish: '/wish', book: '/book',
  chat: '/chat', tree: '/tree', cascade: '/cascade',
  item: '/item', kitchen: '/kitchen', library: '/library', settings: '/settings', ops: '/ops',
  storage: '/storage/files', tools: '/tools', plant: '/plant', games: '/games',
}

// code → 图标组件(Element Plus 线性图标,统一风格)
export const ICON_MAP = {
  blog: Document, diary: Notebook, album: Picture, anniversary: Calendar, cinema: VideoPlay, music: Headset,
  points: Trophy, task: Aim, reminder: AlarmClock, plan: List, wish: Star,
  book: Wallet, cascade: PictureRounded, tree: Share, member: User, storage: Box, item: MapLocation,
  chat: ChatDotRound, kitchen: Food, library: Reading, settings: Setting, ops: Monitor, tools: Tools,
  plant: Sunny, games: Aim,
}
export const iconComp = (code) => ICON_MAP[code] || Document

// 分组顺序 + 中文名(相册 album 已并入 content,不单独成组)
export const NAV_GROUP_ORDER = ['content', 'life', 'social', 'system']
export const CATEGORY_LABELS = { content: '内容', life: '生活', social: '成员', system: '系统' }
export const categoryLabel = (cat) => CATEGORY_LABELS[cat] || '功能'

// album 归入 content;空分类兜底 life
const normalizeCategory = (cat) => (cat === 'album' ? 'content' : (cat || 'life'))

// 原始模块 → 扁平导航项:过滤无路径映射/停用模块,追加「设置」+「运维管理」虚拟入口,按 sortOrder 升序。
// hasOps:是否展示运维管理(拥有 ops:view 权限)。
export function buildNavItems(modules, { hasOps = false } = {}) {
  const list = (modules || [])
    .filter((m) => NAV_PATHS[m.code] && m.enabled !== 0)
    .map((m) => ({
      code: m.code,
      title: m.title,
      path: NAV_PATHS[m.code] || m.path,
      category: m.category || 'life',
      sortOrder: m.sortOrder ?? 99,
    }))
  list.push({ code: 'settings', title: '设置', path: '/settings', category: 'system', sortOrder: 90 })
  if (hasOps) list.push({ code: 'ops', title: '运维管理', path: '/ops', category: 'system', sortOrder: 95 })
  return list.sort((a, b) => a.sortOrder - b.sortOrder)
}

// 扁平导航项 → 按 category 分组(顺序 NAV_GROUP_ORDER),每组 { category, label, items }
export function groupNavItems(items) {
  const groups = {}
  for (const m of items) {
    const cat = normalizeCategory(m.category)
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(m)
  }
  return NAV_GROUP_ORDER
    .filter((c) => groups[c] && groups[c].length)
    .map((c) => ({ category: c, label: CATEGORY_LABELS[c] || '功能', items: groups[c] }))
}

// 便捷入口:原始模块 → 分组导航(无需二次加工的主题直接渲染用)
export function buildNavGroups(modules, options) {
  return groupNavItems(buildNavItems(modules, options))
}

// 路由高亮:当前路径是否命中该导航项(首页 '/' 单独判断,其余前缀匹配)
export const isActive = (path, route) => (path === '/' ? route.path === '/' : route.path.startsWith(path))
