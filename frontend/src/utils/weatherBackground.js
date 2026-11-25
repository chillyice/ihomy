import { resolveMorningWeather } from './morningWeather'

const CONFIG_KEY = 'ihomy:weather-bg-config:v1'
const DEFAULTS = { enabled: true, style: 'photo', size: '2048x2048', refreshDays: 7, scene: '', watermark: false }

const styles = {
  photo:
    '写实风景摄影。平视取景，接近人眼的透视；保留石材、泥土和叶片的细微纹理。色彩接近现场，亮部有层次，暗部保留细节，避免过度锐化和夸张 HDR。',
  film: '彩色胶片摄影。高光柔和过渡，阴影带少量细颗粒，以克制的暖冷对照表现空气和距离，底片边缘裁出画面。',
  watercolor: '透明水彩写生。远景用湿画法晕开，近景以干笔收住边缘，用纸白保留高光；让颜料的沉淀和水痕自然可见。',
  oil: '写生油画。用冷暖色块组织光影，近景用短而有方向的厚笔触，远景薄涂，让空气感拉开距离。',
  illustration: '柔和的手绘插画。铅笔轮廓轻重有别，哑光色块叠出体积，保留纸张肌理，让环境光统一整幅画。',
  minimal: '极简平面插画。以少量轮廓概括近、中、远景，用三到五种协调的颜色区分空间，形状边缘干净，保留大片安静色面。',
}
export const weatherStyleIds = Object.keys(styles)
const legacyStyles = {
  写实摄影质感: 'photo',
  水彩手绘风: 'watercolor',
  油画细腻笔触: 'oil',
  温柔插画风格: 'illustration',
  极简扁平插画: 'minimal',
}

function normalizeConfig(raw) {
  let style = raw?.style
  if (!Object.hasOwn(styles, style)) style = Object.hasOwn(legacyStyles, style) ? legacyStyles[style] : DEFAULTS.style
  return {
    ...DEFAULTS,
    ...raw,
    style,
    scene: typeof raw?.scene === 'string' ? raw.scene.trim().slice(0, 200) : '',
  }
}

export function readWeatherBgConfig() {
  try {
    return normalizeConfig(JSON.parse(localStorage.getItem(CONFIG_KEY)))
  } catch {
    return { ...DEFAULTS }
  }
}

export function saveWeatherBgConfig(config) {
  localStorage.setItem(CONFIG_KEY, JSON.stringify(normalizeConfig(config)))
}

const atmosphere = {
  clear: '空气通透，远处轮廓清晰，受光面与背光面分明。',
  cloud: '层次不同的云团之间透出天空，地面明暗随云隙变化。',
  overcast: '连绵云层遮住天空，漫射光包裹景物，地面几乎没有硬阴影。',
  rain: '用水洼上交错的涟漪、石面深浅不同的湿痕和远处渐淡的轮廓表现降雨。',
  thunder: '积雨云压低天际，阵风吹动树冠，雨幕从远景掠过，保留云底的明暗层次。',
  snow: '少量近处雪花清晰可辨，远处融入空气；积雪顺着枝条与地面的起伏堆叠。',
  fog: '空气中的雾逐层吞没景物，近处仍有清晰纹理，远处轮廓与天空融为一体。',
  dust: '悬浮尘粒使远景发灰、反差减弱，天际线隐约透出，近处轮廓仍然可辨。',
  wind: '从枝条的倾斜和草叶的朝向表现风势，近景保留迎风与背风处的差别。',
  sleet: '湿雪与雨滴一同落下，路面留下薄薄的融雪，枝头的雪很快化作水珠。',
}

const scenes = [
  '从一条小径向远处望去，树丛旁露出几处屋顶，前景只取少量枝叶作为尺度参照',
  '从岸边望向河湾，一段石阶斜入水中，远处的树线把天空和倒影分开',
  '从低矮的院墙向外望去，近处是墙顶的粗粝石面，枝叶后露出错落的屋脊',
  '沿一片草地望向林缘，近处草叶有清晰纹理，林间的空隙让视线穿到远景',
]

function lightAt(hour) {
  if (hour < 6 || hour >= 19) return '夜间，保留夜色和局部微光'
  if (hour < 9) return '清晨，光线从较低处入景'
  if (hour >= 16) return '傍晚，环境光逐渐转弱'
  return '白天，按云量控制光照强弱'
}

export function weatherArtwork(weather, config, now = new Date()) {
  const { style, scene, size, watermark } = config
  const hour = now.getHours()
  const night = hour < 6 || hour >= 19
  const southern = Number(weather.locationId?.split(',')[1]) < 0
  const season = ['冬', '春', '夏', '秋'][Math.floor(((now.getMonth() + (southern ? 7 : 1)) % 12) / 3)]
  let condition = resolveMorningWeather(weather)
  if (/雨夹雪|雨雪/.test(weather.text)) condition = 'sleet'
  else if (/霾|尘|沙/.test(weather.text)) condition = 'dust'
  else if (Number(weather.iconCode) >= 200 && Number(weather.iconCode) < 300) condition = 'wind'
  let precipitation = ''
  if (['rain', 'snow', 'thunder', 'sleet'].includes(condition)) {
    if (Number(weather.precipLevel) >= 3) precipitation = '降水密集成幕，远景能见度明显下降。'
    else if (Number(weather.precipLevel) === 1) precipitation = '降水稀疏轻细，远景依然可辨。'
  }
  const setting = scene || scenes[Math.floor(Math.random() * scenes.length)]
  const prompt = [
    '生成一幅铺满画面的户外风景，像从住处向外看见的一个瞬间。',
    styles[style],
    `取景意向：${setting}。画面主体放在中部偏右，左侧用自然延续的低反差景物收住，裁成宽幅时仍能看清主体。`,
    `${season}季的${lightAt(hour)}。${atmosphere[condition]}${precipitation}天空、地面和远景共用一致的光源与能见度。`,
    '整幅画只呈现景物。避开招牌、广告牌和带字物品。画面任何位置都不要出现文字、字母、数字、城市名、天气名称、温度、日期、题款、签名、标志或天气图标；不要制作海报、预报卡片、边框或界面叠层。',
  ].join('\n')
  // 昼夜、季节保留原有粒度，小时和日期不触发额外生成。城市只用于缓存和相册。
  const key = [
    weather.city,
    weather.text,
    weather.iconCode,
    night ? 'night' : 'day',
    season,
    JSON.stringify([condition, style, scene, size, watermark]),
  ].join('|')
  return { prompt, key }
}
