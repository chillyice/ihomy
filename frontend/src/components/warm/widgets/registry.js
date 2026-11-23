// 暖居首页组件注册表 —— 唯一真相源(别再往 WarmHome.vue 的模板里加 v-if 分支)。
//
// 一个功能(导航模块 code)可以挂**多种样式**,每条定义都是一个能直接加到首页的组件:
//   id        布局身份,写进 localStorage / sys_user_pref(home.warm.v3);新样式命名 '<默认 id>:<style>'
//   module    navModules 的模块 code(侧栏拖入、「已在首页」标记用;weather/daily 没有独立模块)
//   style     样式 key(样式菜单里选中的那一项)
//   labelKey  组件名(复用 warm.widget.*)
//   styleKey  样式名(同一功能有多个样式时展示在托盘与样式菜单里)
//   icon      托盘与紧凑图标档的 emoji
//   span/row  拖入/添加时的默认尺寸(列宽 1/2/4/6/8/12,行高 1/2/3/4/6)
//   chip      卡片色相锚点(--blob-N:1 陶土 / 2 暖沙 / 3 鼠尾草 / 4 暖米 / 5 暖木)
//   component 样式组件(SFC);默认样式留空 —— 仍在 WarmHome 模板里按原样渲染,后续可逐个迁过来
//   props     (ctx) => 组件 props;ctx 由 WarmHome 在渲染时提供
//   dataKey   加载/错误占位对应的数据源键(默认取所属功能的默认样式 id;换了数据源的样式才写,如 finance:trend → ledger)
//
// 加一种样式的步骤:
//   ① components/warm/widgets/ 下写 XxxYyy.vue(接收自己的 props,自带 scoped 样式)
//   ② 本表加一条(带 component + props;id 用 '<默认 id>:<style>')
//   ③ i18n 补 styleKey(中英各一条)
// 不用改 WarmHome 的模板分支:带 component 的条目走统一 <component :is> 出口。
import FeedCards from './FeedCards.vue'
import FinanceTrend from './FinanceTrend.vue'
import AnniCards from './AnniCards.vue'
import PhotoCarousel from './PhotoCarousel.vue'

export const WIDGETS = [
  {
    id: 'weather',
    module: 'weather',
    style: 'window',
    labelKey: 'warm.widget.weather',
    icon: '🌤',
    span: 8,
    row: 4,
    chip: 1,
  },
  {
    id: 'feed',
    module: 'blog',
    style: 'list',
    labelKey: 'warm.widget.feed',
    styleKey: 'warm.style.feedList',
    icon: '👥',
    span: 4,
    row: 4,
    chip: 3,
  },
  {
    id: 'feed:cards',
    module: 'blog',
    style: 'cards',
    labelKey: 'warm.widget.feed',
    styleKey: 'warm.style.feedCards',
    icon: '👥',
    span: 8,
    row: 3,
    chip: 3,
    component: FeedCards,
    props: (ctx) => ({ feeds: ctx.feeds, editMode: ctx.editMode }),
  },
  {
    id: 'photos',
    module: 'album',
    style: 'stack',
    labelKey: 'warm.widget.photos',
    styleKey: 'warm.style.photoStack',
    icon: '📷',
    span: 4,
    row: 2,
    chip: 2,
  },
  {
    id: 'photos:carousel',
    module: 'album',
    style: 'carousel',
    labelKey: 'warm.widget.photos',
    styleKey: 'warm.style.photoCarousel',
    icon: '📷',
    span: 8,
    row: 3,
    chip: 2,
    component: PhotoCarousel,
    props: (ctx) => ({ photos: ctx.photos, editMode: ctx.editMode }),
  },
  {
    id: 'anni',
    module: 'anniversary',
    style: 'countdown',
    labelKey: 'warm.widget.anni',
    styleKey: 'warm.style.anniCountdown',
    icon: '🎂',
    span: 4,
    row: 2,
    chip: 1,
  },
  {
    id: 'anni:cards',
    module: 'anniversary',
    style: 'cards',
    labelKey: 'warm.widget.anni',
    styleKey: 'warm.style.anniCards',
    icon: '🎂',
    span: 6,
    row: 3,
    chip: 1,
    component: AnniCards,
    props: (ctx) => ({ list: ctx.anni, editMode: ctx.editMode }),
  },
  {
    id: 'finance',
    module: 'book',
    style: 'overview',
    labelKey: 'warm.widget.finance',
    styleKey: 'warm.style.finOverview',
    icon: '💰',
    span: 4,
    row: 2,
    chip: 3,
  },
  {
    id: 'finance:trend',
    module: 'book',
    style: 'trend',
    labelKey: 'warm.widget.finance',
    styleKey: 'warm.style.finTrend',
    icon: '💰',
    span: 6,
    row: 3,
    chip: 3,
    // 数据源换成 ledger(WarmHome 的 cardData 键);不写就默认取 base='finance' 的 summary
    dataKey: 'ledger',
    component: FinanceTrend,
    props: (ctx) => ({ rows: ctx.trendRows }),
  },
  {
    id: 'item',
    module: 'item',
    style: 'floorplan',
    labelKey: 'warm.widget.item',
    icon: '📦',
    span: 4,
    row: 2,
    chip: 5,
  },
  {
    id: 'task',
    module: 'task',
    style: 'list',
    labelKey: 'warm.widget.task',
    icon: '🎯',
    span: 4,
    row: 2,
    chip: 5,
  },
  {
    id: 'wish',
    module: 'wish',
    style: 'list',
    labelKey: 'warm.widget.wish',
    icon: '⭐',
    span: 4,
    row: 2,
    chip: 3,
  },
  {
    id: 'reminder',
    module: 'reminder',
    style: 'list',
    labelKey: 'warm.widget.reminder',
    icon: '🔔',
    span: 4,
    row: 2,
    chip: 1,
  },
  {
    id: 'daily',
    module: null,
    style: 'daily',
    labelKey: 'warm.widget.daily',
    icon: '🌅',
    span: 4,
    row: 3,
    chip: 2,
  },
]

export const WIDGET_BY_ID = Object.fromEntries(WIDGETS.map((w) => [w.id, w]))
export const widgetById = (id) => WIDGET_BY_ID[id] || null

// 模块 → 默认组件 id(侧栏拖入、快捷入口预览用);同模块第一条即默认样式
export const MODULE_DEFAULT_WIDGET = WIDGETS.reduce((map, w) => {
  if (w.module && !map[w.module]) map[w.module] = w.id
  return map
}, {})

// 组件 id → 所属模块(首页「已在首页」标记、访客可见性按模块判定)
export const WIDGET_MODULE = WIDGETS.reduce((map, w) => {
  map[w.id] = w.module || null
  return map
}, {})

// 同功能的其它样式(样式菜单用);无模块(如 daily)天然没有兄弟样式
export const siblingsOf = (id) => {
  const def = widgetById(id)
  if (!def?.module) return []
  return WIDGETS.filter((w) => w.module === def.module && w.id !== id)
}
