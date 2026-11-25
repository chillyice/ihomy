import { resolveMorningWeather } from './morningWeather'

const CONFIG_KEY = 'ihomy:weather-bg-config:v1'
const LAST_SCENE_KEY = 'ihomy:weather-bg:last-auto-scene'
const DEFAULTS = { enabled: true, style: 'photo', size: '2048x2048', refreshDays: 7, sceneTheme: 'auto', scene: '', watermark: false }

const styles = {
  photo:
    '写实风景摄影。保留取景位置应有的透视和尺度；让画面中不同材质各有可信的纹理和反光。色彩接近现场，亮部有层次，暗部保留细节，避免过度锐化和夸张 HDR。',
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
  const scene = typeof raw?.scene === 'string' ? raw.scene.trim().slice(0, 200) : ''
  const legacyTheme = scene ? 'custom' : 'auto'
  const sceneTheme = weatherSceneThemes.includes(raw?.sceneTheme) ? raw.sceneTheme : legacyTheme
  return {
    ...DEFAULTS,
    ...raw,
    style,
    sceneTheme,
    scene,
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
  rain: '连续的云层遮住日轮，湿润表面映出柔和天光。用落在水面的涟漪或沿物体边缘滴落的雨珠表现正在下雨。',
  thunder: '积雨云压低天际，阵风将雨幕吹斜，远处的轮廓时隐时现，保留云底的明暗层次。',
  snow: '少量近处雪花清晰可辨，远处融入空气；积雪沿着朝上的表面堆叠，背风处略厚。',
  fog: '空气中的雾逐层吞没景物，近处仍有清晰纹理，远处轮廓与天空融为一体。',
  dust: '悬浮尘粒使远景发灰、反差减弱，天际线隐约透出，近处轮廓仍然可辨。',
  wind: '用现场能被风带动的细节表现一致的风向，区分迎风处与遮蔽处。',
  sleet: '湿雪与雨滴一同落下，近处的表面留下薄薄的融雪，边缘化作水珠。',
}

const scenes = {
  city: [
    '站在城市人行天桥转角，越过近处的金属扶手俯看弯曲的高架，远处办公楼的玻璃映着天色',
    '从住宅楼的屋顶平台平望，近处水箱的圆弧切入一角，中景阳台层层错开，远景是城市天际线',
    '从临街骑楼的柱间望出去，路口的电车从画面右侧转弯，轨道把视线带进街区深处',
  ],
  coast: [
    '从退潮后的礁盘低处望向海面，岩缝里的潮池映着天空，远处岬角把海平线轻轻截断',
    '沿海港防波堤向外看，前景缆绳绕在系船柱上，堤外的小船随涌浪起伏',
    '站在沿海沙丘的肩部侧望，弧形浪线绕过浅色沙滩，画面右侧露出一处裸岩',
  ],
  park: [
    '从公园温室外的花圃旁取景，弧形玻璃顶在树冠间露出，近处草叶把视线引向入口',
    '从公园廊桥的开口平望湖心岛，桥墩倒影和岸边步道在中景交会',
    '站在公园草坡上向下看，弯曲的慢跑道绕过几张长椅，远处树阵疏密有致',
  ],
  plaza: [
    '从开阔广场的一角望向浅水池，铺地接缝延伸到远处柱廊，几位行人的尺度衬出空间的宽阔',
    '从市民广场的宽台阶向下俯看，一座弧形石雕偏在右侧，中央保留开阔的铺装面',
    '从剧院广场的檐下侧望，拱形门廊沿一侧后退，中央的圆形铺地图案呼应建筑的弧线',
  ],
  cyber: [
    '站在架空维修栈桥上，右侧的检修机械臂悬停在打开的涡轮旁；桥下的冷却管线伸向露天工业城，接头、尺度和材质可信',
    '从近未来港区的装卸平台低处仰看，两台无人吊机在右侧高处交错，导轨消失在远方，少量琥珀色指示灯只有光点',
    '沿高架磁轨看向机械城区，前景是磨损的合金护板，中景为层叠连廊和散热鳍片，灯光通过窄缝透出，远端露出天空',
  ],
  countryside: [
    '越过矮石墙望向田野，作物垄线拐入远处谷地，几间农舍落在坡脚',
    '从村口石桥侧望，近岸芦苇让出河湾，远处的瓦屋依着水面错落排开',
    '沿梯田的田埂横看山坡，一层层田面在山坳汇合，近景留下泥土与草根的细节',
  ],
  mountain: [
    '从山脊步道侧望，右侧一块裸岩压住近景，数道山梁依次退向天际',
    '从狭长峡谷的溪边仰看，两侧岩壁夹出一线天空，溪流在近处绕过大块砾石',
    '站在高山湖的碎石岸边向对岸望去，山体倒映在水面，前景只留一小段岸线',
  ],
}
const themeIds = Object.keys(scenes)
export const weatherSceneThemes = ['auto', ...themeIds, 'custom']
const pick = (items) => items[Math.floor(Math.random() * items.length)]

function nextAutoTheme() {
  try {
    const last = themeIds.indexOf(localStorage.getItem(LAST_SCENE_KEY))
    if (last >= 0) return themeIds[(last + 1) % themeIds.length]
  } catch {}
  return pick(themeIds)
}

export function rememberAutoTheme(theme) {
  if (!theme) return
  try {
    localStorage.setItem(LAST_SCENE_KEY, theme)
  } catch {}
}

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
  const fixedTheme = Object.hasOwn(scenes, config.sceneTheme) ? config.sceneTheme : null
  const customScene = config.sceneTheme === 'custom' || config.sceneTheme == null ? scene : ''
  const autoTheme = fixedTheme || customScene ? null : nextAutoTheme()
  const setting = customScene || pick(scenes[fixedTheme || autoTheme])
  const prompt = [
    '生成一幅铺满画面的户外环境景象，像在现场亲眼看见的一个瞬间。',
    styles[style],
    `取景意向：${setting}。画面主体放在中部偏右，左侧用自然延续的低反差景物收住，裁成宽幅时仍能看清主体。`,
    `${season}季的${lightAt(hour)}。${atmosphere[condition]}${precipitation}天空、地面和远景共用一致的光源与能见度。`,
    '整幅画只呈现景物。避开招牌、广告牌和带字物品。画面任何位置都不要出现文字、字母、数字、城市名、天气名称、温度、日期、题款、签名、标志或天气图标；不要制作海报、预报卡片、边框或界面叠层。',
  ].join('\n')
  // 昼夜、季节保留原有粒度，小时和日期不触发额外生成。城市只用于缓存和相册。
  // 旧的自动与自定义图片仍可命中；只有主动选定的主题才进入缓存键。
  const settings = [condition, style, customScene || '', size, watermark]
  if (fixedTheme) settings.push(fixedTheme)
  const key = [weather.city, weather.text, weather.iconCode, night ? 'night' : 'day', season, JSON.stringify(settings)].join('|')
  return { prompt, key, autoTheme }
}
