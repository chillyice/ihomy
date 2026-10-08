<template>
  <div class="filter-row">
    <el-date-picker v-model="filter.startDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.startDate')" />
    <el-date-picker v-model="filter.endDate" type="date" value-format="YYYY-MM-DD" :placeholder="$t('ops.endDate')" />
    <el-input v-model.number="filter.userId" :placeholder="$t('ops.userId')" style="width: 140px" />
    <el-input v-model.number="filter.familyId" :placeholder="$t('ops.familyId')" style="width: 140px" />
    <el-button type="primary" @click="loadStats">{{ $t('ops.query') }}</el-button>
    <el-button @click="resetFilter">{{ $t('ops.reset') }}</el-button>
  </div>
  <div v-loading="statsLoading" class="stat-groups">
    <section v-for="g in statGroups" :key="g.key" class="stat-group">
      <div class="stat-group-head">
        <h3 class="section-label">{{ g.name }}</h3>
        <span class="stat-group-type">{{ g.prefix }}</span>
      </div>
      <div class="stats-grid">
        <div v-for="row in g.cards" :key="row.key" class="stat-card card">
          <div class="stat-name">{{ row.name }}</div>
          <div class="stat-num">{{ stats[row.key] ?? 0 }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'

const { t } = useI18n()

// 资源卡片定义(键与后端 stats 返回字段一一对应,名称走 i18n),按类型分组罗列;
// prefix 为该组资源落库的表前缀(sys_/content_/family_/report_),与 schema 分域口径一致
const STAT_GROUPS = [
  { key: 'account', prefix: 'sys_', cards: ['users', 'families'] },
  { key: 'content', prefix: 'content_', cards: ['blogs', 'diaries', 'albums', 'photos', 'videos', 'wishes'] },
  { key: 'interact', prefix: 'content_', cards: ['comments', 'likes'] },
  { key: 'family', prefix: 'family_', cards: ['checkins', 'plans', 'bookRecords', 'reminders'] },
  { key: 'report', prefix: 'report_', cards: ['operationLogs'] },
]
const statGroups = STAT_GROUPS.map((g) => ({
  key: g.key,
  prefix: g.prefix,
  name: t('ops.statGroup.' + g.key),
  cards: g.cards.map((key) => ({ key, name: t('ops.stat.' + key) })),
}))

const stats = ref({})
const statsLoading = ref(false)
const filter = reactive({ startDate: '', endDate: '', userId: '', familyId: '' })

const loadStats = async () => {
  statsLoading.value = true
  try {
    stats.value = await opsApi.stats({
      startDate: filter.startDate || null,
      endDate: filter.endDate || null,
      userId: filter.userId || null,
      familyId: filter.familyId || null,
    })
  } finally {
    statsLoading.value = false
  }
}

const resetFilter = () => {
  Object.assign(filter, { startDate: '', endDate: '', userId: '', familyId: '' })
  loadStats()
}

onMounted(loadStats)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.stat-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.stat-group-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 10px;
}
.stat-group-head .section-label {
  margin: 0;
}
.stat-group-type {
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  color: var(--color-text-secondary);
}
/* 列数随宽度自适应:桌面约 5 列,卡片在各类目间保持同一列宽(空位留白不拉伸) */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}
.stat-card {
  padding: 14px 16px;
  text-align: center;
}
.stat-name {
  color: #999;
  font-size: 12px;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
  }
  .stat-group-head {
    margin-bottom: 8px;
  }
}
</style>
