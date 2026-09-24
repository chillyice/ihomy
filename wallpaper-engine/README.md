# ihomy 壁纸(Wallpaper Engine 网页壁纸包)

把 ihomy 的壁纸页(`https://ihomy.top/wallpaper`)挂到 Wallpaper Engine 上:时钟、日期星期、天气、
家人照片(待机自动浮现、6 秒轮播)、太阳驱动的光影,可选暖居/光尘 × 晨/暮。

## 目录内容

| 文件 | 作用 |
|------|------|
| `index.html` | 壁纸入口(导入时拖这个文件)。一个全屏 iframe 指向线上 `/wallpaper` + WE 属性桥 |
| `project.json` | WE 项目配置:`type=web` / `file=index.html` / `title` / `preview` + 两个用户属性(主题、晨暮) |
| `preview.jpg` | 缩略图(WE 只会请求 128×128)。不喜欢可在编辑器里用 **Take snapshot** 覆盖 |
| `make-preview.py` | 生成 `preview.jpg` 的脚本(需 Pillow)。重跑 `python make-preview.py` 即可复现缩略图 |

**本目录不含 ihomy 前端构建产物**,壁纸内容是线上页面,ihomy 改版后壁纸自动跟着更新,无需重新打包。
代价是断网时壁纸为空白。前提:壁纸页已发布 —— 未发布时线上路由会兜底显示 ihomy 首页
(墙上会出现普通 ihomy 主页,不是壁纸页)。

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

## 导入步骤

1. Wallpaper Engine → 右下角 **创建壁纸 / Create Wallpaper**。
2. 把本目录的 `index.html` **拖进去**(WE 会把该文件所在目录连同子目录一起复制到
   `wallpaper_engine\projects\myprojects\`,并自动生成/接管 `project.json`)。注意 WE 会读取目录下
   **所有**文件,别把无关文件堆在同一层。
3. 编辑器里预览 → 满意后 **Take snapshot** 生成更贴合的缩略图 → 保存并「应用」。
4. 在已安装列表里右键壁纸 → **属性 / Properties** 面板里可切「主题」「晨暮」(经 postMessage 生效)。

## 需要你自己实测的三件事(官方文档未覆盖)

1. **鼠标输入**:Wallpaper Engine 的网页壁纸官方文档完全没有提到鼠标/键盘输入,桌面壁纸能否点击
   未知。若点不动,页面右下角的主题按钮就用不了 —— 用属性面板换主题即可(壁纸页本身不依赖鼠标:
   加载后 3 秒照片自动浮现、时钟天气照常)。
2. **联网**:官方只建议「尽量不要从网上加载关键文件」(离线会坏),没有说远程 iframe/请求被禁。
   若壁纸空白,先用下面的调试手段看控制台报错。
3. **登录态是否持久**:WE 的 CEF 是独立环境,`localStorage` 跨重启是否保留官方无文档。不保留的话
   每次开机看到登录卡(10 秒登完,前提是鼠标能交互)。

**调试手段**(官方文档):WE 设置 → 常规 → **CEF devtools 端口**(填 8080)→ 浏览器打开
`http://localhost:8080`,即可像 Chrome DevTools 一样看壁纸页的 console / 网络 / 存储。

## 其他已知行为

- **多显示器**:每个显示器一个实例,各自独立;页面用 `vw/vh` 自适应。
- **暂停**:WE 暂停时会**完全冻结**渲染进程,定时器不跑,恢复后继续 —— 暂停期间照片不轮播属正常。
- **登录**:登录态存在壁纸自己的 CEF 里,不影响你浏览器里的 ihomy 登录。

## 不想用属性面板?

把 `index.html` 里的 `<iframe>` 换成 `location.replace('https://ihomy.top/wallpaper')`,少一层嵌套,
但 WE 属性面板会失效(WE 注入的 API 只在它加载的顶层页面上)。
