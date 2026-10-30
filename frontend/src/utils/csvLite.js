// CSV 导入的浏览器侧工具:文件解码(UTF-8/GBK + BOM)、RFC4180 解析、表头猜列映射、预览行校验。
// 与后端 BookImportService 的解析保持同一口径(引号内逗号/换行、空行跳过、行号从 1 起),
// 但只用于「预览 + 即时提示」,真正落库仍由后端再解析一次(导入结果以服务端为准)。

/** 单次导入最多行数(与后端 BookImportService.MAX_ROWS 对齐) */
export const CSV_MAX_ROWS = 2000
/** 单次导入最大字节数(与后端 MAX_BYTES 对齐) */
export const CSV_MAX_BYTES = 2 * 1024 * 1024

/** 列映射:列号从 0 起,-1 表示不映射;日期与金额必填 */
export const EMPTY_MAPPING = {
  dateCol: -1,
  typeCol: -1,
  amountCol: -1,
  categoryCol: -1,
  accountCol: -1,
  remarkCol: -1,
  headerRows: 1,
}

/** 表头关键词 → 映射字段(小写包含匹配,先命中先得) */
const HEADER_HINTS = [
  ['dateCol', ['日期', '交易时间', '交易日期', '记账日期', 'date', 'time']],
  ['typeCol', ['类型', '收支', '方向', 'type']],
  ['amountCol', ['金额', '发生额', 'amount', 'money']],
  ['categoryCol', ['分类', '类别', 'category']],
  ['accountCol', ['账户', '账号', '卡号', 'account']],
  ['remarkCol', ['备注', '摘要', '说明', '商品', 'remark', 'memo', 'note']],
]

/** 按表头名猜列映射;猜不到保持 -1,由用户手动选 */
export function guessMapping(headers) {
  const mapping = { ...EMPTY_MAPPING }
  const used = new Set()
  for (const [key, words] of HEADER_HINTS) {
    const idx = (headers || []).findIndex(
      (h, i) =>
        !used.has(i) &&
        words.some((w) =>
          String(h || '')
            .toLowerCase()
            .includes(w),
        ),
    )
    if (idx >= 0) {
      mapping[key] = idx
      used.add(idx)
    }
  }
  return mapping
}

/**
 * 解码上传的 CSV(ArrayBuffer)为文本:先看 BOM,再按 UTF-8 严格解码,
 * 失败(浏览器抛错或出现替换符)时按 GBK 解,兼容老银行导出的中文 CSV。
 */
export function decodeCsvBuffer(buffer) {
  const bytes = new Uint8Array(buffer)
  if (bytes.length === 0) return ''
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    return new TextDecoder('utf-8').decode(bytes.subarray(3))
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    try {
      return new TextDecoder('gbk').decode(bytes)
    } catch {
      return new TextDecoder('utf-8').decode(bytes)
    }
  }
}

/**
 * 解析 CSV 文本(纯函数):支持引号内逗号/换行、"" 转义、CRLF/LF、BOM;空行跳过。
 * 返回 { headers, rows:[{ line, cells }] },headers 为第一条记录(交给调用方跳过表头)。
 */
export function parseCsv(text) {
  const src = String(text ?? '').replace(/^\uFEFF/, '')
  const records = []
  let cells = []
  let cell = ''
  let quoted = false
  let line = 1
  let startLine = 1
  let started = false

  const pushCell = () => {
    cells.push(cell)
    cell = ''
  }
  const pushRecord = () => {
    pushCell()
    const blank = cells.length === 1 && String(cells[0]).trim() === ''
    if (!blank) records.push({ line: startLine, cells })
    cells = []
    started = false
  }

  for (let i = 0; i < src.length; i++) {
    const ch = src[i]
    if (!started) {
      startLine = line
      started = true
    }
    if (quoted) {
      if (ch === '"') {
        if (src[i + 1] === '"') {
          cell += '"'
          i++
        } else {
          quoted = false
        }
      } else {
        if (ch === '\n') line++
        cell += ch
      }
      continue
    }
    if (ch === '"' && cell === '') {
      quoted = true
    } else if (ch === ',') {
      pushCell()
    } else if (ch === '\r' || ch === '\n') {
      if (ch === '\r' && src[i + 1] === '\n') i++
      pushRecord()
      line++
    } else {
      cell += ch
    }
  }
  if (cell !== '' || cells.length) pushRecord()
  const headers = records.length ? records[0].cells : []
  return { headers, rows: records }
}

/** 金额解析(与后端同口径):去货币符号/千分位/空格,括号记为负 */
export function parseAmount(raw) {
  let s = String(raw ?? '')
    .trim()
    .replace(/[¥￥,\s]/g, '')
    .replace(/[−－]/g, '-')
  if (!s) return null
  let negative = false
  if (s.startsWith('(') && s.endsWith(')')) {
    negative = true
    s = s.slice(1, -1)
  }
  if (!/^-?\d+(\.\d+)?$/.test(s)) return null
  const v = Number(s)
  if (!Number.isFinite(v)) return null
  return negative ? -v : v
}

/** 日期解析:2026-01-05 / 2026/1/5 / 2026.1.5 / 2026年1月5日 / 20260105;非法返回 null */
export function parseDate(raw) {
  let s = String(raw ?? '')
    .trim()
    .replace(/年|月/g, '-')
    .replace(/日/g, '')
    .replace(/[/.]/g, '-')
  if (/^\d{8}$/.test(s)) s = `${s.slice(0, 4)}-${s.slice(4, 6)}-${s.slice(6, 8)}`
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(s)
  if (!m) return null
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])]
  if (mo < 1 || mo > 12 || d < 1) return null
  const days = new Date(Date.UTC(y, mo, 0)).getUTCDate()
  if (d > days) return null
  return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

/** 类型解析:中英文都认;空返回 null(由金额正负推断) */
export function parseType(raw) {
  const s = String(raw ?? '')
    .trim()
    .toUpperCase()
  if (!s) return null
  if (['支出', '出账', '付款', '消费', 'EXPENSE', 'DEBIT'].includes(s)) return 'EXPENSE'
  if (['收入', '入账', '收款', 'INCOME', 'CREDIT'].includes(s)) return 'INCOME'
  if (['转账', 'TRANSFER'].includes(s)) return 'TRANSFER'
  return 'UNKNOWN'
}

const cellAt = (cells, col) => (col >= 0 && col < cells.length ? cells[col] : '')

/**
 * 预览一行(纯函数,不查库):返回 { ok, message, data }。
 * 账户名只做非空校验,是否存在由后端按家庭账户核对(前端不预取账户列表时无法判断)。
 */
export function validatePreviewRow(cells, mapping) {
  const rawDate = cellAt(cells, mapping.dateCol)
  if (!String(rawDate).trim()) return { ok: false, message: 'dateEmpty' }
  const date = parseDate(rawDate)
  if (!date) return { ok: false, message: 'dateBad' }

  const rawAmount = cellAt(cells, mapping.amountCol)
  if (!String(rawAmount).trim()) return { ok: false, message: 'amountEmpty' }
  const signed = parseAmount(rawAmount)
  if (signed === null) return { ok: false, message: 'amountBad' }
  if (signed === 0) return { ok: false, message: 'amountZero' }

  const type = parseType(cellAt(cells, mapping.typeCol))
  if (type === 'UNKNOWN') return { ok: false, message: 'typeBad' }
  if (type === 'TRANSFER') return { ok: false, message: 'transferUnsupported' }

  const category = String(cellAt(cells, mapping.categoryCol) || '').trim() || '其他'
  if (category.length > 30) return { ok: false, message: 'categoryTooLong' }

  const account = String(cellAt(cells, mapping.accountCol) || '').trim() || null
  const remark = String(cellAt(cells, mapping.remarkCol) || '').trim() || null
  if (remark && remark.length > 200) return { ok: false, message: 'remarkTooLong' }

  return {
    ok: true,
    data: { date, type: type || (signed < 0 ? 'EXPENSE' : 'INCOME'), amount: Math.abs(signed), category, account, remark },
  }
}

/** 预览统计:校验数据行(mapping.headerRows 之后),返回 { valid, invalid, samples } */
export function previewRows(parsed, mapping) {
  const rows = (parsed?.rows || []).slice(Math.max(0, mapping.headerRows || 0))
  const samples = []
  let valid = 0
  let invalid = 0
  for (const row of rows) {
    const check = validatePreviewRow(row.cells, mapping)
    if (check.ok) valid++
    else invalid++
    if (samples.length < 8) samples.push({ line: row.line, cells: row.cells, ...check })
  }
  return { valid, invalid, samples, total: rows.length }
}
