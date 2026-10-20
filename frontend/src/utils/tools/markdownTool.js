// Markdown 阅读器的纯逻辑:读文件 + 走统一消毒入口渲染。
// 解析与消毒复用 utils/markdown.js(全站唯一富文本渲染入口),这里不再自写解析器。

import { renderMarkdown } from '@/utils/markdown'

const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

export class MarkdownToolError extends Error {
  constructor(code, extra = {}) {
    super(code)
    this.code = code
    Object.assign(this, extra)
  }
}

/**
 * 读取文件内容。
 * @param {File} file - 文件对象。
 * @returns {Promise<string>} 文件文本内容。
 * @throws {MarkdownToolError} 文件过大或读取失败时抛出。
 */
export function readFile(file) {
  return new Promise((resolve, reject) => {
    if (file.size > MAX_FILE_SIZE) {
      reject(new MarkdownToolError('fileTooLarge', { maxSize: MAX_FILE_SIZE }))
      return
    }

    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new MarkdownToolError('readFailed', { filename: file.name }))
    reader.readAsText(file, 'utf-8')
  })
}

/** Markdown → 可安全交给 v-html 的 HTML。 */
export function parseMarkdown(markdown) {
  return renderMarkdown(markdown)
}

/**
 * 统一入口(kernel 契约)。
 * @param {string|File} input - Markdown 文本或文件对象。
 * @param {Object} options - 预留配置项。
 * @param {Object} ctx - 上下文(含 locale)。
 * @returns {Promise<Object>} { ok: boolean, output?: string, error?: { code, message } }。
 */
export async function run(input, _options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'

  try {
    const markdown = input instanceof File ? await readFile(input) : String(input || '')

    if (!markdown.trim()) {
      return { ok: false, error: { code: 'emptyContent', message: locale === 'zh' ? '内容为空' : 'Content is empty' } }
    }

    return {
      ok: true,
      output: parseMarkdown(markdown),
      stats: {
        lines: markdown.split(/\r\n?|\n/).length,
        chars: markdown.length,
      },
    }
  } catch (error) {
    if (error instanceof MarkdownToolError) {
      return {
        ok: false,
        error: {
          code: error.code,
          message: error.message || (locale === 'zh' ? '操作失败' : 'Operation failed'),
        },
      }
    }
    return {
      ok: false,
      error: {
        code: 'unknownError',
        message: locale === 'zh' ? '未知错误' : 'Unknown error',
      },
    }
  }
}
