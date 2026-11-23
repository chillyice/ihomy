import { describe, it, expect } from 'vitest'
import { parentPathOf, normalizePath, routePatternMatches, findRoute } from './navParent'

// 路由表按真实 router/index.js 的形状抽样(只需覆盖各类路径模式)
const ROUTES = [
  { path: '/home', name: 'Home' },
  { path: '/blog', name: 'BlogList' },
  { path: '/blog/:id', name: 'BlogDetail' },
  { path: '/blog/edit/:id?', name: 'BlogEdit' },
  { path: '/album', name: 'Album' },
  { path: '/album/:id', name: 'AlbumDetail' },
  { path: '/album/shared/:token', name: 'AlbumShared' },
  { path: '/cinema', name: 'Cinema' },
  { path: '/cinema/:itemId', name: 'CinemaDetail' },
  { path: '/kitchen', name: 'Kitchen' },
  { path: '/kitchen/recipe/:id', name: 'RecipeDetail' },
  { path: '/kitchen/recipe/new', name: 'RecipeNew' },
  { path: '/kitchen/recipe/:id/edit', name: 'RecipeEdit' },
  { path: '/games', name: 'Games' },
  { path: '/games/snake', name: 'Snake' },
  { path: '/games/mahjong', name: 'Mahjong' },
  { path: '/games/snake/match/:id', name: 'SnakeMatch' },
  { path: '/games/cards/:game?', name: 'Cards' },
  { path: '/games/room/:id', name: 'GameRoom' },
  { path: '/games/match/:id', name: 'GameMatch' },
  { path: '/tools', name: 'Tools' },
  { path: '/tools/mindmap', name: 'MindMapList' },
  { path: '/tools/mindmap/:id', name: 'MindMapEditor' },
  { path: '/bit-pouch/tools', name: 'BitPouchTools' },
  { path: '/bit-pouch/tools/:id', name: 'BitPouchTool' },
  { path: '/settings', name: 'Settings' },
  { path: '/storage/files', name: 'FileBrowse', meta: { parent: '/settings' } },
  { path: '/health', name: 'Health' },
  { path: '/health/shared/:token', name: 'HealthShared', meta: { parent: '/home' } },
  { path: '/chat', name: 'Chat' },
  { path: '/item', name: 'Item' },
  { path: '/bit_pouch', name: 'BitPouch' },
  { path: '/no-back', name: 'NoBack', meta: { parent: false } },
  { path: '/:pathMatch(.*)*', name: 'NotFound' },
]

describe('normalizePath', () => {
  it('去掉 query/hash 与末尾斜杠', () => {
    expect(normalizePath('/plan?tab=today')).toBe('/plan')
    expect(normalizePath('/blog/12#c1')).toBe('/blog/12')
    expect(normalizePath('/album/')).toBe('/album')
    expect(normalizePath('')).toBe('')
  })
})

describe('routePatternMatches / findRoute', () => {
  it('静态段与 :id 必须逐段相等,末尾 :id? 可缺省', () => {
    expect(routePatternMatches('/blog/:id', '/blog/12')).toBe(true)
    expect(routePatternMatches('/blog', '/blog/12')).toBe(false)
    expect(routePatternMatches('/blog/edit/:id?', '/blog/edit')).toBe(true)
    expect(routePatternMatches('/games/room/:id', '/games/room')).toBe(false)
  })

  it('通配与正则路由不参与匹配(否则会吃掉所有路径)', () => {
    expect(routePatternMatches('/:pathMatch(.*)*', '/whatever/deep')).toBe(false)
  })

  it('静态精确匹配优先于动态模式', () => {
    expect(findRoute('/kitchen/recipe/new', ROUTES)?.name).toBe('RecipeNew')
    expect(findRoute('/kitchen/recipe/9', ROUTES)?.name).toBe('RecipeDetail')
  })
})

describe('parentPathOf', () => {
  it('详情页回所属列表', () => {
    expect(parentPathOf('/blog/12', ROUTES)).toBe('/blog')
    expect(parentPathOf('/album/7', ROUTES)).toBe('/album')
    expect(parentPathOf('/album/shared/tok', ROUTES)).toBe('/album')
    expect(parentPathOf('/cinema/9', ROUTES)).toBe('/cinema')
  })

  it('可选参数段剥出来还是当前页时继续往上剥', () => {
    expect(parentPathOf('/blog/edit/12', ROUTES)).toBe('/blog/12')
    expect(parentPathOf('/blog/edit', ROUTES)).toBe('/blog')
    expect(parentPathOf('/games/cards/klondike', ROUTES)).toBe('/games')
  })

  it('多级路径回到最近一层兄弟页', () => {
    expect(parentPathOf('/kitchen/recipe/9/edit', ROUTES)).toBe('/kitchen/recipe/9')
    expect(parentPathOf('/kitchen/recipe/9', ROUTES)).toBe('/kitchen')
    expect(parentPathOf('/games/snake/match/3', ROUTES)).toBe('/games/match/3')
    expect(parentPathOf('/tools/mindmap/4', ROUTES)).toBe('/tools/mindmap')
    expect(parentPathOf('/bit-pouch/tools/json', ROUTES)).toBe('/bit-pouch/tools')
  })

  it('房间/对战这类列表子页回到小游戏大厅', () => {
    expect(parentPathOf('/games/room/5', ROUTES)).toBe('/games')
    expect(parentPathOf('/games/match/5', ROUTES)).toBe('/games')
  })

  it('meta.parent 显式指定时覆盖推导', () => {
    expect(parentPathOf('/storage/files', ROUTES)).toBe('/settings')
    // 公开分享快照:上级给首页,免得游客被 /health 的登录守卫弹去登录页
    expect(parentPathOf('/health/shared/abc', ROUTES)).toBe('/home')
  })

  it('meta.parent === false 表示不给返回', () => {
    expect(parentPathOf('/no-back', ROUTES)).toBe(null)
  })

  it('根级页面没有上一级', () => {
    expect(parentPathOf('/home', ROUTES)).toBe(null)
    expect(parentPathOf('/', ROUTES)).toBe(null)
    expect(parentPathOf('/settings', ROUTES)).toBe('/home')
  })

  it('普通筛选和分享参数不增加返回层级', () => {
    expect(parentPathOf('/plan?tab=today', ROUTES)).toBe('/home')
    expect(parentPathOf('/chat?peer=3', ROUTES)).toBe('/home')
  })

  it.each([
    ['/settings?tab=client', '/settings', '/home'],
    ['/chat?target=conversation', '/chat', '/home'],
    ['/chat?target=assistant#last', '/chat', '/home'],
    ['/item?view=map&focus=search', '/item?focus=search', '/item', '/home'],
    ['/blog/edit/31?from=feed', '/blog/31', '/blog', '/home'],
    ['/games/mahjong?room=18', '/games/room/18', '/games', '/home'],
    ['/games/cards/klondike?room=18', '/games/room/18', '/games', '/home'],
    ['/games/room/18?room=18', '/games', '/home'],
    ['/games/mahjong?room=0', '/games', '/home'],
    ['/games/snake/match/6', '/games/match/6', '/games', '/home'],
    ['/bit_pouch', '/home'],
  ])('从 %s 逐级回到首页后终止', (...steps) => {
    let path = steps[0]
    for (const expected of steps.slice(1)) {
      path = parentPathOf(path, ROUTES)
      expect(path).toBe(expected)
    }
    expect(parentPathOf(path, ROUTES)).toBe(null)
  })

  it('纯运维账号在自己的根页结束返回', () => {
    expect(parentPathOf('/ops?tab=trace', ROUTES, '/ops')).toBe(null)
  })

  it('路由表里查不到的深链回首页,而不是按历史倒回去', () => {
    expect(parentPathOf('/unknown/deep/link', ROUTES)).toBe('/home')
    expect(parentPathOf('/blog', [])).toBe('/home')
  })
})
