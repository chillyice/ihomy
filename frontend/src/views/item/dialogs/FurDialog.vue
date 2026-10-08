<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? $t('item.editFurniture') : $t('item.addFurniture')" width="420px">
    <el-form label-width="90px">
      <el-form-item :label="$t('item.roomName')">
        <el-select v-model="form.roomId" clearable :placeholder="$t('item.pickRoom')" style="width: 100%">
          <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('item.furnitureName')">
        <el-input v-model="form.name" :placeholder="$t('item.furnitureNamePh')" />
      </el-form-item>
      <el-form-item :label="$t('item.furnitureType')">
        <el-select
          v-model="form.type"
          filterable
          allow-create
          default-first-option
          :placeholder="$t('item.furnitureType')"
          style="width: 100%"
        >
          <el-option v-for="tp in furnitureTypes" :key="tp" :label="tp" :value="tp" />
        </el-select>
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

defineProps({
  rooms: { type: Array, default: () => [] },
  furnitureTypes: { type: Array, default: () => [] },
})
const emit = defineEmits(['saved'])
const { t } = useI18n()

const visible = ref(false)
const form = ref({})

const open = (f) => {
  form.value = f
  visible.value = true
}
const save = async () => {
  if (!form.value.name) return ElMessage.warning(t('item.furnitureNameRequired'))
  if (form.value.id) await itemApi.updateFurniture(form.value.id, form.value)
  else
    await itemApi.addFurniture({
      roomId: form.value.roomId,
      name: form.value.name,
      type: form.value.type,
      note: form.value.note,
    })
  ElMessage.success(t('common.success'))
  visible.value = false
  emit('saved')
}

defineExpose({ open })
</script>
