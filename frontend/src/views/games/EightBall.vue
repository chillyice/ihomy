<template>
  <EightBallTable :key="String(route.query.room || '')" :opening-room="opening" @online-play="openRoom" />
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { roomApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { rememberRoomGame } from '@/utils/gameRoom'
import EightBallTable from '@/components/games/EightBallTable.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const user = useUserStore()
const opening = ref(false)

async function openRoom() {
  if (opening.value) return
  opening.value = true
  try {
    const rooms = (await roomApi.list()) || []
    const active = rooms.find(
      (r) =>
        r.gameCode === 'game_eight_ball' &&
        ['WAITING', 'PLAYING'].includes(r.status) &&
        r.members?.some((m) => String(m.userId) === String(user.userInfo?.id)),
    )
    const room = active || (await roomApi.create({ gameCode: 'game_eight_ball' }))
    if (!active) rememberRoomGame('game_eight_ball')
    await router.push(`/games/room/${room.id}`)
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.createFailed'))
  } finally {
    opening.value = false
  }
}
</script>
