// 路由表 + 登录守卫:默认所有页面游客可浏览,仅纯写/个人页需登录
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  // 密码找回:无 token=请求发送邮件;带 ?token=... =凭邮件链接设置新密码。公开页,不套登录守卫
  { path: '/reset-password', name: 'ResetPassword', component: () => import('@/views/ResetPassword.vue'), meta: { public: true } },
  // 首页:/ 恒重定向到模块化首页 /home(暖居/光尘共用同一首页,由主题切换系统决定外壳)
  { path: '/', redirect: '/home' },
  { path: '/home', name: 'Home', component: () => import('@/views/Home.vue'), meta: { public: true } },
  { path: '/blog', name: 'BlogList', component: () => import('@/views/blog/BlogList.vue'), meta: { public: true } },
  { path: '/blog/:id', name: 'BlogDetail', component: () => import('@/views/blog/BlogDetail.vue'), meta: { public: true } },
  { path: '/blog/edit/:id?', name: 'BlogEdit', component: () => import('@/views/blog/BlogEdit.vue'), meta: { requiresAuth: true } },
  { path: '/diary', name: 'DiaryList', component: () => import('@/views/diary/DiaryList.vue'), meta: { public: true } },
  { path: '/diary/book/:authorId', name: 'DiaryBook', component: () => import('@/views/diary/DiaryBook.vue'), meta: { public: true } },
  { path: '/diary/edit/:id?', name: 'DiaryEdit', component: () => import('@/views/diary/DiaryEdit.vue'), meta: { requiresAuth: true } },
  { path: '/anniversary', name: 'Anniversary', component: () => import('@/views/Anniversary.vue'), meta: { public: true } },
  { path: '/announcement', name: 'Announcement', component: () => import('@/views/Announcement.vue'), meta: { public: true } },
  // 回收站:照片/相册/视频/图书的逻辑删内容恢复与彻底删除,家庭内部数据须登录
  { path: '/recycle', name: 'Recycle', component: () => import('@/views/Recycle.vue'), meta: { requiresAuth: true } },
  { path: '/album', name: 'Album', component: () => import('@/views/album/Album.vue'), meta: { public: true } },
  { path: '/album/shared/:token', name: 'AlbumShared', component: () => import('@/views/album/AlbumDetail.vue'), meta: { public: true } },
  { path: '/album/:id', name: 'AlbumDetail', component: () => import('@/views/album/AlbumDetail.vue'), meta: { public: true } },
  { path: '/cinema', name: 'Cinema', component: () => import('@/views/cinema/Cinema.vue'), meta: { public: true } },
  { path: '/cinema/:itemId', name: 'CinemaDetail', component: () => import('@/views/cinema/CinemaDetail.vue'), meta: { public: true } },
  { path: '/music', name: 'Music', component: () => import('@/views/music/Music.vue'), meta: { public: true } },
  { path: '/member', name: 'Member', component: () => import('@/views/Member.vue'), meta: { requiresAuth: true } },
  { path: '/points', name: 'Points', component: () => import('@/views/points/Points.vue'), meta: { public: true } },
  { path: '/task', name: 'Task', component: () => import('@/views/task/Task.vue'), meta: { public: true } },
  { path: '/reminder', name: 'Reminder', component: () => import('@/views/reminder/Reminder.vue'), meta: { public: true } },
  { path: '/plan', name: 'Plan', component: () => import('@/views/plan/Plan.vue'), meta: { public: true } },
  { path: '/plant', redirect: '/tools/plant' },
  { path: '/wish', name: 'Wish', component: () => import('@/views/wish/Wish.vue'), meta: { public: true } },
  { path: '/book', name: 'Book', component: () => import('@/views/book/Book.vue'), meta: { public: true } },
  // 智能家居中控(Home Assistant 接入):设备/历史/控制属家庭内部数据,要登录
  { path: '/iot', name: 'Iot', component: () => import('@/views/iot/Iot.vue'), meta: { requiresAuth: true } },
  // 家庭保险箱(密码管理器):家庭共享的账号密码保管箱,须登录
  { path: '/vault', name: 'Vault', component: () => import('@/views/vault/Vault.vue'), meta: { requiresAuth: true } },
  { path: '/tree', name: 'Tree', component: () => import('@/views/tree/Tree.vue'), meta: { public: true } },
  { path: '/weather', name: 'Weather', component: () => import('@/views/weather/Weather.vue'), meta: { public: true } },
  // 照片瀑布已并入相册(相册页内视图):保留旧链接/书签,重定向到相册并自动打开瀑布
  { path: '/cascade', redirect: { path: '/album', query: { cascade: '1' } } },
  { path: '/chat', name: 'Chat', component: () => import('@/views/chat/Chat.vue'), meta: { requiresAuth: true } },
  { path: '/settings', name: 'Settings', component: () => import('@/views/Settings.vue'), meta: { requiresAuth: true } },
  // 百度网盘 OAuth 授权回调页(须与百度开放平台注册的「授权回调页地址」一致)
  {
    path: '/storage/baidu/callback',
    name: 'BaiduCallback',
    component: () => import('@/views/storage/BaiduCallback.vue'),
    meta: { public: true },
  },
  // 文件浏览:独立功能页,登录用户浏览/预览/下载存储设备文件(管理操作仅 OWNER)
  { path: '/storage/files', name: 'FileBrowse', component: () => import('@/views/storage/FileBrowse.vue'), meta: { requiresAuth: true } },
  { path: '/item', name: 'Item', component: () => import('@/views/item/Item.vue'), meta: { public: true } },
  { path: '/kitchen', name: 'Kitchen', component: () => import('@/views/kitchen/Kitchen.vue'), meta: { public: true } },
  { path: '/kitchen/ingredients', name: 'Ingredient', component: () => import('@/views/kitchen/Ingredient.vue'), meta: { public: true } },
  {
    path: '/kitchen/recipe/:id',
    name: 'RecipeDetail',
    component: () => import('@/views/kitchen/RecipeDetail.vue'),
    meta: { public: true },
  },
  {
    path: '/kitchen/recipe/new',
    name: 'RecipeNew',
    component: () => import('@/views/kitchen/RecipeEdit.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/kitchen/recipe/:id/edit',
    name: 'RecipeEdit',
    component: () => import('@/views/kitchen/RecipeEdit.vue'),
    meta: { requiresAuth: true },
  },
  { path: '/library', name: 'LibraryList', component: () => import('@/views/library/LibraryList.vue'), meta: { public: true } },
  {
    path: '/library/edit/:id?',
    name: 'LibraryEdit',
    component: () => import('@/views/library/LibraryEdit.vue'),
    meta: { requiresAuth: true },
  },
  // 工具箱:功能聚合页 + 脑图设计(列表游客可见,编辑器需登录)
  { path: '/tools', name: 'Tools', component: () => import('@/views/tools/Tools.vue'), meta: { public: true } },
  { path: '/tools/mindmap', name: 'MindMapList', component: () => import('@/views/tools/MindMap.vue'), meta: { public: true } },
  {
    path: '/tools/mindmap/:id',
    name: 'MindMapEditor',
    component: () => import('@/views/tools/MindMapEditor.vue'),
    meta: { requiresAuth: true },
  },
  // AI 测试台(临时):对话/图片/语音输入输出调试,依赖 /ai/** 登录接口
  {
    path: '/tools/ai-playground',
    name: 'AiPlayground',
    component: () => import('@/views/tools/AiPlayground.vue'),
    meta: { requiresAuth: true },
  },
  // 3D 光影实验台(临时):Three.js 太阳模拟+真实阴影,未来场景主题的 3D 基础模型
  {
    path: '/tools/light-lab',
    name: 'LightLab',
    component: () => import('@/views/tools/LightLab.vue'),
    meta: { public: true, immersive: true },
  },
  // PDF 转换:PDF↔图片、PDF 提取文字,全部在本机浏览器完成(不上传服务器)
  { path: '/tools/pdf', name: 'PdfConverter', component: () => import('@/views/tools/PdfConverter.vue'), meta: { public: true } },
  // 开发者工具(JSON/编码/进制/报文/子网/哈希/随机/时间/正则/文本/运维/JWT)只在比特口袋在线工具
  // (/bit-pouch/tools)里维护;ihomy 的旧地址保留重定向,老书签与外部链接照常可用。
  { path: '/tools/json', redirect: '/bit-pouch/tools/json' },
  { path: '/tools/codec', redirect: '/bit-pouch/tools/codec' },
  { path: '/tools/base', redirect: '/bit-pouch/tools/base' },
  { path: '/tools/packet', redirect: '/bit-pouch/tools/packet' },
  { path: '/tools/subnet', redirect: '/bit-pouch/tools/subnet' },
  { path: '/tools/hash', redirect: '/bit-pouch/tools/hash' },
  { path: '/tools/generator', redirect: '/bit-pouch/tools/generator' },
  { path: '/tools/time', redirect: '/bit-pouch/tools/time' },
  { path: '/tools/regex', redirect: '/bit-pouch/tools/regex' },
  { path: '/tools/text', redirect: '/bit-pouch/tools/text' },
  { path: '/tools/ops', redirect: '/bit-pouch/tools/ops' },
  { path: '/tools/jwt', redirect: '/bit-pouch/tools/jwt' },
  // 图片工具:压缩 / ICO 打包 / EXIF 查看,纯前端 Canvas
  {
    path: '/tools/image',
    name: 'ImageTool',
    component: () => import('@/views/tools/ImageTool.vue'),
    meta: { public: true },
  },
  // 文件工具:类型识别 / ZIP 打包 / ZIP 解压,本地处理
  {
    path: '/tools/file',
    name: 'FileTool',
    component: () => import('@/views/tools/FileTool.vue'),
    meta: { public: true },
  },
  // 颜色与前端设计:格式互转/WCAG 对比度/配色/CSS 片段
  {
    path: '/tools/color',
    name: 'ColorTool',
    component: () => import('@/views/tools/ColorTool.vue'),
    meta: { public: true },
  },
  // 单位换算:长度/面积/体积/重量/温度/速度/压力/数据量/时间
  {
    path: '/tools/unit',
    name: 'UnitTool',
    component: () => import('@/views/tools/UnitTool.vue'),
    meta: { public: true },
  },
  // Markdown 阅读器:本地阅读 .md 文件，支持标题 / 代码块 / 列表 / 链接等语法
  { path: '/tools/markdown', name: 'MarkdownReader', component: () => import('@/views/tools/MarkdownReader.vue'), meta: { public: true } },
  // 旧的比特口袋在线工具页:工具已搬进 bit_pouch 自己的页面(/bit-pouch/tools),旧链接保持可用
  { path: '/tools/bit-pouch', redirect: '/bit-pouch/tools' },
  // 贷款计算器:纯前端试算(等额本息/等额本金、商业/公积金/组合贷、提前还款对比),不依赖后端
  { path: '/tools/loan', name: 'LoanCalculator', component: () => import('@/views/tools/LoanCalculator.vue'), meta: { public: true } },
  // Flash 播放器(Ruffle):本地加载 .swf 怀旧小游戏
  { path: '/tools/flash', name: 'FlashPlayer', component: () => import('@/views/tools/FlashPlayer.vue'), meta: { public: true } },
  // GBA 播放器(EmulatorJS):本地加载 .gba/.gbc/.gb 复古游戏
  { path: '/tools/gba', name: 'GbaPlayer', component: () => import('@/views/games/GbaPlayer.vue'), meta: { public: true } },
  // 植物养殖小游戏:全家共养一棵,营养/天气/积分/成长日志(需登录,涉及积分与家庭交互)
  { path: '/tools/plant', name: 'Plant', component: () => import('@/views/plant/Plant.vue'), meta: { requiresAuth: true } },
  // 小游戏:家庭游戏库(花园 + 导入的 swf 游戏),需登录
  { path: '/games', name: 'Games', component: () => import('@/views/games/Games.vue'), meta: { requiresAuth: true } },
  { path: '/games/play/:id', name: 'GamePlayer', component: () => import('@/views/games/GamePlayer.vue'), meta: { requiresAuth: true } },
  // 宠物连连看 H5(自 Flash 移植的原生小游戏,通关发积分),需登录
  { path: '/games/petlink', name: 'PetLinkLink', component: () => import('@/views/games/PetLinkLink.vue'), meta: { requiresAuth: true } },
  // 运维管理页:仅 OPS 角色可访问
  { path: '/ops', name: 'Ops', component: () => import('@/views/ops/Ops.vue'), meta: { ops: true } },
  // 咔哒软件首页:独立下载页(托管在 ihomy 域名下),standalone 不套 ihomy 外壳
  { path: '/kada', name: 'Kada', component: () => import('@/views/Kada.vue'), meta: { public: true, standalone: true } },
  // bit_pouch 在线工具:属于 bit_pouch 自己的页面,standalone 不套 ihomy 外壳;目录 + 单工具两条路由
  {
    path: '/bit-pouch/tools',
    name: 'BitPouchTools',
    component: () => import('@/views/bitpouch/BitPouchTools.vue'),
    meta: { public: true, standalone: true },
  },
  // 在线目录与 bit_pouch 打包工具对齐后的旧链接:合并 / 改名的工具保持可达
  { path: '/bit-pouch/tools/json', redirect: '/bit-pouch/tools/format' },
  { path: '/bit-pouch/tools/markup', redirect: '/bit-pouch/tools/format' },
  { path: '/bit-pouch/tools/time', redirect: '/bit-pouch/tools/timestamp' },
  { path: '/bit-pouch/tools/markdown', redirect: '/bit-pouch/tools' },
  {
    path: '/bit-pouch/tools/:id',
    name: 'BitPouchTool',
    component: () => import('@/views/bitpouch/BitPouchTool.vue'),
    meta: { public: true, standalone: true },
  },
  // bit_pouch 发布页:独立下载页,品牌写法带下划线(与产品名一致),两种写法都可达
  {
    path: '/bit-pouch',
    alias: '/bit_pouch',
    name: 'BitPouch',
    component: () => import('@/views/BitPouch.vue'),
    meta: { public: true, standalone: true },
  },
  // 壁纸页:家庭私有只读氛围屏(桌面壁纸/常开副屏用),登录后只展示照片/光影/天气;standalone 不套外壳
  { path: '/wallpaper', name: 'Wallpaper', component: () => import('@/views/Wallpaper.vue'), meta: { public: true, standalone: true } },
  // chillyice 个人主页:ihomy 域名下的独立页(ihomy.top/chillyice),全公开、登录与否都可访问
  { path: '/chillyice', name: 'Chillyice', component: () => import('@/views/Chillyice.vue'), meta: { public: true, standalone: true } },
  // 使用帮助:给家人看的纯文案页,不需要登录
  { path: '/help', name: 'Help', component: () => import('@/views/Help.vue'), meta: { public: true } },
  // 兜底:未匹配的路由重定向回首页
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫:仅 requiresAuth 页面拦截;OPS 专属页仅运维角色放行
router.beforeEach((to) => {
  const userStore = useUserStore()
  // 需登录的页面(写操作/个人设置)才拦截
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  // 纯 OPS 账号(无家庭角色)只能访问运维页
  if (userStore.isPureOps && to.name !== 'Ops') {
    return { name: 'Ops' }
  }
  // 运维页要求 OPS 或家长(OWNER)身份;家长仅看 AI 统计/天气,其余由页面内角色渲染控制
  if (to.meta.ops && !(userStore.isOps || userStore.isOwner)) {
    return { name: 'Home' }
  }
})

// 懒加载 chunk 失败自愈:重新构建后旧标签页仍引用旧哈希文件(404),
// 整页刷新一次拿到新构建;同一路由 30 秒内只刷一次,真实网络故障时不至于无限刷新
router.onError((error, to) => {
  const msg = (error?.message || '').toLowerCase()
  const chunkFailed =
    msg.includes('dynamically imported module') || msg.includes('importing a module script') || msg.includes('loading chunk')
  if (!chunkFailed || !to) return
  const key = `ihomy:chunk-reload:${to.fullPath}`
  if (Date.now() - Number(sessionStorage.getItem(key) || 0) < 30_000) return
  sessionStorage.setItem(key, String(Date.now()))
  window.location.href = to.fullPath
})

export default router
