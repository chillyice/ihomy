// 设备类型检测:UA 与视口宽度任一命中即视为移动端(平板/窄窗口也算,
// 保证手机浏览器与桌面缩窄窗口表现一致)。isMobile 是模块级单例,
// init() 在模块首次 import 时执行一次,所有组件共享同一份状态。
import { ref } from 'vue'

const isMobile = ref(false)
let initialized = false

function detect() {
  const ua = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
  const mq = window.matchMedia('(max-width: 768px)').matches
  return ua || mq
}

function init() {
  if (initialized) return
  initialized = true
  isMobile.value = detect()
  const mql = window.matchMedia('(max-width: 768px)')
  const handler = () => { isMobile.value = detect() }
  // 兼容旧浏览器:老版 Safari/部分 WebView 只有 addListener,没有 addEventListener
  if (mql.addEventListener) mql.addEventListener('change', handler)
  else mql.addListener(handler)
}

init()

export function useDevice() {
  return { isMobile }
}
