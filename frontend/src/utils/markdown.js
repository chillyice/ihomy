// 富文本渲染的唯一入口:Markdown 源码 → 可安全交给 v-html 的 HTML。
// 用户/管理员可写的内容要渲染成 HTML(目前仅博客正文)一律走这里 ——
// marked 默认透传原始 HTML,渲染结果不经消毒直接绑 v-html 会执行注入脚本(见 docs/踩坑速查.md §3.24)。
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// h1 降级为 h2(避免与页面标题的 h1 重复),并给每个标题加 id 供目录跳转。
marked.use({
  renderer: {
    heading({ text, depth }) {
      const level = Math.min(depth + (depth === 1 ? 1 : 0), 4)
      const id =
        'h-' +
        text
          .replace(/[^\w\u4e00-\u9fa5]+/g, '-')
          .replace(/^-|-$/g, '')
          .toLowerCase()
          .slice(0, 50)
      return `<h${level} id="${id}">${text}</h${level}>`
    },
  },
})

// DOMPurify 默认配置足够:<script>、on* 事件属性、javascript: 协议均被剥离;
// 正文用到的表格/代码块/图片与标题 id(目录锚点依赖)都在默认白名单内。
export function renderMarkdown(src) {
  if (!src) return ''
  return DOMPurify.sanitize(marked.parse(src))
}
