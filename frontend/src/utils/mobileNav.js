// 移动外壳(MobileLayout)的纯逻辑:首页判据 + 子页面页头标题键。
// 抽成纯函数便于单测(vitest 无组件测试环境),组件里只负责取当前路由与 t() 包装。
//
// ⚠ 首页判据必须同时认 '/' 与 '/home':路由把 '/' 重定向到 /home(router/index.js),
// 只判 '/' 会让三 Tab 分支永远不命中——2026-09-12 经典首页由 '/' 迁到 '/home' 时判据未同步,
// 移动端因此长时间落在子页面模式(渲染桌面首页、无底部 TabBar),V10.26 修复并加回归。
export const MOBILE_HOME_PATHS = ['/', '/home']

export function isMobileHomePath(path) {
  return MOBILE_HOME_PATHS.includes(path)
}

// 精确路径 → 页头标题 i18n 键
const PAGE_TITLE_KEYS = {
  '/blog': 'blog.title',
  '/diary': 'diary.title',
  '/album': 'album.title',
  '/anniversary': 'anniversary.title',
  '/announcement': 'announcement.title',
  '/cinema': 'cinema.title',
  '/music': 'music.title',
  '/member': 'member.title',
  '/points': 'points.title',
  '/task': 'task.title',
  '/reminder': 'reminder.title',
  '/plan': 'plan.title',
  '/wish': 'wish.title',
  '/book': 'book.title',
  '/chat': 'chat.title',
  '/tree': 'tree.title',
  '/item': 'mobile.title.item',
  '/kitchen': 'kitchen.title',
  '/library': 'library.title',
  '/settings': 'settings.title',
  '/ops': 'mobile.title.ops',
  '/vault': 'vault.title',
  '/login': 'mobile.title.login',
  '/recycle': 'recycle.title',
  '/iot': 'iot.title',
  '/weather': 'weatherPage.title',
  '/storage/files': 'storage.title',
  '/tools': 'tools.title',
  '/games': 'games.title',
  '/help': 'help.title',
  '/reset-password': 'passwordReset.resetTitle',
}

// 表单路径判据:新建与编辑的路由分别是 .../edit/:id? 与 .../new(菜谱),两者都该显示「编辑/新建」类标题。
// 原先只判 includes('/edit/')(带尾斜杠),新建时路径是 .../edit 或 .../new,标题会错显成详情页
const isFormPath = (path) => /(^|\/)(edit|new)(\/|$)/.test(path)

// 带子路由的路径前缀规则:按数组顺序取首个命中(与原先 if 链同序),表单态与详情态文案不同
const PREFIX_TITLE_RULES = [
  { prefix: '/blog/', key: (p) => (isFormPath(p) ? 'mobile.title.blogEdit' : 'mobile.title.blogDetail') },
  { prefix: '/diary', key: (p) => (isFormPath(p) ? 'mobile.title.diaryWrite' : 'diary.title') },
  { prefix: '/album/', key: () => 'mobile.title.albumDetail' },
  { prefix: '/library/', key: (p) => (isFormPath(p) ? 'mobile.title.bookEdit' : 'mobile.title.bookDetail') },
  { prefix: '/kitchen/recipe/', key: (p) => (isFormPath(p) ? 'mobile.title.recipeEdit' : 'mobile.title.recipeDetail') },
  { prefix: '/kitchen/ingredients', key: () => 'mobile.title.ingredients' },
  { prefix: '/cinema/', key: () => 'mobile.title.cinemaDetail' },
  { prefix: '/tools/loan', key: () => 'mobile.title.loan' },
  { prefix: '/tools/mindmap', key: () => 'tools.mindmap.title' },
  { prefix: '/tools/ai-playground', key: () => 'tools.aiPlayground.title' },
  { prefix: '/tools/light-lab', key: () => 'lightLab.title' },
  { prefix: '/tools/flash', key: () => 'tools.flash.title' },
  { prefix: '/tools/gba', key: () => 'tools.gba.title' },
  { prefix: '/tools/plant', key: () => 'plant.title' },
  { prefix: '/games/petlink', key: () => 'games.petlink.title' },
  { prefix: '/games/play', key: () => 'games.title' },
]

// 路由路径 → 页头标题 i18n 键;无匹配(独立产品页 /wallpaper、/kada、/bit-pouch 与未适配页)返回空串,页头只显示返回按钮
export function mobileHeaderTitleKey(path) {
  if (!path) return ''
  const hit = PREFIX_TITLE_RULES.find((rule) => path.startsWith(rule.prefix))
  if (hit) return hit.key(path)
  return PAGE_TITLE_KEYS[path] || ''
}
