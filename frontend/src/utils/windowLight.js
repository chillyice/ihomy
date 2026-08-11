// 光线与天气:纯前端按当前时辰生成窗外景色,无外部 API
// 输出 CSS 渐变字符串 + 场景标签,供 Home.vue 窗区使用

// 按小时返回窗外光线渐变(明色)
export function getWindowLight() {
  const h = new Date().getHours()
  if (h >= 6 && h < 9) {
    // 晨光:柔暖白
    return { gradient: 'linear-gradient(180deg, #F5E6C8 0%, #E8D8B8 60%, #D4C0A0 100%)', scene: 'dawn' }
  }
  if (h >= 9 && h < 16) {
    // 正午:明亮米
    return { gradient: 'linear-gradient(180deg, #E8E4D0 0%, #D8D0B8 60%, #C9BD9C 100%)', scene: 'noon' }
  }
  if (h >= 16 && h < 19) {
    // 黄昏:暖橙渐变
    return { gradient: 'linear-gradient(180deg, #D4A574 0%, #C08850 50%, #A8483A 100%)', scene: 'dusk' }
  }
  if (h >= 19 && h < 22) {
    // 黄昏尾:深紫到深褐
    return { gradient: 'linear-gradient(180deg, #4A3A4E 0%, #3A2E38 50%, #2A2018 100%)', scene: 'evening' }
  }
  // 夜:深紫到深褐
  return { gradient: 'linear-gradient(180deg, #2A2038 0%, #221A28 50%, #1A1410 100%)', scene: 'night' }
}

// 窗内灯光(夜间亮起)
export function isLampOn() {
  const h = new Date().getHours()
  return h < 6 || h >= 18
}

// 季节(北半球,按月份粗分)
export function getSeason() {
  const m = new Date().getMonth()
  if (m >= 2 && m <= 4) return 'spring'
  if (m >= 5 && m <= 7) return 'summer'
  if (m >= 8 && m <= 10) return 'autumn'
  return 'winter'
}

// 窗外装饰元素(根据时辰+季节返回 emoji 或空)
export function getWindowDecor() {
  const light = getWindowLight()
  const season = getSeason()
  if (light.scene === 'night') return { icon: '🌙', label: 'night' }
  if (light.scene === 'dawn') return { icon: '🌤️', label: 'dawn' }
  if (light.scene === 'dusk' || light.scene === 'evening') return { icon: '🌇', label: 'sunset' }
  if (season === 'spring') return { icon: '🌸', label: 'spring' }
  if (season === 'summer') return { icon: '☀️', label: 'summer' }
  if (season === 'autumn') return { icon: '🍂', label: 'autumn' }
  if (season === 'winter') return { icon: '❄️', label: 'winter' }
  return { icon: '', label: '' }
}
