<!-- bit_pouch 独立页外壳:顶栏 + 内容槽 + 页脚。
     路由带 meta.standalone,App.vue 只渲染本组件;装/卸载时接管 html 根类的底色,
     让滚动条与橡皮筋区也跟着 bit_pouch 的深浅配色(与发布页同一约定)。 -->
<template>
  <div class="bp">
    <div class="bpwrap">
      <BpTop />
      <slot />
      <footer class="bptools-foot">
        <span>{{ $t('bitPouch.footer', { version }) }}</span>
        <span class="bptools-spacer"></span>
        <router-link class="bpc-link" to="/bit-pouch">{{ $t('bitPouch.tools.backToHome') }}</router-link>
        <span aria-hidden="true">·</span>
        <span>{{ $t('bitPouch.hostedBy') }}</span>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave } from 'vue-router'
import BpTop from './BpTop.vue'
import { BIT_POUCH_VERSION } from './brand'

const props = defineProps({
  // 浏览器标签页标题;工具页传具体工具名,目录页用默认的「在线工具」。
  docTitle: { type: String, default: '' },
})

const version = BIT_POUCH_VERSION
const { t } = useI18n()

const pageTitle = computed(() => props.docTitle || t('bitPouch.tools.docTitle'))
const applyTitle = () => {
  document.title = pageTitle.value
}

const resetChrome = () => {
  document.title = 'ihomy'
  document.documentElement.classList.remove('bitpouch-page')
}

onMounted(() => {
  document.documentElement.classList.add('bitpouch-page')
  applyTitle()
})
onBeforeUnmount(resetChrome)
watch(pageTitle, applyTitle)

// 在 /bit-pouch 内部跳转时不复位,避免「先把根类摘掉、再由下一页挂回来」的闪烁。
onBeforeRouteLeave((to) => {
  if (!to.path.startsWith('/bit-pouch')) resetChrome()
})
</script>
