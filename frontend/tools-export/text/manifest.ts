import type { ToolManifest } from '@/core/types'

export default {
  id: "text",
  name: { zh: "文本清洗与对比", en: "Text Cleanup & Diff" },
  description: {
    zh: "清洗 / 命名转换 / Markdown 表格 / 文本 diff",
    en: "Cleanup / case conversion / Markdown tables / line diff",
  },
  category: "convert",
  icon: "text",
  keywords: ["text","clean","case","snake","camel","markdown","table","diff","文本","清洗","对比"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 124,
} satisfies ToolManifest
