import { describe, it, expect } from 'vitest'
import { buildImagePdf, crc32, createZip, formatBytes, parsePageRange } from './pdfTools'

const bytes = async (blob) => new Uint8Array(await blob.arrayBuffer())
const latin1 = async (blob) => new TextDecoder('latin1').decode(await bytes(blob))

describe('parsePageRange', () => {
  it('留空返回全部页码', () => {
    expect(parsePageRange('', 4)).toEqual([1, 2, 3, 4])
    expect(parsePageRange('   ', 3)).toEqual([1, 2, 3])
  })

  it('支持区间、单页、去重与中文逗号', () => {
    expect(parsePageRange('1-3,5,3', 5)).toEqual([1, 2, 3, 5])
    expect(parsePageRange('2，4-5', 5)).toEqual([2, 4, 5])
  })

  it('支持省略结束页码的开区间', () => {
    expect(parsePageRange('2-', 4)).toEqual([2, 3, 4])
  })

  it('非法或越界返回 null', () => {
    expect(parsePageRange('0-2', 5)).toBeNull()
    expect(parsePageRange('4-2', 5)).toBeNull()
    expect(parsePageRange('6', 5)).toBeNull()
    expect(parsePageRange('abc', 5)).toBeNull()
  })
})

describe('formatBytes', () => {
  it('按量级格式化', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(1024)).toBe('1.0 KB')
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB')
  })
})

describe('crc32', () => {
  it('与标准校验值一致', () => {
    expect(crc32(new TextEncoder().encode('123456789'))).toBe(0xcbf43926)
  })
})

describe('createZip', () => {
  it('写出可解析的存储型 ZIP 结构', async () => {
    const zip = await createZip(
      [
        { name: 'page-001.png', data: new Uint8Array([1, 2, 3]) },
        { name: 'page-002.png', data: new Blob([new Uint8Array([4, 5])]) },
      ],
      new Date(2026, 0, 2, 3, 4, 6),
    )
    const data = await bytes(zip)
    expect([...data.slice(0, 4)]).toEqual([0x50, 0x4b, 0x03, 0x04])
    const text = new TextDecoder('latin1').decode(data)
    expect(text).toContain('page-001.png')
    expect(text).toContain('page-002.png')
    const eocd = data.length - 22
    expect([...data.slice(eocd, eocd + 4)]).toEqual([0x50, 0x4b, 0x05, 0x06])
    const view = new DataView(data.buffer, data.byteOffset, data.byteLength)
    expect(view.getUint16(eocd + 10, true)).toBe(2)
  })
})

describe('buildImagePdf', () => {
  const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xdb, 0x00, 0x43, 0xff, 0xd9])

  it('生成页数、图片资源与可定位的 xref', async () => {
    const pdf = buildImagePdf(
      [
        { data: jpeg, width: 1200, height: 1600 },
        { data: jpeg, width: 800, height: 600 },
      ],
      { pageSize: 'A4', margin: 24 },
    )
    const data = await bytes(pdf)
    const text = await latin1(pdf)
    expect(text.startsWith('%PDF-1.4')).toBe(true)
    expect(text).toContain('/Count 2')
    expect(text).toContain('/MediaBox [0 0 595.28 841.89]')
    expect(text).toContain('/Filter /DCTDecode')
    expect(text.trimEnd().endsWith('%%EOF')).toBe(true)

    const startxref = Number(text.match(/startxref\s+(\d+)/)[1])
    expect(new TextDecoder('latin1').decode(data.slice(startxref, startxref + 4))).toBe('xref')
    expect(text.slice(startxref, startxref + 9)).toBe('xref\n0 9\n')
  })

  it('跟随图片时按 96 DPI 换算页面尺寸', async () => {
    const pdf = buildImagePdf([{ data: jpeg, width: 960, height: 540 }], { pageSize: 'IMAGE' })
    const text = await latin1(pdf)
    expect(text).toContain('/MediaBox [0 0 720 405]')
  })
})
