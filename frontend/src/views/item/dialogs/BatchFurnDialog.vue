<template>
  <el-dialog v-model="visible" append-to-body :title="$t('item.batchEditFurniture')" width="420px">
    <el-form label-width="90px">
      <el-form-item :label="$t('item.furnitureName')">
        <el-select v-model="batchFurnitureId" clearable :placeholder="$t('item.pickFurniture')" style="width: 100%">
          <el-option v-for="f in furnitures" :key="f.id" :label="furnLabel(f)" :value="f.id" />
        </el-select>
      </el-form-item>
      <div class="fp-batch-hint">{{ $t('item.batchAssignHint') }}</div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="apply">{{ $t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { itemApi } from '@/api'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  selectedIds: { type: Array, default: () => [] },
  furnitures: { type: Array, default: () => [] },
  rooms: { type: Array, default: () => [] },
})
const emit = defineEmits(['applied'])
const { t } = useI18n()

const visible = ref(false)
const batchFurnitureId = ref(null)

const roomName = (id) => props.rooms.find((r) => r.id === Number(id))?.name || '-'
const furnLabel = (f) => (f.roomId ? `${f.name}（${roomName(f.roomId)}）` : `${f.name}（${t('item.library')}）`)

const open = () => {
  batchFurnitureId.value = null
  visible.value = true
}
const apply = async () => {
  await itemApi.batchAssignFurniture({ ids: [...props.selectedIds], furnitureId: batchFurnitureId.value })
  ElMessage.success(t('common.success'))
  visible.value = false
  emit('applied')
}

defineExpose({ open })
</script>

<style scoped>
.fp-batch-hint {
  font-size: 12px;
  line-height: 1.6;
  color: #a89a8a;
  margin-top: 4px;
}
</style>
