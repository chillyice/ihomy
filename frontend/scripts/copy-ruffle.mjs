// 把 Ruffle 自托管运行时(node_modules)拷贝到 public/ruffle/,供 Flash 播放器动态加载。
// Ruffle 的 wasm 运行时按文件名字面量在运行时解析,无法被 Vite 打进 bundle,
// 官方自托管方案即「复制到 web 根目录 + 脚本标签加载」。此处仅复制 .js/.wasm 运行文件,跳过 .map/LICENSE。
// 产物 public/ruffle/ 已 gitignore,由本脚本在 dev/build 时重新生成(与 dist 同属构建产物)。
import { copyFileSync, existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = join(__dirname, '..', 'node_modules', '@ruffle-rs', 'ruffle')
const dest = join(__dirname, '..', 'public', 'ruffle')

if (!existsSync(src)) {
  console.warn('[copy-ruffle] @ruffle-rs/ruffle 未安装,跳过(先 npm install)')
  process.exit(0)
}

rmSync(dest, { recursive: true, force: true })
mkdirSync(dest, { recursive: true })

for (const f of readdirSync(src)) {
  if (/\.(js|wasm)$/.test(f)) {
    copyFileSync(join(src, f), join(dest, f))
  }
}
console.log('[copy-ruffle] 已复制 Ruffle 自托管运行时到 public/ruffle')
