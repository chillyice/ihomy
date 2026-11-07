<!-- 家庭对战详情:邀请卡/结果卡的落点。看比分与状态,待加入的加入、未开赛的取消/补发邀请,
     进行中的直接进游戏页(2048 同种子赛 / 贪吃蛇实时对战) -->
<template>
  <div class="page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions :is-fullscreen="false" :wide="false" @back="router.push('/games')" />
    </PageToolbar>

    <div class="match-card card" v-loading="loading">
      <template v-if="match">
        <div class="match-head">
          <el-icon :size="22" class="match-icon"><Trophy /></el-icon>
          <div class="match-title">
            <b>{{ gameName }}</b>
            <span class="match-mode">{{ $t(match.mode === 'REALTIME' ? 'games.match.modeRealtime' : 'games.match.modeAsync') }}</span>
          </div>
          <el-tag :type="statusTagType" size="small">{{ $t(statusText) }}</el-tag>
        </div>

        <div class="match-players">
          <div class="player" :class="{ win: isWinner(match.hostId) }">
            <el-avatar :size="40">{{ match.hostName?.charAt(0) }}</el-avatar>
            <div class="player-info">
              <div class="player-name">
                {{ match.hostName }} <span v-if="isHost" class="you-tag">{{ $t('games.match.you') }}</span>
              </div>
              <div class="player-score">{{ match.hostScore ?? '—' }}</div>
            </div>
          </div>
          <div class="vs">{{ $t('games.match.vsShort') }}</div>
          <div class="player" :class="{ win: isWinner(match.guestId) }">
            <el-avatar :size="40">{{ match.guestName?.charAt(0) || '?' }}</el-avatar>
            <div class="player-info">
              <div class="player-name">
                {{ match.guestName || $t('games.match.emptySeat') }} <span v-if="isGuest" class="you-tag">{{ $t('games.match.you') }}</span>
              </div>
              <div class="player-score">{{ match.guestScore ?? '—' }}</div>
            </div>
          </div>
        </div>

        <div v-if="match.status === 'FINISHED'" class="match-outcome">
          <template v-if="match.winnerId == null">{{ $t('games.match.draw') }}</template>
          <template v-else>{{ $t('games.match.winnerIs', { name: isWinner(match.hostId) ? match.hostName : match.guestName }) }}</template>
        </div>

        <div class="match-actions">
          <template v-if="match.status === 'WAITING'">
            <el-button v-if="!isHost" type="primary" round :loading="acting" @click="join">
              {{ $t('games.match.join') }}
            </el-button>
            <el-button round @click="inviteToChat">{{ $t('games.match.inviteChat') }}</el-button>
            <el-button v-if="isHost" round type="danger" plain :loading="acting" @click="cancel">
              {{ $t('games.match.cancel') }}
            </el-button>
          </template>
          <template v-else-if="match.status === 'PLAYING'">
            <el-button type="primary" round @click="enterGame">
              {{ $t('games.match.enter') }}
            </el-button>
            <el-button round @click="inviteToChat">{{ $t('games.match.inviteChat') }}</el-button>
          </template>
          <el-button round @click="reload">{{ $t('common.refresh') }}</el-button>
        </div>
      </template>
      <el-empty v-else-if="!loading" :description="$t('games.match.notFound')" :image-size="70" />
    </div>
  </div>
</template>

<script setup>
// 对战详情:数据 matchApi;邀请补发走 chatStore.sendContent(GAME_INVITE + matchId)
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Trophy } from '@element-plus/icons-vue'
import { matchApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { buildGameInvitePayload, gameByCode } from '@/utils/chatMsg'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const userStore = useUserStore()
const chatStore = useChatStore()

const match = ref(null)
const loading = ref(true)
const acting = ref(false)
const matchId = computed(() => Number(route.params.id) || 0)

const isHost = computed(() => match.value?.hostId === userStore.userInfo?.id)
const isGuest = computed(() => match.value?.guestId === userStore.userInfo?.id)
const gameName = computed(() => {
  const g = gameByCode(match.value?.gameCode)
  return g ? t(g.nameKey) : match.value?.gameCode || ''
})
const statusText = computed(() => `games.match.status.${(match.value?.status || 'WAITING').toLowerCase()}`)
const statusTagType = computed(
  () => ({ WAITING: 'warning', PLAYING: 'primary', FINISHED: 'success', CANCELLED: 'info' })[match.value?.status] || 'info',
)

const isWinner = (id) => !!id && match.value?.winnerId === id

const enterGame = () => {
  const code = match.value?.gameCode
  if (code === 'game_snake') router.push(`/games/snake/match/${matchId.value}`)
  else router.push({ path: '/games/2048', query: { match: matchId.value } })
}

const reload = async () => {
  loading.value = true
  try {
    match.value = await matchApi.get(matchId.value)
  } catch {
    match.value = null
  } finally {
    loading.value = false
  }
}

const join = async () => {
  acting.value = true
  try {
    match.value = await matchApi.join(matchId.value)
    enterGame()
  } catch (e) {
    ElMessage.warning(e?.message || t('games.match.joinFailed'))
    reload()
  } finally {
    acting.value = false
  }
}

const cancel = async () => {
  acting.value = true
  try {
    match.value = await matchApi.cancel(matchId.value)
  } catch (e) {
    ElMessage.warning(e?.message || t('games.match.actFailed'))
    reload()
  } finally {
    acting.value = false
  }
}

const inviteToChat = () => {
  const code = match.value?.gameCode
  const payload = buildGameInvitePayload(code)
  if (!payload || !match.value) return
  const ok = chatStore.sendContent({ msgType: 'GAME_INVITE', content: gameName.value, payload: { ...payload, matchId: matchId.value } })
  ElMessage[ok ? 'success' : 'warning'](t(ok ? 'games.match.inviteSent' : 'games.match.inviteFailed'))
}

onMounted(reload)
</script>

<style scoped>
.match-card {
  max-width: 560px;
  margin: 24px auto;
  padding: 20px 24px;
}
.match-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.match-icon {
  color: var(--color-brand-text);
}
.match-title {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 17px;
}
.match-mode {
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}
.match-players {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 20px 0;
}
.player {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--color-bg-2);
}
.player.win {
  outline: 2px solid rgba(var(--color-brand-rgb), 0.5);
}
.player-info {
  min-width: 0;
}
.player-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.you-tag {
  font-size: 11px;
  color: var(--color-brand-text);
}
.player-score {
  font-size: 20px;
  font-weight: 700;
}
.vs {
  font-size: 13px;
  color: var(--color-text-tertiary, #aaa);
}
.match-outcome {
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 14px;
}
.match-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}
</style>
