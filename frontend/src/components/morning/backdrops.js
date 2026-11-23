const assets = import.meta.glob('../../assets/morning/*-{day,night}{,-alt}.webp', { eager: true, import: 'default', query: '?url' })
const lastByScene = new Map()

function backdropOptions(condition, night) {
  const stem = '../../assets/morning/' + condition + (night ? '-night' : '-day')
  return [assets[stem + '.webp'], assets[stem + '-alt.webp']]
}

export function weatherBackdrop(condition, night) {
  return backdropOptions(condition, night)[0] ?? ''
}

// 昼夜与天气分别抽取,同一组合打开两次时换一张。只记本次浏览会话。
export function pickMorningBackdrop(condition, night) {
  const options = backdropOptions(condition, night).filter(Boolean)
  if (!options.length) return ''
  const previous = lastByScene.get(options[0])
  const choices = options.length > 1 ? options.filter((url) => url !== previous) : options
  const next = choices[Math.floor(Math.random() * choices.length)]
  lastByScene.set(options[0], next)
  return next
}
