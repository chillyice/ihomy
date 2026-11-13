// 状态栏文字与底色都跟随 App 主题,而不是系统深浅色:两轴主题(暖居/光尘 × 晨/暮)
// 加上日出日落自动切换,常年和系统设置对不上,只跟系统走就会「深图标压深底」看不清。
//
// 图标样式必须走 @capacitor/core 的 SystemBars:它会记住当前样式,在配置变更
// (旋转/系统深色切换/字体缩放)时重放;legacy 的 @capacitor/status-bar 不记忆,
// 事件过后会被 SystemBars 的默认值(DEFAULT=跟系统)盖回去。
//
// 底色只在「内容没有延伸到状态栏之下」时才刷:内容能延伸到底时条底透明、直接看到页面
// 自身底色(theme 的 --color-bg 或独立页根底色),多刷一层反而会盖住页面顶部;
// 老 WebView(<140)里 WebView 被系统顶下来,那条露出的是窗口背景(跟系统深浅色走),
// 必须由我们自己刷成页面底色,否则和主题撞色。
import { SystemBars, SystemBarsStyle } from '@capacitor/core'
import { StatusBar } from '@capacitor/status-bar'
import { isAndroidApp } from '@/utils/platform'

// 页面根底色不透明度低于此值视为「没铺底」,继续往上找
const ALPHA_FLOOR = 0.5
// 感知亮度阈值(0~1):亮底配深色图标,深底配浅色图标
const LIGHT_BG_LUMINANCE = 0.55

function parseRgb(value) {
  const m = /rgba?\(([^)]+)\)/.exec(value || '')
  if (!m) return null
  const parts = m[1]
    .split(/[,\s/]+/)
    .filter(Boolean)
    .map(Number)
  if (parts.length < 3 || parts.some((n) => Number.isNaN(n))) return null
  return { r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 ? parts[3] : 1 }
}

// 「状态栏后面那块颜色」= 页面根底色:主站是 --color-bg(随主题),
// kada / 比特口袋 / chillyice 等独立页各自接管 html 底色。
function pageBackground() {
  for (const el of [document.documentElement, document.body]) {
    const rgb = parseRgb(getComputedStyle(el).backgroundColor)
    if (rgb && rgb.a >= ALPHA_FLOOR) return rgb
  }
  return null
}

function isLightBackground({ r, g, b }) {
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255 > LIGHT_BG_LUMINANCE
}

function toHex({ r, g, b }) {
  const h = (n) =>
    Math.max(0, Math.min(255, Math.round(n)))
      .toString(16)
      .padStart(2, '0')
  return `#${h(r)}${h(g)}${h(b)}`
}

function isDarkTheme() {
  return document.documentElement.classList.contains('dark')
}

// SystemBars(insetsHandling=css)会把安全区尺寸内联成 html 的 CSS 变量;
// 为 0 或还没注入,说明 WebView 被系统顶在状态栏之下,条底露出的是窗口背景。
function contentUnderStatusBar() {
  const raw = parseFloat(document.documentElement.style.getPropertyValue('--safe-area-inset-top'))
  return Number.isFinite(raw) && raw > 0
}

let appliedKey = ''
let pending = null

function schedule() {
  if (pending) return
  pending = setTimeout(() => {
    pending = null
    apply()
  }, 80)
}

async function apply() {
  const bg = pageBackground()
  const under = contentUnderStatusBar()
  const color = bg ? toHex(bg) : ''
  // 底色解析不出来时退回主题两轴判定,别让状态栏卡在旧样式
  const light = bg ? isLightBackground(bg) : !isDarkTheme()
  const key = `${light}|${under}|${color}`
  if (key === appliedKey) return
  appliedKey = key
  try {
    await SystemBars.setStyle({ style: light ? SystemBarsStyle.Light : SystemBarsStyle.Dark })
    if (isAndroidApp()) {
      if (under) await StatusBar.setOverlaysWebView({ overlay: true })
      else if (color) await StatusBar.setBackgroundColor({ color })
    }
  } catch (e) {
    // 状态栏是锦上添花,失败不影响使用;清掉缓存让下次事件再试一遍
    appliedKey = ''
    console.warn('[native] 状态栏样式设置失败', e)
  }
}

export async function initStatusBar() {
  await apply()
  // 主题切换最终落在 html 的 class 上;安全区变量(SystemBars 内联 style)决定是否需要刷底色,
  // 两者都要跟。独立页(咔哒/比特口袋)按需改写自己那份,也走同一个观察。
  const observer = new MutationObserver(schedule)
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style'] })
  // 系统深浅色切换后 Capacitor 会重放样式,这里顺手再对齐一次底色
  window.matchMedia?.('(prefers-color-scheme: dark)').addEventListener?.('change', schedule)
  // 从后台回前台:系统可能在期间重排状态栏,恢复时对齐一次
  document.addEventListener('visibilitychange', schedule)
}
