/**
 * 前端文件小工具:原生桥回传 base64 → File(上传接口要 FormData)。
 *
 * 不用 `Uint8Array.from(atob(s), fn)`:大图会一次性展开成巨型参数列表,
 * 在低内存设备上可能直接 RangeError,逐字节写入更稳。
 */
export function fileFromBase64(data, name = 'file', type = '') {
  const binary = atob(String(data || ''))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return type ? new File([bytes], name, { type }) : new File([bytes], name)
}
