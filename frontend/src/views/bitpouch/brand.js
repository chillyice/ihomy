// 比特口袋的品牌图形:绿底圆角方块 + 深色 b_ 像素字标(与 bit_pouch 仓的 favicon / 应用图标同一形状)。
// 静态常量,由发布页与在线工具页共用;放这里避免两份拷贝走样。

export const pouchMark = `<svg viewBox="0 0 32 32" shape-rendering="crispEdges">
<rect width="32" height="32" rx="7" fill="#b8ff3d"/>
<path d="M5 9H7V13H15V21H5Z M7 14H13V20H7Z M16 21H26V22H16Z" fill="#142000" fill-rule="evenodd"/>
</svg>`

export const ghIcon =
  '<svg viewBox="0 0 16 16"><path fill="currentColor" stroke="none" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82a7.4 7.4 0 0 1 2-.27c.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z"/></svg>'
export const dlIcon = '<svg viewBox="0 0 24 24"><path d="M12 3v12m0 0 4-4m-4 4-4-4M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"/></svg>'
export const webIcon =
  '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.3 2.4 14.2 0 17M12 3.5c-2.4 2.3-2.4 14.2 0 17"/></svg>'
export const zipIcon =
  '<svg viewBox="0 0 24 24"><path d="M4 5h16v14H4z"/><path d="M9 5v3M15 5v3M9 11v3M15 11v3M9 17v2M15 17v2"/><path d="M12 8v2"/></svg>'

// 对外版本、工具数与仓库地址:发布页与在线工具页共用,换版本只改这里。
export const BIT_POUCH_VERSION = '0.4.0'
// 与 bit_pouch 仓 `npm run check:plugins` 的内置插件数保持一致；
// 在线工具目录另有自己的 BIT_POUCH_TOOLS 清单（24 个），两者不要混用。
export const BIT_POUCH_DESKTOP_TOOL_COUNT = 24
export const BIT_POUCH_REPO = 'https://github.com/chillyice/bit_pouch'
