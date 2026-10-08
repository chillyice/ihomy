<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? $t('item.editHouse') : $t('item.addHouse')" width="420px">
    <el-form label-width="90px">
      <el-form-item :label="$t('item.houseName')">
        <el-input v-model="form.name" :placeholder="$t('item.houseNamePh')" />
      </el-form-item>
      <el-form-item :label="$t('item.houseAddress')">
        <el-input v-model="form.address" :placeholder="$t('item.houseAddressPh')" />
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

const emit = defineEmits(['saved'])
const { t } = useI18n()

const visible = ref(false)
const form = ref({})

const open = (f) => {
  form.value = f
  visible.value = true
}
const save = async () => {
  if (!form.value.name) return ElMessage.warning(t('item.houseNameRequired'))
  if (form.value.id) await itemApi.updateHouse(form.value.id, form.value)
  else await itemApi.addHouse(form.value)
  ElMessage.success(t('common.success'))
  visible.value = false
  emit('saved')
}

defineExpose({ open })
</script>
