<template>
  <div class="page">
    <Breadcrumb :items="[
      { label: $t('kitchen.title'), to: '/kitchen' },
      { label: $t('kitchen.ingredients') },
    ]">
      <template #right>
        <el-button v-if="userStore.isLoggedIn" type="primary" round @click="openAdd">
          <el-icon><Plus /></el-icon> {{ $t('kitchen.addIngredient') }}
        </el-button>
      </template>
    </Breadcrumb>

    <!-- 搜索 -->
    <div class="search-bar">
      <el-input v-model="keyword" :placeholder="$t('kitchen.ingredientName')" clearable prefix-icon="Search"
                @input="onSearch" style="max-width: 300px" />
    </div>

    <!-- 横条列表 -->
    <div v-loading="loading" class="ingredient-list">
      <div v-for="item in items" :key="item.id" class="ingredient-bar glass" @click="openEdit(item)">
        <!-- 左半:图片 + 透明渐变 -->
        <div class="bar-image-wrap">
          <img v-if="item.image_url" :src="item.image_url" :alt="item.name" class="bar-image" loading="lazy" />
          <div v-else class="bar-image bar-image-empty">
            <el-icon><Bowl /></el-icon>
          </div>
          <div class="bar-image-overlay" />
        </div>
        <!-- 右半:名称 + 数量 -->
        <div class="bar-info">
          <div class="bar-name">{{ item.name }}</div>
          <div class="bar-quantity">
            <span v-if="item.quantity != null" class="qty-num">{{ item.quantity }}</span>
            <span v-if="item.unit" class="qty-unit">{{ item.unit }}</span>
            <span v-if="item.quantity == null && !item.unit" class="qty-empty">—</span>
          </div>
          <div v-if="item.house_name" class="bar-location">
            <el-icon><Location /></el-icon>
            {{ item.house_name }} {{ item.room_name }} {{ item.furniture_name }}
          </div>
        </div>
        <!-- 操作按钮 -->
        <div class="bar-actions" @click.stop>
          <el-button size="small" circle @click="openEdit(item)"><el-icon><Edit /></el-icon></el-button>
          <el-button size="small" type="danger" circle plain @click="onDelete(item)"><el-icon><Delete /></el-icon></el-button>
        </div>
      </div>

      <el-empty v-if="!loading && !items.length" :description="$t('kitchen.empty')" />
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dlg" :title="form.id ? $t('kitchen.editIngredient') : $t('kitchen.addIngredient')" width="500px">
      <el-form label-position="top">
        <!-- 图片上传 -->
        <el-form-item :label="$t('kitchen.ingredientImage')">
          <el-upload :show-file-list="false" :http-request="onUpload" accept="image/*">
            <img v-if="form.image_url" :src="form.image_url" class="image-preview" />
            <el-button v-else><el-icon><Plus /></el-icon> {{ $t('kitchen.ingredientImage') }}</el-button>
          </el-upload>
        </el-form-item>

        <!-- 名称 -->
        <el-form-item :label="$t('kitchen.ingredientName')" required>
          <el-input v-model="form.name" :placeholder="$t('kitchen.ingredientName')" />
        </el-form-item>

        <!-- 数量 + 单位 -->
        <div class="form-row-2">
          <el-form-item :label="$t('kitchen.ingredientQty')">
            <el-input-number v-model="form.quantity" :min="0" :precision="2" :step="1" :controls="false" style="width: 100%" />
          </el-form-item>
          <el-form-item :label="$t('kitchen.ingredientUnit')">
            <el-select v-model="form.unit" allow-create filterable clearable :placeholder="$t('kitchen.ingredientUnit')" style="width: 100%">
              <el-option v-for="u in units" :key="u" :label="u" :value="u" />
            </el-select>
          </el-form-item>
        </div>

        <!-- 存放位置(三级级联) -->
        <el-form-item :label="$t('kitchen.ingredientLocation')">
          <el-cascader
            v-model="form.locationPath"
            :options="locationTree"
            :props="{ expandTrigger: 'hover', emitPath: true, checkStrictly: true }"
            :placeholder="$t('kitchen.ingredientLocationPh')"
            clearable
            style="width: 100%"
          />
        </el-form-item>

        <!-- 备注 -->
        <el-form-item :label="$t('kitchen.stepContent')">
          <el-input v-model="form.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">{{ $t('kitchen.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('kitchen.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Bowl, Edit, Delete, Location, Search } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { itemApi, fileApi } from '@/api'
import { useUserStore } from '@/stores/user'

const { t: $t } = useI18n()
const userStore = useUserStore()
const items = ref([])
const loading = ref(false)
const keyword = ref('')
const dlg = ref(false)
const saving = ref(false)

const units = ['个', '斤', '瓶', '袋', '克', '千克', '升', '毫升', '把', '根', '盒', '包']

const form = reactive({
  id: null,
  name: '',
  image_url: '',
  quantity: null,
  unit: '',
  furnitureId: null,
  locationPath: [],
  note: '',
})

// 存放位置树(house > room > furniture)
const houses = ref([])
const rooms = ref([])
const furnitures = ref([])

const locationTree = computed(() => {
  return houses.value.map(h => ({
    value: h.id,
    label: h.name,
    children: rooms.value.filter(r => r.houseId === h.id).map(r => ({
      value: r.id,
      label: r.name,
      children: furnitures.value.filter(f => f.roomId === r.id).map(f => ({
        value: f.id,
        label: f.name,
      })),
    })),
  }))
})

const onSearch = () => { loadList() }

const loadList = async () => {
  loading.value = true
  try {
    items.value = await itemApi.list({ type: 'INGREDIENT', keyword: keyword.value || undefined })
  } catch (e) {}
  loading.value = false
}

const loadLocations = async () => {
  try {
    houses.value = await itemApi.houses()
    // 批量拉所有 room 和 furniture
    const roomPromises = houses.value.map(h => itemApi.rooms(h.id))
    const roomResults = await Promise.all(roomPromises)
    rooms.value = roomResults.flat()
    const furnPromises = rooms.value.map(r => itemApi.furnitures(r.id))
    const furnResults = await Promise.all(furnPromises)
    furnitures.value = furnResults.flat()
  } catch (e) {}
}

// 默认选中"厨房"相关位置
const defaultLocation = () => {
  // 找名称含"厨房"的 room
  const kitchenRoom = rooms.value.find(r => r.name && r.name.includes('厨房'))
  if (kitchenRoom) {
    const house = houses.value.find(h => h.id === kitchenRoom.houseId)
    const furn = furnitures.value.find(f => f.roomId === kitchenRoom.id)
    if (house && furn) {
      return [house.id, kitchenRoom.id, furn.id]
    }
    // 有厨房 room 但没有 furniture,返回到 room 级
    if (house) return [house.id, kitchenRoom.id]
  }
  return []
}

const openAdd = () => {
  Object.assign(form, {
    id: null, name: '', image_url: '', quantity: null, unit: '',
    furnitureId: null, locationPath: defaultLocation(), note: '',
  })
  dlg.value = true
}

const openEdit = (item) => {
  Object.assign(form, {
    id: item.id, name: item.name, image_url: item.image_url || '',
    quantity: item.quantity != null ? Number(item.quantity) : null,
    unit: item.unit || '', furnitureId: item.furniture_id || null,
    locationPath: [], note: item.note || '',
  })
  // 反查级联路径
  if (item.furniture_id && item.room_id && item.house_id) {
    form.locationPath = [item.house_id, item.room_id, item.furniture_id]
  }
  dlg.value = true
}

const onUpload = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    form.image_url = data.url
  } catch (e) {}
}

const onSave = async () => {
  if (!form.name.trim()) {
    ElMessage.warning($t('kitchen.nameRequired'))
    return
  }
  saving.value = true
  try {
    // 级联取最后一级作为 furnitureId(仅 3 级路径时才有 furniture)
    const furnId = form.locationPath && form.locationPath.length === 3
      ? form.locationPath[2] : null
    const body = {
      name: form.name,
      imageUrl: form.image_url,
      type: 'INGREDIENT',
      quantity: form.quantity,
      unit: form.unit,
      furnitureId: furnId,
      note: form.note,
    }
    if (form.id) {
      await itemApi.update(form.id, body)
    } else {
      await itemApi.create(body)
    }
    ElMessage.success($t('common.saved'))
    dlg.value = false
    loadList()
  } catch (e) {}
  saving.value = false
}

const onDelete = async (item) => {
  try {
    await ElMessageBox.confirm($t('kitchen.deleteConfirm'), { type: 'warning' })
    await itemApi.remove(item.id)
    ElMessage.success($t('common.deleted'))
    loadList()
  } catch (e) {}
}

onMounted(() => {
  loadLocations()
  loadList()
})
</script>

<style scoped>
.search-bar {
  margin-bottom: 16px;
}

.ingredient-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ingredient-bar {
  display: flex;
  align-items: center;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.3s ease;
  background: var(--el-bg-color, rgba(255,255,255,0.6));
  backdrop-filter: blur(24px) saturate(1.1);
  -webkit-backdrop-filter: blur(24px) saturate(1.1);
  border: 1px solid rgba(255,255,255,0.2);
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
  height: 84px;
}
.ingredient-bar:hover { transform: translateX(4px); }

/* 左侧:图片占条目宽度 1/3,居中裁切 */
.bar-image-wrap {
  flex: 1 0 33.33%;
  max-width: 33.33%;
  height: 100%;
  position: relative;
  overflow: hidden;
}
.bar-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  display: block;
}
.bar-image-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 24px;
  color: var(--el-color-primary-light-5, #a0cfff);
  background: var(--el-fill-color-light, #f5f7fa);
}
.bar-image-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to right, transparent 0%, transparent 50%, var(--el-bg-color, rgba(255,255,255,0.9)) 100%);
  pointer-events: none;
}

/* 右侧:名称 + 数量 + 位置 */
.bar-info {
  flex: 1;
  padding: 8px 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}
.bar-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary, #303133);
}
.bar-quantity {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.qty-num {
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-primary, #409eff);
}
.qty-unit {
  font-size: 12px;
  color: var(--text-secondary, #909399);
}
.qty-empty {
  font-size: 12px;
  color: var(--text-placeholder, #c0c4cc);
}
.bar-location {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--text-secondary, #909399);
}

.bar-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  opacity: 0;
  transition: opacity 0.2s;
}
.ingredient-bar:hover .bar-actions { opacity: 1; }

/* 编辑弹窗 */
.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.image-preview {
  width: 200px;
  height: 140px;
  object-fit: cover;
  border-radius: 8px;
}

:global(html.dark) .ingredient-bar {
  background: rgba(40,44,52,0.6);
  border-color: rgba(255,255,255,0.08);
}
:global(html.dark) .bar-image-overlay {
  background: linear-gradient(to right, transparent 0%, transparent 40%, rgba(40,44,52,0.9) 100%);
}

@media (max-width: 600px) {
  .form-row-2 { grid-template-columns: 1fr; }
  .bar-image-wrap { flex: 0 0 35%; }
  .bar-info { padding: 10px 14px; }
  .bar-name { font-size: 16px; }
}
</style>
