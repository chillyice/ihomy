<template>
  <div class="recipe-edit-page" v-loading="loading">
    <Breadcrumb :items="[
      { label: $t('kitchen.title'), to: '/kitchen' },
      { label: isEdit ? $t('kitchen.editRecipe') : $t('kitchen.addRecipe') },
    ]" />

    <div class="form-wrap">
      <h1 class="page-title">{{ isEdit ? $t('kitchen.editRecipe') : $t('kitchen.addRecipe') }}</h1>

      <!-- 基础信息 -->
      <div class="form-section">
        <el-form label-position="top">
          <el-form-item :label="$t('kitchen.name')" required>
            <el-input v-model="form.name" maxlength="100" show-word-limit />
          </el-form-item>

          <div class="form-row">
            <el-form-item :label="$t('kitchen.coverImage')">
              <el-upload :show-file-list="false" :before-upload="(f) => uploadFile(f, 'coverImage')" accept="image/*">
                <img v-if="form.coverImage" :src="form.coverImage" class="cover-preview" />
                <el-button v-else size="small"><el-icon><Plus /></el-icon> {{ $t('kitchen.coverImage') }}</el-button>
              </el-upload>
            </el-form-item>
          </div>

          <div class="form-row-3">
            <el-form-item :label="$t('kitchen.cuisine')">
              <el-select v-model="form.cuisine" placeholder="—" clearable>
                <el-option v-for="c in cuisines" :key="c" :label="dictText($t, 'recipe_cuisine', c)" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('kitchen.category')">
              <el-select v-model="form.category" placeholder="—">
                <el-option v-for="c in categories" :key="c" :label="dictText($t, 'recipe_category', c)" :value="c" />
              </el-select>
            </el-form-item>
            <el-form-item :label="$t('kitchen.flavor')">
              <el-select v-model="form.flavor" placeholder="—" clearable>
                <el-option v-for="f in flavors" :key="f" :label="dictText($t, 'recipe_flavor', f)" :value="f" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item :label="$t('kitchen.description')">
            <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
      </div>

      <!-- 素材 -->
      <div class="form-section">
        <div class="section-header">
          <h2>{{ $t('kitchen.ingredients') }}</h2>
          <el-button size="small" @click="addIngredient">
            <el-icon><Plus /></el-icon> {{ $t('kitchen.addIngredient') }}
          </el-button>
        </div>
        <div v-for="(ing, i) in form.ingredients" :key="i" class="row-item">
          <el-input v-model="ing.name" :placeholder="$t('kitchen.ingredientName')" />
          <el-input v-model="ing.quantity" :placeholder="$t('kitchen.ingredientQty')" style="max-width: 120px" />
          <el-input v-model="ing.unit" :placeholder="$t('kitchen.ingredientUnit')" style="max-width: 80px" />
          <el-button type="danger" circle size="small" @click="form.ingredients.splice(i, 1)">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 设备 -->
      <div class="form-section">
        <div class="section-header">
          <h2>{{ $t('kitchen.equipment') }}</h2>
          <el-button size="small" @click="addEquipment">
            <el-icon><Plus /></el-icon> {{ $t('kitchen.addEquipment') }}
          </el-button>
        </div>
        <div v-for="(eq, i) in form.equipment" :key="i" class="row-item">
          <el-input v-model="eq.name" :placeholder="$t('kitchen.equipmentName')" />
          <el-button type="danger" circle size="small" @click="form.equipment.splice(i, 1)">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 步骤 -->
      <div class="form-section">
        <div class="section-header">
          <h2>{{ $t('kitchen.steps') }}</h2>
          <el-button size="small" @click="addStep">
            <el-icon><Plus /></el-icon> {{ $t('kitchen.addStep') }}
          </el-button>
        </div>
        <div v-for="(st, i) in form.steps" :key="i" class="step-item-edit">
          <div class="step-num">{{ i + 1 }}</div>
          <div class="step-body-edit">
            <el-input v-model="st.content" type="textarea" :rows="2" :placeholder="$t('kitchen.stepContent')" />
            <div class="step-media-edit">
              <div class="media-up">
                <div class="media-label">{{ $t('kitchen.stepImage') }}</div>
                <el-upload :show-file-list="false" :before-upload="(f) => uploadStepFile(f, i, 'image_url')" accept="image/*">
                  <img v-if="st.image_url" :src="st.image_url" class="step-preview" />
                  <el-button v-else size="small">+ {{ $t('kitchen.stepImage') }}</el-button>
                </el-upload>
              </div>
              <div class="media-up">
                <div class="media-label">{{ $t('kitchen.stepVideo') }}</div>
                <el-upload :show-file-list="false" :before-upload="(f) => uploadStepFile(f, i, 'video_url')" accept="video/*">
                  <video v-if="st.video_url" :src="st.video_url" class="step-preview" />
                  <el-button v-else size="small">+ {{ $t('kitchen.stepVideo') }}</el-button>
                </el-upload>
              </div>
            </div>
          </div>
          <el-button type="danger" circle size="small" @click="form.steps.splice(i, 1)">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 提交 -->
      <div class="form-actions">
        <el-button @click="$router.back()">{{ $t('kitchen.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('kitchen.save') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Close } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { kitchenApi, fileApi } from '@/api'
import { dictText } from '@/utils/dict'

const { t: $t } = useI18n()

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const saving = ref(false)

const cuisines = ['CHUAN', 'YUE', 'LU', 'SU', 'ZHE', 'MIN', 'XIANG', 'HUI', 'OTHER']
const categories = ['HOT', 'HARD', 'COLD', 'STAPLE', 'PORRIDGE', 'DESSERT']
const flavors = ['SAVORY', 'SPICY', 'SWEET_SOUR', 'LIGHT', 'OTHER']

const form = reactive({
  name: '',
  coverImage: '',
  cuisine: 'OTHER',
  category: 'HOT',
  flavor: '',
  description: '',
  ingredients: [],
  equipment: [],
  steps: [],
})

const addIngredient = () => form.ingredients.push({ name: '', quantity: '', unit: '' })
const addEquipment = () => form.equipment.push({ name: '' })
const addStep = () => form.steps.push({ content: '', image_url: '', video_url: '' })

// 上传封面:返回 false 阻止 el-upload 默认提交
const uploadFile = async (file, field) => {
  const data = await fileApi.upload(file)
  form[field] = data.url
  return false
}

// 上传步骤媒体
const uploadStepFile = async (file, idx, field) => {
  const data = await fileApi.upload(file)
  if (form.steps[idx]) form.steps[idx][field] = data.url
  return false
}

const load = async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const r = await kitchenApi.detail(route.params.id)
    form.name = r.name || ''
    form.coverImage = r.coverImage || ''
    form.cuisine = r.cuisine || 'OTHER'
    form.category = r.category || 'HOT'
    form.flavor = r.flavor || ''
    form.description = r.description || ''
    form.ingredients = parseJson(r.ingredients, [])
    form.equipment = parseJson(r.equipment, [])
    form.steps = parseJson(r.steps, [])
  } catch (e) {}
  loading.value = false
}

const parseJson = (s, fallback) => {
  if (!s) return Array.isArray(fallback) ? [...fallback] : fallback
  try { return JSON.parse(s) } catch (e) { return Array.isArray(fallback) ? [...fallback] : fallback }
}

const onSave = async () => {
  if (!form.name.trim()) {
    ElMessage.warning($t('kitchen.nameRequired'))
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name,
      coverImage: form.coverImage,
      cuisine: form.cuisine,
      category: form.category,
      flavor: form.flavor,
      description: form.description,
      ingredients: JSON.stringify(form.ingredients.filter(i => i.name)),
      equipment: JSON.stringify(form.equipment.filter(e => e.name)),
      steps: JSON.stringify(form.steps.filter(s => s.content || s.image_url || s.video_url)),
    }
    if (isEdit.value) {
      await kitchenApi.update(route.params.id, payload)
    } else {
      await kitchenApi.create(payload)
    }
    ElMessage.success($t('common.saved'))
    router.push('/kitchen')
  } catch (e) {}
  saving.value = false
}

onMounted(load)
</script>

<style scoped>
.recipe-edit-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px 40px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin: 20px 0 24px;
  color: var(--text-primary, #303133);
}

.form-section {
  margin-bottom: 32px;
  padding: 20px;
  border-radius: 16px;
  background: var(--el-bg-color, rgba(255,255,255,0.6));
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.15);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.section-header h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary, #303133);
}

.form-row-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.row-item {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.step-item-edit {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  align-items: flex-start;
}
.step-num {
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--el-color-primary, #409eff);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  margin-top: 6px;
}
.step-body-edit {
  flex: 1;
}
.step-media-edit {
  display: flex;
  gap: 16px;
  margin-top: 10px;
}
.media-up {
  flex: 1;
}
.media-label {
  font-size: 12px;
  color: var(--text-secondary, #909399);
  margin-bottom: 4px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}

.cover-preview {
  width: 200px;
  height: 140px;
  object-fit: cover;
  border-radius: 8px;
}

.step-preview {
  max-width: 200px;
  max-height: 140px;
  object-fit: cover;
  border-radius: 8px;
  display: block;
}

@media (max-width: 700px) {
  .form-row-3 { grid-template-columns: 1fr; }
  .step-media-edit { flex-direction: column; }
}

:global(html.dark) .form-section {
  background: rgba(40,44,52,0.6);
  border-color: rgba(255,255,255,0.08);
}
</style>
