# ihomy 沉浸式首页 UI 设计提示词

> 本文件是当前已上线的沉浸式首页(方案 B「展开的相册」)的完整 UI 设计描述,可作为提示词喂给 AI 重新生成同等视觉效果的页面。所有数值均为实装值。

---

## 角色

你是一名资深前端视觉工程师,精通 Vue3 + Element Plus + GSAP + CSS 滤镜/混合模式。请根据以下规格实现一个家庭沉浸式首页。

## 目标

实现"展开的相册"式沉浸式家庭首页:用户打开页面如同翻开一本摊在桌面上的家庭相册,窗外阳光随真实太阳位置斜射进来,光柱在相册和空气中形成丁达尔效应,灰尘微粒在光路中漂浮;左侧是家庭动态流和任务,右侧是时钟天气和纪念日倒计时,右下角半藏着一台黑胶唱片机。整体氛围温暖、旧物、慢节奏,像一个有阳光的午后。

## 技术栈

- Vue3 (Composition API, `<script setup>`)
- Element Plus(按需)
- GSAP(入场动画)
- 原生 CSS(毛玻璃用 `backdrop-filter`,光效用 `mix-blend-mode`)
- 数据来源:`GET /api/public/sun-info`(288 时隙太阳位置,可选 `?date=` 模拟任意日期)、`GET /api/public/weather`(天气)、`GET /api/public/home`(家庭聚合)、`GET /api/public/feed`(动态流)

## 全局约定

- 路由 `meta: { immersive: true }`,父级 App.vue 检测后隐藏全局顶栏/回到顶部/侧栏/音乐播放器,页面自带全部沉浸式 UI。
- 全屏 `position: fixed` 布局,无滚动条。
- 主文字色 `#3A2E22`(深咖啡),点缀色 `#A8483A`(砖红,用于纪念日天数/通知类型)。
- 米白渐变背景:`linear-gradient(135deg, #EDE4D3 0%, #E2D8C4 50%, #D6CBB4 100%)`。

## 分层 z-index(从底到顶,严格按此顺序)

| 层 | z-index | 作用 | 混合模式 |
|----|---------|------|----------|
| bg-blobs | 0 | 5 个飘移色块(模糊大圆) | normal |
| ambient-layer | 2 | 黄金时刻暖染 | multiply |
| album-base(牛皮纸) | 25 | 照片托底 | normal |
| album-stage(照片+相册) | 30 | 中央主舞台 | normal |
| bright-spot(亮斑) | 32 | 阳光照耀强度(夜黑→晨黄→日透明→夕橙) | multiply |
| window-shadow-lower(下层阴影) | 35 | 内框竖+内框横+顶框+底框 | darken |
| reflection-layer(反光) | 42 | 内容被阳光照亮的轻微高光 | soft-light |
| panels(左右毛玻璃) | 40 | 动态/任务/天气/纪念日 | normal |
| vignette(暗角) | 44 | 边缘压暗 | normal |
| dust-layer(灰尘) | 46 | 光路微粒 | screen |
| light-layer(体积光) | 48 | 光束+辉光 | screen |
| window-shadow-upper(上层阴影) | 49 | 左框+右框(最顶层,盖住光柱) | darken |
| top-bar(顶栏) | 50 | 导航+用户+通知 | normal |

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

## 2. 暖色环境光(ambient-layer)

`position: fixed; inset: 0; mix-blend-mode: multiply; transition: background 3s ease`。背景色由 `getSunScene()` 按太阳高度角返回的 `palette.ambient` 决定(黄金时刻 `rgba(255,165,80,0.12)` → 正午 `rgba(255,240,200,0.05)`)。

## 3. 中央相册舞台(album-stage)

- `position: absolute; top: 80px; bottom: 24px; left: 0; right: 0; display: flex; align-items: center; justify-content: center; padding: 0 420px`(左右给面板留位)。
- `.album-frame`: `width: 100%; height: 80%; max-width: 900px; position: relative`。
- `.album-photo`: `border-radius: 4px; overflow: hidden; box-shadow: 0 20px 60px rgba(0,0,0,0.5)`。
- 照片底部 caption:`linear-gradient(to top, rgba(0,0,0,0.25) 0%, transparent 100%)`,标题 20px/700,描述 15px/2 行截断,文字 `#F5EFE0`。
- 切换过渡:`opacity 0.8s ease`。

### 牛皮纸托底(album-base)
- `position: absolute; inset: -30px; z-index: 25; border-radius: 12px; pointer-events: none`。
- 纹理:两组 `repeating-linear-gradient`(±45° 极淡纹理)+ 主渐变 `linear-gradient(135deg, #D4B896 0%, #C9A876 50%, #B8956A 100%)`。
- 阴影:`box-shadow: 0 12px 40px rgba(100,70,30,0.25), 0 2px 8px rgba(0,0,0,0.1) inset`。

### 书脊(album-spine)
- 左边缘 `left: -18px; width: 6px; top:10%; bottom:10%`。
- `linear-gradient(to right, rgba(0,0,0,0.35), transparent)`。

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
40 个粒子,每个:
- `position: absolute; border-radius: 50%; background: rgba(255,238,185,0.85); box-shadow: 0 0 8px rgba(255,225,150,0.7)`。
- 随机 `width/height` 2-5px,随机 `top/left`,随机 `animation-duration` 8-16s,随机 `--drift` 30-100px。
- 动画 `dust-float linear infinite`:0% opacity:0 → 15% opacity:0.9 → 85% opacity:0.9 → 100% `translate(var(--drift), calc(var(--drift) * -1.5))` opacity:0(向右上方飘移淡出)。

## 8. 顶栏(top-bar,z-index 50)

- `position: fixed; top: 0; left: 0; right: 0; height: 64px; padding: 0 32px; display: flex; align-items: center; justify-content: space-between`。
- 背景:`linear-gradient(to bottom, rgba(255,255,255,0.5) 0%, rgba(255,255,255,0.5) 60%, transparent 100%)`。
- `pointer-events: none`(子元素 `auto`)。

### 左侧
- 家庭名:`font-size: 24px; font-weight: 700; color: #3A2E22; letter-spacing: 1px; cursor: pointer`。
- 主导航 5 个(nav-item):`font-size: 16px; color: rgba(58,46,34,0.85); padding: 4px 6px; border-radius: 4px`,hover `color: #3A2E22; background: rgba(58,46,34,0.08)`。
- "更多"下拉(nav-more):次级导航下拉。

### 右侧
- 语言切换:圆形触发器,显示当前语言缩写(中/EN),点击切换 `applyLocale`。
- 消息铃铛:el-popover + el-badge,`font-size: 20px; color: #3A2E22`。面板内 notify-list(max-height 320px),未读项 `background: rgba(168,72,58,0.06)`,类型标签 `color: #A8483A`。
- 用户头像 + 昵称:`border-radius: 20px; padding: 2px 8px 2px 2px`,hover `background: rgba(58,46,34,0.08)`。下拉:个人中心/设置/退出登录。
- 光照测试链接:小文字入口跳 `/light-test`。

## 9. 左侧毛玻璃面板(left-panel,z-index 40)

- `position: fixed; top: 80px; left: 24px; bottom: 24px; width: 380px; display: flex; flex-direction: column; gap: 16px`。

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

## 11. 黑胶唱片播放器(vinyl-wrap,z-index 40)

- `position: fixed; bottom: 80px; right: 0; display: flex; align-items: center; gap: 12px`。
- 唱片本体 120×120,`margin-right: -60px`(半藏右边界),`transition: transform 0.25s, margin-right 0.25s`。
- hover:`margin-right: 0; transform: scale(1.08)`(滑出 + 放大)。
- 唱片盘:`radial-gradient(circle, #3A2E22 0%, #1A1410 30%, #2A2018 60%, #1A1410 100%)`,+ `repeating-radial-gradient` 凹槽纹理,中心红色标签 `radial-gradient(circle, #A8483A 0%, #6B2E26 100%)`。
- 播放时:`.vinyl-disc` `animation: spin 4s linear infinite`(旋转),`.vinyl-arm` `transform: rotate(20deg)`(唱臂落下)。
- 唱臂:`width: 5px; height: 60px; background: linear-gradient(to bottom, #C9A876, #8B6F47); transform-origin: top center; transform: rotate(-15deg)`(静止时抬起)。
- 歌曲信息(左侧):`background: rgba(255,255,255,0.7); backdrop-filter: blur(20px) saturate(1.4); border-radius: 16px; padding: 12px 18px`,默认 `opacity: 0; transform: translateX(20px); pointer-events: none`,hover `.show` 时淡入。标题 15px/700,艺术家 12px/0.6,控件 ⏮⏸▶⏭(18px,主控 22px)。

## 12. GSAP 入场动画

页面 mount 后:
```js
gsap.from('.left-panel', { x: -30, autoAlpha: 0, duration: 0.8, delay: 0.2 })
gsap.from('.weather-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.3 })
gsap.from('.anniversary-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.4 })
gsap.from('.album-stage', { scale: 0.95, autoAlpha: 0, duration: 1, delay: 0.1 })
gsap.from('.top-bar', { y: -20, autoAlpha: 0, duration: 0.6 })
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
- 相册照片每 8 秒自动切换(可手动点击)。

## 验收标准

1. 打开页面,背景米白渐变 + 5 个色块缓慢飘移,右下角拍立得堆/闭合相册,左右毛玻璃面板从两侧滑入。
2. 日出时光柱从左上方斜射(角度 -90°),亮斑从黑色变黄,阴影最深;正午光柱垂直(0°),亮斑透明,阴影最浅;日落光柱从右上方射(+90°),亮斑变橙红;夜间全黑,光柱不可见,阴影最深待命在日落位置,凌晨 2 点跳变回日出角度(无人看,无感)。全天平滑过渡无扫光。
3. 光柱在相册和面板之上(screen 变亮),灰尘粒子在光路中漂浮发光;左右框(上层阴影 z=49)盖住光柱顶部。
4. 阴影重叠区域颜色不叠加变深(灰阶 darken 幂等)。
5. 内容组件(相册/面板)在日间有轻微反光高光(soft-light,跟随光源位置)。
6. 黑胶唱片半藏右下角,hover 滑出放大并显示歌曲信息。
7. 顶栏头像下拉、语言切换、消息铃铛、台灯开关(🌑自动/💡开/⬛关)+ 色温/亮度滑块均可交互。
8. ≤960px 时左侧面板和纪念日隐藏,只保留相册 + 顶栏 + 紧凑天气 + 小唱片。
9. `/light-test` 测试页可模拟任意日期(默认夏至 2026-06-21),1 分钟循环 288 时隙,含日期选择器 + 首页内容组件 + 阶段标签 + 深浅模式切换 + 台灯控制台(开关/色温/亮度/钟摆)。
10. 夜间台灯自动开启(傍晚 dayProgress≥0.9 开,清晨 dayProgress>0.1 关),mask 祛除左上黄金分割点周围阴影;台灯钟摆运动(8 秒周期,横向 ±5vw,两侧椭圆中间圆);亮度滑块控制 mask 透明区域大小(3%-100%);色温滑块控制暖光色温。
