<!-- 首页右侧半隐藏标签:平时只露出窄条,鼠标悬停拉出并放大,点击展开完整内容面板(每日一图/每日知识) -->
<template>
  <div class="side-tabs">
    <div
      v-for="tab in tabs"
      :key="tab.key"
      class="side-tab"
      :class="{ expanded: hovered === tab.key, open: showKey === tab.key }"
      @mouseenter="hovered = tab.key"
      @mouseleave="hovered = null"
      @click="openTab(tab.key)"
    >
      <span class="tab-icon">{{ tab.icon }}</span>
      <span v-if="hovered === tab.key" class="tab-label">{{ $t(tab.labelKey) }}</span>
    </div>

    <!-- 每日一图面板 -->
    <el-drawer v-model="showImage" :title="$t('daily.imageTitle')" size="520px" :z-index="400">
      <div v-if="image" class="daily-image-wrap">
        <el-image
          :src="image.url"
          :preview-src-list="[image.url]"
          :alt="$t('daily.imageTitle')"
          fit="contain"
          hide-on-click-modal
          preview-teleported
          class="daily-image"
        />
        <div class="daily-caption">{{ image.copyright }}</div>
      </div>
      <el-empty v-else :description="$t('daily.imageError')" :image-size="70" />
    </el-drawer>

    <!-- 每日知识面板 -->
    <el-drawer v-model="showKnowledge" :title="$t('daily.knowledgeTitle')" size="420px" :z-index="400">
      <div class="knowledge-card">
        <p class="knowledge-content">{{ knowledge || $t('daily.loading') }}</p>
        <div class="knowledge-actions">
          <el-button text @click="loadKnowledge">{{ $t('daily.next') }}</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { dailyApi } from '@/api'

const ALL_TABS = [
  { key: 'image', icon: '🖼️', labelKey: 'daily.imageTab' },
  { key: 'knowledge', icon: '📚', labelKey: 'daily.knowledgeTab' },
]

// 按设置页开关(ihomy-daily)过滤显示的标签
const kv = () => JSON.parse(localStorage.getItem('ihomy-daily') || '{}')
const tabs = ALL_TABS.filter((t) => kv()[t.key + 'On'] !== false)

const hovered = ref(null)
const showKey = ref(null)
const showImage = ref(false)
const showKnowledge = ref(false)
const image = ref(null)
const knowledge = ref({ content: '' })

// 知识分类:读设置页保存的偏好(ihomy-daily),默认历史+生活
const types = () => (kv().types || ['history', 'life']).join(',')

const openTab = (key) => {
  showKey.value = key
  if (key === 'image') {
    showImage.value = true
    if (!image.value) loadImage()
  } else {
    showKnowledge.value = true
    if (!knowledge.value.content) loadKnowledge()
  }
}

// 必应每日一图:失败显示空态不打扰
const loadImage = async () => {
  try {
    image.value = await dailyApi.image()
  } catch (e) {
    image.value = null
  }
}

const loadKnowledge = async () => {
  try {
    knowledge.value = await dailyApi.knowledge(types())
  } catch (e) {
    knowledge.value = { content: '' }
  }
}

// drawer 关闭时清理 side-tab 的 .open 高亮态
watch(showImage, (v) => { if (!v) showKey.value = null })
watch(showKnowledge, (v) => { if (!v) showKey.value = null })
</script>

<style scoped>
.side-tabs {
  position: fixed;
  right: 6px;
  top: 45%;
  transform: translateY(-50%);
  z-index: 90;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.side-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 34px;
  height: 44px;
  padding: 0 6px;
  border-radius: 10px 6px 6px 10px;
  background: var(--color-card);
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.18);
  cursor: pointer;
  transition: width 0.25s ease, background 0.2s, transform 0.2s;
  overflow: hidden;
  color: var(--color-text);
}
.side-tab:hover,
.side-tab.open {
  width: 92px;
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  color: #fff;
  transform: scale(1.08);
}
.tab-icon { font-size: 18px; flex-shrink: 0; }
.tab-label {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.daily-image-wrap { text-align: center; }
.daily-image {
  width: 100%;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}
.daily-caption {
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  text-align: left;
}
.knowledge-card {
  padding: 4px 2px;
}
.knowledge-card p {
  font-size: 16px;
  line-height: 1.9;
  color: var(--color-text);
  min-height: 120px;
}
.knowledge-actions {
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding-top: 12px;
  margin-top: 12px;
}
</style>