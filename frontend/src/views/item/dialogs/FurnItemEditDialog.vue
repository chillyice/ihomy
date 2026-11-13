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
      <el-form-item :label="$t('visibility.label')">
        <VisibilityPicker v-model="form.visibility" :options="visibilityOptions" />
      </el-form-item>
      <el-form-item v-if="needsAudience(form.visibility)" :label="$t('audience.label')">
        <AudiencePicker v-if="audience.canShare" v-model="audience.selected" :families="audience.families" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="save">{{ $t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { audienceApi, itemApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'
import VisibilityPicker from '@/components/common/VisibilityPicker.vue'
import AudiencePicker from '@/components/common/AudiencePicker.vue'
import { visibilityOptions as buildVisibilityOptions, needsAudience } from '@/utils/visibility'
import { useAudience } from '@/composables/useAudience'

const props = defineProps({
  itemTypes: { type: Array, default: () => [] },
  furnitureId: { type: [Number, String], default: null },
  floorPlan: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['saved'])
const { t } = useI18n()

const visible = ref(false)
const form = ref({})

const visibilityOptions = computed(() => buildVisibilityOptions(t))
const { state: audience, load: loadAudience } = useAudience('item')

const open = (row) => {
  form.value = row
    ? {
        id: row.id,
        name: row.name,
        type: row.type,
        position: row.position || '',
        note: row.note || '',
        visibility: row.visibility || 'FAMILY',
      }
    : { name: '', type: 'OTHER', position: '', note: '', visibility: 'FAMILY' }
  visible.value = true
  loadAudience(row?.id ?? null, 'item', { applyDefaults: !row })
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
    visibility: form.value.visibility,
  }
  const saved = form.value.id ? await itemApi.update(form.value.id, body) : await itemApi.create(body)
  const itemId = form.value.id || saved?.id
  const picked = audience.loaded && audience.canShare ? (needsAudience(form.value.visibility) ? [...audience.selected] : []) : null
  if (itemId && picked !== null) {
    try {
      const res = await audienceApi.save('item', itemId, picked)
      if (picked.length && res?.targetCount === 0) ElMessage.info(t('audience.noTarget'))
    } catch {
      ElMessage.warning(t('audience.saveFailed'))
    }
  }
  ElMessage.success(t('common.success'))
  visible.value = false
  emit('saved')
}

defineExpose({ open })
</script>
