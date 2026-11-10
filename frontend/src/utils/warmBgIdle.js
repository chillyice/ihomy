// 暖居天气 AI 底图「待机浮现」的命中判定(纯函数,便于单测复算)。
// 只有真正露出照片的空隙才算「背景板」:
// - studio 外框内(侧栏/主区/卡片/间隙)一律不算:照片被外框盖住,不存在背景板;
// - 顶栏整条(含胞吐进来的工具栏 `.gc-pin`)也不算:返回键、晨暮开关、工具栏按钮就贴在照片边上,
//   逐个按钮排除会让鼠标从「返回」挪到「晨暮开关」的途中在缝隙里闪一次背景;
// - 浮在照片之上的常驻控件(音乐播放器/聊天室浮窗/通话层/回到顶部/页脚/安装提示/弹窗浮层)
//   挂在根节点(App.vue)而不是 studio 里,天然落在命中范围,必须显式排除——否则停在唱片、
//   聊天面板或通知条上静止满 BG_IDLE_MS,整屏照片就会盖上来。
// 新增常驻浮层(尤其挂在 App.vue 根上的)时要同步补进 BG_CONTENT_SELECTOR。
export const BG_IDLE_MS = 60000

export const BG_CONTENT_SELECTOR = [
  '.gc-topbar',
  '.gc-pin',
  '.music-player',
  '.chat-float-ball',
  '.chat-float-panel',
  '.chat-float-sheet',
  '.call-overlay',
  '.call-mini',
  '.back-to-top',
  '.site-footer',
  '.install-prompt',
  '.el-overlay',
  '.el-popper',
  '.el-message',
  '.el-notification',
].join(', ')

// el 为鼠标位置下的顶层元素(elementFromPoint 的结果);拿不到元素时按「不是背景板」处理
export function isBackgroundPlate(el) {
  if (!el || typeof el.closest !== 'function') return false
  if (el.closest('.gc-studio')) return false
  return !el.closest(BG_CONTENT_SELECTOR)
}
