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
          </div>
          <div class="fp-side-body">
            <!-- 房间 tab -->
            <template v-if="sidebarTab === 'rooms'">
              <div class="fp-tool-hint">{{ $t(toolHintKey) }}</div>
              <div class="fp-tools">
                <!-- 画矩形:虚线矩形+右下角端点 -->
                <el-tooltip :content="$t('item.drawRoomRect')" placement="top" :show-after="300">
                  <el-button :type="tool === 'draw-rect' ? 'primary' : ''" class="fp-tool-ico" @click="tool = tool === 'draw-rect' ? 'select' : 'draw-rect'">
                    <svg viewBox="0 0 24 24" class="fp-ico"><rect x="4.5" y="5.5" width="13" height="12" rx="2" stroke-dasharray="3 2.6" /><circle cx="17.5" cy="17.5" r="2.4" fill="currentColor" stroke="none" /></svg>
                  </el-button>
                </el-tooltip>
                <!-- 逐点描绘:不规则多边形+顶点圆点 -->
                <el-tooltip :content="$t('item.drawRoomPoly')" placement="top" :show-after="300">
                  <el-button :type="tool === 'draw-poly' ? 'primary' : ''" class="fp-tool-ico" @click="togglePoly">
                    <svg viewBox="0 0 24 24" class="fp-ico"><path d="M5.5 14.5 L8 5.5 L15.5 4 L19.5 10 L14 19 L7.5 18.5 Z" /><circle cx="5.5" cy="14.5" r="1.7" fill="currentColor" stroke="none" /><circle cx="8" cy="5.5" r="1.7" fill="currentColor" stroke="none" /><circle cx="15.5" cy="4" r="1.7" fill="currentColor" stroke="none" /><circle cx="19.5" cy="10" r="1.7" fill="currentColor" stroke="none" /><circle cx="14" cy="19" r="1.7" fill="currentColor" stroke="none" /><circle cx="7.5" cy="18.5" r="1.7" fill="currentColor" stroke="none" /></svg>
                  </el-button>
                </el-tooltip>
                <!-- 裁剪:剪刀(与画布 hover 剪刀同款) -->
                <el-tooltip :content="$t('item.cutTip')" placement="top" :show-after="300">
                  <el-button :type="tool === 'cut' ? 'primary' : ''" class="fp-tool-ico" @click="tool = tool === 'cut' ? 'select' : 'cut'">
                    <svg viewBox="0 0 24 24" class="fp-ico"><circle cx="7" cy="19" r="2.8" /><circle cx="17" cy="19" r="2.8" /><line x1="8.5" y1="16.5" x2="20" y2="4" /><line x1="15.5" y1="16.5" x2="4" y2="4" /></svg>
                  </el-button>
                </el-tooltip>
                <!-- 粘合:胶水瓶 -->
                <el-tooltip :content="$t('item.glueTip')" placement="top" :show-after="300">
                  <el-button :type="tool === 'glue' ? 'primary' : ''" class="fp-tool-ico" @click="tool = tool === 'glue' ? 'select' : 'glue'">
                    <svg viewBox="0 0 24 24" class="fp-ico"><rect x="9.7" y="2.5" width="4.6" height="3.6" rx="1" /><path d="M9.3 6.1 h5.4 v2.5 c1.8 1 2.8 2.7 2.8 4.7 v5.4 a2.3 2.3 0 0 1 -2.3 2.3 H8.8 a2.3 2.3 0 0 1 -2.3 -2.3 v-5.4 c0 -2 1 -3.7 2.8 -4.7 Z" /><line x1="8.5" y1="13.5" x2="15.5" y2="13.5" /></svg>
                  </el-button>
                </el-tooltip>
                <!-- 上传底图:微型简约户型图(三个小矩形拼起来) -->
                <el-tooltip :content="$t('item.uploadFloorPlan')" placement="top" :show-after="300">
                  <el-upload :show-file-list="false" :before-upload="uploadFloorPlan" accept="image/*,.pdf">
                    <el-button class="fp-tool-ico">
                      <svg viewBox="0 0 24 24" class="fp-ico"><rect x="4" y="4.5" width="16" height="6.5" /><rect x="4" y="11" width="7.6" height="8.5" /><rect x="11.6" y="11" width="8.4" height="8.5" /></svg>
                    </el-button>
                  </el-upload>
                </el-tooltip>
                <!-- 标定:两点+虚线+端刻度(测距标注,对应点两点量距的交互) -->
                <el-tooltip :content="$t('item.calibrateTip')" placement="top" :show-after="300">
                  <el-button :type="tool === 'calibrate' ? 'primary' : ''" class="fp-tool-ico" @click="tool = tool === 'calibrate' ? 'select' : 'calibrate'">
                    <svg viewBox="0 0 24 24" class="fp-ico"><line x1="5" y1="17.5" x2="19" y2="6.5" stroke-dasharray="3 2.4" /><line x1="3.8" y1="16" x2="6.2" y2="19" /><line x1="17.8" y1="5" x2="20.2" y2="8" /><circle cx="5" cy="17.5" r="1.9" fill="currentColor" stroke="none" /><circle cx="19" cy="6.5" r="1.9" fill="currentColor" stroke="none" /></svg>
                  </el-button>
                </el-tooltip>
                <!-- 调整底图:图片框+外扩箭头(底图分辨率与画布比例尺不一致时,拖动缩放对齐房间) -->
                <el-tooltip v-if="floorPlan.imageUrl" :content="$t('item.adjustBg')" placement="top" :show-after="300">
                  <el-button :type="tool === 'image' ? 'primary' : ''" class="fp-tool-ico" @click="tool = tool === 'image' ? 'select' : 'image'">
                    <svg viewBox="0 0 24 24" class="fp-ico"><rect x="4.5" y="8" width="10" height="9" rx="2" /><path d="M14 8.5 L19.5 3 M19.5 3 h-4.2 M19.5 3 v4.2" /></svg>
                  </el-button>
                </el-tooltip>
              </div>
              <div v-if="floorPlan.imageUrl" class="fp-opacity-row">
                <span class="fp-opacity-label">{{ $t('item.floorPlanOpacity') }}</span>
                <el-slider v-model="floorPlanOpacity" :min="0.1" :max="1" :step="0.05" size="small" @change="saveOpacity" />
              </div>
              <!-- 已有房间列表 -->
              <template v-if="floorPlan.rooms.length">
                <div class="fp-side-head">{{ $t('item.rooms') }}</div>
                <div v-for="r in floorPlan.rooms" :key="r.id" class="fp-side-row">
                  <span class="fp-side-name">{{ r.name }}</span>
                  <span class="fp-side-icons">
                    <el-tooltip :content="$t('item.duplicate')" placement="top" :show-after="300">
                      <el-button size="small" text @click="onDuplicateRoom(r.id)"><el-icon><CopyDocument /></el-icon></el-button>
                    </el-tooltip>
                    <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                      <el-button size="small" text type="danger" @click="removeRoom({ id: r.id })"><el-icon><Delete /></el-icon></el-button>
                    </el-tooltip>
                  </span>
                </div>
              </template>
            </template>
            <!-- 家具 tab(家具库已合并进来) -->
            <template v-else-if="sidebarTab === 'furnitures'">
              <el-button type="primary" size="small" class="fp-tool-btn" @click="openFurniture()">{{ $t('item.addFurniture') }}</el-button>
              <div class="fp-side-head">{{ $t('item.furnPresets') }}</div>
              <div class="fp-presets">
                <div v-for="p in furnPresets" :key="p.type" class="fp-preset" draggable="true" @dragstart="onPresetDragStart($event, p)">
                  <span class="fp-preset-shape" :style="presetShape(p)"></span>
                  <span class="fp-preset-name">{{ p.type }}</span>
                </div>
              </div>

              <div class="fp-side-head">{{ $t('item.placedFurniture') }}</div>
              <div v-for="f in floorFurnitures" :key="f.id" class="fp-side-furn">
                <div class="fp-side-row">
                  <el-input
                    v-if="editingFurnId === f.id"
                    v-model="editingFurnName"
                    size="small"
                    class="fp-side-rename"
                    :ref="setFurnNameInput"
                    @keyup.enter="commitEditFurnName"
                    @blur="commitEditFurnName"
                  />
                  <span v-else class="fp-side-name fp-side-name-editable" @click="startEditFurnName(f)">{{ f.name }}</span>
                  <span class="fp-side-icons">
                    <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
                      <el-button size="small" text @click="openFurniture(f)"><el-icon><Edit /></el-icon></el-button>
                    </el-tooltip>
                    <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                      <el-button size="small" text type="danger" @click="unplaceFurniture(f)"><el-icon><Delete /></el-icon></el-button>
                    </el-tooltip>
                  </span>
                </div>
                <div class="fp-side-meta">
                  <span v-if="furnRoomName(f)" class="fp-side-room">{{ furnRoomName(f) }}</span>
                  <span v-if="f.note" class="fp-side-note">{{ f.note }}</span>
                  <span class="fp-side-count">{{ furnItemCount(f) }} {{ $t('item.itemsCount') }}</span>
                </div>
              </div>
              <div v-if="!floorFurnitures.length" class="fp-tool-hint">{{ $t('item.emptyItems') }}</div>

              <div class="fp-side-head">{{ $t('item.library') }}</div>
              <div v-if="libraryFurnitures.length" class="fp-drag-hint">{{ $t('item.dragFurnHint') }}</div>
              <div v-for="f in libraryFurnitures" :key="f.id" class="fp-side-furn" draggable="true" @dragstart="onLibFurnDragStart($event, f)">
                <div class="fp-side-row">
                  <el-input
                    v-if="editingFurnId === f.id"
                    v-model="editingFurnName"
                    size="small"
                    class="fp-side-rename"
                    :ref="setFurnNameInput"
                    @keyup.enter="commitEditFurnName"
                    @blur="commitEditFurnName"
                  />
                  <span v-else class="fp-side-name fp-side-name-editable" @click="startEditFurnName(f)">{{ f.name }}</span>
                  <span class="fp-side-icons">
                    <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
                      <el-button size="small" text @click="openFurniture(f)"><el-icon><Edit /></el-icon></el-button>
                    </el-tooltip>
                    <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                      <el-button size="small" text type="danger" @click="removeFurniture(f)"><el-icon><Delete /></el-icon></el-button>
                    </el-tooltip>
                  </span>
                </div>
                <div class="fp-side-meta">
                  <span v-if="f.note" class="fp-side-note">{{ f.note }}</span>
                  <span class="fp-side-count">{{ furnItemCount(f) }} {{ $t('item.itemsCount') }}</span>
                </div>
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
          :opacity="floorPlanOpacity"
          :highlight-item-ids="highlightItemIds"
          :selected-furniture-id="selectedFurnitureId"
          :scale="floorPlan.scale || 100"
          :image-transform="floorPlanImg"
          :fit-key="fitKey"
          @save-room="onSaveRoomGeometry"
          @save-rooms="onSaveRoomsBatch"
          @save-furniture="onSaveFurnitureGeometry"
          @save-item="onSaveItemPlace"
          @create-room="onCreateRoom"
          @create-furniture="onCreateFurniture"
          @select-furniture="onSelectFurniture"
          @calibrate="onCalibrate"
          @edit-edge="onEditEdge"
          @delete-furniture="(id) => unplaceFurniture({ id })"
          @rename-room="onRenameRoom"
          @rename-furniture="onRenameFurniture"
          @cut-room="onCutRoom"
          @glue-rooms="onGlueRooms"
          @save-image-transform="onSaveImageTransform"
          @place-furniture="onPlaceFurnitureFromDrop"
          :floor-transition="floorTransition"
        />

        <!-- 空楼层引导(有房子但当前楼层无房间) -->
        <div v-if="!floorPlan.rooms.length && mode !== 'edit'" class="fp-guide" @click="toggleEdit">
          <div class="fp-guide-title">{{ $t('item.emptyFloorRoomsTitle') }}</div>
          <div class="fp-guide-text">{{ $t('item.emptyFloorRoomsText') }}</div>
          <el-button type="primary" size="small">{{ $t('item.editFloorPlan') }}</el-button>
        </div>

        <!-- 楼层切换器(左下角;编辑态常显,可加层) -->
        <div v-if="floors.length > 1 || mode === 'edit'" :class="['fp-floors', { 'with-side': mode === 'edit' }]">
          <div v-for="f in floors" :key="f" :class="['fp-floor', { on: f === currentFloor }]" @click="switchFloor(f)">
            {{ f }}F
          </div>
          <div v-if="mode === 'edit'" class="fp-floor" @click="addFloor">+</div>
        </div>

        <!-- 适配视图:缩放平移后一键回到全景(撤销/保存不再自动重置视图) -->
        <div class="fp-fit" :title="$t('item.fitView')" @click="canvasRef?.fit()">
          <svg viewBox="0 0 24 24" class="fp-fit-ico"><path d="M4 9 V6.5 A2.5 2.5 0 0 1 6.5 4 H9 M15 4 h2.5 A2.5 2.5 0 0 1 20 6.5 V9 M20 15 v2.5 a2.5 2.5 0 0 1 -2.5 2.5 H15 M9 20 H6.5 A2.5 2.5 0 0 1 4 17.5 V15" /></svg>
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
            <template v-if="!selectMode">
              <div class="tb-left">
                <el-input v-model="keyword" :placeholder="$t('item.searchPh')" clearable size="small" style="width: 260px" @keyup.enter="loadItems" @clear="loadItems">
                  <template #append><el-button @click="loadItems">{{ $t('item.search') }}</el-button></template>
                </el-input>
              </div>
              <div class="tb-right">
                <el-button :disabled="!items.length" @click="toggleSelect">{{ $t('item.select') }}</el-button>
                <el-button type="primary" @click="openItem()">{{ $t('item.addItem') }}</el-button>
              </div>
            </template>
            <div v-else class="tb-right">
              <span class="select-count">{{ $t('item.selectedItems', { n: selectedIds.length }) }}</span>
              <el-button @click="toggleSelect">{{ $t('item.cancelSelect') }}</el-button>
              <el-button type="primary" :disabled="!selectedIds.length" @click="openBatchFurniture()">{{ $t('item.batchEditFurniture') }}</el-button>
            </div>
          </div>
          <el-empty v-if="items.length === 0" :description="$t('item.emptyItems')" />
          <el-card v-for="it in items" :key="it.id" shadow="hover" class="item-card" :class="{ 'is-pick': selectMode, selected: selectMode && selectedIds.includes(it.id) }" @click="selectMode && togglePick(it)">
            <span v-if="selectMode" class="pick-badge" :class="{ on: selectedIds.includes(it.id) }">
              <svg viewBox="0 0 16 16" width="12" height="12"><path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </span>
            <div class="item-main">
              <span class="item-name">{{ it.name }}</span>
              <el-tag size="small">{{ dictText(t, 'item_type', it.type) }}</el-tag>
              <el-tag v-if="it.position" size="small" type="info">{{ it.position }}</el-tag>
            </div>
            <div class="item-path">{{ it.house_name }} / {{ it.room_name }} / {{ it.furniture_name }}</div>
            <div v-if="!selectMode" class="item-ops">
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

    <!-- 批量设置所属家具 -->
    <el-dialog v-model="batchFurnDlg" append-to-body :title="$t('item.batchEditFurniture')" width="420px">
      <el-form label-width="90px">
        <el-form-item :label="$t('item.furnitureName')">
          <el-select v-model="batchFurnitureId" clearable :placeholder="$t('item.pickFurniture')" style="width: 100%">
            <el-option v-for="f in furnitures" :key="f.id" :label="furnLabel(f)" :value="f.id" />
          </el-select>
        </el-form-item>
        <div class="fp-batch-hint">{{ $t('item.batchAssignHint') }}</div>
      </el-form>
      <template #footer>
        <el-button @click="batchFurnDlg = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="applyBatchFurniture">{{ $t('common.confirm') }}</el-button>
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
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, CopyDocument, Delete, Edit } from '@element-plus/icons-vue'
import { itemApi, fileApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'
import { splitPoly, mergePolys, pointInPoly, polyBBox, samePt } from '@/utils/floorPlanGeom'
import FloorPlanCanvas from './FloorPlanCanvas.vue'

const { t } = useI18n()
const itemTypes = ['KITCHENWARE', 'INGREDIENT', 'DAILY', 'CLOTHES', 'TOOL', 'OTHER']
const furnitureTypes = ['衣柜', '床', '冰箱', '书桌', '沙发', '茶几', '柜子', '餐桌', '书架', '其他']
// 预设家具(画布 px,默认 100px/m):拖入画布自动关联房间、按类型序号命名
const furnPresets = [
  { type: '衣柜', w: 120, h: 60 },
  { type: '床', w: 150, h: 200 },
  { type: '书桌', w: 120, h: 60 },
  { type: '餐桌', w: 140, h: 80 },
  { type: '沙发', w: 180, h: 90 },
  { type: '茶几', w: 90, h: 50 },
  { type: '冰箱', w: 70, h: 70 },
  { type: '柜子', w: 80, h: 40 },
]
// 预设缩略形状:按真实 w/h 等比缩进 26px 框(此前引用未定义的 pw/ph,形状塌陷不可见)
const presetShape = (p) => {
  const s = 26 / Math.max(p.w, p.h)
  return { width: Math.round(p.w * s) + 'px', height: Math.round(p.h * s) + 'px' }
}

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
const fitKey = ref(0)
const floorTransition = ref({ direction: 'down', phase: '' })
// 侧栏提示随当前工具切换:裁剪/粘合/标定/底图显示各自操作说明,其余回退画图提示
const toolHintKey = computed(() => ({ cut: 'item.cutTip', glue: 'item.glueTip', calibrate: 'item.calibrateTip', image: 'item.adjustBgTip' }[tool.value] || 'item.drawHint'))

// ---- 列表模式状态(旧 CRUD) ----
const tab = ref('houses')
const items = ref([])
const houses = ref([])
const rooms = ref([])
const furnitures = ref([])
const keyword = ref('')
const roomFilter = ref(null)
const roomHouseFilter = ref(null)
// 物品多选 + 批量设置所属家具
const selectMode = ref(false)
const selectedIds = ref([])
const batchFurnDlg = ref(false)
const batchFurnitureId = ref(null)

const itemDlg = ref(false)
const itemForm = ref({})
const roomDlg = ref(false)
const roomForm = ref({})
const furDlg = ref(false)
const furForm = ref({})
const editingFurnId = ref(null) // 侧栏家具名内联编辑中的家具 id
const editingFurnName = ref('')
let furnNameInput = null
const setFurnNameInput = (el) => { furnNameInput = el }
const houseDlg = ref(false)
const houseForm = ref({})

const furnOf = (roomId) => furnitures.value.filter((f) => f.roomId === roomId || f.roomId === Number(roomId))
const roomsOf = (houseId) => rooms.value.filter((r) => r.houseId === houseId || r.houseId === Number(houseId))
const houseName = (id) => houses.value.find((h) => h.id === Number(id))?.name || '-'
const roomName = (id) => rooms.value.find((r) => r.id === Number(id))?.name || '-'
const furnLabel = (f) => (f.roomId ? `${f.name}（${roomName(f.roomId)}）` : `${f.name}（${t('item.library')}）`)

const highlightItemIds = computed(() => searchResults.value.map((it) => it.id))
const floorFurnitures = computed(() => floorPlan.value.furnitures)
const libraryFurnitures = computed(() => furnitures.value.filter((f) => !f.roomId))
// 家具当前所在房间名 / 存放物品数(侧栏家具列表展示)
const furnRoomName = (f) => floorPlan.value.rooms.find((r) => Number(r.id) === Number(f.roomId))?.name || ''
const furnItemCount = (f) => floorPlan.value.items.filter((it) => Number(it.furnitureId) === Number(f.id)).length

const floors = computed(() => {
  const set = new Set()
  floorPlan.value.rooms.forEach((r) => set.add(r.floor))
  const house = houses.value.find((h) => h.id === currentHouseId.value)
  if (house && house.floorPlans) {
    try { Object.keys(JSON.parse(house.floorPlans)).forEach((k) => set.add(Number(k))) } catch {}
  }
  set.add(currentFloor.value) // 当前层常驻:点 + 新开的空层切走后不消失
  return [...set].sort((a, b) => b - a)
})

// 底图不透明度 + 底图变换(平移/缩放),均在楼层配置内,前端自行解析
const floorPlanOpacity = ref(1)
const floorPlanImg = ref({ x: 0, y: 0, k: 1 })
watch([() => houses.value, currentHouseId, currentFloor], () => {
  const h = houses.value.find((x) => x.id === currentHouseId.value)
  let v = 1
  let img = { x: 0, y: 0, k: 1 }
  if (h && h.floorPlans) {
    try {
      const cfg = JSON.parse(h.floorPlans)[currentFloor.value]
      v = cfg?.opacity ?? 1
      if (cfg?.img) img = { ...cfg.img }
    } catch {}
  }
  floorPlanOpacity.value = v
  floorPlanImg.value = img
}, { immediate: true })
const saveOpacity = async (val) => {
  const house = houses.value.find((h) => h.id === currentHouseId.value)
  let floorPlans = {}
  if (house && house.floorPlans) { try { floorPlans = JSON.parse(house.floorPlans) } catch {} }
  const cur = floorPlans[currentFloor.value] || {}
  floorPlans[currentFloor.value] = { ...cur, opacity: val }
  const json = JSON.stringify(floorPlans)
  await itemApi.saveFloorPlans(currentHouseId.value, json)
  // 回写本地(否则切层回来 watch 从旧 floorPlans 解析,opacity 回退)
  if (house) house.floorPlans = json
}
const onSaveImageTransform = async (img) => {
  const house = houses.value.find((h) => h.id === currentHouseId.value)
  let floorPlans = {}
  if (house && house.floorPlans) { try { floorPlans = JSON.parse(house.floorPlans) } catch {} }
  const cur = floorPlans[currentFloor.value] || {}
  floorPlans[currentFloor.value] = { ...cur, img }
  const json = JSON.stringify(floorPlans)
  await itemApi.saveFloorPlans(currentHouseId.value, json)
  // 回写本地(否则切层回来 watch 从旧 floorPlans 解析,底图位置回退)
  if (house) house.floorPlans = json
  floorPlanImg.value = { ...img }
}

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

let floorPlanSeq = 0 // 楼层数据加载序号:快速切层/切房时丢弃过期响应,避免旧楼层晚到覆盖新楼层造成闪烁
const loadFloorPlan = async () => {
  const seq = ++floorPlanSeq
  if (!currentHouseId.value) { floorPlan.value = { rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 }; return }
  const data = await itemApi.floorPlan(currentHouseId.value, currentFloor.value)
  if (seq !== floorPlanSeq) return // 过期响应丢弃
  floorPlan.value = data
}
const onHouseChange = async () => { currentFloor.value = 1; await loadFloorPlan(); fitKey.value++ }
// PDF 底图:渲染第一页为 PNG 再上传(SVG image 不支持 PDF)
const pdfToImage = async (file) => {
  const pdfjs = await import('pdfjs-dist')
  const workerUrl = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default
  pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
  const buf = await file.arrayBuffer()
  const pdf = await pdfjs.getDocument({ data: buf }).promise
  const page = await pdf.getPage(1)
  const viewport = page.getViewport({ scale: 2 })
  const canvas = document.createElement('canvas')
  canvas.width = Math.min(viewport.width, 4000)
  canvas.height = Math.round(viewport.height * (canvas.width / viewport.width))
  await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise
  return new Promise((resolve) => canvas.toBlob((b) => {
    resolve(new File([b], 'floor-plan.png', { type: 'image/png' }))
  }, 'image/png'))
}
const uploadFloorPlan = async (file) => {
  try {
    const img = file.type === 'application/pdf' ? await pdfToImage(file) : file
    const data = await fileApi.upload(img)
    const house = houses.value.find((h) => h.id === currentHouseId.value)
    let floorPlans = {}
    if (house && house.floorPlans) { try { floorPlans = JSON.parse(house.floorPlans) } catch {} }
    const cur = floorPlans[currentFloor.value] || {}
    floorPlans[currentFloor.value] = { ...cur, imageUrl: data.url }
    await itemApi.saveFloorPlans(currentHouseId.value, JSON.stringify(floorPlans))
    ElMessage.success(t('common.success'))
    loadHouses()
    await loadFloorPlan()
    fitKey.value++
  } catch (e) { console.error(e) }
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
  const a = poly[edgeIdx]; const bIdx = (edgeIdx + 1) % poly.length; const b = poly[bIdx]
  const scale = floorPlan.value.scale || 100
  const curLen = Math.hypot(b.x - a.x, b.y - a.y) / scale
  try {
    const { value } = await ElMessageBox.prompt(t('item.edgeLenPrompt'), t('item.editEdgeLen'), { inputValue: curLen.toFixed(2), closeOnClickModal: true })
    const meters = parseFloat(value)
    if (!meters || meters <= 0) return
    const px = meters * scale
    const len = Math.hypot(b.x - a.x, b.y - a.y) || 1
    const nb = { x: a.x + ((b.x - a.x) / len) * px, y: a.y + ((b.y - a.y) / len) * px }
    poly[bIdx] = nb
    // 共享墙角联动:与其他房间重合于被移动端点的顶点一起挪,墙体保持相连
    const saves = [{ id: roomId, geometry: JSON.stringify(poly) }]
    for (const r of floorPlan.value.rooms) {
      if (r.id === roomId) continue
      let rp
      try { rp = JSON.parse(r.geometry || '[]') } catch { continue }
      if (!rp.some((v) => samePt(v, b))) continue
      rp.forEach((v, j) => { if (samePt(v, b)) rp[j] = { ...nb } })
      saves.push({ id: r.id, geometry: JSON.stringify(rp) })
    }
    if (saves.length > 1) await onSaveRoomsBatch(saves)
    else await onSaveRoomGeometry(roomId, JSON.stringify(poly))
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
const switchFloor = async (f) => {
  if (f === currentFloor.value) return
  const direction = f > currentFloor.value ? 'up' : 'down'
  // Phase 1: exit animation (old content visible)
  floorTransition.value = { direction, phase: 'exit' }
  await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))
  // Phase 2: swap data + enter animation (new content fades in)
  currentFloor.value = f
  await loadFloorPlan()
  // 不 fit:保留当前缩放平移位置,用户要求画板不变
  floorTransition.value = { direction, phase: 'enter' }
  setTimeout(() => { floorTransition.value = { direction, phase: '' } }, 550)
}
const addFloor = async () => {
  currentFloor.value = Math.max(...floors.value) + 1
  await loadFloorPlan()
  fitKey.value++
}
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
  // 回写本地:边长编辑/撤销读取的 geometry 与画布视觉保持一致(否则读到旧值,改了不生效)
  if (room) room.geometry = geometry
}
// 联动拖拽批量保存:多房间几何合并为一条撤销记录,一步整体回滚
const onSaveRoomsBatch = async (list) => {
  const items = list.map(({ id, geometry }) => {
    const room = floorPlan.value.rooms.find((r) => r.id === id)
    const prev = Object.prototype.hasOwnProperty.call(lastRoomGeom, id) ? lastRoomGeom[id] : (room ? room.geometry : null)
    lastRoomGeom[id] = geometry
    if (room) room.geometry = geometry
    return { id, prev, geometry }
  })
  if (items.some((x) => x.prev != null)) pushUndo({ type: 'rooms', items })
  for (const it of items) await itemApi.saveRoomGeometry(it.id, it.geometry)
}
const onSaveFurnitureGeometry = async (id, data, prev) => {
  const f = floorPlan.value.furnitures.find((x) => x.id === id)
  const p = prev || (f ? { x: f.x, y: f.y, w: f.w, h: f.h, roomId: f.roomId } : null)
  if (p) pushUndo({ type: 'furn', id, data: p })
  await itemApi.saveFurnitureGeometry(id, data)
  // 回写原对象:级联平移基准/后续 undo prev 与视觉位置一致(否则拖房间时家具跳回旧位)
  if (f) Object.assign(f, data)
}
const onSaveItemPlace = async (id, data, prev) => {
  if (prev) pushUndo({ type: 'item', id, data: prev })
  await itemApi.saveItemPlace(id, data)
}
const undo = async () => {
  const e = undoStack.value.pop()
  if (!e) { ElMessage.info(t('item.nothingToUndo')); return }
  if (e.type === 'room') { await itemApi.saveRoomGeometry(e.id, e.geometry); lastRoomGeom[e.id] = e.geometry }
  else if (e.type === 'rooms') {
    for (const it of e.items) {
      if (it.prev == null) continue
      await itemApi.saveRoomGeometry(it.id, it.prev)
      lastRoomGeom[it.id] = it.prev
    }
  }
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
// 从画布拖放摆放库内家具:指定房间+精确坐标
const onPlaceFurnitureFromDrop = async ({ id, roomId, x, y }) => {
  const f = furnitures.value.find((x) => x.id === id)
  if (!f) return
  const rid = roomId || floorPlan.value.rooms[0]?.id
  if (!rid) { ElMessage.warning(t('item.drawRoomFirst')); return }
  await itemApi.updateFurniture(f.id, { roomId: rid, name: f.name, type: f.type, note: f.note, x: x - 100, y: y - 50, w: 200, h: 100 })
  ElMessage.success(t('common.success'))
  loadRooms()
  loadFloorPlan()
}
const moveFurnitureToRoom = (f) => {
  furForm.value = { id: f.id, roomId: null, name: f.name, type: f.type, note: f.note }
  furDlg.value = true
}

// ---- 预设家具拖入 ----
const onPresetDragStart = (e, p) => {
  e.dataTransfer.setData('text/furn-type', p.type)
  e.dataTransfer.effectAllowed = 'copy'
}
// 库内家具拖入画布:携带家具 id,drop 时走摆放逻辑
const onLibFurnDragStart = (e, f) => {
  e.dataTransfer.setData('text/furn-id', String(f.id))
  e.dataTransfer.effectAllowed = 'move'
}
const onCreateFurniture = async ({ type, roomId, x, y }) => {
  if (!roomId) { ElMessage.warning(t('item.dropInRoomFirst')); return }
  const preset = furnPresets.find((p) => p.type === type)
  const w = preset ? preset.w : 100
  const h = preset ? preset.h : 100
  const seq = furnitures.value.filter((f) => f.type === type).length + 1
  await itemApi.addFurniture({ roomId, name: `${type}${seq}`, type, x: x - w / 2, y: y - h / 2, w, h })
  ElMessage.success(t('common.success'))
  loadRooms()
  loadFloorPlan()
}

// ---- 双击名称改名 ----
const onRenameRoom = async (id) => {
  const room = floorPlan.value.rooms.find((r) => r.id === id)
  if (!room) return
  try {
    const { value } = await ElMessageBox.prompt(t('item.renamePrompt'), t('item.editRoom'), { inputValue: room.name, closeOnClickModal: true })
    if (!value || value === room.name) return
    await itemApi.updateRoom(id, { name: value, floor: room.floor, note: room.note })
    ElMessage.success(t('common.success'))
    loadRooms()
    loadFloorPlan()
  } catch (e) {}
}
const doRenameFurniture = async (id, name) => {
  const f = floorPlan.value.furnitures.find((x) => x.id === id) || furnitures.value.find((x) => x.id === id)
  if (!f || !name || name === f.name) return
  await itemApi.updateFurniture(id, { roomId: f.roomId, name, type: f.type, note: f.note, x: f.x, y: f.y, w: f.w, h: f.h })
  ElMessage.success(t('common.success'))
  loadRooms()
  loadFloorPlan()
}
const onRenameFurniture = async (id) => {
  const f = floorPlan.value.furnitures.find((x) => x.id === id)
  if (!f) return
  try {
    const { value } = await ElMessageBox.prompt(t('item.renamePrompt'), t('item.editFurniture'), { inputValue: f.name, closeOnClickModal: true })
    if (!value || value === f.name) return
    await doRenameFurniture(id, value)
  } catch (e) {}
}
// 侧栏家具名内联改名:点击名称变输入框,回车/失焦保存(不再弹对话框)
const startEditFurnName = (f) => {
  editingFurnId.value = f.id
  editingFurnName.value = f.name
  nextTick(() => furnNameInput?.focus())
}
const commitEditFurnName = async () => {
  const id = editingFurnId.value
  if (id == null) return
  editingFurnId.value = null
  const name = (editingFurnName.value || '').trim()
  await doRenameFurniture(id, name)
}

// ---- 裁剪(拆分)/ 粘合(合并) ----
const nextRoomName = (base, seq) => {
  let n = seq
  while (rooms.value.some((r) => r.name === `${base}${n}`)) n++
  return `${base}${n}`
}
// 家具按中心点分配到新房间
const moveFurnToRoom = async (f, roomId) => {
  await itemApi.updateFurniture(f.id, { roomId, name: f.name, type: f.type, note: f.note, x: f.x, y: f.y, w: f.w, h: f.h })
}
// 散放物品转移到新房间,并按新房间包围盒重算相对坐标
const moveScatteredItem = async (it, fromBBox, newPoly, newRoomId) => {
  const ax = fromBBox.minX + (it.relX || 0.5) * (fromBBox.maxX - fromBBox.minX)
  const ay = fromBBox.minY + (it.relY || 0.5) * (fromBBox.maxY - fromBBox.minY)
  const tb = polyBBox(newPoly)
  const relX = Math.max(0, Math.min(1, (ax - tb.minX) / ((tb.maxX - tb.minX) || 1)))
  const relY = Math.max(0, Math.min(1, (ay - tb.minY) / ((tb.maxY - tb.minY) || 1)))
  await itemApi.update(it.id, {
    roomId: newRoomId, relX, relY,
    name: it.name, aliases: it.aliases, position: it.position, imageUrl: it.image_url,
    type: it.type, quantity: it.quantity, unit: it.unit, note: it.note,
  })
}
const onCutRoom = async ({ roomId, a, b }) => {
  const room = floorPlan.value.rooms.find((r) => r.id === roomId)
  if (!room) return
  let poly
  try { poly = JSON.parse(room.geometry || '[]') } catch { return }
  if (poly.length < 3) return
  const [poly1, poly2] = splitPoly(poly, a.edgeIdx, a.point, b.edgeIdx, b.point)
  const houseId = room.house_id ?? room.houseId
  const name1 = nextRoomName(room.name, 1)
  const name2 = nextRoomName(room.name, 2)
  // 两个新房间互不依赖:并行创建
  const [r1, r2] = await Promise.all([
    itemApi.addRoom({ houseId, name: name1, floor: room.floor, note: room.note }),
    itemApi.addRoom({ houseId, name: name2, floor: room.floor, note: room.note }),
  ])
  // 家具按中心分配;散放物品按绝对位置分配并重算相对坐标
  const bbox = polyBBox(poly)
  // 新房间几何/家具/物品迁移互不依赖:并行;删原房间必须最后(后端删房间会把家具移入库)
  await Promise.all([
    itemApi.saveRoomGeometry(r1.id, JSON.stringify(poly1)),
    itemApi.saveRoomGeometry(r2.id, JSON.stringify(poly2)),
    ...floorPlan.value.furnitures.filter((x) => x.roomId === roomId && x.x != null)
      .map((f) => moveFurnToRoom(f, pointInPoly({ x: f.x + f.w / 2, y: f.y + f.h / 2 }, poly1) ? r1.id : r2.id)),
    ...floorPlan.value.items.filter((x) => x.roomId === roomId && x.furnitureId == null)
      .map((it) => {
        const ax = bbox.minX + (it.relX || 0.5) * (bbox.maxX - bbox.minX)
        const ay = bbox.minY + (it.relY || 0.5) * (bbox.maxY - bbox.minY)
        const target = pointInPoly({ x: ax, y: ay }, poly1) ? { poly: poly1, id: r1.id } : { poly: poly2, id: r2.id }
        return moveScatteredItem(it, bbox, target.poly, target.id)
      }),
  ])
  await itemApi.removeRoom(roomId)
  ElMessage.success(t('common.success'))
  tool.value = 'select'
  loadRooms()
  loadFloorPlan()
}
const onGlueRooms = async ({ roomAId, roomBId }) => {
  const A = floorPlan.value.rooms.find((r) => r.id === roomAId)
  const B = floorPlan.value.rooms.find((r) => r.id === roomBId)
  if (!A || !B) return
  let polyA; let polyB
  try { polyA = JSON.parse(A.geometry || '[]'); polyB = JSON.parse(B.geometry || '[]') } catch { return }
  const merged = mergePolys(polyA, polyB)
  if (!merged) { ElMessage.warning(t('item.glueNoSharedEdge')); return }
  // B 的家具归 A;散放物品按绝对位置重算相对合并包围盒的坐标
  const bboxB = polyBBox(polyB)
  // 迁移/A 改名/A 合并几何互不依赖:并行;删 B 必须最后(后端删房间会把家具移入库)
  await Promise.all([
    ...floorPlan.value.furnitures.filter((x) => x.roomId === roomBId && x.x != null)
      .map((f) => moveFurnToRoom(f, roomAId)),
    ...floorPlan.value.items.filter((x) => x.roomId === roomBId && x.furnitureId == null)
      .map((it) => moveScatteredItem(it, bboxB, merged, roomAId)),
    itemApi.updateRoom(roomAId, { name: `${A.name}-${B.name}`, floor: A.floor, note: A.note }),
    itemApi.saveRoomGeometry(roomAId, JSON.stringify(merged)),
  ])
  await itemApi.removeRoom(roomBId)
  ElMessage.success(t('common.success'))
  tool.value = 'select'
  loadRooms()
  loadFloorPlan()
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
const unplaceFurniture = async (row) => {
  await ElMessageBox.confirm(t('item.unplaceFurnitureConfirm'), t('common.warning'), { type: 'warning', closeOnClickModal: true })
  await itemApi.unplaceFurniture(row.id)
  ElMessage.success(t('common.success'))
  loadRooms()
  loadFloorPlan()
}

// ---- 物品多选 + 批量设置所属家具 ----
const toggleSelect = () => {
  selectMode.value = !selectMode.value
  if (!selectMode.value) selectedIds.value = []
}
const togglePick = (it) => {
  const i = selectedIds.value.indexOf(it.id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(it.id)
}
const openBatchFurniture = () => {
  if (!selectedIds.value.length) return ElMessage.warning(t('item.batchSelectFirst'))
  batchFurnitureId.value = null
  batchFurnDlg.value = true
}
const applyBatchFurniture = async () => {
  await itemApi.batchAssignFurniture({ ids: [...selectedIds.value], furnitureId: batchFurnitureId.value })
  ElMessage.success(t('common.success'))
  batchFurnDlg.value = false
  selectedIds.value = []
  selectMode.value = false
  loadItems()
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
  if (e.key === 'Escape') { canvasRef.value?.cancelPending(); return }
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
.fp-page { display: flex; flex-direction: column; max-width: none; width: 100%; height: 100vh; height: 100dvh; }
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
.fp-tool-hint { font-size: 12px; line-height: 1.6; color: #8a7a6a; background: rgba(184, 140, 110, 0.09); border-radius: 8px; padding: 8px 10px; margin-bottom: 12px; }
.fp-drag-hint { font-size: 11px; color: #a89a8a; margin-bottom: 6px; }
.fp-tool-btn { width: 100%; margin-left: 0 !important; margin-bottom: 8px; }
.fp-tools { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.fp-tools :deep(.el-button) { width: 36px; height: 36px; padding: 0; }
.fp-tools :deep(.el-button + .el-button) { margin-left: 0; }
.fp-tools :deep(.el-upload) { display: inline-flex; }
.fp-ico { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.fp-side-head { font-size: 11px; font-weight: 600; letter-spacing: 0.08em; color: #a89a8a; margin: 14px 0 4px; }
.fp-presets { display: grid; grid-template-columns: repeat(4, 1fr); gap: 6px; margin: 8px 0 12px; }
.fp-preset { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 8px 2px 6px; border: 1px dashed #d8c9b8; border-radius: 8px; cursor: grab; background: #fffdf8; }
.fp-preset:hover { border-color: #b88c6e; background: rgba(184, 140, 110, 0.08); }
.fp-preset:active { cursor: grabbing; }
.fp-preset-shape { background: rgba(138, 111, 85, 0.35); border: 1px solid #8a6f55; border-radius: 2px; }
.fp-preset-name { font-size: 11px; color: #5c4c3d; }
.fp-opacity-row { display: flex; align-items: center; gap: 8px; margin-top: 4px; }
.fp-opacity-label { font-size: 12px; color: #a89a8a; white-space: nowrap; }
.fp-opacity-row :deep(.el-slider) { flex: 1; }
.fp-side-row { display: flex; align-items: center; justify-content: space-between; gap: 6px; padding: 6px 0; border-bottom: 1px dashed #eee5d8; }
.fp-side-name { font-size: 13px; color: #5c4c3d; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-side-name-editable { cursor: text; }
.fp-side-name-editable:hover { color: #b88c6e; text-decoration: underline; text-underline-offset: 2px; }
.fp-side-rename { flex: 1; min-width: 0; }
.fp-side-furn { padding: 6px 0; border-bottom: 1px dashed #eee5d8; }
.fp-side-furn[draggable="true"] { cursor: grab; }
.fp-side-furn[draggable="true"]:active { cursor: grabbing; }
.fp-side-furn .fp-side-row { padding: 0; border-bottom: none; }
.fp-side-meta { display: flex; align-items: center; gap: 10px; margin-top: 4px; font-size: 11px; color: #a89a8a; }
.fp-side-room { color: #8a7a6a; padding: 1px 8px; border-radius: 8px; background: rgba(138, 111, 85, 0.09); border: 1px solid rgba(138, 111, 85, 0.16); white-space: nowrap; }
.fp-side-note { color: #a89a8a; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fp-side-count { white-space: nowrap; }
.fp-side-icons { display: inline-flex; align-items: center; gap: 2px; }
.fp-side-icons :deep(.el-button) { padding: 5px 6px; }
.fp-side-row :deep(.el-button + .el-button) { margin-left: 4px; }
.fp-side-ok { color: #7aa07a; }
.fp-floors { position: absolute; left: 12px; bottom: 12px; display: flex; flex-direction: column; gap: 4px; z-index: 5; }
.fp-floors.with-side { left: 232px; } /* 避让编辑侧栏(220px),浮在画布左下角而非侧栏面板上 */
.fp-floor { width: 36px; height: 36px; border-radius: 8px; background: rgba(255,255,255,0.92); color: #5c4c3d; display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 13px; font-weight: 600; box-shadow: 0 1px 4px rgba(0,0,0,0.1); transition: background 0.15s, box-shadow 0.15s, transform 0.15s; }
.fp-floor:hover { background: #fff; transform: translateY(-1px); box-shadow: 0 2px 8px rgba(0,0,0,0.16); }
.fp-floor.on { background: #b88c6e; color: #fff; }
.fp-floor.on:hover { background: #a87e60; }
.fp-fit { position: absolute; right: 12px; bottom: 12px; width: 36px; height: 36px; border-radius: 8px; background: rgba(255,255,255,0.92); color: #5c4c3d; display: flex; align-items: center; justify-content: center; cursor: pointer; box-shadow: 0 1px 4px rgba(0,0,0,0.1); transition: background 0.15s, box-shadow 0.15s, transform 0.15s; z-index: 5; }
.fp-fit:hover { background: #fff; transform: translateY(-1px); box-shadow: 0 2px 8px rgba(0,0,0,0.16); }
.fp-fit-ico { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.fp-results { position: absolute; right: 12px; top: 12px; width: 260px; max-height: 60%; overflow-y: auto; background: rgba(255,253,248,0.96); border-radius: 12px; box-shadow: 0 3px 12px rgba(0,0,0,0.12); padding: 10px; z-index: 5; }
.fp-results-title { font-size: 13px; font-weight: 600; color: #5c4c3d; margin-bottom: 8px; }
.fp-result { padding: 6px 8px; border-radius: 8px; cursor: pointer; }
.fp-result:hover { background: rgba(184,140,110,0.1); }
.fp-result.on { background: rgba(184,140,110,0.18); }
.fp-result-name { display: block; font-size: 13px; color: #3a2e22; }
.fp-result-path { display: block; font-size: 11px; color: #a89a8a; margin-top: 2px; }
.fp-list { flex: 1; overflow-y: auto; }
.item-card { position: relative; margin-bottom: 12px; }
.item-card.is-pick { cursor: pointer; }
.item-card.selected { outline: 3px solid var(--color-primary, #b88c6e); outline-offset: -3px; }
.select-count { font-size: 13px; color: var(--color-text-secondary, #909399); margin-right: 8px; }
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
.pick-badge.on { background: #b88c6e; border-color: #b88c6e; }
.fp-batch-hint { font-size: 12px; line-height: 1.6; color: #a89a8a; margin-top: 4px; }
.item-main { display: flex; align-items: center; gap: 8px; }
.item-name { font-size: 16px; font-weight: 600; }
.item-path { color: #909399; font-size: 13px; margin-top: 4px; }
.item-ops { margin-top: 8px; }
.item-image-preview { width: 200px; height: 140px; object-fit: cover; border-radius: 8px; }
@media (max-width: 768px) {
  .fp-search { width: 140px; }
  .fp-sidebar { display: none; }
  .fp-floors.with-side { left: 12px; } /* 移动端侧栏隐藏,楼层切换器回到画布左下角 */
  .fp-edit-btn { display: none; }
  .fp-page { height: calc(100dvh - 120px); }
}
</style>
