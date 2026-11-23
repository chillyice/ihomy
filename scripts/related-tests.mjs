#!/usr/bin/env node
/**
 * 按改动挑相关用例,只为本次改动跑对应测试(不做全量回归)。
 *
 * 用法(仓库根目录):
 *   node scripts/related-tests.mjs                 # 读 git 改动,列出将要执行的命令(不执行)
 *   node scripts/related-tests.mjs --run           # 列出并执行
 *   node scripts/related-tests.mjs src/utils/date.js frontend/src/views/Blog.vue
 *                                                  # 指定文件(可与 --run 连用)
 *   node scripts/related-tests.mjs --all --run     # 全量(发布前/基线核对才用)
 *   node scripts/related-tests.mjs --frontend | --backend   # 只看一端
 *
 * 挑选规则:
 *   前端 变更源码 → 沿静态 import 关系向上找直接/间接引用它的 *.spec.js;同基名 spec 也纳入。
 *   前端 全仓扫描型守门 spec(i18n/样式/HTML 注入面/游戏外壳)按改动内容特征触发。
 *   后端 变更类 → 同包同名 XxxTest + 测试源码里引用该类名的测试类;改测试类只跑该类。
 *   其他路径(docs、图片、配置)不产生用例,只在末尾列出。
 *
 * 退出码:0 全部通过;1 有用例失败;2 用法/环境错误。
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { dirname, join, resolve, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const FRONTEND = join(ROOT, 'frontend')
const BACKEND = join(ROOT, 'backend')
const WIN = process.platform === 'win32'
const rel = (p) => relative(ROOT, p).split(sep).join('/')

const args = process.argv.slice(2)
const flags = new Set(args.filter((a) => a.startsWith('--')))
const fileArgs = args.filter((a) => !a.startsWith('--'))

if (flags.has('--help') || flags.has('-h')) {
  console.log(readFileSync(fileURLToPath(import.meta.url), 'utf8').split('*/')[0])
  process.exit(0)
}

const run = flags.has('--run')
const all = flags.has('--all')
const onlyFrontend = flags.has('--frontend')
const onlyBackend = flags.has('--backend')

/* ------------------------------- 改动文件 ------------------------------- */

function gitLines(argv) {
  const r = spawnSync('git', argv, { cwd: ROOT, encoding: 'utf8' })
  if (r.status !== 0) return []
  return r.stdout.split('\n').filter(Boolean)
}

function collectChanged() {
  if (fileArgs.length) {
    return [...new Set(fileArgs.map((f) => rel(resolve(ROOT, f))))]
  }
  // status: 已暂存 + 未暂存 + 未跟踪(排除删除,只要还存在的工作区文件);quotePath=false 保留中文路径
  const out = gitLines(['-c', 'core.quotePath=false', 'status', '--porcelain']).join('\n')
  const files = []
  for (const line of out.split('\n').filter(Boolean)) {
    const xy = line.slice(0, 2)
    if (xy.includes('D')) continue
    let path = line.slice(3).trim()
    if (path.includes(' -> ')) path = path.split(' -> ')[1]
    files.push(path.replace(/^"|"$/g, ''))
  }
  return [...new Set(files)]
}

/* --------------------------- 前端:import 关系图 --------------------------- */

const SRC = join(FRONTEND, 'src')
const CODE_EXT = /\.(js|vue|css)$/

function walk(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    const st = statSync(p)
    if (st.isDirectory()) walk(p, acc)
    else if (CODE_EXT.test(name)) acc.push(p)
  }
  return acc
}

const IMPORT_RE =
  /(?:import|export)\s+(?:[^'"]*?\s+from\s+)?['"]([^'"]+)['"]|import\s*\(\s*['"]([^'"]+)['"]\s*\)|@import\s+['"]([^'"]+)['"]/g

function resolveSpecifier(fromFile, spec) {
  const s = spec.split('?')[0]
  let base
  if (s.startsWith('@/')) base = join(SRC, s.slice(2))
  else if (s.startsWith('./') || s.startsWith('../')) base = resolve(dirname(fromFile), s)
  else if (s.startsWith('src/')) base = join(FRONTEND, s)
  else return null // npm 包
  for (const cand of [base, `${base}.js`, `${base}.vue`, `${base}.css`, join(base, 'index.js'), join(base, 'index.vue')]) {
    if (existsSync(cand) && statSync(cand).isFile()) return cand
  }
  return null
}

let graph = null // module -> Set(importers)
let specFiles = null
function buildGraph() {
  if (graph) return
  graph = new Map()
  const allFiles = walk(SRC)
  specFiles = new Set(allFiles.filter((f) => f.endsWith('.spec.js')))
  for (const file of allFiles) {
    let text
    try {
      text = readFileSync(file, 'utf8')
    } catch {
      continue
    }
    IMPORT_RE.lastIndex = 0
    let m
    while ((m = IMPORT_RE.exec(text))) {
      const target = resolveSpecifier(file, m[1] || m[2] || m[3])
      if (!target) continue
      if (!graph.has(target)) graph.set(target, new Set())
      graph.get(target).add(file)
    }
  }
}

/**
 * 从改动文件向上找引用它的 spec(不含扫全仓的守门 spec)。
 * 页面/组件/布局基本没有单测,页面被 router 引用会一路扩散到 app 壳,所以只取直接引用;
 * utils/composables/stores 是共享逻辑,允许 2 跳(utils → composable → spec)。
 */
function specsFor(changedAbs) {
  buildGraph()
  const found = new Set()
  if (specFiles.has(changedAbs)) found.add(changedAbs)
  const baseSpec = changedAbs.replace(/\.(js|vue|css)$/, '.spec.js')
  if (specFiles.has(baseSpec)) found.add(baseSpec)
  const srcRel = relative(SRC, changedAbs).split(sep).join('/')
  const maxHops = /^(views|components|layouts)\//.test(srcRel) ? 1 : 2
  const queue = [[changedAbs, 0]]
  const seen = new Set([changedAbs])
  while (queue.length) {
    const [cur, hop] = queue.shift()
    if (hop >= maxHops) continue
    for (const importer of graph.get(cur) || []) {
      if (seen.has(importer)) continue
      seen.add(importer)
      if (specFiles.has(importer)) found.add(importer)
      else queue.push([importer, hop + 1])
    }
  }
  return found
}

/* --------------------- 前端:全仓扫描型守门 spec 触发规则 --------------------- */
// 这些 spec 读整个 src/ 目录,import 关系上找不到它们,只能按改动内容特征挑
const CJK = /[\u4e00-\u9fff]/
const GUARDS = [
  {
    spec: 'src/i18n/hardcoded-cjk.spec.js',
    why: '模板/提示里有中文',
    // .js 里的中文注释是正常现象,只看 .vue 模板与 ElMessage 类提示字面量
    test: (p, t) =>
      p.endsWith('.vue')
        ? CJK.test(t)
        : /(ElMessage|ElMessageBox|ElNotification|ElLoading)[^\n]*[\u4e00-\u9fff]/.test(t),
  },
  {
    spec: 'src/i18n/used-keys.spec.js',
    why: '用到 $t/t( 或改了 i18n',
    test: (p, t) => p.startsWith('src/i18n/') || /\$t\(|(^|[^\w.])t\(/.test(t),
  },
  { spec: 'src/i18n/message-format.spec.js', why: '改了 i18n 语言包', test: (p) => p.startsWith('src/i18n/') },
  { spec: 'src/i18n/parity.spec.js', why: '改了 i18n 语言包', test: (p) => p.startsWith('src/i18n/') },
  {
    spec: 'src/styles/zLayers.spec.js',
    why: '动了 z-index/--z- 或 main.css',
    test: (p, t) => p === 'src/styles/main.css' || /z-index|--z-/.test(t),
  },
  { spec: 'src/styles/themeTokens.spec.js', why: '引用了 CSS 变量/色值', test: (p, t) => /var\(--|#[0-9a-fA-F]{3,8}\b/.test(t) },
  { spec: 'src/styles/toolbarLayout.spec.js', why: '改了 main.css', test: (p) => p === 'src/styles/main.css' },
  {
    spec: 'src/utils/htmlSink.spec.js',
    why: '动了 v-html/marked/innerHTML',
    test: (p, t) => /v-html|innerHTML|outerHTML|marked/.test(t),
  },
  {
    spec: 'src/views/games/gameShellAdoption.spec.js',
    why: '改了游戏舞台页或 main.css',
    test: (p) => p.startsWith('src/views/games/') || p === 'src/styles/main.css',
  },
]

/* ------------------------------ 后端:测试类 ------------------------------ */

function backendTestIndex() {
  const testRoot = join(BACKEND, 'src/test/java')
  const files = []
  ;(function walkJava(dir) {
    if (!existsSync(dir)) return
    for (const name of readdirSync(dir)) {
      const p = join(dir, name)
      const st = statSync(p)
      if (st.isDirectory()) walkJava(p)
      else if (name.endsWith('Test.java')) files.push(p)
    }
  })(testRoot)
  const index = files.map((f) => ({ file: f, cls: f.split(sep).pop().replace('.java', ''), text: readFileSync(f, 'utf8') }))
  return index
}

function backendTestsFor(changedAbs, index) {
  const simple = changedAbs.split(sep).pop().replace(/\.java$/, '')
  const out = new Set()
  for (const t of index) {
    if (rel(changedAbs).startsWith('backend/src/test/') && t.file === changedAbs) {
      out.add(t.cls)
      continue
    }
    if (t.cls === `${simple}Test`) {
      out.add(t.cls)
      continue
    }
    const re = new RegExp(`\\b${simple}\\b`)
    if (re.test(t.text)) out.add(t.cls)
  }
  return out
}

/* --------------------------------- 主流程 --------------------------------- */

const changed = collectChanged().filter((p) => p && !p.endsWith('/'))
if (!changed.length && !all) {
  console.log('工作区没有改动。指定文件: node scripts/related-tests.mjs <文件...>')
  process.exit(0)
}

const feChanged = changed.filter((p) => p.startsWith('frontend/src/') && !p.endsWith('.d.ts'))
const beChanged = changed.filter((p) => p.startsWith('backend/src/'))
const otherChanged = changed.filter((p) => !p.startsWith('frontend/src/') && !p.startsWith('backend/src/'))

const plan = []

if (!onlyBackend && (feChanged.length || all)) {
  let specs = new Set()
  if (all) {
    buildGraph()
    specs = new Set(specFiles)
  } else {
    for (const p of feChanged) {
      const abs = join(ROOT, p)
      if (!existsSync(abs) || statSync(abs).isDirectory()) continue
      for (const s of specsFor(abs)) specs.add(s)
      const text = readFileSync(abs, 'utf8')
      const srcRel = p.slice('frontend/'.length)
      for (const g of GUARDS) {
        if (g.test(srcRel, text)) specs.add(join(FRONTEND, g.spec))
      }
    }
  }
  if (specs.size) {
    const list = [...specs].map((s) => relative(FRONTEND, s).split(sep).join('/')).sort()
    plan.push({
      label: `前端 ${list.length} 个 spec`,
      cwd: FRONTEND,
      display: `npx vitest run ${list.join(' ')}`,
      count: list.length,
    })
  }
}

if (!onlyFrontend && (beChanged.length || all)) {
  let classes
  if (all) {
    classes = new Set(backendTestIndex().map((t) => t.cls))
  } else {
    const index = backendTestIndex()
    classes = new Set()
    for (const p of beChanged) {
      const abs = join(ROOT, p)
      if (!existsSync(abs)) continue
      for (const c of backendTestsFor(abs, index)) classes.add(c)
    }
  }
  if (classes.size) {
    const list = [...classes].sort()
    const filter = list.join(',')
    plan.push({
      label: `后端 ${list.length} 个测试类`,
      cwd: BACKEND,
      display: WIN ? `.\\mvnw.cmd -B "-Dtest=${filter}" test` : `./mvnw -B "-Dtest=${filter}" test`,
      count: list.length,
    })
  }
}

/* --------------------------------- 输出 --------------------------------- */

const totalSpecs = (() => {
  buildGraph()
  return specFiles.size
})()

console.log(`\n改动 ${changed.length} 个文件:`)
for (const p of changed) {
  const tag = p.startsWith('frontend/src/') ? '前端' : p.startsWith('backend/src/') ? '后端' : '其它'
  console.log(`  [${tag}] ${p}`)
}

if (!plan.length) {
  console.log('\n没有匹配到自动化用例(文档/图片/配置类改动或无覆盖)。')
  console.log('需要人工核对的场景请从 test/cases/功能测试用例.md 按模块挑选。')
  process.exit(0)
}

console.log('')
for (const p of plan) {
  console.log(`── ${p.label}`)
  console.log(`   工作目录: ${rel(p.cwd)}`)
  console.log(`   命令: ${p.display}\n`)
}

if (!all && !onlyBackend) {
  const fePlan = plan.find((p) => p.cwd === FRONTEND)
  if (fePlan) console.log(`本机全量前端为 ${totalSpecs} 个 spec,本次只跑 ${fePlan.count} 个。`)
}

if (!run) {
  console.log('\n(仅列出。加 --run 执行。)')
  process.exit(0)
}

for (const p of plan) {
  console.log(`\n>>> ${p.display}`)
  // Windows 下 mvnw.cmd / npx 需要经由 cmd 解析;POSIX 走 sh
  const r = WIN
    ? spawnSync('cmd.exe', ['/c', p.display], { cwd: p.cwd, stdio: 'inherit' })
    : spawnSync('sh', ['-c', p.display], { cwd: p.cwd, stdio: 'inherit' })
  if (r.status !== 0) {
    console.error(`\n失败: ${p.label}(退出码 ${r.status})`)
    process.exit(1)
  }
}
console.log('\n相关用例全部通过。')
