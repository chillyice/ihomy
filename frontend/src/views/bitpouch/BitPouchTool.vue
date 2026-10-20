<!-- bit_pouch 单个工具的工作台:左输入右结果,面板按工具 id 异步加载(每种工具一个 chunk)。
     工具目录与编号来自 src/tools/bitPouchTools.js,名称/描述沿用工具箱 i18n 文案。 -->
<template>
  <BpShell :doc-title="docTitle">
    <main class="bpc">
      <div class="bpc-crumb">
        <router-link class="bpc-link" to="/bit-pouch/tools">← {{ $t('bitPouch.tools.backToTools') }}</router-link>
      </div>

      <template v-if="tool">
        <section class="bpc-head">
          <span class="bpc-head-no">{{ tool.no }} / 12</span>
          <div class="bpc-head-body">
            <h1 class="bpc-head-title">{{ $t(tool.titleKey) }}</h1>
            <p class="bpc-head-desc">{{ $t(tool.descKey) }}</p>
          </div>
          <div class="bpc-head-meta">
            <span class="bpc-tag">{{ $t('bitPouch.tools.localOnly') }}</span>
          </div>
        </section>

        <component :is="panel" v-if="panel" :key="tool.id" />
        <p v-else class="bpc-hint">{{ $t('bitPouch.tools.notFound') }}</p>
      </template>

      <template v-else>
        <section class="bpc-head">
          <div class="bpc-head-body">
            <h1 class="bpc-head-title">{{ $t('bitPouch.tools.notFound') }}</h1>
            <p class="bpc-head-desc">{{ $t('bitPouch.tools.notFoundHint') }}</p>
          </div>
        </section>
      </template>
    </main>
  </BpShell>
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import BpShell from './BpShell.vue'
import { findBitPouchTool } from '@/tools/bitPouchTools'

// 每个面板独立成 chunk:打开哪个工具才加载哪份代码,目录页不背全部工具的体积。
const PANELS = {
  json: defineAsyncComponent(() => import('./panels/JsonPanel.vue')),
  codec: defineAsyncComponent(() => import('./panels/CodecPanel.vue')),
  base: defineAsyncComponent(() => import('./panels/BasePanel.vue')),
  packet: defineAsyncComponent(() => import('./panels/PacketPanel.vue')),
  subnet: defineAsyncComponent(() => import('./panels/SubnetPanel.vue')),
  hash: defineAsyncComponent(() => import('./panels/HashPanel.vue')),
  generator: defineAsyncComponent(() => import('./panels/GeneratorPanel.vue')),
  time: defineAsyncComponent(() => import('./panels/TimePanel.vue')),
  regex: defineAsyncComponent(() => import('./panels/RegexPanel.vue')),
  text: defineAsyncComponent(() => import('./panels/TextPanel.vue')),
  ops: defineAsyncComponent(() => import('./panels/OpsPanel.vue')),
  jwt: defineAsyncComponent(() => import('./panels/JwtPanel.vue')),
}

const route = useRoute()
const { t } = useI18n()

const tool = computed(() => findBitPouchTool(String(route.params.id || '')))
const panel = computed(() => (tool.value ? PANELS[tool.value.id] || null : null))
const docTitle = computed(() => (tool.value ? `bit_pouch · ${t(tool.value.titleKey)}` : ''))
</script>
