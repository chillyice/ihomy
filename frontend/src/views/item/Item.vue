<template>
  <div class="page fp-page">
    <!-- 户型图主面板(常驻:工具栏 + 画布 + 编辑侧栏 + 弹窗) -->
    <FloorPlanPanel
      ref="panelRef"
      v-model:list-mode="listMode"
      :tab="tab"
      :keyword="keyword"
      :room-filter="roomFilter"
      :room-house-filter="roomHouseFilter"
    />

    <!-- 列表模式(旧 CRUD) -->
    <div v-if="listMode" class="fp-list">
      <el-tabs v-model="tab">
        <el-tab-pane :label="$t('item.houses')" name="houses">
          <HousesTab :houses="houses" @add="openHouse()" @edit="openHouse" @remove="removeHouse" />
        </el-tab-pane>
        <el-tab-pane :label="$t('item.rooms')" name="rooms">
          <RoomsTab
            v-model:room-house-filter="roomHouseFilter"
            :houses="houses"
            :rooms="rooms"
            @filter-change="loadRooms"
            @add="openRoom()"
            @edit="openRoom"
            @remove="removeRoom"
          />
        </el-tab-pane>
        <el-tab-pane :label="$t('item.furnitures')" name="furnitures">
          <FurnituresTab
            v-model:room-filter="roomFilter"
            :rooms="rooms"
            :furnitures="furnitures"
            @filter-change="loadFurnitures"
            @add="openFurniture()"
            @edit="openFurniture"
            @remove="removeFurniture"
          />
        </el-tab-pane>
        <el-tab-pane :label="$t('item.items')" name="items">
          <ItemsTab
            v-model:keyword="keyword"
            :items="items"
            :select-mode="selectMode"
            :selected-ids="selectedIds"
            @search="loadItems"
            @toggle-select="toggleSelect"
            @pick="togglePick"
            @add="openItem()"
            @ai-register="openAiRegister"
            @batch="openBatchFurniture"
            @edit="openItem"
            @remove="removeItem"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import HousesTab from './tabs/HousesTab.vue'
import RoomsTab from './tabs/RoomsTab.vue'
import FurnituresTab from './tabs/FurnituresTab.vue'
import ItemsTab from './tabs/ItemsTab.vue'
import FloorPlanPanel from './panels/FloorPlanPanel.vue'

// 户型图面板常驻并持有共享数据/CRUD,列表模式经模板 ref + defineExpose 取其数据/方法
const panelRef = ref(null)
const listMode = ref(false)
const tab = ref('houses')
const keyword = ref('')
const roomFilter = ref(null)
const roomHouseFilter = ref(null)

const houses = computed(() => panelRef.value?.houses || [])
const rooms = computed(() => panelRef.value?.rooms || [])
const furnitures = computed(() => panelRef.value?.furnitures || [])
const items = computed(() => panelRef.value?.items || [])
const selectMode = computed(() => panelRef.value?.selectMode ?? false)
const selectedIds = computed(() => panelRef.value?.selectedIds || [])

const loadRooms = () => panelRef.value?.loadRooms()
const loadFurnitures = () => panelRef.value?.loadFurnitures()
const loadItems = () => panelRef.value?.loadItems()
const openHouse = (row) => panelRef.value?.openHouse(row)
const openRoom = (row) => panelRef.value?.openRoom(row)
const openFurniture = (row) => panelRef.value?.openFurniture(row)
const openItem = (row) => panelRef.value?.openItem(row)
const openAiRegister = () => panelRef.value?.openAiRegister()
const openBatchFurniture = () => panelRef.value?.openBatchFurniture()
const removeHouse = (row) => panelRef.value?.removeHouse(row)
const removeRoom = (row) => panelRef.value?.removeRoom(row)
const removeFurniture = (row) => panelRef.value?.removeFurniture(row)
const removeItem = (row) => panelRef.value?.removeItem(row)
const toggleSelect = () => panelRef.value?.toggleSelect()
const togglePick = (it) => panelRef.value?.togglePick(it)
</script>

<style scoped>
.fp-page {
  display: flex;
  flex-direction: column;
  max-width: none;
  width: 100%;
  height: 100vh;
  height: 100dvh;
}
.fp-list {
  flex: 1;
  overflow-y: auto;
}
@media (max-width: 768px) {
  .fp-page {
    height: calc(100dvh - 120px);
  }
}
</style>
