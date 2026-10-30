<!-- 待安排:无日期计划(进行中且 target_date 为空),可安排日期、编辑或删除。 -->
<template>
  <section v-if="items.length" class="undated card">
    <div class="section-label">{{ $t('schedule.undatedCount', { n: items.length }) }}</div>
    <div class="u-list">
      <div v-for="p in items" :key="p.id" class="u-row">
        <span class="u-title">{{ p.title }}</span>
        <span class="u-meta">{{ $t('schedule.createdAt', { date: p.createdAt || '—' }) }}</span>
        <span class="u-actions">
          <el-button size="small" type="primary" plain @click="$emit('arrange', p)">{{ $t('schedule.arrange') }}</el-button>
          <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
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
// 纯展示:数据来自 /schedule/events 的 undated 段
import { Edit, Delete } from '@element-plus/icons-vue'

defineProps({
  items: { type: Array, default: () => [] },
})
defineEmits(['arrange', 'edit', 'delete'])
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
.u-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
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
