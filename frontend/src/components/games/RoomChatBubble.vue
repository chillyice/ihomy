<!-- 房间聊天头像气泡:贴在玩家头像的四个方向(对家下 / 上家右 / 下家左 / 自己上)。
     --rcb-k 用于反缩放:麻将牌桌整体走 transform: scale(--mj-scale),牌桌内把 --rcb-k 设为 1/scale,
     气泡的字号/内距/阴影一起还原成屏幕尺寸,缩小的牌桌里也看得清。
     贴边收敛:渲染后量一次实际位置,顶到游戏区上沿就翻到头像下方、左右越界就镜像到另一侧,
     还差一点就水平挪回来 —— 气泡永远留在游戏区里,不会挤出屏幕。 -->
<template>
  <Transition name="rcb-fade">
    <span v-if="text" ref="el" class="rcb" :class="`rcb-${dirNow}`" :style="{ '--rcb-dx': `${shift}px` }" role="status">{{ text }}</span>
  </Transition>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps({
  text: { type: String, default: '' },
  /** above|below|left|right:气泡位于头像的哪一侧(箭头指向头像) */
  dir: { type: String, default: 'above' },
})

const el = ref(null)
/** 实际用的方向:越界时在 above/below、left/right 之间翻 */
const dirNow = ref(props.dir)
/** 水平微调(本地坐标,祖先有 scale 时按 1/scale 换算) */
const shift = ref(0)
/** 离屏幕/游戏区边缘的安全边 */
const EDGE = 8

const clamp = async () => {
  dirNow.value = props.dir
  shift.value = 0
  await nextTick()
  let node = el.value
  if (!node) return
  const host = node.closest('.gs-body') || node.closest('.game-page')
  const hb = host?.getBoundingClientRect()
  const left = Math.max(EDGE, (hb?.left ?? 0) + EDGE)
  const right = Math.min(window.innerWidth - EDGE, (hb?.right ?? window.innerWidth) - EDGE)
  const top = Math.max(EDGE, (hb?.top ?? 0) + EDGE)
  const bottom = Math.min(window.innerHeight - EDGE, (hb?.bottom ?? window.innerHeight) - EDGE)
  let r = node.getBoundingClientRect()
  let next = props.dir
  if (next === 'above' && r.top < top) next = 'below'
  else if (next === 'below' && r.bottom > bottom) next = 'above'
  else if (next === 'right' && r.right > right) next = 'left'
  else if (next === 'left' && r.left < left) next = 'right'
  if (next !== props.dir) {
    dirNow.value = next
    await nextTick()
    node = el.value
    if (!node) return
    r = node.getBoundingClientRect()
  }
  let dx = 0
  if (r.right > right) dx = right - r.right
  else if (r.left < left) dx = left - r.left
  if (!dx) return
  // 祖先缩放过时 rect 是屏幕尺寸、offsetWidth 是设计尺寸,两者之比就是 1/scale
  const k = r.width > 0 && node.offsetWidth ? node.offsetWidth / r.width : 1
  shift.value = Math.round(dx * k)
}

watch(() => [props.dir, props.text], clamp)
onMounted(clamp)
</script>

<style scoped>
.rcb {
  position: absolute;
  z-index: 3;
  /* 贴边收敛的水平位移(JS 量完越界距离后写入,默认 0 = 不挪) */
  --rcb-dx: 0px;
  width: max-content;
  max-width: calc(150px * var(--rcb-k, 1));
  padding: calc(3px * var(--rcb-k, 1)) calc(8px * var(--rcb-k, 1));
  border-radius: calc(10px * var(--rcb-k, 1));
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  box-shadow: 0 calc(4px * var(--rcb-k, 1)) calc(12px * var(--rcb-k, 1)) rgba(0, 0, 0, 0.18);
  font-size: calc(12px * var(--rcb-k, 1));
  line-height: 1.35;
  font-weight: 400;
  letter-spacing: 0;
  text-align: left;
  white-space: normal;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  pointer-events: none;
}
.rcb::after {
  content: '';
  position: absolute;
  border: calc(5px * var(--rcb-k, 1)) solid transparent;
}
.rcb-above {
  bottom: calc(100% + calc(5px * var(--rcb-k, 1)));
  left: 50%;
  transform: translateX(calc(-50% + var(--rcb-dx, 0px)));
}
.rcb-above::after {
  top: 100%;
  left: 50%;
  margin-left: calc(-5px * var(--rcb-k, 1));
  border-top-color: var(--color-card);
}
.rcb-below {
  top: calc(100% + calc(5px * var(--rcb-k, 1)));
  left: 50%;
  transform: translateX(calc(-50% + var(--rcb-dx, 0px)));
}
.rcb-below::after {
  bottom: 100%;
  left: 50%;
  margin-left: calc(-5px * var(--rcb-k, 1));
  border-bottom-color: var(--color-card);
}
.rcb-right {
  left: calc(100% + calc(5px * var(--rcb-k, 1)));
  top: 50%;
  transform: translate(var(--rcb-dx, 0px), -50%);
}
.rcb-right::after {
  right: 100%;
  top: 50%;
  margin-top: calc(-5px * var(--rcb-k, 1));
  border-right-color: var(--color-card);
}
.rcb-left {
  right: calc(100% + calc(5px * var(--rcb-k, 1)));
  top: 50%;
  transform: translate(var(--rcb-dx, 0px), -50%);
}
.rcb-left::after {
  left: 100%;
  top: 50%;
  margin-top: calc(-5px * var(--rcb-k, 1));
  border-left-color: var(--color-card);
}
.rcb-fade-enter-active,
.rcb-fade-leave-active {
  transition: opacity 0.18s ease;
}
.rcb-fade-enter-from,
.rcb-fade-leave-to {
  opacity: 0;
}
</style>
