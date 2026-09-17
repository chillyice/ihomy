// EmulatorJS(GBA/NES/SNES 等模拟器)运行时懒加载:脚本标签加载自托管文件
// /emulatorjs/data/loader.js,供 GBA 播放器组件共用。单例 promise,避免重复注入脚本。
let emulatorPromise = null

export function loadEmulatorJS() {
  if (window.EmulatorJS) return Promise.resolve(window.EmulatorJS)
  if (!emulatorPromise) {
    emulatorPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = '/emulatorjs/data/loader.js'
      s.onload = resolve
      s.onerror = () => reject(new Error('EmulatorJS runtime load failed'))
      document.head.appendChild(s)
    }).then(() => window.EmulatorJS)
  }
  return emulatorPromise
}
