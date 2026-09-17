// Ruffle(Flash 模拟器)运行时懒加载:脚本标签加载自托管文件 /ruffle/ruffle.js,
// 供「本地 Flash 播放器」与「导入的 swf 小游戏」共用。单例 promise,避免重复注入脚本。
let rufflePromise = null

export function loadRuffle() {
  if (window.RufflePlayer) return Promise.resolve(window.RufflePlayer)
  if (!rufflePromise) {
    rufflePromise = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = '/ruffle/ruffle.js'
      s.onload = resolve
      s.onerror = () => reject(new Error('Ruffle runtime load failed'))
      document.head.appendChild(s)
    }).then(() => {
      try {
        const cfg = window.RufflePlayer.config || (window.RufflePlayer.config = {})
        cfg.publicPath = '/ruffle'
      } catch (e) {
        // 脚本按自身 src 自动推导 publicPath,显式设置失败也不致命
      }
      return window.RufflePlayer
    })
  }
  return rufflePromise
}
