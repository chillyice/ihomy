import type { ToolManifest } from '@/core/types'

export default {
  id: "regex",
  name: { zh: "正则测试", en: "Regex Tester" },
  description: {
    zh: "实时匹配 / 捕获组 / 替换预览，病态表达式自动超时中断",
    en: "Live matches / capture groups / replacement preview, with runaway patterns interrupted",
  },
  category: "inspect",
  icon: "regex",
  keywords: ["regex","regexp","match","replace","capture","正则","匹配","替换"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 122,
} satisfies ToolManifest
