import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// HTML 注入面守门:用户可写内容渲染成 HTML 是存储型 XSS 的唯一入口,必须经消毒后再落进 v-html。
// 规则:凡用了 v-html 的文件,要么引用 utils/markdown.js 的 renderMarkdown(消毒入口),
// 要么属于下面的静态图标白名单;新增的裸 v-html 一律拦下。成因见 docs/踩坑速查.md §3.24。
const SRC = join(fileURLToPath(new URL('.', import.meta.url)), '..')

// 有意保留的裸 v-html:值来自本地静态常量(装饰性图标 SVG),不含用户数据。
const ALLOW_FILES = ['views/Kada.vue', 'views/BitPouch.vue', 'views/tools/MindMapEditor.vue']

// 消毒入口:标记导入 + 实际调用(只导入不调用不算)
const SANITIZER_IMPORT = /from\s+['"]@\/utils\/markdown['"]/
const SANITIZER_CALL = /renderMarkdown\s*\(/

function walk(dir, exts, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p, exts, out)
    else if (exts.some((e) => name.endsWith(e))) out.push(p)
  }
  return out
}

const rel = (file) => file.slice(SRC.length + 1).replace(/\\/g, '/')

describe('HTML 注入面守门', () => {
  it('v-html 只允许出现在引用消毒入口的文件或静态图标白名单里', () => {
    const offenders = []
    for (const file of walk(SRC, ['.vue'])) {
      const path = rel(file)
      if (ALLOW_FILES.includes(path)) continue
      const src = readFileSync(file, 'utf8')
      if (!src.includes('v-html')) continue
      if (SANITIZER_IMPORT.test(src) && SANITIZER_CALL.test(src)) continue
      const line = src.split(/\r?\n/).findIndex((l) => l.includes('v-html')) + 1
      offenders.push(`${path}:${line}`)
    }
    expect(offenders, `未消毒的 v-html(请改用 utils/markdown.js 的 renderMarkdown):\n${offenders.join('\n')}`).toEqual([])
  })

  it('marked 只能由 utils/markdown.js 导入(消毒入口唯一)', () => {
    const offenders = []
    for (const file of walk(SRC, ['.vue', '.js'])) {
      const path = rel(file)
      if (path === 'utils/markdown.js' || file.endsWith('.spec.js')) continue
      if (/from\s+['"]marked['"]/.test(readFileSync(file, 'utf8'))) offenders.push(path)
    }
    expect(offenders, `marked 应只在 utils/markdown.js 里使用:\n${offenders.join('\n')}`).toEqual([])
  })

  it('.vue/.js 不得把 html 写进非空 innerHTML/outerHTML', () => {
    const sink = /\b(innerHTML|outerHTML)\s*\+?=/
    const emptyOnly = /\b(innerHTML|outerHTML)\s*\+?=\s*(''|""|``)\s*;?\s*$/
    const offenders = []
    for (const file of walk(SRC, ['.vue', '.js'])) {
      if (file.endsWith('.spec.js')) continue
      readFileSync(file, 'utf8')
        .split(/\r?\n/)
        .forEach((line, i) => {
          if (!sink.test(line) || emptyOnly.test(line)) return
          offenders.push(`${rel(file)}:${i + 1}: ${line.trim()}`)
        })
    }
    expect(offenders, `非空 innerHTML/outerHTML 注入(请走 v-html + renderMarkdown 或 textContent):\n${offenders.join('\n')}`).toEqual([])
  })
})
