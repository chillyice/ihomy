/**
 * 「说一句话记一笔」的口语解析:把识别出来的中文句子拆成 { type, amount, note, category }。
 *
 * 只在客户端做轻量解析——识别文本本身就是人说的话,口径越简单越不容易出错:
 * 金额取「带块/元/圆/钱后缀的那个数」,没有后缀就取句子里的最后一个数;
 * 中文数字支持到千位(万/亿按解析不出来处理,退回让用户手填)。
 * 返回 null 表示没找到金额,调用方应提示"没听清金额"而不是猜。
 */
import { BOOK_CATEGORIES } from '@/utils/bookLedger'

const CN_DIGITS = { 零: 0, 〇: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 }
const CN_UNITS = { 十: 10, 百: 100, 千: 1000 }

/** 中文数字段(万以内)→ 阿拉伯数字;含不认识的字符返回 null */
export function cnToNumber(text) {
  const s = String(text || '').trim()
  if (!s) return null
  let total = 0
  let current = 0
  let seen = false
  for (const ch of s) {
    if (ch in CN_DIGITS) {
      current = CN_DIGITS[ch]
      seen = true
    } else if (ch in CN_UNITS) {
      total += (current || 1) * CN_UNITS[ch]
      current = 0
      seen = true
    } else {
      return null
    }
  }
  return seen ? total + current : null
}

/** 全角数字/小数点归一,便于后面统一按半角匹配 */
function normalize(text) {
  return String(text || '')
    .replace(/[０-９]/g, (c) => String.fromCharCode(c.charCodeAt(0) - 0xfee0))
    .replace(/[．]/g, '.')
    .replace(/[，、；;]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

const ARABIC_RE = /(\d+(?:\.\d{1,2})?)\s*(块钱|块|元|圆|钱)?/g
const CN_RE = /([零〇一二两三四五六七八九十百千]+)\s*(块钱|块|元|圆|钱)/g

/**
 * 抽金额:优先「带货币后缀」的那个(口语里最后说的通常是金额,取最后一个后缀命中),
 * 没有后缀就取最后一个阿拉伯数字。返回 { yuan, raw } 或 null。
 */
export function extractAmount(text) {
  const s = normalize(text)
  let best = null
  for (const m of s.matchAll(ARABIC_RE)) {
    const value = Number(m[1])
    if (!Number.isFinite(value) || value <= 0) continue
    const withSuffix = !!m[2]
    if (withSuffix || !best || !best.withSuffix) {
      // 有后缀的优先;同为无后缀时保留后出现的那个
      best = { value, raw: m[0].trim(), withSuffix }
    }
  }
  for (const m of s.matchAll(CN_RE)) {
    const value = cnToNumber(m[1])
    if (!value || value <= 0) continue
    if (!best || !best.withSuffix) best = { value, raw: m[0].trim(), withSuffix: true }
  }
  return best ? { yuan: Math.round(best.value * 100) / 100, raw: best.raw } : null
}

/** 关键词 → 分类(命中即用,多个命中取先出现的规则;都没命中归「其他」) */
const CATEGORY_RULES = [
  ['工资', /工资|薪水|发薪|奖金|报销/],
  ['红包', /红包|份子|礼金/],
  ['餐饮', /饭|早餐|午饭|中饭|晚餐|夜宵|外卖|咖啡|奶茶|饮料|水果|菜|吃|食堂/],
  ['交通', /打车|地铁|公交|加油|停车|高铁|火车|机票|过路费|洗车/],
  ['水电燃气', /水费|电费|燃气|话费|宽带|物业费|取暖/],
  ['医疗', /医院|看病|挂号|药|体检|牙医/],
  ['教育', /学费|培训|课|书本|文具|补习/],
  ['娱乐', /电影|游戏|唱歌|门票|旅游|健身|演出/],
  ['家居', /家具|家电|房租|装修|日用|清洁/],
  ['购物', /买|购|衣服|鞋|包|超市|淘宝|京东|拼多多/],
]

export function guessCategory(text) {
  const s = String(text || '')
  for (const [category, re] of CATEGORY_RULES) {
    if (re.test(s)) return category
  }
  return BOOK_CATEGORIES.includes('其他') ? '其他' : BOOK_CATEGORIES[0]
}

/** 去掉金额与常见的口语前缀,剩下的当备注 */
function extractNote(text, rawAmount) {
  let s = normalize(text)
  if (rawAmount) s = s.replace(rawAmount, ' ')
  s = s
    .replace(/^(帮我|给我|请|麻烦)?\s*(记一笔|记一下|记账|记录一下|记上)/, ' ')
    .replace(/(一共|总共|花了|花掉|付了|付|支付|支出|消费|用了|买了|买)/g, ' ')
    .replace(/\s+/g, ' ')
    .replace(/^[\s,.:;!?~。、-]+|[\s,.:;!?~。、-]+$/g, '')
    .trim()
  return s.slice(0, 50)
}

/**
 * 主入口:返回 { type, amount, note, category },解析不到金额返回 null。
 * type 只区分 EXPENSE / INCOME(转账口述不猜,留给用户自己选)。
 */
export function parseVoiceRecord(text) {
  const source = normalize(text)
  if (!source) return null
  const amount = extractAmount(source)
  if (!amount) return null
  const note = extractNote(source, amount.raw)
  const type = /收入|收到|赚了|赚|工资|奖金|报销|退款/.test(source) ? 'INCOME' : 'EXPENSE'
  return {
    type,
    amount: amount.yuan,
    note,
    category: guessCategory(source),
  }
}
