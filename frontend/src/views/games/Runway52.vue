<template>
  <Ludo v-if="roomId" />
  <GameShell v-else ref="shellEl" class="rw-shell" :title="$t('games.ludo.title')" body-align="stretch" @back="goBack">
    <div class="runway-stage">
      <iframe class="runway-table" :src="tableUrl" title="Runway 52"></iframe>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'

const Ludo = defineAsyncComponent(() => import('./Ludo.vue'))
const route = useRoute()
const router = useRouter()
const roomId = computed(() => Number(route.query.room) || 0)
const tableUrl = `${import.meta.env.BASE_URL}runway-52.html`

const shellEl = ref(null)

// 返回固定回上一级(小游戏列表):从首页卡片 / 分享链接直达时也回列表,不按浏览历史回到上一页
const goBack = () => {
  router.push('/games')
}
</script>

<style scoped>
.runway-stage {
  flex: 1 1 auto;
  min-height: 0;
  min-width: 0;
  display: flex;
}

.runway-table {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 320px;
  border: 0;
  border-radius: 14px;
  background: #172522;
}
:global(html.game-mobile-immersive) .runway-table {
  min-height: 0;
  border-radius: 0;
}

/* 手机横屏/矮窗:外壳游戏区给多高就占多高(静态页自己按容器高铺满),
   不再用 320px 的下限把矮游戏区撑破、顶出外壳 */
@media (max-height: 520px) {
  .runway-table {
    min-height: 0;
  }
}
</style>
