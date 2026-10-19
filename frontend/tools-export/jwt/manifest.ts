import type { ToolManifest } from '@/core/types'

export default {
  id: "jwt",
  name: { zh: "JWT 与密钥", en: "JWT & Keys" },
  description: {
    zh: "解码 / 验签 / 密钥对 / AES 加解密",
    en: "Decode / verify / key pairs / AES",
  },
  category: "inspect",
  icon: "jwt",
  keywords: ["jwt","token","hmac","rsa","ecdsa","pem","jwk","aes","令牌","验签","密钥"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 129,
} satisfies ToolManifest
