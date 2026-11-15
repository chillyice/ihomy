import { describe, it, expect } from 'vitest'
import { createNetFailGate } from './netFailGate'

const makeGate = (threshold = 3, burstMs = 800) => {
  let t = 0
  const gate = createNetFailGate({ threshold, burstMs, now: () => t })
  return {
    gate,
    /** 前进毫秒数后记一次失败 */
    failAfter(ms) {
      t += ms
      return gate.fail()
    },
  }
}

describe('连不上网提示闸门', () => {
  it('单次连不上不提示', () => {
    const { gate } = makeGate()
    expect(gate.fail()).toBe(false)
    expect(gate.count()).toBe(1)
  })

  it('连续多次连不上才放行一次', () => {
    const { failAfter } = makeGate()
    expect(failAfter(2000)).toBe(false)
    expect(failAfter(2000)).toBe(false)
    expect(failAfter(2000)).toBe(true)
  })

  it('同一批并发(几乎同时失败)只算一次', () => {
    const { gate } = makeGate()
    gate.fail()
    expect(gate.fail()).toBe(false)
    expect(gate.fail()).toBe(false)
    expect(gate.count()).toBe(1)
  })

  it('一轮断网只提示一次,继续失败不再重复提示', () => {
    const { failAfter } = makeGate()
    failAfter(2000)
    failAfter(2000)
    expect(failAfter(2000)).toBe(true)
    expect(failAfter(2000)).toBe(false)
    expect(failAfter(2000)).toBe(false)
  })

  it('有请求真的通了就清零,恢复后重新计数', () => {
    const { gate, failAfter } = makeGate()
    failAfter(2000)
    failAfter(2000)
    expect(failAfter(2000)).toBe(true)
    gate.ok()
    expect(gate.count()).toBe(0)
    expect(failAfter(2000)).toBe(false)
    expect(failAfter(2000)).toBe(false)
    expect(failAfter(2000)).toBe(true)
  })

  it('阈值可配置', () => {
    const { failAfter } = makeGate(2)
    expect(failAfter(2000)).toBe(false)
    expect(failAfter(2000)).toBe(true)
  })
})
