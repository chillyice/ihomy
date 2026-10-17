// PDF 转换工具的纯函数与浏览器侧封装。
// 转换全部在本机浏览器完成(不上传服务器):PDF 渲染/取文字走 pdfjs,
// 图片转 PDF 与多文件打包用这里的最小实现,避免为一个工具再引第三方依赖。

const encoder = new TextEncoder()

/** 解析页码范围:留空=全部;"1-3,5" / "1-" 均支持;非法返回 null */
export function parsePageRange(input, total) {
  const raw = String(input ?? '').trim()
  if (!raw) return Array.from({ length: total }, (_, i) => i + 1)
  const pages = new Set()
  for (const part of raw.split(/[,，]/)) {
    const piece = part.trim()
    if (!piece) continue
    const m = piece.match(/^(\d+)(?:\s*-\s*(\d*))?$/)
    if (!m) return null
    const from = Number(m[1])
    let to = from
    if (m[2] === '') to = total
    else if (m[2] !== undefined) to = Number(m[2])
    if (from < 1 || to < 1 || from > total || to > total || from > to) return null
    for (let i = from; i <= to; i++) pages.add(i)
  }
  if (!pages.size) return null
  return [...pages].sort((a, b) => a - b)
}

export function formatBytes(n) {
  if (!Number.isFinite(n) || n < 0) return '—'
  if (n < 1024) return `${n} B`
  const units = ['KB', 'MB', 'GB']
  let value = n / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[i]}`
}

/* ---------------- PDF.js(PDF → 图片 / 文本) ---------------- */

let pdfjsPromise = null

function getPdfjs() {
  if (!pdfjsPromise) {
    pdfjsPromise = (async () => {
      const mod = await import('pdfjs-dist')
      const lib = mod.getDocument ? mod : mod.default
      const worker = await import('pdfjs-dist/build/pdf.worker.min.mjs?url')
      // 带版本查询:避免旧 MIME 缓存命中导致 worker 拒载
      lib.GlobalWorkerOptions.workerSrc = worker.default + '?v=2'
      return lib
    })()
  }
  return pdfjsPromise
}

export async function loadPdfDocument(data) {
  const lib = await getPdfjs()
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data)
  return lib.getDocument({ data: bytes }).promise
}

export async function renderPdfPageToBlob(page, { scale = 2, format = 'png', quality = 0.92 } = {}) {
  let viewport = page.getViewport({ scale })
  const maxSide = 12000
  if (Math.max(viewport.width, viewport.height) > maxSide) {
    viewport = page.getViewport({ scale: (scale * maxSide) / Math.max(viewport.width, viewport.height) })
  }
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.floor(viewport.width))
  canvas.height = Math.max(1, Math.floor(viewport.height))
  const ctx = canvas.getContext('2d', { alpha: format === 'png' })
  if (format !== 'png') {
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
  }
  await page.render({ canvas, canvasContext: ctx, viewport }).promise
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('canvas export failed'))),
      format === 'png' ? 'image/png' : 'image/jpeg',
      quality,
    )
  })
}

function textItemsToText(items) {
  const lines = []
  let line = null
  for (const item of items) {
    if (!item.str) continue
    const x = item.transform?.[4] ?? 0
    const y = item.transform?.[5] ?? 0
    if (!line || Math.abs(line.y - y) > 2) {
      line = { y, x, end: x + (item.width || 0), parts: [item.str] }
      lines.push(line)
      continue
    }
    const gap = x - line.end
    const prev = line.parts[line.parts.length - 1]
    if (gap > 1.5 && !/\s$/.test(prev) && !/^\s/.test(item.str)) line.parts.push(' ')
    line.parts.push(item.str)
    line.end = x + (item.width || 0)
  }
  return lines.map((l) => l.parts.join('').replace(/\s+$/, '')).join('\n')
}

/** 逐页提取文字;返回 [{ page, text }],进度与取消回调由调用方控制 */
export async function extractPdfText(doc, pages, { onProgress, isCancelled } = {}) {
  const result = []
  for (let i = 0; i < pages.length; i++) {
    if (isCancelled?.()) break
    const page = await doc.getPage(pages[i])
    const content = await page.getTextContent()
    result.push({ page: pages[i], text: textItemsToText(content.items) })
    page.cleanup?.()
    onProgress?.(i + 1, pages.length)
  }
  return result
}

/* ---------------- 图片 → PDF(最小 PDF 1.4 写入) ---------------- */

const A4 = { width: 595.28, height: 841.89 }

function num(v) {
  return String(Math.round(v * 100) / 100)
}

function pageMetrics(pxWidth, pxHeight, pageSize, margin) {
  if (pageSize === 'IMAGE') {
    // 按 96 DPI 把像素换成 PDF 点,页面与图片同比例
    const w = (pxWidth * 72) / 96
    const h = (pxHeight * 72) / 96
    return { w, h, x: 0, y: 0, dw: w, dh: h }
  }
  const w = pageSize === 'A4_LANDSCAPE' ? A4.height : A4.width
  const h = pageSize === 'A4_LANDSCAPE' ? A4.width : A4.height
  const availW = Math.max(1, w - margin * 2)
  const availH = Math.max(1, h - margin * 2)
  const fit = Math.min(availW / pxWidth, availH / pxHeight)
  const dw = pxWidth * fit
  const dh = pxHeight * fit
  return { w, h, x: (w - dw) / 2, y: (h - dh) / 2, dw, dh }
}

/**
 * 图片生成 PDF。
 * images: [{ data: Uint8Array(JPEG), width, height }]
 * options.pageSize: IMAGE | A4 | A4_LANDSCAPE;margin 仅 A4 生效(单位 pt)
 */
export function buildImagePdf(images, { pageSize = 'A4', margin = 24 } = {}) {
  if (!images.length) throw new Error('no images')
  const chunks = []
  let offset = 0
  const push = (chunk) => {
    const bytes = typeof chunk === 'string' ? encoder.encode(chunk) : chunk
    chunks.push(bytes)
    offset += bytes.length
  }
  const offsets = []
  const startObject = (n) => {
    offsets[n] = offset
    push(`${n} 0 obj\n`)
  }

  push('%PDF-1.4\n')
  push(new Uint8Array([0x25, 0xe2, 0xe3, 0xcf, 0xd3, 0x0a]))

  const pageIds = images.map((_, i) => 3 + i * 3)
  startObject(1)
  push('<< /Type /Catalog /Pages 2 0 R >>\nendobj\n')
  startObject(2)
  push(`<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(' ')}] /Count ${images.length} >>\nendobj\n`)

  images.forEach((img, i) => {
    const pageId = 3 + i * 3
    const contentId = pageId + 1
    const imageId = pageId + 2
    const m = pageMetrics(img.width, img.height, pageSize, margin)
    const content = `q\n${num(m.dw)} 0 0 ${num(m.dh)} ${num(m.x)} ${num(m.y)} cm\n/Im0 Do\nQ\n`
    const contentBytes = encoder.encode(content)

    startObject(pageId)
    push(
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${num(m.w)} ${num(m.h)}] ` +
        `/Resources << /XObject << /Im0 ${imageId} 0 R >> /ProcSet [/PDF /ImageC] >> ` +
        `/Contents ${contentId} 0 R >>\nendobj\n`,
    )
    startObject(contentId)
    push(`<< /Length ${contentBytes.length} >>\nstream\n`)
    push(contentBytes)
    push('endstream\nendobj\n')
    startObject(imageId)
    push(
      `<< /Type /XObject /Subtype /Image /Width ${img.width} /Height ${img.height} ` +
        `/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${img.data.length} >>\nstream\n`,
    )
    push(img.data)
    push('\nendstream\nendobj\n')
  })

  const size = 3 + images.length * 3
  const xrefOffset = offset
  push(`xref\n0 ${size}\n`)
  push('0000000000 65535 f \n')
  for (let i = 1; i < size; i++) push(`${String(offsets[i]).padStart(10, '0')} 00000 n \n`)
  push(`trailer\n<< /Size ${size} /Root 1 0 R /Producer (ihomy) >>\nstartxref\n${xrefOffset}\n%%EOF\n`)

  return new Blob(chunks, { type: 'application/pdf' })
}

/* ---------------- 浏览器侧图片归一化 ---------------- */

async function decodeImage(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' })
    } catch {
      /* 老浏览器不支持该选项时退回默认解码 */
    }
    try {
      return await createImageBitmap(file)
    } catch {
      /* 继续用 <img> 兜底 */
    }
  }
  const url = URL.createObjectURL(file)
  try {
    return await new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => reject(new Error('image decode failed'))
      img.src = url
    })
  } finally {
    URL.revokeObjectURL(url)
  }
}

/** 图片统一转成白底 JPEG,并限制最长边,避免超大照片撑爆内存 */
export async function normalizeImage(file, { maxSide = 4096, quality = 0.9 } = {}) {
  const bitmap = await decodeImage(file)
  const width = bitmap.width || bitmap.naturalWidth
  const height = bitmap.height || bitmap.naturalHeight
  const scale = Math.min(1, maxSide / Math.max(width, height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(width * scale))
  canvas.height = Math.max(1, Math.round(height * scale))
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close?.()
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('canvas export failed'))), 'image/jpeg', quality)
  })
  return { blob, width: canvas.width, height: canvas.height }
}

/* ---------------- ZIP 打包(仅存储,不压缩) ---------------- */

let crcTable = null

export function crc32(bytes) {
  if (!crcTable) {
    crcTable = new Uint32Array(256)
    for (let n = 0; n < 256; n++) {
      let c = n
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
      crcTable[n] = c >>> 0
    }
  }
  let crc = 0xffffffff
  for (let i = 0; i < bytes.length; i++) crc = (crc >>> 8) ^ crcTable[(crc ^ bytes[i]) & 0xff]
  return (crc ^ 0xffffffff) >>> 0
}

function dosDateTime(date) {
  const year = Math.max(1980, date.getFullYear())
  return {
    time: (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2),
    date: ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate(),
  }
}

/** files: [{ name, data: Uint8Array|Blob }] → zip Blob(store:图片/PDF本身已压缩) */
export async function createZip(files, date = new Date()) {
  const { time, date: dosDate } = dosDateTime(date)
  const localParts = []
  const centralParts = []
  let offset = 0
  for (const file of files) {
    const data = file.data instanceof Uint8Array ? file.data : new Uint8Array(await file.data.arrayBuffer())
    const nameBytes = encoder.encode(file.name)
    const crc = crc32(data)
    const local = new Uint8Array(30 + nameBytes.length)
    const lv = new DataView(local.buffer)
    lv.setUint32(0, 0x04034b50, true)
    lv.setUint16(4, 20, true)
    lv.setUint16(6, 0x0800, true)
    lv.setUint16(8, 0, true)
    lv.setUint16(10, time, true)
    lv.setUint16(12, dosDate, true)
    lv.setUint32(14, crc, true)
    lv.setUint32(18, data.length, true)
    lv.setUint32(22, data.length, true)
    lv.setUint16(26, nameBytes.length, true)
    lv.setUint16(28, 0, true)
    local.set(nameBytes, 30)
    localParts.push(local, data)

    const central = new Uint8Array(46 + nameBytes.length)
    const cv = new DataView(central.buffer)
    cv.setUint32(0, 0x02014b50, true)
    cv.setUint16(4, 20, true)
    cv.setUint16(6, 20, true)
    cv.setUint16(8, 0x0800, true)
    cv.setUint16(10, 0, true)
    cv.setUint16(12, time, true)
    cv.setUint16(14, dosDate, true)
    cv.setUint32(16, crc, true)
    cv.setUint32(20, data.length, true)
    cv.setUint32(24, data.length, true)
    cv.setUint16(28, nameBytes.length, true)
    cv.setUint32(42, offset, true)
    central.set(nameBytes, 46)
    centralParts.push(central)

    offset += local.length + data.length
  }
  const centralSize = centralParts.reduce((sum, part) => sum + part.length, 0)
  const eocd = new Uint8Array(22)
  const ev = new DataView(eocd.buffer)
  ev.setUint32(0, 0x06054b50, true)
  ev.setUint16(8, files.length, true)
  ev.setUint16(10, files.length, true)
  ev.setUint32(12, centralSize, true)
  ev.setUint32(16, offset, true)
  return new Blob([...localParts, ...centralParts, eocd], { type: 'application/zip' })
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10000)
}
