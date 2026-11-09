// 主题系统:两轴矩阵——主题(theme,装修风格)× 晨暮(mode,时间光照档)
// theme ∈ 'warm'(暖居,暖木/陶土·独立外壳)| 'guangchen'(光尘,奶油/夜色·侧栏);mode ∈ 'dawn'(晨/浅)| 'dusk'(暮/深)
// 应用方式:html.theme-{id} + html.dark(=dusk,驱动 Element Plus 暗色 css-vars)
// 持久化 key 沿用 'ihomy-theme',做旧 {dark, autoMode} → {theme, mode, autoMode} 迁移

export const THEME_STORAGE_KEY = 'ihomy-theme'

export const THEMES = {
  warm: {
    id: 'warm',
    label: { zh: '暖居', en: 'Warm Dwelling' },
    meta: { dawn: '#F1E7D6', dusk: '#241A12' },
  },
  guangchen: {
    id: 'guangchen',
    label: { zh: '光尘', en: 'Light & Dust' },
    meta: { dawn: '#EDE4D3', dusk: '#0F1A2E' },
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

// —— 启动期晨暮预判 ——
// 自动模式下本地存的 mode 是「上次应用时的档位」,隔一场(隔夜)再开就可能是错的:
// 首屏先按旧档位铺一层整页深/浅色,等 sun-info 回来才校正,看起来就是「整页纯色 → 突然变色」。
// 因此把最近一次 sun-info 的日出/日落缓存下来,启动时直接用当前时间算档位,首屏即是正确配色。
const SUN_WINDOW_KEY = 'ihomy:theme:sun-window'

// 由 useSunLight 每次拿到 sun-info 后写入(只留判定档位所需的三项,不缓存整个响应)
export function rememberSunWindow(data) {
  if (!data || !data.date || !data.sunrise || !data.sunset) return
  try {
    localStorage.setItem(SUN_WINDOW_KEY, JSON.stringify({ date: data.date, sunrise: data.sunrise, sunset: data.sunset }))
  } catch {
    // 隐私模式/配额异常时忽略:只是首屏配色的优化,不影响主题本身
  }
}

function minutesOf(text) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(text || '').trim())
  if (!m) return null
  return Number(m[1]) * 60 + Number(m[2])
}

function todayLocal() {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

// 缓存只认「今天」的:日期对不上就不猜,交给 sun-info 回来后校正。
// 日出日落是服务端家庭时区的墙上时间,与 windowLight.getSunScene 的判定口径一致(客户端与时区同时区)。
function modeFromSunWindow() {
  try {
    const w = JSON.parse(localStorage.getItem(SUN_WINDOW_KEY) || 'null')
    if (!w || w.date !== todayLocal()) return null
    const rise = minutesOf(w.sunrise)
    const set = minutesOf(w.sunset)
    if (rise == null || set == null || set <= rise) return null
    const d = new Date()
    const now = d.getHours() * 60 + d.getMinutes()
    return now >= rise && now < set ? 'dawn' : 'dusk'
  } catch {
    return null
  }
}

export function loadTheme() {
  let m
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    m = raw ? migrate(JSON.parse(raw)) : { ...DEFAULT_THEME }
  } catch {
    m = { ...DEFAULT_THEME }
  }
  if (!THEMES[m.theme]) m.theme = DEFAULT_THEME.theme
  if (m.mode !== 'dawn' && m.mode !== 'dusk') m.mode = DEFAULT_THEME.mode
  m = { ...DEFAULT_THEME, ...m }
  // 自动模式:首屏档位按缓存的日出日落算,不拿隔夜的旧档位先铺一屏
  if (m.autoMode) {
    const derived = modeFromSunWindow()
    if (derived) m.mode = derived
  }
  return m
}

// —— 晨暮切换扫光(暖居专属):克隆旧主题整页 DOM 为幕布,方向性柔和蒙版从一侧划到另一侧露出新主题 ——
// 太阳方位角/高度角上下文,由 useSunLight 每次刷新场景时写入(模块级单例,跨 store/composable 共享)
let _sun = { azimuth: 180, altitude: 0, isNight: true }
export function setSunContext(ctx) {
  if (!ctx || ctx.azimuth == null) return
  _sun = { azimuth: ctx.azimuth, altitude: ctx.altitude ?? 0, isNight: !!ctx.isNight }
}

// 上次已应用状态(区分「首次加载」与「真实切换」,首次不扫光)
let _current = null

// 首帧跑过之前不扫光:浏览器把本站当启动页/预渲染时,页面可能还没开始出帧,
// 此时若因可见性回调翻档,幕布会把还没画出来的页面盖成整片纯色(后台标签里有 rAF 不跑,激活时才补跑)
let _sweepArmed = typeof requestAnimationFrame !== 'function'
if (!_sweepArmed)
  requestAnimationFrame(() => {
    _sweepArmed = true
  })

// 计算「晨→暮」暗色扫过方向(单位向量,屏幕坐标 y 向下)
// 规则:暗色沿「垂直于光束」的方向扫过,取「暮光从下往上蔓延」的一侧——
//   上午~正午(太阳在东/正南)向右上扫;下午(太阳在西)向左上扫;正午纯水平向右;夜晚无定向光默认水平向右。
//   (方位角约定 90=东/180=南/270=西,与 windowLight.sourceX 东左西右一致)
function sweepVector(sun) {
  if (sun.isNight) return { dx: 1, dy: 0 }
  const az = sun.azimuth
  const bx = -Math.max(-1, Math.min(1, (az - 180) / 90)) // 上午>0、正午=0、下午<0
  const by = Math.max(0, Math.sin(((sun.altitude || 0) * Math.PI) / 180)) // 高度角→光束向下分量 0..1
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
  const clone = app ? app.cloneNode(true) : null
  if (clone) {
    clone.removeAttribute('id')
    overlay.appendChild(clone)
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
  if (app && clone) copyScroll(app, clone)
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
  // 空壳/后台标签页不扫光:幕布是对当前 DOM 的克隆,没有内容可克隆时只会铺出一整片不透明色块。
  // 启动瞬间 #app 还没渲染(外壳要等路由解析完)、或在后台标签里切档都会命中,此时直接切类即可
  const app = document.getElementById('app')
  if (document.hidden || !app || !app.firstElementChild) return null

  const toDusk = !prevDusk // true=晨→暮(暗色扫入),false=暮→晨(亮色扫入)
  const dir = sweepVector(_sun)
  const vx = toDusk ? dir.dx : -dir.dx // 扫光前进方向(新主题从该侧露出来)
  const vy = toDusk ? dir.dy : -dir.dy
  // 蒙版渐变轴:0%(透明=新主题已露出)在扫光来向一侧,100%(不透明=旧主题未划到)在前进方向一侧。
  // CSS linear-gradient 角度:0deg=向上/90deg=向右/180deg=向下/270deg=向左,故 angle=atan2(vx, -vy)。
  const angle = (Math.atan2(vx, -vy) * 180) / Math.PI
  const grad = `linear-gradient(${angle}deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0) calc(var(--sweep-p) - 8%), #000 calc(var(--sweep-p) + 8%), #000 100%)`

  const overlay = cloneOldTheme(root)
  overlay.style.webkitMask = `${grad} no-repeat center / 100% 100%`
  overlay.style.mask = `${grad} no-repeat center / 100% 100%`
  overlay.style.setProperty('--sweep-p', '-10%')
  void overlay.offsetWidth // 提交起点值(--sweep-p 初值为 -150%,幕布已布局时须强制一次重排,否则过渡从 -150% 起跑)

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
    // 兜底:窗被遮挡/预渲染时 rAF 可能一直不跑,幕布会停在起点把整页盖成不透明纯色(点击又能穿透),
    // 400ms 内没真正开始过渡就直接撤掉幕布,退回「瞬间切类」
    let started = false
    const startGuard = setTimeout(() => {
      if (!started) cleanup()
    }, 400)
    const disarmGuard = () => clearTimeout(startGuard)
    try {
      overlay.style.transition = `--sweep-p ${travelMs}ms cubic-bezier(.4,0,.2,1)`
      // 先提交起点,下一帧再切到终点触发过渡(--sweep-p 已用 @property 注册为可过渡)
      requestAnimationFrame(() =>
        requestAnimationFrame(() => {
          started = true
          disarmGuard()
          overlay.style.setProperty('--sweep-p', '110%')
        }),
      )
    } catch {
      disarmGuard()
      cleanup()
      return
    }
    // 只认幕布自身的 --sweep-p 过渡结束(transitionend 会从后代冒泡,别让首次气泡提前清掉幕布)
    const onEnd = (e) => {
      if (e.target !== overlay || e.propertyName !== '--sweep-p') return
      overlay.removeEventListener('transitionend', onEnd)
      cleanup()
    }
    overlay.addEventListener('transitionend', onEnd)
    setTimeout(cleanup, travelMs + 300) // 兜底清理(过渡异常/被中断时)
  }
}

// sweep=false:不扫光直接切类,用于启动期按太阳状态校正档位(App.vue 外壳尚未渲染,扫光只会盖出整片色块)
export function applyTheme(state, { sweep = true } = {}) {
  const t = { ...DEFAULT_THEME, ...state }
  const root = document.documentElement
  const nextDusk = t.mode === 'dusk'
  const prevDusk = _current ? _current.mode === 'dusk' : null
  const isModeChange = prevDusk != null && prevDusk !== nextDusk
  const withinWarm = _current && _current.theme === 'warm' && t.theme === 'warm'

  // 暖居内晨↔暮切换:切类前克隆旧主题整页为幕布,切类后蒙版扫光露出新主题
  const finishSweep = sweep && _sweepArmed && isModeChange && withinWarm ? beginModeSweep(root, prevDusk) : null

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
