// 麻将牌面:开源牌面 SVG(frontend/src/assets/games/mahjong,许可见同目录 LICENSE.md)。
// kind 0..33;白板(33)在原素材里就是空白牌,返回空串由页面留白,不再画字。
const TILE_FILES = import.meta.glob('../../assets/games/mahjong/tiles/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
})

/** 34 种牌的文件名,顺序与 utils/games/mahjong.js 的 kind 一致 */
export const MAHJONG_TILE_NAMES = Object.freeze([
  ...Array.from({ length: 9 }, (_, i) => `wan-${i + 1}`),
  ...Array.from({ length: 9 }, (_, i) => `tong-${i + 1}`),
  ...Array.from({ length: 9 }, (_, i) => `tiao-${i + 1}`),
  'east',
  'south',
  'west',
  'north',
  'zhong',
  'fa',
  '', // 白板:空白牌
])

const URL_BY_NAME = {}
for (const [path, url] of Object.entries(TILE_FILES)) {
  const name = path.slice(path.lastIndexOf('/') + 1).replace(/\.svg$/, '')
  URL_BY_NAME[name] = url
}

/** 牌种对应的牌面图片地址;白板返回空串(留白) */
export function mahjongTileSrc(kind) {
  const name = MAHJONG_TILE_NAMES[kind]
  return name ? URL_BY_NAME[name] || '' : ''
}
