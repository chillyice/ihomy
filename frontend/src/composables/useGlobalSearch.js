// 全局搜索(命令面板结果区):6 类内容并发查询、防抖、过期响应丢弃。
//
// 设计约束:
// - 只走既有列表接口(后端本批零改动),博客用服务端 keyword,其余取回后前端过滤;
// - 文件类只搜「当前文件页目录」——storageApi.browse 没有 keyword,也没有递归能力;
// - 游客不发成员类请求(接口会回空,干脆不打);
// - 纯函数(normalizeQuery / matchFields / mergeResults / buildSearchItem)不读 window、不读 store,
//   便于单测;运行期依赖(api / isGuest)由调用方注入,未注入时按需动态加载,避免纯函数测试拉起整个应用。
import { ref } from 'vue'

export const SEARCH_LIMIT = 5
export const SEARCH_MIN_LENGTH = 2
export const SEARCH_VISIBLE_LIMIT = 12
export const SEARCH_DEBOUNCE_MS = 250

// 分组顺序 = 面板里的展示顺序;labelKey 由命令面板自行翻译(内核不依赖 i18n)
export const SEARCH_SOURCES = [
  { id: 'blog', labelKey: 'command.source.blog' },
  { id: 'diary', labelKey: 'command.source.diary' },
  { id: 'album', labelKey: 'command.source.album' },
  { id: 'file', labelKey: 'command.source.file' },
  { id: 'member', labelKey: 'command.source.member' },
  { id: 'wish', labelKey: 'command.source.wish' },
]

const SOURCE_ORDER = SEARCH_SOURCES.map((s) => s.id)

/** 统一查询口径:去首尾空白 + 转小写(纯函数) */
export function normalizeQuery(q) {
  return String(q ?? '')
    .trim()
    .toLowerCase()
}

/** 大小写不敏感的子串匹配,任一字段命中即算命中(纯函数) */
export function matchFields(obj, q, fields = []) {
  const needle = normalizeQuery(q)
  if (!needle || !obj || !Array.isArray(fields)) return false
  return fields.some((f) => {
    const v = obj?.[f]
    return v != null && String(v).toLowerCase().includes(needle)
  })
}

function pickText(record, fields) {
  for (const f of fields || []) {
    const v = record?.[f]
    if (v != null && String(v).trim()) return String(v).trim()
  }
  return ''
}

/**
 * 统一结果项结构:{ id, group:'search', source, label, sub, path }
 * label 为空时退回 `#id`,绝不出现 undefined(纯函数)。
 */
export function buildSearchItem(source, record, spec = {}) {
  const r = record || {}
  const raw = pickText(r, spec.labelFields)
  const label = spec.label ? spec.label(r, raw) : raw
  const sub = typeof spec.sub === 'function' ? spec.sub(r) : ''
  const path = typeof spec.path === 'function' ? spec.path(r) : ''
  const id = r.id ?? ''
  return {
    id: `${source}-${id}`,
    group: 'search',
    source,
    label: String(label || '').trim() || `#${id}`,
    sub: String(sub || '').trim(),
    path: String(path || ''),
  }
}

/** 过滤空组、按组内顺序保留、单组最多 SEARCH_LIMIT 条(纯函数) */
export function mergeResults(groups) {
  if (!Array.isArray(groups)) return []
  return groups
    .filter((g) => g && Array.isArray(g.items) && g.items.length > 0)
    .map((g) => ({ source: g.source, items: g.items.slice(0, SEARCH_LIMIT) }))
}

function dateText(v) {
  return v ? String(v).slice(0, 10) : ''
}

function clip(v, max = 40) {
  const s = String(v ?? '').trim()
  return s.length > max ? `${s.slice(0, max)}…` : s
}

function inDir(path, name) {
  return path ? `${path}/${name}` : name
}

function filePagePath(deviceId, path) {
  const qs = new URLSearchParams()
  qs.set('deviceId', String(deviceId))
  if (path) qs.set('path', path)
  return `/storage/files?${qs.toString()}`
}

/**
 * 创建一份搜索状态。默认(不传 deps)走动态加载 @/api 与 user store;
 * 单测传 deps 即可完全脱离网络与 pinia。
 */
export function createSearchState(injected) {
  const groups = ref([])
  const loading = ref(false)
  // 当前所在文件目录:{ deviceId, path } | null;由 FileBrowse 写入
  const fileContext = ref(null)

  let timer = null
  let seq = 0
  let depsPromise = null

  const resolveDeps = () => {
    if (injected) return Promise.resolve(injected)
    if (!depsPromise) {
      depsPromise = Promise.all([import('@/api'), import('@/stores/user')]).then(([api, store]) => ({
        api,
        isGuest: () => store.useUserStore().isGuest,
      }))
    }
    return depsPromise
  }

  const loadBlog = async (q, api) => {
    const data = await api.blogApi.list({ current: 1, size: SEARCH_LIMIT, keyword: q })
    return (data?.records || []).slice(0, SEARCH_LIMIT).map((r) =>
      buildSearchItem('blog', r, {
        labelFields: ['title'],
        sub: (x) => dateText(x.createdAt),
        path: (x) => `/blog/${x.id}`,
      }),
    )
  }

  const loadDiary = async (q, api) => {
    // 日记无 keyword:取近 50 条后前端过滤;单篇无路由,落到作者手账本
    const data = await api.diaryApi.list({ current: 1, size: 50 })
    return (data?.records || [])
      .filter((r) => matchFields(r, q, ['title', 'content']))
      .slice(0, SEARCH_LIMIT)
      .map((r) =>
        buildSearchItem('diary', r, {
          label: (x) => clip(x.content) || x.title,
          sub: (x) => [x.authorName, dateText(x.createdAt)].filter(Boolean).join(' · '),
          path: (x) => (x.authorId != null ? `/diary/book/${x.authorId}` : '/diary'),
        }),
      )
  }

  const loadAlbum = async (q, api) => {
    const list = await api.albumApi.list()
    return (list || [])
      .filter((r) => matchFields(r, q, ['name']))
      .slice(0, SEARCH_LIMIT)
      .map((r) =>
        buildSearchItem('album', r, {
          labelFields: ['name'],
          sub: (x) => (Number(x.totalPhotoCount ?? x.photoCount) > 0 ? String(Number(x.totalPhotoCount ?? x.photoCount)) : ''),
          path: (x) => `/album/${x.id}`,
        }),
      )
  }

  const loadFile = async (q, api) => {
    const ctx = fileContext.value
    if (!ctx?.deviceId) return []
    const rows = await api.storageApi.browse(ctx.deviceId, ctx.path || '')
    return (rows || [])
      .filter((r) => matchFields(r, q, ['name']))
      .slice(0, SEARCH_LIMIT)
      .map((r) =>
        buildSearchItem(
          'file',
          { ...r, id: r.id ?? r.name },
          {
            labelFields: ['name'],
            // 目录落到它自己,文件落到所在目录(用现有预览面板查看文件,不新开路由)
            path: (x) => filePagePath(ctx.deviceId, x.isDir ? inDir(ctx.path || '', x.name) : ctx.path || ''),
          },
        ),
      )
  }

  const loadMember = async (q, api) => {
    const list = await api.memberApi.list()
    return (list || [])
      .filter((r) => matchFields(r, q, ['nickname', 'username']))
      .slice(0, SEARCH_LIMIT)
      .map((r) =>
        buildSearchItem('member', r, {
          labelFields: ['nickname', 'username'],
          sub: (x) => x.username || '',
          path: () => '/member',
        }),
      )
  }

  const loadWish = async (q, api) => {
    const list = await api.wishApi.list()
    return (list || [])
      .filter((r) => matchFields(r, q, ['title', 'reason']))
      .slice(0, SEARCH_LIMIT)
      .map((r) =>
        buildSearchItem('wish', r, {
          labelFields: ['title'],
          sub: (x) => clip(x.reason, 24),
          path: () => '/wish',
        }),
      )
  }

  async function execute(q, my) {
    const deps = await resolveDeps().catch(() => null)
    if (my !== seq) return
    if (!deps) {
      loading.value = false
      return
    }
    const api = deps.api
    const guest = (() => {
      try {
        return !!deps.isGuest?.()
      } catch (e) {
        return false
      }
    })()

    const tasks = SOURCE_ORDER.map(async (source) => {
      try {
        let items = []
        if (source === 'blog') items = await loadBlog(q, api)
        else if (source === 'diary') items = await loadDiary(q, api)
        else if (source === 'album') items = await loadAlbum(q, api)
        else if (source === 'file') items = await loadFile(q, api)
        else if (source === 'member') items = guest ? [] : await loadMember(q, api)
        else if (source === 'wish') items = await loadWish(q, api)
        return { source, items }
      } catch (e) {
        // 单类失败只让该类为空,不阻塞其它类
        return { source, items: [] }
      }
    })

    const results = await Promise.all(tasks)
    if (my !== seq) return
    groups.value = mergeResults(results)
    loading.value = false
  }

  function clearTimer() {
    if (timer != null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function reset() {
    clearTimer()
    seq += 1
    groups.value = []
    loading.value = false
  }

  function run(raw) {
    const q = normalizeQuery(raw)
    clearTimer()
    if (q.length < SEARCH_MIN_LENGTH) {
      reset()
      return
    }
    const my = ++seq
    loading.value = true
    timer = setTimeout(() => {
      timer = null
      execute(q, my)
    }, SEARCH_DEBOUNCE_MS)
  }

  function setFileContext(ctx) {
    fileContext.value = ctx && ctx.deviceId ? { deviceId: ctx.deviceId, path: ctx.path || '' } : null
  }

  function dispose() {
    reset()
    fileContext.value = null
  }

  return { groups, loading, fileContext, run, reset, setFileContext, dispose }
}

// 面板与文件页共享同一份状态:文件页写入上下文,面板读结果(与 useHotkeys 同样是模块级单例)
let singleton = null

export function useGlobalSearch(deps) {
  if (!singleton) singleton = createSearchState(deps)
  return singleton
}
