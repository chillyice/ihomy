<template>
  <div class="fp-canvas" ref="wrapRef">
    <svg ref="svgRef" class="fp-svg" @wheel.prevent="onWheel" @pointerdown="onSvgDown" @pointermove="onHoverMove" @click="onCanvasClick">
      <g :transform="`translate(${view.tx},${view.ty}) scale(${view.k})`">
        <image v-if="imageUrl" :href="imageUrl" x="0" y="0" class="fp-bg" />
        <!-- 房间 -->
        <g v-for="r in roomsLocal" :key="r.id">
          <polygon
            :points="pts(r.poly)"
            class="fp-room"
            :class="{ 'is-hit': hitRoomIds.includes(r.id) }"
            @pointerdown.stop="mode === 'edit' ? onRoomDown($event, r) : null"
          />
          <text v-if="r.name" :x="r.cx" :y="r.cy" class="fp-room-label">{{ r.name }}</text>
        </g>
        <!-- 家具 -->
        <g v-for="f in placedFurnitures" :key="f.id">
          <rect
            :x="f.x" :y="f.y" :width="f.w" :height="f.h"
            class="fp-furn"
            @pointerdown.stop="mode === 'edit' ? onFurnDown($event, f) : null"
            @dblclick="mode === 'view' ? $emit('select-furniture', f.id) : null"
          />
          <text v-if="f.w > 40 && f.h > 18" :x="f.x + f.w / 2" :y="f.y + f.h / 2" class="fp-furn-label">{{ f.name }}</text>
          <template v-if="mode === 'edit'">
            <rect class="fp-resize" :x="f.x + f.w - 6" :y="f.y + f.h - 6" width="12" height="12" @pointerdown.stop="onFurnResizeDown($event, f)" />
          </template>
        </g>
        <!-- 物品 -->
        <g v-for="it in visibleItems" :key="it.id">
          <circle
            :cx="it.ax" :cy="it.ay" r="6"
            class="fp-item"
            :class="{ 'is-hit': highlightItemIds.includes(it.id) }"
            @pointerdown.stop="mode === 'edit' ? onItemDown($event, it) : null"
          />
        </g>
        <!-- 编辑态手柄 -->
        <template v-if="mode === 'edit'">
          <g v-for="r in roomsLocal" :key="'h' + r.id">
            <circle v-for="(p, i) in r.poly" :key="'v' + i"
                    :cx="p.x" :cy="p.y" r="6" class="fp-handle"
                    :class="{ 'is-snapped': isSnapping(r, i) }"
                    @pointerdown.stop="onVertexDown($event, r, i)"
                    @dblclick.stop="removeVertex(r, i)" />
            <rect v-for="(m, i) in r.mids" :key="'m' + i"
                  :x="m.x - 5" :y="m.y - 5" width="10" height="10" class="fp-edge-handle"
                  @pointerdown.stop="onEdgeDown($event, r, i)" />
          </g>
          <!-- hover 边加号 -->
          <g v-if="hoverEdge" class="fp-hover-add">
            <circle :cx="hoverEdge.point.x" :cy="hoverEdge.point.y" r="9" class="fp-hover-ring" />
            <line :x1="hoverEdge.point.x - 4" :y1="hoverEdge.point.y" :x2="hoverEdge.point.x + 4" :y2="hoverEdge.point.y" class="fp-hover-plus" />
            <line :x1="hoverEdge.point.x" :y1="hoverEdge.point.y - 4" :x2="hoverEdge.point.x" :y2="hoverEdge.point.y + 4" class="fp-hover-plus" />
          </g>
        </template>
        <!-- 正在画的房间 -->
        <polygon v-if="drawing.poly && drawing.poly.length" :points="pts(drawing.poly)" class="fp-drawing" />
        <rect v-if="drawing.rect" :x="drawing.rect.x" :y="drawing.rect.y" :width="drawing.rect.w" :height="drawing.rect.h" class="fp-drawing" />
      </g>
    </svg>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'

const props = defineProps({
  mode: { type: String, default: 'view' },
  rooms: { type: Array, default: () => [] },
  furnitures: { type: Array, default: () => [] },
  items: { type: Array, default: () => [] },
  imageUrl: { type: String, default: null },
  highlightItemIds: { type: Array, default: () => [] },
  selectedFurnitureId: { type: Number, default: null },
  tool: { type: String, default: 'select' },
})
const emit = defineEmits(['save-room', 'save-furniture', 'save-item', 'create-room', 'select-furniture'])

const wrapRef = ref(null)
const svgRef = ref(null)
const view = ref({ tx: 0, ty: 0, k: 1 })
const drawing = ref({ poly: null, rect: null })
const drag = ref(null)
const hoverEdge = ref(null)
let justDragged = false

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v))

// 顶点轴对齐吸附:边贴近横/纵轴时吸附到 prev/next 的 x/y,辅助调节成矩形
const snapVertex = (prev, next, px, py, threshold = 6) => {
  let sx = px; let sy = py; let snapped = false
  if (Math.abs(px - prev.x) < threshold) { sx = prev.x; snapped = true }
  if (Math.abs(py - prev.y) < threshold) { sy = prev.y; snapped = true }
  if (Math.abs(px - next.x) < threshold) { sx = next.x; snapped = true }
  if (Math.abs(py - next.y) < threshold) { sy = next.y; snapped = true }
  return { x: sx, y: sy, snapped }
}

const projectToSegment = (p, a, b) => {
  const abx = b.x - a.x; const aby = b.y - a.y
  const len2 = abx * abx + aby * aby
  let t = 0
  if (len2 > 0) t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / len2
  t = Math.max(0, Math.min(1, t))
  const point = { x: a.x + t * abx, y: a.y + t * aby }
  return { point, dist: Math.hypot(p.x - point.x, p.y - point.y) }
}

const parseGeom = (g) => { try { const a = JSON.parse(g || '[]'); return Array.isArray(a) ? a : [] } catch { return [] } }

const buildRoom = (r) => {
  const poly = parseGeom(r.geometry)
  const xs = poly.map((p) => p.x); const ys = poly.map((p) => p.y)
  const minX = xs.length ? Math.min(...xs) : 0; const minY = ys.length ? Math.min(...ys) : 0
  const maxX = xs.length ? Math.max(...xs) : 0; const maxY = ys.length ? Math.max(...ys) : 0
  const mids = []
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i]; const b = poly[(i + 1) % poly.length]
    mids.push({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, a: i, b: (i + 1) % poly.length })
  }
  return { id: r.id, name: r.name, floor: r.floor, poly, minX, minY, maxX, maxY, cx: (minX + maxX) / 2, cy: (minY + maxY) / 2, mids }
}

const roomsLocal = ref([])
watch(() => props.rooms, (rooms) => { roomsLocal.value = (rooms || []).map(buildRoom) }, { immediate: true, deep: true })

const placedFurnitures = computed(() => props.furnitures.filter((f) => f.x != null && f.y != null && f.w != null && f.h != null))

const furnitureById = computed(() => { const m = {}; props.furnitures.forEach((f) => { m[f.id] = f }); return m })
const roomById = computed(() => { const m = {}; roomsLocal.value.forEach((r) => { m[r.id] = r }); return m })

const absItems = computed(() => props.items.map((it) => {
  let ax = 0; let ay = 0
  if (it.furnitureId != null && furnitureById.value[it.furnitureId]) {
    const f = furnitureById.value[it.furnitureId]
    ax = f.x + (it.relX || 0.5) * f.w
    ay = f.y + (it.relY || 0.5) * f.h
  } else if (it.roomId != null && roomById.value[it.roomId] && roomById.value[it.roomId].poly.length) {
    const r = roomById.value[it.roomId]
    ax = r.minX + (it.relX || 0.5) * (r.maxX - r.minX)
    ay = r.minY + (it.relY || 0.5) * (r.maxY - r.minY)
  }
  return { ...it, ax, ay }
}))

const hitRoomIds = computed(() => {
  const ids = new Set()
  props.highlightItemIds.forEach((iid) => {
    const it = props.items.find((x) => x.id === iid)
    if (it && it.roomId != null) ids.add(it.roomId)
  })
  return [...ids]
})

const visibleItems = computed(() => {
  if (props.mode === 'edit') return absItems.value
  const hit = new Set(props.highlightItemIds)
  const fid = props.selectedFurnitureId
  return absItems.value.filter((it) => hit.has(it.id) || (fid != null && it.furnitureId === fid))
})

const pts = (poly) => poly.map((p) => `${p.x},${p.y}`).join(' ')

const toCanvas = (e) => {
  const rect = svgRef.value.getBoundingClientRect()
  return { x: (e.clientX - rect.left - view.value.tx) / view.value.k, y: (e.clientY - rect.top - view.value.ty) / view.value.k }
}

const fit = () => {
  if (!svgRef.value || !wrapRef.value) return
  const w = wrapRef.value.clientWidth || 800
  const h = wrapRef.value.clientHeight || 500
  let minX = Infinity; let minY = Infinity; let maxX = -Infinity; let maxY = -Infinity
  const collect = (x, y) => { minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y) }
  roomsLocal.value.forEach((r) => r.poly.forEach((p) => collect(p.x, p.y)))
  placedFurnitures.value.forEach((f) => { collect(f.x, f.y); collect(f.x + f.w, f.y + f.h) })
  if (!isFinite(minX)) { minX = 0; minY = 0; maxX = 1000; maxY = 700 }
  const bw = maxX - minX || 1000; const bh = maxY - minY || 700
  const k = Math.min(w / bw, h / bh) * 0.85
  view.value = { k, tx: (w - bw * k) / 2 - minX * k, ty: (h - bh * k) / 2 - minY * k }
}

const onWheel = (e) => {
  const rect = svgRef.value.getBoundingClientRect()
  const mx = e.clientX - rect.left; const my = e.clientY - rect.top
  const factor = e.deltaY < 0 ? 1.1 : 0.9
  const k = Math.max(0.1, Math.min(8, view.value.k * factor))
  view.value.tx = mx - (mx - view.value.tx) * (k / view.value.k)
  view.value.ty = my - (my - view.value.ty) * (k / view.value.k)
  view.value.k = k
}

const rebuildRoomMeta = (r) => {
  const poly = r.poly
  const xs = poly.map((p) => p.x); const ys = poly.map((p) => p.y)
  r.minX = Math.min(...xs); r.minY = Math.min(...ys); r.maxX = Math.max(...xs); r.maxY = Math.max(...ys)
  r.cx = (r.minX + r.maxX) / 2; r.cy = (r.minY + r.maxY) / 2
  r.mids = []
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i]; const b = poly[(i + 1) % poly.length]
    r.mids.push({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, a: i, b: (i + 1) % poly.length })
  }
}

const onPointerMove = (e) => {
  if (!drag.value) return
  const p = toCanvas(e)
  const d = drag.value
  if (d.type === 'pan') {
    view.value.tx += e.movementX; view.value.ty += e.movementY
  } else if (d.type === 'room-body') {
    const dx = p.x - d.startX; const dy = p.y - d.startY
    d.room.poly = d.orig.map((pt) => ({ x: pt.x + dx, y: pt.y + dy }))
    rebuildRoomMeta(d.room)
  } else if (d.type === 'room-vertex') {
    const poly = d.room.poly
    const n = poly.length
    const i = d.idx
    const prev = poly[(i - 1 + n) % n]
    const next = poly[(i + 1) % n]
    const snapped = snapVertex(prev, next, p.x, p.y)
    d.snapped = snapped.snapped
    poly[i] = { x: snapped.x, y: snapped.y }
    rebuildRoomMeta(d.room)
  } else if (d.type === 'room-edge') {
    const dx = p.x - d.startX
    const dy = p.y - d.startY
    d.room.poly[d.aIdx] = { x: d.orig[0].x + dx, y: d.orig[0].y + dy }
    d.room.poly[d.bIdx] = { x: d.orig[1].x + dx, y: d.orig[1].y + dy }
    rebuildRoomMeta(d.room)
  } else if (d.type === 'furn-move') {
    const dx = p.x - d.startX; const dy = p.y - d.startY
    d.f.x = d.orig.x + dx; d.f.y = d.orig.y + dy
  } else if (d.type === 'furn-resize') {
    d.f.w = Math.max(20, p.x - d.f.x); d.f.h = Math.max(20, p.y - d.f.y)
  } else if (d.type === 'item-move') {
    const it = d.item
    if (it.furnitureId != null && furnitureById.value[it.furnitureId]) {
      const f = furnitureById.value[it.furnitureId]
      it.relX = Math.max(0, Math.min(1, (p.x - f.x) / f.w))
      it.relY = Math.max(0, Math.min(1, (p.y - f.y) / f.h))
    } else if (it.roomId != null && roomById.value[it.roomId]) {
      const r = roomById.value[it.roomId]
      const rw = r.maxX - r.minX || 1; const rh = r.maxY - r.minY || 1
      it.relX = Math.max(0, Math.min(1, (p.x - r.minX) / rw))
      it.relY = Math.max(0, Math.min(1, (p.y - r.minY) / rh))
    }
  } else if (d.type === 'draw-rect') {
    const x = Math.min(d.startX, p.x); const y = Math.min(d.startY, p.y)
    drawing.value.rect = { x, y, w: Math.abs(p.x - d.startX), h: Math.abs(p.y - d.startY) }
  } else if (d.type === 'draw-poly') {
    drawing.value.poly = [...d.points, { x: p.x, y: p.y }]
  }
}

const onPointerUp = () => {
  const d = drag.value
  if (!d) return
  justDragged = true
  setTimeout(() => { justDragged = false }, 0)
  if (d.type === 'room-body' || d.type === 'room-vertex' || d.type === 'room-edge') {
    emit('save-room', d.room.id, JSON.stringify(d.room.poly))
    rebuildRoomMeta(d.room)
  } else if (d.type === 'furn-move' || d.type === 'furn-resize') {
    emit('save-furniture', d.f.id, { x: d.f.x, y: d.f.y, w: d.f.w, h: d.f.h })
  } else if (d.type === 'item-move') {
    emit('save-item', d.item.id, { relX: d.item.relX, relY: d.item.relY })
  } else if (d.type === 'draw-rect') {
    const r = drawing.value.rect
    if (r && r.w > 10 && r.h > 10) {
      emit('create-room', JSON.stringify([{ x: r.x, y: r.y }, { x: r.x + r.w, y: r.y }, { x: r.x + r.w, y: r.y + r.h }, { x: r.x, y: r.y + r.h }]))
    }
    drawing.value.rect = null
  }
  drag.value = null
  detach()
}

const detach = () => {
  document.removeEventListener('pointermove', onPointerMove)
  document.removeEventListener('pointerup', onPointerUp)
}

const beginDrag = (e, d) => {
  drag.value = d
  hoverEdge.value = null
  document.addEventListener('pointermove', onPointerMove)
  document.addEventListener('pointerup', onPointerUp)
  e.preventDefault()
}

// ---- hover 边 + 点击插入端点 ----
const detectHoverEdge = (p) => {
  const HANDLE_R = 12
  for (const r of roomsLocal.value) {
    const poly = r.poly
    for (let i = 0; i < poly.length; i++) {
      if (Math.hypot(p.x - poly[i].x, p.y - poly[i].y) < HANDLE_R) { hoverEdge.value = null; return }
      const m = r.mids[i]
      if (m && Math.hypot(p.x - m.x, p.y - m.y) < HANDLE_R) { hoverEdge.value = null; return }
    }
  }
  let best = null; let bestDist = 12
  for (const r of roomsLocal.value) {
    const poly = r.poly
    for (let i = 0; i < poly.length; i++) {
      const proj = projectToSegment(p, poly[i], poly[(i + 1) % poly.length])
      if (proj.dist < bestDist) { bestDist = proj.dist; best = { room: r, edgeIdx: i, point: proj.point } }
    }
  }
  hoverEdge.value = best
}
const onHoverMove = (e) => {
  if (drag.value) return
  if (props.mode !== 'edit' || props.tool !== 'select') { hoverEdge.value = null; return }
  detectHoverEdge(toCanvas(e))
}
const onCanvasClick = () => {
  if (justDragged) return
  if (props.mode !== 'edit' || props.tool !== 'select') return
  if (!hoverEdge.value) return
  const he = hoverEdge.value
  const poly = he.room.poly
  poly.splice(he.edgeIdx + 1, 0, { ...he.point })
  rebuildRoomMeta(he.room)
  emit('save-room', he.room.id, JSON.stringify(poly))
  hoverEdge.value = null
}

// ---- 房间 ----
const onRoomDown = (e, r) => {
  if (hoverEdge.value && hoverEdge.value.room === r) return
  if (props.tool === 'draw-rect') { startDrawRect(e); return }
  if (props.tool === 'draw-poly') { drawPolyPoint(e); return }
  beginDrag(e, { type: 'room-body', room: r, orig: r.poly.map((p) => ({ ...p })), startX: toCanvas(e).x, startY: toCanvas(e).y })
}
const onVertexDown = (e, r, i) => { beginDrag(e, { type: 'room-vertex', room: r, idx: i }) }
const isSnapping = (r, i) => {
  const d = drag.value
  return !!d && d.type === 'room-vertex' && d.room === r && d.idx === i && !!d.snapped
}
const onEdgeDown = (e, r, i) => {
  const poly = r.poly
  const aIdx = i
  const bIdx = (i + 1) % poly.length
  beginDrag(e, { type: 'room-edge', room: r, aIdx, bIdx, orig: [{ ...poly[aIdx] }, { ...poly[bIdx] }], startX: toCanvas(e).x, startY: toCanvas(e).y })
}
const removeVertex = (r, i) => {
  const poly = r.poly
  if (poly.length <= 4) return
  poly.splice(i, 1)
  rebuildRoomMeta(r)
  emit('save-room', r.id, JSON.stringify(poly))
}

// ---- 家具 ----
const onFurnDown = (e, f) => {
  if (props.tool !== 'select') return
  beginDrag(e, { type: 'furn-move', f, orig: { x: f.x, y: f.y }, startX: toCanvas(e).x, startY: toCanvas(e).y })
}
const onFurnResizeDown = (e, f) => { beginDrag(e, { type: 'furn-resize', f }) }

// ---- 物品 ----
const onItemDown = (e, it) => {
  const orig = props.items.find((x) => x.id === it.id)
  if (orig) beginDrag(e, { type: 'item-move', item: orig })
}

// ---- 画房间 ----
const startDrawRect = (e) => {
  const p = toCanvas(e)
  beginDrag(e, { type: 'draw-rect', startX: p.x, startY: p.y })
}
const drawPolyPoint = (e) => {
  const p = toCanvas(e)
  if (!drag.value || drag.value.type !== 'draw-poly') {
    beginDrag(e, { type: 'draw-poly', points: [p] })
    drawing.value.poly = [p]
  } else {
    drag.value.points.push(p)
    drawing.value.poly = [...drag.value.points]
  }
}
const finishPoly = () => {
  if (drawing.value.poly && drawing.value.poly.length >= 4) {
    emit('create-room', JSON.stringify(drawing.value.poly))
  }
  drawing.value.poly = null
  drag.value = null
  detach()
}

const onSvgDown = (e) => {
  if (e.target !== e.currentTarget) return
  if (props.mode !== 'edit') { beginDrag(e, { type: 'pan' }); return }
  if (props.tool === 'draw-rect') { startDrawRect(e); return }
  if (props.tool === 'draw-poly') { drawPolyPoint(e); return }
  beginDrag(e, { type: 'pan' })
}

watch(() => [props.rooms, props.furnitures, props.imageUrl], () => { nextTick(fit) }, { deep: true })
onMounted(fit)
onBeforeUnmount(detach)

defineExpose({ finishPoly, fit })
</script>

<style scoped>
.fp-canvas { position: relative; width: 100%; height: 100%; overflow: hidden; background: #f6efe4; }
.fp-svg { width: 100%; height: 100%; display: block; cursor: grab; }
.fp-svg:active { cursor: grabbing; }
.fp-bg { opacity: 0.85; }
.fp-room { fill: rgba(184, 140, 110, 0.14); stroke: rgba(184, 140, 110, 0.65); stroke-width: 2; }
.fp-room.is-hit { fill: rgba(184, 140, 110, 0.28); }
.fp-room-label { font-size: 13px; fill: #5c4c3d; text-anchor: middle; dominant-baseline: middle; pointer-events: none; }
.fp-furn { fill: rgba(120, 100, 80, 0.18); stroke: #8a6f55; stroke-width: 1.5; }
.fp-furn-label { font-size: 11px; fill: #6b5435; text-anchor: middle; dominant-baseline: middle; pointer-events: none; }
.fp-resize { fill: #fff; stroke: #b88c6e; stroke-width: 1.5; cursor: nwse-resize; }
.fp-item { fill: #b04a3a; stroke: #fff; stroke-width: 2; }
.fp-item.is-hit { fill: #e0a030; }
.fp-handle { fill: #fff; stroke: #b88c6e; stroke-width: 2; cursor: pointer; }
.fp-handle.is-snapped { fill: #6b9b6b; stroke: #fff; }
.fp-edge-handle { fill: #fff; stroke: #b88c6e; stroke-width: 1.5; cursor: pointer; }
.fp-hover-ring { fill: rgba(255, 255, 255, 0.9); stroke: #b88c6e; stroke-width: 2; }
.fp-hover-plus { stroke: #b88c6e; stroke-width: 2; stroke-linecap: round; }
.fp-drawing { fill: rgba(184, 140, 110, 0.12); stroke: #b88c6e; stroke-width: 2; stroke-dasharray: 6 4; }
</style>
