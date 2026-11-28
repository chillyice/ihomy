// 固定小游戏入口:小游戏页的分类卡片与「功能开关」的子项共用这一份清单。
// key 同时用于卡片图标(暖居/游戏图标)与功能开关 id(game:<key>),改动 key 等于换了开关身份。
import { EMBEDDED_GAMES } from './embeddedGames'

/** 固定入口(不依赖后端数据):花园/小院/连连看/数独…;path 为站内路由 */
export const BUILTIN_GAMES = [
  {
    key: 'garden',
    titleKey: 'games.garden.title',
    descKey: 'games.garden.desc',
    path: '/tools/plant',
    category: 'SIMULATION',
  },
  {
    key: 'homestead',
    titleKey: 'games.homestead.title',
    descKey: 'games.homestead.desc',
    path: '/games/homestead',
    category: 'SIMULATION',
  },
  {
    key: 'petlink',
    titleKey: 'games.petlink.title',
    descKey: 'games.petlink.desc',
    path: '/games/petlink',
    category: 'PUZZLE',
  },
  {
    key: 'sudoku',
    titleKey: 'games.sudoku.title',
    descKey: 'games.sudoku.desc',
    path: '/games/sudoku',
    category: 'PUZZLE',
  },
  {
    key: '2048',
    titleKey: 'games.g2048.title',
    descKey: 'games.g2048.desc',
    path: '/games/2048',
    category: 'PUZZLE',
  },
  {
    key: 'minesweeper',
    titleKey: 'games.minesweeper.title',
    descKey: 'games.minesweeper.desc',
    path: '/games/minesweeper',
    category: 'PUZZLE',
  },
  {
    key: 'solitaire',
    titleKey: 'games.solitaire.title',
    descKey: 'games.solitaire.desc',
    path: '/games/cards/klondike',
    category: 'CARD',
  },
  {
    key: 'spider',
    titleKey: 'games.spider.title',
    descKey: 'games.spider.desc',
    path: '/games/cards/spider',
    category: 'CARD',
  },
  {
    key: 'freecell',
    titleKey: 'games.freecell.title',
    descKey: 'games.freecell.desc',
    path: '/games/cards/freecell',
    category: 'CARD',
  },
  {
    key: 'tetris',
    titleKey: 'games.tetris.title',
    descKey: 'games.tetris.desc',
    path: '/games/tetris',
    category: 'ARCADE',
  },
  {
    key: 'snake',
    titleKey: 'games.snake.title',
    descKey: 'games.snake.desc',
    path: '/games/snake',
    category: 'ARCADE',
  },
  {
    key: 'chinese-checkers',
    titleKey: 'games.chineseCheckers.title',
    descKey: 'games.chineseCheckers.desc',
    path: '/games/chinese-checkers',
    category: 'BOARD',
  },
  {
    key: 'gomoku',
    titleKey: 'games.gomoku.title',
    descKey: 'games.gomoku.desc',
    path: '/games/gomoku',
    category: 'BOARD',
  },
  {
    key: 'eight_ball',
    titleKey: 'games.eightBall.title',
    descKey: 'games.eightBall.desc',
    path: '/games/eight-ball',
    category: 'CASUAL',
  },
  {
    key: 'mahjong',
    titleKey: 'games.mahjong.title',
    descKey: 'games.mahjong.desc',
    path: '/games/mahjong',
    category: 'CASUAL',
  },
  {
    key: 'ludo',
    titleKey: 'games.ludo.title',
    descKey: 'games.ludo.desc',
    path: '/games/ludo',
    category: 'CASUAL',
  },
  {
    key: 'doudizhu',
    titleKey: 'games.doudizhu.title',
    descKey: 'games.doudizhu.desc',
    path: '/games/doudizhu',
    category: 'CARD',
  },
  {
    key: 'mahjong3d',
    titleKey: 'games.mahjong3d.title',
    descKey: 'games.mahjong3d.desc',
    path: '/games/mahjong3d',
    category: 'CASUAL',
  },
  {
    key: 'guandan',
    titleKey: 'games.guandan.title',
    descKey: 'games.guandan.desc',
    path: '/games/guandan',
    category: 'CARD',
  },
  {
    key: 'baohuang',
    titleKey: 'games.baohuang.title',
    descKey: 'games.baohuang.desc',
    path: '/games/baohuang',
    category: 'CARD',
  },
  {
    key: 'gouji',
    titleKey: 'games.gouji.title',
    descKey: 'games.gouji.desc',
    path: '/games/gouji',
    category: 'CARD',
  },
]

/** 站内开源小游戏也按同一个卡片形状加入清单(点卡片进 /games/embed/:slug) */
export const EMBEDDED_ENTRIES = EMBEDDED_GAMES.map((g) => ({
  key: g.slug,
  titleKey: g.titleKey,
  descKey: g.descKey,
  path: `/games/embed/${g.slug}`,
  category: g.category,
}))

/** 固定入口 + 站内开源小游戏(顺序与卡片一致) */
export const GAME_ENTRIES = [...BUILTIN_GAMES, ...EMBEDDED_ENTRIES]

/** 功能开关里的子项 id:与导航模块 code 同处一张表,/games 父级之外用前缀区分 */
export const gameFeatureId = (key) => `game:${key}`
