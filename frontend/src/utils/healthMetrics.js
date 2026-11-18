// 健康指标目录(前端唯一来源):code / 分组 / 周期 / 单位与后端 common/HealthMetric 同口径。
// 指标名走 i18n(health.metrics.*),单位是符号不翻译;新增指标 = 后端加一行 + 字典加一行 + 这里加一行。
// 取值精度也在这里定死,首页卡与 /health 页共用,避免同一指标两处显示不同小数位。

export const HEALTH_METRICS = {
  // 睡眠(会话型,走 sleep 表;单列在这里只为统一名称与单位)
  SLEEP: { group: 'SLEEP', period: 'SESSION', unit: 'min', digits: 0, nameKey: 'health.metrics.sleep' },
  // 活动(按天汇总)
  STEPS: { group: 'ACTIVITY', period: 'DAY', unit: '', digits: 0, nameKey: 'health.metrics.steps' },
  DISTANCE: { group: 'ACTIVITY', period: 'DAY', unit: 'km', digits: 1, nameKey: 'health.metrics.distance' },
  ACTIVE_ENERGY: { group: 'ACTIVITY', period: 'DAY', unit: 'kcal', digits: 0, nameKey: 'health.metrics.activeEnergy' },
  TOTAL_ENERGY: { group: 'ACTIVITY', period: 'DAY', unit: 'kcal', digits: 0, nameKey: 'health.metrics.totalEnergy' },
  FLIGHTS: { group: 'ACTIVITY', period: 'DAY', unit: '', digits: 0, nameKey: 'health.metrics.flights' },
  EXERCISE_MIN: { group: 'ACTIVITY', period: 'DAY', unit: 'min', digits: 0, nameKey: 'health.metrics.exerciseMin' },
  STAND_HOUR: { group: 'ACTIVITY', period: 'DAY', unit: 'h', digits: 1, nameKey: 'health.metrics.standHour' },
  MINDFUL_MIN: { group: 'ACTIVITY', period: 'DAY', unit: 'min', digits: 0, nameKey: 'health.metrics.mindfulMin' },
  // 体征(心率/血氧等是瞬时点,静息心率/HRV 是每天一个)
  HEART_RATE: { group: 'VITAL', period: 'POINT', unit: 'bpm', digits: 0, nameKey: 'health.metrics.heartRate' },
  RESTING_HR: { group: 'VITAL', period: 'DAY', unit: 'bpm', digits: 0, nameKey: 'health.metrics.restingHr' },
  HRV: { group: 'VITAL', period: 'DAY', unit: 'ms', digits: 0, nameKey: 'health.metrics.hrv' },
  SPO2: { group: 'VITAL', period: 'POINT', unit: '%', digits: 0, nameKey: 'health.metrics.spo2' },
  RESP_RATE: { group: 'VITAL', period: 'DAY', unit: '次/分', digits: 1, nameKey: 'health.metrics.respRate' },
  BODY_TEMP: { group: 'VITAL', period: 'POINT', unit: '°C', digits: 1, nameKey: 'health.metrics.bodyTemp' },
  BASAL_TEMP: { group: 'VITAL', period: 'DAY', unit: '°C', digits: 2, nameKey: 'health.metrics.basalTemp' },
  BLOOD_GLUCOSE: { group: 'VITAL', period: 'POINT', unit: 'mmol/L', digits: 1, nameKey: 'health.metrics.bloodGlucose' },
  BP_SYSTOLIC: { group: 'VITAL', period: 'POINT', unit: 'mmHg', digits: 0, nameKey: 'health.metrics.bpSystolic' },
  BP_DIASTOLIC: { group: 'VITAL', period: 'POINT', unit: 'mmHg', digits: 0, nameKey: 'health.metrics.bpDiastolic' },
  // 身体成分
  WEIGHT: { group: 'BODY', period: 'POINT', unit: 'kg', digits: 1, nameKey: 'health.metrics.weight' },
  BODY_FAT: { group: 'BODY', period: 'POINT', unit: '%', digits: 1, nameKey: 'health.metrics.bodyFat' },
  HEIGHT: { group: 'BODY', period: 'POINT', unit: 'cm', digits: 0, nameKey: 'health.metrics.height' },
  WAIST: { group: 'BODY', period: 'POINT', unit: 'cm', digits: 1, nameKey: 'health.metrics.waist' },
  // 体能(后端归 FITNESS,页面上跟体征一起看)
  VO2MAX: { group: 'VITAL', period: 'DAY', unit: 'ml/kg/min', digits: 1, nameKey: 'health.metrics.vo2max' },
}

/** 各 tab 要展示的指标(顺序即卡片顺序);睡眠单独一页,不在此列。
 *  体征页除心率/血氧外还列血糖、收缩压/舒张压、呼吸频率与最大摄氧量(手表有就自动出现,没有就是空态);
 *  活动页补正念与爬楼。新增指标仍按目录加一行,这里只决定挂在哪个 tab。 */
export const TAB_METRICS = {
  ACTIVITY: ['STEPS', 'DISTANCE', 'ACTIVE_ENERGY', 'EXERCISE_MIN', 'MINDFUL_MIN', 'FLIGHTS'],
  VITAL: ['HEART_RATE', 'RESTING_HR', 'HRV', 'SPO2', 'BODY_TEMP', 'BLOOD_GLUCOSE', 'BP_SYSTOLIC', 'BP_DIASTOLIC', 'RESP_RATE', 'VO2MAX'],
  BODY: ['WEIGHT', 'BODY_FAT', 'WAIST'],
}

/** 指标码归一(大小写不敏感),未知返回 null */
export const metricDef = (code) => HEALTH_METRICS[String(code || '').toUpperCase()] || null

export const metricName = (t, code) => {
  const d = metricDef(code)
  return d ? t(d.nameKey) : String(code || '')
}

export const metricUnit = (code) => (metricDef(code) || {}).unit || ''

/** 指标周期:DAY = 每天一个汇总;POINT = 时刻点;SESSION = 睡眠会话 */
export const periodOf = (code) => (metricDef(code) || {}).period || 'DAY'

/** 单指标取值格式化(缺数据返回 —,不补 0) */
export const formatMetric = (code, value) => {
  if (value == null || value === '') return '—'
  const num = Number(value)
  if (!Number.isFinite(num)) return '—'
  return num.toFixed((metricDef(code) || {}).digits ?? 0)
}

/** 带单位的一行文案(单位为空时不加空格后缀) */
export const withUnit = (code, text) => {
  const unit = metricUnit(code)
  if (!unit || text === '—') return text
  return `${text} ${unit}`
}

/** 睡眠分钟 → { h, m }(四舍五入到分钟,不出现 60 分) */
export const sleepHM = (min) => {
  if (min == null || !Number.isFinite(Number(min))) return null
  const total = Math.max(0, Math.round(Number(min)))
  let h = Math.floor(total / 60)
  let m = total % 60
  if (m === 60) {
    h += 1
    m = 0
  }
  return { h, m }
}

/** 睡眠时长文案:≥1 小时走「x 小时 y 分」,否则只写分钟 */
export const sleepText = (t, min) => {
  const hm = sleepHM(min)
  if (!hm) return '—'
  return hm.h ? t('health.hm', { h: hm.h, m: hm.m }) : t('health.m', { n: hm.m })
}

/** 睡眠分期:顺序即 stacked bar 顺序(清醒在顶) + 深色主题下的颜色变量 */
export const SLEEP_STAGES = [
  { code: 'AWAKE', color: 'var(--health-stage-awake)', labelKey: 'health.stage.awake' },
  { code: 'REM', color: 'var(--health-stage-rem)', labelKey: 'health.stage.rem' },
  { code: 'LIGHT', color: 'var(--health-stage-light)', labelKey: 'health.stage.light' },
  { code: 'DEEP', color: 'var(--health-stage-deep)', labelKey: 'health.stage.deep' },
  { code: 'INBED', color: 'var(--health-stage-inbed)', labelKey: 'health.stage.inbed' },
  { code: 'UNKNOWN', color: 'var(--health-stage-unknown)', labelKey: 'health.stage.unknown' },
]

export const stageMeta = (code) => SLEEP_STAGES.find((s) => s.code === String(code || '').toUpperCase()) || null

/** 入睡时刻(ISO/本地时间字符串)→ HH:MM;解析失败返回 — */
export const clockText = (value) => {
  if (!value) return '—'
  const m = String(value).match(/T(\d{2}):(\d{2})/)
  if (m) return `${m[1]}:${m[2]}`
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return '—'
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/** 日期短标签(M/D),图表横轴用 */
export const dayLabel = (date) => {
  if (!date) return ''
  const m = String(date).match(/^\d{4}-(\d{2})-(\d{2})/)
  if (m) return `${Number(m[1])}/${Number(m[2])}`
  const d = new Date(date)
  return Number.isNaN(d.getTime()) ? String(date) : `${d.getMonth() + 1}/${d.getDate()}`
}
