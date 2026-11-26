<!-- 功能开关:把导航里的每个功能逐个设成「启用 / 禁用 / 仅成年人」,「游戏」下还能细化到单个小游戏。
     与显示模式同一套口径——记录按账号存在这台设备上(utils/featureGates.js),不上传服务端:
     「禁用」= 这台设备上看不到入口(导航、首页卡片、直达链接一起收起),不动账号权限与内容;
     「仅成年人」= 未成年模式下隐藏,默认/老人模式照常显示。
     清单来自 buildFeatureCatalog(与导航同源,关掉的功能也仍在这里改得回来)。 -->
<template>
  <div class="card settings-card fg-settings">
    <div class="section-label">{{ $t('featureGates.title') }}</div>
    <p class="share-tip fg-lead">{{ $t('featureGates.lead') }}</p>

    <!-- 家长:选择要为谁设置;普通成员只给自己设置 -->
    <el-form v-if="isOwner" label-position="top" class="settings-form">
      <el-form-item :label="$t('featureGates.targetUser')">
        <el-select v-model="targetId" class="target-select">
          <el-option v-for="m in memberOptions" :key="m.id" :value="m.id" :label="m.name" />
        </el-select>
        <div class="share-tip">{{ $t('featureGates.targetHint') }}</div>
      </el-form-item>
    </el-form>

    <div class="fg-legend">
      <span v-for="s in STATES" :key="s" class="fg-legend-item">
        <b>{{ $t('featureGates.state.' + s) }}</b>
        <span>{{ $t('featureGates.stateDesc.' + s) }}</span>
      </span>
    </div>

    <div v-for="group in groups" :key="group.category" class="fg-group">
      <div class="fg-group-head">{{ $t('featureGates.group.' + group.category) }}</div>
      <template v-for="item in group.items" :key="item.id">
        <div class="fg-row">
          <span class="fg-name" :class="{ dim: item.gate === 'off' }">{{ item.title }}</span>
          <el-radio-group
            :model-value="item.gate"
            size="small"
            class="fg-states"
            :aria-label="item.title"
            @change="(v) => onSet(item.id, v)"
          >
            <el-radio-button v-for="s in STATES" :key="s" :value="s">{{ $t('featureGates.state.' + s) }}</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 游戏:总开关之外再按单个小游戏细化 -->
        <div v-if="item.id === 'games'" class="fg-games">
          <button type="button" class="fg-games-toggle" @click="showGames = !showGames">
            <span>{{ $t('featureGates.gamesLabel') }}</span>
            <span v-if="tunedGames" class="fg-count">{{ $t('featureGates.gamesTuned', { n: tunedGames }) }}</span>
            <span class="fg-games-action">{{ $t(showGames ? 'featureGates.gamesCollapse' : 'featureGates.gamesExpand') }}</span>
          </button>
          <div v-if="showGames" class="fg-games-list">
            <div v-for="g in gameRows" :key="g.id" class="fg-row fg-row-sub">
              <span class="fg-name" :class="{ dim: g.gate === 'off' }">{{ g.title }}</span>
              <el-radio-group :model-value="g.gate" size="small" class="fg-states" :aria-label="g.title" @change="(v) => onSet(g.id, v)">
                <el-radio-button v-for="s in STATES" :key="s" :value="s">{{ $t('featureGates.state.' + s) }}</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <p v-if="showGames" class="share-tip fg-games-hint">{{ $t('featureGates.gamesHint') }}</p>
        </div>
      </template>
    </div>

    <div class="fg-actions">
      <el-button :disabled="!tunedCount" @click="onReset">{{ $t('featureGates.reset') }}</el-button>
      <span class="fg-actions-tip">{{ $t('featureGates.tunedCount', { n: tunedCount }) }}</span>
    </div>

    <div class="fg-note">{{ $t('featureGates.note') }}</div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { memberApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useFeatureGates } from '@/composables/useFeatureGates'
import { buildFeatureCatalog, NAV_GROUP_ORDER } from '@/utils/navModules'
import { GAME_ENTRIES, gameFeatureId } from '@/utils/games/gameEntries'
import { gateOf } from '@/utils/featureGates'

const { t } = useI18n()
const userStore = useUserStore()
const appStore = useAppStore()
const { gatesFor, setGateFor, resetFor } = useFeatureGates()

// 三档顺序即界面顺序:能用的在前,限制在后
const STATES = ['on', 'off', 'adult']

const myId = computed(() => userStore.userInfo?.id ?? null)
const isOwner = computed(() => userStore.isOwner)

const members = ref([])
const targetId = ref(myId.value)
const showGames = ref(false)
// 别的成员的开关存在本机,改动后用版本号催一次重算
const version = ref(0)
const memberOptions = computed(() => members.value)

const gatesMap = computed(() => {
  version.value
  return gatesFor(targetId.value)
})

// 功能清单:与导航同源,但不过滤当前可见性——关掉的功能也要能看到并改回来
const groups = computed(() => {
  const rows = buildFeatureCatalog(appStore.modules || []).map((m) => ({
    id: m.code,
    title: m.title,
    gate: gateOf(gatesMap.value, m.code),
    category: m.category === 'album' ? 'content' : m.category === 'social' ? 'family' : m.category || 'life',
  }))
  return NAV_GROUP_ORDER.map((category) => ({ category, items: rows.filter((r) => r.category === category) })).filter((g) => g.items.length)
})

const gameRows = computed(() =>
  GAME_ENTRIES.map((g) => ({ id: gameFeatureId(g.key), title: t(g.titleKey), gate: gateOf(gatesMap.value, gameFeatureId(g.key)) })),
)

const tunedCount = computed(() => Object.keys(gatesMap.value || {}).length)
const tunedGames = computed(() => gameRows.value.filter((g) => g.gate !== 'on').length)

const onSet = (id, state) => {
  if (targetId.value == null) return
  setGateFor(targetId.value, id, state)
  version.value++
}

const onReset = () => {
  resetFor(targetId.value)
  version.value++
  ElMessage.success(t('featureGates.resetDone'))
}

onMounted(async () => {
  if (targetId.value == null) targetId.value = myId.value
  if (!isOwner.value) return
  try {
    const list = await memberApi.list()
    members.value = (list || []).map((m) => ({
      id: m.id,
      name: m.nickname || m.username || String(m.id),
    }))
    if (!members.value.some((m) => String(m.id) === String(targetId.value))) targetId.value = myId.value
  } catch (e) {
    // 拉不到成员时只留自己,不影响本机设置
    members.value = []
  }
})

watch(myId, (id) => {
  if (targetId.value == null) targetId.value = id
})
watch(isOwner, (owner) => {
  if (!owner) {
    targetId.value = myId.value
    members.value = []
  }
})
</script>

<style scoped>
.settings-card {
  margin-bottom: 24px;
  background: var(--color-card);
}
.fg-lead {
  margin: 0 0 12px;
}
.target-select {
  width: 220px;
  max-width: 100%;
}
.fg-legend {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 4px 0 16px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--color-card-2);
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.fg-legend-item b {
  margin-right: 8px;
  color: var(--color-text);
  font-weight: 600;
}
.fg-group + .fg-group {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 45%, var(--color-text-secondary));
}
.fg-group-head {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--color-text-tertiary);
}
.fg-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px 0;
}
.fg-row + .fg-row {
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 45%, var(--color-text-secondary));
}
.fg-name {
  flex: 1;
  min-width: 96px;
  font-size: 14px;
  color: var(--color-text);
  overflow-wrap: anywhere;
}
.fg-name.dim {
  color: var(--color-text-tertiary);
}
.fg-states {
  flex: none;
}
.fg-row-sub .fg-name {
  font-size: 13px;
}
.fg-games {
  margin: 6px 0 4px 10px;
  padding-left: 10px;
  border-left: 2px solid color-mix(in srgb, var(--color-border) 60%, transparent);
}
.fg-games-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 0;
  border: none;
  background: none;
  color: var(--color-text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.fg-games-toggle:hover {
  color: var(--color-accent);
}
.fg-count {
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-text);
  font-size: 12px;
}
.fg-games-action {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.fg-games-list {
  margin-top: 4px;
}
.fg-games-hint {
  margin: 4px 0 0;
}
.fg-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 45%, var(--color-text-secondary));
}
.fg-actions-tip {
  font-size: 12px;
  color: var(--color-text-tertiary);
}
.fg-note {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--color-card-2);
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.settings-form .el-form-item {
  margin-bottom: 12px;
}
/* 手机:开关组换到下一行,三档按钮等宽好点 */
@media (max-width: 560px) {
  .fg-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 6px;
  }
  .fg-states {
    width: 100%;
    display: flex;
  }
  .fg-states :deep(.el-radio-button) {
    flex: 1;
  }
  .fg-states :deep(.el-radio-button__inner) {
    width: 100%;
    padding-inline: 6px;
  }
}
</style>
