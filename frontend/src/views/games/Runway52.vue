<template>
  <Ludo v-if="roomId" />
  <div v-else class="page">
    <div class="page-toolbar card">
      <div class="tb-right">
        <el-button @click="router.push('/games')">{{ $t('games.back') }}</el-button>
      </div>
    </div>
    <iframe class="runway-table" :src="tableUrl" title="Runway 52"></iframe>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const Ludo = defineAsyncComponent(() => import('./Ludo.vue'))
const route = useRoute()
const router = useRouter()
const roomId = computed(() => Number(route.query.room) || 0)
const tableUrl = `${import.meta.env.BASE_URL}runway-52.html`
</script>

<style scoped>
.runway-table {
  display: block;
  width: 100%;
  height: max(440px, calc(100dvh - 160px));
  border: 0;
  border-radius: 14px;
  background: #172522;
}
</style>
