const assets = import.meta.glob('../../assets/morning/*-{day,night}{,-alt}.webp', { eager: true, import: 'default', query: '?url' })
const lastByScene = new Map()

// 昼夜与天气分别抽取,同一组合打开两次时换一张。只记本次浏览会话。
export function pickMorningBackdrop(condition, night) {
  const stem = '../../assets/morning/' + condition + (night ? '-night' : '-day')
  const options = [assets[stem + '.webp'], assets[stem + '-alt.webp']].filter(Boolean)
  const previous = lastByScene.get(stem)
  const choices = options.length > 1 ? options.filter((url) => url !== previous) : options
  const next = choices[Math.floor(Math.random() * choices.length)] ?? ''
  lastByScene.set(stem, next)
  return next
}
