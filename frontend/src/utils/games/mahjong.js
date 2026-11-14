// 麻将内核(与页面解耦的纯函数):
// 136 张牌(万/筒/条 1-9 各 4 张 + 东南西北中发白各 4 张),4 人(单机玩家 + 3 AI / 房间真人 + AI 补位)。
// 规则参数(MahjongRules,开局可选、随房间广播):允许吃 / 允许杠 / 将牌约束(任意对子或二五八将)。
// 碰恒可;胡牌型 = 4 面子 + 1 对子 或 七对子(七对只在门清时成立,不受将牌约束)。
// 副露面子里 type: 'pong'(碰)/ 'gang'(明杠,含别家点杠)/ 'angang'(暗杠)/ 'bugang'(补杠);
// 杠后摸岭上牌补回,暗手张数恒为 13-3×面子数,判定等式不变。
// 番数口径:基础 1,自摸 +1,七对子 +3,碰碰胡 +2,清一色 +4,混一色 +2,断幺九 +1,明杠/补杠每杠 +1,暗杠每杠 +2。
// 本局得分 = 底分 8 × 番数。牌的表示:kind 0..33(0-8 万,9-17 筒,18-26 条,27-33 字),
// 实例 id = kind × 4 + 副本号(0..3)。

/** 牌种总数(34 种)与每种 4 张 */
export const MAHJONG_KINDS = 34
export const MAHJONG_KIND_COPIES = 4
export const MAHJONG_TILE_COUNT = MAHJONG_KINDS * MAHJONG_KIND_COPIES
export const MAHJONG_BASE_SCORE = 8

/** 将牌约束:任意对子 / 二五八将(将牌须为数牌 2/5/8) */
export const MAHJONG_PAIR_ANY = 'any'
export const MAHJONG_PAIR_258 = '258'

/** 规则预设:推倒胡简化(默认,无吃无杠)/ 口口胡(吃碰杠全开) */
export const MAHJONG_RULE_PRESETS = {
  tuiDaoHu: { key: 'tuiDaoHu', allowChi: false, allowGang: false, pairConstraint: MAHJONG_PAIR_ANY },
  kouKouHu: { key: 'kouKouHu', allowChi: true, allowGang: true, pairConstraint: MAHJONG_PAIR_ANY },
}

/** 归一化规则参数(房间信令/本地开局共用;缺省即推倒胡简化口径) */
export function normalizeMahjongRules(rules) {
  const r = rules && typeof rules === 'object' ? rules : {}
  return {
    allowChi: r.allowChi === true,
    allowGang: r.allowGang === true,
    pairConstraint: r.pairConstraint === MAHJONG_PAIR_258 ? MAHJONG_PAIR_258 : MAHJONG_PAIR_ANY,
  }
}

/** 规则是否二五八将 */
export const isPair258Rules = (rules) => normalizeMahjongRules(rules).pairConstraint === MAHJONG_PAIR_258

/** kind 是不是数牌 2/5/8(二五八将的可作将牌种) */
export const isSuit258 = (kind) => isSuitKind(kind) && (kind % 9 === 1 || kind % 9 === 4 || kind % 9 === 7)

/** 牌种序号 → 名称(0-8 万 / 9-17 筒 / 18-26 条 / 27-33 字) */
export const MAHJONG_KIND_NAMES = [
  '一万',
  '二万',
  '三万',
  '四万',
  '五万',
  '六万',
  '七万',
  '八万',
  '九万',
  '一筒',
  '二筒',
  '三筒',
  '四筒',
  '五筒',
  '六筒',
  '七筒',
  '八筒',
  '九筒',
  '一条',
  '二条',
  '三条',
  '四条',
  '五条',
  '六条',
  '七条',
  '八条',
  '九条',
  '东风',
  '南风',
  '西风',
  '北风',
  '红中',
  '发财',
  '白板',
]

/** 3 种数牌花色的种类区间(万/筒/条,各 9 种);字牌不在其中 */
const SUIT_RANGES = [
  { start: 0, name: '万' },
  { start: 9, name: '筒' },
  { start: 18, name: '条' },
]

export const mahjongKindName = (kind) => MAHJONG_KIND_NAMES[kind] || ''

/** 牌种是不是数牌(万/筒/条) */
export const isSuitKind = (kind) => kind >= 0 && kind < 27

/** 牌种是不是幺九(1/9)或字牌——断幺九判定用 */
export const isTerminalOrHonor = (kind) => !isSuitKind(kind) || kind % 9 === 0 || kind % 9 === 8

/** kind 序号 → 显示数字(数牌 1..9;字牌返回 0) */
export const mahjongKindNumber = (kind) => (isSuitKind(kind) ? (kind % 9) + 1 : 0)

/** 牌种 → 花色名(万/筒/条;字牌返回 '') */
export const mahjongKindSuit = (kind) => {
  for (const s of SUIT_RANGES) {
    if (kind >= s.start && kind < s.start + 9) return s.name
  }
  return ''
}

/**
 * 洗一整副牌:返回 136 张实例 id 的乱序数组(可选注入随机源,测试用)。
 * rand 无参函数返回 [0,1);默认 Math.random。
 */
export function createMahjongWall(rand = Math.random) {
  const tiles = []
  for (let kind = 0; kind < MAHJONG_KINDS; kind++) {
    for (let copy = 0; copy < MAHJONG_KIND_COPIES; copy++) tiles.push(kind * 4 + copy)
  }
  for (let i = tiles.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1))
    ;[tiles[i], tiles[j]] = [tiles[j], tiles[i]]
  }
  return tiles
}

/** 实例 id → 牌种 */
export const mahjongKind = (tileId) => Math.floor(tileId / MAHJONG_KIND_COPIES)

/** 实例 id 数组 → 牌种计数表(长度 34) */
export function mahjongCounts(tiles) {
  const counts = new Array(MAHJONG_KINDS).fill(0)
  for (const t of tiles) {
    const k = mahjongKind(t)
    if (k >= 0 && k < MAHJONG_KINDS) counts[k] += 1
  }
  return counts
}

/** 牌种计数表是否合法(每张 ≤4) */
export function mahjongCountsValid(counts) {
  return counts.every((c) => c >= 0 && c <= MAHJONG_KIND_COPIES)
}

/**
 * 判胡:标准牌型(4 面子 + 1 对子,面子=刻子或顺子)或七对子。
 * 入参为暗手牌的 kind 计数表;openMelds 为已碰/杠出的面子数(副露不再占暗手牌)。
 * rules 传规则参数:二五八将时对子必须是数牌 2/5/8(七对不受此约束)。
 * 只判型,不记番。
 */
export function canMahjongWin(counts, openMelds = 0, rules = null) {
  const melds = Math.max(0, Math.min(4, Number(openMelds) || 0))
  const total = counts.reduce((a, b) => a + b, 0)
  if (total !== 14 - melds * 3) return false
  if (melds === 0 && isSevenPairs(counts)) return true
  return canFormMelds(counts, 4 - melds, isPair258Rules(rules))
}

/** 七对子:7 个对子(杠过的四张不算,推倒胡无副露不涉及) */
function isSevenPairs(counts) {
  return counts.every((c) => c % 2 === 0) && counts.reduce((a, b) => a + b, 0) === 14
}

/**
 * 判定 counts 能否拆成 need 副面子 + 1 对将(pair258 时将必须是数牌 2/5/8)。
 * 结构:先枚举将(每种 kind 抽一对),再对剩余牌做纯面子 DFS——
 * 避免「拆面子最后剩对」写法在部分牌型上漏解(将牌被贪心拆掉,如 111 234 55 678 888)。
 * 只用于判胡,不要求最优。
 */
function canFormMelds(counts, need, pair258 = false) {
  const work = [...counts]
  const dfs = (left) => {
    if (left === 0) return work.every((c) => c === 0)
    let k = 0
    while (k < MAHJONG_KINDS && work[k] === 0) k++
    if (k === MAHJONG_KINDS) return false
    // 刻子
    if (work[k] >= 3) {
      work[k] -= 3
      if (dfs(left - 1)) return true
      work[k] += 3
    }
    // 顺子(数牌)
    if (isSuitKind(k) && k % 9 <= 6 && work[k] > 0 && work[k + 1] > 0 && work[k + 2] > 0) {
      work[k] -= 1
      work[k + 1] -= 1
      work[k + 2] -= 1
      if (dfs(left - 1)) return true
      work[k] += 1
      work[k + 1] += 1
      work[k + 2] += 1
    }
    return false
  }
  for (let p = 0; p < MAHJONG_KINDS; p++) {
    if (pair258 && !isSuit258(p)) continue
    if (counts[p] < 2) continue
    // 每轮从原始计数重建(上一轮 DFS 只回溯它动过的位,work 可能残留 -2 污染)
    for (let i = 0; i < MAHJONG_KINDS; i++) work[i] = counts[i]
    work[p] -= 2
    if (dfs(need)) return true
  }
  return false
}

/** 牌型是不是碰碰胡:4 个刻子 + 1 对(七对不算碰碰胡) */
export function isMahjongToitoi(counts) {
  if (isSevenPairs(counts)) return false
  let pair = 0
  let triplets = 0
  for (const c of counts) {
    if (c === 2) pair += 1
    else if (c === 3) triplets += 1
    else if (c !== 0) return false
  }
  return pair === 1 && triplets === 4
}

/** 清一色:全部为同花色数牌 */
export function isMahjongFlush(counts) {
  const suitStart = SUIT_RANGES.findIndex(({ start }) => {
    const inSuit = counts.some((c, k) => c > 0 && k >= start && k < start + 9)
    return inSuit
  })
  if (suitStart === -1) return false
  const { start } = SUIT_RANGES[suitStart]
  return counts.every((c, k) => c === 0 || (k >= start && k < start + 9))
}

/** 混一色:一种数牌 + 任意字牌(且确有字牌) */
export function isMahjongHalfFlush(counts) {
  if (isMahjongFlush(counts)) return false
  const hasHonor = counts.some((c, k) => c > 0 && k >= 27)
  if (!hasHonor) return false
  const suitCount = SUIT_RANGES.filter(({ start }) => counts.some((c, k) => c > 0 && k >= start && k < start + 9)).length
  return suitCount === 1
}

/** 断幺九:没有任何幺九与字牌 */
export function isMahjongAllSimples(counts) {
  return counts.every((c, k) => c === 0 || !isTerminalOrHonor(k))
}

/** 七对子判定(对外导出,胡牌番数用) */
export function isMahjongSevenPairs(counts) {
  return isSevenPairs(counts)
}

/**
 * 计番:返回 { fans: [{ key, points }], total }。
 * 番:基础 1 + 自摸 1 + 七对 3 + 碰碰胡 2 + 清一色 4 + 混一色 2 + 断幺九 1 + 杠(明杠/补杠每杠 1,暗杠每杠 2)。
 * 七对与碰碰胡/清一色等可叠加(如清一色七对);melds 为已碰/杠出的面子,
 * 计番时按三张同牌并入整手牌型(杠的多余一张不参与牌型),七对只在门清(无副露)时成立。
 */
export function mahjongHandScore(counts, { selfDrawn = false, melds = [] } = {}) {
  const openMelds = Array.isArray(melds) ? melds.filter((m) => m && Number.isInteger(m.kind)) : []
  const full = [...counts]
  for (const m of openMelds) {
    if (m.kind >= 0 && m.kind < MAHJONG_KINDS) full[m.kind] += 3
  }
  const fans = [{ key: 'base', points: 1 }]
  if (selfDrawn) fans.push({ key: 'selfDrawn', points: 1 })
  if (!openMelds.length && isSevenPairs(full)) fans.push({ key: 'sevenPairs', points: 3 })
  if (isMahjongToitoi(full) && openMelds.every((m) => !m.type || m.type === 'pong')) {
    fans.push({ key: 'toitoi', points: 2 })
  }
  if (isMahjongFlush(full)) fans.push({ key: 'flush', points: 4 })
  else if (isMahjongHalfFlush(full)) fans.push({ key: 'halfFlush', points: 2 })
  if (isMahjongAllSimples(full)) fans.push({ key: 'allSimples', points: 1 })
  const angangs = openMelds.filter((m) => m.type === 'angang').length
  const gangs = openMelds.filter((m) => m.type === 'gang' || m.type === 'bugang').length
  if (gangs + angangs > 0) fans.push({ key: 'gang', points: gangs + angangs * 2 })
  const total = fans.reduce((a, f) => a + f.points, 0)
  return { fans, total, score: MAHJONG_BASE_SCORE * total }
}

/**
 * 听牌:暗手 13-3×openMelds 张的 kind 计数 → 可胡的牌种数组(空的返回 [])。
 * 思路:对每种「还允许加一张」的 kind 试成可胡牌型。
 */
export function mahjongTenpai(counts13, openMelds = 0, rules = null) {
  const melds = Math.max(0, Math.min(4, Number(openMelds) || 0))
  const total = counts13.reduce((a, b) => a + b, 0)
  if (total !== 13 - melds * 3) return []
  const wins = []
  for (let k = 0; k < MAHJONG_KINDS; k++) {
    if (counts13[k] >= MAHJONG_KIND_COPIES) continue
    const trial = [...counts13]
    trial[k] += 1
    if (canMahjongWin(trial, melds, rules)) wins.push(k)
  }
  return wins
}

/**
 * 可吃的顺子组合(暗手两张 + 别人弃的 discardedKind 组成顺子):
 * 返回组合数组,每项为 [a, b](a<b,顺子里除弃牌外的另两个 kind);字牌不可吃。
 */
export function mahjongChiOptions(counts, discardedKind) {
  const k = discardedKind
  if (!isSuitKind(k)) return []
  const base = k - (k % 9)
  const end = base + 9
  const has = (x) => x >= base && x < end && counts[x] > 0
  const opts = []
  // 弃牌作顺子第三张:k-2, k-1
  if (k - base >= 2 && has(k - 2) && has(k - 1)) opts.push([k - 2, k - 1])
  // 弃牌居中:k-1, k+1
  if (k - base >= 1 && k + 1 < end && has(k - 1) && has(k + 1)) opts.push([k - 1, k + 1])
  // 弃牌作顺子第一张:k+1, k+2
  if (k + 2 < end && has(k + 1) && has(k + 2)) opts.push([k + 1, k + 2])
  return opts
}

/** 明杠(别家弃 kind 而暗手已有三张):可行返回 true */
export const canMahjongMingGang = (counts, kind) => Number.isInteger(kind) && kind >= 0 && kind < MAHJONG_KINDS && counts[kind] === 3

/** 暗杠:暗手凑成四张的 kind 列表(摸牌后自杠用) */
export function mahjongAnGangKinds(counts) {
  const out = []
  for (let k = 0; k < MAHJONG_KINDS; k++) {
    if (counts[k] === MAHJONG_KIND_COPIES) out.push(k)
  }
  return out
}

/** 补杠:此前碰出过 kind 且暗手又摸到一张(返回 true 可补) */
export const canMahjongBuGang = (counts, melds, kind) =>
  Number.isInteger(kind) &&
  kind >= 0 &&
  kind < MAHJONG_KINDS &&
  counts[kind] > 0 &&
  Array.isArray(melds) &&
  melds.some((m) => m && m.kind === kind && (!m.type || m.type === 'pong'))

/**
 * AI 是否愿意吃:吃成一副顺子(固定收益 10)+ 剩余暗手潜力,不低于吃前的整体潜力才吃。
 * 返回选中的 [a, b] 组合或 null。
 */
export function mahjongAiChooseChi(counts13, discardedKind) {
  const opts = mahjongChiOptions(counts13, discardedKind)
  if (!opts.length) return null
  const base = mahjongHandPotential([...counts13])
  let best = null
  let bestAfter = -Infinity
  for (const pair of opts) {
    const work = [...counts13]
    work[pair[0]] -= 1
    work[pair[1]] -= 1
    if (!mahjongCountsValid(work)) continue
    const after = mahjongHandPotential(work) + 10
    if (after >= base && after > bestAfter) {
      best = pair
      bestAfter = after
    }
  }
  return best
}

/**
 * AI 选牌:给 13 张(刚摸完为 14 张,由调用方先枚举弃牌)的候选弃牌挑一张。
 * 策略:弃掉后剩余 13 张的「进张潜力」最高者——潜力 = 完整面子×10 + 半面子×3
 * (对子/相邻两张),字牌孤张自然垫底;平手时优先弃字牌与幺九。
 * 返回要弃的 kind(不在候选里返回 -1)。
 */
export function mahjongAiChooseDiscard(counts14) {
  let bestKind = -1
  let bestScore = -Infinity
  for (let k = 0; k < MAHJONG_KINDS; k++) {
    if (counts14[k] === 0) continue
    const trial = [...counts14]
    trial[k] -= 1
    if (!mahjongCountsValid(trial)) continue
    const potential = mahjongHandPotential(trial)
    // 同分时倾向弃字牌/幺九(对别人威胁小)
    const tieBreak = isTerminalOrHonor(k) ? 1 : 0
    const cmp = potential * 100 + tieBreak
    if (cmp > bestScore) {
      bestScore = cmp
      bestKind = k
    }
  }
  return bestKind
}

/**
 * 手牌潜力估值(13 张):DFS 拆面子,取「面子数×10 + 半面子数×3 + 对子额外 1」的最大值。
 * counts 会原地改动,调用方传副本。
 */
export function mahjongHandPotential(counts) {
  const work = [...counts]
  let best = 0
  const dfs = (sets, partials) => {
    let k = 0
    while (k < MAHJONG_KINDS && work[k] === 0) k++
    if (k === MAHJONG_KINDS) {
      best = Math.max(best, sets * 10 + partials * 3)
      return
    }
    if (work[k] >= 3) {
      work[k] -= 3
      dfs(sets + 1, partials)
      work[k] += 3
    }
    if (isSuitKind(k) && k % 9 <= 6 && work[k] > 0 && work[k + 1] > 0 && work[k + 2] > 0) {
      work[k] -= 1
      work[k + 1] -= 1
      work[k + 2] -= 1
      dfs(sets + 1, partials)
      work[k] += 1
      work[k + 1] += 1
      work[k + 2] += 1
    }
    if (work[k] >= 2) {
      work[k] -= 2
      dfs(sets, partials + 1)
      work[k] += 2
    }
    if (isSuitKind(k) && k % 9 <= 7 && work[k] > 0 && work[k + 1] > 0) {
      work[k] -= 1
      work[k + 1] -= 1
      dfs(sets, partials + 1)
      work[k] += 1
      work[k + 1] += 1
    }
    // 孤张直接跳过
    work[k] -= 1
    dfs(sets, partials)
    work[k] += 1
  }
  dfs(0, 0)
  return best
}

/** 实例 id 按牌种排序(手牌展示顺序) */
export function sortMahjongTiles(tiles) {
  return [...tiles].sort((a, b) => mahjongKind(a) - mahjongKind(b) || a - b)
}
