/**
 * 日记纸张排版共享常量与工具:编辑页(DiaryEdit)与翻书页(DiaryBook)必须使用同一套度量,
 * 否则编辑时的分页线与查看时的信纸分页会对不上。
 * 注意:measureDiaryLines 的离屏测量样式必须与 .paper-textarea / .page-text 的 CSS 保持一致
 * (字体/字号/行高/宽度 448px/word-break/white-space)。
 */
export const LINE_H = 28
export const LINES_PER_PAGE = 18
export const PAGE_H = LINE_H * LINES_PER_PAGE
export const CONTENT_W = 28 * 16
export const PAPER_TEXT_FONT = "'Cascadia Mono', 'Consolas', 'Courier New', monospace"

export const MOODS = [
  { icon: '😊', label: '开心' }, { icon: '😌', label: '平静' }, { icon: '😢', label: '难过' }, { icon: '😡', label: '生气' },
  { icon: '😰', label: '焦虑' }, { icon: '🥰', label: '温馨' }, { icon: '😴', label: '疲倦' }, { icon: '🤔', label: '思考' },
  { icon: '🥳', label: '兴奋' }, { icon: '😎', label: '得意' }, { icon: '🥺', label: '感动' }, { icon: '😔', label: '失落' },
]
export const WEATHERS = [
  { icon: '☀️', label: '晴' }, { icon: '⛅', label: '多云' }, { icon: '☁️', label: '阴' }, { icon: '🌧️', label: '雨' },
  { icon: '⛈️', label: '雷雨' }, { icon: '❄️', label: '雪' }, { icon: '🌫️', label: '雾' }, { icon: '🌪️', label: '大风' },
]

export const moodLabel = (icon) => MOODS.find((m) => m.icon === icon)?.label || '心情'
export const weatherLabel = (icon) => WEATHERS.find((w) => w.icon === icon)?.label || '天气'

/**
 * 测量一段日记文本在信纸宽度下占多少行(共享单个离屏 DOM,一次性触发逐条布局测量)。
 * ponytail: O(条数) 次强制布局,家庭规模(几百篇)可接受;超大数据量再换 canvas 测宽分页。
 */
export function measureDiaryLines(text) {
  let el = document.getElementById('diary-line-measurer')
  if (!el) {
    el = document.createElement('div')
    el.id = 'diary-line-measurer'
    el.style.cssText = `position:fixed;left:-9999px;top:0;visibility:hidden;pointer-events:none;width:${CONTENT_W}px;font-family:${PAPER_TEXT_FONT};font-size:16px;line-height:${LINE_H}px;word-break:break-all;white-space:pre-wrap;margin:0;padding:0;`
    document.body.appendChild(el)
  }
  el.textContent = text || ' '
  const lines = Math.max(1, Math.ceil(el.getBoundingClientRect().height / LINE_H))
  el.textContent = ''
  return lines
}
