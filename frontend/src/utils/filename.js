// URL → 本地文件名:解码末段并清掉 Windows / Android 不允许的字符。
// 下载与系统分享共用同一口径,避免同一文件在两个入口出现不同名字。
export function filenameFrom(url, fallback = 'ihomy-file') {
  let raw
  try {
    const base = typeof location !== 'undefined' ? location.href : 'https://localhost/'
    raw = decodeURIComponent(new URL(url, base).pathname.split('/').pop() || '')
  } catch (e) {
    raw =
      String(url || '')
        .split('/')
        .pop() || ''
  }
  const name = raw.replace(/[\\/:*?"<>|]/g, '_').trim()
  return name || fallback
}
