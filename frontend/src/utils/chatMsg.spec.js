// chatMsg 负载解析/构建守门:与后端 validateRichPayload 同口径,畸形输入一律安全降级不抛错。
import { describe, expect, it } from 'vitest'
import {
  buildCardPayload,
  buildGameInvitePayload,
  buildImagePayload,
  buildLinkPayload,
  isSiteInternal,
  parsePayload,
  serializePayload,
} from './chatMsg'

describe('parsePayload', () => {
  it('合法 JSON 对象返回对象,其余一律 null 不抛错', () => {
    expect(parsePayload('{"url":"/files/a.png"}')).toEqual({ url: '/files/a.png' })
    expect(parsePayload('not-json{{')).toBeNull()
    expect(parsePayload('["array"]')).toBeNull()
    expect(parsePayload('"string"')).toBeNull()
    expect(parsePayload('123')).toBeNull()
    expect(parsePayload('null')).toBeNull()
    expect(parsePayload('')).toBeNull()
    expect(parsePayload(null)).toBeNull()
    expect(parsePayload(undefined)).toBeNull()
  })
})

describe('buildImagePayload', () => {
  it('仅接受 /files/ 站内地址,尺寸可选取整', () => {
    expect(buildImagePayload('/files/pictures/a.png', 100.4, 50.6)).toEqual({ url: '/files/pictures/a.png', width: 100, height: 51 })
    expect(buildImagePayload('https://evil.example/a.png')).toBeNull()
    expect(buildImagePayload('')).toBeNull()
    expect(buildImagePayload('/files/a.png', -1)).toEqual({ url: '/files/a.png' })
  })
})

describe('buildLinkPayload', () => {
  it('保留非空字段并限长,全空返回 null(纯链接)', () => {
    expect(buildLinkPayload({ title: 'T', description: 'D', image: 'https://x/i.png', siteName: 'S' })).toEqual({
      title: 'T',
      description: 'D',
      image: 'https://x/i.png',
      siteName: 'S',
    })
    expect(buildLinkPayload({})).toBeNull()
    expect(buildLinkPayload(null)).toBeNull()
    expect(buildLinkPayload({ title: 'x'.repeat(300) }).title.length).toBe(200)
  })
})

describe('buildCardPayload', () => {
  it('校验 objType/objId/url,非法返回 null;objId 接受数字或短字符串(放映厅 Jellyfin id)', () => {
    expect(buildCardPayload({ objType: 'ALBUM', objId: 3, url: '/album/3' })).toEqual({ objType: 'ALBUM', objId: 3, url: '/album/3' })
    expect(buildCardPayload({ objType: 'ALBUM', objId: 3, url: '/album/3', sub: 's', cover: '/files/c.jpg' })).toEqual({
      objType: 'ALBUM',
      objId: 3,
      url: '/album/3',
      sub: 's',
      cover: '/files/c.jpg',
    })
    expect(buildCardPayload({ objType: 'VIDEO', objId: 'abc123def', url: '/cinema/abc123def' })).toEqual({
      objType: 'VIDEO',
      objId: 'abc123def',
      url: '/cinema/abc123def',
    })
    expect(buildCardPayload({ objType: 'HACK', objId: 3, url: '/album/3' })).toBeNull()
    expect(buildCardPayload({ objType: 'ALBUM', objId: '', url: '/album/3' })).toBeNull()
    expect(buildCardPayload({ objType: 'ALBUM', objId: 3, url: 'https://evil' })).toBeNull()
    expect(buildCardPayload({ objType: 'ALBUM', objId: 3, url: '/x'.repeat(150) })).toBeNull()
  })
})

describe('buildGameInvitePayload', () => {
  it('只放行 H5 小游戏全集', () => {
    expect(buildGameInvitePayload('game_2048')).toEqual({ gameCode: 'game_2048' })
    expect(buildGameInvitePayload('game_petlink')).toEqual({ gameCode: 'game_petlink' })
    expect(buildGameInvitePayload('game_hack')).toBeNull()
    expect(buildGameInvitePayload('')).toBeNull()
  })
})

describe('isSiteInternal / serializePayload', () => {
  it('站内地址判定:/ 开头且非 //', () => {
    expect(isSiteInternal('/album/1')).toBe(true)
    expect(isSiteInternal('//evil.example')).toBe(false)
    expect(isSiteInternal('https://a.example')).toBe(false)
    expect(isSiteInternal('')).toBe(false)
  })
  it('序列化超 4KB 返回 null,异常输入安全降级', () => {
    expect(serializePayload({ a: 1 })).toBe('{"a":1}')
    expect(serializePayload({ a: 'x'.repeat(5000) })).toBeNull()
    const circular = {}
    circular.self = circular
    expect(serializePayload(circular)).toBeNull()
    expect(serializePayload(null)).toBeNull()
  })
})
