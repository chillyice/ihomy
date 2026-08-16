# ihomy 沉浸式首页 UI 设计提示词

> 本文件是当前已上线的沉浸式首页(方案 B「展开的相册」)的完整 UI 设计描述,可作为提示词喂给 AI 重新生成同等视觉效果的页面。所有数值均为实装值(V6.0)。

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

## 全局约定(V6.0 光影层全局化)

- 光影层(`SunLightLayer.vue`)+ 左侧导航栏(`AppSidebar.vue`)+ 右下角备案号(`SiteFooter.vue`)在 `App.vue` 全局挂载,所有页面共享。
- 首页不再自带顶栏和光影层,只负责面板+相册+唱片机+光照测试控制台。
- 全屏 `position: fixed` 布局,无滚动条。
- 主文字色 `#3A2E22`(深咖啡),点缀色 `#A8483A`(砖红,用于纪念日天数/通知类型)。
- 浅色背景(双层伪元素 `::before`):`linear-gradient(135deg, #EDE4D3 0%, #E2D8C4 50%, #D6CBB4 100%)`;深色背景(`::after`):`linear-gradient(135deg, #0F1A2E 0%, #162238 50%, #1A2540 100%)`;主题切换时 opacity 交叉淡入淡出 1s。
- 字体统一 `sans-serif`(`-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Microsoft YaHei', sans-serif`),不显式声明 serif。

## 分层 z-index(从底到顶,严格按此顺序)

| 层 | z-index | 作用 | 混合模式 |
|----|---------|------|----------|
| bg-blobs(伪元素) | 1 | 5 个飘移色块(模糊大圆) | normal |
| bright-spot(亮斑) | 32 | 阳光照耀强度(夜黑→晨黄→日透明→夕橙) | multiply |
| album-corner(拍立得堆) | 10 | 右下角散落拍立得/闭合相册 | normal |
| panels(左右毛玻璃) | 20 | 动态/任务/天气/纪念日/今日 | normal |
| window-shadow-lower(下层阴影) | 35 | 内框竖+内框横+顶框+底框 | darken |
| reflection-layer(反光) | 42 | 内容被阳光照亮的轻微高光 | soft-light |
| vignette(暗角) | 44 | 边缘压暗(台灯 mask 祛除阴影) | normal |
| dust-layer(灰尘) | 46 | 光路微粒(20 个,夜间不可见) | screen |
| light-layer(体积光) | 48 | 光束+辉光 | screen |
| window-shadow-upper(上层阴影) | 49 | 左框+右框(最顶层,盖住光柱) | darken |
| AppSidebar(导航栏) | 100 | 左侧导航+底部主题/台灯/用户 | normal |
| lamp-light(台灯) | 100 | 钟摆运动径向发光(夜间) | normal |
| SiteFooter(备案号) | 70 | 右下角 ICP+公安备案 | normal |

## 1. 背景色块(bg-blobs)

5 个 `position: absolute; border-radius: 50%; filter: blur(60px); opacity: 0.4` 的大圆,各自飘移动画(18-26s `ease-in-out infinite`):

| 编号 | 尺寸 | 位置 | 颜色 | 动画 |
|------|------|------|------|------|
| 1 | 480×480 | top:-120, left:-100 | #9CD0B5(绿) | drift1 22s |
| 2 | 560×560 | top:25%, right:-180 | #EDDB8C(黄) | drift2 26s |
| 3 | 420×420 | bottom:-120, left:18% | #ECC0AC(粉) | drift3 20s |
| 4 | 380×380 | top:35%, left:32% | #A8C9DE(蓝) | drift4 24s |
| 5 | 300×300 | bottom:20%, right:22% | #C0D8A8(绿黄) | drift5 18s |

动画为 `translate + scale` 组合,位移 ±120~240px,缩放 0.8~1.35。

## 2. 右下角相册模块(album-corner,V6.0 重构)

`position: fixed; right: 5vw; bottom: 5vh; width: 27.5vw; min-height: 308px; z-index: 10`。

### 散落拍立得堆(近 7 天有新照片)
- `recentPhotos` 过滤 `createdAt < 7天` 最多 7 张。
- 每张 `.polaroid-pos`(定位包装)+ `.polaroid`(hover 缩放)分离 transform 上下文防频闪。
- `.polaroid`: 220px 白边相纸 `padding: 10px 10px 38px`,随机旋转 ±15°、随机偏移 dx±120 dy±60、投影。
- hover:z-index 99 + 旋转归零 + scale 1.08(抽出感)。
- GSAP stagger 入场。
- 点击 → `el-image-viewer` 全屏大图浏览(`viewerUrls` = 近期照片 URL 列表)。

### 闭合相册(近 7 天无新照片)
- 平躺木色封面 `linear-gradient(#8B6F47,#6B5435)` 420×315。
- 家庭名称 + "家庭相册",随机斜放。
- hover 抬正放大,点击跳 `/album`。

## 4. 体积光系统(light-layer,z-index 48)

`position: fixed; inset: 0; mix-blend-mode: screen; pointer-events: none; overflow: hidden`。基于真实太阳位置(`/api/public/sun-info` 返回 288 个 5 分钟时隙,前端取当前时隙)。

### 光照生命周期(V5.3)

旋转/颜色/强度全部由**日出日落时间**驱动(不再由方位角/高度角直接映射):

- **日昼进度** `dayProgress = (currentMin - sunriseMin) / (sunsetMin - sunriseMin)`,0=日出→1=日落,夜间 hold 在端点(日出前=0,日落后=1)。
- **旋转**:`rotation = (dayProgress - 0.5) * 180` → 日出 -90°→ 正午 0°→ 日落 +90°,夜间 hold。光柱角度 = 阴影角度(同向)。
- **光柱不透明度**:夜间 0(不发光),日间 `sin(dayProgress * π) * rayBaseOpacity`(正弦过渡,正午最强)。
- **夜间调色板全 transparent**:bloom/core/mid/ambient 均为 `transparent`,rayBaseOpacity=0 → 光源辉光和光束都不可见。

### 光源辉光(light-bloom)
- `position: absolute; width: 700px; height: 700px; margin-left: -350px; margin-top: -350px; border-radius: 50%; filter: blur(60px)`。
- `background: radial-gradient(circle, <bloom> 0%, <mid> 35%, transparent 70%)`。
- 位置:`left/top` = `source.x/source.y`。
- 夜间 bloom=transparent → 不可见。

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
- 夜间 opacity 全 0 → 不可见。

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

## 5. 亮斑图层(bright-spot,z-index 32)

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

## 6. 反光层(reflection-layer,z-index 42)

`position: fixed; inset: 0; mix-blend-mode: soft-light; pointer-events: none`。内容组件被阳光照亮的轻微高光。

- `background: radial-gradient(ellipse 60% 50% at <source.x> <source.y>, rgba(255,245,220,1) 0%, rgba(255,235,200,0.6) 30%, transparent 70%)`。
- 不透明度:夜间 0,日间 `sin(dayProgress * π) * 0.22`(正午最强,轻微)。
- soft-light 模式:效果极其微妙,只给内容组件顶部边缘加一点暖光。

## 7. 窗户阴影(V5.3 分层重构)

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
- `--rot: (dayProgress - 0.5) * 180 deg` → 日出 -90°→ 正午 0°→ 日落 +90°,夜间 hold。
- 内框横/顶框/底框不旋转(始终水平)。
- `--htop: clamp(5, 85, 80 - max(0,alt) * 0.8)`%(太阳越高→内框横越靠近顶部)。
- 窗户开口 85%:左右框 `translateX(±42.5vw)`。

## 6. 暗角(vignette,z-index 44)

`position: fixed; inset: 0; pointer-events: none`。
`background: radial-gradient(ellipse 90% 75% at 50% 42%, transparent 0%, transparent 55%, rgba(60,38,12,0.08) 80%, rgba(45,25,8,0.18) 100%)`。

## 7. 灰尘粒子(dust-layer,z-index 46)

`position: fixed; inset: 0; mix-blend-mode: screen; pointer-events: none; overflow: hidden`。
20 个粒子,每个:
- `position: absolute; border-radius: 50%; background: rgba(255,238,185,0.85); box-shadow: 0 0 8px rgba(255,225,150,0.7)`。
- 随机 `width/height` 2-5px,随机 `top/left`,随机 `animation-duration` 8-16s,随机 `--drift` 30-100px。
- 动画 `dust-float linear infinite`:0% opacity:0 → 15% opacity:0.9 → 85% opacity:0.9 → 100% `translate(var(--drift), calc(var(--drift) * -1.5))` opacity:0(向右上方飘移淡出)。

## 8. 全局导航栏(AppSidebar,z-index 100,V6.0 全局化)

- `position: fixed; left: 0; top: 0; bottom: 0; width: 220px`(收起 64px)。
- 温暖磨砂玻璃:`backdrop-filter: blur(24px) saturate(1.1)` + 暖奶油渐变背景。
- 顶部:家庭名(hover 暖色下划线)+ 折叠按钮。
- 导航列表:按 category 分组(内容/相册/生活/成员/系统),Element Plus 线性图标(Document/Notebook/Picture 等,非 emoji)。
- "系统"分组:下拉菜单(设置 + 运维管理,后者仅 OPS 可见)。
- 底部:主题切换(☀️/🌙)+ 台灯三态(🌑/💡/⬛,关灯时伪元素径向发光圈+脉冲动画)+ 语言切换 + 消息铃铛 + 用户头像胶囊。
- 深色模式:`background: rgba(30,40,65,0.55)` + `border-color: rgba(255,255,255,0.12)`。

## 9. 左侧毛玻璃面板(left-panel,z-index 20,V6.0 可拖拽)

- `position: fixed; top: 80px; left: 24px; width: 380px`(`useDragResize` 拖拽+调整大小,位置持久化 localStorage)。
- 顶部 `.drag-handle`(24px,中间 40×4px 拖动条),右下角 `.resize-handle`(20×20px 斜角)。

### 通用毛玻璃样式(glass-panel)
```css
background: rgba(255, 255, 255, 0.25);
backdrop-filter: blur(30px) saturate(1.4);
-webkit-backdrop-filter: blur(30px) saturate(1.4);
border: 1px solid rgba(255, 255, 255, 0.5);
border-radius: 28px;
box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.4);
color: #3A2E22;
```

### 动态流面板(feed-panel,上 45%)
- 标题:`padding: 28px 32px 14px; font-size: 15px; font-weight: 600; letter-spacing: 1px; opacity: 0.7`。
- 滚动区:`padding: 0 28px 24px`,隐藏滚动条。
- 每条 feed 微信风:
  - `.feed-row`: `display: flex; gap: 10px; margin-bottom: 14px; cursor: pointer`。
  - 头像独立行(左)。
  - `.feed-content`: `flex: 1; margin-top: 2px`,包裹 nick + bubble。
  - `.feed-nick`: `font-size: 12px; font-weight: 600; line-height: 16px; margin-bottom: 2px`,单行截断。
  - `.feed-bubble`: `background: rgba(255,255,255,0.45); border-radius: 4px 14px 14px 14px; padding: 10px 14px`,hover `background: rgba(255,255,255,0.65); transform: translateX(-3px)`。
  - bubble 内:类型 12px/0.6,正文 14px/1.55/3 行截断,时间 11px/0.5 右对齐。

### 任务面板(task-panel,下 55%)
- 同样的标题 + 滚动区样式。
- 每行:`display: flex; align-items: center; gap: 14px; padding: 12px 14px; border-radius: 10px`,hover `background: rgba(58,46,34,0.06)`。
- 奖励图标 20px,标题 15px/600 单行截断,meta 12px/0.6。

## 10. 右侧毛玻璃面板(z-index 40)

### 时间天气面板(weather-panel)
- `position: fixed; top: 80px; right: 24px; width: 380px; padding: 22px 32px; text-align: center`。
- 时钟:`font-size: 32px; font-weight: 700; letter-spacing: 2px`。
- 日期:`font-size: 13px; opacity: 0.7; margin-bottom: 6px`。
- 天气:`display: flex; justify-content: center; gap: 4px; font-size: 15px`。图标 emoji 20px,温度 700,文字按 condition 映射中文(晴/多云/阴/雨/雪)。

### 纪念日面板(anniversary-panel)
- `position: fixed; top: 210px; right: 24px; width: 380px; max-height: 340px`。
- 每行:`padding: 12px; border-bottom: 1px solid rgba(58,46,34,0.08)`,hover `background: rgba(58,46,34,0.05); border-radius: 8px`。
- 名称 14px/600 单行截断,日期 12px/0.6。
- 倒计时天数:`font-size: 20px; font-weight: 700; color: #A8483A`,单位 12px/0.7。

## 11. 黑胶唱片播放器(vinyl-wrap,z-index 20,V6.0 可拖动)

- `position: fixed; left/top` 定位(可拖动,localStorage 持久化;区分点击/拖拽)。
- 唱片本体 120×120,`transition: transform 0.25s`。
- hover:scale 1.08 + 显示曲名(`trackDisplay` computed:正在播放/已暂停/未设置音乐)。
- 唱片盘:`radial-gradient(circle, #3A2E22 0%, #1A1410 30%, #2A2018 60%, #1A1410 100%)`,+ `repeating-radial-gradient` 凹槽纹理,中心红色标签 `radial-gradient(circle, #A8483A 0%, #6B2E26 100%)`。
- 播放时:`.vinyl-disc` `animation: spin 4s linear infinite`(旋转),`.vinyl-arm` `transform: rotate(20deg)`(唱臂落下)。
- 唱臂:`width: 5px; height: 60px; background: linear-gradient(to bottom, #C9A876, #8B6F47); transform-origin: top center; transform: rotate(-15deg)`(静止时抬起)。
- 歌曲信息(左侧):`background: rgba(255,255,255,0.7); backdrop-filter: blur(20px) saturate(1.4); border-radius: 16px; padding: 12px 18px`,默认 `opacity: 0; transform: translateX(20px); pointer-events: none`,hover `.show` 时淡入。标题 15px/700,艺术家 12px/0.6,控件 ⏮⏸▶⏭(18px,主控 22px)。

## 11.5 光照测试控制台(V6.0 内嵌首页)

- `position: fixed; left: 24px; bottom: 24px; width: 220px`(毛玻璃面板)。
- 信息面板:时间/高度角/方位角/日出/日落/阶段 + 进度条。
- 控件:后退/暂停/前进/停止按钮 + 天气控制(☀️/☁️/🌧️/❄️ weatherMultiplier 衰减光强)+ 色温/亮度滑块。
- 1 分钟循环 288 时隙(208ms/段),停止=重置真实时间+关闭控制台。
- `useSunLight.js` provide/inject 全局共享光影状态。

## 12. GSAP 入场动画

页面 mount 后:
```js
gsap.from('.left-panel', { x: -30, autoAlpha: 0, duration: 0.8, delay: 0.2 })
gsap.from('.weather-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.3 })
gsap.from('.anniversary-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.4 })
gsap.from('.polaroid-pos', { y: 20, autoAlpha: 0, duration: 0.5, stagger: 0.08, delay: 0.5 })
```
**光柱层不入场**,直接显示当前太阳状态(避免光从无到有的突兀)。

## 13. 响应式

| 断点 | 变化 |
|------|------|
| ≤1280px | album-stage padding 改 360px;左右面板宽度 320px |
| ≤960px | 左侧面板隐藏;album-stage padding 24px;书脊隐藏;天气面板 width auto/padding 10px 16px;时钟 26px;纪念日面板隐藏;唱片 90×90/margin-right -45px |

## 14. 数据流

- 页面 mount 时并发请求 `/public/sun-info` + `/public/weather` + `/public/home` + `/public/feed`。
- sun-info 返回 288 时隙(每 5 分钟一个),前端 `currentSlotIndex(sunInfo)` 按当前时间取 5 分钟时隙索引,`getSunScene(sunInfo, slotIndex)` 返回 `{source, rotation, palette, rays, shadowVRotation, shadowHTop, shadowIntensity, shadowGray, brightSpotColor, brightSpotOpacity, reflectionOpacity, altitude, azimuth, isNight, dayProgress}`。
- 旋转由日出日落时间驱动(dayProgress),夜间 hold 在端点待命;光柱夜间全透明不发光;亮斑夜间黑色压暗;反光夜间 0。
- 每 5 分钟更新一次时隙。
- 拍立得堆按近 7 天照片筛选,点击 `el-image-viewer` 全屏浏览。

## 验收标准

1. 打开页面,背景米白渐变 + 5 个色块缓慢飘移,右下角拍立得堆/闭合相册,左右毛玻璃面板从两侧滑入。
2. 日出时光柱从左上方斜射(角度 -90°),亮斑从黑色变黄,阴影最深;正午光柱垂直(0°),亮斑透明,阴影最浅;日落光柱从右上方射(+90°),亮斑变橙红;夜间全黑,光柱不可见,阴影最深待命在日落位置,凌晨 2 点跳变回日出角度(无人看,无感)。全天平滑过渡无扫光。
3. 光柱在相册和面板之上(screen 变亮),灰尘粒子在光路中漂浮发光;左右框(上层阴影 z=49)盖住光柱顶部。
4. 阴影重叠区域颜色不叠加变深(灰阶 darken 幂等)。
5. 内容组件(相册/面板)在日间有轻微反光高光(soft-light,跟随光源位置)。
6. 黑胶唱片可拖动,hover 放大并显示歌曲信息(正在播放/已暂停/未设置音乐)。
7. 全局导航栏(AppSidebar)左侧固定,含家庭名/模块导航/系统下拉(设置+运维)/主题切换/台灯三态/语言/铃铛/用户头像;关灯时按钮伪元素径向发光圈脉冲动画。
8. ≤960px 时左侧面板和纪念日隐藏,只保留相册 + 导航栏 + 紧凑天气 + 小唱片。
9. 首页左下角光照测试控制台:1 分钟循环 288 时隙,含时间/高度/方位/阶段 + 进度条 + 后退/暂停/前进/停止 + 天气控制(☀️/☁️/🌧️/❄️)+ 色温/亮度滑块;停止=重置真实时间+关闭控制台。
10. 夜间台灯自动开启(傍晚 dayProgress≥0.9 开,清晨 dayProgress>0.1 关),mask 祛除左上黄金分割点周围阴影;台灯钟摆运动(8 秒周期,横向 ±5vw,两侧椭圆中间圆);亮度滑块控制 mask 透明区域大小(3%-100%);色温滑块控制暖光色温。**开关灯 2s 渐变**:mask-image 不支持 CSS transition,用 GSAP 补间驱动 `lampAnim`(reactive),`lampDivOpacity` 和 `lampMask` 同步 2s 渐变(ease `power2.out`);mask 挖洞半径随强度缩放(开灯洞从 0 放大,关灯洞缩到 0)。
11. **主题切换 1s 过渡**:`linear-gradient` 背景不支持 CSS transition 插值,改为双层伪元素 `::before`(浅色)/`::after`(深色)opacity 交叉淡入淡出 1s;面板/色块/文字颜色同步 1s 过渡。
12. **可拖拽面板**:5 个面板(feed/task/weather/anniversary/today)可拖拽+可调大小,位置/大小持久化到 localStorage;拖拽边界 clamp(左右不越出页面、顶部不低于导航栏、底部不越界);全部用 `left` 定位 + 右下角 resize handle;Settings"恢复默认面板布局"清持久化恢复初始值。
13. **今日面板**:积分余额+连续天数+签到按钮+今日待办提醒前 3 条(登录可见)。
14. **备案号**:右下角 `right:16 bottom:8 z:70`,ICP+公安占位,磨砂玻璃小字。
15. **照片瀑布**(`/cascade`):落叶式飘落动画(包装元素分离 transform 防频闪),hover 暂停+scale 1.15,点击 `el-image-viewer`,每 2s 生成一张上限 50 张。
15. **深色模式**:`html.dark .blob { opacity: 0.1 }` 色块压暗至 10%;`--color-primary` 深色覆写 `#E8DCC8`;手动切主题后取消日出日落自动切换(`autoMode=false`)+提示。
16. **创建新家庭**:`POST /family` 已登录用户创建新家庭组(绑定 OWNER+切换当前家庭);Settings 页入口。
