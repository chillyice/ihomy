# ihomy 沉浸式首页 UI 设计提示词

> 本文件是当前已上线的沉浸式首页的完整 UI 设计描述,可作为提示词喂给 AI 重新生成同等视觉效果的页面。所有数值均为实装值。
> 本文按视觉层级组织,弱化版本号,着重描述实现细节与设计规范。

---

## 角色

你是一名资深前端视觉工程师,精通 Vue3 + Element Plus + GSAP + CSS 滤镜/混合模式。请根据以下规格实现一个家庭沉浸式首页。

## 目标

实现"展开的相册"式沉浸式家庭首页:用户打开页面如同翻开一本摊在桌面上的家庭相册,窗外阳光随真实太阳位置斜射进来,光柱在相册和空气中形成丁达尔效应,灰尘微粒在光路中漂浮;左侧是家庭动态流和任务,右侧是天气和纪念日倒计时,右下角散落着拍立得照片堆,左下角是光照测试控制台。整体氛围温暖、旧物、慢节奏,像一个有阳光的午后。

## 技术栈

- Vue3 (Composition API, `<script setup>`)
- Element Plus(按需)
- GSAP(入场动画 + 台灯 mask 补间)
- 原生 CSS(毛玻璃用 `backdrop-filter`,光效用 `mix-blend-mode`)
- 数据来源:`GET /api/public/sun-info`(288 时隙太阳位置,可选 `?date=` 模拟任意日期)、`GET /api/public/weather`(天气)、`GET /api/public/home`(家庭聚合)、`GET /api/public/feed`(动态流)

## 全局约定(光影层全局化)

- 光影层(`SunLightLayer.vue`)+ 左侧导航栏(`AppSidebar.vue`)+ 右下角备案号(`SiteFooter.vue`)在 `App.vue` 全局挂载,所有页面共享。
- 首页不再自带顶栏和光影层,只负责面板+相册+唱片机+光照测试控制台。
- 全屏 `position: fixed` 布局,无滚动条。
- 主文字色 `#3A2E22`(深咖啡),点缀色 `#A8483A`(砖红,用于纪念日天数/通知类型)。
- 浅色背景(双层伪元素 `::before`):`linear-gradient(135deg, #EDE4D3 0%, #E2D8C4 50%, #D6CBB4 100%)`;深色背景(`::after`):`linear-gradient(135deg, #0F1A2E 0%, #162238 50%, #1A2540 100%)`;主题切换时 opacity 交叉淡入淡出 1s。
- 字体统一 `sans-serif`(`-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif`),不显式声明 serif。

## 分层 z-index(从底到顶,严格按此顺序)

| 层 | z-index | 作用 | 混合模式 |
|----|---------|------|----------|
| bg-blobs(伪元素) | 1 | 5 个飘移色块(模糊大圆,fixed 不随滚动) | normal |
| bright-spot(亮斑) | 32 | 阳光照耀强度(夜黑→晨黄→日透明→夕橙) | multiply |
| album-corner(拍立得堆) | 10 | 右下角散落拍立得/闭合相册 | normal |
| panels(左右毛玻璃) | 20 | 动态/任务/天气/纪念日/今日 | normal |
| window-shadow-lower(下层阴影) | 35 | 内框竖+内框横+顶框+底框 | darken |
| reflection-layer(反光) | 42 | 内容被阳光照亮的轻微高光 | soft-light |
| vignette(暗角) | 44 | 边缘压暗(台灯 mask 祛除阴影) | normal |
| dust-layer(灰尘) | 46 | 光路微粒(20 个,夜间不可见) | screen |
| light-layer(体积光) | 48 | 光束+辉光 | screen |
| window-shadow-upper(上层阴影) | 49 | 左框+右框(最顶层,盖住光柱) | darken |
| el-popper/dropdown/tooltip | 61 | 全局下拉菜单(teleported,高于导航栏,光影层下方) | normal |
| MusicPlayer(音乐播放器) | 62 | 左下角黑胶唱片播放器(有背景歌单且曲目>0 时渲染) | normal |
| AppSidebar(导航栏) | 60 | 左侧导航+底部主题/台灯/用户 | normal |
| bright-spot(光源辉光) | 65 | 体积光源核心光晕 | screen |
| window-shadow-upper(窗框) | 68 | 上下分层窗框阴影 | darken |
| reflection(反光高光) | 72 | 内容反光 | soft-light |
| vignette(暗角) | 74 | 边缘压暗 | normal |
| dust(灰尘粒子) | 76 | 光路微粒 | screen |
| snow/rain(天气粒子) | 77 | 雪花/雨滴 | normal |
| light-layer(光影根) | 78 | 光影层根容器 | normal |
| lightning(闪电) | 79 | 雷电闪烁 | normal |
| SiteFooter(备案号) | 70 | 右下角 ICP+公安备案 | normal |
| lamp-light(台灯) | 100 | 钟摆运动径向发光(夜间) | normal |
| BackToTop/InstallPrompt | 200 | 返回顶部/PWA 安装提示 | normal |
| LibraryReader float-close | 210 | 电子书全屏模式浮动关闭按钮 | normal |
| PhotoViewer | 201 | 全屏照片播放器(Teleport to body) | normal |
| el-overlay/dialog/message-box | 63 | 弹窗遮罩(低于光影层 65,高于 AppSidebar 60) | normal |
| ElMessage(Toast) | 3000 | 右上角提示消息(EP 内置,不覆写) | normal |

## 1. 背景色块(bg-blobs)

5 个 `position: fixed; border-radius: 50%; filter: blur(60px); opacity: 0.4` 的大圆,各自飘移动画(18-26s `ease-in-out infinite`):

| 编号 | 尺寸 | 位置 | 颜色 | 动画 |
|------|------|------|------|------|
| 1 | 480×480 | top:-120, left:-100 | #9CD0B5(绿) | drift1 22s |
| 2 | 560×560 | top:25%, right:-180 | #EDDB8C(黄) | drift2 26s |
| 3 | 420×420 | bottom:-120, left:18% | #ECC0AC(粉) | drift3 20s |
| 4 | 380×380 | top:35%, left:32% | #A8C9DE(蓝) | drift4 24s |
| 5 | 300×300 | bottom:20%, right:22% | #C0D8A8(绿黄) | drift5 18s |

动画为 `translate + scale` 组合,位移 ±120~240px,缩放 0.8~1.35。

**性能注意**:
- `position: fixed`(不随页面滚动,避免 backdrop-filter 每帧重算)
- `transform: translateZ(0)`(隔离合成层)
- `will-change: transform`
- 深色模式 `opacity: 0.1`

## 2. 相册组件(album,首页栅格内)

相册是首页栅格系统中的一个组件,照片可溢出组件边界。

### 散落拍立得堆(近 7 天有新照片)
- `sevenDayPhotos` 过滤 `createdAt < 7天` 全量;`recentPhotos` 从中随机取 7 张展示。
- 每张 `.polaroid-pos`(定位包装)+ `.polaroid`(hover 缩放)分离 transform 上下文防频闪。
- `.polaroid`: 白边相纸 `padding: 6px 6px 22px`,随机旋转 ±25°、随机偏移 dx±170 dy±70、投影。
- 拍立得宽度随组件 w 缩放:`--polaroid-w: clamp(80px, w*30px, 140px)`。
- 照片可溢出组件边界(`overflow: visible`),z-index:40 高于其他卡片。
- hover:z-index 99 + scale 1.15(抽出感)。
- 点击 → `PhotoViewer` 全屏沉浸式浏览(播放全部近 7 天照片,非仅展示的 7 张),启动时关闭天气/灯光/毛玻璃/色块特效。

### 闭合相册(近 7 天无新照片)
- 平躺木色封面 `linear-gradient(#8B6F47,#6B5435)`,宽度 85% 容器,4:3 比例。
- 透视厚度感:多层 box-shadow 模拟俯视厚度。
- 花纹点缀:`::before` 45° 斜纹底纹 + `::after` 内边框。
- 家庭名称 + "家庭相册",随机斜放。
- hover 抬正放大,点击跳 `/album`。

## 3. 体积光系统(light-layer,z-index 48)

`position: fixed; inset: 0; mix-blend-mode: screen; pointer-events: none; overflow: hidden`。基于真实太阳位置(`/api/public/sun-info` 返回 288 个 5 分钟时隙,前端取当前时隙)。

### 光照生命周期

旋转/颜色/强度由**方位角 + 窗角**驱动:

- **窗角** `windowAngle = 90 - Math.abs(az - 180)`,90°=太阳正对窗户(正午),0°=太阳平行窗户(日出/日落方向)。窗角≤0 时无直射光。
- **直射光门控** `hasDirectLight = !isNight && windowAngle > 0 && alt > 0`,太阳不直射窗户时(az<90° 或 az>270°),体积光/辉光/反光全部关闭。
- **日昼进度** `dayProgress = (currentMin - sunriseMin) / (sunsetMin - sunriseMin)`,0=日出→1=日落,夜间 hold 在端点(日出前=0,日落后=1)。
- **旋转**:`shadowVRotation = az - 180` → az=90° 时 -90°(开始)→ az=180° 正午 0°→ az=270° +90°(结束)。窗角≤0 时 hold 在对应端点(-90° 或 +90°)。光柱角度 = 阴影角度(同向)。
- **光柱不透明度**:无直射光时 0,有直射光时 `sin(dayProgress * π) * rayBaseOpacity`(正弦过渡,正午最强)。
- **无直射光调色板全 transparent**:bloom/core/mid/ambient 均为 `transparent`,rayBaseOpacity=0 → 光源辉光和光束都不可见。

### 光源辉光(light-bloom)
- `position: absolute; width: 700px; height: 700px; margin-left: -350px; margin-top: -350px; border-radius: 50%; filter: blur(60px)`。
- `background: radial-gradient(circle, <bloom> 0%, <mid> 35%, transparent 70%)`。
- 位置:`left/top` = `source.x/source.y`。
- 夜间 bloom=transparent → 不可见。无直射光(窗角≤0)时同样 transparent。

### 光束(7 条羽毛状,makeRays)
每条 `position: absolute; top: 0; left: 50%; height: 160vh; transform-origin: top center`。
| # | width | offset | opacity | blur |
|---|-------|--------|---------|------|
| 1 | 90px | -210 | 0.45 | 45 |
| 2 | 60px | -120 | 0.6 | 35 |
| 3 | 110px | -30 | 0.5 | 55 |
| 4 | 50px | 60 | 0.65 | 30 |
| 5 | 80px | 150 | 0.55 | 40 |
| 6 | 40px | 240 | 0.7 | 25 |
| 7 | 100px | -270 | 0.4 | 50 |

- `transform: translateX(<offset>px) rotate(<rotation>deg)`,`rotation` = 阴影同角度。
- `background: linear-gradient(to bottom, <core> 0%, <mid> 35%, transparent 75%)`。
- 夜间 opacity 全 0 → 不可见。无直射光时同样全 0。

### 光源水平位置
- 日间:`sourceX = clamp(7.5, 92.5, ((az - 90) / 180) * 100)`%(东=左 7.5%,南=中 50%,西=右 92.5%)。
- 夜间:hold 在日出位置(左侧 7.5%)。
- `sourceY`:高度角 <6° 时 `2%`(地平线附近),否则 `-2%`(窗外)。

### 日间 4 档调色板(按太阳高度角 alt)
| 档位 | 条件 | bloom | core | mid | ambient | rayBaseOpacity |
|------|------|-------|------|-----|---------|----------------|
| 黄金时刻 | alt<6° | rgba(255,175,90,0.85) | rgba(255,160,70,0.95) | rgba(255,130,50,0.6) | rgba(255,165,80,0.12) | 1.3 |
| 晨昏 | alt<15° | rgba(255,200,130,0.78) | rgba(255,190,110,0.92) | rgba(255,170,85,0.52) | rgba(255,195,115,0.09) | 1.15 |
| 日间 | alt<60° | rgba(255,230,180,0.68) | rgba(255,222,160,0.9) | rgba(255,210,140,0.48) | rgba(255,225,170,0.06) | 1.05 |
| 正午 | alt≥60° | rgba(255,242,210,0.62) | rgba(255,238,195,0.9) | rgba(255,228,170,0.42) | rgba(255,240,200,0.05) | 0.95 |

## 4. 亮斑图层(bright-spot,z-index 32)

`position: fixed; inset: 0; mix-blend-mode: multiply; pointer-events: none`。模拟阳光照耀强度,在内容之上、阴影之下。multiply 让白色=透明(日间无影响)、黑色=压暗(夜间)、彩色=染色(日出日落)。

### 颜色生命周期(由 dayProgress 驱动)
| 阶段 | dayProgress | 颜色 | 不透明度 |
|------|-------------|------|----------|
| 夜间 | <0 或 >1 | 黑 rgba(0,0,0,1) | 0.7 |
| 凌晨 | 0~0.1 | 黑→黄 lerp | 0.7→0.4 |
| 清晨 | 0.1~0.3 | 黄→白 lerp | 0.4→0.1 |
| 日间 | 0.3~0.7 | 白(逐渐透明) | 0.1→0 |
| 傍晚 | 0.7~0.9 | 白→橙 lerp | 0→0.5 |
| 日落 | 0.9~1.0 | 橙→红 lerp | 0.5→0.7 |

## 5. 反光层(reflection-layer,z-index 42)

`position: fixed; inset: 0; mix-blend-mode: soft-light; pointer-events: none`。内容组件被阳光照亮的轻微高光。

- `background: radial-gradient(ellipse 60% 50% at <source.x> <source.y>, rgba(255,245,220,1) 0%, rgba(255,235,200,0.6) 30%, transparent 70%)`。
- 不透明度:无直射光时 0,有直射光时 `sin(dayProgress * π) * 0.22`(正午最强,轻微)。
- soft-light 模式:效果极其微妙,只给内容组件顶部边缘加一点暖光。

## 6. 窗户阴影(分层重构)

阴影拆为上下两层,光柱穿过中间。6 条 div bar,全部 `mix-blend-mode: darken`。

### 灰阶防叠加(关键)
- bar 用不透明灰 `rgb(G,G,G)`,G = `(1 - shadowIntensity) * 255`(夜间 G=0 全黑,正午 G=178 浅灰)。
- `mix-blend-mode: darken` → `min(backdrop, G)`,min 是幂等运算,跨层重叠 `min(min(backdrop,G),G) = min(backdrop,G)`,**永不叠加**。
- 阴影强度 `shadowIntensity`:夜间 1(最深)→ 正午 0.3(最浅),`1 - sin(π·dayProgress) × 0.7`。

### 下层(window-shadow-lower,z=35):4 条 bar
- **shadow-v**(内框竖):`top: -50vh; left: 50%; width: 112px; margin-left: -56px; height: 337.5vh; transform-origin: 50% 40vh; transform: rotate(var(--rot))`。
- **shadow-h**(内框横):`height: 70px; top: var(--htop); left: -75%; right: -75%`(不旋转)。
- **frame-h-top**(顶框):`height: 140px; top: calc(-10vh - 140px); left: -75%; right: -75%`(不旋转,底边在旋转原点水平线 y=-10vh)。
- **frame-h-bottom**(底框):`height: 1400px; top: calc(var(--htop) * 2 + 70px); left: -75%; right: -75%`(不旋转)。

### 上层(window-shadow-upper,z=49):2 条 bar(最顶层,盖住光柱)
- **frame-v-left**(左框):`top: -50vh; left: 50%; width: 1400px; margin-left: -1400px; height: 337.5vh; transform-origin: 100% 40vh; transform: translateX(-42.5vw) rotate(var(--rot))`。
- **frame-v-right**(右框):`top: -50vh; left: 50%; width: 1400px; height: 337.5vh; transform-origin: 0% 40vh; transform: translateX(42.5vw) rotate(var(--rot))`。

### 三条竖直 bar 原点对齐(关键)
全部对齐到 **(页面 50% X, 页面 -10vh Y)**:
- `top: -50vh; height: 337.5vh; transform-origin Y: 40vh` → 页面 Y = -50+40 = -10vh(原点在页面上方)。
- X 对齐:shadow-v `50%`(中心)、frame-v-left `100%`(右边缘)、frame-v-right `0%`(左边缘)→ 全部在页面 50%。

### 旋转参数
- `--rot: (az - 180) deg` → az=90° 时 -90°(开始旋转)→ az=180° 正午 0°→ az=270° +90°(结束旋转)。窗角≤0(az<90 或 az>270)时 hold 在对应端点。
- 内框横/顶框/底框不旋转(始终水平)。
- `--htop: clamp(5, 85, 80 - max(0,alt) * 0.8)`%(太阳越高→内框横越靠近顶部)。
- 窗户开口 85%:左右框 `translateX(±42.5vw)`。
- `--frame-top-offset: (alt - 45) × 0.045 vh`(±2vh,太阳越高顶框越低)。

## 7. 暗角(vignette,z-index 44)

`position: fixed; inset: 0; pointer-events: none`。
`background: radial-gradient(ellipse 90% 75% at 50% 42%, transparent 0%, transparent 55%, rgba(60,38,12,0.08) 80%, rgba(45,25,8,0.18) 100%)`。

## 8. 灰尘粒子(dust-layer,z-index 46)

`position: fixed; inset: 0; mix-blend-mode: screen; pointer-events: none; overflow: hidden`。
20 个粒子,每个:
- `position: absolute; border-radius: 50%; background: rgba(255,238,185,0.85); box-shadow: 0 0 8px rgba(255,225,150,0.7)`。
- 随机 `width/height` 2-5px,随机 `top/left`,随机 `animation-duration` 8-16s,随机 `--drift` 30-100px。
- 动画 `dust-float linear infinite`:0% opacity:0 → 15% opacity:0.9 → 85% opacity:0.9 → 100% `translate(var(--drift), calc(var(--drift) * -1.5))` opacity:0(向右上方飘移淡出)。

## 9. 全局导航栏(AppSidebar,z-index 60)

- `position: fixed; left: 0; top: 0; bottom: 0; width: 220px`(收起 64px)。
- 温暖磨砂玻璃:`backdrop-filter: blur(24px) saturate(1.1)` + 暖奶油渐变背景。
- 顶部:家庭名(hover 暖色下划线)+ 折叠按钮。
- 导航列表:按 category 分组(内容/相册/生活/成员/系统),Element Plus 线性图标(Document/Notebook/Picture 等,非 emoji)。
- **"系统"分组:设置 + 运维管理(后者仅 OPS 可见)**。**设置图标用内联 SVG 替代 el-icon Setting**(复杂 path 在 100% 缩放触发子像素光栅化开销),运维图标同理。
- 底部:主题切换(☀️/🌙)+ 台灯三态(🌑/💡/⬛,关灯时伪元素径向发光圈+脉冲动画)+ 语言切换 + 消息铃铛 + 用户头像胶囊。
- 深色模式:`background: rgba(30,40,65,0.55)` + `border-color: rgba(255,255,255,0.12)`。

**性能注意**:
- `contain: layout style` + `transform: translateZ(0)`(隔离合成层)
- `.sidebar-nav`(滚动区):`transform: translateZ(0)` + `will-change: transform`
- nav-item hover 用 `transform: translateX(4px) scale(1.03)`(而非 box-shadow,避免触发 backdrop-filter 重算)

## 10. 首页栅格仪表盘(12列×9行)

- 12 列×9 行栅格,组件按栅格单元定位(col/row/w/h)。
- GAP=40px,四边 margin(top=32/right=40/bottom=40/left=260,左侧=侧边栏220+40)。
- 栅格尺寸根据 `window.innerWidth/Height` 自适应计算 `cellW`/`cellH`。
- 组件 `position: fixed`,left/top/width/height 由 `cardStyle(w)` 按栅格计算。
- **编辑模式**:导航栏 EditPen 按钮切换 `appStore.homeEditMode`。
  - 侧边栏导航项变为组件来源(向右下偏移 `translate(4px,4px)` + 暖棕虚线边框 + 半透明底色;hover 进一步 `translate(8px,8px) scale(1.05)`)。
  - 原位置虚线框占位(`::before`)。
  - 从侧边栏拖出到内容区,ghost 由 `scale(0.3)` 弹性增长到 `scale(1)`(cubic-bezier 0.34,1.56,0.64,1),尺寸 80×60→200×150;drop 创建 4×5 新组件。
  - 侧边栏右边界气泡融合效果(`::after` radial-gradient pulse)。
  - 卡片可拖拽移动(drag-bar 顶部手柄)+ 缩放(resize-corner 右下角),栅格吸附(`Math.round(dx/(cw+GAP))`)。
  - 编辑模式禁用内部交互(`.card-inner { pointer-events: none }`),仅移动+缩放+删除。
  - grid-cell 出现动画(逐个渐现 `cellAppear 0.4s ease`,delay `i*5ms`)。
  - 编辑工具栏 hover 隐藏(`opacity:0; translateY(-10px); pointer-events:none`)。
  - 栅格背景可见(`.grid-overlay` CSS grid,JS 计算的 `--cell-w`/`--cell-h` 变量)。
- **h=1 时标题行消失**:`.card-head` opacity→0 + max-height→0 + padding→0(0.3s ease 动效)。
- 点击组件置顶(zCounter,编辑/非编辑模式均可)。
- 布局持久化 localStorage `ihomy:dashboard:layout`。
- 8 个默认组件:feed(5×5)/anni(3×4)/weather(3×2)/today(3×2)/recipe(4×4)/wish(3×2)/album(5×3)/finance(2×2)。
- 相册组件照片可溢出边界(`overflow: visible`,z-index:40)。

### 通用毛玻璃样式(dash-card)
```css
background: rgba(255, 255, 255, 0.42);
backdrop-filter: blur(28px) saturate(1.4);
-webkit-backdrop-filter: blur(28px) saturate(1.4);
border: 1px solid rgba(255, 255, 255, 0.5);
border-radius: 20px;
box-shadow: 0 8px 28px rgba(58, 46, 34, 0.1), inset 0 1px 0 rgba(255, 255, 255, 0.6);
color: #3A2E22;
```

### 组件内容(hover 效果统一:背景变化,无 transform 位移)
- **家人动态(feed)**:头像+气泡消息,bubble hover `background` 变化(无 translateX)。
- **悬赏任务(task)**:奖励图标+标题+状态点,行 hover `background`。
- **今日(today)**:积分余额+连续天数+签到按钮+待办提醒。
- **天气(weather)**:城市+温度+图标+状况+未来三天+空气+预警(默认展开)。
- **纪念日(anni)**:名称+日期+倒计时天数,行 hover `background`+`border-radius`。
- **今日推荐(recipe)**:菜谱列表(封面+名称),行 hover `background`。
- **物品寻找(search)**:输入框+搜索结果(名称+位置),行 hover `background`。
- **愿望单(wish)**:愿望列表(状态点+标题),行 hover `background`。
- **本月收支(finance)**:收入/支出/结余三列。
- **音乐(music)**:背景歌单名+曲目列表,无歌单显示空状态。

## 11. 黑胶唱片播放器(MusicPlayer,z-index 55)

- `position: fixed; left: 240px; bottom: 24px`(全局挂载,所有页面显示)。
- **渲染条件**:`v-if="playlist.length"` — 仅当当前家庭有背景音乐歌单且曲目数 > 0 时渲染。无歌单或无曲目时组件不显示。
- **z-index: 55**(光影层下方:低于 bright-spot=65/vignette=74/lamp=100,高于 popper=54)。
- `backdrop-filter: blur(24px) saturate(1.3)` + 暖白背景 `rgba(255,255,255,0.55)`。
- 唱片本体 56×56(`.vinyl-disc`),`transition: transform 0.3s`;hover scale 1.05。
- 唱片盘:`radial-gradient(circle, #3A2E22 0%, #1A1410 30%, #2A2018 60%, #1A1410 100%)` + `repeating-radial-gradient` 凹槽纹理,中心红色标签 `radial-gradient(circle, #A8483A 0%, #6B2E26 100%)`。
- 播放时:`.vinyl-disc` `animation: spin 4s linear infinite`(旋转),`.vinyl-arm` `transform: rotate(15deg)`(唱臂落下)。
- 唱臂:`width: 3px; height: 28px; background: linear-gradient(to bottom, #C9A876, #8B6F47); transform-origin: top center; transform: rotate(-15deg)`(静止时抬起)。
- 展开内容(`.player-body` 280px):歌名 13px/600 + 状态(正在播放/已暂停)+ 进度条 + 控件(⏮⏸▶⏭ + 歌单列表)。
- 展开/收缩动画:`transform-origin: left bottom` + `scaleX(0→1)` + `width: 0→280px` 0.35s。
- 拖拽:`left + bottom` 定位,位置持久化 localStorage `ihomy:music:pos`;拖拽边界 clamp `max(8, ...)` 防出屏。重置位置合并到 Settings「恢复默认面板布局」。
- 数据源:`musicApi.getBackground()` 获取当前家庭背景歌单 + 曲目;watch `bgMusicVersion`(Settings/音乐页设为背景时 bump)重新加载;watch `familyId` 切换家庭时重置。
- 切歌:`nextTick` 后 `audioEl.play()`(不用 setTimeout);`playing.value` 先置 false 再恢复,避免状态闪烁。

**性能注意**:`contain: layout style` + `transform: translateZ(0)` 隔离合层。

## 11a. 全局弹窗/Toast/Popper 规范(补充)

### 弹窗尺寸规则(`main.css` 全局,按业务场景)

| class | 场景 | 宽度 | max-height | 滚动 |
|-------|------|------|------------|------|
| `dialog-sm` | 简短确认/单行输入 | 420px | 自适应 | 无 |
| `dialog-md` | 选择器/简单表单 | 520px | 520px | body 内部滚动 |
| `dialog-lg` | 复杂多字段表单 | 640px | 640px | body 内部滚动 |
| `dialog-xl` | 详情/媒体预览 | 82vw(max 900px) | 85vh | body 内部滚动 |

- 容器 `flex-direction:column`;body `flex:1 + overflow-y:auto`;容器本身不滚动。
- 圆角 14px;磨砂背景 `#fcf8f0` + `blur(12px)`;ESC + 点击遮罩关闭。
- 关闭按钮 X 显示(28×28px,圆角 8px,hover 浅米底色)。
- 弹窗内 Tab:选中态低饱和暖棕 `#5c4c3d` + `#c4a884` 下划线 `opacity:0.7`(禁蓝色)。
- 弹窗内 checkbox:选中色 `#b88c6e` 暖棕(禁原生蓝色)。

### Toast(ElMessage)

- 右上角 `right:24px`;圆角 10px;磨砂背景。
- success:暖米底 `rgba(243,238,230,0.95)` + 褐字 `#5c4c3d`(**禁止亮绿**)。
- warning:暖米底 + 暗金 `#8a6d3b`;error:浅红底 + 暗红 `#b04a3a`。
- 暗色模式:`rgba(30,42,72,0.92)` 背景 + 浅米文字。

### Popper/Dropdown

- `.el-popper.is-light` `z-index:54`(光影层下方,MusicPlayer=55 之上)。

## 11b. 功能页统一工具栏与多选规范(相册/放映厅/音乐通用)

**工具栏结构**(`.page-toolbar card`,全局 padding `10px 16px !important`):

- `.tb-left` 搜索/筛选:组件统一 `size="small"`(24px 高,三页已对齐);搜索框 200px 带放大镜 SVG prefix + 来源筛选 150px + 类型/题材筛选 120-140px;筛选为**前端过滤**(家庭数据量小,全量拉取后 computed 过滤)。
- `.tb-right` 操作按钮(默认 32px 高,自适应宽度):
  - 常规态从左到右:多选 → 次级动作(想看/刷新映射,owner+有映射时显示) → 从设备同步(owner) → 主操作(新建/上传,`type="primary"`)。
  - 选择态(互斥替换):`.select-count` 13px 次要色选中计数 + 取消 + 删除所选(danger)。
- **按钮间距(强制)**:`.tb-right` 为 `flex gap:8px`,普通按钮间另有 EP `.el-button+.el-button` 12px 兄弟边距(合计 20px);**按钮被 `el-dropdown` 包裹时(如「上传音乐」下拉)吃不到兄弟边距,必须 scoped 补 `.tb-right :deep(.el-dropdown) { margin-left: 12px }`**,否则间距不一致。

**多选交互(强制,三页同款)**:

- 仅用 `.pick-badge` 右上对勾圆标(22×22px,`.on` 时 `#b88c6e` 实底白勾,非选中 `rgba(184,140,110,0.25)`)+ 卡片 `outline: 2px solid #b88c6e` 描边(`outline-offset: -2px`)。
- 选择态点击卡片即勾选(封面点击事件 `selectMode ? toggle : play`);**禁止叠加左上 checkbox 角标**(双选择效果,已在音乐页去重)。
- 选择模式一次性操作,不 localStorage 记忆;批量删除混设备映射内容时确认文案提示"仅删记录,设备文件不受影响"。

**设备映射来源角标(统一)**:卡片封面角落 `设备名 + .status-dot` 状态点,半透明白底(`rgba(255,255,255,0.85)`)磨砂圆角小标签(11px,#6b5d4c);状态点 6px 圆点带辉光:VALID 绿 `#67b26b` / OFFLINE 灰 `#9a9a9a` / MISSING 红 `#b96058`;暗色模式 `rgba(30,42,72,0.85)` 底 `#c9b8a0` 字。

## 11c. 音乐页(Music)

- **结构**:Breadcrumb + 标准工具栏(11b 规范)+ 三 Tab(全部曲目/按专辑/歌单);Tab 项 15px/40px 高,active `#5c4c3d` + 下划线 `#c4a884` 2px `opacity:0.7`,无 nav 底边线;切 Tab 自动退出多选。
- **工具栏**:tb-left 搜索(歌名/艺术家/专辑)+ 来源筛选(全部/本地上传/外链/各映射设备名,设备选项由列表数据去重);tb-right 多选/新建歌单(歌单 Tab)/刷新映射/从设备同步/上传音乐(primary 下拉:单曲/专辑文件夹/外链)。
- **曲目卡片**:`repeat(auto-fill, minmax(220px, 1fr))` 网格,gap 24px;封面 160px(hover 半透明黑遮罩 + 44px 白底圆形 ▶ 播放按钮 scale 0.85→1;无封面显示 `#e9e2d7` 底 🎵 占位);标题 15px/600 单行省略;艺术家 12px + 专辑 11px 次要色;时长/比特率 11px `opacity:0.6`;hover `translateY(-4px)` + 阴影加深;右下 ⋮ 更多按钮(白底磨砂圆形 28px,加入歌单/删除)。
- **专辑卡片**:280px 网格,封面 140px + 内嵌曲目列表(序号/曲名可点/时长,`max-height:120px` 滚动)+ 底部「整张加入歌单」下拉。
- **歌单卡片**:260px 网格,封面 160px(无封面 SVG 唱片占位),BGM 标记左上白底磨砂圆角小标签;查看/设为BGM(primary small)/取消BGM/删除(text danger)。
- **播放**:dialog-md 弹窗,居中封面 160px 圆角 12px + 艺术家 + 原生 `<audio controls autoplay>`;设备映射曲目(url 为 `storage://` 逻辑地址)点击播放时现取 `GET /music/{id}/play-url`(HMAC 签名 10 分钟),本地/外链直用原 URL。
- **弹窗**:dialog-sm(添加外链四字段/上传专辑/新建歌单)、dialog-md(歌单详情 + 添加曲目双 Tab:按曲目 checkbox 列表/按专辑整勾)。
- **文案**:全页 `$t('music.*')` 中英词条,无硬编码中文。

## 12. 光照测试控制台(全局组件)

- `position: fixed; left: 240px; bottom: 24px`(毛玻璃面板),`LightTestConsole.vue` 全局挂载于 App.vue。
- 信息面板:地区/日期/高度角/方位角/窗角度/日出/日落/时段标签(9 段:深夜/凌晨/日出/清晨/上午/正午/下午/日落/黄昏)+ 进度条。
- 播放控件:后退/暂停/前进/停止 + **速度控制** 5 档(0.5x/1x/2x/4x/8x,间隔 `208/testSpeed` ms)。
- 天气控制:☀️/☁️/🌧️/❄️/⛈️ + 降水等级滑块(雨雪雷专用)。
- 图层开关:阴影/环境光复选框。
- 台灯模式:自动/开/关按钮 + 色温/亮度滑块。
- 循环 288 时隙,停止=重置真实时间+关闭控制台。
- `useSunLight.js` provide/inject 全局共享光影状态。

## 13. 台灯系统(lamp-light,z-index 100)

- 位置:左上黄金分割点(38.2%, 38.2%),`filter: blur(20px)`。
- 光圈直径 144vw(半径 72vw,页面 4/5)。
- 3 态开关:lampMode=auto(夜间自动)/on(常开)/off(关),按钮图标🌑/💡/⬛循环切换。
- `lampStrength`:auto=`sunScene.lampOpacity`、on=1、off=0。
- 开关时机:`lampOpacity=(isNight||dayProgress≥0.9||dayProgress≤0.1)?1:0`。
- 色温可调:lampTemp 0-100 滑块,warm rgba(255,180,100)→cool rgba(220,230,255)。
- 亮度可调:lampBrightness 0-100 滑块,控制 mask 透明区域比例。
- 钟摆运动:CSS `@keyframes lampSwing` 驱动(8 秒周期),不经过 Vue 响应式。横向 ±1.5vw,两侧 scaleX 0.97/1.03。
- **mask 祛除阴影**:`.window-shadow`/`.bright-spot`/`.vignette` 三层都加 `mask-image: var(--lamp-mask)`,台灯开启时 mask 中心透明挖洞祛除阴影,关闭时 `mask: none`。
- **mask 中心固定**(不随钟摆变化),避免每帧重栅格化 3 个全屏 fixed 元素;只有台灯 div 本身做钟摆。
- **开关灯 2s 渐变**:mask-image 不支持 CSS transition,用 GSAP 补间驱动 `lampAnim`(reactive),`lampDivOpacity` 和 `lampMask` 同步 2s 渐变(ease `power2.out`);mask 挖洞半径随强度缩放(开灯洞从 0 放大,关灯洞缩到 0)。

## 14. 天气特效

- `codeToPrecipLevel(code)` 1-6 级。
- `snowParticles`(❄ 字符 12-28px)/`rainParticles`(线性雨滴)computed 数组,数量=降水等级×10。
- `cloudFlicker` GSAP 每 4-8s 随机补间 0.1-1,模拟云彩遮阳。
- `weatherShadowOpacity`:雨雪=1,多云=`1-cloudFlicker`,晴=0。
- `.shadow-bar.weather-shadow` 在 window-shadow 内,继承同色同 blur。
- `weatherMultiplier`(晴 1.0/多云 0.55/雨 0.25/雪 0.4)实时驱动 rayStyles/lightLayerOpacity/reflectionStyle。
- 雪花/雨滴 CSS keyframes + `translateZ(0)` 合成层隔离。
- bright-spot:雨雪时 opacity=0 隐藏。

## 15. GSAP 入场动画

页面 mount 后:
```js
gsap.from('.dash-card', { y: 16, autoAlpha: 0, duration: 0.4, stagger: 0.04, ease: 'power2.out' })
```
**光柱层不入场**,直接显示当前太阳状态(避免光从无到有的突兀)。

## 16. 响应式

| 断点 | 变化 |
|------|------|
| ≤960px | 所有 dash-card `display:none`,显示"请使用电脑或平板访问首页仪表盘"提示 |

## 17. 数据流

- 页面 mount 时并发请求 `/public/sun-info` + `/public/weather` + `/public/home` + `/public/feed`。
- sun-info 返回 288 时隙(每 5 分钟一个),前端 `currentSlotIndex(sunInfo)` 按当前时间取 5 分钟时隙索引,`getSunScene(sunInfo, slotIndex)` 返回 `{source, rotation, palette, rays, shadowVRotation, shadowHTop, shadowIntensity, shadowColor, brightSpotColor, brightSpotOpacity, reflectionOpacity, altitude, azimuth, isNight, dayProgress, windowAngle, hasDirectLight}`。
- 旋转由方位角驱动(`az - 180`),窗角≤0(无直射光)时 hold 在端点;光柱/辉光/反光在无直射光时全透明;亮斑夜间黑色压暗。
- 每 5 分钟更新一次时隙。
- 拍立得堆按近 7 天照片筛选(展示 7 张随机),点击 `PhotoViewer` 播放全部近 7 天照片。

## 18. 主题切换

- **双层伪元素背景 1s 过渡**:`linear-gradient` 背景不支持 CSS transition 插值,改为双层伪元素 `::before`(浅色渐变)/`::after`(深色渐变),`html.dark` 时 opacity 交叉淡入淡出 1s。
- 面板/色块/文字颜色同步 1s 过渡。
- **手动切主题取消自动**:`onTheme` 设 `autoMode=false` + ElMessage.info 提示"已切换到手动主题,日出日落自动切换已暂停(可在设置中恢复)"。`applyAutoTheme` 检测 `autoMode=false` 自动跳过。
- 深色模式:`html.dark .blob { opacity: 0.1 }` 色块压暗至 10%;`--color-primary` 深色覆写 `#E8DCC8`。

## 18a. 深色模式配色体系(星空暗色背景)

> 深色模式与浅色模式使用**完全不同的色值**,禁止直接复用浅色色值。整体设计语言:柔和低饱和暖棕主题,磨砂毛玻璃质感。

### 按钮(el-button,`main.css` 全局覆写)

| 按钮类型 | 深色模式背景 | 深色模式文字 | hover 背景 |
|---------|------------|------------|-----------|
| 主按钮(primary) | `#d4b298`(提亮浅暖棕) | `#2a2018`(深色) | `#e0c2aa` |
| 次级按钮(默认) | `rgba(255,255,255,0.12)`(半透明白色磨砂) | `#E8DCC8`(浅米白) | `rgba(255,255,255,0.18)` |
| 幽灵按钮(.ghost-btn) | 透明底 | `#E8DCC8` | `rgba(255,255,255,0.08)` |
| 危险按钮(danger) | `rgba(201,116,116,0.15)`(半透明) | `#c97474`(低饱和暗红) | `rgba(201,116,116,0.25)` |
| 危险 text/link | 透明底 | `#c97474` | `rgba(201,116,116,0.12)` |
| 禁用/loading | `rgba(232,220,200,0.06)` | `rgba(232,220,200,0.3)` | — |

### Tab 选中态

- `el-tabs__active-bar`:`#d4b298` opacity 0.5(低饱和暖棕下划线,禁止亮蓝)。
- `el-tabs__item.is-active`:`#E8DCC8`。

### 状态标签(el-tag)

| 标签类型 | 深色模式背景 | 深色模式文字 |
|---------|------------|------------|
| 默认 | `rgba(212,178,152,0.25)` | `#d4b298`(浅暖棕) |
| success | `rgba(125,186,125,0.15)` | `#7dba7d`(低饱和绿) |
| warning | `rgba(212,178,152,0.15)` | `#d4b298` |
| danger | `rgba(201,116,116,0.15)` | `#c97474`(低饱和暗红) |
| info | `rgba(255,255,255,0.08)` | `rgba(232,220,200,0.6)`(浅米) |

- 圆角 8px;半透明磨砂背景;禁止高饱和实色块。

### 数量角标(el-badge)

- `rgba(0,0,0,0.5)` 半透明黑色磨砂背景;`#E8DCC8` 浅米白文字;不使用纯黑实色块。

### 图标规范

- 所有 `el-icon` SVG `stroke-width: 2px`。
- 图标颜色跟随文字层级:主要图标=主文字色,次要图标=辅助灰色,危险图标=`#c97474`。

### 圆角全局统一

- `el-button` 12px(small 10px);`el-input__wrapper` 10px;`el-card` 14px;`el-dialog` 14px。

### 弹窗深色模式

- 弹窗背景 `#1E2A48`;次级按钮 `rgba(255,255,255,0.12)`;placeholder `rgba(232,220,200,0.35)`。

## 19. 性能规范(已踩坑)

- **100% 缩放卡顿根因**:Element Plus `Setting`/`Monitor` 图标 SVG path 过于复杂,hover 时子像素光栅化开销大 → 用内联 SVG 替代(详见博客 id=18)。
- **backdrop-filter + overflow:auto 子元素**:毛玻璃父元素 + 子元素滚动 = 性能炸弹 → `transform: translateZ(0)` 隔离合层。
- **背景随滚动**:`.bg-blobs` 用 `position: fixed` 不随页面滚动;移除 `background-attachment: fixed`。
- **关闭效果时跳过定时器**:`flickerTimer` 在所有开关关闭时 return,避免持续触发 sunScene 重写。
- **v-if 优化**:blend 层(bright-spot/reflection/light-layer/dust-layer/lamp-light)opacity≤0.01 时 `display:none`。
- **translateZ(0) 隔离**:bg-blobs、bright-spot、reflection-layer、light-layer、vignette、dust-layer、snow-layer、rain-layer、sidebar-nav、5 个面板滚动区。
- **contain:layout style**:app-sidebar、draggable-panel、music-player。
- **rAF 写 Vue ref**:原 `useSunLight.js` 钟摆用 `requestAnimationFrame` 每帧写 ref,触发 `SunLightLayer.vue` 每帧重渲染 → 改 CSS `@keyframes lampSwing` 完全绕过响应式。
- **常驻事件监听器**:原 `useDragResize.js` 每实例 `onMounted` 挂 `mousemove`/`mouseup` → 改 `onDragStart` 时挂、`onMouseUp` 时移除。
- **mask 中心固定**:台灯 mask 不随钟摆变化,避免每帧重栅格化 3 个全屏 fixed 元素;只有台灯 div 本身做钟摆。
- **mousemove 节流**:idle 检测 2s 节流。

## 20. 运维页·详细日志(Trace Logs,`/ops` 第 5 个 tab)

> 按 tid(16 位链路号)检索 access/server/thirdparty 三类日志文件,时间线合并展示;面向 OPS 排查问题。

### 查询区

- 工具行(`.filter-row`):tid 输入框(320px,placeholder 提示「报错提示 [tid:xxx] 里的那串」)+ 日期选择器(缺省今天,日志按天滚动)+ 查询主按钮;回车触发查询。
- 结果超 3000 条顶部 warning alert「已截断」;无结果 info alert「该日期未找到此 tid 的日志,试试调整日期」。
- 计数行:13px 次要色「共 N 条 · yyyy-MM-dd」。

### 时间线条目(`.trace-list` 纵向列表,10px 间距)

- 每条 `.trace-entry`(`--color-card-2` 圆角 10px,padding 10px 14px):
  - 头部行:时间(12px mono 次要色)+ 来源标签 + 级别标签 + logger 名(12px 次要色);
  - 消息体 `pre.trace-msg`:等宽字体 12px,`pre-wrap`+`break-all`,`max-height: 420px` 内部滚动;**多行堆栈整块保留**(后端已按行头时间戳归并,119 行堆栈 = 一条目)。
- 来源标签配色:access=primary(暖棕)/ server=warning(金)/ thirdparty=success(绿)。
- 级别标签配色:ERROR=danger(红)/ WARN=warning(金)/ INFO=info。
- 条目左侧色条:`lv-error` 3px `#b04a3a`,`lv-warn` 3px `#b88c6e`,其余无。

### 入口联动

- 操作日志 tab 表格 TID 列:等宽字体暖棕色链接样式(hover 下划线),点击切到详细日志 tab 并按该行日期自动查询。
- 路由直达:`/ops?tab=trace&tid=xxx&date=yyyy-MM-dd`(onMounted 解析 query 自动查询,便于分享/书签)。
- 前端 5xx 报错 toast(`request.js`)自带 `[tid:xxx]` 前缀(取响应头 X-Trace-Id),报障直接复制检索。

## 验收标准

1. 打开页面,背景米白渐变 + 5 个色块缓慢飘移,右下角拍立得堆/闭合相册,左右毛玻璃面板从两侧滑入。
2. 日出后太阳方位到达 90° 时光柱从左上方斜射(角度 -90°),亮斑从黑色变黄,阴影最深;正午光柱垂直(0°),亮斑透明,阴影最浅;日落前方位到达 270° 时光柱从右上方射(+90°),亮斑变橙红;太阳方位在 90°~270° 之外(窗角≤0)时无直射光,光柱不可见;夜间全黑,阴影最深待命在日落位置,凌晨 2 点跳变回日出角度(无人看,无感)。全天平滑过渡无扫光。
3. 光柱在相册和面板之上(screen 变亮),灰尘粒子在光路中漂浮发光;左右框(上层阴影 z=49)盖住光柱顶部。
4. 阴影重叠区域颜色不叠加变深(灰阶 darken 幂等)。
5. 内容组件(相册/面板)在日间有轻微反光高光(soft-light,跟随光源位置)。
6. 黑胶唱片播放器:有背景歌单且曲目>0 时显示在左下角(`left:240px bottom:24px z:55`),hover 放大唱片+展开歌名/进度条/控件;无歌单或无曲目时不渲染;切换家庭自动重载。
7. 全局导航栏(AppSidebar)左侧固定,含家庭名/模块导航/系统下拉(设置+运维)/主题切换/台灯三态/语言/铃铛/用户头像;关灯时按钮伪元素径向发光圈脉冲动画。
8. ≤960px 时左侧面板和纪念日隐藏,只保留相册 + 导航栏 + 紧凑天气 + 小唱片。
9. 全局光照测试控制台(`LightTestConsole.vue`,App.vue 挂载):循环 288 时隙,含地区/日期/时间/高度/方位/窗角/9 段时段标签 + 进度条 + 后退/暂停/前进/停止 + 速度控制(0.5/1/2/4/8x) + 天气控制(☀️/☁️/🌧️/❄️/⛈️ + 降水滑块) + 图层开关(阴影/环境光) + 台灯模式(自动/开/关) + 色温/亮度滑块;停止=重置真实时间+关闭控制台。
10. 夜间台灯自动开启(傍晚 dayProgress≥0.9 开,清晨 dayProgress>0.1 关),mask 祛除左上黄金分割点周围阴影;台灯钟摆运动(CSS `@keyframes lampSwing` 8 秒周期,横向 ±1.5vw);亮度滑块控制 mask 透明区域大小(3%-100%);色温滑块控制暖光色温。**开关灯 2s 渐变**:GSAP 补间驱动,mask 挖洞半径随强度缩放。
11. **主题切换 1s 过渡**:双层伪元素 `::before`/`::after` opacity 交叉淡入淡出;面板/色块/文字颜色同步 1s 过渡。
12. **首页栅格仪表盘**:12列×9行栅格,8个默认组件(feed/anni/weather/today/recipe/wish/album/finance);编辑模式可拖拽移动+缩放+增删组件(从侧边栏拖入);h=1 标题消失;点击置顶;照片溢出;布局持久化 localStorage。
13. **今日面板**:积分余额+连续天数+签到按钮+今日待办提醒前 3 条(登录可见)。
14. **首页功能组件区**(登录可见):栅格系统内 8 个组件(feed/anni/weather/today/recipe/wish/album/finance),编辑模式可拖拽缩放增删;移动端 ≤960px 隐藏显示提示。
15. **备案号**:右下角 `right:16 bottom:8 z:70`,ICP+公安占位,磨砂玻璃小字。
16. **照片瀑布**(`/cascade`):落叶式飘落动画(包装元素分离 transform 防频闪),hover 暂停+scale 1.15,点击 `el-image-viewer`,每 2s 生成一张上限 50 张。
17. **深色模式**:`html.dark .blob { opacity: 0.1 }` 色块压暗至 10%;`--color-primary` 深色覆写 `#E8DCC8`;手动切主题后取消日出日落自动切换(`autoMode=false`)+提示;深色模式配色体系详见 §18a。
18. **创建新家庭**:`POST /family` 已登录用户创建新家庭组(绑定 OWNER+切换当前家庭);Settings 页入口。
19. **天气特效**:雪(❄ 字符 12-28px)/雨(线性雨滴)粒子,数量=降水等级×10;多云闪烁(GSAP 4-8s 随机补间);weather-shadow 雨雪常显/多云随 cloudFlicker/晴不显示;weatherMultiplier 衰减光强(晴 1.0/多云 0.55/雨 0.25/雪 0.4)。
20. **和风字体图标**:`<i class="qi-{iconCode}">`,iconCode 来自和风 API now.icon;npm 包 qweather-icons。
21. **z-index 层级**:光影层(65-100)为最高层(台灯 100 除外);弹窗遮罩 `el-overlay`+`ElMessage`=63(低于光影层 65,高于 AppSidebar 60);Popper/dropdown/select=64(高于弹窗,低于光影层);PhotoViewer=201;BackToTop/InstallPrompt=200;MusicPlayer=62;任何新增组件 z-index 不得超 100(台灯 100 除外),弹窗遮罩和 Popper 例外(63/64)。
22. **书架页**(`/library`):与博客列表页设计风格统一。顶部工具栏(`.page-toolbar.card`:搜索→分类级联→排序→格式筛选→状态筛选→上传主按钮+批量操作);图书网格 `auto-fill minmax(150px, 1fr)`,卡片含封面(3:4 比例)+格式角标(右上半透明黑底白字)+书名(2 行截断)+作者+浏览数;卡片 hover 上浮 `translateY(-4px)`+阴影;下拉菜单(编辑/复制链接/删除)hover 显示;分类选择/筛选用 `el-cascader`(`checkStrictly + emitPath: false`,支持多级树、`filterable` 搜索)。
23. **书架详情页**(`/library/:id`):flex 布局(封面 140px+元信息区);封面 3:4 带阴影;元信息含书名(24px/700)、作者、格式标签(暖棕半透明)、分类标签、文件大小、标签、浏览数;操作按钮区(在线阅读/下载/阅读状态切换);简介区(section-label 标题);在线阅读器全屏覆盖(z-index:200)含顶栏(书名+翻页控件+关闭)+内容区(PDF iframe / EPUB epub.js / TXT 分页);移动端封面+元信息改垂直居中布局。
24. **书架编辑页**(`/library/edit/:id?`):与博客编辑页设计风格统一。`.card` + `el-form label-position="top"`;文件上传(`el-input` readonly + `el-upload` append 按钮);封面上传同;表单含书名/作者/文件/封面/简介/分类(`el-cascader` 多选+新建分类弹窗支持选父级)/标签/可见范围;底部 `.form-footer` 右对齐保存按钮。
25. **ElMessageBox 动画**:与 el-dialog 一致(`fade-in-linear` transition + `.el-overlay-message-box` scale(0.94)+opacity 淡入 0.25s);点击遮罩关闭(`closeOnClickModal: true`);所有 `ElMessageBox.confirm` 调用均加此参数。外观:圆角 14px+暖米底色+毛玻璃+暖棕按钮,同 el-dialog 规范。
26. **日记编辑页**(`/diary/edit/:id?`):单张信纸自适应高度,按整页(18行×28px=504px)增长;`.page-break-bg` 层每 504px 一条深色实线标记分页;页眉日期/时间上下排列(`header-left` flex-column),日期与心情底端对齐、时间与天气底端对齐;心情/天气 picker overlay z-index:61(光影层 65 之下,编辑框之上)。
27. **el-dialog append-to-body**(强制):所有 `el-dialog` 必须加 `append-to-body`,否则弹窗渲染在组件内部被 stacking context 困住,遮罩无法覆盖导航栏 backdrop-filter。
28. **音乐页**(`/music`):上传方式合并为「上传音乐」下拉菜单(单曲/专辑文件夹/外链);新建歌单+多选按钮放在 `el-tabs__nav-scroll` 内部右侧(absolute 定位 `right:0; top:0`)。
29. **博客列表页**(`/blog`):顶部工具栏(`.page-toolbar.card`:搜索→分类级联[<1400px]→标签筛选(`filterable` 可输入搜索)→排序图标(↓NEW/↓MOST 无外框点击切换)→筛选后计数→写博客主按钮);≥1400px 左侧常驻分类面板(220px,独立滚动,计数基于全量权限API `GET /blog/categories/counts`,不随筛选改变);<1400px 自动隐藏面板改工具栏 `el-cascader`;分类树基于 `content_blog_category` 表 `parent_id` 自引用(后端返回扁平树 `{id,name,parentId,path,depth,childCount}`);卡片三行布局(标题行→内容摘要行→分类+标签+元数据合并行);hover 浮现编辑/删除快捷按钮;草稿标记;分类弹窗 `el-cascader` 选父级(编辑时排除当前及后代防环)。
30. **全局工具栏规范**(`main.css`):`.page-toolbar` padding `10px 16px !important`(不被 `.card` 20px 覆盖);`.tb-left` 放搜索/筛选/排序,`.tb-right` 放操作按钮;工具栏按钮 `.write-btn`/`.ghost-btn`/`.danger-btn`/`.view-toggle`/`.vt-btn` 全局定义 height:32px,禁止 scoped 重复;所有列表页(Wish/Anniversary/Album/Cinema/Reminder/Task/Plan/Book/Kitchen/Member/DiaryList/Ingredient/Tree/Music/Points/Storage/Item/Library/Blog)统一用 `.page-toolbar.card`。
31. **圆角统一规范**(`main.css`):`el-input__wrapper`/`el-select__wrapper`/`el-cascader .el-input__wrapper`/`el-cascader .el-select__wrapper` 全局 10px;`el-button` 12px(small 10px);`el-card`/`el-dialog` 14px。
32. **分类级联规范**:博客+图书分类选择/筛选统一用 `el-cascader`(`checkStrictly + emitPath: false`,单选任意层级,`filterable` 可搜索);分类树基于 `parent_id` 自引用表;编辑时排除当前分类及其后代防环。
33. **日记书架页**(`/diary`):每位作者一本日记本,3:4 封面网格 `auto-fill minmax(180px, 1fr)`。封面木纹渐变(浅 `linear-gradient(155deg,#A8845C 0%,#8B6F47 45%,#6B5435 100%)`,深 `#5A4630→#322616`)+`::before` 115° 细斜纹+左缘装订脊(16px 渐变压暗)+右缘厚度内阴影+砖红绑带(右 12px 处 5px 竖条);纸质标签 `rgba(255,253,248,0.94)`、`rotate(-1.2°)`、`::after` 内圈虚线缝纫边,含作者名(16px/600)/篇数(12px)/起迄日期分行(11px,`起 {date}`/`迄 {date}`,单篇只一行)。hover 抬起 `translateY(-6px)`+阴影加深,标签转正。点击进 `/diary/book/:authorId`。
34. **日记翻书页**(`/diary/book/:authorId`):桌面双页信纸(左/右页各 496px+10px 书脊间隙,左页圆角 `14px 3px 3px 14px`,右页镜像;书脊阴影=左页右缘/右页左缘 42px 渐变压暗);移动端单页(useDevice 切换,页码语义转换)。书页=页眉(首页:日期/时间左栏+心情/天气右栏横线样式,沿用编辑页;hover 浮现编辑/删除 26px 圆角按钮,作者或 OWNER 可见)+正文 504px 裁剪窗口(同一文本块 `translateY(-页序*504px)` 裁切)+页脚(可见范围标签+页码+字数)。排页紧凑连续(上一篇写完下一篇紧接,不补空白页;仅总页数为奇数时末页右侧留白);页数=`max(正文行数, 涂鸦高度)/504`。底部毛玻璃工具栏:上一页/目录(el-dropdown 按日期跳篇,max-height 320px 滚动)/页码指示/下一页;键盘 ←/→ 翻页;翻页方向性滑入动画 0.28s(`translateX(±28px)`→0)。
35. **日记涂鸦笔盘**(`DoodleTray.vue`,编辑页右侧 sticky,宽 272px):7 支内联 SVG 画笔(签字笔/铅笔/蜡笔/荧光笔/画笔/像素橡皮/对象橡皮,26×52 viewBox,笔尖/笔身实时显示当前墨色);hover 抬起旋转 `translateY(-5px) rotate(-4deg)`,选中悬停+投影+暖棕标签。粗细 el-slider 1-12、透明度 el-slider 10%-100%(标签 `min-width: 3.2em` 等宽对齐);28 色 7 列调色盘(18px 圆点)+彩虹「+」自定义取色(`input type=color`);撤销/重做两枚 `ghost-btn small`(仅涂鸦生效,Ctrl+Z/Y 在 textarea 聚焦时不劫持)。
36. **日记涂鸦画布层**:选中画笔后直接画在信纸上(再次点击画笔或 Esc 回写字模式)。编辑页层级:textarea(1)<荧光画布(2,`mix-blend-mode: multiply`+opacity 0.55,深色模式 screen)<墨迹画布(3)<实时画布(4)。坐标系=paper-body/sheet-body 左上角原点(两页同为 496px 宽、正文 y=0 起,编辑/查看严格一致)。笔触:签字笔实色圆头(alpha 0.95)/铅笔两遍抖动(0.3)/蜡笔三遍抖动(0.28)/荧光笔宽平头(butt cap,≥8px)/画笔随速度变宽(EMA 平滑);抖动纹理用 mulberry32 笔画种子确定性渲染防闪变;笔画透明度 `a`(0-1)与笔型基础 alpha 相乘。橡皮:像素橡皮按半径切断笔画(剩余点串拆新笔画)、对象橡皮整笔删除;撤销历史=笔画数组引用快照(不可变更新)。数据 `{v:1,strokes:[{t,c,w,a,s,pts}]}` 存 `content_diary.doodle` JSON;查看页双层画布(荧光/墨迹)随正文同窗口裁剪显示。
37. **相册列表页**(`/album`):顶部工具栏(`.page-toolbar.card`)常规态:tb-left=搜索框→来源筛选(全部/本地上传/各映射设备名)→类型筛选(全部/公开/私有),tb-right=选择(ghost)→同步→新建(primary);选择态:tb-right=选中计数(`.select-count` 13px 次要色)+取消+删除所选(danger,默认尺寸与常规按钮同规格),常规按钮隐藏。卡片:默认封面暖棕文件夹内联 SVG(AlbumDefaultCover,无照片无自定义封面时);来源角标(设备名+状态点:绿 VALID/灰 OFFLINE/红 MISSING);子相册数角标;总数用子树合计;选中卡片暖棕 outline(3px inset)+左上 pick-badge 勾选计数,多选时隐藏卡片类型标签避让。选择模式一次性操作,不 localStorage 记忆;批量删除映射相册文案区分(解除映射)。
38. **相册详情页**(`/album/:id`):页头 album-head(padding 10px 16px 对齐工具栏规范)=面包屑层级返回(parents 父级链)+封面缩略+名称/描述+操作区。操作区常规态=视图切换 view-toggle(仅含子相册时)/选择(ghost)/分享(ghost,仅公开相册)/设置封面(ghost,创建者或 OWNER)/编辑/删除/上传照片(primary);选择态=选中计数+取消+删除所选(danger),独立多选工具条已并入操作区。子相册双视图 localStorage 记忆:方块(4:3 大封面卡片)与列表(单列横条:小封面+名称+照片数+状态点);照片网格缩略图 `&thumb=1` 只拼设备映射照片的签名 URL(本地 `/files/` 直链无 query 不拼参数);映射相册只读横幅(来源+上次刷新时间)+刷新按钮(OWNER),隐藏上传与编辑/删除;纯子相册相册(有子无照片)不显示照片空态;子相册+照片混合多选删除(先删子相册再删照片);后台同步完成 ElNotification 通知+页面自动刷新(watch syncStore.doneCount)。
39. **运维详细日志页**(`/ops` 详细日志 tab):tid+日期查询三类日志(access/server/thirdparty)按时间线合并;来源/级别标签配色(见 §20);ERROR/WARN 左侧色条;等宽字体消息区 max-height 420px 滚动,堆栈整块保留;操作日志 TID 列点击跳转自动查询;`/ops?tab=trace&tid=xxx` 路由直达;5xx 报错 toast 自带 `[tid:xxx]`。
