import type { ToolManifest } from '@/core/types'

export default {
  id: "unit",
  name: { zh: "单位换算", en: "Unit Converter" },
  description: {
    zh: "长度 / 重量 / 温度 / 数据量等",
    en: "Length / weight / temperature / data and more",
  },
  category: "inspect",
  icon: "wrench",
  keywords: ["unit","length","weight","temperature","convert","单位","换算","长度","重量","温度"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 134,
} satisfies ToolManifest
