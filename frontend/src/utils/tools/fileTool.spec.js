import { describe, expect, it } from 'vitest'
import { detectFileType, formatFileSize, planZipNames, run, uniqueName } from './fileTool'

const bytes = (...values) => Uint8Array.from(values)
const ascii = (text) => Array.from(text, (char) => char.charCodeAt(0))

describe('magic bytes 识别', () => {
  it('常见图片与文档', () => {
    expect(detectFileType(bytes(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)).ext).toBe('png')
    expect(detectFileType(bytes(0xff, 0xd8, 0xff, 0xe0)).ext).toBe('jpg')
    expect(detectFileType(bytes(...ascii('%PDF-1.7'))).ext).toBe('pdf')
    expect(detectFileType(bytes(0x50, 0x4b, 0x03, 0x04)).ext).toBe('zip')
    expect(detectFileType(bytes(0x1f, 0x8b, 0x08)).ext).toBe('gz')
  })

  it('复合签名:RIFF 家族', () => {
    const webp = Uint8Array.from([...ascii('RIFF'), 0, 0, 0, 0, ...ascii('WEBP')])
    expect(detectFileType(webp).ext).toBe('webp')
    const wav = Uint8Array.from([...ascii('RIFF'), 0, 0, 0, 0, ...ascii('WAVE')])
    expect(detectFileType(wav).ext).toBe('wav')
  })

  it('偏移签名:MP4 的 ftyp', () => {
    const mp4 = Uint8Array.from([0, 0, 0, 0x18, ...ascii('ftypisom')])
    expect(detectFileType(mp4).ext).toBe('mp4')
  })

  it('文本类按内容识别', () => {
    expect(detectFileType(Uint8Array.from(ascii('<svg xmlns="http://www.w3.org/2000/svg">'))).ext).toBe('svg')
    expect(detectFileType(Uint8Array.from(ascii('<!DOCTYPE html><html>'))).ext).toBe('html')
    expect(detectFileType(Uint8Array.from(ascii('{"a":1}'))).ext).toBe('json')
  })

  it('识别不出返回 null', () => {
    expect(detectFileType(bytes(1, 2, 3, 4))).toBe(null)
    expect(detectFileType(bytes())).toBe(null)
  })
})

describe('ZIP 条目命名', () => {
  it('重名追加序号且大小写不敏感', () => {
    const used = new Set()
    expect(uniqueName('a.txt', used)).toBe('a.txt')
    expect(uniqueName('a.txt', used)).toBe('a(1).txt')
    expect(uniqueName('A.TXT', used)).toBe('A(2).TXT')
    expect(uniqueName('noext', used)).toBe('noext')
    expect(uniqueName('noext', used)).toBe('noext(1)')
  })

  it('一次规划保持顺序', () => {
    expect(planZipNames(['x.png', 'y.png', 'x.png'])).toEqual(['x.png', 'y.png', 'x(1).png'])
  })
})

describe('run 与格式化', () => {
  it('十六进制输入识别', () => {
    const result = run('ffd8ffe0', {}, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.ext).toBe('jpg')
  })

  it('非法十六进制给出可读错误', () => {
    expect(run('zz', {}, { locale: 'en' }).error.code).toBe('invalidHex')
  })

  it('文件大小格式', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(3 * 1024 * 1024)).toBe('3.0 MB')
  })
})
