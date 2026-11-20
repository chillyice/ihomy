<!-- 使用帮助页:纯文案,面向家人(不是开发者)。内容改 i18n 的 help.*,不写实现方式 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('help.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('help.title') }}</span>
      </div>
    </PageToolbar>

    <p class="help-lead">{{ $t('help.lead') }}</p>

    <div class="help-list">
      <section class="card help-sec">
        <h3 class="help-h">{{ $t('help.about.title') }}</h3>
        <p class="help-p">{{ $t('help.about.body') }}</p>
      </section>

      <section class="card help-sec">
        <h3 class="help-h">{{ $t('help.roles.title') }}</h3>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.roles.items')" :key="i">{{ x }}</li>
        </ul>
        <p class="help-p help-note">{{ $t('help.roles.note') }}</p>
      </section>

      <section class="card help-sec">
        <h3 class="help-h">{{ $t('help.start.title') }}</h3>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.start.items')" :key="i">{{ x }}</li>
        </ul>
      </section>

      <section class="card help-sec">
        <h3 class="help-h">{{ $t('help.phone.title') }}</h3>
        <p class="help-p">{{ $t('help.phone.body') }}</p>
      </section>

      <!-- 锚点 #health:设置页「健康数据」的「完整步骤」链到这里 -->
      <section id="health" class="card help-sec help-health">
        <h3 class="help-h">{{ $t('help.health.title') }}</h3>
        <p class="help-p">{{ $t('help.health.lead') }}</p>

        <h4 class="help-sub">{{ $t('help.health.tokenTitle') }}</h4>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.health.tokenItems')" :key="i">{{ x }}</li>
        </ul>

        <h4 class="help-sub">{{ $t('help.health.shortcutTitle') }}</h4>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.health.shortcutItems')" :key="i">{{ x }}</li>
        </ul>

        <div class="help-tpl">
          <div class="help-tpl-head">
            <span class="help-tpl-label">{{ $t('help.health.bodyLabel') }}</span>
            <el-button size="small" type="primary" plain @click="copyBody">{{ $t('help.health.copy') }}</el-button>
          </div>
          <pre class="help-code">{{ SHORTCUT_BODY }}</pre>
          <p class="help-p help-note">{{ $t('help.health.bodyNote') }}</p>
        </div>

        <h4 class="help-sub">{{ $t('help.health.stageTitle') }}</h4>
        <p class="help-p">{{ $t('help.health.stageNote') }}</p>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.health.stageItems')" :key="i">{{ x }}</li>
        </ul>

        <h4 class="help-sub">{{ $t('help.health.autoTitle') }}</h4>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.health.autoItems')" :key="i">{{ x }}</li>
        </ul>

        <h4 class="help-sub">{{ $t('help.health.checkTitle') }}</h4>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.health.checkItems')" :key="i">{{ x }}</li>
        </ul>

        <h4 class="help-sub">{{ $t('help.health.limitTitle') }}</h4>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.health.limitItems')" :key="i">{{ x }}</li>
        </ul>
        <p class="help-p help-note">{{ $t('help.health.androidNote') }}</p>

        <h4 class="help-sub">{{ $t('help.health.huaweiTitle') }}</h4>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.health.huaweiItems')" :key="i">{{ x }}</li>
        </ul>
      </section>

      <section class="card help-sec">
        <h3 class="help-h">{{ $t('help.visible.title') }}</h3>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.visible.items')" :key="i">{{ x }}</li>
        </ul>
        <p class="help-p help-note">{{ $t('help.visible.note') }}</p>
      </section>

      <section class="card help-sec">
        <h3 class="help-h">{{ $t('help.trouble.title') }}</h3>
        <ul class="help-ul">
          <li v-for="(x, i) in $tm('help.trouble.items')" :key="i">{{ x }}</li>
        </ul>
      </section>

      <section class="card help-sec">
        <h3 class="help-h">{{ $t('help.ask.title') }}</h3>
        <p class="help-p">{{ $t('help.ask.body') }}</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { copyText } from '@/utils/clipboard'
import { SHORTCUT_BODY } from '@/utils/healthShortcut'

const { t } = useI18n()
const route = useRoute()

const copyBody = async () => {
  const ok = await copyText(SHORTCUT_BODY)
  if (ok) ElMessage.success(t('common.copied'))
  else ElMessage.warning(t('common.copyFailed'))
}

// 从设置页「完整步骤」进来时带着 #health,滚到那一节(本页无全局 scrollBehavior)
const alignHealth = () => document.getElementById('health')?.scrollIntoView({ block: 'start' })

onMounted(async () => {
  if (route.hash !== '#health') return
  await nextTick()
  alignHealth()
  // 演示家庭提示条、字体等异步内容会在挂载后再把页面撑高,内容落定后补对一次;
  // 用户已经自己滚过就不再抢滚动
  let userMoved = false
  const markMoved = () => {
    userMoved = true
  }
  window.addEventListener('wheel', markMoved, { passive: true, once: true })
  window.addEventListener('touchmove', markMoved, { passive: true, once: true })
  setTimeout(() => {
    if (!userMoved) alignHealth()
  }, 400)
})
</script>

<style scoped>
.help-lead {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--color-text-secondary, #8a8a8a);
  margin-bottom: 16px;
}
.help-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.help-sec {
  padding: 18px 20px;
}
.help-h {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 8px;
}
.help-p,
.help-ul {
  font-size: 13.5px;
  line-height: 1.9;
  color: var(--color-text-secondary, #8a8a8a);
}
.help-ul {
  margin: 0;
  padding-left: 1.25em;
}
.help-ul li {
  margin-bottom: 2px;
}
.help-note {
  margin-top: 8px;
}
.help-sub {
  font-size: 13.5px;
  font-weight: 600;
  margin: 14px 0 4px;
}
.help-health {
  scroll-margin-top: 80px;
}
.help-tpl {
  margin-top: 10px;
}
.help-tpl-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}
.help-tpl-label {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.help-code {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--color-border, rgba(0, 0, 0, 0.08));
  background: var(--color-bg-2, rgba(0, 0, 0, 0.04));
  font-size: 12px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre;
}
</style>
