import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// 统一游戏外壳守门(docs/UI设计提示词.md §43):
//   1) 每个游戏页要么已经迁到 GameShell,要么在「未迁移」名单里显式挂号 —— 新页面必须做选择;
//   2) 已迁移的页面不许再自己拼 PageToolbar / GameHeader / game-topbar,也不许写 calc(100dvh - Npx);
//   3) 外壳样式唯一来源是 main.css 的「统一游戏外壳」段。
// 每批迁移完成后,把该文件从 PENDING 挪到 MIGRATED,PENDING 只减不增。
const SRC = join(fileURLToPath(new URL('.', import.meta.url)), '..', '..')
const GAME_DIR = join(SRC, 'views/games')
const CARDS_DIR = join(GAME_DIR, 'cards')

/** 已迁到 GameShell 的游戏页 */
const MIGRATED = ['Game2048.vue', 'Minesweeper.vue', 'Snake.vue', 'Sudoku.vue', 'Tetris.vue']

/** 尚未迁移(按计划分批进行);不是游戏舞台的页面归 EXEMPT */
const PENDING = [
  'Baohuang.vue',
  'Cards.vue',
  'Doudizhu.vue',
  'EmbedGame.vue',
  'Gouji.vue',
  'Guandan.vue',
  'Ludo.vue',
  'Mahjong.vue',
  'Mahjong3d.vue',
  'PetLinkLink.vue',
  'Runway52.vue',
  'SnakeMatch.vue',
  'WebGame.vue',
]

/** 不是游戏舞台:大厅/房间/详情/播放器分发/桌面下载/模拟器 */
const EXEMPT = ['DesktopGame.vue', 'EmulatorPlayer.vue', 'GamePlayer.vue', 'GameRoom.vue', 'Games.vue', 'MatchDetail.vue']

/** 纸牌三桌由 Cards.vue 承载,随 Cards.vue 一起迁移 */
const CARD_BOARD = ['FreeCellBoard.vue', 'KlondikeBoard.vue', 'SpiderBoard.vue']

const read = (file) => readFileSync(file, 'utf8')
const vueFiles = (dir) => readdirSync(dir).filter((n) => n.endsWith('.vue'))

describe('统一游戏外壳守门', () => {
  it('每个游戏页都必须在迁移名单里挂号', () => {
    const known = new Set([...MIGRATED, ...PENDING, ...EXEMPT, ...CARD_BOARD])
    const unknown = [...vueFiles(GAME_DIR), ...vueFiles(CARDS_DIR)].filter((name) => !known.has(name) && !(name === 'CardDragGhost.vue'))
    expect(unknown, `新游戏页请决定是否用 GameShell 并登记到本文件:\n${unknown.join('\n')}`).toEqual([])
  })

  it('已迁移页不再自己拼头部,也不用魔法数算高度', () => {
    for (const name of MIGRATED) {
      const src = read(join(GAME_DIR, name))
      expect(src, `${name} 应使用 GameShell`).toContain("from '@/components/games/GameShell.vue'")
      expect(src, `${name} 不应再引入 PageToolbar`).not.toContain('components/PageToolbar.vue')
      expect(src, `${name} 不应再引入 GameHeader`).not.toContain('components/games/GameHeader.vue')
      expect(src, `${name} 不应再写 game-topbar`).not.toContain('game-topbar')
      expect(src, `${name} 不要再用 calc(100dvh - Npx) 猜高度,改用 --gs-h`).not.toMatch(/calc\(\s*100dvh\s*-/)
    }
  })

  it('外壳样式唯一来源是 main.css 的统一游戏外壳段', () => {
    const css = read(join(SRC, 'styles/main.css'))
    for (const sel of ['.page.game-shell', '.gs-head', '.gs-controls', '.gs-body']) {
      expect(css, `main.css 缺少 ${sel}`).toContain(sel)
    }
    for (const token of ['--gs-pad', '--gs-gap', '--gs-head-h', '--gs-control-h', '--gs-radius']) {
      expect(css, `main.css 缺少 ${token}`).toContain(token)
    }
    // 外壳通过实测把游戏区尺寸写成 CSS 变量
    const shell = read(join(SRC, 'components/games/GameShell.vue'))
    expect(shell).toContain('--gs-w')
    expect(shell).toContain('--gs-h')
  })
})
