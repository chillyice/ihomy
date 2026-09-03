<template>
  <div class="fp-canvas" ref="wrapRef">
    <svg ref="svgRef" class="fp-svg" @wheel.prevent="onWheel" @pointerdown="onSvgDown" @pointerdown.capture="onPointerDownCapture" @pointermove="onHoverMove" @pointerup="onSvgPointerEnd" @pointercancel="onSvgPointerEnd" @click="onCanvasClick" @dblclick="onSvgDblClick" @dragover.prevent @drop="onDrop">
      <defs>
        <filter id="fp-rough" x="-5%" y="-5%" width="110%" height="110%">
          <feTurbulence type="fractalNoise" baseFrequency="0.02" numOctaves="2" result="n" />
          <feDisplacementMap in="SourceGraphic" in2="n" scale="2.5" />
        </filter>
      </defs>
      <g :transform="`translate(${view.tx},${view.ty}) scale(${view.k})`">
        <image v-if="imageUrl" :href="imageUrl" x="0" y="0" class="fp-bg" :opacity="opacity" />
        <!-- 房间 -->
        <g v-for="r in roomsLocal" :key="r.id">
          <polygon
            :points="pts(r.poly)"
            class="fp-room"
            :class="{ 'is-hit': hitRoomIds.includes(r.id), 'is-overlap': mode === 'edit' && overlapRoomIds.includes(r.id) }"
            filter="url(#fp-rough)"
            @pointerdown.stop="mode === 'edit' ? onRoomDown($event, r) : null"
            @dblclick="mode === 'edit' && tool === 'select' ? $emit('duplicate-room', r.id) : null"
            @contextmenu.prevent="mode === 'edit' ? $emit('delete-room', r.id) : null"
          />
          <g v-if="r.name">
            <text :x="r.cx" :y="r.cy - 6" class="fp-room-label fp-editable" @dblclick.stop="$emit('rename-room', r.id)">{{ r.name }}</text>
            <text :x="r.cx" :y="r.cy + 10" class="fp-room-area">{{ (polyArea(r.poly) / Math.pow(props.scale || 100, 2)).toFixed(2) }} m²</text>
          </g>
        </g>
        <!-- 家具 -->
        <g v-for="f in placedFurnitures" :key="f.id">
          <rect
            :x="f.x" :y="f.y" :width="f.w" :height="f.h"
            class="fp-furn"
            filter="url(#fp-rough)"
            @pointerdown.stop="mode === 'edit' ? onFurnDown($event, f) : null"
            @dblclick="mode === 'view' ? $emit('select-furniture', f.id) : null"
            @contextmenu.prevent="mode === 'edit' ? $emit('delete-furniture', f.id) : null"
          />
          <text v-if="f.w > 40 && f.h > 18" :x="f.x + f.w / 2" :y="f.y + f.h / 2" class="fp-furn-label fp-editable" @dblclick.stop="$emit('rename-furniture', f.id)">{{ f.name }}</text>
          <template v-if="mode === 'edit'">
            <circle v-for="a in furnCorners" :key="'fc' + a" class="fp-handle" r="5"
                    :cx="furnHandlePos(f, a)[0]" :cy="furnHandlePos(f, a)[1]"
                    :style="{ cursor: (a === 'nw' || a === 'se') ? 'nwse-resize' : 'nesw-resize' }"
                    @pointerdown.stop="onFurnHandleDown($event, f, a)" />
            <rect v-for="a in furnEdges" :key="'fe' + a" class="fp-edge-handle" width="9" height="9"
                  :x="furnHandlePos(f, a)[0] - 4.5" :y="furnHandlePos(f, a)[1] - 4.5"
                  :style="{ cursor: (a === 'n' || a === 's') ? 'ns-resize' : 'ew-resize' }"
                  @pointerdown.stop="onFurnHandleDown($event, f, a)" />
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
          <text :x="it.ax" :y="it.ay - 11" class="fp-item-label">{{ it.name }}</text>
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
                  @pointerdown.stop="onEdgeDown($event, r, i)"
                  @dblclick.stop="$emit('edit-edge', r.id, i)" />
            <text v-for="(m, i) in r.mids" :key="'dim' + i" :x="m.x" :y="m.y - 9" class="fp-dim">{{ edgeLenM(r, i) }}</text>
          </g>
          <!-- hover 边加号 -->
          <g v-if="hoverEdge" class="fp-hover-add">
            <circle :cx="hoverEdge.point.x" :cy="hoverEdge.point.y" r="9" class="fp-hover-ring" />
            <line :x1="hoverEdge.point.x - 4" :y1="hoverEdge.point.y" :x2="hoverEdge.point.x + 4" :y2="hoverEdge.point.y" class="fp-hover-plus" />
            <line :x1="hoverEdge.point.x" :y1="hoverEdge.point.y - 4" :x2="hoverEdge.point.x" :y2="hoverEdge.point.y + 4" class="fp-hover-plus" />
          </g>
          <!-- 吸附对齐虚线 -->
          <line v-if="snapLine" :x1="snapLine.x1" :y1="snapLine.y1" :x2="snapLine.x2" :y2="snapLine.y2" class="fp-snap-line" />
        </template>
        <!-- 正在画的房间 -->
        <polygon v-if="drawing.poly && drawing.poly.length" :points="pts(drawing.poly)" class="fp-drawing" />
        <rect v-if="drawing.rect" :x="drawing.rect.x" :y="drawing.rect.y" :width="drawing.rect.w" :height="drawing.rect.h" class="fp-drawing" />
        <!-- 底图标定两点 -->
        <g v-if="calibPoints.length">
          <circle v-for="(p, i) in calibPoints" :key="'c' + i" :cx="p.x" :cy="p.y" r="5" class="fp-calib-dot" />
          <line v-if="calibPoints.length === 2" :x1="calibPoints[0].x" :y1="calibPoints[0].y" :x2="calibPoints[1].x" :y2="calibPoints[1].y" class="fp-calib-line" />
        </g>
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
  opacity: { type: Number, default: 1 },
  highlightItemIds: { type: Array, default: () => [] },
  selectedFurnitureId: { type: Number, default: null },
  tool: { type: String, default: 'select' },
  scale: { type: Number, default: 100 },
})
const emit = defineEmits(['save-room', 'save-furniture', 'save-item', 'create-room', 'create-furniture', 'select-furniture', 'calibrate', 'edit-edge', 'duplicate-room', 'delete-room', 'delete-furniture', 'rename-room', 'rename-furniture'])

const wrapRef = ref(null)
const svgRef = ref(null)
const view = ref({ tx: 0, ty: 0, k: 1 })
const drawing = ref({ poly: null, rect: null })
const drag = ref(null)
const hoverEdge = ref(null)
const snapLine = ref(null)
const calibPoints = ref([])
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

// 其他房间顶点(排除当前房间)
const otherRoomVertices = (excludeId) => {
  const vs = []
  roomsLocal.value.forEach((r) => {
    if (r.id === excludeId) return
    r.poly.forEach((v) => vs.push({ x: v.x, y: v.y }))
  })
  return vs
}

// 点吸附到最近顶点(阈值内),返回目标顶点或 null
const snapPoint = (p, vertices, threshold = 6) => {
  let best = null; let bestDist = threshold
  for (const v of vertices) {
    const dist = Math.hypot(p.x - v.x, p.y - v.y)
    if (dist < bestDist) { bestDist = dist; best = { x: v.x, y: v.y } }
  }
  return best
}

// 边线对齐:值吸附到其他房间的水平/垂直边线(阈值按视图缩放换算),返回对齐值或 null
const findAlignLine = (excludeId, val, axis) => {
  const th = 6 / view.value.k
  for (const r of roomsLocal.value) {
    if (r.id === excludeId) continue
    for (let i = 0; i < r.poly.length; i++) {
      const a2 = r.poly[i]; const b2 = r.poly[(i + 1) % r.poly.length]
      if (axis === 'y' && Math.abs(a2.y - b2.y) < 0.5 && Math.abs(val - a2.y) < th) return a2.y
      if (axis === 'x' && Math.abs(a2.x - b2.x) < 0.5 && Math.abs(val - a2.x) < th) return a2.x
    }
  }
  return null
}

// 家具贴墙吸附:家具边贴合房间边/其他家具边(垂直/水平,阈值 6px),返回平移修正量
const snapFurniture = (f, excludeId) => {  const SNAP = 6
  const edges = []
  roomsLocal.value.forEach((r) => {
    r.poly.forEach((v, i) => {
      const a = v; const b = r.poly[(i + 1) % r.poly.length]
      if (Math.abs(a.x - b.x) < 0.5) edges.push({ axis: 'x', val: a.x, lo: Math.min(a.y, b.y), hi: Math.max(a.y, b.y) })
      else if (Math.abs(a.y - b.y) < 0.5) edges.push({ axis: 'y', val: a.y, lo: Math.min(a.x, b.x), hi: Math.max(a.x, b.x) })
    })
  })
  placedFurnitures.value.forEach((o) => {
    if (o.id === excludeId) return
    edges.push({ axis: 'x', val: o.x, lo: o.y, hi: o.y + o.h })
    edges.push({ axis: 'x', val: o.x + o.w, lo: o.y, hi: o.y + o.h })
    edges.push({ axis: 'y', val: o.y, lo: o.x, hi: o.x + o.w })
    edges.push({ axis: 'y', val: o.y + o.h, lo: o.x, hi: o.x + o.w })
  })
  const fEdges = [
    { axis: 'x', val: f.x, lo: f.y, hi: f.y + f.h, ref: { x1: f.x, y1: f.y } },
    { axis: 'x', val: f.x + f.w, lo: f.y, hi: f.y + f.h, ref: { x1: f.x + f.w, y1: f.y } },
    { axis: 'y', val: f.y, lo: f.x, hi: f.x + f.w, ref: { x1: f.x, y1: f.y } },
    { axis: 'y', val: f.y + f.h, lo: f.x, hi: f.x + f.w, ref: { x1: f.x, y1: f.y + f.h } },
  ]
  for (const fe of fEdges) {
    for (const re of edges) {
      if (fe.axis !== re.axis) continue
      const diff = fe.val - re.val
      if (Math.abs(diff) >= SNAP) continue
      if (fe.hi < re.lo || fe.lo > re.hi) continue
      if (fe.axis === 'x') return { dx: 0, dy: -diff, line: { x1: fe.ref.x1, y1: fe.ref.y1, x2: fe.ref.x1, y2: fe.ref.y1 - diff } }
      return { dx: -diff, dy: 0, line: { x1: fe.ref.x1, y1: fe.ref.y1, x2: fe.ref.x1 - diff, y2: fe.ref.y1 } }
    }
  }
  return { dx: 0, dy: 0, line: null }
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

// 副本隔离(ref 深响应式,拖拽有过程效果;原数据保留旧值供撤销记录)
const placedFurnitures = ref([])
watch(() => props.furnitures, (fs) => {
  placedFurnitures.value = (fs || [])
    .filter((f) => f.x != null && f.y != null && f.w != null && f.h != null)
    .map((f) => ({ ...f }))
}, { immediate: true, deep: true })

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

// ---- 房间重叠检测(编辑态警示) ----
const pointInPoly = (p, poly) => {
  let inside = false
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    if (((poly[i].y > p.y) !== (poly[j].y > p.y)) &&
      (p.x < ((poly[j].x - poly[i].x) * (p.y - poly[i].y)) / (poly[j].y - poly[i].y) + poly[i].x)) inside = !inside
  }
  return inside
}
const segsIntersect = (p1, p2, p3, p4) => {
  const d = (a, b, c) => (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
  const d1 = d(p3, p4, p1); const d2 = d(p3, p4, p2); const d3 = d(p1, p2, p3); const d4 = d(p1, p2, p4)
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}
// 点在边上(相邻房间共边/角对角不算重叠)
const onSegment = (p, a, b) => {
  const cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
  if (Math.abs(cross) > 1e-9) return false
  return p.x >= Math.min(a.x, b.x) - 1e-9 && p.x <= Math.max(a.x, b.x) + 1e-9 &&
    p.y >= Math.min(a.y, b.y) - 1e-9 && p.y <= Math.max(a.y, b.y) + 1e-9
}
const pointStrictlyInside = (p, poly) => {
  for (let i = 0; i < poly.length; i++) {
    if (onSegment(p, poly[i], poly[(i + 1) % poly.length])) return false
  }
  return pointInPoly(p, poly)
}
const polysOverlap = (pa, pb) => {
  if (pa.length < 3 || pb.length < 3) return false
  if (pa.some((p) => pointStrictlyInside(p, pb))) return true
  if (pb.some((p) => pointStrictlyInside(p, pa))) return true
  for (let i = 0; i < pa.length; i++) {
    for (let j = 0; j < pb.length; j++) {
      if (segsIntersect(pa[i], pa[(i + 1) % pa.length], pb[j], pb[(j + 1) % pb.length])) return true
    }
  }
  return false
}
const overlapRoomIds = computed(() => {
  const ids = new Set()
  const rs = roomsLocal.value.filter((r) => r.poly.length >= 3)
  for (let i = 0; i < rs.length; i++) {
    for (let j = i + 1; j < rs.length; j++) {
      if (polysOverlap(rs[i].poly, rs[j].poly)) { ids.add(rs[i].id); ids.add(rs[j].id) }
    }
  }
  return [...ids]
})

const visibleItems = computed(() => {
  if (props.mode === 'edit') return absItems.value
  const hit = new Set(props.highlightItemIds)
  const fid = props.selectedFurnitureId
  return absItems.value.filter((it) => hit.has(it.id) || (fid != null && it.furnitureId === fid))
})

const pts = (poly) => poly.map((p) => `${p.x},${p.y}`).join(' ')

// 多边形面积(shoelace,像素²)
const polyArea = (poly) => {
  let s = 0
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i]; const b = poly[(i + 1) % poly.length]
    s += a.x * b.y - b.x * a.y
  }
  return Math.abs(s) / 2
}

// 米数格式化(像素→米,保留 2 位)
const fmtM = (px) => {
  const m = px / (props.scale || 100)
  return m >= 100 ? m.toFixed(0) : m.toFixed(2)
}

// 边 i 的长度(米)
const edgeLenM = (r, i) => {
  const a = r.poly[i]; const b = r.poly[(i + 1) % r.poly.length]
  return fmtM(Math.hypot(b.x - a.x, b.y - a.y))
}

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
    let dx = p.x - d.startX; let dy = p.y - d.startY
    const rawDx = dx; const rawDy = dy
    const others = otherRoomVertices(d.room.id)
    snapLine.value = null
    if (others.length) {
      for (const pt of d.orig) {
        const t = snapPoint({ x: pt.x + dx, y: pt.y + dy }, others)
        if (t) {
          snapLine.value = { x1: pt.x + rawDx, y1: pt.y + rawDy, x2: t.x, y2: t.y }
          dx += t.x - (pt.x + dx); dy += t.y - (pt.y + dy)
          break
        }
      }
    }
    d.room.poly = d.orig.map((pt) => ({ x: pt.x + dx, y: pt.y + dy }))
    rebuildRoomMeta(d.room)
    // 家具级联平移:归属该房间且已摆放的家具跟随房间
    props.furnitures.forEach((f) => {
      if (f.roomId !== d.room.id || f.x == null || f.y == null) return
      if (!d.furnOrig[f.id]) d.furnOrig[f.id] = { x: f.x, y: f.y, w: f.w, h: f.h }
      const o = d.furnOrig[f.id]
      f.x = o.x + dx; f.y = o.y + dy
    })
  } else if (d.type === 'room-vertex') {
    const poly = d.room.poly
    const n = poly.length
    const i = d.idx
    const prev = poly[(i - 1 + n) % n]
    const next = poly[(i + 1) % n]
    const ctrl = e.ctrlKey || e.metaKey
    snapLine.value = null
    d.snapped = false
    if (d.followMode && !ctrl) {
      // 角缩放:顶点自由移动,水平邻边对端跟随 dy,垂直邻边对端跟随 dx(保持直角)
      const cur = poly[i]
      let nx = p.x; let ny = p.y
      const others = otherRoomVertices(d.room.id)
      const t = others.length ? snapPoint({ x: nx, y: ny }, others) : null
      if (t) { snapLine.value = { x1: nx, y1: ny, x2: t.x, y2: t.y }; nx = t.x; ny = t.y; d.snapped = true }
      const dx = nx - cur.x
      const dy = ny - cur.y
      if (d.followMode.e1H) prev.y += dy
      else if (d.followMode.e1V) prev.x += dx
      if (d.followMode.e2H) next.y += dy
      else if (d.followMode.e2V) next.x += dx
      poly[i] = { x: nx, y: ny }
    } else if (ctrl) {
      // Ctrl:纯自由拖点(无任何吸附),可拉出斜边
      poly[i] = { x: p.x, y: p.y }
    } else {
      // 非直角:自由拖 + 轴对齐吸附辅助调直 + 磁吸
      const snapped = snapVertex(prev, next, p.x, p.y)
      d.snapped = snapped.snapped
      let vx = snapped.x; let vy = snapped.y
      const others = otherRoomVertices(d.room.id)
      const t = others.length ? snapPoint({ x: vx, y: vy }, others) : null
      if (t) { snapLine.value = { x1: vx, y1: vy, x2: t.x, y2: t.y }; vx = t.x; vy = t.y }
      poly[i] = { x: vx, y: vy }
    }
    rebuildRoomMeta(d.room)
  } else if (d.type === 'room-edge') {
    let dx = p.x - d.startX
    let dy = p.y - d.startY
    const rawDx = dx; const rawDy = dy
    const others = otherRoomVertices(d.room.id)
    snapLine.value = null
    if (others.length) {
      for (const pt of d.orig) {
        const t = snapPoint({ x: pt.x + dx, y: pt.y + dy }, others)
        if (t) {
          snapLine.value = { x1: pt.x + rawDx, y1: pt.y + rawDy, x2: t.x, y2: t.y }
          dx += t.x - (pt.x + dx); dy += t.y - (pt.y + dy)
          break
        }
      }
    }
    // 基于原始边判定接近水平/垂直 → 拖拽时拉平(平移不改变边方向,实时判定永远不触发)
    const oA = d.orig[0]; const oB = d.orig[1]
    const origLen = Math.hypot(oB.x - oA.x, oB.y - oA.y) || 1
    const nearH = Math.abs(oA.y - oB.y) / origLen < 0.25
    const nearV = Math.abs(oA.x - oB.x) / origLen < 0.25
    if (nearH) {
      let ty = (oA.y + oB.y) / 2 + dy
      const ln = findAlignLine(d.room.id, ty, 'y')
      if (ln != null) { snapLine.value = { x1: oA.x + dx, y1: ty, x2: oA.x + dx, y2: ln }; ty = ln }
      d.room.poly[d.aIdx] = { x: oA.x + dx, y: ty }
      d.room.poly[d.bIdx] = { x: oB.x + dx, y: ty }
    } else if (nearV) {
      let tx = (oA.x + oB.x) / 2 + dx
      const ln = findAlignLine(d.room.id, tx, 'x')
      if (ln != null) { snapLine.value = { x1: tx, y1: oA.y + dy, x2: ln, y2: oA.y + dy }; tx = ln }
      d.room.poly[d.aIdx] = { x: tx, y: oA.y + dy }
      d.room.poly[d.bIdx] = { x: tx, y: oB.y + dy }
    } else {
      d.room.poly[d.aIdx] = { x: oA.x + dx, y: oA.y + dy }
      d.room.poly[d.bIdx] = { x: oB.x + dx, y: oB.y + dy }
    }
    rebuildRoomMeta(d.room)
  } else if (d.type === 'furn-move') {
    let dx = p.x - d.startX; let dy = p.y - d.startY
    const snap = snapFurniture({ x: d.orig.x + dx, y: d.orig.y + dy, w: d.f.w, h: d.f.h }, d.f.id)
    snapLine.value = snap.line
    d.f.x = d.orig.x + dx + snap.dx
    d.f.y = d.orig.y + dy + snap.dy
  } else if (d.type === 'furn-resize') {
    const o = d.orig; const a = d.anchor
    let { x, y, w, h } = o
    if (a.includes('e')) w = Math.max(20, p.x - o.x)
    if (a.includes('s')) h = Math.max(20, p.y - o.y)
    if (a.includes('w')) { const right = o.x + o.w; x = Math.min(p.x, right - 20); w = right - x }
    if (a.includes('n')) { const bottom = o.y + o.h; y = Math.min(p.y, bottom - 20); h = bottom - y }
    d.f.x = x; d.f.y = y; d.f.w = w; d.f.h = h
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

const onPointerUp = (e) => {
  if (e && e.pointerId != null) onSvgPointerEnd(e)
  const d = drag.value
  if (!d) return
  if (d.type === 'draw-poly') return // 逐点描绘:点击间保持状态,双击闭合/切工具时 finishPoly
  justDragged = true
  setTimeout(() => { justDragged = false }, 0)
  if (d.type === 'room-body' || d.type === 'room-vertex' || d.type === 'room-edge') {
    emit('save-room', d.room.id, JSON.stringify(d.room.poly))
    rebuildRoomMeta(d.room)
    if (d.type === 'room-body' && d.furnOrig) {
      // 级联平移的家具逐个保存(带 prev 供撤销)
      Object.keys(d.furnOrig).forEach((fid) => {
        const f = props.furnitures.find((x) => x.id === Number(fid))
        if (f) emit('save-furniture', f.id, { x: f.x, y: f.y, w: f.w, h: f.h }, d.furnOrig[fid])
      })
    }
  } else if (d.type === 'furn-move' || d.type === 'furn-resize') {
    emit('save-furniture', d.f.id, { x: d.f.x, y: d.f.y, w: d.f.w, h: d.f.h })
  } else if (d.type === 'item-move') {
    emit('save-item', d.item.id, { relX: d.item.relX, relY: d.item.relY }, d.prevRel)
  } else if (d.type === 'draw-rect') {
    const r = drawing.value.rect
    if (r && r.w > 10 && r.h > 10) {
      emit('create-room', JSON.stringify([{ x: r.x, y: r.y }, { x: r.x + r.w, y: r.y }, { x: r.x + r.w, y: r.y + r.h }, { x: r.x, y: r.y + r.h }]))
    }
    drawing.value.rect = null
  }
  drag.value = null
  snapLine.value = null
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
  if (pointers.has(e.pointerId)) pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
  if (pointers.size === 2 && pinchDist > 0) {
    const [a, b] = [...pointers.values()]
    const dist = Math.hypot(a.x - b.x, a.y - b.y)
    const rect = svgRef.value.getBoundingClientRect()
    const mx = (a.x + b.x) / 2 - rect.left
    const my = (a.y + b.y) / 2 - rect.top
    const k = clamp(view.value.k * (dist / pinchDist), 0.1, 8)
    view.value.tx = mx - (mx - view.value.tx) * (k / view.value.k)
    view.value.ty = my - (my - view.value.ty) * (k / view.value.k)
    view.value.k = k
    pinchDist = dist
    return
  }
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
// 切换工具清 hover 残留(避免残留 guard 吞掉画图点击)
watch(() => props.tool, () => { hoverEdge.value = null })

// ---- 房间 ----
// 工具分发:非 select 工具时点击任何元素(房间/家具/物品/手柄)都落到画布层,不被挡住吞点
const routeTool = (e) => {
  if (props.tool === 'calibrate') { handleCalibrateClick(e); return true }
  if (props.tool === 'draw-rect') { startDrawRect(e); return true }
  if (props.tool === 'draw-poly') { drawPolyPoint(e); return true }
  return false
}
const onRoomDown = (e, r) => {
  if (props.tool === 'select' && hoverEdge.value && hoverEdge.value.room === r) return
  if (routeTool(e)) return
  beginDrag(e, { type: 'room-body', room: r, orig: r.poly.map((p) => ({ ...p })), startX: toCanvas(e).x, startY: toCanvas(e).y, furnOrig: {} })
}
const onVertexDown = (e, r, i) => {
  if (routeTool(e)) return
  const poly = r.poly
  const n = poly.length
  const prev = poly[(i - 1 + n) % n]
  const next = poly[(i + 1) % n]
  const cur = poly[i]
  const e1H = Math.abs(prev.y - cur.y) < 0.5
  const e1V = Math.abs(prev.x - cur.x) < 0.5
  const e2H = Math.abs(next.y - cur.y) < 0.5
  const e2V = Math.abs(next.x - cur.x) < 0.5
  // 拖动开始时角是轴对齐直角 → 默认「角缩放」(两条邻边沿轴向平移、对端跟随);Ctrl = 只动此点
  const followMode = ((e1H && e2V) || (e1V && e2H)) ? { e1H, e1V, e2H, e2V } : null
  beginDrag(e, { type: 'room-vertex', room: r, idx: i, followMode })
}
const isSnapping = (r, i) => {
  const d = drag.value
  return !!d && d.type === 'room-vertex' && d.room === r && d.idx === i && !!d.snapped
}
const onEdgeDown = (e, r, i) => {
  if (routeTool(e)) return
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
  if (routeTool(e)) return
  beginDrag(e, { type: 'furn-move', f, orig: { x: f.x, y: f.y }, startX: toCanvas(e).x, startY: toCanvas(e).y })
}
// 8 向缩放手柄(4 角 + 4 边中点,同房间编辑体验)
const furnCorners = ['nw', 'ne', 'sw', 'se']
const furnEdges = ['n', 's', 'e', 'w']
const furnHandlePos = (f, a) => {
  const cx = f.x + f.w / 2; const cy = f.y + f.h / 2
  return {
    nw: [f.x, f.y], ne: [f.x + f.w, f.y], sw: [f.x, f.y + f.h], se: [f.x + f.w, f.y + f.h],
    n: [cx, f.y], s: [cx, f.y + f.h], w: [f.x, cy], e: [f.x + f.w, cy],
  }[a]
}
const onFurnHandleDown = (e, f, anchor) => {
  if (routeTool(e)) return
  beginDrag(e, { type: 'furn-resize', f, anchor, orig: { x: f.x, y: f.y, w: f.w, h: f.h } })
}

// ---- 物品 ----
const onItemDown = (e, it) => {
  if (routeTool(e)) return
  const orig = props.items.find((x) => x.id === it.id)
  if (orig) beginDrag(e, { type: 'item-move', item: orig, prevRel: { relX: orig.relX, relY: orig.relY } })
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

// ---- 侧栏预设家具拖入(drop) ----
const onDrop = (e) => {
  const type = e.dataTransfer.getData('text/furn-type')
  if (!type) return
  e.preventDefault()
  const p = toCanvas(e)
  const room = roomsLocal.value.find((r) => r.poly.length >= 3 && pointInPoly(p, r.poly))
  emit('create-furniture', { type, roomId: room ? room.id : null, x: p.x, y: p.y })
}

// ---- 底图标定(两点测距) ----
const handleCalibrateClick = (e) => {
  const p = toCanvas(e)
  calibPoints.value.push({ x: p.x, y: p.y })
  if (calibPoints.value.length === 2) {
    const [a, b] = calibPoints.value
    emit('calibrate', Math.hypot(b.x - a.x, b.y - a.y))
    calibPoints.value = []
  }
  e.preventDefault()
}

const onSvgDown = (e) => {
  if (e.target !== e.currentTarget) return
  if (props.mode !== 'edit') { beginDrag(e, { type: 'pan' }); return }
  if (props.tool === 'calibrate') { handleCalibrateClick(e); return }
  if (props.tool === 'draw-rect') { startDrawRect(e); return }
  if (props.tool === 'draw-poly') { drawPolyPoint(e); return }
  beginDrag(e, { type: 'pan' })
}

// ---- 移动端双指缩放(pinch) ----
const pointers = new Map()
let pinchDist = 0
const onPointerDownCapture = (e) => {
  pointers.set(e.pointerId, { x: e.clientX, y: e.clientY })
  if (pointers.size === 2) {
    if (drag.value) { drag.value = null; detach(); snapLine.value = null }
    hoverEdge.value = null
    const [a, b] = [...pointers.values()]
    pinchDist = Math.hypot(a.x - b.x, a.y - b.y)
    e.stopPropagation()
    e.preventDefault()
  }
}
const onSvgPointerEnd = (e) => {
  pointers.delete(e.pointerId)
  if (pointers.size < 2) pinchDist = 0
}

// ---- 逐点描绘双击闭合 ----
const onSvgDblClick = (e) => {
  if (props.tool !== 'draw-poly') return
  e.preventDefault()
  if (drag.value && drag.value.type === 'draw-poly' && drag.value.points.length >= 2) {
    const p = drag.value.points
    const last = p[p.length - 1]; const prev = p[p.length - 2]
    if (Math.hypot(last.x - prev.x, last.y - prev.y) < 12 / view.value.k) p.splice(p.length - 1, 1)
  }
  finishPoly()
}

// 拖拽中不 fit(级联平移等实时数据变化会重置视图,元素视觉漂移)
watch(() => [props.rooms, props.furnitures, props.imageUrl], () => { if (!drag.value) nextTick(fit) }, { deep: true })
let resizeObserver = null
onMounted(() => {
  fit()
  // 画布尺寸变化(侧栏出现/窗口缩放)重新适配,元素始终在窗格内
  resizeObserver = new ResizeObserver(() => { if (!drag.value) fit() })
  resizeObserver.observe(wrapRef.value)
})
onBeforeUnmount(() => { detach(); if (resizeObserver) resizeObserver.disconnect() })

defineExpose({ finishPoly, fit })
</script>

<style scoped>
.fp-canvas { position: relative; width: 100%; height: 100%; overflow: hidden; background: #f6efe4; }
.fp-svg { width: 100%; height: 100%; display: block; cursor: grab; }
.fp-svg:active { cursor: grabbing; }
.fp-bg { pointer-events: none; } /* 底图纯背景,不挡画布交互 */
.fp-editable { pointer-events: auto; cursor: text; }
.fp-room { fill: rgba(184, 140, 110, 0.14); stroke: rgba(184, 140, 110, 0.65); stroke-width: 2; }
.fp-room.is-hit { fill: rgba(184, 140, 110, 0.28); }
.fp-room.is-overlap { stroke: #b04a3a; stroke-width: 2.5; fill: rgba(185, 96, 88, 0.16); }
.fp-room-label { font-size: 13px; fill: #5c4c3d; text-anchor: middle; dominant-baseline: middle; pointer-events: none; }
.fp-room-area { font-size: 11px; fill: #a89a8a; text-anchor: middle; dominant-baseline: middle; pointer-events: none; }
.fp-dim { font-size: 10px; fill: #6b9b6b; text-anchor: middle; pointer-events: none; }
.fp-furn { fill: rgba(120, 100, 80, 0.18); stroke: #8a6f55; stroke-width: 1.5; }
.fp-furn-label { font-size: 11px; fill: #6b5435; text-anchor: middle; dominant-baseline: middle; pointer-events: none; }
.fp-item { fill: #b04a3a; stroke: #fff; stroke-width: 2; }.fp-item.is-hit { fill: #e0a030; }
.fp-item-label { font-size: 10px; fill: #5c4c3d; text-anchor: middle; paint-order: stroke; stroke: rgba(255, 253, 248, 0.85); stroke-width: 3; pointer-events: none; }
.fp-handle { fill: #fff; stroke: #b88c6e; stroke-width: 2; cursor: pointer; }
.fp-handle.is-snapped { fill: #6b9b6b; stroke: #fff; }
.fp-edge-handle { fill: #fff; stroke: #b88c6e; stroke-width: 1.5; cursor: pointer; }
.fp-hover-ring { fill: rgba(255, 255, 255, 0.9); stroke: #b88c6e; stroke-width: 2; }
.fp-hover-plus { stroke: #b88c6e; stroke-width: 2; stroke-linecap: round; }
.fp-snap-line { stroke: #6b9b6b; stroke-width: 1.5; stroke-dasharray: 5 4; }
.fp-calib-dot { fill: #e0a030; stroke: #fff; stroke-width: 2; }
.fp-calib-line { stroke: #e0a030; stroke-width: 1.5; stroke-dasharray: 5 4; }
.fp-drawing { fill: rgba(184, 140, 110, 0.12); stroke: #b88c6e; stroke-width: 2; stroke-dasharray: 6 4; }
</style>
