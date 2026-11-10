<!-- Android 客户端下载页(公开):版本与安装包地址来自 /public/app/latest,下载走计数接口。
     文案只讲用户能做什么,不写内部机制;支持项改 i18n androidApp.*。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('androidApp.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('androidApp.title') }}</span>
      </div>
    </PageToolbar>

    <section class="card app-hero">
      <div class="app-hero-text">
        <h3 class="app-name">{{ $t('androidApp.name') }}</h3>
        <p class="app-lead">{{ $t('androidApp.lead') }}</p>
        <ul class="app-meta">
          <li>
            <span>{{ $t('androidApp.versionLabel') }}</span>
            <b>{{ version || $t('androidApp.notPublished') }}</b>
          </li>
          <li>
            <span>{{ $t('androidApp.requirementLabel') }}</span>
            <b>{{ $t('androidApp.requirementValue') }}</b>
          </li>
          <li v-if="sizeText">
            <span>{{ $t('androidApp.sizeLabel') }}</span>
            <b>{{ sizeText }}</b>
          </li>
        </ul>
        <div class="app-actions">
          <el-button v-if="downloadUrl" type="primary" tag="a" :href="downloadUrl" download>
            {{ $t('androidApp.download') }}
          </el-button>
          <span v-else class="app-hint">{{ $t('androidApp.notPublishedHint') }}</span>
        </div>
        <p v-if="notes" class="app-notes">{{ $t('androidApp.notesLabel') }}{{ notes }}</p>
      </div>
    </section>

    <section class="card app-sec">
      <h3 class="app-h">{{ $t('androidApp.installTitle') }}</h3>
      <ol class="app-steps">
        <li v-for="(x, i) in $tm('androidApp.installSteps')" :key="i">{{ x }}</li>
      </ol>
    </section>

    <section class="card app-sec">
      <h3 class="app-h">{{ $t('androidApp.permissionTitle') }}</h3>
      <p class="app-p">{{ $t('androidApp.permissionNote') }}</p>
      <ul class="app-list">
        <li v-for="(x, i) in $tm('androidApp.permissions')" :key="i">{{ x }}</li>
      </ul>
    </section>

    <section class="card app-sec">
      <h3 class="app-h">{{ $t('androidApp.widgetTitle') }}</h3>
      <p class="app-p">{{ $t('androidApp.widgetBody') }}</p>
    </section>

    <section class="card app-sec">
      <h3 class="app-h">{{ $t('androidApp.faqTitle') }}</h3>
      <dl class="app-faq">
        <template v-for="(item, i) in $tm('androidApp.faq')" :key="i">
          <dt>{{ item.q }}</dt>
          <dd>{{ item.a }}</dd>
        </template>
      </dl>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { serverUrl } from '@/utils/assetUrl'

const info = ref(null)

const version = computed(() => info.value?.version || '')
const notes = computed(() => info.value?.notes || '')
const apkName = computed(() => (info.value?.url || '').split('/').pop() || '')
// 下载走计数接口(先计数再 302 到 /files);文件名取自服务端下发的地址,避免前端写死版本号
const downloadUrl = computed(() =>
  apkName.value ? serverUrl(`/api/public/product/download?page=app&file=${encodeURIComponent(apkName.value)}`) : '',
)

const sizeBytes = ref(0)
const sizeText = computed(() => {
  if (!sizeBytes.value) return ''
  return `${(sizeBytes.value / 1024 / 1024).toFixed(1)} MB`
})

/** HEAD 安装包取体积(拿不到就不显示,不阻塞页面) */
async function loadSize() {
  if (!info.value?.url) return
  try {
    const r = await fetch(serverUrl(info.value.url), { method: 'HEAD', cache: 'no-store' })
    const len = Number(r.headers.get('content-length') || 0)
    if (Number.isFinite(len) && len > 0) sizeBytes.value = len
  } catch {
    /* 体积只是锦上添花,失败静默 */
  }
}

onMounted(async () => {
  // 访问打点用 POST,避开 PWA 对 GET 的缓存(缓存命中就不到后端,访问数会漏)
  fetch(serverUrl('/api/public/page-view?page=app'), { method: 'POST', keepalive: true }).catch(() => {})
  try {
    const r = await fetch(serverUrl('/api/public/app/latest'), { cache: 'no-store' })
    const j = await r.json()
    info.value = j?.data || null
  } catch {
    info.value = null
  }
  loadSize()
})
</script>

<style scoped>
.app-hero {
  padding: 22px 24px;
  margin-bottom: 14px;
}
.app-hero-text {
  min-width: 0;
}
.app-name {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 6px;
}
.app-lead {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--color-text-secondary, #8a8a8a);
}
.app-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 22px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;
  font-size: 12.5px;
  color: var(--color-text-secondary, #8a8a8a);
}
.app-meta b {
  margin-left: 6px;
  color: var(--color-text-primary, #303133);
  font-weight: 600;
}
.app-actions {
  margin-top: 14px;
}
.app-hint,
.app-notes {
  font-size: 12.5px;
  color: var(--color-text-secondary, #8a8a8a);
}
.app-notes {
  margin-top: 10px;
  line-height: 1.7;
}
.app-sec {
  padding: 18px 20px;
  margin-bottom: 14px;
}
.app-h {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
}
.app-p,
.app-list,
.app-steps,
.app-faq {
  font-size: 13.5px;
  line-height: 1.9;
  color: var(--color-text-secondary, #8a8a8a);
}
.app-list,
.app-steps {
  margin: 0;
  padding-left: 1.3em;
}
.app-steps li,
.app-list li {
  margin-bottom: 4px;
}
.app-faq {
  margin: 0;
}
.app-faq dt {
  font-weight: 600;
  color: var(--color-text-primary, #303133);
  margin-top: 10px;
}
.app-faq dt:first-child {
  margin-top: 0;
}
.app-faq dd {
  margin: 2px 0 0;
}

@media (max-width: 640px) {
  .app-hero {
    padding: 18px 16px;
  }
}
</style>
