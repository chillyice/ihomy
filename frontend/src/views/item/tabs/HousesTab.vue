<template>
  <PageToolbar :card="false">
    <div class="tb-right">
      <el-button type="primary" @click="$emit('add')">{{ $t('item.addHouse') }}</el-button>
    </div>
  </PageToolbar>
  <el-table :data="houses" stripe>
    <el-table-column :label="$t('item.houseName')" min-width="200" show-overflow-tooltip>
      <template #default="{ row }">
        <span class="house-name-cell">
          {{ row.name }}
          <el-tag v-if="row.shared" size="small" type="info">{{ $t('item.sharedFrom', { family: row.ownerFamilyName || '-' }) }}</el-tag>
        </span>
      </template>
    </el-table-column>
    <el-table-column prop="address" :label="$t('item.houseAddress')" min-width="280" show-overflow-tooltip />
    <el-table-column :label="$t('common.actions')" width="180">
      <template #default="{ row }">
        <el-tooltip v-if="canShare && !row.shared" :content="$t('item.shareHouse')" placement="top" :show-after="300">
          <el-button size="small" text @click="$emit('share', row)"
            ><el-icon><Share /></el-icon
          ></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
          <el-button size="small" text @click="$emit('edit', row)"
            ><el-icon><Edit /></el-icon
          ></el-button>
        </el-tooltip>
        <el-tooltip v-if="!row.shared" :content="$t('common.delete')" placement="top" :show-after="300">
          <el-button size="small" text type="danger" @click="$emit('remove', row)"
            ><el-icon><Delete /></el-icon
          ></el-button>
        </el-tooltip>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { Edit, Delete, Share } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'

defineProps({
  houses: { type: Array, default: () => [] },
  // 只有家长能调整共享;共享按钮只对本家的房子出现(共享来的房子不给再转授)
  canShare: { type: Boolean, default: false },
})
defineEmits(['add', 'edit', 'remove', 'share'])
</script>

<style scoped>
.house-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
:deep(.el-table .el-button) {
  padding: 5px 6px;
}
:deep(.el-table .el-button + .el-button) {
  margin-left: 4px;
}
</style>
