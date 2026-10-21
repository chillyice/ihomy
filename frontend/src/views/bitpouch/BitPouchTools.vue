<!-- bit_pouch 在线工具目录:24 个工具的入口,与 bit_pouch 打包的内置工具一一对应
     (01 格式化、02–17 既有工具、18 URL、19 UUID、20–24 格式与协议工具),
     页面本身就是 bit_pouch 的一部分,不套 ihomy 家庭外壳(路由 meta.standalone)。
     工具按编号清单排列,不用卡片网格。 -->
<template>
  <BpShell>
    <main class="bpc">
      <section class="bpc-head">
        <div class="bpc-head-body">
          <h1 class="bpc-head-title">{{ $t('bitPouch.tools.title') }}</h1>
          <p class="bpc-head-desc">{{ $t('bitPouch.tools.intro') }}</p>
        </div>
      </section>

      <div class="bpc-inline">
        <div class="bpc-field">
          <input
            id="bp-tool-search"
            v-model="keyword"
            class="bpc-input"
            type="search"
            autocomplete="off"
            spellcheck="false"
            :aria-label="$t('bitPouch.tools.search')"
            :placeholder="$t('bitPouch.tools.searchPlaceholder')"
          />
        </div>
        <span class="bpc-meta">{{ $t('bitPouch.tools.count', { n: visible.length }) }}</span>
      </div>

      <div class="bpc-index">
        <router-link v-for="tool in visible" :key="tool.id" class="bpc-index-item" :to="`/bit-pouch/tools/${tool.id}`">
          <span class="bpc-index-no">{{ tool.no }}</span>
          <span class="bpc-index-name">{{ $t(tool.titleKey) }}</span>
          <span class="bpc-index-desc">{{ $t(tool.descKey) }}</span>
        </router-link>
      </div>

      <p v-if="!visible.length" class="bpc-hint">{{ $t('bitPouch.tools.noMatch') }}</p>

      <p class="bpc-hint">
        {{ $t('bitPouch.tools.desktopHint') }}
        <router-link class="bpc-link" to="/bit-pouch">{{ $t('bitPouch.tools.desktopLink') }}</router-link>
      </p>
    </main>
  </BpShell>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpShell from './BpShell.vue'
import { BIT_POUCH_TOOLS } from '@/tools/bitPouchTools'

const { t } = useI18n()
const keyword = ref('')

const visible = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  if (!needle) return BIT_POUCH_TOOLS
  return BIT_POUCH_TOOLS.filter((tool) =>
    [t(tool.titleKey), t(tool.descKey), ...tool.keywords].some((text) => String(text).toLowerCase().includes(needle)),
  )
})
</script>
