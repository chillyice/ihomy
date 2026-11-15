// 站内 iframe 直跑的开源小游戏:整包放在 frontend/public/mini-games/<slug>/
// 只登记元数据,不引 npm 依赖、不进首屏 bundle;新增一款 = 放目录 + 这里加一行 + i18n 两条文案。
// 出处与协议见 frontend/public/mini-games/THIRD-PARTY.md。
export const EMBEDDED_GAMES = [
  { slug: 'xiangqi', titleKey: 'games.embedded.xiangqi.title', descKey: 'games.embedded.xiangqi.desc', category: 'STRATEGY' },
  { slug: 'reversi', titleKey: 'games.embedded.reversi.title', descKey: 'games.embedded.reversi.desc', category: 'STRATEGY' },
  { slug: 'sokoban', titleKey: 'games.embedded.sokoban.title', descKey: 'games.embedded.sokoban.desc', category: 'PUZZLE' },
  { slug: 'klotski', titleKey: 'games.embedded.klotski.title', descKey: 'games.embedded.klotski.desc', category: 'PUZZLE' },
  { slug: 'nonogram', titleKey: 'games.embedded.nonogram.title', descKey: 'games.embedded.nonogram.desc', category: 'PUZZLE' },
  { slug: 'match3', titleKey: 'games.embedded.match3.title', descKey: 'games.embedded.match3.desc', category: 'PUZZLE' },
  { slug: 'bubble', titleKey: 'games.embedded.bubble.title', descKey: 'games.embedded.bubble.desc', category: 'ARCADE' },
  {
    slug: 'space-cadet',
    titleKey: 'games.embedded.space-cadet.title',
    descKey: 'games.embedded.space-cadet.desc',
    category: 'ARCADE',
  },
  { slug: 'trex', titleKey: 'games.embedded.trex.title', descKey: 'games.embedded.trex.desc', category: 'ARCADE' },
]

/** 按 slug 找元数据;不存在返回 null(路由手输地址时不炸) */
export const findEmbeddedGame = (slug) => EMBEDDED_GAMES.find((g) => g.slug === slug) || null

/** iframe 地址:静态目录由 Nginx / dev server 直接托管,不经过后端 */
export const embeddedGameSrc = (slug) => `/mini-games/${slug}/index.html`
