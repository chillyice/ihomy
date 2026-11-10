// 设备类型检测:useDevice 里的「移动端 / 桌面端」布局开关唯一来源。
//
// 三档(判定规则在 utils/platform.js 的 classifyViewport,纯函数可测):
// - isPhone:真手机(iPhone / Android 且屏幕短边 < 600dp)→ 移动端布局
// - isTablet:平板(iPad / Android 且短边 ≥ 600dp)→ **桌面端布局**(平板屏幕够大,PC 布局更好用)
// - isMobile:是否用移动端布局 = 手机 or 窄窗口(≤768px)
//
// 平板短边过线但仍被系统分屏压窄时,按窄窗口走移动端布局 —— 这是唯一按运行期变化的量,
// 所以监听 resize / orientationchange;其余两个是硬件属性,同一次计算里带出来即可。
import { ref } from 'vue'
import { classifyViewport, NARROW_MAX_WIDTH } from '@/utils/platform'

const isMobile = ref(false)
const isPhone = ref(false)
const isTablet = ref(false)
let initialized = false

function apply() {
  const d = classifyViewport({
    ua: navigator.userAgent,
    maxTouchPoints: navigator.maxTouchPoints || 0,
    screenWidth: (typeof screen !== 'undefined' && screen.width) || window.innerWidth,
    screenHeight: (typeof screen !== 'undefined' && screen.height) || window.innerHeight,
    innerWidth: window.innerWidth,
  })
  isPhone.value = d.phone
  isTablet.value = d.tablet
  isMobile.value = d.mobile
}

function init() {
  if (initialized) return
  initialized = true
  apply()
  const handler = () => apply()
  const mql = window.matchMedia(`(max-width: ${NARROW_MAX_WIDTH}px)`)
  // 兼容旧浏览器:老版 Safari / 部分 WebView 只有 addListener,没有 addEventListener
  if (mql.addEventListener) mql.addEventListener('change', handler)
  else mql.addListener(handler)
  // 旋屏 / 分屏 / 拖窗口都会改 innerWidth,不依赖 mql 一定触发
  window.addEventListener('resize', handler)
  window.addEventListener('orientationchange', handler)
}

init()

export function useDevice() {
  return { isMobile, isPhone, isTablet }
}
