// 触觉反馈原生出口(仅原生壳):统一语义 kind,插件不存在时静默 no-op。
// 由 utils/haptics.js 动态引入,Web 端不会加载 Capacitor 运行时。
import { registerPlugin } from '@capacitor/core'
import { isNativeApp } from '@/utils/platform'

const IhomyHaptics = registerPlugin('IhomyHaptics')

export async function play(kind = 'tap') {
  if (!isNativeApp()) return
  try {
    await IhomyHaptics.play({ kind })
  } catch (e) {
    // 插件未注册(旧 APK)/系统拒绝:触觉只是锦上添花,不打断业务
  }
}
