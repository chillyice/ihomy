<template>
  <header class="room-scene">
    <div class="room-scene-frame">
      <div class="room-scene-furniture">
        <img class="room-scene-view" :src="themeStore.isDusk ? eveningRoom : morningRoom" alt="" fetchpriority="high" />
        <router-link v-if="music && !appStore.homeEditMode" :to="music.path" class="room-record-player">
          <span><span aria-hidden="true">♫</span> {{ $t('warm.module.music') }}</span>
        </router-link>
      </div>
    </div>
    <div class="room-scene-welcome">
      <slot />
      <div v-if="$slots.actions" class="room-scene-actions"><slot name="actions" /></div>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import { useThemeStore } from '@/stores/theme'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { buildNavItems } from '@/utils/navModules'
import morningRoom from '@/assets/warm-room/sitting-room.webp'
import eveningRoom from '@/assets/warm-room/sitting-room-dusk.webp'
import './room.css'

const themeStore = useThemeStore()
const appStore = useAppStore()
const { isChild } = useDisplayMode()
const music = computed(() => buildNavItems(appStore.modules, { child: isChild.value }).find((item) => item.code === 'music'))
</script>

<style scoped>
.room-scene {
  position: relative;
  isolation: isolate;
  display: grid;
  align-content: end;
  min-height: clamp(240px, 22vw, 330px);
  overflow: hidden;
  border-radius: 4px;
  background: var(--room-wood-dark);
  color: var(--room-cream);
}
.room-scene-frame,
.room-scene::before {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.room-scene-frame {
  z-index: -2;
  container-type: size;
  overflow: hidden;
  --room-focal-x: 64%;
}
/* 图和唱机入口共用 3:1 坐标系，窄屏裁掉两侧时两者一起移动。 */
.room-scene-furniture {
  position: absolute;
  width: max(100cqw, 300cqh);
  aspect-ratio: 3;
  left: var(--room-focal-x);
  top: 50%;
  transform: translate(calc(-1 * var(--room-focal-x)), -50%);
}
.room-scene-view {
  display: block;
  width: 100%;
  height: 100%;
}
.room-record-player {
  position: absolute;
  left: 44.6%;
  bottom: 52.5%;
  transform: translateX(-50%);
  width: 14%;
  height: 36%;
  display: grid;
  align-items: end;
  justify-items: center;
  color: var(--room-wood-dark);
  text-decoration: none;
}
.room-record-player > span {
  padding: 2px 8px;
  border: 1px solid var(--room-walnut);
  border-radius: 2px;
  background: #e2c48e;
  box-shadow: 0 1px 3px #24170d80;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.4;
}
.room-record-player:is(:hover, :focus-visible) > span {
  background: var(--room-cream);
}
.room-scene::before {
  content: '';
  z-index: -1;
  pointer-events: none;
  background: linear-gradient(90deg, rgba(28, 22, 16, 0.81), rgba(33, 25, 16, 0.56) 24%, transparent 58%);
}
.room-scene-welcome {
  width: fit-content;
  max-width: min(60%, 430px);
  padding: clamp(20px, 2.2vw, 34px);
  box-sizing: border-box;
  overflow-wrap: anywhere;
  pointer-events: none;
}
.room-scene-welcome :deep(:is(a, button)) {
  pointer-events: auto;
}
.room-scene-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 16px;
}
@media (max-width: 768px) {
  .room-scene {
    display: flex;
    flex-direction: column;
    min-height: 0;
    border-radius: 0;
  }
  .room-scene::before {
    content: none;
  }
  .room-scene-frame {
    position: relative;
    width: 100%;
    height: clamp(145px, 46vw, 236px);
    --room-focal-x: 72%;
  }
  .room-scene-welcome {
    width: 100%;
    max-width: none;
    padding: 14px 20px;
    background:
      linear-gradient(#2c190fb8, #2c190fad),
      var(--room-veneer) center / 380px auto;
  }
}
</style>
