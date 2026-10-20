// bit_pouch 在线工具目录:01–12 与桌面版插件的排列一致,13 起是与 ihomy 工具箱共用的
// 设计与文档类工具(颜色/单位换算/图片/文件/PDF/Markdown 阅读器) —— 同一套内核,两处入口。
// 名称与描述沿用工具箱的 tools.<id>.* 文案,同一工具不维护第二套说法;
// 面板组件在 views/bitpouch/panels/ 下,由工具页按 id 异步加载。
export const BIT_POUCH_TOOLS = [
  {
    id: 'json',
    no: '01',
    titleKey: 'tools.json.title',
    descKey: 'tools.json.desc',
    keywords: ['json', 'format', 'minify', 'sort', '格式化', '压缩'],
  },
  {
    id: 'codec',
    no: '02',
    titleKey: 'tools.codec.title',
    descKey: 'tools.codec.desc',
    keywords: ['codec', 'unicode', 'base64', 'url', 'html', '编码', '转义'],
  },
  {
    id: 'base',
    no: '03',
    titleKey: 'tools.base.title',
    descKey: 'tools.base.desc',
    keywords: ['base', 'radix', 'binary', 'hex', '进制', '二进制', '十六进制'],
  },
  {
    id: 'packet',
    no: '04',
    titleKey: 'tools.packet.title',
    descKey: 'tools.packet.desc',
    keywords: ['packet', 'hex', 'checksum', '报文', '校验', '拆包'],
  },
  {
    id: 'subnet',
    no: '05',
    titleKey: 'tools.subnet.title',
    descKey: 'tools.subnet.desc',
    keywords: ['subnet', 'cidr', 'netmask', 'ip', '子网', '掩码', '网段'],
  },
  {
    id: 'hash',
    no: '06',
    titleKey: 'tools.hash.title',
    descKey: 'tools.hash.desc',
    keywords: ['hash', 'md5', 'sha', 'crc32', 'hmac', '摘要', '校验'],
  },
  {
    id: 'generator',
    no: '07',
    titleKey: 'tools.generator.title',
    descKey: 'tools.generator.desc',
    keywords: ['password', 'random', 'uuid', 'ulid', 'nanoid', '密码', '随机'],
  },
  {
    id: 'time',
    no: '08',
    titleKey: 'tools.time.title',
    descKey: 'tools.time.desc',
    keywords: ['timestamp', 'timezone', 'date', '时间戳', '时区', '日期差'],
  },
  {
    id: 'regex',
    no: '09',
    titleKey: 'tools.regex.title',
    descKey: 'tools.regex.desc',
    keywords: ['regex', 'regexp', 'match', 'replace', '正则', '匹配', '替换'],
  },
  {
    id: 'text',
    no: '10',
    titleKey: 'tools.text.title',
    descKey: 'tools.text.desc',
    keywords: ['text', 'clean', 'case', 'diff', '文本', '清洗', '对比'],
  },
  {
    id: 'ops',
    no: '11',
    titleKey: 'tools.ops.title',
    descKey: 'tools.ops.desc',
    keywords: ['ops', 'shell', 'nginx', 'docker', 'systemd', 'ssh', '运维', '命令'],
  },
  {
    id: 'jwt',
    no: '12',
    titleKey: 'tools.jwt.title',
    descKey: 'tools.jwt.desc',
    keywords: ['jwt', 'token', 'hmac', 'rsa', 'jws', '令牌', '验签'],
  },
  {
    id: 'color',
    no: '13',
    titleKey: 'tools.color.title',
    descKey: 'tools.color.desc',
    keywords: ['color', 'hex', 'rgb', 'hsl', 'lab', 'contrast', 'wcag', 'gradient', '颜色', '对比度', '配色'],
  },
  {
    id: 'unit',
    no: '14',
    titleKey: 'tools.unit.title',
    descKey: 'tools.unit.desc',
    keywords: ['unit', 'length', 'weight', 'temperature', 'convert', '单位', '换算', '长度', '重量', '温度'],
  },
  {
    id: 'image',
    no: '15',
    titleKey: 'tools.image.title',
    descKey: 'tools.image.desc',
    keywords: ['image', 'compress', 'resize', 'favicon', 'ico', 'exif', '图片', '压缩', '图标'],
  },
  {
    id: 'file',
    no: '16',
    titleKey: 'tools.file.title',
    descKey: 'tools.file.desc',
    keywords: ['file', 'magic', 'mime', 'zip', 'unzip', 'archive', '文件', '类型', '压缩包'],
  },
  {
    id: 'pdf',
    no: '17',
    titleKey: 'tools.pdf.title',
    descKey: 'tools.pdf.desc',
    keywords: ['pdf', 'image', 'text', 'convert', '转换', '提取文字'],
  },
  {
    id: 'markdown',
    no: '18',
    titleKey: 'tools.markdown.title',
    descKey: 'tools.markdown.desc',
    keywords: ['markdown', 'md', 'reader', 'document', '文档', '阅读', 'md 文件'],
  },
]

export const BIT_POUCH_TOOL_IDS = BIT_POUCH_TOOLS.map((tool) => tool.id)

export const findBitPouchTool = (id) => BIT_POUCH_TOOLS.find((tool) => tool.id === id) || null
