<template>
  <span class="pc-face" :class="{ red: isRed }">
    <span class="pc-corner pc-corner-tl" aria-hidden="true">
      <b>{{ rankLabel }}</b>
      <i>{{ suitSymbol }}</i>
    </span>
    <span class="pc-corner pc-corner-br" aria-hidden="true">
      <b>{{ rankLabel }}</b>
      <i>{{ suitSymbol }}</i>
    </span>
    <span v-if="court" class="pc-court" aria-hidden="true">
      <span class="pc-court-frame">
        <b>{{ court }}</b>
        <i>{{ suitSymbol }}</i>
      </span>
    </span>
    <span v-else class="pc-pips" :class="{ 'pc-ace': card.rank === 1, 'pc-dense': card.rank >= 9 }" aria-hidden="true">
      <i v-for="(pip, idx) in pips" :key="idx" class="pc-pip" :style="{ left: `${pip[0]}%`, top: `${pip[1]}%` }">
        {{ suitSymbol }}
      </i>
    </span>
  </span>
</template>

<script setup>
// 扑克牌正面:四角点数 + 按点数铺开的花色图案(J/Q/K 用字母宫格)。
// 画面尺寸全部由外层 --sol-card-w 推导,牌桌只负责外框、选中态与叠放位置。
import { computed } from 'vue'
import { isCourtCard, pipPoints } from '@/utils/games/cardPips'

const props = defineProps({
  card: { type: Object, required: true },
})

const SUIT_SYMBOL = { S: '♠', H: '♥', D: '♦', C: '♣' }
const COURT_LABEL = { 11: 'J', 12: 'Q', 13: 'K' }
const RANK_LABEL = { 1: 'A', 11: 'J', 12: 'Q', 13: 'K' }

const suitSymbol = computed(() => SUIT_SYMBOL[props.card.suit] || '')
const rankLabel = computed(() => RANK_LABEL[props.card.rank] || String(props.card.rank))
const court = computed(() => (isCourtCard(props.card.rank) ? COURT_LABEL[props.card.rank] : ''))
const pips = computed(() => pipPoints(props.card.rank))
const isRed = computed(() => props.card.suit === 'H' || props.card.suit === 'D')
</script>
