// 浏览器本地另存为:把已经生成好的 Blob 交给浏览器下载,不做任何上传。
// 图片 / 文件 / PDF / Markdown 几个在线工具共用,避免各写一份 a[download] 流程。
export function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // 立即 revoke 在部分浏览器会让下载中断,留一段时间再回收。
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}
