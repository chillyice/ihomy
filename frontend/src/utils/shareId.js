// 分享链接 ID 混淆:Knuth 乘法散列后 base36,仅混淆不加密(真正的访问控制是相册 share_token)
// 单向编码:匹配时对候选 id 逐个编码比对,无需解码
const K = 2654435761 // 0x9E3779B1 黄金比例散列常数

export const shareId = (id) => ((Number(id) * K) >>> 0).toString(36)
