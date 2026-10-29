import { describe, it, expect } from 'vitest'
import { buildFamilyGraph, normalizeFamily } from './familyGraph'

describe('normalizeFamily', () => {
  it('配偶只写一侧也按双向处理', () => {
    const g = normalizeFamily([
      { id: 1, gender: 1, spouseId: 2 },
      { id: 2, gender: 2 },
    ])
    expect(g.spouseOf.get(1).has(2)).toBe(true)
    expect(g.spouseOf.get(2).has(1)).toBe(true)
  })

  it('指向不存在成员的父/母/配偶计入 orphanLinks', () => {
    const g = normalizeFamily([{ id: 1, fatherId: 99, spouseId: 98 }])
    expect(g.orphanLinks).toBe(2)
    expect(g.parentsOf.get(1)).toEqual([])
  })
})

describe('buildFamilyGraph', () => {
  it('忽略 generation:夫妻永远同一行并连上配偶线', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '胡', gender: 1, spouseId: 2, generation: 3 },
      { id: 2, name: '高', gender: 2, generation: 0 },
    ])
    const hu = graph.nodes.get(1)
    const gao = graph.nodes.get(2)
    expect(hu.y).toBe(gao.y)
    expect(graph.edges.filter((e) => e.type === 'spouse')).toHaveLength(1)
    expect(graph.stats.spousePairs).toBe(1)
  })

  it('孩子挂在父母单元下方,只有一条主亲子线', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '胡', gender: 1, spouseId: 2, generation: 5 },
      { id: 2, name: '高', gender: 2, generation: 1 },
      { id: 3, name: '小明', gender: 1, fatherId: 1, motherId: 2, generation: 9 },
    ])
    expect(graph.nodes.get(3).y).toBeGreaterThan(graph.nodes.get(1).y)
    const parentEdges = graph.edges.filter((e) => e.type === 'parent')
    expect(parentEdges).toHaveLength(1)
    expect(parentEdges[0].primary).toBe(true)
    expect(parentEdges[0].memberId).toBe(3)
  })

  it('父母不在同一单元时,第二个父/母画成补充虚线', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '父', gender: 1 },
      { id: 2, name: '母', gender: 2 },
      { id: 3, name: '子', gender: 1, fatherId: 1, motherId: 2 },
    ])
    const parentEdges = graph.edges.filter((e) => e.type === 'parent')
    expect(parentEdges).toHaveLength(2)
    expect(parentEdges.filter((e) => e.primary)).toHaveLength(1)
    expect(parentEdges.filter((e) => !e.primary)).toHaveLength(1)
  })

  it('父子关系成环也能布局,不会死循环', () => {
    const graph = buildFamilyGraph([
      { id: 1, gender: 1, fatherId: 2 },
      { id: 2, gender: 1, fatherId: 1 },
    ])
    expect(graph.nodes.size).toBe(2)
    expect(graph.height).toBeGreaterThan(0)
  })

  it('空数据返回空图', () => {
    const graph = buildFamilyGraph([])
    expect(graph.nodes.size).toBe(0)
    expect(graph.width).toBe(0)
  })
})
