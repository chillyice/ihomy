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
          <span class="bpc-head-no">{{ tool.no }} / {{ total }}</span>
          <div class="bpc-head-body">
            <h1 class="bpc-head-title">{{ $t(tool.titleKey) }}</h1>
            <p class="bpc-head-desc">{{ $t(tool.descKey) }}</p>
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
import { BIT_POUCH_TOOLS, findBitPouchTool } from '@/tools/bitPouchTools'

// 每个面板独立成 chunk:打开哪个工具才加载哪份代码,目录页不背全部工具的体积。
const PANELS = {
  format: defineAsyncComponent(() => import('./panels/FormatPanel.vue')),
  codec: defineAsyncComponent(() => import('./panels/CodecPanel.vue')),
  base: defineAsyncComponent(() => import('./panels/BasePanel.vue')),
  packet: defineAsyncComponent(() => import('./panels/PacketPanel.vue')),
  subnet: defineAsyncComponent(() => import('./panels/SubnetPanel.vue')),
  hash: defineAsyncComponent(() => import('./panels/HashPanel.vue')),
  generator: defineAsyncComponent(() => import('./panels/GeneratorPanel.vue')),
  timestamp: defineAsyncComponent(() => import('./panels/TimePanel.vue')),
  regex: defineAsyncComponent(() => import('./panels/RegexPanel.vue')),
  text: defineAsyncComponent(() => import('./panels/TextPanel.vue')),
  ops: defineAsyncComponent(() => import('./panels/OpsPanel.vue')),
  jwt: defineAsyncComponent(() => import('./panels/JwtPanel.vue')),
  // 13 起:与 ihomy 工具箱共用的设计与文档工具(同一套内核,独立界面)
  color: defineAsyncComponent(() => import('./panels/ColorPanel.vue')),
  unit: defineAsyncComponent(() => import('./panels/UnitPanel.vue')),
  image: defineAsyncComponent(() => import('./panels/ImagePanel.vue')),
  file: defineAsyncComponent(() => import('./panels/FilePanel.vue')),
  pdf: defineAsyncComponent(() => import('./panels/PdfPanel.vue')),
  url: defineAsyncComponent(() => import('./panels/UrlPanel.vue')),
  uuid: defineAsyncComponent(() => import('./panels/UuidPanel.vue')),
  // 20 起:V0.5 格式与协议工具
  'data-format': defineAsyncComponent(() => import('./panels/DataFormatPanel.vue')),
  'http-message': defineAsyncComponent(() => import('./panels/HttpMessagePanel.vue')),
  'curl-convert': defineAsyncComponent(() => import('./panels/CurlConvertPanel.vue')),
  'ip-range': defineAsyncComponent(() => import('./panels/IpRangePanel.vue')),
  'mqtt-topic': defineAsyncComponent(() => import('./panels/MqttTopicPanel.vue')),
}

const route = useRoute()
const { t } = useI18n()

const total = BIT_POUCH_TOOLS.length
const tool = computed(() => findBitPouchTool(String(route.params.id || '')))
const panel = computed(() => (tool.value ? PANELS[tool.value.id] || null : null))
const docTitle = computed(() => (tool.value ? `bit_pouch · ${t(tool.value.titleKey)}` : ''))
</script>
