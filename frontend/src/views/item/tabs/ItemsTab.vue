<template>
  <PageToolbar :card="false">
    <template v-if="!selectMode">
      <div class="tb-left">
        <el-input
          v-model="keyword"
          :placeholder="$t('item.searchPh')"
          clearable
          size="small"
          style="width: 260px"
          @keyup.enter="$emit('search')"
          @clear="$emit('search')"
        >
          <template #append
            ><el-button @click="$emit('search')">{{ $t('item.search') }}</el-button></template
          >
        </el-input>
      </div>
      <div class="tb-right">
        <el-button :disabled="!items.length" @click="$emit('toggle-select')">{{ $t('item.select') }}</el-button>
        <el-button @click="$emit('ai-register')">✨ {{ $t('item.aiRegister') }}</el-button>
        <el-button type="primary" @click="$emit('add')">{{ $t('item.addItem') }}</el-button>
      </div>
    </template>
    <div v-else class="tb-right">
      <span class="select-count">{{ $t('item.selectedItems', { n: selectedIds.length }) }}</span>
      <el-button @click="$emit('toggle-select')">{{ $t('item.cancelSelect') }}</el-button>
      <el-button type="primary" :disabled="!selectedIds.length" @click="$emit('batch')">{{ $t('item.batchEditFurniture') }}</el-button>
    </div>
  </PageToolbar>
  <el-empty v-if="items.length === 0" :description="$t('item.emptyItems')" />
  <el-card
    v-a11y-click
    v-for="it in items"
    :key="it.id"
    shadow="hover"
    class="item-card"
    :class="{ 'is-pick': selectMode, selected: selectMode && selectedIds.includes(it.id) }"
    @click="selectMode && $emit('pick', it)"
  >
    <span v-if="selectMode" class="pick-badge" :class="{ on: selectedIds.includes(it.id) }">
      <svg viewBox="0 0 16 16" width="12" height="12">
        <path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </span>
    <div class="item-main">
      <img v-if="it.image_url" :src="it.image_url" class="item-avatar" :alt="it.name || ''" />
      <span class="item-name">{{ it.name }}</span>
      <el-tag size="small">{{ dictText(t, 'item_type', it.type) }}</el-tag>
      <el-tag v-if="it.position" size="small" type="info">{{ it.position }}</el-tag>
    </div>
    <div class="item-path">{{ it.house_name }} / {{ it.room_name }} / {{ it.furniture_name }}</div>
    <div v-if="!selectMode" class="item-ops">
      <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
        <el-button size="small" text @click="$emit('edit', it)"
          ><el-icon><Edit /></el-icon
        ></el-button>
      </el-tooltip>
      <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
        <el-button size="small" text type="danger" @click="$emit('remove', it)"
          ><el-icon><Delete /></el-icon
        ></el-button>
      </el-tooltip>
    </div>
  </el-card>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { Edit, Delete } from '@element-plus/icons-vue'
import { dictText } from '@/utils/dict'
import PageToolbar from '@/components/PageToolbar.vue'

const { t } = useI18n()

defineProps({
  items: { type: Array, default: () => [] },
  selectMode: Boolean,
  selectedIds: { type: Array, default: () => [] },
})
defineEmits(['search', 'toggle-select', 'pick', 'add', 'ai-register', 'batch', 'edit', 'remove'])
const keyword = defineModel('keyword', { default: '' })
</script>

<style scoped>
.item-card {
  position: relative;
  margin-bottom: 12px;
}
.item-card.is-pick {
  cursor: pointer;
}
.item-card.selected {
  outline: 3px solid var(--color-primary, var(--color-brand));
  outline-offset: -3px;
}
.select-count {
  font-size: 13px;
  color: var(--color-text-secondary, #909399);
  margin-right: 8px;
}
.pick-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  border: 2px solid rgba(184, 140, 110, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.pick-badge.on {
  background: var(--color-brand);
  border-color: var(--color-brand);
}
.item-main {
  display: flex;
  align-items: center;
  gap: 8px;
}
/* 物品头像(列表卡片) */
.item-avatar {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}
.item-name {
  font-size: 16px;
  font-weight: 600;
}
.item-path {
  color: #909399;
  font-size: 13px;
  margin-top: 4px;
}
.item-ops {
  margin-top: 8px;
}
.item-ops :deep(.el-button) {
  padding: 5px 6px;
}
.item-ops :deep(.el-button + .el-button) {
  margin-left: 4px;
}
</style>
