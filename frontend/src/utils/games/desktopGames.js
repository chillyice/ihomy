/**
 * 桌面游戏(PC 安装包)口径:与后端 GameConst.TYPE_DESKTOP、GameSystem.isDesktopPackage 同源。
 *
 * 这类游戏浏览器里跑不起来(EmulatorJS 只有主机核心),站内只做两件事:
 * 托管安装包原文件 + 给安装说明;成员下载到自己电脑上装。卡片点开进详情页(不是播放器)。
 *
 * 与模拟器机种不同,类型由导入时**显式选择**,不按扩展名自动识别:
 * .zip/.7z 与街机 romset 同后缀,自动判定会把安装包当成街机包丢进 fbneo。
 */
export const DESKTOP_TYPE = 'DESKTOP'

/** 安装包扩展名(与后端 GameSystem.DESKTOP_EXTENSIONS 保持一致) */
export const DESKTOP_EXTENSIONS = ['exe', 'msi', 'zip', '7z', 'rar']

/** 是否桌面游戏类型(未知/空值一律 false) */
export function isDesktopType(type) {
  return String(type ?? '').toUpperCase() === DESKTOP_TYPE
}

/** 安装包判定:导入前粗校验,最终以后端为准 */
export function isDesktopPackage(name) {
  const dot = String(name ?? '').lastIndexOf('.')
  if (dot <= 0) return false
  return DESKTOP_EXTENSIONS.includes(
    String(name)
      .slice(dot + 1)
      .toLowerCase(),
  )
}

/** 导入文件选择器 accept,如 .exe,.msi,.zip,.7z,.rar */
export function desktopAccept() {
  return DESKTOP_EXTENSIONS.map((e) => `.${e}`).join(',')
}
