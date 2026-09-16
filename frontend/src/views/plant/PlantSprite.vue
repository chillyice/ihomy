<!-- 植物 SVG 精灵:按品种(配色)与阶段(生长形态)绘制,枯萎时整体降饱和。纯矢量无位图资产。 -->
<template>
  <svg viewBox="0 0 200 260" xmlns="http://www.w3.org/2000/svg" :style="{ filter }" role="img" aria-label="plant">
    <!-- 地面投影 -->
    <ellipse cx="100" cy="252" rx="42" ry="7" fill="rgba(0,0,0,0.10)" />

    <!-- 花盆 -->
    <path d="M72 196 H128 L122 246 H78 Z" fill="#c98a5e" />
    <rect x="66" y="184" width="68" height="16" rx="5" fill="#d79a6e" />
    <ellipse cx="100" cy="190" rx="27" ry="8" fill="#7a5233" />

    <!-- 茎(种子阶段无茎) -->
    <rect
      v-if="cfg.head !== 'none'"
      x="96"
      :y="cfg.stemTop"
      width="8"
      :height="188 - cfg.stemTop"
      rx="4"
      :fill="palette.stem"
    />

    <!-- 茎生叶(成对,数量随阶段增多) -->
    <g v-for="(y, i) in leafPairs" :key="i">
      <ellipse cx="100" :cy="y" rx="7" ry="18" :fill="palette.leaf" :transform="`rotate(-38 100 ${y})`" />
      <ellipse cx="100" :cy="y" rx="7" ry="18" :fill="palette.leaf" :transform="`rotate(38 100 ${y})`" />
    </g>

    <!-- 种子(埋在土里的小点) -->
    <ellipse v-if="cfg.head === 'none'" cx="100" cy="184" rx="5" ry="3" :fill="palette.center" />

    <!-- 子叶(发芽阶段两片圆叶) -->
    <g v-if="cfg.head === 'cotyledon'">
      <ellipse cx="91" :cy="cfg.stemTop" rx="8" ry="12" :fill="palette.leaf" :transform="`rotate(-28 91 ${cfg.stemTop})`" />
      <ellipse cx="109" :cy="cfg.stemTop" rx="8" ry="12" :fill="palette.leaf" :transform="`rotate(28 109 ${cfg.stemTop})`" />
    </g>

    <!-- 幼苗顶叶簇 -->
    <g v-if="cfg.head === 'leaf'">
      <ellipse cx="100" :cy="cfg.stemTop" rx="6" ry="13" :fill="palette.leaf" />
      <ellipse cx="89" :cy="cfg.stemTop + 2" rx="5" ry="10" :fill="palette.leaf" :transform="`rotate(-40 89 ${cfg.stemTop + 2})`" />
      <ellipse cx="111" :cy="cfg.stemTop + 2" rx="5" ry="10" :fill="palette.leaf" :transform="`rotate(40 111 ${cfg.stemTop + 2})`" />
    </g>

    <!-- 花苞 -->
    <g v-if="cfg.head === 'bud'">
      <circle cx="100" :cy="cfg.stemTop" r="9" :fill="palette.flower" />
      <path :d="`M100 ${cfg.stemTop - 6} q -8 -4 -12 -10`" stroke="rgba(0,0,0,0.12)" stroke-width="2" fill="none" stroke-linecap="round" />
    </g>

    <!-- 花朵(花瓣 + 花心) -->
    <g v-if="cfg.head === 'flower' || cfg.head === 'fruit'">
      <ellipse
        v-for="a in petals"
        :key="a"
        cx="100"
        :cy="cfg.stemTop"
        rx="9"
        ry="16"
        :fill="palette.flower"
        :transform="`rotate(${a} 100 ${cfg.stemTop})`"
      />
      <circle cx="100" :cy="cfg.stemTop" r="10" :fill="palette.center" />
    </g>

    <!-- 果实(花朵 + 浆果) -->
    <g v-if="cfg.head === 'fruit'">
      <circle cx="100" :cy="cfg.stemTop" r="4.5" fill="#d96a5a" />
      <circle cx="90" :cy="cfg.stemTop - 4" r="3.5" fill="#e88a6a" />
      <circle cx="110" :cy="cfg.stemTop - 4" r="3.5" fill="#e88a6a" />
    </g>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  species: { type: String, default: 'SUNFLOWER' },
  stage: { type: String, default: 'SEED' },
  wilted: { type: Boolean, default: false },
})

const PALETTES = {
  SUNFLOWER: { flower: '#f2c14e', center: '#8a5a2b', leaf: '#6f9e4f', stem: '#5d8a44' },
  ROSE: { flower: '#e7799c', center: '#b34a6e', leaf: '#5f9b5a', stem: '#4f8a4a' },
  SUCCULENT: { flower: '#8fb56e', center: '#5e8f4e', leaf: '#8fb573', stem: '#6f9e63' },
}
const palette = computed(() => PALETTES[props.species] || PALETTES.SUNFLOWER)

// 阶段 → 生长形态:stemTop 越小植物越高(头顶越靠上),leaves 为茎生叶「对数」
const STAGE_CFG = {
  SEED: { stemTop: 186, leaves: 0, head: 'none' },
  SPROUT: { stemTop: 164, leaves: 0, head: 'cotyledon' },
  SEEDLING: { stemTop: 132, leaves: 1, head: 'leaf' },
  BUD: { stemTop: 104, leaves: 2, head: 'bud' },
  FLOWER: { stemTop: 84, leaves: 2, head: 'flower' },
  FRUIT: { stemTop: 84, leaves: 2, head: 'fruit' },
}
const cfg = computed(() => STAGE_CFG[props.stage] || STAGE_CFG.SEED)

const petals = [0, 60, 120, 180, 240, 300]

const leafPairs = computed(() => {
  const top = cfg.value.stemTop
  const pairs = []
  if (cfg.value.leaves >= 1) pairs.push(top + 30)
  if (cfg.value.leaves >= 2) pairs.push(top + 54)
  return pairs
})

const filter = computed(() => (props.wilted ? 'saturate(0.55) brightness(0.86)' : 'none'))
</script>
