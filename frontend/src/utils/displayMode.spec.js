import { describe, it, expect } from 'vitest'
import {
  DISPLAY_MODE_KEY,
  isChildAllowedPath,
  isValidPin,
  makePinRecord,
  readDisplayMode,
  verifyPinRecord,
  writeDisplayMode,
} from './displayMode'

function fakeStorage(initial = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
  }
}

describe('isChildAllowedPath', () => {
  it('放行首页与内容页(含子路径)', () => {
    expect(isChildAllowedPath('/home')).toBe(true)
    expect(isChildAllowedPath('/blog/12?x=1')).toBe(true)
    expect(isChildAllowedPath('/album/3/photos')).toBe(true)
    expect(isChildAllowedPath('/games/cards/klondike')).toBe(true)
  })

  it('拦下设置、记账、聊天等写操作与私有页', () => {
    expect(isChildAllowedPath('/settings')).toBe(false)
    expect(isChildAllowedPath('/book')).toBe(false)
    expect(isChildAllowedPath('/vault')).toBe(false)
    expect(isChildAllowedPath('/chat')).toBe(false)
    expect(isChildAllowedPath('/storage/files')).toBe(false)
  })

  it('不把同前缀的无关路径当成放行', () => {
    expect(isChildAllowedPath('/homework')).toBe(false)
    expect(isChildAllowedPath('/blogs')).toBe(false)
  })
})

describe('模式读写', () => {
  it('缺键时回落 normal', () => {
    expect(readDisplayMode(fakeStorage())).toEqual({ mode: 'normal', pin: null })
  })

  it('坏 JSON 不抛错', () => {
    expect(readDisplayMode(fakeStorage({ [DISPLAY_MODE_KEY]: '{oops' })).mode).toBe('normal')
  })

  it('未知模式回落到 normal,密码记录原样保留', () => {
    const st = fakeStorage({ [DISPLAY_MODE_KEY]: JSON.stringify({ mode: 'weird', pin: { salt: 'a', hash: 'b' } }) })
    expect(readDisplayMode(st)).toEqual({ mode: 'normal', pin: { salt: 'a', hash: 'b' } })
  })

  it('写出后能读回', () => {
    const st = fakeStorage()
    writeDisplayMode({ mode: 'elder', pin: null }, st)
    expect(readDisplayMode(st)).toEqual({ mode: 'elder', pin: null })
  })
})

describe('儿童模式密码', () => {
  it('只认 4–8 位纯数字', () => {
    expect(isValidPin('1234')).toBe(true)
    expect(isValidPin('12345678')).toBe(true)
    expect(isValidPin('123')).toBe(false)
    expect(isValidPin('123456789')).toBe(false)
    expect(isValidPin('12a4')).toBe(false)
  })

  it('同一密码同一盐可复现,不同盐结果不同', async () => {
    const rec = await makePinRecord('1234')
    expect(await verifyPinRecord('1234', rec)).toBe(true)
    expect(await verifyPinRecord('4321', rec)).toBe(false)
    const rec2 = await makePinRecord('1234')
    expect(rec2.hash).not.toBe(rec.hash)
  })

  it('没设过密码视为通过', async () => {
    expect(await verifyPinRecord('0000', null)).toBe(true)
  })
})
