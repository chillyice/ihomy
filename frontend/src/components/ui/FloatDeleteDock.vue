<!-- 浮窗「拖到底部删除」提示条:整条铺满下边框,主题红羽化(下深上浅、左右浅中间深),
     形状像上半个椭圆;指针进入删除范围后整条发亮、图标放大,松手即删除(判定在浮窗侧算)。
     Teleport 到 body:浮窗自身可能带 transform(fixed 会被当成绝对定位),不放进浮窗内部。
     常驻渲染、只切 class(不进 <Transition>):后台标签页 rAF 冻结时 enter-from 会卡在 opacity:0,
     删除条就永远不显示。 -->
<template>
  <Teleport to="body">
    <div class="float-delete-dock" :class="{ active, over }" aria-hidden="true">
      <div class="dock-icon">
        <el-icon :size="22"><Delete /></el-icon>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { Delete } from '@element-plus/icons-vue'

defineProps({
  active: { type: Boolean, default: false },
  over: { type: Boolean, default: false },
})
</script>

<style scoped>
.float-delete-dock {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 104px;
  z-index: var(--z-float-top);
  pointer-events: none;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 18px;
  /* 椭圆径向渐变(底边中心最浓)+ 底部线性渐变 = 上半个椭圆的羽化红晕 */
  background:
    radial-gradient(
      ellipse 48% 120% at 50% 104%,
      rgba(var(--color-danger-rgb), 0.72) 0%,
      rgba(var(--color-danger-rgb), 0.4) 44%,
      rgba(var(--color-danger-rgb), 0.12) 70%,
      transparent 88%
    ),
    linear-gradient(to top, rgba(var(--color-danger-rgb), 0.32) 0%, rgba(var(--color-danger-rgb), 0.12) 40%, transparent 100%);
  opacity: 0;
  filter: saturate(0.92);
  transition:
    opacity var(--dur-fast) var(--ease-soft),
    filter var(--dur-fast) var(--ease-soft);
}
.float-delete-dock.active {
  opacity: 0.75;
}
.float-delete-dock.active.over {
  opacity: 1;
  filter: saturate(1.15) brightness(1.16);
}
.dock-icon {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(var(--color-danger-rgb), 0.6);
  border: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 4px 18px rgba(var(--color-danger-rgb), 0.45);
  transform: scale(0.92);
  transition:
    transform var(--dur-fast) var(--ease-spring),
    background var(--dur-fast) var(--ease-soft);
}
.float-delete-dock.active.over .dock-icon {
  transform: scale(1.1);
  background: rgba(var(--color-danger-rgb), 0.92);
}
</style>
