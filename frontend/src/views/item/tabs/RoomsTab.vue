<template>
  <PageToolbar :card="false">
    <el-select
      v-model="roomHouseFilter"
      :placeholder="$t('item.allHouses')"
      clearable
      style="width: 200px"
      @change="$emit('filter-change')"
    >
      <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
    </el-select>
    <el-button type="primary" @click="$emit('add')">{{ $t('item.addRoom') }}</el-button>
  </PageToolbar>
  <el-table :data="rooms" stripe>
    <el-table-column :label="$t('item.houseName')">
      <template #default="{ row }">{{ houseName(row.houseId) }}</template>
    </el-table-column>
    <el-table-column prop="name" :label="$t('item.roomName')" />
    <el-table-column prop="floor" :label="$t('item.floor')" width="90" />
    <el-table-column prop="note" :label="$t('item.note')" show-overflow-tooltip />
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

const props = defineProps({ houses: { type: Array, default: () => [] }, rooms: { type: Array, default: () => [] } })
defineEmits(['filter-change', 'add', 'edit', 'remove'])
const roomHouseFilter = defineModel('roomHouseFilter', { default: null })

const houseName = (id) => props.houses.find((h) => h.id === Number(id))?.name || '-'
</script>

<style scoped>
:deep(.el-table .el-button) {
  padding: 5px 6px;
}
:deep(.el-table .el-button + .el-button) {
  margin-left: 4px;
}
</style>
