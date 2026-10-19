// @ts-nocheck
// 由 ihomy scripts/export-tools.mjs 从 frontend/src/utils/tools/textTool.js 导出。
// 文本清洗与对比的纯逻辑:清洗、命名转换、Markdown 表格互转、行级 Myers diff。
// 全部手写、零依赖;失败抛带 code 的 TextToolError,文案由页面按 i18n 渲染。

export const MAX_DIFF_LINES = 2000
export const MAX_TEXT_LENGTH = 1_000_000

export class TextToolError extends Error {
  constructor(code, extra = {}) {
    super(code)
    this.code = code
    Object.assign(this, extra)
  }
}

const normalizeNewlines = (text) => String(text ?? '').replace(/\r\n?/g, '\n')

export const splitLines = (text) => normalizeNewlines(text).split('\n')

/** 字数统计:字符(按码位)、中文字、词、行、UTF-8 字节 */
export function textStats(text) {
  const value = String(text ?? '')
  const chars = Array.from(value).length
  const cjk = (value.match(/[\u3400-\u9fff\uf900-\ufaff]/g) || []).length
  const latinWords = (value.match(/[A-Za-z0-9_'-]+/g) || []).length
  const cjkRuns = (value.match(/[\u3400-\u9fff\uf900-\ufaff]+/g) || []).length
  return {
    chars,
    cjk,
    words: latinWords + cjkRuns,
    lines: value === '' ? 0 : value.split(/\r\n?|\n/).length,
    bytes: new TextEncoder().encode(value).length,
  }
}

/** 全角 → 半角(ASCII 可见字符与全角空格) */
export function toHalfWidth(text) {
  return String(text ?? '')
    .replace(/[\uff01-\uff5e]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0xfee0))
    .replace(/\u3000/g, ' ')
}

/** 半角 → 全角(ASCII 可见字符与半角空格) */
export function toFullWidth(text) {
  return String(text ?? '')
    .replace(/[!-~]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) + 0xfee0))
    .replace(/ /g, '\u3000')
}

function compareText(a, b) {
  return a < b ? -1 : a > b ? 1 : 0
}

/** 清洗:按选项处理行;sort 支持 none/asc/desc/length */
export function cleanText(text, options = {}) {
  const { dedupe, reverse, dropEmpty, trim, numberLines, sort = 'none', prefix = '', suffix = '', joinWith = '' } = options
  let lines = splitLines(text)
  if (trim) lines = lines.map((line) => line.trim())
  if (dropEmpty) lines = lines.filter((line) => line.trim() !== '')
  if (dedupe) lines = [...new Set(lines)]
  if (sort === 'asc') lines = [...lines].sort(compareText)
  else if (sort === 'desc') lines = [...lines].sort((a, b) => compareText(b, a))
  else if (sort === 'length') lines = [...lines].sort((a, b) => a.length - b.length || compareText(a, b))
  if (reverse) lines = [...lines].reverse()
  if (prefix || suffix || numberLines) {
    const width = String(lines.length).length
    lines = lines.map((line, index) => `${numberLines ? `${String(index + 1).padStart(width, '0')}. ` : ''}${prefix}${line}${suffix}`)
  }
  return joinWith ? lines.join(joinWith) : lines.join('\n')
}

/** 拆词:处理 camelCase / PascalCase / 缩写 / 数字边界 */
export function splitWords(text) {
  return String(text ?? '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
}

const capitalize = (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()

/** 一次输入,五种命名风格同时输出 */
export function convertCase(text) {
  const words = splitWords(text)
  const lower = words.map((word) => word.toLowerCase())
  return {
    camel: lower.map((word, index) => (index === 0 ? word : capitalize(word))).join(''),
    pascal: lower.map(capitalize).join(''),
    snake: lower.join('_'),
    kebab: lower.join('-'),
    constant: lower.join('_').toUpperCase(),
  }
}

function parseDelimitedLine(line, delimiter) {
  // ponytail: 不支持字段内换行,表格类输入足够;需要时再加完整 RFC 4180 状态机
  if (delimiter === '|') {
    const raw = line.replace(/^\s*\|/, '').replace(/\|\s*$/, '')
    return raw.split('|').map((cell) => cell.replace(/\\\|/g, '|').trim())
  }
  const cells = []
  let current = ''
  let quoted = false
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i]
    if (quoted) {
      if (ch === '"' && line[i + 1] === '"') {
        current += '"'
        i += 1
      } else if (ch === '"') {
        quoted = false
      } else {
        current += ch
      }
      continue
    }
    if (ch === '"') quoted = true
    else if (ch === delimiter) {
      cells.push(current)
      current = ''
    } else current += ch
  }
  cells.push(current)
  return cells.map((cell) => cell.trim())
}

export function detectDelimiter(text) {
  const firstLine =
    normalizeNewlines(text)
      .split('\n')
      .find((line) => line.trim() !== '') || ''
  if (firstLine.includes('\t')) return '\t'
  if (firstLine.includes('|')) return '|'
  return ','
}

/** 把 CSV / TSV / 竖线分隔文本解析成二维数组 */
export function parseDelimited(text, delimiter = detectDelimiter(text)) {
  const lines = normalizeNewlines(text).split('\n')
  if (lines.length && lines[lines.length - 1] === '') lines.pop()
  return lines.map((line) => parseDelimitedLine(line, delimiter))
}

const escapeTable = (cell) => String(cell ?? '').replace(/\|/g, '\\|')

/** 二维数组 → Markdown 表格;align: left/center/right */
export function toMarkdownTable(rows, { align = 'left' } = {}) {
  if (!rows.length) return ''
  const columns = Math.max(...rows.map((row) => row.length))
  const grid = rows.map((row) => Array.from({ length: columns }, (_, i) => escapeTable(row[i] ?? '')))
  const separator = align === 'center' ? ':---:' : align === 'right' ? '---:' : ':---'
  const lines = [grid[0], Array.from({ length: columns }, () => separator), ...grid.slice(1)]
  return lines.map((row) => `| ${row.join(' | ')} |`).join('\n')
}

const isTableSeparator = (line) => /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/.test(line)

/** Markdown 表格 → 指定分隔符文本(默认 CSV) */
export function markdownToDelimited(text, delimiter = ',') {
  const rows = normalizeNewlines(text)
    .split('\n')
    .filter((line) => line.trim() !== '' && !isTableSeparator(line))
    .map((line) => parseDelimitedLine(line, '|'))
  return rows
    .map((row) => row.map((cell) => (delimiter === '\t' || !/[",\n]/.test(cell) ? cell : `"${cell.replace(/"/g, '""')}"`)).join(delimiter))
    .join('\n')
}

function myersOps(a, b) {
  const n = a.length
  const m = b.length
  const max = n + m
  const offset = max
  let v = new Int32Array(2 * max + 2)
  const trace = []
  let distance = 0
  let found = false

  for (let d = 0; d <= max && !found; d += 1) {
    trace.push(Int32Array.from(v))
    for (let k = -d; k <= d; k += 2) {
      const index = offset + k
      let x
      if (k === -d || (k !== d && v[index - 1] < v[index + 1])) x = v[index + 1]
      else x = v[index - 1] + 1
      let y = x - k
      while (x < n && y < m && a[x] === b[y]) {
        x += 1
        y += 1
      }
      v[index] = x
      if (x >= n && y >= m) {
        distance = d
        found = true
        break
      }
    }
  }

  const ops = []
  let x = n
  let y = m
  for (let d = distance; d >= 1; d -= 1) {
    const snapshot = trace[d]
    const k = x - y
    const prevK = k === -d || (k !== d && snapshot[offset + k - 1] < snapshot[offset + k + 1]) ? k + 1 : k - 1
    const prevX = snapshot[offset + prevK]
    const prevY = prevX - prevK
    while (x > prevX && y > prevY) {
      x -= 1
      y -= 1
      ops.push({ type: 'same', ai: x, bi: y })
    }
    if (x === prevX) {
      y -= 1
      ops.push({ type: 'add', bi: y })
    } else {
      x -= 1
      ops.push({ type: 'del', ai: x })
    }
  }
  while (x > 0 && y > 0) {
    x -= 1
    y -= 1
    ops.push({ type: 'same', ai: x, bi: y })
  }
  while (x > 0) {
    x -= 1
    ops.push({ type: 'del', ai: x })
  }
  while (y > 0) {
    y -= 1
    ops.push({ type: 'add', bi: y })
  }
  return ops.reverse()
}

/**
 * 行级对比;返回左右并排的行与统计。
 * 比较时统一按 \n 归一,忽略空白/大小写只影响「是否算相同」,展示仍用原文。
 */
export function diffLines(leftText, rightText, options = {}) {
  const { ignoreWhitespace = false, ignoreCase = false, maxLines = MAX_DIFF_LINES } = options
  const left = splitLines(leftText)
  const right = splitLines(rightText)
  if (left.length > maxLines || right.length > maxLines) throw new TextToolError('tooManyLines', { max: maxLines })

  const keyOf = (line) => {
    let value = line
    if (ignoreWhitespace) value = value.replace(/\s+/g, ' ').trim()
    if (ignoreCase) value = value.toLowerCase()
    return value
  }
  const ops = myersOps(left.map(keyOf), right.map(keyOf))
  const rows = []
  let stats = { added: 0, removed: 0, same: 0 }

  for (let i = 0; i < ops.length;) {
    const op = ops[i]
    if (op.type === 'same') {
      rows.push({ type: 'same', left: left[op.ai], right: right[op.bi], leftNo: op.ai + 1, rightNo: op.bi + 1 })
      stats.same += 1
      i += 1
      continue
    }
    const dels = []
    const adds = []
    while (i < ops.length && ops[i].type !== 'same') {
      ;(ops[i].type === 'del' ? dels : adds).push(ops[i])
      i += 1
    }
    const size = Math.max(dels.length, adds.length)
    for (let k = 0; k < size; k += 1) {
      const del = dels[k]
      const add = adds[k]
      rows.push({
        type: del && add ? 'change' : del ? 'del' : 'add',
        left: del ? left[del.ai] : '',
        right: add ? right[add.bi] : '',
        leftNo: del ? del.ai + 1 : null,
        rightNo: add ? add.bi + 1 : null,
      })
    }
    stats.removed += dels.length
    stats.added += adds.length
  }

  return { rows, stats: { ...stats, linesLeft: left.length, linesRight: right.length } }
}

const MESSAGES = {
  empty: { zh: '先输入内容', en: 'Enter some content first' },
  tooLong: { zh: '输入超过 {max} 字符,请分段处理', en: 'Input exceeds {max} characters; split it up' },
  tooManyLines: { zh: '行数超过 {max},请缩小对比范围', en: 'More than {max} lines; narrow the comparison' },
}

function fail(code, locale, extra = {}) {
  const template = MESSAGES[code] || MESSAGES.empty
  let message = template[locale] || template.zh
  for (const [key, value] of Object.entries(extra)) message = message.replace(`{${key}}`, String(value))
  return { ok: false, error: { code, message } }
}

/**
 * 统一入口:mode = clean(默认) / case / table / diff。
 * diff 模式把 options.second 作为右侧文本。
 */
export function run(input, options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const text = String(input ?? '')
  if (text.length > MAX_TEXT_LENGTH) return fail('tooLong', locale, { max: MAX_TEXT_LENGTH })
  const mode = options.mode || 'clean'

  try {
    if (mode === 'case') {
      const data = convertCase(text)
      if (!splitWords(text).length) return fail('empty', locale)
      return {
        ok: true,
        output: Object.entries(data)
          .map(([key, value]) => `${key}: ${value}`)
          .join('\n'),
        data,
        stats: { words: splitWords(text).length },
      }
    }
    if (mode === 'table') {
      if (!text.trim()) return fail('empty', locale)
      const rows = parseDelimited(text, options.delimiter || undefined)
      if (options.tableTo === 'delimited') {
        return {
          ok: true,
          output: markdownToDelimited(text, options.delimiter === '\t' ? '\t' : ','),
          data: { rows },
          stats: { rows: rows.length },
        }
      }
      return {
        ok: true,
        output: toMarkdownTable(rows, { align: options.align }),
        data: { rows },
        stats: { rows: rows.length, columns: rows[0]?.length || 0 },
      }
    }
    if (mode === 'diff') {
      const result = diffLines(text, String(options.second ?? ''), options)
      return { ok: true, output: '', data: result.rows, stats: result.stats }
    }
    if (!text.length) return fail('empty', locale)
    const output = options.width === 'half' ? toHalfWidth(text) : options.width === 'full' ? toFullWidth(text) : cleanText(text, options)
    return { ok: true, output, data: { stats: textStats(output) }, stats: textStats(output) }
  } catch (error) {
    if (error instanceof TextToolError && MESSAGES[error.code]) return fail(error.code, locale, { max: error.max })
    throw error
  }
}
