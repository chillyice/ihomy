import { seededRng } from './games/seededRng'

export const materialSeed = Math.floor(Math.random() * 0x100000000)

export function keyedDraw(seed, key) {
  let hash = seed >>> 0
  for (const char of key) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619)
  return seededRng(hash)()
}

const papers = ['plain', 'pressed', 'laid']
const notes = ['note', 'note-grid', 'note-sage']
const journals = ['journal', 'journal-linen', 'journal-floral']

function pick(variants, key, seed) {
  return variants[Math.floor(keyedDraw(seed, key) * variants.length)]
}

export const cardPaper = (key, seed = materialSeed) => pick(papers, 'paper:' + key, seed)
export const noteArt = (key, seed) => pick(notes, key + ':art', seed)
export const journalArt = (key, seed = materialSeed) => pick(journals, 'journal:' + key, seed)
