<template>
  <div class="page">
    <el-button text @click="$router.back()">← 返回</el-button>
    <div v-if="blog" class="card detail">
      <h1>{{ blog.title }}</h1>
      <div class="meta">{{ formatDate(blog.createdAt) }} · {{ blog.viewCount }} 次浏览</div>
      <img v-if="blog.coverImage" :src="blog.coverImage" class="cover" />
      <div class="content">{{ blog.content }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { blogApi } from '@/api'

const route = useRoute()
const blog = ref(null)

const formatDate = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')

onMounted(async () => {
  blog.value = await blogApi.detail(route.params.id)
})
</script>

<style scoped>
.detail h1 { color: var(--color-primary); margin-bottom: 8px; }
.meta { font-size: 13px; color: var(--color-text-secondary); margin-bottom: 16px; }
.cover { width: 100%; max-height: 300px; object-fit: cover; border-radius: 8px; margin-bottom: 16px; }
.content { white-space: pre-wrap; line-height: 1.8; font-size: 15px; }
</style>
