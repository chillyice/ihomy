// 小游戏的计时显示:不足一小时用 m:ss,超过一小时用 h:mm:ss。
// 扫雷/纸牌接龙/俄罗斯方块等页面共用,避免各写一份补零逻辑。

const pad2 = (n) => String(n).padStart(2, '0')

export function formatClock(totalSeconds) {
  const seconds = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return h > 0 ? `${h}:${pad2(m)}:${pad2(s)}` : `${m}:${pad2(s)}`
}
