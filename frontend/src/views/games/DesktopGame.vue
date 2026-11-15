<!-- 桌面游戏(PC 安装包,type=DESKTOP):浏览器里跑不起来,本页只做两件事——
     托管安装包原文件供成员下载 + 给安装/兼容性说明。手机端点下载会拿到文件但装不了,先提示改用电脑。 -->
<template>
  <div class="page desktop-game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <el-button size="small" round @click="router.push(props.backTo)">
        <el-icon><ArrowLeft /></el-icon>
        {{ $t('games.back') }}
      </el-button>
      <span class="dg-title">{{ game?.name || $t('games.title') }}</span>
      <div class="game-top-actions">
        <el-button type="primary" size="small" round :disabled="!game?.fileUrl" @click="download">
          <el-icon><Download /></el-icon>
          <span class="dg-btn-label">{{ $t('games.desktop.download') }}</span>
        </el-button>
      </div>
    </PageToolbar>

    <div class="dg-body">
      <div class="dg-card card">
        <div class="dg-head">
          <el-icon :size="34"><Monitor /></el-icon>
          <div>
            <div class="dg-name">{{ game?.name }}</div>
            <div class="dg-sub">{{ $t('games.desktop.subtitle') }}</div>
          </div>
        </div>
        <p v-if="game?.description" class="dg-desc">{{ game.description }}</p>

        <div class="dg-tip">{{ $t('games.desktop.mobileTip') }}</div>

        <div class="section-label">{{ $t('games.desktop.steps') }}</div>
        <ol class="dg-steps">
          <li>{{ $t('games.desktop.step1') }}</li>
          <li>{{ $t('games.desktop.step2') }}</li>
          <li>{{ $t('games.desktop.step3') }}</li>
        </ol>

        <div class="dg-note">{{ $t('games.desktop.note') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Download, Monitor } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { gameApi } from '@/api'

const props = defineProps({
  /** 上层播放器页已经取到的游戏对象(直接访问 /games/play/{id} 时也有) */
  game: { type: Object, default: null },
  /** 返回按钮目标(默认回游戏库) */
  backTo: { type: String, default: '/games' },
})

const route = useRoute()
const router = useRouter()
// 兜底:没有传入对象(单独使用本页)时自己取一次,避免同一页取两遍
const fetched = ref(null)
const game = computed(() => props.game || fetched.value)

onMounted(async () => {
  if (!game.value) fetched.value = await gameApi.get(route.params.id)
})

/** 同源下载:带 download 属性直接存盘,不在标签页里打开二进制 */
const download = () => {
  const url = game.value?.fileUrl
  if (!url) return
  const a = document.createElement('a')
  a.href = url
  a.download = decodeURIComponent(url.split('/').pop() || game.value.name || 'game')
  document.body.appendChild(a)
  a.click()
  a.remove()
}
</script>

<style scoped>
.dg-title {
  font-weight: 600;
  color: var(--color-text);
}

.dg-body {
  max-width: 720px;
  margin: 0 auto;
}

.dg-card {
  padding: 20px 22px 18px;
}

.dg-head {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--color-primary);
}

.dg-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
}

.dg-sub {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.dg-desc {
  margin: 14px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}

.dg-tip {
  margin: 14px 0;
  padding: 10px 12px;
  border-radius: var(--radius);
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  background: var(--color-bg-2);
  border-left: 3px solid var(--color-primary);
}

.dg-steps {
  margin: 8px 0 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-secondary);
}

.dg-note {
  margin-top: 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-tertiary);
}

@media (max-width: 640px) {
  .dg-btn-label {
    display: none;
  }
}
</style>
