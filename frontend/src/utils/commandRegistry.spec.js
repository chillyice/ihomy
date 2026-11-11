import { describe, it, expect } from 'vitest'
import { COMMANDS, filterCommands, visibleCommands } from './commandRegistry'

const cmd = (id, label) => ({ id, label })
const ids = (list) => list.map((c) => c.id)

describe('filterCommands', () => {
  const list = [cmd('a', '首页'), cmd('b', '家庭相册'), cmd('c', 'Blog'), cmd('d', '图书馆'), cmd('e', 'Settings')]

  it('空 query 返回前 limit 条', () => {
    expect(ids(filterCommands(list, '', 3))).toEqual(['a', 'b', 'c'])
    expect(ids(filterCommands(list, '   ', 2))).toEqual(['a', 'b'])
  })

  it('中文子串命中', () => {
    expect(ids(filterCommands(list, '相册'))).toEqual(['b'])
    expect(ids(filterCommands(list, '图书'))).toEqual(['d'])
  })

  it('不区分大小写', () => {
    expect(ids(filterCommands(list, 'blog'))).toEqual(['c'])
    expect(ids(filterCommands(list, 'SETTING'))).toEqual(['e'])
  })

  it('同时匹配 label 与 id,子序列也能命中', () => {
    expect(ids(filterCommands(list, 'blg'))).toEqual(['c'])
    expect(ids(filterCommands([cmd('nav-album', '相册')], 'album'))).toEqual(['nav-album'])
  })

  it('无命中返回空数组', () => {
    expect(filterCommands(list, 'zzz')).toEqual([])
  })

  it('同分按原始顺序,子串命中排在子序列命中之前', () => {
    const mixed = [cmd('x1', 'Beta'), cmd('x2', 'Blog'), cmd('x3', 'Budget Gap')]
    // b / l / o / g 是子序列;Beta / Blog / Budget Gap 都含 b 子串(不区分大小写)
    expect(ids(filterCommands(mixed, 'b'))).toEqual(['x1', 'x2', 'x3'])
    // blg 只命中 Blog(子串不成立、子序列成立)
    expect(ids(filterCommands(mixed, 'blg'))).toEqual(['x2'])
  })

  it('匹配结果也受 limit 限制', () => {
    expect(filterCommands(list, 'o', 1)).toHaveLength(1)
  })
})

describe('visibleCommands(守卫)', () => {
  it('游客看不到文件 / 成员 / 设置', () => {
    const guest = visibleCommands({ isLoggedIn: false })
    expect(ids(guest)).not.toContain('nav-files')
    expect(ids(guest)).not.toContain('nav-member')
    expect(ids(guest)).not.toContain('nav-settings')
    // 公开页面仍在
    expect(ids(guest)).toContain('nav-album')
    expect(ids(guest)).toContain('nav-library')
  })

  it('登录后三类入口重新出现', () => {
    const user = visibleCommands({ isLoggedIn: true })
    expect(ids(user)).toContain('nav-files')
    expect(ids(user)).toContain('nav-member')
    expect(ids(user)).toContain('nav-settings')
  })

  it('当前主题对应的切换命令自动隐藏', () => {
    expect(ids(visibleCommands({ theme: 'warm' }))).not.toContain('action-theme-warm')
    expect(ids(visibleCommands({ theme: 'warm' }))).toContain('action-theme-guangchen')
    expect(ids(visibleCommands({ theme: 'guangchen' }))).not.toContain('action-theme-guangchen')
  })

  it('命令数据本身不重复 id,且跳转路径指向站内', () => {
    expect(new Set(ids(COMMANDS)).size).toBe(COMMANDS.length)
    for (const c of COMMANDS) {
      if (c.group === 'nav') expect(c.path.startsWith('/')).toBe(true)
    }
  })
})
