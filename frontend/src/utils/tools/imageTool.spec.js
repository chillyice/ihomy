import { describe, expect, it } from 'vitest'
import { buildIco, parseJpegExif, readIcoDirectory, run } from './imageTool'

const ascii = (text) => Array.from(text, (char) => char.charCodeAt(0))

describe('ICO 打包', () => {
  it('写入目录项与图像数据', () => {
    const png16 = Uint8Array.from([1, 2, 3, 4])
    const png32 = Uint8Array.from([5, 6, 7])
    const ico = buildIco([
      { width: 16, height: 16, data: png16 },
      { width: 32, height: 32, data: png32 },
    ])
    const entries = readIcoDirectory(ico)
    expect(entries).toHaveLength(2)
    expect(entries[0]).toMatchObject({ width: 16, height: 16, size: 4, offset: 6 + 32 })
    expect(entries[1]).toMatchObject({ width: 32, height: 32, size: 3 })
    expect(Array.from(ico.slice(entries[0].offset, entries[0].offset + 4))).toEqual([1, 2, 3, 4])
  })

  it('256 尺寸在目录里写 0', () => {
    const ico = buildIco([{ width: 256, height: 256, data: Uint8Array.from([9]) }])
    expect(ico[6]).toBe(0)
    expect(ico[7]).toBe(0)
  })

  it('非法尺寸与空数据报错', () => {
    expect(() => buildIco([])).toThrow('empty')
    expect(() => buildIco([{ width: 300, height: 300, data: Uint8Array.from([1]) }])).toThrow('invalidSize')
    expect(() => buildIco([{ width: 16, height: 32, data: Uint8Array.from([1]) }])).toThrow('invalidSize')
  })
})

describe('JPEG EXIF', () => {
  const buildJpeg = () => {
    const tiff = []
    tiff.push(0x49, 0x49, 0x2a, 0x00, 8, 0, 0, 0)
    tiff.push(0x02, 0x00)
    tiff.push(0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00)
    tiff.push(0x10, 0x01, 0x02, 0x00, 0x06, 0x00, 0x00, 0x00, 38, 0x00, 0x00, 0x00)
    tiff.push(0x00, 0x00, 0x00, 0x00)
    tiff.push(...ascii('Canon\0'))
    const payload = [...ascii('Exif\0\0'), ...tiff]
    const length = payload.length + 2
    return Uint8Array.from([0xff, 0xd8, 0xff, 0xe1, (length >> 8) & 0xff, length & 0xff, ...payload, 0xff, 0xd9])
  }

  it('读出 IFD0 标签', () => {
    const result = parseJpegExif(buildJpeg())
    expect(result.hasExif).toBe(true)
    expect(result.tags.find((tag) => tag.name === 'Model')?.value).toBe('Canon')
    expect(result.tags.find((tag) => tag.name === 'Orientation')?.value).toBe(6)
  })

  it('没有 EXIF 的 JPEG', () => {
    expect(parseJpegExif(Uint8Array.from([0xff, 0xd8, 0xff, 0xd9]))).toEqual({ hasExif: false, tags: [] })
  })

  it('非 JPEG 报错', () => {
    expect(() => parseJpegExif(Uint8Array.from([0x89, 0x50]))).toThrow('notJpeg')
  })

  it('run 输出标签摘要', () => {
    const hex = Array.from(buildJpeg(), (byte) => byte.toString(16).padStart(2, '0')).join('')
    const result = run(hex, {}, { locale: 'en' })
    expect(result.ok).toBe(true)
    expect(result.output).toContain('Model: Canon')
    expect(result.stats.tags).toBeGreaterThanOrEqual(2)
  })
})
