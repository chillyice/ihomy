<template>
  <span class="pc-face" :class="{ red: isRed }">
    <!-- J/Q/K:图集里自带角标与对花倒影的宫廷画,整张铺满即可,不再叠自己画的角标 -->
    <span v-if="courtStyle" class="pc-art" :style="courtStyle" aria-hidden="true"></span>
    <template v-else>
      <span class="pc-corner pc-corner-tl" aria-hidden="true">
        <b>{{ rankLabel }}</b>
        <i>{{ suitSymbol }}</i>
      </span>
      <span class="pc-corner pc-corner-br" aria-hidden="true">
        <b>{{ rankLabel }}</b>
        <i>{{ suitSymbol }}</i>
      </span>
      <span class="pc-pips" :class="{ 'pc-ace': card.rank === 1, 'pc-dense': card.rank >= 9 }" aria-hidden="true">
        <i v-for="(pip, idx) in pips" :key="idx" class="pc-pip" :style="{ left: `${pip[0]}%`, top: `${pip[1]}%` }">
          {{ suitSymbol }}
        </i>
      </span>
    </template>
  </span>
</template>

<script setup>
// 扑克牌正面:A~10 是四角点数 + 按点数铺开的花色图案,J/Q/K 用图集
// (assets/games/tiles-cards-atlas.png)里出好的人头牌画;图集缺格时退回角标 + 大写字母。
// 画面尺寸全部由外层 --sol-card-w 推导,牌桌只负责外框、选中态与叠放位置。
import { computed } from 'vue'
import { pipPoints } from '@/utils/games/cardPips'
import { pokerFaceStyle } from '@/utils/games/tileAtlas'

const props = defineProps({
  card: { type: Object, required: true },
})

const SUIT_SYMBOL = { S: '♠', H: '♥', D: '♦', C: '♣' }
const RANK_LABEL = { 1: 'A', 11: 'J', 12: 'Q', 13: 'K' }

const suitSymbol = computed(() => SUIT_SYMBOL[props.card.suit] || '')
const rankLabel = computed(() => RANK_LABEL[props.card.rank] || String(props.card.rank))
const pips = computed(() => pipPoints(props.card.rank))
const isRed = computed(() => props.card.suit === 'H' || props.card.suit === 'D')
const courtStyle = computed(() => pokerFaceStyle(props.card.suit, props.card.rank))
</script>
