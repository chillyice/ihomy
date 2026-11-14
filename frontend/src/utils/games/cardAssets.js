// 扑克牌面:本地高清牌面转出的 WebP(frontend/src/assets/games/poker,来源与许可见同目录 README)。
// 纸牌内核的牌对象是 { suit:'S'|'H'|'C'|'D', rank:1..13 },这里只负责把牌换算成图片地址。
const CARD_FILES = import.meta.glob('../../assets/games/poker/deck/*.webp', {
  eager: true,
  query: '?url',
  import: 'default',
})

const URL_BY_ID = {}
for (const [path, url] of Object.entries(CARD_FILES)) {
  const id = path.slice(path.lastIndexOf('/') + 1).replace(/\.webp$/, '')
  URL_BY_ID[id] = url
}

/** 牌面图片地址;缺图时返回空串,由组件回退成文字牌面 */
export function cardFaceUrl(card) {
  if (!card || !card.suit || !card.rank) return ''
  if (card.suit === 'JOKER') return URL_BY_ID[card.rank === 14 ? 'joker-small' : 'joker-big'] || ''
  return URL_BY_ID[`${card.suit}-${card.rank}`] || ''
}

/** 素材包里实际有的牌 id(S-1 … D-13、joker-small/joker-big),用于测试与排查 */
export const CARD_FACE_IDS = Object.freeze(Object.keys(URL_BY_ID).sort())
