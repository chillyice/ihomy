// 主题系统:两轴矩阵——主题(theme,装修风格)× 晨暮(mode,时间光照档)
// theme ∈ 'warm'(暖居,现有配色)| 'guangchen'(光尘,新旗舰);mode ∈ 'dawn'(晨/浅)| 'dusk'(暮/深)
// 应用方式:html.theme-{id} + html.dark(=dusk,驱动 Element Plus 暗色 css-vars)
// 持久化 key 沿用 'ihomy-theme',做旧 {dark, autoMode} → {theme, mode, autoMode} 迁移

export const THEME_STORAGE_KEY = 'ihomy-theme'

export const THEMES = {
  warm: {
    id: 'warm',
    label: { zh: '暖居', en: 'Warm Dwelling' },
    meta: { dawn: '#EDE4D3', dusk: '#0F1A2E' },
  },
  guangchen: {
    id: 'guangchen',
    label: { zh: '光尘', en: 'Light & Dust' },
    meta: { dawn: '#F1E7D6', dusk: '#241A12' },
  },
}

export const THEME_IDS = Object.keys(THEMES)

export const DEFAULT_THEME = { theme: 'warm', mode: 'dawn', autoMode: true }

// 旧格式迁移:{dark, autoMode} → {theme, mode, autoMode}
function migrate(raw) {
  if (raw && (raw.theme || raw.mode)) return raw
  return {
    theme: 'warm',
    mode: raw?.dark ? 'dusk' : 'dawn',
    autoMode: raw?.autoMode ?? true,
  }
}

export function loadTheme() {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    if (!raw) return { ...DEFAULT_THEME }
    const m = migrate(JSON.parse(raw))
    if (!THEMES[m.theme]) m.theme = DEFAULT_THEME.theme
    if (m.mode !== 'dawn' && m.mode !== 'dusk') m.mode = DEFAULT_THEME.mode
    return { ...DEFAULT_THEME, ...m }
  } catch {
    return { ...DEFAULT_THEME }
  }
}

// —— 晨暮切换扫光(光尘专属):克隆旧主题整页 DOM 为幕布,方向性柔和蒙版从一侧划到另一侧露出新主题 ——
// 太阳方位角/高度角上下文,由 useSunLight 每次刷新场景时写入(模块级单例,跨 store/composable 共享)
let _sun = { azimuth: 180, altitude: 0, isNight: true }
export function setSunContext(ctx) {
  if (!ctx || ctx.azimuth == null) return
  _sun = { azimuth: ctx.azimuth, altitude: ctx.altitude ?? 0, isNight: !!ctx.isNight }
}

// 上次已应用状态(区分「首次加载」与「真实切换」,首次不扫光)
let _current = null

// 计算「晨→暮」暗色扫过方向(单位向量,屏幕坐标 y 向下)
// 规则:暗色沿「垂直于光束」的方向扫过,取「暮光从下往上蔓延」的一侧——
//   上午~正午(太阳在东/正南)向右上扫;下午(太阳在西)向左上扫;正午纯水平向右;夜晚无定向光默认水平向右。
//   (方位角约定 90=东/180=南/270=西,与 windowLight.sourceX 东左西右一致)
function sweepVector(sun) {
  if (sun.isNight) return { dx: 1, dy: 0 }
  const az = sun.azimuth
  const bx = -Math.max(-1, Math.min(1, (az - 180) / 90)) // 上午>0、正午=0、下午<0
  const by = Math.max(0, Math.sin((sun.altitude || 0) * Math.PI / 180)) // 高度角→光束向下分量 0..1
  const dx = bx >= 0 ? by : -by
  const dy = bx >= 0 ? -bx : bx
  const len = Math.hypot(dx, dy) || 1
  return { dx: dx / len, dy: dy / len }
}

// 克隆当前(旧主题)整页 DOM 为「旧主题幕布」,冻结旧主题 CSS 变量与滚动位置;
// 之后蒙版软边从一侧划到另一侧,划过去的地方露出(已切换的)新主题,未划到的仍是旧主题的真实渲染。
function cloneOldTheme(root) {
  const overlay = document.createElement('div')
  overlay.className = 'theme-sweep-old'

  const app = document.getElementById('app')
  if (app) {
    const clone = app.cloneNode(true)
    clone.removeAttribute('id')
    overlay.appendChild(clone)
    copyScroll(app, clone)
  }

  // 冻结旧主题 CSS 变量:把 html 当前所有 --* 计算值内联到幕布,后代 var() 不再跟随 html 类切换
  const cs = getComputedStyle(root)
  for (let i = 0; i < cs.length; i++) {
    const name = cs[i]
    if (name.startsWith('--')) overlay.style.setProperty(name, cs.getPropertyValue(name))
  }
  // 幕布底色:旧主题背景色(克隆 #app 本身透明,需自铺底色遮住 html 背景)
  const oldBg = cs.getPropertyValue('--color-bg').trim() || '#F1E7D6'
  overlay.style.background = oldBg

  document.body.appendChild(overlay)
  return overlay
}

// 克隆 DOM 不保留内部容器的滚动位置,需按结构一一回填(否则滚动页切换时旧主题会跳到顶部)
function copyScroll(orig, clone) {
  const ow = document.createTreeWalker(orig, NodeFilter.SHOW_ELEMENT)
  const cw = document.createTreeWalker(clone, NodeFilter.SHOW_ELEMENT)
  let o = ow.currentNode
  let c = cw.currentNode
  while (o && c) {
    if (o.scrollTop || o.scrollLeft) {
      c.scrollTop = o.scrollTop
      c.scrollLeft = o.scrollLeft
    }
    o = ow.nextNode()
    c = cw.nextNode()
  }
}

function beginModeSweep(root, prevDusk) {
  const toDusk = !prevDusk // true=晨→暮(暗色扫入),false=暮→晨(亮色扫入)
  const dir = sweepVector(_sun)
  const vx = toDusk ? dir.dx : -dir.dx // 扫光前进方向(新主题从该侧露出来)
  const vy = toDusk ? dir.dy : -dir.dy
  // 蒙版渐变轴:0%(透明=新主题已露出)在扫光来向一侧,100%(不透明=旧主题未划到)在前进方向一侧。
  // CSS linear-gradient 角度:0deg=向上/90deg=向右/180deg=向下/270deg=向左,故 angle=atan2(vx, -vy)。
  const angle = Math.atan2(vx, -vy) * 180 / Math.PI
  const grad = `linear-gradient(${angle}deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0) calc(var(--sweep-p) - 8%), #000 calc(var(--sweep-p) + 8%), #000 100%)`

  const overlay = cloneOldTheme(root)
  overlay.style.webkitMask = `${grad} no-repeat center / 100% 100%`
  overlay.style.mask = `${grad} no-repeat center / 100% 100%`
  overlay.style.setProperty('--sweep-p', '-10%')

  // 扫光期间禁 html/body/#app 的 1s 颜色过渡(否则与幕布交叉淡入打架)
  root.classList.add('theme-sweeping')

  return () => {
    let done = false
    const cleanup = () => {
      if (done) return
      done = true
      overlay.remove()
      root.classList.remove('theme-sweeping')
    }
    const travelMs = 1700
    try {
      overlay.style.transition = `--sweep-p ${travelMs}ms cubic-bezier(.4,0,.2,1)`
      // 先提交起点,下一帧再切到终点触发过渡(--sweep-p 已用 @property 注册为可过渡)
      requestAnimationFrame(() => requestAnimationFrame(() => {
        overlay.style.setProperty('--sweep-p', '110%')
      }))
    } catch {
      cleanup()
      return
    }
    overlay.addEventListener('transitionend', cleanup, { once: true })
    setTimeout(cleanup, travelMs + 300) // 兜底清理(过渡异常/被中断时)
  }
}

export function applyTheme(state) {
  const t = { ...DEFAULT_THEME, ...state }
  const root = document.documentElement
  const nextDusk = t.mode === 'dusk'
  const prevDusk = _current ? _current.mode === 'dusk' : null
  const isModeChange = prevDusk != null && prevDusk !== nextDusk
  const withinGuangchen = _current && _current.theme === 'guangchen' && t.theme === 'guangchen'

  // 光尘内晨↔暮切换:切类前克隆旧主题整页为幕布,切类后蒙版扫光露出新主题
  const finishSweep = isModeChange && withinGuangchen ? beginModeSweep(root, prevDusk) : null

  root.classList.remove(...THEME_IDS.map((id) => 'theme-' + id))
  root.classList.add('theme-' + t.theme)
  root.classList.toggle('dark', nextDusk)
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', THEMES[t.theme].meta[t.mode])
  localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(t))
  _current = { ...t }

  if (finishSweep) finishSweep()
  return t
}

export function initTheme() {
  return applyTheme(loadTheme())
}
