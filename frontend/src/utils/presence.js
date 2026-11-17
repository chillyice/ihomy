// 成员在线状态(头像右下角状态点):ONLINE 绿 / BUSY 红 / AWAY 黄 / OFFLINE 灰。
// 离线由 WS 连接态决定;在线时展示本人手动状态(忙碌/离开),离线优先——离线时手动状态保留但不显示。
export const PRESENCE_STATUS = {
  ONLINE: 'ONLINE',
  BUSY: 'BUSY',
  AWAY: 'AWAY',
  OFFLINE: 'OFFLINE',
}

/** 本人可切换的状态(离线不在选项里,它由连接态决定) */
export const PRESENCE_CHOICES = [PRESENCE_STATUS.ONLINE, PRESENCE_STATUS.BUSY, PRESENCE_STATUS.AWAY]

const isPresence = (v) =>
  v === PRESENCE_STATUS.ONLINE || v === PRESENCE_STATUS.BUSY || v === PRESENCE_STATUS.AWAY || v === PRESENCE_STATUS.OFFLINE

/** 成员行的手动状态:只认 ONLINE/BUSY/AWAY,缺省按在线 */
function manualOf(member) {
  const s = member?.presenceStatus
  return s === PRESENCE_STATUS.BUSY || s === PRESENCE_STATUS.AWAY ? s : PRESENCE_STATUS.ONLINE
}

/**
 * 计算头像状态点应显示的状态。
 * @param {Object} member 成员行:{id, online, presenceStatus}
 * @param {Object} [overrides] WS 实时覆盖:userId(字符串) → 状态
 */
export function resolvePresence(member, overrides) {
  if (!member) return PRESENCE_STATUS.OFFLINE
  const id = member.id ?? member.userId
  const live = id == null ? null : overrides?.[String(id)]
  // WS 推来的值是权威(建连/断连/本人切换都会推),优先于列表首拉快照
  if (isPresence(live)) return live
  return member.online ? manualOf(member) : PRESENCE_STATUS.OFFLINE
}

/** 状态 → CSS 类后缀/i18n 键用小写 */
export const presenceKey = (status) => String(status || PRESENCE_STATUS.OFFLINE).toLowerCase()
