<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <button class="toolbar-title" type="button" :aria-pressed="grouped" :title="$t('tools.group.toggleHint')" @click="toggleGrouped">
          <span class="section-label">{{ $t('tools.title') }}</span>
          <el-icon class="title-caret" :class="{ open: grouped }" :size="14"><ArrowDown /></el-icon>
        </button>
        <span v-if="grouped" class="sort-hint">{{ $t('tools.group.hint') }}</span>
        <span v-else-if="sorting" class="sort-hint">{{ $t('tools.sort.hint') }}</span>
      </div>
      <div class="tb-right">
        <template v-if="sorting">
          <el-button size="small" @click="resetOrder">{{ $t('tools.sort.reset') }}</el-button>
          <el-button size="small" type="primary" :icon="Check" @click="finishSort">{{ $t('tools.sort.done') }}</el-button>
        </template>
        <el-button v-else size="small" :icon="Sort" @click="startSort">{{ $t('tools.sort.start') }}</el-button>
      </div>
    </PageToolbar>

    <div class="tool-grid">
      <template v-for="item in gridItems" :key="item.key">
        <div v-if="item.type === 'heading'" class="tool-group-head">
          <span class="group-label">{{ $t(item.labelKey) }}</span>
          <span class="group-count">{{ item.count }}</span>
        </div>
        <div
          v-else
          v-a11y-click
          class="tool-card card"
          :class="{ 'is-sorting': sorting, 'is-dragging': dragId === item.tool.id }"
          :data-tool-id="item.tool.id"
          :aria-disabled="sorting ? 'true' : undefined"
          @click="openTool(item.tool)"
        >
          <div class="tool-icon">
            <el-icon :size="30"><component :is="item.tool.icon" /></el-icon>
          </div>
          <div class="tool-name">
            <span>{{ $t(item.tool.titleKey) }}</span>
            <span class="tool-tag">{{ $t(item.tool.tagKey) }}</span>
          </div>
          <div class="tool-desc">{{ $t(item.tool.descKey) }}</div>
          <span class="tool-enter"
            >{{ $t('tools.enter') }}<el-icon><ArrowRight /></el-icon
          ></span>
          <button
            v-if="sorting"
            class="tool-grip"
            type="button"
            :data-grip-id="item.tool.id"
            :aria-label="$t('tools.sort.moveHint')"
            :title="$t('tools.sort.moveHint')"
            @pointerdown.prevent.stop="onGripDown($event, item.tool.id)"
            @keydown="onGripKeydown($event, item.tool.id)"
            @click.stop
          >
            <el-icon><Rank /></el-icon>
          </button>
        </div>
      </template>
      <div class="tool-card card tool-disabled">
        <div class="tool-icon">
          <el-icon :size="30"><MagicStick /></el-icon>
        </div>
        <div class="tool-name">{{ $t('tools.more') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Tools,
  ArrowRight,
  ArrowDown,
  MagicStick,
  Sunny,
  VideoPlay,
  Coin,
  Document,
  DataLine,
  Switch,
  Operation,
  Sort,
  Rank,
  Check,
  Connection,
  Share,
} from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { loadToolOrder, mergeToolOrder, moveToolBy, moveToolTo, saveToolOrder } from '@/utils/toolOrder'

const { t } = useI18n()
const router = useRouter()

// 卡片清单:新增工具只在这里加一行(默认排最后,已排过序的用户会自动追加到末尾)
// category 决定「按分类展示」时的分组,tagKey 是卡片名旁的小标签
const DEFAULT_TOOLS = [
  {
    id: 'mindmap',
    path: '/tools/mindmap',
    icon: Tools,
    titleKey: 'tools.mindmap.title',
    descKey: 'tools.mindmap.desc',
    category: 'design',
    tagKey: 'tools.tags.shared',
  },
  {
    id: 'ai-playground',
    path: '/tools/ai-playground',
    icon: MagicStick,
    titleKey: 'tools.aiPlayground.title',
    descKey: 'tools.aiPlayground.desc',
    category: 'dev',
    tagKey: 'tools.tags.ai',
  },
  {
    id: 'light-lab',
    path: '/tools/light-lab',
    icon: Sunny,
    titleKey: 'tools.lightLab.title',
    descKey: 'tools.lightLab.desc',
    category: 'design',
    tagKey: 'tools.tags.threeD',
  },
  {
    id: 'pdf',
    path: '/tools/pdf',
    icon: Document,
    titleKey: 'tools.pdf.title',
    descKey: 'tools.pdf.desc',
    category: 'document',
    tagKey: 'tools.tags.local',
  },
  {
    id: 'json',
    path: '/tools/json',
    icon: DataLine,
    titleKey: 'tools.json.title',
    descKey: 'tools.json.desc',
    category: 'dev',
    tagKey: 'tools.tags.local',
  },
  {
    id: 'codec',
    path: '/tools/codec',
    icon: Switch,
    titleKey: 'tools.codec.title',
    descKey: 'tools.codec.desc',
    category: 'dev',
    tagKey: 'tools.tags.local',
  },
  {
    id: 'base',
    path: '/tools/base',
    icon: Operation,
    titleKey: 'tools.base.title',
    descKey: 'tools.base.desc',
    category: 'dev',
    tagKey: 'tools.tags.local',
  },
  {
    id: 'packet',
    path: '/tools/packet',
    icon: Connection,
    titleKey: 'tools.packet.title',
    descKey: 'tools.packet.desc',
    category: 'dev',
    tagKey: 'tools.tags.local',
  },
  {
    id: 'subnet',
    path: '/tools/subnet',
    icon: Share,
    titleKey: 'tools.subnet.title',
    descKey: 'tools.subnet.desc',
    category: 'dev',
    tagKey: 'tools.tags.local',
  },
  {
    id: 'flash',
    path: '/tools/flash',
    icon: VideoPlay,
    titleKey: 'tools.flash.title',
    descKey: 'tools.flash.desc',
    category: 'fun',
    tagKey: 'tools.tags.localPlay',
  },
  {
    id: 'loan',
    path: '/tools/loan',
    icon: Coin,
    titleKey: 'tools.loan.title',
    descKey: 'tools.loan.desc',
    category: 'life',
    tagKey: 'tools.tags.offline',
  },
  {
    id: 'gba',
    path: '/tools/gba',
    icon: VideoPlay,
    titleKey: 'tools.gba.title',
    descKey: 'tools.gba.desc',
    category: 'fun',
    tagKey: 'tools.tags.localPlay',
  },
]
const defaultIds = DEFAULT_TOOLS.map((tool) => tool.id)

// 分类顺序固定(不跟随用户排序):分组视图只决定「怎么排」,不改变每组内部的用户顺序
const CATEGORIES = [
  { key: 'design', labelKey: 'tools.categories.design' },
  { key: 'document', labelKey: 'tools.categories.document' },
  { key: 'dev', labelKey: 'tools.categories.dev' },
  { key: 'life', labelKey: 'tools.categories.life' },
  { key: 'fun', labelKey: 'tools.categories.fun' },
]

const order = ref(loadToolOrder(defaultIds))
const tools = computed(() => {
  const byId = new Map(DEFAULT_TOOLS.map((tool) => [tool.id, tool]))
  return order.value.map((id) => byId.get(id)).filter(Boolean)
})
const persist = () => saveToolOrder(order.value)

// 分类视图偏好与卡片顺序一样只存本机;分组标题直接插进同一个网格(占满整行)
const GROUPED_KEY = 'ihomy:tools:grouped:v1'
const grouped = ref(
  (() => {
    try {
      return localStorage.getItem(GROUPED_KEY) === '1'
    } catch {
      return false
    }
  })(),
)
const gridItems = computed(() => {
  if (!grouped.value) return tools.value.map((tool) => ({ type: 'card', key: tool.id, tool }))
  const items = []
  for (const cat of CATEGORIES) {
    const group = tools.value.filter((tool) => tool.category === cat.key)
    if (!group.length) continue
    items.push({ type: 'heading', key: `cat-${cat.key}`, labelKey: cat.labelKey, count: group.length })
    for (const tool of group) items.push({ type: 'card', key: tool.id, tool })
  }
  return items
})

const sorting = ref(false)
const setGrouped = (val) => {
  grouped.value = val
  try {
    localStorage.setItem(GROUPED_KEY, val ? '1' : '0')
  } catch {
    // 隐私模式/配额满时不落盘,本次会话内仍然生效
  }
}
const toggleGrouped = () => {
  // 分组视图下拖拽跨类会让人误解顺序,进入任一模式前先互斥掉另一个
  if (sorting.value) sorting.value = false
  setGrouped(!grouped.value)
}
const startSort = () => {
  setGrouped(false)
  sorting.value = true
}
const finishSort = () => {
  sorting.value = false
}
const resetOrder = () => {
  order.value = mergeToolOrder(null, defaultIds)
  persist()
  ElMessage.success(t('tools.sort.resetDone'))
}
const openTool = (tool) => {
  if (!sorting.value) router.push(tool.path)
}

// 拖拽排序:只认卡片右上角的把手,pointer 事件同时覆盖鼠标与触屏
const dragId = ref(null)
let moveHandler = null
let upHandler = null

const detachDragListeners = () => {
  if (moveHandler) window.removeEventListener('pointermove', moveHandler)
  if (upHandler) {
    window.removeEventListener('pointerup', upHandler)
    window.removeEventListener('pointercancel', upHandler)
  }
  moveHandler = null
  upHandler = null
  dragId.value = null
}

const onGripDown = (event, id) => {
  if (!sorting.value) return
  if (event.pointerType === 'mouse' && event.button !== 0) return
  detachDragListeners()
  dragId.value = id
  moveHandler = (e) => {
    const el = document.elementFromPoint(e.clientX, e.clientY)?.closest?.('[data-tool-id]')
    const targetId = el?.getAttribute('data-tool-id')
    if (!targetId || targetId === dragId.value) return
    const next = moveToolTo(order.value, dragId.value, targetId)
    if (next !== order.value) {
      order.value = next
      persist()
    }
  }
  upHandler = detachDragListeners
  window.addEventListener('pointermove', moveHandler)
  window.addEventListener('pointerup', upHandler)
  window.addEventListener('pointercancel', upHandler)
}

// 键盘:把手聚焦后按方向键移动,不依赖拖拽也能完成排序
const onGripKeydown = (event, id) => {
  const delta = event.key === 'ArrowUp' || event.key === 'ArrowLeft' ? -1 : event.key === 'ArrowDown' || event.key === 'ArrowRight' ? 1 : 0
  if (!delta) return
  event.preventDefault()
  const next = moveToolBy(order.value, id, delta)
  if (next === order.value) return
  order.value = next
  persist()
  nextTick(() => document.querySelector(`.tool-grip[data-grip-id="${id}"]`)?.focus())
}

onBeforeUnmount(detachDragListeners)
</script>

<style scoped>
/* 工具栏标题:.section-label 的 16px 下边距是给正文分区标题用的,放进工具栏会把文字顶得比右侧按钮高,
   这里清零并用 inline-flex 与按钮同中线;标题本身是「平铺 / 分类」视图开关 */
.toolbar-title {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.toolbar-title .section-label {
  margin: 0;
}
.toolbar-title:hover .section-label,
.toolbar-title:focus-visible .section-label {
  color: var(--color-accent);
}
.title-caret {
  color: var(--color-text-secondary);
  transition: transform 0.2s ease;
}
.title-caret.open {
  transform: rotate(180deg);
}
.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.tool-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease;
  contain: layout style;
}
.tool-card:hover {
  transform: translateY(-3px);
}
.tool-card.is-sorting {
  cursor: default;
  border-style: dashed;
}
.tool-card.is-sorting:hover {
  transform: none;
}
.tool-card.is-dragging {
  opacity: 0.5;
}
.tool-disabled {
  cursor: default;
  opacity: 0.55;
  border-style: dashed;
}
.tool-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent-weak, rgba(184, 140, 110, 0.14));
  color: var(--color-accent, var(--color-brand));
  margin-bottom: 14px;
}
.tool-name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}
.tool-tag {
  flex-shrink: 0;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--color-accent);
  background: rgba(168, 72, 58, 0.1);
  border: 1px solid rgba(168, 72, 58, 0.18);
}
html.dark .tool-tag {
  color: #e19a8c;
  background: rgba(225, 154, 140, 0.12);
  border-color: rgba(225, 154, 140, 0.2);
}
.tool-group-head {
  grid-column: 1 / -1;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.group-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}
.group-count {
  font-size: 11px;
  color: var(--color-text-secondary);
  opacity: 0.75;
}
.tool-desc {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
  line-height: 1.5;
}
.tool-enter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--color-accent, var(--color-brand));
}
.tool-grip {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-card);
  color: var(--color-text-secondary, #8a8a8a);
  cursor: grab;
  touch-action: none;
}
.tool-grip:hover {
  color: var(--color-accent, var(--color-brand));
}
.tool-grip:active {
  cursor: grabbing;
}
.sort-hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
</style>
