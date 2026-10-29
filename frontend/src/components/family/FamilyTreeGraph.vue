<!-- 家谱关系图:SVG 画关系线 + 绝对定位卡片,画布支持拖动平移与缩放。
     点击卡片选中(最多两人),由父组件计算称谓;右上角小铅笔进入编辑。
     关系线:实线=亲子(父母与子女),红色横线=夫妻;悬停高亮同一关系组。 -->
<template>
  <div class="ftg-shell">
    <div class="ftg-bar">
      <el-button-group>
        <el-button size="small" :title="$t('tree.zoomOut')" :aria-label="$t('tree.zoomOut')" @click="zoomBy(1 / 1.2)">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <el-button size="small" :title="$t('tree.zoomFit')" :aria-label="$t('tree.zoomFit')" @click="fit()">
          <el-icon><FullScreen /></el-icon>
        </el-button>
        <el-button size="small" :title="$t('tree.zoomIn')" :aria-label="$t('tree.zoomIn')" @click="zoomBy(1.2)">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
      </el-button-group>
      <span class="ftg-zoom">{{ zoomLabel }}</span>
      <span class="ftg-hint">{{ $t('tree.panHint') }}</span>
    </div>

    <div
      ref="viewport"
      class="ftg-viewport"
      :class="{ dragging }"
      @wheel.prevent="onWheel"
      @pointerdown="onPointerDown"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointercancel="onPointerUp"
    >
      <div v-if="!members.length" class="ftg-empty">
        <el-empty :description="$t('tree.emptyHint')" :image-size="90" />
      </div>
      <div v-else class="ftg-stage" :style="stageStyle">
        <div class="ftg-canvas" :style="{ width: `${graph.width}px`, height: `${graph.height}px` }">
          <svg class="ftg-edges" :width="graph.width" :height="graph.height" aria-hidden="true">
            <g v-for="edge in graph.edges" :key="edge.key" :class="edgeClass(edge)">
              <path :d="edge.path || `M${edge.x1},${edge.y1} L${edge.x2},${edge.y2}`" class="ftg-line" />
              <text v-if="edge.type === 'spouse'" :x="edge.heartX" :y="edge.heartY" text-anchor="middle" class="ftg-heart">♥</text>
            </g>
          </svg>

          <div
            v-for="entry in nodeEntries"
            :key="entry.id"
            v-a11y-click
            class="ftg-node"
            :class="[`g${entry.member.gender || 0}`, { selected: selected.includes(entry.id), dim: isDim(entry.id) }]"
            :style="{ left: `${entry.pos.x}px`, top: `${entry.pos.y}px`, width: `${entry.pos.width}px`, height: `${entry.pos.height}px` }"
            role="button"
            :tabindex="0"
            :aria-pressed="selected.includes(entry.id)"
            :aria-label="`${entry.member.name} ${$t('tree.selectHint')}`"
            @click="toggle(entry.id)"
            @mouseenter="hoverId = entry.id"
            @mouseleave="hoverId = null"
          >
            <button class="ftg-edit" type="button" :title="$t('tree.edit')" @click.stop="$emit('edit', entry.member)">
              <el-icon><EditPen /></el-icon>
            </button>
            <div class="ftg-photo">
              <img v-if="entry.member.photo" :src="entry.member.photo" :alt="entry.member.name" draggable="false" />
              <span v-else class="ftg-fallback">{{ genderIcon(entry.member.gender) }}</span>
            </div>
            <div class="ftg-name" :title="entry.member.name">{{ entry.member.name }}</div>
            <div v-if="birthYear(entry.member)" class="ftg-year">{{ birthYear(entry.member) }}</div>
            <div v-if="entry.member.userName" class="ftg-account" :title="$t('tree.account')">
              <el-icon><UserFilled /></el-icon>{{ entry.member.userName }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditPen, FullScreen, UserFilled, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { buildFamilyGraph } from '@/utils/familyGraph'

const props = defineProps({
  members: { type: Array, default: () => [] },
  selected: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:selected', 'edit'])

const MIN_SCALE = 0.2
const MAX_SCALE = 2.5
const DRAG_THRESHOLD = 4

const hoverId = ref(null)
const graph = computed(() => buildFamilyGraph(props.members))
const nodeEntries = computed(() => {
  const list = []
  for (const member of props.members) {
    const pos = graph.value.nodes.get(member.id)
    if (pos) list.push({ id: member.id, member, pos })
  }
  return list
})

// ---------- 画板位移与缩放 ----------
const viewport = ref(null)
const scale = ref(1)
const offsetX = ref(0)
const offsetY = ref(0)
const dragging = ref(false)

const pointers = new Map()
let start = null
let pinch = null
let suppressClickUntil = 0
let userAdjusted = false
let resizeObserver = null

const stageStyle = computed(() => ({
  transform: `translate3d(${offsetX.value}px, ${offsetY.value}px, 0) scale(${scale.value})`,
}))
const zoomLabel = computed(() => `${Math.round(scale.value * 100)}%`)
const clampScale = (value) => Math.min(MAX_SCALE, Math.max(MIN_SCALE, value))

const fit = () => {
  const vp = viewport.value
  if (!vp) return false
  const { width, height } = graph.value
  // 面板还没布局完(宽高为 0)时先不算,等 fitSoon 重试或 ResizeObserver 通知
  if (!width || !height || !vp.clientWidth || !vp.clientHeight) return false
  const fitScale = clampScale(Math.min(vp.clientWidth / width, vp.clientHeight / height) * 0.96)
  scale.value = fitScale
  offsetX.value = (vp.clientWidth - width * fitScale) / 2
  offsetY.value = (vp.clientHeight - height * fitScale) / 2
  userAdjusted = false
  return true
}

// 挂载/换数据时容器可能还没量出尺寸(Tab 懒渲染、独立页首帧),
// 量不到就每 150ms 再试,最多约 3 秒,避免自适应被时序吃掉
let fitTimer = null
let fitRetries = 0
const fitSoon = () => {
  clearTimeout(fitTimer)
  fitRetries = 0
  const tryFit = () => {
    if (userAdjusted) return
    if (fit()) return
    if (fitRetries < 20) {
      fitRetries += 1
      fitTimer = setTimeout(tryFit, 150)
    }
  }
  tryFit()
}

// 以某个视口内坐标为锚点缩放,锚点下的内容保持不动
const zoomAt = (factor, anchorX, anchorY) => {
  const next = clampScale(scale.value * factor)
  const ratio = next / scale.value
  if (ratio === 1) return
  offsetX.value = anchorX - (anchorX - offsetX.value) * ratio
  offsetY.value = anchorY - (anchorY - offsetY.value) * ratio
  scale.value = next
  userAdjusted = true
}

const zoomBy = (factor) => {
  const vp = viewport.value
  if (!vp) return
  zoomAt(factor, vp.clientWidth / 2, vp.clientHeight / 2)
}

const viewportPoint = (clientX, clientY) => {
  const rect = viewport.value.getBoundingClientRect()
  return { x: clientX - rect.left, y: clientY - rect.top }
}

const onWheel = (event) => {
  const point = viewportPoint(event.clientX, event.clientY)
  zoomAt(Math.exp(-event.deltaY * 0.0015), point.x, point.y)
}

const onPointerDown = (event) => {
  if (event.pointerType === 'mouse' && event.button !== 0) return
  const vp = viewport.value
  if (!vp) return
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })
  try {
    vp.setPointerCapture(event.pointerId)
  } catch {
    // 合成事件或已释放的指针不支持捕获,不致命
  }
  dragging.value = true
  if (pointers.size === 1) start = { x: event.clientX, y: event.clientY }
  if (pointers.size === 2) {
    const [a, b] = [...pointers.values()]
    const mid = viewportPoint((a.x + b.x) / 2, (a.y + b.y) / 2)
    pinch = {
      dist: Math.hypot(a.x - b.x, a.y - b.y) || 1,
      mid,
      scale: scale.value,
      offsetX: offsetX.value,
      offsetY: offsetY.value,
    }
  }
}

const onPointerMove = (event) => {
  const prev = pointers.get(event.pointerId)
  if (!prev) return
  const dx = event.clientX - prev.x
  const dy = event.clientY - prev.y
  pointers.set(event.pointerId, { x: event.clientX, y: event.clientY })

  if (pinch && pointers.size >= 2) {
    const [a, b] = [...pointers.values()]
    const dist = Math.hypot(a.x - b.x, a.y - b.y) || 1
    const mid = viewportPoint((a.x + b.x) / 2, (a.y + b.y) / 2)
    const next = clampScale(pinch.scale * (dist / pinch.dist))
    const ratio = next / pinch.scale
    offsetX.value = mid.x - (pinch.mid.x - pinch.offsetX) * ratio
    offsetY.value = mid.y - (pinch.mid.y - pinch.offsetY) * ratio
    scale.value = next
    userAdjusted = true
    return
  }

  offsetX.value += dx
  offsetY.value += dy
  userAdjusted = true
  if (start && Math.hypot(event.clientX - start.x, event.clientY - start.y) > DRAG_THRESHOLD) {
    // 拖动过就别再当成点击,避免松手时误选成员
    suppressClickUntil = Date.now() + 400
  }
}

const onPointerUp = (event) => {
  pointers.delete(event.pointerId)
  try {
    viewport.value?.releasePointerCapture(event.pointerId)
  } catch {
    // 指针未被捕获时无需释放
  }
  if (pointers.size < 2) pinch = null
  if (!pointers.size) {
    dragging.value = false
    start = null
  }
}

const onWindowResize = () => {
  if (!userAdjusted) fit()
}

onMounted(() => {
  fitSoon()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      if (!userAdjusted) fit()
    })
    if (viewport.value) resizeObserver.observe(viewport.value)
  }
  window.addEventListener('resize', onWindowResize)
})

onBeforeUnmount(() => {
  clearTimeout(fitTimer)
  resizeObserver?.disconnect()
  resizeObserver = null
  window.removeEventListener('resize', onWindowResize)
  pointers.clear()
})

watch(
  () => props.members,
  () => nextTick(fitSoon),
)

// 当前聚焦的单元:悬停优先,其次已选中的两人
const focusUnits = computed(() => {
  const units = new Set()
  const ids = hoverId.value != null ? [hoverId.value] : props.selected
  for (const id of ids) {
    const unitId = graph.value.nodes.get(id)?.unitId
    if (unitId) units.add(unitId)
  }
  return units
})

const edgeTouchesFocus = (edge) => {
  if (!focusUnits.value.size) return true
  if (edge.type === 'spouse') return focusUnits.value.has(edge.unitId)
  return focusUnits.value.has(edge.fromUnitId) || focusUnits.value.has(edge.toUnitId)
}

const edgeClass = (edge) => ({
  'edge-spouse': edge.type === 'spouse',
  'edge-parent': edge.type === 'parent',
  dim: !edgeTouchesFocus(edge),
})

const isDim = (id) => {
  if (!focusUnits.value.size) return false
  const unitId = graph.value.nodes.get(id)?.unitId
  return !focusUnits.value.has(unitId)
}

const toggle = (id) => {
  if (Date.now() < suppressClickUntil) return
  const next = props.selected.includes(id) ? props.selected.filter((x) => x !== id) : [...props.selected, id].slice(-2)
  emit('update:selected', next)
}

// 成员被删除或刷新后,清掉已不存在的选中项
watch(
  () => props.members,
  (list) => {
    const ids = new Set(list.map((m) => m.id))
    const kept = props.selected.filter((id) => ids.has(id))
    if (kept.length !== props.selected.length) emit('update:selected', kept)
  },
)

const genderIcon = (g) => (g === 1 ? '👨' : g === 2 ? '👩' : '🧑')
const birthYear = (m) => (m.birthDate ? String(m.birthDate).slice(0, 4) : '')
</script>

<style scoped>
.ftg-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.ftg-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ftg-zoom {
  min-width: 40px;
  color: var(--color-text-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.ftg-hint {
  color: var(--color-text-2);
  font-size: 12px;
  opacity: 0.75;
}
.ftg-viewport {
  position: relative;
  overflow: hidden;
  height: clamp(420px, 68vh, 780px);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background:
    radial-gradient(circle at 1px 1px, color-mix(in srgb, var(--color-border) 55%, transparent) 1px, transparent 0) 0 0 / 22px 22px,
    var(--color-card);
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.ftg-viewport.dragging {
  cursor: grabbing;
}
.ftg-empty {
  padding: 40px 0;
}
.ftg-stage {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
}
.ftg-canvas {
  position: relative;
}
.ftg-edges {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: visible;
}
.ftg-line {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  transition:
    opacity 0.15s,
    stroke-width 0.15s;
}
.edge-parent .ftg-line {
  stroke: var(--color-primary);
  stroke-width: 2;
}
.edge-spouse .ftg-line {
  stroke: #e85d75;
  stroke-width: 2.5;
}
.ftg-heart {
  fill: #e85d75;
  font-size: 11px;
}
.ftg-edges g.dim {
  opacity: 0.18;
}
.ftg-edges g:not(.dim) .ftg-line {
  stroke-width: 2.6;
}
.ftg-node {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 6px 6px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  box-shadow: 0 2px 6px rgb(0 0 0 / 5%);
  cursor: pointer;
  transition:
    transform 0.15s,
    box-shadow 0.15s,
    opacity 0.15s,
    border-color 0.15s;
}
.ftg-node:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgb(0 0 0 / 12%);
}
.ftg-node.dim {
  opacity: 0.4;
}
.ftg-node.selected {
  border-color: var(--color-primary);
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--color-primary) 35%, transparent),
    0 8px 18px rgb(0 0 0 / 10%);
}
.ftg-node.g1 {
  border-top: 3px solid #5b8def;
}
.ftg-node.g2 {
  border-top: 3px solid #e87a9b;
}
.ftg-edit {
  position: absolute;
  top: 4px;
  right: 4px;
  display: inline-flex;
  padding: 2px;
  border: none;
  border-radius: 6px;
  background: color-mix(in srgb, var(--color-text) 8%, transparent);
  color: var(--color-text-secondary);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}
.ftg-node:hover .ftg-edit,
.ftg-node:focus-visible .ftg-edit {
  opacity: 1;
}
.ftg-edit:hover {
  color: var(--color-primary);
}
.ftg-photo {
  width: 42px;
  height: 42px;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 50%;
  background: #eef2f7;
}
.ftg-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ftg-fallback {
  font-size: 20px;
}
.ftg-name {
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ftg-year {
  color: var(--color-text-secondary);
  font-size: 10px;
  line-height: 1.4;
}
.ftg-account {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  max-width: 100%;
  color: var(--color-accent, var(--color-primary));
  font-size: 9px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ftg-account :deep(.el-icon) {
  flex: none;
  font-size: 10px;
}
@media (max-width: 768px) {
  .ftg-viewport {
    height: clamp(340px, 60vh, 640px);
  }
  .ftg-hint {
    display: none;
  }
}
</style>
