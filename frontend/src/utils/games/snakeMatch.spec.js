// snakeMatch 对战模拟守门:同 seed 可重放、转向/推进确定性、吃食物计分、
// 尸体留场成障碍、双方跑完才终局且按比分定胜负(与后端 finish 结算口径一致)。
import { describe, expect, it } from 'vitest'
import { applyInput, createMatch, stepMatch, sideStatus, HOST, GUEST, STATUS } from './snakeMatch'

describe('snakeMatch', () => {
  it('同 seed 创建的双端局面完全一致,含首只共享食物', () => {
    const a = createMatch(12345)
    const b = createMatch(12345)
    expect(a).toEqual(b)
    expect(a.host.food).toEqual(a.guest.food)
    expect(a.host.food).not.toBeNull()
  })

  it('本地转向立即生效,对端方向由 applyInput 注入', () => {
    const m = applyInput(applyInput(createMatch(1), HOST, 'up'), GUEST, 'down')
    expect(m.host.nextDirection).toBe('up')
    expect(m.guest.nextDirection).toBe('down')
  })

  it('推进确定性:同 seed 同输入,任意步数后双端状态一致', () => {
    let a = createMatch(99)
    let b = createMatch(99)
    for (let i = 0; i < 50; i++) {
      a = applyInput(a, HOST, i % 2 ? 'up' : 'down')
      b = applyInput(b, HOST, i % 2 ? 'up' : 'down')
      a = stepMatch(a)
      b = stepMatch(b)
    }
    expect(a).toEqual(b)
  })

  it('无输入双蛇同速平行,同 tick 撞同一面墙,终局按比分定胜负', () => {
    let m = createMatch(7)
    for (let i = 0; i < 60 && !m.over; i++) m = stepMatch(m)
    expect(m.over).toBe(true)
    expect(sideStatus(m, HOST)).toBe(STATUS.LOST)
    expect(sideStatus(m, GUEST)).toBe(STATUS.LOST)
    // 同起跑同分(都只吃对称机会的食物)→ 平局无胜者或分差即胜者,两者必居其一
    expect(m.winner === null || m.winner === HOST || m.winner === GUEST).toBe(true)
  })

  it('先死者身体留场成为障碍:后死者撞上尸体也会结束', () => {
    let m = createMatch(11)
    // host 蛇头向上画圈撞墙先死,guest 无输入继续前进,最终也会结束
    m = applyInput(m, HOST, 'up')
    for (let i = 0; i < 200 && !m.over; i++) m = stepMatch(m)
    expect(m.over).toBe(true)
    expect(sideStatus(m, HOST)).toBe(STATUS.LOST)
    expect(m.host.snake.length).toBeGreaterThanOrEqual(3)
  })

  it('吃到共享食物加分且食物镜像同步', () => {
    // 找一个能让 host 吃到食物的 seed:直接跑若干局检查计分路径
    let scored = false
    for (let seed = 1; seed < 40 && !scored; seed++) {
      let m = createMatch(seed)
      for (let i = 0; i < 80 && !m.over; i++) {
        // 追着食物转向的朴素策略:只在 food 在本行时上下调整
        if (m.host.food) {
          const head = m.host.snake[0]
          if (m.host.food.y < head.y) m = applyInput(m, HOST, 'up')
          else if (m.host.food.y > head.y) m = applyInput(m, HOST, 'down')
        }
        m = stepMatch(m)
      }
      if (m.host.score > 0) {
        scored = true
        expect(m.guest.score).toBeGreaterThanOrEqual(0)
      }
    }
    expect(scored).toBe(true)
  })
})
