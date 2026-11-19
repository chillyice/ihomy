/**
 * Apple 健康导出包(导出.zip / export.xml)的浏览器端解析与归一。
 *
 * 为什么放在前端:导入是「选一个我手机导出的文件」这种一次性动作,不该要求用户去服务器
 * 跑脚本;解析只依赖浏览器 File 流,几十万条记录也在内存里只留聚合中间态。
 *
 * 归一规则与 scripts/health-import/apple_health_import.py 完全一致(那份脚本是同一套规则的
 * 命令行版),要点:
 *  - 心率逐点 → 按「小时 × 来源」聚合,每小时只留样本最多的那个来源(多设备重叠不能相加)
 *  - 步数/距离/爬楼层 → 按「天 × 来源」求和,取当天求和最大的来源
 *  - 活动消耗/锻炼/站立 → 优先取 ActivitySummary 的活动圆环(Apple 已按来源优先级去重)
 *  - 体重/身高 → POINT 原样上报,坏值交给服务端合理范围挡掉
 *  - 睡眠 → 分期区间原样上报,会话由服务端聚合(三端同口径)
 */

// 数据源通道:与服务端 provider 常量对齐,决定「清除某来源的数据」清得掉谁
export const IMPORT_PROVIDER = 'SHORTCUT'
// 单请求样本上限是服务端 2000;取 1000 是为了进度条动得更细,失败重试代价更小
export const IMPORT_BATCH_SIZE = 1000

const HOUR_MS = 3600 * 1000
const DAY_MS = 24 * HOUR_MS

const HR_TYPES = {
  HKQuantityTypeIdentifierHeartRate: 'HEART_RATE',
  HKQuantityTypeIdentifierRestingHeartRate: 'RESTING_HR',
}

const DAY_SUM_TYPES = {
  HKQuantityTypeIdentifierStepCount: 'STEPS',
  HKQuantityTypeIdentifierDistanceWalkingRunning: 'DISTANCE',
  HKQuantityTypeIdentifierFlightsClimbed: 'FLIGHTS',
  HKQuantityTypeIdentifierBasalEnergyBurned: 'BASAL_ENERGY',
  HKQuantityTypeIdentifierActiveEnergyBurned: 'ACTIVE_ENERGY',
}

const POINT_TYPES = {
  HKQuantityTypeIdentifierBodyMass: 'WEIGHT',
  HKQuantityTypeIdentifierHeight: 'HEIGHT',
}

const SLEEP_TYPE = 'HKCategoryTypeIdentifierSleepAnalysis'

// 未分期的 asleep 落 UNKNOWN:只算在床时长,不冒充浅睡(否则「浅睡」会被撑大)
const SLEEP_STAGES = {
  HKCategoryValueSleepAnalysisAsleepCore: 'LIGHT',
  HKCategoryValueSleepAnalysisAsleepDeep: 'DEEP',
  HKCategoryValueSleepAnalysisAsleepREM: 'REM',
  HKCategoryValueSleepAnalysisAsleepUnspecified: 'UNKNOWN',
  HKCategoryValueSleepAnalysisAsleep: 'UNKNOWN',
  HKCategoryValueSleepAnalysisAwake: 'AWAKE',
  HKCategoryValueSleepAnalysisInBed: 'INBED',
}

// 只有这些 app 名才配得上「来源 App」;其余一律按设备厂商归档
const APPLE_APP_NAMES = new Set(['健康', 'Health'])

const VENDORS = ['HUAWEI', 'GARMIN', 'ZEPP', 'AMAZFIT', 'APPLE', 'XIAOMI', 'OPPO', 'SAMSUNG', 'HONOR', 'VIVO']

/**
 * Apple 时间串 → 墙钟毫秒 + 偏移。带偏移而不是转成本机时区:
 * 导出包里全是设备当地墙钟(如 +0800),按原偏移上报服务端才不会把夜里 1 点挪到前一天。
 */
export function parseAppleTs(raw) {
  if (!raw) return null
  const m = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})\s*([+-])(\d{2}):?(\d{2})$/.exec(String(raw).trim())
  if (!m) return null
  const wallMs = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6])
  const offsetMin = (m[7] === '-' ? -1 : 1) * (+m[8] * 60 + +m[9])
  return { wallMs, offsetMin }
}

const pad2 = (n) => String(n).padStart(2, '0')

/** 墙钟毫秒 + 偏移 → `yyyy-MM-ddTHH:mm:ss±HH:mm`(服务端 OffsetDateTime 口径) */
export function isoOf(ts) {
  const d = new Date(ts.wallMs)
  const abs = Math.abs(ts.offsetMin)
  const sign = ts.offsetMin < 0 ? '-' : '+'
  return (
    `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}` +
    `T${pad2(d.getUTCHours())}:${pad2(d.getUTCMinutes())}:${pad2(d.getUTCSeconds())}` +
    `${sign}${pad2(Math.floor(abs / 60))}:${pad2(abs % 60)}`
  )
}

/** 墙钟日期(YYYY-MM-DD):日聚合按设备当地时间分天 */
export function dayOf(ts) {
  return new Date(ts.wallMs).toISOString().slice(0, 10)
}

export function vendorOf(sourceName) {
  const s = String(sourceName || '').toUpperCase()
  for (const key of VENDORS) if (s.includes(key)) return key
  return 'OTHER'
}

export function sourceAppOf(sourceName) {
  const name = String(sourceName || '').trim()
  if (!name || APPLE_APP_NAMES.has(name)) return 'APPLE_HEALTH'
  return name.slice(0, 120)
}

const ENTITY = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&apos;': "'" }

const unescapeXml = (s) => (s.includes('&') ? s.replace(/&(?:amp|lt|gt|quot|apos);/g, (m) => ENTITY[m]) : s)

function parseAttrs(raw) {
  const out = {}
  const re = /([A-Za-z_][\w.:-]*)="([^"]*)"/g
  let m
  while ((m = re.exec(raw))) out[m[1]] = m[2]
  return out
}

const toNum = (raw) => {
  if (raw == null || raw === '') return null
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}

/**
 * 聚合中间态。只留「按小时/按天/区间」的折叠结果,原始几十万条不落内存。
 */
export function createCollector() {
  return {
    // `${hourMs}` → Map(source → { total, count, min, max })
    hr: new Map(),
    // `${metric}|${day}|${source}` → 和
    daySum: new Map(),
    // day → { ACTIVE_ENERGY, EXERCISE_MIN, STAND_HOUR }
    rings: new Map(),
    points: [],
    sleep: [],
    // [day, minutes]:没有活动圆环时用它兜底锻炼分钟
    workouts: [],
    // 原始条数统计(界面上给用户对账用)
    seen: {},
    tail: '',
  }
}

function bump(map, key, by = 1) {
  map[key] = (map[key] || 0) + by
}

function handleRecord(c, a) {
  const type = unescapeXml(a.type || '')
  const start = parseAppleTs(a.startDate)
  if (!start) return
  bump(c.seen, type)

  if (type === SLEEP_TYPE) {
    const end = parseAppleTs(a.endDate)
    const stage = SLEEP_STAGES[a.value || '']
    if (!end || !stage || String(a.value || '').endsWith('NotSet')) return
    if (end.wallMs <= start.wallMs) return
    c.sleep.push({ start, end, stage, sourceName: unescapeXml(a.sourceName || '') })
    return
  }

  const hrMetric = HR_TYPES[type]
  if (hrMetric) {
    const value = toNum(a.value)
    if (value == null) return
    if (hrMetric === 'HEART_RATE') {
      const hourMs = Math.floor(start.wallMs / HOUR_MS) * HOUR_MS
      let bySource = c.hr.get(hourMs)
      if (!bySource) {
        bySource = new Map()
        c.hr.set(hourMs, bySource)
      }
      const src = unescapeXml(a.sourceName || '')
      const acc = bySource.get(src) || { total: 0, count: 0, min: value, max: value, offsetMin: start.offsetMin }
      acc.total += value
      acc.count += 1
      acc.min = Math.min(acc.min, value)
      acc.max = Math.max(acc.max, value)
      bySource.set(src, acc)
    } else {
      // 静息心率一天一条:同来源多次只留最后一条
      c.daySum.set(`${hrMetric}|${dayOf(start)}|${unescapeXml(a.sourceName || '')}`, value)
    }
    return
  }

  const sumMetric = DAY_SUM_TYPES[type]
  if (sumMetric) {
    const value = toNum(a.value)
    if (value == null) return
    const key = `${sumMetric}|${dayOf(start)}|${unescapeXml(a.sourceName || '')}`
    c.daySum.set(key, (c.daySum.get(key) || 0) + value)
    return
  }

  const pointMetric = POINT_TYPES[type]
  if (pointMetric) {
    const value = toNum(a.value)
    if (value == null) return
    c.points.push({ metric: pointMetric, ts: start, value, sourceName: unescapeXml(a.sourceName || '') })
  }
}

function handleRing(c, a) {
  const day = (a.dateComponents || '').trim()
  if (!/^\d{4}-\d{2}-\d{2}$/.test(day)) return
  bump(c.seen, '<ActivitySummary>')
  const rings = {}
  for (const [attr, key] of [
    ['activeEnergyBurned', 'ACTIVE_ENERGY'],
    ['appleExerciseTime', 'EXERCISE_MIN'],
    ['appleStandHours', 'STAND_HOUR'],
  ]) {
    // 0 一律不记:没戴 Apple Watch 时这三项恒为 0,写进去会让图表画成一条零线
    const value = toNum(a[attr])
    if (value != null && value > 0) rings[key] = value
  }
  if (Object.keys(rings).length) c.rings.set(day, rings)
}

function handleWorkout(c, a) {
  const start = parseAppleTs(a.startDate)
  if (!start) return
  const dur = toNum(a.duration) || 0
  const unit = String(a.durationUnit || 'min').toLowerCase()
  const mins = unit === 'min' ? dur : unit === 'sec' ? dur / 60 : unit === 'hr' ? dur * 60 : dur
  if (mins <= 0) return
  c.workouts.push([dayOf(start), mins])
  bump(c.seen, '<Workout>')
}

function handleTag(c, tag, attrStr) {
  const a = parseAttrs(attrStr)
  if (tag === 'Record') handleRecord(c, a)
  else if (tag === 'ActivitySummary') handleRing(c, a)
  else if (tag === 'Workout') handleWorkout(c, a)
}

const TAG_RE = /<(Record|ActivitySummary|Workout)\s([^>]*)>/g

/**
 * 喂一块文本。块边界可能把一个标签劈成两半,残尾留到下一块拼上。
 * 只认开始标签:Record 可能是自闭合,也可能带 MetadataEntry 子节点,读属性就够。
 */
export function feedChunk(c, text) {
  if (!text) return
  const buf = c.tail ? c.tail + text : text
  TAG_RE.lastIndex = 0
  let last = 0
  let m
  while ((m = TAG_RE.exec(buf))) {
    handleTag(c, m[1], m[2])
    last = TAG_RE.lastIndex
  }
  c.tail = buf.slice(last)
  // 正常残尾是几十字节(半个标签);异常时(文件不是导出包)截断防止无限增长
  if (c.tail.length > 1 << 20) c.tail = c.tail.slice(-4096)
}

/** 按天取「求和最大的来源」:多设备同一天重叠,只信数据最多的那台 */
function bestSourceDay(daySum, metric) {
  const per = new Map()
  const prefix = `${metric}|`
  for (const [key, value] of daySum) {
    if (!key.startsWith(prefix)) continue
    const rest = key.slice(prefix.length)
    const sep = rest.indexOf('|')
    const day = rest.slice(0, sep)
    const src = rest.slice(sep + 1)
    let bySource = per.get(day)
    if (!bySource) {
      bySource = new Map()
      per.set(day, bySource)
    }
    bySource.set(src, (bySource.get(src) || 0) + value)
  }
  const out = new Map()
  for (const [day, bySource] of per) out.set(day, Math.max(...bySource.values()))
  return out
}

const round2 = (n) => Math.round(n * 100) / 100

/** 中间态 → 待上报样本行(规则与命令行脚本同口径) */
export function buildSamples(c) {
  const samples = []
  const stats = {}

  // 心率:每小时只留样本最多的来源,再折成一条小时聚合
  const hours = [...c.hr.keys()].sort((a, b) => a - b)
  for (const hourMs of hours) {
    const bySource = c.hr.get(hourMs)
    let best = null
    let bestSrc = ''
    for (const [src, acc] of bySource) {
      if (!best || acc.count > best.count) {
        best = acc
        bestSrc = src
      }
    }
    if (!best) continue
    // 小时桶沿用这条样本的偏移:导出包是单一偏移时,与按墙钟切小时完全等价
    const startTs = { wallMs: hourMs, offsetMin: best.offsetMin }
    const endTs = { wallMs: hourMs + HOUR_MS, offsetMin: best.offsetMin }
    samples.push({
      metric: 'HEART_RATE',
      period: 'POINT',
      start: isoOf(startTs),
      end: isoOf(endTs),
      value: round2(best.total / best.count),
      min: round2(best.min),
      max: round2(best.max),
      count: best.count,
      source: vendorOf(bestSrc),
      sourceApp: 'APPLE_HEALTH',
    })
    stats.HEART_RATE = (stats.HEART_RATE || 0) + 1
  }

  const daily = {}
  for (const metric of ['STEPS', 'DISTANCE', 'FLIGHTS', 'BASAL_ENERGY', 'ACTIVE_ENERGY']) {
    daily[metric] = bestSourceDay(c.daySum, metric)
  }

  // 活动消耗的日子取「日求和」与「圆环」的并集:有的日子只有第三方 App 写了圆环、没有原始样本
  const ringActiveDays = [...c.rings].filter(([, rings]) => rings.ACTIVE_ENERGY != null).map(([day]) => day)
  const activityDays = {
    STEPS: [],
    DISTANCE: [],
    FLIGHTS: [],
    ACTIVE_ENERGY: [...new Set([...daily.ACTIVE_ENERGY.keys(), ...ringActiveDays])],
  }
  for (const metric of ['STEPS', 'DISTANCE', 'FLIGHTS', 'ACTIVE_ENERGY']) {
    const days = metric === 'ACTIVE_ENERGY' ? activityDays.ACTIVE_ENERGY : [...daily[metric].keys()]
    for (const day of days.sort()) {
      const ring = c.rings.get(day)?.[metric]
      // 活动消耗优先用圆环(Apple 已按来源优先级去重),其余用日求和
      const value = metric === 'ACTIVE_ENERGY' && ring != null ? ring : daily[metric].get(day)
      if (value == null) continue
      samples.push({ metric, period: 'DAY', date: day, value: round2(value), source: 'APPLE', sourceApp: 'APPLE_HEALTH' })
      stats[metric] = (stats[metric] || 0) + 1
    }
  }

  // 总消耗 = 活动 + 基础(导出里没有 total,只能相加;两项都按天取单一来源,不叠设备)
  const basal = daily.BASAL_ENERGY
  for (const day of activityDays.ACTIVE_ENERGY.sort()) {
    let active = c.rings.get(day)?.ACTIVE_ENERGY
    if (active == null) active = daily.ACTIVE_ENERGY.get(day)
    if (active == null) continue
    const total = active + (basal.get(day) || 0)
    samples.push({ metric: 'TOTAL_ENERGY', period: 'DAY', date: day, value: round2(total), source: 'APPLE', sourceApp: 'APPLE_HEALTH' })
    stats.TOTAL_ENERGY = (stats.TOTAL_ENERGY || 0) + 1
  }

  // 锻炼分钟 / 站立小时:没有 Apple Watch 时圆环恒为 0,锻炼分钟回落到当天训练时长合计,
  // 站立小时只能留空
  const workoutMin = new Map()
  for (const [day, mins] of c.workouts) workoutMin.set(day, (workoutMin.get(day) || 0) + mins)
  const exerciseDays = new Set([...c.rings.keys(), ...workoutMin.keys()])
  for (const day of [...exerciseDays].sort()) {
    const rings = c.rings.get(day) || {}
    const exercise = rings.EXERCISE_MIN || workoutMin.get(day)
    if (exercise) {
      samples.push({
        metric: 'EXERCISE_MIN',
        period: 'DAY',
        date: day,
        value: round2(exercise),
        source: 'APPLE',
        sourceApp: 'APPLE_HEALTH',
      })
      stats.EXERCISE_MIN = (stats.EXERCISE_MIN || 0) + 1
    }
    if (rings.STAND_HOUR) {
      samples.push({
        metric: 'STAND_HOUR',
        period: 'DAY',
        date: day,
        value: round2(rings.STAND_HOUR),
        source: 'APPLE',
        sourceApp: 'APPLE_HEALTH',
      })
      stats.STAND_HOUR = (stats.STAND_HOUR || 0) + 1
    }
  }

  const resting = new Map()
  for (const [key, value] of c.daySum) {
    if (!key.startsWith('RESTING_HR|')) continue
    const day = key.split('|')[1]
    resting.set(day, Math.max(resting.get(day) || 0, value))
  }
  for (const day of [...resting.keys()].sort()) {
    samples.push({
      metric: 'RESTING_HR',
      period: 'DAY',
      date: day,
      value: round2(resting.get(day)),
      source: 'APPLE',
      sourceApp: 'APPLE_HEALTH',
    })
    stats.RESTING_HR = (stats.RESTING_HR || 0) + 1
  }

  for (const p of c.points) {
    samples.push({
      metric: p.metric,
      period: 'POINT',
      at: isoOf(p.ts),
      value: round2(p.value),
      source: vendorOf(p.sourceName),
      sourceApp: sourceAppOf(p.sourceName),
    })
    stats[p.metric] = (stats[p.metric] || 0) + 1
  }

  return { samples, stats }
}

/**
 * 睡眠按 30 天(正午切)分段并向前多带 12 小时:切口落在白天,
 * 相邻窗口重叠的那段靠服务端「整窗重算」去重,不会把一晚切成两半。
 */
export function buildSleepBatches(c) {
  const intervals = c.sleep.slice().sort((a, b) => a.start.wallMs - b.start.wallMs)
  if (!intervals.length) return []
  const batches = []
  const noon = (ts) => {
    const d = new Date(ts.wallMs)
    d.setUTCHours(12, 0, 0, 0)
    return { wallMs: d.getTime(), offsetMin: ts.offsetMin }
  }
  let cursor = noon(intervals[0].start)
  const lastStart = intervals[intervals.length - 1].start.wallMs
  for (;;) {
    const from = cursor.wallMs - 12 * HOUR_MS
    const to = cursor.wallMs + 30 * DAY_MS
    const batch = intervals.filter((it) => it.start.wallMs >= from && it.start.wallMs < to)
    if (batch.length) batches.push(batch)
    if (cursor.wallMs >= lastStart) break
    cursor = { wallMs: cursor.wallMs + 30 * DAY_MS, offsetMin: cursor.offsetMin }
  }
  return batches
}

export function sleepPayload(batch) {
  return batch.map((it) => ({
    start: isoOf(it.start),
    end: isoOf(it.end),
    stage: it.stage,
    source: vendorOf(it.sourceName),
    sourceApp: sourceAppOf(it.sourceName),
  }))
}

/**
 * 中间态 → 可发送的请求批次。样本在前、睡眠在后(与命令行脚本一致)。
 * 返回 null 表示这份文件里没有可导入的记录(选错文件 / 不是导出包)。
 */
export function buildImportPlan(c, { batchSize = IMPORT_BATCH_SIZE } = {}) {
  const { samples, stats } = buildSamples(c)
  const sleepBatches = buildSleepBatches(c)
  if (!samples.length && !sleepBatches.length) return null

  const sampleBatches = []
  for (let i = 0; i < samples.length; i += batchSize) sampleBatches.push(samples.slice(i, i + batchSize))

  let from = ''
  let to = ''
  const touch = (day) => {
    if (!day) return
    if (!from || day < from) from = day
    if (!to || day > to) to = day
  }
  for (const s of samples) touch(s.date || (s.at || '').slice(0, 10))
  for (const it of c.sleep) touch(dayOf(it.start))

  return {
    sampleBatches,
    sleepBatches,
    stats,
    sampleRows: samples.length,
    sleepRows: c.sleep.length,
    requests: sampleBatches.length + sleepBatches.length,
    from,
    to,
  }
}

/** 原始记录条数排序后的 [名称, 条数] 列表(界面「解析结果」用) */
export function seenList(c) {
  return Object.entries(c.seen).sort((a, b) => b[1] - a[1])
}

// ---------------- 文件读取:export.xml 或导出包 zip ----------------

async function* readPlain(file, onProgress) {
  const total = file.size || 0
  const reader = file.stream().getReader()
  let read = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) return
    read += value.byteLength || 0
    if (onProgress && total) onProgress(read / total)
    yield value
  }
}

function u16(view, at) {
  return view.getUint16(at, true)
}
function u32(view, at) {
  return view.getUint32(at, true)
}

/**
 * 从 zip 里挑导出包那份 XML(通常是 导出.xml / export.xml,zip 里最大的那个)。
 * 只支持 stored / deflate 且非 zip64 —— Apple 导出包就是这种,别的直接报错让用户先解压。
 */
async function pickZipEntry(file) {
  const tailLen = Math.min(file.size, 66 * 1024)
  const tailAt = file.size - tailLen
  const tail = new DataView(await file.slice(tailAt).arrayBuffer())
  let eocd = -1
  for (let i = tail.byteLength - 22; i >= 0; i -= 1) {
    if (u32(tail, i) === 0x06054b50) {
      eocd = i
      break
    }
  }
  if (eocd < 0) throw new Error('ZIP_NO_EOCD')
  const entries = u16(tail, eocd + 10)
  const cdSize = u32(tail, eocd + 12)
  const cdOffset = u32(tail, eocd + 16)
  if (cdOffset === 0xffffffff || cdSize === 0xffffffff) throw new Error('ZIP_NO_EOCD')

  const cd = new DataView(await file.slice(cdOffset, cdOffset + cdSize).arrayBuffer())
  let at = 0
  let best = null
  for (let i = 0; i < entries && at + 46 <= cd.byteLength; i += 1) {
    if (u32(cd, at) !== 0x02014b50) break
    const method = u16(cd, at + 10)
    const compSize = u32(cd, at + 20)
    const rawSize = u32(cd, at + 24)
    const nameLen = u16(cd, at + 28)
    const extraLen = u16(cd, at + 30)
    const commentLen = u16(cd, at + 32)
    const localAt = u32(cd, at + 42)
    const name = new TextDecoder('utf-8').decode(new Uint8Array(cd.buffer, cd.byteOffset + at + 46, nameLen))
    if (/\.xml$/i.test(name) && !/cda/i.test(name) && (!best || rawSize > best.rawSize)) {
      best = { name, method, compSize, rawSize, localAt }
    }
    at += 46 + nameLen + extraLen + commentLen
  }
  if (!best) throw new Error('ZIP_NO_XML')
  if (best.method !== 0 && best.method !== 8) throw new Error('ZIP_UNSUPPORTED')
  return best
}

async function* readZip(file, onProgress) {
  const entry = await pickZipEntry(file)
  const head = new DataView(await file.slice(entry.localAt, entry.localAt + 30).arrayBuffer())
  if (u32(head, 0) !== 0x04034b50) throw new Error('ZIP_UNSUPPORTED')
  const dataAt = entry.localAt + 30 + u16(head, 26) + u16(head, 28)
  let body = file.slice(dataAt, dataAt + entry.compSize).stream()
  if (entry.method === 8) {
    if (typeof DecompressionStream === 'undefined') throw new Error('ZIP_NO_INFLATE')
    body = body.pipeThrough(new DecompressionStream('deflate-raw'))
  }
  let done = 0
  const reader = body.getReader()
  for (;;) {
    const { done: end, value } = await reader.read()
    if (end) return
    done += value.byteLength || 0
    if (onProgress && entry.rawSize) onProgress(Math.min(1, done / entry.rawSize))
    yield value
  }
}

/**
 * 逐块吐文本。zip 自动找里面那份导出 XML;两者都不行时抛错,由界面翻成可读文案。
 * onProgress 收到 0~1 的读取进度(解码期间涨到 1,归一在读完后再算)。
 */
export async function* streamExportText(file, { onProgress } = {}) {
  const bytes = /\.zip$/i.test(file.name || '') ? readZip(file, onProgress) : readPlain(file, onProgress)
  const decoder = new TextDecoder('utf-8')
  for (;;) {
    const { done, value } = await bytes.next()
    if (done) break
    const text = decoder.decode(value, { stream: true })
    if (text) yield text
  }
  const rest = decoder.decode()
  if (rest) yield rest
}
