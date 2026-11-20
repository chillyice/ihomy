import { NAV_PATHS } from './navModules'
import { isChildAllowedPath } from './displayMode'

const modules = Object.entries(NAV_PATHS)
  .filter(([code]) => !['ops', 'health', 'vault'].includes(code))
  .map(([, path]) => path)
const detail = /^\/(?:blog\/\d+|album\/\d+|blog\/edit\/\d+|diary\/(?:book|edit)\/\d+)$/

/** Result links are supplied by registered tools. The browser checks them again before navigating. */
export function assistantPath(path, childMode = false) {
  if (typeof path !== 'string' || !/^\/[a-z\d/-]+(?:\?[a-zA-Z0-9_=&-]+)?$/.test(path)) return ''
  const [base] = path.split('?')
  if (!modules.includes(base) && !detail.test(base)) return ''
  return !childMode || isChildAllowedPath(path) ? path : ''
}
