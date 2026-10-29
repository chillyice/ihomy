import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// 层级守门:全局层级只由 styles/main.css 的 --z-* 标尺给出,组件里再写 >=100 的裸数字会跟
// 常驻外壳/装饰光影抢层级,表现为「弹窗遮罩盖不住页脚」「回顶/查看器浮在遮罩上」「光影画在弹窗上」
// 这类遮挡问题(成因见 docs/踩坑速查.md §3.36)。
const SRC = join(fileURLToPath(new URL('.', import.meta.url)), '..')

const REQUIRED_TOKENS = [
  '--z-nav',
  '--z-float',
  '--z-footer',
  '--z-action',
  '--z-immersive',
  '--z-overlay',
  '--z-messagebox',
  '--z-popup',
  '--z-toast',
  '--z-topmost',
]

// 有意保留的裸层级:main.css 是标尺本体;SunLightLayer 的 65–100 是装饰层自身刻度;
// Kada 是独立页(无站点外壳),9989–9991 为该页 CRT 扫描线/暗角特效的页内层。
const ALLOW_FILES = ['styles/main.css', 'components/SunLightLayer.vue', 'views/Kada.vue']

const Z_RAW = /z-index\s*:\s*(\d+)/g

function walk(dir, exts, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p, exts, out)
    else if (exts.some((e) => name.endsWith(e))) out.push(p)
  }
  return out
}

const rel = (file) => file.slice(SRC.length + 1).replace(/\\/g, '/')

describe('全局层级标尺守门', () => {
  it('main.css 定义了完整的 --z-* 标尺', () => {
    const css = readFileSync(join(SRC, 'styles/main.css'), 'utf8')
    const missing = REQUIRED_TOKENS.filter((t) => !css.includes(`${t}:`))
    expect(missing, `styles/main.css 缺少层级 token:${missing.join(', ')}`).toEqual([])
  })

  it('组件内不得写 >=100 的裸 z-index(改用 --z-* token)', () => {
    const offenders = []
    for (const file of walk(SRC, ['.vue', '.css'])) {
      const path = rel(file)
      if (ALLOW_FILES.includes(path)) continue
      readFileSync(file, 'utf8')
        .split(/\r?\n/)
        .forEach((line, i) => {
          for (const m of line.matchAll(Z_RAW)) {
            if (Number(m[1]) >= 100) offenders.push(`${path}:${i + 1}: ${line.trim()}`)
          }
        })
    }
    expect(offenders, `组件内裸层级过大(请改用 main.css 的 --z-* token):\n${offenders.join('\n')}`).toEqual([])
  })
})
