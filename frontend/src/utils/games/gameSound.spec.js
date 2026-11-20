import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/** 极简 localStorage 替身:vitest 跑在 node 环境,没有浏览器存储 */
const makeStorage = () => {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    clear: () => map.clear(),
  }
}

/** 模块在导入时读存储,每个用例都重新导入一份干净状态 */
const load = async () => {
  vi.resetModules()
  return import('./gameSound')
}

describe('gameSound', () => {
  beforeEach(() => {
    globalThis.localStorage = makeStorage()
  })
  afterEach(() => {
    delete globalThis.localStorage
  })

  it('默认不静音', async () => {
    const { gameMuted } = await load()
    expect(gameMuted.value).toBe(false)
  })

  it('读到已存的静音状态', async () => {
    globalThis.localStorage.setItem('ihomy:game:mute:v1', '1')
    const { gameMuted } = await load()
    expect(gameMuted.value).toBe(true)
  })

  it('切换后写回存储,重新导入仍是静音', async () => {
    const { gameMuted, toggleGameMuted } = await load()
    expect(toggleGameMuted()).toBe(true)
    expect(gameMuted.value).toBe(true)
    expect(globalThis.localStorage.getItem('ihomy:game:mute:v1')).toBe('1')

    vi.resetModules()
    const again = await import('./gameSound')
    expect(again.gameMuted.value).toBe(true)
  })

  it('老版宠物连连看的静音键迁移到总开关', async () => {
    globalThis.localStorage.setItem('ihomy:game:petlink:muted', '1')
    const { gameMuted } = await load()
    expect(gameMuted.value).toBe(true)
    expect(globalThis.localStorage.getItem('ihomy:game:mute:v1')).toBe('1')
  })

  it('新键存在时以新键为准,老键不干扰取消静音', async () => {
    globalThis.localStorage.setItem('ihomy:game:petlink:muted', '1')
    globalThis.localStorage.setItem('ihomy:game:mute:v1', '0')
    const { gameMuted } = await load()
    expect(gameMuted.value).toBe(false)
  })

  it('没有 localStorage 的环境不抛错', async () => {
    delete globalThis.localStorage
    const { gameMuted, setGameMuted } = await load()
    expect(gameMuted.value).toBe(false)
    expect(setGameMuted(true)).toBe(true)
    expect(gameMuted.value).toBe(true)
  })
})
