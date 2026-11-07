import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// 对齐守门:三类跨页面根因只允许在「唯一来源」里修,禁止后续在页面里覆写回退
// (成因见 docs/踩坑速查.md「对齐/滚动条三类根因」,口径见 UI 提示词对齐小节):
// 1. 工具栏单槽 + space-between → 主操作左飞、窄屏不换行被裁;
// 2. 行内控件放进块级容器 → 按基线排版,整体偏离容器中线;
// 3. 横向滚动条占据布局盒 → 同一条余额条随账户数量改变高度;
// 4. 两栏网格默认 stretch → 短栏被拉到与长栏等高,下方一大片空白。
const SRC = join(fileURLToPath(new URL('.', import.meta.url)), '..')

const css = (rel) => readFileSync(join(SRC, rel), 'utf8')
// 按选择器取规则体(要求选择器后紧跟 `{`,且前面是行首/块结束,避免命中 `.tb-left, .tb-right` 组合选择器)
const rule = (text, selector) => {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const m = new RegExp(`(?:^|[\\n}])\\s*${escaped}\\s*\\{`).exec(text)
  if (!m) return ''
  const open = m.index + m[0].length - 1
  const close = text.indexOf('}', open)
  return close < 0 ? '' : text.slice(open + 1, close).replace(/\s+/g, ' ')
}
const has = (text, decl) => text.includes(decl.replace(/\s+/g, ' '))

describe('工具栏槽位守门', () => {
  const main = css('styles/main.css')

  it('.page-toolbar .tb-right 单槽也靠右并允许窄屏换行', () => {
    const body = rule(main, '.page-toolbar .tb-right')
    expect(body, '缺少 .page-toolbar .tb-right 规则').not.toBe('')
    for (const decl of ['margin-left: auto', 'min-width: 0', 'max-width: 100%', 'justify-content: flex-end', 'flex-wrap: wrap']) {
      expect(has(body, decl), `.page-toolbar .tb-right 缺少 ${decl}(单槽回左/窄屏裁切会复发)`).toBe(true)
    }
  })

  it('暖居胞吐后的工具栏在窄屏放开 nowrap', () => {
    const media = main.slice(main.indexOf('@media (max-width: 768px)', main.indexOf('html.theme-warm .gc-pin .page-toolbar')))
    expect(rule(media, 'html.theme-warm .gc-pin .page-toolbar'), '暖居窄屏工具栏未放开换行').not.toBe('')
  })
})

describe('行内控件中线守门', () => {
  it('记账本页签容器是 flex + 垂直居中', () => {
    const body = rule(css('views/book/Book.vue'), '.book-tabs')
    expect(has(body, 'display: flex')).toBe(true)
    expect(has(body, 'align-items: center')).toBe(true)
  })

  it('相册卡片操作区是 flex + 垂直居中', () => {
    const body = rule(css('views/album/Album.vue'), '.album-actions')
    expect(has(body, 'display: flex')).toBe(true)
    expect(has(body, 'align-items: center')).toBe(true)
  })
})

describe('横向滚动条不占布局盒', () => {
  const book = css('views/book/bookPage.css')

  it('.bs-chips 隐藏原生滚动条且不再用不对称内边距', () => {
    const body = rule(book, '.book-balance-strip .bs-chips')
    expect(has(body, 'scrollbar-width: none')).toBe(true)
    expect(has(body, 'overflow-y: hidden')).toBe(true)
    expect(body, 'padding-bottom 会改变 chip 中线').not.toContain('padding-bottom')
    expect(has(book, '::-webkit-scrollbar')).toBe(true)
  })

  it('隐藏滚动条后仍给边缘渐隐提示', () => {
    const body = rule(book, '.book-balance-strip .bs-chips')
    expect(body).toContain('mask-image')
  })

  it('记账本其余横向滚动容器同样不占布局盒', () => {
    for (const [file, selector] of [
      ['views/book/finance/FinanceCalendar.vue', '.fn-cal-strip'],
      ['views/book/BookTrendChart.vue', '.bt-scroll'],
      ['views/book/BookLedger.vue', '.lt-scroll'],
    ]) {
      const body = rule(css(file), selector)
      expect(body, `${file} 缺少 ${selector} 规则`).not.toBe('')
      expect(has(body, 'scrollbar-width: none'), `${file} ${selector} 仍保留原生滚动条`).toBe(true)
      expect(body, `${file} ${selector} 的不对称内边距会改变中线`).not.toContain('padding-bottom')
    }
  })
})

describe('两栏网格按内容高度排', () => {
  it('记账本 .content 不拉伸右栏', () => {
    const body = rule(css('views/book/BookDaily.vue'), '.content')
    expect(has(body, 'align-items: start')).toBe(true)
  })
})

describe('收支色 token', () => {
  const main = css('styles/main.css')

  it('深浅两套都定义了 --color-income / --color-expense', () => {
    expect(main).toContain('--color-income:')
    expect(main).toContain('--color-expense:')
    expect(main.match(/--color-income:/g).length).toBeGreaterThanOrEqual(2)
    expect(main.match(/--color-expense:/g).length).toBeGreaterThanOrEqual(2)
  })
})
