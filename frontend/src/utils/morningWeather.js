import { MORNING_SCENES } from './morning'

export function resolveMorningWeather(weather) {
  // 和风当前天气码比公共接口的 condition 更细,早安场景用原始码区分满天阴云和雷阵雨。
  const icon = String(weather?.iconCode ?? weather?.nowFull?.icon ?? '')
  if (icon === '104') return 'overcast'
  if (icon === '150') return 'clear'
  if (icon === '302' || icon === '303' || icon === '304') return 'thunder'

  const condition = String(weather?.condition ?? '').toLowerCase()
  return MORNING_SCENES.includes(condition) ? condition : 'clear'
}
