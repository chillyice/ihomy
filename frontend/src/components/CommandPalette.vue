<!-- 命令面板:Ctrl/⌘+K 唤起;触屏可经快捷方式/深链显式打开(键盘快捷键仍只对键鼠生效)。
     由 App.vue 懒加载,独立页(咔哒/比特口袋/壁纸/chillyice)与沉浸页不挂载。
     键盘:↑↓ 循环移动高亮、Enter 执行、Esc 关闭;Escape/方向键在 capture 阶段截住,
     避免底下的照片查看器 / 阅读器 / 游戏同时响应。
     输入 ≥2 字时在静态命令之上并入跨 6 类内容的搜索结果(见 useGlobalSearch)。 -->
<template>
  <Teleport to="body">
    <Transition name="cmd-fade">
      <div v-if="paletteOpen" class="cmd-scrim" @pointerdown.self="closePalette">
        <div class="cmd-panel" role="dialog" aria-modal="true" :aria-label="t('command.title')">
          <input
            ref="inputEl"
            v-model="query"
            class="cmd-input"
            type="text"
            role="combobox"
            aria-autocomplete="list"
            aria-expanded="true"
            aria-controls="cmd-list"
            :aria-activedescendant="activeId"
            :placeholder="t('command.placeholder')"
            @input="onInput"
            @compositionstart="onCompositionStart"
            @compositionend="onCompositionEnd"
          />
          <ul id="cmd-list" class="cmd-list" role="listbox">
            <template v-for="row in rows" :key="row.key">
              <li v-if="row.kind === 'header'" class="cmd-group-head" role="presentation">{{ row.label }}</li>
              <li v-else-if="row.kind === 'info'" class="cmd-info" role="presentation">{{ row.text }}</li>
              <li v-else-if="row.kind === 'tip'" class="cmd-tip" role="presentation">{{ row.text }}</li>
              <li
                v-else
                :id="`cmd-opt-${row.index}`"
                class="cmd-item"
                :class="{ active: row.index === active }"
                role="option"
                :aria-selected="row.index === active"
                @pointermove="active = row.index"
                @click="choose(row.item)"
              >
                <span class="cmd-label">{{ row.item.label }}</span>
                <span v-if="row.item.sub" class="cmd-group">{{ row.item.sub }}</span>
                <span v-else-if="row.item.group !== 'search'" class="cmd-group">{{
                  row.item.group === 'nav' ? t('command.groupNav') : t('command.groupAction')
                }}</span>
              </li>
            </template>
          </ul>
          <p v-if="!items.length && !searchActive" class="cmd-empty">{{ t('command.empty') }}</p>
          <div class="cmd-hint">{{ t('command.hint') }}</div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useHotkeys, focusPageSearch } from '@/composables/useHotkeys'
import { SEARCH_MIN_LENGTH, SEARCH_VISIBLE_LIMIT, normalizeQuery, useGlobalSearch } from '@/composables/useGlobalSearch'
import { filterCommands, visibleCommands } from '@/utils/commandRegistry'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()
const { paletteOpen, closePalette } = useHotkeys()
const search = useGlobalSearch()
const { groups, fileContext, loading: searchLoading } = search

const inputEl = ref(null)
const query = ref('')
const active = ref(0)
// 输入法组字期间不发请求:拼音字母先落进输入框,等 compositionend 再查一次
let composing = false
// 打开前的焦点:关闭时还回去,不丢给 body
let lastFocused = null

const searchActive = computed(() => normalizeQuery(query.value).length >= SEARCH_MIN_LENGTH)

const ctx = computed(() => ({
  isLoggedIn: userStore.isLoggedIn,
  isGuest: userStore.isGuest,
  isOps: userStore.isOps,
  hasPerm: userStore.hasPerm,
  theme: themeStore.theme,
  setTheme: (id) => themeStore.setTheme(id),
  focusSearch: focusPageSearch,
}))

const staticCommands = computed(() => {
  const list = visibleCommands(ctx.value).map((c) => ({ ...c, label: t(c.labelKey) }))
  return filterCommands(list, query.value)
})

// 一次遍历同时产出「键盘可选列表」与「带分组标题的渲染行」,保证两边索引一致
const view = computed(() => {
  const items = []
  const rows = []

  if (searchActive.value) {
    let budget = SEARCH_VISIBLE_LIMIT
    if (groups.value.length) rows.push({ kind: 'header', key: 'head-search', label: t('command.groupSearch') })
    for (const group of groups.value) {
      if (budget <= 0) break
      const list = group.items.slice(0, budget)
      if (!list.length) continue
      rows.push({ kind: 'header', key: `head-${group.source}`, label: t(`command.source.${group.source}`) })
      for (const item of list) {
        rows.push({ kind: 'option', key: item.id, index: items.length, item })
        items.push(item)
        budget -= 1
      }
    }
    if (!groups.value.length && !searchLoading.value) rows.push({ kind: 'info', key: 'search-empty', text: t('command.searchEmpty') })
    if (!fileContext.value) rows.push({ kind: 'tip', key: 'file-scope', text: t('command.fileScopeTip') })
    if (staticCommands.value.length) {
      rows.push({ kind: 'header', key: 'head-command', label: t('command.groupCommands') })
      for (const item of staticCommands.value) {
        rows.push({ kind: 'option', key: item.id, index: items.length, item })
        items.push(item)
      }
    }
    return { items, rows }
  }

  // 只敲了 1 个字:不够搜索门槛,给一句提示,静态命令照常可选中
  if (normalizeQuery(query.value).length === 1) {
    rows.push({ kind: 'info', key: 'search-hint', text: t('command.searchHint') })
  }
  for (const item of staticCommands.value) {
    rows.push({ kind: 'option', key: item.id, index: items.length, item })
    items.push(item)
  }
  return { items, rows }
})

const items = computed(() => view.value.items)
const rows = computed(() => view.value.rows)
const activeId = computed(() => (items.value.length ? `cmd-opt-${active.value}` : undefined))

// 明确采用「循环」:在首项按 ↑ 回到末项、末项按 ↓ 回到首项
const move = (delta) => {
  const n = items.value.length
  if (!n) return
  active.value = (active.value + delta + n) % n
}

const onPanelKeydown = (e) => {
  if (e.isComposing || e.keyCode === 229) return
  if (e.key === 'Escape') {
    e.preventDefault()
    e.stopPropagation()
    closePalette()
    return
  }
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    e.stopPropagation()
    move(e.key === 'ArrowDown' ? 1 : -1)
    return
  }
  if (e.key === 'Enter') {
    e.preventDefault()
    e.stopPropagation()
    choose(items.value[active.value])
  }
  // Tab 不劫持:留给浏览器做焦点移动
}

const choose = async (item) => {
  if (!item) return
  closePalette()
  // 等关闭 watch 把焦点还原完,再执行跳转/聚焦,否则「聚焦搜索」会被还原动作抢走焦点
  await nextTick()
  if (item.path) router.push(item.path).catch(() => {})
  else if (typeof item.run === 'function') item.run(ctx.value)
}

const onInput = (e) => {
  // v-model 与自定义 @input 同事件:以输入框真值为准,避免顺序依赖
  const value = e?.target?.value ?? query.value
  if (query.value !== value) query.value = value
  active.value = 0
  if (composing) return
  search.run(value)
}

const onCompositionStart = () => {
  composing = true
}

const onCompositionEnd = () => {
  composing = false
  active.value = 0
  search.run(query.value)
}

watch(paletteOpen, async (open) => {
  if (open) {
    lastFocused = document.activeElement
    query.value = ''
    active.value = 0
    search.reset()
    await nextTick()
    inputEl.value?.focus()
    document.addEventListener('keydown', onPanelKeydown, true)
    return
  }
  document.removeEventListener('keydown', onPanelKeydown, true)
  // 关闭即弃掉上一次的结果与进行中的响应,下次打开是干净的
  search.reset()
  const back = lastFocused
  lastFocused = null
  if (back && typeof back.focus === 'function' && document.contains(back)) back.focus()
}, { immediate: true })

// 结果集变短时把高亮收回边界内,避免 Enter 落在已不存在的项上
watch(
  () => items.value.length,
  (n) => {
    if (active.value >= n) active.value = Math.max(0, n - 1)
  },
)

// 路由变化(如点了面板里的页面,或点了别处链接)一律收起,避免面板悬在新页面上
watch(
  () => route.fullPath,
  () => {
    if (paletteOpen.value) closePalette()
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onPanelKeydown, true)
  search.reset()
})
</script>

<style scoped>
.cmd-scrim {
  position: fixed;
  inset: 0;
  z-index: var(--z-palette);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 12vh 16px 16px;
  background: var(--overlay-scrim);
  backdrop-filter: blur(var(--overlay-blur));
}
.cmd-panel {
  width: min(560px, 100%);
  max-height: min(76vh, 560px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  box-shadow: var(--dialog-shadow);
}
.cmd-input {
  width: 100%;
  padding: 14px 16px;
  border: none;
  border-bottom: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text);
  font-size: 15px;
  outline: none;
}
.cmd-input::placeholder {
  color: var(--color-text-tertiary);
}
.cmd-list {
  list-style: none;
  margin: 0;
  padding: 6px;
  overflow-y: auto;
  max-height: min(56vh, 440px); /* 约 12 条可见,超出滚动 */
}
.cmd-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 10px;
  cursor: pointer;
}
.cmd-item.active {
  background: rgba(var(--color-brand-rgb), 0.12);
}
.cmd-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--color-text);
}
.cmd-group {
  flex: none;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--color-text-tertiary);
}
.cmd-group-head {
  padding: 10px 10px 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-tertiary);
}
.cmd-info,
.cmd-tip {
  padding: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.cmd-tip {
  color: var(--color-text-tertiary);
}
.cmd-empty {
  margin: 0;
  padding: 18px 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
  text-align: center;
}
.cmd-hint {
  padding: 8px 16px 10px;
  border-top: 1px solid var(--color-border);
  font-size: 11px;
  color: var(--color-text-tertiary);
}
.cmd-fade-enter-active,
.cmd-fade-leave-active {
  transition:
    opacity 0.12s ease,
    transform 0.12s ease;
}
.cmd-fade-enter-from,
.cmd-fade-leave-to {
  opacity: 0;
  transform: translateY(2px);
}
/* 触屏:行高 ≥44px 好点;面板贴顶一点,把高度让给结果 */
@media (pointer: coarse) {
  .cmd-scrim {
    padding-top: 8vh;
  }
  .cmd-item {
    min-height: 44px;
    padding: 12px 10px;
  }
  .cmd-label {
    font-size: 15px;
  }
}
</style>
