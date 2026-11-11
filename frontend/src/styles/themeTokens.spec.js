import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

// 主题配色守门:防止「深色/暖居下颜色还是浅的」再次混进来(体检报告见
// docs/设计想法/主题/主题配色不匹配体检-计划.md)。两类回归:
//   1) 引用从未定义的 CSS 变量 —— var() 静默取 fallback 或继承父色,等于样式失效;
//   2) 在非 dark/theme-warm 规则里裸写主题色板 hex —— 四组合里只有「光尘·晨」是对的。
// 颜色请走 main.css 的语义 token:--color-text / --color-text-secondary / --color-card /
// --color-card-2 / --color-surface / --color-bg-2 / --color-border / --color-brand(-rgb) /
// --color-danger / --color-warning。
const SRC = join(fileURLToPath(new URL('.', import.meta.url)), '..')

function walk(dir, exts, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) walk(p, exts, out)
    else if (exts.some((e) => name.endsWith(e))) out.push(p)
  }
  return out
}

const rel = (file) => file.slice(SRC.length + 1).replace(/\\/g, '/')

const STYLE_RE = /<style[^>]*>([\s\S]*?)<\/style>/g
const getCss = (file) => {
  const src = readFileSync(file, 'utf8')
  return file.endsWith('.css') ? src : [...src.matchAll(STYLE_RE)].map((m) => m[1]).join('\n')
}
// 去注释 + 剥掉 var(--x, fallback) 的 fallback(有变量兜底的不算硬编码)
const normalize = (css) => css.replace(/\/\*[\s\S]*?\*\//g, '').replace(/var\(\s*(--[a-zA-Z0-9_-]+)\s*,[^()]*\)/g, 'var($1)')

const STYLE_FILES = walk(SRC, ['.vue', '.css'])

// 运行时由 JS/内联 style 注入的变量:样式里读它们是对的,只是 CSS 里查不到声明
const RUNTIME_VARS = {
  '--i': '模板按序号注入的进场延迟(main.css .anim-rise)',
  '--z': 'WarmHome/Wallpaper 卡片按叠放顺序注入的层级',
  '--chip': 'WarmHome 卡片/动态条的品牌色号,JS 按内容类型注入',
  '--rot': 'WarmHome/Wallpaper 拍立得倾角、SunLightLayer 阴影旋转',
  '--dx': '卡片拖拽位移',
  '--dy': '卡片拖拽位移',
  '--card-h': 'Kitchen 卡片高度按封面比例注入',
  '--polaroid-w': 'Home 拍立得宽度',
  '--bar-transition': 'SunLightLayer 光柱过渡时长',
  '--frame-top-offset': 'SunLightLayer 暖居外框顶部偏移',
  '--htop': 'SunLightLayer 光斑高度',
  '--lamp-mask': 'SunLightLayer 台灯遮罩',
  '--shadow-alpha': 'SunLightLayer 阴影强度',
  '--shadow-color': 'SunLightLayer 阴影颜色',
  '--gba-aspect': 'EmulatorPlayer 屏幕宽高比',
  '--sn-cols': 'Snake 棋盘列数',
  '--sol-cols': '纸牌棋盘列数',
  '--sol-gap': '纸牌棋盘间距',
  '--sol-card-w': '纸牌卡宽(useGameView 实测)',
  '--wide-col-max': '大屏双栏列内滚动净高(Schedule.vue 按实测头高注入)',
  '--seg-color': 'Vault 密码强度分段色',
  '--sw-a': 'ThemeSwatch 晨色',
  '--sw-b': 'ThemeSwatch 暮色',
  '--orb-scale': 'Chillyice 玩偶缩放',
  '--leaf-drift': 'PhotoCascade 落叶横向漂移',
  '--leaf-rot': 'PhotoCascade 落叶旋转',
  '--cy-light-x': 'Chillyice 舞台光源坐标',
  '--cy-light-y': 'Chillyice 舞台光源坐标',
  '--cy-night': 'Chillyice 夜色系数',
  '--cy-sh-a': 'Chillyice 投影透明度',
  '--cy-sh-blur': 'Chillyice 投影模糊',
  '--cy-sh-x': 'Chillyice 投影偏移',
  '--cy-sh-y': 'Chillyice 投影偏移',
  '--cy-warm-a': 'Chillyice 暖光强度',
}

const isAllowedVar = (name) =>
  name.startsWith('--el-') || // Element Plus 自带
  /^--blob-/.test(name) || // 五色色块:--blob-1..5 已定义,模板里有 `--blob-${n}` 拼接
  name === '--sweep-p' || // theme/index.js 扫光动画写入
  Object.hasOwn(RUNTIME_VARS, name)

describe('主题配色守门', () => {
  it('引用的 CSS 变量必须真实存在(拼错 = 样式静默失效)', () => {
    const defined = new Set()
    const refs = []
    for (const file of STYLE_FILES) {
      const css = normalize(getCss(file))
      for (const m of css.matchAll(/(--[a-zA-Z0-9_-]+)\s*:/g)) defined.add(m[1])
      for (const m of css.matchAll(/var\(\s*(--[a-zA-Z0-9_-]+)/g)) refs.push([m[1], rel(file)])
    }
    const offenders = refs.filter(([name]) => !defined.has(name) && !isAllowedVar(name)).map(([name, path]) => `${path}: ${name}`)
    expect(offenders, `引用了未定义的 CSS 变量(运行时会静默取 fallback 或继承父色):\n${offenders.join('\n')}`).toEqual([])
  })

  it('main.css 四态都定义了语义色 token(danger/warning/tertiary/green)', () => {
    const css = normalize(readFileSync(join(SRC, 'styles/main.css'), 'utf8'))
    const blocks = [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map((m) => ({
      selectors: m[1].split(',').map((s) => s.trim().split(/\r?\n/).pop().trim()),
      body: m[2],
    }))
    for (const selector of [':root', 'html.dark', 'html.theme-warm', 'html.theme-warm.dark']) {
      const body = blocks
        .filter((b) => b.selectors.includes(selector))
        .map((b) => b.body)
        .join('\n')
      for (const token of ['--color-danger', '--color-warning', '--color-text-tertiary', '--color-green']) {
        expect(body.includes(`${token}:`), `${selector} 缺少 ${token}`).toBe(true)
      }
    }
  })

  it('非 dark / theme-warm 规则里不得裸写主题色板 hex', () => {
    // 色板 = main.css 里 --color-* 的值;纯黑纯白是压在图片/品牌底上的通用前景,不算色板
    const mainCss = readFileSync(join(SRC, 'styles/main.css'), 'utf8')
    const palette = new Set()
    for (const m of mainCss.matchAll(/--(?:el-)?color-[a-z0-9-]+\s*:\s*(#[0-9a-fA-F]{3,8})/g)) {
      const hex = m[1].toLowerCase()
      if (!['#fff', '#ffffff', '#000', '#000000'].includes(hex)) palette.add(hex)
    }
    // Element Plus 旧灰也一并拦下:它们是「深色下还是浅的」的典型来源
    for (const hex of [
      '#f5f7fa',
      '#303133',
      '#606266',
      '#909399',
      '#c0c4cc',
      '#dcdfe6',
      '#e4e7ed',
      '#ebeef5',
      '#409eff',
      '#e6a23c',
      '#f56c6c',
      '#67c23a',
      '#f2f6fc',
    ]) {
      palette.add(hex)
    }

    // 已有深色/暖居覆写的类名:这些类的浅色声明由 html.dark / html.theme-warm 接管
    const coveredClasses = new Set()
    const cssByFile = new Map()
    for (const file of [...STYLE_FILES, join(SRC, 'styles/main.css')]) {
      const css = normalize(getCss(file))
      cssByFile.set(rel(file), css)
      for (const m of css.matchAll(/([^{}]+)\{/g)) {
        if (!/dark|theme-warm/.test(m[1])) continue
        for (const c of m[1].matchAll(/\.([a-zA-Z0-9_-]+)/g)) coveredClasses.add(c[1])
      }
    }

    // 已拍板不动的装饰(详见体检报告 §4):每条都要写清为什么保留
    const ALLOW = [
      { file: 'components/MusicPlayer.vue', selector: '.vinyl-disc', reason: '黑胶唱片本体,实物隐喻' },
      { file: 'components/MusicPlayer.vue', selector: '.vinyl-label', reason: '唱片中心纸标,实物隐喻' },
      { file: 'components/MusicPlayer.vue', selector: '.progress-played', reason: '品牌色进度渐变(强调色)' },
      { file: 'components/MusicPlayer.vue', selector: '.ctrl-main', reason: '主播放键品牌色渐变(强调色)' },
      { file: 'views/blog/BlogDetail.vue', selector: '.markdown-body pre', reason: '代码块固定深底亮字,与主题无关' },
      { file: 'views/Kada.vue', reason: '独立产品页,自带 data-crt 配色' },
      { file: 'views/Chillyice.vue', reason: '独立产品页,自带舞台配色' },
      { file: 'styles/bit-pouch.css', reason: '独立产品页配色层(.bp 令牌)' },
      { file: 'views/bitpouch/', reason: '比特口袋独立页', prefix: true },
      { file: 'views/item/FloorPlanCanvas.vue', reason: '户型图纸本体应保持纸感(§4)' },
      { file: 'components/SunLightLayer.vue', reason: '光影装饰层,颜色即光尘本体' },
      { file: 'views/games/EmulatorPlayer.vue', reason: '模拟器画布与外壳按钮为外来渲染层' },
    ]
    const allowed = (file, selector) =>
      ALLOW.some((a) => (a.prefix ? file.startsWith(a.file) : a.file === file && (!a.selector || selector.includes(a.selector))))

    const offenders = []
    for (const [file, css] of cssByFile) {
      if (file === 'styles/main.css') continue
      for (const block of css.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
        const selector = block[1].trim().split(/\r?\n/).pop().trim()
        if (/dark|theme-warm/.test(selector)) continue
        const classes = [...selector.matchAll(/\.([a-zA-Z0-9_-]+)/g)].map((m) => m[1])
        if (classes.some((c) => coveredClasses.has(c))) continue
        if (allowed(file, selector)) continue
        for (const m of block[2].matchAll(/#[0-9a-fA-F]{3,8}\b/g)) {
          const hex = m[0].toLowerCase()
          const norm = hex.length === 4 ? '#' + hex[1] + hex[1] + hex[2] + hex[2] + hex[3] + hex[3] : hex
          if (!palette.has(hex) && !palette.has(norm)) continue
          offenders.push(`${file} :: ${selector} => ${m[0]}(请改用 main.css 的语义 token)`)
        }
      }
    }
    expect(offenders, `非 dark / theme-warm 规则里裸写了主题色板颜色(暖居·晨/暮与光尘·暮会错色):\n${offenders.join('\n')}`).toEqual([])
  })
})
