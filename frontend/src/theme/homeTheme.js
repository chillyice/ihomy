// 首页主题:场景主题(scene,默认,沉浸式) vs 传统主题(classic,模块化首页)。
// 偏好存 localStorage('ihomy:homeTheme'),路由 `/` 按此偏好重定向到 /scene 或 /home。
// 场景↔传统的显式切换入口在 设置 → 个性化设置 → 首页主题(P23 提前落地)。

export const HOME_THEME_KEY = 'ihomy:homeTheme'
export const HOME_THEME_SCENE = 'scene'
export const HOME_THEME_CLASSIC = 'classic'

export function loadHomeTheme() {
  try {
    const v = localStorage.getItem(HOME_THEME_KEY)
    return v === HOME_THEME_CLASSIC ? HOME_THEME_CLASSIC : HOME_THEME_SCENE
  } catch {
    return HOME_THEME_SCENE
  }
}

export function setHomeTheme(theme) {
  const v = theme === HOME_THEME_CLASSIC ? HOME_THEME_CLASSIC : HOME_THEME_SCENE
  try {
    localStorage.setItem(HOME_THEME_KEY, v)
  } catch {
    /* ignore */
  }
  return v
}
