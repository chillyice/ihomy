<template>
  <div class="page" v-loading="loading">
    <Breadcrumb :items="[{ label: $t('kitchen.title'), to: '/kitchen' }, { label: $t('kitchen.recipeDetail') }]" />

    <div v-if="recipe" class="recipe-content">
      <!-- 顶部:封面 + 基础信息 -->
      <div class="recipe-header">
        <div class="cover-wrap">
          <img v-if="recipe.coverImage" :src="recipe.coverImage" :alt="recipe.name" class="cover" />
          <div v-else class="cover cover-empty"><el-icon><Bowl /></el-icon></div>
        </div>
        <div class="info">
          <span class="name">{{ recipe.name }}</span>
          <div class="meta-row">
            <span class="meta-item">
              <span class="meta-label">{{ $t('kitchen.cuisine') }}:</span>
              {{ dictText($t, 'recipe_cuisine', recipe.cuisine) }}
            </span>
            <span class="meta-item">
              <span class="meta-label">{{ $t('kitchen.category') }}:</span>
              {{ dictText($t, 'recipe_category', recipe.category) }}
            </span>
            <span v-if="recipe.flavor" class="meta-item">
              <span class="meta-label">{{ $t('kitchen.flavor') }}:</span>
              {{ dictText($t, 'recipe_flavor', recipe.flavor) }}
            </span>
          </div>
          <p v-if="recipe.description" class="desc">{{ recipe.description }}</p>
          <div v-if="recipe.authorName" class="author">
            <el-icon><User /></el-icon>
            {{ $t('kitchen.author') }}: {{ recipe.authorName }}
          </div>
          <div v-if="canEdit" class="actions">
            <el-button size="small" @click="$router.push(`/kitchen/recipe/${recipe.id}/edit`)">
              <el-icon><Edit /></el-icon> {{ $t('kitchen.edit') }}
            </el-button>
            <el-button size="small" type="danger" plain @click="onDelete">
              <el-icon><Delete /></el-icon> {{ $t('kitchen.delete') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 三段:素材 / 设备 / 步骤 -->
      <div class="sections">
        <!-- 素材 -->
        <section v-if="ingredients.length" class="block">
          <h2 class="block-title">
            <el-icon><Goods /></el-icon> {{ $t('kitchen.ingredients') }}
          </h2>
          <div class="ingredient-list">
            <div v-for="(ing, i) in ingredients" :key="i" class="ingredient-item glass">
              <span class="ing-name">{{ ing.name }}</span>
              <span v-if="ing.quantity" class="ing-qty">{{ ing.quantity }}{{ ing.unit || '' }}</span>
            </div>
          </div>
        </section>

        <!-- 设备 -->
        <section v-if="equipment.length" class="block">
          <h2 class="block-title">
            <el-icon><Box /></el-icon> {{ $t('kitchen.equipment') }}
          </h2>
          <div class="equipment-list">
            <div v-for="(eq, i) in equipment" :key="i" class="equipment-item glass">
              <el-icon><Cpu /></el-icon>
              <span>{{ eq.name }}</span>
            </div>
          </div>
        </section>

        <!-- 步骤 -->
        <section v-if="steps.length" class="block">
          <h2 class="block-title">
            <el-icon><List /></el-icon> {{ $t('kitchen.steps') }}
          </h2>
          <div class="step-list">
            <div v-for="(st, i) in steps" :key="i" class="step-item">
              <div class="step-num">{{ i + 1 }}</div>
              <div class="step-body glass">
                <p v-if="st.content" class="step-content">{{ st.content }}</p>
                <img v-if="st.image_url" :src="st.image_url" class="step-media step-image" loading="lazy" />
                <video v-if="st.video_url" :src="st.video_url" class="step-media step-video" controls />
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>

    <el-empty v-if="!loading && !recipe" :description="$t('kitchen.notFound')" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Bowl, User, Edit, Delete, Goods, Box, Cpu, List } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { kitchenApi } from '@/api'
import { dictText } from '@/utils/dict'
import { useUserStore } from '@/stores/user'

const { t: $t } = useI18n()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const recipe = ref(null)
const loading = ref(false)

const parseJson = (s, fallback) => {
  if (!s) return fallback
  try { return JSON.parse(s) } catch (e) { return fallback }
}

const ingredients = computed(() => parseJson(recipe.value?.ingredients, []))
const equipment = computed(() => parseJson(recipe.value?.equipment, []))
const steps = computed(() => parseJson(recipe.value?.steps, []))

const canEdit = computed(() => {
  if (!recipe.value || !userStore.isLoggedIn) return false
  return userStore.isOwner || recipe.value.authorId === userStore.user?.id
})

const load = async () => {
  loading.value = true
  try {
    recipe.value = await kitchenApi.detail(route.params.id)
  } catch (e) {
    recipe.value = null
  }
  loading.value = false
}

const onDelete = async () => {
  try {
    await ElMessageBox.confirm($t('kitchen.deleteConfirm'), { type: 'warning' })
    await kitchenApi.remove(recipe.value.id)
    ElMessage.success($t('common.deleted'))
    router.push('/kitchen')
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped>
.recipe-header {
  display: flex;
  gap: 24px;
  margin: 20px 0 32px;
}

.cover-wrap {
  flex: 0 0 280px;
}
.cover {
  width: 100%;
  height: 220px;
  object-fit: cover;
  border-radius: 16px;
}
.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60px;
  color: var(--el-color-primary-light-5, #a0cfff);
  background: var(--el-fill-color-light, #f5f7fa);
}

.info {
  flex: 1;
}
.name {
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--text-primary, #303133);
}
.meta-row {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.meta-item {
  font-size: 14px;
  color: var(--text-primary, #606266);
}
.meta-label {
  color: var(--text-secondary, #909399);
  margin-right: 4px;
}
.desc {
  font-size: 14px;
  color: var(--text-regular, #606266);
  line-height: 1.6;
  margin: 0 0 12px;
}
.author {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary, #909399);
  margin-bottom: 12px;
}
.actions {
  display: flex;
  gap: 8px;
}

.sections { margin-top: 16px; }
.block { margin-bottom: 32px; }
.block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 14px;
  color: var(--text-primary, #303133);
}

.ingredient-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}
.ingredient-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  background: var(--el-bg-color, rgba(255,255,255,0.6));
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.15);
}
.ing-name { color: var(--text-primary, #303133); font-weight: 500; }
.ing-qty { color: var(--text-secondary, #909399); }

.equipment-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.equipment-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 10px;
  font-size: 14px;
  background: var(--el-bg-color, rgba(255,255,255,0.6));
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.15);
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.step-item {
  display: flex;
  gap: 14px;
}
.step-num {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--el-color-primary, #409eff);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}
.step-body {
  flex: 1;
  padding: 14px 18px;
  border-radius: 12px;
  background: var(--el-bg-color, rgba(255,255,255,0.6));
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.15);
}
.step-content {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-primary, #303133);
}
.step-media {
  max-width: 100%;
  border-radius: 8px;
  display: block;
  margin-top: 8px;
}
.step-image { max-height: 400px; object-fit: cover; }
.step-video { max-height: 400px; }

.glass {
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

@media (max-width: 700px) {
  .recipe-header { flex-direction: column; }
  .cover-wrap { flex: 0 0 auto; }
  .cover { height: 200px; }
}

:global(html.dark) .ingredient-item,
:global(html.dark) .equipment-item,
:global(html.dark) .step-body {
  background: rgba(40,44,52,0.6);
  border-color: rgba(255,255,255,0.08);
}
</style>
