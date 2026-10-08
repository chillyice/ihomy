<template>
  <el-dialog v-model="visible" append-to-body :title="form.id ? $t('item.editItem') : $t('item.addItem')" width="480px">
    <el-form label-width="90px">
      <el-form-item :label="$t('item.houseName')">
        <el-select
          v-model="form.houseId"
          :placeholder="$t('item.pickHouse')"
          style="width: 100%"
          @change="() => ((form.furnitureId = null), (form.roomId = null))"
        >
          <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('item.roomName')">
        <el-select v-model="form.roomId" :placeholder="$t('item.pickRoom')" style="width: 100%" @change="form.furnitureId = null">
          <el-option v-for="r in roomsOf(form.houseId)" :key="r.id" :label="r.name" :value="r.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('item.furnitureName')">
        <el-select v-model="form.furnitureId" :placeholder="$t('item.pickFurniture')" style="width: 100%">
          <el-option v-for="f in furnOf(form.roomId)" :key="f.id" :label="f.name" :value="f.id" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('item.itemName')">
        <el-input v-model="form.name" :placeholder="$t('item.itemNamePh')" />
      </el-form-item>
      <el-form-item :label="$t('item.itemImage')">
        <el-upload :show-file-list="false" :before-upload="(f) => uploadItemImage(f)" accept="image/*">
          <img v-if="form.image_url" :src="form.image_url" class="item-image-preview" :alt="form.name || ''" />
          <el-button v-else size="small"
            ><el-icon><Plus /></el-icon> {{ $t('item.itemImage') }}</el-button
          >
        </el-upload>
      </el-form-item>
      <el-form-item :label="$t('item.itemType')">
        <el-select v-model="form.type" style="width: 100%">
          <el-option v-for="tp in itemTypes" :key="tp" :label="dictText(t, 'item_type', tp)" :value="tp" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('item.position')">
        <el-input v-model="form.position" :placeholder="$t('item.positionPh')" />
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
import { Plus } from '@element-plus/icons-vue'
import { itemApi, fileApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'

const props = defineProps({
  houses: { type: Array, default: () => [] },
  rooms: { type: Array, default: () => [] },
  furnitures: { type: Array, default: () => [] },
  itemTypes: { type: Array, default: () => [] },
})
const emit = defineEmits(['saved'])
const { t } = useI18n()

const visible = ref(false)
const form = ref({})

const roomsOf = (houseId) => props.rooms.filter((r) => r.houseId === houseId || r.houseId === Number(houseId))
const furnOf = (roomId) => props.furnitures.filter((f) => f.roomId === roomId || f.roomId === Number(roomId))

const open = (f) => {
  form.value = f
  visible.value = true
}
const uploadItemImage = async (file) => {
  try {
    const data = await fileApi.upload(file)
    form.value.image_url = data.url
  } catch (e) {}
  return false
}
const save = async () => {
  if (!form.value.name) return ElMessage.warning(t('item.itemNameRequired'))
  const body = {
    furnitureId: form.value.furnitureId,
    roomId: form.value.roomId,
    name: form.value.name,
    aliases: form.value.aliases,
    position: form.value.position,
    imageUrl: form.value.image_url,
    type: form.value.type,
    quantity: form.value.quantity,
    unit: form.value.unit,
    note: form.value.note,
    storedAt: form.value.storedAt || null,
    shelfLife: form.value.shelfLife,
    shelfLifeUnit: form.value.shelfLifeUnit || null,
    relX: form.value.furnitureId ? 0.5 : null,
    relY: form.value.furnitureId ? 0.5 : null,
  }
  if (form.value.id) await itemApi.update(form.value.id, body)
  else await itemApi.create(body)
  ElMessage.success(t('common.success'))
  visible.value = false
  emit('saved')
}

defineExpose({ open })
</script>

<style scoped>
.item-image-preview {
  width: 200px;
  height: 140px;
  object-fit: cover;
  border-radius: 8px;
}
</style>
