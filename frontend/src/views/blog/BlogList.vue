<!-- 博客列表页:封面+标题+标签+浏览数的卡片列表,点击进入详情 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('blog.title') }]" />
    <div class="list-header">
      <h2>{{ $t('blog.title') }}</h2>
      <el-button v-if="userStore.isLoggedIn" type="primary" @click="router.push('/blog/edit')">{{ $t('blog.newPost') }}</el-button>
    </div>
    <div class="blog-layout">
      <aside v-if="categories.length" class="category-side">
        <div class="side-title">{{ $t('blog.category') }}</div>
        <div class="cat-item" :class="{ active: !activeCategory }" @click="setCategory('')">{{ $t('blog.allCategories') }}</div>
        <div
          v-for="c in categories"
          :key="c"
          class="cat-item"
          :class="{ active: activeCategory === c }"
          @click="setCategory(c)"
        >{{ c }}</div>
      </aside>
      <div v-loading="loading" class="blog-main">
        <div v-for="b in list" :key="b.id" class="blog-item card" @click="router.push(`/blog/${b.id}`)">
          <img v-if="b.coverImage" :src="b.coverImage" class="blog-cover" />
          <div class="blog-info">
            <div class="blog-title">{{ b.title }}</div>
            <div class="blog-sub">
              <span v-if="b.category" class="blog-cat">{{ b.category }}</span>
              <span v-if="b.tags" class="blog-tags">
                <span v-for="t in String(b.tags).split(',').filter(Boolean)" :key="t" class="tag">#{{ t }}</span>
              </span>
            </div>
            <div class="blog-meta">{{ b.viewCount }} {{ $t('blog.views') }} · {{ formatDate(b.createdAt) }}</div>
          </div>
        </div>
        <el-empty v-if="!loading && !list.length" :description="userStore.isGuest ? $t('blog.noPublicBlog') : $t('blog.firstHint')" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { blogApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'

const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const categories = ref([])
const activeCategory = ref('')
const loading = ref(false)

const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

const load = async () => {
  loading.value = true
  try {
    const params = { current: 1, size: 20 }
    if (activeCategory.value) params.category = activeCategory.value
    const data = await blogApi.list(params)
    list.value = data.records || []
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    categories.value = await blogApi.categories() || []
  } catch (e) {
    // 忽略
  }
}

const setCategory = (c) => {
  activeCategory.value = c
  load()
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<style scoped>
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.list-header h2 { color: var(--color-primary); }
.blog-layout { display: grid; grid-template-columns: 180px 1fr; gap: 16px; }
.category-side {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 12px;
  height: fit-content;
}
.side-title { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 8px; padding: 0 6px; }
.cat-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text);
  transition: background 0.15s;
}
.cat-item:hover { background: var(--color-bg-2); }
.cat-item.active { background: var(--color-accent); color: #fff; }
.blog-main { min-width: 0; }
.blog-item { display: flex; gap: 14px; margin-bottom: 12px; cursor: pointer; }
.blog-item:hover { box-shadow: 0 6px 18px rgba(31,58,95,0.15); }
.blog-cover { width: 120px; height: 80px; object-fit: cover; border-radius: 8px; flex-shrink: 0; }
.blog-title { font-size: 16px; font-weight: 600; }
.blog-sub { display: flex; gap: 8px; margin-top: 6px; flex-wrap: wrap; align-items: center; }
.blog-cat { background: var(--color-primary); color: #fff; padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.blog-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.blog-tags .tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.blog-meta { margin-top: 8px; font-size: 12px; color: var(--color-text-secondary); }
@media (max-width: 768px) {
  .blog-layout { grid-template-columns: 1fr; }
  .category-side { display: flex; gap: 6px; overflow-x: auto; padding: 8px; }
  .side-title { display: none; }
  .cat-item { white-space: nowrap; }
  .blog-cover { width: 90px; height: 64px; }
}
</style>
