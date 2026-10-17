<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('tools.title') }}</span>
        <span v-if="sorting" class="sort-hint">{{ $t('tools.sort.hint') }}</span>
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
      <div
        v-for="tool in tools"
        :key="tool.id"
        v-a11y-click
        class="tool-card card"
        :class="{ 'is-sorting': sorting, 'is-dragging': dragId === tool.id }"
        :data-tool-id="tool.id"
        :aria-disabled="sorting ? 'true' : undefined"
        @click="openTool(tool)"
      >
        <div class="tool-icon">
          <el-icon :size="30"><component :is="tool.icon" /></el-icon>
        </div>
        <div class="tool-name">{{ $t(tool.titleKey) }}</div>
        <div class="tool-desc">{{ $t(tool.descKey) }}</div>
        <span class="tool-enter"
          >{{ $t('tools.enter') }}<el-icon><ArrowRight /></el-icon
        ></span>
        <button
          v-if="sorting"
          class="tool-grip"
          type="button"
          :data-grip-id="tool.id"
          :aria-label="$t('tools.sort.moveHint')"
          :title="$t('tools.sort.moveHint')"
          @pointerdown.prevent.stop="onGripDown($event, tool.id)"
          @keydown="onGripKeydown($event, tool.id)"
          @click.stop
        >
          <el-icon><Rank /></el-icon>
        </button>
      </div>
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
import { Tools, ArrowRight, MagicStick, Sunny, VideoPlay, Coin, Document, DataLine, Sort, Rank, Check } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { loadToolOrder, mergeToolOrder, moveToolBy, moveToolTo, saveToolOrder } from '@/utils/toolOrder'

const { t } = useI18n()
const router = useRouter()

// 卡片清单:新增工具只在这里加一行(默认排最后,已排过序的用户会自动追加到末尾)
const DEFAULT_TOOLS = [
  { id: 'mindmap', path: '/tools/mindmap', icon: Tools, titleKey: 'tools.mindmap.title', descKey: 'tools.mindmap.desc' },
  {
    id: 'ai-playground',
    path: '/tools/ai-playground',
    icon: MagicStick,
    titleKey: 'tools.aiPlayground.title',
    descKey: 'tools.aiPlayground.desc',
  },
  { id: 'light-lab', path: '/tools/light-lab', icon: Sunny, titleKey: 'tools.lightLab.title', descKey: 'tools.lightLab.desc' },
  { id: 'pdf', path: '/tools/pdf', icon: Document, titleKey: 'tools.pdf.title', descKey: 'tools.pdf.desc' },
  { id: 'json', path: '/tools/json', icon: DataLine, titleKey: 'tools.json.title', descKey: 'tools.json.desc' },
  { id: 'flash', path: '/tools/flash', icon: VideoPlay, titleKey: 'tools.flash.title', descKey: 'tools.flash.desc' },
  { id: 'loan', path: '/tools/loan', icon: Coin, titleKey: 'tools.loan.title', descKey: 'tools.loan.desc' },
  { id: 'gba', path: '/tools/gba', icon: VideoPlay, titleKey: 'tools.gba.title', descKey: 'tools.gba.desc' },
]
const defaultIds = DEFAULT_TOOLS.map((tool) => tool.id)

const order = ref(loadToolOrder(defaultIds))
const tools = computed(() => {
  const byId = new Map(DEFAULT_TOOLS.map((tool) => [tool.id, tool]))
  return order.value.map((id) => byId.get(id)).filter(Boolean)
})
const persist = () => saveToolOrder(order.value)

const sorting = ref(false)
const startSort = () => {
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
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
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
