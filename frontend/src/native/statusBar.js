// 状态栏文字跟随主题两轴:深色页用浅色文字,浅色页用深色文字。
// 背景由页面自身延伸到底(overlaysWebView 默认 true,Android 15+ 也是边到边),
// 顶部留白由 CSS env(safe-area-inset-top) 负责,这里不再设背景色。
import { StatusBar, Style } from '@capacitor/status-bar'

function isDarkTheme() {
  return document.documentElement.classList.contains('dark')
}

async function apply() {
  try {
    await StatusBar.setStyle({ style: isDarkTheme() ? Style.Dark : Style.Light })
  } catch (e) {
    // 状态栏是锦上添花,失败不影响使用
    console.warn('[native] 状态栏样式设置失败', e)
  }
}

export async function initStatusBar() {
  await apply()
  // 主题切换(光尘/暖居 × 浅色/深色)最终都落在 html 的 class 上
  const observer = new MutationObserver(() => apply())
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
}
