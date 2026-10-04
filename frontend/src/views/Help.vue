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
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
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
</style>
