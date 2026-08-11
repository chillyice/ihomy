<template>
  <div class="page">
    <el-tabs v-model="tab">
      <!-- 物品:搜索 + 列表 + 新增/编辑 -->
      <el-tab-pane :label="$t('item.items')" name="items">
        <div class="toolbar">
          <el-input v-model="keyword" :placeholder="$t('item.searchPh')" clearable style="width: 260px"
                    @keyup.enter="loadItems" @clear="loadItems">
            <template #append>
              <el-button @click="loadItems">{{ $t('item.search') }}</el-button>
            </template>
          </el-input>
          <el-button type="primary" @click="openItem()">{{ $t('item.addItem') }}</el-button>
        </div>
        <el-empty v-if="items.length === 0" :description="$t('item.emptyItems')" />
        <el-card v-for="it in items" :key="it.id" shadow="hover" class="item-card">
          <div class="item-main">
            <span class="item-name">{{ it.name }}</span>
            <el-tag v-if="it.position" size="small">{{ it.position }}</el-tag>
          </div>
          <div class="item-path">{{ it.house_name }} / {{ it.room_name }} / {{ it.furniture_name }}</div>
          <div class="item-aliases" v-if="it.aliases">{{ $t('item.aliases') }}: {{ it.aliases }}</div>
          <div class="item-ops">
            <el-button size="small" @click="openItem(it)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" type="danger" plain @click="removeItem(it)">{{ $t('common.delete') }}</el-button>
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 房子 -->
      <el-tab-pane :label="$t('item.houses')" name="houses">
        <div class="toolbar">
          <el-button type="primary" @click="openHouse()">{{ $t('item.addHouse') }}</el-button>
        </div>
        <el-table :data="houses" stripe>
          <el-table-column prop="name" :label="$t('item.houseName')" />
          <el-table-column prop="address" :label="$t('item.houseAddress')" show-overflow-tooltip />
          <el-table-column :label="$t('common.actions')" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="openHouse(row)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" plain @click="removeHouse(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 房间 -->
      <el-tab-pane :label="$t('item.rooms')" name="rooms">
        <div class="toolbar">
          <el-select v-model="roomHouseFilter" :placeholder="$t('item.allHouses')" clearable style="width: 200px"
                     @change="loadRooms">
            <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
          </el-select>
          <el-button type="primary" @click="openRoom()">{{ $t('item.addRoom') }}</el-button>
        </div>
        <el-table :data="rooms" stripe>
          <el-table-column :label="$t('item.houseName')">
            <template #default="{ row }">{{ houseName(row.houseId) }}</template>
          </el-table-column>
          <el-table-column prop="name" :label="$t('item.roomName')" />
          <el-table-column prop="floor" :label="$t('item.floor')" width="90" />
          <el-table-column prop="sortOrder" :label="$t('item.sort')" width="90" />
          <el-table-column prop="note" :label="$t('item.note')" show-overflow-tooltip />
          <el-table-column :label="$t('common.actions')" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="openRoom(row)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" plain @click="removeRoom(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 家具 -->
      <el-tab-pane :label="$t('item.furnitures')" name="furnitures">
        <div class="toolbar">
          <el-select v-model="roomFilter" :placeholder="$t('item.allRooms')" clearable style="width: 200px"
                     @change="loadFurnitures">
            <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
          <el-button type="primary" @click="openFurniture()">{{ $t('item.addFurniture') }}</el-button>
        </div>
        <el-table :data="furnitures" stripe>
          <el-table-column prop="name" :label="$t('item.furnitureName')" />
          <el-table-column :label="$t('item.roomName')">
            <template #default="{ row }">{{ roomName(row.roomId) }}</template>
          </el-table-column>
          <el-table-column prop="note" :label="$t('item.note')" show-overflow-tooltip />
          <el-table-column :label="$t('common.actions')" width="160">
            <template #default="{ row }">
              <el-button size="small" @click="openFurniture(row)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" plain @click="removeFurniture(row)">{{ $t('common.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 物品编辑 -->
    <el-dialog v-model="itemDlg" :title="itemForm.id ? $t('item.editItem') : $t('item.addItem')" width="480px">
      <el-form label-width="90px">
        <el-form-item :label="$t('item.houseName')">
          <el-select v-model="itemForm.houseId" :placeholder="$t('item.pickHouse')" style="width: 100%"
                     @change="itemForm.furnitureId = null">
            <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('item.roomName')">
          <el-select v-model="itemForm.roomId" :placeholder="$t('item.pickRoom')" style="width: 100%" @change="itemForm.furnitureId = null">
            <el-option v-for="r in roomsOf(itemForm.houseId)" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('item.furnitureName')">
          <el-select v-model="itemForm.furnitureId" :placeholder="$t('item.pickFurniture')" style="width: 100%">
            <el-option v-for="f in furnOf(itemForm.roomId)" :key="f.id" :label="f.name" :value="f.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('item.itemName')">
          <el-input v-model="itemForm.name" :placeholder="$t('item.itemNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('item.aliases')">
          <el-input v-model="itemForm.aliases" :placeholder="$t('item.aliasesPh')" />
        </el-form-item>
        <el-form-item :label="$t('item.position')">
          <el-input v-model="itemForm.position" :placeholder="$t('item.positionPh')" />
        </el-form-item>
        <el-form-item :label="$t('item.note')">
          <el-input v-model="itemForm.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveItem">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 房间编辑 -->
    <el-dialog v-model="roomDlg" :title="roomForm.id ? $t('item.editRoom') : $t('item.addRoom')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="$t('item.houseName')">
          <el-select v-model="roomForm.houseId" :placeholder="$t('item.pickHouse')" style="width: 100%">
            <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('item.roomName')">
          <el-input v-model="roomForm.name" :placeholder="$t('item.roomNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('item.floor')">
          <el-input-number v-model="roomForm.floor" :min="-2" :max="99" />
        </el-form-item>
        <el-form-item :label="$t('item.note')">
          <el-input v-model="roomForm.note" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roomDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveRoom">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 家具编辑 -->
    <el-dialog v-model="furDlg" :title="furForm.id ? $t('item.editFurniture') : $t('item.addFurniture')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="$t('item.roomName')">
          <el-select v-model="furForm.roomId" :placeholder="$t('item.pickRoom')" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('item.furnitureName')">
          <el-input v-model="furForm.name" :placeholder="$t('item.furnitureNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('item.note')">
          <el-input v-model="furForm.note" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="furDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveFurniture">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 房子编辑 -->
    <el-dialog v-model="houseDlg" :title="houseForm.id ? $t('item.editHouse') : $t('item.addHouse')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="$t('item.houseName')">
          <el-input v-model="houseForm.name" :placeholder="$t('item.houseNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('item.houseAddress')">
          <el-input v-model="houseForm.address" :placeholder="$t('item.houseAddressPh')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="houseDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveHouse">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { itemApi } from '@/api'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const tab = ref('items')
const items = ref([])
const houses = ref([])
const rooms = ref([])
const furnitures = ref([])
const keyword = ref('')
const roomFilter = ref(null)
const roomHouseFilter = ref(null)

const itemDlg = ref(false)
const itemForm = ref({})
const roomDlg = ref(false)
const roomForm = ref({})
const furDlg = ref(false)
const furForm = ref({})
const houseDlg = ref(false)
const houseForm = ref({})

const furnOf = (roomId) => furnitures.value.filter((f) => f.roomId === roomId || f.roomId === Number(roomId))
const roomsOf = (houseId) => rooms.value.filter((r) => r.houseId === houseId || r.houseId === Number(houseId))
const houseName = (id) => houses.value.find((h) => h.id === Number(id))?.name || '-'
const roomName = (id) => rooms.value.find((r) => r.id === Number(id))?.name || '-'

const loadHouses = async () => {
  houses.value = await itemApi.houses()
  loadRooms()
}
const loadRooms = async () => {
  rooms.value = await itemApi.rooms(roomHouseFilter.value)
  loadFurnitures()
  loadItems()
}
const loadFurnitures = async () => {
  furnitures.value = await itemApi.furnitures(roomFilter.value)
  if (tab.value === 'items') loadItems()
}
const loadItems = async () => {
  items.value = await itemApi.list({ keyword: keyword.value || undefined })
}

const openItem = (row) => {
  itemForm.value = row
    ? { id: row.id, houseId: row.house_id, roomId: row.room_id, furnitureId: row.furniture_id, name: row.name, aliases: row.aliases, position: row.position, note: row.note }
    : { houseId: null, roomId: null, furnitureId: null, name: '', aliases: '', position: '', note: '' }
  itemDlg.value = true
}
const saveItem = async () => {
  if (!itemForm.value.name) return ElMessage.warning(t('item.itemNameRequired'))
  const body = {
    furnitureId: itemForm.value.furnitureId,
    name: itemForm.value.name,
    aliases: itemForm.value.aliases,
    position: itemForm.value.position,
    note: itemForm.value.note,
  }
  if (itemForm.value.id) await itemApi.update(itemForm.value.id, body)
  else await itemApi.create(body)
  ElMessage.success(t('common.success'))
  itemDlg.value = false
  loadItems()
}
const removeItem = async (row) => {
  await ElMessageBox.confirm(t('item.deleteItemConfirm'), t('common.warning'), { type: 'warning' })
  await itemApi.remove(row.id)
  ElMessage.success(t('common.success'))
  loadItems()
}

const openRoom = (row) => {
  roomForm.value = row ? { id: row.id, houseId: row.houseId, name: row.name, floor: row.floor, note: row.note }
    : { houseId: roomHouseFilter.value, name: '', floor: 1, note: '' }
  roomDlg.value = true
}
const saveRoom = async () => {
  if (!roomForm.value.name) return ElMessage.warning(t('item.roomNameRequired'))
  if (roomForm.value.id) await itemApi.updateRoom(roomForm.value.id, roomForm.value)
  else await itemApi.addRoom(roomForm.value)
  ElMessage.success(t('common.success'))
  roomDlg.value = false
  loadRooms()
}
const removeRoom = async (row) => {
  await ElMessageBox.confirm(t('item.deleteRoomConfirm'), t('common.warning'), { type: 'warning' })
  await itemApi.removeRoom(row.id)
  ElMessage.success(t('common.success'))
  loadRooms()
}

const openFurniture = (row) => {
  furForm.value = row ? { id: row.id, roomId: row.roomId, name: row.name, note: row.note } : { roomId: roomFilter.value, name: '', note: '' }
  furDlg.value = true
}
const saveFurniture = async () => {
  if (!furForm.value.name) return ElMessage.warning(t('item.furnitureNameRequired'))
  if (furForm.value.id) await itemApi.updateFurniture(furForm.value.id, furForm.value)
  else await itemApi.addFurniture(furForm.value)
  ElMessage.success(t('common.success'))
  furDlg.value = false
  loadRooms()
}
const removeFurniture = async (row) => {
  await ElMessageBox.confirm(t('item.deleteFurnitureConfirm'), t('common.warning'), { type: 'warning' })
  await itemApi.removeFurniture(row.id)
  ElMessage.success(t('common.success'))
  loadRooms()
}

const openHouse = (row) => {
  houseForm.value = row ? { id: row.id, name: row.name, address: row.address } : { name: '', address: '' }
  houseDlg.value = true
}
const saveHouse = async () => {
  if (!houseForm.value.name) return ElMessage.warning(t('item.houseNameRequired'))
  if (houseForm.value.id) await itemApi.updateHouse(houseForm.value.id, houseForm.value)
  else await itemApi.addHouse(houseForm.value)
  ElMessage.success(t('common.success'))
  houseDlg.value = false
  loadHouses()
}
const removeHouse = async (row) => {
  await ElMessageBox.confirm(t('item.deleteHouseConfirm'), t('common.warning'), { type: 'warning' })
  await itemApi.removeHouse(row.id)
  ElMessage.success(t('common.success'))
  loadHouses()
}

onMounted(() => {
  loadHouses()
})
</script>

<style scoped>
.page { max-width: 960px; margin: 0 auto; padding: 24px; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.item-card { margin-bottom: 12px; }
.item-main { display: flex; align-items: center; gap: 8px; }
.item-name { font-size: 16px; font-weight: 600; }
.item-path { color: #909399; font-size: 13px; margin-top: 4px; }
.item-aliases { color: #909399; font-size: 12px; margin-top: 2px; }
.item-ops { margin-top: 8px; }
</style>