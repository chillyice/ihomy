// 暖居首页布局的解析:本机缓存与账号下发的 JSON 文本都走这里。
// v1 存的是 id 字符串数组,v3 起是 { id, span, row } 对象;宽度/行高默认值与「这一项还算不算数」
// 依赖页面内的组件注册表,由调用方以回调传入(纯函数,便于单测)。
// 解析不出来、或一项都不剩时返回 null,交给调用方回落到默认布局(别把空数组当成有效布局)。
export function parseWarmLayout(raw, { spanOf = () => 4, rowOf = () => 2, isValid = () => true } = {}) {
  try {
    if (!raw) return null
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr) || !arr.length) return null
    const layout = arr
      .map((item) => {
        if (!item) return null
        if (typeof item === 'string') return { id: item, span: spanOf(item), row: rowOf(item) }
        if (typeof item !== 'object' || !item.id) return null
        return { ...item, row: item.row || rowOf(item.id) }
      })
      .filter((item) => item && isValid(item))
    return layout.length ? layout : null
  } catch (e) {
    return null
  }
}
