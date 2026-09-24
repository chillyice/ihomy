# ihomy 壁纸(Wallpaper Engine 网页壁纸包)

把 ihomy 的壁纸页(`https://ihomy.top/wallpaper`)挂到 Wallpaper Engine 上:时钟、日期星期、天气、
家人照片(待机自动浮现、6 秒轮播)、太阳驱动的光影,可选暖居/光尘 × 晨/暮。

## 目录内容

| 文件 | 作用 |
|------|------|
| `index.html` | 壁纸入口(导入时拖这个文件)。本地过渡时钟 + 立刻**顶层跳转到线上 `/wallpaper`**(不用 iframe,原因见下) |
| `project.json` | WE 项目配置:`type=web` / `file=index.html` / `title` / `preview` + 四个用户属性(主题、晨暮、语言、壁纸令牌) |
| `preview.jpg` | 缩略图(WE 只会请求 128×128)。不喜欢可在编辑器里用 **Take snapshot** 覆盖 |
| `make-preview.py` | 生成 `preview.jpg` 的脚本(需 Pillow)。重跑 `python make-preview.py` 即可复现缩略图 |

**本目录不含 ihomy 前端构建产物**,壁纸内容是线上页面,ihomy 改版后壁纸自动跟着更新,无需重新打包。
代价是断网时只剩本地那个过渡时钟。前提:壁纸页已发布 —— 未发布时线上路由会兜底显示 ihomy 首页
(墙上会出现普通 ihomy 主页,不是壁纸页)。

## ⚠ 为什么不用 iframe(重要,别改回去)

最初这版壳页是一个指向线上 `/wallpaper` 的**全屏 iframe**,导入后编辑器预览区是**一整片纯灰**、什么都看不见。
实测(WE 2.8.42 / CEF Chromium 146)定位到:

- WE 的 CEF 在渲染**跨域 iframe**(OOPIF)时**让渲染进程崩溃** —— `chrome_debug.log` 里是
  `FATAL ... cef_scoped_refptr.h:329 Check failed: ptr_.`,并伴随 `blink.mojom.FrameWidgetHost` 消息被拒。
- 崩溃前页面其实**已经加载并执行**:CEF 缓存里有入口 chunk 与壁纸页 chunk(`index-*.js` / `Wallpaper-*.js`),
  localStorage 里写入了 `ihomy-theme`。所以不是网络不通、不是脚本报错,而是主 frame 崩了 → 整块预览变灰。
- Steam 讨论区 2026-08 有同样现象的报告("IFrame in Web Wallpaper Doesn't Load",控制台无任何报错)。

**顶层跳转不产生 OOPIF,因此绕开该崩溃。** 这也是 Wallpaper Engine 官方文档的取向:它建议把所有文件
打包在本地、不要从网上加载关键文件 —— 只是本包选择了「壁纸内容就是线上页面,改版自动跟随」这条路线。

## 导入步骤

1. Wallpaper Engine → 右下角 **创建壁纸 / Create Wallpaper**。
2. 把本目录的 `index.html` **拖进去**(WE 会把该文件所在目录连同子目录一起复制到
   `wallpaper_engine\projects\myprojects\`,并保留目录内的 `project.json`)。注意 WE 会读取目录下
   **所有**文件,别把无关文件堆在同一层。
3. 编辑器里预览 → 满意后 **Take snapshot** 生成更贴合的缩略图 → 保存并「应用」。

## ⚠ 改完本包后,怎么让已导入的壁纸生效(WE 是「拷贝」导入)

导入时 WE 把 `index.html` / `project.json` **复制**到
`wallpaper_engine\projects\myprojects\<项目名>\`(本机实测为 `ihomy1`),之后一直读那份拷贝 ——
所以改完仓库里的 `wallpaper-engine/`,**已导入的壁纸不会自己更新**。两种更新方式:

| 方式 | 做法 | 代价 |
|------|------|------|
| 就地覆盖(推荐) | 把新的 `index.html` 覆盖到 `...\myprojects\<项目名>\index.html`,再 文件 → 重启预览 | **project.json 千万别覆盖** —— 属性面板里的值(壁纸令牌)存在那儿,覆盖了就丢了 |
| 重新导入 | 再拖一次 `index.html` | WE 新建一个项目(`ihomy2`),**令牌要重粘一次** |

核对手上那份是不是新版:`index.html` 里搜 `SETTLE_MS`(V9.90 起)或 `setTimeout(go, 1200)`(V9.89 及更早)。

## 属性面板(主题 / 晨暮 / 语言 / 壁纸令牌)

WE 只在**页面加载时**投递用户属性,而且是**分次**投递的:第一次只带 `project.json` 里声明的值,
用户在面板里**改过**的值(壁纸令牌默认是空串,粘过就一定有值 → 必然算「改过」)会**晚一拍**到。
壳页因此不立刻跳转:**拿到令牌再等 400ms 就走,还没拿到就等到 3s 硬上限**(令牌投递越晚,越不能提前跳)。
V9.90 修的正是这一点 —— **别再改回「第一次回调就跳」**:那样令牌会被整个丢掉,现象是「粘了令牌也不登录」。
不填令牌的用户每次开屏会多看约 3s 过渡时钟(壳页那个本地时钟),这是为了不漏掉令牌特意付的代价。

壳页收到属性后这样带走,由壁纸页读取生效:

| 属性 | 怎么带过去 | 作用 |
|------|-----------|------|
| 主题 `theme` | 查询参数 `?theme=warm` | 暖居 / 光尘 |
| 晨暮 `mode` | 查询参数 `?mode=dawn`(默认 `auto` 不发送,由太阳高度角驱动) | 晨 / 暮 |
| 语言 `lang` | 查询参数 `?lang=zh`(默认 `zh`;选「跟随系统」不发送) | WE 的 CEF 是 en-us,不指定的话壁纸会走英文 |
| 壁纸令牌 `token` | **URL hash** `#token=...` | 登录用,详见下节 |

令牌刻意走 hash 而不是查询参数:**hash 不会发给服务器**,不会落进 nginx 访问日志(令牌等同登录凭证)。

**双保险**:除了 hash,壁纸页自己也挂了 `wallpaperPropertyListener`。万一 WE 把令牌投得比壳页跳转还晚,
页面上会当场用它换会话(日志里能看到「晚到令牌换会话:成功」)——所以两条路任一命中都能登录。

由此的**限制**:页面起来之后再改属性面板**不会实时生效** —— 改完请用 文件 → 重启预览。
鼠标能用的话,壁纸页右下角也有主题 / 晨暮 / 中英切换按钮,可当场改。

## 登录:壁纸令牌

桌面壁纸拿不到键盘,登录卡填不了邮箱密码和验证码,所以壁纸改用**令牌**登录:

1. 普通浏览器打开 ihomy → 登录 → **个人设置 → 个性化设置 → 「壁纸氛围屏」卡片 →「复制壁纸令牌」**
   (打开 `/wallpaper` 页登录后,右下角也有同一个复制按钮)。
2. 粘到 WE 属性面板的**「壁纸令牌」**栏 → 文件 → **重启预览**。
3. 壁纸页拿这个 refresh token 调 `/auth/refresh` 换正式会话,并落进 **WE 自己的** localStorage;
   之后靠滑动续期自动保活(连续 7 天不开机才会掉)。

几点要注意:

- **令牌与浏览器会话互不干扰**:后端 refresh 不拉黑旧 refresh token(可重复使用),浏览器和壁纸两条链
  各自滑动续期,不会互相踢下线。
- **属性面板里那份是「种子」**:壁纸首次刷新后 token 就在 WE 自己那边轮换了,面板里的旧值不再使用
  (壁纸页只在本地没有会话时才采纳面板值,不会用旧值覆盖有效登录态)。令牌过期 / 改密码后,
  回设置页重新复制一次即可。
- **不登录也能跑**:壁纸照常显示时钟、天气、光影,只是不显示家人照片。

## 需要你自己实测/知悉的几件事

1. **鼠标输入**:Wallpaper Engine 网页壁纸的官方文档完全没有提到鼠标/键盘输入,桌面壁纸能否点击未知。
   若点不动,右下角的主题按钮就用不了 —— 用属性面板换主题即可(改完重启预览)。壁纸本身不依赖鼠标:
   加载后 3 秒照片自动浮现、时钟天气照常。
2. **联网**:实测可以加载远程内容(CEF 缓存里能看到抓取的页面与 chunk),官方只是**不建议**依赖网络
   (离线会坏)。断网时壳页只剩本地过渡时钟。
3. **登录态**:WE 的 CEF 是独立环境,有自己的 localStorage/cookie(实测壁纸页写入的 `ihomy-theme`
   确实落到了 WE 的 CEF 存储里),与你浏览器里的 ihomy 登录互不影响 —— 故登录要在属性面板填令牌
   (见上节)。不填令牌时壁纸只显示时钟/天气/光影。

**调试手段**(官方文档):WE 设置 → 常规 → **CEF devtools**,可像 Chrome DevTools 一样看壁纸页的
console / 网络 / 存储。壁纸进程的 CEF 控制台与崩溃日志在
`<WE 安装目录>\ui\wpcache\monitor<N>\base\chrome_debug.log`(**哪个 monitor 就看哪个** —— 本机壁纸
实例用的是 `monitor100`,别只盯 `monitor0`;壳页与壁纸页的 `console.log` 都会以 `INFO:CONSOLE` 落在这里,
自带的诊断行有 `[ihomy-shell]` / `[ihomy-wallpaper]` 前缀),WE 自身的操作日志在
`<WE 安装目录>\bin\wallpaperuilog.txt`。属性面板里存的值在 `<WE 安装目录>\config.json` 的
`wproperties` 下,按「壁纸文件路径 → 显示器」分组(`token` 就在那里,只记长度、别往外贴)。

## 其他已知行为

- **多显示器**:每个显示器一个实例,各自独立;页面用 `vw/vh` 自适应。
- **暂停**:WE 暂停时会**完全冻结**渲染进程,定时器不跑,恢复后继续 —— 暂停期间照片不轮播属正常。
- **登录**:登录态存在壁纸自己的 CEF 里(令牌换来的会话),不影响你浏览器里的 ihomy 登录。

## 打包(投递/备份用)

WE 导入靠拖 `index.html`,**直接用本目录即可**,不必打包。要给别人或留档时才 zip 一份,
在仓库根目录执行(用 Windows 自带 bsdtar):

```powershell
tar -a -c -f wallpaper-engine.zip wallpaper-engine
```

**别用 PowerShell 5.1 的 `Compress-Archive`**:它写出的条目用反斜杠分隔
(`wallpaper-engine\index.html`),不符合 ZIP 规范(应正斜杠),非 Windows 解压工具会把整条路径
当成一个文件名。bsdtar 的输出是规范的。

压缩包里带 `wallpaper-engine/` 顶层目录,解压后拖里面的 `index.html` 即可。
**zip 不要放在本目录内** —— WE 会读取目录下所有文件,把压缩包也一起复制进壁纸工程。
