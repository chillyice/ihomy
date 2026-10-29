import { describe, it, expect } from 'vitest'
import { computeKinship, computeMutualKinship } from './kinship'
import TERMS from './kinshipTerms'

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

// 母系三代(北方叫法):姥爷的父母是老姥爷/老姥姥;姥爷的兄弟是二姥爷,其妻是二姥姥。
// 老姥爷只填了「老姥姥的配偶」,没有直接写父女/父子边——这条姻亲路径曾算不出来。
const maternal = [
  { id: 1, name: '老姥爷', gender: 1, birthDate: '1908-01-01', spouseId: 2 },
  { id: 2, name: '老姥姥', gender: 2, birthDate: '1910-01-01', spouseId: 1 },
  { id: 3, name: '姥爷', gender: 1, birthDate: '1930-01-01', motherId: 2, spouseId: 4 },
  { id: 4, name: '姥姥', gender: 2, birthDate: '1932-01-01', spouseId: 3 },
  { id: 5, name: '二姥爷', gender: 1, birthDate: '1933-01-01', motherId: 2, spouseId: 6 },
  { id: 6, name: '二姥姥', gender: 2, birthDate: '1935-01-01', spouseId: 5 },
  { id: 7, name: '姑姥姥', gender: 2, birthDate: '1928-01-01', motherId: 2 },
  { id: 8, name: '妈妈', gender: 2, birthDate: '1960-01-01', fatherId: 3, motherId: 4 },
  { id: 9, name: '大舅', gender: 1, birthDate: '1958-01-01', fatherId: 3, motherId: 4, spouseId: 10 },
  { id: 10, name: '大舅妈', gender: 2, birthDate: '1959-01-01', spouseId: 9 },
  { id: 11, name: '我', gender: 1, birthDate: '1990-01-01', motherId: 8 },
  { id: 12, name: '表姨', gender: 2, birthDate: '1962-01-01', fatherId: 5, motherId: 6 },
  { id: 13, name: '表姐', gender: 2, birthDate: '1988-01-01', motherId: 12 },
  { id: 14, name: '表哥', gender: 1, birthDate: '1988-06-01', fatherId: 9, motherId: 10 },
]

const mt = (a, b) => computeKinship(maternal, a, b)

describe('母系祖辈与祖辈的兄弟姐妹', () => {
  it('姥爷的父母:老姥姥(血亲)与老姥爷(老一辈的配偶)', () => {
    expect(mt(11, 2).code).toBe('maternalGreatGrandmother')
    const elder = mt(11, 1)
    expect(elder.code).toBe('maternalGreatGrandfather')
    expect(elder.type).toBe('affinity')
    expect(mt(1, 11).code).toBe('maternalGreatGrandson')
  })

  it('姥爷的兄弟与姐妹:二姥爷 / 姑姥姥', () => {
    expect(mt(11, 5).code).toBe('grandUncleMaternalYounger')
    expect(mt(11, 7).code).toBe('grandAuntMaternalLine')
  })

  it('祖辈兄弟的妻子:二姥姥', () => {
    const wife = mt(11, 6)
    expect(wife.code).toBe('grandUncleMaternalYoungerWife')
    expect(wife.type).toBe('affinity')
  })

  it('妈妈的堂姐妹及其孩子:表姨 / 表姐', () => {
    expect(mt(11, 12).code).toBe('biaoAuntMaternal')
    expect(mt(12, 11).code).toBe('biaoSororalNephew')
    expect(mt(11, 13).code).toBe('biaoElderFemale')
  })

  it('舅舅、舅妈与表哥', () => {
    expect(mt(11, 9).code).toBe('maternalUncle')
    expect(mt(11, 10).code).toBe('maternalUncleWife')
    expect(mt(11, 14).code).toBe('biaoElderMale')
  })

  it('关系链能推出「妈妈的爸爸的妈妈」', () => {
    expect(mt(11, 2).chain).toEqual(['mother', 'father', 'mother'])
    expect(mt(11, 1).chain).toEqual(['mother', 'father', 'mother', 'husband'])
    expect(mt(11, 5).chain).toEqual(['mother', 'father', 'youngerBrother'])
  })

  it('用到的称谓码都在词表里', () => {
    const ids = maternal.map((m) => m.id)
    for (const a of ids) {
      for (const b of ids) {
        const r = computeMutualKinship(maternal, a, b)
        for (const item of [r.aToB, r.bToA]) {
          expect(TERMS[item.code], `${a}→${b} 缺 ${item.code}`).toBeTruthy()
        }
      }
    }
  })
})

describe('父系祖辈的兄弟姐妹', () => {
  const ext = [
    ...family,
    { id: 20, name: '爷爷的弟弟', gender: 1, birthDate: '1945-01-01', fatherId: 30, motherId: 31 },
    { id: 21, name: '爷爷的妹妹', gender: 2, birthDate: '1948-01-01', fatherId: 30, motherId: 31 },
    { id: 22, name: '奶奶的哥哥', gender: 1, birthDate: '1938-01-01', fatherId: 32, motherId: 33 },
    { id: 23, name: '奶奶的妹妹', gender: 2, birthDate: '1946-01-01', fatherId: 32, motherId: 33 },
    { id: 30, name: '曾祖父', gender: 1, spouseId: 31 },
    { id: 31, name: '曾祖母', gender: 2, spouseId: 30 },
    { id: 32, name: '外曾祖父', gender: 1, spouseId: 33 },
    { id: 33, name: '外曾祖母', gender: 2, spouseId: 32 },
  ]
  // 1 爷爷、2 奶奶:把 1 挂到 30/31,2 挂到 32/33
  ext[0] = { ...ext[0], fatherId: 30, motherId: 31 }
  ext[1] = { ...ext[1], fatherId: 32, motherId: 33 }
  const t = (a, b) => computeKinship(ext, a, b)

  it('爷爷的弟弟 / 妹妹:叔祖父 / 姑婆', () => {
    expect(t(5, 20).code).toBe('grandUnclePaternalYounger')
    expect(t(5, 21).code).toBe('grandAuntPaternal')
  })

  it('奶奶的哥哥 / 妹妹:舅公 / 姨婆', () => {
    expect(t(5, 22).code).toBe('grandUnclePaternalGrandma')
    expect(t(5, 23).code).toBe('grandAuntPaternalGrandma')
  })

  it('曾祖辈:爷爷的父母是老爷爷/老奶奶,奶奶的父母是太姥爷/太姥姥', () => {
    expect(t(5, 30).code).toBe('greatGrandfather')
    expect(t(5, 31).code).toBe('greatGrandmother')
    expect(t(5, 32).code).toBe('grandMaternalGreatGrandfather')
    expect(t(5, 33).code).toBe('grandMaternalGreatGrandmother')
  })
})

describe('性别缺失时的兜底', () => {
  it('只填了父/母关系、没填性别,按角色推断', () => {
    const f = [
      { id: 1, name: '姥姥', gender: 0 },
      { id: 2, name: '妈妈', gender: 0, motherId: 1 },
      { id: 3, name: '我', gender: 1, motherId: 2 },
    ]
    expect(computeKinship(f, 1, 3).code).toBe('maternalGrandson')
    expect(computeKinship(f, 1, 3).chain).toEqual(['daughter', 'son'])
  })
})
