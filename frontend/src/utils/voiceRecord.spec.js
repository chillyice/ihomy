import { describe, expect, it } from 'vitest'
import { cnToNumber, extractAmount, guessCategory, parseVoiceRecord } from './voiceRecord'

describe('cnToNumber 中文数字', () => {
  it('个位与十位', () => {
    expect(cnToNumber('一')).toBe(1)
    expect(cnToNumber('两')).toBe(2)
    expect(cnToNumber('十')).toBe(10)
    expect(cnToNumber('十五')).toBe(15)
    expect(cnToNumber('二十')).toBe(20)
    expect(cnToNumber('三十五')).toBe(35)
  })

  it('百位与零', () => {
    expect(cnToNumber('一百零八')).toBe(108)
    expect(cnToNumber('两百三十五')).toBe(235)
    expect(cnToNumber('一千')).toBe(1000)
  })

  it('认不出来返回 null', () => {
    expect(cnToNumber('')).toBeNull()
    expect(cnToNumber('三块五')).toBeNull()
  })
})

describe('extractAmount 金额抽取', () => {
  it('阿拉伯数字 + 货币后缀优先', () => {
    expect(extractAmount('买菜 35 块').yuan).toBe(35)
    expect(extractAmount('打车花了 12.5 元').yuan).toBe(12.5)
  })

  it('没有后缀时取最后一个数字(数量在前、金额在后)', () => {
    expect(extractAmount('买了 3 斤苹果 15').yuan).toBe(15)
  })

  it('中文数字带单位', () => {
    expect(extractAmount('午饭三十五块').yuan).toBe(35)
    expect(extractAmount('打车 二十五元').yuan).toBe(25)
  })

  it('没有金额返回 null', () => {
    expect(extractAmount('今天天气不错')).toBeNull()
    expect(extractAmount('')).toBeNull()
  })

  it('零元不算金额', () => {
    expect(extractAmount('花了 0 元')).toBeNull()
  })
})

describe('guessCategory 分类猜测', () => {
  it('按关键词归类', () => {
    expect(guessCategory('中午吃饭 30')).toBe('餐饮')
    expect(guessCategory('打车回家 20')).toBe('交通')
    expect(guessCategory('交电费 120')).toBe('水电燃气')
    expect(guessCategory('随便记一笔')).toBe('其他')
  })
})

describe('parseVoiceRecord 整句解析', () => {
  it('支出:金额 + 备注 + 分类', () => {
    expect(parseVoiceRecord('买菜 35 块')).toEqual({ type: 'EXPENSE', amount: 35, note: '菜', category: '餐饮' })
  })

  it('收入:带工资字样', () => {
    const r = parseVoiceRecord('发工资 8000 块')
    expect(r.type).toBe('INCOME')
    expect(r.amount).toBe(8000)
    expect(r.category).toBe('工资')
  })

  it('去掉口语前缀', () => {
    expect(parseVoiceRecord('帮我记一笔 咖啡 18 元').note).toBe('咖啡')
  })

  it('没有金额返回 null', () => {
    expect(parseVoiceRecord('记一笔')).toBeNull()
  })
})
