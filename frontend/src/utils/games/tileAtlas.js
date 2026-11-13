// 麻将/扑克牌面图集(8 列 × 6 行):前 34 格是麻将牌面(顺序与 utils/games/mahjong.js 的 kind 完全一致),
// 之后是四门 J/Q/K 与大小王。整张图当 sprite 用,这里只做「第几格 → CSS background 定位」的纯计算,
// Mahjong.vue 的牌面与 PlayingCard.vue 的人头牌共用同一套口径。
// 图集元数据(格子数、起始格、牌序)读同目录的 tiles-cards-atlas.json,换图只改 json 不用改代码。
import atlas from '@/assets/games/tiles-cards-atlas.json'
import atlasImage from '@/assets/games/tiles-cards-atlas.png'
import sevenDots from '@/assets/games/mahjong-seven-dots.png'
export { default as POKER_DECK } from '@/assets/games/poker/deck.json'
const numberFaces = import.meta.glob('/src/assets/games/poker/*.svg', { eager: true, query: '?url', import: 'default' })

/** 图集图片地址(走 Vite 资源哈希,命中缓存不会重复下载) */
export const ATLAS_IMAGE = atlasImage
export const ATLAS_COLUMNS = atlas.columns
export const ATLAS_ROWS = atlas.rows
/** 麻将牌面在图集里的起始格 */
export const MAHJONG_FACE_START = atlas.mahjong.start
/** 麻将牌面种类数(34:万/筒/条各 9 + 字牌 7) */
export const MAHJONG_FACE_COUNT = atlas.mahjong.count

/**
 * 每格向外多取的比例(横/竖分开算):图集每格在牌面四周留了一圈浅灰留白,
 * 略放大并居中裁掉留白,牌面才能正好填满牌框。数值按当前图集实测
 * (格 156.75×209,牌面约 132×183),换图后需要重新核一次。
 */
const OVERSCAN_X = 1.22
const OVERSCAN_Y = 1.18

/** 扑克花色码 → 图集里的花色名 */
const POKER_SUIT_NAME = { S: 'spades', H: 'hearts', C: 'clubs', D: 'diamonds' }
/** 人头牌点数 → 图集里的点数名(图集只出了 J/Q/K,2~10 仍由 CSS 铺点) */
const POKER_RANK_NAME = { 11: 'J', 12: 'Q', 13: 'K' }

/** 扑克人头牌名字 → 图集绝对格号 */
const POKER_CELL = new Map(atlas.poker.map((name, i) => [name, atlas.pokerStart + i]))

/** 第 index 格在图集里的行列;越界返回 null */
export function atlasCell(index) {
  if (!Number.isInteger(index) || index < 0 || index >= ATLAS_COLUMNS * ATLAS_ROWS) return null
  return { col: index % ATLAS_COLUMNS, row: Math.floor(index / ATLAS_COLUMNS) }
}

/**
 * 第 index 格的 CSS 背景:整图放大 8×6 倍后定位到该格,并居中裁掉格间留白。
 * 返回 null 表示越界,调用方自行降级(不要渲染空白)。
 */
export function atlasStyle(index) {
  const cell = atlasCell(index)
  if (!cell) return null
  const overscanX = index >= atlas.pokerStart ? 1.04 : OVERSCAN_X
  const overscanY = index >= atlas.pokerStart ? 1.03 : OVERSCAN_Y
  const sizeW = ATLAS_COLUMNS * overscanX
  const sizeH = ATLAS_ROWS * overscanY
  // 百分比背景定位:x = (格中心 - 半牌框) / (图宽 - 牌框宽),让该格正好居中
  const x = (((cell.col + 0.5) * overscanX - 0.5) * 100) / (sizeW - 1)
  const y = (((cell.row + 0.5) * overscanY - 0.5) * 100) / (sizeH - 1)
  return {
    backgroundImage: `url(${atlasImage})`,
    backgroundSize: `${(sizeW * 100).toFixed(2)}% ${(sizeH * 100).toFixed(2)}%`,
    backgroundPosition: `${x.toFixed(3)}% ${y.toFixed(3)}%`,
    backgroundRepeat: 'no-repeat',
  }
}

/** 麻将 kind(0..33)→ 牌面背景;越界返回 null,由调用方退回文字牌面 */
export function mahjongFaceStyle(kind) {
  const k = Number(kind)
  if (!Number.isInteger(k) || k < 0 || k >= MAHJONG_FACE_COUNT) return null
  if (k === 15)
    return {
      backgroundImage: `url(${sevenDots})`,
      backgroundSize: '100% 100%',
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat',
    }
  return atlasStyle(MAHJONG_FACE_START + k)
}

/** 扑克 J/Q/K → 人头牌背景;A~10(图集未出)返回 null,由 PlayingCard 继续铺点 */
export function pokerFaceStyle(suit, rank) {
  if (suit === 'JOKER' && (rank === 14 || rank === 15)) return atlasStyle(POKER_CELL.get(rank === 14 ? 'joker-small' : 'joker-big'))
  const numberFace = numberFaces[`/src/assets/games/poker/${suit}-${rank}.svg`]
  if (numberFace)
    return {
      backgroundImage: `url(${numberFace})`,
      backgroundSize: '100% 100%',
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat',
    }
  const suitName = POKER_SUIT_NAME[suit]
  const rankName = POKER_RANK_NAME[rank]
  if (!suitName || !rankName) return null
  const index = POKER_CELL.get(`${suitName}-${rankName}`)
  return index == null ? null : atlasStyle(index)
}
