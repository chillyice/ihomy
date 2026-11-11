<!-- 命令面板:Ctrl/⌘+K 唤起,鼠标/触控板设备专用(触屏不出现)。
     由 App.vue 懒加载,独立页(咔哒/比特口袋/壁纸/chillyice)与沉浸页不挂载。
     键盘:↑↓ 循环移动高亮、Enter 执行、Esc 关闭;Escape/方向键在 capture 阶段截住,
     避免底下的照片查看器 / 阅读器 / 游戏同时响应。 -->
<template>
  <Teleport to="body">
    <Transition name="cmd-fade">
      <div v-if="paletteOpen && hasPointer" class="cmd-scrim" @pointerdown.self="closePalette">
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
            @input="active = 0"
          />
          <ul id="cmd-list" class="cmd-list" role="listbox">
            <li
              v-for="(item, i) in items"
              :id="`cmd-opt-${i}`"
              :key="item.id"
              class="cmd-item"
              :class="{ active: i === active }"
              role="option"
              :aria-selected="i === active"
              @pointermove="active = i"
              @click="choose(item)"
            >
              <span class="cmd-label">{{ item.label }}</span>
              <span class="cmd-group">{{ item.group === 'nav' ? t('command.groupNav') : t('command.groupAction') }}</span>
            </li>
          </ul>
          <p v-if="!items.length" class="cmd-empty">{{ t('command.empty') }}</p>
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
import { useDevice } from '@/composables/useDevice'
import { useHotkeys, focusPageSearch } from '@/composables/useHotkeys'
import { filterCommands, visibleCommands } from '@/utils/commandRegistry'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()
const { hasPointer } = useDevice()
const { paletteOpen, closePalette } = useHotkeys()

// 独立页/沉浸页不挂载本组件;若其间按过 Ctrl/⌘+K,回到站内首帧先收掉残留打开态
if (paletteOpen.value) closePalette()

const inputEl = ref(null)
const query = ref('')
const active = ref(0)
// 打开前的焦点:关闭时还回去,不丢给 body
let lastFocused = null

const ctx = computed(() => ({
  isLoggedIn: userStore.isLoggedIn,
  isGuest: userStore.isGuest,
  isOps: userStore.isOps,
  hasPerm: userStore.hasPerm,
  theme: themeStore.theme,
  setTheme: (id) => themeStore.setTheme(id),
  focusSearch: focusPageSearch,
}))

const items = computed(() => {
  const list = visibleCommands(ctx.value).map((c) => ({ ...c, label: t(c.labelKey) }))
  return filterCommands(list, query.value)
})

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
  if (item.path) router.push(item.path)
  else if (typeof item.run === 'function') item.run(ctx.value)
}

watch(paletteOpen, async (open) => {
  if (open) {
    lastFocused = document.activeElement
    query.value = ''
    active.value = 0
    await nextTick()
    inputEl.value?.focus()
    document.addEventListener('keydown', onPanelKeydown, true)
    return
  }
  document.removeEventListener('keydown', onPanelKeydown, true)
  const back = lastFocused
  lastFocused = null
  if (back && typeof back.focus === 'function' && document.contains(back)) back.focus()
})

// 路由变化(如点了面板里的页面,或点了别处链接)一律收起,避免面板悬在新页面上
watch(
  () => route.fullPath,
  () => {
    if (paletteOpen.value) closePalette()
  },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onPanelKeydown, true)
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
  max-height: min(70vh, 480px);
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
  max-height: 320px; /* 约 8 条可见,超出滚动 */
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
  font-size: 11px;
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
</style>
