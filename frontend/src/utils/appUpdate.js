// 应用内更新的纯逻辑:版本号解析/比较、安装包文件名、更新必要性判定。
// 与界面无关,便于单测;真正的下载与安装分别在 native/appUpdate.js 与原生插件 IhomyUpdater。
//
// 版本号形态固定 `V<主>.<次>`(见 AGENTS.md 版本号规则),解析不出来一律不提示更新(宁漏不误)。

const VERSION_RE = /^V(\d+)\.(\d+)$/i

/** 'V11.84' → { major: 11, minor: 84 };非法返回 null */
export function parseVersion(value) {
  const m = VERSION_RE.exec(String(value || '').trim())
  return m ? { major: Number(m[1]), minor: Number(m[2]) } : null
}

/** 比较两个版本号:-1 / 0 / 1;任一侧非法返回 null */
export function compareVersion(a, b) {
  const left = parseVersion(a)
  const right = parseVersion(b)
  if (!left || !right) return null
  if (left.major !== right.major) return left.major < right.major ? -1 : 1
  if (left.minor !== right.minor) return left.minor < right.minor ? -1 : 1
  return 0
}

/** 安装包缓存文件名;版本号非法时返回空串(调用方据此放弃下载) */
export function apkFileName(version) {
  const v = parseVersion(version)
  return v ? `ihomy-V${v.major}.${v.minor}.apk` : ''
}

/**
 * 更新必要性判定。
 *
 * - `current` 或 `latest` 解析失败 → `unknown`(不打扰用户);
 * - 不低于 `latest` → `none`;
 * - 有 `minSupported` 且 current 低于它 → `required`(客户端只留「立即更新」);
 * - 否则 `optional`。
 */
export function updatePlan({ current, latest, minSupported } = {}) {
  const cmpLatest = compareVersion(current, latest)
  if (cmpLatest === null) return { kind: 'unknown', required: false }
  if (cmpLatest >= 0) return { kind: 'none', required: false }
  const cmpMin = minSupported ? compareVersion(current, minSupported) : 1
  const required = cmpMin !== null && cmpMin < 0
  return { kind: required ? 'required' : 'optional', required }
}

/** 字节数 → '54.4 MB'(下载进度展示用) */
export function formatBytes(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return '0 MB'
  const mb = n / (1024 * 1024)
  if (mb >= 1) return `${mb.toFixed(1)} MB`
  return `${Math.max(1, Math.round(n / 1024))} KB`
}
