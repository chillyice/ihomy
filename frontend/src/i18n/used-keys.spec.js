import { describe, it, expect } from 'vitest'
import zhCN from './zh-CN'
import en from './en'

// 模板/脚本里 $t('a.b') 引用的键必须真实存在:
// parity.spec.js 只校验中英两文件键结构对齐,漏掉「引用了不存在的键」这类问题
// (渲染出来就是字面量 ops.xxx,曾踩过)。动态拼接的键(如 'ops.source_' + s)不在此列。
const sources = import.meta.glob(['../**/*.vue', '../**/*.js', '!../i18n/**', '!../**/*.spec.js'], {
  query: '?raw',
  import: 'default',
  eager: true,
})

// 前一个字符不能是标识符字符:否则会把 import('x') 里结尾的 t( 也当成 $t( 调用
const KEY_RE = /(?<![\w$.])\$?t\(\s*'([^']+)'\s*\)|(?<![\w$.])\$?t\(\s*"([^"]+)"\s*\)/g

export function collectKeys(fileContents) {
  const keys = new Set()
  for (const src of Object.values(fileContents)) {
    for (const m of String(src).matchAll(KEY_RE)) {
      const k = m[1] ?? m[2]
      // 只认命名空间键(含点、不以前后点结尾);排除动态拼接与表格列等非 i18n 调用
      if (k.includes('.') && !k.startsWith('.') && !k.endsWith('.')) keys.add(k)
    }
  }
  return keys
}

const get = (obj, key) => key.split('.').reduce((o, part) => (o == null ? o : o[part]), obj)

describe('i18n 引用键存在性', () => {
  it('所有 $t(键) 在 zh-CN 与 en 中都有值', () => {
    const keys = collectKeys(sources)
    expect(keys.size).toBeGreaterThan(0)
    const missing = []
    for (const k of [...keys].sort()) {
      if (get(zhCN, k) === undefined) missing.push(`zh-CN 缺 ${k}`)
      if (get(en, k) === undefined) missing.push(`en 缺 ${k}`)
    }
    expect(missing).toEqual([])
  })
})
