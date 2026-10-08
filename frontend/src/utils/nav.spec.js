import { describe, it, expect } from 'vitest'
import { buildNavItems, buildNavGroups } from './navModules'

// 导航虚拟入口(设置/帮助/运维)不进 sys_home_module,靠 buildNavItems 无条件追加
describe('buildNavItems 虚拟入口', () => {
  it('没有 DB 行也有「设置」和「帮助」,且帮助只出现一次', () => {
    const items = buildNavItems([])
    const codes = items.map((i) => i.code)
    expect(codes).toContain('settings')
    expect(codes).toContain('help')
    expect(codes.filter((c) => c === 'help')).toHaveLength(1)
    expect(items.find((i) => i.code === 'help').path).toBe('/help')
  })

  it('运维管理仅在有运维权限时出现,且排在帮助之后', () => {
    expect(buildNavItems([]).map((i) => i.code)).not.toContain('ops')
    const codes = buildNavItems([], { hasOps: true }).map((i) => i.code)
    expect(codes).toContain('ops')
    expect(codes.indexOf('help')).toBeLessThan(codes.indexOf('ops'))
  })

  it('DB 模块与虚拟入口一起进「系统」组', () => {
    const groups = buildNavGroups([{ code: 'blog', title: '博客', path: '/blog', category: 'content', sortOrder: 1, enabled: 1 }])
    const system = groups.find((g) => g.category === 'system')
    expect(system.items.map((i) => i.code)).toEqual(['settings', 'help'])
  })
})
