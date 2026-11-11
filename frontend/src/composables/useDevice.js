// 设备类型检测:useDevice 里的「移动端 / 桌面端」布局开关唯一来源。
//
// 三档(判定规则在 utils/platform.js 的 classifyViewport,纯函数可测):
// - isPhone:真手机(iPhone / Android 且屏幕短边 < 600dp)→ 移动端布局
// - isTablet:平板(iPad / Android 且短边 ≥ 600dp)→ **桌面端布局**(平板屏幕够大,PC 布局更好用)
// - isMobile:是否用移动端布局 = 手机 or 窄窗口(≤768px)
//
// 平板短边过线但仍被系统分屏压窄时,按窄窗口走移动端布局 —— 这是唯一按运行期变化的量,
// 所以监听 resize / orientationchange;其余两个是硬件属性,同一次计算里带出来即可。
//
// 大屏能力(P1 大屏包)另给三个只读值,**判定只在这里做**,页面/组件禁止自己读 window.innerWidth:
// - isWide:窗口 ≥900 且屏幕短边 ≥600dp(手机横屏短边不足,不算宽屏)→ 页面可切双栏;
// - isLandscape:窗口宽 > 高,纯派生,不落 localStorage;
// - hasPointer:有精细指针(鼠标 / 触控板,不是触屏)→ hover / 右键等桌面交互用。
import { ref } from 'vue'
import { classifyViewport, isWideViewport, NARROW_MAX_WIDTH } from '@/utils/platform'

const isMobile = ref(false)
const isPhone = ref(false)
const isTablet = ref(false)
const isWide = ref(false)
const isLandscape = ref(false)
const hasPointer = ref(false)
let initialized = false

function apply() {
  const width = window.innerWidth
  const height = window.innerHeight
  // 拿不到 screen 时退回窗口尺寸(与 classifyViewport 同口径)
  const screenWidth = (typeof screen !== 'undefined' && screen.width) || width
  const screenHeight = (typeof screen !== 'undefined' && screen.height) || height
  const d = classifyViewport({
    ua: navigator.userAgent,
    maxTouchPoints: navigator.maxTouchPoints || 0,
    screenWidth,
    screenHeight,
    innerWidth: width,
  })
  isPhone.value = d.phone
  isTablet.value = d.tablet
  isMobile.value = d.mobile
  isWide.value = isWideViewport({ innerWidth: width, screenWidth, screenHeight })
  isLandscape.value = width > height
}

function init() {
  if (initialized) return
  // 单测(Node,无 DOM)环境只导入不初始化;浏览器/壳内照旧
  if (typeof window === 'undefined') return
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

  // 指针能力:插拔鼠标、平板接/拆键盘壳都会变,所以也要监听 change(不走 resize)
  if (typeof window.matchMedia === 'function') {
    const pointerMql = window.matchMedia('(any-pointer: fine)')
    const onPointer = (e) => {
      hasPointer.value = e.matches
    }
    hasPointer.value = pointerMql.matches
    if (pointerMql.addEventListener) pointerMql.addEventListener('change', onPointer)
    else pointerMql.addListener(onPointer)
  }
}

init()

export function useDevice() {
  return { isMobile, isPhone, isTablet, isWide, isLandscape, hasPointer }
}
