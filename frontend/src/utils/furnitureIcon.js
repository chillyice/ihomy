// 家具类型线性图标(24 viewBox,stroke 线性风格与 Element Plus 图标一致)
// 供画布矩形角标 / 侧栏预设 / 侧栏家具列表 / 列表表格四处复用;自定义类型回落「其他」。
// 每个图标是 SVG 子元素描述数组({tag, attrs}),渲染方用 <component :is="p.tag" v-bind="p.attrs">。
const ICONS = {
  '衣柜': [
    { tag: 'rect', attrs: { x: 5, y: 3, width: 14, height: 18, rx: 1.5 } },
    { tag: 'line', attrs: { x1: 12, y1: 3, x2: 12, y2: 21 } },
    { tag: 'line', attrs: { x1: 9.5, y1: 10, x2: 9.5, y2: 13 } },
    { tag: 'line', attrs: { x1: 14.5, y1: 10, x2: 14.5, y2: 13 } },
  ],
  '床': [
    { tag: 'line', attrs: { x1: 3, y1: 6, x2: 3, y2: 19 } },
    { tag: 'path', attrs: { d: 'M3 12 h14 a4 4 0 0 1 4 4 v3' } },
    { tag: 'rect', attrs: { x: 5.5, y: 8.5, width: 5, height: 3, rx: 1 } },
  ],
  '书桌': [
    { tag: 'line', attrs: { x1: 3, y1: 8, x2: 21, y2: 8 } },
    { tag: 'line', attrs: { x1: 5, y1: 8, x2: 5, y2: 19 } },
    { tag: 'line', attrs: { x1: 19, y1: 8, x2: 19, y2: 19 } },
    { tag: 'line', attrs: { x1: 5, y1: 13, x2: 19, y2: 13 } },
  ],
  '餐桌': [
    { tag: 'ellipse', attrs: { cx: 12, cy: 7.5, rx: 8, ry: 2.2 } },
    { tag: 'line', attrs: { x1: 12, y1: 9.7, x2: 12, y2: 18 } },
    { tag: 'line', attrs: { x1: 8.5, y1: 18.5, x2: 15.5, y2: 18.5 } },
  ],
  '沙发': [
    { tag: 'rect', attrs: { x: 5, y: 4.5, width: 14, height: 8, rx: 2 } },
    { tag: 'rect', attrs: { x: 3, y: 10.5, width: 18, height: 6.5, rx: 2 } },
    { tag: 'line', attrs: { x1: 6, y1: 17, x2: 6, y2: 19.5 } },
    { tag: 'line', attrs: { x1: 18, y1: 17, x2: 18, y2: 19.5 } },
  ],
  '茶几': [
    { tag: 'rect', attrs: { x: 4, y: 9.5, width: 16, height: 2.2, rx: 1 } },
    { tag: 'line', attrs: { x1: 6, y1: 11.7, x2: 6, y2: 19 } },
    { tag: 'line', attrs: { x1: 18, y1: 11.7, x2: 18, y2: 19 } },
    { tag: 'line', attrs: { x1: 6, y1: 15.5, x2: 18, y2: 15.5 } },
  ],
  '冰箱': [
    { tag: 'rect', attrs: { x: 6.5, y: 3, width: 11, height: 18, rx: 2 } },
    { tag: 'line', attrs: { x1: 6.5, y1: 9.5, x2: 17.5, y2: 9.5 } },
    { tag: 'line', attrs: { x1: 9.5, y1: 5.5, x2: 9.5, y2: 7.5 } },
    { tag: 'line', attrs: { x1: 9.5, y1: 11.5, x2: 9.5, y2: 14 } },
  ],
  '柜子': [
    { tag: 'rect', attrs: { x: 5, y: 4, width: 14, height: 16, rx: 1.5 } },
    { tag: 'line', attrs: { x1: 5, y1: 11, x2: 19, y2: 11 } },
    { tag: 'line', attrs: { x1: 12, y1: 11, x2: 12, y2: 20 } },
  ],
  '书架': [
    { tag: 'rect', attrs: { x: 5, y: 3, width: 14, height: 18, rx: 1.5 } },
    { tag: 'line', attrs: { x1: 5, y1: 9, x2: 19, y2: 9 } },
    { tag: 'line', attrs: { x1: 5, y1: 15, x2: 19, y2: 15 } },
  ],
  '其他': [
    { tag: 'rect', attrs: { x: 5.5, y: 5.5, width: 13, height: 13, rx: 2 } },
  ],
}

export const furnitureIcon = (type) => ICONS[type] || ICONS['其他']
