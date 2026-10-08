<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? $t('item.editRoom') : $t('item.addRoom')" width="420px">
    <el-form label-width="90px">
      <el-form-item v-if="!form._geometry" :label="$t('item.houseName')">
        <el-select v-model="form.houseId" :placeholder="$t('item.pickHouse')" style="width: 100%">
          <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('item.roomName')">
        <el-input v-model="form.name" :placeholder="$t('item.roomNameHint')" />
      </el-form-item>
      <el-form-item :label="$t('item.floor')">
        <el-input-number v-model="form.floor" :min="-2" :max="99" />
      </el-form-item>
      <el-form-item :label="$t('item.note')">
        <el-input v-model="form.note" />
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

defineProps({ houses: { type: Array, default: () => [] } })
const emit = defineEmits(['saved'])
const { t } = useI18n()

const visible = ref(false)
const form = ref({})

const open = (f) => {
  form.value = f
  visible.value = true
}
const save = async () => {
  if (!form.value.houseId && !form.value._geometry) return ElMessage.warning(t('item.pickHouse'))
  if (!form.value.name) return ElMessage.warning(t('item.roomNameRequired'))
  const geom = form.value._geometry
  const floor = form.value.floor
  if (form.value.id) await itemApi.updateRoom(form.value.id, form.value)
  else
    form.value = await itemApi.addRoom({
      houseId: form.value.houseId,
      name: form.value.name,
      floor: form.value.floor,
      note: form.value.note,
    })
  if (geom) await itemApi.saveRoomGeometry(form.value.id, geom)
  ElMessage.success(t('common.success'))
  visible.value = false
  emit('saved', { floor })
}

defineExpose({ open })
</script>
