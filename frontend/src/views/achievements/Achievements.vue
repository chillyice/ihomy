<!-- 成就墙页:等级卡 + 成就图鉴(签到/积分/内容/任务/游戏) + 家庭成就榜 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('achievements.title') }]" />

    <!-- 成就归属个人,游客只给登录引导(与积分页同口径,不打注定 401 的请求) -->
    <div v-if="!userStore.isLoggedIn" class="card paper-surface">
      <el-empty :description="$t('achievements.guestEmpty')" />
    </div>

    <template v-else>
      <div v-loading="loading" class="level-card card paper-surface">
        <div class="level-badge">
          <span class="level-num">Lv.{{ summary.level }}</span>
          <span class="level-name">{{ $t(`achievements.levels.${summary.level}`) }}</span>
        </div>
        <div class="level-body">
          <div class="level-score">
            <b>{{ summary.score }}</b>
            <span>{{ $t('achievements.score') }}</span>
          </div>
          <div class="level-count">
            {{ $t('achievements.unlockedCount', { n: summary.unlocked, total: summary.total }) }}
          </div>
          <el-progress :percentage="levelPct" :show-text="false" :stroke-width="8" class="level-bar" />
          <div class="level-next">{{ nextHint }}</div>
        </div>
      </div>

      <p class="ach-hint">{{ $t('achievements.hint') }}</p>

      <div v-if="loadError" class="load-error">
        {{ $t('common.loadFailed') }}
        <el-button text size="small" @click="load">{{ $t('common.retry') }}</el-button>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane :label="$t('achievements.tabWall')" name="wall">
          <div class="cat-chips">
            <el-button
              v-for="c in filters"
              :key="c.value"
              size="small"
              round
              :type="filter === c.value ? 'primary' : 'default'"
              @click="filter = c.value"
            >
              {{ c.label }}
            </el-button>
          </div>

          <div v-if="visibleItems.length" class="ach-grid">
            <div v-for="item in visibleItems" :key="item.code" class="ach-card card paper-surface" :class="{ unlocked: item.unlocked }">
              <div class="ach-icon">{{ item.icon }}</div>
              <div class="ach-name">{{ $t(catalogKeys(item.code).name) }}</div>
              <div class="ach-desc">{{ $t(catalogKeys(item.code).desc) }}</div>
              <template v-if="item.unlocked">
                <div class="ach-foot">
                  <el-tag size="small" type="success" effect="plain">+{{ item.score }}</el-tag>
                  <span class="ach-date">{{ $t('achievements.unlockedAt', { date: unlockDate(item.unlockedAt) }) }}</span>
                </div>
              </template>
              <template v-else>
                <el-progress :percentage="progressPercent(item)" :show-text="false" :stroke-width="4" class="ach-bar" />
                <div class="ach-progress">{{ item.progress }}/{{ item.threshold }}</div>
              </template>
            </div>
          </div>
          <el-empty v-else :description="$t('achievements.emptyCategory')" />
        </el-tab-pane>

        <el-tab-pane :label="$t('achievements.tabRanking')" name="ranking">
          <p class="ach-hint">{{ $t('achievements.rankingHint') }}</p>
          <el-table v-if="ranking.length" :data="ranking" stripe :row-class-name="rowClass">
            <el-table-column :label="$t('achievements.rank')" width="64">
              <template #default="{ $index }">{{ $index + 1 }}</template>
            </el-table-column>
            <el-table-column :label="$t('achievements.member')" min-width="160">
              <template #default="{ row }">
                <span class="rank-name">{{ row.name }}</span>
                <el-tag v-if="isMe(row)" size="small" effect="plain" class="rank-me">{{ $t('achievements.me') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="unlocked" :label="$t('achievements.unlockedColumn')" width="110" />
            <el-table-column prop="score" :label="$t('achievements.scoreColumn')" width="110" />
          </el-table>
          <el-empty v-else :description="$t('achievements.rankingEmpty')" />
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>

<script setup>
// 成就墙:进入即结算(惰性解锁),返回目录/概览/家庭榜;本页只做展示与提示
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { achievementsApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { CATEGORY_ORDER, catalogKeys, categoryKey, levelProgress, progressPercent, unlockDate } from '@/utils/achievements'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const loadError = ref(false)
const activeTab = ref('wall')
const filter = ref('ALL')
const summary = ref({ level: 1, score: 0, unlocked: 0, total: 0, nextLevelScore: -1 })
const items = ref([])
const ranking = ref([])

const levelPct = computed(() => levelProgress(summary.value.score, summary.value.level))

// 满级没有下一档,提示改说「已满级」
const nextHint = computed(() => {
  const next = summary.value.nextLevelScore
  if (next === null || next === undefined || next < 0) return t('achievements.maxLevel')
  return t('achievements.nextLevel', { level: summary.value.level + 1, n: Math.max(0, next - summary.value.score) })
})

// 分类筛选:全部 + 目录里实际出现的分类(顺序与成就图鉴一致)
const filters = computed(() => [
  { value: 'ALL', label: t('achievements.all') },
  ...CATEGORY_ORDER.filter((c) => items.value.some((i) => i.category === c)).map((c) => ({
    value: c,
    label: t(categoryKey(c)),
  })),
])

const visibleItems = computed(() => (filter.value === 'ALL' ? items.value : items.value.filter((i) => i.category === filter.value)))

const isMe = (row) => String(row.userId) === String(userStore.userInfo?.id)

const rowClass = ({ row }) => (isMe(row) ? 'rank-row-me' : '')

// 本次新解锁逐条提示;一次解锁很多时(首次打开)只提示前三条,其余给一条汇总
const notifyUnlocked = (list) => {
  if (!list?.length) return
  list.slice(0, 3).forEach((a) => {
    ElMessage.success(t('achievements.unlockedToast', { name: t(`achievements.catalog.${a.code}.name`) }))
  })
  if (list.length > 3) ElMessage.success(t('achievements.unlockedMore', { n: list.length - 3 }))
}

const load = async () => {
  if (!userStore.isLoggedIn) return
  loading.value = true
  loadError.value = false
  try {
    const data = await achievementsApi.overview()
    summary.value = data.summary || summary.value
    items.value = data.items || []
    ranking.value = data.ranking || []
    notifyUnlocked(data.newlyUnlocked)
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
/* 等级卡:左侧等级徽章,右侧成就分/进度/下一档提示 */
.level-card {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 20px 24px;
  margin-bottom: 12px;
  border-radius: 14px;
}
.level-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 96px;
  padding: 12px 8px;
  border-radius: 12px;
  background: var(--el-color-primary-light-9, rgba(0, 0, 0, 0.04));
}
.level-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-primary);
}
.level-name {
  font-size: 13px;
  margin-top: 4px;
  color: var(--color-text-secondary);
}
.level-body {
  flex: 1;
  min-width: 0;
}
.level-score b {
  font-size: 28px;
  margin-right: 6px;
}
.level-score span {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.level-count {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 2px 0 8px;
}
.level-bar {
  max-width: 420px;
}
.level-next {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 6px;
}
.ach-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin: 0 0 12px;
}
.load-error {
  padding: 12px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.cat-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.ach-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}
/* 未解锁:整卡压暗;已解锁:正常亮度 + 主色描边 */
.ach-card {
  display: flex;
  flex-direction: column;
  padding: 16px;
  border-radius: 12px;
  opacity: 0.55;
  filter: grayscale(0.85);
}
.ach-card.unlocked {
  opacity: 1;
  filter: none;
  box-shadow: inset 0 0 0 1px var(--el-color-primary-light-5, rgba(0, 0, 0, 0.08));
}
.ach-icon {
  font-size: 30px;
  line-height: 1;
}
.ach-name {
  font-weight: 600;
  margin: 8px 0 4px;
}
.ach-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  flex: 1;
}
.ach-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
}
.ach-date {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.ach-bar {
  margin-top: 10px;
}
.ach-progress {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
  text-align: right;
}
.rank-name {
  margin-right: 8px;
}
.rank-me {
  opacity: 0.8;
}
:deep(.rank-row-me) {
  font-weight: 600;
}

@media (max-width: 768px) {
  .level-card {
    gap: 14px;
    padding: 16px;
  }
  .ach-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
