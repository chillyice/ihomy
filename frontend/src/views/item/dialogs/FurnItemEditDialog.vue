<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? $t('item.editItem') : $t('item.addItem')" width="420px">
    <el-form label-width="80px">
      <el-form-item :label="$t('item.itemName')">
        <el-input v-model="form.name" />
      </el-form-item>
      <el-form-item :label="$t('item.itemType')">
        <el-select v-model="form.type" style="width: 100%">
          <el-option v-for="tp in itemTypes" :key="tp" :label="dictText(t, 'item_type', tp)" :value="tp" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('item.position')">
        <el-input v-model="form.position" />
      </el-form-item>
      <el-form-item :label="$t('item.note')">
        <el-input v-model="form.note" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="save">{{ $t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { itemApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'

const props = defineProps({
  itemTypes: { type: Array, default: () => [] },
  furnitureId: { type: [Number, String], default: null },
  floorPlan: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['saved'])
const { t } = useI18n()

const visible = ref(false)
const form = ref({})

const open = (row) => {
  form.value = row
    ? { id: row.id, name: row.name, type: row.type, position: row.position || '', note: row.note || '' }
    : { name: '', type: 'OTHER', position: '', note: '' }
  visible.value = true
}
const save = async () => {
  if (!form.value.name) return ElMessage.warning(t('item.itemNameRequired'))
  const body = {
    furnitureId: props.furnitureId,
    roomId:
      props.floorPlan.furnitures.find((f) => f.id === props.furnitureId)?.room_id ||
      props.floorPlan.furnitures.find((f) => f.id === props.furnitureId)?.roomId ||
      null,
    name: form.value.name,
    type: form.value.type,
    position: form.value.position,
    note: form.value.note,
    relX: 0.5,
    relY: 0.5,
  }
  if (form.value.id) await itemApi.update(form.value.id, body)
  else await itemApi.create(body)
  ElMessage.success(t('common.success'))
  visible.value = false
  emit('saved')
}

defineExpose({ open })
</script>
