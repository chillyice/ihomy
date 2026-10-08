<template>
  <el-dialog v-model="visible" append-to-body :title="title" width="520px">
    <div class="furn-items-toolbar">
      <el-button type="primary" size="small" @click="openFurnItem()"
        ><el-icon><Plus /></el-icon> {{ $t('item.addItem') }}</el-button
      >
      <el-button size="small" @click="openFurnForm()"
        ><el-icon><Edit /></el-icon> {{ $t('item.editFurniture') }}</el-button
      >
    </div>
    <el-table :data="furnItems" stripe max-height="360" empty-text="--">
      <el-table-column prop="name" :label="$t('item.itemName')" show-overflow-tooltip />
      <el-table-column prop="type" :label="$t('item.itemType')" width="100">
        <template #default="{ row }">{{ dictText(t, 'item_type', row.type) }}</template>
      </el-table-column>
      <el-table-column prop="position" :label="$t('item.position')" width="100" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="120">
        <template #default="{ row }">
          <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
            <el-button size="small" text @click="openFurnItem(row)"
              ><el-icon><Edit /></el-icon
            ></el-button>
          </el-tooltip>
          <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
            <el-button size="small" text type="danger" @click="removeFurnItem(row)"
              ><el-icon><Delete /></el-icon
            ></el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <!-- 编辑家具自身属性 -->
    <el-form
      v-if="showEdit"
      label-width="80px"
      style="margin-top: 12px; padding-top: 12px; border-top: 1px solid rgba(184, 140, 110, 0.15)"
    >
      <el-form-item :label="$t('item.furnitureName')">
        <el-input v-model="editForm.name" />
      </el-form-item>
      <el-form-item :label="$t('item.furnitureType')">
        <el-input v-model="editForm.type" />
      </el-form-item>
      <el-form-item :label="$t('item.note')">
        <el-input v-model="editForm.note" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" size="small" @click="saveFurnEdit">{{ $t('common.confirm') }}</el-button>
        <el-button size="small" @click="showEdit = false">{{ $t('common.cancel') }}</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>

  <FurnItemEditDialog
    ref="furnItemEditRef"
    :item-types="itemTypes"
    :furniture-id="furnItemsFurnitureId"
    :floor-plan="floorPlan"
    @saved="$emit('saved')"
  />
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { itemApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'
import FurnItemEditDialog from './FurnItemEditDialog.vue'

const props = defineProps({
  floorPlan: { type: Object, default: () => ({}) },
  itemTypes: { type: Array, default: () => [] },
})
const emit = defineEmits(['saved'])
const { t } = useI18n()

const visible = ref(false)
const furnItemsFurnitureId = ref(null)
const showEdit = ref(false)
const editForm = ref({ name: '', type: '', note: '' })
const furnItemEditRef = ref(null)

const furnItems = computed(() => (props.floorPlan.items || []).filter((it) => it.furnitureId === furnItemsFurnitureId.value))
const title = computed(() => {
  const f = props.floorPlan.furnitures.find((x) => x.id === furnItemsFurnitureId.value)
  return f ? `${f.name} - ${t('item.items')}` : t('item.items')
})

const open = (fId) => {
  furnItemsFurnitureId.value = fId
  showEdit.value = false
  visible.value = true
}
const openFurnItem = (row) => furnItemEditRef.value?.open(row)
const removeFurnItem = async (row) => {
  await ElMessageBox.confirm(t('item.deleteItemConfirm'), t('common.warning'), { type: 'warning', closeOnClickModal: true })
  await itemApi.remove(row.id)
  ElMessage.success(t('common.success'))
  emit('saved')
}
const openFurnForm = () => {
  const f = props.floorPlan.furnitures.find((x) => x.id === furnItemsFurnitureId.value)
  if (!f) return
  editForm.value = { name: f.name, type: f.type || '', note: f.note || '' }
  showEdit.value = true
}
const saveFurnEdit = async () => {
  await itemApi.updateFurniture(furnItemsFurnitureId.value, editForm.value)
  ElMessage.success(t('common.success'))
  showEdit.value = false
  emit('saved')
}

defineExpose({ open })
</script>

<style scoped>
.furn-items-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
:deep(.el-table .el-button) {
  padding: 5px 6px;
}
:deep(.el-table .el-button + .el-button) {
  margin-left: 4px;
}
</style>
