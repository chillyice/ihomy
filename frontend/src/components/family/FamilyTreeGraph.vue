<!-- 家谱关系图:SVG 画关系线 + 绝对定位卡片。
     点击卡片选中(最多两人),由父组件计算称谓;右上角小铅笔进入编辑。
     关系线:实线=主亲子,虚线=补充父母/配偶侧亲属,双色横线=夫妻;悬停高亮同一关系组。 -->
<template>
  <div class="ftg-shell">
    <div class="ftg-scroll">
      <div v-if="!members.length" class="ftg-empty">
        <el-empty :description="$t('tree.emptyHint')" :image-size="90" />
      </div>
      <div v-else class="ftg-canvas" :style="{ width: `${graph.width}px`, height: `${graph.height}px` }">
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
            <img v-if="entry.member.photo" :src="entry.member.photo" :alt="entry.member.name" />
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
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { EditPen, UserFilled } from '@element-plus/icons-vue'
import { buildFamilyGraph } from '@/utils/familyGraph'

const props = defineProps({
  members: { type: Array, default: () => [] },
  selected: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:selected', 'edit'])

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
  'edge-parent': edge.type === 'parent' && edge.primary,
  'edge-extra': edge.type === 'parent' && !edge.primary,
  dim: !edgeTouchesFocus(edge),
})

const isDim = (id) => {
  if (!focusUnits.value.size) return false
  const unitId = graph.value.nodes.get(id)?.unitId
  return !focusUnits.value.has(unitId)
}

const toggle = (id) => {
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
  width: 100%;
}
.ftg-scroll {
  overflow: auto;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background:
    radial-gradient(circle at 1px 1px, color-mix(in srgb, var(--color-border) 55%, transparent) 1px, transparent 0) 0 0 / 22px 22px,
    var(--color-card);
  max-height: min(68vh, 760px);
}
.ftg-empty {
  padding: 40px 0;
}
.ftg-canvas {
  position: relative;
  min-width: 100%;
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
.edge-extra .ftg-line {
  stroke: var(--color-accent, #c9807a);
  stroke-width: 1.6;
  stroke-dasharray: 5 5;
  opacity: 0.75;
}
.edge-spouse .ftg-line {
  stroke: #e85d75;
  stroke-width: 3;
  stroke-dasharray: 1 5;
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
  .ftg-scroll {
    max-height: 62vh;
  }
}
</style>
