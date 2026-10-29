// 中华传统亲属称谓:基于 family_tree 的父/母/配偶关系推算任意两人之间的称谓。
// 只返回稳定的称谓码(如 father / maternalUncle / cousinYoungerFemale),
// 文案在 kinshipTerms.js 维护,本文件不出现用户可见文案。
//
// 设计取舍:
// - 直系祖先:父系/母系到曾祖辈;「经过祖母/外祖母」的祖先(奶奶的父母、姥姥的父母)
//   单独成码——口语里前者叫太姥爷/太姥姥,后者叫老姥爷/老姥姥。
// - 旁系只算到「祖辈的兄弟姐妹」与「再从兄弟姐妹」,更远给「族亲/远亲」,不生造称谓。
// - 姻亲只展开两层(配偶的血亲、血亲的配偶);配偶的祖辈/晚辈随配偶称呼,再远统一「姻亲」。
// - 长幼按出生日期比较;缺失时用「伯叔/兄弟」等中性称谓,不瞎猜。
// - 共同祖先取最近的一个;堂/表按父系 + 整条男丁链路判定(中间有一代女儿就是表)。
// - 每个结果带 chain(关系链的称谓码),界面可展示「妈妈的爸爸的妈妈」这样的推导路径。

import { normalizeFamily } from './familyGraph'

const MAX_UP = 10

// 性别:成员字段优先;缺失时用「作为谁的 father/mother 出现过」反推——
// 家谱的父/母关系自带角色,比 gender=0 更可靠(能救回只填了关系没填性别的祖辈)。
const SEX_CACHE = new WeakMap()

function sexMapOf(g) {
  let map = SEX_CACHE.get(g)
  if (map) return map
  map = new Map()
  for (const [id, m] of g.byId) {
    if (m.gender === 1 || m.gender === 2) map.set(id, m.gender)
  }
  for (const parents of g.parentsOf.values()) {
    for (const p of parents) {
      if (!map.has(p.id)) map.set(p.id, p.role === 'father' ? 1 : 2)
    }
  }
  SEX_CACHE.set(g, map)
  return map
}

const sex = (g, id) => sexMapOf(g).get(id) ?? 0
const isMale = (g, id) => sex(g, id) === 1
const isFemale = (g, id) => sex(g, id) === 2

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
  const maternalLine = upPath[0] === 'm'
  if (depth === 1) return male ? 'father' : 'mother'
  if (depth === 2) {
    if (maternalLine) return male ? 'maternalGrandfather' : 'maternalGrandmother'
    return male ? 'paternalGrandfather' : 'paternalGrandmother'
  }
  // 曾祖辈里经过祖母/外祖母的那一支(奶奶的父母、姥姥的父母)是「太姥爷/太姥姥」
  if (depth === 3 && upPath[1] === 'm') {
    return male ? 'grandMaternalGreatGrandfather' : 'grandMaternalGreatGrandmother'
  }
  const level = depth - 2
  if (level === 1) {
    if (maternalLine) return male ? 'maternalGreatGrandfather' : 'maternalGreatGrandmother'
    return male ? 'greatGrandfather' : 'greatGrandmother'
  }
  if (level === 2) {
    if (maternalLine) return male ? 'maternalGreatGreatGrandfather' : 'maternalGreatGreatGrandmother'
    return male ? 'greatGreatGrandfather' : 'greatGreatGrandmother'
  }
  if (level === 3) {
    if (maternalLine) return male ? 'maternalHeavenGrandfather' : 'maternalHeavenGrandmother'
    return male ? 'heavenGrandfather' : 'heavenGrandmother'
  }
  return male ? 'ancestorMale' : 'ancestorFemale'
}

/** downIds 为「共同祖先 → … → B」;「外」看 B 自己的父/母是女儿(孙女的孩子也是外孙)。 */
function descendantCode(g, downIds) {
  const depth = downIds.length - 1
  const male = isMale(g, downIds[depth])
  const throughDaughter = isFemale(g, downIds[depth - 1])
  if (depth === 1) return male ? 'son' : 'daughter'
  if (depth === 2) {
    if (throughDaughter) return male ? 'maternalGrandson' : 'maternalGranddaughter'
    return male ? 'grandson' : 'granddaughter'
  }
  if (depth === 3) {
    if (throughDaughter) return male ? 'maternalGreatGrandson' : 'maternalGreatGranddaughter'
    return male ? 'greatGrandson' : 'greatGranddaughter'
  }
  if (depth === 4) {
    if (throughDaughter) return male ? 'maternalGreatGreatGrandson' : 'maternalGreatGreatGranddaughter'
    return male ? 'greatGreatGrandson' : 'greatGreatGranddaughter'
  }
  return male ? 'descendantMale' : 'descendantFemale'
}

/** 关系链:向上按 father/mother,向下按 son/daughter,拼起来就是「妈妈的爸爸」。 */
function upChain(upPath) {
  return upPath.map((p) => (p === 'f' ? 'father' : 'mother'))
}

function downChain(g, downIds) {
  const chain = []
  for (let i = 1; i < downIds.length; i += 1) {
    chain.push(isFemale(g, downIds[i]) ? 'daughter' : 'son')
  }
  return chain
}

function spouseVerb(g, id) {
  return isMale(g, id) ? 'husband' : 'wife'
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
  const parentIsFather = isMale(g, parentId)
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

/** B 是 A 的表/堂兄弟姐妹的孩子:按「B 的父/母」是不是女儿分侄/甥。 */
function cousinChildCode(g, cousinId, childId, tang) {
  const s = sex(g, childId)
  if (isFemale(g, cousinId)) {
    if (tang) return s === 2 ? 'cousinSororalNiece' : 'cousinSororalNephew'
    return s === 2 ? 'biaoSororalNiece' : 'biaoSororalNephew'
  }
  return `${tang ? 'cousin' : 'biao'}${s === 2 ? 'Niece' : 'Nephew'}`
}

/**
 * 祖辈的兄弟姐妹:先看是哪一边(父系爷爷/母系姥爷),再看祖辈本人是男是女,
 * 决定「伯叔祖父 / 舅公 / 外叔祖父 / 舅姥爷」这一格。
 */
function grandUncleCode(g, grandParentId, uncleId, upPath) {
  const uncleSex = sex(g, uncleId)
  const grandParentIsMale = upPath[1] === 'f'
  const paternalLine = upPath[0] === 'f'
  const age = seniority(g, grandParentId, uncleId)
  if (paternalLine) {
    if (uncleSex === 2) return grandParentIsMale ? 'grandAuntPaternal' : 'grandAuntPaternalGrandma'
    if (!grandParentIsMale) return 'grandUnclePaternalGrandma'
    return age === 'elder' ? 'grandUnclePaternalElder' : age === 'younger' ? 'grandUnclePaternalYounger' : 'grandUnclePaternal'
  }
  if (uncleSex === 2) return grandParentIsMale ? 'grandAuntMaternalLine' : 'grandAuntMaternalGrandma'
  if (!grandParentIsMale) return 'grandUncleMaternalGrandma'
  return age === 'elder' ? 'grandUncleMaternalElder' : age === 'younger' ? 'grandUncleMaternalYounger' : 'grandUncleMaternal'
}

/** 父/母的表堂兄弟姐妹:父亲那边叫伯叔/姑,母亲那边叫舅/姨。 */
function parentCousinCode(g, parentId, relativeId, tang) {
  const parentIsFather = isMale(g, parentId)
  const s = sex(g, relativeId)
  const age = seniority(g, parentId, relativeId)
  if (s === 2) {
    if (!parentIsFather) return 'biaoAuntMaternal'
    return tang ? 'tangAunt' : 'biaoAunt'
  }
  if (!parentIsFather) return 'biaoUncleMaternal'
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
    const score = L + R
    if (!best || score < best.score) best = { ancestorId, a, b, L, R, score }
  }
  if (!best) return null

  const { a, b, L, R } = best
  const downIds = b.ids.slice().reverse()
  // 关系链:先走到共同祖先下面「分叉的那一位」,再用兄弟/姐妹接上 B 那一支,
  // 这样得到的是「妈妈的爸爸的弟弟」,而不是绕回共同祖先的「…的妈妈的儿子」。
  let chain
  if (R === 0) {
    chain = upChain(a.path)
  } else if (L === 0) {
    chain = downChain(g, downIds)
  } else {
    const branchA = a.ids[L - 1]
    const branchB = b.ids[R - 1]
    chain = upChain(a.path.slice(0, -1))
    if (branchA !== branchB) chain.push(siblingCode(g, branchA, branchB))
    chain.push(...downChain(g, downIds.slice(1)))
  }
  // 堂:父亲这条线,且两侧中间链路全是男丁;中间有一代女儿就是表。
  // 最后一步只说明共同祖先是爷爷还是奶奶(同一对夫妻)——最后一个 'm' 不等于母系,故排除。
  const tang = a.path.slice(0, -1).every((p) => p === 'f') && downIds.slice(1, Math.max(1, downIds.length - 1)).every((id) => isMale(g, id))
  const out = (code, distance) => ({ code, distance, type: 'blood', chain })

  if (L === 0 && R === 0) return { code: 'self', distance: 0, type: 'blood', chain: [] }
  if (R === 0) return out(ancestorCode(a.path), L)
  if (L === 0) return out(descendantCode(g, downIds), R)

  if (L === 1 && R === 1) return out(siblingCode(g, aId, bId), 2)
  if (L === 1 && R === 2) return out(siblingChildCode(g, downIds[1], bId), 3)
  if (L === 1 && R === 3) return out(siblingGrandchildCode(g, downIds[1], bId), 4)
  if (L === 2 && R === 1) return out(parentSiblingCode(g, a.ids[1], bId), 3)
  if (L === 2 && R === 2) return out(cousinCode(g, aId, bId, tang), 4)
  if (L === 2 && R === 3) return out(cousinChildCode(g, downIds[2], bId, tang), 5)
  if (L === 3 && R === 1) return out(grandUncleCode(g, a.ids[2], bId, a.path), 4)
  if (L === 3 && R === 2) return out(parentCousinCode(g, a.ids[1], bId, tang), 5)
  if (L === 3 && R === 3) return out(cousinCode(g, aId, bId, tang), 6)

  // 更远的旁系:只给「族亲/远亲」,避免生造称谓
  return out(best.score <= 6 ? 'relative' : 'distant', best.score)
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
  grandson: 'grandsonWife',
  granddaughter: 'granddaughterHusband',
  maternalGrandson: 'maternalGrandsonWife',
  maternalGranddaughter: 'maternalGranddaughterHusband',
  greatGrandson: 'greatGrandsonWife',
  greatGranddaughter: 'greatGranddaughterHusband',
  maternalGreatGrandson: 'maternalGreatGrandsonWife',
  maternalGreatGranddaughter: 'maternalGreatGranddaughterHusband',
  greatGreatGrandson: 'greatGreatGrandsonWife',
  greatGreatGranddaughter: 'greatGreatGranddaughterHusband',
  maternalGreatGreatGrandson: 'maternalGreatGreatGrandsonWife',
  maternalGreatGreatGranddaughter: 'maternalGreatGreatGranddaughterHusband',
  nephew: 'nephewWife',
  niece: 'nieceHusband',
  sororalNephew: 'sororalNephewWife',
  sororalNiece: 'sororalNieceHusband',
  cousinNephew: 'cousinNephewWife',
  cousinNiece: 'cousinNieceHusband',
  biaoNephew: 'biaoNephewWife',
  biaoNiece: 'biaoNieceHusband',
  cousinSororalNephew: 'cousinSororalNephewWife',
  cousinSororalNiece: 'cousinSororalNieceHusband',
  biaoSororalNephew: 'biaoSororalNephewWife',
  biaoSororalNiece: 'biaoSororalNieceHusband',
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
  tangUncleElder: 'tangUncleElderWife',
  tangUncleYounger: 'tangUncleYoungerWife',
  tangUncle: 'tangUncleWife',
  tangAunt: 'tangAuntHusband',
  biaoUncleElder: 'biaoUncleElderWife',
  biaoUncleYounger: 'biaoUncleYoungerWife',
  biaoUncle: 'biaoUncleWife',
  biaoAunt: 'biaoAuntHusband',
  biaoAuntMaternal: 'biaoAuntMaternalHusband',
  biaoUncleMaternal: 'biaoUncleMaternalWife',
  // 祖辈的兄弟姐妹:配偶就是对应的「祖母/姥爷一辈」
  grandUnclePaternalElder: 'grandUnclePaternalElderWife',
  grandUnclePaternalYounger: 'grandUnclePaternalYoungerWife',
  grandUnclePaternal: 'grandUnclePaternalWife',
  grandAuntPaternal: 'grandAuntPaternalHusband',
  grandUnclePaternalGrandma: 'grandUnclePaternalGrandmaWife',
  grandAuntPaternalGrandma: 'grandAuntPaternalGrandmaHusband',
  grandUncleMaternalElder: 'grandUncleMaternalElderWife',
  grandUncleMaternalYounger: 'grandUncleMaternalYoungerWife',
  grandUncleMaternal: 'grandUncleMaternalWife',
  grandAuntMaternalLine: 'grandAuntMaternalLineHusband',
  grandUncleMaternalGrandma: 'grandUncleMaternalGrandmaWife',
  grandAuntMaternalGrandma: 'grandAuntMaternalGrandmaHusband',
}

// 直系祖辈的配偶就是对偶的那一位祖先(老姥姥 ↔ 老姥爷、奶奶 ↔ 爷爷)。
const ANCESTOR_PAIRS = [
  ['paternalGrandfather', 'paternalGrandmother'],
  ['maternalGrandfather', 'maternalGrandmother'],
  ['greatGrandfather', 'greatGrandmother'],
  ['maternalGreatGrandfather', 'maternalGreatGrandmother'],
  ['grandMaternalGreatGrandfather', 'grandMaternalGreatGrandmother'],
  ['greatGreatGrandfather', 'greatGreatGrandmother'],
  ['maternalGreatGreatGrandfather', 'maternalGreatGreatGrandmother'],
  ['heavenGrandfather', 'heavenGrandmother'],
  ['maternalHeavenGrandfather', 'maternalHeavenGrandmother'],
  ['ancestorMale', 'ancestorFemale'],
]
for (const [a, b] of ANCESTOR_PAIRS) {
  SPOUSE_OF_BLOOD[a] = b
  SPOUSE_OF_BLOOD[b] = a
}

/**
 * 配偶的血亲:A 的配偶 x 是 B 的什么,决定 A 怎么称呼 B。
 * 岳父/岳母、公公/婆婆、大舅子、小姨子等有专称;祖辈/晚辈/叔伯姑舅姨随配偶称呼(同码)。
 */
function bloodOfSpouse(g, aId, relCode) {
  const male = isMale(g, aId)
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
    case 'relative':
    case 'distant':
    case 'none':
    case 'unknown':
      return 'inLaw'
    default:
      // 配偶的祖辈/晚辈/伯叔姑舅姨:跟着配偶叫(妻子叫姥爷,我也叫姥爷)
      return relCode
  }
}

/** 姻亲兜底距离:用不到专称时排到后面,让「配偶的血亲」优先。 */
const affinityScore = (rel, code) => rel.distance + (code === 'inLaw' ? 100 : 0)

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

  // B 是 A 某位血亲的配偶(嫂子/姐夫/儿媳/老姥爷/二姥姥…)
  let best = null
  for (const x of g.spouseOf.get(bId) || []) {
    const rel = bloodKinship(g, aId, x)
    if (!rel) continue
    const mapped = SPOUSE_OF_BLOOD[rel.code]
    if (!mapped) continue
    const score = affinityScore(rel, mapped)
    if (!best || score < best.score) {
      best = {
        code: mapped,
        distance: rel.distance + 1,
        type: 'affinity',
        chain: [...(rel.chain || []), spouseVerb(g, bId)],
        score,
      }
    }
  }
  if (best) return { code: best.code, distance: best.distance, type: best.type, chain: best.chain }

  // A 的配偶是 B 的血亲(岳父/公公/小姨子/妻子的姥爷…)
  for (const x of g.spouseOf.get(aId) || []) {
    const rel = bloodKinship(g, x, bId)
    if (!rel) continue
    const mapped = bloodOfSpouse(g, aId, rel.code)
    const score = affinityScore(rel, mapped)
    if (!best || score < best.score) {
      best = {
        code: mapped,
        distance: rel.distance + 1,
        type: 'affinity',
        chain: [spouseVerb(g, x), ...(rel.chain || [])],
        score,
      }
    }
  }
  if (best) return { code: best.code, distance: best.distance, type: best.type, chain: best.chain }

  // 双方配偶是兄弟姐妹:连襟 / 妯娌
  for (const x of g.spouseOf.get(aId) || []) {
    for (const y of g.spouseOf.get(bId) || []) {
      const sib = bloodKinship(g, x, y)
      if (!sib) continue
      if (['elderBrother', 'youngerBrother', 'brother', 'elderSister', 'youngerSister', 'sister'].includes(sib.code)) {
        const aSex = sex(g, aId)
        const bSex = sex(g, bId)
        const code = aSex === 1 && bSex === 1 ? 'coBrotherInLaw' : aSex === 2 && bSex === 2 ? 'coSisterInLaw' : 'inLaw'
        return {
          code,
          distance: 4,
          type: 'affinity',
          chain: [spouseVerb(g, x), ...(sib.chain || []), spouseVerb(g, y)],
        }
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
