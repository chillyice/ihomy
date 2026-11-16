import { describe, it, expect } from 'vitest'
import {
  DEFAULT_MOBILE_TABS,
  MOBILE_TAB_COUNT,
  buildMobileTabCandidates,
  isMobileTabActive,
  normalizeMobileTabs,
  parseMobileTabs,
  resolveMobileTab,
  serializeMobileTabs,
} from './mobileTabs'

const MODULES = [
  { code: 'album', title: '相册', category: 'content', sortOrder: 2, enabled: 1 },
  { code: 'blog', title: '博客', category: 'content', sortOrder: 1, enabled: 1 },
  { code: 'plan', title: '日程', category: 'life', sortOrder: 10, enabled: 1 },
  { code: 'games', title: '游戏', category: 'life', sortOrder: 20, enabled: 1 },
  { code: 'book', title: '记账本', category: 'life', sortOrder: 22, enabled: 1 },
  { code: 'hidden', title: '停用', category: 'life', sortOrder: 21, enabled: 0 },
]

describe('buildMobileTabCandidates', () => {
  it('虚拟入口在前(首页/更多/我的/拍摄),再接后端模块', () => {
    const codes = buildMobileTabCandidates(MODULES).map((c) => c.code)
    expect(codes.slice(0, 4)).toEqual(['home', 'more', 'me', 'capture'])
    expect(codes).toContain('album')
    expect(codes).toContain('blog')
  })

  it('停用模块与无路由映射的模块不进候选', () => {
    const codes = buildMobileTabCandidates(MODULES).map((c) => c.code)
    expect(codes).not.toContain('hidden')
  })

  it('儿童模式按路由白名单过滤', () => {
    const codes = buildMobileTabCandidates(MODULES, { child: true }).map((c) => c.code)
    expect(codes).toContain('album')
    expect(codes).toContain('games')
    expect(codes).not.toContain('book')
  })
})

describe('normalizeMobileTabs', () => {
  const valid = ['home', 'more', 'me', 'capture', 'album', 'plan', 'blog']

  it('不足五格用默认值补齐,保持五格', () => {
    const out = normalizeMobileTabs(['blog', 'me'], valid)
    expect(out).toHaveLength(MOBILE_TAB_COUNT)
    expect(out.slice(0, 2)).toEqual(['blog', 'me'])
    expect(out).not.toContain(undefined)
  })

  it('超长配置裁到五格', () => {
    const out = normalizeMobileTabs(['home', 'more', 'me', 'capture', 'album', 'plan', 'blog'], valid)
    expect(out).toHaveLength(MOBILE_TAB_COUNT)
    expect(out).toEqual(['home', 'more', 'me', 'capture', 'album'])
  })

  it('未知 code 与重复项被丢弃,用默认补齐', () => {
    const out = normalizeMobileTabs(['ghost', 'album', 'album', 'me'], valid)
    expect(out).toHaveLength(MOBILE_TAB_COUNT)
    expect(new Set(out).size).toBe(out.length)
    expect(out).not.toContain('ghost')
    expect(out).toContain('capture')
  })

  it('非数组输入回落默认五格', () => {
    expect(normalizeMobileTabs(null, valid)).toEqual(DEFAULT_MOBILE_TABS.filter((c) => valid.includes(c)).slice(0, 5))
    expect(normalizeMobileTabs('home', valid)).toHaveLength(MOBILE_TAB_COUNT)
  })

  it('中间位置稳住:模块缺失时也不把加号挤到别的格', () => {
    const partial = normalizeMobileTabs(DEFAULT_MOBILE_TABS, ['home', 'more', 'me', 'capture', 'settings'])
    expect(partial[2]).toBe('capture')
    expect(partial[0]).toBe('home')
    expect(partial[4]).toBe('me')
  })
})

describe('parse/serialize', () => {
  it('序列化不拿默认清单裁剪:用户选的非默认模块要能存下来', () => {
    expect(serializeMobileTabs(['home', 'blog', 'capture', 'games', 'me'])).toBe('["home","blog","capture","games","me"]')
  })

  it('解析失败/非数组按未设置处理', () => {
    expect(parseMobileTabs('not json')).toBeNull()
    expect(parseMobileTabs('{"a":1}')).toBeNull()
    expect(parseMobileTabs('')).toBeNull()
    expect(parseMobileTabs('["home",2,"me"]')).toEqual(['home', 'me'])
  })
})

describe('resolveMobileTab / isMobileTabActive', () => {
  const candidates = buildMobileTabCandidates(MODULES)

  it('拍摄是动作入口,没有路由', () => {
    const capture = resolveMobileTab('capture', candidates)
    expect(capture.capture).toBe(true)
    expect(capture.path).toBe('')
  })

  it('外壳三 Tab 标 shellTab,交给外壳切换而不是路由跳转', () => {
    for (const code of ['home', 'more', 'me']) {
      expect(resolveMobileTab(code, candidates).shellTab).toBe(code)
    }
  })

  it('被停用的模块解析不到时返回 null(调用方回落默认)', () => {
    expect(resolveMobileTab('hidden', candidates)).toBeNull()
  })

  it('子页面按前缀高亮,详情页也算所属入口', () => {
    const album = resolveMobileTab('album', candidates)
    expect(isMobileTabActive(album, '/album')).toBe(true)
    expect(isMobileTabActive(album, '/album/12')).toBe(true)
    expect(isMobileTabActive(album, '/blog')).toBe(false)
  })

  it('拍摄与外壳 Tab 不参与路由高亮', () => {
    expect(isMobileTabActive(resolveMobileTab('capture', candidates), '/home')).toBe(false)
    expect(isMobileTabActive(resolveMobileTab('me', candidates), '/home')).toBe(false)
  })
})
