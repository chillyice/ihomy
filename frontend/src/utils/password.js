// 密码工具:强度评估 + 随机生成。纯前端计算,不经后端、不落库(明文只在编辑弹窗内存在)。
// 生成走 crypto.getRandomValues(拒绝采样消除取模偏置),非 Math.random。

const LOWER = 'abcdefghijklmnopqrstuvwxyz'
const UPPER = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
const DIGIT = '0123456789'
const SYMBOL = '!@#$%^&*()-_=+[]{};:,.?/'

/** 易混淆字符(0/O、1/l/I),勾选后从字符集中剔除,便于手抄 */
const AMBIGUOUS = /[0O1lI]/g

/** 常见弱密码前缀,命中直接判最弱(长度再长也不加分) */
const COMMON = ['password', '123456', 'qwerty', 'abc123', 'admin', 'iloveyou', '888888', '111111']

/** [min, max) 区间随机整数,拒绝采样避免取模偏置 */
const randInt = (max) => {
  if (max <= 0) return 0
  const limit = Math.floor(0xffffffff / max) * max
  const buf = new Uint32Array(1)
  let v
  do {
    crypto.getRandomValues(buf)
    v = buf[0]
  } while (v >= limit)
  return v % max
}

/**
 * 生成随机密码。
 * @param {object} opts length 长度 / upper 大写 / lower 小写 / digit 数字 / symbol 符号
 *                      / noAmbiguous 剔除易混淆字符
 * @returns {string} 未勾选任何字符集时回退小写字母
 */
export const generatePassword = (opts = {}) => {
  const { length = 16, upper = true, lower = true, digit = true, symbol = true, noAmbiguous = false } = opts
  const strip = (s) => (noAmbiguous ? s.replace(AMBIGUOUS, '') : s)
  const pools = []
  if (lower) pools.push(strip(LOWER))
  if (upper) pools.push(strip(UPPER))
  if (digit) pools.push(strip(DIGIT))
  if (symbol) pools.push(strip(SYMBOL))
  const usable = pools.filter((p) => p.length)
  if (!usable.length) return ''
  const all = usable.join('')
  const size = Math.max(4, Math.min(64, Number(length) || 16))
  const chars = []
  // 先每类各取一个,保证勾选的字符集都出现;再补齐剩余位数并打乱
  usable.forEach((pool) => chars.push(pool[randInt(pool.length)]))
  while (chars.length < size) chars.push(all[randInt(all.length)])
  for (let i = chars.length - 1; i > 0; i--) {
    const j = randInt(i + 1)
    ;[chars[i], chars[j]] = [chars[j], chars[i]]
  }
  return chars.join('')
}

/**
 * 密码强度评估。
 * @returns {{score:number, label:string, hints:string[]}} score 0~4(0 最弱),label 走 i18n:password.strength.<score>
 */
export const passwordStrength = (pw = '') => {
  const pwd = String(pw || '')
  const hints = []
  if (!pwd) return { score: 0, labelKey: 'empty', hints }
  const lower = pwd.toLowerCase()
  if (pwd.length < 6 || COMMON.some((c) => lower.includes(c))) {
    hints.push('common')
    return { score: 0, labelKey: 'veryWeak', hints }
  }
  let score = 0
  if (pwd.length >= 8) score += 1
  if (pwd.length >= 12) score += 1
  if (pwd.length >= 16) score += 1
  const kinds = [/[a-z]/, /[A-Z]/, /\d/, /[^a-zA-Z\d]/].filter((re) => re.test(pwd)).length
  if (kinds >= 2) score += 1
  if (kinds >= 3) score += 1
  // 单一字符重复(如 aaaaaaaa)与连续序列(如 12345678)明显偏弱,降一档
  if (/^(.)\1+$/.test(pwd) || isSequential(pwd)) score -= 1
  score = Math.max(0, Math.min(4, score))
  if (pwd.length < 12) hints.push('longer')
  if (kinds < 3) hints.push('mix')
  return { score, labelKey: ['veryWeak', 'weak', 'fair', 'good', 'strong'][score], hints }
}

/** 是否为键盘/字母顺序的连续序列(升序或降序,至少 6 位) */
const isSequential = (pwd) => {
  if (pwd.length < 6) return false
  const codes = [...pwd].map((c) => c.charCodeAt(0))
  const up = codes.every((c, i) => i === 0 || c - codes[i - 1] === 1)
  const down = codes.every((c, i) => i === 0 || codes[i - 1] - c === 1)
  return up || down
}
