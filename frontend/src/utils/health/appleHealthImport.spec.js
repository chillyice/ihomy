import { describe, expect, it } from 'vitest'
import {
  buildImportPlan,
  buildSamples,
  buildSleepBatches,
  createCollector,
  dayOf,
  feedChunk,
  isoOf,
  parseAppleTs,
  sleepPayload,
  sourceAppOf,
  vendorOf,
} from './appleHealthImport'

const HEAD = '<?xml version="1.0" encoding="UTF-8"?>\n<HealthData locale="zh_CN">\n'
const FOOT = '</HealthData>'

function rec(type, attrs, value) {
  return `<Record type="${type}" ${attrs} value="${value}"/>\n`
}

function collect(xml, sliceSize = 0) {
  const c = createCollector()
  if (!sliceSize) {
    feedChunk(c, xml)
    return c
  }
  for (let i = 0; i < xml.length; i += sliceSize) feedChunk(c, xml.slice(i, i + sliceSize))
  return c
}

const SAMPLE_XML =
  HEAD +
  rec(
    'HKQuantityTypeIdentifierStepCount',
    'sourceName="HUAWEI Health" unit="count" startDate="2024-03-01 08:00:00 +0800" endDate="2024-03-01 09:00:00 +0800"',
    '5000',
  ) +
  rec(
    'HKQuantityTypeIdentifierStepCount',
    'sourceName="HUAWEI Health" unit="count" startDate="2024-03-01 18:00:00 +0800" endDate="2024-03-01 19:00:00 +0800"',
    '6031',
  ) +
  rec(
    'HKQuantityTypeIdentifierStepCount',
    'sourceName="Garmin Connect" unit="count" startDate="2024-03-01 20:00:00 +0800" endDate="2024-03-01 21:00:00 +0800"',
    '11690',
  ) +
  rec(
    'HKQuantityTypeIdentifierActiveEnergyBurned',
    'sourceName="HUAWEI Health" unit="kcal" startDate="2024-03-01 08:00:00 +0800" endDate="2024-03-01 09:00:00 +0800"',
    '450',
  ) +
  rec(
    'HKQuantityTypeIdentifierBasalEnergyBurned',
    'sourceName="HUAWEI Health" unit="kcal" startDate="2024-03-01 00:00:00 +0800" endDate="2024-03-01 23:59:00 +0800"',
    '1400',
  ) +
  rec(
    'HKQuantityTypeIdentifierHeartRate',
    'sourceName="Garmin Connect" startDate="2024-03-01 08:10:00 +0800" endDate="2024-03-01 08:10:00 +0800"',
    '60',
  ) +
  rec(
    'HKQuantityTypeIdentifierHeartRate',
    'sourceName="Garmin Connect" startDate="2024-03-01 08:20:00 +0800" endDate="2024-03-01 08:20:00 +0800"',
    '70',
  ) +
  rec(
    'HKQuantityTypeIdentifierHeartRate',
    'sourceName="HUAWEI Health" startDate="2024-03-01 08:30:00 +0800" endDate="2024-03-01 08:30:00 +0800"',
    '100',
  ) +
  rec(
    'HKQuantityTypeIdentifierRestingHeartRate',
    'sourceName="HUAWEI Health" startDate="2024-03-01 07:00:00 +0800" endDate="2024-03-01 07:00:00 +0800"',
    '58',
  ) +
  rec(
    'HKQuantityTypeIdentifierBodyMass',
    'sourceName="HUAWEI Health" unit="kg" startDate="2024-03-02 07:30:00 +0800" endDate="2024-03-02 07:30:00 +0800"',
    '70.5',
  ) +
  // 没有手表:三项圆环里站立小时为 0,不许写成 0 值样本
  '<ActivitySummary dateComponents="2024-03-01" activeEnergyBurned="504.26" activeEnergyBurnedGoal="500" ' +
  'activeEnergyBurnedUnit="kcal" appleExerciseTime="30" appleExerciseTimeGoal="30" ' +
  'appleStandHours="0" appleStandHoursGoal="12"/>\n' +
  rec(
    'HKCategoryTypeIdentifierSleepAnalysis',
    'sourceName="Apple Watch" startDate="2024-03-01 01:00:00 +0800" endDate="2024-03-01 02:30:00 +0800"',
    'HKCategoryValueSleepAnalysisAsleepCore',
  ) +
  rec(
    'HKCategoryTypeIdentifierSleepAnalysis',
    'sourceName="Apple Watch" startDate="2024-03-01 02:30:00 +0800" endDate="2024-03-01 03:00:00 +0800"',
    'HKCategoryValueSleepAnalysisAsleepDeep',
  ) +
  FOOT

describe('parseAppleTs', () => {
  it('保留设备当地墙钟与偏移,不转成本机时区', () => {
    const ts = parseAppleTs('2024-03-01 08:05:06 +0800')
    expect(isoOf(ts)).toBe('2024-03-01T08:05:06+08:00')
    expect(dayOf(ts)).toBe('2024-03-01')
    expect(isoOf(parseAppleTs('2024-03-01 00:30:00 -0500'))).toBe('2024-03-01T00:30:00-05:00')
  })

  it('认不出的时间串返回 null', () => {
    expect(parseAppleTs('')).toBeNull()
    expect(parseAppleTs('2024-03-01')).toBeNull()
    expect(parseAppleTs(null)).toBeNull()
  })
})

describe('feedChunk', () => {
  it('标签被块边界劈开也能拼回(逐 3 字符喂入与整块喂入结果一致)', () => {
    const whole = buildSamples(collect(HEAD + SAMPLE_XML.slice(HEAD.length, -FOOT.length) + FOOT))
    const sliced = buildSamples(collect(SAMPLE_XML, 3))
    expect(sliced.stats).toEqual(whole.stats)
    expect(sliced.samples.length).toBe(whole.samples.length)
  })
})

describe('buildSamples', () => {
  const c = collect(SAMPLE_XML)
  const { samples, stats } = buildSamples(c)
  const pick = (metric) => samples.filter((s) => s.metric === metric)

  it('步数按天取求和最大的来源,不把多设备相加', () => {
    const steps = pick('STEPS')
    expect(steps).toHaveLength(1)
    // 华为 5000+6031=11031 与 Garmin 11690 重叠,相加会虚高近一倍
    expect(steps[0].value).toBe(11690)
    expect(steps[0].date).toBe('2024-03-01')
    expect(steps[0].period).toBe('DAY')
  })

  it('心率按小时只留样本最多的来源', () => {
    const hr = pick('HEART_RATE')
    expect(hr).toHaveLength(1)
    expect(hr[0].source).toBe('GARMIN')
    expect(hr[0].value).toBe(65)
    expect(hr[0].min).toBe(60)
    expect(hr[0].max).toBe(70)
    expect(hr[0].count).toBe(2)
    expect(hr[0].start).toBe('2024-03-01T08:00:00+08:00')
    expect(hr[0].end).toBe('2024-03-01T09:00:00+08:00')
  })

  it('活动消耗优先用圆环,总消耗=圆环活动+基础消耗', () => {
    expect(pick('ACTIVE_ENERGY')[0].value).toBe(504.26)
    expect(pick('TOTAL_ENERGY')[0].value).toBe(1904.26)
  })

  it('只有圆环没有原始样本的日子也用圆环补活动消耗与总消耗', () => {
    // 第三方 App 直接写圆环、不写 ActiveEnergyBurned 明细:不能漏掉这一天
    const ringOnly = collect(
      HEAD +
        rec(
          'HKQuantityTypeIdentifierBasalEnergyBurned',
          'sourceName="HUAWEI Health" unit="kcal" startDate="2024-03-05 00:00:00 +0800" endDate="2024-03-05 23:59:00 +0800"',
          '1500',
        ) +
        '<ActivitySummary dateComponents="2024-03-05" activeEnergyBurned="300" activeEnergyBurnedGoal="500"/>\n' +
        FOOT,
    )
    const out = buildSamples(ringOnly).samples
    const active = out.filter((s) => s.metric === 'ACTIVE_ENERGY')
    const total = out.filter((s) => s.metric === 'TOTAL_ENERGY')
    expect(active).toHaveLength(1)
    expect(active[0].value).toBe(300)
    expect(active[0].date).toBe('2024-03-05')
    expect(total).toHaveLength(1)
    expect(total[0].value).toBe(1800)
  })

  it('站立小时为 0 不写样本,锻炼分钟用圆环', () => {
    expect(pick('STAND_HOUR')).toHaveLength(0)
    expect(pick('EXERCISE_MIN')[0].value).toBe(30)
  })

  it('静息心率一天一条,体重按 POINT 带时刻上报', () => {
    expect(pick('RESTING_HR')[0].value).toBe(58)
    const weight = pick('WEIGHT')[0]
    expect(weight.period).toBe('POINT')
    expect(weight.at).toBe('2024-03-02T07:30:00+08:00')
    expect(weight.value).toBe(70.5)
  })

  it('睡眠区间原样保留分期,AsleepCore 落 LIGHT', () => {
    const payload = sleepPayload(c.sleep)
    expect(payload).toHaveLength(2)
    expect(payload.map((p) => p.stage)).toEqual(['LIGHT', 'DEEP'])
    expect(payload[0]).toMatchObject({
      start: '2024-03-01T01:00:00+08:00',
      end: '2024-03-01T02:30:00+08:00',
      sourceApp: 'Apple Watch',
      source: 'APPLE',
    })
    expect(stats.SLEEP).toBeUndefined()
  })

  it('没有活动圆环的日子用训练时长兜底锻炼分钟', () => {
    const doc =
      HEAD +
      '<Workout workoutActivityType="HKWorkoutActivityTypeRunning" duration="45" durationUnit="min" ' +
      'startDate="2024-04-01 07:00:00 +0800" endDate="2024-04-01 07:45:00 +0800"/>\n' +
      FOOT
    const built = buildSamples(collect(doc))
    expect(built.samples).toHaveLength(1)
    expect(built.samples[0]).toMatchObject({ metric: 'EXERCISE_MIN', value: 45, date: '2024-04-01' })
  })
})

describe('睡眠分段', () => {
  const doc =
    HEAD +
    rec(
      'HKCategoryTypeIdentifierSleepAnalysis',
      'sourceName="Apple Watch" startDate="2024-01-05 01:00:00 +0800" endDate="2024-01-05 02:00:00 +0800"',
      'HKCategoryValueSleepAnalysisAsleepCore',
    ) +
    rec(
      'HKCategoryTypeIdentifierSleepAnalysis',
      'sourceName="Apple Watch" startDate="2024-02-04 06:00:00 +0800" endDate="2024-02-04 07:00:00 +0800"',
      'HKCategoryValueSleepAnalysisAsleepCore',
    ) +
    rec(
      'HKCategoryTypeIdentifierSleepAnalysis',
      'sourceName="Apple Watch" startDate="2024-03-05 01:00:00 +0800" endDate="2024-03-05 02:00:00 +0800"',
      'HKCategoryValueSleepAnalysisAsleepCore',
    ) +
    FOOT

  it('按 30 天正午切段,切口附近向前多带 12 小时(重叠交给服务端整窗重算)', () => {
    const batches = buildSleepBatches(collect(doc))
    expect(batches.length).toBeGreaterThanOrEqual(2)
    // 第一段以 01-05 正午为锚:窗口从 01-05 00:00 起,2-04 06:00 属于边界前 12 小时,两段都带
    const inFirst = batches[0].length
    expect(inFirst).toBe(2)
    expect(batches[1].some((it) => dayOf(it.start) === '2024-02-04')).toBe(true)
    // 每段跨度不超过 31 天(服务端上限)
    for (const b of batches) {
      const span = Math.max(...b.map((it) => it.end.wallMs)) - Math.min(...b.map((it) => it.start.wallMs))
      expect(span).toBeLessThan(31 * 24 * 3600 * 1000)
    }
  })
})

describe('buildImportPlan', () => {
  it('切成 ≤ 上限的请求批次,并给出数据日期范围', () => {
    const plan = buildImportPlan(collect(SAMPLE_XML), { batchSize: 4 })
    expect(plan).not.toBeNull()
    expect(plan.sampleRows).toBe(7)
    expect(plan.sleepRows).toBe(2)
    expect(plan.sampleBatches.map((b) => b.length)).toEqual([4, 3])
    expect(plan.sleepBatches).toHaveLength(1)
    expect(plan.requests).toBe(3)
    expect(plan.from).toBe('2024-03-01')
    expect(plan.to).toBe('2024-03-02')
  })

  it('不是导出包(没有任何可认的记录)返回 null', () => {
    expect(buildImportPlan(collect(HEAD + '<Me HKCharacteristicTypeIdentifierDateOfBirth="1990-01-01"/>\n' + FOOT))).toBeNull()
  })
})

describe('来源命名', () => {
  it('Apple 自家的数据留 APPLE_HEALTH,设备厂商数据按设备名归档', () => {
    expect(vendorOf('HUAWEI Health')).toBe('HUAWEI')
    expect(vendorOf('Garmin Connect')).toBe('GARMIN')
    expect(vendorOf('某某手环')).toBe('OTHER')
    expect(sourceAppOf('健康')).toBe('APPLE_HEALTH')
    expect(sourceAppOf('Apple Watch')).toBe('Apple Watch')
  })
})
