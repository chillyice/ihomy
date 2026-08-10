<!-- 全部功能页:按 category 分类展示所有启用模块,承接首页侧栏超出上限的模块入口 -->
<template>
  <div class="page">
    <div class="more-header">
      <h2>{{ $t('more.allModules') }}</h2>
      <el-button text @click="$router.push('/')">{{ $t('common.backHome') }}</el-button>
    </div>

    <div v-for="group in groups" :key="group.category" class="card more-group">
      <div class="group-title">{{ $t('more.cat.' + group.category) }}</div>
      <div class="group-grid">
        <div
          v-for="m in group.modules"
          :key="m.code"
          class="more-module"
          @click="goModule(m)"
        >
          <div class="more-icon">{{ iconFor(m.icon) }}</div>
          <div class="more-name">{{ m.title }}</div>
        </div>
        <el-empty v-if="!group.modules.length" :description="$t('more.noGroup')" :image-size="60" />
      </div>
    </div>
    <el-empty v-if="!modules.length" :description="$t('more.empty')" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()
const modules = ref([])

const categoryMeta = [
  { category: 'content' },
  { category: 'album' },
  { category: 'life' },
  { category: 'social' },
  { category: 'system' },
]

// 模块按预置分类元信息聚合,空分类不展示
const groups = computed(() =>
  categoryMeta
    .map(c => ({ ...c, modules: modules.value.filter(m => m.category === c.category) }))
    .filter(g => g.modules.length)
)

const iconMap = {
  'icon-blog': '📝',
  'icon-diary': '📔',
  'icon-album': '📷',
  'icon-member': '👥',
  'icon-cover': '🖼️',
  'icon-study': '📚',
  'icon-toolbox': '🧰',
  'icon-anniversary': '🎉',
  'icon-tree': '🌳',
  'icon-storage': '🗄️',
  'icon-item': '📦',
}
const iconFor = (icon) => iconMap[icon] || '⭐'

const goModule = (m) => router.push(m.path)

onMounted(async () => {
  try {
    if (userStore.isLoggedIn) {
      const data = await homeApi.getAllModules()
      modules.value = (data || []).filter(m => m.enabled === 1)
    } else {
      const data = await publicApi.getHome()
      modules.value = (data.modules || []).filter(m => m.enabled === 1)
    }
  } catch (e) {
    modules.value = []
  }
})
</script>

<style scoped>
.page { max-width: 960px; margin: 0 auto; padding: 24px; }
.more-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
h2 { color: var(--color-primary); }
.more-group { margin-bottom: 20px; padding: 20px; }
.group-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(31, 58, 95, 0.08);
}
.group-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.more-module {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.more-module:hover {
  background: rgba(46, 116, 181, 0.08);
  transform: translateY(-2px);
}
.more-icon { font-size: 30px; margin-bottom: 6px; }
.more-name { font-size: 14px; color: var(--color-text); }

@media (max-width: 768px) {
  .group-grid { grid-template-columns: repeat(3, 1fr); }
}
</style>
