// 浏览器下载小工具:备份包等「直接返回文件流」的接口共用。
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  // 给浏览器留出读取 blob 的时间再释放
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}

/**
 * 下载接口用 responseType=blob,业务失败时后端仍会返回 JSON;
 * 这里先识别 JSON 并抛出可读错误,避免把错误信息存成 .zip。
 */
export async function saveBlobResponse(blob, filename) {
  if (blob && typeof blob.text === 'function' && String(blob.type || '').includes('json')) {
    let message = ''
    try {
      message = JSON.parse(await blob.text())?.message || ''
    } catch (e) {
      // 响应体不是 JSON 时保持空串,由调用方补自己的兜底文案
    }
    throw new Error(message)
  }
  downloadBlob(blob, filename)
}
