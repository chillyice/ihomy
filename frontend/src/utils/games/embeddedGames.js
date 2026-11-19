// 站内开源小游戏的卡片元数据(slug/标题/描述/分类):新增一款 = 这里加一行 + i18n 两条文案。
// 玩法分两种:中国象棋/黑白棋/消消乐/泡泡龙/小恐龙已本地化为 Vue 组件(`views/games/embedded/`,
// 引擎在 utils/games/,精灵与音效在 src/assets/games/),走统一游戏外壳;
// 只保留三维弹球·太空军校生为整包静态页(`public/mini-games/space-cadet/`)站内 iframe 直跑——
// 它的引擎是 Emscripten 编译的 C++/wasm,无法自研重写。
// 出处与协议见 frontend/public/mini-games/THIRD-PARTY.md。
export const EMBEDDED_GAMES = [
  { slug: 'xiangqi', titleKey: 'games.embedded.xiangqi.title', descKey: 'games.embedded.xiangqi.desc', category: 'BOARD' },
  { slug: 'reversi', titleKey: 'games.embedded.reversi.title', descKey: 'games.embedded.reversi.desc', category: 'BOARD' },
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

/** iframe 地址:只剩 space-cadet 这类整包静态游戏用,静态目录由 Nginx / dev server 直接托管 */
export const embeddedGameSrc = (slug) => `/mini-games/${slug}/index.html`
