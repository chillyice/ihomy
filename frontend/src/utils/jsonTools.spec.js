import { describe, it, expect } from 'vitest'
import { JSON_MAX_DEPTH, csvToJson, formatJson, jsonToCsv, jsonToXml, toJava, toSqlInsert, toTypeScript, tokenizeJson } from './jsonTools'

const ok = (text, options) => {
  const r = formatJson(text, options)
  expect(r.ok).toBe(true)
  return r.text
}
const fail = (text, options) => {
  const r = formatJson(text, options)
  expect(r.ok).toBe(false)
  return r.error
}

describe('formatJson', () => {
  it('按 2 空格缩进,对象与数组换行', () => {
    expect(ok('{"a":1,"b":[1,2]}')).toBe('{\n  "a": 1,\n  "b": [\n    1,\n    2\n  ]\n}')
  })

  it('空对象/空数组保持单行', () => {
    expect(ok('{"a":{},"b":[]}')).toBe('{\n  "a": {},\n  "b": []\n}')
  })

  it('indent 传空串即压缩为单行', () => {
    expect(ok('{\n "a": 1,\n "b": [1, 2]\n}', { indent: '' })).toBe('{"a":1,"b":[1,2]}')
  })

  it('保留长整数、指数与小数原始字面量', () => {
    const text = '{"id":12345678901234567890,"n":1.0,"e":1E+3,"z":-0.5}'
    expect(ok(text, { indent: '' })).toBe(text)
  })

  it('保留字符串转义与重复键', () => {
    const text = '{"s":"a\\u00e9\\n\\"b\\"","k":1,"k":2}'
    expect(ok(text, { indent: '' })).toBe(text)
  })

  it('sortKeys 递归按键名排序,不用 locale 规则', () => {
    const text = '{"b":{"z":1,"a":2},"A":1,"a":2,"10":3,"9":4}'
    expect(ok(text, { indent: '', sortKeys: true })).toBe('{"10":3,"9":4,"A":1,"a":2,"b":{"a":2,"z":1}}')
  })

  it('返回输出统计', () => {
    const r = formatJson('{"a":1}')
    expect(r.stats).toEqual({ lines: 3, chars: 12, bytes: 12 })
  })

  it('空内容返回 empty', () => {
    expect(fail('').code).toBe('empty')
    expect(fail('   \n ').code).toBe('empty')
  })

  it('指出意外字符的行列', () => {
    const err = fail('{\n  "a": @\n}')
    expect(err).toMatchObject({ code: 'unexpectedChar', line: 2, column: 8, char: '@' })
  })

  it('识别缺冒号、缺逗号与多余逗号', () => {
    expect(fail('{"a" 1}')).toMatchObject({ code: 'expectedColon', line: 1, column: 6 })
    expect(fail('{"a":1 "b":2}')).toMatchObject({ code: 'expectedComma', line: 1, column: 8 })
    expect(fail('{"a":1,}')).toMatchObject({ code: 'trailingComma', line: 1, column: 8 })
    expect(fail('[1, ]')).toMatchObject({ code: 'trailingComma', line: 1, column: 5 })
  })

  it('识别引号、转义与数字格式问题', () => {
    expect(fail('{"a":"abc}').code).toBe('unterminatedString')
    expect(fail('{"a":"\\q"}')).toMatchObject({ code: 'invalidEscape', line: 1, column: 8 })
    expect(fail('{"a":01}')).toMatchObject({ code: 'expectedComma', line: 1, column: 7 })
    expect(fail('{"a":-}').code).toBe('invalidNumber')
  })

  it('结束符后有多余内容会被拒绝', () => {
    expect(fail('{"a":1}\n{}')).toMatchObject({ code: 'trailingContent', line: 2, column: 1 })
  })

  it('嵌套超过上限返回 tooDeep', () => {
    const deep = '['.repeat(JSON_MAX_DEPTH + 1) + ']'.repeat(JSON_MAX_DEPTH + 1)
    expect(fail(deep).code).toBe('tooDeep')
    const atLimit = '['.repeat(JSON_MAX_DEPTH) + ']'.repeat(JSON_MAX_DEPTH)
    expect(formatJson(atLimit).ok).toBe(true)
  })
})

describe('tokenizeJson', () => {
  it('区分键名、字符串、数字、布尔、null 与标点', () => {
    const tokens = tokenizeJson('{\n  "a": "x",\n  "b": [1, true, null]\n}')
    expect(tokens.map((t) => t.type)).toContain('key')
    expect(tokens.filter((t) => t.type === 'key')).toHaveLength(2)
    expect(tokens.find((t) => t.type === 'string')?.text).toBe('"x"')
    expect(tokens.find((t) => t.type === 'number')?.text).toBe('1')
    expect(tokens.find((t) => t.type === 'boolean')?.text).toBe('true')
    expect(tokens.find((t) => t.type === 'null')?.text).toBe('null')
  })

  it('拼回原文不丢字符', () => {
    const text = '{\n  "a": "x",\n  "b": [1, true, null]\n}'
    expect(
      tokenizeJson(text)
        .map((t) => t.text)
        .join(''),
    ).toBe(text)
  })
})

describe('数据转换', () => {
  it('嵌套对象拍平成 CSV', () => {
    const csv = jsonToCsv('{"a":{"b":1},"list":[1,2],"name":"x,y"}')
    expect(csv.split('\n')[0]).toBe('a.b,list.0,list.1,name')
    expect(csv).toContain('"x,y"')
  })

  it('CSV 还原成 JSON 并推断类型', () => {
    const json = JSON.parse(csvToJson('name,age\n阿岚,30'))
    expect(json).toEqual([{ name: '阿岚', age: 30 }])
  })

  it('CSV 点路径还原嵌套', () => {
    const json = JSON.parse(csvToJson('user.name,user.age\n小满,8'))
    expect(json[0].user.name).toBe('小满')
  })

  it('JSON 生成 XML', () => {
    const xml = jsonToXml('{"user":{"name":"阿岚","tags":["a","b"]}}')
    expect(xml).toContain('<name>阿岚</name>')
    expect((xml.match(/<tags>/g) || []).length).toBe(2)
    expect(xml).toContain('<root>')
  })

  it('非法 JSON 抛可识别错误', () => {
    expect(() => jsonToCsv('{')).toThrow('invalidJson')
  })
})

describe('代码生成', () => {
  const source = '{"id":1,"name":"a","vip":true,"tags":["x"],"profile":{"city":"hz"},"list":[{"k":1}]}'

  it('TypeScript 接口含嵌套命名', () => {
    const code = toTypeScript(source)
    expect(code).toContain('export interface Root')
    expect(code).toContain('id: number')
    expect(code).toContain('tags: string[]')
    expect(code).toContain('profile: Profile')
    expect(code).toContain('list: ListItem[]')
  })

  it('Java POJO 生成字段与内部类', () => {
    const code = toJava(source, { className: 'Demo' })
    expect(code).toContain('public class Demo')
    expect(code).toContain('private String name;')
    expect(code).toContain('public static class Profile')
    expect(code).toContain('private List<String> tags;')
  })

  it('SQL INSERT 支持数组与转义', () => {
    const sql = toSqlInsert('[{"id":1,"name":"O\'Neil"},{"id":2,"name":null}]', { table: 'demo' })
    expect(sql).toContain('INSERT INTO demo (id, name)')
    expect(sql).toContain("'O''Neil'")
    expect(sql).toContain('(2, NULL)')
  })
})
