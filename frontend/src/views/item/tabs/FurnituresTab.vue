<template>
  <PageToolbar :card="false">
    <el-select v-model="roomFilter" :placeholder="$t('item.allRooms')" clearable style="width: 200px" @change="$emit('filter-change')">
      <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
    </el-select>
    <el-button type="primary" @click="$emit('add')">{{ $t('item.addFurniture') }}</el-button>
  </PageToolbar>
  <el-table :data="furnitures" stripe>
    <el-table-column prop="name" :label="$t('item.furnitureName')">
      <template #default="{ row }">
        <span class="furn-name-cell">
          <svg viewBox="0 0 24 24" class="furn-ico-sm">
            <component :is="pt.tag" v-for="(pt, i) in furnitureIcon(row.type)" :key="i" v-bind="pt.attrs" />
          </svg>
          {{ row.name }}
        </span>
      </template>
    </el-table-column>
    <el-table-column :label="$t('item.roomName')">
      <template #default="{ row }">{{ roomName(row.roomId) }}</template>
    </el-table-column>
    <el-table-column prop="type" :label="$t('item.furnitureType')" width="100" />
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
import { furnitureIcon } from '@/utils/furnitureIcon'
import PageToolbar from '@/components/PageToolbar.vue'

const props = defineProps({ rooms: { type: Array, default: () => [] }, furnitures: { type: Array, default: () => [] } })
defineEmits(['filter-change', 'add', 'edit', 'remove'])
const roomFilter = defineModel('roomFilter', { default: null })

const roomName = (id) => props.rooms.find((r) => r.id === Number(id))?.name || '-'
</script>

<style scoped>
.furn-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.furn-ico-sm {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  fill: none;
  stroke: #8a7a6a;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
:deep(.el-table .el-button) {
  padding: 5px 6px;
}
:deep(.el-table .el-button + .el-button) {
  margin-left: 4px;
}
</style>
