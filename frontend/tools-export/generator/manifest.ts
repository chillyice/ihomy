import type { ToolManifest } from '@/core/types'

export default {
  id: "generator",
  name: { zh: "随机生成器", en: "Random Generator" },
  description: {
    zh: "密码 / UUID / ULID / 随机字节 / 抽签分组，本地生成",
    en: "Passwords / UUID / ULID / random bytes / draw & group, generated locally",
  },
  category: "convert",
  icon: "key",
  keywords: ["password","random","uuid","ulid","nanoid","passphrase","密码","随机","抽签"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 110,
} satisfies ToolManifest
