import { describe, it, expect } from 'vitest'
import { computeKinship, computeMutualKinship } from './kinship'

// 测试家庭:
// 1 爷爷 + 2 奶奶
//   ├ 3 爸爸 + 4 妈妈
//   │   ├ 5 我
//   │   └ 6 妹妹
//   └ 7 叔叔 + 8 婶婶
//       └ 9 堂弟
// 5 + 10 妻子;10 的家人:11 岳父、12 岳母、13 妻弟、16 妻妹 + 17 连襟
const family = [
  { id: 1, name: '爷爷', gender: 1, birthDate: '1940-01-01', spouseId: 2 },
  { id: 2, name: '奶奶', gender: 2, birthDate: '1942-01-01' },
  { id: 3, name: '爸爸', gender: 1, birthDate: '1965-01-01', fatherId: 1, motherId: 2, spouseId: 4 },
  { id: 4, name: '妈妈', gender: 2, birthDate: '1967-01-01' },
  { id: 5, name: '我', gender: 1, birthDate: '1990-01-01', fatherId: 3, motherId: 4, spouseId: 10 },
  { id: 6, name: '妹妹', gender: 2, birthDate: '1993-01-01', fatherId: 3, motherId: 4 },
  { id: 7, name: '叔叔', gender: 1, birthDate: '1968-01-01', fatherId: 1, motherId: 2, spouseId: 8 },
  { id: 8, name: '婶婶', gender: 2, birthDate: '1970-01-01' },
  { id: 9, name: '堂弟', gender: 1, birthDate: '1992-01-01', fatherId: 7, motherId: 8 },
  { id: 10, name: '妻子', gender: 2, birthDate: '1991-01-01', fatherId: 11, motherId: 12, spouseId: 5 },
  { id: 11, name: '岳父', gender: 1, birthDate: '1963-01-01', spouseId: 12 },
  { id: 12, name: '岳母', gender: 2, birthDate: '1964-01-01' },
  { id: 13, name: '妻弟', gender: 1, birthDate: '1994-01-01', fatherId: 11, motherId: 12 },
  { id: 14, name: '儿子', gender: 1, birthDate: '2018-01-01', fatherId: 5, motherId: 10 },
  { id: 16, name: '妻妹', gender: 2, birthDate: '1996-01-01', fatherId: 11, motherId: 12 },
  { id: 17, name: '连襟', gender: 1, birthDate: '1993-05-01', spouseId: 16 },
  { id: 18, name: '姐姐', gender: 2, birthDate: '1988-01-01', fatherId: 3, motherId: 4 },
  { id: 19, name: '外甥', gender: 1, birthDate: '2015-01-01', motherId: 18 },
]

const term = (a, b) => computeKinship(family, a, b).code

describe('直系血亲', () => {
  it('父母、祖辈、子女', () => {
    expect(term(5, 3)).toBe('father')
    expect(term(5, 4)).toBe('mother')
    expect(term(5, 1)).toBe('paternalGrandfather')
    expect(term(5, 2)).toBe('paternalGrandmother')
    expect(term(5, 14)).toBe('son')
    expect(term(14, 5)).toBe('father')
    expect(term(1, 5)).toBe('grandson')
  })

  it('外祖父母与外孙', () => {
    const f = [
      { id: 1, name: '外公', gender: 1 },
      { id: 2, name: '外婆', gender: 2 },
      { id: 3, name: '妈妈', gender: 2, fatherId: 1, motherId: 2 },
      { id: 4, name: '我', gender: 1, motherId: 3 },
    ]
    expect(computeKinship(f, 4, 1).code).toBe('maternalGrandfather')
    expect(computeKinship(f, 4, 2).code).toBe('maternalGrandmother')
    expect(computeKinship(f, 1, 4).code).toBe('maternalGrandson')
  })
})

describe('旁系血亲', () => {
  it('兄弟姐妹按出生日期分长幼', () => {
    expect(term(5, 6)).toBe('youngerSister')
    expect(term(6, 5)).toBe('elderBrother')
  })

  it('叔伯姑舅姨', () => {
    expect(term(5, 7)).toBe('paternalUncleYounger')
    expect(term(9, 3)).toBe('paternalUncleElder')
    expect(term(5, 13)).not.toBe('maternalUncle') // 妻弟属姻亲,另测
  })

  it('侄甥', () => {
    expect(term(7, 5)).toBe('nephew')
    expect(term(6, 14)).toBe('nephew')
    expect(term(6, 19)).toBe('sororalNephew')
    expect(term(7, 14)).toBe('greatNephew')
  })

  it('堂表兄弟姐妹', () => {
    expect(term(5, 9)).toBe('cousinYoungerMale')
    expect(term(9, 5)).toBe('cousinElderMale')
  })

  it('无关两人返回 none', () => {
    const f = [...family, { id: 99, name: '路人', gender: 1 }]
    expect(computeKinship(f, 5, 99).code).toBe('none')
  })
})

describe('姻亲', () => {
  it('配偶', () => {
    expect(term(5, 10)).toBe('wife')
    expect(term(10, 5)).toBe('husband')
  })

  it('岳父岳母、公公婆婆', () => {
    expect(term(5, 11)).toBe('wifeFather')
    expect(term(5, 12)).toBe('wifeMother')
    expect(term(10, 3)).toBe('husbandFather')
    expect(term(10, 4)).toBe('husbandMother')
  })

  it('妻弟、妻妹、连襟', () => {
    expect(term(5, 13)).toBe('wifeYoungerBrother')
    expect(term(5, 16)).toBe('wifeYoungerSister')
    expect(term(5, 17)).toBe('coBrotherInLaw')
  })

  it('女婿、儿媳、嫂子', () => {
    expect(term(11, 5)).toBe('sonInLaw')
    expect(term(3, 10)).toBe('daughterInLaw')
    expect(term(5, 8)).toBe('paternalUncleYoungerWife')
    expect(term(9, 4)).toBe('paternalUncleElderWife')
  })

  it('双向称谓成对返回', () => {
    const { aToB, bToA } = computeMutualKinship(family, 5, 9)
    expect(aToB.code).toBe('cousinYoungerMale')
    expect(bToA.code).toBe('cousinElderMale')
  })
})
