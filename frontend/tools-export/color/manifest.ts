import type { ToolManifest } from '@/core/types'

export default {
  id: "color",
  name: { zh: "颜色与前端设计", en: "Color & UI Design" },
  description: {
    zh: "格式互转 / 对比度 / 配色 / CSS",
    en: "Conversions / contrast / palettes / CSS",
  },
  category: "inspect",
  icon: "wrench",
  keywords: ["color","hex","rgb","hsl","lab","contrast","wcag","gradient","颜色","对比度","配色"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 132,
} satisfies ToolManifest
