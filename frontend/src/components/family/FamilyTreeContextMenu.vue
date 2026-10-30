<!-- 家谱画板的右键/长按菜单:自建浮层(不走 el-dropdown,避免与画布拖拽抢指针)。
     append-to-body 由 teleport 实现,防止被画板 overflow:hidden 裁切;层级用 --z-popper。 -->
<template>
  <teleport to="body">
    <div v-if="menu" class="ftm-layer" @pointerdown.self="$emit('close')" @contextmenu.prevent>
      <div class="ftm-menu" role="menu" :style="style" @contextmenu.prevent>
        <template v-for="item in menu.items" :key="item.key">
          <div v-if="item.children?.length" class="ftm-group">
            <button type="button" class="ftm-item ftm-parent" role="menuitem" aria-haspopup="true" @click="toggleSub(item.key)">
              <span>{{ item.label }}</span>
              <el-icon class="ftm-arrow" :class="{ open: openKey === item.key }"><ArrowRight /></el-icon>
            </button>
            <div v-show="openKey === item.key" class="ftm-submenu">
              <button v-for="child in item.children" :key="child.key" type="button" class="ftm-item" role="menuitem" @click="pick(child)">
                {{ child.label }}
              </button>
            </div>
          </div>
          <button v-else type="button" class="ftm-item" :class="{ danger: item.danger }" role="menuitem" @click="pick(item)">
            {{ item.label }}
          </button>
        </template>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  /** { x, y, items:[{key,label,danger,children:[{key,label,...}]}] } | null */
  menu: { type: Object, default: null },
})
const emit = defineEmits(['close', 'select'])

const openKey = ref(null)
const MENU_WIDTH = 196

// 靠近视口边缘时向内收,避免菜单被裁掉
const style = computed(() => {
  if (!props.menu) return {}
  const count = props.menu.items.length + (openKey.value ? 2 : 0)
  const height = 18 + count * 34
  const x = Math.max(8, Math.min(props.menu.x, window.innerWidth - MENU_WIDTH - 8))
  const y = Math.max(8, Math.min(props.menu.y, window.innerHeight - height - 8))
  return { left: `${x}px`, top: `${y}px` }
})

const toggleSub = (key) => {
  openKey.value = openKey.value === key ? null : key
}

const pick = (item) => emit('select', item)

const onKeydown = (event) => {
  if (event.key === 'Escape') emit('close')
}

watch(
  () => props.menu,
  () => {
    openKey.value = null
  },
)

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.ftm-layer {
  position: fixed;
  inset: 0;
  z-index: var(--z-popper);
}
.ftm-menu {
  position: fixed;
  min-width: 176px;
  padding: 6px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  box-shadow: 0 12px 32px rgb(0 0 0 / 18%);
}
.ftm-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: 8px;
  background: none;
  color: var(--color-text);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.ftm-item:hover,
.ftm-item:focus-visible {
  background: color-mix(in srgb, var(--color-primary) 10%, transparent);
  outline: none;
}
.ftm-item.danger {
  color: #d9534f;
}
.ftm-arrow {
  font-size: 12px;
  transition: transform 0.15s;
}
.ftm-arrow.open {
  transform: rotate(90deg);
}
.ftm-submenu {
  margin: 2px 0 4px 10px;
  padding-left: 6px;
  border-left: 1px dashed var(--color-border);
}
</style>
