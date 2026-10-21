// 统一格式化：JSON 与 XML / HTML / SVG 共用模式、缩进、压缩与错误结构。
// 解析仍由 jsonTools / markupTool 完成，本模块只做模式识别与分派，
// 与 bit_pouch src/plugins/format/run.ts 的行为保持一致。
import { formatJson } from '@/utils/jsonTools'
import { formatMarkup } from './markupTool'

/** 自动模式只看开头：{ 或 [ 视为 JSON，其余交给标记语言推断。 */
export function detectFormat(text) {
  const head = String(text ?? '').trimStart()
  if (head.startsWith('{') || head.startsWith('[')) return 'json'
  return 'auto'
}

/**
 * 按模式格式化。mode 为 auto / json / xml / html。
 * 成功返回 { ok, output, mode, stats }；失败返回 { ok:false, kind, error }。
 */
export function formatAny(text, options = {}) {
  const input = String(text ?? '')
  const requested = options.mode === 'json' || options.mode === 'xml' || options.mode === 'html' ? options.mode : 'auto'
  const minify = Boolean(options.minify)
  const indent = typeof options.indent === 'string' ? options.indent : '  '
  const mode = requested === 'auto' ? detectFormat(input) : requested

  if (mode === 'json') {
    const result = formatJson(input, { indent: minify ? '' : indent, sortKeys: Boolean(options.sortKeys) })
    if (!result.ok) return { ok: false, kind: 'json', error: result.error }
    return { ok: true, output: result.text, mode: 'json', stats: result.stats }
  }

  const result = formatMarkup(input, { mode, indent, minify })
  if (!result.ok) return { ok: false, kind: 'markup', error: result.error }
  return {
    ok: true,
    output: result.text,
    mode: result.mode,
    stats: result.stats,
  }
}
