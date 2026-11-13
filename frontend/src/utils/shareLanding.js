// 分享落地(/?hid=):分享链接带来的家庭标识解析 + 首页「封面门脸」判定。
// hid 是家庭的 share_token(设置页分享链接里的 16 位混淆串),home_id 是兜底;两者都只指向公开家庭。

/** hid 合法形状:字母数字下划线连字符,6~64 位(非法值一律当没带,别把怪串透给后端) */
const HID_PATTERN = /^[A-Za-z0-9_-]{6,64}$/

/**
 * 从 location.search 取分享参数。
 * @returns {{hid: string, homeId: number|null}}
 */
export function parseShareQuery(search = '') {
  const params = new URLSearchParams(String(search || '').replace(/^\?/, ''))
  const hid = (params.get('hid') || '').trim()
  const rawHomeId = (params.get('home_id') || '').trim()
  return {
    hid: HID_PATTERN.test(hid) ? hid : '',
    homeId: /^\d{1,18}$/.test(rawHomeId) ? Number(rawHomeId) : null,
  }
}

/**
 * 首页要不要挂封面门脸:被看的家庭有封面,且不是自己家。
 * 游客没有 currentFamilyId → 只要有封面就展示(这正是「对外名片」的语义);
 * 登录用户看别人家的分享链接会展示,回到自己家不展示。
 * @param {{id?: number|string, coverImage?: string}|null} family
 * @param {number|string|null|undefined} currentFamilyId
 */
export function showCoverBanner(family, currentFamilyId) {
  if (!family || !family.coverImage) return false
  if (family.id == null) return false
  return String(family.id) !== String(currentFamilyId ?? '')
}
