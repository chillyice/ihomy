// 聊天字体偏好(字号 / 自己气泡的文字颜色):本机持久化,浮窗与 /chat 页共用。
// 字号有上限——再大在小屏上一条消息就能把整屏堆满;颜色只接受预设色值,
// 避免把任意字符串拼进内联 CSS(存进 localStorage 的值同样要走 normalize)。
export const CHAT_STYLE_KEY = 'ihomy:chat:text:v1'

/** 字号上下限(px):下限保证可读,上限 = 小屏一屏仍能看清好几条 */
export const CHAT_FONT_MIN = 12
export const CHAT_FONT_MAX = 20
export const CHAT_FONT_DEFAULT = 14

/** 可选气泡文字颜色;空串 = 跟随主题色 */
export const CHAT_TEXT_COLORS = ['', '#d94b3f', '#e07b21', '#2f9b62', '#2f7fe0', '#7a5af5', '#d6417f']

export const CHAT_STYLE_DEFAULT = { size: CHAT_FONT_DEFAULT, color: '' }

/** 把任意外来值收敛成合法偏好:字号取整并夹在上下限内,颜色只认预设集合 */
export function normalizeChatStyle(raw) {
  const size = Number(raw?.size)
  const color = typeof raw?.color === 'string' ? raw.color : ''
  return {
    size: Number.isFinite(size) ? Math.min(CHAT_FONT_MAX, Math.max(CHAT_FONT_MIN, Math.round(size))) : CHAT_FONT_DEFAULT,
    color: CHAT_TEXT_COLORS.includes(color) ? color : '',
  }
}

/** 读取本机偏好;无痕环境 / 脏数据一律回默认 */
export function loadChatStyle() {
  try {
    const raw = localStorage.getItem(CHAT_STYLE_KEY)
    return raw ? normalizeChatStyle(JSON.parse(raw)) : { ...CHAT_STYLE_DEFAULT }
  } catch (e) {
    return { ...CHAT_STYLE_DEFAULT }
  }
}

/** 写入本机偏好(已 normalize);失败只影响下次进来是否记得 */
export function saveChatStyle(style) {
  const value = normalizeChatStyle(style)
  try {
    localStorage.setItem(CHAT_STYLE_KEY, JSON.stringify(value))
  } catch (e) {
    // 无痕/配额满:仅本次会话生效
  }
  return value
}
