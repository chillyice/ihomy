// 日程提醒「提醒方式」的取值口径与「记住上次选择」。
// 与后端 common/NotifyChannels 同口径:EMAIL 邮件 / APP 应用内;
// 空数组 = 不勾任何方式,后端按默认「仅应用内」处理(历史数据也是这个语义)。
export const NOTIFY_CHANNELS = ['EMAIL', 'APP']

/** 记住上次选择的本机偏好键(按账号无关:同一台设备换账号也沿用最近一次) */
export const NOTIFY_CHANNELS_KEY = 'ihomy:schedule:notifyChannels:v1'

/** 去重 + 只留合法值,固定 EMAIL,APP 顺序;非数组一律当空 */
export function normalizeChannels(list) {
  if (!Array.isArray(list)) return []
  return NOTIFY_CHANNELS.filter((c) => list.includes(c))
}

function storage() {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

/** 读取上次选择;坏值/存储不可用一律回落空数组(默认不选) */
export function loadLastChannels() {
  const ls = storage()
  if (!ls) return []
  try {
    return normalizeChannels(JSON.parse(ls.getItem(NOTIFY_CHANNELS_KEY) || '[]'))
  } catch {
    return []
  }
}

/** 保存本次选择(空数组也保存,表示用户主动清空) */
export function saveLastChannels(list) {
  const ls = storage()
  if (!ls) return
  try {
    ls.setItem(NOTIFY_CHANNELS_KEY, JSON.stringify(normalizeChannels(list)))
  } catch {
    // 隐私模式/配额满:只影响下次默认值,不打断保存
  }
}
