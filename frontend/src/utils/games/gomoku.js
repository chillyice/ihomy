export const GOMOKU_SIZE = 15
const SIZE = GOMOKU_SIZE
const AXES = [
  [1, 0],
  [0, 1],
  [1, 1],
  [1, -1],
]
const opposite = (stone) => (stone === 'black' ? 'white' : 'black')

export function createGomoku() {
  return { board: Array(SIZE * SIZE).fill(null), moves: [], turn: 'black', result: null, line: [] }
}

function scan(board, row, col, dr, dc, stone) {
  let count = 0
  for (;;) {
    row += dr
    col += dc
    if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return { count, open: false }
    const cell = board[row * SIZE + col]
    if (cell !== stone) return { count, open: cell === null }
    count++
  }
}

function linesThrough(board, cell, stone) {
  const row = Math.floor(cell / SIZE)
  const col = cell % SIZE
  return AXES.map(([dr, dc]) => {
    const forward = scan(board, row, col, dr, dc, stone)
    const back = scan(board, row, col, -dr, -dc, stone)
    return {
      length: 1 + forward.count + back.count,
      open: Number(forward.open) + Number(back.open),
      stride: dr * SIZE + dc,
      back: back.count,
    }
  })
}

export function playGomoku(game, cell) {
  if (game.result || !Number.isInteger(cell) || cell < 0 || cell >= game.board.length || game.board[cell] !== null) return null
  const board = game.board.slice()
  const stone = game.turn
  board[cell] = stone
  const moves = [...game.moves, cell]
  const line = new Set()
  for (const { length, back, stride } of linesThrough(board, cell, stone)) {
    if (length < 5) continue
    for (let i = 0; i < length; i++) line.add(cell + (i - back) * stride)
  }
  const result = line.size ? stone : moves.length === board.length ? 'draw' : null
  return { board, moves, result, line: [...line], turn: opposite(stone) }
}

// Persist just the move history. Replaying also validates turn order, duplicates,
// bounds and attempts to continue after a win.
export function restoreGomoku(moves) {
  if (!Array.isArray(moves) || moves.length > SIZE * SIZE) return null
  let game = createGomoku()
  for (const cell of moves) {
    game = playGomoku(game, cell)
    if (!game) return null
  }
  return game
}

function threat(board, cell, stone) {
  let score = 0
  for (const { length, open } of linesThrough(board, cell, stone)) {
    if (length >= 5) return Infinity
    if (open) score += [0, 1, 8, 70, 650][length] * (open === 2 ? 5 : 1)
  }
  return score
}

export function chooseGomokuMove(game) {
  if (game.result) return null
  const center = (SIZE - 1) / 2
  let best = null
  let bestScore = -Infinity
  for (let cell = 0; cell < game.board.length; cell++) {
    if (game.board[cell] !== null) continue
    const attack = threat(game.board, cell, game.turn)
    if (attack === Infinity) return cell
    const defense = threat(game.board, cell, opposite(game.turn))
    const distance = Math.abs(Math.floor(cell / SIZE) - center) + Math.abs((cell % SIZE) - center)
    const score = Math.max(attack, defense * 0.94) + (attack + defense) / 20 - distance / 100
    if (score > bestScore) {
      bestScore = score
      best = cell
    }
  }
  return best
}
