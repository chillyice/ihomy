import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// 硬编码中文守门:模板里用户可见的文本/静态属性必须走 i18n($t),英文站点才不残留中文。
// 只扫 <template> 根块的文本与静态属性({{ }} 插值、动态绑定 :  @  v- 属性和 HTML 注释都已剔除);
// <script> 里的中文多为枚举数据/日志,交由评审把关(见 docs/踩坑速查.md)。
const SRC = join(fileURLToPath(new URL('.', import.meta.url)), '..')
const CJK = /[\u4e00-\u9fff]/

// 有意保留中文的例外(逐行子串):备案号、语言自显名、游戏 LOGO 字标、双语切换提示
const ALLOW = [
  '鲁ICP备',
  '鲁公网安备',
  'value="zh-CN">中文<',
  'label="中文 (zh)"',
  'wp-segbtn',
  'ct-pet',
  'ct-lian',
  'ct-kan',
  'ct-ihomy',
  'Language switched',
  "startsWith('探测失败')", // 比对后端返回的探测失败文案,非展示文本
]

function walk(dir, exts, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p, exts, out)
    else if (exts.some((e) => name.endsWith(e))) out.push(p)
  }
  return out
}

function templateUiLines(src) {
  const m = src.match(/<template[^>]*>([\s\S]*)<\/template>/)
  if (!m) return []
  // 注释保留换行,保持行号与原文件一致
  const body = m[1].replace(/<!--[\s\S]*?-->/g, (c) => '\n'.repeat((c.match(/\n/g) || []).length))
  return body.split(/\r?\n/).map((l) => l.replace(/\{\{[\s\S]*?\}\}/g, ' ').replace(/\s(?::|@|v-)[\w:.-]+="[^"]*"/g, ' '))
}

// 消息字面量:ElMessage/ElMessageBox/ElNotification 的文本必须走 i18n
const MSG_API = /\b(ElMessage|ElMessageBox|ElNotification)\b/
function scriptLines(src) {
  const m = src.match(/<script[^>]*>([\s\S]*)<\/script>/)
  return (m ? m[1] : src).split(/\r?\n/).map((l) => l.replace(/(^|[^:])\/\/[^\n]*/g, '$1'))
}

describe('用户可见文本无硬编码中文', () => {
  it('.vue 模板文本/静态属性不含未 i18n 的中文', () => {
    const offenders = []
    for (const file of walk(SRC, ['.vue'])) {
      templateUiLines(readFileSync(file, 'utf8')).forEach((line, i) => {
        if (!CJK.test(line) || ALLOW.some((a) => line.includes(a))) return
        offenders.push(`${file.slice(SRC.length + 1)}:${i + 1}: ${line.trim()}`)
      })
    }
    expect(offenders, `模板硬编码中文(请改用 $t):\n${offenders.join('\n')}`).toEqual([])
  })

  it('ElMessage 等提示字面量不含未 i18n 的中文', () => {
    const offenders = []
    for (const file of walk(SRC, ['.vue', '.js'])) {
      if (file.endsWith('.spec.js')) continue
      scriptLines(readFileSync(file, 'utf8')).forEach((line, i) => {
        if (!MSG_API.test(line) || !CJK.test(line) || ALLOW.some((a) => line.includes(a))) return
        offenders.push(`${file.slice(SRC.length + 1)}:${i + 1}: ${line.trim()}`)
      })
    }
    expect(offenders, `提示硬编码中文(请改用 t):\n${offenders.join('\n')}`).toEqual([])
  })
})
