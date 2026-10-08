<template>
  <PageToolbar :card="false">
    <el-button type="primary" @click="$emit('add')">{{ $t('item.addHouse') }}</el-button>
  </PageToolbar>
  <el-table :data="houses" stripe>
    <el-table-column prop="name" :label="$t('item.houseName')" />
    <el-table-column prop="address" :label="$t('item.houseAddress')" show-overflow-tooltip />
    <el-table-column :label="$t('common.actions')" width="160">
      <template #default="{ row }">
        <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
          <el-button size="small" text @click="$emit('edit', row)"
            ><el-icon><Edit /></el-icon
          ></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
          <el-button size="small" text type="danger" @click="$emit('remove', row)"
            ><el-icon><Delete /></el-icon
          ></el-button>
        </el-tooltip>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup>
import { Edit, Delete } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'

defineProps({ houses: { type: Array, default: () => [] } })
defineEmits(['add', 'edit', 'remove'])
</script>

<style scoped>
:deep(.el-table .el-button) {
  padding: 5px 6px;
}
:deep(.el-table .el-button + .el-button) {
  margin-left: 4px;
}
</style>
