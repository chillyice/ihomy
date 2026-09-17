// 把 EmulatorJS 运行时(node_modules)拷贝到 public/emulatorjs/,供 GBA 播放器动态加载。
// 与 copy-ruffle.mjs 同理:WASM 运行时无法被 Vite 打进 bundle,需作为静态资源由 nginx 托管。
// 产物 public/emulatorjs/ 已 gitignore,由本脚本在 dev/build 时重新生成。
import { copyFileSync, existsSync, mkdirSync, readdirSync, rmSync, statSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const base = join(__dirname, '..')
const dataSrc = join(base, 'node_modules', '@emulatorjs', 'emulatorjs', 'data')
const coreSrc = join(base, 'node_modules', '@emulatorjs', 'core-mgba')
const dest = join(base, 'public', 'emulatorjs', 'data')

if (!existsSync(dataSrc)) {
  console.warn('[copy-emulatorjs] @emulatorjs/emulatorjs 未安装,跳过(先 npm install)')
  process.exit(0)
}

// 清空目标
rmSync(join(base, 'public', 'emulatorjs'), { recursive: true, force: true })
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

// 2. 复制 mgba 核心 WASM 文件到 data/cores/
const coreDst = join(dest, 'cores')
if (existsSync(coreSrc)) {
  mkdirSync(coreDst, { recursive: true })
  for (const f of readdirSync(coreSrc)) {
    if (f.endsWith('.data') || f.endsWith('.wasm')) {
      copyFileSync(join(coreSrc, f), join(coreDst, f))
    }
  }
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

console.log('[copy-emulatorjs] 已复制 EmulatorJS + mgba 核心到 public/emulatorjs/')
