import { NAV_PATHS } from './navModules'
import { isChildAllowedPath } from './displayMode'

const modules = Object.entries(NAV_PATHS)
  .filter(([code]) => !['ops', 'health', 'vault'].includes(code))
  .map(([, path]) => path)
const detail = /^\/(?:blog\/\d+|album\/\d+|blog\/edit\/\d+|diary\/(?:book|edit)\/\d+)$/

/** Result links are supplied by registered tools. The browser checks them again before navigating. */
export function assistantPath(path, childMode = false, hidden = null) {
  if (typeof path !== 'string' || !/^\/[a-z\d/-]+(?:\?[a-zA-Z0-9_=&-]+)?$/.test(path)) return ''
  const [base] = path.split('?')
  if (!modules.includes(base) && !detail.test(base)) return ''
  // 功能开关关掉的页面不给跳(与路由守卫同一口径)
  if (hidden && hidden(path)) return ''
  return !childMode || isChildAllowedPath(path) ? path : ''
}
