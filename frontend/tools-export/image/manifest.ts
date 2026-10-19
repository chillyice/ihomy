import type { ToolManifest } from '@/core/types'

export default {
  id: "image",
  name: { zh: "图片工具", en: "Image Tools" },
  description: {
    zh: "压缩 / ICO 图标 / EXIF 查看",
    en: "Compress / ICO icons / EXIF viewer",
  },
  category: "inspect",
  icon: "image",
  keywords: ["image","compress","resize","favicon","ico","exif","图片","压缩","图标"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 127,
} satisfies ToolManifest
