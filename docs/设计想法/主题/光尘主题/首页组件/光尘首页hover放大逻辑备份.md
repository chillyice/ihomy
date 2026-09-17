# 光尘首页 hover 放大逻辑备份

> 本文件是 `frontend/src/views/Home.vue`（光尘主题自由布局仪表盘）中「P2 hover 放大 + 推开邻居」逻辑的**原样备份**。
> 该逻辑已从当前分支的 `Home.vue` 移除，原因是「调整位置」与「hover 变大」冲突（拖动卡片时 hover 放大用 GSAP 改真实 left/top/width/height，与拖拽抢焦点）。
> 如需恢复，按下文「恢复指引」逐块放回 `Home.vue` 即可。

## 一、移除原因

光尘首页组件支持直接拖拽调整位置（`.drag-bar`）与右下角缩放（`.resize-corner`，正常模式 hover 即浮现）。同时组件有 hover 放大 + 推开邻居的 P2 动效：被悬停的卡片用 GSAP 把真实尺寸（left/top/width/height）弹性放大，邻居用 transform 推挤/缩成胶囊。两者都操作同一批 DOM 的几何，导致「拖位置」和「hover 变大」互相干扰。故本次把 hover 放大相关逻辑整体移除，仅保留拖拽/缩放与相册翻页等无关动效。

## 二、被移除的模板片段

### 1. 卡片根节点（`v-for` 内 `.dash-card`）

移除前（含 `:ref`、hover 类、`:style`）：

```html
<div
  :ref="el => setCardEl(w.uid, el)"
  class="dash-card"
  :class="[w.id, { 'edit-active': editMode, dragging: w._dragging, 'h-1': tinyCard(w), 'is-hovered': hoverExpands && hoverUid === w.uid, 'is-neighbor': isFullNeighbor(w) }]"
  :style="cardBoxStyle(w)"
  @mouseenter="onCardEnter(w)"
  @mouseleave="onCardLeave"
  @click="bringToFront(w)"
>
```

移除后：

```html
<div
  class="dash-card"
  :class="[w.id, { 'edit-active': editMode, dragging: w._dragging, 'h-1': tinyCard(w) }]"
  :style="cardStyle(w)"
  @mouseenter="onCardEnter(w)"
  @mouseleave="onCardLeave"
  @click="bringToFront(w)"
>
```

> `@mouseenter`/`@mouseleave` 保留，`onCardEnter`/`onCardLeave` 现仅负责相册封面翻页（见第三节末尾）。

### 2. 邻居胶囊（`card-inner` 之前）

移除前：

```html
<!-- 邻居胶囊:全盖级邻居原位缩成图标+文字;若仍重叠则进一步缩成纯图标小圆角矩形(部分盖→降档,擦边→轻微缩小) -->
<div v-if="isFullNeighbor(w)" class="neighbor-capsule" :class="{ 'nc-icon-only': neighborIconOnly(w) }">
  <span class="nc-icon">{{ WIDGET_ICONS[w.id] || '' }}</span>
  <span v-if="!neighborIconOnly(w)" class="nc-label">{{ WIDGET_LABELS[w.id] }}</span>
</div>
```

## 三、被移除的脚本逻辑（`<script setup>` 内，原第 566~761 行）

```js
// ========== P2 hover 放大 + 推开邻居(排版方案 §二,三档邻居:全盖→胶囊 / 部分盖→降档缩小 / 擦边→轻微缩小) ==========
const HOVER_DELAY = 350
// 触摸/平板只览不展开(探询 §7):仅「支持 hover + 精确指针」设备参与放大/推挤/翻牌,避免 tap 误触
const HOVER_CAPABLE = typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(hover: hover)').matches && window.matchMedia('(pointer: fine)').matches
const hoverUid = ref(null)
let hoverTimer = null
// 拖拽/缩放进行中:抑制 hover 放大展开,避免与拖拽抢焦点(正常模式即可直接拖拽调整)
const isDragging = ref(false)

// 放大比例:普通组件 小档 2→3(1.5x)/中档 3→4(1.33x),大/巨大不放大;
// 天气为「主角」:hover 直接放大到 5×5 巨大档(scale = 5 / 较长边,≥1),已 XL 不放大
const WEATHER_HERO = 5
const hoverScale = (hw) => {
  // 天气「主角」:hover 放大到 5 格(画布比例 × 列/行数 = 等效格宽/格高),已 XL 不放大
  if (hw.id === 'weather') return displayTier(hw) === 'XL' ? 1 : Math.max(1, WEATHER_HERO / Math.max(hw.w * COLS, hw.h * ROWS))
  return ({ S: 1.5, M: 4 / 3, L: 1, XL: 1 }[displayTier(hw)] ?? 1)
}
// 判断邻居 n 是否被放大后的组件 h 影响:与 clamp 后的放大矩形(px)相交即受影响
const hoverAffects = (h, n) => {
  const sc = hoverScale(h)
  if (sc <= 1) return false
  const r = hoverRectPx(h)
  const N = cardRectPx(n)
  return N.left < r.left + r.width && N.left + N.width > r.left && N.top < r.top + r.height && N.top + N.height > r.top
}
// ---- 推开邻居三档(方案二:按覆盖比例)----
// 全盖 ≥70% → 胶囊(缩 0.55+位移);部分盖 ≥30% → 降档缩小(内容还在,少显示一些);擦边 → 轻微缩小 0.85、内容不变
const NEIGHBOR_FULL = 0.7
const NEIGHBOR_PARTIAL = 0.3
const NEIGHBOR_SCALE_FULL = 0.55
const NEIGHBOR_SCALE_ICON = 0.32 // 胶囊缩小后仍与放大矩形重叠时,进一步缩成纯图标小圆角矩形
const NEIGHBOR_SCALE_GRAZE = 0.85
// 放大矩形盖住邻居 n 的面积占比(0~1)
const hoverCoverage = (h, n) => {
  const r = hoverRectPx(h)
  const N = cardRectPx(n)
  const ox = Math.max(N.left, r.left)
  const oy = Math.max(N.top, r.top)
  const ox2 = Math.min(N.left + N.width, r.left + r.width)
  const oy2 = Math.min(N.top + N.height, r.top + r.height)
  if (ox >= ox2 || oy >= oy2) return 0
  return ((ox2 - ox) * (oy2 - oy)) / (N.width * N.height)
}
// 覆盖等级:full / partial / graze(S 档最小无法降档,部分盖也按全盖处理成胶囊)
const neighborLevel = (h, n) => {
  const c = hoverCoverage(h, n)
  if (c >= NEIGHBOR_FULL || (c >= NEIGHBOR_PARTIAL && displayTier(n) === 'S')) return 'full'
  if (c >= NEIGHBOR_PARTIAL) return 'partial'
  return 'graze'
}
// 模板/tierOf 用:某组件当前是哪个覆盖等级的邻居(非邻居返回 '')
const neighborLevelOf = (w) => {
  if (!HOVER_CAPABLE || editMode.value || !hoverExpands.value || hoverUid.value === w.uid) return ''
  const hovered = widgets.value.find(x => x.uid === hoverUid.value)
  if (!hovered || !hoverAffects(hovered, w)) return ''
  return neighborLevel(hovered, w)
}
const isFullNeighbor = (w) => neighborLevelOf(w) === 'full'
const isPartialNeighbor = (w) => neighborLevelOf(w) === 'partial'
// full 邻居缩成胶囊后仍被覆盖 → 纯图标小圆角矩形态(模板据此隐藏文字)
const neighborIconOnly = (w) => {
  if (!HOVER_CAPABLE || editMode.value || !hoverExpands.value || hoverUid.value === w.uid) return false
  const hovered = widgets.value.find(x => x.uid === hoverUid.value)
  if (!hovered || !hoverAffects(hovered, w)) return false
  if (neighborLevel(hovered, w) !== 'full') return false
  return stillOverlapsAfter(hovered, w, NEIGHBOR_SCALE_FULL)
}

// 生效档位:被 hover 的组件临时升一档;被部分盖的邻居临时降一档(内容随之少一些,全盖邻居缩成胶囊);其余保持基础档
const TIER_UP = { S: 'M', M: 'L' }
const TIER_DOWN = { XL: 'L', L: 'M', M: 'S' }
const DOWN_SCALE = { XL: 0.8, L: 0.75, M: 2 / 3 }
const downScaleOf = (w) => DOWN_SCALE[displayTier(w)] ?? NEIGHBOR_SCALE_FULL
const tierOf = (w) => {
  const base = displayTier(w)
  if (editMode.value || !HOVER_CAPABLE) return base
  if (hoverUid.value === w.uid) return w.id === 'weather' ? 'XL' : (TIER_UP[base] || base)
  if (isPartialNeighbor(w)) return TIER_DOWN[base] || base
  return base
}
// 当前悬停组件是否真的会放大:天气放大到巨大(XL 前都放大),其余仅中小档放大——只有放大时才推挤邻居
const hoverExpands = computed(() => {
  if (!HOVER_CAPABLE || editMode.value || isDragging.value) return false
  const h = widgets.value.find(x => x.uid === hoverUid.value)
  if (!h) return false
  if (h.id === 'weather') return displayTier(h) !== 'XL'
  return displayTier(h) === 'S' || displayTier(h) === 'M'
})

const setHover = (w) => {
  if (editMode.value || isDragging.value || !HOVER_CAPABLE) return
  if (hoverTimer) clearTimeout(hoverTimer)
  hoverTimer = setTimeout(() => { hoverUid.value = w.uid }, HOVER_DELAY)
}
const clearHover = () => {
  if (hoverTimer) { clearTimeout(hoverTimer); hoverTimer = null }
  hoverUid.value = null
}
const onCardEnter = (w) => { setHover(w); if (w.id === 'album') onAlbumEnter() }
const onCardLeave = () => { clearHover(); onAlbumLeave() }

// 卡片 px 矩形(cardStyle 同源画布比例数学)
const cardRectPx = (w) => {
  const width = w.w * canvasW.value
  const height = w.h * canvasH.value
  const left = MARGIN.left + w.x * canvasW.value
  const top = MARGIN.top + w.y * canvasH.value
  return { left, top, width, height, cx: left + width / 2, cy: top + height / 2 }
}
// 放大矩形 px:以卡片中心为锚等比放大,再 clamp 进画布(边缘组件向内收)
const hoverRectPx = (h) => {
  const sc = hoverScale(h)
  const base = cardRectPx(h)
  const width = base.width * sc
  const height = base.height * sc
  const left = Math.min(Math.max(base.cx - width / 2, MARGIN.left), MARGIN.left + canvasW.value - width)
  const top = Math.min(Math.max(base.cy - height / 2, MARGIN.top), MARGIN.top + canvasH.value - height)
  return { left, top, width, height, cx: left + width / 2, cy: top + height / 2 }
}
// 原位缩放到 scale 后是否仍与放大矩形重叠(不移位,以邻居中心为锚点缩)
const stillOverlapsAfter = (hovered, n, scale) => {
  const R = hoverRectPx(hovered)
  const N = cardRectPx(n)
  const nW = N.width * scale, nH = N.height * scale
  const nL = N.cx - nW / 2, nR = N.cx + nW / 2
  const nT = N.cy - nH / 2, nB = N.cy + nH / 2
  return nL < R.left + R.width && nR > R.left && nT < R.top + R.height && nB > R.top
}
// 邻居缩放的 transform-origin:锚定在「天气中心→邻居中心」射线在邻居远边界上的出射点——
// 连续点位(边的任意位置,不限于中点/角),使邻居向该点退缩、近边被拉开
const neighborOrigin = (hovered, n) => {
  const R = hoverRectPx(hovered)
  const N = cardRectPx(n)
  const dx = N.cx - R.cx, dy = N.cy - R.cy
  if (dx === 0 && dy === 0) return '50% 50%'
  const halfW = N.width / 2, halfH = N.height / 2
  const tx = dx === 0 ? Infinity : halfW / Math.abs(dx)
  const ty = dy === 0 ? Infinity : halfH / Math.abs(dy)
  const t = Math.min(tx, ty)
  const px = N.cx + dx * t
  const py = N.cy + dy * t
  const ox = ((px - N.left) / N.width) * 100
  const oy = ((py - N.top) / N.height) * 100
  return `${ox.toFixed(2)}% ${oy.toFixed(2)}%`
}
// 邻居 GSAP 动效目标(纯 transform,不移位):被 hover 的卡片由 applyHoverTweens 做真实尺寸增长,不缩放字号
const neighborTarget = (w) => {
  if (editMode.value || !HOVER_CAPABLE || !hoverExpands.value) return null
  if (hoverUid.value === w.uid) return null
  const hovered = widgets.value.find(x => x.uid === hoverUid.value)
  if (!hovered || !hoverAffects(hovered, w)) return null
  const level = neighborLevel(hovered, w)
  if (level === 'graze') return { x: 0, y: 0, scale: NEIGHBOR_SCALE_GRAZE, origin: neighborOrigin(hovered, w) }
  // partial:原位降档缩小(不移位),内容少显示一些
  if (level === 'partial') return { x: 0, y: 0, scale: downScaleOf(w), origin: neighborOrigin(hovered, w) }
  // full:原位缩成胶囊(图标+文字);若缩小后仍与放大矩形重叠,进一步缩成纯图标小圆角矩形(仍不移位)
  const iconOnly = stillOverlapsAfter(hovered, w, NEIGHBOR_SCALE_FULL)
  return { x: 0, y: 0, scale: iconOnly ? NEIGHBOR_SCALE_ICON : NEIGHBOR_SCALE_FULL, origin: neighborOrigin(hovered, w) }
}
// 仅 z-index(非动画,进 cardBoxStyle 响应式样式):被 hover 的卡片 80,邻居 10
const hoverZ = (w) => {
  if (!HOVER_CAPABLE || editMode.value || !hoverExpands.value) return null
  if (hoverUid.value === w.uid) return 80
  const hovered = widgets.value.find(x => x.uid === hoverUid.value)
  if (!hovered || !hoverAffects(hovered, w)) return null
  return 10
}
const cardBoxStyle = (w) => {
  // 基础位置 + z-index(被 hover 卡片 80 / 邻居 10);真实尺寸增长由 applyHoverTweens 用 GSAP 接管,不缩放字号
  const s = cardStyle(w)
  const hz = hoverZ(w)
  if (hz) s.zIndex = hz
  return s
}
// ---- GSAP 弹性动效:被 hover 卡片真实尺寸增长 + 邻居 transform 推挤/缩放,统一 elastic.out(回弹/水波) ----
const cardEls = {}
const setCardEl = (uid, el) => { if (el) cardEls[uid] = el; else delete cardEls[uid] }
const applyHoverTweens = () => {
  gsap.killTweensOf('.dash-card')
  for (const w of visibleWidgets.value) {
    const el = cardEls[w.uid]
    if (!el) continue
    const isHovered = hoverExpands.value && hoverUid.value === w.uid && !editMode.value
    const nt = neighborTarget(w)
    // 真实尺寸目标:被 hover 卡片扩到放大矩形(不缩放字号),其余回到基础矩形(离开 hover 时复原)
    const rect = isHovered ? hoverRectPx(w) : cardRectPx(w)
    const vars = {
      left: rect.left, top: rect.top, width: rect.width, height: rect.height,
      x: nt ? nt.x : 0, y: nt ? nt.y : 0, scale: nt ? nt.scale : 1,
      duration: 0.7, ease: 'elastic.out(1, 0.5)',
    }
    if (nt) vars.transformOrigin = nt.origin // 邻居缩放锚点;复位时不改 origin,沿用当前避免缩放基准点跳变
    gsap.to(el, vars)
  }
}
watch([hoverUid, hoverExpands, editMode], () => { nextTick(applyHoverTweens) })
```

### 移除后保留的等价最小逻辑

```js
// 相册封面翻开依赖:仅「支持 hover + 精确指针」设备参与翻页(触摸设备 tap 会误触)
const HOVER_CAPABLE = typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(hover: hover)').matches && window.matchMedia('(pointer: fine)').matches

// 生效档位:仅按组件实际尺寸推导(hover 放大/推开邻居逻辑已移除,见本文件)
const tierOf = (w) => displayTier(w)

const onCardEnter = (w) => { if (w.id === 'album') onAlbumEnter() }
const onCardLeave = () => { onAlbumLeave() }
```

> `HOVER_CAPABLE` 仍被相册封面翻页 `onAlbumEnter`（`if (!HOVER_CAPABLE) return`）使用，故保留；`tierOf` 被各组件四档内容条数（`nFeed/nTask/...`）与天气预报（`showForecast/forecastDays`）使用，简化为直接返回 `displayTier`。

## 四、被移除的拖拽/缩放中的 hover 抑制

`onDragStart` / `onResizeStart` 中移除两行，`onMouseUp` 中移除一行（均因 `isDragging`/`clearHover` 随 hover 逻辑一并删除）：

```js
  w._dragging = true
  isDragging.value = true   // 删除
  clearHover()              // 删除
  zCounter = Math.min(zCounter + 1, 59)
```

```js
  dragState = null
  isDragging.value = false  // 删除
  window.removeEventListener('mousemove', onMouseMove)
```

## 五、被移除的样式（`<style scoped>` 内）

### 1. P2 hover 样式块

```css
/* ===== P2 hover:放大的卡片 + 被推开的邻居(全盖→胶囊/部分盖→降档缩小/擦边→轻微缩小) + 波浪引导 ===== */
.dash-card.is-hovered { box-shadow: 0 20px 52px rgba(58,46,34,0.24); transition: box-shadow 0.3s ease; }
html.dark .dash-card.is-hovered { box-shadow: 0 20px 52px rgba(0,0,0,0.4); }
/* 邻居 transform 由 GSAP elastic.out 接管(水波弹性形变),此处只隐藏内容显示胶囊 */
.dash-card.is-neighbor .card-inner { opacity: 0; pointer-events: none; }
.neighbor-capsule { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; z-index: 5; }
.neighbor-capsule .nc-icon { font-size: 20px; line-height: 1; }
.neighbor-capsule .nc-label { font-size: 11px; font-weight: 600; opacity: 0.7; }
.neighbor-capsule.nc-icon-only .nc-icon { font-size: 16px; }
```

### 2. 天气 XL 悬停透底样式

```css
/* 巨大档(XL)悬停时:内容面板更透,让 AI 生图背景透出来,文字区靠局部描边/投影保证可读 */
.dash-card.weather.is-hovered .weather-main { background: rgba(255,255,255,0.34); backdrop-filter: blur(6px) saturate(1.15); -webkit-backdrop-filter: blur(6px) saturate(1.15); border-color: rgba(255,255,255,0.35); box-shadow: 0 2px 14px rgba(58,46,34,0.16), 0 0 0 1px rgba(255,255,255,0.12) inset; text-shadow: 0 1px 2px rgba(255,255,255,0.5); }
html.dark .dash-card.weather.is-hovered .weather-main { background: rgba(var(--color-card-rgb),0.4); border-color: rgba(255,255,255,0.16); text-shadow: 0 1px 3px rgba(0,0,0,0.5); }
```

## 六、恢复指引

若要恢复 hover 放大逻辑，按以下顺序放回 `frontend/src/views/Home.vue`：

1. 模板：恢复卡片根节点的 `:ref="el => setCardEl(w.uid, el)"`、`:class` 里的 `'is-hovered'`/`'is-neighbor'`、`:style="cardBoxStyle(w)"`，以及 `card-inner` 之前的邻居胶囊片段。
2. 脚本：把第三节整段（原 566~761 行）替换回第三节末尾「移除后保留的等价最小逻辑」的位置；同时把 `tierOf` 改回带 hover 升档的版本。
3. 拖拽/缩放：恢复 `isDragging` 声明与三处赋值、`clearHover()` 两处调用。
4. 样式：恢复第五节两段 CSS。
5. `WIDGET_ICONS`（`{ feed:'👥', task:'🎯', today:'📅', weather:'🌤', anni:'🎂', recipe:'🍳', search:'🔍', wish:'⭐', finance:'💰', album:'📷' }`）随邻居胶囊一并被移除，恢复时需在 `WIDGET_LABELS` 旁补回。
