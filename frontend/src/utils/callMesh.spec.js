import { describe, it, expect } from 'vitest'
import {
  MAX_PARTICIPANTS,
  PEER_ACTIVE,
  upsertParticipant,
  markPeer,
  applyRoster,
  joinedOthers,
  isActiveStatus,
  clampInviteeCount,
} from './callMesh'

// 多人通话 mesh 的纯函数内核:名单迁移是信令乱序下的正确性底线。
describe('callMesh', () => {
  it('上限 8 人;活动状态口径', () => {
    expect(MAX_PARTICIPANTS).toBe(8)
    expect(PEER_ACTIVE).toEqual(['inviting', 'ringing', 'joined'])
    expect(isActiveStatus('joined')).toBe(true)
    expect(isActiveStatus('left')).toBe(false)
  })

  it('upsertParticipant:新成员入列,已有成员只改名/状态且不覆盖终态', () => {
    const base = upsertParticipant([], { id: 2, name: '阿明', status: 'inviting' })
    expect(base).toEqual([{ id: 2, name: '阿明', status: 'inviting' }])
    // 邀请中 → 接通
    const joined = upsertParticipant(base, { id: 2, status: 'joined' })
    expect(joined[0].status).toBe('joined')
    expect(joined[0].name).toBe('阿明')
    // 已离开(终态)后乱序到达的 ringing 不回退状态,名字仍可修
    const left = markPeer(joined, 2, 'left')
    const stale = upsertParticipant(left, { id: 2, name: '阿明·改', status: 'ringing' })
    expect(stale[0].status).toBe('left')
    expect(stale[0].name).toBe('阿明·改')
    // 无 id / 重复 id 不同类型:按数值比较(id 可能字符串化)
    expect(upsertParticipant(base, { id: '2', status: 'joined' })).toHaveLength(1)
    expect(upsertParticipant(base, null)).toBe(base)
  })

  it('markPeer 只改命中者,未知 id 不新增', () => {
    const base = upsertParticipant([], { id: 3, name: '小豆', status: 'ringing' })
    const out = markPeer(base, '3', 'joined')
    expect(out[0].status).toBe('joined')
    expect(markPeer(base, 99, 'joined')).toHaveLength(1)
  })

  it('applyRoster:roster 合并不覆盖本地已有状态,新成员 joined 入列', () => {
    const local = upsertParticipant([], { id: 1, name: '主叫', status: 'joined' })
    const out = applyRoster(local, [{ id: 1, name: '主叫' }, { id: 4, name: '阿花' }, null, {}])
    expect(out).toHaveLength(2)
    expect(out.find((p) => p.id === 4)).toEqual({ id: 4, name: '阿花', status: 'joined' })
    // 本地 inviting 的成员不被 roster 顶成 joined(roster 只补不知道的)
    const inviting = upsertParticipant([], { id: 5, name: '待答', status: 'inviting' })
    expect(applyRoster(inviting, [{ id: 5, status: 'joined' }])[0].status).toBe('inviting')
  })

  it('joinedOthers:排除自己与非接通成员', () => {
    const list = [
      { id: 1, status: 'joined' },
      { id: 2, status: 'joined' },
      { id: 3, status: 'ringing' },
      { id: 4, status: 'left' },
    ]
    expect(joinedOthers(list, 2).map((p) => p.id)).toEqual([1])
  })

  it('clampInviteeCount:除我之外最多 7 人', () => {
    expect(clampInviteeCount([])).toBe(7)
    expect(clampInviteeCount([{ id: 1 }, { id: 2 }])).toBe(5)
    expect(clampInviteeCount(Array.from({ length: 9 }, (_, i) => ({ id: i })))).toBe(0)
  })
})
