// 设备平台判定:iOS / iPadOS 专用分支(如「全屏下禁止键入」)使用。
// iPadOS 13+ 的 Safari 把 UA 伪装成 Macintosh,只能再用触摸点数与真 Mac 区分。
export function isIOSUA(ua = '', maxTouchPoints = 0) {
  const agent = String(ua || '')
  if (/iPad|iPhone|iPod/i.test(agent)) return true
  return /Mac/i.test(agent) && maxTouchPoints > 1
}

export function isIOS() {
  if (typeof navigator === 'undefined') return false
  return isIOSUA(navigator.userAgent, navigator.maxTouchPoints)
}
