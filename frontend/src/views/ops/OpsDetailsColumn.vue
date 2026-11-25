<template>
  <el-table-column :label="$t('ops.rowDetails')" width="58" align="center">
    <template #default="{ row }">
      <el-button text :icon="View" :aria-label="$t('ops.rowDetails')" :title="$t('ops.rowDetails')" @click="show(row)" />
    </template>
  </el-table-column>
  <el-dialog v-model="open" append-to-body class="dialog-md" :title="title">
    <dl v-if="selected" class="record-fields"><slot :row="selected" :close="() => (open = false)" /></dl>
  </el-dialog>
</template>
<script setup>
import { ref, shallowRef } from 'vue'
import { View } from '@element-plus/icons-vue'
defineProps({ title: { type: String, required: true } })
const open = ref(false)
const selected = shallowRef(null)
function show(row) {
  selected.value = row
  open.value = true
}
</script>
<style scoped>
.record-fields {
  margin: 0;
  display: grid;
  grid-template-columns: minmax(84px, auto) 1fr;
  gap: 12px 18px;
}
.record-fields :deep(dt) {
  color: var(--color-text-secondary);
}
.record-fields :deep(dd) {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
</style>
