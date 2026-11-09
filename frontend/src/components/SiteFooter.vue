<!-- 站点底部:工信部 ICP 备案号 + 公安备案号 + 版本号(左下角,与导航栏同层,受光效影响) -->
<template>
  <footer class="site-footer">
    <a href="https://beian.miit.gov.cn" target="_blank" rel="noopener noreferrer" class="footer-link">鲁ICP备2026045543号-1</a>
    <span class="footer-sep">·</span>
    <a
      href="http://www.beian.gov.cn/portal/registerSystemInfo?recordcode=37010202700748"
      target="_blank"
      rel="noopener noreferrer"
      class="footer-link"
      >鲁公网安备37010202700748号</a
    >
    <span class="footer-sep">·</span>
    <span class="footer-version">ihomy {{ appVersion }}</span>
  </footer>
</template>

<script setup>
import { onMounted, ref } from 'vue'

// 版本号唯一事实来源是根 VERSION:Web 端由构建注入 __APP_VERSION__,
// 原生壳内改读安装包 versionName(gradle 同样从 VERSION 生成),两者展示一致。
const appVersion = ref(__APP_VERSION__)

onMounted(() => {
  import('@/native/version')
    .then((m) => m.getAppVersion())
    .then((v) => {
      if (v) appVersion.value = v
    })
    .catch(() => {})
})
</script>

<style scoped>
.site-footer {
  position: fixed;
  right: 16px;
  bottom: 8px;
  z-index: var(--z-footer);
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--color-text-secondary, rgba(58, 46, 34, 0.65));
  font-family: Georgia, serif;
  pointer-events: auto;
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.5);
}
.footer-link {
  color: inherit;
  text-decoration: none;
  letter-spacing: 0.2px;
  transition: color 0.15s;
}
.footer-link:hover {
  color: var(--color-accent, #c9a876);
}
.footer-pending {
  opacity: 0.55;
  cursor: default;
}
.footer-sep {
  opacity: 0.4;
}
.footer-version {
  opacity: 0.55;
  letter-spacing: 0.2px;
  cursor: default;
}
html.dark .site-footer {
  color: rgba(232, 220, 200, 0.55);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}
@media (max-width: 768px) {
  .site-footer {
    font-size: 10px;
  }
}
</style>
