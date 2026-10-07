// JWT 载荷解码与有效期判断(只读不验签):给请求预检判断 access token 是否已过期。
// 起因:公开接口(/api/public/**)拿到过期令牌会静默按未登录处理并照常返回 200,
// 前端只在 HTTP 401 时才续期 → 这类请求不会续期,数据悄悄退回游客口径
// (家庭天气位置失效退回 IP 定位、日出日落与首页聚合同理)。发请求前看一眼 exp 即可避免。

/** 解码 JWT 载荷;非 JWT 结构或解析失败返回 null */
export function decodeJwtPayload(token) {
  const seg = String(token || '').split('.')[1]
  if (!seg) return null
  try {
    // base64url → base64 并补足填充,再按 UTF-8 解码(载荷里的用户名等可能是中文)
    const b64 = seg.replace(/-/g, '+').replace(/_/g, '/')
    const bin = atob(b64.padEnd(Math.ceil(b64.length / 4) * 4, '='))
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch (e) {
    return null
  }
}

/** 令牌是否已过期(默认含 30 秒提前量,避开请求发出途中的临界过期)。
 *  无 exp 或无法解析时返回 false:交回原有 401 流程处理,不在这里猜。 */
export function isTokenExpired(token, skewMs = 30000) {
  const payload = decodeJwtPayload(token)
  if (!payload || typeof payload.exp !== 'number') return false
  return payload.exp * 1000 <= Date.now() + skewMs
}
