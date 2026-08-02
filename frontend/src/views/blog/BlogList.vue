<template>
  <div class="page">
    <div class="list-header">
      <h2>家庭博客</h2>
      <el-button v-if="userStore.isLoggedIn" type="primary" @click="$router.push('/blog/edit')">写博客</el-button>
    </div>
    <div v-loading="loading">
      <div v-for="b in list" :key="b.id" class="blog-item card" @click="$router.push(`/blog/${b.id}`)">
        <img v-if="b.coverImage" :src="b.coverImage" class="blog-cover" />
        <div class="blog-info">
          <div class="blog-title">{{ b.title }}</div>
          <div class="blog-meta">{{ b.viewCount }} 次浏览 · {{ formatDate(b.createdAt) }}</div>
        </div>
      </div>
      <el-empty v-if="!loading && !list.length" :description="userStore.isGuest ? '暂无公开博客' : '还没有博客，去写第一篇吧'" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { blogApi } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const list = ref([])
const loading = ref(false)

const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

const load = async () => {
  loading.value = true
  try {
    const data = await blogApi.list({ current: 1, size: 20 })
    list.value = data.records || []
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.list-header h2 { color: var(--color-primary); }
.blog-item { display: flex; gap: 14px; margin-bottom: 12px; cursor: pointer; }
.blog-item:hover { box-shadow: 0 6px 18px rgba(31,58,95,0.15); }
.blog-cover { width: 120px; height: 80px; object-fit: cover; border-radius: 8px; flex-shrink: 0; }
.blog-title { font-size: 16px; font-weight: 600; }
.blog-meta { margin-top: 8px; font-size: 12px; color: var(--color-text-secondary); }
@media (max-width: 768px) { .blog-cover { width: 90px; height: 64px; } }
</style>
