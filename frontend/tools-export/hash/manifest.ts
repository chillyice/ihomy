import type { ToolManifest } from '@/core/types'

export default {
  id: "hash",
  name: { zh: "哈希与校验", en: "Hash & Checksum" },
  description: {
    zh: "MD5 / SHA / CRC32 / HMAC，文本与文件摘要，全程本地",
    en: "MD5 / SHA / CRC32 / HMAC for text and files, all local",
  },
  category: "inspect",
  icon: "hash",
  keywords: ["hash","md5","sha1","sha256","sha384","sha512","crc32","hmac","checksum","摘要","校验"],
  version: "1.0.0",
  runtime: "main",
  platforms: ["web","desktop","mobile"],
  offline: true,
  order: 100,
} satisfies ToolManifest
