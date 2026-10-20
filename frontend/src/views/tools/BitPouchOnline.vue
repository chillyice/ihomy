<!-- 比特口袋 · 在线工具:工具箱里所有开发者工具的统一入口(卡片点击进入对应 ihomy 工具页)。
     同一批工具也在 bit_pouch 桌面版离线可用,故发布页与本页互相跳转。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title'), to: '/tools' }, { label: $t('bitPouchOnline.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="bp-online-title">{{ $t('bitPouchOnline.title') }}</span>
        <span class="bp-online-count">{{ $t('bitPouchOnline.count', { n: devTools.length }) }}</span>
      </div>
      <div class="tb-right">
        <el-button size="small" @click="router.push('/tools')">{{ $t('bitPouchOnline.backToTools') }}</el-button>
        <el-button size="small" type="primary" @click="router.push('/bit-pouch')">
          {{ $t('bitPouchOnline.getDesktop') }}
        </el-button>
      </div>
    </PageToolbar>

    <div class="card bp-online-intro">
      <p class="bp-online-copy">{{ $t('bitPouchOnline.intro') }}</p>
      <p class="bp-online-privacy">{{ $t('bitPouchOnline.privacy') }}</p>
    </div>

    <div class="bp-online-grid">
      <div v-for="tool in devTools" :key="tool.id" v-a11y-click class="bp-online-card card" @click="open(tool)">
        <div class="bp-online-icon">
          <el-icon :size="26"><component :is="iconFor(tool.icon)" /></el-icon>
        </div>
        <div class="bp-online-name">
          <span>{{ $t(tool.titleKey) }}</span>
          <span class="bp-online-tag">{{ $t(tool.tagKey) }}</span>
        </div>
        <div class="bp-online-desc">{{ $t(tool.descKey) }}</div>
        <span class="bp-online-enter"
          >{{ $t('tools.enter') }}<el-icon><ArrowRight /></el-icon
        ></span>
      </div>
    </div>

    <div class="card bp-online-foot">
      <span>{{ $t('bitPouchOnline.desktopHint') }}</span>
      <el-button size="small" text @click="router.push('/bit-pouch')">{{ $t('bitPouchOnline.desktopLink') }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { TOOL_REGISTRY } from '@/tools/registry'
import { iconFor } from '@/tools/icons'

const router = useRouter()

// 比特口袋收录的开发者工具 = dev 分类里 bitPouch.status 不是 out-of-scope 的条目(共 12 个)。
const devTools = computed(() =>
  TOOL_REGISTRY.filter((tool) => tool.category === 'dev' && tool.bitPouch?.status !== 'out-of-scope').sort((a, b) => a.order - b.order),
)

const open = (tool) => router.push(tool.path)
</script>

<style scoped>
.bp-online-title {
  font-size: 15px;
  font-weight: 600;
}
.bp-online-count {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.bp-online-intro {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.bp-online-copy {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
}
.bp-online-privacy {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-secondary);
}
.bp-online-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.bp-online-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease;
  contain: layout style;
}
.bp-online-card:hover {
  transform: translateY(-3px);
}
.bp-online-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  border-radius: 12px;
  background: var(--color-accent-weak, rgba(184, 140, 110, 0.14));
  color: var(--color-accent, var(--color-brand));
}
.bp-online-name {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
  font-size: 15px;
  font-weight: 600;
}
.bp-online-tag {
  flex-shrink: 0;
  padding: 2px 7px;
  border: 1px solid rgba(168, 72, 58, 0.18);
  border-radius: 999px;
  background: rgba(168, 72, 58, 0.1);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.5;
}
html.dark .bp-online-tag {
  border-color: rgba(225, 154, 140, 0.2);
  background: rgba(225, 154, 140, 0.12);
  color: #e19a8c;
}
.bp-online-desc {
  min-height: 39px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-secondary, #8a8a8a);
}
.bp-online-enter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--color-accent, var(--color-brand));
}
.bp-online-foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
