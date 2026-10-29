// 中华传统亲属称谓:基于 family_tree 的父/母/配偶关系推算任意两人之间的称谓。
// 只返回稳定的称谓码(如 father / paternalUncleElder / cousinYoungerFemale),
// 文案在 i18n 的 kinship.* 下维护,中英各一份;本文件不出现用户可见文案。
//
// 设计取舍:
// - 姻亲只展开两层(配偶的血亲、血亲的配偶),再远统一显示「姻亲」,避免把关系链算错。
// - 长幼按出生日期比较;缺失时用「兄弟/姐妹」等中性称谓,不瞎猜哥哥还是弟弟。
// - 共同祖先取最近的一个;堂/表按父系 + 男丁线判定。

import { normalizeFamily } from './familyGraph'

const MAX_UP = 10

const sex = (g, id) => g.byId.get(id)?.gender ?? 0

/** 返回 'elder' | 'younger' | ''(other 相对 subject 的长幼)。 */
function seniority(g, subjectId, otherId) {
  const a = g.byId.get(subjectId)?.birthDate
  const b = g.byId.get(otherId)?.birthDate
  if (!a || !b) return ''
  const ta = Date.parse(a)
  const tb = Date.parse(b)
  if (Number.isNaN(ta) || Number.isNaN(tb) || ta === tb) return ''
  return tb < ta ? 'elder' : 'younger'
}

/** A 向上找所有祖先:ids 为 A→…→祖先,path 为 f/m 序列。 */
function ancestorInfo(g, startId) {
  const map = new Map()
  map.set(startId, { id: startId, ids: [startId], path: [] })
  const visit = (id, ids, path) => {
    if (path.length >= MAX_UP) return
    for (const p of g.parentsOf.get(id) || []) {
      if (ids.includes(p.id)) continue
      const nextIds = [...ids, p.id]
      const nextPath = [...path, p.role === 'father' ? 'f' : 'm']
      const current = map.get(p.id)
      if (!current || current.path.length > nextPath.length) {
        map.set(p.id, { id: p.id, ids: nextIds, path: nextPath })
      }
      visit(p.id, nextIds, nextPath)
    }
  }
  visit(startId, [startId], [])
  return map
}

function ancestorCode(upPath) {
  const depth = upPath.length
  const male = upPath[depth - 1] === 'f'
  const maternal = upPath[0] === 'm'
  if (depth === 1) return male ? 'father' : 'mother'
  if (depth === 2) {
    if (maternal) return male ? 'maternalGrandfather' : 'maternalGrandmother'
    return male ? 'paternalGrandfather' : 'paternalGrandmother'
  }
  const level = depth - 2
  if (level === 1) {
    if (maternal) return male ? 'maternalGreatGrandfather' : 'maternalGreatGrandmother'
    return male ? 'greatGrandfather' : 'greatGrandmother'
  }
  if (level === 2) {
    if (maternal) return male ? 'maternalGreatGreatGrandfather' : 'maternalGreatGreatGrandmother'
    return male ? 'greatGreatGrandfather' : 'greatGreatGrandmother'
  }
  if (level === 3) {
    if (maternal) return male ? 'maternalHeavenGrandfather' : 'maternalHeavenGrandmother'
    return male ? 'heavenGrandfather' : 'heavenGrandmother'
  }
  return male ? 'ancestorMale' : 'ancestorFemale'
}

function descendantCode(g, downIds) {
  const depth = downIds.length - 1
  const male = sex(g, downIds[depth]) === 1
  const throughSon = sex(g, downIds[1]) === 1
  if (depth === 1) return male ? 'son' : 'daughter'
  if (depth === 2) {
    if (!throughSon) return male ? 'maternalGrandson' : 'maternalGranddaughter'
    return male ? 'grandson' : 'granddaughter'
  }
  if (depth === 3) {
    if (!throughSon) return male ? 'maternalGreatGrandson' : 'maternalGreatGranddaughter'
    return male ? 'greatGrandson' : 'greatGranddaughter'
  }
  if (depth === 4) {
    if (!throughSon) return male ? 'maternalGreatGreatGrandson' : 'maternalGreatGreatGranddaughter'
    return male ? 'greatGreatGrandson' : 'greatGreatGranddaughter'
  }
  return male ? 'descendantMale' : 'descendantFemale'
}

function siblingCode(g, subjectId, otherId) {
  const s = sex(g, otherId)
  const age = seniority(g, subjectId, otherId)
  if (s === 1) return age === 'elder' ? 'elderBrother' : age === 'younger' ? 'youngerBrother' : 'brother'
  if (s === 2) return age === 'elder' ? 'elderSister' : age === 'younger' ? 'youngerSister' : 'sister'
  return 'sibling'
}

function siblingChildCode(g, siblingId, childId) {
  const siblingSex = sex(g, siblingId)
  const childSex = sex(g, childId)
  if (siblingSex === 2) return childSex === 2 ? 'sororalNiece' : 'sororalNephew'
  if (siblingSex === 1) return childSex === 2 ? 'niece' : 'nephew'
  return childSex === 2 ? 'juniorFemale' : 'juniorMale'
}

function siblingGrandchildCode(g, siblingId, childId) {
  const siblingSex = sex(g, siblingId)
  const childSex = sex(g, childId)
  if (siblingSex === 2) return childSex === 2 ? 'sororalGreatNiece' : 'sororalGreatNephew'
  if (siblingSex === 1) return childSex === 2 ? 'greatNiece' : 'greatNephew'
  return childSex === 2 ? 'juniorFemale' : 'juniorMale'
}

function parentSiblingCode(g, parentId, uncleId) {
  const parentIsFather = sex(g, parentId) === 1
  const uncleSex = sex(g, uncleId)
  const age = seniority(g, parentId, uncleId)
  if (parentIsFather) {
    if (uncleSex === 2) return 'paternalAunt'
    return age === 'elder' ? 'paternalUncleElder' : age === 'younger' ? 'paternalUncleYounger' : 'paternalUncle'
  }
  if (uncleSex === 2) return 'maternalAunt'
  return 'maternalUncle'
}

function cousinCode(g, subjectId, cousinId, tang) {
  const s = sex(g, cousinId)
  const age = seniority(g, subjectId, cousinId)
  const prefix = tang ? 'cousin' : 'biao'
  const suffix = age === 'elder' ? 'Elder' : age === 'younger' ? 'Younger' : ''
  return `${prefix}${suffix}${s === 1 ? 'Male' : 'Female'}`
}

function cousinChildCode(g, cousinId, childId, tang) {
  const s = sex(g, childId)
  const prefix = tang ? 'cousin' : 'biao'
  return `${prefix}${s === 2 ? 'Niece' : 'Nephew'}`
}

function grandUncleCode(g, grandParentId, uncleId) {
  const uncleSex = sex(g, uncleId)
  const grandParentSex = sex(g, grandParentId)
  if (grandParentSex === 2) return uncleSex === 2 ? 'grandAuntMaternal' : 'grandUncleMaternal'
  return uncleSex === 2 ? 'grandAuntPaternal' : 'grandUnclePaternal'
}

function parentCousinCode(g, parentId, relativeId, tang) {
  const s = sex(g, relativeId)
  if (s === 2) return tang ? 'tangAunt' : 'biaoAunt'
  const age = seniority(g, parentId, relativeId)
  if (tang) return age === 'elder' ? 'tangUncleElder' : age === 'younger' ? 'tangUncleYounger' : 'tangUncle'
  return age === 'elder' ? 'biaoUncleElder' : age === 'younger' ? 'biaoUncleYounger' : 'biaoUncle'
}

/** 找最近共同祖先并出称谓码;无血缘返回 null。 */
function bloodKinship(g, aId, bId) {
  const aAnc = ancestorInfo(g, aId)
  const bAnc = ancestorInfo(g, bId)
  let best = null
  for (const [ancestorId, a] of aAnc) {
    const b = bAnc.get(ancestorId)
    if (!b) continue
    const L = a.path.length
    const R = b.path.length
    const firstDown = R > 0 ? sex(g, b.ids[b.ids.length - 2]) : 0
    const tang = a.path[0] === 'f' && firstDown === 1
    const score = L + R
    if (!best || score < best.score || (score === best.score && tang && !best.tang)) {
      best = { ancestorId, a, b, L, R, score, tang }
    }
  }
  if (!best) return null

  const { a, b, L, R, tang } = best
  const up = a.path
  const downIds = b.ids.slice().reverse()

  if (L === 0 && R === 0) return { code: 'self', distance: 0, type: 'blood' }
  if (R === 0) return { code: ancestorCode(up), distance: L, type: 'blood' }
  if (L === 0) return { code: descendantCode(g, downIds), distance: R, type: 'blood' }

  if (L === 1 && R === 1) return { code: siblingCode(g, aId, bId), distance: 2, type: 'blood' }
  if (L === 1 && R === 2) return { code: siblingChildCode(g, downIds[1], bId), distance: 3, type: 'blood' }
  if (L === 1 && R === 3) return { code: siblingGrandchildCode(g, downIds[1], bId), distance: 4, type: 'blood' }
  if (L === 2 && R === 1) return { code: parentSiblingCode(g, a.ids[1], bId), distance: 3, type: 'blood' }
  if (L === 2 && R === 2) return { code: cousinCode(g, aId, bId, tang), distance: 4, type: 'blood' }
  if (L === 2 && R === 3) return { code: cousinChildCode(g, downIds[2], bId, tang), distance: 5, type: 'blood' }
  if (L === 3 && R === 1) return { code: grandUncleCode(g, a.ids[2], bId), distance: 4, type: 'blood' }
  if (L === 3 && R === 2) return { code: parentCousinCode(g, a.ids[1], bId, tang), distance: 5, type: 'blood' }

  // 更远的旁系:只给「族亲/远亲」,避免生造称谓
  return { code: best.score <= 6 ? 'relative' : 'distant', distance: best.score, type: 'blood' }
}

// 血亲的配偶:嫂子/弟媳/姐夫/妹夫/伯母/婶婶/姑父/舅妈/姨父/侄媳…
const SPOUSE_OF_BLOOD = {
  father: 'mother',
  mother: 'father',
  son: 'daughterInLaw',
  daughter: 'sonInLaw',
  elderBrother: 'sisterInLawElder',
  youngerBrother: 'sisterInLawYounger',
  brother: 'sisterInLaw',
  elderSister: 'brotherInLawElder',
  youngerSister: 'brotherInLawYounger',
  sister: 'brotherInLaw',
  paternalGrandfather: 'paternalGrandmother',
  paternalGrandmother: 'paternalGrandfather',
  maternalGrandfather: 'maternalGrandmother',
  maternalGrandmother: 'maternalGrandfather',
  grandson: 'grandsonWife',
  granddaughter: 'granddaughterHusband',
  maternalGrandson: 'maternalGrandsonWife',
  maternalGranddaughter: 'maternalGranddaughterHusband',
  greatGrandson: 'greatGrandsonWife',
  greatGranddaughter: 'greatGranddaughterHusband',
  nephew: 'nephewWife',
  niece: 'nieceHusband',
  sororalNephew: 'sororalNephewWife',
  sororalNiece: 'sororalNieceHusband',
  paternalUncleElder: 'paternalUncleElderWife',
  paternalUncleYounger: 'paternalUncleYoungerWife',
  paternalUncle: 'paternalUncleWife',
  paternalAunt: 'paternalAuntHusband',
  maternalUncle: 'maternalUncleWife',
  maternalAunt: 'maternalAuntHusband',
  cousinElderMale: 'cousinElderMaleWife',
  cousinYoungerMale: 'cousinYoungerMaleWife',
  cousinMale: 'cousinMaleWife',
  cousinElderFemale: 'cousinElderFemaleHusband',
  cousinYoungerFemale: 'cousinYoungerFemaleHusband',
  cousinFemale: 'cousinFemaleHusband',
  biaoElderMale: 'biaoElderMaleWife',
  biaoYoungerMale: 'biaoYoungerMaleWife',
  biaoMale: 'biaoMaleWife',
  biaoElderFemale: 'biaoElderFemaleHusband',
  biaoYoungerFemale: 'biaoYoungerFemaleHusband',
  biaoFemale: 'biaoFemaleHusband',
  cousinNephew: 'cousinNephewWife',
  cousinNiece: 'cousinNieceHusband',
  biaoNephew: 'biaoNephewWife',
  biaoNiece: 'biaoNieceHusband',
}

/** 配偶的血亲:岳父/岳母、公公/婆婆、大舅子、小姨子、妯娌/连襟等。 */
function bloodOfSpouse(g, aId, relCode) {
  const aSex = sex(g, aId)
  const male = aSex === 1
  switch (relCode) {
    case 'father':
      return male ? 'wifeFather' : 'husbandFather'
    case 'mother':
      return male ? 'wifeMother' : 'husbandMother'
    case 'elderBrother':
      return male ? 'wifeElderBrother' : 'husbandElderBrother'
    case 'youngerBrother':
      return male ? 'wifeYoungerBrother' : 'husbandYoungerBrother'
    case 'brother':
      return male ? 'wifeBrother' : 'husbandBrother'
    case 'elderSister':
      return male ? 'wifeElderSister' : 'husbandElderSister'
    case 'youngerSister':
      return male ? 'wifeYoungerSister' : 'husbandYoungerSister'
    case 'sister':
      return male ? 'wifeSister' : 'husbandSister'
    case 'son':
      return 'stepSon'
    case 'daughter':
      return 'stepDaughter'
    case 'nephew':
    case 'sororalNephew':
    case 'niece':
    case 'sororalNiece':
      return male ? 'wifeNephew' : 'husbandNephew'
    case 'brotherInLawElder':
    case 'brotherInLawYounger':
    case 'brotherInLaw':
      return male ? 'coBrotherInLaw' : 'inLaw'
    default:
      return 'inLaw'
  }
}

export function kinshipOf(g, aId, bId) {
  if (!aId || !bId || !g.byId.has(aId) || !g.byId.has(bId)) return { code: 'unknown', distance: Infinity, type: 'none' }
  if (aId === bId) return { code: 'self', distance: 0, type: 'blood' }

  const directSpouse = g.spouseOf.get(aId)?.has(bId)
  if (directSpouse) {
    const s = sex(g, bId)
    return { code: s === 1 ? 'husband' : s === 2 ? 'wife' : 'spouse', distance: 1, type: 'spouse' }
  }

  const blood = bloodKinship(g, aId, bId)
  if (blood) return blood

  // B 是 A 某位血亲的配偶(嫂子/姐夫/儿媳/岳父…)
  for (const x of g.spouseOf.get(bId) || []) {
    const rel = bloodKinship(g, aId, x)
    if (!rel) continue
    const mapped = SPOUSE_OF_BLOOD[rel.code]
    if (mapped) return { code: mapped, distance: rel.distance + 1, type: 'affinity' }
  }

  // A 的配偶是 B 的血亲(岳父/公公/小姨子…)
  for (const x of g.spouseOf.get(aId) || []) {
    const rel = bloodKinship(g, x, bId)
    if (!rel) continue
    const mapped = bloodOfSpouse(g, aId, rel.code)
    if (mapped) return { code: mapped, distance: rel.distance + 1, type: 'affinity' }
  }

  // 双方配偶是兄弟姐妹:连襟 / 妯娌
  for (const x of g.spouseOf.get(aId) || []) {
    for (const y of g.spouseOf.get(bId) || []) {
      const sib = bloodKinship(g, x, y)
      if (!sib) continue
      if (['elderBrother', 'youngerBrother', 'brother', 'elderSister', 'youngerSister', 'sister'].includes(sib.code)) {
        const aSex = sex(g, aId)
        const bSex = sex(g, bId)
        const code = aSex === 1 && bSex === 1 ? 'coBrotherInLaw' : aSex === 2 && bSex === 2 ? 'coSisterInLaw' : 'inLaw'
        return { code, distance: 4, type: 'affinity' }
      }
    }
  }

  return { code: 'none', distance: Infinity, type: 'none' }
}

/** 对外入口:传原始成员数组与两个 id。 */
export function computeKinship(members, aId, bId) {
  return kinshipOf(normalizeFamily(members), aId, bId)
}

/** 双向称谓:{ aToB, bToA }。 */
export function computeMutualKinship(members, aId, bId) {
  const g = normalizeFamily(members)
  return { aToB: kinshipOf(g, aId, bId), bToA: kinshipOf(g, bId, aId) }
}
