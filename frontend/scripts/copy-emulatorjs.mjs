// 把 EmulatorJS 运行时(node_modules)拷贝到 public/emulatorjs/,供模拟器播放器动态加载。
// 与 copy-ruffle.mjs 同理:WASM 运行时无法被 Vite 打进 bundle,需作为静态资源由 nginx 托管。
// 产物 public/emulatorjs/ 已 gitignore,由本脚本在 dev/build 时重新生成。
import { copyFileSync, existsSync, mkdirSync, readdirSync, rmSync, statSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const base = join(__dirname, '..')
const dataSrc = join(base, 'node_modules', '@emulatorjs', 'emulatorjs', 'data')
const dest = join(base, 'public', 'emulatorjs', 'data')

if (!existsSync(dataSrc)) {
  console.warn('[copy-emulatorjs] @emulatorjs/emulatorjs 未安装,跳过(先 npm install)')
  process.exit(0)
}

// 随前端发布的模拟器核心(与前端 utils/games/emulatorSystems.js、后端 common/GameSystem 一一对应)。
// EmulatorJS 按浏览器能力选文件名:<core>[-thread][-legacy]-wasm.data
//   - legacy:浏览器不支持/关闭 WebGL2 时使用(老 iOS 必需);强制 WebGL2 的核心走不到这条分支,不用拷
//   - thread:需要 SharedArrayBuffer(COOP/COEP 响应头),本站没开,不拷
const CORES = [
  { name: 'mgba', legacy: true }, // GBA
  { name: 'gambatte', legacy: true }, // GB/GBC
  { name: 'fceumm', legacy: true }, // NES
  { name: 'snes9x', legacy: true }, // SNES
  { name: 'genesis_plus_gx', legacy: true }, // MD/GG
  { name: 'fbneo', legacy: true }, // 街机(核心最大,约 8MB/份)
  { name: 'melonds', legacy: true }, // NDS
]

// 清空目标(vite dev server / OneDrive 可能正占用目录句柄,整目录 rm 会 EPERM:
// 退化为逐项删除,残留的旧核心文件不影响运行,后续 copyFileSync 会覆盖同名文件)
const publicDir = join(base, 'public', 'emulatorjs')
try {
  rmSync(publicDir, { recursive: true, force: true, maxRetries: 3, retryDelay: 100 })
} catch {
  if (existsSync(publicDir)) {
    for (const entry of readdirSync(publicDir)) {
      try {
        rmSync(join(publicDir, entry), { recursive: true, force: true, maxRetries: 3, retryDelay: 100 })
      } catch {
        /* 仍被占用:留给后面的覆盖复制 */
      }
    }
  }
}
mkdirSync(dest, { recursive: true })

/** 递归复制目录(跳过 .map) */
function copyDir(src, dst) {
  mkdirSync(dst, { recursive: true })
  for (const entry of readdirSync(src)) {
    if (entry.endsWith('.map')) continue
    const s = join(src, entry)
    const d = join(dst, entry)
    if (statSync(s).isDirectory()) {
      copyDir(s, d)
    } else {
      copyFileSync(s, d)
    }
  }
}

// 1. 复制 emulatorjs data/(loader.js + src/*.js + emulator.css + cores/ + localization/)
copyDir(dataSrc, dest)

// 2. 复制各机种核心 WASM 文件到 data/cores/
const coreDst = join(dest, 'cores')
mkdirSync(coreDst, { recursive: true })
const missing = []
for (const { name, legacy } of CORES) {
  const coreSrc = join(base, 'node_modules', '@emulatorjs', `core-${name}`)
  if (!existsSync(coreSrc)) {
    missing.push(name)
    continue
  }
  const files = [`${name}-wasm.data`]
  if (legacy) files.push(`${name}-legacy-wasm.data`)
  for (const f of files) {
    const src = join(coreSrc, f)
    if (existsSync(src)) copyFileSync(src, join(coreDst, f))
    else missing.push(`${name}/${f}`)
  }
  // reports/<core>.json:EJS 用它的 buildStart 做核心缓存版本号、options.defaultWebGL2 决定渲染后端;
  // 缺了会每次重新下载核心并退回 legacy 判定,必须一并拷。
  const reportSrc = join(coreSrc, 'reports', `${name}.json`)
  if (existsSync(reportSrc)) {
    const reportDst = join(coreDst, 'reports')
    mkdirSync(reportDst, { recursive: true })
    copyFileSync(reportSrc, join(reportDst, `${name}.json`))
  } else {
    missing.push(`${name}/reports/${name}.json`)
  }
}
if (missing.length) {
  console.warn(`[copy-emulatorjs] 缺少核心文件(检查 @emulatorjs/cores 是否装全):${missing.join(', ')}`)
}

// 3. patch loader.js:npm 包 data/ 只有 src/ 源码(无 emulator.min.js/css),loader.js 默认先请求
//    min 文件会 404 再回退;把 min 分支条件置 true 直接走 src/*.js。
//    注意不能设 window.EJS_DEBUG_XX=true(那会同时打开 emulator.js 里 this.debug 调试模式)。
const loaderPath = join(dest, 'loader.js')
if (existsSync(loaderPath)) {
  const loader = readFileSync(loaderPath, 'utf8')
  const needle = '"undefined" != typeof EJS_DEBUG_XX && true === EJS_DEBUG_XX'
  if (loader.includes(needle)) {
    writeFileSync(loaderPath, loader.replace(needle, 'true'), 'utf8')
    console.log('[copy-emulatorjs] 已 patch loader.js:直接加载 src/*.js(跳过不存在的 min 产物)')
  } else {
    console.warn('[copy-emulatorjs] 未匹配 loader.js 的 min 分支条件,可能版本升级,请人工检查')
  }
}

console.log(`[copy-emulatorjs] 已复制 EmulatorJS 运行时 + ${CORES.length} 个机种核心到 public/emulatorjs/`)
