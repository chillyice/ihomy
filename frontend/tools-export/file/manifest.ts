import type { ToolManifest } from '@/core/types'

export default {
  id: "file",
  name: { zh: "文件工具", en: "File Tools" },
  description: {
    zh: "类型识别 / ZIP 打包与解压",
    en: "Type detection / ZIP pack and unpack",
  },
  category: "inspect",
  icon: "file",
  keywords: ["file","magic","mime","zip","unzip","archive","文件","类型","压缩包"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 128,
} satisfies ToolManifest
