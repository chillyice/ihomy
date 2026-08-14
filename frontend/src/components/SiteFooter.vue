<!-- 站点底部:工信部 ICP 备案号 + 公安备案号(左下角,与导航栏同层,受光效影响) -->
<template>
  <footer class="site-footer">
    <a href="https://beian.miit.gov.cn" target="_blank" rel="noopener noreferrer" class="footer-link">鲁ICP备2026045543号-1</a>
    <span class="footer-sep">·</span>
    <span class="footer-link footer-pending">公安备案 待登记</span>
  </footer>
</template>

<script setup>
</script>

<style scoped>
.site-footer {
  position: fixed;
  left: 12px;
  bottom: 8px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--color-text-secondary, rgba(58,46,34,0.65));
  font-family: Georgia, serif;
  pointer-events: auto;
}
.footer-link {
  color: inherit;
  text-decoration: none;
  letter-spacing: 0.2px;
  transition: color 0.15s;
}
.footer-link:hover {
  color: var(--color-accent, #C9A876);
}
.footer-pending {
  opacity: 0.55;
  cursor: default;
}
.footer-sep {
  opacity: 0.4;
}
:global(html.dark) .site-footer {
  color: rgba(232,220,200,0.55);
}
@media (max-width: 768px) {
  .site-footer { font-size: 10px; }
}
</style>
