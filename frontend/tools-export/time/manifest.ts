import type { ToolManifest } from '@/core/types'

export default {
  id: "time",
  name: { zh: "时间工具箱", en: "Time Toolbox" },
  description: {
    zh: "时间戳 / 时区 / 日期差 / Cron，本地换算",
    en: "Timestamp / time zone / date difference / Cron, all local",
  },
  category: "convert",
  icon: "clock",
  keywords: ["timestamp","timezone","cron","date","时间戳","时区","日期差","表达式"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 120,
} satisfies ToolManifest
