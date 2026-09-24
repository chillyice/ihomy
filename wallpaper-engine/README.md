# ihomy 壁纸(Wallpaper Engine 网页壁纸包)

把 ihomy 的壁纸页(`https://ihomy.top/wallpaper`)挂到 Wallpaper Engine 上:时钟、日期星期、天气、
家人照片(待机自动浮现、6 秒轮播)、太阳驱动的光影,可选暖居/光尘 × 晨/暮。

## 目录内容

| 文件 | 作用 |
|------|------|
| `index.html` | 壁纸入口(导入时拖这个文件)。本地过渡时钟 + 立刻**顶层跳转到线上 `/wallpaper`**(不用 iframe,原因见下) |
| `project.json` | WE 项目配置:`type=web` / `file=index.html` / `title` / `preview` + 两个用户属性(主题、晨暮) |
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

## 属性面板(主题 / 晨暮)

WE 会在**页面加载之初一次性**投递用户属性,壳页收到后把它们作为查询参数一起跳转
(`/wallpaper?theme=warm&mode=dawn`),由壁纸页读取生效。

由此的**限制**:页面起来之后再改属性面板**不会实时生效** —— 改完请用 文件 → 重启预览,
或者用页面右下角的主题按钮(前提是鼠标能用,见下)。

## 需要你自己实测/知悉的几件事

1. **鼠标输入**:Wallpaper Engine 网页壁纸的官方文档完全没有提到鼠标/键盘输入,桌面壁纸能否点击未知。
   若点不动,右下角的主题按钮就用不了 —— 用属性面板换主题即可(改完重启预览)。壁纸本身不依赖鼠标:
   加载后 3 秒照片自动浮现、时钟天气照常。
2. **联网**:实测可以加载远程内容(CEF 缓存里能看到抓取的页面与 chunk),官方只是**不建议**依赖网络
   (离线会坏)。断网时壳页只剩本地过渡时钟。
3. **登录态**:WE 的 CEF 是独立环境,有自己的 localStorage/cookie(实测壁纸页写入的 `ihomy-theme`
   确实落到了 WE 的 CEF 存储里),与你浏览器里的 ihomy 登录互不影响。不登录时壁纸只显示时钟/天气/光影。

**调试手段**(官方文档):WE 设置 → 常规 → **CEF devtools**,可像 Chrome DevTools 一样看壁纸页的
console / 网络 / 存储。壁纸进程的 CEF 崩溃日志在
`<WE 安装目录>\ui\wpcache\monitor0\base\chrome_debug.log`,WE 自身的操作日志在 `<WE 安装目录>\bin\wallpaperuilog.txt`。

## 其他已知行为

- **多显示器**:每个显示器一个实例,各自独立;页面用 `vw/vh` 自适应。
- **暂停**:WE 暂停时会**完全冻结**渲染进程,定时器不跑,恢复后继续 —— 暂停期间照片不轮播属正常。
- **登录**:登录态存在壁纸自己的 CEF 里,不影响你浏览器里的 ihomy 登录。

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
