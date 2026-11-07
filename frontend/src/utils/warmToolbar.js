// 暖居「工具栏胞吐」的滚动判定(纯函数,便于单测复算)。
// 胞吐会把工具栏从内容流里搬进顶栏,页面总高同步少掉 loss(工具栏 + 下边距):
// - 靠近底部时不胞吐,否则浏览器会把 scrollTop 夹回新的底部,表现为「往下翻又弹回来」;
// - 收回要等滚回「胞吐前的画面」(阈值再往下 loss),单一阈值会在临界页高上反复
//   「胞吐 → scrollTop 夹到顶 → 收回 → 再胞吐」,看起来就是内容上下跳动;
// - 已经省不出这么多高度时(切到只有几条记录的月份)要收回,否则工具栏永远回不去。
// 记账本这类「外壳工具栏 + 页签工具栏」双层页面 loss 是两份,临界页高下最容易被踩到。
export function nextScrolled({ scrolled, scrollTop, scrollHeight, clientHeight, threshold, loss }) {
  const atTop = threshold - 4
  const room = scrollHeight - clientHeight // 还能滚多远
  if (scrolled) {
    // 页面已经负担不起这次胞吐(切月后内容变短):继续留在顶栏工具栏就再也回不来,直接收回
    if (loss > 0 && room <= loss) return false
    return scrollTop > Math.max(0, atTop - (loss || 0))
  }
  if (scrollTop <= atTop) return false
  if (loss > 0 && scrollTop > room - loss) return false
  return true
}
