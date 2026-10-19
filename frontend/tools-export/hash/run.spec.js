import { describe, it, expect } from 'vitest'
import { HASH_ALGORITHMS, MAX_FILE_BYTES, bytesToBase64, bytesToHex, crc32, digestBytes, md5, run, runFile } from './core'

const encoder = new TextEncoder()
const hexOf = (bytes) => bytesToHex(bytes)

describe('MD5', () => {
  it('RFC 1321 测试向量', () => {
    expect(hexOf(md5(encoder.encode('')))).toBe('d41d8cd98f00b204e9800998ecf8427e')
    expect(hexOf(md5(encoder.encode('a')))).toBe('0cc175b9c0f1b6a831c399e269772661')
    expect(hexOf(md5(encoder.encode('abc')))).toBe('900150983cd24fb0d6963f7d28e17f72')
    expect(hexOf(md5(encoder.encode('message digest')))).toBe('f96b697d7cb7938d525a2f31aaf161d0')
    expect(hexOf(md5(encoder.encode('12345678901234567890123456789012345678901234567890123456789012345678901234567890')))).toBe(
      '57edf4a22be3c955ac49da2e2107b67a',
    )
  })

  it('UTF-8 中文按字节计算', () => {
    expect(hexOf(md5(encoder.encode('你好')))).toBe('7eca689f0d3389d9dea66ae112e5cfd7')
  })
})

describe('CRC32', () => {
  it('标准校验值', () => {
    expect(crc32(encoder.encode('123456789')).toString(16)).toBe('cbf43926')
    expect(crc32(encoder.encode(''))).toBe(0)
    expect(crc32(encoder.encode('The quick brown fox jumps over the lazy dog')).toString(16)).toBe('414fa339')
  })
})

describe('SHA 系与 HMAC', () => {
  it('SHA-256 / SHA-1 已知向量', async () => {
    expect(hexOf(await digestBytes('abc', 'sha256'))).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
    expect(hexOf(await digestBytes('abc', 'sha1'))).toBe('a9993e364706816aba3e25717850c26c9cd0d89d')
  })

  it('HMAC-SHA256 RFC 4231 用例', async () => {
    const digest = await digestBytes('what do ya want for nothing?', 'sha256', { hmacKey: 'Jefe' })
    expect(hexOf(digest)).toBe('5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843')
  })

  it('CRC32 输出为大端 4 字节', async () => {
    const digest = await digestBytes('123456789', 'crc32')
    expect(hexOf(digest)).toBe('cbf43926')
    expect(digest).toHaveLength(4)
  })

  it('HMAC 模式忽略 MD5 / CRC32', async () => {
    const result = await run('abc', { algorithms: ['md5', 'sha256', 'crc32'], hmacKey: 'k' }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.results.map((item) => item.algorithm)).toEqual(['sha256'])
  })
})

describe('run 文本入口', () => {
  it('默认返回 MD5 与 SHA-256', async () => {
    const result = await run('abc', {}, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.results.map((item) => item.algorithm)).toEqual(['md5', 'sha256'])
    expect(result.output).toContain('MD5: 900150983cd24fb0d6963f7d28e17f72')
  })

  it('支持 base64 输出', async () => {
    const result = await run('abc', { algorithms: ['sha256'], format: 'base64' }, { locale: 'zh' })
    expect(result.data.results[0].value).toBe('ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=')
    expect(result.data.results[0].hex).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad')
  })

  it('期望值比对忽略大小写', async () => {
    const result = await run('abc', { algorithms: ['md5'], expected: '900150983CD24FB0D6963F7D28E17F72' }, { locale: 'zh' })
    expect(result.data.expected).toMatchObject({ match: true, algorithms: ['md5'] })

    const miss = await run('abc', { algorithms: ['md5'], expected: 'deadbeef' }, { locale: 'zh' })
    expect(miss.data.expected.match).toBe(false)
  })

  it('空输入可以计算摘要', async () => {
    const result = await run('', { algorithms: ['sha256'] }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.results[0].hex).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855')
  })

  it('非法/空算法选择给出可读错误', async () => {
    const none = await run('abc', { algorithms: [] }, { locale: 'zh' })
    expect(none).toMatchObject({ ok: false, error: { code: 'noAlgorithm' } })

    const invalid = await run('abc', { algorithms: ['sha999'] }, { locale: 'en' })
    expect(invalid.ok).toBe(false)
    expect(invalid.error.message).toMatch(/algorithm/i)
  })

  it('超长输入被拒绝', async () => {
    const result = await run('a'.repeat(1_000_001), {}, { locale: 'zh' })
    expect(result).toMatchObject({ ok: false, error: { code: 'tooLong' } })
  })
})

describe('runFile 文件入口', () => {
  const fakeFile = (text) => {
    const bytes = encoder.encode(text)
    return { name: 'sample.txt', size: bytes.length, arrayBuffer: async () => bytes.buffer }
  }

  it('文件摘要与文本摘要一致', async () => {
    const result = await runFile(fakeFile('abc'), { algorithms: ['md5', 'sha256'] }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.fileName).toBe('sample.txt')
    expect(result.data.results[0].hex).toBe('900150983cd24fb0d6963f7d28e17f72')
  })

  it('超过上限直接拒绝,不读取文件', async () => {
    let read = false
    const file = { name: 'huge.bin', size: MAX_FILE_BYTES + 1, arrayBuffer: async () => ((read = true), new ArrayBuffer(0)) }
    const result = await runFile(file, {}, { locale: 'zh' })
    expect(result).toMatchObject({ ok: false, error: { code: 'fileTooLarge' } })
    expect(read).toBe(false)
  })
})

describe('编码工具函数', () => {
  it('bytesToBase64 处理补位', () => {
    expect(bytesToBase64(encoder.encode('a'))).toBe('YQ==')
    expect(bytesToBase64(encoder.encode('ab'))).toBe('YWI=')
    expect(bytesToBase64(encoder.encode('abc'))).toBe('YWJj')
  })

  it('算法清单稳定', () => {
    expect(HASH_ALGORITHMS).toEqual(['md5', 'sha1', 'sha256', 'sha384', 'sha512', 'crc32'])
  })
})
