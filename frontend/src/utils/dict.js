// V3.9 数据字典前端映射: 与 sys_dict_item 对应,文案走 i18n(dict.<group>.<value>)。
// 调用方传入 vue-i18n 的 t 函数,未知值回退原值,避免页面空白。
// ponytail: 用 t(key) === key 判定缺失(vue-i18n 缺键时返回 key 本身),无需 te
export const dictText = (t, group, value) => {
  const key = `dict.${group}.${value}`
  const msg = t(key)
  return msg === key ? (value || '') : msg
}
