import { beforeEach, describe, expect, it } from 'vitest'
import { loadLastChannels, normalizeChannels, NOTIFY_CHANNELS_KEY, saveLastChannels } from './notifyChannels'

/** 极简 localStorage 替身(node 环境没有 DOM 存储) */
function fakeStorage() {
  const map = new Map()
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    _map: map,
  }
}

describe('notifyChannels', () => {
  let ls

  beforeEach(() => {
    ls = fakeStorage()
    globalThis.localStorage = ls
  })

  it('只留合法值,去重并按 EMAIL,APP 固定顺序', () => {
    expect(normalizeChannels(['app', 'EMAIL', 'SMS', 'APP', null])).toEqual(['EMAIL', 'APP'])
    expect(normalizeChannels(['APP'])).toEqual(['APP'])
  })

  it('非数组一律当空(默认不选)', () => {
    expect(normalizeChannels(null)).toEqual([])
    expect(normalizeChannels('EMAIL')).toEqual([])
    expect(normalizeChannels(undefined)).toEqual([])
  })

  it('没存过时默认空数组', () => {
    expect(loadLastChannels()).toEqual([])
  })

  it('保存后能读回上次选择', () => {
    saveLastChannels(['APP', 'EMAIL'])
    expect(loadLastChannels()).toEqual(['EMAIL', 'APP'])
  })

  it('坏 JSON 回落空数组', () => {
    ls.setItem(NOTIFY_CHANNELS_KEY, '{not-json')
    expect(loadLastChannels()).toEqual([])
  })

  it('存储不可用时不抛异常', () => {
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      get() {
        throw new Error('denied')
      },
    })
    expect(loadLastChannels()).toEqual([])
    expect(() => saveLastChannels(['EMAIL'])).not.toThrow()
  })
})
