<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('kitchen.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-left">
        <el-button v-if="userStore.isLoggedIn" @click="$router.push('/kitchen/ingredients')">
          <el-icon><Bowl /></el-icon>
          {{ $t('kitchen.ingredients') }}
        </el-button>
      </div>
      <div class="tb-right">
        <el-button v-if="userStore.isLoggedIn" type="primary" @click="$router.push('/kitchen/recipe/new')">
          <el-icon><Plus /></el-icon>
          {{ $t('kitchen.addRecipe') }}
        </el-button>
      </div>
    </div>

    <div v-loading="loading">
      <!-- 今日推荐 -->
      <section v-if="recommend.length" class="recommend-section">
        <h2 class="section-title">
          <el-icon><Sunny /></el-icon>
          {{ $t('kitchen.todayRecommend') }}
        </h2>
        <div class="recommend-row">
          <div
            v-for="r in recommend"
            :key="r.id"
            class="recommend-card card"
            @click="goDetail(r.id)"
          >
            <img v-if="r.coverImage" :src="r.coverImage" :alt="r.name" class="recommend-img" />
            <div v-else class="recommend-img recommend-img-empty">
              <el-icon><Bowl /></el-icon>
            </div>
            <div class="recommend-name">{{ r.name }}</div>
          </div>
        </div>
      </section>

      <!-- 按类别分组的菜单 -->
      <section v-for="g in groups" :key="g.category" class="menu-section">
        <h2 class="section-title">
          <span class="title-tag">{{ dictText($t, 'recipe_category', g.category) }}</span>
          <span class="title-count">{{ g.items.length }}</span>
        </h2>
        <div class="menu-grid">
          <div
            v-for="r in g.items"
            :key="r.id"
            class="menu-card card"
            :style="{ '--card-h': cardHeight(r) }"
            @click="goDetail(r.id)"
          >
            <img v-if="r.coverImage" :src="r.coverImage" :alt="r.name" class="menu-img" loading="lazy" />
            <div v-else class="menu-img menu-img-empty">
              <el-icon><Bowl /></el-icon>
            </div>
            <div class="menu-info">
              <div class="menu-name">{{ r.name }}</div>
              <div class="menu-meta">
                <span v-if="r.cuisine" class="meta-tag">{{ dictText($t, 'recipe_cuisine', r.cuisine) }}</span>
                <span v-if="r.flavor" class="meta-tag meta-flavor">{{ dictText($t, 'recipe_flavor', r.flavor) }}</span>
              </div>
              <div v-if="r.authorName" class="menu-author">{{ r.authorName }}</div>
            </div>
          </div>
        </div>
      </section>

      <el-empty v-if="!groups.length && !recommend.length" :description="$t('kitchen.empty')" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Sunny, Bowl } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { kitchenApi } from '@/api'
import { dictText } from '@/utils/dict'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const groups = ref([])
const recommend = ref([])
const loading = ref(false)

const cardHeight = (r) => {
  const base = r.coverImage ? 260 : 220
  return base + (r.id % 3) * 20 + 'px'
}

const goDetail = (id) => router.push(`/kitchen/recipe/${id}`)

const loadMenu = async () => {
  loading.value = true
  try {
    const data = await kitchenApi.menu()
    groups.value = data.groups || []
    recommend.value = data.todayRecommend || []
  } finally {
    loading.value = false
  }
}

onMounted(loadMenu)
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 600;
  margin: 32px 0 16px;
  color: var(--text-primary, #303133);
}

.title-tag {
  display: inline-block;
  padding: 4px 14px;
  border-radius: 14px;
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}

.title-count {
  font-size: 14px;
  color: var(--text-secondary, #909399);
  font-weight: normal;
}

.recommend-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.recommend-card {
  flex: 1 1 200px;
  min-width: 200px;
  max-width: 280px;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
}
.recommend-card:hover { transform: translateY(-4px); }
.recommend-img {
  width: 100%;
  height: 140px;
  object-fit: cover;
  display: block;
}
.recommend-img-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40px;
  color: var(--el-color-primary-light-5, #a0cfff);
  background: var(--el-fill-color-light, #f5f7fa);
}
.recommend-name {
  padding: 10px 14px;
  font-size: 15px;
  font-weight: 500;
  text-align: center;
  color: var(--text-primary, #303133);
}

.menu-grid {
  columns: 5 220px;
  column-gap: 16px;
}

.menu-card {
  break-inside: avoid;
  margin-bottom: 16px;
  padding: 0;
  overflow: hidden;
  cursor: pointer;
  height: var(--card-h, 240px);
  display: flex;
  flex-direction: column;
}
.menu-card:hover { transform: translateY(-4px); }
.menu-img {
  width: 100%;
  flex: 1;
  object-fit: cover;
  display: block;
  min-height: 0;
}
.menu-img-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  color: var(--el-color-primary-light-5, #a0cfff);
  background: var(--el-fill-color-light, #f5f7fa);
}
.menu-info {
  padding: 10px 12px;
  background: var(--el-bg-color, rgba(255,255,255,0.85));
}
.menu-name {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--text-primary, #303133);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.menu-meta {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.meta-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 8px;
  background: var(--el-fill-color, #f0f2f5);
  color: var(--text-secondary, #606266);
}
.meta-flavor {
  background: var(--el-color-warning-light-9, #fdf6ec);
  color: var(--el-color-warning, #e6a23c);
}
.menu-author {
  font-size: 12px;
  color: var(--text-secondary, #909399);
}

@media (max-width: 1200px) {
  .menu-grid { columns: 4 200px; }
}
@media (max-width: 900px) {
  .menu-grid { columns: 3 180px; }
  .recommend-card { max-width: 200px; }
}
@media (max-width: 600px) {
  .menu-grid { columns: 2 150px; }
  .recommend-card { flex: 1 1 140px; min-width: 140px; }
}

:global(html.dark) .menu-info {
  background: rgba(40,44,52,0.85);
}
</style>
