#!/usr/bin/env node
/**
 * 工具箱导出:把 frontend/src/tools/registry.js 与对应纯函数核心导出成
 *   1) 新功能列表:frontend/public/toolbox-data/manifest.json(线上 https://ihomy.top/toolbox-data/manifest.json)
 *      别放回 public/tools/:构建后是 dist/tools/ 真目录,与 SPA 路由 /tools 同名,nginx 会 403
 *   2) bit_pouch 插件代码:frontend/tools-export/<id>/{manifest.ts,core.ts,run.ts,run.spec.js}
 *
 * 用法:
 *   node scripts/export-tools.mjs
 *   node scripts/export-tools.mjs --check
 *   node scripts/export-tools.mjs --only hash
 *   node scripts/export-tools.mjs --target ../bit_pouch/src/plugins [--force]
 *
 * --target 默认不覆盖已存在的插件目录(bit_pouch 的 hash/timestamp 等需要人工合并);
 * 确认要覆盖时再加 --force。
 */
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve, sep } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const root = resolve(scriptDir, '..')
const frontend = join(root, 'frontend')
const toolsSrc = join(frontend, 'src', 'utils', 'tools')
const exportDir = join(frontend, 'tools-export')
const publicDir = join(frontend, 'public', 'toolbox-data')

const args = process.argv.slice(2)
const flag = (name) => args.includes(name)
const valueOf = (name) => {
  const index = args.indexOf(name)
  return index >= 0 ? args[index + 1] : undefined
}

const checkOnly = flag('--check')
const force = flag('--force')
const only = valueOf('--only')
const target = valueOf('--target') ? resolve(process.cwd(), valueOf('--target')) : null

const { TOOL_REGISTRY } = await import(pathToFileURL(join(frontend, 'src', 'tools', 'registry.js')).href)
const zh = (await import(pathToFileURL(join(frontend, 'src', 'i18n', 'zh-CN.js')).href)).default
const en = (await import(pathToFileURL(join(frontend, 'src', 'i18n', 'en.js')).href)).default
const version = readFileSync(join(root, 'VERSION'), 'utf8').trim()

const resolveMessage = (tree, key) => {
  let node = tree
  for (const part of String(key).split('.')) node = node?.[part]
  return typeof node === 'string' ? node : key
}

const selected = only ? TOOL_REGISTRY.filter((tool) => tool.id === only) : TOOL_REGISTRY
if (only && !selected.length) {
  console.error(`Unknown tool id: ${only}`)
  process.exit(1)
}

const files = new Map()
const put = (path, content) => files.set(path, content)

const exportManifest = {
  source: 'ihomy',
  version,
  registry: 'frontend/src/tools/registry.js',
  tools: TOOL_REGISTRY.map((tool) => ({
    id: tool.id,
    bitPouchId: tool.bitPouchId || null,
    path: tool.path,
    name: { zh: resolveMessage(zh, tool.titleKey), en: resolveMessage(en, tool.titleKey) },
    description: { zh: resolveMessage(zh, tool.descKey), en: resolveMessage(en, tool.descKey) },
    category: tool.category,
    bitPouchCategory: tool.bitPouchCategory || null,
    bitPouchIcon: tool.bitPouchIcon || null,
    keywords: tool.keywords,
    version: tool.version,
    runtime: tool.runtime,
    platforms: tool.platforms,
    offline: tool.offline,
    order: tool.order,
    kernel: tool.kernel,
    bitPouch: tool.bitPouch,
  })),
}
const manifestJson = `${JSON.stringify(exportManifest, null, 2)}\n`
put(join(publicDir, 'manifest.json'), manifestJson)
put(join(exportDir, 'manifest.json'), manifestJson)

const tsNoCheck = (source, origin) => `// @ts-nocheck\n// 由 ihomy scripts/export-tools.mjs 从 ${origin} 导出。\n${source}`

const manifestTs = (tool) => {
  const nameZh = resolveMessage(zh, tool.titleKey)
  const nameEn = resolveMessage(en, tool.titleKey)
  const descZh = resolveMessage(zh, tool.descKey)
  const descEn = resolveMessage(en, tool.descKey)
  return `import type { ToolManifest } from '@/core/types'

export default {
  id: ${JSON.stringify(tool.bitPouchId || tool.id)},
  name: { zh: ${JSON.stringify(nameZh)}, en: ${JSON.stringify(nameEn)} },
  description: {
    zh: ${JSON.stringify(descZh)},
    en: ${JSON.stringify(descEn)},
  },
  category: ${JSON.stringify(tool.bitPouchCategory || 'inspect')},
  icon: ${JSON.stringify(tool.bitPouchIcon || 'wrench')},
  keywords: ${JSON.stringify(tool.keywords)},
  version: ${JSON.stringify(tool.version)},
  runtime: ${JSON.stringify(tool.runtime || 'main')},
  platforms: ${JSON.stringify(tool.platforms)},
  offline: ${Boolean(tool.offline)},
  order: ${tool.order},
} satisfies ToolManifest
`
}

const runTs = `import { run as coreRun } from './core'

export const run = coreRun
`

const kernelTools = selected.filter((tool) => tool.kernel)
const pluginFiles = new Map()

for (const tool of kernelTools) {
  const kernelPath = join(toolsSrc, `${tool.kernel}.js`)
  if (!existsSync(kernelPath)) {
    console.error(`Missing kernel for ${tool.id}: ${kernelPath}`)
    process.exit(1)
  }
  const source = readFileSync(kernelPath, 'utf8')
  const dir = join(exportDir, tool.id)
  const origin = `frontend/src/utils/tools/${tool.kernel}.js`

  pluginFiles.set(join(dir, 'manifest.ts'), manifestTs(tool))
  pluginFiles.set(join(dir, 'core.ts'), tsNoCheck(source, origin))
  pluginFiles.set(join(dir, 'run.ts'), runTs)

  const specPath = join(toolsSrc, `${tool.kernel}.spec.js`)
  if (existsSync(specPath)) {
    const spec = readFileSync(specPath, 'utf8').replace(new RegExp(`from '\\./${tool.kernel}'`, 'g'), "from './core'")
    pluginFiles.set(join(dir, 'run.spec.js'), spec)
  }

  // 核心若依赖共享的 bytes.js,一并导出;bit_pouch 插件目录因此保持自包含
  if (/from '\.\/bytes'/.test(source)) {
    const bytesSource = readFileSync(join(toolsSrc, 'bytes.js'), 'utf8')
    pluginFiles.set(join(dir, 'bytes.ts'), tsNoCheck(bytesSource, 'frontend/src/utils/tools/bytes.js'))
  }
}

for (const [path, content] of pluginFiles) put(path, content)

const writeAll = (baseDir, map) => {
  for (const [path, content] of map) {
    const relative = path.startsWith(baseDir) ? path.slice(baseDir.length + 1) : null
    if (!relative) continue
    const destination = join(baseDir, relative)
    mkdirSync(dirname(destination), { recursive: true })
    writeFileSync(destination, content, 'utf8')
  }
}

if (checkOnly) {
  const drift = []
  for (const [path, content] of files) {
    const actual = existsSync(path) ? readFileSync(path, 'utf8') : null
    if (actual !== content) drift.push(path)
  }
  // 已删除工具的残留目录也算漂移
  for (const folder of ['public/toolbox-data', 'tools-export']) {
    const dir = join(frontend, folder)
    if (!existsSync(dir)) continue
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue
      const id = entry.name
      if (!TOOL_REGISTRY.some((tool) => tool.id === id && tool.kernel)) drift.push(join(dir, id))
    }
  }
  if (drift.length) {
    console.error('Tool export is out of date:')
    for (const path of [...new Set(drift)]) console.error(`  - ${path.replace(root + '\\', '')}`)
    console.error('Run: node scripts/export-tools.mjs')
    process.exit(1)
  }
  console.log('Tool export is up to date.')
  process.exit(0)
}

writeAll(publicDir, new Map([...files].filter(([path]) => path.startsWith(publicDir))))
writeAll(exportDir, new Map([...files].filter(([path]) => path.startsWith(exportDir))))

console.log(`Exported ${exportManifest.tools.length} tool(s) from ihomy ${version}.`)
console.log(`  manifest: frontend/public/toolbox-data/manifest.json`)
for (const tool of kernelTools) {
  console.log(`  code:     frontend/tools-export/${tool.id}/ (${tool.bitPouch?.status || 'new'})`)
}

if (target) {
  let copied = 0
  let skipped = 0
  for (const tool of kernelTools) {
    const targetId = tool.bitPouchId || tool.id
    const destination = join(target, targetId)
    if (existsSync(destination) && !force) {
      console.log(`  skip:     ${destination} (already exists; use --force to overwrite)`)
      skipped += 1
      continue
    }
    mkdirSync(destination, { recursive: true })
    const toolDir = join(exportDir, tool.id)
    for (const [path, content] of pluginFiles) {
      if (!path.startsWith(toolDir + sep)) continue
      const relative = path.slice(toolDir.length + 1)
      writeFileSync(join(destination, relative), content, 'utf8')
    }
    copied += 1
    console.log(`  copy:     ${destination}`)
  }
  console.log(`Target sync: ${copied} copied, ${skipped} skipped.`)
}
