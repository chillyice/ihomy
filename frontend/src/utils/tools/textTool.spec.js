import { describe, expect, it } from 'vitest'
import {
  cleanText,
  convertCase,
  detectDelimiter,
  diffLines,
  markdownToDelimited,
  parseDelimited,
  run,
  splitWords,
  textStats,
  toFullWidth,
  toHalfWidth,
  toMarkdownTable,
} from './textTool'

describe('textStats', () => {
  it('统计字符/中文/词/行/字节', () => {
    const stats = textStats('你好世界\nhello world\n')
    expect(stats.cjk).toBe(4)
    expect(stats.words).toBe(3)
    expect(stats.lines).toBe(3)
    expect(stats.bytes).toBe(25)
  })
})

describe('全半角', () => {
  it('互转', () => {
    expect(toHalfWidth('ＡＢＣ，１２３')).toBe('ABC,123')
    expect(toFullWidth('ABC,123')).toBe('ＡＢＣ，１２３')
    expect(toHalfWidth(toFullWidth('ihomy'))).toBe('ihomy')
  })
})

describe('cleanText', () => {
  it('去空行、去重、排序', () => {
    expect(cleanText('b\n\n a \nb', { dropEmpty: true, trim: true, dedupe: true, sort: 'asc' })).toBe('a\nb')
  })

  it('加前缀后缀与行号', () => {
    expect(cleanText('x\ny', { prefix: '- ', numberLines: true })).toBe('1. - x\n2. - y')
  })

  it('join 合并为一行', () => {
    expect(cleanText('a\nb', { joinWith: ', ' })).toBe('a, b')
  })

  it('反转', () => {
    expect(cleanText('a\nb\nc', { reverse: true })).toBe('c\nb\na')
  })
})

describe('命名转换', () => {
  it('拆词处理驼峰与缩写', () => {
    expect(splitWords('HTTPServer response_code')).toEqual(['HTTP', 'Server', 'response', 'code'])
  })

  it('五种风格一次输出', () => {
    expect(convertCase('user profile-id')).toEqual({
      camel: 'userProfileId',
      pascal: 'UserProfileId',
      snake: 'user_profile_id',
      kebab: 'user-profile-id',
      constant: 'USER_PROFILE_ID',
    })
  })
})

describe('Markdown 表格', () => {
  it('识别分隔符', () => {
    expect(detectDelimiter('a\tb')).toBe('\t')
    expect(detectDelimiter('a|b')).toBe('|')
    expect(detectDelimiter('a,b')).toBe(',')
  })

  it('CSV 引号字段', () => {
    expect(parseDelimited('a,"b,c",d')).toEqual([['a', 'b,c', 'd']])
  })

  it('生成表格并对齐', () => {
    const table = toMarkdownTable(
      [
        ['姓名', '年龄'],
        ['阿岚', '30'],
      ],
      { align: 'right' },
    )
    expect(table).toContain('| 姓名 | 年龄 |')
    expect(table).toContain('| ---: | ---: |')
  })

  it('表格转回 CSV', () => {
    expect(markdownToDelimited('| a | b |\n| --- | --- |\n| 1 | 2 |')).toBe('a,b\n1,2')
  })

  it('竖线转义', () => {
    expect(toMarkdownTable([['a|b']])).toContain('a\\|b')
  })
})

describe('diffLines', () => {
  it('相同内容', () => {
    const { rows, stats } = diffLines('a\nb', 'a\nb')
    expect(rows.every((row) => row.type === 'same')).toBe(true)
    expect(stats).toMatchObject({ added: 0, removed: 0, same: 2 })
  })

  it('新增与删除', () => {
    const { rows, stats } = diffLines('a\nb', 'a\nc\nb')
    expect(stats).toMatchObject({ added: 1, removed: 0 })
    expect(rows.map((row) => row.type)).toEqual(['same', 'add', 'same'])
  })

  it('乱序时按块替换', () => {
    const { rows, stats } = diffLines('a\nb\nc', 'c\na\nd')
    expect(stats.added + stats.removed).toBeGreaterThan(0)
    expect(
      rows
        .filter((row) => row.leftNo)
        .map((row) => row.left)
        .join('|'),
    ).toBe('a|b|c')
    expect(
      rows
        .filter((row) => row.rightNo)
        .map((row) => row.right)
        .join('|'),
    ).toBe('c|a|d')
  })

  it('忽略大小写与空白', () => {
    const strict = diffLines('Hello', ' hello ')
    expect(strict.stats.same).toBe(0)
    const loose = diffLines('Hello', ' hello ', { ignoreCase: true, ignoreWhitespace: true })
    expect(loose.stats.same).toBe(1)
  })

  it('CRLF 与 LF 混合稳定', () => {
    const { stats } = diffLines('a\r\nb', 'a\nb')
    expect(stats.same).toBe(2)
  })

  it('超行数限制报错', () => {
    expect(() => diffLines('a\nb\nc', 'a', { maxLines: 2 })).toThrow('tooManyLines')
  })
})

describe('run', () => {
  it('clean 模式空输入报错', () => {
    expect(run('', {}, { locale: 'zh' }).error.code).toBe('empty')
  })

  it('case 模式输出五种风格', () => {
    const result = run('hello world', { mode: 'case' }, { locale: 'en' })
    expect(result.ok).toBe(true)
    expect(result.data.snake).toBe('hello_world')
  })

  it('table 模式从 CSV 生成表格', () => {
    const result = run('a,b\n1,2', { mode: 'table' }, { locale: 'en' })
    expect(result.ok).toBe(true)
    expect(result.output).toContain('| a | b |')
  })

  it('diff 模式错误本地化', () => {
    const result = run('a\nb\nc', { mode: 'diff', second: 'a', maxLines: 2 }, { locale: 'en' })
    expect(result.error.message).toContain('More than 2 lines')
  })
})
