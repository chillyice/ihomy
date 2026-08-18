/**
 * 图片 URL 工具:缩略图约定(原图 xxx.jpg → xxx_thumb.jpg)。
 * 后端 FileService.generateThumbIfImage 在上传时生成;存量图片无缩略图,
 * 前端 onerror 回退原图(功能不受影响,仅无流量优化)。
 */

/** 原图 URL → 缩略图 URL(非本站 /files/ URL 原样返回) */
export function thumbUrl(url) {
  if (!url) return url
  // 仅本站 /files/ 路径生成缩略图(外链不处理)
  if (!url.includes('/files/')) return url
  const dot = url.lastIndexOf('.')
  const slash = url.lastIndexOf('/')
  if (dot <= slash) return url  // 无扩展名
  return url.substring(0, dot) + '_thumb.jpg'
}

/** img onerror 回退原图(配合 thumbUrl 使用) */
export function onThumbError(e) {
  const img = e.target
  if (img.dataset.fallback) return  // 已回退过,避免死循环
  const src = img.src
  const dot = src.lastIndexOf('.')
  const slash = src.lastIndexOf('/')
  if (dot > slash && src.includes('_thumb')) {
    img.dataset.fallback = '1'
    img.src = src.substring(0, dot - 5) + src.substring(dot)  // 去掉 _thumb
  }
}
