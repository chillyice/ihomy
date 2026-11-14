<!-- 开房配置弹窗:选小游戏 + 选玩法(合作/对抗,按游戏支持项),确认后由宿主页建房进大厅 -->
<template>
  <el-dialog
    :model-value="visible"
    class="dialog-md"
    :title="$t('games.roomCreate.title')"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="rc-body">
      <div class="rc-label">{{ $t('games.roomCreate.pickGame') }}</div>
      <div class="rc-games">
        <button
          v-for="g in gameList"
          :key="g.code"
          type="button"
          class="rc-game"
          :class="{ active: g.code === pickedCode }"
          :aria-pressed="g.code === pickedCode"
          @click="pickGame(g.code)"
        >
          <span class="rc-game-name">{{ $t(g.nameKey) }}</span>
          <span class="rc-game-meta">
            <span class="rc-tag">{{ $t('games.roomCreate.seats', { n: g.maxSeats }) }}</span>
            <span class="rc-tag" :class="{ coop: isCoopMode(g.modes) }">{{ modesText(g.modes) }}</span>
          </span>
        </button>
      </div>

      <div class="rc-label">{{ $t('games.roomCreate.pickMode') }}</div>
      <div class="rc-modes">
        <template v-if="pickedModes.length > 1">
          <button
            v-for="m in pickedModes"
            :key="m"
            type="button"
            class="rc-mode"
            :class="{ active: m === pickedMode }"
            :aria-pressed="m === pickedMode"
            @click="pickedMode = m"
          >
            {{ $t(m === ROOM_MODE.COOP ? 'games.room.modeCoop' : 'games.room.modePk') }}
          </button>
        </template>
        <template v-else>
          <span class="rc-mode-locked">{{ $t(pickedModes[0] === ROOM_MODE.COOP ? 'games.room.modeCoop' : 'games.room.modePk') }}</span>
        </template>
        <span class="rc-hint">{{ $t('games.roomCreate.spectatorHint') }}</span>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:visible', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :disabled="!pickedCode" :loading="creating" @click="confirm">
        {{ $t('games.roomCreate.create') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ROOM_GAMES, ROOM_MODE, defaultRoomMode, isCoop } from '@/utils/gameRoom'

const props = defineProps({
  visible: { type: Boolean, default: false },
  creating: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'confirm'])

const { t } = useI18n()

const gameList = Object.values(ROOM_GAMES)
const pickedCode = ref('game_petlink')
const pickedMode = ref(defaultRoomMode(pickedCode.value))

const pickedModes = computed(() => ROOM_GAMES[pickedCode.value]?.modes || [])
// 有 PK 优先 PK,与建房默认口径一致
const pickGame = (code) => {
  pickedCode.value = code
  pickedMode.value = defaultRoomMode(code)
}

const isCoopMode = (modes) => modes.length === 1 && isCoop(modes[0])
const modesText = (modes) => {
  if (modes.length === 1) return t(isCoop(modes[0]) ? 'games.room.modeCoop' : 'games.room.modePk')
  return t('games.roomCreate.modesBoth')
}

// 弹窗每次打开回到默认选中,避免上次的残留
watch(
  () => props.visible,
  (v) => {
    if (v) {
      pickedCode.value = 'game_petlink'
      pickedMode.value = defaultRoomMode(pickedCode.value)
    }
  },
)

const confirm = () => {
  if (!pickedCode.value) return
  emit('confirm', { gameCode: pickedCode.value, mode: pickedMode.value })
}
</script>

<style scoped>
.rc-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rc-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.rc-games {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
}
.rc-game {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  padding: 10px 12px;
  border: 1.5px solid var(--color-border);
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.rc-game:hover {
  background: rgba(var(--color-brand-rgb), 0.06);
}
.rc-game.active {
  border-color: var(--color-brand);
  background: rgba(var(--color-brand-rgb), 0.1);
}
.rc-game-name {
  font-size: 14px;
  color: var(--color-text);
}
.rc-game-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.rc-tag {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(var(--color-brand-rgb), 0.12);
  color: var(--color-brand);
}
.rc-tag.coop {
  background: rgba(103, 194, 58, 0.14);
  color: var(--el-color-success);
}
.rc-modes {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.rc-mode {
  padding: 6px 16px;
  border: 1.5px solid var(--color-border);
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text);
  transition:
    border-color 0.15s,
    background 0.15s;
}
.rc-mode.active {
  border-color: var(--color-brand);
  background: rgba(var(--color-brand-rgb), 0.1);
  color: var(--color-brand);
}
.rc-mode-locked {
  font-size: 13px;
  padding: 6px 16px;
  border-radius: 999px;
  background: rgba(var(--color-brand-rgb), 0.1);
  color: var(--color-brand);
}
.rc-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
