<!-- 日记涂鸦笔盘:不同笔尖的画笔陈列 + 粗细调节 + 调色盘;点击画笔选中/取消 -->
<template>
  <div class="doodle-area">
    <div class="tray-title">{{ $t('diary.doodleTitle') }}</div>

    <div class="pen-grid">
      <button v-for="b in BRUSHES" :key="b.id" type="button" class="pen-item" :class="{ active: brush === b.id }" :title="$t(b.labelKey)" @click="$emit('update:brush', brush === b.id ? null : b.id)">
        <span class="pen-svg">
          <!-- 签字笔 -->
          <svg v-if="b.id === 'gel'" viewBox="0 0 26 52">
            <rect x="9" y="3" width="8" height="25" rx="2.5" fill="none" stroke="currentColor" stroke-width="1.5" />
            <path d="M10.5 8 h5" stroke="currentColor" stroke-width="1.2" />
            <path d="M9 28 L17 28 L14.2 39 L11.8 39 Z" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
            <path d="M11.8 39 L14.2 39 L13 48 Z" :fill="brushColor" />
          </svg>
          <!-- 铅笔 -->
          <svg v-else-if="b.id === 'pencil'" viewBox="0 0 26 52">
            <rect x="9.5" y="3" width="7" height="24" fill="none" stroke="currentColor" stroke-width="1.5" />
            <path d="M11.5 9 h3 M11.5 13 h3" stroke="currentColor" stroke-width="1" opacity="0.5" />
            <path d="M9.5 27 L16.5 27 L13 41 Z" fill="#E8CE9E" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
            <path d="M11.9 36.5 L14.1 36.5 L13 41 Z" :fill="brushColor" />
          </svg>
          <!-- 蜡笔 -->
          <svg v-else-if="b.id === 'crayon'" viewBox="0 0 26 52">
            <path d="M8 6 h10 v22 q0 3 -2 4 l-2 9 h-2 l-2 -9 q-2 -1 -2 -4 Z" :fill="brushColor" fill-opacity="0.75" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
            <path d="M8 14 h10 M8 18 h10" stroke="currentColor" stroke-width="1" opacity="0.45" />
          </svg>
          <!-- 荧光笔 -->
          <svg v-else-if="b.id === 'marker'" viewBox="0 0 26 52">
            <rect x="9" y="3" width="8" height="20" rx="2" fill="none" stroke="currentColor" stroke-width="1.5" />
            <path d="M9 9 h8" stroke="currentColor" stroke-width="1.2" />
            <path d="M8.5 23 L17.5 23 L15.5 41 L10.5 39 Z" :fill="brushColor" fill-opacity="0.7" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" />
          </svg>
          <!-- 画笔 -->
          <svg v-else-if="b.id === 'brush'" viewBox="0 0 26 52">
            <rect x="11.5" y="3" width="3" height="19" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.5" />
            <rect x="10.5" y="22" width="5" height="5" fill="none" stroke="currentColor" stroke-width="1.3" />
            <path d="M10.5 27 Q13.5 34 11 44 Q13 50 15.5 44 Q14.5 34 15.5 27 Z" :fill="brushColor" fill-opacity="0.85" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
          </svg>
          <!-- 像素橡皮 -->
          <svg v-else-if="b.id === 'eraserP'" viewBox="0 0 26 52">
            <rect x="7" y="12" width="12" height="22" rx="3" fill="none" stroke="currentColor" stroke-width="1.5" />
            <path d="M7 19 h12" stroke="currentColor" stroke-width="1" opacity="0.45" />
            <g :fill="brushColor">
              <rect x="9" y="41" width="3" height="3" /><rect x="14" y="41" width="3" height="3" /><rect x="11.5" y="45" width="3" height="3" />
            </g>
          </svg>
          <!-- 对象橡皮 -->
          <svg v-else viewBox="0 0 26 52">
            <rect x="7" y="12" width="12" height="22" rx="3" fill="none" stroke="currentColor" stroke-width="1.5" />
            <path d="M7 19 h12" stroke="currentColor" stroke-width="1" opacity="0.45" />
            <path d="M7 45 q3.5 -5 6.5 0 t6.5 0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" opacity="0.55" />
            <path d="M18.5 40.5 l5 5 M23.5 40.5 l-5 5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </span>
        <span class="pen-name">{{ $t(b.labelKey) }}</span>
      </button>
    </div>

    <div class="tray-row">
      <span class="tray-label">{{ $t('diary.thickness') }}</span>
      <el-slider :model-value="size" :min="1" :max="12" :step="1" size="small" @update:model-value="(v) => $emit('update:size', v)" />
    </div>

    <div class="tray-row">
      <span class="tray-label">{{ $t('diary.alphaLabel') }}</span>
      <el-slider :model-value="alpha" :min="10" :max="100" :step="5" size="small" @update:model-value="(v) => $emit('update:alpha', v)" />
    </div>

    <div class="tray-row palette-row">
      <span class="tray-label">{{ $t('diary.colorLabel') }}</span>
      <div class="palette">
        <button v-for="c in INK_COLORS" :key="c" type="button" class="swatch" :class="{ active: brushColor.toLowerCase() === c.toLowerCase() }" :style="{ background: c }" @click="$emit('update:brushColor', c)" />
        <label class="swatch custom" :class="{ active: !INK_COLORS.some((c) => c.toLowerCase() === brushColor.toLowerCase()) }" :title="$t('diary.customColor')">
          <input type="color" :value="safeColor" @input="(e) => $emit('update:brushColor', e.target.value)" />
        </label>
      </div>
    </div>

    <div class="tray-actions">
      <button type="button" class="ghost-btn small" :disabled="!canUndo" @click="$emit('undo')">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/></svg>
        {{ $t('diary.undo') }}
      </button>
      <button type="button" class="ghost-btn small" :disabled="!canRedo" @click="$emit('redo')">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 14l5-5-5-5"/><path d="M20 9H10a6 6 0 0 0 0 12h3"/></svg>
        {{ $t('diary.redo') }}
      </button>
    </div>

    <div class="tray-hint">{{ $t('diary.trayHint') }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { BRUSHES, INK_COLORS } from '@/utils/doodle'

const props = defineProps({
  brush: { type: String, default: null },
  size: { type: Number, default: 4 },
  alpha: { type: Number, default: 100 },
  brushColor: { type: String, default: '#3A2E22' },
  canUndo: { type: Boolean, default: false },
  canRedo: { type: Boolean, default: false },
})
defineEmits(['update:brush', 'update:size', 'update:alpha', 'update:brushColor', 'undo', 'redo'])

const safeColor = computed(() => (/^#[0-9a-fA-F]{6}$/.test(props.brushColor) ? props.brushColor : '#3A2E22'))
</script>

<style scoped>
.doodle-area {
  background: rgba(255,255,255,0.45); backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2); border-radius: 14px;
  width: calc(14 * 16px + 24px * 2);
  padding: 14px 16px;
  border: 1px solid rgba(255,255,255,0.4); box-shadow: 0 2px 12px rgba(58,46,34,0.06);
  flex-shrink: 0; position: sticky; top: 16px; align-self: flex-start;
}
html.dark .doodle-area {
  background: rgba(30,42,72,0.45); border-color: rgba(255,255,255,0.08);
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}

.tray-title { font-size: 12px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 10px; text-align: center; letter-spacing: 2px; }

.pen-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px 2px; }

.pen-item {
  border: none; background: transparent; cursor: pointer; padding: 4px 0 6px;
  border-radius: 10px; color: var(--color-text-secondary);
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  transition: background 0.2s;
}
.pen-item:hover { background: rgba(184,140,110,0.08); }
html.dark .pen-item:hover { background: rgba(212,178,152,0.08); }

.pen-svg { display: block; width: 26px; height: 52px; transition: transform 0.22s ease; }
.pen-item:hover .pen-svg { transform: translateY(-5px) rotate(-4deg); }
.pen-item.active { color: var(--color-text); background: rgba(184,140,110,0.1); }
html.dark .pen-item.active { background: rgba(212,178,152,0.12); color: #E8DCC8; }
.pen-item.active .pen-svg { transform: translateY(-5px) rotate(-4deg); filter: drop-shadow(0 4px 6px rgba(58,46,34,0.25)); }

.pen-name { font-size: 10.5px; color: var(--color-text-secondary); opacity: 0.75; }
.pen-item.active .pen-name { color: #b88c6e; opacity: 1; font-weight: 600; }
html.dark .pen-item.active .pen-name { color: #d4b298; }

.tray-row { display: flex; align-items: center; gap: 10px; margin-top: 12px; }
.tray-label { font-size: 12px; color: var(--color-text-secondary); flex-shrink: 0; min-width: 3.2em; }
.tray-row .el-slider { flex: 1; }

.palette { display: grid; grid-template-columns: repeat(7, 1fr); gap: 6px; flex: 1; justify-items: center; }
.swatch {
  width: 18px; height: 18px; border-radius: 50%; border: 2px solid rgba(255,255,255,0.8);
  cursor: pointer; padding: 0; position: relative;
  box-shadow: 0 1px 3px rgba(58,46,34,0.2);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.swatch:hover { transform: scale(1.18); }
.swatch.active { box-shadow: 0 0 0 2px #b88c6e, 0 1px 4px rgba(58,46,34,0.3); transform: scale(1.12); }
html.dark .swatch { border-color: rgba(255,255,255,0.25); }
.swatch.custom { border: none; background: conic-gradient(#E3B23C, #5B8C5A, #4A7FB5, #7B5EA7, #C96A8B, #D97B29, #E3B23C); }
.swatch.custom::after {
  content: '+'; position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: #fff; text-shadow: 0 0 3px rgba(0,0,0,0.6);
  pointer-events: none;
}
.swatch.custom input { position: absolute; inset: -4px; opacity: 0; cursor: pointer; }

.tray-actions { display: flex; gap: 8px; margin-top: 14px; }
.tray-actions .ghost-btn { flex: 1; justify-content: center; }

.tray-hint { font-size: 11px; color: var(--color-text-secondary); opacity: 0.55; line-height: 1.6; margin-top: 12px; }

@media (max-width: 768px) {
  .doodle-area { width: 100%; position: static; }
  .pen-svg { width: 22px; height: 44px; }
}
</style>
