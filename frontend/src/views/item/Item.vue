<template>
  <div class="page fp-page">
    <!-- 顶栏 -->
    <div class="fp-topbar">
      <el-select v-model="currentHouseId" :placeholder="$t('item.pickHouse')" class="fp-house" @change="onHouseChange">
        <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
      </el-select>
      <el-input v-model="searchKeyword" :placeholder="$t('item.searchPh')" clearable class="fp-search" @keyup.enter="onSearch" @clear="clearSearch">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <div class="fp-top-actions">
        <el-button @click="listMode = !listMode">{{ listMode ? $t('item.done') : $t('item.listView') }}</el-button>
        <el-button v-if="!listMode && houses.length" type="primary" class="fp-edit-btn" @click="toggleEdit">{{ mode === 'edit' ? $t('item.done') : $t('item.editFloorPlan') }}</el-button>
      </div>
    </div>

    <!-- 户型图主视图 -->
    <div v-if="!listMode" class="fp-main">
      <!-- 空态 -->
      <div v-if="!houses.length" class="fp-empty" @click="openHouse()">
        <div class="fp-empty-plus">+</div>
        <div class="fp-empty-text">{{ $t('item.emptyFloorPlan') }}</div>
      </div>

      <template v-else>
        <!-- 编辑侧栏(酷家乐式) -->
        <div v-if="mode === 'edit'" class="fp-sidebar">
          <div class="fp-side-tabs">
            <div :class="['fp-side-tab', { on: sidebarTab === 'rooms' }]" @click="sidebarTab = 'rooms'">{{ $t('item.rooms') }}</div>
            <div :class="['fp-side-tab', { on: sidebarTab === 'furnitures' }]" @click="sidebarTab = 'furnitures'">{{ $t('item.furnitures') }}</div>
            <div :class="['fp-side-tab', { on: sidebarTab === 'library' }]" @click="sidebarTab = 'library'">{{ $t('item.library') }}</div>
          </div>
          <div class="fp-side-body">
            <!-- 房间 tab -->
            <template v-if="sidebarTab === 'rooms'">
              <div class="fp-tool-hint">{{ $t('item.drawHint') }}</div>
              <el-button :type="tool === 'draw-rect' ? 'primary' : ''" class="fp-tool-btn" @click="tool = tool === 'draw-rect' ? 'select' : 'draw-rect'">{{ $t('item.drawRoomRect') }}</el-button>
              <el-button :type="tool === 'draw-poly' ? 'primary' : ''" class="fp-tool-btn" @click="togglePoly">{{ $t('item.drawRoomPoly') }}</el-button>
              <el-upload :show-file-list="false" :before-upload="uploadFloorPlan" accept="image/*" class="fp-upload">
                <el-button class="fp-tool-btn">{{ $t('item.uploadFloorPlan') }}</el-button>
              </el-upload>
              <el-button :type="tool === 'calibrate' ? 'primary' : ''" class="fp-tool-btn" @click="tool = tool === 'calibrate' ? 'select' : 'calibrate'">{{ $t('item.calibrate') }}</el-button>
            </template>
            <!-- 家具 tab -->
            <template v-else-if="sidebarTab === 'furnitures'">
              <div v-for="f in floorFurnitures" :key="f.id" class="fp-side-row">
                <span class="fp-side-name">{{ f.name }}</span>
                <el-button v-if="f.x == null" size="small" type="primary" @click="placeFurniture(f)">{{ $t('item.place') }}</el-button>
                <span v-else class="fp-side-ok">✓</span>
              </div>
              <el-button type="primary" size="small" class="fp-tool-btn" @click="openFurniture()">{{ $t('item.addFurniture') }}</el-button>
            </template>
            <!-- 库 tab -->
            <template v-else>
              <div v-for="f in libraryFurnitures" :key="f.id" class="fp-side-row">
                <span class="fp-side-name">{{ f.name }}</span>
                <span>
                  <el-button size="small" type="primary" @click="placeFurniture(f)">{{ $t('item.place') }}</el-button>
                  <el-button size="small" @click="moveFurnitureToRoom(f)">{{ $t('item.pickRoom') }}</el-button>
                </span>
              </div>
              <div v-if="!libraryFurnitures.length" class="fp-tool-hint">{{ $t('item.emptyItems') }}</div>
            </template>
          </div>
        </div>

        <!-- 画布 -->
        <FloorPlanCanvas
          ref="canvasRef"
          :mode="mode"
          :tool="tool"
          :rooms="floorPlan.rooms"
          :furnitures="floorPlan.furnitures"
          :items="floorPlan.items"
          :image-url="floorPlan.imageUrl"
          :highlight-item-ids="highlightItemIds"
          :selected-furniture-id="selectedFurnitureId"
          :scale="floorPlan.scale || 100"
          @save-room="onSaveRoomGeometry"
          @save-furniture="onSaveFurnitureGeometry"
          @save-item="onSaveItemPlace"
          @create-room="onCreateRoom"
          @select-furniture="onSelectFurniture"
          @calibrate="onCalibrate"
          @edit-edge="onEditEdge"
          @duplicate-room="onDuplicateRoom"
          @delete-room="(id) => removeRoom({ id })"
          @delete-furniture="(id) => removeFurniture({ id })"
        />

        <!-- 空楼层引导(有房子但当前楼层无房间) -->
        <div v-if="!floorPlan.rooms.length && mode !== 'edit'" class="fp-guide" @click="toggleEdit">
          <div class="fp-guide-title">{{ $t('item.emptyFloorRoomsTitle') }}</div>
          <div class="fp-guide-text">{{ $t('item.emptyFloorRoomsText') }}</div>
          <el-button type="primary" size="small">{{ $t('item.editFloorPlan') }}</el-button>
        </div>

        <!-- 楼层切换器(左下角) -->
        <div v-if="floors.length > 1" class="fp-floors">
          <div v-for="f in floors" :key="f" :class="['fp-floor', { on: f === currentFloor }]" @click="switchFloor(f)">
            {{ f }}F
          </div>
        </div>

        <!-- 搜索结果 -->
        <div v-if="searchResults.length" class="fp-results">
          <div class="fp-results-title">{{ $t('item.searchResults') }} ({{ searchResults.length }})</div>
          <div v-for="it in searchResults" :key="it.id" class="fp-result" :class="{ on: highlightItemIds.includes(it.id) }" @click="locateItem(it)">
            <span class="fp-result-name">{{ it.name }}</span>
            <span class="fp-result-path">{{ it.house_name }} / {{ it.room_name }} / {{ it.furniture_name || it.position }}</span>
          </div>
        </div>
      </template>
    </div>

    <!-- 列表模式(旧 CRUD) -->
    <div v-else class="fp-list">
      <el-tabs v-model="tab">
        <el-tab-pane :label="$t('item.houses')" name="houses">
          <div class="page-toolbar">
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
        <el-tab-pane :label="$t('item.rooms')" name="rooms">
          <div class="page-toolbar">
            <el-select v-model="roomHouseFilter" :placeholder="$t('item.allHouses')" clearable style="width: 200px" @change="loadRooms">
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
            <el-table-column prop="note" :label="$t('item.note')" show-overflow-tooltip />
            <el-table-column :label="$t('common.actions')" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="openRoom(row)">{{ $t('common.edit') }}</el-button>
                <el-button size="small" type="danger" plain @click="removeRoom(row)">{{ $t('common.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="$t('item.furnitures')" name="furnitures">
          <div class="page-toolbar">
            <el-select v-model="roomFilter" :placeholder="$t('item.allRooms')" clearable style="width: 200px" @change="loadFurnitures">
              <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
            </el-select>
            <el-button type="primary" @click="openFurniture()">{{ $t('item.addFurniture') }}</el-button>
          </div>
          <el-table :data="furnitures" stripe>
            <el-table-column prop="name" :label="$t('item.furnitureName')" />
            <el-table-column :label="$t('item.roomName')">
              <template #default="{ row }">{{ roomName(row.roomId) }}</template>
            </el-table-column>
            <el-table-column prop="type" :label="$t('item.furnitureType')" width="100" />
            <el-table-column :label="$t('common.actions')" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="openFurniture(row)">{{ $t('common.edit') }}</el-button>
                <el-button size="small" type="danger" plain @click="removeFurniture(row)">{{ $t('common.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="$t('item.items')" name="items">
          <div class="page-toolbar">
            <el-input v-model="keyword" :placeholder="$t('item.searchPh')" clearable style="width: 260px" @keyup.enter="loadItems" @clear="loadItems">
              <template #append><el-button @click="loadItems">{{ $t('item.search') }}</el-button></template>
            </el-input>
            <el-button type="primary" @click="openItem()">{{ $t('item.addItem') }}</el-button>
          </div>
          <el-empty v-if="items.length === 0" :description="$t('item.emptyItems')" />
          <el-card v-for="it in items" :key="it.id" shadow="hover" class="item-card">
            <div class="item-main">
              <span class="item-name">{{ it.name }}</span>
              <el-tag size="small">{{ dictText(t, 'item_type', it.type) }}</el-tag>
              <el-tag v-if="it.position" size="small" type="info">{{ it.position }}</el-tag>
            </div>
            <div class="item-path">{{ it.house_name }} / {{ it.room_name }} / {{ it.furniture_name }}</div>
            <div class="item-ops">
              <el-button size="small" @click="openItem(it)">{{ $t('common.edit') }}</el-button>
              <el-button size="small" type="danger" plain @click="removeItem(it)">{{ $t('common.delete') }}</el-button>
            </div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 物品编辑 -->
    <el-dialog v-model="itemDlg" append-to-body :title="itemForm.id ? $t('item.editItem') : $t('item.addItem')" width="480px">
      <el-form label-width="90px">
        <el-form-item :label="$t('item.houseName')">
          <el-select v-model="itemForm.houseId" :placeholder="$t('item.pickHouse')" style="width: 100%" @change="itemForm.furnitureId = null; itemForm.roomId = null">
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
        <el-form-item :label="$t('item.itemImage')">
          <el-upload :show-file-list="false" :before-upload="(f) => uploadItemImage(f)" accept="image/*">
            <img v-if="itemForm.image_url" :src="itemForm.image_url" class="item-image-preview" />
            <el-button v-else size="small"><el-icon><Plus /></el-icon> {{ $t('item.itemImage') }}</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item :label="$t('item.itemType')">
          <el-select v-model="itemForm.type" style="width: 100%">
            <el-option v-for="tp in itemTypes" :key="tp" :label="dictText(t, 'item_type', tp)" :value="tp" />
          </el-select>
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
    <el-dialog v-model="roomDlg" append-to-body :title="roomForm.id ? $t('item.editRoom') : $t('item.addRoom')" width="420px">
      <el-form label-width="90px">
        <el-form-item v-if="!roomForm._geometry" :label="$t('item.houseName')">
          <el-select v-model="roomForm.houseId" :placeholder="$t('item.pickHouse')" style="width: 100%">
            <el-option v-for="h in houses" :key="h.id" :label="h.name" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('item.roomName')">
          <el-input v-model="roomForm.name" :placeholder="$t('item.roomNameHint')" />
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
    <el-dialog v-model="furDlg" append-to-body :title="furForm.id ? $t('item.editFurniture') : $t('item.addFurniture')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="$t('item.roomName')">
          <el-select v-model="furForm.roomId" clearable :placeholder="$t('item.pickRoom')" style="width: 100%">
            <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('item.furnitureName')">
          <el-input v-model="furForm.name" :placeholder="$t('item.furnitureNamePh')" />
        </el-form-item>
        <el-form-item :label="$t('item.furnitureType')">
          <el-select v-model="furForm.type" filterable allow-create default-first-option :placeholder="$t('item.furnitureType')" style="width: 100%">
            <el-option v-for="tp in furnitureTypes" :key="tp" :label="tp" :value="tp" />
          </el-select>
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
    <el-dialog v-model="houseDlg" append-to-body :title="houseForm.id ? $t('item.editHouse') : $t('item.addHouse')" width="420px">
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
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { itemApi, fileApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'
import FloorPlanCanvas from './FloorPlanCanvas.vue'

const { t } = useI18n()
const itemTypes = ['KITCHENWARE', 'INGREDIENT', 'DAILY', 'CLOTHES', 'TOOL', 'OTHER']
const furnitureTypes = ['衣柜', '床', '冰箱', '书桌', '沙发', '茶几', '柜子', '餐桌', '书架', '其他']

// ---- 户型图状态 ----
const listMode = ref(false)
const mode = ref('view') // view | edit
const tool = ref('select') // select | draw-rect | draw-poly
const sidebarTab = ref('rooms')
const currentHouseId = ref(null)
const currentFloor = ref(1)
const floorPlan = ref({ rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 })
const searchKeyword = ref('')
const searchResults = ref([])
const selectedFurnitureId = ref(null)
const canvasRef = ref(null)

// ---- 列表模式状态(旧 CRUD) ----
const tab = ref('houses')
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

const highlightItemIds = computed(() => searchResults.value.map((it) => it.id))
const floorFurnitures = computed(() => floorPlan.value.furnitures)
const libraryFurnitures = computed(() => furnitures.value.filter((f) => !f.roomId))

const floors = computed(() => {
  const set = new Set()
  floorPlan.value.rooms.forEach((r) => set.add(r.floor))
  const house = houses.value.find((h) => h.id === currentHouseId.value)
  if (house && house.floorPlans) {
    try { Object.keys(JSON.parse(house.floorPlans)).forEach((k) => set.add(Number(k))) } catch {}
  }
  if (!set.size) set.add(currentFloor.value)
  return [...set].sort((a, b) => a - b)
})

// ---- 数据加载 ----
const loadHouses = async () => {
  houses.value = await itemApi.houses()
  if (!currentHouseId.value && houses.value.length) {
    currentHouseId.value = houses.value[0].id
    loadFloorPlan()
  }
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

const loadFloorPlan = async () => {
  if (!currentHouseId.value) { floorPlan.value = { rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 }; return }
  floorPlan.value = await itemApi.floorPlan(currentHouseId.value, currentFloor.value)
}
const onHouseChange = () => { currentFloor.value = 1; loadFloorPlan() }
const uploadFloorPlan = async (file) => {
  try {
    const data = await fileApi.upload(file)
    const house = houses.value.find((h) => h.id === currentHouseId.value)
    let floorPlans = {}
    if (house && house.floorPlans) { try { floorPlans = JSON.parse(house.floorPlans) } catch {} }
    const cur = floorPlans[currentFloor.value] || {}
    floorPlans[currentFloor.value] = { ...cur, imageUrl: data.url }
    await itemApi.saveFloorPlans(currentHouseId.value, JSON.stringify(floorPlans))
    ElMessage.success(t('common.success'))
    loadHouses()
    loadFloorPlan()
  } catch (e) {}
  return false
}
const onCalibrate = async (pxDist) => {
  try {
    const { value } = await ElMessageBox.prompt(t('item.calibratePrompt'), t('item.calibrate'), { inputValue: '1.0', closeOnClickModal: true })
    const meters = parseFloat(value)
    if (!meters || meters <= 0) return
    const scale = pxDist / meters
    const house = houses.value.find((h) => h.id === currentHouseId.value)
    let floorPlans = {}
    if (house && house.floorPlans) { try { floorPlans = JSON.parse(house.floorPlans) } catch {} }
    const cur = floorPlans[currentFloor.value] || {}
    floorPlans[currentFloor.value] = { ...cur, scale }
    await itemApi.saveFloorPlans(currentHouseId.value, JSON.stringify(floorPlans))
    ElMessage.success(t('common.success'))
    tool.value = 'select'
    loadHouses()
    loadFloorPlan()
  } catch (e) {}
}
const onEditEdge = async (roomId, edgeIdx) => {
  const room = floorPlan.value.rooms.find((r) => r.id === roomId)
  if (!room) return
  let poly
  try { poly = JSON.parse(room.geometry || '[]') } catch { return }
  if (poly.length < 3) return
  const a = poly[edgeIdx]; const b = poly[(edgeIdx + 1) % poly.length]
  const scale = floorPlan.value.scale || 100
  const curLen = Math.hypot(b.x - a.x, b.y - a.y) / scale
  try {
    const { value } = await ElMessageBox.prompt(t('item.edgeLenPrompt'), t('item.editEdgeLen'), { inputValue: curLen.toFixed(2), closeOnClickModal: true })
    const meters = parseFloat(value)
    if (!meters || meters <= 0) return
    const px = meters * scale
    const len = Math.hypot(b.x - a.x, b.y - a.y) || 1
    poly[(edgeIdx + 1) % poly.length] = { x: a.x + ((b.x - a.x) / len) * px, y: a.y + ((b.y - a.y) / len) * px }
    await onSaveRoomGeometry(roomId, JSON.stringify(poly))
    ElMessage.success(t('common.success'))
  } catch (e) {}
}
const onDuplicateRoom = (roomId) => {
  const room = floorPlan.value.rooms.find((r) => r.id === roomId)
  if (!room) return
  let poly
  try { poly = JSON.parse(room.geometry || '[]') } catch { return }
  if (poly.length < 3) return
  const shifted = poly.map((p) => ({ x: p.x + 40, y: p.y + 40 }))
  roomForm.value = { houseId: currentHouseId.value, name: `${room.name}${t('item.duplicateSuffix')}`, floor: currentFloor.value, note: room.note || '', _geometry: JSON.stringify(shifted) }
  roomDlg.value = true
}
const switchFloor = (f) => { currentFloor.value = f; loadFloorPlan() }
const toggleEdit = () => {
  mode.value = mode.value === 'edit' ? 'view' : 'edit'
  if (mode.value === 'edit') tool.value = 'select'
}
const togglePoly = () => {
  if (tool.value === 'draw-poly') { tool.value = 'select'; canvasRef.value?.finishPoly() }
  else tool.value = 'draw-poly'
}

// ---- 画布回调 ----
const undoStack = ref([])
const lastRoomGeom = {}
const pushUndo = (entry) => { undoStack.value.push(entry); if (undoStack.value.length > 50) undoStack.value.shift() }

const onSaveRoomGeometry = async (id, geometry) => {
  const room = floorPlan.value.rooms.find((r) => r.id === id)
  const prev = Object.prototype.hasOwnProperty.call(lastRoomGeom, id) ? lastRoomGeom[id] : (room ? room.geometry : null)
  pushUndo({ type: 'room', id, geometry: prev })
  lastRoomGeom[id] = geometry
  await itemApi.saveRoomGeometry(id, geometry)
}
const onSaveFurnitureGeometry = async (id, data, prev) => {
  const f = floorPlan.value.furnitures.find((x) => x.id === id)
  const p = prev || (f ? { x: f.x, y: f.y, w: f.w, h: f.h } : null)
  if (p) pushUndo({ type: 'furn', id, data: p })
  await itemApi.saveFurnitureGeometry(id, data)
}
const onSaveItemPlace = async (id, data, prev) => {
  if (prev) pushUndo({ type: 'item', id, data: prev })
  await itemApi.saveItemPlace(id, data)
}
const undo = async () => {
  const e = undoStack.value.pop()
  if (!e) { ElMessage.info(t('item.nothingToUndo')); return }
  if (e.type === 'room') { await itemApi.saveRoomGeometry(e.id, e.geometry); lastRoomGeom[e.id] = e.geometry }
  else if (e.type === 'furn') await itemApi.saveFurnitureGeometry(e.id, e.data)
  else if (e.type === 'item') await itemApi.saveItemPlace(e.id, e.data)
  await loadFloorPlan()
}
const onSelectFurniture = (id) => { selectedFurnitureId.value = selectedFurnitureId.value === id ? null : id }
const onCreateRoom = (geometry) => {
  roomForm.value = { houseId: currentHouseId.value, name: '', floor: currentFloor.value, note: '', _geometry: geometry }
  roomDlg.value = true
  tool.value = 'select'
}
const placeFurniture = async (f) => {
  let roomId = f.roomId
  if (!roomId) {
    const room = floorPlan.value.rooms[0]
    if (!room) { ElMessage.warning(t('item.drawRoomFirst')); return }
    roomId = room.id
  }
  // 已摆放数递增错开,避免多件家具叠在同一点
  const placed = floorPlan.value.furnitures.filter((x) => x.x != null).length
  const x = 200 + (placed % 5) * 50
  const y = 200 + Math.floor(placed / 5) * 50
  await itemApi.updateFurniture(f.id, { roomId, name: f.name, type: f.type, note: f.note, x, y, w: 200, h: 100 })
  loadRooms()
  loadFloorPlan()
}
const moveFurnitureToRoom = (f) => {
  furForm.value = { id: f.id, roomId: null, name: f.name, type: f.type, note: f.note }
  furDlg.value = true
}

// ---- 搜索 ----
const onSearch = async () => {
  if (!searchKeyword.value) { clearSearch(); return }
  searchResults.value = await itemApi.list({ keyword: searchKeyword.value })
}
const clearSearch = () => { searchKeyword.value = ''; searchResults.value = [] }
const locateItem = (it) => {
  if (it.house_id != null) {
    if (currentHouseId.value !== Number(it.house_id)) {
      currentHouseId.value = Number(it.house_id)
    }
    if (it.floor != null) currentFloor.value = Number(it.floor)
    selectedFurnitureId.value = it.furniture_id || null
    loadFloorPlan()
  }
}

// ---- 旧 CRUD ----
const openItem = (row) => {
  itemForm.value = row
    ? { id: row.id, houseId: row.house_id, roomId: row.room_id, furnitureId: row.furniture_id, name: row.name, aliases: row.aliases, position: row.position, image_url: row.image_url, type: row.type, quantity: row.quantity != null ? Number(row.quantity) : null, unit: row.unit, note: row.note }
    : { houseId: null, roomId: null, furnitureId: null, name: '', aliases: '', position: '', image_url: '', type: 'OTHER', quantity: null, unit: '', note: '' }
  itemDlg.value = true
}
const uploadItemImage = async (file) => {
  try {
    const data = await fileApi.upload(file)
    itemForm.value.image_url = data.url
  } catch (e) {}
  return false
}
const saveItem = async () => {
  if (!itemForm.value.name) return ElMessage.warning(t('item.itemNameRequired'))
  const body = {
    furnitureId: itemForm.value.furnitureId,
    roomId: itemForm.value.roomId,
    name: itemForm.value.name,
    aliases: itemForm.value.aliases,
    position: itemForm.value.position,
    imageUrl: itemForm.value.image_url,
    type: itemForm.value.type,
    quantity: itemForm.value.quantity,
    unit: itemForm.value.unit,
    note: itemForm.value.note,
    relX: itemForm.value.furnitureId ? 0.5 : null,
    relY: itemForm.value.furnitureId ? 0.5 : null,
  }
  if (itemForm.value.id) await itemApi.update(itemForm.value.id, body)
  else await itemApi.create(body)
  ElMessage.success(t('common.success'))
  itemDlg.value = false
  loadItems()
}
const removeItem = async (row) => {
  await ElMessageBox.confirm(t('item.deleteItemConfirm'), t('common.warning'), { type: 'warning', closeOnClickModal: true })
  await itemApi.remove(row.id)
  ElMessage.success(t('common.success'))
  loadItems()
}

const openRoom = async (row) => {
  if (!houses.value.length) await loadHouses()
  roomForm.value = row ? { id: row.id, houseId: row.houseId, name: row.name, floor: row.floor, note: row.note }
    : { houseId: roomHouseFilter.value || currentHouseId.value, name: '', floor: currentFloor.value, note: '' }
  roomDlg.value = true
}
const saveRoom = async () => {
  if (!roomForm.value.houseId && !roomForm.value._geometry) return ElMessage.warning(t('item.pickHouse'))
  if (!roomForm.value.name) return ElMessage.warning(t('item.roomNameRequired'))
  const geom = roomForm.value._geometry
  const floor = roomForm.value.floor
  if (roomForm.value.id) await itemApi.updateRoom(roomForm.value.id, roomForm.value)
  else roomForm.value = await itemApi.addRoom({ houseId: roomForm.value.houseId, name: roomForm.value.name, floor: roomForm.value.floor, note: roomForm.value.note })
  if (geom) await itemApi.saveRoomGeometry(roomForm.value.id, geom)
  // 画在别的楼层时保存后自动切过去(楼层入口)
  if (floor != null && floor !== currentFloor.value) currentFloor.value = floor
  ElMessage.success(t('common.success'))
  roomDlg.value = false
  loadRooms()
  loadFloorPlan()
}
const removeRoom = async (row) => {
  await ElMessageBox.confirm(t('item.deleteRoomMoveLib'), t('common.warning'), { type: 'warning', closeOnClickModal: true })
  await itemApi.removeRoom(row.id)
  ElMessage.success(t('common.success'))
  loadRooms()
  loadFloorPlan()
}

const openFurniture = (row) => {
  furForm.value = row ? { id: row.id, roomId: row.roomId, name: row.name, type: row.type, note: row.note } : { roomId: roomFilter.value || floorPlan.value.rooms[0]?.id || null, name: '', type: '衣柜', note: '' }
  furDlg.value = true
}
const saveFurniture = async () => {
  if (!furForm.value.name) return ElMessage.warning(t('item.furnitureNameRequired'))
  if (furForm.value.id) await itemApi.updateFurniture(furForm.value.id, furForm.value)
  else await itemApi.addFurniture({ roomId: furForm.value.roomId, name: furForm.value.name, type: furForm.value.type, note: furForm.value.note })
  ElMessage.success(t('common.success'))
  furDlg.value = false
  loadRooms()
  loadFloorPlan()
}
const removeFurniture = async (row) => {
  await ElMessageBox.confirm(t('item.deleteFurnitureConfirm'), t('common.warning'), { type: 'warning', closeOnClickModal: true })
  await itemApi.removeFurniture(row.id)
  ElMessage.success(t('common.success'))
  loadRooms()
  loadFloorPlan()
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
  await ElMessageBox.confirm(t('item.deleteHouseConfirm'), t('common.warning'), { type: 'warning', closeOnClickModal: true })
  await itemApi.removeHouse(row.id)
  ElMessage.success(t('common.success'))
  loadHouses()
}

watch(listMode, (v) => { if (!v) loadFloorPlan() })
const onKeydown = (e) => {
  if (!(e.ctrlKey || e.metaKey) || e.key.toLowerCase() !== 'z') return
  const tag = (e.target && e.target.tagName) || ''
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  if (mode.value !== 'edit') return
  e.preventDefault()
  undo()
}
onMounted(() => { loadHouses(); window.addEventListener('keydown', onKeydown) })
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.fp-page { display: flex; flex-direction: column; height: calc(100vh - 80px); }
.fp-topbar { display: flex; align-items: center; gap: 12px; padding: 10px 16px; }
.fp-house { width: 180px; }
.fp-search { width: 320px; }
.fp-top-actions { margin-left: auto; display: flex; gap: 8px; }
.fp-main { position: relative; flex: 1; display: flex; overflow: hidden; border-radius: 14px; }
.fp-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; color: #5c4c3d; }
.fp-empty-plus { width: 96px; height: 96px; border: 2px dashed #b88c6e; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 48px; color: #b88c6e; }
.fp-empty-text { margin-top: 16px; font-size: 15px; color: #8a7a6a; }
.fp-guide { position: absolute; left: 50%; top: 42%; transform: translate(-50%, -50%); text-align: center; cursor: pointer; z-index: 4; display: flex; flex-direction: column; align-items: center; gap: 8px; }
.fp-guide-title { font-size: 16px; font-weight: 600; color: #5c4c3d; }
.fp-guide-text { font-size: 13px; color: #a89a8a; }
.fp-sidebar { width: 220px; border-right: 1px solid #eee5d8; background: #faf5ec; display: flex; flex-direction: column; }
.fp-side-tabs { display: flex; border-bottom: 1px solid #eee5d8; }
.fp-side-tab { flex: 1; text-align: center; padding: 10px 0; cursor: pointer; font-size: 13px; color: #8a7a6a; }
.fp-side-tab.on { color: #5c4c3d; font-weight: 600; border-bottom: 2px solid #b88c6e; }
.fp-side-body { flex: 1; overflow-y: auto; padding: 12px; }
.fp-tool-hint { font-size: 12px; color: #a89a8a; margin-bottom: 12px; }
.fp-tool-btn { width: 100%; margin-left: 0 !important; margin-bottom: 8px; }
.fp-upload { width: 100%; margin-bottom: 8px; }
.fp-upload :deep(.el-upload) { width: 100%; }
.fp-upload :deep(.el-upload) .fp-tool-btn { margin-bottom: 0; }
.fp-side-row { display: flex; align-items: center; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed #eee5d8; }
.fp-side-name { font-size: 13px; color: #5c4c3d; }
.fp-side-ok { color: #7aa07a; }
.fp-floors { position: absolute; left: 12px; bottom: 12px; display: flex; flex-direction: column; gap: 4px; z-index: 5; }
.fp-floor { width: 36px; height: 36px; border-radius: 8px; background: rgba(255,255,255,0.9); color: #5c4c3d; display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 13px; box-shadow: 0 1px 4px rgba(0,0,0,0.1); }
.fp-floor.on { background: #b88c6e; color: #fff; }
.fp-results { position: absolute; right: 12px; top: 12px; width: 260px; max-height: 60%; overflow-y: auto; background: rgba(255,253,248,0.96); border-radius: 12px; box-shadow: 0 3px 12px rgba(0,0,0,0.12); padding: 10px; z-index: 5; }
.fp-results-title { font-size: 13px; font-weight: 600; color: #5c4c3d; margin-bottom: 8px; }
.fp-result { padding: 6px 8px; border-radius: 8px; cursor: pointer; }
.fp-result:hover { background: rgba(184,140,110,0.1); }
.fp-result.on { background: rgba(184,140,110,0.18); }
.fp-result-name { display: block; font-size: 13px; color: #3a2e22; }
.fp-result-path { display: block; font-size: 11px; color: #a89a8a; margin-top: 2px; }
.fp-list { flex: 1; overflow-y: auto; }
.item-card { margin-bottom: 12px; }
.item-main { display: flex; align-items: center; gap: 8px; }
.item-name { font-size: 16px; font-weight: 600; }
.item-path { color: #909399; font-size: 13px; margin-top: 4px; }
.item-ops { margin-top: 8px; }
.item-image-preview { width: 200px; height: 140px; object-fit: cover; border-radius: 8px; }
@media (max-width: 768px) {
  .fp-search { width: 140px; }
  .fp-sidebar { display: none; }
  .fp-edit-btn { display: none; }
  .fp-page { height: calc(100vh - 120px); }
}
</style>
