<!-- 待安排:无日期计划 + 计划下无截止日的子任务,可直接勾选完成;计划可安排日期、编辑或删除。
      子任务没有独立日期,勾完成后从待安排消失,回计划详情仍可查看/恢复。 -->
<template>
  <section v-if="items.length" class="undated card">
    <div class="section-label">{{ $t('schedule.undatedCount', { n: items.length }) }}</div>
    <div class="u-list">
      <div v-for="p in items" :key="p.kind + '-' + p.id" class="u-row" :class="{ done: p.done }">
        <el-checkbox :model-value="!!p.done" @change="$emit('toggle', p)" />
        <span class="u-title">{{ p.title }}</span>
        <span v-if="p.kind === 'PLAN_TASK'" class="u-plan">{{ $t('schedule.undatedPlan', { name: p.planTitle || '' }) }}</span>
        <span v-else class="u-meta">{{ $t('schedule.createdAt', { date: p.createdAt || '—' }) }}</span>
        <span class="u-actions">
          <el-button v-if="p.kind !== 'PLAN_TASK'" size="small" type="primary" plain @click="$emit('arrange', p)">{{
            $t('schedule.arrange')
          }}</el-button>
          <el-tooltip v-if="p.kind === 'PLAN_TASK'" :content="$t('schedule.viewPlan')" placement="top" :show-after="300">
            <el-button size="small" text @click="$emit('open-plan', p)"
              ><el-icon><View /></el-icon
            ></el-button>
          </el-tooltip>
          <el-tooltip v-if="p.kind !== 'PLAN_TASK'" :content="$t('common.edit')" placement="top" :show-after="300">
            <el-button size="small" text @click="$emit('edit', p)"
              ><el-icon><Edit /></el-icon
            ></el-button>
          </el-tooltip>
          <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
            <el-button size="small" text type="danger" @click="$emit('delete', p)"
              ><el-icon><Delete /></el-icon
            ></el-button>
          </el-tooltip>
        </span>
      </div>
    </div>
  </section>
</template>

<script setup>
// 纯展示:数据来自 /schedule/events 的 undated 段(后端 FamilyPlanService.undated 唯一口径)
import { Edit, Delete, View } from '@element-plus/icons-vue'

defineProps({
  items: { type: Array, default: () => [] },
})
defineEmits(['arrange', 'edit', 'delete', 'toggle', 'open-plan'])
</script>

<style scoped>
.undated {
  margin-top: 12px;
  padding: 16px 18px;
}
.u-list {
  display: grid;
  gap: 6px;
}
.u-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  min-width: 0;
}
.u-row.done .u-title {
  text-decoration: line-through;
  opacity: 0.6;
}
.u-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.u-plan,
.u-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.u-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.u-actions :deep(.el-button) {
  padding: 4px 6px;
}
@media (max-width: 768px) {
  .u-row {
    flex-wrap: wrap;
  }
  .u-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
