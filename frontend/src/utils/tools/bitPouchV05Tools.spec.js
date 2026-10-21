import { describe, it, expect } from 'vitest'
import { run as runDataFormat } from './dataFormatTool'
import { run as runHttpMessage } from './httpMessageTool'
import { run as runCurl } from './curlConvertTool'
import { run as runIpRange } from './ipRangeTool'
import { run as runMqtt } from './mqttTopicTool'

const ctx = { locale: 'en' }

describe('data-format 在线逻辑', () => {
  it('YAML → JSON 并保留普通对象', async () => {
    const result = await runDataFormat('name: demo\nport: 8080\n', { mode: 'toJson', format: 'yaml' }, ctx)
    expect(result.ok).toBe(true)
    expect(JSON.parse(result.output)).toEqual({ name: 'demo', port: 8080 })
    expect(result.data.sourceFormat).toBe('yaml')
    expect(result.data.targetFormat).toBe('json')
  })

  it('JSON → INI 支持展开嵌套', async () => {
    const result = await runDataFormat('{"server":{"host":"a","port":1}}', { mode: 'fromJson', target: 'ini', flatten: true }, ctx)
    expect(result.ok).toBe(true)
    expect(result.output).toContain('server.host=a')
    expect(result.output).toContain('server.port=1')
  })

  it('拒绝原型污染键', async () => {
    const result = await runDataFormat('{"__proto__":{"polluted":true}}', { mode: 'toJson', format: 'json' }, ctx)
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('unsafeKey')
    expect({}.polluted).toBeUndefined()
  })

  it('自动识别 YAML 与无法识别时返回 unsupportedFormat', async () => {
    const yaml = await runDataFormat('a: 1\n', { mode: 'toJson', format: 'auto' }, ctx)
    expect(yaml.ok).toBe(true)
    expect(yaml.data.sourceFormat).toBe('yaml')

    const unknown = await runDataFormat('just some words', { mode: 'toJson', format: 'auto' }, ctx)
    expect(unknown.ok).toBe(false)
    expect(unknown.error.code).toBe('unsupportedFormat')
  })
})

describe('http-message 在线逻辑', () => {
  it('解析标准 GET 请求', () => {
    const result = runHttpMessage('GET /a?x=1 HTTP/1.1\r\nHost: example.com\r\n\r\n', { mode: 'parse', kind: 'auto' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.kind).toBe('request')
    expect(result.data.request.method).toBe('GET')
    expect(result.data.query).toEqual([{ key: 'x', value: '1' }])
  })

  it('解析 chunked 正文', () => {
    const result = runHttpMessage(
      'HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n',
      { mode: 'parse', kind: 'auto' },
      ctx,
    )
    expect(result.ok).toBe(true)
    expect(result.data.body.raw).toBe('hello')
  })

  it('生成报文时拒绝头值 CRLF 注入', () => {
    const result = runHttpMessage(
      JSON.stringify({
        kind: 'response',
        status: 200,
        headers: [['X-Test', 'ok\r\nX-Injected: 1']],
      }),
      { mode: 'build' },
      ctx,
    )
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('invalidHeader')
  })
})

describe('curl-convert 在线逻辑', () => {
  it('解析 POST JSON 并生成 fetch 代码', () => {
    const command = "curl -X POST 'https://api.test/v1/login' -H 'Content-Type: application/json' -d '{\"name\":\"demo\"}'"
    const result = runCurl(command, { target: 'fetch' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.request.method).toBe('POST')
    expect(result.output).toContain('https://api.test/v1/login')
    expect(result.output).toContain('application/json')
  })

  it('Basic 认证按目标生成 header 或 auth 对象', () => {
    const fetchResult = runCurl("curl -u user:pass 'https://api.test'", { target: 'fetch' }, ctx)
    expect(fetchResult.ok).toBe(true)
    expect(fetchResult.output).toContain('dXNlcjpwYXNz')

    const axiosResult = runCurl("curl -u user:pass 'https://api.test'", { target: 'axios' }, ctx)
    expect(axiosResult.ok).toBe(true)
    expect(axiosResult.output).toContain('username: "user"')
    expect(axiosResult.output).toContain('password: "pass"')
  })

  it('未知选项给出警告但仍可转换', () => {
    const result = runCurl("curl --definitely-unknown 'https://api.test'", { target: 'fetch' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.warnings.length).toBeGreaterThan(0)
  })

  it('五个目标都包含方法、URL 与头', () => {
    const command = "curl -X PUT 'https://api.test/item' -H 'X-Token: abc' -d 'x=1'"
    for (const target of ['fetch', 'axios', 'python', 'go', 'powershell']) {
      const result = runCurl(command, { target }, ctx)
      expect(result.ok, target).toBe(true)
      expect(result.output, target).toContain('https://api.test/item')
      expect(result.output, target).toContain('X-Token')
      expect(result.output.toUpperCase(), target).toContain('PUT')
    }
  })

  it('未闭合引号给出位置错误', () => {
    const result = runCurl("curl 'https://api.test", { target: 'fetch' }, ctx)
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('unterminatedQuote')
  })
})

describe('ip-range 在线逻辑', () => {
  it('相邻网段合并为一个 CIDR', () => {
    const result = runIpRange('10.0.0.0/25\n10.0.0.128/25', { mode: 'merge' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.output).toBe('10.0.0.0/24')
  })

  it('排除子段后输出剩余网段', () => {
    const result = runIpRange('10.0.0.0/24\n10.0.0.0/26\n10.0.0.192/26', { mode: 'exclude' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.output.split('\n')).toEqual(['10.0.0.64/26', '10.0.0.128/26'])
  })

  it('IPv6 合并并按 RFC 5952 压缩', () => {
    const result = runIpRange('2001:db8::/65\n2001:db8:0:0:8000::/65', { mode: 'merge' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.output).toBe('2001:db8::/64')
  })

  it('混合 family 报错', () => {
    const result = runIpRange('10.0.0.0/24\n2001:db8::/64', { mode: 'merge' }, ctx)
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('mixedFamily')
  })
})

describe('mqtt-topic 在线逻辑', () => {
  it('解析共享订阅与通配符', () => {
    const result = runMqtt('$share/workers/sensors/+/temperature', { mode: 'parse' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.rows[0].shared.group).toBe('workers')
    expect(result.data.rows[0].wildcards.length).toBe(1)
  })

  it('首层通配符不匹配系统主题', () => {
    const normal = runMqtt('#\n$SYS/broker/uptime', { mode: 'match' }, ctx)
    expect(normal.ok).toBe(true)
    expect(normal.data.rows[0].matched).toBe(false)

    const explicit = runMqtt('$SYS/#\n$SYS/broker/uptime', { mode: 'match' }, ctx)
    expect(explicit.ok).toBe(true)
    expect(explicit.data.rows[0].matched).toBe(true)
  })

  it('非法通配符返回 invalidWildcard', () => {
    const result = runMqtt('sport/#/x', { mode: 'parse' }, ctx)
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('invalidWildcard')
  })

  it('校验模式同时报告过滤器与发布主题', () => {
    const result = runMqtt('sensors/+/temperature', { mode: 'validate' }, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.rows[0].validFilter).toBe(true)
    expect(result.data.rows[0].validTopic).toBe(false)
  })
})
