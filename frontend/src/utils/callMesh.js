// 多人通话(家庭内 mesh)的纯函数内核:参与者列表迁移/名单合并/人数上限。
// 连接与信令状态机在 stores/call.js;这里只做可单测的数据变换(规范:纯函数不碰 side effect)。

/** 单通通话人数上限(mesh 全连接,超出提示分通) */
export const MAX_PARTICIPANTS = 8

/**
 * 参与者条目:{ id, name, status, pc?, remoteStream? }
 * status 口径:inviting(已发出邀请)→ ringing(响铃中,远端视角)→ joined(接通)
 *   → rejected / busy / timeout / left / failed(终态)
 */
export const PEER_ACTIVE = ['inviting', 'ringing', 'joined']

/** 按 id 上插或更新参与者;已有条目只合并可更新字段,不覆盖终态(乱序信令兜底) */
export function upsertParticipant(list, entry) {
  if (!entry || entry.id == null) return list
  const idx = list.findIndex((p) => Number(p.id) === Number(entry.id))
  if (idx < 0) return [...list, { ...entry }]
  const cur = list[idx]
  const terminal = !PEER_ACTIVE.includes(cur.status)
  const next = { ...cur }
  for (const k of ['name', 'status']) {
    if (entry[k] != null && !(terminal && k === 'status')) next[k] = entry[k]
  }
  const out = [...list]
  out[idx] = next
  return out
}

/** 标记某成员状态;不存在则忽略(信令先于名单到达时不动) */
export function markPeer(list, id, status) {
  return list.map((p) => (Number(p.id) === Number(id) ? { ...p, status } : p))
}

/** roster(主叫下发的已接通名单)合并进本地列表:已存在的不动,新的以 joined 入列 */
export function applyRoster(list, roster) {
  let out = list
  for (const p of roster || []) {
    if (p == null || p.id == null) continue
    if (out.some((q) => Number(q.id) === Number(p.id))) continue
    out = [...out, { id: p.id, name: p.name || '', status: 'joined' }]
  }
  return out
}

/** 除我之外的已接通成员(建连目标) */
export function joinedOthers(list, myId) {
  return list.filter((p) => Number(p.id) !== Number(myId) && p.status === 'joined')
}

/** 是否仍处于「可被 end/leave 影响」的活动状态 */
export function isActiveStatus(status) {
  return PEER_ACTIVE.includes(status)
}

/** 邀请人数钳制:除主叫外最多 MAX_PARTICIPANTS-1 人 */
export function clampInviteeCount(selected) {
  return Math.max(0, MAX_PARTICIPANTS - 1 - (selected || []).length)
}
